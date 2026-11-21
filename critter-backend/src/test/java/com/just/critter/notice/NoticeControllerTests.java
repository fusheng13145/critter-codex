package com.just.critter.notice;

import com.just.critter.notice.service.NoticeDetail;
import com.just.critter.notice.service.NoticeService;
import com.just.critter.notice.service.NoticeView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NoticeControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private NoticeService noticeService;

    @Test
    @DisplayName("公告列表公开可读，即使携带无效令牌也不拦截")
    void listsNoticesPublicly() throws Exception {
        when(noticeService.list(eq("PROGRESS"), any(PageQuery.class))).thenReturn(
                PageResult.of(List.of(new NoticeView(7L, "NT20261121001", "PROGRESS",
                        "TNR 进展", true, LocalDateTime.of(2026, 11, 20, 18, 0))), 1, 10, 1));

        mockMvc.perform(get("/api/v1/notices")
                        .param("category", "PROGRESS")
                        .param("page", "1")
                        .param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].noticeNo").value("NT20261121001"))
                .andExpect(jsonPath("$.data.list[0].pinned").value(true))
                .andExpect(jsonPath("$.data.page.total").value(1));

        verify(noticeService).list(eq("PROGRESS"), any(PageQuery.class));
    }

    @Test
    @DisplayName("公告详情公开可读")
    void readsNoticeDetailPublicly() throws Exception {
        when(noticeService.detail(7L)).thenReturn(new NoticeDetail(
                7L, "NT20261121001", "PROGRESS", "TNR 进展",
                "本周完成三只猫的抓捕与送医。", 9001L, "纪老师", true,
                LocalDateTime.of(2026, 11, 20, 18, 0), LocalDateTime.of(2026, 11, 20, 18, 0)));

        mockMvc.perform(get("/api/v1/notices/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noticeNo").value("NT20261121001"))
                .andExpect(jsonPath("$.data.content").value("本周完成三只猫的抓捕与送医。"))
                .andExpect(jsonPath("$.data.authorName").value("纪老师"));
    }

    @Test
    @DisplayName("公告不存在返回 30410")
    void rejectsMissingNotice() throws Exception {
        when(noticeService.detail(99L)).thenThrow(new BusinessException(ErrorCode.RECORD_NOT_FOUND));

        mockMvc.perform(get("/api/v1/notices/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30410));
    }

    @Test
    @DisplayName("管理员发布公告：返回公告编号")
    void publishesNoticeAsAdmin() throws Exception {
        when(noticeService.publish(any(), eq(1001L))).thenReturn(new NoticeView(
                8L, "NT20261121001", "PROGRESS", "TNR 进展", true,
                LocalDateTime.of(2026, 11, 21, 12, 0)));

        mockMvc.perform(post("/api/v1/notices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "PROGRESS",
                                  "title": "TNR 进展",
                                  "content": "本周完成三只猫的抓捕与送医。",
                                  "pinned": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.noticeNo").value("NT20261121001"))
                .andExpect(jsonPath("$.data.pinned").value(true));

        verify(noticeService).publish(any(), eq(1001L));
    }

    @Test
    @DisplayName("普通学生不能发布公告，返回 20003")
    void rejectsStudentPublish() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(post("/api/v1/notices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"SCIENCE","title":"校园鸟类","content":"记录三种候鸟。"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    @DisplayName("公告分类非法返回 30303")
    void rejectsUnknownCategory() throws Exception {
        when(noticeService.list(eq("UNKNOWN"), any(PageQuery.class)))
                .thenThrow(new BusinessException(ErrorCode.ENUM_INVALID));

        mockMvc.perform(get("/api/v1/notices").param("category", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30303));
    }
}
