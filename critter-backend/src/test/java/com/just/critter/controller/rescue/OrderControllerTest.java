package com.just.critter.controller.rescue;

import com.just.critter.domain.enums.OrderStatus;
import com.just.critter.domain.enums.ReportType;
import com.just.critter.service.rescue.OrderDetail;
import com.just.critter.service.rescue.OrderFilter;
import com.just.critter.service.rescue.OrderService;
import com.just.critter.service.rescue.OrderStatusCommand;
import com.just.critter.service.rescue.OrderSummary;
import com.just.critter.service.rescue.OrderTimelineItem;
import com.just.critter.service.rescue.OrderTransitionResult;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.AuthInterceptor;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T2.3 接口契约：列表筛选、详情、状态流转参数解析与非法枚举。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuthInterceptor authInterceptor;

    @MockBean
    private OrderService orderService;

    @BeforeEach
    void auth() {
        when(authInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        AuthContext.set(new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jti", null));
    }

    @AfterEach
    void clearAuth() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("GET /orders 返回分页并解析状态/类型/超时筛选")
    void list_returns_page_and_parses_filters() throws Exception {
        when(orderService.search(any(), any())).thenReturn(PageResult.of(List.of(
                new OrderSummary("RO20261121001", 5L, "RP20261121007", ReportType.INJURED,
                        7L, "东区宿舍七号楼", OrderStatus.PROCESSING, false,
                        LocalDateTime.parse("2026-11-21T10:31:00"),
                        OffsetDateTime.parse("2026-11-22T10:31:00+08:00"),
                        LocalDateTime.parse("2026-11-21T10:31:00"), 3001L)), 1, 10, 1));

        mvc.perform(get("/api/v1/orders")
                        .param("status", "PROCESSING,TO_REVISIT")
                        .param("type", "INJURED")
                        .param("overdue", "false")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list[0].orderNo").value("RO20261121001"))
                .andExpect(jsonPath("$.data.list[0].pointName").value("东区宿舍七号楼"))
                .andExpect(jsonPath("$.data.page.total").value(1));

        ArgumentCaptor<OrderFilter> filterCaptor = ArgumentCaptor.forClass(OrderFilter.class);
        ArgumentCaptor<PageQuery> pageCaptor = ArgumentCaptor.forClass(PageQuery.class);
        verify(orderService).search(filterCaptor.capture(), pageCaptor.capture());
        assertThat(filterCaptor.getValue().statuses())
                .containsExactly(OrderStatus.PROCESSING, OrderStatus.TO_REVISIT);
        assertThat(filterCaptor.getValue().type()).isEqualTo(ReportType.INJURED);
        assertThat(filterCaptor.getValue().overdue()).isFalse();
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("GET /orders/{no} 返回详情与时间线")
    void detail_returns_order_and_timeline() throws Exception {
        when(orderService.detail("RO20261121001")).thenReturn(detail());

        mvc.perform(get("/api/v1/orders/RO20261121001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("RO20261121001"))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.timeline[0].actionLabel").value("受理并转工单"))
                .andExpect(jsonPath("$.data.allowed[0]").value("TO_REVISIT"));
    }

    @Test
    @DisplayName("PUT /orders/{no}/status 解析目标状态并调用服务")
    void transition_parses_target_status() throws Exception {
        when(orderService.transition(eq("RO20261121001"), any(), any()))
                .thenReturn(new OrderTransitionResult("RO20261121001", OrderStatus.TO_REVISIT,
                        List.of(OrderStatus.CLOSED, OrderStatus.PROCESSING),
                        LocalDateTime.parse("2026-11-21T10:31:00"),
                        OffsetDateTime.parse("2026-11-22T10:31:00+08:00"),
                        3001L, "已送医观察", null));

        mvc.perform(put("/api/v1/orders/RO20261121001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetStatus":"TO_REVISIT","note":"已送医观察"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TO_REVISIT"))
                .andExpect(jsonPath("$.data.disposeNote").value("已送医观察"));

        ArgumentCaptor<OrderStatusCommand> captor = ArgumentCaptor.forClass(OrderStatusCommand.class);
        verify(orderService).transition(eq("RO20261121001"), captor.capture(), any());
        assertThat(captor.getValue().targetStatus()).isEqualTo(OrderStatus.TO_REVISIT);
        assertThat(captor.getValue().note()).isEqualTo("已送医观察");
    }

    @Test
    @DisplayName("非法目标状态返回 30303（业务失败默认 HTTP 200）")
    void invalid_target_status_is_rejected() throws Exception {
        mvc.perform(put("/api/v1/orders/RO20261121001/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30303));
    }

    private OrderDetail detail() {
        return new OrderDetail("RO20261121001", 5L, "RP20261121007", ReportType.INJURED,
                7L, "东区宿舍七号楼", "右后腿受伤无法正常站立", List.of("F1"),
                42L, "CAT-2026-001", OrderStatus.PROCESSING, false,
                LocalDateTime.parse("2026-11-21T10:31:00"),
                OffsetDateTime.parse("2026-11-22T10:31:00+08:00"),
                LocalDateTime.parse("2026-11-21T10:31:00"),
                9001L, 3001L, "已联系处置人", null,
                List.of(OrderStatus.TO_REVISIT, OrderStatus.CLOSED),
                List.of(new OrderTimelineItem("REPORT_ACCEPT", "受理并转工单",
                        LocalDateTime.parse("2026-11-21T10:31:00"), 9001L, "ANIMAL_ADMIN", "SUCCESS",
                        OrderStatus.PENDING, OrderStatus.PROCESSING, null)));
    }
}
