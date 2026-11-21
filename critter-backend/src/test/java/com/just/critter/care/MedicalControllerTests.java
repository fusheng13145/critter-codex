package com.just.critter.care;

import com.just.critter.care.service.ClinicService;
import com.just.critter.care.service.ClinicView;
import com.just.critter.care.service.MedicalQueryService;
import com.just.critter.care.service.VisitDetail;
import com.just.critter.care.service.VisitRegistration;
import com.just.critter.care.service.VisitService;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.security.JwtService;
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
class MedicalControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private MedicalQueryService queryService;

    @MockitoBean
    private VisitService visitService;

    @MockitoBean
    private ClinicService clinicService;

    @Test
    @DisplayName("合作医院列表：登录后返回名称、联系人与示例报价")
    void listsClinics() throws Exception {
        when(queryService.clinics()).thenReturn(List.of(clinic()));

        mockMvc.perform(get("/api/v1/clinics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].clinicName").value("校园北门合作宠物医院（示例）"))
                .andExpect(jsonPath("$.data[0].priceNote").value("绝育 300 元、疫苗 60 元/针（示例价）"));

        verify(queryService).clinics();
    }

    @Test
    @DisplayName("管理员登记就诊：返回就诊编号与医院信息")
    void registersVisit() throws Exception {
        when(visitService.register(any(), eq(1001L))).thenReturn(new VisitRegistration(
                "VS20261120001", 11L, "CAT-2026-031", "三花", 2L,
                "校园北门合作宠物医院（示例）",
                LocalDateTime.of(2026, 11, 20, 15, 0), 12000));

        mockMvc.perform(post("/api/v1/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "animalId": 11,
                                  "clinicId": 2,
                                  "visitedAt": "2026-11-20T15:00:00+08:00",
                                  "complaint": "左前肢跛行",
                                  "diagnosis": "软组织挫伤",
                                  "treatment": "清创包扎",
                                  "cost": 12000,
                                  "fileIds": ["F20261120001"],
                                  "sourceNote": "校园巡查发现"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.visitNo").value("VS20261120001"))
                .andExpect(jsonPath("$.data.animalCode").value("CAT-2026-031"))
                .andExpect(jsonPath("$.data.cost").value(12000));

        verify(visitService).register(any(), eq(1001L));
    }

    @Test
    @DisplayName("普通认养人不能登记就诊，返回 20003")
    void rejectsStudentRegistration() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(post("/api/v1/visits")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"animalId":11,"clinicId":2,
                                 "visitedAt":"2026-11-20T15:00:00+08:00",
                                 "sourceNote":"校园巡查发现"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    @DisplayName("就诊详情：返回诊断、处置、费用与影像")
    void readsVisitDetail() throws Exception {
        when(queryService.visit("VS20261120001")).thenReturn(new VisitDetail(
                "VS20261120001", 11L, "CAT-2026-031", "三花", null, null,
                2L, "校园北门合作宠物医院（示例）",
                LocalDateTime.of(2026, 11, 20, 15, 0),
                "左前肢跛行", "软组织挫伤", "清创包扎", 12000,
                List.of("F20261120001"), "校园巡查发现",
                LocalDateTime.of(2026, 11, 21, 10, 0)));

        mockMvc.perform(get("/api/v1/visits/VS20261120001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visitNo").value("VS20261120001"))
                .andExpect(jsonPath("$.data.diagnosis").value("软组织挫伤"))
                .andExpect(jsonPath("$.data.treatment").value("清创包扎"))
                .andExpect(jsonPath("$.data.fileIds[0]").value("F20261120001"));

        verify(queryService).visit("VS20261120001");
    }

    @Test
    @DisplayName("就诊编号不存在返回 30410")
    void rejectsMissingVisit() throws Exception {
        when(queryService.visit("VS20269999001"))
                .thenThrow(new BusinessException(ErrorCode.RECORD_NOT_FOUND));

        mockMvc.perform(get("/api/v1/visits/VS20269999001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30410));
    }

    @Test
    @DisplayName("按档案编号反查就诊：返回诊断、处置与费用")
    void listsVisitsByAnimal() throws Exception {
        when(queryService.visitsByAnimal("CAT-2026-031")).thenReturn(List.of(new VisitDetail(
                "VS20261120001", 11L, "CAT-2026-031", "三花", null, null,
                2L, "校园北门合作宠物医院（示例）",
                LocalDateTime.of(2026, 11, 20, 15, 0),
                "左前肢跛行", "软组织挫伤", "清创包扎", 12000,
                List.of("F20261120001"), "校园巡查发现",
                LocalDateTime.of(2026, 11, 21, 10, 0))));

        mockMvc.perform(get("/api/v1/visits").param("animalCode", "CAT-2026-031"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].visitNo").value("VS20261120001"))
                .andExpect(jsonPath("$.data[0].diagnosis").value("软组织挫伤"))
                .andExpect(jsonPath("$.data[0].cost").value(12000));

        verify(queryService).visitsByAnimal("CAT-2026-031");
    }

    @Test
    @DisplayName("反查就诊缺少档案编号返回 30301")
    void rejectsMissingAnimalCode() throws Exception {
        when(queryService.visitsByAnimal(any()))
                .thenThrow(new BusinessException(ErrorCode.PARAM_INVALID));

        mockMvc.perform(get("/api/v1/visits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30301));
    }

    @Test
    @DisplayName("反查就诊时档案不存在返回 30410")
    void rejectsUnknownAnimalCode() throws Exception {
        when(queryService.visitsByAnimal("CAT-2026-999"))
                .thenThrow(new BusinessException(ErrorCode.RECORD_NOT_FOUND));

        mockMvc.perform(get("/api/v1/visits").param("animalCode", "CAT-2026-999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(30410));
    }

    @Test
    @DisplayName("管理员新增合作医院：返回医院编号与规范化后的信息")
    void createsClinic() throws Exception {
        when(clinicService.create(any(), eq(1001L))).thenReturn(new ClinicView(
                7L, "校园东门合作医院（示例）", "李医生", "0000-0000002",
                "东门路 2 号", "绝育 280 元（示例价）"));

        mockMvc.perform(post("/api/v1/clinics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clinicName": " 校园东门合作医院（示例） ",
                                  "contact": "李医生",
                                  "phone": "0000-0000002",
                                  "address": "东门路 2 号",
                                  "priceNote": "绝育 280 元（示例价）"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.clinicId").value(7))
                .andExpect(jsonPath("$.data.clinicName").value("校园东门合作医院（示例）"))
                .andExpect(jsonPath("$.data.priceNote").value("绝育 280 元（示例价）"));

        verify(clinicService).create(any(), eq(1001L));
    }

    @Test
    @DisplayName("普通认养人不能新增合作医院，返回 20003")
    void rejectsStudentClinicCreate() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(post("/api/v1/clinics")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clinicName":"不该写入的医院"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    @DisplayName("管理员纠错就诊：返回更新后的诊断、处置与费用")
    void updatesVisit() throws Exception {
        VisitDetail updated = new VisitDetail(
                "VS20261120001", 11L, "CAT-2026-031", "三花", null, null,
                2L, "校园北门合作宠物医院（示例）",
                LocalDateTime.of(2026, 11, 20, 15, 0),
                "左前肢跛行", "软组织挫伤（复核）", "清创包扎、限制活动", 9000,
                List.of("F20261121001"), "校园巡查发现",
                LocalDateTime.of(2026, 11, 21, 12, 0));
        when(visitService.update(eq("VS20261120001"), any(), eq(1001L))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/visits/VS20261120001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "complaint": "左前肢跛行",
                                  "diagnosis": "软组织挫伤（复核）",
                                  "treatment": "清创包扎、限制活动",
                                  "cost": 9000,
                                  "fileIds": ["F20261121001"],
                                  "reason": "费用按实际票据更正"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.diagnosis").value("软组织挫伤（复核）"))
                .andExpect(jsonPath("$.data.treatment").value("清创包扎、限制活动"))
                .andExpect(jsonPath("$.data.cost").value(9000));

        verify(visitService).update(eq("VS20261120001"), any(), eq(1001L));
    }

    @Test
    @DisplayName("普通认养人不能纠错就诊，返回 20003")
    void rejectsStudentVisitUpdate() throws Exception {
        String token = jwtService.issueAccess(
                2002L, "ADOPTER-USER", "ADOPTER", Set.of("ADOPTER"), "测试环境").token();

        mockMvc.perform(put("/api/v1/visits/VS20261120001")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"diagnosis":"擅自修改","cost":1000,"reason":"测试"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(20003));
    }

    @Test
    @DisplayName("合作医院列表必须登录，缺少令牌返回 20001")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/clinics").header("Authorization", ""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20001));
    }

    private ClinicView clinic() {
        return new ClinicView(2L, "校园北门合作宠物医院（示例）", "王医生",
                "0000-0000001", "北门路 1 号（示例地址）", "绝育 300 元、疫苗 60 元/针（示例价）");
    }
}
