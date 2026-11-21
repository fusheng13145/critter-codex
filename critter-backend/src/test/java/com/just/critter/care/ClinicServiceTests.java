package com.just.critter.care;

import com.just.critter.care.repository.MedicalRepository;
import com.just.critter.care.service.ClinicCommand;
import com.just.critter.care.service.ClinicService;
import com.just.critter.care.service.ClinicView;
import com.just.critter.care.service.ClinicWrite;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClinicServiceTests {

    private static final long CREATOR_ID = 9001L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-11-21T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    private MedicalRepository medical;
    private AuditLogService auditLog;
    private ClinicService service;

    @BeforeEach
    void setUp() {
        medical = mock(MedicalRepository.class);
        auditLog = mock(AuditLogService.class);
        service = new ClinicService(medical, auditLog, CLOCK);
    }

    @Test
    @DisplayName("新增合作医院：trim 文本、空白可选字段转空值，返回生成的医院编号")
    void createsClinicWithNormalizedFields() {
        when(medical.insertClinic(any())).thenReturn(7L);

        ClinicView result = service.create(new ClinicCommand(
                "  校园东门合作医院（示例）  ",
                "  李医生 ",
                " 0000-0000002 ",
                "  东门路 2 号 ",
                "  绝育 280 元（示例价） "), CREATOR_ID);

        assertThat(result.clinicId()).isEqualTo(7L);
        assertThat(result.clinicName()).isEqualTo("校园东门合作医院（示例）");
        assertThat(result.contact()).isEqualTo("李医生");
        assertThat(result.phone()).isEqualTo("0000-0000002");
        assertThat(result.address()).isEqualTo("东门路 2 号");
        assertThat(result.priceNote()).isEqualTo("绝育 280 元（示例价）");

        ArgumentCaptor<ClinicWrite> write = ArgumentCaptor.forClass(ClinicWrite.class);
        verify(medical).insertClinic(write.capture());
        assertThat(write.getValue().createdBy()).isEqualTo(CREATOR_ID);
        assertThat(write.getValue().createdAt())
                .isEqualTo(LocalDateTime.of(2026, 11, 21, 12, 0));
        verify(auditLog).success(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("可选字段为空白时落库为空，不保存空白字符串")
    void storesBlankOptionalsAsNull() {
        when(medical.insertClinic(any())).thenReturn(8L);

        service.create(new ClinicCommand("校园南门诊所（示例）", "  ", null, "", " "), CREATOR_ID);

        ArgumentCaptor<ClinicWrite> write = ArgumentCaptor.forClass(ClinicWrite.class);
        verify(medical).insertClinic(write.capture());
        assertThat(write.getValue().contact()).isNull();
        assertThat(write.getValue().phone()).isNull();
        assertThat(write.getValue().address()).isNull();
        assertThat(write.getValue().priceNote()).isNull();
    }

    @Test
    @DisplayName("医院名称为空或超长时返回 30301，不写库")
    void rejectsInvalidName() {
        assertInvalid(new ClinicCommand("   ", null, null, null, null), "clinicName");
        assertInvalid(new ClinicCommand("医".repeat(65), null, null, null, null), "clinicName");
        verify(medical, never()).insertClinic(any());
    }

    @Test
    @DisplayName("联系人、电话、地址与报价超长时返回 30301")
    void rejectsOverlongOptionalFields() {
        assertInvalid(new ClinicCommand("示例医院", "联".repeat(33), null, null, null), "contact");
        assertInvalid(new ClinicCommand("示例医院", null, "1".repeat(33), null, null), "phone");
        assertInvalid(new ClinicCommand("示例医院", null, null, "址".repeat(256), null), "address");
        assertInvalid(new ClinicCommand("示例医院", null, null, null, "价".repeat(256)), "priceNote");
    }

    @Test
    @DisplayName("医院写入 0 行返回 50002")
    void reportsInsertFailure() {
        when(medical.insertClinic(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.create(
                new ClinicCommand("示例医院", null, null, null, null), CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(50002));
    }

    private void assertInvalid(ClinicCommand command, String field) {
        assertThatThrownBy(() -> service.create(command, CREATOR_ID))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(30301);
                    assertThat((java.util.Map<String, Object>) ex.getData())
                            .containsEntry("field", field);
                });
        verifyNoInteractions(medical);
    }
}
