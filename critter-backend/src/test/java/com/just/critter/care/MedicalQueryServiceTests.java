package com.just.critter.care;

import com.just.critter.care.repository.MedicalRepository;
import com.just.critter.care.service.ClinicView;
import com.just.critter.care.service.MedicalQueryService;
import com.just.critter.care.service.ReferenceAnimal;
import com.just.critter.care.service.VisitDetail;
import com.just.critter.support.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MedicalQueryServiceTests {

    private MedicalRepository medical;
    private MedicalQueryService service;

    @BeforeEach
    void setUp() {
        medical = mock(MedicalRepository.class);
        service = new MedicalQueryService(medical);
    }

    @Test
    @DisplayName("合作医院列表原样返回报价说明")
    void listsClinics() {
        when(medical.findClinics()).thenReturn(List.of(new ClinicView(
                2L, "校园北门合作宠物医院（示例）", "王医生", "0000-0000001",
                "北门路 1 号（示例地址）", "绝育 300 元、疫苗 60 元/针（示例价）")));

        List<ClinicView> clinics = service.clinics();

        assertThat(clinics).hasSize(1);
        assertThat(clinics.get(0).priceNote()).contains("示例价");
    }

    @Test
    @DisplayName("按就诊编号返回诊断、处置、费用与影像")
    void returnsVisitDetail() {
        VisitDetail detail = new VisitDetail(
                "VS20261120001", 11L, "CAT-2026-031", "三花", null, null,
                2L, "校园北门合作宠物医院（示例）",
                LocalDateTime.of(2026, 11, 20, 15, 0),
                "左前肢跛行", "软组织挫伤", "清创包扎", 12000,
                List.of("F20261120001"), "校园巡查发现",
                LocalDateTime.of(2026, 11, 21, 10, 0));
        when(medical.findVisit("VS20261120001")).thenReturn(Optional.of(detail));

        VisitDetail result = service.visit("VS20261120001");

        assertThat(result.diagnosis()).isEqualTo("软组织挫伤");
        assertThat(result.treatment()).isEqualTo("清创包扎");
        assertThat(result.fileIds()).containsExactly("F20261120001");
    }

    @Test
    @DisplayName("就诊编号为空或查不到返回 30410")
    void rejectsMissingVisit() {
        assertThatThrownBy(() -> service.visit(" "))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));

        when(medical.findVisit("VS20269999001")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.visit("VS20269999001"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
    }

    @Test
    @DisplayName("按档案编号反查就诊：返回该动物全部记录")
    void listsVisitsByAnimal() {
        VisitDetail detail = new VisitDetail(
                "VS20261120001", 11L, "CAT-2026-031", "三花", null, null,
                2L, "校园北门合作宠物医院（示例）",
                LocalDateTime.of(2026, 11, 20, 15, 0),
                "左前肢跛行", "软组织挫伤", "清创包扎", 12000,
                List.of("F20261120001"), "校园巡查发现",
                LocalDateTime.of(2026, 11, 21, 10, 0));
        when(medical.findAnimalByCode("CAT-2026-031"))
                .thenReturn(Optional.of(new ReferenceAnimal(11L, "CAT-2026-031", "三花")));
        when(medical.findVisitsByAnimal("CAT-2026-031")).thenReturn(List.of(detail));

        List<VisitDetail> result = service.visitsByAnimal(" CAT-2026-031 ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).diagnosis()).isEqualTo("软组织挫伤");
        assertThat(result.get(0).clinicName()).contains("示例");
    }

    @Test
    @DisplayName("按档案编号反查就诊：空编号 30301，档案不存在 30410")
    void rejectsInvalidAnimalCode() {
        assertThatThrownBy(() -> service.visitsByAnimal(" "))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30301));
        verifyNoInteractions(medical);

        when(medical.findAnimalByCode("CAT-2026-999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.visitsByAnimal("CAT-2026-999"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
    }

    @Test
    @DisplayName("档案存在但尚无就诊：返回空列表而不是 30410")
    void returnsEmptyVisitsForAnimal() {
        when(medical.findAnimalByCode("CAT-2026-031"))
                .thenReturn(Optional.of(new ReferenceAnimal(11L, "CAT-2026-031", "三花")));
        when(medical.findVisitsByAnimal("CAT-2026-031")).thenReturn(List.of());

        assertThat(service.visitsByAnimal("CAT-2026-031")).isEmpty();
    }
}
