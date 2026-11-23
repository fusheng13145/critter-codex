package com.just.critter.volunteer.shift;

import com.just.critter.domain.archive.SequencePort;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ShiftService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<String> APPEAL_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");
    private static final Set<String> DECISIONS = Set.of("APPROVE", "REJECT");

    private final ShiftRepository shifts;
    private final SequencePort sequences;
    private final MessageRepository messages;
    private final Clock clock;

    public ShiftService(ShiftRepository shifts, SequencePort sequences,
                        MessageRepository messages, Clock clock) {
        this.shifts = shifts;
        this.sequences = sequences;
        this.messages = messages;
        this.clock = clock;
    }

    public List<ShiftView> list(LocalDate from, LocalDate to, long userId, boolean mine) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from == null ? today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) : from;
        LocalDate end = to == null ? start.plusDays(6) : to;
        if (end.isBefore(start) || end.isAfter(start.plusDays(31))) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "dateRange"));
        }
        return mine ? shifts.findMySignups(userId, start, end) : shifts.findShifts(start, end, userId);
    }

    public SuspensionView me(long userId) {
        ShiftRepository.VolunteerState state = shifts.findVolunteer(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_MISMATCH));
        boolean pending = shifts.hasPendingAppeal(userId);
        return new SuspensionView(state.status(), state.absenceStreak(), state.suspendedAt(),
                "SUSPENDED".equals(state.status()) && !pending, pending);
    }

    @Transactional
    public ShiftView create(ShiftCreateCommand command, long operatorId) {
        if (command == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "body"));
        }
        String title = requireText(command.title(), 64, "title");
        if (command.shiftDate() == null || command.startTime() == null || command.endTime() == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "time"));
        }
        if (!command.endTime().isAfter(command.startTime())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "endTime"));
        }
        int capacity = command.capacity() == null ? 0 : command.capacity();
        if (capacity < 1 || capacity > 100) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "capacity"));
        }
        if (command.pointId() != null && !shifts.pointExists(command.pointId())) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        LocalDate today = LocalDate.now(clock);
        int serial = sequences.next("SHIFT" + DAY.format(today), today.getYear());
        String shiftNo = "SH" + DAY.format(today) + String.format("%03d", serial);
        long id = shifts.insertShift(shiftNo, command, operatorId);
        if (id <= 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return shifts.findShift(id).orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    @Transactional
    public ShiftView signup(long shiftId, long userId) {
        ShiftRepository.VolunteerState volunteer = shifts.findVolunteer(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_MISMATCH));
        if ("SUSPENDED".equals(volunteer.status())) {
            throw new BusinessException(ErrorCode.NO_PERMISSION, "连续三次缺勤，已暂停接单，请先申诉");
        }
        if (!"ACTIVE".equals(volunteer.status())) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        ShiftView shift = shifts.findShiftForUpdate(shiftId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        LocalDateTime startAt = shift.shiftDate().atTime(shift.startTime());
        if (startAt.isBefore(LocalDateTime.now(clock).withNano(0))) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        if (!"OPEN".equals(shift.status()) || shift.booked() >= shift.capacity()) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        if (shifts.signupExists(shiftId, userId)) {
            throw new BusinessException(ErrorCode.ACTIVE_RELATION_EXISTS);
        }
        long signupId = shifts.insertSignup(shiftId, userId, LocalDateTime.now(clock).withNano(0));
        if (signupId <= 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        if (shifts.incrementBooked(shiftId) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return shifts.findShifts(shift.shiftDate(), shift.shiftDate(), userId).stream()
                .filter(item -> item.id() == shiftId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    @Transactional
    public ShiftView checkin(long shiftId, long userId) {
        ShiftView shift = shifts.findShift(shiftId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        ShiftRepository.SignupRow signup = shifts.findSignupForUpdate(shiftId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (signup.absent()) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        if (signup.checkinAt() != null) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        double hours = Duration.between(shift.startTime(), shift.endTime()).toMinutes() / 60.0;
        int rows = shifts.markCheckin(shiftId, userId, LocalDateTime.now(clock).withNano(0), hours);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        shifts.addHours(userId, hours);
        shifts.resetAbsenceStreak(userId);
        return shifts.findShifts(shift.shiftDate(), shift.shiftDate(), userId).stream()
                .filter(item -> item.id() == shiftId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    public List<ShiftSignupView> signups(long shiftId) {
        shifts.findShift(shiftId).orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        return shifts.findSignups(shiftId);
    }

    @Transactional
    public AbsenceResult markAbsent(long shiftId, long userId, long operatorId) {
        ShiftRepository.SignupRow signup = shifts.findSignupForUpdate(shiftId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (signup.checkinAt() != null || signup.absent()) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        if (shifts.markAbsent(signup.id()) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        shifts.incrementAbsenceStreak(userId);
        ShiftRepository.VolunteerState state = shifts.findVolunteer(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        boolean suspended = false;
        if (state.absenceStreak() >= 3 && shifts.suspend(userId,
                LocalDateTime.now(clock).withNano(0)) > 0) {
            suspended = true;
            String title = "志愿接单已暂停：连续三次缺勤";
            messages.insert(new MessageWrite(userId, "VOLUNTEER_SUSPEND", title,
                    "VOLUNTEER", String.valueOf(userId), operatorId,
                    LocalDateTime.now(clock).withNano(0)));
        }
        return new AbsenceResult(state.absenceStreak(), suspended,
                suspended ? "连续三次缺勤，已暂停接单，可在排班页申诉" : "缺勤已记录");
    }

    @Transactional
    public ShiftView close(long shiftId, String action) {
        ShiftView shift = shifts.findShift(shiftId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (!"OPEN".equals(shift.status())) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        String status = action == null ? "CLOSED" : action.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("CLOSED", "CANCELED").contains(status)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", "action"));
        }
        if (shifts.closeShift(shiftId, status) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return shifts.findShift(shiftId).orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    @Transactional
    public AppealView appeal(long userId, String reason) {
        ShiftRepository.VolunteerState state = shifts.findVolunteer(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_MISMATCH));
        if (!"SUSPENDED".equals(state.status())) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        if (shifts.hasPendingAppeal(userId)) {
            throw new BusinessException(ErrorCode.ACTIVE_RELATION_EXISTS, "已有待审核申诉");
        }
        String reasonText = requireText(reason, 255, "reason");
        if (reasonText.length() < 10) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "reason"));
        }
        long id = shifts.insertAppeal(userId, reasonText, LocalDateTime.now(clock).withNano(0));
        if (id <= 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        return shifts.findMyAppeals(userId, 1).getFirst();
    }

    public List<AppealView> myAppeals(long userId) {
        return shifts.findMyAppeals(userId, 20);
    }

    public PageResult<AppealView> listAppeals(String status, PageQuery query) {
        String normalized = null;
        if (status != null && !status.isBlank()) {
            normalized = status.trim().toUpperCase(Locale.ROOT);
            if (!APPEAL_STATUSES.contains(normalized)) {
                throw new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", "status"));
            }
        }
        PageQuery page = query == null ? new PageQuery() : query.normalized();
        long total = shifts.countAppeals(normalized);
        if (total == 0) {
            return PageResult.of(List.of(), page.getPage(), page.getSize(), 0);
        }
        return PageResult.of(shifts.findAppeals(normalized, page.offset(), page.getSize()),
                page.getPage(), page.getSize(), total);
    }

    @Transactional
    public AppealView reviewAppeal(long appealId, String decision, String reason, long reviewerId) {
        ShiftRepository.AppealRow appeal = shifts.findAppealForUpdate(appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
        if (!"PENDING".equals(appeal.status())) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID);
        }
        String normalized = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!DECISIONS.contains(normalized)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID, Map.of("field", "decision"));
        }
        String status = "APPROVE".equals(normalized) ? "APPROVED" : "REJECTED";
        String reviewReason = reason == null ? null : reason.trim();
        if ("REJECTED".equals(status) && (reviewReason == null || reviewReason.isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "reason"));
        }
        if (reviewReason != null && reviewReason.length() > 255) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", "reason"));
        }
        if (shifts.reviewAppeal(appealId, status, reviewReason, reviewerId,
                LocalDateTime.now(clock).withNano(0)) == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_CONFLICT);
        }
        if ("APPROVED".equals(status)) {
            shifts.reactivate(appeal.userId());
        }
        return shifts.findAppealById(appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECORD_NOT_FOUND));
    }

    private String requireText(String value, int max, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.length() > max) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, Map.of("field", field));
        }
        return text;
    }
}
