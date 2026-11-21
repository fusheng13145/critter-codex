package com.just.critter.controller.archive;

import com.just.critter.domain.enums.GroupStatus;
import com.just.critter.service.archive.CreatedGroup;
import com.just.critter.service.archive.CreatedSighting;
import com.just.critter.service.archive.GroupDetail;
import com.just.critter.service.archive.GroupService;
import com.just.critter.service.archive.GroupSummary;
import com.just.critter.service.archive.PointRef;
import com.just.critter.service.archive.SightingView;
import com.just.critter.service.archive.SpeciesRef;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T1.5 群体接口契约：发号、30504、分页壳、30410→404、目击挂靠。
 */
@SpringBootTest
@AutoConfigureMockMvc
class GroupControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GroupService groupService;

    @Test
    @DisplayName("POST /groups 返回群体编号与活跃状态")
    void create_returns_group_code() throws Exception {
        when(groupService.create(any(), anyLong()))
                .thenReturn(new CreatedGroup("GRP-2026-001", GroupStatus.ACTIVE));

        mvc.perform(post("/api/v1/groups")
                        .header("X-User-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"speciesId":1,"features":"黄褐色，尾部蓬松","pointIds":[7,8],
                                 "minSize":3,"maxSize":5,"sizeBasis":"两次目击取中位数"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.groupCode").value("GRP-2026-001"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /groups 规模区间非法 → 30504")
    void create_with_invalid_range_returns_30504() throws Exception {
        when(groupService.create(any(), anyLong())).thenThrow(new BusinessException(30504, "规模区间非法"));

        mvc.perform(post("/api/v1/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"speciesId":1,"features":"黄褐色","pointIds":[7],"minSize":6,"maxSize":5,"sizeBasis":"目测"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30504));
    }

    @Test
    @DisplayName("GET /groups 返回分页与规模区间")
    void list_returns_page() throws Exception {
        GroupSummary row = new GroupSummary("GRP-2026-001",
                new SpeciesRef(1L, "黄鼬", "哺乳纲"), "黄褐色，尾部蓬松", 3, 5, "两次目击取中位数",
                GroupStatus.ACTIVE, "活跃",
                List.of(new PointRef(7L, "东区绿化带")),
                OffsetDateTime.parse("2026-11-20T18:00:00+08:00"));
        when(groupService.search(any(), any())).thenReturn(PageResult.of(List.of(row), 1, 20, 1));

        mvc.perform(get("/api/v1/groups").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].groupCode").value("GRP-2026-001"))
                .andExpect(jsonPath("$.data.list[0].species.commonName").value("黄鼬"))
                .andExpect(jsonPath("$.data.list[0].minSize").value(3))
                .andExpect(jsonPath("$.data.page.total").value(1));
    }

    @Test
    @DisplayName("GET /groups/{code} 不存在 → 30410，HTTP 404")
    void detail_not_found_is_404() throws Exception {
        when(groupService.detail("GRP-2026-999")).thenThrow(new BusinessException(ErrorCode.RECORD_NOT_FOUND));

        mvc.perform(get("/api/v1/groups/GRP-2026-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30410));
    }

    @Test
    @DisplayName("PUT /groups/{code} 返回更新后的规模与点位")
    void update_returns_detail() throws Exception {
        GroupDetail detail = new GroupDetail("GRP-2026-001",
                new SpeciesRef(1L, "黄鼬", "哺乳纲"), "毛色偏灰，尾部蓬松", 2, 2, "合并后重新计数",
                GroupStatus.ACTIVE, "活跃", List.of(new PointRef(8L, "西区草坪")),
                OffsetDateTime.parse("2026-11-21T09:00:00+08:00"),
                OffsetDateTime.parse("2026-11-20T09:00:00+08:00"),
                OffsetDateTime.parse("2026-11-21T09:00:00+08:00"));
        when(groupService.update(eq("GRP-2026-001"), any(), anyLong())).thenReturn(detail);

        mvc.perform(put("/api/v1/groups/GRP-2026-001")
                        .header("X-User-Id", "1002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"features":"毛色偏灰，尾部蓬松","pointIds":[8],"minSize":2,"maxSize":2,
                                 "sizeBasis":"合并后重新计数"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minSize").value(2))
                .andExpect(jsonPath("$.data.points[0].id").value(8));
    }

    @Test
    @DisplayName("POST /groups/{code}/sightings 返回目击 id")
    void create_sighting_returns_id() throws Exception {
        when(groupService.recordSighting(eq("GRP-2026-001"), any(), anyLong()))
                .thenReturn(new CreatedSighting(100L));

        mvc.perform(post("/api/v1/groups/GRP-2026-001/sightings")
                        .header("X-User-Id", "1003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pointId":7,"seenAt":"2026-11-21T09:00:00+08:00",
                                 "countEstimate":4,"behavior":"沿围墙活动","fileIds":["F1"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sightingId").value(100));
    }

    @Test
    @DisplayName("GET /sightings 返回待归属标记与群体编号")
    void list_sightings_returns_page() throws Exception {
        SightingView view = new SightingView(100L, "GRP-2026-001", new PointRef(7L, "东区绿化带"),
                OffsetDateTime.parse("2026-11-21T09:00:00+08:00"), 4, "沿围墙活动",
                List.of("F1"), 1003L, "ASSIGNED",
                OffsetDateTime.parse("2026-11-21T09:10:00+08:00"));
        when(groupService.searchSightings(any(), any())).thenReturn(PageResult.of(List.of(view), 1, 20, 1));

        mvc.perform(get("/api/v1/sightings").param("assignStatus", "ASSIGNED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].groupCode").value("GRP-2026-001"))
                .andExpect(jsonPath("$.data.list[0].assignStatus").value("ASSIGNED"));
    }
}
