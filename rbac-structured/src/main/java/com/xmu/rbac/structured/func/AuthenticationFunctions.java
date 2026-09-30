package com.xmu.rbac.structured.func;

import com.xmu.rbac.structured.dao.UserDao;
import com.xmu.rbac.structured.data.AuthzResult;
import com.xmu.rbac.structured.data.LoginOutcome;
import com.xmu.rbac.structured.data.UserRecord;

import java.time.LocalDateTime;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

/**
 * 1.0 身份认证：登录校验与账号状态判断。
 * UserRecord 没有 isEnabled() 之类的方法——判断状态是功能层的事（06 §4.1）。
 */
public final class AuthenticationFunctions {

    public static final int MAX_LOGIN_FAILURES = 5;
    public static final int LOCK_MINUTES = 15;

    /** 用户不存在时也做一次哈希比对，使失败响应时间与成功相近，防时序侧信道（SRS UC 登录·其他）。 */
    private static final String DUMMY_HASH = "$2a$10$QML7HXgspyFoS7XwRrS1meN2EnGnUOGtXCLQteHHodYiCZQIFb/Jm";

    private AuthenticationFunctions() {}

    public static boolean isEnabled(UserRecord u, LocalDateTime now) {
        return subjectDenyReason(u, now) == null;
    }

    /**
     * 判定 S1：主体有效性。返回拒绝原因，有效时返回 null。
     * 三态（启用 / 停用 / 删除）之外，临时人员的账号失效时间也在这里判断。
     */
    public static String subjectDenyReason(UserRecord u, LocalDateTime now) {
        if (u == null) {
            return AuthzResult.SUBJECT_INVALID;
        }
        if (u.status() == UserRecord.STATUS_DELETED || u.deletedAt() != null) {
            return AuthzResult.SUBJECT_DELETED;
        }
        if (u.status() != UserRecord.STATUS_ACTIVE) {
            return AuthzResult.SUBJECT_DISABLED;
        }
        if (u.expiresAt() != null && !now.isBefore(u.expiresAt())) {
            return AuthzResult.SUBJECT_EXPIRED;
        }
        return null;
    }

    /**
     * 登录校验。口令比对函数作为参数传入（生产用 BCrypt），本函数不依赖任何框架。
     * 失败一律返回 20002，不区分「用户不存在」与「口令错误」，防用户名枚举。
     */
    public static LoginOutcome login(String username,
                                     String password,
                                     LocalDateTime now,
                                     UserDao userDao,
                                     BiPredicate<String, String> passwordMatches) {
        UserRecord user = (username == null) ? null : userDao.findByUsername(username);
        if (user == null) {
            passwordMatches.test(password == null ? "" : password, DUMMY_HASH);
            return LoginOutcome.fail(LoginOutcome.CREDENTIAL_INVALID);
        }
        if (user.lockedUntil() != null && now.isBefore(user.lockedUntil())) {
            return LoginOutcome.fail(LoginOutcome.USER_LOCKED);
        }
        if (password == null || !passwordMatches.test(password, user.passwordHash())) {
            int fails = user.loginFailCount() + 1;
            LocalDateTime lockUntil = fails >= MAX_LOGIN_FAILURES ? now.plusMinutes(LOCK_MINUTES) : null;
            userDao.updateLoginFailure(user.id(), lockUntil == null ? fails : 0, lockUntil);
            return LoginOutcome.fail(LoginOutcome.CREDENTIAL_INVALID);
        }
        String deny = subjectDenyReason(user, now);
        if (AuthzResult.SUBJECT_DELETED.equals(deny)) {
            return LoginOutcome.fail(LoginOutcome.CREDENTIAL_INVALID);   // 已删除等同不存在
        }
        if (deny != null) {
            return LoginOutcome.fail(LoginOutcome.USER_DISABLED);
        }
        if (user.loginFailCount() > 0 || user.lockedUntil() != null) {
            userDao.resetLoginFailure(user.id());
        }
        return LoginOutcome.ok(user);
    }

    /**
     * 修改本人口令。返回 0 成功，否则为错误码。
     * 口令策略取契约 PasswordPolicy 的默认值：至少 8 位，含大写、小写、数字、特殊字符，且不得与旧口令相同。
     * 历史口令与过期天数随「密码策略管理」接口一起实现。
     */
    public static int changePassword(Long userId,
                                     String oldPassword,
                                     String newPassword,
                                     UserDao userDao,
                                     BiPredicate<String, String> passwordMatches,
                                     UnaryOperator<String> passwordEncoder) {
        UserRecord user = userDao.findById(userId);
        if (user == null || oldPassword == null || !passwordMatches.test(oldPassword, user.passwordHash())) {
            return LoginOutcome.CREDENTIAL_INVALID;
        }
        if (!meetsDefaultPolicy(newPassword) || newPassword.equals(oldPassword)) {
            return LoginOutcome.PASSWORD_POLICY_VIOLATION;
        }
        userDao.updatePassword(userId, passwordEncoder.apply(newPassword));
        return LoginOutcome.OK;
    }

    static boolean meetsDefaultPolicy(String pw) {
        return pw != null && pw.length() >= 8
                && pw.chars().anyMatch(Character::isUpperCase)
                && pw.chars().anyMatch(Character::isLowerCase)
                && pw.chars().anyMatch(Character::isDigit)
                && pw.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
    }
}
