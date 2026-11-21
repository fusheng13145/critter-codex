package com.just.critter.message.repository;

import com.just.critter.message.service.MessageRecord;
import com.just.critter.message.service.MessageTypeCount;
import com.just.critter.message.service.MessageWrite;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageRepository {

    long count(long receiverId, Boolean readFlag, String type, LocalDateTime since);

    List<MessageRecord> findPage(long receiverId,
                                 Boolean readFlag,
                                 String type,
                                 LocalDateTime since,
                                 long offset,
                                 int size);

    List<MessageTypeCount> countUnreadByType(long receiverId, LocalDateTime since);

    Optional<Boolean> findReadFlag(long id, long receiverId);

    int markRead(long id, long receiverId, LocalDateTime readAt);

    boolean exists(String type, String bizId, long receiverId);

    int insert(MessageWrite message);
}
