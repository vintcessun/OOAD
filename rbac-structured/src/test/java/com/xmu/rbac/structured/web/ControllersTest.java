package com.xmu.rbac.structured.web;

import com.xmu.rbac.structured.data.AuthzResult;
import com.xmu.rbac.structured.data.UserRecord;
import com.xmu.rbac.structured.func.Fixtures;
import com.xmu.rbac.structured.func.TokenFunctions;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

import static com.xmu.rbac.structured.func.Fixtures.NOW;
import static com.xmu.rbac.structured.func.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 接入层只做转换：直接 new 出来调用即可验证，不需要 Spring 上下文。 */
class ControllersTest {

    private final SecretKey key = Keys.hmacShaKeyFor("unit-test-secret-unit-test-secret-32b".getBytes(StandardCharsets.UTF_8));
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    /** 口令 = "pw-" + 用户名，与 Fixtures.user 的哈希约定一致。 */
    private final PasswordEncoder encoder = new PasswordEncoder() {
        @Override
        public String encode(CharSequence raw) {
            return "hash-" + raw.toString().substring(3);
        }

        @Override
        public boolean matches(CharSequence raw, String hash) {
            return encode(raw).equals(hash);
        }
    };

    private AuthController auth;
    private AuthorizationController authz;

    @BeforeEach
    void setUp() {
        Fixtures.InMemoryUserDao users = new Fixtures.InMemoryUserDao()
                .add(user(1, "alice", UserRecord.STATUS_ACTIVE));
        Fixtures.InMemoryAssignmentDao assignments = new Fixtures.InMemoryAssignmentDao()
                .role(10, "EMPLOYEE", "oa:doc:draft:view")
                .assign(1, 10);
        Fixtures.MapCacheHandle cache = new Fixtures.MapCacheHandle();
        auth = new AuthController(users, assignments, cache, encoder, key, clock);
        authz = new AuthorizationController(users, assignments, cache, clock);
    }

    @Test
    void loginReturnsTokenRolesAndPermissions() {
        ResponseEntity<ApiResponse<AuthController.LoginResult>> resp =
                auth.login(new AuthController.LoginRequest("alice", "pw-alice"));
        assertEquals(200, resp.getStatusCode().value());
        AuthController.LoginResult r = resp.getBody().data();
        assertEquals(1L, TokenFunctions.parseUserId(key, r.token(), clock.instant()));
        assertEquals(7200L, r.expiresIn());
        assertEquals(List.of("oa:doc:draft:view"), r.permissions());
        assertEquals("EMPLOYEE", r.roles().get(0).roleCode());
    }

    @Test
    void loginFailureIs401WithErrorCode() {
        ResponseEntity<ApiResponse<AuthController.LoginResult>> resp =
                auth.login(new AuthController.LoginRequest("alice", "pw-wrong"));
        assertEquals(401, resp.getStatusCode().value());
        assertEquals(20002, resp.getBody().code());
    }

    @Test
    void meAndLogout() {
        AuthController.LoginResult me = auth.me(1L).data();
        assertNull(me.token());
        assertEquals("alice", me.user().username());
        assertEquals(0, auth.logout().code());
    }

    @Test
    void changePassword() {
        assertEquals(400, auth.changePassword(1L,
                new AuthController.ChangePasswordRequest("pw-wrong", "pw-Aa1!aaaa")).getStatusCode().value());
        assertEquals(28009, auth.changePassword(1L,
                new AuthController.ChangePasswordRequest("pw-alice", "weak")).getBody().code());
        assertEquals(0, auth.changePassword(1L,
                new AuthController.ChangePasswordRequest("pw-alice", "pw-Aa1!aaaa")).getBody().code());
        assertEquals(200, auth.login(new AuthController.LoginRequest("alice", "pw-Aa1!aaaa"))
                .getStatusCode().value());
    }

    @Test
    void checkReturnsDecisionWithCodeZeroEvenWhenDenied() {
        ApiResponse<AuthzResult> allowed = authz.check(
                new AuthorizationController.AuthzRequest("alice", "oa:doc:draft", "view", null)).getBody();
        assertEquals(0, allowed.code());
        assertTrue(allowed.data().allowed());

        ApiResponse<AuthzResult> denied = authz.check(
                new AuthorizationController.AuthzRequest("alice", "oa:doc:draft", "approve", null)).getBody();
        assertEquals(0, denied.code());
        assertFalse(denied.data().allowed());
    }

    @Test
    void checkRejectsMissingFields() {
        ResponseEntity<ApiResponse<AuthzResult>> resp = authz.check(
                new AuthorizationController.AuthzRequest("alice", null, "view", null));
        assertEquals(400, resp.getStatusCode().value());
        assertEquals(10001, resp.getBody().code());
    }
}
