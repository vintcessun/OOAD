package com.xmu.rbac.structured.web;

import com.xmu.rbac.structured.dao.AssignmentDao;
import com.xmu.rbac.structured.dao.UserDao;
import com.xmu.rbac.structured.data.LoginOutcome;
import com.xmu.rbac.structured.data.RoleRecord;
import com.xmu.rbac.structured.data.UserRecord;
import com.xmu.rbac.structured.func.AuthenticationFunctions;
import com.xmu.rbac.structured.func.AuthorizationFunctions;
import com.xmu.rbac.structured.func.CacheHandle;
import com.xmu.rbac.structured.func.TokenFunctions;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** 接入层：只做参数转换与结果包装，不含业务逻辑。 */
@RestController
public class AuthController {

    private static final Map<Integer, String> MESSAGES = Map.of(
            LoginOutcome.CREDENTIAL_INVALID, "用户名或口令错误",
            LoginOutcome.USER_DISABLED, "账号已停用",
            LoginOutcome.USER_LOCKED, "连续登录失败次数过多，账号已锁定 15 分钟",
            LoginOutcome.PASSWORD_POLICY_VIOLATION, "新口令须至少 8 位，含大写、小写、数字与特殊字符，且不同于旧口令");

    public record LoginRequest(String username, String password) {}

    public record ChangePasswordRequest(String oldPassword, String newPassword) {}

    public record UserView(Long id, String username, String realName, Long departmentId,
                           int status, String userType, boolean mustChangePwd) {
        static UserView of(UserRecord u) {
            return new UserView(u.id(), u.username(), u.realName(), u.departmentId(),
                    u.status(), u.userType(), u.mustChangePwd());
        }
    }

    public record LoginResult(String token, Long expiresIn, UserView user,
                              List<RoleRecord> roles, List<String> permissions) {}

    private final UserDao userDao;
    private final AssignmentDao assignmentDao;
    private final CacheHandle cache;
    private final PasswordEncoder passwordEncoder;
    private final SecretKey jwtKey;
    private final Clock clock;

    public AuthController(UserDao userDao, AssignmentDao assignmentDao, CacheHandle cache,
                          PasswordEncoder passwordEncoder, SecretKey jwtKey, Clock clock) {
        this.userDao = userDao;
        this.assignmentDao = assignmentDao;
        this.cache = cache;
        this.passwordEncoder = passwordEncoder;
        this.jwtKey = jwtKey;
        this.clock = clock;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<LoginResult>> login(@RequestBody LoginRequest req) {
        LoginOutcome outcome = AuthenticationFunctions.login(req.username(), req.password(),
                LocalDateTime.now(clock), userDao, passwordEncoder::matches);
        if (!outcome.success()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.fail(outcome.errorCode(), MESSAGES.get(outcome.errorCode())));
        }
        UserRecord u = outcome.user();
        String token = TokenFunctions.issue(jwtKey, u.id(), u.username(), clock.instant(),
                TokenFunctions.DEFAULT_TTL_SECONDS);
        return ResponseEntity.ok(ApiResponse.ok(result(u, token)));
    }

    /** 令牌无状态，登出由客户端丢弃令牌；服务端吊销列表随 Redis 接入（迭代三 SESS）。 */
    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(null);
    }

    @GetMapping("/auth/me")
    public ApiResponse<LoginResult> me(@RequestAttribute(PermissionInterceptor.ATTR_USER_ID) Long userId) {
        return ApiResponse.ok(result(userDao.findById(userId), null));
    }

    @PostMapping("/auth/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestAttribute(PermissionInterceptor.ATTR_USER_ID) Long userId,
            @RequestBody ChangePasswordRequest req) {
        int code = AuthenticationFunctions.changePassword(userId, req.oldPassword(), req.newPassword(),
                userDao, passwordEncoder::matches, passwordEncoder::encode);
        if (code != LoginOutcome.OK) {
            String message = code == LoginOutcome.CREDENTIAL_INVALID ? "旧口令错误" : MESSAGES.get(code);
            return ResponseEntity.badRequest().body(ApiResponse.fail(code, message));
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private LoginResult result(UserRecord u, String token) {
        List<String> permissions = List.copyOf(new TreeSet<>(
                AuthorizationFunctions.effectivePermissions(u.id(), assignmentDao, cache)));
        return new LoginResult(token, token == null ? null : TokenFunctions.DEFAULT_TTL_SECONDS,
                UserView.of(u), assignmentDao.findRolesByUser(u.id()), permissions);
    }
}
