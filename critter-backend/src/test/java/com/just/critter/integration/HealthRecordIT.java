package com.just.critter.integration;

import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.enums.Gender;
import com.just.critter.domain.enums.HealthType;
import com.just.critter.service.archive.AddHealthRecordCommand;
import com.just.critter.service.archive.AnimalService;
import com.just.critter.service.archive.CreateAnimalCommand;
import com.just.critter.service.archive.CreatedAnimal;
import com.just.critter.service.archive.CreatedHealthRecord;
import com.just.critter.service.archive.HealthRecordView;
import com.just.critter.service.archive.HealthService;
import com.just.critter.support.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T1.4 真库验收：健康记录落库、倒序查询、疫苗三天内提醒、日期早于建档日拒绝。
 * 默认跳过，设 CRITTER_MYSQL_IT=true 运行；事务回滚，不污染演示数据。
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
class HealthRecordIT extends MySqlIntegrationTestSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private AnimalService animalService;
    @Autowired
    private HealthService healthService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("新增：健康记录落库，查询按记录时间倒序，疫苗三天内到期提醒")
    void create_and_list_health_records() {
        CreatedAnimal animal = animalService.create(command(), 1001L);
        OffsetDateTime now = OffsetDateTime.now(ZONE);
        LocalDate due = LocalDate.now(ZONE).plusDays(3);

        CreatedHealthRecord vaccine = healthService.create(animal.code(), new AddHealthRecordCommand(
                HealthType.VACCINE, now.minusHours(1), "猫三联第一针", due), 1002L);
        healthService.create(animal.code(), new AddHealthRecordCommand(
                HealthType.WEIGHT, now, "4.2kg", null), 1003L);

        assertThat(vaccine.healthRecordId()).isNotNull();
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM health_record WHERE animal_id = "
                        + "(SELECT id FROM animal WHERE code = ?)", Integer.class, animal.code());
        assertThat(rows).isEqualTo(2);

        List<HealthRecordView> views = healthService.list(animal.code());
        assertThat(views).hasSize(2);
        assertThat(views.get(0).type()).isEqualTo(HealthType.WEIGHT);
        HealthRecordView vaccineView = views.stream()
                .filter(view -> view.type() == HealthType.VACCINE).findFirst().orElseThrow();
        assertThat(vaccineView.operatorId()).isEqualTo(1002L);
        assertThat(vaccineView.nextDate()).isEqualTo(due);
        assertThat(vaccineView.reminderDue()).isTrue();
        assertThat(vaccineView.daysUntilNext()).isEqualTo(3);
    }

    @Test
    @DisplayName("新增：记录日期早于建档日拒绝 30301，不落库")
    void reject_record_before_created_date() {
        CreatedAnimal animal = animalService.create(command(), 1001L);
        OffsetDateTime yesterday = OffsetDateTime.now(ZONE).minusDays(1);

        assertThatThrownBy(() -> healthService.create(animal.code(), new AddHealthRecordCommand(
                HealthType.HISTORY, yesterday, "入校前旧伤", null), 1001L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(30301));

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM health_record WHERE animal_id = "
                        + "(SELECT id FROM animal WHERE code = ?)", Integer.class, animal.code());
        assertThat(rows).isZero();
    }

    private CreateAnimalCommand command() {
        Long pointId = jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1", Long.class);
        return new CreateAnimalCommand(AnimalCategory.CAT, Gender.FEMALE, "三花", 2,
                "右后腿跛行，左耳有缺口", pointId, List.of("F2026112100013"), null, null);
    }
}
