package com.just.critter.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T0.3 验收：Flyway 在真实 MySQL 8 上建出 51 张业务表 + seq_no + 3 视图，
 * 且 CHECK / 唯一索引 / 生成列真实生效。
 * 默认跳过；需要真库时设 CRITTER_MYSQL_IT=true 运行。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/critter_codex_it"
                + "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=${CRITTER_MYSQL_USER:root}",
        "spring.datasource.password=${CRITTER_MYSQL_PASSWORD:}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "CRITTER_MYSQL_IT", matches = "true")
class MySqlSchemaIT extends MySqlIntegrationTestSupport {

    private static final List<String> TABLES = List.of(
            "sys_user", "sys_role", "sys_user_role", "sys_permission", "sys_dict", "sys_file", "sys_log",
            "sys_role_permission", "sys_appeal",
            "base_roster", "base_point", "base_species", "base_clinic",
            "animal", "animal_status_log", "animal_change_log", "health_record", "animal_alias",
            "animal_group", "sighting", "occurrence",
            "adoption_plan", "adoption", "adoption_payment",
            "project", "project_budget", "donation", "expense", "project_settlement",
            "project_settlement_note",
            "report", "rescue_order", "visit", "tnr_task", "tnr_node",
            "volunteer", "task", "task_signup", "patrol_record",
            "notice", "message", "comment", "follow",
            "animal_handover", "handover_followup",
            "volunteer_shift", "volunteer_shift_signup", "volunteer_appeal",
            "adoption_post", "adoption_visit", "care_plan",
            "seq_no");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("51 张业务表 + seq_no 全部建出")
    void all_tables_exist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                        + "AND table_name <> 'flyway_schema_history'",
                String.class);

        assertThat(tables).hasSize(52).containsAll(TABLES);
    }

    @Test
    @DisplayName("三个统计视图可用")
    void views_exist() {
        List<String> views = jdbc.queryForList(
                "SELECT table_name FROM information_schema.views WHERE table_schema = DATABASE()",
                String.class);

        assertThat(views).containsExactlyInAnyOrder("v_animal_overview", "v_group_overview", "v_project_fund");
    }

    @Test
    @Transactional
    @DisplayName("CHECK 约束拒绝非法类别")
    void check_constraint_rejects_bad_category() {
        jdbc.update("INSERT INTO base_point (point_name, area) VALUES ('IT-点位', '测试')");
        Long pointId = jdbc.queryForObject("SELECT id FROM base_point WHERE point_name = 'IT-点位'", Long.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO animal (code, category, gender, color, features, point_id, status) "
                        + "VALUES ('IT-001', 'BAD', 'MALE', '黑', '测试', ?, 'ON_CAMPUS')", pointId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_animal_category");
    }

    @Test
    @Transactional
    @DisplayName("编号唯一索引拒绝重复")
    void unique_code_rejects_duplicate() {
        jdbc.update("INSERT INTO base_point (point_name, area) VALUES ('IT-点位2', '测试')");
        Long pointId = jdbc.queryForObject("SELECT id FROM base_point WHERE point_name = 'IT-点位2'", Long.class);
        String insert = "INSERT INTO animal (code, category, gender, color, features, point_id, status) "
                + "VALUES ('IT-002', 'CAT', 'FEMALE', '橘', '测试', ?, 'ON_CAMPUS')";
        jdbc.update(insert, pointId);

        assertThatThrownBy(() -> jdbc.update(insert, pointId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("认养生效唯一性用生成列兜底")
    void adoption_active_flag_is_generated() {
        String ddl = jdbc.queryForObject("SHOW CREATE TABLE adoption", (rs, rowNum) -> rs.getString(2));

        assertThat(ddl).contains("GENERATED ALWAYS").contains("uk_adoption_active");
    }
}
