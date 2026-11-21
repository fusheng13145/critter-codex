package com.just.critter.service.fund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.just.critter.domain.enums.DonationStatus;
import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.fund.DonationCodeGenerator;
import com.just.critter.domain.model.Donation;
import com.just.critter.domain.model.Project;
import com.just.critter.domain.model.SysUser;
import com.just.critter.mapper.DonationMapper;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.mapper.SysUserMapper;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 捐赠登记与核对入账。登记只落 PENDING_VERIFY；只有核对为 VERIFIED 的流水
 * 才会在同一事务里递增 project.raised_amount，因此“未入账不计进度”。
 *
 * <p>入账前校验项目已筹金额必须等于已入账流水合计；不相等返回 40106，
 * 防止有人手工改动 raised_amount 把未入账金额提前算进公开进度。</p>
 */
@Service
public class DonationService {

    private static final Logger log = LoggerFactory.getLogger(DonationService.class);

    private static final int MIN_AMOUNT = 1;
    private static final int MAX_AMOUNT = 10_000_000;
    private static final int MAX_MESSAGE = 100;
    private static final int MAX_NOTE = 255;
    private static final int MAX_VOUCHERS = 10;
    private static final int MAX_BATCH = 100;
    private static final String NOTE_PENDING = "登记已收到，待管理员核对到账后计入项目进度";
    private static final String NOTE_ABNORMAL = "实收金额与登记金额不一致，已标记异常，待人工复核";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final DonationMapper donations;
    private final ProjectMapper projects;
    private final SysUserMapper users;
    private final DonationCodeGenerator codeGenerator;
    private final AuditLogService auditLog;
    private final TransactionTemplate transactionTemplate;
    private final MessageRepository messages;
    private final Clock clock;

    public DonationService(DonationMapper donations,
                           ProjectMapper projects,
                           SysUserMapper users,
                           DonationCodeGenerator codeGenerator,
                           AuditLogService auditLog,
                           TransactionTemplate transactionTemplate,
                           MessageRepository messages,
                           Clock clock) {
        this.donations = donations;
        this.projects = projects;
        this.users = users;
        this.codeGenerator = codeGenerator;
        this.auditLog = auditLog;
        this.transactionTemplate = transactionTemplate;
        this.messages = messages;
        this.clock = clock;
    }

    /**
     * 学生捐赠登记。登记本身不改变项目进度，响应里明确回 note。
     */
    @Transactional
    public CreatedDonation create(CreateDonationCommand command, CurrentUser actor) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "请求体不能为空");
        }
        requireActor(actor);
        Project project = requireFundingProject(command.projectNo());
        int amount = requireAmount(command.amount());
        List<String> vouchers = requireVouchers(command.voucherFileIds());
        String message = optionalText(command.message(), MAX_MESSAGE, "留言最多 100 个字符");
        boolean anonymous = Boolean.TRUE.equals(command.anonymous());

        LocalDateTime now = LocalDateTime.now(clock).withNano(0);
        Donation donation = new Donation();
        donation.setDonationNo(codeGenerator.next());
        donation.setProjectId(project.getId());
        donation.setUserId(anonymous ? null : actor.id());
        donation.setDonorDisplay(anonymous ? "爱心人士" : displayName(actor));        donation.setAmount(amount);
        donation.setAnonymous(anonymous);
        donation.setMessage(message);
        donation.setVoucherFileIds(vouchers);
        donation.setStatus(DonationStatus.PENDING_VERIFY);
        donation.setCreatedAt(now);
        donation.setUpdatedAt(now);
        donation.setCreatedBy(actor.id());
        donations.insert(donation);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("projectNo", project.getProjectNo());
        after.put("amount", amount);
        after.put("anonymous", anonymous);
        after.put("status", donation.getStatus().name());
        audit(actor, "DONATION_CREATE", donation.getDonationNo(), null, after);
        return new CreatedDonation(donation.getDonationNo(), donation.getStatus(), NOTE_PENDING);
    }

    /**
     * 单笔核对。实收金额一致才入账并递增项目进度；不一致只标 ABNORMAL，进度不动。
     */
    @Transactional
    public DonationConfirmation confirm(String donationNo, ConfirmDonationCommand command, CurrentUser actor) {
        return doConfirm(donationNo, command, actor);
    }

    /**
     * 批量核对：按单号逐笔独立事务，部分成功不回滚；失败项逐条返回。
     * 批量只核对登记金额，不允许把同一实收金额套到多笔不同登记上。
     */
    public BatchConfirmResult confirmBatch(List<String> donationNos, CurrentUser actor) {
        requireActor(actor);
        if (donationNos == null || donationNos.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "请选择待核对捐赠");
        }
        if (donationNos.size() > MAX_BATCH) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "单次最多核对 100 笔");
        }
        List<String> ordered = new ArrayList<>(new LinkedHashSet<>(donationNos));
        List<BatchFailure> failures = new ArrayList<>();
        int success = 0;
        for (String donationNo : ordered) {
            if (donationNo == null || donationNo.isBlank()) {
                failures.add(new BatchFailure("", ErrorCode.PARAM_INVALID.getCode(), "捐赠单号不能为空"));
                continue;
            }
            try {
                transactionTemplate.execute(status -> {
                    doConfirm(donationNo, null, actor);
                    return null;
                });
                success++;
            } catch (BusinessException ex) {
                failures.add(new BatchFailure(donationNo, ex.getCode(), ex.getMessage()));
            } catch (RuntimeException ex) {
                log.warn("批量核对失败 donationNo={}", donationNo, ex);
                failures.add(new BatchFailure(donationNo, ErrorCode.SYSTEM_ERROR.getCode(),
                        ErrorCode.SYSTEM_ERROR.getMessage()));
            }
        }
        return new BatchConfirmResult(success, failures);
    }

    public PageResult<DonationSummary> search(DonationFilter filter, PageQuery pageQuery) {
        PageQuery safe = pageQuery.normalized();
        Page<Donation> page = new Page<>(safe.getPage(), safe.getSize());
        LambdaQueryWrapper<Donation> query = new LambdaQueryWrapper<>();

        String projectNo = filter == null ? null : trimToNull(filter.projectNo());
        if (projectNo != null) {
            Project project = projects.selectByProjectNo(projectNo);
            if (project == null) {
                return PageResult.of(List.of(), safe.getPage(), safe.getSize(), 0);
            }
            query.eq(Donation::getProjectId, project.getId());
        }
        Set<DonationStatus> statuses = filter == null ? null : filter.statuses();
        if (statuses != null && !statuses.isEmpty()) {
            query.in(Donation::getStatus, statuses);
        }
        query.orderByDesc(Donation::getUpdatedAt).orderByDesc(Donation::getId);

        IPage<Donation> result = donations.selectPage(page, query);
        Map<Long, Project> projectById = projectMap(result.getRecords());
        List<DonationSummary> list = result.getRecords().stream()
                .map(donation -> {
                    Project project = projectById.get(donation.getProjectId());
                    return new DonationSummary(
                            donation.getDonationNo(),
                            project == null ? null : project.getProjectNo(),
                            project == null ? null : project.getName(),
                            donation.getUserId(),
                            donation.getDonorDisplay(),
                            donation.getAmount(),
                            Boolean.TRUE.equals(donation.getAnonymous()),
                            donation.getMessage(),
                            donation.getVoucherFileIds(),
                            donation.getStatus(),
                            donation.getVerifierId(),
                            toOffset(donation.getVerifiedAt()),
                            toOffset(donation.getCreatedAt()),
                            toOffset(donation.getUpdatedAt()));
                })
                .toList();
        return PageResult.of(list, safe.getPage(), safe.getSize(), result.getTotal());
    }

    private DonationConfirmation doConfirm(String donationNo, ConfirmDonationCommand command, CurrentUser actor) {
        requireActor(actor);
        String no = trimToNull(donationNo);
        if (no == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "捐赠单号必填");
        }
        Donation donation = donations.selectByDonationNoForUpdate(no);
        if (donation == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND.getCode(), "捐赠记录不存在：" + no);
        }
        if (donation.getStatus() == DonationStatus.VERIFIED) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "已入账捐赠不可重复核对");
        }
        if (donation.getStatus() != DonationStatus.PENDING_VERIFY
                && donation.getStatus() != DonationStatus.ABNORMAL) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "捐赠状态不可核对");
        }

        Project project = projects.selectByIdForUpdate(donation.getProjectId());
        if (project == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND.getCode(), "捐赠关联项目不存在");
        }
        if (project.getStatus() != ProjectStatus.FUNDING
                && project.getStatus() != ProjectStatus.PENDING_SETTLE) {
            throw new BusinessException(ErrorCode.PROJECT_STATE_INVALID.getCode(), "项目当前状态不可核对入账");
        }

        int verifiedTotal = donations.sumVerifiedByProjectId(project.getId());
        int raisedAmount = project.getRaisedAmount() == null ? 0 : project.getRaisedAmount();
        if (raisedAmount != verifiedTotal) {
            throw new BusinessException(ErrorCode.DONATION_NOT_VERIFIED,
                    "项目已筹金额与已入账流水不一致，请先纠正后再核对");
        }

        int actualAmount = command == null || command.amount() == null
                ? donation.getAmount() : requireAmount(command.amount());
        String note = optionalText(command == null ? null : command.note(), MAX_NOTE, "核对备注最多 255 个字符");
        List<String> vouchers = command == null ? null : optionalVouchers(command.voucherFileIds());
        LocalDateTime now = LocalDateTime.now(clock).withNano(0);

        if (actualAmount != donation.getAmount()) {
            DonationStatus beforeStatus = donation.getStatus();
            donation.setStatus(DonationStatus.ABNORMAL);
            donation.setVerifierId(actor.id());
            donation.setVerifiedAt(null);
            donation.setUpdatedAt(now);
            if (vouchers != null) {
                donation.setVoucherFileIds(vouchers);
            }
            donations.updateById(donation);

            String abnormalNote = NOTE_ABNORMAL + "：登记 " + donation.getAmount()
                    + " 分，实收 " + actualAmount + " 分";
            if (note != null) {
                abnormalNote = abnormalNote + "；" + note;
            }
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("status", beforeStatus.name());
            before.put("amount", donation.getAmount());
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("status", donation.getStatus().name());
            after.put("confirmedAmount", actualAmount);
            after.put("note", abnormalNote);
            after.put("paidAt", command == null ? null : command.paidAt());
            audit(actor, "DONATION_ABNORMAL", donation.getDonationNo(), before, after);
            notifyDonor(donation, actor, false, actualAmount, now);
            return new DonationConfirmation(donation.getDonationNo(), donation.getStatus(),
                    donation.getAmount(), null, progress(project), abnormalNote);
        }

        donation.setStatus(DonationStatus.VERIFIED);
        donation.setVerifierId(actor.id());
        donation.setVerifiedAt(now);
        donation.setUpdatedAt(now);
        if (vouchers != null) {
            donation.setVoucherFileIds(vouchers);
        }
        donations.updateById(donation);

        project.setRaisedAmount(verifiedTotal + actualAmount);
        project.setUpdatedAt(now);
        projects.updateById(project);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", DonationStatus.PENDING_VERIFY.name());
        before.put("raisedAmount", raisedAmount);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", donation.getStatus().name());
        after.put("confirmedAmount", actualAmount);
        after.put("raisedAmount", project.getRaisedAmount());
        after.put("paidAt", command == null ? null : command.paidAt());
        after.put("note", note);
        audit(actor, "DONATION_CONFIRM", donation.getDonationNo(), before, after);
        notifyDonor(donation, actor, true, actualAmount, now);
        return new DonationConfirmation(donation.getDonationNo(), donation.getStatus(),
                donation.getAmount(), toOffset(now), progress(project), note);
    }

    /**
     * 只通知实名捐赠人；匿名捐赠没有 receiver_id，跳过。消息与入账同事务，失败整笔回滚。
     */
    private void notifyDonor(Donation donation, CurrentUser actor,
                             boolean verified, int actualAmount, LocalDateTime now) {
        Long receiverId = donation.getUserId();
        if (receiverId == null) {
            return;
        }
        String title = verified
                ? "捐赠已入账：" + yuan(actualAmount) + " 元"
                : "捐赠核对异常：登记 " + yuan(donation.getAmount())
                        + " 元，实收 " + yuan(actualAmount) + " 元";
        int rows = messages.insert(new MessageWrite(
                receiverId, "DONATION_PROGRESS", title,
                "DONATION", donation.getDonationNo(), actor == null ? null : actor.id(), now));
        if (rows <= 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
    }

    private String yuan(int cents) {
        return String.format(java.util.Locale.ROOT, "%.2f", cents / 100.0);
    }

    private Project requireFundingProject(String projectNo) {
        String no = trimToNull(projectNo);
        if (no == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "项目编号必填");
        }
        Project project = projects.selectByProjectNo(no);
        if (project == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND.getCode(), "项目不存在：" + no);
        }
        if (project.getStatus() != ProjectStatus.FUNDING) {
            throw new BusinessException(ErrorCode.PROJECT_STATE_INVALID.getCode(), "项目当前不在募捐中");
        }
        return project;
    }

    private int requireAmount(Integer amount) {
        if (amount == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "捐赠金额必填");
        }
        if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new BusinessException(ErrorCode.DONATION_AMOUNT_INVALID,
                    "捐赠金额需在 1—10000000 分之间");
        }
        return amount;
    }

    private List<String> requireVouchers(List<String> voucherFileIds) {
        List<String> vouchers = optionalVouchers(voucherFileIds);
        if (vouchers == null || vouchers.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "请上传转账凭证");
        }
        return vouchers;
    }

    private List<String> optionalVouchers(List<String> voucherFileIds) {
        if (voucherFileIds == null || voucherFileIds.isEmpty()) {
            return null;
        }
        if (voucherFileIds.size() > MAX_VOUCHERS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "单笔捐赠最多上传 10 张凭证");
        }
        List<String> cleaned = voucherFileIds.stream()
                .map(DonationService::trimToNull)
                .filter(Objects::nonNull)
                .toList();
        if (cleaned.isEmpty()) {
            return null;
        }
        for (String voucher : cleaned) {
            if (voucher.length() > 64) {
                throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "凭证文件编号非法");
            }
        }
        return List.copyOf(cleaned);
    }

    private String optionalText(String value, int maxLength, String errorMessage) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        if (text.length() > maxLength) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, errorMessage);
        }
        return text;
    }

    private String displayName(CurrentUser actor) {
        SysUser user = users.selectById(actor.id());
        String nickname = user == null ? null : trimToNull(user.getNickname());
        if (nickname == null) {
            nickname = trimToNull(actor.nickname());
        }
        if (nickname == null) {
            return "爱心人士";
        }
        return nickname.length() <= 32 ? nickname : nickname.substring(0, 32);
    }

    private void requireActor(CurrentUser actor) {
        if (actor == null) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN);
        }
    }

    private Map<Long, Project> projectMap(List<Donation> records) {
        Set<Long> projectIds = records.stream()
                .map(Donation::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return projects.selectBatchIds(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (left, right) -> left));
    }

    private ProjectProgress progress(Project project) {
        int raised = project.getRaisedAmount() == null ? 0 : project.getRaisedAmount();
        int target = project.getTargetAmount() == null ? 0 : project.getTargetAmount();
        int percent = target <= 0 ? 0 : (int) Math.min(100L, (long) raised * 100 / target);
        return new ProjectProgress(project.getProjectNo(), raised, target, percent);
    }

    private void audit(CurrentUser actor, String action, String donationNo,
                       Object before, Map<String, Object> after) {
        auditLog.successAs(actor.id(), actor.primaryRole() == null ? "STUDENT" : actor.primaryRole(),
                action, "DONATION", donationNo, before, after);
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
