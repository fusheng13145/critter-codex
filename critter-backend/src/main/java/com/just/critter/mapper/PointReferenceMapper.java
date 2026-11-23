package com.just.critter.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 点位引用计数：用于点位维护页展示引用量，并在删除前做引用保护。
 */
@Mapper
public interface PointReferenceMapper {

    @Select("SELECT COUNT(*) FROM animal WHERE point_id = #{id} AND deleted = 0")
    long countAnimals(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM animal_group WHERE JSON_CONTAINS(point_ids, CAST(#{id} AS JSON))")
    long countGroups(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM sighting WHERE point_id = #{id}")
    long countSightings(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM occurrence WHERE point_id = #{id}")
    long countOccurrences(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM report WHERE point_id = #{id}")
    long countReports(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM patrol_record WHERE point_id = #{id}")
    long countPatrols(@Param("id") Long id);
}
