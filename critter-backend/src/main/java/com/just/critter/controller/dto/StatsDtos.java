package com.just.critter.controller.dto;

import java.util.List;

/** 三个统计接口的出参。金额一律整数分，字段以 Amount 结尾（接口设计 §1）。 */
public final class StatsDtos {

    private StatsDtos() {
    }

    public record StatusSlice(String status, String label, long count) {
    }

    public record MonthPoint(String month, long newAnimals) {
    }

    public record BiologyResponse(
            long total,
            long deceased,
            List<StatusSlice> byStatus,
            long neuteredCount,
            double neuteredRate,
            long activeAdoptions,
            double adoptionCoverage,
            long groupCount,
            long groupSizeMin,
            long groupSizeMax,
            long occurrenceCount,
            List<MonthPoint> months) {
    }

    public record FundSummary(long raisedAmount, long spentAmount, long balanceAmount,
                              long donationCount, long expenseCount) {
    }

    public record FundMonth(String month, long raisedAmount, long spentAmount) {
    }

    public record FundProject(String no, String name, String status, long targetAmount,
                              long raisedAmount, long spentAmount, long balanceAmount, double progress) {
    }

    public record FundResponse(FundSummary summary, List<FundMonth> byMonth, List<FundProject> byProject,
                               List<FundProject> unsettled) {
    }

    public record ReportEfficiency(long total, long pending, long processing, long closed, long invalid) {
    }

    public record OrderEfficiency(long total, long closed, long timeout, long open,
                                  long avgAcceptMinutes, double closeRate) {
    }

    public record EfficiencyResponse(ReportEfficiency reports, OrderEfficiency orders,
                                     long pendingAdoptions, long pendingExpenses) {
    }
}
