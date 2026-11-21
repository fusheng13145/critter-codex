package com.just.critter.tnr.repository;

import com.just.critter.care.service.ReferenceAnimal;
import com.just.critter.tnr.service.TnrNodeRecord;
import com.just.critter.tnr.service.TnrNodeWrite;
import com.just.critter.tnr.service.TnrStage;
import com.just.critter.tnr.service.TnrTaskRecord;
import com.just.critter.tnr.service.TnrWrite;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * TNR 任务仓储：任务头 + 节点流水。
 */
public interface TnrRepository {

    Optional<ReferenceAnimal> findAnimal(long animalId);

    Optional<String> findUserName(long userId);

    boolean hasOpenTask(long animalId);

    int insertTask(TnrWrite write);

    Optional<TnrTaskRecord> findTask(String tnrNo);

    int updateTask(String tnrNo, TnrNodeWrite node);

    int insertNode(TnrNodeWrite node);

    List<TnrNodeRecord> findNodes(String tnrNo);

    List<TnrTaskRecord> findTasks(TnrStage stage, String animalCode, long offset, int size);

    long countTasks(TnrStage stage, String animalCode);

    int markNeutered(long animalId, LocalDate neuteredAt, LocalDateTime now);
}
