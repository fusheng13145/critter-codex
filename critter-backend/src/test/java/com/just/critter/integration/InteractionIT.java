package com.just.critter.integration;

import com.just.critter.interaction.service.CommentCreateCommand;
import com.just.critter.interaction.service.CommentReplyCommand;
import com.just.critter.interaction.service.CommentService;
import com.just.critter.interaction.service.CommentStatus;
import com.just.critter.interaction.service.CommentView;
import com.just.critter.interaction.service.FollowCommand;
import com.just.critter.interaction.service.FollowService;
import com.just.critter.interaction.service.FollowView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/critter_codex_it"
                + "?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8"
                + "&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
        "spring.datasource.username=${CRITTER_MYSQL_USER:root}",
        "spring.datasource.password=${CRITTER_MYSQL_PASSWORD}",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true"
})
@EnabledIfEnvironmentVariable(named = "CRITTER_MYSQL_IT", matches = "true")
@Transactional
class InteractionIT extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CommentService commentService;

    @Autowired
    private FollowService followService;

    @Test
    @DisplayName("真实 MySQL：敏感留言待审、作者可见、管理员回复转公开；关注幂等且可取消")
    void commentAuditAndFollowRoundTrip() {
        long pointId = insertPoint();
        long animalId = insertAnimal(pointId);
        String animalCode = jdbc.queryForObject(
                "SELECT code FROM animal WHERE id = ?", String.class, animalId);

        CommentView visible = commentService.create(new CommentCreateCommand(
                "ANIMAL", animalCode, "请问这只猫最近还好吗？"), 1L);
        CommentView pending = commentService.create(new CommentCreateCommand(
                "ANIMAL", animalCode, "加微信详聊，代开发票"), 1L);

        assertThat(visible.status()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(pending.status()).isEqualTo(CommentStatus.PENDING_AUDIT);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM comment WHERE id = ?", String.class, pending.commentId()))
                .isEqualTo("PENDING_AUDIT");

        PageResult<CommentView> anonymous = commentService.list(
                "ANIMAL", animalCode, new PageQuery(), null, false);
        assertThat(anonymous.getList()).extracting(CommentView::commentId)
                .containsExactly(visible.commentId());

        PageResult<CommentView> author = commentService.list(
                "ANIMAL", animalCode, new PageQuery(), 1L, false);
        assertThat(author.getList()).extracting(CommentView::commentId)
                .contains(visible.commentId(), pending.commentId());

        AuthContext.set(new CurrentUser(1L, "ADMIN-001", "管理员", "SYSTEM_ADMIN",
                Set.of("SYSTEM_ADMIN"), "平台", "it-jwt", Instant.now().plusSeconds(3600)));
        try {
            CommentView replied = commentService.reply(pending.commentId(),
                    new CommentReplyCommand("已核实，请放心。", true));
            assertThat(replied.status()).isEqualTo(CommentStatus.VISIBLE);
            assertThat(replied.replyContent()).isEqualTo("已核实，请放心。");
        } finally {
            AuthContext.clear();
        }

        PageResult<CommentView> afterReply = commentService.list(
                "ANIMAL", animalCode, new PageQuery(), null, false);
        assertThat(afterReply.getList()).extracting(CommentView::commentId)
                .contains(visible.commentId(), pending.commentId());

        FollowView first = followService.follow(new FollowCommand("ANIMAL", animalCode), 1L);
        FollowView second = followService.follow(new FollowCommand("ANIMAL", animalCode), 1L);
        assertThat(first.newlyCreated()).isTrue();
        assertThat(second.followId()).isEqualTo(first.followId());
        assertThat(second.newlyCreated()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM follow WHERE user_id = 1 AND biz_type = 'ANIMAL' AND biz_id = ?",
                Integer.class, animalCode)).isEqualTo(1);

        followService.unfollow(first.followId(), 1L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM follow WHERE id = ?", Integer.class, first.followId()))
                .isZero();
    }

    private long insertPoint() {
        jdbc.update("INSERT INTO base_point (point_name, enabled) VALUES (?, 1)",
                "留言集成测试点位-" + shortSuffix());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertAnimal(long pointId) {
        jdbc.update("""
                INSERT INTO animal (code, category, gender, color, features, point_id, status)
                VALUES (?, 'CAT', 'UNKNOWN', '狸花', '留言集成测试个体', ?, 'ON_CAMPUS')
                """, "CAT-INT-" + shortSuffix(), pointId);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private String shortSuffix() {
        String nano = String.valueOf(System.nanoTime());
        return nano.substring(nano.length() - 8);
    }
}
