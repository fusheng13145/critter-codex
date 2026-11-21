package com.just.critter.integration;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionPlan;
import com.just.critter.adopt.service.AdoptionPlanCommand;
import com.just.critter.adopt.service.AdoptionPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

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
class AdoptionPlanIT extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AdoptionPlanService service;

    @Test
    @DisplayName("真实 MySQL：新增方案、停用后公开列表隐藏、管理员列表仍可见")
    void createsAndTogglesPlan() {
        AdoptionPlan created = service.create(new AdoptionPlanCommand(
                "集成测试月度方案", "MONTH", 3000, "每月一次照片", true), 1L);

        assertThat(created.planId()).isPositive();
        assertThat(jdbc.queryForObject(
                "SELECT amount FROM adoption_plan WHERE id = ?", Integer.class, created.planId()))
                .isEqualTo(3000);

        AdoptionPlan disabled = service.update(created.planId(), new AdoptionPlanCommand(
                "集成测试月度方案", "MONTH", 3000, "每月一次照片", false), 1L);

        assertThat(disabled.enabled()).isFalse();
        assertThat(service.listEnabledPlans())
                .noneMatch(plan -> plan.planId() == created.planId());
        assertThat(service.listPlans(true))
                .anyMatch(plan -> plan.planId() == created.planId() && !plan.enabled());
        assertThat(jdbc.queryForObject(
                "SELECT enabled FROM adoption_plan WHERE id = ?", Boolean.class, created.planId()))
                .isFalse();
    }
}
