package com.just.critter.message.service;

import com.just.critter.message.repository.MessageRepository;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class MessageReadService {

    private final MessageRepository messages;
    private final Clock clock;

    public MessageReadService(MessageRepository messages, Clock clock) {
        this.messages = messages;
        this.clock = clock;
    }

    @Transactional
    public MessageReadResult markRead(long receiverId, List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "ids"));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        int success = 0;
        List<MessageReadFailure> failed = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            var readFlag = messages.findReadFlag(id, receiverId);
            if (readFlag.isEmpty()) {
                failed.add(new MessageReadFailure(String.valueOf(id), 30410, "记录不存在"));
                continue;
            }
            if (readFlag.get()) {
                success++;
                continue;
            }
            if (messages.markRead(id, receiverId, now) == 1) {
                success++;
            } else {
                failed.add(new MessageReadFailure(String.valueOf(id), 50002, "数据已被他人修改，请刷新后重试"));
            }
        }
        return new MessageReadResult(success, List.copyOf(failed));
    }
}
