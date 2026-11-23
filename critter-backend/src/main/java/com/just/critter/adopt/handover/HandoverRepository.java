package com.just.critter.adopt.handover;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class HandoverRepository {

    private static final RowMapper<HandoverView> HANDOVER_MAPPER = (rs, rowNum) -> new HandoverView(
            rs.getLong("id"),
            rs.getString("handover_no"),
            rs.getLong("animal_id"),
            rs.getString("animal_code"),
            rs.getString("adopter_name"),
            rs.getString("contact"),
            rs.getBoolean("guardian_consent"),
            rs.getString("residence_note"),
            rs.getString("status"),
            rs.getTimestamp("adopted_at") == null ? null : rs.getTimestamp("adopted_at").toLocalDateTime(),
            rs.getInt("completed_followups"),
            rs.getInt("total_followups"));

    private static final RowMapper<FollowupView> FOLLOWUP_MAPPER = (rs, rowNum) -> new FollowupView(
            rs.getLong("id"),
            rs.getInt("seq_no"),
            rs.getDate("due_date") == null ? null : rs.getDate("due_date").toLocalDate(),
            rs.getString("status"),
            rs.getString("result"),
            rs.getString("note"),
            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime(),
            "PENDING".equals(rs.getString("status"))
                    && rs.getDate("due_date") != null
                    && rs.getDate("due_date").toLocalDate().isBefore(LocalDate.now()));

    private final JdbcTemplate jdbc;

    public HandoverRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AnimalRef> findAnimal(String code) {
        List<AnimalRef> rows = jdbc.query(
                "SELECT id, code FROM animal WHERE code = ? AND deleted = 0",
                (rs, rowNum) -> new AnimalRef(rs.getLong("id"), rs.getString("code")), code);
        return rows.stream().findFirst();
    }

    public boolean animalDeceased(long animalId) {
        List<String> statuses = jdbc.query(
                "SELECT status FROM animal WHERE id = ?",
                (rs, rowNum) -> rs.getString("status"), animalId);
        return !statuses.isEmpty() && "DECEASED".equals(statuses.getFirst());
    }

    public long insertHandover(String handoverNo, long animalId, String animalCode,
                               HandoverCommand command, LocalDateTime adoptedAt, long operatorId) {
        String sql = """
                INSERT INTO animal_handover
                    (handover_no, animal_id, animal_code, adopter_name, contact,
                     guardian_consent, residence_note, status, adopted_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, handoverNo);
            ps.setLong(2, animalId);
            ps.setString(3, animalCode);
            ps.setString(4, command.adopterName().trim());
            ps.setString(5, command.contact().trim());
            ps.setBoolean(6, Boolean.TRUE.equals(command.guardianConsent()));
            ps.setString(7, command.residenceNote().trim());
            ps.setTimestamp(8, Timestamp.valueOf(adoptedAt));
            ps.setLong(9, operatorId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.longValue();
    }

    public void insertFollowup(long handoverId, int seqNo, LocalDate dueDate, long operatorId) {
        jdbc.update("""
                INSERT INTO handover_followup
                    (handover_id, seq_no, due_date, status, created_by)
                VALUES (?, ?, ?, 'PENDING', ?)
                """, handoverId, seqNo, Date.valueOf(dueDate), operatorId);
    }

    public long count(String status) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM animal_handover
                WHERE (? IS NULL OR status = ?)
                """, Long.class, status, status);
        return count == null ? 0 : count;
    }

    public List<HandoverView> findPage(String status, long offset, int size) {
        return jdbc.query("""
                SELECT h.id, h.handover_no, h.animal_id, h.animal_code, h.adopter_name,
                       h.contact, h.guardian_consent, h.residence_note, h.status, h.adopted_at,
                       (SELECT COUNT(*) FROM handover_followup f
                         WHERE f.handover_id = h.id AND f.status = 'DONE') AS completed_followups,
                       (SELECT COUNT(*) FROM handover_followup f
                         WHERE f.handover_id = h.id) AS total_followups
                FROM animal_handover h
                WHERE (? IS NULL OR h.status = ?)
                ORDER BY h.adopted_at DESC, h.id DESC
                LIMIT ? OFFSET ?
                """, HANDOVER_MAPPER, status, status, size, offset);
    }

    public Optional<HandoverView> findByNo(String handoverNo) {
        List<HandoverView> rows = jdbc.query("""
                SELECT h.id, h.handover_no, h.animal_id, h.animal_code, h.adopter_name,
                       h.contact, h.guardian_consent, h.residence_note, h.status, h.adopted_at,
                       (SELECT COUNT(*) FROM handover_followup f
                         WHERE f.handover_id = h.id AND f.status = 'DONE') AS completed_followups,
                       (SELECT COUNT(*) FROM handover_followup f
                         WHERE f.handover_id = h.id) AS total_followups
                FROM animal_handover h
                WHERE h.handover_no = ?
                """, HANDOVER_MAPPER, handoverNo);
        return rows.stream().findFirst();
    }

    public List<FollowupView> findFollowups(long handoverId) {
        return jdbc.query("""
                SELECT id, seq_no, due_date, status, result, note, completed_at
                FROM handover_followup
                WHERE handover_id = ?
                ORDER BY seq_no
                """, FOLLOWUP_MAPPER, handoverId);
    }

    public Optional<FollowupView> findFollowup(long handoverId, int seqNo) {
        List<FollowupView> rows = jdbc.query("""
                SELECT id, seq_no, due_date, status, result, note, completed_at
                FROM handover_followup
                WHERE handover_id = ? AND seq_no = ?
                """, FOLLOWUP_MAPPER, handoverId, seqNo);
        return rows.stream().findFirst();
    }

    public int completeFollowup(long followupId, String result, String note,
                                LocalDateTime completedAt, long operatorId) {
        return jdbc.update("""
                UPDATE handover_followup
                SET status = 'DONE', result = ?, note = ?, completed_at = ?, operator_id = ?
                WHERE id = ? AND status = 'PENDING'
                """, result, note, Timestamp.valueOf(completedAt), operatorId, followupId);
    }

    public record AnimalRef(long id, String code) {
    }
}
