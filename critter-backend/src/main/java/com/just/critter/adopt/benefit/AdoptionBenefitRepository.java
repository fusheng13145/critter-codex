package com.just.critter.adopt.benefit;

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
public class AdoptionBenefitRepository {

    private static final RowMapper<AdoptionPostView> POST_MAPPER = (rs, rowNum) -> new AdoptionPostView(
            rs.getLong("id"), rs.getString("title"), rs.getString("content"),
            rs.getString("author_name"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());

    private static final RowMapper<AdoptionVisitView> VISIT_MAPPER = (rs, rowNum) -> new AdoptionVisitView(
            rs.getLong("id"),
            rs.getDate("visit_date") == null ? null : rs.getDate("visit_date").toLocalDate(),
            rs.getString("time_slot"), rs.getString("note"), rs.getString("status"),
            rs.getString("review_reason"),
            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public AdoptionBenefitRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Scope> findScope(String adoptionNo) {
        List<Scope> rows = jdbc.query("""
                SELECT a.id, a.user_id, a.adoption_no, a.animal_code, an.name AS animal_name,
                       a.start_date, a.end_date, a.status
                FROM adoption a JOIN animal an ON an.id = a.animal_id
                WHERE a.adoption_no = ?
                """, (rs, rowNum) -> new Scope(
                rs.getLong("id"), rs.getLong("user_id"), rs.getString("adoption_no"),
                rs.getString("animal_code"), rs.getString("animal_name"),
                rs.getDate("start_date") == null ? null : rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date") == null ? null : rs.getDate("end_date").toLocalDate(),
                rs.getString("status")), adoptionNo);
        return rows.stream().findFirst();
    }

    public List<AdoptionPostView> findPosts(long adoptionId) {
        return jdbc.query("""
                SELECT p.id, p.title, p.content, u.nickname AS author_name, p.created_at
                FROM adoption_post p LEFT JOIN sys_user u ON u.id = p.author_id
                WHERE p.adoption_id = ?
                ORDER BY p.created_at DESC, p.id DESC
                """, POST_MAPPER, adoptionId);
    }

    public long insertPost(long adoptionId, long authorId, String title, String content,
                           LocalDateTime now) {
        String sql = """
                INSERT INTO adoption_post (adoption_id, author_id, title, content, created_by)
                VALUES (?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, adoptionId);
            ps.setLong(2, authorId);
            ps.setString(3, title);
            ps.setString(4, content);
            ps.setLong(5, authorId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.longValue();
    }

    public List<AdoptionVisitView> findVisits(long adoptionId) {
        return jdbc.query("""
                SELECT id, visit_date, time_slot, note, status, review_reason, created_at
                FROM adoption_visit
                WHERE adoption_id = ?
                ORDER BY visit_date DESC, id DESC
                """, VISIT_MAPPER, adoptionId);
    }

    public long countPendingOnDate(long adoptionId, LocalDate visitDate) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM adoption_visit
                WHERE adoption_id = ? AND visit_date = ? AND status = 'PENDING'
                """, Long.class, adoptionId, Date.valueOf(visitDate));
        return count == null ? 0 : count;
    }

    public long insertVisit(long adoptionId, VisitBookingCommand command, long operatorId) {
        String sql = """
                INSERT INTO adoption_visit
                    (adoption_id, visit_date, time_slot, note, status, created_by)
                VALUES (?, ?, ?, ?, 'PENDING', ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, adoptionId);
            ps.setDate(2, Date.valueOf(command.visitDate()));
            ps.setString(3, command.timeSlot().trim());
            ps.setString(4, command.note() == null ? null : command.note().trim());
            ps.setLong(5, operatorId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.longValue();
    }

    public Optional<AdoptionVisitView> findVisit(long id) {
        List<AdoptionVisitView> rows = jdbc.query("""
                SELECT id, visit_date, time_slot, note, status, review_reason, created_at
                FROM adoption_visit WHERE id = ?
                """, VISIT_MAPPER, id);
        return rows.stream().findFirst();
    }

    public Optional<String> findVisitStatus(String adoptionNo, long visitId) {
        List<String> rows = jdbc.query("""
                SELECT v.status
                FROM adoption_visit v JOIN adoption a ON a.id = v.adoption_id
                WHERE a.adoption_no = ? AND v.id = ?
                """, (rs, rowNum) -> rs.getString("status"), adoptionNo, visitId);
        return rows.stream().findFirst();
    }

    public int cancelVisit(long id, long adoptionId, long operatorId) {
        return jdbc.update("""
                UPDATE adoption_visit
                SET status = 'CANCELED', reviewer_id = ?, decided_at = CURRENT_TIMESTAMP
                WHERE id = ? AND adoption_id = ? AND status IN ('PENDING', 'APPROVED')
                """, operatorId, id, adoptionId);
    }

    public int reviewVisit(long id, String status, String reason, long reviewerId,
                           LocalDateTime decidedAt) {
        return jdbc.update("""
                UPDATE adoption_visit
                SET status = ?, review_reason = ?, reviewer_id = ?, decided_at = ?
                WHERE id = ? AND status = 'PENDING'
                """, status, reason, reviewerId, Timestamp.valueOf(decidedAt), id);
    }

    public List<VisitAdminView> findPendingVisits(int limit) {
        return jdbc.query("""
                SELECT v.id, a.adoption_no, a.animal_code, an.name AS animal_name,
                       v.visit_date, v.time_slot, v.note, v.status,
                       u.nickname AS adopter_name, v.created_at
                FROM adoption_visit v
                JOIN adoption a ON a.id = v.adoption_id
                JOIN animal an ON an.id = a.animal_id
                LEFT JOIN sys_user u ON u.id = a.user_id
                WHERE v.status = 'PENDING'
                ORDER BY v.visit_date ASC, v.id ASC
                LIMIT ?
                """, (rs, rowNum) -> new VisitAdminView(
                rs.getLong("id"), rs.getString("adoption_no"), rs.getString("animal_code"),
                rs.getString("animal_name"),
                rs.getDate("visit_date") == null ? null : rs.getDate("visit_date").toLocalDate(),
                rs.getString("time_slot"), rs.getString("note"), rs.getString("status"),
                rs.getString("adopter_name"),
                rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime()),
                limit);
    }

    public record Scope(long id, long userId, String adoptionNo, String animalCode,
                        String animalName, LocalDate startDate, LocalDate endDate, String status) {
    }

    public record VisitAdminView(long id, String adoptionNo, String animalCode, String animalName,
                                 LocalDate visitDate, String timeSlot, String note, String status,
                                 String adopterName, LocalDateTime createdAt) {
    }
}
