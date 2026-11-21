package com.just.critter.tnr;

import com.just.critter.support.api.PageResult;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.JwtService;
import com.just.critter.tnr.service.TnrNodeView;
import com.just.critter.tnr.service.TnrService;
import com.just.critter.tnr.service.TnrStage;
import com.just.critter.tnr.service.TnrTaskView;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TnrControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private TnrService tnrService;

    @Test
    @DisplayName("TNR 看板：按阶段分页返回任务与节点时间线")
    void listsTnrBoard() throws Exception {
        TnrTaskView task = task();
        when(tnrService.list(eq("CATCH"), eq("CAT-2026-031"), any(PageQuery.class)))
                .thenReturn(PageResult.of(List.of(task), 1, 20, 1));

        mockMvc.perform(get("/api/v1/tnr")
                        .param("stage", "CATCH")
                        .param("animalCode", "CAT-2026-031")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].tnrNo").value("TNR20261121001"))
                .andExpect(jsonPath("$.data.list[0].stage").value("CATCH"))
                .andExpect(jsonPath("$.data.list[0].nodes[0].stage").value("CATCH"))
                .andExpect(jsonPath("$.data.page.total").value(1));

        verify(tnrService).list(eq("CATCH"), eq("CAT-2026-031"), any(PageQuery.class));
    }

    @Test
    @DisplayName("TNR 看板：阶段非法返回 30303")
    void rejectsInvalidStageFilter() throws Exception {
        when(tnrService.list(eq("UNKNOWN"), any(), any(PageQuery.class)))
                .thenThrow(new BusinessException(ErrorCode.ENUM_INVALID));

        mockMvc.perform(get("/api/v1/tnr").param("stage", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30303));
    }

    @Test
    @DisplayName("管理员发起 TNR：返回编号与 CATCH 首节点")
    void createsTnr() throws Exception {
        when(tnrService.create(any(), eq(1001L))).thenReturn(task());

        mockMvc.perform(post("/api/v1/tnr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "animalId": 11,
                                  "handlerId": 9,
                                  "occurredAt": "2026-11-21T09:00:00+08:00",
                                  "note": "校园东区抓捕，装入诱捕笼",
                                  "fileIds": ["F20261121001"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tnrNo").value("TNR20261121001"))
                .andExpect(jsonPath("$.data.stage").value("CATCH"));

        verify(tnrService).create(any(), eq(1001L));
    }

    @Test
    @DisplayName("普通认养人不能发起 TNR，返回 20003")
    void rejectsStudentCreate() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(post("/api/v1/tnr")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"animalId":11,"occurredAt":"2026-11-21T09:00:00+08:00"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    @DisplayName("管理员推进节点：返回新阶段与追加后的时间线")
    void advancesNode() throws Exception {
        TnrTaskView advanced = new TnrTaskView(
                "TNR20261121001", 11L, "CAT-2026-031", "三花",
                TnrStage.TRANSPORT, 9L, "王医生",
                LocalDateTime.of(2026, 11, 21, 10, 0), "送往合作医院",
                List.of("F20261121001"), LocalDateTime.of(2026, 11, 21, 9, 0),
                List.of(node(TnrStage.CATCH, 9, 9), node(TnrStage.TRANSPORT, 10, 10)));
        when(tnrService.advance(eq("TNR20261121001"), any(), eq(1001L))).thenReturn(advanced);

        mockMvc.perform(put("/api/v1/tnr/TNR20261121001/node")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "TRANSPORT",
                                  "handlerId": 9,
                                  "occurredAt": "2026-11-21T10:00:00+08:00",
                                  "note": "送往合作医院"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.stage").value("TRANSPORT"))
                .andExpect(jsonPath("$.data.nodes[1].stage").value("TRANSPORT"));

        verify(tnrService).advance(eq("TNR20261121001"), any(), eq(1001L));
    }

    @Test
    @DisplayName("普通认养人不能推进 TNR 节点，返回 20003")
    void rejectsStudentAdvance() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(put("/api/v1/tnr/TNR20261121001/node")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stage":"TRANSPORT","occurredAt":"2026-11-21T10:00:00+08:00"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }

    private TnrTaskView task() {
        return new TnrTaskView(
                "TNR20261121001", 11L, "CAT-2026-031", "三花",
                TnrStage.CATCH, 9L, "王医生",
                LocalDateTime.of(2026, 11, 21, 9, 0), "校园东区抓捕",
                List.of("F20261121001"), LocalDateTime.of(2026, 11, 21, 9, 0),
                List.of(node(TnrStage.CATCH, 9, 9)));
    }

    private TnrNodeView node(TnrStage stage, int hour, int minute) {
        return new TnrNodeView(stage, 9L, "王医生",
                LocalDateTime.of(2026, 11, 21, hour, minute),
                "节点备注", List.of("F20261121001"));
    }
}
