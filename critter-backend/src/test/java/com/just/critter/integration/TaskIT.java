package com.just.critter.integration;

import com.just.critter.volunteer.service.TaskCheckinCommand;
import com.just.critter.volunteer.service.TaskCheckinView;
import com.just.critter.volunteer.service.TaskCommand;
import com.just.critter.volunteer.service.TaskService;
import com.just.critter.volunteer.service.TaskView;
import com.just.critter.volunteer.service.VolunteerTaskView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

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
@Transactional
class TaskIT extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TaskService service;

    @Test
    @DisplayName("真实 MySQL：发布任务、报名、打卡累计时长、我的任务反查")
    void taskRoundTrip() {
        long pointId = ensurePoint();
        jdbc.update("""
                INSERT INTO volunteer (user_id, status, available_slots, total_hours, created_by)
                VALUES (1, 'ACTIVE', '周末', 0, 1)
                ON DUPLICATE KEY UPDATE status = 'ACTIVE', total_hours = 0
                """);

        TaskView created = service.create(new TaskCommand(
                "集成测试投喂", "FEED", pointId, LocalDateTime.now().plusDays(1),
                2, new BigDecimal("2.0"), "带手套"), 1L);
        assertThat(created.taskNo()).startsWith("TASK");
        assertThat(created.status()).isEqualTo("OPEN");
        assertThat(jdbc.queryForObject(
                "SELECT title FROM task WHERE id = ?", String.class, created.taskId()))
                .isEqualTo("集成测试投喂");

        TaskView joined = service.join(created.taskId(), 1L);
        assertThat(joined.joinedByMe()).isTrue();
        assertThat(joined.joined()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT joined FROM task WHERE id = ?", Integer.class, created.taskId()))
                .isEqualTo(1);

        TaskCheckinView checkin = service.checkin(created.taskId(),
                new TaskCheckinCommand("file/task-checkin-it.jpg"), 1L);
        assertThat(checkin.hours()).isEqualByComparingTo("2.0");
        assertThat(checkin.totalHours()).isEqualByComparingTo("2.0");
        assertThat(jdbc.queryForObject(
                "SELECT checkin_photo FROM task_signup WHERE task_id = ? AND user_id = 1",
                String.class, created.taskId())).isEqualTo("file/task-checkin-it.jpg");
        assertThat(jdbc.queryForObject(
                "SELECT total_hours FROM volunteer WHERE user_id = 1", BigDecimal.class))
                .isEqualByComparingTo("2.0");

        VolunteerTaskView mine = service.mine(1L, 1, 20);
        assertThat(mine.volunteerStatus()).isEqualTo("ACTIVE");
        assertThat(mine.totalHours()).isEqualByComparingTo("2.0");
        assertThat(mine.tasks()).anyMatch(task -> task.taskId() == created.taskId()
                && task.checkedIn() && task.hours().compareTo(new BigDecimal("2.0")) == 0);
    }

    private long ensurePoint() {
        Long id = jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1", Long.class);
        if (id != null) {
            return id;
        }
        jdbc.update("INSERT INTO base_point (point_name, enabled) VALUES ('志愿任务测试点', 1)");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
