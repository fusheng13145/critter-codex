package com.just.critter.mapper;

import com.just.critter.domain.model.TaskEntity;
import com.just.critter.domain.model.TaskRow;
import com.just.critter.domain.model.TaskSignupEntity;
import com.just.critter.domain.model.TaskSignupRow;
import com.just.critter.domain.model.VolunteerRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TaskMapper {

    @Select("""
            SELECT status, total_hours AS totalHours
            FROM volunteer
            WHERE user_id = #{userId}
            """)
    VolunteerRow findVolunteer(@Param("userId") long userId);

    @Select("""
            SELECT COUNT(*) FROM base_point WHERE id = #{pointId}
            """)
    long countPoint(@Param("pointId") long pointId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM task t
            WHERE 1 = 1
            <if test="status != null">AND t.status = #{status}</if>
            <if test="type != null">AND t.type = #{type}</if>
            </script>
            """)
    long countTasks(@Param("status") String status, @Param("type") String type);

    @Select("""
            <script>
            SELECT t.id,
                   t.task_no AS taskNo,
                   t.title,
                   t.type,
                   t.description,
                   t.point_id AS pointId,
                   p.point_name AS pointName,
                   t.start_at AS startAt,
                   t.quota,
                   t.joined,
                   t.duration_hours AS durationHours,
                   t.status,
                   s.signed_at AS signedAt,
                   s.checkin_at AS checkinAt,
                   s.absent
            FROM task t
            LEFT JOIN base_point p ON p.id = t.point_id
            LEFT JOIN task_signup s ON s.task_id = t.id AND s.user_id = #{viewerId}
            WHERE 1 = 1
            <if test="status != null">AND t.status = #{status}</if>
            <if test="type != null">AND t.type = #{type}</if>
            ORDER BY t.start_at ASC, t.id ASC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<TaskRow> findTasks(@Param("status") String status, @Param("type") String type,
                            @Param("viewerId") long viewerId, @Param("offset") long offset,
                            @Param("size") int size);

    @Insert("""
            INSERT INTO task
                (task_no, title, type, description, point_id, start_at, quota,
                 duration_hours, joined, status, created_at, updated_at, created_by)
            VALUES
                (#{taskNo}, #{title}, #{type}, #{description}, #{pointId}, #{startAt}, #{quota},
                 #{durationHours}, #{joined}, #{status}, #{createdAt}, #{updatedAt}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTask(TaskEntity entity);

    @Select("""
            SELECT t.id,
                   t.task_no AS taskNo,
                   t.title,
                   t.type,
                   t.description,
                   t.point_id AS pointId,
                   p.point_name AS pointName,
                   t.start_at AS startAt,
                   t.quota,
                   t.joined,
                   t.duration_hours AS durationHours,
                   t.status
            FROM task t
            LEFT JOIN base_point p ON p.id = t.point_id
            WHERE t.id = #{taskId}
            """)
    TaskRow findTask(@Param("taskId") long taskId);

    @Select("""
            SELECT t.id,
                   t.task_no AS taskNo,
                   t.title,
                   t.type,
                   t.description,
                   t.point_id AS pointId,
                   t.start_at AS startAt,
                   t.quota,
                   t.joined,
                   t.duration_hours AS durationHours,
                   t.status
            FROM task t
            WHERE t.id = #{taskId}
            FOR UPDATE
            """)
    TaskRow findTaskForUpdate(@Param("taskId") long taskId);

    @Select("""
            SELECT s.id,
                   s.task_id AS taskId,
                   s.user_id AS userId,
                   s.signed_at AS signedAt,
                   s.checkin_at AS checkinAt,
                   s.checkin_photo AS checkinPhoto,
                   s.hours,
                   s.absent
            FROM task_signup s
            WHERE s.task_id = #{taskId} AND s.user_id = #{userId}
            """)
    TaskSignupRow findSignup(@Param("taskId") long taskId, @Param("userId") long userId);

    @Insert("""
            INSERT INTO task_signup
                (task_id, user_id, signed_at, absent, created_at, updated_at, created_by)
            VALUES
                (#{taskId}, #{userId}, #{signedAt}, 0, #{signedAt}, #{signedAt}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSignup(TaskSignupEntity entity);

    @Update("""
            UPDATE task
            SET joined = joined + 1, updated_at = #{updatedAt}
            WHERE id = #{taskId}
            """)
    int incrementJoined(@Param("taskId") long taskId, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE task
            SET status = 'FULL', updated_at = #{updatedAt}
            WHERE id = #{taskId} AND joined >= quota
            """)
    int markFull(@Param("taskId") long taskId, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("""
            UPDATE task_signup
            SET checkin_at = #{checkinAt},
                checkin_photo = #{photo},
                hours = #{hours},
                updated_at = #{checkinAt}
            WHERE task_id = #{taskId} AND user_id = #{userId} AND checkin_at IS NULL
            """)
    int checkin(@Param("taskId") long taskId, @Param("userId") long userId,
                @Param("checkinAt") LocalDateTime checkinAt, @Param("photo") String photo,
                @Param("hours") BigDecimal hours);

    @Update("""
            UPDATE volunteer
            SET total_hours = total_hours + #{hours}, updated_at = #{updatedAt}
            WHERE user_id = #{userId}
            """)
    int addVolunteerHours(@Param("userId") long userId, @Param("hours") BigDecimal hours,
                          @Param("updatedAt") LocalDateTime updatedAt);

    @Select("""
            SELECT COUNT(*)
            FROM task_signup
            WHERE user_id = #{userId}
            """)
    long countMyTasks(@Param("userId") long userId);

    @Select("""
            SELECT s.id,
                   t.id AS taskId,
                   t.task_no AS taskNo,
                   t.title,
                   t.start_at AS startAt,
                   s.signed_at AS signedAt,
                   s.checkin_at AS checkinAt,
                   s.hours,
                   t.status AS taskStatus,
                   s.absent
            FROM task_signup s
            JOIN task t ON t.id = s.task_id
            WHERE s.user_id = #{userId}
            ORDER BY t.start_at DESC, s.id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<TaskSignupRow> findMyTasks(@Param("userId") long userId,
                                    @Param("offset") long offset, @Param("size") int size);
}
