package com.just.critter.service.archive;

import com.just.critter.domain.enums.GroupStatus;

/**
 * 群体建档结果。
 */
public record CreatedGroup(String groupCode, GroupStatus status) {
}
