package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.AnimalRefRow;
import com.just.critter.domain.model.TnrNodeEntity;
import com.just.critter.domain.model.TnrNodeRow;
import com.just.critter.domain.model.TnrTaskEntity;
import com.just.critter.domain.model.TnrTaskRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TnrMapper extends BaseMapper<TnrTaskEntity> {

    @Select("""
            SELECT id, code, name
            FROM animal
            WHERE id = #{animalId} AND deleted = 0
            """)
    AnimalRefRow findAnimal(@Param("animalId") long animalId);

    @Select("""
            SELECT nickname
            FROM sys_user
            WHERE id = #{userId}
            """)
    String findUserName(@Param("userId") long userId);

    @Select("""
            SELECT COUNT(*)
            FROM tnr_task
            WHERE animal_id = #{animalId} AND stage <> 'OBSERVE'
            """)
    long countOpenTask(@Param("animalId") long animalId);

    @Select("""
            SELECT t.tnr_no AS tnrNo,
                   t.animal_id AS animalId,
                   a.code AS animalCode,
                   a.name AS animalName,
                   t.stage,
                   t.handler_id AS handlerId,
                   u.nickname AS handlerName,
                   t.occurred_at AS occurredAt,
                   t.file_ids AS fileIds,
                   t.note,
                   t.created_at AS createdAt
            FROM tnr_task t
            JOIN animal a ON a.id = t.animal_id
            LEFT JOIN sys_user u ON u.id = t.handler_id
            WHERE t.tnr_no = #{tnrNo}
            """)
    TnrTaskRow findTask(@Param("tnrNo") String tnrNo);

    @Update("""
            UPDATE tnr_task
            SET stage = #{entity.stage},
                handler_id = #{entity.handlerId},
                occurred_at = #{entity.occurredAt},
                file_ids = #{entity.fileIds},
                note = #{entity.note},
                updated_at = #{entity.updatedAt}
            WHERE tnr_no = #{tnrNo}
            """)
    int updateTask(@Param("tnrNo") String tnrNo, @Param("entity") TnrTaskEntity entity);

    @Insert("""
            INSERT INTO tnr_node
                (tnr_no, stage, handler_id, occurred_at, file_ids, note,
                 created_at, updated_at, created_by)
            VALUES
                (#{tnrNo}, #{stage}, #{handlerId}, #{occurredAt}, #{fileIds}, #{note},
                 #{createdAt}, #{updatedAt}, #{createdBy})
            """)
    int insertNode(TnrNodeEntity entity);

    @Select("""
            SELECT n.stage,
                   n.handler_id AS handlerId,
                   u.nickname AS handlerName,
                   n.occurred_at AS occurredAt,
                   n.note,
                   n.file_ids AS fileIds
            FROM tnr_node n
            LEFT JOIN sys_user u ON u.id = n.handler_id
            WHERE n.tnr_no = #{tnrNo}
            ORDER BY n.occurred_at ASC, n.id ASC
            """)
    List<TnrNodeRow> findNodes(@Param("tnrNo") String tnrNo);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM tnr_task t
            JOIN animal a ON a.id = t.animal_id
            WHERE 1 = 1
            <if test="stage != null">
              AND t.stage = #{stage}
            </if>
            <if test="animalCode != null">
              AND a.code = #{animalCode}
            </if>
            </script>
            """)
    long countTasks(@Param("stage") String stage, @Param("animalCode") String animalCode);

    @Select("""
            <script>
            SELECT t.tnr_no AS tnrNo,
                   t.animal_id AS animalId,
                   a.code AS animalCode,
                   a.name AS animalName,
                   t.stage,
                   t.handler_id AS handlerId,
                   u.nickname AS handlerName,
                   t.occurred_at AS occurredAt,
                   t.file_ids AS fileIds,
                   t.note,
                   t.created_at AS createdAt
            FROM tnr_task t
            JOIN animal a ON a.id = t.animal_id
            LEFT JOIN sys_user u ON u.id = t.handler_id
            WHERE 1 = 1
            <if test="stage != null">
              AND t.stage = #{stage}
            </if>
            <if test="animalCode != null">
              AND a.code = #{animalCode}
            </if>
            ORDER BY t.occurred_at DESC, t.tnr_no DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<TnrTaskRow> findTasks(@Param("stage") String stage,
                               @Param("animalCode") String animalCode,
                               @Param("offset") long offset,
                               @Param("size") int size);

    @Update("""
            UPDATE animal
            SET neutered = 1,
                neutered_at = #{neuteredAt},
                status_changed_at = #{now},
                updated_at = #{now}
            WHERE id = #{animalId}
            """)
    int markNeutered(@Param("animalId") long animalId,
                     @Param("neuteredAt") LocalDate neuteredAt,
                     @Param("now") LocalDateTime now);
}
