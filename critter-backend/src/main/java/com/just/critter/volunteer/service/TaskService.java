package com.just.critter.volunteer.service;

import com.just.critter.domain.archive.SequencePort;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.volunteer.repository.TaskRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class TaskService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> TYPES = Set.of(
            "FEED", "CLEAN", "RESCUE", "PUBLICITY", "TNR", "OTHER");
    private static final Set<String> STATUSES = Set.of("OPEN", "FULL", "CLOSED", "FINISHED");
    private static final Set<String> ACTIVE_VOLUNTEER = Set.of("ACTIVE");
    private static final int MAX_TITLE = 64;
    private static final int MAX_DESCRIPTION = 255;
    private static final int MAX_PHOTO = 255;

    private final TaskRepository repository;
    private final SequencePort sequences;
    private final AuditLogService audit;
    private final Clock clock;

    public TaskService(TaskRepository repository, SequencePort sequences,
                       AuditLogService audit, Clock clock) {
        this.repository = repository;
        this.sequences = sequences;
        this.audit = audit;
        this.clock = clock;
    }

    public PageResult<TaskView> list(String status, String type, PageQuery query, long viewerId) {
        String statusValue = parseStatus(status);
        String typeValue = parseType(type);
        PageQuery page = query == null ? new PageQuery() : query.normalized();
        long total = repository.countTasks(statusValue, typeValue, viewerId);
        List<TaskView> views = repository.findTasks(
                        statusValue, typeValue, viewerId, page.offset(), page.getSize()).stream()
                .map(record -> toView(record, record.signedAt() != null, record.signedAt(),
                        record.checkinAt()))
                .toList();
        return PageResult.of(views, page.getPage(), page.getSize(), total);
    }

    @Transactional
    public TaskView create(TaskCommand command, long actorId) {
        String title = requireText(command.title(), MAX_TITLE);
        String type = parseType(command.type());
        if (type == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        if (command.startAt() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        int quota = command.quota() == null ? 0 : command.quota();
        if (quota < 1 || quota > 100) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        BigDecimal duration = command.durationHours();
        if (duration == null || duration.compareTo(new BigDecimal("0.5")) < 0
                || duration.compareTo(new BigDecimal("12.0")) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        if (command.pointId() != null && !repository.pointExists(command.pointId())) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        String description = optionalText(command.description(), MAX_DESCRIPTION);

        LocalDate today = LocalDate.now(clock);
        int sequence = sequences.next("TASK" + DAY.format(today), today.getYear());
        String taskNo = "TASK" + DAY.format(today) + String.format("%03d", sequence);
        LocalDateTime now = LocalDateTime.now(clock);
        TaskRecord created = repository.insert(new TaskWrite(taskNo, title, type, description,
                command.pointId(), command.startAt(), quota, duration, "OPEN", now, actorId));
        if (created.id() <= 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        audit.success("TASK_CREATE", "TASK", String.valueOf(created.id()), null, created);
        return toView(created, false, null, null);
    }

    @Transactional
    public TaskView join(long taskId, long userId) {
        requireActiveVolunteer(userId);
        TaskRecord task = repository.findTaskForUpdate(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (!"OPEN".equals(task.status()) || task.joined() >= task.quota()) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        if (repository.findSignup(taskId, userId).isPresent()) {
            throw new BusinessException(ErrorCode.ACTIVE_RELATION_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            long signupId = repository.insertSignup(new SignupWrite(taskId, userId, now, userId));
            if (signupId <= 0) {
                throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.ACTIVE_RELATION_EXISTS);
        }
        if (repository.incrementJoined(taskId, now) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        int joined = task.joined() + 1;
        String status = "OPEN";
        if (joined >= task.quota()) {
            repository.markFull(taskId, now);
            status = "FULL";
        }
        return new TaskView(task.id(), task.taskNo(), task.title(), task.type(),
                task.description(), task.pointId(), task.pointName(), task.startAt(),
                task.quota(), joined, task.durationHours(), status, true, now, null);
    }

    @Transactional
    public TaskCheckinView checkin(long taskId, TaskCheckinCommand command, long userId) {
        String photo = requireText(command.photo(), MAX_PHOTO);
        VolunteerRecord volunteer = requireActiveVolunteer(userId);
        TaskRecord task = repository.findTask(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        TaskSignupRecord signup = repository.findSignup(taskId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (signup.checkinAt() != null) {
            throw new BusinessException(ErrorCode.ACTIVE_RELATION_EXISTS);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (repository.checkin(taskId, userId, now, photo, task.durationHours()) == 0) {
            throw new BusinessException(ErrorCode.ACTIVE_RELATION_EXISTS);
        }
        repository.addVolunteerHours(userId, task.durationHours(), now);
        BigDecimal total = volunteer.totalHours().add(task.durationHours());
        TaskCheckinView view = new TaskCheckinView(task.id(), task.taskNo(), task.title(),
                now, task.durationHours(), total);
        audit.success("TASK_CHECKIN", "TASK", String.valueOf(taskId), signup, view);
        return view;
    }

    public VolunteerTaskView mine(long userId, int pageNo, int size) {
        VolunteerRecord volunteer = repository.findVolunteer(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_MISMATCH));
        PageQuery page = new PageQuery(pageNo, size).normalized();
        long total = repository.countMyTasks(userId);
        List<TaskSignupView> tasks = repository.findMyTasks(
                userId, page.offset(), page.getSize());
        return new VolunteerTaskView(volunteer.status(), volunteer.totalHours(), total, tasks);
    }

    private VolunteerRecord requireActiveVolunteer(long userId) {
        VolunteerRecord volunteer = repository.findVolunteer(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_MISMATCH));
        if (!ACTIVE_VOLUNTEER.contains(volunteer.status())) {
            throw new BusinessException(ErrorCode.ROLE_MISMATCH);
        }
        return volunteer;
    }

    private String parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID);
        }
        return normalized;
    }

    private String parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (!TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID);
        }
        return normalized;
    }

    private String requireText(String value, int max) {
        if (value == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > max) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return trimmed;
    }

    private String optionalText(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        return trimmed;
    }

    private TaskView toView(TaskRecord record, boolean joinedByMe,
                            LocalDateTime signedAt, LocalDateTime checkinAt) {
        return new TaskView(record.id(), record.taskNo(), record.title(), record.type(),
                record.description(), record.pointId(), record.pointName(), record.startAt(),
                record.quota(), record.joined(), record.durationHours(), record.status(),
                joinedByMe, signedAt, checkinAt);
    }
}
