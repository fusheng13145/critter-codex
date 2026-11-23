package com.just.critter.service.stats;

import com.just.critter.controller.dto.StatsDtos;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.mapper.StatsMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计（M09 展示部分）：只读 B/C 维护的表与视图，不建表、不写数（成员任务 §1.2 末段）。
 *
 * <p>口径三条（数据库设计 §7）：
 * 生物总览走 v_animal_overview 的口径（排除已离世与软删）；
 * 资金总览走 v_project_fund，且 (raised − spent) 必须等于 balance，不等就报 50001 而不是返回矛盾数字；
 * 效率统计按上报与工单的创建时间落在区间内计数。
 */
@Service
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StatsMapper statsMapper;

    public StatsService(StatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    public StatsDtos.BiologyResponse biology() {
        List<Map<String, Object>> rows = statsMapper.animalStatusCounts();
        long total = 0;
        long neutered = 0;
        Map<String, StatsDtos.StatusSlice> slices = new LinkedHashMap<>();
        for (AnimalStatus status : AnimalStatus.values()) {
            slices.put(status.name(), new StatsDtos.StatusSlice(status.name(), status.label(), 0));
        }
        for (Map<String, Object> row : rows) {
            String status = String.valueOf(row.get("status"));
            long count = asLong(row.get("total"));
            long fixed = asLong(row.get("neutered"));
            total += count;
            neutered += fixed;
            StatsDtos.StatusSlice existing = slices.get(status);
            String label = existing == null ? status : existing.label();
            slices.put(status, new StatsDtos.StatusSlice(status, label, count));
        }

        long alive = statsMapper.animalAliveTotal();
        long deceased = statsMapper.animalDeceasedTotal();
        // 认养数取 animal.adopted：这是 B 域维护的冗余字段，与列表筛选同源；
        // 不直接查 adoption 表，避免 C 域表尚未建时统计接口整体不可用。
        long adopted = statsMapper.adoptedAnimalTotal();

        List<StatsDtos.MonthPoint> months = buildMonths();

        return new StatsDtos.BiologyResponse(
                Math.max(total, alive),
                deceased,
                new ArrayList<>(slices.values()),
                neutered,
                rate(neutered, alive),
                adopted,
                rate(adopted, alive),
                statsMapper.groupTotal(),
                statsMapper.groupSizeMinTotal(),
                statsMapper.groupSizeMaxTotal(),
                statsMapper.occurrenceTotal(),
                months);
    }

    public StatsDtos.FundResponse fund(LocalDate from, LocalDate to) {
        Map<String, Object> totals = statsMapper.fundTotals();
        long raised = totals == null ? 0 : asLong(totals.get("raised"));
        long spent = totals == null ? 0 : asLong(totals.get("spent"));
        long balance = totals == null ? 0 : asLong(totals.get("balance"));

        if (raised - spent != balance) {
            // 数据库设计 §7 的硬规则：结余恒等式不成立说明有绕过复核的写入，拒绝返回矛盾数字
            log.error("资金口径自检失败 raised={} spent={} balance={}", raised, spent, balance);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "资金口径自检未通过，请检查台账");
        }

        List<StatsDtos.FundMonth> byMonth = statsMapper.fundByMonth(from, to).stream()
                .map(row -> new StatsDtos.FundMonth(String.valueOf(row.get("month")),
                        asLong(row.get("raised")), asLong(row.get("spent"))))
                .toList();

        List<Map<String, Object>> projectRows = statsMapper.fundByProject();
        List<StatsDtos.FundProject> byProject = projectRows.stream().map(row -> new StatsDtos.FundProject(
                String.valueOf(row.get("no")),
                String.valueOf(row.get("name")),
                String.valueOf(row.get("status")),
                asLong(row.get("target")),
                asLong(row.get("raised")),
                asLong(row.get("spent")),
                asLong(row.get("balance")),
                rate(asLong(row.get("raised")), asLong(row.get("target"))))).toList();

        List<StatsDtos.FundProject> unsettled = byProject.stream()
                .filter(item -> item.balanceAmount() > 0
                        && !"SETTLED".equals(item.status()) && !"ENDED".equals(item.status()))
                .toList();

        StatsDtos.FundSummary summary = new StatsDtos.FundSummary(raised, spent, balance,
                statsMapper.verifiedDonationCount(), statsMapper.approvedExpenseCount());

        return new StatsDtos.FundResponse(summary, byMonth, byProject, unsettled);
    }

    public StatsDtos.EfficiencyResponse efficiency(LocalDate from, LocalDate to) {
        Map<String, Object> reports = statsMapper.reportSummary(from, to);
        Map<String, Object> orders = statsMapper.orderSummary(from, to);

        long reportTotal = reports == null ? 0 : asLong(reports.get("total"));
        StatsDtos.ReportEfficiency reportEfficiency = new StatsDtos.ReportEfficiency(
                reportTotal,
                reports == null ? 0 : asLong(reports.get("pending")),
                reports == null ? 0 : asLong(reports.get("processing")),
                reports == null ? 0 : asLong(reports.get("closed")),
                reports == null ? 0 : asLong(reports.get("invalid")));

        long orderTotal = orders == null ? 0 : asLong(orders.get("total"));
        long orderClosed = orders == null ? 0 : asLong(orders.get("closed"));
        StatsDtos.OrderEfficiency orderEfficiency = new StatsDtos.OrderEfficiency(
                orderTotal,
                orderClosed,
                orders == null ? 0 : asLong(orders.get("timeout")),
                orders == null ? 0 : asLong(orders.get("open")),
                orders == null ? 0 : Math.round(asDouble(orders.get("avg_accept_minutes"))),
                rate(orderClosed, orderTotal));

        return new StatsDtos.EfficiencyResponse(reportEfficiency, orderEfficiency,
                safePendingAdoptions(), statsMapper.pendingExpenseTotal());
    }

    /**
     * adoption 表属 C 域，联调早期可能尚未建表。这里降级为 0 而不是让整个效率接口 500，
     * 等 C 建表后自动取到真实值。
     */
    private long safePendingAdoptions() {
        try {
            return statsMapper.pendingAdoptionTotal();
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            log.debug("adoption 表尚不存在，认养待办按 0 计：{}", ex.getMostSpecificCause().getMessage());
            return 0L;
        }
    }

    // ---------------- 内部 ----------------

    /** 近 6 个月（含当月）：没有数据的月份补 0，前端柱状图不会缺柱。 */
    private List<StatsDtos.MonthPoint> buildMonths() {
        Map<String, Long> bucket = new LinkedHashMap<>();
        YearMonth now = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            bucket.put(now.minusMonths(i).atDay(1).format(MONTH), 0L);
        }
        statsMapper.animalByMonth().forEach(row -> {
            String month = String.valueOf(row.get("month"));
            if (bucket.containsKey(month)) {
                bucket.put(month, asLong(row.get("total")));
            }
        });
        List<StatsDtos.MonthPoint> points = new ArrayList<>();
        bucket.forEach((month, count) -> points.add(new StatsDtos.MonthPoint(month, count)));
        return points;
    }

    private static double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return BigDecimal.valueOf(numerator * 100.0 / denominator)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.longValue();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static double asDouble(Object value) {
        if (value == null) {
            return 0d;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return 0d;
        }
    }
}
