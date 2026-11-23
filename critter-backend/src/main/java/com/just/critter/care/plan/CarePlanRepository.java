package com.just.critter.care.plan;

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
public class CarePlanRepository {

    private final JdbcTemplate jdbc;

    public CarePlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<CarePlanView> MAPPER = (rs, rowNum) -> new CarePlanView(
            rs.getLong("id"),
            rs.getString("plan_no"),
            rs.getString("animal_code"),
            rs.getString("type"),
            rs.getString("item_name"),
            rs.getString("dosage"),
            rs.getDate("plan_date") == null ? null : rs.getDate("plan_date").toLocalDate(),
            rs.getString("status"),
            rs.getObject("source_visit_id") == null ? null : rs.getLong("source_visit_id"),
            rs.getString("result"),
            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime(),
            rs.getString("note"),
            "PLANNED".equals(rs.getString("status"))
                    && rs.getDate("plan_date") != null
                    && rs.getDate("plan_date").toLocalDate().isBefore(LocalDate.now()));

    public Optional<AnimalRef> findAnimal(String code) {
        List<AnimalRef> rows = jdbc.query(
                "SELECT id, code FROM animal WHERE code = ? AND deleted = 0",
                (rs, rowNum) -> new AnimalRef(rs.getLong("id"), rs.getString("code")), code);
        return rows.stream().findFirst();
    }

    public Optional<VisitRef> findVisit(long visitId) {
        List<VisitRef> rows = jdbc.query("""
                SELECT v.id, v.animal_id, a.code AS animal_code
                FROM visit v JOIN animal a ON a.id = v.animal_id
                WHERE v.id = ?
                """, (rs, rowNum) -> new VisitRef(rs.getLong("id"), rs.getLong("animal_id"),
                rs.getString("animal_code")), visitId);
        return rows.stream().findFirst();
    }

    public long insert(String planNo, long animalId, CarePlanCommand command,
                       String type, LocalDateTime now, long operatorId) {
        String sql = """
                INSERT INTO care_plan
                    (plan_no, animal_id, type, item_name, dosage, plan_date, status,
                     source_visit_id, note, created_by)
                VALUES (?, ?, ?, ?, ?, ?, 'PLANNED', ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, planNo);
            ps.setLong(2, animalId);
            ps.setString(3, type);
            ps.setString(4, command.itemName().trim());
            ps.setString(5, command.dosage() == null ? null : command.dosage().trim());
            ps.setDate(6, Date.valueOf(command.planDate()));
            if (command.visitId() == null) {
                ps.setNull(7, java.sql.Types.BIGINT);
            } else {
                ps.setLong(7, command.visitId());
            }
            ps.setString(8, command.note() == null ? null : command.note().trim());
            ps.setLong(9, operatorId);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0 : key.longValue();
    }

    public long count(String animalCode, String type, String status) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM care_plan p JOIN animal a ON a.id = p.animal_id
                WHERE (? IS NULL OR a.code = ?)
                  AND (? IS NULL OR p.type = ?)
                  AND (? IS NULL OR p.status = ?)
                """, Long.class, animalCode, animalCode, type, type, status, status);
        return count == null ? 0 : count;
    }

    public List<CarePlanView> findPage(String animalCode, String type, String status,
                                       long offset, int size) {
        return jdbc.query("""
                SELECT p.id, p.plan_no, a.code AS animal_code, p.type, p.item_name, p.dosage,
                       p.plan_date, p.status, p.source_visit_id, p.result, p.completed_at, p.note
                FROM care_plan p JOIN animal a ON a.id = p.animal_id
                WHERE (? IS NULL OR a.code = ?)
                  AND (? IS NULL OR p.type = ?)
                  AND (? IS NULL OR p.status = ?)
                ORDER BY CASE WHEN p.status = 'PLANNED' AND p.plan_date < CURRENT_DATE THEN 0 ELSE 1 END,
                         p.plan_date ASC, p.id DESC
                LIMIT ? OFFSET ?
                """, MAPPER, animalCode, animalCode, type, type, status, status, size, offset);
    }

    public Optional<CarePlanView> findById(long id) {
        List<CarePlanView> rows = jdbc.query("""
                SELECT p.id, p.plan_no, a.code AS animal_code, p.type, p.item_name, p.dosage,
                       p.plan_date, p.status, p.source_visit_id, p.result, p.completed_at, p.note
                FROM care_plan p JOIN animal a ON a.id = p.animal_id
                WHERE p.id = ?
                """, MAPPER, id);
        return rows.stream().findFirst();
    }

    public List<CarePlanView> findOverdue(int limit) {
        return jdbc.query("""
                SELECT p.id, p.plan_no, a.code AS animal_code, p.type, p.item_name, p.dosage,
                       p.plan_date, p.status, p.source_visit_id, p.result, p.completed_at, p.note
                FROM care_plan p JOIN animal a ON a.id = p.animal_id
                WHERE p.status = 'PLANNED' AND p.plan_date < CURRENT_DATE
                ORDER BY p.plan_date ASC, p.id ASC
                LIMIT ?
                """, MAPPER, limit);
    }

    public int complete(long id, String result, LocalDateTime now, long operatorId) {
        return jdbc.update("""
                UPDATE care_plan
                SET status = 'DONE', result = ?, completed_at = ?, operator_id = ?
                WHERE id = ? AND status = 'PLANNED'
                """, result, Timestamp.valueOf(now), operatorId, id);
    }

    public int cancel(long id, String note, LocalDateTime now, long operatorId) {
        return jdbc.update("""
                UPDATE care_plan
                SET status = 'CANCELED', note = COALESCE(?, note), operator_id = ?
                WHERE id = ? AND status = 'PLANNED'
                """, note, operatorId, id);
    }

    public List<CarePlanDue> findDueForReminder(LocalDate from, LocalDate to, int limit) {
        return jdbc.query("""
                SELECT p.id, p.plan_no, p.animal_id, a.code AS animal_code, p.type,
                       p.item_name, p.plan_date
                FROM care_plan p JOIN animal a ON a.id = p.animal_id
                WHERE p.status = 'PLANNED'
                  AND p.plan_date BETWEEN ? AND ?
                  AND (p.reminded_at IS NULL OR p.reminded_at < CURRENT_DATE)
                ORDER BY p.plan_date ASC, p.id ASC
                LIMIT ?
                """, (rs, rowNum) -> new CarePlanDue(
                rs.getLong("id"), rs.getString("plan_no"), rs.getLong("animal_id"),
                rs.getString("animal_code"), rs.getString("type"), rs.getString("item_name"),
                rs.getDate("plan_date").toLocalDate()), Date.valueOf(from), Date.valueOf(to), limit);
    }

    public void markReminded(long id, LocalDate date) {
        jdbc.update("UPDATE care_plan SET reminded_at = ? WHERE id = ?", Date.valueOf(date), id);
    }

    public List<Long> findActiveAdopter(long animalId) {
        return jdbc.query("""
                SELECT user_id FROM adoption
                WHERE animal_id = ? AND status = 'ACTIVE'
                """, (rs, rowNum) -> rs.getLong("user_id"), animalId);
    }

    public List<Long> findAnimalAdmins() {
        return jdbc.query("""
                SELECT DISTINCT u.id
                FROM sys_user u JOIN sys_user_role r ON r.user_id = u.id
                WHERE r.role_code IN ('ANIMAL_ADMIN', 'SYSTEM_ADMIN')
                  AND u.status = 'ACTIVE'
                ORDER BY u.id
                """, (rs, rowNum) -> rs.getLong("id"));
    }

    public record AnimalRef(long id, String code) {
    }

    public record VisitRef(long id, long animalId, String animalCode) {
    }

    public record CarePlanDue(long id, String planNo, long animalId, String animalCode,
                              String type, String itemName, LocalDate planDate) {
    }
}
