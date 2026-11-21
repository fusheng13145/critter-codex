package com.just.critter.service.fund;

import com.just.critter.config.CritterProperties;
import com.just.critter.domain.enums.DonationStatus;
import com.just.critter.domain.model.Donation;
import com.just.critter.domain.model.Project;
import com.just.critter.mapper.DonationMapper;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 捐赠证书：仅已入账可签发，实名本人或管理员可下载，产出带中文的 PDF。
 */
@ExtendWith(MockitoExtension.class)
class DonationCertificateServiceTest {

    @Mock
    private DonationMapper donations;
    @Mock
    private ProjectMapper projects;
    @Mock
    private AuditLogService auditLog;

    @Test
    @DisplayName("已入账捐赠：管理员可下载，输出 PDF 文件头")
    void pdf_renders_for_verified_donation() {
        Donation donation = donation(DonationStatus.VERIFIED);
        Project project = new Project();
        project.setId(21L);
        project.setName("校园流浪猫绝育计划");
        when(donations.selectByDonationNo("DN20261121001")).thenReturn(donation);
        when(projects.selectById(21L)).thenReturn(project);

        DonationCertificateService service = new DonationCertificateService(
                donations, projects, new CritterProperties(), auditLog);
        byte[] pdf = service.pdf("DN20261121001", admin());

        assertThat(pdf.length).isGreaterThan(1000);
        String header = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
        assertThat(service.fileName("DN20261121001")).contains("DN20261121001").endsWith(".pdf");
    }

    @Test
    @DisplayName("未入账或非本人：拒绝签发")
    void pdf_rejects_unverified_and_other_user() {
        Donation pending = donation(DonationStatus.PENDING_VERIFY);
        when(donations.selectByDonationNo("DN20261121001")).thenReturn(pending);

        DonationCertificateService service = new DonationCertificateService(
                donations, projects, new CritterProperties(), auditLog);
        assertThatThrownBy(() -> service.pdf("DN20261121001", admin()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(com.just.critter.support.exception.ErrorCode.PAYMENT_UNVERIFIED.getCode()));

        Donation verified = donation(DonationStatus.VERIFIED);
        verified.setDonationNo("DN20261121002");
        when(donations.selectByDonationNo("DN20261121002")).thenReturn(verified);
        CurrentUser stranger = new CurrentUser(2002L, "2023020002", "学生", "STUDENT",
                Set.of("STUDENT"), "计算机学院", "jwt-2", null);
        assertThatThrownBy(() -> service.pdf("DN20261121002", stranger))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(com.just.critter.support.exception.ErrorCode.NO_PERMISSION.getCode()));
    }

    private Donation donation(DonationStatus status) {
        Donation donation = new Donation();
        donation.setDonationNo("DN20261121001");
        donation.setProjectId(21L);
        donation.setUserId(1001L);
        donation.setDonorDisplay("张同学");
        donation.setAmount(10_000);
        donation.setStatus(status);
        donation.setVerifiedAt(LocalDateTime.parse("2026-11-20T09:30:00"));
        return donation;
    }

    private CurrentUser admin() {
        return new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jwt-admin", null);
    }
}
