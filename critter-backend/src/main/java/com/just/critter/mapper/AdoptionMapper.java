package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.AdoptionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AdoptionMapper extends BaseMapper<AdoptionEntity> {

    @Select("""
            SELECT adoption_no
            FROM adoption
            WHERE animal_id = #{animalId} AND status = 'ACTIVE'
            ORDER BY id DESC
            LIMIT 1
            """)
    String findActiveAdoptionNo(@Param("animalId") long animalId);

    @Select("""
            SELECT COUNT(*)
            FROM adoption
            WHERE user_id = #{userId} AND status = 'ACTIVE'
            """)
    long countActiveByUser(@Param("userId") long userId);

    @Select("""
            SELECT *
            FROM adoption
            WHERE adoption_no = #{adoptionNo}
            FOR UPDATE
            """)
    AdoptionEntity findByNoForUpdate(@Param("adoptionNo") String adoptionNo);

    @Select("""
            SELECT adoption_no
            FROM adoption
            WHERE animal_id = #{animalId}
              AND adoption_no <> #{excludedAdoptionNo}
              AND status IN ('PENDING_PAYMENT', 'ACTIVE')
            ORDER BY id DESC
            LIMIT 1
            """)
    String findBlockingAdoptionNo(@Param("animalId") long animalId,
                                  @Param("excludedAdoptionNo") String excludedAdoptionNo);

    @Update("""
            UPDATE adoption
            SET status = #{status},
                auditor_id = #{auditorId},
                audit_reason = #{reason},
                amount = #{amount},
                updated_at = #{auditedAt}
            WHERE adoption_no = #{adoptionNo}
              AND status = 'PENDING_AUDIT'
            """)
    int updateAudit(@Param("adoptionNo") String adoptionNo,
                    @Param("status") String status,
                    @Param("auditorId") long auditorId,
                    @Param("reason") String reason,
                    @Param("amount") int amount,
                    @Param("auditedAt") LocalDateTime auditedAt);
}
