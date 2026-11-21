package com.just.critter.service.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.just.critter.controller.dto.AppealDtos;
import com.just.critter.domain.model.SysAppeal;
import com.just.critter.domain.model.SysUser;
import com.just.critter.mapper.SysAppealMapper;
import com.just.critter.mapper.SysUserMapper;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 账号禁用申诉（FR-U-02 的救济通道）。
 *
 * <p>被禁用的账号登录会直接 30104，所以这个入口必须公开——只凭学号即可提交，
 * 落库后由系统管理员线下核实。规则三条：账号必须存在（30410）；
 * 只有禁用状态才需要申诉（30402）；同一学号已有待处理申诉时不重复建单，
 * 直接回原记录（幂等），前端据此提示"已收到，无需重复提交"。</p>
 */
@Service
public class AppealService {

    private static final String STATUS_DISABLED = "DISABLED";
    private static final String STATUS_PENDING = "PENDING";

    private final SysAppealMapper appealMapper;
    private final SysUserMapper userMapper;
    private final AuditLogService auditLog;

    public AppealService(SysAppealMapper appealMapper, SysUserMapper userMapper, AuditLogService auditLog) {
        this.appealMapper = appealMapper;
        this.userMapper = userMapper;
        this.auditLog = auditLog;
    }

    @Transactional
    public AppealDtos.AppealResponse submit(AppealDtos.AppealRequest request, String ip) {
        String userNo = request.userNo().trim();
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUserNo, userNo)
                .last("limit 1"));
        if (user == null) {
            auditLog.fail("APPEAL", "sys_appeal", userNo, ErrorCode.RECORD_NOT_FOUND.getCode(),
                    Map.of("reason", "账号不存在"));
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND.getCode(), "该账号不存在，请核对学号");
        }
        if (!STATUS_DISABLED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.STATE_TRANSITION_INVALID.getCode(),
                    "账号当前状态为「" + statusLabel(user.getStatus()) + "」，无需申诉");
        }

        SysAppeal existing = appealMapper.selectOne(Wrappers.<SysAppeal>lambdaQuery()
                .eq(SysAppeal::getUserNo, userNo)
                .eq(SysAppeal::getStatus, STATUS_PENDING)
                .orderByDesc(SysAppeal::getId)
                .last("limit 1"));
        if (existing != null) {
            return toView(existing, "已收到你的申诉，正在等待管理员处理，无需重复提交");
        }

        SysAppeal appeal = new SysAppeal();
        appeal.setUserNo(userNo);
        appeal.setReason(request.reason().trim());
        appeal.setContact(request.contact() == null || request.contact().isBlank() ? null : request.contact().trim());
        appeal.setStatus(STATUS_PENDING);
        appeal.setIp(ip);
        // 显式给创建时间：库里虽有 DEFAULT CURRENT_TIMESTAMP，但 insert 后实体不会回读该列
        appeal.setCreatedAt(java.time.LocalDateTime.now());
        appealMapper.insert(appeal);

        // 申诉人此刻登不进来，actor 记 0/GUEST，靠 userNo 关联到人
        auditLog.successAs(0L, "GUEST", "APPEAL", "sys_appeal", userNo,
                null, Map.of("appealId", appeal.getId(), "ip", ip == null ? "" : ip));

        return toView(appeal, "申诉已提交，管理员核实后会通过站内公告或你留下的联系方式回复");
    }

    private AppealDtos.AppealResponse toView(SysAppeal appeal, String message) {
        return new AppealDtos.AppealResponse(appeal.getId(), appeal.getUserNo(), appeal.getStatus(),
                "PENDING".equals(appeal.getStatus()) ? "待处理" : "已处理", message, appeal.getCreatedAt());
    }

    private static String statusLabel(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case "PENDING" -> "待激活";
            case "ACTIVE" -> "正常";
            case "LOCKED" -> "锁定";
            case "DISABLED" -> "已禁用";
            default -> status;
        };
    }
}
