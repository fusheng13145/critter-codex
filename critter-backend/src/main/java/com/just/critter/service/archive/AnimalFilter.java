package com.just.critter.service.archive;

import com.just.critter.domain.enums.AnimalStatus;

import java.util.List;

/**
 * 档案列表五条件：关键字、状态集合、点位、绝育、认养。全为空时返回全部。
 */
public record AnimalFilter(
        String keyword,
        List<AnimalStatus> statuses,
        Long pointId,
        Boolean neutered,
        Boolean adopted) {
}
