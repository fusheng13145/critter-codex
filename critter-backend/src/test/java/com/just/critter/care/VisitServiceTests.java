package com.just.critter.care;

import com.just.critter.care.repository.MedicalRepository;
import com.just.critter.care.service.ReferenceAnimal;
import com.just.critter.care.service.ReferenceClinic;
import com.just.critter.care.service.ReferenceOrder;
import com.just.critter.care.service.VisitCommand;
import com.just.critter.care.service.VisitDetail;
import com.just.critter.care.service.VisitRegistration;
import com.just.critter.care.service.VisitService;
import com.just.critter.care.service.VisitUpdateCommand;
import com.just.critter.care.service.VisitUpdateWrite;
import com.just.critter.care.service.VisitWrite;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VisitServiceTests {

    private static final long ANIMAL_ID = 11L;
    private static final long CLINIC_ID = 2L;
    private static final long ORDER_ID = 51L;
    private static final long CREATOR_ID = 9001L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private MedicalRepository medical;
    private SequencePort sequences;
    private AuditLogService auditLog;
    private VisitService service;

    @BeforeEach
    void setUp() {
        medical = mock(MedicalRepository.class);
        sequences = mock(SequencePort.class);
        auditLog = mock(AuditLogService.class);
        service = new VisitService(medical, sequences, auditLog, CLOCK);
    }

    @Test
    @DisplayName("登记就诊：无工单时必须填写来源，生成 VS 编号并保存影像与费用")
    void registersVisitWithoutOrder() {
        when(medical.findAnimal(ANIMAL_ID))
                .thenReturn(Optional.of(new ReferenceAnimal(ANIMAL_ID, "CAT-2026-031", "三花")));
        when(medical.findClinic(CLINIC_ID)).thenReturn(Optional.of(clinic()));
        when(sequences.next("VISIT", 2026)).thenReturn(1);
        when(medical.insertVisit(any())).thenReturn(1);

        VisitRegistration result = service.register(new VisitCommand(
                ANIMAL_ID, null, CLINIC_ID,
                OffsetDateTime.parse("2026-11-20T15:00:00+08:00"),
                "左前肢跛行", "软组织挫伤", "清创包扎", 12000,
                List.of("F20261120001"), "校园巡查发现"), CREATOR_ID);

        assertThat(result.visitNo()).isEqualTo("VS20261120001");
        assertThat(result.animalCode()).isEqualTo("CAT-2026-031");
        assertThat(result.clinicName()).isEqualTo("校园北门合作宠物医院（示例）");
        assertThat(result.visitedAt()).isEqualTo(LocalDateTime.of(2026, 11, 20, 15, 0));

        ArgumentCaptor<VisitWrite> write = ArgumentCaptor.forClass(VisitWrite.class);
        verify(medical).insertVisit(write.capture());
        assertThat(write.getValue().animalId()).isEqualTo(ANIMAL_ID);
        assertThat(write.getValue().orderId()).isNull();
        assertThat(write.getValue().clinicId()).isEqualTo(CLINIC_ID);
        assertThat(write.getValue().cost()).isEqualTo(12000);
        assertThat(write.getValue().fileIds()).containsExactly("F20261120001");
        assertThat(write.getValue().sourceNote()).isEqualTo("校园巡查发现");
        assertThat(write.getValue().createdBy()).isEqualTo(CREATOR_ID);
    }

    @Test
    @DisplayName("关联工单时必须与动物一致，有关联时来源备注可空")
    void registersVisitWithMatchingOrder() {
        when(medical.findAnimal(ANIMAL_ID))
                .thenReturn(Optional.of(new ReferenceAnimal(ANIMAL_ID, "CAT-2026-031", "三花")));
        when(medical.findClinic(CLINIC_ID)).thenReturn(Optional.of(clinic()));
        when(medical.findOrder(ORDER_ID))
                .thenReturn(Optional.of(new ReferenceOrder(ORDER_ID, "RO20261120001", ANIMAL_ID)));
        when(sequences.next("VISIT", 2026)).thenReturn(2);
        when(medical.insertVisit(any())).thenReturn(1);

        VisitRegistration result = service.register(new VisitCommand(
                ANIMAL_ID, ORDER_ID, CLINIC_ID,
                OffsetDateTime.parse("2026-11-21T09:30:00+08:00"),
                null, "肠胃炎", "补液观察", 8000,
                List.of(), null), CREATOR_ID);

        assertThat(result.visitNo()).isEqualTo("VS20261121002");
        assertThat(result.visitedAt()).isEqualTo(LocalDateTime.of(2026, 11, 21, 9, 30));
        verify(medical).findOrder(ORDER_ID);
    }

    @Test
    @DisplayName("动物不存在返回 30410")
    void rejectsMissingAnimal() {
        when(medical.findAnimal(ANIMAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(command(null, "校园巡查发现"), CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));

        verify(medical, never()).findClinic(anyLong());
        verify(medical, never()).insertVisit(any());
    }

    @Test
    @DisplayName("医院不存在返回 30410")
    void rejectsMissingClinic() {
        when(medical.findAnimal(ANIMAL_ID))
                .thenReturn(Optional.of(new ReferenceAnimal(ANIMAL_ID, "CAT-2026-031", "三花")));
        when(medical.findClinic(CLINIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(command(null, "校园巡查发现"), CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));

        verify(medical, never()).insertVisit(any());
    }

    @Test
    @DisplayName("工单关联的动物与就诊动物不一致返回 30301")
    void rejectsOrderForAnotherAnimal() {
        when(medical.findAnimal(ANIMAL_ID))
                .thenReturn(Optional.of(new ReferenceAnimal(ANIMAL_ID, "CAT-2026-031", "三花")));
        when(medical.findClinic(CLINIC_ID)).thenReturn(Optional.of(clinic()));
        when(medical.findOrder(ORDER_ID))
                .thenReturn(Optional.of(new ReferenceOrder(ORDER_ID, "RO20261120001", 99L)));

        assertThatThrownBy(() -> service.register(command(ORDER_ID, null), CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(30301);
                    assertThat((java.util.Map<String, Object>) ex.getData())
                            .containsEntry("field", "orderId");
                });

        verify(medical, never()).insertVisit(any());
    }

    @Test
    @DisplayName("无工单且来源备注为空返回 30301，不查动物与医院")
    void rejectsMissingSourceNoteWithoutOrder() {
        assertThatThrownBy(() -> service.register(command(null, "  "), CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verifyNoInteractions(medical, sequences);
    }

    @Test
    @DisplayName("到院时间晚于当前时间返回 30301")
    void rejectsFutureVisitedAt() {
        VisitCommand command = new VisitCommand(
                ANIMAL_ID, null, CLINIC_ID,
                OffsetDateTime.parse("2026-11-21T15:00:00+08:00"),
                null, "软组织挫伤", null, 1000, List.of(), "校园巡查发现");

        assertThatThrownBy(() -> service.register(command, CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verifyNoInteractions(medical, sequences);
    }

    @Test
    @DisplayName("动物、医院、时间与费用缺失或非法时返回 30301")
    void rejectsInvalidCommand() {
        VisitCommand command = new VisitCommand(
                0L, null, 0L, null, null, null, null, -1, List.of(), null);

        assertThatThrownBy(() -> service.register(command, CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verifyNoInteractions(medical, sequences);
    }

    @Test
    @DisplayName("影像编号含空白时返回 30301")
    void rejectsBlankFileId() {
        VisitCommand command = new VisitCommand(
                ANIMAL_ID, null, CLINIC_ID,
                OffsetDateTime.parse("2026-11-20T15:00:00+08:00"),
                null, null, null, 1000, List.of("F1", " "), "校园巡查发现");

        assertThatThrownBy(() -> service.register(command, CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verifyNoInteractions(medical, sequences);
    }

    @Test
    @DisplayName("纠错就诊：更新可编辑字段并写审计日志，返回更新后的详情")
    void updatesVisitAndWritesAudit() {
        String visitNo = "VS20261120001";
        VisitDetail before = visitDetail("VS20261120001", "软组织挫伤", "清创包扎", 12000);
        VisitDetail after = visitDetail("VS20261120001", "软组织挫伤（复核）", "清创包扎、限制活动", 9000);
        when(medical.findVisit(visitNo)).thenReturn(Optional.of(before), Optional.of(after));
        when(medical.updateVisit(eq(visitNo), any())).thenReturn(1);

        VisitDetail result = service.update(visitNo, new VisitUpdateCommand(
                "左前肢跛行",
                "软组织挫伤（复核）",
                "清创包扎、限制活动",
                9000,
                List.of("F20261121001"),
                "费用按实际票据更正"), CREATOR_ID);

        assertThat(result.diagnosis()).isEqualTo("软组织挫伤（复核）");
        assertThat(result.cost()).isEqualTo(9000);

        ArgumentCaptor<VisitUpdateWrite> write = ArgumentCaptor.forClass(VisitUpdateWrite.class);
        verify(medical).updateVisit(eq(visitNo), write.capture());
        assertThat(write.getValue().diagnosis()).isEqualTo("软组织挫伤（复核）");
        assertThat(write.getValue().treatment()).isEqualTo("清创包扎、限制活动");
        assertThat(write.getValue().cost()).isEqualTo(9000);
        assertThat(write.getValue().fileIds()).containsExactly("F20261121001");
        assertThat(write.getValue().updatedAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 21, 12, 0));
        verify(auditLog).success(eq("VISIT_CORRECT"), eq("VISIT"), eq(visitNo), eq(before), any());
    }

    @Test
    @DisplayName("纠错就诊：记录不存在返回 30410，不更新不写审计")
    void rejectsMissingVisitOnUpdate() {
        when(medical.findVisit("VS20269999001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update("VS20269999001", new VisitUpdateCommand(
                null, "新诊断", null, 1000, List.of(), "更正诊断"), CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));

        verify(medical, never()).updateVisit(any(), any());
        verifyNoInteractions(auditLog);
    }

    @Test
    @DisplayName("纠错就诊：未填变更原因返回 30401，不查记录")
    void rejectsMissingUpdateReason() {
        assertThatThrownBy(() -> service.update("VS20261120001", new VisitUpdateCommand(
                null, "新诊断", null, 1000, List.of(), "  "), CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30401));

        verifyNoInteractions(medical, auditLog);
    }

    @Test
    @DisplayName("纠错就诊：费用、文本与影像非法时返回 30301")
    void rejectsInvalidUpdateFields() {
        VisitUpdateCommand negativeCost = new VisitUpdateCommand(
                null, null, null, -1, List.of(), "更正费用");
        VisitUpdateCommand overlong = new VisitUpdateCommand(
                "主".repeat(256), null, null, 1000, List.of(), "更正主诉");
        VisitUpdateCommand blankFile = new VisitUpdateCommand(
                null, null, null, 1000, List.of("F1", " "), "更正影像");

        assertThatThrownBy(() -> service.update("VS20261120001", negativeCost, CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
        assertThatThrownBy(() -> service.update("VS20261120001", overlong, CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
        assertThatThrownBy(() -> service.update("VS20261120001", blankFile, CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        verifyNoInteractions(medical, auditLog);
    }

    @Test
    @DisplayName("纠错就诊：更新影响 0 行返回 50002")
    void reportsUpdateFailure() {
        String visitNo = "VS20261120001";
        when(medical.findVisit(visitNo)).thenReturn(Optional.of(
                visitDetail(visitNo, "软组织挫伤", "清创包扎", 12000)));
        when(medical.updateVisit(eq(visitNo), any())).thenReturn(0);

        assertThatThrownBy(() -> service.update(visitNo, new VisitUpdateCommand(
                null, "新诊断", null, 1000, List.of(), "更正诊断"), CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(50002));

        verifyNoInteractions(auditLog);
    }

    private VisitDetail visitDetail(String visitNo, String diagnosis, String treatment, int cost) {
        return new VisitDetail(visitNo, ANIMAL_ID, "CAT-2026-031", "三花", null, null,
                CLINIC_ID, "校园北门合作宠物医院（示例）",
                LocalDateTime.of(2026, 11, 20, 15, 0),
                "左前肢跛行", diagnosis, treatment, cost,
                List.of("F20261120001"), "校园巡查发现",
                LocalDateTime.of(2026, 11, 21, 10, 0));
    }

    private VisitCommand command(Long orderId, String sourceNote) {
        return new VisitCommand(
                ANIMAL_ID, orderId, CLINIC_ID,
                OffsetDateTime.parse("2026-11-20T15:00:00+08:00"),
                "左前肢跛行", "软组织挫伤", "清创包扎", 12000,
                List.of("F20261120001"), sourceNote);
    }

    private ReferenceClinic clinic() {
        return new ReferenceClinic(CLINIC_ID, "校园北门合作宠物医院（示例）", "王医生",
                "0000-0000001", "北门路 1 号（示例地址）", "绝育 300 元、疫苗 60 元/针（示例价）");
    }
}
