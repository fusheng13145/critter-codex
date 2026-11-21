package com.just.critter.message;

import com.just.critter.message.service.MessageQueryService;
import com.just.critter.message.service.MessageReadFailure;
import com.just.critter.message.service.MessageReadResult;
import com.just.critter.message.service.MessageReadService;
import com.just.critter.message.service.MessageUnreadCount;
import com.just.critter.message.service.MessageView;
import com.just.critter.support.api.PageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageQueryService queryService;

    @MockitoBean
    private MessageReadService readService;

    @Test
    @DisplayName("消息列表：返回统一分页结构与消息业务跳转字段")
    void listsMessages() throws Exception {
        when(queryService.list(eq(1001L), eq(false), eq("APPROVAL_RESULT"), any()))
                .thenReturn(PageResult.of(List.of(new MessageView(1L, "APPROVAL_RESULT",
                        "认养申请已通过", "ADOPTION", "AD20261121003", false,
                        null, LocalDateTime.of(2026, 11, 21, 10, 5))), 1, 20, 1));

        mockMvc.perform(get("/api/v1/messages")
                        .param("readFlag", "false")
                        .param("type", "APPROVAL_RESULT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].type").value("APPROVAL_RESULT"))
                .andExpect(jsonPath("$.data.list[0].bizType").value("ADOPTION"))
                .andExpect(jsonPath("$.data.list[0].bizId").value("AD20261121003"))
                .andExpect(jsonPath("$.data.page.total").value(1));

        verify(queryService).list(eq(1001L), eq(false), eq("APPROVAL_RESULT"), any());
    }

    @Test
    @DisplayName("四类未读数：返回固定四个分组")
    void readsUnreadCounts() throws Exception {
        when(queryService.unread(1001L)).thenReturn(new MessageUnreadCount(1L, 2L, 3L, 4L));

        mockMvc.perform(get("/api/v1/messages/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adoptionExpiry").value(1))
                .andExpect(jsonPath("$.data.donationProgress").value(2))
                .andExpect(jsonPath("$.data.orderProgress").value(3))
                .andExpect(jsonPath("$.data.approvalResult").value(4));

        verify(queryService).unread(1001L);
    }

    @Test
    @DisplayName("批量已读：返回成功数与逐条失败信息")
    void marksMessagesRead() throws Exception {
        when(readService.markRead(eq(1001L), eq(List.of(1L, 9L)))).thenReturn(
                new MessageReadResult(1, List.of(new MessageReadFailure("9", 30410, "记录不存在"))));

        mockMvc.perform(put("/api/v1/messages/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":[1,9]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(1))
                .andExpect(jsonPath("$.data.failed[0].id").value("9"))
                .andExpect(jsonPath("$.data.failed[0].code").value(30410));

        verify(readService).markRead(eq(1001L), eq(List.of(1L, 9L)));
    }

    @Test
    @DisplayName("消息中心必须登录，缺少令牌返回 20001")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/messages").header("Authorization", ""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20001));
    }
}
