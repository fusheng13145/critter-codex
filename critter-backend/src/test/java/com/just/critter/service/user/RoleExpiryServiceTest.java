package com.just.critter.service.user;

import com.just.critter.mapper.SysUserRoleMapper;
import com.just.critter.support.log.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 临时授权到期回收（FR-SYS-03 验收项）：
 * 到期行 GRANTED→EXPIRED，回收量 >0 时写一条批量审计日志。
 */
@ExtendWith(MockitoExtension.class)
class RoleExpiryServiceTest {

    @Mock
    private SysUserRoleMapper userRoles;
    @Mock
    private AuditLogService audit;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T07:40:00Z"), ZoneId.of("Asia/Shanghai"));

    @Test
    @DisplayName("回收 2 行：写 ROLE_EXPIRE 审计日志并返回行数")
    void reclaim_marks_expired_rows_and_logs() {
        when(userRoles.update(isNull(), any())).thenReturn(2);
        RoleExpiryService service = new RoleExpiryService(userRoles, audit, clock);

        int reclaimed = service.reclaim();

        assertThat(reclaimed).isEqualTo(2);
        verify(audit).success(eq("ROLE_EXPIRE"), eq("sys_user_role"), eq("batch"),
                isNull(), any());
    }

    @Test
    @DisplayName("没有到期行：不写日志，安静返回 0")
    void reclaim_without_expired_rows_is_quiet() {
        when(userRoles.update(isNull(), any())).thenReturn(0);
        RoleExpiryService service = new RoleExpiryService(userRoles, audit, clock);

        assertThat(service.reclaim()).isZero();
        verify(audit, never()).success(anyString(), anyString(), anyString(), any(), any());
    }
}
