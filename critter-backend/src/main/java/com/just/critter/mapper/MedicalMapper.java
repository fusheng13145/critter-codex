package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.AnimalRefRow;
import com.just.critter.domain.model.ClinicRow;
import com.just.critter.domain.model.OrderRefRow;
import com.just.critter.domain.model.VisitDetailRow;
import com.just.critter.domain.model.VisitEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MedicalMapper extends BaseMapper<VisitEntity> {

    @Select("""
            SELECT id, clinic_name AS clinicName, contact, phone, address, price_note AS priceNote
            FROM base_clinic
            ORDER BY id ASC
            """)
    List<ClinicRow> findClinics();

    @Select("""
            SELECT id, code, name
            FROM animal
            WHERE id = #{animalId}
            """)
    AnimalRefRow findAnimal(@Param("animalId") long animalId);

    @Select("""
            SELECT id, clinic_name AS clinicName, contact, phone, address, price_note AS priceNote
            FROM base_clinic
            WHERE id = #{clinicId}
            """)
    ClinicRow findClinic(@Param("clinicId") long clinicId);

    @Select("""
            SELECT id, order_no AS orderNo, animal_id AS animalId
            FROM rescue_order
            WHERE id = #{orderId}
            """)
    OrderRefRow findOrder(@Param("orderId") long orderId);

    @Select("""
            SELECT v.visit_no AS visitNo,
                   v.animal_id AS animalId,
                   a.code AS animalCode,
                   a.name AS animalName,
                   v.order_id AS orderId,
                   o.order_no AS orderNo,
                   v.clinic_id AS clinicId,
                   c.clinic_name AS clinicName,
                   v.visited_at AS visitedAt,
                   v.complaint,
                   v.diagnosis,
                   v.treatment,
                   v.cost,
                   v.file_ids AS fileIds,
                   v.source_note AS sourceNote,
                   v.created_at AS createdAt
            FROM visit v
            JOIN animal a ON a.id = v.animal_id
            JOIN base_clinic c ON c.id = v.clinic_id
            LEFT JOIN rescue_order o ON o.id = v.order_id
            WHERE v.visit_no = #{visitNo}
            """)
    VisitDetailRow findVisit(@Param("visitNo") String visitNo);
}
