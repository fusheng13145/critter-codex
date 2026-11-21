package com.just.critter.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统计查询（成员 A 只读 B/C 维护的表与视图，绝不写入）：
 * 生物总览走 v_animal_overview、资金总览走 v_project_fund，
 * 与台账口径一致是验收要求（成员任务 §1.3 最后一行）。
 */
public interface StatsMapper {

    // ---------- 生物总览 ----------

    @Select("""
            SELECT status AS status, animal_count AS total, neutered_count AS neutered
            FROM v_animal_overview
            WHERE status <> 'DECEASED'
            """)
    List<Map<String, Object>> animalStatusCounts();

    @Select("SELECT COUNT(*) FROM animal WHERE deleted = 0 AND status <> 'DECEASED'")
    long animalAliveTotal();

    @Select("SELECT COUNT(*) FROM animal WHERE deleted = 0")
    long animalTotal();

    @Select("SELECT COUNT(*) FROM animal WHERE deleted = 0 AND status = 'DECEASED'")
    long animalDeceasedTotal();

    @Select("SELECT COUNT(*) FROM animal_group")
    long groupTotal();

    @Select("SELECT COALESCE(SUM(size_min), 0) FROM animal_group")
    long groupSizeMinTotal();

    @Select("SELECT COALESCE(SUM(size_max), 0) FROM animal_group")
    long groupSizeMaxTotal();

    @Select("SELECT COUNT(*) FROM occurrence")
    long occurrenceTotal();

    @Select("SELECT COUNT(*) FROM animal WHERE deleted = 0 AND adopted = 1 AND status <> 'DECEASED'")
    long adoptedAnimalTotal();

    @Select("""
            SELECT DATE_FORMAT(created_at, '%Y-%m') AS month, COUNT(*) AS total
            FROM animal
            WHERE deleted = 0 AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 MONTH)
            GROUP BY month ORDER BY month
            """)
    List<Map<String, Object>> animalByMonth();

    // ---------- 资金总览 ----------

    @Select("""
            SELECT COALESCE(SUM(raised_amount), 0) AS raised,
                   COALESCE(SUM(spent_amount), 0) AS spent,
                   COALESCE(SUM(balance), 0) AS balance
            FROM v_project_fund
            """)
    Map<String, Object> fundTotals();

    @Select("SELECT COUNT(*) FROM donation WHERE status = 'VERIFIED'")
    long verifiedDonationCount();

    @Select("SELECT COUNT(*) FROM expense WHERE status = 'APPROVED'")
    long approvedExpenseCount();

    @Select("""
            SELECT m AS month, SUM(raised) AS raised, SUM(spent) AS spent FROM (
                SELECT DATE_FORMAT(verified_at, '%Y-%m') AS m, amount AS raised, 0 AS spent
                FROM donation WHERE status = 'VERIFIED' AND verified_at IS NOT NULL
                UNION ALL
                SELECT DATE_FORMAT(occurred_at, '%Y-%m') AS m, 0 AS raised, amount AS spent
                FROM expense WHERE status = 'APPROVED'
            ) t
            WHERE (#{from} IS NULL OR m >= DATE_FORMAT(#{from}, '%Y-%m'))
              AND (#{to} IS NULL OR m <= DATE_FORMAT(#{to}, '%Y-%m'))
            GROUP BY m ORDER BY m
            """)
    List<Map<String, Object>> fundByMonth(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("""
            SELECT p.project_no AS no, p.name AS name, p.status AS status,
                   p.target_amount AS target, v.raised_amount AS raised,
                   v.spent_amount AS spent, v.balance AS balance
            FROM project p
            JOIN v_project_fund v ON v.project_id = p.id
            WHERE p.deleted = 0
            ORDER BY p.id
            """)
    List<Map<String, Object>> fundByProject();

    // ---------- 效率统计 ----------

    @Select("SELECT COUNT(*) FROM adoption WHERE status = 'PENDING_AUDIT'")
    long pendingAdoptionTotal();
    @Select("SELECT COUNT(*) FROM expense WHERE status IN ('PENDING_REVIEW', 'FIRST_APPROVED')")
    long pendingExpenseTotal();

    @Select("""
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending,
                   SUM(CASE WHEN status IN ('PROCESSING', 'TO_REVISIT') THEN 1 ELSE 0 END) AS processing,
                   SUM(CASE WHEN status = 'CLOSED' THEN 1 ELSE 0 END) AS closed
            FROM report
            WHERE (#{from} IS NULL OR created_at >= #{from})
              AND (#{to} IS NULL OR created_at < DATE_ADD(#{to}, INTERVAL 1 DAY))
            """)
    Map<String, Object> reportSummary(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("""
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN status = 'CLOSED' THEN 1 ELSE 0 END) AS closed,
                   SUM(CASE WHEN status = 'TIMEOUT' THEN 1 ELSE 0 END) AS timeout,
                   SUM(CASE WHEN status IN ('PENDING', 'PROCESSING', 'TO_REVISIT') THEN 1 ELSE 0 END) AS open,
                   AVG(CASE WHEN accepted_at IS NOT NULL
                            THEN TIMESTAMPDIFF(MINUTE, created_at, accepted_at) END) AS avg_accept_minutes
            FROM rescue_order
            WHERE (#{from} IS NULL OR created_at >= #{from})
              AND (#{to} IS NULL OR created_at < DATE_ADD(#{to}, INTERVAL 1 DAY))
            """)
    Map<String, Object> orderSummary(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
