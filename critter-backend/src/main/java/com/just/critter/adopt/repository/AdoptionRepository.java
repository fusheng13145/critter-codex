package com.just.critter.adopt.repository;

import com.just.critter.adopt.domain.AdoptionRecord;

import java.util.Optional;

/**
 * 认养单持久化端口：动物行锁之后的生效关系查询与本单写入必须处于同一事务。
 */
public interface AdoptionRepository {

    Optional<String> findActiveAdoptionNo(long animalId);

    long countActiveByUser(long userId);

    void save(AdoptionRecord record);
}
