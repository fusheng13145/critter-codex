package com.just.critter.service.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.just.critter.domain.model.SysUserRole;
import com.just.critter.mapper.SysUserRoleMapper;
import com.just.critter.support.log.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 临时授权到期回收（FR-SYS-03 的收口）。
 *
 * <p>rolesOf 与菜单计算在"读"的这一侧已经过滤过期行（expire_at <= now 不生效），
 * 这里负责"写"的这一侧：把过期行标成 EXPIRED 落库，让
 * 用户列表、我的申请、审批工作台看到的是真实状态，而不是一堆过期却仍标 GRANTED 的脏行。
 */
@Service
public class RoleExpiryService {

    private static final Logger log = LoggerFactory.getLogger(RoleExpiryService.class);

    private final SysUserRoleMapper userRoleMapper;
    private final AuditLogService auditLog;
    private final Clock clock;

    public RoleExpiryService(SysUserRoleMapper userRoleMapper, AuditLogService auditLog, Clock clock) {
        this.userRoleMapper = userRoleMapper;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /** 回收到期授权，返回回收行数；每批写一条审计日志，避免日志被刷屏。 */
    @Transactional
    public int reclaim() {
        LocalDateTime now = LocalDateTime.now(clock);
        int reclaimed = userRoleMapper.update(null, Wrappers.<SysUserRole>lambdaUpdate()
                .eq(SysUserRole::getStatus, "GRANTED")
                .isNotNull(SysUserRole::getExpireAt)
                .le(SysUserRole::getExpireAt, now)
                .set(SysUserRole::getStatus, "EXPIRED"));
        if (reclaimed > 0) {
            log.info("临时授权回收 {} 行", reclaimed);
            auditLog.success("ROLE_EXPIRE", "sys_user_role", "batch",
                    null, Map.of("reclaimed", reclaimed, "batchAt", now.toString()));
        }
        return reclaimed;
    }
}
