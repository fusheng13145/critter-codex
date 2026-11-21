package com.just.critter.volunteer.repository;

import com.just.critter.domain.model.TaskEntity;
import com.just.critter.domain.model.TaskRow;
import com.just.critter.domain.model.TaskSignupEntity;
import com.just.critter.domain.model.TaskSignupRow;
import com.just.critter.domain.model.VolunteerRow;
import com.just.critter.mapper.TaskMapper;
import com.just.critter.volunteer.service.SignupWrite;
import com.just.critter.volunteer.service.TaskRecord;
import com.just.critter.volunteer.service.TaskSignupRecord;
import com.just.critter.volunteer.service.TaskSignupView;
import com.just.critter.volunteer.service.TaskWrite;
import com.just.critter.volunteer.service.VolunteerRecord;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisTaskRepository implements TaskRepository {

    private final TaskMapper mapper;

    public MyBatisTaskRepository(TaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<VolunteerRecord> findVolunteer(long userId) {
        VolunteerRow row = mapper.findVolunteer(userId);
        return row == null ? Optional.empty()
                : Optional.of(new VolunteerRecord(row.getStatus(),
                        row.getTotalHours() == null ? BigDecimal.ZERO : row.getTotalHours()));
    }

    @Override
    public boolean pointExists(long pointId) {
        return mapper.countPoint(pointId) > 0;
    }

    @Override
    public long countTasks(String status, String type, long viewerId) {
        return mapper.countTasks(status, type);
    }

    @Override
    public List<TaskRecord> findTasks(String status, String type, long viewerId,
                                      long offset, int size) {
        return mapper.findTasks(status, type, viewerId, offset, size).stream()
                .map(this::toRecord)
                .toList();
    }

    @Override
    public TaskRecord insert(TaskWrite write) {
        TaskEntity entity = new TaskEntity();
        entity.setTaskNo(write.taskNo());
        entity.setTitle(write.title());
        entity.setType(write.type());
        entity.setDescription(write.description());
        entity.setPointId(write.pointId());
        entity.setStartAt(write.startAt());
        entity.setQuota(write.quota());
        entity.setDurationHours(write.durationHours());
        entity.setJoined(0);
        entity.setStatus(write.status());
        entity.setCreatedAt(write.createdAt());
        entity.setUpdatedAt(write.createdAt());
        entity.setCreatedBy(write.createdBy());
        mapper.insertTask(entity);
        return new TaskRecord(entity.getId() == null ? 0L : entity.getId(), write.taskNo(),
                write.title(), write.type(), write.description(), write.pointId(), null,
                write.startAt(), write.quota(), 0, write.durationHours(), write.status(),
                null, null, false);
    }

    @Override
    public Optional<TaskRecord> findTask(long taskId) {
        return Optional.ofNullable(mapper.findTask(taskId)).map(this::toRecord);
    }

    @Override
    public Optional<TaskRecord> findTaskForUpdate(long taskId) {
        return Optional.ofNullable(mapper.findTaskForUpdate(taskId)).map(this::toRecord);
    }

    @Override
    public Optional<TaskSignupRecord> findSignup(long taskId, long userId) {
        TaskSignupRow row = mapper.findSignup(taskId, userId);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new TaskSignupRecord(row.getId(), row.getTaskId(), row.getUserId(),
                row.getSignedAt(), row.getCheckinAt(), row.getCheckinPhoto(),
                row.getHours() == null ? BigDecimal.ZERO : row.getHours(),
                Boolean.TRUE.equals(row.getAbsent())));
    }

    @Override
    public long insertSignup(SignupWrite write) {
        TaskSignupEntity entity = new TaskSignupEntity();
        entity.setTaskId(write.taskId());
        entity.setUserId(write.userId());
        entity.setSignedAt(write.signedAt());
        entity.setAbsent(false);
        entity.setCreatedAt(write.signedAt());
        entity.setUpdatedAt(write.signedAt());
        entity.setCreatedBy(write.createdBy());
        mapper.insertSignup(entity);
        return entity.getId() == null ? 0L : entity.getId();
    }

    @Override
    public int incrementJoined(long taskId, LocalDateTime updatedAt) {
        return mapper.incrementJoined(taskId, updatedAt);
    }

    @Override
    public int markFull(long taskId, LocalDateTime updatedAt) {
        return mapper.markFull(taskId, updatedAt);
    }

    @Override
    public int checkin(long taskId, long userId, LocalDateTime checkinAt, String photo,
                       BigDecimal hours) {
        return mapper.checkin(taskId, userId, checkinAt, photo, hours);
    }

    @Override
    public int addVolunteerHours(long userId, BigDecimal hours, LocalDateTime updatedAt) {
        return mapper.addVolunteerHours(userId, hours, updatedAt);
    }

    @Override
    public long countMyTasks(long userId) {
        return mapper.countMyTasks(userId);
    }

    @Override
    public List<TaskSignupView> findMyTasks(long userId, long offset, int size) {
        return mapper.findMyTasks(userId, offset, size).stream()
                .map(row -> new TaskSignupView(row.getId(), row.getTaskId(), row.getTaskNo(),
                        row.getTitle(), row.getStartAt(), row.getSignedAt(), row.getCheckinAt(),
                        row.getHours() == null ? BigDecimal.ZERO : row.getHours(),
                        row.getTaskStatus(), row.getCheckinAt() != null,
                        Boolean.TRUE.equals(row.getAbsent())))
                .toList();
    }

    private TaskRecord toRecord(TaskRow row) {
        return new TaskRecord(row.getId(), row.getTaskNo(), row.getTitle(), row.getType(),
                row.getDescription(), row.getPointId(), row.getPointName(), row.getStartAt(),
                row.getQuota() == null ? 0 : row.getQuota(),
                row.getJoined() == null ? 0 : row.getJoined(),
                row.getDurationHours() == null ? BigDecimal.ZERO : row.getDurationHours(),
                row.getStatus(), row.getSignedAt(), row.getCheckinAt(),
                Boolean.TRUE.equals(row.getAbsent()));
    }
}
