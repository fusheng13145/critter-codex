package com.just.critter.service.fund;

import com.just.critter.domain.enums.DonationStatus;

import java.util.Set;

/**
 * 捐赠列表筛选：状态多值逗号解析后传入，projectNo 为空表示不限。
 */
public record DonationFilter(Set<DonationStatus> statuses, String projectNo) {
}
