package com.just.critter.service.fund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.just.critter.domain.enums.ExpenseStatus;
import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.fund.ExpenseCodeGenerator;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.Expense;
import com.just.critter.domain.model.Project;
import com.just.critter.domain.model.SysUser;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.ExpenseMapper;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.mapper.SysUserMapper;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支出登记与复核（T3.2/T3.3）。
 *
 * <p>登记只写 PENDING_REVIEW，不改变项目公开的已支出、余额；复核通过才计入。
 * 余额检查在事务内锁 project 行后进行，待复核金额作为已占用额度，防止多笔同时
 * 登记把余额透支。超 500 元必须两名不同管理员先后复核，登记人永远不能复核自己。</p>
 */
@Service
public class ExpenseService {

    static final int DOUBLE_REVIEW_THRESHOLD = 50_000;
    private static final int MIN_AMOUNT = 1;
    private static final int MAX_AMOUNT = 10_000_000;
    private static final int MAX_PURPOSE = 32;
    private static final int MAX_PAYEE = 64;
    private static final int MAX_REASON = 255;
    private static final int MAX_VOUCHERS = 10;
    private static final String NOTE_PENDING = "支出已登记，待管理员复核后计入项目支出";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ExpenseMapper expenses;
    private final ProjectMapper projects;
    private final AnimalMapper animals;
    private final SysUserMapper users;
    private final ExpenseCodeGenerator codeGenerator;
    private final AuditLogService auditLog;
    private final Clock clock;

    public ExpenseService(ExpenseMapper expenses, ProjectMapper projects, AnimalMapper animals,
                          SysUserMapper users, ExpenseCodeGenerator codeGenerator,
                          AuditLogService auditLog, Clock clock) {
        this.expenses = expenses;
        this.projects = projects;
        this.animals = animals;
        this.users = users;
        this.codeGenerator = codeGenerator;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Transactional
    public CreatedExpense create(CreateExpenseCommand command, CurrentUser actor) {
        requireActor(actor);
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请求体不能为空");
        }
        String projectNo = requireText(command.projectNo(), "项目编号必填");
        String animalCode = requireText(command.animalCode(), "动物编号必填");
        String purpose = requireText(command.purpose(), "支出用途必填");
        String payee = requireText(command.payee(), "收款方必填");
        if (purpose.length() > MAX_PURPOSE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "支出用途最多 32 个字符");
        }
        if (payee.length() > MAX_PAYEE) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "收款方最多 64 个字符");
        }
        int amount = requireAmount(command.amount());
        if (command.occurredAt() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "支出发生时间必填");
        }
        List<String> vouchers = requireVouchers(command.voucherFileIds());

        Project project = projects.selectByProjectNo(projectNo);
        if (project == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "项目不存在：" + projectNo);
        }
        project = projects.selectByIdForUpdate(project.getId());
        if (project == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "项目不存在：" + projectNo);
        }
        requireExpenseProject(project);

        Animal animal = animals.selectByCode(animalCode);
        if (animal == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "动物编号不存在：" + animalCode);
        }

        LocalDateTime occurred = command.occurredAt().atZoneSameInstant(ZONE).toLocalDateTime().withNano(0);
        if (occurred.toLocalDate().isBefore(project.getStartDate())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "支出发生时间不得早于项目开始日期");
        }

        int balance = balance(project);
        int pending = expenses.sumPendingByProjectId(project.getId());
        int available = balance - pending;
        if (amount > available) {
            throw balanceError(balance, pending, amount, available);
        }

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Expense expense = new Expense();
        expense.setExpenseNo(codeGenerator.next());
        expense.setProjectId(project.getId());
        expense.setAnimalId(animal.getId());
        expense.setPurpose(purpose);
        expense.setAmount(amount);
        expense.setPayee(payee);
        expense.setOccurredAt(occurred);
        expense.setHandlerId(actor.id());
        expense.setVoucherFileIds(vouchers);
        expense.setStatus(ExpenseStatus.PENDING_REVIEW);
        expense.setCreatedAt(now);
        expense.setUpdatedAt(now);
        expense.setCreatedBy(actor.id());
        expenses.insert(expense);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("projectNo", project.getProjectNo());
        after.put("animalCode", animalCode);
        after.put("amount", amount);
        after.put("status", expense.getStatus().name());
        audit(actor, "EXPENSE_CREATE", expense.getExpenseNo(), null, after);
        return new CreatedExpense(expense.getExpenseNo(), expense.getStatus(), NOTE_PENDING);
    }

    @Transactional
    public ExpenseReviewResult review(String expenseNo, ExpenseReviewCommand command, CurrentUser actor) {
        requireActor(actor);
        if (command == null || command.decision() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "复核决定必填");
        }
        String reason = trimToNull(command.reason());
        if (command.decision() == ExpenseDecision.REJECT && (reason == null || reason.length() < 5)) {
            throw new BusinessException(ErrorCode.CHANGE_REASON_REQUIRED.getCode(), "驳回原因至少 5 个字");
        }
        if (reason != null && reason.length() > MAX_REASON) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "复核意见最多 255 个字符");
        }

        Expense expense = expenses.selectByExpenseNoForUpdate(requireText(expenseNo, "支出单号必填"));
        if (expense == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "支出记录不存在：" + expenseNo);
        }
        if (expense.getStatus() == ExpenseStatus.APPROVED || expense.getStatus() == ExpenseStatus.REJECTED) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "该支出已完成复核，不可重复处理");
        }
        Project project = projects.selectByIdForUpdate(expense.getProjectId());
        if (project == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND, "支出关联项目不存在");
        }

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        if (command.decision() == ExpenseDecision.REJECT) {
            reject(expense, project, actor, reason, now);
            return new ExpenseReviewResult(expense.getExpenseNo(), expense.getStatus(), false,
                    snapshot(project), "已驳回：" + reason);
        }

        if (Objects.equals(expense.getHandlerId(), actor.id())) {
            throw new BusinessException(ErrorCode.SELF_REVIEW_NOT_ALLOWED);
        }
        if (expense.getStatus() == ExpenseStatus.PENDING_REVIEW
                && expense.getAmount() > DOUBLE_REVIEW_THRESHOLD) {
            int balance = balance(project);
            if (expense.getAmount() > balance) {
                throw balanceError(balance, 0, expense.getAmount(), balance);
            }
            expense.setFirstReviewerId(actor.id());
            expense.setStatus(ExpenseStatus.FIRST_APPROVED);
            expense.setReviewReason(reason);
            expense.setUpdatedAt(now);
            expenses.updateById(expense);
            audit(actor, "EXPENSE_FIRST_APPROVE", expense.getExpenseNo(), null,
                    Map.of("status", expense.getStatus().name(), "firstReviewerId", actor.id()));
            return new ExpenseReviewResult(expense.getExpenseNo(), expense.getStatus(), true,
                    snapshot(project), "大额支出已完成第一签，等待另一名管理员复核");
        }

        if (expense.getStatus() == ExpenseStatus.FIRST_APPROVED) {
            if (Objects.equals(expense.getFirstReviewerId(), actor.id())) {
                throw new BusinessException(ErrorCode.SELF_REVIEW_NOT_ALLOWED,
                        "第二签不得与第一复核人相同");
            }
            int balance = balance(project);
            if (expense.getAmount() > balance) {
                throw balanceError(balance, 0, expense.getAmount(), balance);
            }
            expense.setSecondReviewerId(actor.id());
        } else {
            expense.setFirstReviewerId(actor.id());
        }

        int approved = expenses.sumApprovedByProjectId(project.getId());
        expense.setStatus(ExpenseStatus.APPROVED);
        expense.setReviewReason(reason);
        expense.setUpdatedAt(now);
        expenses.updateById(expense);

        project.setSpentAmount(approved + expense.getAmount());
        project.setUpdatedAt(now);
        projects.updateById(project);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", expense.getStatus().name());
        after.put("spentAmount", project.getSpentAmount());
        after.put("balance", balance(project));
        audit(actor, "EXPENSE_APPROVE", expense.getExpenseNo(), null, after);
        return new ExpenseReviewResult(expense.getExpenseNo(), expense.getStatus(), false,
                snapshot(project), "支出已复核，计入项目已支出");
    }

    public PageResult<ExpenseSummary> search(ExpenseFilter filter, PageQuery pageQuery) {
        PageQuery safe = pageQuery.normalized();
        LambdaQueryWrapper<Expense> query = new LambdaQueryWrapper<>();
        if (filter != null && filter.projectNo() != null && !filter.projectNo().isBlank()) {
            Project project = projects.selectByProjectNo(filter.projectNo().trim());
            if (project == null) {
                return PageResult.of(List.of(), safe.getPage(), safe.getSize(), 0);
            }
            query.eq(Expense::getProjectId, project.getId());
        }
        if (filter != null && filter.statuses() != null && !filter.statuses().isEmpty()) {
            query.in(Expense::getStatus, filter.statuses());
        }
        query.orderByDesc(Expense::getUpdatedAt).orderByDesc(Expense::getId);

        IPage<Expense> result = expenses.selectPage(new Page<>(safe.getPage(), safe.getSize()), query);
        Map<Long, Project> projectById = projectMap(result.getRecords());
        Map<Long, Animal> animalById = animalMap(result.getRecords());
        Map<Long, SysUser> userById = userMap(result.getRecords());
        List<ExpenseSummary> list = result.getRecords().stream()
                .map(expense -> toSummary(expense, projectById, animalById, userById))
                .toList();
        return PageResult.of(list, safe.getPage(), safe.getSize(), result.getTotal());
    }

    private void reject(Expense expense, Project project, CurrentUser actor,
                        String reason, LocalDateTime now) {
        if (Objects.equals(expense.getHandlerId(), actor.id())) {
            throw new BusinessException(ErrorCode.SELF_REVIEW_NOT_ALLOWED);
        }
        if (expense.getStatus() == ExpenseStatus.PENDING_REVIEW) {
            expense.setFirstReviewerId(actor.id());
        } else {
            expense.setSecondReviewerId(actor.id());
        }
        expense.setStatus(ExpenseStatus.REJECTED);
        expense.setReviewReason(reason);
        expense.setUpdatedAt(now);
        expenses.updateById(expense);
        audit(actor, "EXPENSE_REJECT", expense.getExpenseNo(), null,
                Map.of("status", expense.getStatus().name(), "reason", reason));
    }

    private ExpenseSummary toSummary(Expense expense, Map<Long, Project> projectById,
                                     Map<Long, Animal> animalById, Map<Long, SysUser> userById) {
        Project project = projectById.get(expense.getProjectId());
        Animal animal = animalById.get(expense.getAnimalId());
        SysUser handler = userById.get(expense.getHandlerId());
        return new ExpenseSummary(
                expense.getExpenseNo(),
                project == null ? null : project.getProjectNo(),
                project == null ? null : project.getName(),
                animal == null ? null : animal.getCode(),
                animal == null ? null : animal.getName(),
                expense.getPurpose(),
                expense.getAmount(),
                expense.getPayee(),
                toOffset(expense.getOccurredAt()),
                expense.getHandlerId(),
                handler == null ? null : handler.getNickname(),
                expense.getVoucherFileIds(),
                expense.getStatus(),
                expense.getFirstReviewerId(),
                expense.getSecondReviewerId(),
                expense.getReviewReason(),
                toOffset(expense.getCreatedAt()),
                toOffset(expense.getUpdatedAt()));
    }

    private void requireExpenseProject(Project project) {
        if (project.getStatus() != ProjectStatus.FUNDING
                && project.getStatus() != ProjectStatus.PENDING_SETTLE) {
            throw new BusinessException(ErrorCode.PROJECT_STATE_INVALID,
                    "项目当前状态不可登记支出");
        }
    }

    private int balance(Project project) {
        int raised = project.getRaisedAmount() == null ? 0 : project.getRaisedAmount();
        int approved = expenses.sumApprovedByProjectId(project.getId());
        return raised - approved;
    }

    private BusinessException balanceError(int balance, int reserved, int requested, int available) {
        int gap = requested - available;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("projectBalance", balance);
        data.put("reserved", reserved);
        data.put("requested", requested);
        data.put("gap", Math.max(gap, 0));
        return new BusinessException(ErrorCode.BALANCE_NOT_ENOUGH.getCode(),
                "项目余额不足，尚差 " + money(gap) + " 元", data);
    }

    private ProjectFundSnapshot snapshot(Project project) {
        int raised = project.getRaisedAmount() == null ? 0 : project.getRaisedAmount();
        int spent = project.getSpentAmount() == null ? 0 : project.getSpentAmount();
        return new ProjectFundSnapshot(project.getProjectNo(), raised, spent, raised - spent);
    }

    private int requireAmount(Integer amount) {
        if (amount == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "支出金额必填");
        }
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "支出金额需在 1—10000000 分之间");
        }
        return amount;
    }

    private List<String> requireVouchers(List<String> voucherFileIds) {
        if (voucherFileIds == null || voucherFileIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "请上传至少 1 张支出票据");
        }
        if (voucherFileIds.size() > MAX_VOUCHERS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "单笔支出最多上传 10 张票据");
        }
        List<String> cleaned = voucherFileIds.stream()
                .map(ExpenseService::trimToNull)
                .filter(Objects::nonNull)
                .toList();
        if (cleaned.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "票据文件编号不能为空");
        }
        for (String voucher : cleaned) {
            if (voucher.length() > 64) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "票据文件编号非法");
            }
        }
        return List.copyOf(cleaned);
    }

    private String requireText(String value, String message) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, message);
        }
        return text;
    }

    private void requireActor(CurrentUser actor) {
        if (actor == null) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN);
        }
    }

    private Map<Long, Project> projectMap(List<Expense> records) {
        Set<Long> ids = records.stream().map(Expense::getProjectId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return projects.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Animal> animalMap(List<Expense> records) {
        Set<Long> ids = records.stream().map(Expense::getAnimalId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return animals.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Animal::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, SysUser> userMap(List<Expense> records) {
        Set<Long> ids = records.stream().map(Expense::getHandlerId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity(), (left, right) -> left));
    }

    private void audit(CurrentUser actor, String action, String expenseNo,
                       Object before, Map<String, Object> after) {
        auditLog.successAs(actor.id(), actor.primaryRole() == null ? "STUDENT" : actor.primaryRole(),
                action, "EXPENSE", expenseNo, before, after);
    }

    private static String money(int cents) {
        return String.format("%,.2f", cents / 100.0);
    }

    private static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZONE).toOffsetDateTime();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
