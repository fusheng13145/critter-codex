package com.just.critter.integration;

import com.just.critter.support.exception.BusinessException;
import com.just.critter.tnr.service.TnrCommand;
import com.just.critter.tnr.service.TnrNodeCommand;
import com.just.critter.tnr.service.TnrService;
import com.just.critter.tnr.service.TnrStage;
import com.just.critter.tnr.service.TnrTaskView;
import com.just.critter.support.api.PageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class TnrIT extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TnrService tnrService;

    @Test
    @DisplayName("真实 MySQL：TNR 五节点顺序推进、节点留痕、末节点同步绝育标记")
    void advancesFiveStagesAndMarksNeutered() {
        long pointId = insertPoint();
        long animalId = insertAnimal(pointId);
        OffsetDateTime base = OffsetDateTime.now().minusHours(6);

        TnrTaskView created = tnrService.create(new TnrCommand(
                animalId, 1L, base, "校园东区抓捕，装入诱捕笼",
                List.of("F20261121001")), 9001L);

        assertThat(created.tnrNo()).startsWith("TNR");
        assertThat(created.stage()).isEqualTo(TnrStage.CATCH);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tnr_node WHERE tnr_no = ?", Integer.class, created.tnrNo()))
                .isEqualTo(1);

        assertThatThrownBy(() -> tnrService.advance(created.tnrNo(), new TnrNodeCommand(
                "SURGERY", 1L, base.plusHours(2), "跳节点", List.of("F1")), 9001L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30402));

        tnrService.advance(created.tnrNo(), new TnrNodeCommand(
                "TRANSPORT", 1L, base.plusHours(1), "送往合作医院", List.of()), 9001L);
        tnrService.advance(created.tnrNo(), new TnrNodeCommand(
                "SURGERY", 1L, base.plusHours(2), "完成绝育手术",
                List.of("F20261121002")), 9001L);
        tnrService.advance(created.tnrNo(), new TnrNodeCommand(
                "RELEASE", 1L, base.plusHours(3), "送回校园", List.of()), 9001L);
        TnrTaskView completed = tnrService.advance(created.tnrNo(), new TnrNodeCommand(
                "OBSERVE", 1L, base.plusHours(4), "观察期结束", List.of()), 9001L);

        assertThat(completed.stage()).isEqualTo(TnrStage.OBSERVE);
        assertThat(completed.nodes()).extracting(node -> node.stage())
                .containsExactly(TnrStage.CATCH, TnrStage.TRANSPORT,
                        TnrStage.SURGERY, TnrStage.RELEASE, TnrStage.OBSERVE);
        assertThat(jdbc.queryForObject(
                "SELECT stage FROM tnr_task WHERE tnr_no = ?", String.class, created.tnrNo()))
                .isEqualTo("OBSERVE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM tnr_node WHERE tnr_no = ?", Integer.class, created.tnrNo()))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT neutered FROM animal WHERE id = ?", Integer.class, animalId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT neutered_at FROM animal WHERE id = ?", LocalDate.class, animalId))
                .isEqualTo(LocalDate.now());

        var board = tnrService.list("OBSERVE", completed.animalCode(), new PageQuery());
        assertThat(board.getList()).extracting(task -> task.tnrNo())
                .contains(created.tnrNo());
    }

    private long insertPoint() {
        jdbc.update("INSERT INTO base_point (point_name, enabled) VALUES (?, 1)",
                "TNR 集成测试点位-" + shortSuffix());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertAnimal(long pointId) {
        jdbc.update("""
                INSERT INTO animal (code, category, gender, color, features, point_id, status)
                VALUES (?, 'CAT', 'UNKNOWN', '狸花', 'TNR 集成测试个体', ?, 'ON_CAMPUS')
                """, "CAT-TNR-" + shortSuffix(), pointId);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String shortSuffix() {
        String nano = String.valueOf(System.nanoTime());
        return nano.substring(nano.length() - 8);
    }
}
