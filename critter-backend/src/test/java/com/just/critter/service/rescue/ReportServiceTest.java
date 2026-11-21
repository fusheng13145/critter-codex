package com.just.critter.service.rescue;

import com.just.critter.domain.enums.ReportStatus;
import com.just.critter.domain.enums.ReportType;
import com.just.critter.domain.model.Animal;
import com.just.critter.domain.model.BasePoint;
import com.just.critter.domain.model.Order;
import com.just.critter.domain.model.Report;
import com.just.critter.domain.rescue.ReportCodeGenerator;
import com.just.critter.mapper.AnimalMapper;
import com.just.critter.mapper.BasePointMapper;
import com.just.critter.mapper.BaseSpeciesMapper;
import com.just.critter.mapper.OrderMapper;
import com.just.critter.mapper.ReportMapper;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T2.1 验收：必填校验、编号/时限、可选档案关联、联系方式落库、同点位 24 小时去重提示。
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Mock
    private ReportMapper reportMapper;
    @Mock
    private BasePointMapper pointMapper;
    @Mock
    private BaseSpeciesMapper speciesMapper;
    @Mock
    private AnimalMapper animalMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private AuditLogService auditLogService;

    private ReportService service;

    @BeforeEach
    void setUp() {
        service = new ReportService(reportMapper, pointMapper, speciesMapper, animalMapper,
                new ReportCodeGenerator((bizType, year) -> 7, CLOCK),
                new com.just.critter.domain.rescue.OrderCodeGenerator((bizType, year) -> 8, CLOCK),
                orderMapper, auditLogService, CLOCK);
        BasePoint point = new BasePoint();
        point.setId(7L);
        point.setPointName("东区宿舍七号楼");
        lenient().when(pointMapper.selectById(7L)).thenReturn(point);
        lenient().when(pointMapper.selectBatchIds(any())).thenReturn(List.of(point));
        lenient().when(reportMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("创建：返回 RP 编号、PENDING 与 24 小时时限，并落照片/联系方式")
    void create_returns_number_status_and_deadline() {
        CreatedReport created = service.create(
                command("INJURED", 7L, List.of("F1", "F2"), "右后腿受伤无法正常站立", null, "13800000000"),
                1001L);

        assertThat(created.reportNo()).isEqualTo("RP20261121007");
        assertThat(created.status()).isEqualTo(ReportStatus.PENDING);
        assertThat(created.deadline()).isEqualTo(OffsetDateTime.parse("2026-11-22T10:31:00+08:00"));
        assertThat(created.duplicated()).isFalse();
        assertThat(created.duplicateOf()).isNull();

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportMapper).insert(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getReportNo()).isEqualTo("RP20261121007");
        assertThat(saved.getType()).isEqualTo(ReportType.INJURED);
        assertThat(saved.getPointId()).isEqualTo(7L);
        assertThat(saved.getDescription()).isEqualTo("右后腿受伤无法正常站立");
        assertThat(saved.getFileIds()).containsExactly("F1", "F2");
        assertThat(saved.getReporterId()).isEqualTo(1001L);
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.PENDING);
        assertThat(saved.getContact()).isEqualTo("13800000000");
        assertThat(saved.getAnimalId()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-11-21T10:31:00"));
        assertThat(saved.getCreatedBy()).isEqualTo(1001L);
    }

    @Test
    @DisplayName("创建：可选 animalCode 解析为档案 ID")
    void create_links_optional_animal() {
        Animal animal = new Animal();
        animal.setId(42L);
        animal.setCode("CAT-2026-001");
        when(animalMapper.selectByCode("CAT-2026-001")).thenReturn(animal);

        service.create(command("INJURED", 7L, List.of("F1"), "右后腿受伤无法正常站立",
                "CAT-2026-001", null), 1001L);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportMapper).insert(captor.capture());
        assertThat(captor.getValue().getAnimalId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("必填校验：类型、点位、照片、描述至少 10 字")
    void create_validates_required_fields() {
        assertCode(() -> service.create(command(null, 7L, List.of("F1"), "右后腿受伤无法正常站立", null, null), 1L), 30301);
        assertCode(() -> service.create(command("INJURED", null, List.of("F1"), "右后腿受伤无法正常站立", null, null), 1L), 30301);
        assertCode(() -> service.create(command("INJURED", 7L, List.of(), "右后腿受伤无法正常站立", null, null), 1L), 30301);
        assertCode(() -> service.create(command("INJURED", 7L, List.of("F1"), "右后腿受伤无法", null, null), 1L), 30301);
        assertCode(() -> service.create(command("INJURED", 7L, List.of("F1"), "右后腿受伤无法正常站立", null, "12345678901234567890123456789012345678901234567890123456789012345"), 1L), 30301);
        verify(reportMapper, never()).insert(any(Report.class));
    }

    @Test
    @DisplayName("类型非法或空照片项返回 30303/30301")
    void create_rejects_bad_type_and_blank_photo() {
        assertCode(() -> service.create(command("NOT_A_TYPE", 7L, List.of("F1"), "右后腿受伤无法正常站立", null, null), 1L), 30303);
        assertCode(() -> service.create(command("INJURED", 7L, List.of(" "), "右后腿受伤无法正常站立", null, null), 1L), 30301);
    }

    @Test
    @DisplayName("点位或档案不存在返回 30410")
    void create_rejects_unknown_point_and_animal() {
        assertCode(() -> service.create(command("INJURED", 999L, List.of("F1"), "右后腿受伤无法正常站立", null, null), 1L), 30410);
        assertCode(() -> service.create(command("INJURED", 7L, List.of("F1"), "右后腿受伤无法正常站立", "CAT-2026-999", null), 1L), 30410);
        verify(reportMapper, never()).insert(any(Report.class));
    }

    @Test
    @DisplayName("同点位 24 小时内已有未关闭上报：仍插入新单，返回 duplicated 与原单号")
    void duplicate_within_24h_is_flagged() {
        Report original = new Report();
        original.setReportNo("RP20261121001");
        original.setStatus(ReportStatus.PENDING);
        when(reportMapper.selectList(any())).thenReturn(List.of(original));

        CreatedReport created = service.create(
                command("INJURED", 7L, List.of("F1"), "右后腿受伤无法正常站立", null, null), 1001L);

        assertThat(created.duplicated()).isTrue();
        assertThat(created.duplicateOf()).isEqualTo("RP20261121001");
        verify(reportMapper).insert(any(Report.class));
    }

    @Test
    @DisplayName("野猪拦截：俗名含野猪或学名 Sus scrofa，返回 30503 且不落上报")
    void create_rejects_wild_boar() {
        com.just.critter.domain.model.BaseSpecies boar = new com.just.critter.domain.model.BaseSpecies();
        boar.setId(31L);
        boar.setCommonName("野猪");
        boar.setScientificName("Sus scrofa");
        when(speciesMapper.selectById(31L)).thenReturn(boar);

        assertCode(() -> service.create(new CreateReportCommand(
                "INJURED", 7L, List.of("F1"), "体型较大，疑似野猪闯入宿舍区", null, 31L, null), 1L), 30503);
        verify(reportMapper, never()).insert(any(Report.class));
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("列表：按状态/类型/点位/时限过滤，返回点位名与超时标记")
    void search_returns_summary_with_point_and_overdue() {
        Report report = report(5L, ReportStatus.PENDING, LocalDateTime.parse("2026-11-20T10:30:00"), 1001L);
        Page<Report> page = new Page<>(1, 10);
        page.setRecords(List.of(report));
        page.setTotal(1);
        when(reportMapper.selectPage(any(), any())).thenReturn(page);

        var result = service.search(new ReportFilter(List.of(ReportStatus.PENDING), ReportType.INJURED, 7L, true),
                new PageQuery(1, 10));

        assertThat(result.getPage().getTotal()).isEqualTo(1);
        assertThat(result.getList()).singleElement().satisfies(summary -> {
            assertThat(summary.reportNo()).isEqualTo("RP20261121007");
            assertThat(summary.pointName()).isEqualTo("东区宿舍七号楼");
            assertThat(summary.overdue()).isTrue();
        });
    }

    @Test
    @DisplayName("详情：管理员可见联系方式，普通学生不可见")
    void detail_masks_contact_for_non_admin() {
        Report report = report(5L, ReportStatus.PENDING, LocalDateTime.parse("2026-11-21T10:31:00"), 1001L);
        report.setContact("13800000000");
        when(reportMapper.selectById(5L)).thenReturn(report);
        Order order = new Order();
        order.setOrderNo("RO20261121001");
        when(orderMapper.selectOne(any())).thenReturn(order);

        CurrentUser admin = new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                java.util.Set.of("ANIMAL_ADMIN"), "后勤处", "jti", null);
        ReportDetail adminView = service.detail(5L, admin);
        assertThat(adminView.contact()).isEqualTo("13800000000");
        assertThat(adminView.orderNo()).isEqualTo("RO20261121001");

        CurrentUser otherStudent = new CurrentUser(2002L, "2023020002", "学生", "STUDENT",
                java.util.Set.of("STUDENT"), "计算机学院", "jti2", null);
        assertThat(service.detail(5L, otherStudent).contact()).isNull();
    }

    @Test
    @DisplayName("关闭：必须填原因，成功后回 CLOSED 并写审计")
    void close_requires_reason_and_updates_status() {
        Report report = report(5L, ReportStatus.PENDING, LocalDateTime.parse("2026-11-21T10:31:00"), 1001L);
        when(reportMapper.selectById(5L)).thenReturn(report);

        ClosedReport closed = service.close(5L, "  重复上报  ");

        assertThat(closed.status()).isEqualTo(ReportStatus.CLOSED);
        assertThat(closed.closeReason()).isEqualTo("重复上报");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.CLOSED);
        verify(reportMapper).updateById(report);
        verify(auditLogService).success(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("关闭：空原因 30401，已受理 30602")
    void close_rejects_empty_reason_and_non_pending() {
        Report pending = report(5L, ReportStatus.PENDING, LocalDateTime.parse("2026-11-21T10:31:00"), 1001L);
        when(reportMapper.selectById(5L)).thenReturn(pending);
        assertCode(() -> service.close(5L, " "), 30401);

        Report processing = report(6L, ReportStatus.PROCESSING, LocalDateTime.parse("2026-11-21T10:31:00"), 1001L);
        when(reportMapper.selectById(6L)).thenReturn(processing);
        assertCode(() -> service.close(6L, "重复"), 30602);
    }

    private Report report(Long id, ReportStatus status, LocalDateTime createdAt, Long reporterId) {
        Report report = new Report();
        report.setId(id);
        report.setReportNo("RP20261121007");
        report.setType(ReportType.INJURED);
        report.setPointId(7L);
        report.setDescription("右后腿受伤无法正常站立");
        report.setFileIds(List.of("F1"));
        report.setReporterId(reporterId);
        report.setStatus(status);
        report.setCreatedAt(createdAt);
        return report;
    }

    private CreateReportCommand command(String type, Long pointId, List<String> photos,
                                        String description, String animalCode, String contact) {
        return new CreateReportCommand(type, pointId, photos, description, animalCode, null, contact);
    }

    private void assertCode(ThrowingRunnable action, int expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(expected));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
