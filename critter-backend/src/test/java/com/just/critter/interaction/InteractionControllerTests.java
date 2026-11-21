package com.just.critter.interaction;

import com.just.critter.interaction.service.CommentCreateCommand;
import com.just.critter.interaction.service.CommentReplyCommand;
import com.just.critter.interaction.service.CommentService;
import com.just.critter.interaction.service.CommentStatus;
import com.just.critter.interaction.service.CommentView;
import com.just.critter.interaction.service.FollowCommand;
import com.just.critter.interaction.service.FollowRemoval;
import com.just.critter.interaction.service.FollowService;
import com.just.critter.interaction.service.FollowView;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.security.JwtService;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InteractionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private CommentService commentService;

    @MockitoBean
    private FollowService followService;

    @Test
    @DisplayName("发表留言：登录用户提交后返回状态与作者昵称")
    void createsComment() throws Exception {
        when(commentService.create(any(CommentCreateCommand.class), eq(1001L)))
                .thenReturn(comment(7L, CommentStatus.VISIBLE));

        mockMvc.perform(post("/api/v1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizType":"ANIMAL","bizId":"CAT-2026-031","content":"请问这只猫最近还好吗？"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.commentId").value(7))
                .andExpect(jsonPath("$.data.authorName").value("小林"))
                .andExpect(jsonPath("$.data.status").value("VISIBLE"));
    }

    @Test
    @DisplayName("发表留言：未登录返回 401")
    void rejectsAnonymousCreate() throws Exception {
        mockMvc.perform(post("/api/v1/comments")
                        .header("Authorization", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizType":"ANIMAL","bizId":"CAT-2026-031","content":"留言"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    @DisplayName("公开留言列表：匿名可读，仅返回服务层裁剪后的可见内容")
    void listsCommentsPublicly() throws Exception {
        when(commentService.list(eq("ANIMAL"), eq("CAT-2026-031"), any(PageQuery.class),
                eq(null), eq(false)))
                .thenReturn(PageResult.of(List.of(comment(7L, CommentStatus.VISIBLE)), 1, 20, 1));

        mockMvc.perform(get("/api/v1/comments")
                        .header("Authorization", "")
                        .param("bizType", "ANIMAL")
                        .param("bizId", "CAT-2026-031"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].commentId").value(7))
                .andExpect(jsonPath("$.data.page.total").value(1));

        verify(commentService).list(eq("ANIMAL"), eq("CAT-2026-031"), any(PageQuery.class),
                eq(null), eq(false));
    }

    @Test
    @DisplayName("回复留言：管理员可设置仅本人可见，返回回复内容")
    void repliesAsAdmin() throws Exception {
        CommentView replied = new CommentView(9L, "ANIMAL", "CAT-2026-031",
                1001L, "小林", "加微信详聊", CommentStatus.VISIBLE,
                "已核实，请放心。", true, LocalDateTime.of(2026, 11, 21, 12, 0));
        when(commentService.reply(eq(9L), any(CommentReplyCommand.class))).thenReturn(replied);

        mockMvc.perform(put("/api/v1/comments/9/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"replyContent":"已核实，请放心。","visible":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.replyContent").value("已核实，请放心。"))
                .andExpect(jsonPath("$.data.replyVisible").value(true));
    }

    @Test
    @DisplayName("回复留言：普通用户返回 403")
    void rejectsNonAdminReply() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(put("/api/v1/comments/9/reply")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"replyContent":"回复","visible":false}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    @DisplayName("关注：登录用户首次关注返回 newlyCreated=true")
    void followsTarget() throws Exception {
        when(followService.follow(any(FollowCommand.class), eq(1001L)))
                .thenReturn(new FollowView(12L, "ANIMAL", "CAT-2026-031",
                        LocalDateTime.of(2026, 11, 21, 14, 0), true));

        mockMvc.perform(post("/api/v1/follows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizType":"ANIMAL","bizId":"CAT-2026-031"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.followId").value(12))
                .andExpect(jsonPath("$.data.newlyCreated").value(true));
    }

    @Test
    @DisplayName("关注：未登录返回 401")
    void rejectsAnonymousFollow() throws Exception {
        mockMvc.perform(post("/api/v1/follows")
                        .header("Authorization", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizType":"ANIMAL","bizId":"CAT-2026-031"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20001));
    }

    @Test
    @DisplayName("取消关注：本人关系删除后返回 removed=true")
    void unfollowsTarget() throws Exception {
        when(followService.unfollow(12L, 1001L)).thenReturn(new FollowRemoval(12L, true));

        mockMvc.perform(delete("/api/v1/follows/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.followId").value(12))
                .andExpect(jsonPath("$.data.removed").value(true));
    }

    private CommentView comment(long id, CommentStatus status) {
        return new CommentView(id, "ANIMAL", "CAT-2026-031", 1001L, "小林",
                "请问这只猫最近还好吗？", status, null, false,
                LocalDateTime.of(2026, 11, 21, 12, 0));
    }
}
