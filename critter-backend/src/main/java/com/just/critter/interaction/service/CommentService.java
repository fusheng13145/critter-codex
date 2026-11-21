package com.just.critter.interaction.service;

import com.just.critter.interaction.repository.InteractionRepository;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CommentService {

    private static final Set<String> BIZ_TYPES = Set.of("ANIMAL", "PROJECT");
    private static final int MAX_CONTENT = 500;

    private final InteractionRepository repository;
    private final SensitiveWordFilter filter;
    private final AuditLogService audit;
    private final Clock clock;

    public CommentService(InteractionRepository repository, SensitiveWordFilter filter,
                          AuditLogService audit, Clock clock) {
        this.repository = repository;
        this.filter = filter;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CommentView create(CommentCreateCommand command, long authorId) {
        String bizType = requireBizType(command.bizType());
        String bizId = requireBizId(command.bizId());
        String content = requireContent(command.content());
        requireTarget(bizType, bizId);

        CommentStatus status = filter.isSensitive(content)
                ? CommentStatus.PENDING_AUDIT : CommentStatus.VISIBLE;
        LocalDateTime now = LocalDateTime.now(clock);
        long commentId = repository.insertComment(new CommentWrite(
                bizType, bizId, authorId, content, status, now, authorId));
        if (commentId <= 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        String authorName = repository.findUserName(authorId).orElse(null);
        return new CommentView(commentId, bizType, bizId, authorId, authorName, content,
                status, null, false, now);
    }

    public PageResult<CommentView> list(String bizType, String bizId, PageQuery query,
                                        Long viewerId, boolean admin) {
        String type = requireBizType(bizType);
        String id = requireBizId(bizId);
        PageQuery page = query == null ? new PageQuery() : query.normalized();
        long total = repository.countComments(type, id, viewerId, admin);
        List<CommentRecord> records = repository.findComments(
                type, id, viewerId, admin, page.offset(), page.getSize());
        List<CommentView> views = records.stream()
                .map(record -> toView(record, viewerId, admin))
                .toList();
        return PageResult.of(views, page.getPage(), page.getSize(), total);
    }

    @Transactional
    public CommentView reply(long commentId, CommentReplyCommand command) {
        String replyContent = requireContent(command.replyContent());
        Boolean visible = command.visible();
        if (visible == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        CommentRecord before = repository.findComment(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        int updated = repository.replyComment(commentId, replyContent, visible,
                CommentStatus.VISIBLE, now);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        CommentView after = new CommentView(before.id(), before.bizType(), before.bizId(),
                before.authorId(), before.authorName(), before.content(),
                CommentStatus.VISIBLE, replyContent, visible, before.createdAt());
        audit.success("COMMENT_REPLY", "COMMENT", String.valueOf(commentId), before, after);
        return after;
    }

    private CommentView toView(CommentRecord record, Long viewerId, boolean admin) {
        boolean replyVisible = record.replyVisible() || admin
                || (viewerId != null && viewerId == record.authorId());
        String reply = replyVisible ? record.replyContent() : null;
        return new CommentView(record.id(), record.bizType(), record.bizId(), record.authorId(),
                record.authorName(), record.content(), record.status(), reply,
                replyVisible && record.replyContent() != null, record.createdAt());
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

    private String requireContent(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_CONTENT) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return trimmed;
    }
}
