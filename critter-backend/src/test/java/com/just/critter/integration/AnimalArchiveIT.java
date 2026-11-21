package com.just.critter.integration;

import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.domain.enums.Gender;
import com.just.critter.service.archive.AnimalChangeLogView;
import com.just.critter.service.archive.AnimalDetail;
import com.just.critter.service.archive.AnimalFilter;
import com.just.critter.service.archive.AnimalService;
import com.just.critter.service.archive.AnimalSummary;
import com.just.critter.service.archive.CreateAnimalCommand;
import com.just.critter.service.archive.CreatedAnimal;
import com.just.critter.service.archive.UpdateAnimalCommand;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1.2 真库验收：发号落库、五条件组合筛选、编辑留痕可查。
 * 默认跳过，设 CRITTER_MYSQL_IT=true 运行；事务回滚，不污染演示数据。
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
@Transactional
class AnimalArchiveIT {

    @Autowired
    private AnimalService service;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("建档：编号按类别-年份-流水落库，连续两号递增")
    void create_persists_generated_code() {
        Long pointId = firstPointId();

        CreatedAnimal first = service.create(command(AnimalCategory.CAT, Gender.FEMALE, "三花", pointId,
                "右后腿跛行，左耳有缺口", "初步体检未见外伤"), 1001L);
        CreatedAnimal second = service.create(command(AnimalCategory.CAT, Gender.MALE, "橘白", pointId,
                "尾巴短一截，亲人", null), 1001L);

        assertThat(first.code()).matches("CAT-2026-\\d{3,}");
        assertThat(second.code()).matches("CAT-2026-\\d{3,}");
        assertThat(sequenceOf(second.code())).isGreaterThan(sequenceOf(first.code()));
        assertThat(first.status()).isEqualTo(AnimalStatus.ON_CAMPUS);

        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM animal WHERE code IN (?, ?)",
                Integer.class, first.code(), second.code());
        assertThat(rows).isEqualTo(2);
    }

    @Test
    @DisplayName("检索：关键字、状态、点位、绝育、认养五条件组合与手算一致")
    void search_combines_five_conditions() {
        Long pointA = firstPointId();
        Long pointB = secondPointId();

        CreatedAnimal a = service.create(command(AnimalCategory.CAT, Gender.FEMALE, "三花", pointA,
                "右后腿跛行，左耳有缺口", "内部备注A"), 1001L);
        CreatedAnimal b = service.create(command(AnimalCategory.DOG, Gender.MALE, "黑白", pointB,
                "脖颈有红色项圈，黑白花色", null), 1001L);
        CreatedAnimal c = service.create(command(AnimalCategory.OTHER, Gender.UNKNOWN, "灰", pointA,
                "体型偏小，行动缓慢", null), 1001L);

        jdbc.update("UPDATE animal SET status = 'TREATING', neutered = 1, adopted = 1 WHERE code = ?", b.code());

        assertThat(search(new AnimalFilter("2026", null, null, null, null)))
                .contains(a.code(), b.code(), c.code());

        assertThat(search(new AnimalFilter(null, List.of(AnimalStatus.TREATING), null, null, null)))
                .containsExactly(b.code());

        assertThat(search(new AnimalFilter(null, null, pointB, null, null)))
                .containsExactly(b.code());

        assertThat(search(new AnimalFilter(null, null, null, true, null)))
                .containsExactly(b.code());

        assertThat(search(new AnimalFilter(null, null, null, null, true)))
                .containsExactly(b.code());

        PageResult<AnimalSummary> combo = service.search(new AnimalFilter(
                "黑白", List.of(AnimalStatus.TREATING), pointB, true, true), new PageQuery(1, 20));
        assertThat(combo.getList()).extracting(AnimalSummary::code).containsExactly(b.code());
        assertThat(combo.getPage().getTotal()).isEqualTo(1);

        PageResult<AnimalSummary> page = service.search(new AnimalFilter(null, null, null, null, null),
                new PageQuery(1, 2));
        assertThat(page.getList()).hasSize(2);
        assertThat(page.getPage().getTotal()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("编辑：变更原因与前后值写库，历史可查，详情按视图裁剪")
    void update_writes_change_log_and_detail_trims() {
        CreatedAnimal created = service.create(command(AnimalCategory.CAT, Gender.FEMALE, "三花",
                firstPointId(), "右后腿跛行，左耳有缺口", "仅管理员可见：疑似旧伤"), 1001L);

        AnimalDetail updated = service.update(created.code(), new UpdateAnimalCommand(
                "三花", null, "橘白", 3, "右后腿跛行，左耳缺口更明显", null, null,
                "现场复核后修正花色与年龄"), 1002L);

        assertThat(updated.color()).isEqualTo("橘白");
        assertThat(updated.estimatedAge()).isEqualTo(3);

        List<AnimalChangeLogView> history = service.history(created.code());
        assertThat(history).extracting(AnimalChangeLogView::field)
                .containsExactlyInAnyOrder("name", "color", "estimatedAge", "features");
        assertThat(history).allSatisfy(row -> {
            assertThat(row.reason()).isEqualTo("现场复核后修正花色与年龄");
            assertThat(row.operatorId()).isEqualTo(1002L);
        });
        AnimalChangeLogView color = history.stream().filter(row -> "color".equals(row.field())).findFirst().orElseThrow();
        assertThat(color.beforeValue()).isEqualTo("三花");
        assertThat(color.afterValue()).isEqualTo("橘白");

        AnimalDetail publicView = service.detail(created.code(), false);
        assertThat(publicView.internalNote()).isNull();
        AnimalDetail adminView = service.detail(created.code(), true);
        assertThat(adminView.internalNote()).isEqualTo("仅管理员可见：疑似旧伤");
    }

    private List<String> search(AnimalFilter filter) {
        return service.search(filter, new PageQuery(1, 20)).getList().stream()
                .map(AnimalSummary::code).toList();
    }

    private CreateAnimalCommand command(AnimalCategory category, Gender gender, String color,
                                        Long pointId, String features, String healthNote) {
        return new CreateAnimalCommand(category, gender, color, 2, features, pointId,
                List.of("F2026112100013"), healthNote, null);
    }

    private Long firstPointId() {
        return jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1", Long.class);
    }

    private Long secondPointId() {
        return jdbc.queryForObject("SELECT id FROM base_point ORDER BY id LIMIT 1 OFFSET 1", Long.class);
    }

    private int sequenceOf(String code) {
        return Integer.parseInt(code.substring(code.lastIndexOf('-') + 1));
    }
}

