package com.just.critter.message.repository;

import com.just.critter.domain.model.MessageEntity;
import com.just.critter.domain.model.MessageRow;
import com.just.critter.message.service.MessageRecord;
import com.just.critter.message.service.MessageTypeCount;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.mapper.MessageMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisMessageRepository implements MessageRepository {

    private final MessageMapper mapper;

    public MyBatisMessageRepository(MessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long count(long receiverId, Boolean readFlag, String type, LocalDateTime since) {
        return mapper.count(receiverId, toFlag(readFlag), type, since);
    }

    @Override
    public List<MessageRecord> findPage(long receiverId,
                                        Boolean readFlag,
                                        String type,
                                        LocalDateTime since,
                                        long offset,
                                        int size) {
        return mapper.findPage(receiverId, toFlag(readFlag), type, since, offset, size)
                .stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<MessageTypeCount> countUnreadByType(long receiverId, LocalDateTime since) {
        return mapper.countUnreadByType(receiverId, since).stream()
                .map(row -> new MessageTypeCount(row.getType(), row.getCount() == null ? 0 : row.getCount()))
                .toList();
    }

    @Override
    public Optional<Boolean> findReadFlag(long id, long receiverId) {
        return Optional.ofNullable(mapper.findReadFlag(id, receiverId)).map(flag -> flag == 1);
    }

    @Override
    public int markRead(long id, long receiverId, LocalDateTime readAt) {
        return mapper.markRead(id, receiverId, readAt);
    }

    @Override
    public int insert(MessageWrite message) {
        MessageEntity entity = new MessageEntity();
        entity.setReceiverId(message.receiverId());
        entity.setType(message.type());
        entity.setTitle(message.title());
        entity.setBizType(message.bizType());
        entity.setBizId(message.bizId());
        entity.setReadFlag(false);
        entity.setCreatedAt(message.createdAt());
        entity.setUpdatedAt(message.createdAt());
        entity.setCreatedBy(message.createdBy());
        return mapper.insert(entity);
    }

    private MessageRecord toRecord(MessageRow row) {
        return new MessageRecord(
                row.getId() == null ? 0 : row.getId(),
                row.getType(),
                row.getTitle(),
                row.getBizType(),
                row.getBizId(),
                Boolean.TRUE.equals(row.getReadFlag()),
                row.getReadAt(),
                row.getCreatedAt());
    }

    private Integer toFlag(Boolean readFlag) {
        return readFlag == null ? null : readFlag ? 1 : 0;
    }
}
