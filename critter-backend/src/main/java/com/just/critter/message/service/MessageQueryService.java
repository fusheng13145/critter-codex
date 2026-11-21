package com.just.critter.message.service;

import com.just.critter.message.repository.MessageRepository;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class MessageQueryService {

    private static final int RETENTION_DAYS = 180;

    private final MessageRepository messages;
    private final Clock clock;

    public MessageQueryService(MessageRepository messages, Clock clock) {
        this.messages = messages;
        this.clock = clock;
    }

    public PageResult<MessageView> list(long receiverId, Boolean readFlag, String type, PageQuery query) {
        PageQuery safe = query.normalized();
        String normalizedType = normalizeType(type);
        LocalDateTime since = LocalDateTime.now(clock).minusDays(RETENTION_DAYS);

        long total = messages.count(receiverId, readFlag, normalizedType, since);
        if (total == 0) {
            return PageResult.of(java.util.List.of(), safe.getPage(), safe.getSize(), 0);
        }
        var list = messages.findPage(receiverId, readFlag, normalizedType,
                        since, safe.offset(), safe.getSize())
                .stream()
                .map(this::toView)
                .toList();
        return PageResult.of(list, safe.getPage(), safe.getSize(), total);
    }

    public MessageUnreadCount unread(long receiverId) {
        LocalDateTime since = LocalDateTime.now(clock).minusDays(RETENTION_DAYS);
        long adoptionExpiry = 0;
        long donationProgress = 0;
        long orderProgress = 0;
        long approvalResult = 0;
        long healthReminder = 0;
        for (MessageTypeCount item : messages.countUnreadByType(receiverId, since)) {
            if (MessageType.ADOPTION_EXPIRY.name().equals(item.type())) {
                adoptionExpiry = item.count();
            } else if (MessageType.DONATION_PROGRESS.name().equals(item.type())) {
                donationProgress = item.count();
            } else if (MessageType.ORDER_PROGRESS.name().equals(item.type())) {
                orderProgress = item.count();
            } else if (MessageType.APPROVAL_RESULT.name().equals(item.type())) {
                approvalResult = item.count();
            } else if (MessageType.HEALTH_REMINDER.name().equals(item.type())) {
                healthReminder = item.count();
            }
        }
        return new MessageUnreadCount(adoptionExpiry, donationProgress, orderProgress,
                approvalResult, healthReminder);
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return MessageType.parse(type).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", "type"));
        }
    }

    private MessageView toView(MessageRecord record) {
        return new MessageView(record.id(), record.type(), record.title(),
                record.bizType(), record.bizId(), record.readFlag(),
                record.readAt(), record.createdAt());
    }
}
