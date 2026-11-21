package com.just.critter.volunteer;

import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.security.JwtService;
import com.just.critter.volunteer.service.TaskCheckinCommand;
import com.just.critter.volunteer.service.TaskCheckinView;
import com.just.critter.volunteer.service.TaskCommand;
import com.just.critter.volunteer.service.TaskService;
import com.just.critter.volunteer.service.TaskSignupView;
import com.just.critter.volunteer.service.TaskView;
import com.just.critter.volunteer.service.VolunteerTaskView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TaskService taskService;

    @Test
    @DisplayName("任务列表：按状态分页返回")
    void listsTasks() throws Exception {
        when(taskService.list(eq("OPEN"), eq(null), any(PageQuery.class), eq(1001L)))
                .thenReturn(PageResult.of(List.of(taskView()), 1, 20, 1));

        mockMvc.perform(get("/api/v1/tasks").param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].taskNo").value("TASK20261121001"))
                .andExpect(jsonPath("$.data.page.total").value(1));
    }

    @Test
    @DisplayName("发布任务：管理员提交后返回任务")
    void createsTask() throws Exception {
        when(taskService.create(any(TaskCommand.class), eq(1001L))).thenReturn(taskView());

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"周末投喂","type":"FEED","pointId":3,
                                 "startAt":"2026-11-22T09:00:00","quota":2,
                                 "durationHours":2.5,"description":"带好手套"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskNo").value("TASK20261121001"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    @DisplayName("发布任务：普通用户返回 403")
    void rejectsNonAdminCreate() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(post("/api/v1/tasks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"任务","type":"FEED","startAt":"2026-11-22T09:00:00",
                                 "quota":2,"durationHours":2}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    @DisplayName("报名：志愿者加入成功返回 joinedByMe=true")
    void joinsTask() throws Exception {
        when(taskService.join(eq(4L), eq(1001L))).thenReturn(taskViewJoined());

        mockMvc.perform(post("/api/v1/tasks/4/join"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.joinedByMe").value(true));
    }

    @Test
    @DisplayName("报名：ADOPTER 角色返回 403")
    void rejectsNonVolunteerJoin() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(post("/api/v1/tasks/4/join")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    @DisplayName("打卡：提交照片后返回服务时长")
    void checksIn() throws Exception {
        when(taskService.checkin(eq(4L), any(TaskCheckinCommand.class), eq(1001L)))
                .thenReturn(new TaskCheckinView(4L, "TASK20261121001", "周末投喂",
                        LocalDateTime.of(2026, 11, 22, 9, 30), new BigDecimal("2.5"),
                        new BigDecimal("10.5")));

        mockMvc.perform(post("/api/v1/tasks/4/checkin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"photo":"file-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hours").value(2.5))
                .andExpect(jsonPath("$.data.totalHours").value(10.5));
    }

    @Test
    @DisplayName("我的任务：返回志愿者状态与总时长")
    void listsMine() throws Exception {
        when(taskService.mine(1001L, 1, 20)).thenReturn(new VolunteerTaskView(
                "ACTIVE", new BigDecimal("8.0"), 1,
                List.of(new TaskSignupView(21L, 4L, "TASK20261121001", "周末投喂",
                        LocalDateTime.of(2026, 11, 22, 9, 0),
                        LocalDateTime.of(2026, 11, 21, 10, 0), null,
                        new BigDecimal("0.0"), "OPEN", true, false))));

        mockMvc.perform(get("/api/v1/tasks/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.volunteerStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.totalHours").value(8.0))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    private TaskView taskView() {
        return new TaskView(4L, "TASK20261121001", "周末投喂", "FEED", "带好手套",
                3L, "一号点位", LocalDateTime.of(2026, 11, 22, 9, 0),
                2, 0, new BigDecimal("2.5"), "OPEN", false, null, null);
    }

    private TaskView taskViewJoined() {
        return new TaskView(4L, "TASK20261121001", "周末投喂", "FEED", "带好手套",
                3L, "一号点位", LocalDateTime.of(2026, 11, 22, 9, 0),
                2, 1, new BigDecimal("2.5"), "OPEN", true,
                LocalDateTime.of(2026, 11, 21, 10, 0), null);
    }
}
