package com.just.critter.service.archive;

import java.util.List;

/**
 * 群体建档命令。规模只收区间，sizeBasis 必填，避免出现"大概一群"的无依据数字。
 */
public record CreateGroupCommand(
        Long speciesId,
        String features,
        List<Long> pointIds,
        int minSize,
        int maxSize,
        String sizeBasis) {
}
