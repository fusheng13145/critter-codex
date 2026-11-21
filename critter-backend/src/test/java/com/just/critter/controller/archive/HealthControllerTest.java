package com.just.critter.controller.archive;

import com.just.critter.domain.enums.HealthType;
import com.just.critter.service.archive.CreatedHealthRecord;
import com.just.critter.service.archive.HealthRecordView;
import com.just.critter.service.archive.HealthService;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T1.4 接口契约：POST 返回 healthRecordId、枚举非法 30303、GET 返回提醒字段、不存在 404。
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void auth() {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        AuthContext.set(new CurrentUser(1001L, "2023019001", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jti", null));
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @MockBean
    private HealthService healthService;

    @Test
    @DisplayName("POST /animals/{code}/health 返回 healthRecordId")
    void create_returns_health_record_id() throws Exception {
        when(healthService.create(eq("CAT-2026-001"), any(), anyLong()))
                .thenReturn(new CreatedHealthRecord(9L));

        mvc.perform(post("/api/v1/animals/CAT-2026-001/health")
                        .header("X-User-Id", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"VACCINE","recordedAt":"2026-11-21T09:00:00+08:00",
                                 "result":"猫三联第一针","nextDate":"2026-11-24"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.healthRecordId").value(9));
    }

    @Test
    @DisplayName("POST 健康记录类型非法 → 30303")
    void create_with_invalid_type_returns_30303() throws Exception {
        mvc.perform(post("/api/v1/animals/CAT-2026-001/health")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"BATH","recordedAt":"2026-11-21T09:00:00+08:00","result":"洗浴"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30303));
    }

    @Test
    @DisplayName("GET /animals/{code}/health 返回记录与疫苗提醒字段")
    void list_returns_records_and_reminder() throws Exception {
        HealthRecordView view = new HealthRecordView(1L, HealthType.VACCINE, "疫苗",
                OffsetDateTime.parse("2026-11-20T09:00:00+08:00"), "狂犬疫苗",
                LocalDate.of(2026, 11, 24), true, 3, 1001L,
                OffsetDateTime.parse("2026-11-20T09:00:00+08:00"));
        when(healthService.list("CAT-2026-001")).thenReturn(List.of(view));

        mvc.perform(get("/api/v1/animals/CAT-2026-001/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].type").value("VACCINE"))
                .andExpect(jsonPath("$.data[0].typeLabel").value("疫苗"))
                .andExpect(jsonPath("$.data[0].nextDate").value("2026-11-24"))
                .andExpect(jsonPath("$.data[0].reminderDue").value(true))
                .andExpect(jsonPath("$.data[0].daysUntilNext").value(3));
    }

    @Test
    @DisplayName("GET 编号不存在 → 30410，HTTP 404")
    void list_not_found_is_404() throws Exception {
        when(healthService.list("CAT-2026-999")).thenThrow(new BusinessException(ErrorCode.RECORD_NOT_FOUND));

        mvc.perform(get("/api/v1/animals/CAT-2026-999/health"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30410));
    }
}
