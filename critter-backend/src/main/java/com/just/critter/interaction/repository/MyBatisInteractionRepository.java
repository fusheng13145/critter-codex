package com.just.critter.interaction.repository;

import com.just.critter.domain.model.CommentEntity;
import com.just.critter.domain.model.CommentRow;
import com.just.critter.domain.model.FollowEntity;
import com.just.critter.domain.model.FollowRow;
import com.just.critter.interaction.service.CommentRecord;
import com.just.critter.interaction.service.CommentStatus;
import com.just.critter.interaction.service.CommentWrite;
import com.just.critter.interaction.service.FollowRecord;
import com.just.critter.interaction.service.FollowWrite;
import com.just.critter.mapper.InteractionMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisInteractionRepository implements InteractionRepository {

    private final InteractionMapper mapper;

    public MyBatisInteractionRepository(InteractionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean animalExists(String animalCode) {
        return mapper.countAnimal(animalCode) > 0;
    }

    @Override
    public boolean projectExists(String projectKey) {
        return mapper.countProject(projectKey) > 0;
    }

    @Override
    public Optional<String> findUserName(long userId) {
        return Optional.ofNullable(mapper.findUserName(userId));
    }

    @Override
    public long insertComment(CommentWrite write) {
        CommentEntity entity = new CommentEntity();
        entity.setBizType(write.bizType());
        entity.setBizId(write.bizId());
        entity.setAuthorId(write.authorId());
        entity.setContent(write.content());
        entity.setStatus(write.status().name());
        entity.setCreatedAt(write.createdAt());
        entity.setUpdatedAt(write.createdAt());
        entity.setCreatedBy(write.createdBy());
        mapper.insertComment(entity);
        return entity.getId() == null ? 0L : entity.getId();
    }

    @Override
    public Optional<CommentRecord> findComment(long commentId) {
        CommentRow row = mapper.findComment(commentId);
        return row == null ? Optional.empty() : Optional.of(toRecord(row));
    }

    @Override
    public long countComments(String bizType, String bizId, Long viewerId, boolean admin) {
        return mapper.countComments(bizType, bizId, viewerId, admin);
    }

    @Override
    public List<CommentRecord> findComments(String bizType, String bizId, Long viewerId,
                                            boolean admin, long offset, int size) {
        return mapper.findComments(bizType, bizId, viewerId, admin, offset, size).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public int replyComment(long commentId, String replyContent, boolean replyVisible,
                            CommentStatus status, LocalDateTime updatedAt) {
        return mapper.replyComment(commentId, replyContent, replyVisible, status.name(), updatedAt);
    }

    @Override
    public Optional<FollowRecord> findFollow(long userId, String bizType, String bizId) {
        FollowRow row = mapper.findFollow(userId, bizType, bizId);
        return row == null ? Optional.empty() : Optional.of(toFollowRecord(row));
    }

    @Override
    public FollowRecord insertFollow(FollowWrite write) {
        FollowEntity entity = new FollowEntity();
        entity.setUserId(write.userId());
        entity.setBizType(write.bizType());
        entity.setBizId(write.bizId());
        entity.setCreatedAt(write.createdAt());
        entity.setUpdatedAt(write.createdAt());
        entity.setCreatedBy(write.createdBy());
        mapper.insertFollow(entity);
        return new FollowRecord(entity.getId() == null ? 0L : entity.getId(), write.userId(),
                write.bizType(), write.bizId(), write.createdAt());
    }

    @Override
    public int deleteFollow(long followId, long userId) {
        return mapper.deleteFollow(followId, userId);
    }

    private CommentRecord toRecord(CommentRow row) {
        return new CommentRecord(row.getId(), row.getBizType(), row.getBizId(), row.getAuthorId(),
                row.getAuthorName(), row.getContent(), CommentStatus.parse(row.getStatus()),
                row.getReplyContent(), Boolean.TRUE.equals(row.getReplyVisible()), row.getCreatedAt());
    }

    private FollowRecord toFollowRecord(FollowRow row) {
        return new FollowRecord(row.getId(), row.getUserId(), row.getBizType(), row.getBizId(),
                row.getCreatedAt());
    }
}
