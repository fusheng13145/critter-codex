package com.just.critter.integration;

import com.just.critter.domain.enums.GroupStatus;
import com.just.critter.domain.enums.OccurrenceScale;
import com.just.critter.domain.enums.Season;
import com.just.critter.service.archive.BiodiversityService;
import com.just.critter.service.archive.CreateGroupCommand;
import com.just.critter.service.archive.CreateOccurrenceCommand;
import com.just.critter.service.archive.CreateSightingCommand;
import com.just.critter.service.archive.CreatedGroup;
import com.just.critter.service.archive.CreatedOccurrence;
import com.just.critter.service.archive.CreatedSighting;
import com.just.critter.service.archive.GroupDetail;
import com.just.critter.service.archive.GroupService;
import com.just.critter.service.archive.OccurrenceFilter;
import com.just.critter.service.archive.OccurrenceView;
import com.just.critter.support.api.PageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T1.5 真库验收：群体发号与 JSON 点位、目击刷新最近目击、出现记录季节筛选。
 * 默认跳过，设 CRITTER_MYSQL_IT=true 运行；事务回滚。
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
class GroupAndBiodiversityIT extends MySqlIntegrationTestSupport {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private GroupService groupService;
    @Autowired
    private BiodiversityService biodiversityService;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("群体：连续建档编号递增，JSON 点位与规模区间落库，详情带物种与点位名")
    void create_group_and_read_detail() {
        Long speciesId = firstId("base_species");
        Long pointA = firstId("base_point");
        Long pointB = secondId("base_point");

        CreatedGroup first = groupService.create(command(speciesId, List.of(pointA, pointB)), 1001L);
        CreatedGroup second = groupService.create(command(speciesId, List.of(pointA)), 1001L);

        assertThat(first.groupCode()).matches("GRP-2026-\\d{3,}");
        assertThat(second.groupCode()).matches("GRP-2026-\\d{3,}");
        assertThat(sequenceOf(second.groupCode())).isGreaterThan(sequenceOf(first.groupCode()));
        assertThat(first.status()).isEqualTo(GroupStatus.ACTIVE);

        String json = jdbc.queryForObject("SELECT point_ids FROM animal_group WHERE group_code = ?",
                String.class, first.groupCode());
        assertThat(json).contains(String.valueOf(pointA)).contains(String.valueOf(pointB));

        GroupDetail detail = groupService.detail(first.groupCode());
        assertThat(detail.species().commonName()).isNotBlank();
        assertThat(detail.points()).extracting(point -> point.id()).containsExactly(pointA, pointB);
        assertThat(detail.minSize()).isEqualTo(3);
        assertThat(detail.maxSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("目击：挂靠群体并刷新 last_seen_at，记录为已归属")
    void sighting_updates_last_seen() {
        Long speciesId = firstId("base_species");
        Long pointId = firstId("base_point");
        CreatedGroup group = groupService.create(command(speciesId, List.of(pointId)), 1001L);
        OffsetDateTime seenAt = OffsetDateTime.now(ZONE).minusHours(1).withNano(0);

        CreatedSighting sighting = groupService.recordSighting(group.groupCode(),
                new CreateSightingCommand(pointId, seenAt, 4, "沿围墙活动", List.of("F1")), 1002L);

        assertThat(sighting.sightingId()).isNotNull();
        LocalDateTime storedLastSeen = jdbc.queryForObject(
                "SELECT last_seen_at FROM animal_group WHERE group_code = ?", LocalDateTime.class, group.groupCode());
        assertThat(storedLastSeen).isEqualTo(seenAt.atZoneSameInstant(ZONE).toLocalDateTime());

        String assignStatus = jdbc.queryForObject(
                "SELECT assign_status FROM sighting WHERE id = ?", String.class, sighting.sightingId());
        assertThat(assignStatus).isEqualTo("ASSIGNED");
    }

    @Test
    @DisplayName("出现记录：落库，春季筛选只返回 3—5 月记录")
    void occurrence_season_filter() {
        Long speciesId = firstId("base_species");
        Long pointId = firstId("base_point");

        CreatedOccurrence spring = biodiversityService.createOccurrence(new CreateOccurrenceCommand(
                speciesId, pointId, OffsetDateTime.parse("2026-04-18T08:30:00+08:00"),
                OccurrenceScale.SMALL_HERD, "灌木丛", List.of("F1")), 1001L);
        biodiversityService.createOccurrence(new CreateOccurrenceCommand(
                speciesId, pointId, OffsetDateTime.parse("2026-12-18T08:30:00+08:00"),
                OccurrenceScale.FEW, "灌木丛", List.of("F2")), 1001L);

        assertThat(spring.occurrenceId()).isNotNull();
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM occurrence WHERE id = ?",
                Integer.class, spring.occurrenceId());
        assertThat(rows).isEqualTo(1);

        List<OccurrenceView> springRows = biodiversityService.searchOccurrences(
                new OccurrenceFilter(speciesId, pointId, Season.SPRING, null), new PageQuery(1, 20)).getList();
        assertThat(springRows).extracting(OccurrenceView::id).containsExactly(spring.occurrenceId());
        assertThat(springRows.getFirst().scaleLabel()).isEqualTo("小群");
    }

    private CreateGroupCommand command(Long speciesId, List<Long> pointIds) {
        return new CreateGroupCommand(speciesId, "黄褐色，尾部蓬松", pointIds, 3, 5, "两次目击取中位数");
    }

    private Long firstId(String table) {
        return jdbc.queryForObject("SELECT id FROM " + table + " ORDER BY id LIMIT 1", Long.class);
    }

    private Long secondId(String table) {
        return jdbc.queryForObject("SELECT id FROM " + table + " ORDER BY id LIMIT 1 OFFSET 1", Long.class);
    }

    private int sequenceOf(String code) {
        return Integer.parseInt(code.substring(code.lastIndexOf('-') + 1));
    }
}
