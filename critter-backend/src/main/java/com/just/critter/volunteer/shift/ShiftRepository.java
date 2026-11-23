package com.just.critter.volunteer.shift;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ShiftRepository {

    private static final RowMapper<ShiftView> SHIFT_MAPPER = (rs, rowNum) -> new ShiftView(
            rs.getLong("id"),
            rs.getString("shift_no"),
            rs.getString("title"),
            rs.getObject("point_id") == null ? null : rs.getLong("point_id"),
            rs.getDate("shift_date") == null ? null : rs.getDate("shift_date").toLocalDate(),
            rs.getTime("start_time") == null ? null : rs.getTime("start_time").toLocalTime(),
            rs.getTime("end_time") == null ? null : rs.getTime("end_time").toLocalTime(),
            rs.getInt("capacity"),
            rs.getInt("booked"),
            rs.getString("status"),
            rs.getBoolean("signed"),
            rs.getBoolean("checked_in"),
            rs.getBoolean("absent"));

    private final JdbcTemplate jdbc;

    public ShiftRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean pointExists(long pointId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM base_point WHERE id = ?", Long.class, pointId);
        return count != null && count > 0;
    }

    public long insertShift(String shiftNo, ShiftCreateCommand command, long operatorId) {
        String sql = """
                INSERT INTO volunteer_shift
                    (shift_no, title, point_id, shift_date, start_time, end_time, capacity, booked,
                     status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'OPEN', ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, shiftNo);
            ps.setString(2, command.title().trim());
            if (command.pointId() == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, command.pointId());
            }
            ps.setDate(4, Date.valueOf(command.shiftDate()));
            ps.setTime(5, Time.valueOf(command.startTime()));
            ps.setTime(6, Time.valueOf(command.endTime()));
            ps.setInt(7, command.capacity());
            ps.setLong(8, operatorId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.longValue();
    }

    public List<ShiftView> findShifts(LocalDate from, LocalDate to, long userId) {
        return jdbc.query("""
                SELECT sh.id, sh.shift_no, sh.title, sh.point_id, sh.shift_date,
                       sh.start_time, sh.end_time, sh.capacity, sh.booked, sh.status,
                       (s.id IS NOT NULL) AS signed,
                       (s.checkin_at IS NOT NULL) AS checked_in,
                       (s.absent = 1) AS absent
                FROM volunteer_shift sh
                LEFT JOIN volunteer_shift_signup s
                       ON s.shift_id = sh.id AND s.user_id = ?
                WHERE sh.shift_date BETWEEN ? AND ?
                ORDER BY sh.shift_date, sh.start_time, sh.id
                """, SHIFT_MAPPER, userId, Date.valueOf(from), Date.valueOf(to));
    }

    public List<ShiftView> findMySignups(long userId, LocalDate from, LocalDate to) {        return jdbc.query("""
                SELECT sh.id, sh.shift_no, sh.title, sh.point_id, sh.shift_date,
                       sh.start_time, sh.end_time, sh.capacity, sh.booked, sh.status,
                       TRUE AS signed,
                       (s.checkin_at IS NOT NULL) AS checked_in,
                       (s.absent = 1) AS absent
                FROM volunteer_shift_signup s
                JOIN volunteer_shift sh ON sh.id = s.shift_id
                WHERE s.user_id = ? AND sh.shift_date BETWEEN ? AND ?
                ORDER BY sh.shift_date, sh.start_time
                """, SHIFT_MAPPER, userId, Date.valueOf(from), Date.valueOf(to));
    }

    public List<ShiftSignupView> findSignups(long shiftId) {
        return jdbc.query("""
                SELECT s.id, s.user_id, u.nickname AS user_name, s.signed_at,
                       s.checkin_at, s.absent, s.hours
                FROM volunteer_shift_signup s JOIN sys_user u ON u.id = s.user_id
                WHERE s.shift_id = ?
                ORDER BY s.signed_at, s.id
                """, (rs, rowNum) -> new ShiftSignupView(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("user_name"),
                rs.getTimestamp("signed_at") == null ? null
                        : rs.getTimestamp("signed_at").toLocalDateTime(),
                rs.getTimestamp("checkin_at") == null ? null
                        : rs.getTimestamp("checkin_at").toLocalDateTime(),
                rs.getBoolean("absent"), rs.getBigDecimal("hours")), shiftId);
    }

    public Optional<ShiftView> findShift(long id) {
        List<ShiftView> rows = jdbc.query("""
                SELECT sh.id, sh.shift_no, sh.title, sh.point_id, sh.shift_date,
                       sh.start_time, sh.end_time, sh.capacity, sh.booked, sh.status,
                       FALSE AS signed, FALSE AS checked_in, FALSE AS absent
                FROM volunteer_shift sh WHERE sh.id = ?
                """, SHIFT_MAPPER, id);
        return rows.stream().findFirst();
    }

    public Optional<ShiftView> findShiftForUpdate(long id) {
        List<ShiftView> rows = jdbc.query("""
                SELECT sh.id, sh.shift_no, sh.title, sh.point_id, sh.shift_date,
                       sh.start_time, sh.end_time, sh.capacity, sh.booked, sh.status,
                       FALSE AS signed, FALSE AS checked_in, FALSE AS absent
                FROM volunteer_shift sh WHERE sh.id = ? FOR UPDATE
                """, SHIFT_MAPPER, id);
        return rows.stream().findFirst();
    }

    public boolean signupExists(long shiftId, long userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM volunteer_shift_signup
                WHERE shift_id = ? AND user_id = ?
                """, Long.class, shiftId, userId);
        return count != null && count > 0;
    }

    public long insertSignup(long shiftId, long userId, LocalDateTime now) {
        String sql = """
                INSERT INTO volunteer_shift_signup (shift_id, user_id, signed_at, created_by)
                VALUES (?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, shiftId);
            ps.setLong(2, userId);
            ps.setTimestamp(3, Timestamp.valueOf(now));
            ps.setLong(4, userId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.longValue();
    }

    public int incrementBooked(long shiftId) {
        return jdbc.update("""
                UPDATE volunteer_shift SET booked = booked + 1
                WHERE id = ? AND booked < capacity AND status = 'OPEN'
                """, shiftId);
    }

    public Optional<VolunteerState> findVolunteer(long userId) {
        List<VolunteerState> rows = jdbc.query("""
                SELECT status, absence_streak, suspended_at
                FROM volunteer WHERE user_id = ?
                """, (rs, rowNum) -> new VolunteerState(
                rs.getString("status"), rs.getInt("absence_streak"),
                rs.getTimestamp("suspended_at") == null ? null
                        : rs.getTimestamp("suspended_at").toLocalDateTime()), userId);
        return rows.stream().findFirst();
    }

    public Optional<SignupRow> findSignupForUpdate(long shiftId, long userId) {
        List<SignupRow> rows = jdbc.query("""
                SELECT id, checkin_at, absent
                FROM volunteer_shift_signup
                WHERE shift_id = ? AND user_id = ?
                FOR UPDATE
                """, (rs, rowNum) -> new SignupRow(rs.getLong("id"),
                rs.getTimestamp("checkin_at") == null ? null
                        : rs.getTimestamp("checkin_at").toLocalDateTime(),
                rs.getBoolean("absent")), shiftId, userId);
        return rows.stream().findFirst();
    }

    public int markAbsent(long signupId) {
        return jdbc.update("""
                UPDATE volunteer_shift_signup SET absent = 1
                WHERE id = ? AND absent = 0 AND checkin_at IS NULL
                """, signupId);
    }

    public int incrementAbsenceStreak(long userId) {
        return jdbc.update("""
                UPDATE volunteer SET absence_streak = absence_streak + 1
                WHERE user_id = ?
                """, userId);
    }

    public int resetAbsenceStreak(long userId) {
        return jdbc.update("""
                UPDATE volunteer SET absence_streak = 0
                WHERE user_id = ?
                """, userId);
    }

    public int suspend(long userId, LocalDateTime now) {
        return jdbc.update("""
                UPDATE volunteer
                SET status = 'SUSPENDED', suspended_at = ?, absence_streak = 3
                WHERE user_id = ? AND status = 'ACTIVE'
                """, Timestamp.valueOf(now), userId);
    }

    public int reactivate(long userId) {
        return jdbc.update("""
                UPDATE volunteer
                SET status = 'ACTIVE', suspended_at = NULL, absence_streak = 0
                WHERE user_id = ? AND status = 'SUSPENDED'
                """, userId);
    }

    public int markCheckin(long shiftId, long userId, LocalDateTime now, double hours) {
        return jdbc.update("""
                UPDATE volunteer_shift_signup
                SET checkin_at = ?, hours = ?
                WHERE shift_id = ? AND user_id = ? AND checkin_at IS NULL
                """, Timestamp.valueOf(now), hours, shiftId, userId);
    }

    public int addHours(long userId, double hours) {
        return jdbc.update("""
                UPDATE volunteer SET total_hours = total_hours + ?
                WHERE user_id = ?
                """, hours, userId);
    }

    public int closeShift(long id, String status) {
        return jdbc.update("""
                UPDATE volunteer_shift SET status = ?
                WHERE id = ? AND status = 'OPEN'
                """, status, id);
    }

    public boolean hasPendingAppeal(long userId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM volunteer_appeal
                WHERE user_id = ? AND status = 'PENDING'
                """, Long.class, userId);
        return count != null && count > 0;
    }

    public long insertAppeal(long userId, String reason, LocalDateTime now) {
        String sql = """
                INSERT INTO volunteer_appeal (user_id, reason, status, created_by)
                VALUES (?, ?, 'PENDING', ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, reason);
            ps.setLong(3, userId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.longValue();
    }

    public long countAppeals(String status) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM volunteer_appeal
                WHERE (? IS NULL OR status = ?)
                """, Long.class, status, status);
        return count == null ? 0 : count;
    }

    public List<AppealView> findAppeals(String status, long offset, int size) {        return jdbc.query("""
                SELECT ap.id, u.nickname AS user_name, ap.reason, ap.status,
                       ap.review_reason, ap.decided_at, ap.created_at
                FROM volunteer_appeal ap JOIN sys_user u ON u.id = ap.user_id
                WHERE (? IS NULL OR ap.status = ?)
                ORDER BY ap.created_at DESC, ap.id DESC
                LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new AppealView(
                rs.getLong("id"), rs.getString("user_name"), rs.getString("reason"),
                rs.getString("status"), rs.getString("review_reason"),
                rs.getTimestamp("decided_at") == null ? null
                        : rs.getTimestamp("decided_at").toLocalDateTime(),
                rs.getTimestamp("created_at") == null ? null
                        : rs.getTimestamp("created_at").toLocalDateTime()), status, status, size, offset);
    }

    public List<AppealView> findMyAppeals(long userId, int limit) {
        return jdbc.query("""
                SELECT ap.id, u.nickname AS user_name, ap.reason, ap.status,
                       ap.review_reason, ap.decided_at, ap.created_at
                FROM volunteer_appeal ap JOIN sys_user u ON u.id = ap.user_id
                WHERE ap.user_id = ?
                ORDER BY ap.created_at DESC, ap.id DESC
                LIMIT ?
                """, (rs, rowNum) -> new AppealView(
                rs.getLong("id"), rs.getString("user_name"), rs.getString("reason"),
                rs.getString("status"), rs.getString("review_reason"),
                rs.getTimestamp("decided_at") == null ? null
                        : rs.getTimestamp("decided_at").toLocalDateTime(),
                rs.getTimestamp("created_at") == null ? null
                        : rs.getTimestamp("created_at").toLocalDateTime()), userId, limit);
    }

    public Optional<AppealView> findAppealById(long id) {
        List<AppealView> rows = jdbc.query("""
                SELECT ap.id, u.nickname AS user_name, ap.reason, ap.status,
                       ap.review_reason, ap.decided_at, ap.created_at
                FROM volunteer_appeal ap JOIN sys_user u ON u.id = ap.user_id
                WHERE ap.id = ?
                """, (rs, rowNum) -> new AppealView(
                rs.getLong("id"), rs.getString("user_name"), rs.getString("reason"),
                rs.getString("status"), rs.getString("review_reason"),
                rs.getTimestamp("decided_at") == null ? null
                        : rs.getTimestamp("decided_at").toLocalDateTime(),
                rs.getTimestamp("created_at") == null ? null
                        : rs.getTimestamp("created_at").toLocalDateTime()), id);
        return rows.stream().findFirst();
    }

    public Optional<AppealRow> findAppealForUpdate(long id) {
        List<AppealRow> rows = jdbc.query("""
                SELECT id, user_id, status FROM volunteer_appeal WHERE id = ? FOR UPDATE
                """, (rs, rowNum) -> new AppealRow(rs.getLong("id"), rs.getLong("user_id"),
                rs.getString("status")), id);
        return rows.stream().findFirst();
    }

    public int reviewAppeal(long id, String status, String reason, long reviewerId,
                            LocalDateTime now) {
        return jdbc.update("""
                UPDATE volunteer_appeal
                SET status = ?, review_reason = ?, reviewer_id = ?, decided_at = ?
                WHERE id = ? AND status = 'PENDING'
                """, status, reason, reviewerId, Timestamp.valueOf(now), id);
    }

    public record VolunteerState(String status, int absenceStreak, LocalDateTime suspendedAt) {
    }

    public record SignupRow(long id, LocalDateTime checkinAt, boolean absent) {
    }

    public record AppealRow(long id, long userId, String status) {
    }
}
