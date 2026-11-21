package com.just.critter.volunteer.repository;

import com.just.critter.volunteer.service.SignupWrite;
import com.just.critter.volunteer.service.TaskRecord;
import com.just.critter.volunteer.service.TaskSignupRecord;
import com.just.critter.volunteer.service.TaskSignupView;
import com.just.critter.volunteer.service.TaskWrite;
import com.just.critter.volunteer.service.VolunteerRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {

    Optional<VolunteerRecord> findVolunteer(long userId);

    boolean pointExists(long pointId);

    long countTasks(String status, String type, long viewerId);

    List<TaskRecord> findTasks(String status, String type, long viewerId, long offset, int size);

    TaskRecord insert(TaskWrite write);

    Optional<TaskRecord> findTask(long taskId);

    Optional<TaskRecord> findTaskForUpdate(long taskId);

    Optional<TaskSignupRecord> findSignup(long taskId, long userId);

    long insertSignup(SignupWrite write);

    int incrementJoined(long taskId, LocalDateTime updatedAt);

    int markFull(long taskId, LocalDateTime updatedAt);

    int checkin(long taskId, long userId, LocalDateTime checkinAt, String photo,
                BigDecimal hours);

    int addVolunteerHours(long userId, BigDecimal hours, LocalDateTime updatedAt);

    long countMyTasks(long userId);

    List<TaskSignupView> findMyTasks(long userId, long offset, int size);
}
