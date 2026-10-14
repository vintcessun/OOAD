package com.xmu.rbac.structured.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmu.rbac.structured.RbacStructuredApplication;
import com.xmu.rbac.structured.entity.Role;
import com.xmu.rbac.structured.entity.RolePermission;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验收用例 T01–T22（审查报告 §10.2，需求—用例追溯见 docs/09-test-plan.md §4）。
 * 全部经 HTTP 接口、连真实 MySQL 与 Redis 执行。
 */
class AcceptanceTest extends IntegrationTestBase {

    // ------------------------------------------------------------------ 登录场景

    @Test
    void t01_loginWithCorrectCredentialsGivesUsableToken() throws Exception {
        user("u1", org.aFinance());
        String token = login("u1");
        Resp me = getJson(token, "/auth/me");
        assertEquals(200, me.status());
        assertEquals("u1", me.data().path("user").path("username").asText());
    }

    @Test
    void t02_wrongPasswordDisabledAndDeletedAccountsCannotLogin() throws Exception {
        user("u1", org.aFinance());
        User disabled = user("u2", org.aFinance());
        disabled.status = User.DISABLED;
        userMapper.save(disabled);
        User deleted = user("u3", org.aFinance());
        deleted.status = User.DELETED;
        userMapper.save(deleted);

        assertEquals(401, loginStatus("u1", "wrong"));
        assertEquals(401, loginStatus("u2", PASSWORD));
        assertEquals(401, loginStatus("u3", PASSWORD));
        assertEquals(401, loginStatus("nobody", PASSWORD));
        // 口令错误与已删除不区分（40102），口令正确但已停用才说明（40104）
        assertEquals(40102, postJson(null, "/auth/login", Map.of("username", "u1", "password", "wrong")).code());
        assertEquals(40104, postJson(null, "/auth/login", Map.of("username", "u2", "password", PASSWORD)).code());

        // 每次失败都有审计
        JsonNode logs = getJson(login("root"), "/audit-logs?eventType=LOGIN&result=FAILED&size=50").data();
        assertEquals(6, logs.path("total").asInt());
        assertTrue(logs.toString().contains("SUBJECT_DISABLED"), logs.toString());
        assertTrue(logs.toString().contains("CREDENTIAL_INVALID"), logs.toString());
    }

    @Test
    void t03_temporaryUserHasNoEmployeeInfoButExpires() throws Exception {
        String root = login("root");
        Map<String, Object> req = new HashMap<>();
        req.put("username", "temp1");
        req.put("realName", "临时工");
        req.put("departmentId", org.roadProject().id);
        req.put("userType", "TEMPORARY");
        req.put("expiresAt", LocalDateTime.now().plusDays(1).toString());
        req.put("password", PASSWORD);
        Resp created = postJson(root, "/users", req);
        assertEquals(200, created.status(), created.toString());
        assertTrue(created.data().path("employeeNo").isNull());

        // 临时用户不能有工号
        req.put("username", "temp2");
        req.put("employeeNo", "X1");
        assertEquals(400, postJson(root, "/users", req).status());
        // 临时用户必须有到期时间
        req.remove("employeeNo");
        req.remove("expiresAt");
        assertEquals(400, postJson(root, "/users", req).status());

        // 到期后：不能登录，鉴权结论为 SUBJECT_EXPIRED
        String token = login("temp1");
        User t = userMapper.findByUsername("temp1").orElseThrow();
        t.expiresAt = LocalDateTime.now().minusMinutes(1);
        userMapper.save(t);
        assertEquals(401, getJson(token, "/auth/me").status());
        assertEquals(401, loginStatus("temp1", PASSWORD));
        JsonNode d = postJson(root, "/authz/check",
                Map.of("userId", t.id, "permission", "oa:doc:draft:view")).data();
        assertEquals("SUBJECT_EXPIRED", d.path("reason").asText());
    }

    // ------------------------------------------------------------------ 鉴权场景

    @Test
    void t04_userWithoutRolesIsDeniedByDefault() throws Exception {
        user("u1", org.aFinance());
        String token = login("u1");
        JsonNode d = check(token, "oa:doc:draft:view", org.aFinance().id);
        assertFalse(d.path("allowed").asBoolean());
        assertEquals("MISSING_PERMISSION", d.path("reason").asText());
        assertEquals(403, getJson(token, "/users").status());
    }

    @Test
    void t05_roleWithoutTargetPermissionIsDenied() throws Exception {
        User u = user("u1", org.aFinance());
        grant(u, org.docViewer(), org.aFinance());
        String token = login("u1");
        assertTrue(check(token, "oa:doc:draft:view", org.aFinance().id).path("allowed").asBoolean());
        assertEquals("MISSING_PERMISSION", check(token, "oa:doc:draft:approve", org.aFinance().id)
                .path("reason").asText());
    }

    @Test
    void t06_multipleRolesAreUnioned() throws Exception {
        User u = user("u1", org.aFinance());
        grant(u, org.docViewer(), org.aFinance());
        var other = role("DOC_APPROVE_ONLY", "只审批", null, org.docApprove());
        grant(u, other, org.aFinance());
        String token = login("u1");
        assertTrue(check(token, "oa:doc:draft:view", org.aFinance().id).path("allowed").asBoolean());
        assertTrue(check(token, "oa:doc:draft:approve", org.aFinance().id).path("allowed").asBoolean());
    }

    @Test
    void t07_permissionInDepartmentADoesNotReachDepartmentB() throws Exception {
        User u = user("u1", org.road());
        grant(u, org.docApprover(), org.road());
        String token = login("u1");
        // 道路分公司及其下级：允许
        assertTrue(check(token, "oa:doc:draft:approve", org.road().id).path("allowed").asBoolean());
        assertTrue(check(token, "oa:doc:draft:approve", org.roadFinance().id).path("allowed").asBoolean());
        // 桥梁分公司、总公司财务部：超出作用域
        JsonNode d = check(token, "oa:doc:draft:approve", org.bridgeFinance().id);
        assertFalse(d.path("allowed").asBoolean());
        assertEquals("OUT_OF_DATA_SCOPE", d.path("reason").asText());
        assertFalse(check(token, "oa:doc:draft:approve", org.aFinance().id).path("allowed").asBoolean());
    }

    @Test
    void t07b_scopeBelongsToTheAssignmentNotToTheUnionOfRoles() throws Exception {
        // 审批权只在道路分公司，查看权只在桥梁分公司：不能拼出「在桥梁分公司审批」
        User u = user("u1", org.road());
        grant(u, org.docApprover(), org.road());
        grant(u, org.docViewer(), org.bridge());
        String token = login("u1");
        assertTrue(check(token, "oa:doc:draft:view", org.bridgeFinance().id).path("allowed").asBoolean());
        assertFalse(check(token, "oa:doc:draft:approve", org.bridgeFinance().id).path("allowed").asBoolean());
    }

    // ------------------------------------------------------------------ 管理员

    @Test
    void t08_adminAssignsRoleToSubordinate() throws Exception {
        User admin = adminOf(org.road());
        grant(admin, org.docViewer(), org.road());   // 管理员自己在道路分公司持有该角色的权限，才能授出
        User staff = user("staff", org.roadFinance());
        String token = login(admin.username);
        Resp r = postJson(token, "/users/" + staff.id + "/roles", Map.of("roleId", org.docViewer().id));
        assertEquals(200, r.status(), r.toString());
        assertEquals(org.roadFinance().id.longValue(), r.data().path("scopeDeptId").asLong());
    }

    @Test
    void t09_adminCannotGrantToAnotherBranchAndItIsAudited() throws Exception {
        User admin = adminOf(org.road());
        User other = user("other", org.bridgeFinance());
        String token = login(admin.username);
        Resp r = postJson(token, "/users/" + other.id + "/roles", Map.of("roleId", org.docViewer().id));
        assertEquals(403, r.status());
        assertEquals(40302, r.code());
        // 也不能把下属的适用部门设到管理范围以外
        User staff = user("staff", org.roadFinance());
        Resp r2 = postJson(token, "/users/" + staff.id + "/roles",
                Map.of("roleId", org.docViewer().id, "scopeDepartmentId", org.aRoot().id));
        assertEquals(403, r2.status());

        String root = login("root");
        JsonNode logs = getJson(root, "/audit-logs?eventType=ROLE_ASSIGN&result=DENIED").data().path("items");
        assertTrue(logs.size() >= 2);
        assertEquals("OUT_OF_MANAGE_SCOPE", logs.get(0).path("reason").asText());
        assertEquals(admin.username, logs.get(0).path("actorName").asText());
    }

    @Test
    void t10_adminCannotGrantRoleWithPermissionsTheyLack() throws Exception {
        User admin = adminOf(org.road());
        grant(admin, org.docViewer(), org.road());     // 有查看，没有审批
        User staff = user("staff", org.roadFinance());
        String token = login(admin.username);
        assertEquals(200, postJson(token, "/users/" + staff.id + "/roles",
                Map.of("roleId", org.docViewer().id)).status());
        Resp r = postJson(token, "/users/" + staff.id + "/roles", Map.of("roleId", org.docApprover().id));
        assertEquals(403, r.status());
        assertEquals(40303, r.code());
        // 超级管理员角色只有超级管理员能分配
        Resp r2 = postJson(token, "/users/" + staff.id + "/roles", Map.of("roleId", org.superAdmin().id));
        assertEquals(403, r2.status());
        // 也不能给自己升级
        Resp r3 = postJson(token, "/users/" + admin.id + "/roles", Map.of("roleId", org.docApprover().id));
        assertEquals(403, r3.status());
    }

    @Test
    void t10b_selfOnlyGrantDoesNotLetAdminGrantTheFullPermission() throws Exception {
        // 操作者的「查看」只限本人（权限清单的「查看本人」）
        Role selfViewer = role("DOC_SELF_VIEWER", "公文查阅（本人）", null, org.docView());
        RolePermission rp = rolePermissionMapper.findByRoleIdAndPermissionId(selfViewer.id, org.docView().id).orElseThrow();
        rp.selfOnly = true;
        rolePermissionMapper.save(rp);
        User admin = user("admin", org.aRoot());
        grant(admin, org.rbacAdmin(), org.aRoot());
        grant(admin, selfViewer, org.aRoot());
        User staff = user("staff", org.roadFinance());
        String token = login(admin.username);

        // 分配含完整「查看」的角色：拒绝
        Resp r = postJson(token, "/users/" + staff.id + "/roles", Map.of("roleId", org.docViewer().id));
        assertEquals(403, r.status(), r.toString());
        assertEquals(40303, r.code());
        // 同样只限本人的角色可以分配
        assertEquals(200, postJson(token, "/users/" + staff.id + "/roles",
                Map.of("roleId", selfViewer.id)).status());

        // 给本公司角色授予完整「查看」：同样拒绝（这里授出的都是完整权限）
        long id = postJson(token, "/roles", Map.of("roleCode", "A_ROLE", "roleName", "A 公司角色",
                "companyId", org.a().id)).data().path("id").asLong();
        Resp g = postJson(token, "/roles/" + id + "/permissions", Map.of("permissionIds", List.of(org.docView().id)));
        assertEquals(403, g.status(), g.toString());
        assertEquals(40303, g.code());

        String root = login("root");
        JsonNode assignLog = getJson(root, "/audit-logs?eventType=ROLE_ASSIGN&result=DENIED").data().path("items");
        assertEquals("PRIVILEGE_ESCALATION", assignLog.get(0).path("reason").asText());
        JsonNode grantLog = getJson(root, "/audit-logs?eventType=PERMISSION_ASSIGN&result=DENIED").data().path("items");
        assertEquals("PRIVILEGE_ESCALATION", grantLog.get(0).path("reason").asText());
    }

    @Test
    void t11_superAdminBypassesEngineButMustBeActive() throws Exception {
        String root = login("root");
        JsonNode d = check(root, "anything:at:all", org.bFinance().id);
        assertTrue(d.path("allowed").asBoolean());
        assertEquals("SUPER_ADMIN_BYPASS", d.path("reason").asText());

        User second = user("root2", org.aRoot());
        grant(second, org.superAdmin(), org.aRoot());
        second.status = User.DISABLED;
        userMapper.save(second);
        JsonNode d2 = postJson(root, "/authz/check", Map.of("userId", second.id, "permission", "x:y:z")).data();
        assertFalse(d2.path("allowed").asBoolean());
        assertEquals("SUBJECT_DISABLED", d2.path("reason").asText());
    }

    // ------------------------------------------------------------------ 下一次访问生效

    @Test
    void t12_revokedRoleIsDeniedOnTheNextRequest() throws Exception {
        User u = user("u1", org.aFinance());
        UserRole ur = grant(u, org.docViewer(), org.aFinance());
        String token = login("u1");
        JsonNode first = check(token, "oa:doc:draft:view", org.aFinance().id);
        assertTrue(first.path("allowed").asBoolean());
        assertTrue(check(token, "oa:doc:draft:view", org.aFinance().id).path("cacheHit").asBoolean());

        String root = login("root");
        assertEquals(200, deleteReq(root, "/users/" + u.id + "/roles/" + ur.id).status());
        assertFalse(check(token, "oa:doc:draft:view", org.aFinance().id).path("allowed").asBoolean());
    }

    @Test
    void t12b_revocationDoesNotDependOnRedis() throws Exception {
        User u = user("u1", org.aFinance());
        UserRole ur = grant(u, org.docViewer(), org.aFinance());
        String token = login("u1");
        check(token, "oa:doc:draft:view", org.aFinance().id);
        assertTrue(check(token, "oa:doc:draft:view", org.aFinance().id).path("cacheHit").asBoolean());
        long before = jdbc.queryForObject("SELECT version FROM sys_grant_version WHERE id = 1", Long.class);
        String oldKey = "rbac:grants:v" + before + ":u" + u.id;
        assertTrue(redis.hasKey(oldKey));

        assertEquals(200, deleteReq(login("root"), "/users/" + u.id + "/roles/" + ur.id).status());

        // 回收没有碰 Redis：旧键原样留着（Redis 此刻宕机也一样），版本号随回收一起在 MySQL 里提交
        assertTrue(redis.hasKey(oldKey));
        assertEquals(before + 1, jdbc.queryForObject("SELECT version FROM sys_grant_version WHERE id = 1", Long.class));
        JsonNode d = check(token, "oa:doc:draft:view", org.aFinance().id);
        assertFalse(d.path("allowed").asBoolean());
        assertFalse(d.path("cacheHit").asBoolean());
        assertEquals("MISSING_PERMISSION", d.path("reason").asText());

        // 被拒绝的修改不递增版本号（随事务回滚），缓存照常命中
        User admin = adminOf(org.road());
        assertEquals(403, postJson(login(admin.username), "/users/" + u.id + "/roles",
                Map.of("roleId", org.docViewer().id)).status());
        assertEquals(before + 1, jdbc.queryForObject("SELECT version FROM sys_grant_version WHERE id = 1", Long.class));
    }

    @Test
    void t13_changingRolePermissionsAffectsAllHoldersOnTheNextRequest() throws Exception {
        User u1 = user("u1", org.aFinance());
        User u2 = user("u2", org.road());
        grant(u1, org.docApprover(), org.aFinance());
        grant(u2, org.docApprover(), org.road());
        String t1 = login("u1");
        String t2 = login("u2");
        assertTrue(check(t1, "oa:doc:draft:approve", null).path("allowed").asBoolean());
        assertTrue(check(t2, "oa:doc:draft:approve", null).path("allowed").asBoolean());

        String root = login("root");
        assertEquals(200, deleteReq(root, "/roles/" + org.docApprover().id + "/permissions/"
                + org.docApprove().id).status());
        assertFalse(check(t1, "oa:doc:draft:approve", null).path("allowed").asBoolean());
        assertFalse(check(t2, "oa:doc:draft:approve", null).path("allowed").asBoolean());

        // 停用角色同样立即生效
        assertTrue(check(t1, "oa:doc:draft:view", null).path("allowed").asBoolean());
        assertEquals(200, patchJson(root, "/roles/" + org.docApprover().id, Map.of("status", "DISABLED")).status());
        assertFalse(check(t1, "oa:doc:draft:view", null).path("allowed").asBoolean());
    }

    @Test
    void t14_removingAnAdminDoesNotCascadeToWhatTheyGranted() throws Exception {
        User admin = adminOf(org.road());
        UserRole adminRole = userRoleMapper.findAll().stream()
                .filter(x -> x.userId.equals(admin.id)).findFirst().orElseThrow();
        grant(admin, org.docViewer(), org.road());
        User staff = user("staff", org.roadFinance());
        String adminToken = login(admin.username);
        assertEquals(200, postJson(adminToken, "/users/" + staff.id + "/roles",
                Map.of("roleId", org.docViewer().id)).status());

        String root = login("root");
        assertEquals(200, deleteReq(root, "/users/" + admin.id + "/roles/" + adminRole.id).status());

        // 管理能力立即失效
        assertEquals(403, getJson(adminToken, "/users").status());
        // 他授出去的指派保留
        String staffToken = login("staff");
        assertTrue(check(staffToken, "oa:doc:draft:view", org.roadFinance().id).path("allowed").asBoolean());
        assertEquals(1, getJson(root, "/users/" + staff.id + "/roles").data().size());
    }

    @Test
    void t15_duplicateAssignmentCreatesNoSecondRecord() throws Exception {
        User u = user("u1", org.aFinance());
        String root = login("root");
        Map<String, Object> req = Map.of("roleId", org.docViewer().id);
        assertEquals(200, postJson(root, "/users/" + u.id + "/roles", req).status());
        Resp again = postJson(root, "/users/" + u.id + "/roles", req);
        assertEquals(409, again.status());
        assertEquals(1, userRoleMapper.countByRoleId(org.docViewer().id));
        // 同一角色、不同适用部门是另一条指派（多个部门的权限）
        assertEquals(200, postJson(root, "/users/" + u.id + "/roles",
                Map.of("roleId", org.docViewer().id, "scopeDepartmentId", org.road().id)).status());
        assertEquals(2, userRoleMapper.countByRoleId(org.docViewer().id));
    }

    @Test
    void t16_batchWithOutOfScopeUsersFollowsTheDeclaredMode() throws Exception {
        User admin = adminOf(org.road());
        grant(admin, org.docViewer(), org.road());
        User a1 = user("a1", org.roadFinance());
        User a2 = user("a2", org.roadProject());
        User outsider = user("x1", org.bridgeFinance());
        String token = login(admin.username);
        Map<String, Object> req = new HashMap<>();
        req.put("targetType", "USER");
        req.put("targetIds", List.of(a1.id, a2.id, outsider.id, 999999L));
        req.put("roleId", org.docViewer().id);

        // 默认 ATOMIC：整批不写
        JsonNode atomic = postJson(token, "/assignments/batch", req).data();
        assertFalse(atomic.path("committed").asBoolean());
        assertEquals(2, atomic.path("failed").asInt());
        assertEquals(0, userRoleMapper.countByRoleId(org.docViewer().id) - 1);   // 只有管理员自己那条

        // PARTIAL：写通过的两人，失败逐人给原因
        req.put("mode", "PARTIAL");
        JsonNode partial = postJson(token, "/assignments/batch", req).data();
        assertTrue(partial.path("committed").asBoolean());
        assertEquals(2, partial.path("succeeded").asInt());
        assertEquals(2, partial.path("failed").asInt());
        List<String> reasons = List.of(partial.path("failures").get(0).path("reason").asText(),
                partial.path("failures").get(1).path("reason").asText());
        assertTrue(reasons.contains("OUT_OF_MANAGE_SCOPE"));
        assertTrue(reasons.contains("NOT_FOUND"));

        // 再来一次：已持有的计入 duplicated
        JsonNode again = postJson(token, "/assignments/batch", req).data();
        assertEquals(0, again.path("succeeded").asInt());
        assertEquals(2, again.path("duplicated").asInt());
    }

    // ------------------------------------------------------------------ 登出、菜单、多公司、解释

    @Test
    void t17_tokenIsRejectedAfterLogout() throws Exception {
        user("u1", org.aFinance());
        String token = login("u1");
        assertEquals(200, getJson(token, "/auth/me").status());
        assertEquals(200, postJson(token, "/auth/logout", Map.of()).status());
        assertEquals(401, getJson(token, "/auth/me").status());
        // 重新登录得到新令牌，可以用
        assertEquals(200, getJson(login("u1"), "/auth/me").status());
    }

    @Test
    void t18_hiddenMenuDoesNotMeanTheApiIsOpen() throws Exception {
        user("u1", org.aFinance());
        String token = login("u1");
        assertEquals(0, getJson(token, "/menus/me").data().size());
        assertEquals(403, getJson(token, "/audit-logs").status());
        assertEquals(403, getJson(token, "/roles").status());

        // 审计员：只看到 Dashboard、审计日志、热力图
        User auditor = user("aud", org.aRoot());
        grant(auditor, roleMapper.findByRoleCode("AUDITOR").orElseThrow(), org.aRoot());
        JsonNode menus = getJson(login("aud"), "/menus/me").data();
        List<String> names = new java.util.ArrayList<>();
        menus.forEach(m -> {
            names.add(m.path("menuName").asText());
            m.path("children").forEach(c -> names.add(c.path("menuName").asText()));
        });
        assertTrue(names.containsAll(List.of("Dashboard", "系统管理", "审计日志", "权限使用热力图")), names.toString());
        assertFalse(names.contains("用户管理"));
    }

    @Test
    void t19_sameNamedDepartmentsInTwoCompaniesStaySeparate() throws Exception {
        User u = user("u1", org.aRoot());
        grant(u, org.docApprover(), org.aRoot());
        String token = login("u1");
        // 公司 A 的根部门覆盖 A 的「财务部」，覆盖不到 B 的同名「财务部」
        assertTrue(check(token, "oa:doc:draft:approve", org.aFinance().id).path("allowed").asBoolean());
        assertFalse(check(token, "oa:doc:draft:approve", org.bFinance().id).path("allowed").asBoolean());

        // 跨公司指派被拒绝
        User bUser = user("b1", org.bFinance());
        String root = login("root");
        Resp r = postJson(root, "/users/" + bUser.id + "/roles",
                Map.of("roleId", org.docViewer().id, "scopeDepartmentId", org.aFinance().id));
        assertEquals(400, r.status());

        // 公司 A 的管理员给公司 B 的员工授权：超出管理范围
        User admin = adminOf(org.aRoot());
        String adminToken = login(admin.username);
        Resp cross = postJson(adminToken, "/users/" + bUser.id + "/roles", Map.of("roleId", org.docViewer().id));
        assertEquals(403, cross.status());
        assertEquals(40302, cross.code());

        // 管理员只能看到本公司的部门树
        JsonNode companies = getJson(adminToken, "/companies").data();
        assertEquals(1, companies.size());
        assertEquals("SZ", companies.get(0).path("companyCode").asText());
        assertEquals(403, getJson(adminToken, "/users/" + bUser.id).status());
    }

    @Test
    void t20_explainAndCheckAlwaysAgree() throws Exception {
        User u = user("u1", org.road());
        grant(u, org.docApprover(), org.road());
        User disabled = user("u2", org.road());
        disabled.status = User.DISABLED;
        userMapper.save(disabled);
        String root = login("root");
        Object[][] cases = {
                {u.id, "oa:doc:draft:approve", org.roadFinance().id},
                {u.id, "oa:doc:draft:approve", org.bridgeFinance().id},
                {u.id, "oa:doc:draft:export", null},
                {u.id, "oa:doc:draft:view", 987654L},
                {disabled.id, "oa:doc:draft:view", null},
                {1L, "anything:x:y", null},
                {424242L, "oa:doc:draft:view", null},
        };
        for (Object[] c : cases) {
            Map<String, Object> req = new HashMap<>();
            req.put("userId", c[0]);
            req.put("permission", c[1]);
            req.put("targetDeptId", c[2]);
            JsonNode check = postJson(root, "/authz/check", req).data();
            JsonNode explain = postJson(root, "/authz/explain", req).data();
            assertEquals(check.path("allowed").asBoolean(), explain.path("allowed").asBoolean(), req.toString());
            assertEquals(check.path("reason").asText(), explain.path("reason").asText(), req.toString());
            assertTrue(explain.path("trace").size() > 0);
            assertTrue(check.path("trace").isNull());
        }
    }

    @Test
    void t20b_ordinaryUserCannotProbeOthersThroughAuthzApis() throws Exception {
        User u = user("u1", org.aFinance());
        User other = user("u2", org.aFinance());
        String token = login("u1");
        assertEquals(403, postJson(token, "/authz/check",
                Map.of("userId", other.id, "permission", "oa:doc:draft:view")).status());
        assertEquals(403, postJson(token, "/authz/explain",
                Map.of("username", "u2", "permission", "oa:doc:draft:view")).status());
        // 查自己可以
        assertEquals(200, postJson(token, "/authz/explain",
                Map.of("userId", u.id, "permission", "oa:doc:draft:view")).status());
    }

    @Test
    void t22_anotherReplicaSeesTheRevocationOnItsNextRequest() throws Exception {
        User u = user("u1", org.aFinance());
        UserRole ur = grant(u, org.docViewer(), org.aFinance());
        String token = login("u1");

        // 第二个副本：同一份代码、同一个 MySQL 与 Redis 的另一个进程内上下文
        try (ConfigurableApplicationContext replica = new SpringApplicationBuilder(RbacStructuredApplication.class)
                .profiles("test")
                .properties("server.port=0", "spring.flyway.enabled=false")
                .run()) {
            int port = Integer.parseInt(replica.getEnvironment().getProperty("local.server.port"));
            RestTemplate http = new RestTemplate();
            http.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
                @Override
                public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                    return false;
                }
            });
            String url = "http://localhost:" + port + "/api/v1/authz/check";
            HttpHeaders h = new HttpHeaders();
            h.setBearerAuth(token);
            h.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> req = new HttpEntity<>(json.writeValueAsString(
                    Map.of("permission", "oa:doc:draft:view", "targetDeptId", org.aFinance().id)), h);

            // 副本先放行并把授权数据缓存起来
            ResponseEntity<String> r1 = http.postForEntity(url, req, String.class);
            assertTrue(json.readTree(r1.getBody()).path("data").path("allowed").asBoolean());
            ResponseEntity<String> r2 = http.postForEntity(url, req, String.class);
            assertTrue(json.readTree(r2.getBody()).path("data").path("cacheHit").asBoolean());

            // 在本实例上撤销
            assertEquals(200, deleteReq(login("root"), "/users/" + u.id + "/roles/" + ur.id).status());

            // 副本的下一次请求就拒绝
            ResponseEntity<String> r3 = http.postForEntity(url, req, String.class);
            assertFalse(json.readTree(r3.getBody()).path("data").path("allowed").asBoolean());
        }
    }

    // ------------------------------------------------------------------ 辅助

    /** 一个在 scope 部门持有权限管理员角色的用户。 */
    private User adminOf(com.xmu.rbac.structured.entity.Department scope) {
        User admin = user("admin_" + scope.id, scope);
        grant(admin, org.rbacAdmin(), scope);
        return admin;
    }

    private int loginStatus(String username, String password) throws Exception {
        return postJson(null, "/auth/login", Map.of("username", username, "password", password)).status();
    }
}
