package com.just.critter.care;

import com.just.critter.care.repository.MedicalRepository;
import com.just.critter.care.service.ReferenceAnimal;
import com.just.critter.care.service.ReferenceClinic;
import com.just.critter.care.service.ReferenceOrder;
import com.just.critter.care.service.VisitCommand;
import com.just.critter.care.service.VisitRegistration;
import com.just.critter.care.service.VisitService;
import com.just.critter.care.service.VisitWrite;
import com.just.critter.domain.archive.SequencePort;
import com.just.critter.support.exception.BusinessException;
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
    private VisitService service;

    @BeforeEach
    void setUp() {
        medical = mock(MedicalRepository.class);
        sequences = mock(SequencePort.class);
        service = new VisitService(medical, sequences, CLOCK);
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
