package com.just.critter.adopt;

import com.just.critter.adopt.domain.AdoptionPeriod;
import com.just.critter.adopt.domain.AdoptionStatus;
import com.just.critter.adopt.repository.AdoptionRepository;
import com.just.critter.adopt.service.AdoptionDetail;
import com.just.critter.adopt.service.AdoptionQueryService;
import com.just.critter.adopt.service.AdoptionSummary;
import com.just.critter.adopt.service.CertificateView;
import com.just.critter.adopt.service.AdoptionDetailRecord;
import com.just.critter.adopt.service.AdoptionPaymentRecord;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.api.PageResult;
import com.just.critter.support.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdoptionQueryServiceTests {

    private static final String ADOPTION_NO = "AD20261121003";
    private AdoptionRepository adoptions;
    private AdoptionQueryService service;

    @BeforeEach
    void setUp() {
        adoptions = mock(AdoptionRepository.class);
        service = new AdoptionQueryService(adoptions);
    }

    @Test
    @DisplayName("管理员列表：按状态筛选并返回分页与脱敏联系方式")
    void listsForAdminWithMaskedContact() {
        when(adoptions.count(null, "PENDING_AUDIT")).thenReturn(41L);
        when(adoptions.findPage(null, "PENDING_AUDIT", 20, 20))
                .thenReturn(List.of(detailRecord(AdoptionStatus.PENDING_AUDIT)));

        PageResult<AdoptionSummary> result = service.list("PENDING_AUDIT", new PageQuery(2, 20));

        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).adoptionNo()).isEqualTo(ADOPTION_NO);
        assertThat(result.getList().get(0).contactMasked()).isEqualTo("138****0000");
        assertThat(result.getPage().getPage()).isEqualTo(2);
        assertThat(result.getPage().getTotal()).isEqualTo(41);
        assertThat(result.getPage().getPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("我的认养：只按当前用户查询，状态为空时查全部")
    void listsMineByCurrentUser() {
        when(adoptions.count(1001L, null)).thenReturn(1L);
        when(adoptions.findPage(1001L, null, 0, 20))
                .thenReturn(List.of(detailRecord(AdoptionStatus.ACTIVE)));

        PageResult<AdoptionSummary> result = service.mine(1001L, null, new PageQuery(1, 20));

        assertThat(result.getList()).extracting(AdoptionSummary::status)
                .containsExactly(AdoptionStatus.ACTIVE);
        verify(adoptions).findPage(1001L, null, 0, 20);
    }

    @Test
    @DisplayName("详情：本人可查看，联系方式脱敏并带缴费流水")
    void detailMasksContactAndIncludesPayments() {
        AdoptionDetailRecord record = detailRecord(AdoptionStatus.ACTIVE);
        when(adoptions.findDetail(ADOPTION_NO)).thenReturn(Optional.of(record));
        when(adoptions.findPayments(101L)).thenReturn(List.of(
                new AdoptionPaymentRecord("PAY20261121001", 3000,
                        LocalDateTime.of(2026, 11, 21, 10, 0), List.of("F20261121001"),
                        "线下转账", LocalDateTime.of(2026, 11, 21, 10, 5))));

        AdoptionDetail detail = service.detail(ADOPTION_NO, 1001L, false);

        assertThat(detail.contactMasked()).isEqualTo("138****0000");
        assertThat(detail.certificateNo()).isEqualTo("QYWL-AD-2026-0031");
        assertThat(detail.payments()).hasSize(1);
        assertThat(detail.payments().get(0).paidAmount()).isEqualTo(3000);
    }

    @Test
    @DisplayName("详情：非本人且非管理员返回 20002")
    void detailRejectsOtherUser() {
        when(adoptions.findDetail(ADOPTION_NO))
                .thenReturn(Optional.of(detailRecord(AdoptionStatus.PENDING_AUDIT)));

        assertThatThrownBy(() -> service.detail(ADOPTION_NO, 2002L, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(20002));
        verify(adoptions, never()).findPayments(101L);
    }

    @Test
    @DisplayName("详情：管理员可看完整联系方式")
    void adminSeesFullContact() {
        when(adoptions.findDetail(ADOPTION_NO))
                .thenReturn(Optional.of(detailRecord(AdoptionStatus.PENDING_AUDIT)));
        when(adoptions.findPayments(101L)).thenReturn(List.of());

        AdoptionDetail detail = service.detail(ADOPTION_NO, 9001L, true);

        assertThat(detail.contactMasked()).isEqualTo("13800000000");
    }

    @Test
    @DisplayName("记录不存在返回 30410")
    void missingAdoptionReturns30410() {
        when(adoptions.findDetail(ADOPTION_NO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(ADOPTION_NO, 1001L, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30410));
    }

    @Test
    @DisplayName("证书：生效关系返回可打印证书数据")
    void certificateForActiveAdoption() {
        when(adoptions.findDetail(ADOPTION_NO))
                .thenReturn(Optional.of(detailRecord(AdoptionStatus.ACTIVE)));

        CertificateView view = service.certificate(ADOPTION_NO, 1001L, false);

        assertThat(view.adoptionNo()).isEqualTo(ADOPTION_NO);
        assertThat(view.certificateNo()).isEqualTo("QYWL-AD-2026-0031");
        assertThat(view.adopterName()).isEqualTo("青檐一号");
        assertThat(view.startDate()).isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(view.endDate()).isEqualTo(LocalDate.of(2026, 12, 21));
    }

    @Test
    @DisplayName("未入账或没有证书号时返回 30412")
    void certificateRejectsUnverifiedAdoption() {
        AdoptionDetailRecord pending = new AdoptionDetailRecord(
                101L, ADOPTION_NO, 1001L, "青檐一号", "CAT-2026-031", "三花", 2L, "月度守护",
                3000, AdoptionPeriod.MONTH, AdoptionStatus.PENDING_PAYMENT,
                "13800000000", null, null, null, null, null,
                LocalDateTime.of(2026, 11, 21, 10, 0));
        when(adoptions.findDetail(ADOPTION_NO)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.certificate(ADOPTION_NO, 1001L, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(30412));
    }

    private AdoptionDetailRecord detailRecord(AdoptionStatus status) {
        return new AdoptionDetailRecord(
                101L, ADOPTION_NO, 1001L, "青檐一号", "CAT-2026-031", "三花", 2L, "月度守护",
                3000, AdoptionPeriod.MONTH, status, "13800000000", "材料齐全",
                LocalDate.of(2026, 11, 21), LocalDate.of(2026, 12, 21),
                "QYWL-AD-2026-0031",
                LocalDateTime.of(2026, 11, 28, 12, 0),
                LocalDateTime.of(2026, 11, 21, 10, 0));
    }
}
