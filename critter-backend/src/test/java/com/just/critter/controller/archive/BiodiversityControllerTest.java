package com.just.critter.controller.archive;

import com.just.critter.domain.enums.OccurrenceScale;
import com.just.critter.service.archive.BiodiversityService;
import com.just.critter.service.archive.CreatedOccurrence;
import com.just.critter.service.archive.OccurrenceView;
import com.just.critter.service.archive.PointRef;
import com.just.critter.service.archive.PointView;
import com.just.critter.service.archive.SpeciesView;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.AuthInterceptor;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T1.5 基础数据与出现记录契约：物种/点位只读、数量枚举、30303、分页。
 */
@SpringBootTest
@AutoConfigureMockMvc
class BiodiversityControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void auth() {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        AuthContext.set(new CurrentUser(1001L, "2023019001", "学生", "STUDENT",
                Set.of("STUDENT"), "计算机学院", "jti", null));
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @MockBean
    private BiodiversityService biodiversityService;

    @Test
    @DisplayName("GET /species 返回物种名录")
    void list_species() throws Exception {
        when(biodiversityService.listSpecies(any())).thenReturn(List.of(
                new SpeciesView(1L, "黄鼬", "Mustela sibirica", "哺乳纲", "待核", "夜行，偶见")));

        mvc.perform(get("/api/v1/species").param("category", "哺乳纲"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].commonName").value("黄鼬"))
                .andExpect(jsonPath("$.data[0].protectionLevel").value("待核"));
    }

    @Test
    @DisplayName("GET /points 返回平面图坐标")
    void list_points() throws Exception {
        when(biodiversityService.listPoints()).thenReturn(List.of(
                new PointView(7L, "东区绿化带", "东区", new BigDecimal("63.50"), new BigDecimal("41.20"),
                        true, "靠近七号楼")));

        mvc.perform(get("/api/v1/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("东区绿化带"))
                .andExpect(jsonPath("$.data[0].x").value(63.50));
    }

    @Test
    @DisplayName("POST /occurrences 返回 occurrenceId")
    void create_occurrence() throws Exception {
        when(biodiversityService.createOccurrence(any(), anyLong()))
                .thenReturn(new CreatedOccurrence(55L));

        mvc.perform(post("/api/v1/occurrences")
                        .header("X-User-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"speciesId":1,"pointId":7,"occurredAt":"2026-04-18T08:30:00+08:00",
                                 "scale":"SMALL_HERD","habitat":"灌木丛","fileIds":["F1"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occurrenceId").value(55));
    }

    @Test
    @DisplayName("POST /occurrences 数量填数字 → 30303")
    void create_with_numeric_scale_returns_30303() throws Exception {
        mvc.perform(post("/api/v1/occurrences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"speciesId":1,"pointId":7,"occurredAt":"2026-04-18T08:30:00+08:00",
                                 "scale":3,"fileIds":["F1"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30303));
    }

    @Test
    @DisplayName("GET /occurrences 返回量级中文名与分页")
    void list_occurrences() throws Exception {
        OccurrenceView view = new OccurrenceView(55L, 1L, "黄鼬", new PointRef(7L, "东区绿化带"),
                OffsetDateTime.parse("2026-04-18T08:30:00+08:00"),
                OccurrenceScale.SMALL_HERD, "小群", "灌木丛", List.of("F1"), 1001L,
                OffsetDateTime.parse("2026-04-18T09:00:00+08:00"));
        when(biodiversityService.searchOccurrences(any(), any()))
                .thenReturn(PageResult.of(List.of(view), 1, 20, 1));

        mvc.perform(get("/api/v1/occurrences").param("season", "SPRING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].scaleLabel").value("小群"))
                .andExpect(jsonPath("$.data.page.total").value(1));
    }
}
