package com.just.critter.service.fund;

import com.just.critter.domain.enums.DonationStatus;
import com.just.critter.domain.enums.ProjectStatus;
import com.just.critter.domain.enums.ProjectTargetType;
import com.just.critter.domain.fund.DonationCodeGenerator;
import com.just.critter.domain.model.Donation;
import com.just.critter.domain.model.Project;
import com.just.critter.domain.model.SysUser;
import com.just.critter.mapper.DonationMapper;
import com.just.critter.mapper.ProjectMapper;
import com.just.critter.mapper.SysUserMapper;
import com.just.critter.message.repository.MessageRepository;
import com.just.critter.message.service.MessageWrite;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T3.1 验收：登记与入账两态分离；只有核对入账后才计入 project.raised_amount。
 * 金额单位为分；进度百分比向下取整。
 */
@ExtendWith(MockitoExtension.class)
class DonationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-21T02:31:00Z"), ZONE);

    @Mock
    private DonationMapper donationMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private AuditLogService auditLog;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private MessageRepository messages;

    private DonationService service;

    @BeforeEach
    void setUp() {
        service = new DonationService(donationMapper, projectMapper, userMapper,
                new DonationCodeGenerator((bizType, year) -> 1, CLOCK),
                auditLog, transactionTemplate, messages, CLOCK);
        lenient().when(messages.insert(any(MessageWrite.class))).thenReturn(1);
    }

    @Test
    @DisplayName("登记：匿名捐赠落 PENDING_VERIFY，不写 verifiedAt，不动项目进度")
    void create_persists_pending_without_progress() {
        when(projectMapper.selectByProjectNo("PRJ-2026-007")).thenReturn(project(ProjectStatus.FUNDING, 0));
        doAnswer(invocation -> {
            Donation donation = invocation.getArgument(0);
            donation.setId(21L);
            return 1;
        }).when(donationMapper).insert(any(Donation.class));

        CreatedDonation created = service.create(new CreateDonationCommand(
                "PRJ-2026-007", 10_000, true, "加油", List.of("file-1")), user());

        assertThat(created.donationNo()).isEqualTo("DN202611210001");
        assertThat(created.status()).isEqualTo(DonationStatus.PENDING_VERIFY);
        assertThat(created.note()).contains("待管理员核对");

        ArgumentCaptor<Donation> captor = ArgumentCaptor.forClass(Donation.class);
        verify(donationMapper).insert(captor.capture());
        Donation saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo(11L);
        assertThat(saved.getAmount()).isEqualTo(10_000);
        assertThat(saved.getAnonymous()).isTrue();
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getDonorDisplay()).isEqualTo("爱心人士");
        assertThat(saved.getMessage()).isEqualTo("加油");
        assertThat(saved.getVoucherFileIds()).containsExactly("file-1");
        assertThat(saved.getStatus()).isEqualTo(DonationStatus.PENDING_VERIFY);
        assertThat(saved.getVerifierId()).isNull();
        assertThat(saved.getVerifiedAt()).isNull();
        assertThat(saved.getCreatedBy()).isEqualTo(9001L);

        verify(projectMapper, never()).updateById(any(Project.class));
        verify(auditLog).successAs(any(Long.class), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("登记：实名捐赠保留用户与昵称展示")
    void create_named_donor_keeps_user_and_nickname() {
        when(projectMapper.selectByProjectNo("PRJ-2026-007")).thenReturn(project(ProjectStatus.FUNDING, 0));
        SysUser user = new SysUser();
        user.setNickname("小林");
        when(userMapper.selectById(9001L)).thenReturn(user);
        doAnswer(invocation -> {
            Donation donation = invocation.getArgument(0);
            donation.setId(21L);
            return 1;
        }).when(donationMapper).insert(any(Donation.class));

        service.create(new CreateDonationCommand(
                "PRJ-2026-007", 5_000, false, null, List.of("file-1")), user());

        ArgumentCaptor<Donation> captor = ArgumentCaptor.forClass(Donation.class);
        verify(donationMapper).insert(captor.capture());
        verify(userMapper).selectById(9001L);
        assertThat(captor.getValue().getUserId()).isEqualTo(9001L);
        assertThat(captor.getValue().getDonorDisplay()).isEqualTo("小林");
    }

    @Test
    @DisplayName("登记：金额 0 或超过 10000000 分均为 40102")
    void create_rejects_illegal_amount() {
        when(projectMapper.selectByProjectNo("PRJ-2026-007")).thenReturn(project(ProjectStatus.FUNDING, 0));

        assertThatThrownBy(() -> service.create(new CreateDonationCommand(
                "PRJ-2026-007", 0, false, null, List.of("file-1")), user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.DONATION_AMOUNT_INVALID.getCode()));

        assertThatThrownBy(() -> service.create(new CreateDonationCommand(
                "PRJ-2026-007", 10_000_001, false, null, List.of("file-1")), user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.DONATION_AMOUNT_INVALID.getCode()));

        verify(donationMapper, never()).insert(any(Donation.class));
    }

    @Test
    @DisplayName("登记：非募集中项目 40108，不产生流水")
    void create_rejects_non_funding_project() {
        when(projectMapper.selectByProjectNo("PRJ-2026-007")).thenReturn(project(ProjectStatus.PENDING_AUDIT, 0));

        assertThatThrownBy(() -> service.create(new CreateDonationCommand(
                "PRJ-2026-007", 5_000, false, null, List.of("file-1")), user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PROJECT_STATE_INVALID.getCode()));

        verify(donationMapper, never()).insert(any(Donation.class));
    }

    @Test
    @DisplayName("核对：金额一致 -> VERIFIED，同事务递增 project.raised_amount")
    void confirm_verifies_and_increments_project() {
        Donation donation = donation("DN202611210001", DonationStatus.PENDING_VERIFY, 10_000);
        Project project = project(ProjectStatus.FUNDING, 50_000);
        when(donationMapper.selectByDonationNoForUpdate("DN202611210001")).thenReturn(donation);
        when(projectMapper.selectByIdForUpdate(11L)).thenReturn(project);
        when(donationMapper.sumVerifiedByProjectId(11L)).thenReturn(50_000);

        DonationConfirmation confirmation = service.confirm("DN202611210001",
                new ConfirmDonationCommand(10_000, LocalDateTime.parse("2026-11-20T10:00:00"),
                        List.of("file-proof"), "到账"), user());

        assertThat(confirmation.status()).isEqualTo(DonationStatus.VERIFIED);
        assertThat(confirmation.verifiedAt()).isEqualTo(OffsetDateTime.parse("2026-11-21T10:31:00+08:00"));
        assertThat(confirmation.progress().raisedAmount()).isEqualTo(60_000);
        assertThat(confirmation.progress().targetAmount()).isEqualTo(300_000);
        assertThat(confirmation.progress().progressPercent()).isEqualTo(20);

        assertThat(donation.getStatus()).isEqualTo(DonationStatus.VERIFIED);
        assertThat(donation.getVerifierId()).isEqualTo(9001L);
        assertThat(donation.getVerifiedAt()).isEqualTo(LocalDateTime.parse("2026-11-21T10:31:00"));
        assertThat(donation.getVoucherFileIds()).containsExactly("file-proof");
        assertThat(project.getRaisedAmount()).isEqualTo(60_000);
        verify(projectMapper).updateById(project);
        verify(donationMapper).updateById(donation);
    }

    @Test
    @DisplayName("入账成功后向实名捐赠人写 DONATION_PROGRESS")
    void confirm_writes_donation_progress_message() {
        Donation donation = donation("DN202611210001", DonationStatus.PENDING_VERIFY, 10_000);
        Project project = project(ProjectStatus.FUNDING, 50_000);
        when(donationMapper.selectByDonationNoForUpdate("DN202611210001")).thenReturn(donation);
        when(projectMapper.selectByIdForUpdate(11L)).thenReturn(project);
        when(donationMapper.sumVerifiedByProjectId(11L)).thenReturn(50_000);

        service.confirm("DN202611210001",
                new ConfirmDonationCommand(10_000, null, null, "到账"), user());

        ArgumentCaptor<MessageWrite> captor = ArgumentCaptor.forClass(MessageWrite.class);
        verify(messages).insert(captor.capture());
        MessageWrite message = captor.getValue();
        assertThat(message.receiverId()).isEqualTo(9001L);
        assertThat(message.type()).isEqualTo("DONATION_PROGRESS");
        assertThat(message.bizType()).isEqualTo("DONATION");
        assertThat(message.bizId()).isEqualTo("DN202611210001");
        assertThat(message.title()).contains("已入账");
        assertThat(message.title()).contains("100.00");
    }

    @Test
    @DisplayName("核对异常时向实名捐赠人写 DONATION_PROGRESS")
    void abnormal_writes_donation_progress_message() {
        Donation donation = donation("DN202611210001", DonationStatus.PENDING_VERIFY, 10_000);
        Project project = project(ProjectStatus.FUNDING, 50_000);
        when(donationMapper.selectByDonationNoForUpdate("DN202611210001")).thenReturn(donation);
        when(projectMapper.selectByIdForUpdate(11L)).thenReturn(project);
        when(donationMapper.sumVerifiedByProjectId(11L)).thenReturn(50_000);

        service.confirm("DN202611210001",
                new ConfirmDonationCommand(8_000, null, null, "少 20 元"), user());

        ArgumentCaptor<MessageWrite> captor = ArgumentCaptor.forClass(MessageWrite.class);
        verify(messages).insert(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("DONATION_PROGRESS");
        assertThat(captor.getValue().title()).contains("核对异常");
    }

    @Test
    @DisplayName("匿名捐赠没有接收人，核对后不写消息")
    void anonymous_donation_writes_no_message() {
        Donation donation = donation("DN202611210001", DonationStatus.PENDING_VERIFY, 10_000);
        donation.setUserId(null);
        donation.setAnonymous(true);
        Project project = project(ProjectStatus.FUNDING, 50_000);
        when(donationMapper.selectByDonationNoForUpdate("DN202611210001")).thenReturn(donation);
        when(projectMapper.selectByIdForUpdate(11L)).thenReturn(project);
        when(donationMapper.sumVerifiedByProjectId(11L)).thenReturn(50_000);

        service.confirm("DN202611210001",
                new ConfirmDonationCommand(10_000, null, null, "到账"), user());

        verify(messages, never()).insert(any(MessageWrite.class));
    }

    @Test
    @DisplayName("核对：实收金额不一致 -> ABNORMAL，进度保持不变")
    void confirm_mismatch_marks_abnormal_without_increment() {
        Donation donation = donation("DN202611210001", DonationStatus.PENDING_VERIFY, 10_000);
        Project project = project(ProjectStatus.FUNDING, 50_000);
        when(donationMapper.selectByDonationNoForUpdate("DN202611210001")).thenReturn(donation);
        when(projectMapper.selectByIdForUpdate(11L)).thenReturn(project);
        when(donationMapper.sumVerifiedByProjectId(11L)).thenReturn(50_000);

        DonationConfirmation confirmation = service.confirm("DN202611210001",
                new ConfirmDonationCommand(8_000, LocalDateTime.parse("2026-11-20T10:00:00"),
                        null, "实际到账 80 元"), user());

        assertThat(confirmation.status()).isEqualTo(DonationStatus.ABNORMAL);
        assertThat(confirmation.amount()).isEqualTo(10_000);
        assertThat(confirmation.progress().raisedAmount()).isEqualTo(50_000);
        assertThat(confirmation.note()).contains("金额不一致");
        assertThat(donation.getStatus()).isEqualTo(DonationStatus.ABNORMAL);
        assertThat(donation.getVerifiedAt()).isNull();
        assertThat(project.getRaisedAmount()).isEqualTo(50_000);
        verify(projectMapper, never()).updateById(any(Project.class));
        verify(donationMapper).updateById(donation);
    }

    @Test
    @DisplayName("核对：已入账流水不可重复核对，30301")
    void confirm_rejects_already_verified() {
        when(donationMapper.selectByDonationNoForUpdate("DN202611210001"))
                .thenReturn(donation("DN202611210001", DonationStatus.VERIFIED, 10_000));

        assertThatThrownBy(() -> service.confirm("DN202611210001",
                new ConfirmDonationCommand(10_000, null, null, null), user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> {
                            assertThat(ex.getCode()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
                            assertThat(ex.getMessage()).contains("已入账");
                        });

        verify(donationMapper, never()).updateById(any(Donation.class));
    }

    @Test
    @DisplayName("核对：项目已筹与已入账流水不一致 -> 40106，拒绝把未入账金额计入进度")
    void confirm_rejects_manual_raised_amount_with_40106() {
        Donation donation = donation("DN202611210001", DonationStatus.PENDING_VERIFY, 10_000);
        Project project = project(ProjectStatus.FUNDING, 60_000);
        when(donationMapper.selectByDonationNoForUpdate("DN202611210001")).thenReturn(donation);
        when(projectMapper.selectByIdForUpdate(11L)).thenReturn(project);
        when(donationMapper.sumVerifiedByProjectId(11L)).thenReturn(50_000);

        assertThatThrownBy(() -> service.confirm("DN202611210001",
                new ConfirmDonationCommand(10_000, null, null, null), user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.DONATION_NOT_VERIFIED.getCode()));

        verify(donationMapper, never()).updateById(any(Donation.class));
        verify(projectMapper, never()).updateById(any(Project.class));
    }

    @Test
    @DisplayName("核对：项目非募集中 40108，不允许继续入账")
    void confirm_rejects_non_funding_project() {
        when(donationMapper.selectByDonationNoForUpdate("DN202611210001"))
                .thenReturn(donation("DN202611210001", DonationStatus.PENDING_VERIFY, 10_000));
        when(projectMapper.selectByIdForUpdate(11L)).thenReturn(project(ProjectStatus.SETTLED, 50_000));

        assertThatThrownBy(() -> service.confirm("DN202611210001",
                new ConfirmDonationCommand(10_000, null, null, null), user()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.PROJECT_STATE_INVALID.getCode()));

        verify(donationMapper, never()).updateById(any(Donation.class));
    }

    @Test
    @DisplayName("批量核对：一笔成功一笔不存在，部分成功不回滚")
    void confirm_batch_returns_partial_success() {
        Donation donation = donation("DN202611210001", DonationStatus.PENDING_VERIFY, 10_000);
        Project project = project(ProjectStatus.FUNDING, 50_000);
        when(donationMapper.selectByDonationNoForUpdate("DN202611210001")).thenReturn(donation);
        when(donationMapper.selectByDonationNoForUpdate("DN202611210404")).thenReturn(null);
        when(projectMapper.selectByIdForUpdate(11L)).thenReturn(project);
        when(donationMapper.sumVerifiedByProjectId(11L)).thenReturn(50_000);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
        });

        BatchConfirmResult result = service.confirmBatch(
                List.of("DN202611210001", "DN202611210404"), user());

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().get(0).id()).isEqualTo("DN202611210404");
        assertThat(result.failed().get(0).code()).isEqualTo(ErrorCode.RECORD_NOT_FOUND.getCode());
        assertThat(donation.getStatus()).isEqualTo(DonationStatus.VERIFIED);
        assertThat(project.getRaisedAmount()).isEqualTo(60_000);
    }

    @Test
    @DisplayName("凭证异常：PENDING_VERIFY 可标记 ABNORMAL、替换凭证并给实名捐赠人发消息")
    void flag_marks_abnormal_and_notifies_donor() {
        Donation target = donation("DN20261121001", DonationStatus.PENDING_VERIFY, 10_000);
        when(donationMapper.selectByDonationNoForUpdate("DN20261121001")).thenReturn(target);
        CurrentUser admin = new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jwt-admin", null);

        DonationFlagResult result = service.flag("DN20261121001",
                new FlagDonationCommand("凭证金额与登记金额不一致", List.of("file-2")), admin);

        assertThat(result.status()).isEqualTo(DonationStatus.ABNORMAL);
        assertThat(result.abnormalReason()).isEqualTo("凭证金额与登记金额不一致");
        assertThat(result.voucherFileIds()).containsExactly("file-2");
        assertThat(target.getAbnormalReason()).isEqualTo("凭证金额与登记金额不一致");
        verify(donationMapper).updateById(target);
        verify(messages).insert(any(MessageWrite.class));
        verify(auditLog).successAs(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("凭证异常：VERIFIED 已入账不可直接标记，提示先冲正")
    void flag_rejects_verified_donation() {
        Donation verified = donation("DN20261121002", DonationStatus.VERIFIED, 10_000);
        when(donationMapper.selectByDonationNoForUpdate("DN20261121002")).thenReturn(verified);
        CurrentUser admin = new CurrentUser(9001L, "admin", "管理员", "ANIMAL_ADMIN",
                Set.of("ANIMAL_ADMIN"), "后勤处", "jwt-admin", null);

        assertThatThrownBy(() -> service.flag("DN20261121002",
                new FlagDonationCommand("凭证模糊无法核验", null), admin))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(ErrorCode.PARAM_INVALID.getCode()));
        verify(donationMapper, never()).updateById(any(Donation.class));
    }

    private Project project(ProjectStatus status, int raisedAmount) {
        Project project = new Project();
        project.setId(11L);
        project.setProjectNo("PRJ-2026-007");
        project.setName("冬季流浪猫医疗基金");
        project.setTargetType(ProjectTargetType.ANIMAL);
        project.setTargetAmount(300_000);
        project.setRaisedAmount(raisedAmount);
        project.setSpentAmount(0);
        project.setStartDate(LocalDate.parse("2026-11-01"));
        project.setEndDate(LocalDate.parse("2026-12-31"));
        project.setStatus(status);
        project.setDeleted(0);
        project.setCreatedAt(LocalDateTime.parse("2026-11-01T09:00:00"));
        project.setUpdatedAt(LocalDateTime.parse("2026-11-01T09:00:00"));
        return project;
    }

    private Donation donation(String donationNo, DonationStatus status, int amount) {
        Donation donation = new Donation();
        donation.setId(21L);
        donation.setDonationNo(donationNo);
        donation.setProjectId(11L);
        donation.setUserId(9001L);
        donation.setDonorDisplay("小林");
        donation.setAmount(amount);
        donation.setAnonymous(false);
        donation.setVoucherFileIds(List.of("file-1"));
        donation.setStatus(status);
        donation.setCreatedAt(LocalDateTime.parse("2026-11-20T10:00:00"));
        donation.setUpdatedAt(LocalDateTime.parse("2026-11-20T10:00:00"));
        donation.setCreatedBy(9001L);
        return donation;
    }

    private CurrentUser user() {
        return new CurrentUser(9001L, "U2026001", "小林", "STUDENT",
                Set.of("STUDENT"), "软件工程", "jwt-1", Instant.parse("2026-11-21T02:31:00Z"));
    }
}
