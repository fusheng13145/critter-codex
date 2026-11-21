package com.just.critter.service.fund;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.just.critter.domain.enums.DonationStatus;
import com.just.critter.domain.enums.ExpenseStatus;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.Donation;
import com.just.critter.domain.model.Expense;
import com.just.critter.domain.model.Project;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.DonationMapper;
import com.just.critter.mapper.ExpenseMapper;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资金台账（T3.5）：收入只取 VERIFIED 捐赠，支出只取 APPROVED 支出，
 * 两者按发生时间合成逐笔流水，合计恒等于收入减支出。
 */
@Service
public class LedgerService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final DonationMapper donations;
    private final ExpenseMapper expenses;
    private final ProjectMapper projects;
    private final AnimalMapper animals;

    public LedgerService(DonationMapper donations, ExpenseMapper expenses,
                         ProjectMapper projects, AnimalMapper animals) {
        this.donations = donations;
        this.expenses = expenses;
        this.projects = projects;
        this.animals = animals;
    }

    public LedgerResult ledger(String projectNo, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "开始日期不得晚于结束日期");
        }
        Project scopedProject = null;
        String no = trimToNull(projectNo);
        if (no != null) {
            scopedProject = projects.selectByProjectNo(no);
            if (scopedProject == null) {
                return empty();
            }
        }

        LambdaQueryWrapper<Donation> donationQuery = new LambdaQueryWrapper<Donation>()
                .eq(Donation::getStatus, DonationStatus.VERIFIED);
        LambdaQueryWrapper<Expense> expenseQuery = new LambdaQueryWrapper<Expense>()
                .eq(Expense::getStatus, ExpenseStatus.APPROVED);
        if (scopedProject != null) {
            donationQuery.eq(Donation::getProjectId, scopedProject.getId());
            expenseQuery.eq(Expense::getProjectId, scopedProject.getId());
        }
        if (from != null) {
            LocalDateTime start = from.atStartOfDay();
            donationQuery.ge(Donation::getVerifiedAt, start);
            expenseQuery.ge(Expense::getOccurredAt, start);
        }
        if (to != null) {
            LocalDateTime end = to.plusDays(1).atStartOfDay();
            donationQuery.lt(Donation::getVerifiedAt, end);
            expenseQuery.lt(Expense::getOccurredAt, end);
        }
        donationQuery.orderByDesc(Donation::getVerifiedAt).orderByDesc(Donation::getId);
        expenseQuery.orderByDesc(Expense::getOccurredAt).orderByDesc(Expense::getId);

        List<Donation> incomeRows = donations.selectList(donationQuery);
        List<Expense> expenseRows = expenses.selectList(expenseQuery);
        Map<Long, Project> projectById = projectsById(incomeRows, expenseRows);
        Map<Long, Animal> animalById = animalsById(expenseRows);

        List<LedgerEntry> entries = new ArrayList<>(incomeRows.size() + expenseRows.size());
        long income = 0;
        for (Donation donation : incomeRows) {
            income += donation.getAmount();
            Project project = projectById.get(donation.getProjectId());
            entries.add(new LedgerEntry("INCOME", donation.getDonationNo(),
                    project == null ? null : project.getProjectNo(),
                    project == null ? null : project.getName(),
                    donation.getDonorDisplay(),
                    Boolean.TRUE.equals(donation.getAnonymous()) ? "匿名捐赠" : "捐赠人",
                    null, donation.getAmount(), donation.getStatus().name(),
                    toOffset(donation.getVerifiedAt())));
        }

        long expense = 0;
        for (Expense row : expenseRows) {
            expense += row.getAmount();
            Project project = projectById.get(row.getProjectId());
            Animal animal = animalById.get(row.getAnimalId());
            entries.add(new LedgerEntry("EXPENSE", row.getExpenseNo(),
                    project == null ? null : project.getProjectNo(),
                    project == null ? null : project.getName(),
                    row.getPurpose(), row.getPayee(),
                    animal == null ? null : animal.getCode(),
                    row.getAmount(), row.getStatus().name(),
                    toOffset(row.getOccurredAt())));
        }
        entries.sort(Comparator.comparing(LedgerEntry::occurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new LedgerResult(new LedgerSummary(income, expense, income - expense,
                incomeRows.size(), expenseRows.size()), entries);
    }

    private LedgerResult empty() {
        return new LedgerResult(new LedgerSummary(0, 0, 0, 0, 0), List.of());
    }

    private Map<Long, Project> projectsById(List<Donation> donations, List<Expense> expenses) {
        Set<Long> ids = donations.stream().map(Donation::getProjectId)
                .collect(Collectors.toSet());
        ids.addAll(expenses.stream().map(Expense::getProjectId).toList());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return projects.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, Animal> animalsById(List<Expense> expenses) {
        Set<Long> ids = expenses.stream().map(Expense::getAnimalId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return animals.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Animal::getId, Function.identity(), (left, right) -> left));
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
