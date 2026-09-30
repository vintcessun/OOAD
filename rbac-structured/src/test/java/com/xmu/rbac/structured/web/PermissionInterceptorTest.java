package com.xmu.rbac.structured.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xmu.rbac.structured.data.UserRecord;
import com.xmu.rbac.structured.func.Fixtures;
import com.xmu.rbac.structured.func.TokenFunctions;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static com.xmu.rbac.structured.func.Fixtures.NOW;
import static com.xmu.rbac.structured.func.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionInterceptorTest {

    private final SecretKey key = Keys.hmacShaKeyFor("unit-test-secret-unit-test-secret-32b".getBytes(StandardCharsets.UTF_8));
    private final Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    private PermissionInterceptor interceptor;
    private Fixtures.InMemoryAssignmentDao assignments;

    @BeforeEach
    void setUp() {
        ContractRoutes routes = ContractRoutes.fromDocument(Map.of("paths", Map.of(
                "/auth/login", Map.of("post", Map.of("security", List.of())),
                "/auth/me", Map.of("get", Map.of("x-required-permission", "@self")),
                "/users", Map.of("get", Map.of("x-required-permission", "system:user:list")),
                "/legacy", Map.of("get", Map.of()))));
        Fixtures.InMemoryUserDao users = new Fixtures.InMemoryUserDao()
                .add(user(1, "admin", UserRecord.STATUS_ACTIVE))
                .add(user(2, "emp", UserRecord.STATUS_ACTIVE))
                .add(user(3, "off", UserRecord.STATUS_DISABLED));
        assignments = new Fixtures.InMemoryAssignmentDao()
                .role(10, "SYS_ADMIN", "system:user:list")
                .assign(1, 10);
        interceptor = new PermissionInterceptor(routes, key, users, assignments, new Fixtures.MapCacheHandle(),
                new ObjectMapper().registerModule(new JavaTimeModule()), clock);
    }

    private MockHttpServletRequest request(String method, String pattern, Long userId) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, pattern);
        req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
        if (userId != null) {
            req.addHeader("Authorization", "Bearer " + TokenFunctions.issue(key, userId, "u", clock.instant(), 60));
        }
        return req;
    }

    private MockHttpServletResponse pass(MockHttpServletRequest req, boolean expected) throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        assertEquals(expected, interceptor.preHandle(req, resp, new Object()));
        return resp;
    }

    @Test
    void routeMissingFromContractIsDenied() throws Exception {
        assertEquals(403, pass(request("GET", "/secret", 1L), false).getStatus());
        MockHttpServletRequest noPattern = new MockHttpServletRequest("GET", "/x");
        assertEquals(403, pass(noPattern, false).getStatus());
    }

    @Test
    void routeWithoutDeclarationIsDenied() throws Exception {
        assertEquals(403, pass(request("GET", "/legacy", 1L), false).getStatus());
    }

    @Test
    void loginIsAnonymous() throws Exception {
        pass(request("POST", "/auth/login", null), true);
    }

    @Test
    void missingOrBadTokenIs401() throws Exception {
        assertEquals(401, pass(request("GET", "/users", null), false).getStatus());
        MockHttpServletRequest bad = request("GET", "/users", null);
        bad.addHeader("Authorization", "Basic abc");
        assertEquals(401, pass(bad, false).getStatus());
    }

    @Test
    void selfEndpointNeedsOnlyActiveAccount() throws Exception {
        MockHttpServletRequest req = request("GET", "/auth/me", 2L);
        pass(req, true);
        assertEquals(2L, req.getAttribute(PermissionInterceptor.ATTR_USER_ID));
        assertEquals(401, pass(request("GET", "/auth/me", 3L), false).getStatus());
    }

    @Test
    void permissionEndpointGoesThroughKernel() throws Exception {
        pass(request("GET", "/users", 1L), true);
        MockHttpServletResponse denied = pass(request("GET", "/users", 2L), false);
        assertEquals(403, denied.getStatus());
        assertTrue(denied.getContentAsString(StandardCharsets.UTF_8).contains("MISSING_PERMISSION"));
    }
}
