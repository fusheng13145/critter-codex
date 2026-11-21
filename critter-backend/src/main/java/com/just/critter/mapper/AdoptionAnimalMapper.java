package com.just.critter.mapper;

import com.just.critter.domain.model.Animal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdoptionAnimalMapper {

    @Select("""
            SELECT id, code, status
            FROM animal
            WHERE code = #{code} AND deleted = 0
            FOR UPDATE
            """)
    Animal lockByCode(@Param("code") String code);

    @Update("""
            UPDATE animal
            SET status = 'ADOPTED', adopted = 1, status_changed_at = NOW(), updated_at = NOW()
            WHERE id = #{animalId}
              AND deleted = 0
              AND status IN ('ON_CAMPUS', 'RELEASED')
            """)
    int markAdopted(@Param("animalId") long animalId);

    @Update("""
            UPDATE animal
            SET status = 'ON_CAMPUS', adopted = 0, status_changed_at = NOW(), updated_at = NOW()
            WHERE id = #{animalId}
              AND deleted = 0
              AND status = 'ADOPTED'
            """)
    int markOnCampus(@Param("animalId") long animalId);
}
