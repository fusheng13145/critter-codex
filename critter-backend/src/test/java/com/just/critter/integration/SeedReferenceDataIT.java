package com.just.critter.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T0.4 验收：V2 种子落库后可被前端下拉与建档直接消费。
 * 默认跳过，设 CRITTER_MYSQL_IT=true 运行。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/critter_codex"
                + "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=root",
        "spring.datasource.password=1234",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "CRITTER_MYSQL_IT", matches = "true")
class SeedReferenceDataIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("字典覆盖建档、上报、支出所需的下拉类型")
    void dict_types_are_seeded() {
        List<String> types = jdbc.queryForList("SELECT DISTINCT dict_type FROM sys_dict", String.class);

        assertThat(types).contains(
                "ANIMAL_CATEGORY", "ANIMAL_GENDER", "ANIMAL_COLOR", "POINT_AREA",
                "PROTECTION_LEVEL", "REPORT_TYPE", "EXPENSE_PURPOSE",
                "HEALTH_TYPE", "OCCURRENCE_SCALE");
    }

    @Test
    @DisplayName("花色与支出用途条目够用")
    void dict_items_are_enough_for_forms() {
        assertThat(countOfDict("ANIMAL_COLOR")).isGreaterThanOrEqualTo(9);
        assertThat(countOfDict("EXPENSE_PURPOSE")).isGreaterThanOrEqualTo(7);
        assertThat(countOfDict("PROTECTION_LEVEL")).isGreaterThanOrEqualTo(5);
        assertThat(jdbc.queryForList(
                "SELECT dict_label FROM sys_dict WHERE dict_type = 'REPORT_TYPE' ORDER BY sort_no", String.class))
                .containsExactly("受伤", "新出现", "疑似遗弃", "扰民", "死亡");
    }

    @Test
    @DisplayName("物种名录含校园常见种与学名")
    void species_are_seeded() {
        List<String> names = jdbc.queryForList("SELECT common_name FROM base_species", String.class);
        assertThat(names).hasSizeGreaterThanOrEqualTo(9).contains("家猫", "家犬", "野猪");

        String scientific = jdbc.queryForObject(
                "SELECT scientific_name FROM base_species WHERE common_name = '家猫'", String.class);
        assertThat(scientific).isEqualTo("Felis catus");
    }

    @Test
    @DisplayName("合作医院与点位达到可用数量")
    void clinics_and_points_are_seeded() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM base_clinic", Integer.class))
                .isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM base_point WHERE enabled = 1", Integer.class))
                .isGreaterThanOrEqualTo(10);

        List<String> priceNotes = jdbc.queryForList("SELECT price_note FROM base_clinic", String.class);
        assertThat(priceNotes).allSatisfy(note -> assertThat(note).contains("示例"));
    }

    private int countOfDict(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_dict WHERE dict_type = ? AND enabled = 1",
                Integer.class, type);
    }
}
