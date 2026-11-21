package com.just.critter.controller.fund;

import com.just.critter.service.fund.ProjectDraftCommand;

import java.time.LocalDate;
import java.util.List;

/**
 * 项目创建/修改请求体。金额单位为分；submit 省略时默认 false（仅存草稿）。
 */
public record CreateProjectRequest(
        String name,
        String purpose,
        String targetType,
        Integer targetAmount,
        LocalDate startDate,
        LocalDate endDate,
        List<BudgetItem> budget,
        String coverFileId,
        Boolean submit) {

    public ProjectDraftCommand toCommand() {
        List<ProjectDraftCommand.BudgetItem> items = budget == null ? null : budget.stream()
                .map(item -> new ProjectDraftCommand.BudgetItem(
                        item == null ? null : item.subject(),
                        item == null ? null : item.amount(),
                        item == null ? null : item.note()))
                .toList();
        return new ProjectDraftCommand(name, purpose, targetType, targetAmount,
                startDate, endDate, items, coverFileId, Boolean.TRUE.equals(submit));
    }

    public record BudgetItem(String subject, Integer amount, String note) {
    }
}
