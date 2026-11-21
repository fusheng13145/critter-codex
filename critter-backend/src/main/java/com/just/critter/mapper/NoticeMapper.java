package com.just.critter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.just.critter.domain.model.NoticeEntity;
import com.just.critter.domain.model.NoticeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NoticeMapper extends BaseMapper<NoticeEntity> {

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM notice
            WHERE published_at IS NOT NULL
            <if test="category != null">
              AND category = #{category}
            </if>
            </script>
            """)
    long count(@Param("category") String category);

    @Select("""
            <script>
            SELECT id,
                   notice_no AS noticeNo,
                   category,
                   title,
                   pinned,
                   published_at AS publishedAt
            FROM notice
            WHERE published_at IS NOT NULL
            <if test="category != null">
              AND category = #{category}
            </if>
            ORDER BY pinned DESC, published_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<NoticeRow> findPage(@Param("category") String category,
                             @Param("offset") long offset,
                             @Param("size") int size);

    @Select("""
            SELECT n.id,
                   n.notice_no AS noticeNo,
                   n.category,
                   n.title,
                   n.content,
                   n.author_id AS authorId,
                   u.nickname AS authorName,
                   n.pinned,
                   n.published_at AS publishedAt,
                   n.created_at AS createdAt
            FROM notice n
            LEFT JOIN sys_user u ON u.id = n.author_id
            WHERE n.id = #{id}
              AND n.published_at IS NOT NULL
            """)
    NoticeRow findById(@Param("id") long id);
}
