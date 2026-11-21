package com.just.critter.care.repository;

import com.just.critter.care.service.ClinicView;
import com.just.critter.care.service.ReferenceAnimal;
import com.just.critter.care.service.ReferenceClinic;
import com.just.critter.care.service.ReferenceOrder;
import com.just.critter.care.service.VisitDetail;
import com.just.critter.care.service.VisitWrite;

import java.util.List;
import java.util.Optional;

/**
 * 医疗协作仓储：合作医院、登记就诊与按编号查询。
 */
public interface MedicalRepository {

    List<ClinicView> findClinics();

    Optional<ReferenceAnimal> findAnimal(long animalId);

    Optional<ReferenceAnimal> findAnimalByCode(String animalCode);

    Optional<ReferenceClinic> findClinic(long clinicId);

    Optional<ReferenceOrder> findOrder(long orderId);

    int insertVisit(VisitWrite write);

    Optional<VisitDetail> findVisit(String visitNo);

    List<VisitDetail> findVisitsByAnimal(String animalCode);
}
