package com.just.critter.service.user;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.just.critter.controller.dto.UserDtos;
import com.just.critter.domain.model.SysLog;
import com.just.critter.domain.model.SysUser;
import com.just.critter.domain.model.SysUserRole;
import com.just.critter.mapper.SysLogMapper;
import com.just.critter.mapper.SysUserMapper;
import com.just.critter.mapper.SysUserRoleMapper;
import com.just.critter.service.auth.AuthService;
import com.just.critter.service.support.MenuMatrix;
import com.just.critter.support.api.PageQuery;
import com.just.critter.support.cache.KeyValueStore;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.AesCipher;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import com.just.critter.support.security.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 个人中心与用户治理：资料维护、改密、身份申请、登录日志、用户列表与禁用。
 *
 * <p>身份申请借用 sys_user_role 表达状态：status=PENDING 且 granted_at 为空即待审，
 * 审核通过才写 granted_at 并参与 rolesOf 计算，"申请了但没批"不会提前拿到菜单。
 */
@Service
public class UserService {

    private static final Set<String> APPLICABLE_ROLES = Set.of("ADOPTER", "VOLUNTEER");
    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "DISABLED", "LOCKED", "PENDING");

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysLogMapper logMapper;
    private final PasswordHasher passwordHasher;
    private final AesCipher aesCipher;
    private final AuthService authService;
    private final AuditLogService auditLog;
    private final KeyValueStore store;

    public UserService(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper, SysLogMapper logMapper,
                       PasswordHasher passwordHasher, AesCipher aesCipher, AuthService authService,
                       AuditLogService auditLog, KeyValueStore store) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.logMapper = logMapper;
        this.passwordHasher = passwordHasher;
        this.aesCipher = aesCipher;
        this.authService = authService;
        this.auditLog = auditLog;
        this.store = store;
    }

    // ---------------- 个人资料 ----------------

    public UserDtos.ProfileResponse profile(long userId) {
        SysUser user = requireUser(userId);
        Set<String> roles = authService.rolesOf(user);
        String plainPhone = aesCipher.decrypt(user.getPhone());
        List<UserDtos.MenuView> menus = new ArrayList<>();
        for (String code : MenuMatrix.menusFor(roles)) {
            MenuMatrix.MenuDef def = MenuMatrix.MENUS.stream()
                    .filter(item -> item.code().equals(code))
                    .findFirst()
                    .orElse(null);
            if (def != null) {
                menus.add(new UserDtos.MenuView(def.code(), def.title(), def.path(), def.owner()));
            }
        }
        return new UserDtos.ProfileResponse(user.getId(), user.getUserNo(), user.getEmail(), user.getNickname(),
                user.getDept(), plainPhone, AesCipher.mask(plainPhone), user.getPrimaryRole(),
                MenuMatrix.roleLabel(user.getPrimaryRole()), new ArrayList<>(roles), menus,
                user.getStatus(), user.getCreatedAt());
    }

    @Transactional
    public UserDtos.ProfileResponse updateProfile(long userId, UserDtos.UpdateProfileRequest request) {
        SysUser before = requireUser(userId);
        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setNickname(request.nickname().trim());
        patch.setDept(request.dept() == null ? null : request.dept().trim());
        String phone = request.phone();
        boolean clearPhone = phone != null && phone.isBlank();
        if (clearPhone) {
            userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                    .eq(SysUser::getId, userId)
                    .set(SysUser::getNickname, patch.getNickname())
                    .set(SysUser::getDept, patch.getDept())
                    .set(SysUser::getPhone, null));
        } else {
            patch.setPhone(phone == null ? before.getPhone() : aesCipher.encrypt(phone.trim()));
            userMapper.updateById(patch);
        }
        auditLog.success("PROFILE_UPDATE", "sys_user", before.getUserNo(),
                Map.of("nickname", before.getNickname() == null ? "" : before.getNickname(),
                        "dept", before.getDept() == null ? "" : before.getDept()),
                Map.of("nickname", patch.getNickname(),
                        "dept", patch.getDept() == null ? "" : patch.getDept()));
        return profile(userId);
    }

    /**
     * 改密：校验原密码 → 新密码强度 → BCrypt 重哈希 → 写 min-iat，
     * 使该用户此前签发的所有令牌立即失效（验收项"改密后旧令牌失效"）。
     */
    @Transactional
    public void changePassword(long userId, UserDtos.ChangePasswordRequest request) {
        SysUser user = requireUser(userId);
        if (!passwordHasher.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS.getCode(), "原密码不正确");
        }
        if (passwordHasher.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "新密码不能与原密码相同",
                    List.of(new com.just.critter.support.exception.FieldErrorItem("newPassword", "新密码不能与原密码相同")));
        }
        String hash = passwordHasher.hash(request.newPassword());
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, userId)
                .set(SysUser::getPasswordHash, hash));

        store.set(AuthService.MIN_IAT_PREFIX + userId,
                String.valueOf(Instant.now().getEpochSecond()),
                Duration.ofSeconds(7 * 24 * 3600L));
        auditLog.success("PASSWORD_CHANGE", "sys_user", user.getUserNo(), null,
                Map.of("note", "改密后旧令牌全部失效"));
    }

    // ---------------- 身份申请 ----------------

    @Transactional
    public UserDtos.ApplyRoleResponse applyRole(long userId, UserDtos.ApplyRoleRequest request) {
        String roleCode = request.roleCode().trim().toUpperCase(Locale.ROOT);
        if (!APPLICABLE_ROLES.contains(roleCode)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID.getCode(), "只能申请志愿者或认养人身份");
        }
        SysUser user = requireUser(userId);
        Set<String> roles = authService.rolesOf(user);
        if (roles.contains(roleCode)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "你已拥有该身份",
                    List.of(new com.just.critter.support.exception.FieldErrorItem("roleCode", "已拥有该身份")));
        }
        long pending = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleCode, roleCode)
                .eq(SysUserRole::getStatus, "PENDING"));
        if (pending > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "该身份申请正在审核中",
                    List.of(new com.just.critter.support.exception.FieldErrorItem("roleCode", "已有待审申请")));
        }

        SysUserRole row = new SysUserRole();
        row.setUserId(userId);
        row.setRoleCode(roleCode);
        row.setStatus("PENDING");
        row.setApplyReason(request.reason().trim());
        row.setAvailableSlots(request.availableSlots());
        userRoleMapper.insert(row);

        auditLog.success("ROLE_APPLY", "sys_user_role", String.valueOf(row.getId()),
                null, Map.of("roleCode", roleCode, "reason", request.reason().trim()));
        return new UserDtos.ApplyRoleResponse(row.getId(), roleCode, "PENDING");
    }

    public List<UserDtos.ApplicationView> myApplications(long userId) {
        List<SysUserRole> rows = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, userId)
                .ne(SysUserRole::getRoleCode, "STUDENT")
                .orderByDesc(SysUserRole::getId));
        return rows.stream().map(this::toApplication).toList();
    }

    public List<UserDtos.ApplicationView> pendingApplications() {
        List<SysUserRole> rows = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getStatus, "PENDING")
                .orderByAsc(SysUserRole::getId));
        return rows.stream().map(this::toApplication).toList();
    }

    @Transactional
    public UserDtos.ApplicationView reviewApplication(long applicationId, boolean approve, String reason) {
        SysUserRole row = userRoleMapper.selectById(applicationId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!"PENDING".equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.AUDIT_CONFLICT);
        }
        CurrentUser reviewer = AuthContext.require();
        SysUserRole patch = new SysUserRole();
        patch.setId(applicationId);
        patch.setStatus(approve ? "GRANTED" : "REJECTED");
        if (reason != null && !reason.isBlank()) {
            patch.setApplyReason(row.getApplyReason() + " ｜ 审批意见：" + reason.trim());
        }
        if (approve) {
            patch.setGrantedAt(LocalDateTime.now());
            patch.setGrantedBy(reviewer.id());
        }
        userRoleMapper.updateById(patch);
        auditLog.success(approve ? "ROLE_APPROVE" : "ROLE_REJECT", "sys_user_role",
                String.valueOf(applicationId), Map.of("status", "PENDING"),
                Map.of("status", patch.getStatus()));
        return toApplication(userRoleMapper.selectById(applicationId));
    }

    // ---------------- 登录日志与用户列表 ----------------

    public List<UserDtos.LoginLogView> myLoginLogs(long userId, int limit) {
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        List<SysLog> rows = logMapper.selectList(Wrappers.<SysLog>lambdaQuery()
                .eq(SysLog::getActorId, userId)
                .in(SysLog::getAction, List.of("LOGIN", "LOGOUT", "PASSWORD_CHANGE"))
                .ge(SysLog::getCreatedAt, from)
                .orderByDesc(SysLog::getId)
                .last("limit " + Math.min(Math.max(limit, 1), 100)));
        return rows.stream()
                .map(item -> new UserDtos.LoginLogView(item.getId(), item.getAction(), item.getResult(),
                        item.getIp(), item.getRequestId(), item.getCreatedAt()))
                .toList();
    }

    public UserDtos.UserPage listUsers(String status, String roleCode, String dept, String keyword, PageQuery query) {
        PageQuery safe = query.normalized();
        List<Long> roleUserIds = null;
        if (roleCode != null && !roleCode.isBlank()) {
            String role = roleCode.trim().toUpperCase(Locale.ROOT);
            // SYS_ADMIN 与 SYSTEM_ADMIN 是同一个角色的两种写法，筛选时都要命中
            List<String> roleCodes = MenuMatrix.isSystemAdmin(Set.of(role))
                    ? List.of("SYS_ADMIN", "SYSTEM_ADMIN")
                    : List.of(role);
            // 主角色在 sys_user.primary_role，叠加身份在 sys_user_role，两处都要查，
            // 否则"按系统管理员筛选"会漏掉主角色就是管理员的账号
            List<Long> ids = new ArrayList<>(userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                            .in(SysUserRole::getRoleCode, roleCodes)
                            .eq(SysUserRole::getStatus, "GRANTED"))
                    .stream().map(SysUserRole::getUserId).toList());
            ids.addAll(userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                            .in(SysUser::getPrimaryRole, roleCodes))
                    .stream().map(SysUser::getId).toList());
            roleUserIds = ids.stream().distinct().toList();
            if (roleUserIds.isEmpty()) {
                return new UserDtos.UserPage(List.of(),
                        new UserDtos.PageMeta(safe.getPage(), safe.getSize(), 0, 0));
            }
        }

        Page<SysUser> page = new Page<>(safe.getPage(), safe.getSize());
        IPage<SysUser> result = userMapper.selectPage(page, Wrappers.<SysUser>lambdaQuery()
                .eq(status != null && !status.isBlank(), SysUser::getStatus, status)
                .eq(dept != null && !dept.isBlank(), SysUser::getDept, dept)
                .in(roleUserIds != null, SysUser::getId, roleUserIds)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUser::getUserNo, keyword).or().like(SysUser::getNickname, keyword))
                .orderByDesc(SysUser::getId));

        List<UserDtos.UserListItem> items = result.getRecords().stream()
                .map(user -> {
                    Set<String> roles = authService.rolesOf(user);
                    return new UserDtos.UserListItem(user.getId(), user.getUserNo(), user.getNickname(),
                            user.getDept(), user.getEmail(), AesCipher.mask(aesCipher.decrypt(user.getPhone())),
                            user.getPrimaryRole(), new ArrayList<>(roles), user.getStatus(), user.getCreatedAt());
                })
                .toList();

        return new UserDtos.UserPage(items, new UserDtos.PageMeta(safe.getPage(), safe.getSize(),
                result.getTotal(), result.getPages()));
    }

    @Transactional
    public void updateStatus(long userId, UserDtos.StatusUpdateRequest request) {
        String target = request.status() == null ? "" : request.status().trim().toUpperCase(Locale.ROOT);
        if (!USER_STATUSES.contains(target)) {
            throw new BusinessException(ErrorCode.ENUM_INVALID);
        }
        if ("DISABLED".equals(target) && (request.reason() == null || request.reason().trim().length() < 5)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "禁用账号必须填写不少于 5 字的原因",
                    List.of(new com.just.critter.support.exception.FieldErrorItem("reason", "禁用原因不少于 5 字")));
        }
        SysUser user = requireUser(userId);
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, userId)
                .set(SysUser::getStatus, target)
                .set(SysUser::getLockedUntil, null));
        // 禁用当天就把已签发令牌全部作废，否则"禁用"只是登不上，已登录的还能继续用
        store.set(AuthService.MIN_IAT_PREFIX + userId,
                String.valueOf(Instant.now().getEpochSecond()), Duration.ofDays(7));
        auditLog.success("USER_STATUS_UPDATE", "sys_user", user.getUserNo(),
                Map.of("status", user.getStatus()), Map.of("status", target,
                        "reason", request.reason() == null ? "" : request.reason()));
    }

    // ---------------- 内部 ----------------

    private UserDtos.ApplicationView toApplication(SysUserRole row) {
        return new UserDtos.ApplicationView(row.getId(), row.getRoleCode(),
                MenuMatrix.roleLabel(row.getRoleCode()), row.getStatus(), row.getApplyReason(),
                row.getCreatedAt(), row.getGrantedBy());
    }

    private SysUser requireUser(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        return user;
    }
}
