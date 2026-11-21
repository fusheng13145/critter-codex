package com.just.critter.service.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.just.critter.config.CritterProperties;
import com.just.critter.controller.dto.AuthDtos;
import com.just.critter.domain.model.BaseRoster;
import com.just.critter.domain.model.SysUser;
import com.just.critter.domain.model.SysUserRole;
import com.just.critter.mapper.RosterMapper;
import com.just.critter.mapper.SysUserMapper;
import com.just.critter.mapper.SysUserRoleMapper;
import com.just.critter.service.support.MenuMatrix;
import com.just.critter.service.support.RolePermissionService;
import com.just.critter.support.cache.KeyValueStore;
import com.just.critter.support.exception.BusinessException;
import com.just.critter.support.exception.ErrorCode;
import com.just.critter.support.exception.FieldErrorItem;
import com.just.critter.support.log.AuditLogService;
import com.just.critter.support.security.AesCipher;
import com.just.critter.support.security.AuthContext;
import com.just.critter.support.security.CurrentUser;
import com.just.critter.support.security.JwtService;
import com.just.critter.support.security.PasswordHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 认证用例编排：注册/验证码激活/登录/刷新/注销。
 *
 * <p>硬规则落点（成员任务 §1.3）：
 * 学号 10 位数字 + 校内邮箱域名双校验；连续 5 次密码错锁定 10 分钟（账号 + IP 双计数）；
 * BCrypt 不加明文；登录发双令牌；改密后旧令牌由 min-iat 时间戳整体失效。
 */
@Service
public class AuthService {

    private static final Pattern STUDENT_NO = Pattern.compile("^\\d{10}$");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");

    private static final String ROLE_STUDENT = "STUDENT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_LOCKED = "LOCKED";
    private static final String STATUS_DISABLED = "DISABLED";

    private static final String CODE_PREFIX = "reg:code:";
    private static final String RESET_CODE_PREFIX = "pwd:code:";
    private static final String FAIL_ACCOUNT_PREFIX = "login:fail:acct:";
    private static final String FAIL_IP_PREFIX = "login:fail:ip:";
    private static final String LOCK_ACCOUNT_PREFIX = "login:lock:acct:";
    private static final String LOCK_IP_PREFIX = "login:lock:ip:";
    private static final String BLACKLIST_PREFIX = "auth:black:";
    public static final String MIN_IAT_PREFIX = "auth:min-iat:";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final RosterMapper rosterMapper;
    private final PasswordHasher passwordHasher;
    private final AesCipher aesCipher;
    private final JwtService jwtService;
    private final KeyValueStore store;
    private final AuditLogService auditLog;
    private final CritterProperties properties;
    private final RolePermissionService rolePermissionService;
    private final SecureRandom random = new SecureRandom();

    public AuthService(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper, RosterMapper rosterMapper,
                       PasswordHasher passwordHasher, AesCipher aesCipher, JwtService jwtService,
                       KeyValueStore store, AuditLogService auditLog, CritterProperties properties,
                       RolePermissionService rolePermissionService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.rosterMapper = rosterMapper;
        this.passwordHasher = passwordHasher;
        this.aesCipher = aesCipher;
        this.jwtService = jwtService;
        this.store = store;
        this.auditLog = auditLog;
        this.properties = properties;
        this.rolePermissionService = rolePermissionService;
    }

    // ---------------- 注册 ----------------

    @Transactional
    public AuthDtos.RegisterResponse register(AuthDtos.RegisterRequest request) {
        String userNo = request.account() == null ? "" : request.account().trim();
        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        String nickname = request.nickname() == null ? "" : request.nickname().trim();

        List<FieldErrorItem> errors = new ArrayList<>();
        if (!STUDENT_NO.matcher(userNo).matches()) {
            errors.add(new FieldErrorItem("account", "学号必须为 10 位数字"));
        }
        if (!EMAIL.matcher(email).matches()) {
            errors.add(new FieldErrorItem("email", "邮箱格式不正确"));
        } else if (!domainAllowed(email)) {
            errors.add(new FieldErrorItem("email", "请使用校内邮箱（" + String.join("、", properties.getRegister().domains()) + "）"));
        }
        if (!strongEnough(request.password())) {
            errors.add(new FieldErrorItem("password", "密码至少 8 位，且同时包含字母与数字"));
        }
        if (nickname.length() < 2 || nickname.length() > 16) {
            errors.add(new FieldErrorItem("nickname", "昵称长度需在 2—16 字之间"));
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "参数校验失败", errors);
        }

        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUserNo, userNo)) > 0) {
            throw new BusinessException(ErrorCode.STUDENT_NO_REGISTERED);
        }
        if (userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getEmail, email)) > 0) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "该邮箱已注册",
                    List.of(new FieldErrorItem("email", "该邮箱已注册")));
        }

        BaseRoster roster = rosterMapper.selectOne(
                Wrappers.<BaseRoster>lambdaQuery().eq(BaseRoster::getUserNo, userNo).last("limit 1"));
        if (roster == null) {
            // 名单外学号：注册被拒，但按"可申诉"口径写入失败日志，便于管理员核对
            auditLog.fail("REGISTER", "sys_user", userNo, ErrorCode.STUDENT_NO_NOT_IN_ROSTER.getCode(),
                    new FieldErrorItem("account", "不在学籍名单"));
            throw new BusinessException(ErrorCode.STUDENT_NO_NOT_IN_ROSTER);
        }

        SysUser user = new SysUser();
        user.setUserNo(userNo);
        user.setEmail(email);
        user.setPasswordHash(passwordHasher.hash(request.password()));
        user.setNickname(nickname);
        user.setDept(request.dept() == null || request.dept().isBlank() ? roster.getDept() : request.dept().trim());
        user.setPhone(aesCipher.encrypt(request.phone()));
        user.setPrimaryRole(ROLE_STUDENT);
        user.setStatus(STATUS_PENDING);
        user.setFailCount(0);
        user.setAppliedAt(LocalDateTime.now());
        userMapper.insert(user);

        String code = String.format("%06d", random.nextInt(1_000_000));
        store.set(CODE_PREFIX + email, code, Duration.ofSeconds(properties.getRegister().getCodeTtlSeconds()));

        auditLog.successAs(user.getId(), ROLE_STUDENT, "REGISTER", "sys_user", userNo, null,
                java.util.Map.of("email", email, "dept", user.getDept() == null ? "" : user.getDept()));

        boolean expose = properties.getRegister().isExposeVerificationCode();
        return new AuthDtos.RegisterResponse(userNo, user.getStatus(), email,
                (int) properties.getRegister().getCodeTtlSeconds(), expose ? code : null);
    }

    @Transactional
    public AuthDtos.VerifyResponse verify(AuthDtos.VerifyRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String stored = store.get(CODE_PREFIX + email).orElse(null);
        if (stored == null || !stored.equals(request.code().trim())) {
            throw new BusinessException(ErrorCode.VERIFY_CODE_INVALID);
        }

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getEmail, email).last("limit 1"));
        if (user == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (STATUS_PENDING.equals(user.getStatus())) {
            user.setStatus(STATUS_ACTIVE);
            userMapper.updateById(user);
            ensureStudentRole(user);
            store.delete(CODE_PREFIX + email);
            auditLog.successAs(user.getId(), ROLE_STUDENT, "REGISTER_VERIFY", "sys_user", user.getUserNo(),
                    java.util.Map.of("status", STATUS_PENDING), java.util.Map.of("status", STATUS_ACTIVE));
        }
        return new AuthDtos.VerifyResponse(user.getUserNo(), user.getStatus());
    }

    // ---------------- 登录 ----------------

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request, String ip) {
        String account = request.account().trim();
        String accountKey = account.toLowerCase(Locale.ROOT);

        long lockedSeconds = remainingLock(accountKey, ip);
        if (lockedSeconds > 0) {
            auditLog.failAs(0L, "GUEST", "LOGIN", "sys_user", account,
                    ErrorCode.ACCOUNT_LOCKED.getCode(), java.util.Map.of("lockSeconds", lockedSeconds));
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED.getCode(),
                    "密码错误次数过多，请 " + Math.max(1, lockedSeconds / 60) + " 分钟后重试",
                    java.util.Map.of("lockSeconds", lockedSeconds));
        }

        SysUser user = findByAccount(account);
        if (user == null || !passwordHasher.matches(request.password(), user.getPasswordHash())) {
            int failures = recordFailure(accountKey, ip);
            int max = properties.getLogin().getMaxFailures();
            if (user != null) {
                SysUser patch = new SysUser();
                patch.setId(user.getId());
                patch.setFailCount(failures);
                if (failures >= max) {
                    patch.setLockedUntil(LocalDateTime.now().plusSeconds(properties.getLogin().getLockSeconds()));
                }
                userMapper.updateById(patch);
            }
            auditLog.failAs(user == null ? 0L : user.getId(), user == null ? "GUEST" : user.getPrimaryRole(),
                    "LOGIN", "sys_user", account, ErrorCode.BAD_CREDENTIALS.getCode(),
                    java.util.Map.of("failures", failures));
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS.getCode(),
                    failures >= max ? "密码错误次数已达上限，账号锁定 10 分钟"
                            : "账号或密码错误，还可尝试 " + Math.max(0, max - failures) + " 次",
                    java.util.Map.of("remainingAttempts", Math.max(0, max - failures)));
        }

        if (STATUS_DISABLED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (STATUS_PENDING.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED.getCode(), "账号尚未激活，请先完成邮箱验证码校验");
        }
        if (STATUS_LOCKED.equals(user.getStatus())
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))) {
            long seconds = user.getLockedUntil() == null ? properties.getLogin().getLockSeconds()
                    : Duration.between(LocalDateTime.now(), user.getLockedUntil()).toSeconds();
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED.getCode(),
                    "账号已锁定，请稍后重试", java.util.Map.of("lockSeconds", Math.max(1, seconds)));
        }

        // 登录成功：清计数、清锁定、写日志
        store.delete(FAIL_ACCOUNT_PREFIX + accountKey);
        store.delete(FAIL_IP_PREFIX + ip);
        boolean wasPending = STATUS_PENDING.equals(user.getStatus());
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getFailCount, 0)
                .set(SysUser::getLockedUntil, null)
                .set(wasPending, SysUser::getStatus, STATUS_ACTIVE));
        if (wasPending) {
            user.setStatus(STATUS_ACTIVE);
        }
        ensureStudentRole(user);

        auditLog.successAs(user.getId(), user.getPrimaryRole(), "LOGIN", "sys_user", user.getUserNo(), null,
                java.util.Map.of("ip", ip));

        return issue(user);
    }

    /**
     * 刷新访问令牌。刷新令牌同时轮换（旧的立即入黑名单），
     * 并把新刷新令牌交回 Controller 写回 httpOnly Cookie。
     */
    public Refreshed refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN);
        }
        JwtService.Payload payload = jwtService.parse(refreshToken);
        if (!payload.isRefresh() || store.exists(BLACKLIST_PREFIX + payload.jwtId())) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        String minIssuedAt = store.get(MIN_IAT_PREFIX + payload.userId()).orElse(null);
        if (minIssuedAt != null && payload.issuedAt().getEpochSecond() <= Long.parseLong(minIssuedAt)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }

        SysUser user = userMapper.selectById(payload.userId());
        if (user == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        if (STATUS_DISABLED.equals(user.getStatus()) || STATUS_PENDING.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        Set<String> roles = rolesOf(user);
        JwtService.Issued access = jwtService.issueAccess(user.getId(), user.getUserNo(),
                user.getPrimaryRole(), roles, user.getDept());
        JwtService.Issued refresh = jwtService.issueRefresh(user.getId(), user.getUserNo(),
                user.getPrimaryRole(), roles, user.getDept());
        blacklist(payload.jwtId(), payload.expiresAt());

        return new Refreshed(new AuthDtos.TokenResponse(access.token(), access.expiresInSeconds()), refresh.token());
    }

    /** 注销：当前访问令牌入黑名单，过期时间到了自然清掉。 */
    public void logout(String rawAuthorization) {
        CurrentUser current = AuthContext.get();
        if (current == null) {
            return;
        }
        blacklist(current.jwtId(), current.tokenExpiresAt());
        auditLog.success("LOGOUT", "sys_user", current.userNo(), null, null);
    }

    // ---------------- 找回密码 ----------------

    /**
     * 发送找回密码验证码：5 分钟有效，走 {@link KeyValueStore}。
     * 键按学号存（不是邮箱），这样换邮箱后仍能自助重置。
     */
    @Transactional
    public AuthDtos.ResetCodeResponse requestResetCode(AuthDtos.ResetCodeRequest request) {
        String account = request.account().trim();
        SysUser user = findByAccount(account);
        if (user == null) {
            auditLog.fail("PASSWORD_CODE", "sys_user", account, ErrorCode.RECORD_NOT_FOUND.getCode(), null);
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (STATUS_DISABLED.equals(user.getStatus())) {
            // 禁用账号只能走申诉，否则"重置密码"就成了绕过禁用的后门
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED.getCode(),
                    "账号已被禁用，请通过登录页的申诉入口提交申诉");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        long ttl = properties.getRegister().getCodeTtlSeconds();
        store.set(RESET_CODE_PREFIX + user.getUserNo(), code, Duration.ofSeconds(ttl));

        auditLog.successAs(user.getId(), user.getPrimaryRole(), "PASSWORD_CODE", "sys_user", user.getUserNo(),
                null, java.util.Map.of("ttlSeconds", ttl));

        boolean expose = properties.getRegister().isExposeVerificationCode();
        return new AuthDtos.ResetCodeResponse(user.getUserNo(), maskEmail(user.getEmail()), (int) ttl,
                expose ? code : null);
    }

    /**
     * 凭验证码重置密码。错误次数与登录共用同一套账号 + IP 计数，达上限同样锁定，
     * 避免"忘密码"变成绕过登录锁定的通道。成功后写 min-iat，旧令牌整体失效。
     */
    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request, String ip) {
        String account = request.account().trim();
        String accountKey = account.toLowerCase(Locale.ROOT);

        long lockedSeconds = remainingLock(accountKey, ip);
        if (lockedSeconds > 0) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED.getCode(),
                    "验证码错误次数过多，请 " + Math.max(1, lockedSeconds / 60) + " 分钟后重试",
                    java.util.Map.of("lockSeconds", lockedSeconds));
        }

        if (!strongEnough(request.newPassword())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID.getCode(), "参数校验失败",
                    java.util.List.of(new FieldErrorItem("newPassword", "密码至少 8 位，且同时包含字母与数字")));
        }

        SysUser user = findByAccount(account);
        if (user == null) {
            throw new BusinessException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (STATUS_DISABLED.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED.getCode(),
                    "账号已被禁用，请通过登录页的申诉入口提交申诉");
        }

        String stored = store.get(RESET_CODE_PREFIX + user.getUserNo()).orElse(null);
        if (stored == null || !stored.equals(request.code().trim())) {
            int failures = recordFailure(accountKey, ip);
            int max = properties.getLogin().getMaxFailures();
            SysUser patch = new SysUser();
            patch.setId(user.getId());
            patch.setFailCount(failures);
            if (failures >= max) {
                patch.setLockedUntil(LocalDateTime.now().plusSeconds(properties.getLogin().getLockSeconds()));
            }
            userMapper.updateById(patch);
            throw new BusinessException(ErrorCode.VERIFY_CODE_INVALID.getCode(),
                    failures >= max ? "验证码错误次数已达上限，账号锁定 10 分钟"
                            : "验证码错误或已过期，还可尝试 " + Math.max(0, max - failures) + " 次",
                    java.util.Map.of("remainingAttempts", Math.max(0, max - failures)));
        }

        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getPasswordHash, passwordHasher.hash(request.newPassword()))
                .set(SysUser::getFailCount, 0)
                .set(SysUser::getLockedUntil, null));
        store.delete(RESET_CODE_PREFIX + user.getUserNo());
        store.delete(FAIL_ACCOUNT_PREFIX + accountKey);
        store.delete(FAIL_IP_PREFIX + ip);
        store.set(MIN_IAT_PREFIX + user.getId(), String.valueOf(java.time.Instant.now().getEpochSecond()),
                Duration.ofSeconds(properties.getSecurity().getRefreshTokenSeconds()));

        auditLog.successAs(user.getId(), user.getPrimaryRole(), "PASSWORD_RESET", "sys_user", user.getUserNo(),
                null, java.util.Map.of("note", "找回密码后旧令牌全部失效"));
    }

    // ---------------- 公共方法 ----------------

    public Set<String> rolesOf(SysUser user) {
        Set<String> roles = new LinkedHashSet<>();
        if (user.getPrimaryRole() != null) {
            roles.add(user.getPrimaryRole());
        }
        List<SysUserRole> extra = userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, user.getId())
                .eq(SysUserRole::getStatus, "GRANTED")
                .isNotNull(SysUserRole::getGrantedAt)
                .and(w -> w.isNull(SysUserRole::getExpireAt).or().gt(SysUserRole::getExpireAt, LocalDateTime.now())));
        extra.forEach(item -> roles.add(item.getRoleCode()));
        return roles;
    }

    public AuthDtos.LoginUser toLoginUser(SysUser user, Set<String> roles) {
        List<String> menus = new ArrayList<>(menusFor(roles));
        return new AuthDtos.LoginUser(user.getId(), user.getUserNo(), user.getNickname(), user.getDept(),
                user.getPrimaryRole(), MenuMatrix.roleLabel(user.getPrimaryRole()),
                new ArrayList<>(roles), menus);
    }

    /** 菜单来自 sys_role_permission（V7），库里查不到该角色时回退常量。 */
    public Set<String> menusFor(Set<String> roles) {
        return rolePermissionService.menusFor(roles);
    }

    /** 接口权限集合，同样以库为准。 */
    public Set<String> apisFor(Set<String> roles) {
        return rolePermissionService.apisFor(roles);
    }

    public String getRoleLabel(String roleCode) {
        return MenuMatrix.roleLabel(roleCode);
    }

    // ---------------- 内部 ----------------

    private AuthDtos.LoginResponse issue(SysUser user) {
        Set<String> roles = rolesOf(user);
        JwtService.Issued access = jwtService.issueAccess(user.getId(), user.getUserNo(),
                user.getPrimaryRole(), roles, user.getDept());
        JwtService.Issued refresh = jwtService.issueRefresh(user.getId(), user.getUserNo(),
                user.getPrimaryRole(), roles, user.getDept());
        return new AuthDtos.LoginResponse(access.token(), refresh.token(), access.expiresInSeconds(),
                toLoginUser(user, roles));
    }

    private SysUser findByAccount(String account) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUserNo, account)
                .or()
                .eq(SysUser::getEmail, account.toLowerCase(Locale.ROOT))
                .last("limit 1"));
    }

    /** 主角色学生才补 sys_user_role，避免管理员被误加学生身份。 */
    private void ensureStudentRole(SysUser user) {
        if (!ROLE_STUDENT.equals(user.getPrimaryRole())) {
            return;
        }
        long exists = userRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, user.getId())
                .eq(SysUserRole::getRoleCode, ROLE_STUDENT));
        if (exists == 0) {
            SysUserRole row = new SysUserRole();
            row.setUserId(user.getId());
            row.setRoleCode(ROLE_STUDENT);
            row.setStatus("GRANTED");
            row.setGrantedAt(LocalDateTime.now());
            row.setGrantedBy(user.getId());
            userRoleMapper.insert(row);
        }
    }

    private boolean domainAllowed(String email) {
        String domain = email.substring(email.lastIndexOf('@') + 1);
        for (String allowed : properties.getRegister().domains()) {
            String normalized = allowed.toLowerCase(Locale.ROOT);
            if (domain.equals(normalized) || domain.endsWith("." + normalized)) {
                return true;
            }
        }
        return false;
    }

    private static boolean strongEnough(String password) {
        return password != null && password.length() >= 8 && password.length() <= 32
                && HAS_LETTER.matcher(password).matches() && HAS_DIGIT.matcher(password).matches();
    }

    /** 邮箱脱敏：只保留首字符与域名，用于"验证码已发送至 a***@stu.just.edu.cn"。 */
    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String name = email.substring(0, at);
        String domain = email.substring(at);
        return name.charAt(0) + "***" + domain;
    }

    private long remainingLock(String accountKey, String ip) {
        long fromAccount = remainingMillis(LOCK_ACCOUNT_PREFIX + accountKey);
        long fromIp = remainingMillis(LOCK_IP_PREFIX + ip);
        long millis = Math.max(fromAccount, fromIp);
        return millis <= 0 ? 0 : (millis + 999) / 1000;
    }

    private long remainingMillis(String lockKey) {
        String value = store.get(lockKey).orElse(null);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value) - System.currentTimeMillis();
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int recordFailure(String accountKey, String ip) {
        Duration window = Duration.ofSeconds(properties.getLogin().getLockSeconds() * 6L);
        // 两个维度都直接取 increment 的返回值：内存实现自增后 value 为空，再 get 会读到空串，
        // 曾导致 IP 维度永远不锁（账号维度靠返回值才生效）。
        long accountCount = store.increment(FAIL_ACCOUNT_PREFIX + accountKey, window);
        long ipCount = store.increment(FAIL_IP_PREFIX + ip, window);
        int max = properties.getLogin().getMaxFailures();
        if (accountCount >= max) {
            lock(LOCK_ACCOUNT_PREFIX + accountKey);
        }
        if (ipCount >= max) {
            lock(LOCK_IP_PREFIX + ip);
        }
        return (int) accountCount;
    }

    private void lock(String key) {
        long until = System.currentTimeMillis() + properties.getLogin().getLockSeconds() * 1000L;
        store.set(key, String.valueOf(until), Duration.ofSeconds(properties.getLogin().getLockSeconds()));
    }

    private void blacklist(String jwtId, java.time.Instant expiresAt) {
        if (jwtId == null || expiresAt == null) {
            return;
        }
        long seconds = expiresAt.getEpochSecond() - java.time.Instant.now().getEpochSecond();
        if (seconds > 0) {
            store.set(BLACKLIST_PREFIX + jwtId, "1", Duration.ofSeconds(seconds));
        }
    }

    /** 刷新结果：响应体只回 accessToken + expiresIn（契约），新刷新令牌走 Cookie。 */
    public record Refreshed(AuthDtos.TokenResponse body, String refreshToken) {
    }
}
