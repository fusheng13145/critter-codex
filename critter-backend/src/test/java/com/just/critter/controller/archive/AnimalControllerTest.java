package com.just.critter.controller.archive;

import com.just.critter.domain.archive.AnimalCategory;
import com.just.critter.domain.enums.AnimalStatus;
import com.just.critter.service.archive.AnimalService;
import com.just.critter.service.archive.AnimalSummary;
import com.just.critter.service.archive.CreatedAnimal;
import com.just.critter.service.archive.PointRef;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T1.2 接口契约：成功壳、分页壳、30410→404、30401/30411→200。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnimalControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AnimalService animalService;

    @Test
    @DisplayName("POST /animals 返回编号、状态与创建时间")
    void create_returns_code_and_status() throws Exception {
        when(animalService.create(any(), anyLong())).thenReturn(new CreatedAnimal(
                "CAT-2026-001", AnimalStatus.ON_CAMPUS, OffsetDateTime.parse("2026-11-21T10:31:00+08:00")));

        mvc.perform(post("/api/v1/animals")
                        .header("X-User-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"CAT","gender":"FEMALE","color":"三花","estimatedAge":2,
                                 "features":"右后腿跛行，左耳有缺口","pointId":7,
                                 "photos":["F2026112100013"]}
                                """) )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("CAT-2026-001"))
                .andExpect(jsonPath("$.data.status").value("ON_CAMPUS"));
    }

    @Test
    @DisplayName("POST /animals 前端传 code → 30411，HTTP 200")
    void create_with_client_code_returns_30411() throws Exception {
        when(animalService.create(any(), anyLong())).thenThrow(new BusinessException(ErrorCode.CODE_DUPLICATED));

        mvc.perform(post("/api/v1/animals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"CAT","gender":"FEMALE","color":"三花","features":"右后腿跛行",
                                 "pointId":7,"photos":["F1"],"code":"CAT-2026-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30411));
    }

    @Test
    @DisplayName("GET /animals 返回 list + page 四字段")
    void search_returns_page_envelope() throws Exception {
        AnimalSummary row = new AnimalSummary("CAT-2026-031", AnimalCategory.CAT, "三花", "FEMALE",
                "三花", 2, new PointRef(7L, "东区宿舍七号楼"), AnimalStatus.TREATING,
                false, false, "/api/v1/files/F2026112100013",
                OffsetDateTime.parse("2026-11-20T18:22:00+08:00"));
        when(animalService.search(any(), any())).thenReturn(PageResult.of(List.of(row), 1, 20, 137));

        mvc.perform(get("/api/v1/animals").param("status", "TREATING").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].code").value("CAT-2026-031"))
                .andExpect(jsonPath("$.data.list[0].point.name").value("东区宿舍七号楼"))
                .andExpect(jsonPath("$.data.page.total").value(137))
                .andExpect(jsonPath("$.data.page.pages").value(7));
    }

    @Test
    @DisplayName("GET /animals/{code} 编号不存在 → 30410，HTTP 404")
    void detail_not_found_is_404() throws Exception {
        when(animalService.detail(eq("CAT-2026-999"), anyBoolean()))
                .thenThrow(new BusinessException(ErrorCode.RECORD_NOT_FOUND));

        mvc.perform(get("/api/v1/animals/CAT-2026-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30410));
    }

    @Test
    @DisplayName("PUT /animals/{code} 未填原因 → 30401，HTTP 200")
    void update_without_reason_returns_30401() throws Exception {
        when(animalService.update(any(), any(), anyLong()))
                .thenThrow(new BusinessException(ErrorCode.CHANGE_REASON_REQUIRED));

        mvc.perform(put("/api/v1/animals/CAT-2026-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"color":"橘白","reason":""}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30401));
    }
}
