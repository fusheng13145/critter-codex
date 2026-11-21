package com.just.critter.integration;

import com.just.critter.notice.service.NoticeCommand;
import com.just.critter.notice.service.NoticeService;
import com.just.critter.notice.service.NoticeView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
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
class NoticeIT extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NoticeService noticeService;

    @Test
    @DisplayName("真实 MySQL：公告落库、公开列表置顶排序与详情正文")
    void publishesAndReadsNotice() {
        long authorId = jdbc.queryForObject("SELECT id FROM sys_user ORDER BY id LIMIT 1", Long.class);
        String authorName = jdbc.queryForObject(
                "SELECT nickname FROM sys_user WHERE id = ?", String.class, authorId);

        NoticeView pinned = noticeService.publish(new NoticeCommand(
                "SCIENCE", "校园鸟类观察", "本周记录到三种候鸟。", true), authorId);
        NoticeView normal = noticeService.publish(new NoticeCommand(
                "SCIENCE", "秋日投喂提示", "请勿在图书馆后门集中投喂。", false), authorId);

        assertThat(pinned.noticeNo()).startsWith("NT");
        assertThat(normal.noticeNo()).isNotEqualTo(pinned.noticeNo());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notice WHERE category = 'SCIENCE'", Integer.class)).isEqualTo(2);

        PageResult<NoticeView> page = noticeService.list("SCIENCE", new PageQuery(1, 10));
        assertThat(page.getList()).hasSize(2);
        assertThat(page.getList().get(0).noticeId()).isEqualTo(pinned.noticeId());
        assertThat(page.getList().get(0).pinned()).isTrue();

        var detail = noticeService.detail(pinned.noticeId());
        assertThat(detail.content()).isEqualTo("本周记录到三种候鸟。");
        assertThat(detail.authorName()).isEqualTo(authorName);
        assertThat(detail.publishedAt()).isNotNull();
    }
}
