package com.just.critter.interaction.repository;

import com.just.critter.interaction.service.CommentRecord;
import com.just.critter.interaction.service.CommentStatus;
import com.just.critter.interaction.service.CommentWrite;
import com.just.critter.interaction.service.FollowRecord;
import com.just.critter.interaction.service.FollowWrite;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 留言与关注的数据访问口。可见性裁剪放在 SQL 条件里，避免把待审内容查出后再丢掉。
 */
public interface InteractionRepository {

    boolean animalExists(String animalCode);

    boolean projectExists(String projectKey);

    Optional<String> findUserName(long userId);

    long insertComment(CommentWrite write);

    Optional<CommentRecord> findComment(long commentId);

    long countComments(String bizType, String bizId, Long viewerId, boolean admin);

    List<CommentRecord> findComments(String bizType, String bizId, Long viewerId, boolean admin,
                                     long offset, int size);

    int replyComment(long commentId, String replyContent, boolean replyVisible,
                     CommentStatus status, LocalDateTime updatedAt);

    Optional<FollowRecord> findFollow(long userId, String bizType, String bizId);

    FollowRecord insertFollow(FollowWrite write);

    int deleteFollow(long followId, long userId);
}
