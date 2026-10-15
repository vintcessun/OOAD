package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.mapper.TokenRevocationMapper;
import com.xmu.rbac.structured.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 1.0 认证：登录、登出、令牌校验、修改口令（docs/06-detail-structured.md §5.1）。
 */
@Service
public class AuthenticationService {

    public static final int MAX_LOGIN_FAILURES = 5;
    public static final int LOCK_MINUTES = 15;

    /** 用户不存在时也比对一次哈希，使失败响应时间与口令错误相近，避免借响应时间枚举用户名。 */
    private static final String DUMMY_HASH = "$2a$10$QML7HXgspyFoS7XwRrS1meN2EnGnUOGtXCLQteHHodYiCZQIFb/Jm";

    /** 登录成功的结果。 */
    public record LoginResult(String token, long expiresIn, Long userId, String username, String realName,
                              boolean mustChangePwd) {
    }

    /** 通过认证的请求主体。 */
    public record Authenticated(Long userId, String username, String jti, Instant expiresAt, boolean mustChangePwd) {
    }

    private final UserMapper userMapper;
    private final TokenRevocationMapper tokenRevocationMapper;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public AuthenticationService(UserMapper userMapper, TokenRevocationMapper tokenRevocationMapper,
                                 TokenService tokenService, PasswordEncoder passwordEncoder,
                                 AuditService auditService, Clock clock) {
        this.userMapper = userMapper;
        this.tokenRevocationMapper = tokenRevocationMapper;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * 1.1 登录。失败一律说「用户名或口令错误」，不区分用户不存在与口令错误；
     * 只有口令正确之后才告诉对方账号已停用或已锁定以外的状态。
     */
    @Transactional(noRollbackFor = BizException.class)
    public LoginResult login(String username, String password, String clientIp) {
        LocalDateTime now = LocalDateTime.now(clock);
        Operator who = new Operator(null, username, clientIp);
        User user = (username == null || username.isBlank()) ? null : userMapper.findByUsername(username).orElse(null);
        if (user == null || User.DELETED.equals(user.status)) {
            passwordEncoder.matches(password == null ? "" : password, DUMMY_HASH);
            throw loginFailed(who, null, "CREDENTIAL_INVALID", BizException.CREDENTIAL_INVALID, "用户名或口令错误");
        }
        who = new Operator(user.id, user.username, clientIp);
        if (user.lockedUntil != null && now.isBefore(user.lockedUntil)) {
            throw loginFailed(who, user, "ACCOUNT_LOCKED", BizException.ACCOUNT_LOCKED,
                    "连续登录失败次数过多，账号已锁定 " + LOCK_MINUTES + " 分钟");
        }
        if (password == null || !passwordEncoder.matches(password, user.passwordHash)) {
            // 原子地加一：并发的失败请求不会丢失计数
            userMapper.recordLoginFailure(user.id, MAX_LOGIN_FAILURES, now.plusMinutes(LOCK_MINUTES));
            throw loginFailed(who, user, "CREDENTIAL_INVALID", BizException.CREDENTIAL_INVALID, "用户名或口令错误");
        }
        String deny = AuthorizationService.subjectDenyReason(user, now);
        if (deny != null) {
            throw loginFailed(who, user, deny, BizException.ACCOUNT_DISABLED, "账号已停用或已到期");
        }
        if (user.loginFailCount > 0 || user.lockedUntil != null) {
            userMapper.resetLoginFailure(user.id);
        }
        String token = tokenService.issue(user.id, user.username, user.tokenVersion, clock.instant());
        auditService.record(who, "LOGIN", "USER", user.id, user.username, null, AuditService.SUCCESS, null);
        return new LoginResult(token, TokenService.TTL_SECONDS, user.id, user.username, user.realName,
                user.mustChangePwd);
    }

    /**
     * 1.2 每个请求的令牌校验：签名与有效期 → 是否已登出 → 账号当前状态 → 令牌版本号与账号一致（改过口令的旧令牌作废）。
     * 账号状态每次都查库，停用、删除在下一次请求立即生效。
     */
    public Authenticated authenticate(String token) {
        Instant now = clock.instant();
        TokenService.TokenClaims claims = tokenService.parse(token, now);
        if (claims == null) {
            throw unauthenticated("未登录或令牌已失效");
        }
        if (tokenRevocationMapper.isRevoked(claims.jti())) {
            throw unauthenticated("令牌已注销，请重新登录");
        }
        User user = userMapper.findById(claims.userId()).orElse(null);
        String deny = AuthorizationService.subjectDenyReason(user, LocalDateTime.ofInstant(now, clock.getZone()));
        if (deny != null) {
            throw unauthenticated("账号不可用：" + deny);
        }
        if (claims.version() != user.tokenVersion) {
            throw unauthenticated("口令已修改，请重新登录");
        }
        return new Authenticated(user.id, user.username, claims.jti(), claims.expiresAt(), user.mustChangePwd);
    }

    /** 1.4 登出：登记令牌编号，令牌原本到期前一直有效拒绝。 */
    public void logout(Operator op, Authenticated auth) {
        tokenRevocationMapper.revoke(auth.jti(), Duration.between(clock.instant(), auth.expiresAt()));
        auditService.record(op, "LOGOUT", "USER", op.userId(), op.username(), null, AuditService.SUCCESS, null);
    }

    /** 1.5 修改本人口令。成功后此前签发的令牌全部失效。 */
    @Transactional
    public void changePassword(Operator op, String oldPassword, String newPassword) {
        User user = userMapper.findById(op.userId()).orElseThrow(() -> BizException.notFound("用户"));
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.passwordHash)) {
            auditService.record(op, "PASSWORD_CHANGE", "USER", user.id, user.username, null, AuditService.FAILED,
                    "CREDENTIAL_INVALID");
            throw BizException.badRequest("旧口令错误");
        }
        if (!meetsPolicy(newPassword) || newPassword.equals(oldPassword)) {
            auditService.record(op, "PASSWORD_CHANGE", "USER", user.id, user.username, null, AuditService.FAILED,
                    "PASSWORD_POLICY");
            throw BizException.badRequest("新口令须至少 8 位，含大写、小写、数字与特殊字符，且不同于旧口令");
        }
        user.passwordHash = passwordEncoder.encode(newPassword);
        user.mustChangePwd = false;
        user.tokenVersion++;
        userMapper.save(user);
        auditService.record(op, "PASSWORD_CHANGE", "USER", user.id, user.username, null, AuditService.SUCCESS, null);
    }

    public static boolean meetsPolicy(String pw) {
        return pw != null && pw.length() >= 8
                && pw.chars().anyMatch(Character::isUpperCase)
                && pw.chars().anyMatch(Character::isLowerCase)
                && pw.chars().anyMatch(Character::isDigit)
                && pw.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
    }

    private BizException loginFailed(Operator who, User user, String reason, int code, String message) {
        auditService.record(who, "LOGIN", "USER", user == null ? null : user.id, who.username(), null,
                AuditService.FAILED, reason);
        return new BizException(401, code, message);
    }

    private static BizException unauthenticated(String message) {
        return new BizException(401, BizException.UNAUTHENTICATED, message);
    }

}
