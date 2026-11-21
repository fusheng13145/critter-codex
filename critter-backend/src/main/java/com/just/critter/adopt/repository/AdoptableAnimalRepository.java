package com.just.critter.adopt.repository;

import com.just.critter.adopt.domain.AdoptableAnimal;

import java.util.Optional;

/**
 * 动物档案查询端口。lockByCode 必须在事务内对动物行加排他锁，
 * 使同一动物的并发申请串行判定。
 */
public interface AdoptableAnimalRepository {

    Optional<AdoptableAnimal> lockByCode(String code);

    int markAdopted(long animalId);

    int markOnCampus(long animalId);
}
