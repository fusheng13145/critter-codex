package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.MessageEntity;
import com.just.critter.domain.model.MessageRow;
import com.just.critter.domain.model.MessageTypeCountRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM message
            WHERE receiver_id = #{receiverId}
              AND created_at >= #{since}
            <if test="readFlag != null">
              AND read_flag = #{readFlag}
            </if>
            <if test="type != null">
              AND type = #{type}
            </if>
            </script>
            """)
    long count(@Param("receiverId") long receiverId,
               @Param("readFlag") Integer readFlag,
               @Param("type") String type,
               @Param("since") LocalDateTime since);

    @Select("""
            <script>
            SELECT id,
                   receiver_id AS receiverId,
                   type,
                   title,
                   biz_type AS bizType,
                   biz_id AS bizId,
                   read_flag AS readFlag,
                   read_at AS readAt,
                   created_at AS createdAt
            FROM message
            WHERE receiver_id = #{receiverId}
              AND created_at >= #{since}
            <if test="readFlag != null">
              AND read_flag = #{readFlag}
            </if>
            <if test="type != null">
              AND type = #{type}
            </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<MessageRow> findPage(@Param("receiverId") long receiverId,
                              @Param("readFlag") Integer readFlag,
                              @Param("type") String type,
                              @Param("since") LocalDateTime since,
                              @Param("offset") long offset,
                              @Param("size") int size);

    @Select("""
            SELECT type, COUNT(*) AS count
            FROM message
            WHERE receiver_id = #{receiverId}
              AND read_flag = 0
              AND created_at >= #{since}
            GROUP BY type
            """)
    List<MessageTypeCountRow> countUnreadByType(@Param("receiverId") long receiverId,
                                                @Param("since") LocalDateTime since);

    @Select("""
            SELECT read_flag
            FROM message
            WHERE id = #{id} AND receiver_id = #{receiverId}
            """)
    Integer findReadFlag(@Param("id") long id,
                         @Param("receiverId") long receiverId);

    @Update("""
            UPDATE message
            SET read_flag = 1,
                read_at = #{readAt},
                updated_at = #{readAt}
            WHERE id = #{id}
              AND receiver_id = #{receiverId}
              AND read_flag = 0
            """)
    int markRead(@Param("id") long id,
                 @Param("receiverId") long receiverId,
                 @Param("readAt") LocalDateTime readAt);

    @Select("""
            SELECT COUNT(*)
            FROM message
            WHERE type = #{type}
              AND biz_id = #{bizId}
              AND receiver_id = #{receiverId}
            """)
    long countByBiz(@Param("type") String type,
                    @Param("bizId") String bizId,
                    @Param("receiverId") long receiverId);
}
