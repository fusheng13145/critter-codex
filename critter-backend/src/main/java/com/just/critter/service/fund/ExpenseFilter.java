package com.just.critter.service.fund;

import com.just.critter.domain.enums.ExpenseStatus;

import java.util.List;

/**
 * 支出列表筛选。statuses 为空表示不过滤。
 */
public record ExpenseFilter(List<ExpenseStatus> statuses, String projectNo) {
}
