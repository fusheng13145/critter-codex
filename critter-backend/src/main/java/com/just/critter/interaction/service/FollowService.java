package com.just.critter.interaction.service;

import com.just.critter.interaction.repository.InteractionRepository;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Service
public class FollowService {

    private static final Set<String> BIZ_TYPES = Set.of("ANIMAL", "PROJECT");

    private final InteractionRepository repository;
    private final Clock clock;

    public FollowService(InteractionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public FollowView follow(FollowCommand command, long userId) {
        String bizType = requireBizType(command.bizType());
        String bizId = requireBizId(command.bizId());
        requireTarget(bizType, bizId);

        Optional<FollowRecord> existing = repository.findFollow(userId, bizType, bizId);
        if (existing.isPresent()) {
            FollowRecord record = existing.get();
            return new FollowView(record.id(), record.bizType(), record.bizId(),
                    record.createdAt(), false);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        FollowRecord created = repository.insertFollow(new FollowWrite(
                userId, bizType, bizId, now, userId));
        if (created == null) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return new FollowView(created.id(), created.bizType(), created.bizId(),
                created.createdAt(), true);
    }

    @Transactional
    public FollowRemoval unfollow(long followId, long userId) {
        int deleted = repository.deleteFollow(followId, userId);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return new FollowRemoval(followId, true);
    }

    private void requireTarget(String bizType, String bizId) {
        if ("ANIMAL".equals(bizType)) {
            if (!repository.animalExists(bizId)) {
                throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
            }
            return;
        }
        if (!repository.projectExists(bizId)) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
    }

    private String requireBizType(String value) {
        if (value == null || !BIZ_TYPES.contains(value)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID);
        }
        return value;
    }

    private String requireBizId(String value) {
        if (value == null || value.isBlank() || value.length() > 32) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return value.trim();
    }
}
