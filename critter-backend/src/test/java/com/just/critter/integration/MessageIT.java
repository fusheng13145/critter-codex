package com.just.critter.integration;

import com.just.critter.message.service.MessageReadResult;
import com.just.critter.message.service.MessageReadService;
import com.just.critter.message.service.MessageQueryService;
import com.just.critter.message.service.MessageUnreadCount;
import com.just.critter.message.service.MessageView;
import com.just.critter.support.api.PageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
class MessageIT extends MySqlIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MessageQueryService queryService;

    @Autowired
    private MessageReadService readService;

    @Test
    @DisplayName("真实 MySQL：消息按用户、已读、类型与 180 天窗口查询，批量已读部分成功")
    void queriesAndMarksMessages() {
        long userId = insertUser();
        long approvalId = insertMessage(userId, "APPROVAL_RESULT", "认养申请已通过", false,
                LocalDateTime.now().minusMinutes(5));
        long orderId = insertMessage(userId, "ORDER_PROGRESS", "工单已受理", true,
                LocalDateTime.now().minusHours(1));
        insertMessage(userId, "DONATION_PROGRESS", "200 天前的旧消息", false,
                LocalDateTime.now().minusDays(200));

        PageResultView unread = page(queryService.list(userId, false, null, new PageQuery(1, 20)));
        assertThat(unread.items()).extracting(MessageView::id).containsExactly(approvalId);
        assertThat(unread.total()).isEqualTo(1);

        MessageUnreadCount counts = queryService.unread(userId);
        assertThat(counts.approvalResult()).isEqualTo(1);
        assertThat(counts.orderProgress()).isZero();
        assertThat(counts.donationProgress()).isZero();
        assertThat(counts.adoptionExpiry()).isZero();

        MessageReadResult result = readService.markRead(userId, List.of(approvalId, 999999L));
        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().get(0).code()).isEqualTo(30410);
        assertThat(jdbc.queryForObject(
                "SELECT read_flag FROM message WHERE id = ?", Integer.class, approvalId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT read_at IS NOT NULL FROM message WHERE id = ?", Integer.class, approvalId)).isEqualTo(1);

        var read = page(queryService.list(userId, true, null, new PageQuery(1, 20)));
        assertThat(read.items()).extracting(MessageView::id).contains(approvalId, orderId);
        assertThat(read.total()).isEqualTo(2);
    }

    private long insertUser() {
        String suffix = shortSuffix();
        jdbc.update("""
                INSERT INTO sys_user (user_no, email, password_hash, nickname, primary_role, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, "STU-MSG-" + suffix, "msg" + suffix + "@test.local",
                "test-hash", "消息用户", "STUDENT", "ACTIVE");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long insertMessage(long receiverId, String type, String title,
                               boolean read, LocalDateTime createdAt) {
        jdbc.update("""
                INSERT INTO message (receiver_id, type, title, biz_type, biz_id,
                                     read_flag, read_at, created_at)
                VALUES (?, ?, ?, 'ADOPTION', 'AD20261121999', ?, ?, ?)
                """, receiverId, type, title, read ? 1 : 0,
                read ? createdAt.plusMinutes(1) : null, createdAt);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private PageResultView page(com.just.critter.support.api.PageResult<MessageView> result) {
        return new PageResultView(result.getList(), result.getPage().getTotal());
    }

    private String shortSuffix() {
        String nano = String.valueOf(System.nanoTime());
        return nano.substring(nano.length() - 8);
    }

    private record PageResultView(List<MessageView> items, long total) {
    }
}
