package com.xmu.rbac.structured.func;

import com.xmu.rbac.structured.data.LoginOutcome;
import com.xmu.rbac.structured.data.UserRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.xmu.rbac.structured.func.Fixtures.NOW;
import static com.xmu.rbac.structured.func.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationFunctionsTest {

    private Fixtures.InMemoryUserDao users;
    private final List<String> comparedHashes = new ArrayList<>();
    /** 口令 = "pw-" + 用户名 */
    private final BiPredicate<String, String> matches = (raw, hash) -> {
        comparedHashes.add(hash);
        return raw.startsWith("pw-") && hash.equals("hash-" + raw.substring(3));
    };

    @BeforeEach
    void setUp() {
        users = new Fixtures.InMemoryUserDao()
                .add(user(1, "alice", UserRecord.STATUS_ACTIVE))
                .add(user(2, "bob", UserRecord.STATUS_DISABLED))
                .add(user(3, "carol", UserRecord.STATUS_DELETED));
    }

    private LoginOutcome login(String username, String password) {
        return AuthenticationFunctions.login(username, password, NOW, users, matches);
    }

    @Test
    void succeedsWithCorrectPassword() {
        LoginOutcome o = login("alice", "pw-alice");
        assertTrue(o.success());
        assertEquals(1L, o.user().id());
    }

    @Test
    void unknownUserGetsSameErrorAndStillHashes() {
        assertEquals(LoginOutcome.CREDENTIAL_INVALID, login("ghost", "pw-ghost").errorCode());
        assertEquals(1, comparedHashes.size());   // 做了一次假比对，防时序侧信道
        assertEquals(LoginOutcome.CREDENTIAL_INVALID, login(null, null).errorCode());
    }

    @Test
    void wrongPasswordCountsFailuresAndLocksAtFive() {
        for (int i = 1; i < AuthenticationFunctions.MAX_LOGIN_FAILURES; i++) {
            assertEquals(LoginOutcome.CREDENTIAL_INVALID, login("alice", "pw-wrong").errorCode());
            assertEquals(i, users.users.get(1L).loginFailCount());
        }
        assertEquals(LoginOutcome.CREDENTIAL_INVALID, login("alice", "pw-wrong").errorCode());
        assertEquals(NOW.plusMinutes(AuthenticationFunctions.LOCK_MINUTES), users.users.get(1L).lockedUntil());

        // 锁定期内即使口令正确也拒绝
        assertEquals(LoginOutcome.USER_LOCKED, login("alice", "pw-alice").errorCode());
    }

    @Test
    void successAfterLockExpiresResetsCounter() {
        users.updateLoginFailure(1L, 3, NOW.minusMinutes(1));
        assertTrue(login("alice", "pw-alice").success());
        assertEquals(0, users.users.get(1L).loginFailCount());
        assertNull(users.users.get(1L).lockedUntil());
    }

    @Test
    void nullPasswordIsInvalid() {
        assertEquals(LoginOutcome.CREDENTIAL_INVALID, login("alice", null).errorCode());
    }

    @Test
    void disabledUserIsToldDisabledOnlyAfterCorrectPassword() {
        assertEquals(LoginOutcome.USER_DISABLED, login("bob", "pw-bob").errorCode());
        assertEquals(LoginOutcome.CREDENTIAL_INVALID, login("bob", "pw-wrong").errorCode());
    }

    @Test
    void deletedUserLooksLikeUnknownUser() {
        assertEquals(LoginOutcome.CREDENTIAL_INVALID, login("carol", "pw-carol").errorCode());
    }

    @Test
    void subjectDenyReasonCoversAllStates() {
        assertNull(AuthenticationFunctions.subjectDenyReason(users.users.get(1L), NOW));
        assertTrue(AuthenticationFunctions.isEnabled(users.users.get(1L), NOW));
        assertNotNull(AuthenticationFunctions.subjectDenyReason(null, NOW));
        UserRecord futureExpiry = new UserRecord(9L, "t", "h", "t", null, null, 1, "TEMPORARY",
                NOW.plusDays(1), 0, null, false, null);
        assertNull(AuthenticationFunctions.subjectDenyReason(futureExpiry, NOW));
    }

    @Test
    void changePasswordChecksOldPasswordAndPolicy() {
        UnaryOperator<String> encode = raw -> "hash-" + raw.substring(3);
        assertEquals(LoginOutcome.CREDENTIAL_INVALID,
                AuthenticationFunctions.changePassword(1L, "pw-wrong", "pw-Aa1!aaaa", users, matches, encode));
        assertEquals(LoginOutcome.CREDENTIAL_INVALID,
                AuthenticationFunctions.changePassword(404L, "pw-alice", "pw-Aa1!aaaa", users, matches, encode));
        assertEquals(LoginOutcome.CREDENTIAL_INVALID,
                AuthenticationFunctions.changePassword(1L, null, "pw-Aa1!aaaa", users, matches, encode));
        assertEquals(LoginOutcome.PASSWORD_POLICY_VIOLATION,
                AuthenticationFunctions.changePassword(1L, "pw-alice", "short", users, matches, encode));
        assertEquals(LoginOutcome.PASSWORD_POLICY_VIOLATION,
                AuthenticationFunctions.changePassword(1L, "pw-alice", "pw-alice", users, matches, encode));

        assertEquals(LoginOutcome.OK,
                AuthenticationFunctions.changePassword(1L, "pw-alice", "pw-Aa1!aaaa", users, matches, encode));
        assertEquals("hash-Aa1!aaaa", users.users.get(1L).passwordHash());
        assertFalse(users.users.get(1L).mustChangePwd());
    }

    @Test
    void defaultPolicyNeedsAllFourCharacterClasses() {
        assertTrue(AuthenticationFunctions.meetsDefaultPolicy("Admin@123"));
        assertFalse(AuthenticationFunctions.meetsDefaultPolicy(null));
        assertFalse(AuthenticationFunctions.meetsDefaultPolicy("admin@123"));
        assertFalse(AuthenticationFunctions.meetsDefaultPolicy("ADMIN@123"));
        assertFalse(AuthenticationFunctions.meetsDefaultPolicy("Admin@abc"));
        assertFalse(AuthenticationFunctions.meetsDefaultPolicy("Admin1234"));
        assertFalse(AuthenticationFunctions.meetsDefaultPolicy("Ad@1"));
    }

    /**
     * 迁移脚本里 admin / root 的初始口令哈希必须真的对应文档写的 Admin@123。
     * 这条测试第一次运行就失败了：原先的哈希抄自网上示例，并不对应 Admin@123（design-log #10）。
     * 直接读 classpath 上的 V1 脚本（来自 rbac-contract 的 jar），脚本里的哈希一改，测试立刻失败。
     */
    @Test
    void seedPasswordHashMatchesDocumentedInitialPassword() throws Exception {
        String v1;
        try (InputStream in = new ClassPathResource("db/migration/V1__rbac0_baseline.sql").getInputStream()) {
            v1 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        for (String account : List.of("admin", "root")) {
            Matcher m = Pattern.compile("\\('" + account + "',\\s*'(\\$2a\\$10\\$[^']+)'").matcher(v1);
            assertTrue(m.find(), "V1 中找不到 " + account + " 的种子行");
            assertTrue(bcrypt.matches("Admin@123", m.group(1)), account + " 的哈希不对应 Admin@123");
        }
        Matcher svc = Pattern.compile("\\('svc_hr_sync',\\s*'(\\$2a\\$10\\$[^']+)'").matcher(v1);
        assertTrue(svc.find());
        assertFalse(bcrypt.matches("Admin@123", svc.group(1)), "集成账号不得与管理员共用口令");
    }
}
