package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.CommentEntity;
import com.just.critter.domain.model.CommentRow;
import com.just.critter.domain.model.FollowEntity;
import com.just.critter.domain.model.FollowRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface InteractionMapper extends BaseMapper<CommentEntity> {

    @Select("""
            SELECT COUNT(*)
            FROM animal
            WHERE code = #{code} AND deleted = 0
            """)
    long countAnimal(@Param("code") String code);

    @Select("""
            SELECT COUNT(*)
            FROM project
            WHERE deleted = 0
              AND (project_no = #{projectKey} OR CAST(id AS CHAR) = #{projectKey})
            """)
    long countProject(@Param("projectKey") String projectKey);

    @Select("""
            SELECT nickname
            FROM sys_user
            WHERE id = #{userId}
            """)
    String findUserName(@Param("userId") long userId);

    @Insert("""
            INSERT INTO comment
                (biz_type, biz_id, author_id, content, status,
                 created_at, updated_at, created_by)
            VALUES
                (#{bizType}, #{bizId}, #{authorId}, #{content}, #{status},
                 #{createdAt}, #{updatedAt}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertComment(CommentEntity entity);

    @Select("""
            SELECT c.id,
                   c.biz_type AS bizType,
                   c.biz_id AS bizId,
                   c.author_id AS authorId,
                   u.nickname AS authorName,
                   c.content,
                   c.status,
                   c.reply_content AS replyContent,
                   c.reply_visible AS replyVisible,
                   c.created_at AS createdAt
            FROM comment c
            LEFT JOIN sys_user u ON u.id = c.author_id
            WHERE c.id = #{commentId}
            """)
    CommentRow findComment(@Param("commentId") long commentId);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM comment c
            WHERE c.biz_type = #{bizType} AND c.biz_id = #{bizId}
            <choose>
              <when test="admin"></when>
              <when test="viewerId != null">
                AND (c.status = 'VISIBLE' OR c.author_id = #{viewerId})
              </when>
              <otherwise>
                AND c.status = 'VISIBLE'
              </otherwise>
            </choose>
            </script>
            """)
    long countComments(@Param("bizType") String bizType, @Param("bizId") String bizId,
                       @Param("viewerId") Long viewerId, @Param("admin") boolean admin);

    @Select("""
            <script>
            SELECT c.id,
                   c.biz_type AS bizType,
                   c.biz_id AS bizId,
                   c.author_id AS authorId,
                   u.nickname AS authorName,
                   c.content,
                   c.status,
                   c.reply_content AS replyContent,
                   c.reply_visible AS replyVisible,
                   c.created_at AS createdAt
            FROM comment c
            LEFT JOIN sys_user u ON u.id = c.author_id
            WHERE c.biz_type = #{bizType} AND c.biz_id = #{bizId}
            <choose>
              <when test="admin"></when>
              <when test="viewerId != null">
                AND (c.status = 'VISIBLE' OR c.author_id = #{viewerId})
              </when>
              <otherwise>
                AND c.status = 'VISIBLE'
              </otherwise>
            </choose>
            ORDER BY c.created_at ASC, c.id ASC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<CommentRow> findComments(@Param("bizType") String bizType, @Param("bizId") String bizId,
                                  @Param("viewerId") Long viewerId, @Param("admin") boolean admin,
                                  @Param("offset") long offset, @Param("size") int size);

    @Update("""
            UPDATE comment
            SET reply_content = #{replyContent},
                reply_visible = #{replyVisible},
                status = #{status},
                updated_at = #{updatedAt}
            WHERE id = #{commentId}
            """)
    int replyComment(@Param("commentId") long commentId,
                     @Param("replyContent") String replyContent,
                     @Param("replyVisible") boolean replyVisible,
                     @Param("status") String status,
                     @Param("updatedAt") LocalDateTime updatedAt);

    @Select("""
            SELECT f.id,
                   f.user_id AS userId,
                   f.biz_type AS bizType,
                   f.biz_id AS bizId,
                   f.created_at AS createdAt
            FROM follow f
            WHERE f.user_id = #{userId}
              AND f.biz_type = #{bizType}
              AND f.biz_id = #{bizId}
            """)
    FollowRow findFollow(@Param("userId") long userId, @Param("bizType") String bizType,
                         @Param("bizId") String bizId);

    @Insert("""
            INSERT INTO follow
                (user_id, biz_type, biz_id, created_at, updated_at, created_by)
            VALUES
                (#{userId}, #{bizType}, #{bizId}, #{createdAt}, #{updatedAt}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertFollow(FollowEntity entity);

    @Delete("""
            DELETE FROM follow
            WHERE id = #{followId} AND user_id = #{userId}
            """)
    int deleteFollow(@Param("followId") long followId, @Param("userId") long userId);
}
