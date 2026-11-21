package com.just.critter.tnr.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.just.critter.care.service.ReferenceAnimal;
import com.just.critter.domain.model.AnimalRefRow;
import com.just.critter.domain.model.TnrNodeEntity;
import com.just.critter.domain.model.TnrNodeRow;
import com.just.critter.domain.model.TnrTaskEntity;
import com.just.critter.domain.model.TnrTaskRow;
import com.just.critter.mapper.TnrMapper;
import com.just.critter.tnr.service.TnrNodeRecord;
import com.just.critter.tnr.service.TnrNodeWrite;
import com.just.critter.tnr.service.TnrStage;
import com.just.critter.tnr.service.TnrTaskRecord;
import com.just.critter.tnr.service.TnrWrite;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisTnrRepository implements TnrRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final TnrMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisTnrRepository(TnrMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ReferenceAnimal> findAnimal(long animalId) {
        AnimalRefRow row = mapper.findAnimal(animalId);
        return row == null ? Optional.empty()
                : Optional.of(new ReferenceAnimal(row.getId(), row.getCode(), row.getName()));
    }

    @Override
    public Optional<String> findUserName(long userId) {
        return Optional.ofNullable(mapper.findUserName(userId));
    }

    @Override
    public boolean hasOpenTask(long animalId) {
        return mapper.countOpenTask(animalId) > 0;
    }

    @Override
    public int insertTask(TnrWrite write) {
        TnrTaskEntity entity = new TnrTaskEntity();
        entity.setTnrNo(write.tnrNo());
        entity.setAnimalId(write.animalId());
        entity.setStage(write.stage().name());
        entity.setHandlerId(write.handlerId());
        entity.setOccurredAt(write.occurredAt());
        entity.setFileIds(writeJson(write.fileIds()));
        entity.setNote(write.note());
        entity.setCreatedAt(write.createdAt());
        entity.setUpdatedAt(write.createdAt());
        entity.setCreatedBy(write.createdBy());
        return mapper.insert(entity);
    }

    @Override
    public Optional<TnrTaskRecord> findTask(String tnrNo) {
        TnrTaskRow row = mapper.findTask(tnrNo);
        return row == null ? Optional.empty() : Optional.of(toTaskRecord(row));
    }

    @Override
    public int updateTask(String tnrNo, TnrNodeWrite node) {
        TnrTaskEntity entity = new TnrTaskEntity();
        entity.setStage(node.stage().name());
        entity.setHandlerId(node.handlerId());
        entity.setOccurredAt(node.occurredAt());
        entity.setFileIds(writeJson(node.fileIds()));
        entity.setNote(node.note());
        entity.setUpdatedAt(node.createdAt());
        return mapper.updateTask(tnrNo, entity);
    }

    @Override
    public int insertNode(TnrNodeWrite node) {
        TnrNodeEntity entity = new TnrNodeEntity();
        entity.setTnrNo(node.tnrNo());
        entity.setStage(node.stage().name());
        entity.setHandlerId(node.handlerId());
        entity.setOccurredAt(node.occurredAt());
        entity.setFileIds(writeJson(node.fileIds()));
        entity.setNote(node.note());
        entity.setCreatedAt(node.createdAt());
        entity.setUpdatedAt(node.createdAt());
        entity.setCreatedBy(node.createdBy());
        return mapper.insertNode(entity);
    }

    @Override
    public List<TnrNodeRecord> findNodes(String tnrNo) {
        return mapper.findNodes(tnrNo).stream().map(this::toNodeRecord).toList();
    }

    @Override
    public List<TnrTaskRecord> findTasks(TnrStage stage, String animalCode, long offset, int size) {
        String stageCode = stage == null ? null : stage.name();
        return mapper.findTasks(stageCode, animalCode, offset, size).stream()
                .map(this::toTaskRecord)
                .toList();
    }

    @Override
    public long countTasks(TnrStage stage, String animalCode) {
        return mapper.countTasks(stage == null ? null : stage.name(), animalCode);
    }

    @Override
    public int markNeutered(long animalId, LocalDate neuteredAt, LocalDateTime now) {
        return mapper.markNeutered(animalId, neuteredAt, now);
    }

    private TnrTaskRecord toTaskRecord(TnrTaskRow row) {
        return new TnrTaskRecord(row.getTnrNo(), row.getAnimalId(), row.getAnimalCode(),
                row.getAnimalName(), stage(row.getStage()), row.getHandlerId(),
                row.getHandlerName(), row.getOccurredAt(), readFileIds(row.getFileIds()),
                row.getNote(), row.getCreatedAt());
    }

    private TnrNodeRecord toNodeRecord(TnrNodeRow row) {
        return new TnrNodeRecord(stage(row.getStage()), row.getHandlerId(), row.getHandlerName(),
                row.getOccurredAt(), row.getNote(), readFileIds(row.getFileIds()));
    }

    private TnrStage stage(String value) {
        return TnrStage.parse(value)
                .orElseThrow(() -> new IllegalStateException("非法 TNR 节点：" + value));
    }

    private String writeJson(List<String> fileIds) {
        try {
            return objectMapper.writeValueAsString(fileIds == null ? List.of() : fileIds);
        } catch (Exception ex) {
            throw new IllegalStateException("TNR 附件序列化失败", ex);
        }
    }

    private List<String> readFileIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }
}
