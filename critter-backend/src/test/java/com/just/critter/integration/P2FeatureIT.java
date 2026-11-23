package com.just.critter.integration;

import com.just.critter.adopt.benefit.AdoptionBenefitService;
import com.just.critter.adopt.handover.HandoverCommand;
import com.just.critter.adopt.handover.HandoverService;
import com.just.critter.care.plan.CarePlanCommand;
import com.just.critter.care.plan.CarePlanService;
import com.just.critter.care.plan.CarePlanView;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.notice.service.NoticeCommand;
import com.just.critter.notice.service.NoticeService;
import com.just.critter.service.archive.AnimalStatusChangeCommand;
import com.just.critter.service.archive.AnimalStatusService;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.volunteer.shift.ShiftCreateCommand;
import com.just.critter.volunteer.shift.ShiftService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P2 收口功能的真库验收：V22 表可写、领养回访三次、排班三次缺勤暂停、
 * 认养终止后 403、用药计划逾期置顶、首页公示 highlights。
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
@Transactional
class P2FeatureIT extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HandoverService handovers;

    @Autowired
    private CarePlanService carePlans;

    @Autowired
    private ShiftService shifts;

    @Autowired
    private AdoptionBenefitService benefits;

    @Autowired
    private AnimalStatusService animalStatuses;

    @Autowired
    private NoticeService notices;

    @Test
    @DisplayName("领养交接：材料齐全生成 7/30/90 三次回访，缺失监护人直接 30409")
    void handoverCreatesThreeFollowups() {
        String animalCode = insertAnimal("HO");
        long operatorId = insertUser();

        var detail = handovers.create(new HandoverCommand(animalCode, "IT 领养人",
                "13800000000", true, "独立住房，已封窗", null), operatorId);

        assertThat(detail.handover().handoverNo()).startsWith("HO");
        assertThat(detail.followups()).hasSize(3);
        assertThat(detail.followups().get(0).dueDate())
                .isEqualTo(LocalDate.now().plusDays(7));
        assertThat(detail.followups().get(2).dueDate())
                .isEqualTo(LocalDate.now().plusDays(90));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM handover_followup WHERE handover_id = ?",
                Integer.class, detail.handover().id())).isEqualTo(3);

        assertThatThrownBy(() -> handovers.create(new HandoverCommand(animalCode, "IT 领养人",
                "13800000000", false, "独立住房", null), operatorId))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30409));
    }

    @Test
    @DisplayName("认养专属页：ACTIVE 可读，终止后立即 403")
    void benefitAccessFollowsAdoptionStatus() {
        String animalCode = insertAnimal("BEN");
        long userId = insertUser();
        long animalId = jdbc.queryForObject(
                "SELECT id FROM animal WHERE code = ?", Long.class, animalCode);
        long planId = insertPlan();
        String adoptionNo = "IT-AD-" + System.nanoTime();
        jdbc.update("""
                INSERT INTO adoption
                    (adoption_no, user_id, animal_id, animal_code, plan_id, amount, period,
                     start_date, end_date, status, contact, promise_accepted_at)
                VALUES (?, ?, ?, ?, ?, 100, 'MONTH', CURRENT_DATE,
                        DATE_ADD(CURRENT_DATE, INTERVAL 1 YEAR), 'ACTIVE', '13800000000', NOW())
                """, adoptionNo, userId, animalId, animalCode, planId);
        long adoptionId = jdbc.queryForObject(
                "SELECT id FROM adoption WHERE adoption_no = ?", Long.class, adoptionNo);

        var view = benefits.view(adoptionNo, userId);
        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.animalCode()).isEqualTo(animalCode);

        jdbc.update("UPDATE adoption SET status = 'TERMINATED' WHERE id = ?", adoptionId);
        assertThatThrownBy(() -> benefits.view(adoptionNo, userId))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(20002));
    }

    @Test
    @DisplayName("动物离世：自动终止 ACTIVE 认养、发 ANIMAL_DEATH 消息，专属页转 403")
    void animalDeathTerminatesAdoptionAndNotifies() {
        String animalCode = insertAnimal("DEAD");
        long userId = insertUser();
        long operatorId = insertUser();
        long animalId = jdbc.queryForObject(
                "SELECT id FROM animal WHERE code = ?", Long.class, animalCode);
        long planId = insertPlan();
        String adoptionNo = "IT-AD-" + System.nanoTime();
        jdbc.update("""
                INSERT INTO adoption
                    (adoption_no, user_id, animal_id, animal_code, plan_id, amount, period,
                     start_date, end_date, status, contact, promise_accepted_at)
                VALUES (?, ?, ?, ?, ?, 100, 'MONTH', CURRENT_DATE,
                        DATE_ADD(CURRENT_DATE, INTERVAL 1 YEAR), 'ACTIVE', '13800000000', NOW())
                """, adoptionNo, userId, animalId, animalCode, planId);
        long adoptionId = jdbc.queryForObject(
                "SELECT id FROM adoption WHERE adoption_no = ?", Long.class, adoptionNo);

        assertThat(benefits.view(adoptionNo, userId).status()).isEqualTo("ACTIVE");
        animalStatuses.change(animalCode,
                new AnimalStatusChangeCommand(AnimalStatus.DECEASED, "真实库离世验收"), operatorId);

        assertThat(jdbc.queryForObject(
                "SELECT status FROM adoption WHERE id = ?", String.class, adoptionId))
                .isEqualTo("TERMINATED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM message WHERE receiver_id = ? AND type = 'ANIMAL_DEATH' "
                        + "AND biz_id = ?", Integer.class, userId, adoptionNo)).isEqualTo(1);
        assertThatThrownBy(() -> benefits.view(adoptionNo, userId))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(20002));
    }

    @Test
    @DisplayName("用药计划：逾期项在列表顶部返回")
    void overdueCarePlansComeFirst() {
        String animalCode = insertAnimal("CP");
        long operatorId = insertUser();

        CarePlanView overdue = carePlans.create(new CarePlanCommand(animalCode, null,
                "MEDICATION", "驱虫药", "1 片", LocalDate.now().minusDays(3), null), operatorId);
        carePlans.create(new CarePlanCommand(animalCode, null,
                "VACCINE", "狂犬疫苗", "1 支", LocalDate.now().plusDays(10), null), operatorId);

        var top = carePlans.overdue(3);
        assertThat(top).isNotEmpty();
        assertThat(top.getFirst().id()).isEqualTo(overdue.id());
        assertThat(top.getFirst().overdue()).isTrue();
    }

    @Test
    @DisplayName("排班：连续三次缺勤后志愿者状态变为 SUSPENDED")
    void threeAbsencesSuspendVolunteer() {
        long userId = insertUser();
        long operatorId = insertUser();
        jdbc.update("""
                INSERT INTO volunteer (user_id, status, total_hours, created_by)
                VALUES (?, 'ACTIVE', 0, ?)
                ON DUPLICATE KEY UPDATE status = 'ACTIVE', absence_streak = 0,
                    suspended_at = NULL
                """, userId, operatorId);

        for (int i = 0; i < 3; i++) {
            LocalDate date = LocalDate.now().plusDays(i + 1);
            var shift = shifts.create(new ShiftCreateCommand("IT 晚班", null, date,
                    LocalTime.of(18, 0), LocalTime.of(20, 0), 2), operatorId);
            shifts.signup(shift.id(), userId);
            var result = shifts.markAbsent(shift.id(), userId, operatorId);
            assertThat(result.absenceStreak()).isEqualTo(i + 1);
        }

        assertThat(jdbc.queryForObject(
                "SELECT status FROM volunteer WHERE user_id = ?", String.class, userId))
                .isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM volunteer_appeal WHERE user_id = ? AND status = 'PENDING'",
                Integer.class, userId)).isEqualTo(0);
    }

    @Test
    @DisplayName("公告 highlights：置顶优先、公示类紧随其后")
    void noticeHighlightsArePinnedFirst() {
        long authorId = insertUser();
        var pinned = notices.publish(new NoticeCommand("SCIENCE",
                "IT 置顶科普", "置顶正文", true), authorId);
        var publicity = notices.publish(new NoticeCommand("PUBLICITY",
                "IT 结项公示", "公示正文", false), authorId);
        notices.publish(new NoticeCommand("PROGRESS", "IT 进展", "进展正文", false), authorId);

        List<com.just.critter.notice.service.NoticeView> highlights = notices.highlights(5);
        assertThat(highlights).isNotEmpty();
        assertThat(highlights.stream().map(com.just.critter.notice.service.NoticeView::noticeId).toList())
                .contains(pinned.noticeId(), publicity.noticeId());
        assertThat(highlights).anySatisfy(item -> {
            if (item.noticeId() == pinned.noticeId()) {
                assertThat(item.pinned()).isTrue();
            }
        });
    }

    private String insertAnimal(String prefix) {
        String suffix = shortSuffix();
        jdbc.update("INSERT INTO base_point (point_name, enabled) VALUES (?, 1)",
                "P2 集成测试点位-" + suffix);
        long pointId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        String code = prefix + "-IT-" + suffix;
        jdbc.update("""
                INSERT INTO animal (code, category, gender, color, features, point_id, status)
                VALUES (?, 'CAT', 'UNKNOWN', '狸花', 'P2 集成测试个体', ?, 'ON_CAMPUS')
                """, code, pointId);
        return code;
    }

    private long insertUser() {
        String suffix = shortSuffix();
        jdbc.update("""
                INSERT INTO sys_user (user_no, email, password_hash, nickname, primary_role, status)
                VALUES (?, ?, ?, ?, 'STUDENT', 'ACTIVE')
                """, "STU-P2-" + suffix, "p2" + suffix + "@test.local",
                "test-hash", "P2 集成测试用户");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertPlan() {
        jdbc.update("""
                INSERT INTO adoption_plan (plan_name, period, amount, benefits, enabled)
                VALUES (?, 'MONTH', 1000, 'P2 集成测试权益', 1)
                """, "P2 方案-" + shortSuffix());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String shortSuffix() {
        String nano = String.valueOf(System.nanoTime());
        return nano.substring(nano.length() - 8);
    }
}
