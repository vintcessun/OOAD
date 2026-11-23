package com.xmu.rbac.structured.integration;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;
import com.xmu.rbac.structured.RbacStructuredApplication;
import com.xmu.rbac.structured.dto.*;
import com.xmu.rbac.structured.entity.*;
import com.xmu.rbac.structured.mapper.Store;
import com.xmu.rbac.structured.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
/** 冻结合同集成回归：真实专用MySQL/Redis，不使用H2或模拟一致性依赖。 */
@SpringBootTest(classes = RbacStructuredApplication.class)
@AutoConfigureMockMvc(
    print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("test")
class Rbac1IntegrationTest {
    static final String PASSWORD = "StructuredTestOnly123!", KEY = "StructuredAuditTestOnly123!",
                        COURSE = "course-project-management";
    static final AtomicLong EPOCH = new AtomicLong();
    static final String NAMESPACE = "structured-test:" + UUID.randomUUID();
    @DynamicPropertySource
    static void isolatedCache(DynamicPropertyRegistry registry) {
        String url = System.getenv().getOrDefault("TEST_DB_URL", "jdbc:mysql://127.0.0.1:13306/rbac1_structured_test?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false");
        java.net.URI address;
        try {
            if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
            address = java.net.URI.create(url.substring(5).split("\\?", 2)[0]);
        } catch (RuntimeException error) {
            throw new IllegalStateException("测试拒绝非专用MySQL配置");
        }
        if (!Set.of("127.0.0.1", "host.docker.internal").contains(address.getHost()) || address.getPort() != 13306 || !"/rbac1_structured_test".equals(address.getPath()) || address.getUserInfo() != null)
            throw new IllegalStateException("测试只允许专用13306端口与rbac1_structured_test空开发库");
        String user = System.getenv().getOrDefault("TEST_DB_USER", "root");
        String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "root");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> user);
        registry.add("spring.flyway.password", () -> password);
        registry.add("spring.data.redis.host", address::getHost);
        registry.add("spring.data.redis.port", () -> 16379);
        registry.add("rbac.cache.namespace", () -> NAMESPACE);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired PasswordEncoder passwords;
    @Autowired StringRedisTemplate redis;
    @Autowired AuthorizationService az;
    @Value("${rbac.cache.namespace}") String namespace;
    @Autowired ManagementService management;
    @Autowired AuditService audit;
    @Autowired TokenService tokens;
    @Autowired Store db;
    @Autowired PlatformTransactionManager manager;
    String root;
    Actor actor;
    static String hash;
    @BeforeEach
    void reset() throws Exception {
        String database = jdbc.queryForObject("select database()", String.class);
        assertThat(database).isEqualTo("rbac1_structured_test");
        flyway.clean();
        flyway.migrate();
        if (hash == null)
            hash = passwords.encode(PASSWORD);
        jdbc.update("update sys_user set password_hash=?,must_change_password=0 where id=1", hash);
        jdbc.update(
            "update sys_grant_version set version=? where id=1", EPOCH.incrementAndGet() * 10000);
        Set<String> keys = redis.keys(namespace + ":*");
        if (keys != null && !keys.isEmpty())
            redis.delete(keys);
        root = login("root", "rbac-center");
        actor = tokens.verify("Bearer " + root);
    }
    record Response(int status, JsonNode body) {
        JsonNode data() {
            return body.path("data");
        }
    }
    Response call(String method, String path, String token, Object body) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder =
            switch (method) {
                case "GET" -> get(path);
                case "POST" -> post(path);
                case "PUT" -> put(path);
                case "PATCH" -> patch(path);
                case "DELETE" -> delete(path);
                default -> throw new IllegalArgumentException();
            };
        if (token != null)
            builder.header("Authorization", "Bearer " + token);
        if (body != null)
            builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        MvcResult result = mvc.perform(builder).andReturn();
        String text =
            result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return new Response(result.getResponse().getStatus(),
            text.isBlank() ? json.nullNode() : json.readTree(text));
    }
    JsonNode ok(String method, String path, Object body) throws Exception {
        Response result = call(method, "/api/v1" + path, root, body);
        assertThat(result.status)
            .withFailMessage("%s %s -> %s", method, path, result.body)
            .isEqualTo(200);
        return result.data();
    }
    String login(String username, String system) throws Exception {
        Response r = call("POST", "/auth/login", null,
            Map.of("username", username, "password", PASSWORD, "systems", List.of(system)));
        assertThat(r.status).withFailMessage("login -> %s", r.body).isEqualTo(200);
        return r.body.path("accessToken").asText();
    }
    long role(String code) throws Exception {
        return ok("POST", "/roles", Map.of("systemCode", COURSE, "code", code, "name", code))
            .path("id")
            .asLong();
    }
    long permission(String code, String kind) throws Exception {
        return ok("POST", "/permissions",
            Map.of("systemCode", COURSE, "code", code, "name", code, "kind", kind))
            .path("id")
            .asLong();
    }
    long user(String name, long department) throws Exception {
        return ok("POST", "/users",
            Map.of(
                "username", name, "name", name, "departmentId", department, "password", PASSWORD))
            .path("id")
            .asLong();
    }
    void grants(long role, long... permissions) throws Exception {
        ok("PUT", "/roles/" + role + "/permissions",
            Map.of("permissionIds", Arrays.stream(permissions).boxed().toList()));
    }
    void edge(long senior, long junior) throws Exception {
        ok("POST", "/roles/" + senior + "/inherits/" + junior, null);
    }
    JsonNode effective(long role) throws Exception {
        return ok("GET", "/roles/" + role + "/effective-permissions", null);
    }
    void allow(long department, long... roles) throws Exception {
        ok("PUT", "/departments/" + department + "/assignable-roles",
            Map.of("roleIds", Arrays.stream(roles).boxed().toList()));
    }
    void assign(long user, long role) throws Exception {
        ok("POST", "/users/" + user + "/role-assignments", Map.of("roleId", role));
    }
    long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }
    void assertPermission(JsonNode effective, String code, boolean expected) {
        assertThat(effective.path("permissions").findValuesAsText("code").contains(code))
            .isEqualTo(expected);
    }
    @Test
    void dag01DirectOnly() throws Exception {
        long a = role("A"), p = permission("p1", "API");
        grants(a, p);
        assertPermission(effective(a), "p1", true);
    }
    @Test
    void dag02SingleInheritance() throws Exception {
        long a = role("A"), b = role("B"), p = permission("p2", "API");
        grants(b, p);
        edge(a, b);
        assertPermission(effective(a), "p2", true);
    }
    @Test
    void dag03Transitive() throws Exception {
        long a = role("A"), b = role("B"), c = role("C"), p = permission("p3", "API");
        grants(c, p);
        edge(a, b);
        edge(b, c);
        assertPermission(effective(a), "p3", true);
    }
    @Test
    void dag04DiamondDeduplicatesWithBothPaths() throws Exception {
        long a = role("A"), b = role("B"), c = role("C"), d = role("D"),
             p = permission("p4", "API");
        grants(d, p);
        edge(a, b);
        edge(a, c);
        edge(b, d);
        edge(c, d);
        JsonNode e = effective(a);
        assertThat(e.path("permissions").size()).isEqualTo(1);
        assertThat(e.path("sources").size()).isEqualTo(2);
    }
    @Test
    void dag05DuplicateEdge() throws Exception {
        long a = role("A"), b = role("B");
        edge(a, b);
        assertThat(call("POST", "/api/v1/roles/" + a + "/inherits/" + b, root, null).status)
            .isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from sys_role_hierarchy where senior_role_id=?", Long.class, a)).isEqualTo(1);
    }
    @Test
    void dag06SelfCycle() throws Exception {
        long a = role("A");
        Response r = call("POST", "/api/v1/roles/" + a + "/inherits/" + a, root, null);
        assertThat(r.status).isEqualTo(409);
        assertThat(r.body.path("code").asText()).isEqualTo("ROLE_CYCLE");
        assertThat(jdbc.queryForObject("select count(*) from sys_role_hierarchy where senior_role_id=?", Long.class, a)).isZero();
    }
    @Test
    void dag07CycleRollsBack() throws Exception {
        long a = role("A"), b = role("B"), c = role("C");
        edge(a, b);
        edge(b, c);
        assertThat(call("POST", "/api/v1/roles/" + c + "/inherits/" + a, root, null).status)
            .isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from sys_role_hierarchy where senior_role_id in (?,?,?)", Long.class, a, b, c)).isEqualTo(2);
        assertThat(
            jdbc.queryForObject(
                "select count(*) from sys_audit_log where result='DENIED' and reason='ROLE_CYCLE'",
                Long.class))
            .isEqualTo(1L);
    }
    @Test
    void dag08CrossSystemRejected() throws Exception {
        long a = role("A");
        assertThat(call("POST", "/api/v1/roles/" + a + "/inherits/1", root, null).status)
            .isEqualTo(400);
    }
    @Test
    void dag09DisabledNodeCutsPathAndRestores() throws Exception {
        long a = role("A"), b = role("B"), c = role("C"), p = permission("p", "API");
        grants(c, p);
        edge(a, b);
        edge(b, c);
        ok("PUT", "/roles/" + b + "/status", Map.of("status", "DISABLED"));
        assertPermission(effective(a), "p", false);
        ok("PUT", "/roles/" + b + "/status", Map.of("status", "ENABLED"));
        assertPermission(effective(a), "p", true);
    }
    @Test
    void dag10AlternatePathSurvivesEdgeDeletion() throws Exception {
        long a = role("A"), b = role("B"), c = role("C"), p = permission("p", "API");
        grants(b, p);
        edge(a, b);
        edge(a, c);
        edge(c, b);
        ok("DELETE", "/roles/" + a + "/inherits/" + b, null);
        assertPermission(effective(a), "p", true);
        assertThat(call("DELETE", "/api/v1/roles/" + a + "/inherits/" + b, root, null).status)
            .isEqualTo(404);
    }
    @Test
    void dag11ConcurrentOpposingEdgesCannotBothCommit() throws Exception {
        long a = role("A"), b = role("B");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = List.of(pool.submit(() -> {
                start.await();
                return call("POST", "/api/v1/roles/" + a + "/inherits/" + b, root, null).status;
            }),
                pool.submit(() -> {
                    start.await();
                    return call("POST", "/api/v1/roles/" + b + "/inherits/" + a, root, null).status;
                }));
            start.countDown();
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> result : results) status.add(result.get(30, TimeUnit.SECONDS));
            assertThat(status).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("select count(*) from sys_role_hierarchy where senior_role_id in (?,?)", Long.class, a, b)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
    @Test
    void dag12MultipleDirectRolesUnion() throws Exception {
        long a = role("A"), b = role("B"), p = permission("p", "API"), q = permission("q", "UI"),
             u = user("union", 2);
        grants(a, p);
        grants(b, q);
        allow(2, a, b);
        assign(u, a);
        assign(u, b);
        assertThat(ok("GET", "/users/" + u + "/systems/" + COURSE + "/permissions", null)
                       .path("permissions")
                       .size())
            .isEqualTo(2);
    }
    @Test
    void dag13RetirementCleansReferencesAndPreservesHistory() throws Exception {
        long a = role("A"), b = role("B"), p = permission("p", "API"), u = user("retire", 2);
        grants(a, p);
        allow(2, a);
        assign(u, a);
        edge(b, a);
        long logs = count("sys_audit_log");
        ok("DELETE", "/roles/" + a, null);
        assertThat(jdbc.queryForObject(
                       "select count(*) from sys_user_role where role_id=?", Long.class, a))
            .isZero();
        assertThat(jdbc.queryForObject("select count(*) from sys_role_hierarchy where senior_role_id=? or junior_role_id=?", Long.class, a, a)).isZero();
        assertThat(count("sys_audit_log")).isGreaterThan(logs);
        assertThat(call("PUT", "/api/v1/users/1/status", root, Map.of("status", "DISABLED")).status)
            .isEqualTo(409);
    }
    @Test
    void dag14TwoInstancesAndCacheFailure() throws Exception {
        long a = role("A"), p = permission("p", "API"), u = user("cached", 2);
        grants(a, p);
        allow(2, a);
        assign(u, a);
        AuthorizationService replica = new AuthorizationService(db, redis, json, namespace);
        assertThat(az.permission(u, COURSE, "API", "p")).isTrue();
        assertThat(replica.permission(u, COURSE, "API", "p")).isTrue();
        grants(a);
        assertThat(az.permission(u, COURSE, "API", "p")).isFalse();
        assertThat(replica.permission(u, COURSE, "API", "p")).isFalse();
        org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory broken =
            new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
                "127.0.0.1", 16380);
        broken.afterPropertiesSet();
        broken.start();
        try {
            AuthorizationService fallback =
                new AuthorizationService(db, new StringRedisTemplate(broken), json, namespace);
            assertThat(fallback.permission(u, COURSE, "API", "p")).isFalse();
            grants(a, p);
            assertThat(fallback.permission(u, COURSE, "API", "p")).isTrue();
        } finally {
            broken.destroy();
        }
    }
    @Test
    void dag15DirectAndInheritedSources() throws Exception {
        long a = role("A"), b = role("B"), p = permission("p", "API");
        grants(a, p);
        grants(b, p);
        edge(a, b);
        JsonNode e = effective(a);
        assertThat(e.path("permissions").size()).isEqualTo(1);
        assertThat(e.path("sources").findValues("inherited"))
            .extracting(JsonNode::asBoolean)
            .containsExactlyInAnyOrder(false, true);
    }
    @Test
    void organizationAdmissionMovementAndUpperBound() throws Exception {
        long a = role("A"), p = permission("p", "API"), u = user("moving", 2);
        grants(a, p);
        Response denied =
            call("POST", "/api/v1/users/" + u + "/role-assignments", root, Map.of("roleId", a));
        assertThat(denied.status).isEqualTo(403);
        allow(2, a);
        allow(3, a);
        assign(u, a);
        ok("PUT", "/users/" + u + "/department", Map.of("departmentId", 2));
        assertThat(ok("GET", "/users/" + u + "/role-assignments", null).size()).isEqualTo(1);
        ok("PUT", "/users/" + u + "/department", Map.of("departmentId", 3));
        assertThat(ok("GET", "/users/" + u + "/role-assignments", null).size()).isZero();
        assertPermission(effective(a), "p", true);
        assertThat(call("DELETE", "/api/v1/departments/1", root, null).status).isEqualTo(409);
        assertThat(call("PUT", "/api/v1/departments/1/assignable-roles", root,
                       Map.of("roleIds", List.of()))
                       .status)
            .isEqualTo(409);
    }
    @Test
    void crossSystemAndManagementBoundary() throws Exception {
        long u = user("ordinary", 2);
        String token = login("ordinary", COURSE);
        assertThat(call("GET", "/api/v1/users", token, null).status).isEqualTo(403);
        assertThat(call("GET", "/auth/me", token, null).status).isEqualTo(200);
        token = login("ordinary", "rbac-center");
        assertThat(call("GET", "/api/v1/roles?systemCode=" + COURSE, token, null).status)
            .isEqualTo(403);
        assertThat(call("POST", "/api/v1/authorization/check", token,
                       Map.of("systemCode", COURSE, "userId", 1, "kind", "UI", "permission", "CV"))
                       .status)
            .isEqualTo(403);
    }
    @Test
    void unknownFieldsAndPagingAreRejected() throws Exception {
        assertThat(call("POST", "/auth/login", null,
                       Map.of("username", "root", "password", PASSWORD, "systems", List.of(COURSE),
                           "actorId", 1))
                       .status)
            .isEqualTo(400);
        assertThat(call("GET", "/api/v1/users?page=0", root, null).status).isEqualTo(400);
        assertThat(call("GET", "/api/v1/users?pageSize=101", root, null).status).isEqualTo(400);
        assertThat(call("PATCH", "/api/v1/users/1", root, Map.of("departmentId", 2)).status)
            .isEqualTo(400);
        assertThat(call("GET", "/api/v1/unregistered", root, null).status).isEqualTo(403);
    }
    @Test
    void identitySevenOperationsAndJwtValidation() throws Exception {
        long u = user("teacher1", 2);
        assign(u, 4);
        String token = login("teacher1", COURSE);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]),
            java.nio.charset.StandardCharsets.UTF_8);
        JsonNode claims = json.readTree(payload);
        assertThat(claims.has("roles")).isFalse();
        assertThat(claims.has("permissions")).isFalse();
        assertThat(claims.path("aud").size()).isEqualTo(1);
        assertThat(call("GET", "/auth/me", token, null).body.path("roles").findValuesAsText(""))
            .isEmpty();
        assertThat(call("GET", "/auth/me", token, null).body.path("roles").get(0).asText())
            .isEqualTo("teacher");
        assertThat(
            call("GET", "/auth/permissions/unknown", token, null).body.path("allowed").asBoolean())
            .isFalse();
        assertThat(call("GET", "/auth/api-permissions/unknown", token, null).status).isEqualTo(404);
        assertThat(call("GET", "/.well-known/jwks.json", null, null).body.path("keys").size())
            .isEqualTo(1);
        String bad = token.substring(0, token.length() - 3) + "xxx";
        assertThat(call("GET", "/auth/me", bad, null).status).isEqualTo(401);
        assertThat(call("GET", "/auth/me", null, null).status).isEqualTo(401);
    }
    @Test
    void logoutSurvivesRedisRestart() throws Exception {
        long u = user("logout", 2);
        String token = login("logout", COURSE);
        assertThat(call("POST", "/auth/logout", token, null).status).isEqualTo(204);
        restartDedicatedRedis();
        assertThat(call("GET", "/auth/me", token, null).status).isEqualTo(401);
        assertThat(count("sys_token_revocation")).isEqualTo(1);
    }
    @Test
    void loginFailureLockoutAndPasswordRotation() throws Exception {
        long u = user("lockout", 2);
        ExecutorService failures = Executors.newFixedThreadPool(5);
        try {
            List<Future<Integer>> attempts = new ArrayList<>();
            for (int i = 0; i < 5; i++)
                attempts.add(failures.submit(()
                                                 -> call("POST", "/auth/login", null,
                                                     Map.of("username", "lockout", "password",
                                                         "bad", "systems", List.of(COURSE)))
                                                     .status));
            for (Future<Integer> attempt : attempts)
                assertThat(attempt.get(30, TimeUnit.SECONDS)).isEqualTo(401);
        } finally {
            failures.shutdownNow();
        }
        assertThat(
            call("POST", "/auth/login", null,
                Map.of("username", "lockout", "password", PASSWORD, "systems", List.of(COURSE)))
                .status)
            .isEqualTo(401);
        ok("POST", "/auth/password",
            Map.of("oldPassword", PASSWORD, "newPassword", "ChangedPassword123!"));
        assertThat(call("GET", "/auth/me", root, null).status).isEqualTo(401);
    }
    @Test
    void auditConcurrentIdempotencyConflictAndRollback() throws Exception {
        long u = user("auditor", 2);
        long role = role("AUDIT");
        long read = jdbc.queryForObject("select id from sys_permission where system_id=2 and "
                     + "kind='API' and code='audit:read'",
                 Long.class),
             write = jdbc.queryForObject("select id from sys_permission where system_id=2 and "
                     + "kind='API' and code='audit:write'",
                 Long.class);
        grants(role, read, write);
        allow(2, role);
        assign(u, role);
        String token = login("auditor", COURSE);
        String request = UUID.randomUUID().toString();
        Map<String, Object> input = new LinkedHashMap<>(
            Map.of("requestId", request, "type", "score_changed", "objectType", "course",
                "objectId", "cpm-course-42", "target", "课程42", "after", Map.of("score", 88)));
        assertThat(call("POST", "/auth/audit-logs", token, input).status).isEqualTo(403);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) results.add(pool.submit(() -> auditRequest(token, input)));
            Set<Long> ids = new HashSet<>();
            for (Future<Long> result : results) ids.add(result.get(30, TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            assertThat(jdbc.queryForObject("select count(*) from sys_audit_log where request_id=?",
                           Long.class, request))
                .isEqualTo(1);
            input.put("target", "different");
            MvcResult conflict = mvc.perform(post("/auth/audit-logs")
                                                 .header("Authorization", "Bearer " + token)
                                                 .header("X-Audit-Service-Key", KEY)
                                                 .contentType(MediaType.APPLICATION_JSON)
                                                 .content(json.writeValueAsBytes(input)))
                                     .andReturn();
            assertThat(conflict.getResponse().getStatus()).isEqualTo(400);
            assertThat(conflict.getResponse().getContentAsString())
                .contains("AUDIT_DUPLICATE_CONFLICT");
            MvcResult queried = mvc.perform(get("/auth/audit-logs?target=课程42&page=1&pageSize=1")
                                                .header("Authorization", "Bearer " + token)
                                                .header("X-Audit-Service-Key", KEY))
                                    .andReturn();
            assertThat(queried.getResponse().getStatus()).isEqualTo(200);
            assertThat(
                json.readTree(queried.getResponse().getContentAsString()).path("total").asLong())
                .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
    long auditRequest(String token, Object input) throws Exception {
        MvcResult r = mvc.perform(post("/auth/audit-logs")
                                      .header("Authorization", "Bearer " + token)
                                      .header("X-Audit-Service-Key", KEY)
                                      .contentType(MediaType.APPLICATION_JSON)
                                      .content(json.writeValueAsBytes(input)))
                          .andReturn();
        assertThat(r.getResponse().getStatus())
            .withFailMessage(r.getResponse().getContentAsString())
            .isEqualTo(201);
        return json.readTree(r.getResponse().getContentAsString()).path("id").asLong();
    }
    @Test
    void localMutationAndAuditRollbackTogether() throws Exception {
        long roles = count("sys_role"), logs = count("sys_audit_log"), version = az.version();
        assertThatThrownBy(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
            management.createRole(
                actor, new Inputs.RoleCreate(COURSE, "ROLLBACK", "rollback", null));
            throw new IllegalStateException("注入失败");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("sys_role")).isEqualTo(roles);
        assertThat(count("sys_audit_log")).isEqualTo(logs);
        assertThat(az.version()).isEqualTo(version);
    }
    @Test
    void routeRegistryMatchesImplementedHttp() throws Exception {
        assertThat(ok("GET", "/systems", null).size()).isEqualTo(2);
        assertThat(ok("GET", "/dashboard", null).path("users").asLong()).isEqualTo(1);
        assertThat(ok("GET", "/me", null).path("effective").isObject()).isTrue();
    }

    @Test
    void dashboardRequiresAllThreeViewPermissions() throws Exception {
        long role = ok("POST", "/roles",
            Map.of("systemCode", "rbac-center", "code", "DASHBOARD_TEST", "name", "仪表盘权限测试"))
            .path("id").asLong();
        allow(2, role);
        assign(user("dashboard_tester", 2), role);
        String token = login("dashboard_tester", "rbac-center");
        String lookup = "/auth/api-permissions/getDashboard";
        String dashboard = "/api/v1/dashboard";
        String permissionId = "select id from sys_permission where system_id=1 and code=?";
        long users = jdbc.queryForObject(permissionId, Long.class, "rbac:user:view");
        long roles = jdbc.queryForObject(permissionId, Long.class, "rbac:role:view");
        long systems = jdbc.queryForObject(permissionId, Long.class, "rbac:system:view");

        grants(role, users, roles, systems);
        assertThat(call("GET", lookup, token, null).body().path("allowed").asBoolean()).isTrue();
        Response allowed = call("GET", dashboard, token, null);
        assertThat(allowed.status()).isEqualTo(200);
        assertThat(allowed.data().has("systems")).isTrue();

        for (long[] incomplete : List.of(new long[] {users}, new long[] {users, roles},
                 new long[] {users, systems}, new long[] {roles, systems})) {
            grants(role, incomplete);
            assertThat(call("GET", dashboard, token, null).status()).isEqualTo(403);
            Response decision = call("GET", lookup, token, null);
            assertThat(decision.status()).isEqualTo(200);
            assertThat(decision.body().path("allowed").asBoolean()).isFalse();
        }
    }

    @Test
    void managementLifecycleAndSqlPaging() throws Exception {
        long system =
            ok("POST", "/systems", Map.of("code", "extra", "name", "额外系统")).path("id").asLong();
        ok("PATCH", "/systems/" + system, Map.of("name", "新名称"));
        ok("PUT", "/systems/" + system + "/status", Map.of("status", "DISABLED"));
        long department =
            ok("POST", "/departments", Map.of("parentId", 1, "code", "QA", "name", "测试部"))
                .path("id")
                .asLong();
        ok("PATCH", "/departments/" + department, Map.of("name", "质量部"));
        assertThat(ok("GET", "/departments/tree", null).get(0).path("children").size())
            .isEqualTo(3);
        long u = user("person", department);
        JsonNode initial = ok("GET", "/users/" + u, null);
        assertThat(initial.has("passwordHash")).isFalse();
        ok("PATCH", "/users/" + u,
            Map.of("name", "改名", "no", "NO1", "phone", "13800000000", "version",
                initial.path("version").asLong()));
        assertThat(ok("GET", "/users/" + u, null).path("phone").asText()).isEqualTo("138****0000");
        assertThat(
            call("PATCH", "/api/v1/users/" + u, root, Map.of("name", "冲突", "version", 0)).status)
            .isEqualTo(409);
        assertThat(ok("GET",
                       "/users?keyword=改名&departmentId=" + department
                           + "&status=ACTIVE&page=1&pageSize=1",
                       null)
                       .path("total")
                       .asLong())
            .isEqualTo(1);
        long r = role("LIFE"), p = permission("life", "API");
        grants(r, p);
        ok("PATCH", "/roles/" + r, Map.of("name", "角色改名", "description", "说明"));
        assertThat(ok("GET", "/roles/" + r, null).path("status").asText()).isEqualTo("ENABLED");
        assertThat(
            ok("GET", "/roles?systemCode=" + COURSE + "&keyword=LIFE&status=ENABLED&pageSize=1",
                null)
                .path("total")
                .asLong())
            .isEqualTo(1);
        assertThat(
            ok("GET", "/permissions?systemCode=" + COURSE + "&kind=API&keyword=life&pageSize=1",
                null)
                .path("total")
                .asLong())
            .isEqualTo(1);
        allow(department, r);
        assertThat(
            ok("GET", "/departments/" + department + "/assignable-roles?systemCode=" + COURSE, null)
                .size())
            .isEqualTo(1);
        assign(u, r);
        long assignment = ok("GET", "/users/" + u + "/role-assignments?systemCode=" + COURSE, null)
                              .get(0)
                              .path("id")
                              .asLong();
        assertThat(
            call("POST", "/api/v1/users/" + u + "/role-assignments", root, Map.of("roleId", r))
                .status)
            .isEqualTo(409);
        assertThat(
            call("DELETE", "/api/v1/users/1/role-assignments/" + assignment, root, null).status)
            .isEqualTo(404);
        ok("DELETE", "/users/" + u + "/role-assignments/" + assignment, null);
        ok("PUT", "/permissions/" + p + "/status", Map.of("status", "DISABLED"));
        assertPermission(effective(r), "life", false);
        ok("PUT", "/permissions/" + p + "/status", Map.of("status", "ENABLED"));
        ok("PUT", "/users/" + u + "/status", Map.of("status", "DISABLED"));
        assertThat(
            call("POST", "/auth/login", null,
                Map.of("username", "person", "password", PASSWORD, "systems", List.of(COURSE)))
                .status)
            .isEqualTo(401);
        ok("PUT", "/users/" + u + "/status", Map.of("status", "ACTIVE"));
        ok("DELETE", "/users/" + u, null);
        assertThat(ok("GET", "/users/" + u, null).path("status").asText()).isEqualTo("DELETED");
        long empty =
            ok("POST", "/departments", Map.of("parentId", 1, "code", "EMPTY", "name", "空部门"))
                .path("id")
                .asLong();
        ok("DELETE", "/departments/" + empty, null);
        assertThat(
            ok("GET", "/systems/" + COURSE + "/role-hierarchy", null).path("roles").isArray())
            .isTrue();
        assertThat(ok("GET", "/audit-logs?pageSize=1", null).path("total").asLong()).isPositive();
    }
    @Test
    void uiAndApiAreIndependentWithExplicitMappings() throws Exception {
        long r = role("UIROLE"), ui = permission("ui:test", "UI"),
             api = permission("api:test", "API"), u = user("uiuser", 2);
        grants(r, ui);
        allow(2, r);
        assign(u, r);
        Map<String, Object> menu = new LinkedHashMap<>(
            Map.of("type", "MENU", "code", "testmenu", "name", "测试菜单", "permissionCode",
                "ui:test", "route", "/test", "component", "Test", "sortOrder", 2));
        long menuId = ok("POST", "/systems/" + COURSE + "/ui-resources", menu).path("id").asLong();
        long buttonPermission = permission("ui:button", "UI");
        long button = ok("POST", "/systems/" + COURSE + "/ui-resources",
            Map.of("parentId", menuId, "type", "BUTTON", "code", "testbutton", "name", "按钮",
                "permissionCode", "ui:button"))
                          .path("id")
                          .asLong();
        Map<String, Object> op =
            Map.of("operationId", "testOperation", "method", "POST", "pathTemplate", "/test/action",
                "permissionCode", "api:test", "accessPolicy", "PROTECTED", "enabled", true);
        long operation =
            ok("POST", "/systems/" + COURSE + "/api-operations", op).path("id").asLong();
        ok("PATCH", "/api-operations/" + operation, op);
        ok("PUT", "/systems/" + COURSE + "/ui-api-mappings",
            Map.of(
                "mappings", List.of(Map.of("uiResourceId", menuId, "apiOperationId", operation))));
        String token = login("uiuser", COURSE);
        assertThat(
            call("GET", "/auth/permissions/ui:test", token, null).body.path("allowed").asBoolean())
            .isTrue();
        assertThat(call("GET", "/auth/api-permissions/testOperation", token, null)
                       .body.path("allowed")
                       .asBoolean())
            .isFalse();
        JsonNode menus = call("GET", "/api/v1/menus/me", token, null).data();
        assertThat(menus.size()).isEqualTo(1);
        assertThat(menus.get(0).path("children").size()).isZero();
        grants(r, ui, api, buttonPermission);
        assertThat(call("GET", "/auth/api-permissions/testOperation", token, null)
                       .body.path("allowed")
                       .asBoolean())
            .isTrue();
        assertThat(
            call("GET", "/api/v1/menus/me", token, null).data().get(0).path("children").size())
            .isEqualTo(1);
        assertThat(call("DELETE", "/api/v1/ui-resources/" + menuId, root, null).status)
            .isEqualTo(409);
        menu.put("parentId", button);
        assertThat(call("PATCH", "/api/v1/ui-resources/" + menuId, root, menu).status)
            .isEqualTo(400);
        menu.remove("parentId");
        menu.put("name", "改菜单");
        ok("PATCH", "/ui-resources/" + menuId, menu);
        assertThat(ok("GET", "/systems/" + COURSE + "/ui-resources", null).isArray()).isTrue();
        assertThat(
            ok("GET", "/systems/" + COURSE + "/api-operations?keyword=testOperation&pageSize=1",
                null)
                .path("total")
                .asLong())
            .isEqualTo(1);
        assertThat(
            ok("GET", "/systems/" + COURSE + "/ui-api-mappings", null).path("mappings").size())
            .isEqualTo(1);
        ok("PUT", "/systems/" + COURSE + "/ui-api-mappings", Map.of("mappings", List.of()));
        ok("DELETE", "/ui-resources/" + button, null);
        ok("DELETE", "/ui-resources/" + menuId, null);
        Map<String, Object> check = Map.of(
            "systemCode", COURSE, "userId", u, "kind", "API", "operationId", "testOperation");
        assertThat(ok("POST", "/authorization/check", check).path("allowed").asBoolean()).isTrue();
        assertThat(ok("POST", "/authorization/explain", check).path("effective").isObject())
            .isTrue();
    }
    @Test
    void invalidAudienceKidIssuerIssuedAtAreRejected() throws Exception {
        java.security.PrivateKey key =
            (java.security.PrivateKey) org.springframework.test.util.ReflectionTestUtils.getField(
                tokens, "privateKey");
        Instant now = Instant.now();
        for (String bad : List.of("aud", "kid", "iss", "iat", "multi")) {
            io.jsonwebtoken.JwtBuilder builder =
                io.jsonwebtoken.Jwts.builder()
                    .header()
                    .keyId(bad.equals("kid") ? "wrong" : "rbac-key-1")
                    .and()
                    .subject("rbac-u1")
                    .issuer(bad.equals("iss") ? "wrong" : "rbac-center")
                    .issuedAt(Date.from(bad.equals("iat") ? now.plusSeconds(600) : now))
                    .expiration(Date.from(now.plusSeconds(7200)))
                    .id(UUID.randomUUID().toString())
                    .claim("tokenVersion", 0);
            builder.audience().add(bad.equals("aud") ? "unknown" : "rbac-center");
            if (bad.equals("multi"))
                builder.audience().add(COURSE);
            String token = builder.signWith(key, io.jsonwebtoken.Jwts.SIG.RS256).compact();
            assertThat(call("GET", "/auth/me", token, null).status)
                .withFailMessage(bad)
                .isEqualTo(401);
        }
    }

    @Test
    void departmentAdministratorCannotEscapeScopeOrSharedCatalogAndInheritedUpperBound()
        throws Exception {
        long adminRole = ok("POST", "/roles",
            Map.of("systemCode", "rbac-center", "code", "LOCAL_ADMIN", "name", "局部管理员"))
                             .path("id")
                             .asLong();
        List<Long> administration = jdbc.queryForList(
            "select id from sys_permission where system_id=1 and kind='API' and code in "
                + "('rbac:user:view','rbac:assignment:edit','rbac:role:edit','rbac:department:edit'"
                + ")",
            Long.class);
        grants(adminRole, administration.stream().mapToLong(Long::longValue).toArray());
        long own = role("OWN"), target = role("TARGET"), high = role("HIGH"),
             p = permission("upper:p", "API"), q = permission("upper:q", "API");
        grants(own, p);
        grants(target, p);
        grants(high, q);
        long local = user("localadmin", 2), inside = user("inside", 2),
             outside = user("outside", 3);
        allow(2, adminRole, own, target, high);
        assign(local, adminRole);
        assign(local, own);
        String token = login("localadmin", "rbac-center");
        assertThat(call("POST", "/api/v1/users/" + inside + "/role-assignments", token,
                       Map.of("roleId", target))
                       .status)
            .isEqualTo(200);
        assertThat(call("POST", "/api/v1/users/" + outside + "/role-assignments", token,
                       Map.of("roleId", target))
                       .status)
            .isEqualTo(403);
        assertThat(call("POST", "/api/v1/users/" + inside + "/role-assignments", token,
                       Map.of("roleId", high))
                       .status)
            .isEqualTo(403);
        assertThat(call("POST", "/api/v1/roles", token,
                       Map.of("systemCode", COURSE, "code", "FORBIDDEN", "name", "禁止"))
                       .status)
            .isEqualTo(403);
        long headquarters = user("hqadmin", 1);
        allow(1, 1, adminRole, own);
        assign(headquarters, adminRole);
        assign(headquarters, own);
        String hqToken = login("hqadmin", "rbac-center");
        assertThat(
            call("POST", "/api/v1/roles/" + target + "/inherits/" + high, hqToken, null).status)
            .isEqualTo(403);
        assertThat(jdbc.queryForObject("select count(*) from sys_role_hierarchy where senior_role_id=?", Long.class, target)).isZero();
        assertThat(call("GET", "/api/v1/users/" + outside, hqToken, null).status).isEqualTo(200);
    }

    @Test
    void previousPublicKeyRemainsVerifiableUntilTokenExpires() throws Exception {
        java.security.KeyPairGenerator generator =
            java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        java.security.KeyPair previous = generator.generateKeyPair();
        java.security.PrivateKey currentPrivate =
            (java.security.PrivateKey) org.springframework.test.util.ReflectionTestUtils.getField(
                tokens, "privateKey");
        java.security.PublicKey currentPublic =
            (java.security.PublicKey) org.springframework.test.util.ReflectionTestUtils.getField(
                tokens, "publicKey");
        java.nio.file.Path directory =
            java.nio.file.Files.createTempDirectory("structured-rotation-");
        try {
            java.nio.file.Path privateFile = directory.resolve("private.pem");
            java.nio.file.Path publicFile = directory.resolve("public.pem");
            java.nio.file.Path previousFile = directory.resolve("previous.pem");
            java.nio.file.Files.writeString(
                privateFile, pem("PRIVATE KEY", currentPrivate.getEncoded()));
            java.nio.file.Files.writeString(
                publicFile, pem("PUBLIC KEY", currentPublic.getEncoded()));
            java.nio.file.Files.writeString(
                previousFile, pem("PUBLIC KEY", previous.getPublic().getEncoded()));
            org.springframework.mock.env.MockEnvironment environment =
                new org.springframework.mock.env.MockEnvironment();
            environment.setActiveProfiles("test");
            TokenService rotated = new TokenService(db, az, environment, privateFile.toString(),
                publicFile.toString(), "new-key", "rbac-center", false,
                json.writeValueAsString(Map.of("old-key", previousFile.toString())));
            Instant now = Instant.now();
            String valid = signedToken(
                previous.getPrivate(), "old-key", now.minusSeconds(60), now.plusSeconds(3600));
            assertThat(rotated.verify("Bearer " + valid).userId()).isEqualTo(1L);
            assertThat(json.valueToTree(rotated.jwks()).path("keys").size()).isEqualTo(2);
            String expired = signedToken(
                previous.getPrivate(), "old-key", now.minusSeconds(7200), now.minusSeconds(1));
            assertThatThrownBy(() -> rotated.verify("Bearer " + expired))
                .isInstanceOf(BizException.class)
                .extracting(exception -> ((BizException) exception).status)
                .isEqualTo(401);
            String unknown = signedToken(
                previous.getPrivate(), "missing-key", now.minusSeconds(60), now.plusSeconds(3600));
            assertThatThrownBy(() -> rotated.verify("Bearer " + unknown))
                .isInstanceOf(BizException.class);
            assertThat(rotated.issue(db.get(User.class, 1L), "rbac-center").get("accessToken"))
                .isNotNull();
        } finally {
            try (java.util.stream.Stream<java.nio.file.Path> files =
                     java.nio.file.Files.list(directory)) {
                for (java.nio.file.Path file : files.toList()) java.nio.file.Files.delete(file);
            }
            java.nio.file.Files.delete(directory);
        }
    }

    private String signedToken(
        java.security.PrivateKey key, String kid, Instant issued, Instant expires) {
        return io.jsonwebtoken.Jwts.builder()
            .header()
            .keyId(kid)
            .and()
            .subject("rbac-u1")
            .issuer("rbac-center")
            .audience()
            .add("rbac-center")
            .and()
            .issuedAt(Date.from(issued))
            .expiration(Date.from(expires))
            .id(UUID.randomUUID().toString())
            .claim("tokenVersion", 0)
            .signWith(key, io.jsonwebtoken.Jwts.SIG.RS256)
            .compact();
    }

    private String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
            + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(encoded) + "\n-----END "
            + type + "-----\n";
    }

    private Response auditCall(String token, Object input, String key) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder =
            post("/auth/audit-logs")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(input));
        if (key != null)
            builder.header("X-Audit-Service-Key", key);
        MvcResult result = mvc.perform(builder).andReturn();
        return new Response(result.getResponse().getStatus(),
            json.readTree(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void externalAuditRejectsUnsafePayloadsAndCrossActorReplay() throws Exception {
        long u = user("auditadmin", 2);
        assign(u, 6);
        String token = login("auditadmin", COURSE);
        Map<String, Object> input = new LinkedHashMap<>(
            Map.of("requestId", UUID.randomUUID().toString(), "type", "score_changed", "objectType",
                "course", "objectId", "cpm-course-42", "target", "课程42"));
        assertThat(auditCall(token, input, null).status).isEqualTo(403);
        assertThat(auditCall(token, input, "wrong").status).isEqualTo(403);
        for (String type : List.of("unknown", "ROLE_CHANGED")) {
            input.put("type", type);
            assertThat(auditCall(token, input, KEY).status).isEqualTo(400);
        }
        input.put("type", "score_changed");
        input.put("objectId", "42");
        assertThat(auditCall(token, input, KEY).status).isEqualTo(400);
        input.put("objectId", "cpm-term-42");
        assertThat(auditCall(token, input, KEY).status).isEqualTo(400);
        input.put("objectId", "cpm-course-42");
        input.put("target", "x".repeat(501));
        assertThat(auditCall(token, input, KEY).status).isEqualTo(400);
        input.put("target", "课程42");
        input.put("before", Map.of("password", "secret"));
        assertThat(auditCall(token, input, KEY).status).isEqualTo(400);
        input.put("before", List.of(1, 2));
        assertThat(auditCall(token, input, KEY).status).isEqualTo(400);
        input.put("before", Map.of("score", 1, "status", "DRAFT"));
        Response first = auditCall(token, input, KEY);
        assertThat(first.status).isEqualTo(201);
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("status", "DRAFT");
        reordered.put("score", 1);
        input.put("before", reordered);
        assertThat(auditCall(token, input, KEY).body.path("id")).isEqualTo(first.body.path("id"));
        long other = user("otherauditor", 3);
        assign(other, 6);
        String otherToken = login("otherauditor", COURSE);
        Response conflict = auditCall(otherToken, input, KEY);
        assertThat(conflict.status).isEqualTo(400);
        assertThat(conflict.body.has("id")).isFalse();
        MvcResult invalid =
            mvc.perform(get("/auth/audit-logs?from=2030-01-01T00:00:00Z&to=2020-01-01T00:00:00Z")
                            .header("Authorization", "Bearer " + token)
                            .header("X-Audit-Service-Key", KEY))
                .andReturn();
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void realAuditInsertFailureRollsBackBusinessAndVersion() throws Exception {
        long roles = count("sys_role"), version = az.version(), logs = count("sys_audit_log");
        jdbc.execute("CREATE TRIGGER structured_audit_failure BEFORE INSERT ON sys_audit_log FOR "
            + "EACH ROW BEGIN IF NEW.event_type='ROLE_CREATED' THEN SIGNAL SQLSTATE "
            + "'45000' SET MESSAGE_TEXT='injected audit failure'; END IF; END");
        try {
            Response result = call("POST", "/api/v1/roles", root,
                Map.of("systemCode", COURSE, "code", "NO_COMMIT", "name", "不得提交"));
            assertThat(result.status).isEqualTo(503);
            assertThat(count("sys_role")).isEqualTo(roles);
            assertThat(az.version()).isEqualTo(version);
            assertThat(count("sys_audit_log")).isEqualTo(logs);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS structured_audit_failure");
        }
    }

    @Test
    void copyCourseRequiresTeacherWithoutMakingCcGloballyMandatory() throws Exception {
        long role = ok("POST", "/roles",
            Map.of("systemCode", COURSE, "code", "LAB_TEACHER", "name", "实验课教师"))
                        .path("id")
                        .asLong();
        long courseAccess = jdbc.queryForObject("select id from sys_permission where system_id=2 "
                + "and kind='API' and code='course:access'",
            Long.class);
        grants(role, courseAccess);
        jdbc.update("insert into sys_role_hierarchy(senior_role_id,junior_role_id) "
                + "select ?, id from sys_role where system_id=2 and code='teacher'", role);
        long u = user("projectedteacher", 2);
        allow(2, role);
        assign(u, role);
        String token = login("projectedteacher", COURSE);
        assertThat(call("GET", "/auth/api-permissions/copyCourse", token, null)
                       .body.path("allowed")
                       .asBoolean())
            .isTrue();
        assertThat(call("GET", "/integration/authorities", token, null).status).isEqualTo(403);
        MvcResult result = mvc.perform(get("/integration/authorities")
                                           .header("Authorization", "Bearer " + token)
                                           .header("X-Audit-Service-Key", KEY))
                               .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode authorities =
            json.readTree(result.getResponse().getContentAsString()).path("authorities");
        assertThat(authorities.toString()).contains("ROLE_teacher").doesNotContain("\"CC\"");
        MvcResult denied = mvc.perform(get("/identity/users/rbac-u1")
                                           .header("Authorization", "Bearer " + token)
                                           .header("X-Audit-Service-Key", KEY))
                               .andReturn();
        assertThat(denied.getResponse().getStatus()).isEqualTo(403);
        long admin = user("provideradmin", 3);
        assign(admin, 6);
        String adminToken = login("provideradmin", COURSE);
        MvcResult lookup = mvc.perform(get("/identity/users/rbac-u1")
                                           .header("Authorization", "Bearer " + adminToken)
                                           .header("X-Audit-Service-Key", KEY))
                               .andReturn();
        assertThat(lookup.getResponse().getStatus()).isEqualTo(200);
        MvcResult missing = mvc.perform(get("/identity/users/not-found")
                                            .header("Authorization", "Bearer " + adminToken)
                                            .header("X-Audit-Service-Key", KEY))
                                .andReturn();
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
    }

    /** 只重启用户指定的专用测试Redis，不清空任何实例或其他命名空间。 */
    private void restartDedicatedRedis() throws Exception {
        java.nio.file.Path socket = java.nio.file.Path.of("/var/run/docker.sock");
        if (java.nio.file.Files.exists(socket)) {
            try (java.nio.channels.SocketChannel channel =
                     java.nio.channels.SocketChannel.open(java.net.StandardProtocolFamily.UNIX)) {
                channel.connect(java.net.UnixDomainSocketAddress.of(socket));
                String request =
                    "POST /containers/rbac-phase2-test-redis/restart?t=1 HTTP/1.1\r\nHost: "
                    + "localhost\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(
                    request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                while (bytes.hasRemaining()) channel.write(bytes);
                java.nio.ByteBuffer response = java.nio.ByteBuffer.allocate(4096);
                channel.read(response);
                response.flip();
                String status =
                    java.nio.charset.StandardCharsets.US_ASCII.decode(response).toString();
                assertThat(status).contains(" 204 ");
            }
        } else {
            Process process =
                new ProcessBuilder("docker", "restart", "--time", "1", "rbac-phase2-test-redis")
                    .redirectErrorStream(true)
                    .start();
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
        }
        boolean ready = false;
        for (int attempt = 0; attempt < 40; attempt++) {
            try {
                if ("PONG".equals(redis.getConnectionFactory().getConnection().ping())) {
                    ready = true;
                    break;
                }
            } catch (Exception unavailable) {
                Thread.sleep(250);
            }
        }
        assertThat(ready).isTrue();
    }

    /** 两个真实JVM共享RSA公钥和数据库，分别暖缓存后验证下一次请求撤权。 */
    @Test
    void twoJvmImmediateRevocationWithSharedKeysAndPersistentLogout() throws Exception {
        long r = role("REPLICA_ROLE"), p = permission("replica:p", "API"),
             u = user("replicauser", 2);
        grants(r, p);
        allow(2, r);
        assign(u, r);
        ok("POST", "/systems/" + COURSE + "/api-operations",
            Map.of("operationId", "replicaOperation", "method", "POST", "pathTemplate",
                "/replica/protected", "permissionCode", "replica:p", "accessPolicy", "PROTECTED",
                "enabled", true));
        String token = login("replicauser", COURSE);
        java.nio.file.Path directory =
            java.nio.file.Files.createTempDirectory("m3-structured-replica-");
        java.nio.file.Path log =
            java.nio.file.Files.createTempFile("m3-structured-replica-", ".log");
        Process replica = null;
        try {
            java.security.PrivateKey privateKey =
                (java.security.PrivateKey) org.springframework.test.util.ReflectionTestUtils
                    .getField(tokens, "privateKey");
            java.security.PublicKey publicKey =
                (java.security.PublicKey)
                    org.springframework.test.util.ReflectionTestUtils.getField(tokens, "publicKey");
            java.nio.file.Path privateFile = directory.resolve("private.pem"),
                               publicFile = directory.resolve("public.pem");
            java.nio.file.Files.writeString(
                privateFile, pem("PRIVATE KEY", privateKey.getEncoded()));
            java.nio.file.Files.writeString(publicFile, pem("PUBLIC KEY", publicKey.getEncoded()));
            int port;
            try (java.net.ServerSocket reservation = new java.net.ServerSocket(0)) {
                port = reservation.getLocalPort();
            }
            String javaExecutable =
                java.nio.file.Path
                    .of(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                    .toString();
            String classpath = System.getProperty(
                "surefire.test.class.path", System.getProperty("java.class.path"));
            org.springframework.data.redis.connection.RedisStandaloneConfiguration redisConfig =
                ((org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory)
                        redis.getConnectionFactory())
                    .getStandaloneConfiguration();
            List<String> command = List.of(javaExecutable, "-cp", classpath,
                "com.xmu.rbac.structured.RbacStructuredApplication",
                "--spring.profiles.active=test", "--server.address=127.0.0.1",
                "--server.port=" + port, "--rbac.jwt.private-key-file=" + privateFile,
                "--rbac.jwt.public-key-file=" + publicFile,
                "--rbac.jwt.kid="
                    + org.springframework.test.util.ReflectionTestUtils.getField(tokens, "kid"),
                "--rbac.jwt.issuer="
                    + org.springframework.test.util.ReflectionTestUtils.getField(tokens, "issuer"),
                "--rbac.jwt.allow-ephemeral=false",
                "--spring.data.redis.host=" + redisConfig.getHostName(),
                "--spring.data.redis.port=" + redisConfig.getPort());
            replica = new ProcessBuilder(command)
                          .redirectErrorStream(true)
                          .redirectOutput(log.toFile())
                          .start();
            java.net.http.HttpClient client =
                java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            String base = "http://127.0.0.1:" + port;
            boolean ready = false;
            Instant deadline = Instant.now().plusSeconds(90);
            while (replica.isAlive() && Instant.now().isBefore(deadline)) {
                try {
                    java.net.http.HttpResponse<String> health = client.send(
                        java.net.http.HttpRequest
                            .newBuilder(java.net.URI.create(base + "/api/v1/actuator/health"))
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                    if (health.statusCode() == 200) {
                        ready = true;
                        break;
                    }
                } catch (java.io.IOException notReady) {
                    Thread.sleep(250);
                }
            }
            assertThat(ready).withFailMessage("第二JVM未就绪，日志：%s", log).isTrue();
            assertThat(call("GET", "/auth/api-permissions/replicaOperation", token, null)
                           .body.path("allowed")
                           .asBoolean())
                .isTrue();
            assertThat(replicaAllowed(client, base, token)).isTrue();
            grants(r);
            assertThat(call("GET", "/auth/api-permissions/replicaOperation", token, null)
                           .body.path("allowed")
                           .asBoolean())
                .isFalse();
            assertThat(replicaAllowed(client, base, token)).isFalse();
            assertThat(call("POST", "/auth/logout", token, null).status).isEqualTo(204);
            java.net.http.HttpResponse<String> revoked = client.send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/auth/me"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(revoked.statusCode()).isEqualTo(401);
        } finally {
            if (replica != null) {
                replica.destroy();
                if (!replica.waitFor(20, TimeUnit.SECONDS))
                    replica.destroyForcibly();
            }
            try (java.util.stream.Stream<java.nio.file.Path> files =
                     java.nio.file.Files.list(directory)) {
                for (java.nio.file.Path file : files.toList())
                    java.nio.file.Files.deleteIfExists(file);
            }
            java.nio.file.Files.deleteIfExists(directory);
        }
    }

    private boolean replicaAllowed(java.net.http.HttpClient client, String base, String token)
        throws Exception {
        java.net.http.HttpResponse<String> response = client.send(
            java.net.http.HttpRequest
                .newBuilder(java.net.URI.create(base + "/auth/api-permissions/replicaOperation"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("allowed").asBoolean();
    }

    /** 重新启用同样属于授予能力，不能绕过变更前的授权上界。 */
    @Test
    void activationCannotRestoreCapabilitiesAboveActor() throws Exception {
        long administrator = ok("POST", "/roles",
            Map.of("systemCode", "rbac-center", "code", "ACTIVATOR", "name", "启用管理员"))
                                 .path("id")
                                 .asLong();
        List<Long> permissions =
            jdbc.queryForList("select id from sys_permission where system_id=1 and kind='API' and "
                    + "code in ('rbac:user:edit','rbac:system:edit')",
                Long.class);
        grants(administrator, permissions.stream().mapToLong(Long::longValue).toArray());
        long owned = role("ACTIVATION_OWN"), higher = role("ACTIVATION_HIGH"),
             p = permission("activation:p", "API"), q = permission("activation:q", "API");
        grants(owned, p);
        grants(higher, q);
        long managerUser = user("activationadmin", 1), target = user("activationtarget", 2);
        allow(1, 1, administrator, owned);
        allow(2, higher);
        assign(managerUser, administrator);
        assign(managerUser, owned);
        assign(target, higher);
        String managerToken = login("activationadmin", "rbac-center");
        ok("PUT", "/users/" + target + "/status", Map.of("status", "DISABLED"));
        assertThat(call("PUT", "/api/v1/users/" + target + "/status", managerToken,
                       Map.of("status", "ACTIVE"))
                       .status)
            .isEqualTo(403);
        assertThat(ok("GET", "/users/" + target, null).path("status").asText())
            .isEqualTo("DISABLED");
        ok("PUT", "/systems/2/status", Map.of("status", "DISABLED"));
        assertThat(
            call("PUT", "/api/v1/systems/2/status", managerToken, Map.of("status", "ENABLED"))
                .status)
            .isEqualTo(403);
        assertThat(jdbc.queryForObject("select status from sys_system where id=2", String.class))
            .isEqualTo("DISABLED");
        ok("PUT", "/systems/2/status", Map.of("status", "ENABLED"));
    }

    /** 被检查主体停用不能误报为调用管理员未认证，应形成明确DENY及空有效投影。 */
    @Test
    void inactiveTargetProducesDenyWithoutInvalidatingAdministrator() throws Exception {
        long u = user("inactivetarget", 2);
        ok("PUT", "/users/" + u + "/status", Map.of("status", "DISABLED"));
        Map<String, Object> request =
            Map.of("systemCode", COURSE, "userId", u, "kind", "UI", "permission", "CV");
        JsonNode decision = ok("POST", "/authorization/check", request);
        assertThat(decision.path("allowed").asBoolean()).isFalse();
        assertThat(ok("POST", "/authorization/explain", request)
                       .path("effective")
                       .path("effectiveRoles")
                       .size())
            .isZero();
        assertThat(ok("GET", "/users/" + u + "/systems/" + COURSE + "/permissions", null)
                       .path("permissions")
                       .size())
            .isZero();
        assertThat(call("GET", "/auth/me", root, null).status).isEqualTo(200);
        ok("PUT", "/users/" + u + "/status", Map.of("status", "ACTIVE"));
        ok("PUT", "/systems/2/status", Map.of("status", "DISABLED"));
        assertThat(ok("POST", "/authorization/check", request).path("allowed").asBoolean())
            .isFalse();
        assertThat(ok("GET", "/users/" + u + "/systems/" + COURSE + "/permissions", null)
                       .path("permissions")
                       .size())
            .isZero();
    }

    @Test
    void rolePatchRejectsWhitespaceNameWithoutWriting() throws Exception {
        long target = role("ROLE_INPUT_BOUNDARY");
        long version = jdbc.queryForObject("select version from sys_grant_version where id=1", Long.class);
        long success = jdbc.queryForObject("select count(*) from sys_audit_log where result='SUCCESS'", Long.class);
        Response response = call("PATCH", "/api/v1/roles/" + target, root, Map.of("name", "   "));
        assertThat(response.status).isEqualTo(400);
        assertThat(response.body.path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("select name from sys_role where id=?", String.class, target))
            .isEqualTo("ROLE_INPUT_BOUNDARY");
        assertThat(jdbc.queryForObject("select version from sys_grant_version where id=1", Long.class))
            .isEqualTo(version);
        assertThat(jdbc.queryForObject("select count(*) from sys_audit_log where result='SUCCESS'", Long.class))
            .isEqualTo(success);
    }

    /** 停用节点不能隐藏可恢复的超级管理员能力；拒绝后业务、版本与成功审计均不变。 */
    @Test
    void nonSuperCannotPlantDisabledSuperAdministratorPath() throws Exception {
        long guard = ok("POST", "/roles",
            Map.of("systemCode", "rbac-center", "code", "DORMANT_GUARD", "name", "角色管理员"))
                         .path("id").asLong();
        long dormant = ok("POST", "/roles",
            Map.of("systemCode", "rbac-center", "code", "DORMANT_BRIDGE", "name", "停用继承桥"))
                           .path("id").asLong();
        long edit = jdbc.queryForObject(
            "select id from sys_permission where system_id=1 and kind='API' and code='rbac:role:edit'",
            Long.class);
        grants(guard, edit);
        grants(1);
        edge(dormant, 1);
        ok("PUT", "/roles/" + dormant + "/status", Map.of("status", "DISABLED"));
        long u = user("dormantroleguard", 1);
        allow(1, 1, guard);
        assign(u, guard);
        String ordinary = login("dormantroleguard", "rbac-center");
        long version = jdbc.queryForObject(
            "select version from sys_grant_version where id=1", Long.class);
        long success = jdbc.queryForObject(
            "select count(*) from sys_audit_log where result='SUCCESS'", Long.class);

        assertThat(call("POST", "/api/v1/roles/" + guard + "/inherits/" + dormant,
                       ordinary, null).status).isEqualTo(403);
        assertThat(jdbc.queryForObject(
                       "select count(*) from sys_role_hierarchy where senior_role_id=? and junior_role_id=?",
                       Long.class, guard, dormant)).isZero();
        assertThat(jdbc.queryForObject(
                       "select version from sys_grant_version where id=1", Long.class))
            .isEqualTo(version);
        assertThat(jdbc.queryForObject(
                       "select count(*) from sys_audit_log where result='SUCCESS'", Long.class))
            .isEqualTo(success);
        ok("PUT", "/roles/" + dormant + "/status", Map.of("status", "ENABLED"));
        assertThat(ok("GET", "/users/" + u + "/systems/rbac-center/permissions", null)
                       .path("effectiveRoles").findValuesAsText("code"))
            .doesNotContain("SUPER_ADMIN");
    }

    /** SUPER_ADMIN的能力标记独立于权限集合，清空直接权限也不能授权普通管理员触碰。 */
    @Test
    void nonSuperCannotModifyGrantOrInheritAnEmptySuperAdmin() throws Exception {
        long guard = ok("POST", "/roles",
            Map.of("systemCode", "rbac-center", "code", "SUPER_GUARD", "name", "普通角色管理员"))
                         .path("id")
                         .asLong();
        long bridge = ok("POST", "/roles",
            Map.of("systemCode", "rbac-center", "code", "SUPER_BRIDGE", "name", "超级继承桥"))
                          .path("id")
                          .asLong();
        List<Long> rights =
            jdbc.queryForList("select id from sys_permission where system_id=1 and kind='API' and "
                              + "code in ('rbac:role:edit','rbac:role:status','rbac:assignment:edit')",
                Long.class);
        grants(guard, rights.stream().mapToLong(Long::longValue).toArray());
        long u = user("ordinaryroleguard", 1);
        allow(1, 1, guard, bridge);
        assign(u, guard);
        String ordinary = login("ordinaryroleguard", "rbac-center");
        List<Long> original = jdbc.queryForList(
            "select permission_id from sys_role_permission where role_id=1", Long.class);
        try {
            grants(1);
            assertThat(ok("GET", "/roles/1/permissions", null).size()).isZero();
            edge(bridge, 1);
            assertThat(call("PUT", "/api/v1/roles/1/permissions", ordinary,
                           Map.of("permissionIds", List.of()))
                           .status)
                .isEqualTo(403);
            assertThat(call("PUT", "/api/v1/roles/1/permissions", ordinary,
                           Map.of("permissionIds", rights))
                           .status)
                .isEqualTo(403);
            assertThat(call("POST", "/api/v1/users/" + u + "/role-assignments", ordinary,
                           Map.of("roleId", 1))
                           .status)
                .isEqualTo(403);
            assertThat(
                call("POST", "/api/v1/roles/" + guard + "/inherits/1", ordinary, null).status)
                .isEqualTo(403);
            assertThat(
                call("POST", "/api/v1/roles/" + guard + "/inherits/" + bridge, ordinary, null)
                    .status)
                .isEqualTo(403);
            assertThat(call("PUT", "/api/v1/roles/1/status", ordinary, Map.of("status", "DISABLED"))
                           .status)
                .isEqualTo(403);
            assertThat(call("DELETE", "/api/v1/roles/1", ordinary, null).status).isEqualTo(403);
            assertThat(jdbc.queryForObject(
                           "select count(*) from sys_user_role where user_id=? and role_id=1",
                           Long.class, u))
                .isZero();
            assertThat(jdbc.queryForObject(
                           "select count(*) from sys_role_hierarchy where senior_role_id=?",
                           Long.class, guard))
                .isZero();
        } finally {
            grants(1, original.stream().mapToLong(Long::longValue).toArray());
        }
    }
}
