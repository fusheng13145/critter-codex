package com.just.critter.mapper;

import com.just.critter.domain.model.AdoptionExpiryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdoptionExpiryMapper {

    @Select("""
            SELECT id,
                   adoption_no AS adoptionNo,
                   user_id AS userId,
                   animal_id AS animalId,
                   animal_code AS animalCode,
                   end_date AS endDate
            FROM adoption
            WHERE status = 'ACTIVE'
              AND end_date = #{endDate}
            ORDER BY id ASC
            """)
    List<AdoptionExpiryRow> findExpiring(@Param("endDate") LocalDate endDate);

    @Select("""
            SELECT id,
                   adoption_no AS adoptionNo,
                   user_id AS userId,
                   animal_id AS animalId,
                   animal_code AS animalCode,
                   end_date AS endDate
            FROM adoption
            WHERE status = 'ACTIVE'
              AND end_date IS NOT NULL
              AND end_date < #{today}
            ORDER BY end_date ASC, id ASC
            """)
    List<AdoptionExpiryRow> findExpired(@Param("today") LocalDate today);

    @Update("""
            UPDATE adoption
            SET status = 'EXPIRED',
                updated_at = #{now}
            WHERE id = #{adoptionId}
              AND status = 'ACTIVE'
            """)
    int expire(@Param("adoptionId") long adoptionId,
               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE animal
            SET status = 'ON_CAMPUS',
                adopted = 0,
                status_changed_at = #{now},
                updated_at = #{now}
            WHERE id = #{animalId}
              AND status = 'ADOPTED'
            """)
    int releaseAnimal(@Param("animalId") long animalId,
                      @Param("now") LocalDateTime now);
}
