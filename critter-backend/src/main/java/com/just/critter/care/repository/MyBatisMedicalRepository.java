package com.just.critter.care.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.just.critter.care.service.ClinicView;
import com.just.critter.care.service.ReferenceAnimal;
import com.just.critter.care.service.ReferenceClinic;
import com.just.critter.care.service.ReferenceOrder;
import com.just.critter.care.service.VisitDetail;
import com.just.critter.care.service.VisitWrite;
import com.just.critter.domain.model.AnimalRefRow;
import com.just.critter.domain.model.ClinicRow;
import com.just.critter.domain.model.OrderRefRow;
import com.just.critter.domain.model.VisitDetailRow;
import com.just.critter.domain.model.VisitEntity;
import com.just.critter.mapper.MedicalMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisMedicalRepository implements MedicalRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final MedicalMapper mapper;
    private final ObjectMapper objectMapper;

    public MyBatisMedicalRepository(MedicalMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ClinicView> findClinics() {
        return mapper.findClinics().stream().map(this::toClinicView).toList();
    }

    @Override
    public Optional<ReferenceAnimal> findAnimal(long animalId) {
        AnimalRefRow row = mapper.findAnimal(animalId);
        return row == null ? Optional.empty()
                : Optional.of(new ReferenceAnimal(row.getId(), row.getCode(), row.getName()));
    }

    @Override
    public Optional<ReferenceAnimal> findAnimalByCode(String animalCode) {
        AnimalRefRow row = mapper.findAnimalByCode(animalCode);
        return row == null ? Optional.empty()
                : Optional.of(new ReferenceAnimal(row.getId(), row.getCode(), row.getName()));
    }

    @Override
    public Optional<ReferenceClinic> findClinic(long clinicId) {
        ClinicRow row = mapper.findClinic(clinicId);
        return row == null ? Optional.empty()
                : Optional.of(new ReferenceClinic(row.getId(), row.getClinicName(), row.getContact(),
                row.getPhone(), row.getAddress(), row.getPriceNote()));
    }

    @Override
    public Optional<ReferenceOrder> findOrder(long orderId) {
        OrderRefRow row = mapper.findOrder(orderId);
        return row == null ? Optional.empty()
                : Optional.of(new ReferenceOrder(row.getId(), row.getOrderNo(), row.getAnimalId()));
    }

    @Override
    public int insertVisit(VisitWrite write) {
        VisitEntity entity = new VisitEntity();
        entity.setVisitNo(write.visitNo());
        entity.setAnimalId(write.animalId());
        entity.setOrderId(write.orderId());
        entity.setClinicId(write.clinicId());
        entity.setVisitedAt(write.visitedAt());
        entity.setComplaint(write.complaint());
        entity.setDiagnosis(write.diagnosis());
        entity.setTreatment(write.treatment());
        entity.setCost(write.cost());
        entity.setFileIds(writeJson(write.fileIds()));
        entity.setSourceNote(write.sourceNote());
        entity.setCreatedAt(write.createdAt());
        entity.setUpdatedAt(write.createdAt());
        entity.setCreatedBy(write.createdBy());
        return mapper.insert(entity);
    }

    @Override
    public Optional<VisitDetail> findVisit(String visitNo) {
        VisitDetailRow row = mapper.findVisit(visitNo);
        return row == null ? Optional.empty() : Optional.of(toDetail(row));
    }

    @Override
    public List<VisitDetail> findVisitsByAnimal(String animalCode) {
        return mapper.findVisitsByAnimal(animalCode).stream()
                .map(this::toDetail)
                .toList();
    }

    private VisitDetail toDetail(VisitDetailRow row) {
        return new VisitDetail(
                row.getVisitNo(), row.getAnimalId(), row.getAnimalCode(), row.getAnimalName(),
                row.getOrderId(), row.getOrderNo(), row.getClinicId(), row.getClinicName(),
                row.getVisitedAt(), row.getComplaint(), row.getDiagnosis(), row.getTreatment(),
                row.getCost() == null ? 0 : row.getCost(), readFileIds(row.getFileIds()),
                row.getSourceNote(), row.getCreatedAt());
    }

    private ClinicView toClinicView(ClinicRow row) {
        return new ClinicView(row.getId(), row.getClinicName(), row.getContact(),
                row.getPhone(), row.getAddress(), row.getPriceNote());
    }

    private String writeJson(List<String> fileIds) {
        try {
            return objectMapper.writeValueAsString(fileIds == null ? List.of() : fileIds);
        } catch (Exception ex) {
            throw new IllegalStateException("就诊影像序列化失败", ex);
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
