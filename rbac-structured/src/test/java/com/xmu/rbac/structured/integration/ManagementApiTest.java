package com.xmu.rbac.structured.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 管理接口的成功、失败、权限不足、对象不存在、重复等分支。 */
class ManagementApiTest extends IntegrationTestBase {

    // ------------------------------------------------------------------ 登录与口令

    @Test
    void firstLoginMustChangePasswordAndOldTokensDie() throws Exception {
        User u = user("u1", org.aFinance());
        u.mustChangePwd = true;
        userMapper.save(u);
        String token = login("u1");
        assertEquals(403, getJson(token, "/menus/me").status());
        assertEquals(200, getJson(token, "/auth/me").status());
        // 新口令不合策略、旧口令错误
        assertEquals(400, postJson(token, "/auth/password", Map.of("oldPassword", PASSWORD, "newPassword", "short")).status());
        assertEquals(400, postJson(token, "/auth/password", Map.of("oldPassword", "bad", "newPassword", "N3w-Passw0rd")).status());
        assertEquals(400, postJson(token, "/auth/password", Map.of("oldPassword", PASSWORD, "newPassword", PASSWORD)).status());
        assertEquals(200, postJson(token, "/auth/password", Map.of("oldPassword", PASSWORD, "newPassword", "N3w-Passw0rd")).status());
        // 改口令前签发的令牌全部作废
        assertEquals(401, getJson(token, "/auth/me").status());
        String fresh = login("u1", "N3w-Passw0rd");
        assertEquals(200, getJson(fresh, "/menus/me").status());
    }

    @Test
    void fiveFailuresLockTheAccountEvenWhenConcurrent() throws Exception {
        user("u1", org.aFinance());
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<Integer>> fs = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                fs.add(pool.submit(() -> postJson(null, "/auth/login",
                        Map.of("username", "u1", "password", "wrong")).status()));
            }
            for (Future<Integer> f : fs) {
                assertEquals(401, f.get());
            }
        } finally {
            pool.shutdown();
        }
        // 5 次并发失败一次不丢，账号已锁定：正确口令也进不去
        Resp locked = postJson(null, "/auth/login", Map.of("username", "u1", "password", PASSWORD));
        assertEquals(401, locked.status());
        assertEquals(40103, locked.code());
        // 锁定到期后可以登录，计数清零
        User u = userMapper.findByUsername("u1").orElseThrow();
        u.lockedUntil = LocalDateTime.now().minusSeconds(1);
        userMapper.save(u);
        login("u1");
        assertEquals(0, userMapper.findByUsername("u1").orElseThrow().loginFailCount);
    }

    @Test
    void invalidTokensAreRejected() throws Exception {
        assertEquals(401, getJson(null, "/auth/me").status());
        assertEquals(401, getJson("not-a-jwt", "/auth/me").status());
        assertEquals(401, postJson(null, "/auth/login", Map.of("username", "", "password", "")).status());
        // 被删除的用户手里的令牌下一次请求就失效
        User u = user("u1", org.aFinance());
        String token = login("u1");
        u.status = User.DELETED;
        userMapper.save(u);
        assertEquals(401, getJson(token, "/auth/me").status());
    }

    // ------------------------------------------------------------------ 用户管理

    @Test
    void userCrudWithinManagementScope() throws Exception {
        User admin = user("admin", org.road());
        grant(admin, org.rbacAdmin(), org.road());
        String token = login("admin");

        Map<String, Object> req = new HashMap<>();
        req.put("username", "new1");
        req.put("realName", "新员工");
        req.put("departmentId", org.roadFinance().id);
        req.put("employeeNo", "E001");
        req.put("phone", "13900000000");
        Resp created = postJson(token, "/users", req);
        assertEquals(200, created.status(), created.toString());
        long id = created.data().path("id").asLong();
        assertTrue(created.data().path("mustChangePwd").asBoolean());

        // 重复用户名、重复工号、缺工号、管理范围外、类型不对
        assertEquals(409, postJson(token, "/users", req).status());
        req.put("username", "new2");
        assertEquals(409, postJson(token, "/users", req).status());
        req.remove("employeeNo");
        assertEquals(400, postJson(token, "/users", req).status());
        req.put("employeeNo", "E002");
        req.put("departmentId", org.bridgeFinance().id);
        assertEquals(403, postJson(token, "/users", req).status());
        req.put("departmentId", org.roadFinance().id);
        req.put("userType", "ALIEN");
        assertEquals(400, postJson(token, "/users", req).status());
        req.put("userType", "EMPLOYEE");
        req.put("password", "weak");
        assertEquals(400, postJson(token, "/users", req).status());
        req.put("password", PASSWORD);
        assertEquals(200, postJson(token, "/users", req).status());
        assertEquals(400, postJson(token, "/users", Map.of("username", "x")).status());

        // 列表只含管理范围内的人；关键词、部门、状态过滤
        user("outsider", org.bridgeFinance());
        JsonNode list = getJson(token, "/users?size=50").data();
        assertEquals(3, list.path("total").asInt());   // admin、new1、new2
        assertEquals(1, getJson(token, "/users?keyword=new1").data().path("total").asInt());
        assertEquals(2, getJson(token, "/users?departmentId=" + org.roadFinance().id + "&includeDescendants=false")
                .data().path("total").asInt());
        assertEquals(3, getJson(token, "/users?departmentId=" + org.road().id).data().path("total").asInt());
        assertEquals(0, getJson(token, "/users?userType=TEMPORARY").data().path("total").asInt());

        // 修改：调到范围内的部门可以，调到范围外、跨公司不行
        assertEquals(200, patchJson(token, "/users/" + id, Map.of("realName", "改名", "position", "会计",
                "phone", "13911111111", "departmentId", org.roadProject().id)).status());
        assertEquals(403, patchJson(token, "/users/" + id, Map.of("departmentId", org.bridge().id)).status());
        assertEquals(400, patchJson(token, "/users/" + id, Map.of("departmentId", org.bFinance().id)).status());
        assertEquals(400, patchJson(token, "/users/" + id, Map.of("realName", " ")).status());
        assertEquals(404, patchJson(token, "/users/999999", Map.of("realName", "x")).status());

        // 详情
        JsonNode detail = getJson(token, "/users/" + id).data();
        assertEquals("改名", detail.path("user").path("realName").asText());
        assertEquals("道路分公司-第一项目部", detail.path("user").path("departmentName").asText());

        // 停用、启用、删除
        assertEquals(200, patchJson(token, "/users/" + id + "/status", Map.of("status", "DISABLED", "reason", "离岗")).status());
        assertEquals(1, getJson(token, "/users?status=DISABLED").data().path("total").asInt());
        assertEquals(200, patchJson(token, "/users/" + id + "/status", Map.of("status", "ACTIVE")).status());
        assertEquals(400, patchJson(token, "/users/" + id + "/status", Map.of("status", "DELETED")).status());
        assertEquals(409, patchJson(token, "/users/" + admin.id + "/status", Map.of("status", "DISABLED")).status());
        assertEquals(200, deleteReq(token, "/users/" + id + "?reason=离职").status());
        assertEquals(409, deleteReq(token, "/users/" + id).status());
        assertEquals(409, patchJson(token, "/users/" + id, Map.of("realName", "x")).status());
        assertEquals(2, getJson(token, "/users").data().path("total").asInt());
        // 已删除用户的用户名不能复用
        req.put("username", "new1");
        req.put("employeeNo", "E009");
        assertEquals(409, postJson(token, "/users", req).status());
    }

    @Test
    void temporaryUserCannotGetAPhoneLater() throws Exception {
        User t = user("t1", org.aFinance());
        t.userType = User.TEMPORARY;
        t.employeeNo = null;
        userMapper.save(t);
        String root = login("root");
        assertEquals(400, patchJson(root, "/users/" + t.id, Map.of("phone", "13900000000")).status());
        assertEquals(200, patchJson(root, "/users/" + t.id,
                Map.of("expiresAt", LocalDateTime.now().plusDays(3).toString())).status());
    }

    @Test
    void internalAccountsUseTheReservedIdRange() throws Exception {
        String root = login("root");
        Map<String, Object> emp = new HashMap<>(Map.of("username", "e1", "realName", "员工", "employeeNo", "E1",
                "departmentId", org.aFinance().id, "password", PASSWORD));
        long empId = postJson(root, "/users", emp).data().path("id").asLong();
        assertTrue(empId >= User.INTERNAL_ID_LIMIT, "员工编号 " + empId);

        Map<String, Object> sys = new HashMap<>(Map.of("username", "oa-system", "realName", "OA 系统",
                "userType", "SYSTEM", "departmentId", org.aRoot().id, "password", PASSWORD));
        Resp first = postJson(root, "/users", sys);
        assertEquals(200, first.status(), first.toString());
        assertEquals(2, first.data().path("id").asLong());   // root 是 1
        assertEquals("SYSTEM", first.data().path("userType").asText());
        sys.put("username", "hr-system");
        assertEquals(3, postJson(root, "/users", sys).data().path("id").asLong());
        // 内部账号照常登录、照常审计
        assertEquals(200, getJson(login("oa-system"), "/auth/me").status());
        JsonNode logs = getJson(root, "/audit-logs?eventType=USER_CREATE&size=50").data();
        assertTrue(logs.toString().contains("\"targetId\":\"2\""), logs.toString());

        // 员工继续在 10000 段自增，不会落进内部段
        emp.put("username", "e2");
        emp.put("employeeNo", "E2");
        assertEquals(empId + 1, postJson(root, "/users", emp).data().path("id").asLong());
        // 内部段用完时拒绝，而不是越界占用员工编号
        jdbc.update("UPDATE sys_user SET id = ? WHERE username = 'hr-system'", User.INTERNAL_ID_LIMIT - 1);
        sys.put("username", "fin-system");
        assertEquals(409, postJson(root, "/users", sys).status());
    }

    @Test
    void batchStatusReportsPerUserResults() throws Exception {
        User admin = user("admin", org.road());
        grant(admin, org.rbacAdmin(), org.road());
        User a = user("a", org.roadFinance());
        User b = user("b", org.roadProject());
        User out = user("out", org.bridge());
        String token = login("admin");
        JsonNode r = postJson(token, "/users/batch-status",
                Map.of("userIds", List.of(a.id, b.id, out.id, 424242L, admin.id), "status", "DISABLED")).data();
        assertEquals(2, r.path("succeeded").asInt());
        assertEquals(3, r.path("failed").asInt());
        assertEquals(User.DISABLED, userMapper.findById(a.id).orElseThrow().status);
        assertEquals(User.ACTIVE, userMapper.findById(out.id).orElseThrow().status);
        assertEquals(400, postJson(token, "/users/batch-status", Map.of("userIds", List.of(), "status", "X")).status());
        JsonNode del = postJson(token, "/users/batch-status", Map.of("userIds", List.of(a.id), "status", "DELETED")).data();
        assertEquals(1, del.path("succeeded").asInt());
    }

    @Test
    void onlySuperAdminMayTouchASuperAdmin() throws Exception {
        User admin = user("admin", org.aRoot());
        grant(admin, org.rbacAdmin(), org.aRoot());
        User boss = user("boss", org.aFinance());
        grant(boss, org.superAdmin(), org.aRoot());
        String token = login("admin");
        assertEquals(403, patchJson(token, "/users/" + boss.id + "/status", Map.of("status", "DISABLED")).status());
        assertEquals(403, patchJson(token, "/users/" + boss.id, Map.of("realName", "x")).status());
        UserRole bossRole = userRoleMapper.findAll().stream().filter(x -> x.userId.equals(boss.id)).findFirst().orElseThrow();
        assertEquals(403, deleteReq(token, "/users/" + boss.id + "/roles/" + bossRole.id).status());
        // 超级管理员可以；但不能回收最后一个
        String root = login("root");
        assertEquals(200, deleteReq(root, "/users/" + boss.id + "/roles/" + bossRole.id).status());
        UserRole rootRole = userRoleMapper.findAll().stream().filter(x -> x.userId.equals(1L)).findFirst().orElseThrow();
        assertEquals(409, deleteReq(root, "/users/1/roles/" + rootRole.id).status());
        assertEquals(404, deleteReq(root, "/users/1/roles/999999").status());
    }

    @Test
    void userDetailShowsRolesAndEffectivePermissions() throws Exception {
        User u = user("u1", org.road());
        grant(u, org.docApprover(), org.road());
        grant(u, org.docViewer(), org.aFinance());
        String token = login("u1");
        JsonNode me = getJson(token, "/auth/me").data();
        assertEquals(2, me.path("roles").size());
        assertEquals(3, me.path("permissions").size());
        assertFalse(me.path("superAdmin").asBoolean());
        assertEquals(3, getJson(token, "/users/" + u.id + "/permissions").data().size());
        assertEquals(2, getJson(token, "/users/" + u.id + "/roles").data().size());
        User other = user("u2", org.road());
        assertEquals(403, getJson(token, "/users/" + other.id).status());
        assertEquals(403, getJson(token, "/users/" + other.id + "/roles").status());
        assertEquals(404, getJson(login("root"), "/users/999999").status());
    }

    // ------------------------------------------------------------------ 角色管理

    @Test
    void roleLifecycleAndPermissions() throws Exception {
        String root = login("root");
        Resp created = postJson(root, "/roles", Map.of("roleCode", "FIN_CLERK", "roleName", "财务专员",
                "description", "d", "companyId", org.a().id));
        assertEquals(200, created.status(), created.toString());
        long roleId = created.data().path("id").asLong();
        assertEquals(409, postJson(root, "/roles", Map.of("roleCode", "FIN_CLERK", "roleName", "x")).status());
        assertEquals(400, postJson(root, "/roles", Map.of("roleCode", "bad code", "roleName", "x")).status());

        // 授权、详情、回收
        Resp granted = postJson(root, "/roles/" + roleId + "/permissions",
                Map.of("permissionIds", List.of(org.docView().id, org.docApprove().id)));
        assertEquals(200, granted.status());
        assertEquals(2, granted.data().path("permissions").size());
        assertEquals(200, postJson(root, "/roles/" + roleId + "/permissions",
                Map.of("permissionIds", List.of(org.docView().id))).status());   // 已有的跳过
        assertEquals(404, postJson(root, "/roles/" + roleId + "/permissions",
                Map.of("permissionIds", List.of(999999L))).status());
        assertEquals(400, postJson(root, "/roles/" + roleId + "/permissions", Map.of("permissionIds", List.of())).status());
        assertEquals(409, postJson(root, "/roles/" + org.superAdmin().id + "/permissions",
                Map.of("permissionIds", List.of(org.docView().id))).status());
        assertEquals(200, deleteReq(root, "/roles/" + roleId + "/permissions/" + org.docApprove().id).status());
        assertEquals(404, deleteReq(root, "/roles/" + roleId + "/permissions/" + org.docApprove().id).status());

        // 列表：用户数、权限数
        User u = user("u1", org.aFinance());
        grant(u, roleMapper.findById(roleId).orElseThrow(), org.aFinance());
        JsonNode roles = getJson(root, "/roles?keyword=FIN").data();
        assertEquals(1, roles.size());
        assertEquals(1, roles.get(0).path("userCount").asInt());
        assertEquals(1, roles.get(0).path("permissionCount").asInt());
        JsonNode holders = getJson(root, "/roles/" + roleId + "/users").data().path("items");
        assertEquals(1, holders.size());
        assertEquals("u1", holders.get(0).path("username").asText());
        assertEquals("测试u1", holders.get(0).path("userRealName").asText());
        assertEquals(0, getJson(root, "/roles?status=DISABLED").data().size());

        // 修改、停用；有人持有时不能删
        assertEquals(200, patchJson(root, "/roles/" + roleId, Map.of("roleName", "财务员", "description", "x")).status());
        assertEquals(400, patchJson(root, "/roles/" + roleId, Map.of("status", "WHATEVER")).status());
        assertEquals(409, patchJson(root, "/roles/" + org.superAdmin().id, Map.of("status", "DISABLED")).status());
        assertEquals(409, deleteReq(root, "/roles/" + roleId).status());
        assertEquals(409, deleteReq(root, "/roles/" + org.rbacAdmin().id).status());   // 内置
        userRoleMapper.deleteAll(userRoleMapper.findAll().stream().filter(x -> x.userId.equals(u.id)).toList());
        assertEquals(200, deleteReq(root, "/roles/" + roleId).status());
        assertEquals(404, getJson(root, "/roles/" + roleId).status());
    }

    @Test
    void roleHoldersAreLimitedToManagementScopeWithConsistentTotals() throws Exception {
        User admin = user("admin", org.road());
        grant(admin, org.rbacAdmin(), org.road());
        for (int i = 0; i < 3; i++) {
            grant(user("r" + i, org.roadFinance()), org.docViewer(), org.roadFinance());
            grant(user("b" + i, org.bridgeFinance()), org.docViewer(), org.bridgeFinance());
        }
        JsonNode page0 = getJson(login("admin"), "/roles/" + org.docViewer().id + "/users?size=2").data();
        assertEquals(3, page0.path("total").asInt());   // 只算道路分公司的 3 人
        assertEquals(2, page0.path("items").size());
        JsonNode page1 = getJson(login("admin"), "/roles/" + org.docViewer().id + "/users?size=2&page=1").data();
        assertEquals(1, page1.path("items").size());
        assertEquals(6, getJson(login("root"), "/roles/" + org.docViewer().id + "/users").data().path("total").asInt());
    }

    @Test
    void companyAdminManagesOnlyCompanyRoles() throws Exception {
        User admin = user("admin", org.aRoot());
        grant(admin, org.rbacAdmin(), org.aRoot());
        grant(admin, org.docViewer(), org.aRoot());
        String token = login("admin");
        // 全局角色只有超级管理员能建、能改
        assertEquals(403, postJson(token, "/roles", Map.of("roleCode", "G1", "roleName", "全局")).status());
        assertEquals(403, patchJson(token, "/roles/" + org.docViewer().id, Map.of("roleName", "x")).status());
        // 本公司角色可以
        Resp r = postJson(token, "/roles", Map.of("roleCode", "A_ROLE", "roleName", "A 公司角色", "companyId", org.a().id));
        assertEquals(200, r.status());
        long id = r.data().path("id").asLong();
        assertEquals(200, postJson(token, "/roles/" + id + "/permissions",
                Map.of("permissionIds", List.of(org.docView().id))).status());
        // 不能授出自己没有的权限
        Resp esc = postJson(token, "/roles/" + id + "/permissions", Map.of("permissionIds", List.of(org.docApprove().id)));
        assertEquals(403, esc.status());
        assertEquals(40303, esc.code());
        // 别家公司的角色看不到、改不了
        var bRole = role("B_ROLE", "B 公司角色", org.b().id);
        assertEquals(403, postJson(token, "/roles", Map.of("roleCode", "B2", "roleName", "x", "companyId", org.b().id)).status());
        assertEquals(403, getJson(token, "/roles/" + bRole.id).status());
        assertFalse(getJson(token, "/roles").data().toString().contains("B_ROLE"));
        // 公司内角色不能指派到别的公司
        User bUser = user("b1", org.bFinance());
        assertEquals(400, postJson(login("root"), "/users/" + bUser.id + "/roles",
                Map.of("roleId", id)).status());
    }

    /**
     * 跨公司授权（docs/12-design-log.md R-20）：用户只属于一家公司，但可以持有别家公司部门的指派。
     * 能否授出照常看管理范围（用户所属部门与适用部门都要在范围内）与越权检查，不再看两边是否同公司。
     */
    @Test
    void assignmentsMayCrossCompaniesWithinManagementScope() throws Exception {
        User aUser = user("a1", org.aFinance());
        User bUser = user("b1", org.bFinance());
        String root = login("root");
        var aRole = role("A_ROLE", "A 公司角色", org.a().id, org.docView());

        // 同一个人同时持有 A、B 两家公司的指派
        Resp inA = postJson(root, "/users/" + bUser.id + "/roles",
                Map.of("roleId", aRole.id, "scopeDepartmentId", org.road().id, "reason", "支援 A 公司"));
        assertEquals(200, inA.status(), inA.body().toString());
        assertEquals(org.a().id, inA.data().path("scopeCompanyId").asLong());
        assertEquals(200, postJson(root, "/users/" + bUser.id + "/roles", Map.of("roleId", org.docApprover().id)).status());
        JsonNode held = getJson(root, "/users/" + bUser.id + "/roles").data();
        assertEquals(2, held.size(), held.toString());
        String bToken = login("b1");
        assertTrue(check(bToken, "oa:doc:draft:view", org.roadProject().id).path("allowed").asBoolean());
        assertFalse(check(bToken, "oa:doc:draft:approve", org.roadProject().id).path("allowed").asBoolean());
        assertTrue(check(bToken, "oa:doc:draft:approve", org.bFinance().id).path("allowed").asBoolean());
        assertFalse(check(bToken, "oa:doc:draft:view", org.aFinance().id).path("allowed").asBoolean());
        String ok = getJson(root, "/audit-logs?eventType=ROLE_ASSIGN&result=SUCCESS").data().toString();
        assertTrue(ok.contains("role=A_ROLE scope=道路分公司"), ok);
        // 公司内角色仍只能落在本公司的部门
        assertEquals(400, postJson(root, "/users/" + aUser.id + "/roles",
                Map.of("roleId", aRole.id, "scopeDepartmentId", org.bFinance().id)).status());

        // 只管 A 公司的管理员：B 公司的员工不在范围内，即使适用部门在 A 也不行
        User aAdmin = user("a_admin", org.aRoot());
        grant(aAdmin, org.rbacAdmin(), org.aRoot());
        grant(aAdmin, org.docViewer(), org.aRoot());
        String aToken = login("a_admin");
        Resp denied = postJson(aToken, "/users/" + bUser.id + "/roles",
                Map.of("roleId", org.docViewer().id, "scopeDepartmentId", org.aFinance().id));
        assertEquals(403, denied.status());
        assertEquals(40302, denied.code());

        // 同时管两家公司的管理员（A 公司的人，另持 B 公司根部门的管理指派）可以跨公司授权、回收
        User groupAdmin = user("group_admin", org.aRoot());
        grant(groupAdmin, org.rbacAdmin(), org.aRoot());
        grant(groupAdmin, org.docViewer(), org.aRoot());
        assertEquals(200, postJson(root, "/users/" + groupAdmin.id + "/roles",
                Map.of("roleId", org.rbacAdmin().id, "scopeDepartmentId", org.bRoot().id)).status());
        String gToken = login("group_admin");
        assertEquals(2, getJson(gToken, "/companies").data().size());
        Resp granted = postJson(gToken, "/users/" + bUser.id + "/roles",
                Map.of("roleId", org.docViewer().id, "scopeDepartmentId", org.aFinance().id));
        assertEquals(200, granted.status(), granted.body().toString());
        assertTrue(check(bToken, "oa:doc:draft:view", org.aFinance().id).path("allowed").asBoolean());
        // 越权检查照旧：他在 A 公司没有审批权限，就不能把审批角色授到 A
        Resp escalate = postJson(gToken, "/users/" + bUser.id + "/roles",
                Map.of("roleId", org.docApprover().id, "scopeDepartmentId", org.aFinance().id));
        assertEquals(403, escalate.status());
        assertEquals(40303, escalate.code());
        assertEquals(200, deleteReq(gToken, "/users/" + bUser.id + "/roles/"
                + granted.data().path("assignmentId").asLong()).status());
        assertFalse(check(bToken, "oa:doc:draft:view", org.aFinance().id).path("allowed").asBoolean());
        // 只管 A 的管理员回收不了 B 公司员工的指派
        assertEquals(403, deleteReq(aToken, "/users/" + bUser.id + "/roles/"
                + inA.data().path("assignmentId").asLong()).status());

        // 批量：按员工把两家公司的人一起授到 A 公司的部门
        JsonNode batch = postJson(root, "/assignments/batch", Map.of("targetType", "USER",
                "targetIds", List.of(aUser.id, bUser.id), "roleId", org.docViewer().id,
                "scopeDepartmentId", org.bridge().id)).data();
        assertTrue(batch.path("committed").asBoolean(), batch.toString());
        assertEquals(2, batch.path("succeeded").asInt());
        assertTrue(check(bToken, "oa:doc:draft:view", org.bridgeFinance().id).path("allowed").asBoolean());
        JsonNode back = postJson(root, "/assignments/batch-revoke", Map.of("targetType", "USER",
                "targetIds", List.of(aUser.id, bUser.id), "roleId", org.docViewer().id,
                "scopeDepartmentId", org.bridge().id)).data();
        assertEquals(2, back.path("succeeded").asInt(), back.toString());
        assertFalse(check(bToken, "oa:doc:draft:view", org.bridgeFinance().id).path("allowed").asBoolean());
    }

    @Test
    void batchAssignByCompanyAndDepartmentThenRevoke() throws Exception {
        user("a1", org.aFinance());
        user("a2", org.roadFinance());
        user("a3", org.bridgeFinance());
        User gone = user("a4", org.bridgeFinance());
        gone.status = User.DELETED;
        userMapper.save(gone);
        user("b1", org.bFinance());
        String root = login("root");

        JsonNode byCompany = postJson(root, "/assignments/batch", Map.of("targetType", "COMPANY",
                "targetIds", List.of(org.a().id), "roleId", org.docViewer().id, "reason", "初始化")).data();
        assertTrue(byCompany.path("committed").asBoolean());
        assertEquals(3, byCompany.path("succeeded").asInt());   // 已删除的不算
        assertFalse(check(login("b1"), "oa:doc:draft:view", null).path("allowed").asBoolean());

        JsonNode byDept = postJson(root, "/assignments/batch", Map.of("targetType", "DEPARTMENT",
                "targetIds", List.of(org.road().id), "roleId", org.docApprover().id,
                "scopeDepartmentId", org.road().id)).data();
        assertEquals(1, byDept.path("succeeded").asInt());
        assertTrue(check(login("a2"), "oa:doc:draft:approve", org.roadProject().id).path("allowed").asBoolean());

        JsonNode notDescend = postJson(root, "/assignments/batch", Map.of("targetType", "DEPARTMENT",
                "targetIds", List.of(org.road().id), "includeDescendants", false, "roleId", org.docViewer().id)).data();
        assertEquals(0, notDescend.path("requested").asInt());

        JsonNode revoked = postJson(root, "/assignments/batch-revoke", Map.of("targetType", "COMPANY",
                "targetIds", List.of(org.a().id), "roleId", org.docViewer().id)).data();
        assertEquals(3, revoked.path("succeeded").asInt());
        assertFalse(check(login("a1"), "oa:doc:draft:view", null).path("allowed").asBoolean());
        JsonNode again = postJson(root, "/assignments/batch-revoke", Map.of("targetType", "USER",
                "targetIds", List.of(gone.id), "roleId", org.docViewer().id)).data();
        assertEquals(1, again.path("failed").asInt());

        // 参数错误
        assertEquals(400, postJson(root, "/assignments/batch", Map.of("targetType", "PLANET",
                "targetIds", List.of(1), "roleId", org.docViewer().id)).status());
        assertEquals(400, postJson(root, "/assignments/batch", Map.of("targetType", "USER",
                "targetIds", List.of(), "roleId", org.docViewer().id)).status());
        assertEquals(400, postJson(root, "/assignments/batch", Map.of("targetType", "USER",
                "targetIds", List.of(1), "roleId", org.docViewer().id, "mode", "MAYBE")).status());
        assertEquals(404, postJson(root, "/assignments/batch", Map.of("targetType", "DEPARTMENT",
                "targetIds", List.of(999999), "roleId", org.docViewer().id)).status());
        assertEquals(404, postJson(root, "/assignments/batch", Map.of("targetType", "COMPANY",
                "targetIds", List.of(999999), "roleId", org.docViewer().id)).status());
        assertEquals(404, postJson(root, "/assignments/batch", Map.of("targetType", "USER",
                "targetIds", List.of(1), "roleId", 999999, "scopeDepartmentId", org.aRoot().id)).status());
    }

    @Test
    void outOfScopeRevokeAndDisabledRoleAreRefused() throws Exception {
        User admin = user("admin", org.road());
        grant(admin, org.rbacAdmin(), org.road());
        User out = user("out", org.bridge());
        UserRole ur = grant(out, org.docViewer(), org.bridge());
        String token = login("admin");
        assertEquals(403, deleteReq(token, "/users/" + out.id + "/roles/" + ur.id).status());
        JsonNode batch = postJson(token, "/assignments/batch-revoke", Map.of("targetType", "USER",
                "targetIds", List.of(out.id), "roleId", org.docViewer().id)).data();
        assertFalse(batch.path("committed").asBoolean());

        String root = login("root");
        patchJson(root, "/roles/" + org.docViewer().id, Map.of("status", "DISABLED"));
        User u = user("u1", org.aFinance());
        assertEquals(409, postJson(root, "/users/" + u.id + "/roles", Map.of("roleId", org.docViewer().id)).status());
        assertEquals(400, postJson(root, "/users/" + u.id + "/roles", Map.of()).status());
        assertEquals(404, postJson(root, "/users/" + u.id + "/roles", Map.of("roleId", 999999)).status());
    }

    // ------------------------------------------------------------------ 资源、权限、菜单、组织

    @Test
    void catalogResourcesActionsPermissions() throws Exception {
        String root = login("root");
        assertTrue(getJson(root, "/systems").data().size() >= 2);
        assertEquals(11, getJson(root, "/actions").data().size());
        assertEquals(12, getJson(root, "/resources?type=MENU").data().path("total").asInt()
                + getJson(root, "/resources?type=API").data().path("total").asInt());
        assertEquals(2, getJson(root, "/permissions?keyword=oa:doc").data().path("total").asInt());

        long moduleId = getJson(root, "/systems").data().get(1).path("modules").get(0).path("id").asLong();
        Resp res = postJson(root, "/resources", Map.of("moduleId", moduleId, "resourceCode", "oa:doc:receive",
                "resourceName", "收文", "actionCodes", List.of("view", "edit")));
        assertEquals(200, res.status(), res.toString());
        long resourceId = res.data().path("id").asLong();
        assertEquals(2, getJson(root, "/permissions?resourceId=" + resourceId).data().path("total").asInt());
        assertEquals(409, postJson(root, "/resources", Map.of("moduleId", moduleId, "resourceCode", "oa:doc:receive",
                "resourceName", "收文")).status());
        assertEquals(400, postJson(root, "/resources", Map.of("moduleId", moduleId, "resourceCode", "hr:x:y",
                "resourceName", "错系统")).status());
        assertEquals(400, postJson(root, "/resources", Map.of("moduleId", moduleId, "resourceCode", "BAD",
                "resourceName", "x")).status());
        assertEquals(400, postJson(root, "/resources", Map.of("moduleId", moduleId, "resourceCode", "oa:x",
                "resourceName", "x", "resourceType", "WEIRD")).status());
        assertEquals(200, postJson(root, "/permissions", Map.of("resourceId", resourceId, "actionCode", "export")).status());
        assertEquals(409, postJson(root, "/permissions", Map.of("resourceId", resourceId, "actionCode", "export")).status());
        assertEquals(404, postJson(root, "/permissions", Map.of("resourceId", resourceId, "actionCode", "fly")).status());
        assertEquals(400, postJson(root, "/permissions", Map.of("resourceId", resourceId)).status());

        // 权限管理员可以看，不能新建权限定义
        User admin = user("admin", org.aRoot());
        grant(admin, org.rbacAdmin(), org.aRoot());
        String token = login("admin");
        assertEquals(200, getJson(token, "/permissions").status());
        assertEquals(403, postJson(token, "/permissions", Map.of("resourceId", resourceId, "actionCode", "approve")).status());
    }

    @Test
    void menuManagementAndDynamicMenus() throws Exception {
        String root = login("root");
        JsonNode all = getJson(root, "/menus").data();
        assertEquals(4, all.size());
        // 超级管理员看到全部菜单
        assertEquals(4, getJson(root, "/menus/me").data().size());

        Resp created = postJson(root, "/menus", Map.of("parentId", 2, "resourceId", 6, "menuName", "登录日志",
                "routePath", "/login-logs", "sortOrder", 9));
        assertEquals(200, created.status(), created.toString());
        long id = created.data().path("id").asLong();
        assertEquals(400, postJson(root, "/menus", Map.of("menuName", "")).status());
        assertEquals(400, postJson(root, "/menus", Map.of("menuName", "x", "resourceId", 8)).status());   // API 资源
        assertEquals(404, postJson(root, "/menus", Map.of("menuName", "x", "parentId", 9999)).status());
        assertEquals(200, patchJson(root, "/menus/" + id, Map.of("parentId", 2, "resourceId", 6, "menuName", "登录记录")).status());
        assertEquals(400, patchJson(root, "/menus/2", Map.of("parentId", id, "menuName", "系统管理")).status());   // 成环
        assertEquals(409, deleteReq(root, "/menus/2").status());
        assertEquals(200, deleteReq(root, "/menus/" + id).status());
        assertEquals(404, deleteReq(root, "/menus/" + id).status());

        // 权限管理员看不到「权限使用热力图」以外缺权限的菜单：给他的角色去掉 rbac:menu:view
        User admin = user("admin", org.aRoot());
        grant(admin, org.rbacAdmin(), org.aRoot());
        long menuView = permissionMapper.findByPermissionCode("rbac:menu:view").orElseThrow().id;
        assertEquals(200, deleteReq(root, "/roles/" + org.rbacAdmin().id + "/permissions/" + menuView).status());
        String token = login("admin");
        String menus = getJson(token, "/menus/me").data().toString();
        assertFalse(menus.contains("菜单管理"));
        assertTrue(menus.contains("用户管理"));
        assertEquals(403, getJson(token, "/menus").status());
    }

    @Test
    void organizationTreeIsLimitedToScope() throws Exception {
        String root = login("root");
        assertEquals(3, getJson(root, "/companies").data().size());   // 平台、A、B
        JsonNode tree = getJson(root, "/departments/tree?companyId=" + org.a().id).data();
        assertEquals(1, tree.size());
        assertEquals(3, tree.get(0).path("children").size());
        assertEquals(404, getJson(root, "/departments/tree?companyId=999999").status());

        User admin = user("admin", org.road());
        grant(admin, org.rbacAdmin(), org.road());
        JsonNode mine = getJson(login("admin"), "/departments/tree?companyId=" + org.a().id).data();
        // 只看到通往道路分公司的祖先（总公司）与道路分公司子树
        assertEquals(1, mine.get(0).path("children").size());
        assertEquals("道路分公司", mine.get(0).path("children").get(0).path("deptName").asText());
        assertEquals(2, mine.get(0).path("children").get(0).path("children").size());
    }

    // ------------------------------------------------------------------ 审计、Dashboard、热力图

    @Test
    void auditDashboardAndHeatmapComeFromRealEvents() throws Exception {
        User u = user("u1", org.aFinance());
        grant(u, org.docApprover(), org.aFinance());
        String token = login("u1");
        check(token, "oa:doc:draft:approve", org.aFinance().id);   // 成功
        check(token, "oa:doc:draft:approve", org.bridge().id);     // 拒绝
        check(token, "oa:doc:draft:view", null);                    // 成功
        postJson(null, "/auth/login", Map.of("username", "u1", "password", "wrong"));   // 登录失败 → 异常访问

        String root = login("root");
        JsonNode d = getJson(root, "/dashboard").data();
        assertTrue(d.path("userCount").asInt() >= 2);
        assertTrue(d.path("roleCount").asInt() >= 5);
        assertTrue(d.path("permissionCount").asInt() >= 28);
        assertTrue(d.path("todayAuthzCount").asInt() >= 3);
        assertTrue(d.path("successRate").asDouble() > 0 && d.path("failureRate").asDouble() > 0);
        assertEquals(1.0, d.path("successRate").asDouble() + d.path("failureRate").asDouble(), 1e-9);
        assertTrue(d.path("p99LatencyMs").asDouble() >= d.path("p95LatencyMs").asDouble());
        assertTrue(d.path("qps").asDouble() > 0);
        assertTrue(d.path("abnormalCount").asInt() >= 1);

        JsonNode heat = getJson(root, "/permissions/heatmap").data();
        assertTrue(heat.path("systems").toString().contains("OA协同办公系统"));
        assertTrue(heat.path("cells").size() >= 2);
        assertEquals(400, getJson(root, "/permissions/heatmap?from=2026-10-02T00:00:00&to=2026-10-01T00:00:00").status());

        JsonNode logs = getJson(root, "/audit-logs?eventType=AUTH_FAILED").data();
        assertTrue(logs.path("total").asInt() >= 1);
        JsonNode one = logs.path("items").get(0);
        assertEquals("u1", one.path("actorName").asText());
        assertEquals("OUT_OF_DATA_SCOPE", one.path("reason").asText());
        assertFalse(one.path("clientIp").isNull());
        assertTrue(getJson(root, "/audit-logs?eventType=LOGIN&result=FAILED").data().path("total").asInt() >= 1);
        assertTrue(getJson(root, "/audit-logs?actor=root&from=2020-01-01T00:00:00&to=2099-01-01T00:00:00&target=root")
                .data().path("total").asInt() >= 1);
        assertEquals(403, getJson(token, "/dashboard").status());
    }

    @Test
    void abnormalAuditFilterMatchesDashboardCount() throws Exception {
        User u = user("u1", org.aFinance());
        grant(u, org.docApprover(), org.aFinance());
        String token = login("u1");
        check(token, "oa:doc:draft:approve", org.bridge().id);                           // 缺少权限：不算异常
        postJson(null, "/auth/login", Map.of("username", "u1", "password", "wrong"));   // 登录失败：异常
        postJson(null, "/auth/login", Map.of("username", "nobody", "password", "x"));   // 登录失败：异常
        u.status = User.DISABLED;
        userMapper.save(u);
        String root = login("root");
        JsonNode d = postJson(root, "/authz/check", Map.of("userId", u.id, "permission", "oa:doc:draft:view")).data();
        assertEquals("SUBJECT_DISABLED", d.path("reason").asText());                     // 停用主体鉴权：异常

        long count = getJson(root, "/dashboard").data().path("abnormalCount").asLong();
        assertEquals(3, count);
        JsonNode logs = getJson(root, "/audit-logs?abnormal=true&size=200").data();
        assertEquals(count, logs.path("total").asLong());
        for (JsonNode a : logs.path("items")) {
            boolean loginFailed = a.path("eventType").asText().equals("LOGIN") && a.path("result").asText().equals("FAILED");
            boolean invalidSubject = a.path("eventType").asText().equals("AUTH_FAILED")
                    && a.path("reason").asText().startsWith("SUBJECT_");
            assertTrue(loginFailed || invalidSubject, a.toString());
        }
        // 不带 abnormal 时照常返回全部鉴权失败，包括「缺少权限」
        assertTrue(getJson(root, "/audit-logs?eventType=AUTH_FAILED").data().path("total").asLong() >= 2);
    }

    @Test
    void deniedAndFailedManagementOperationsAreAudited() throws Exception {
        User admin = user("admin", org.road());
        grant(admin, org.rbacAdmin(), org.road());
        User outside = user("outside", org.bridgeFinance());
        String token = login("admin");
        String root = login("root");

        // 目标超出管理范围：修改、新增、看详情都在 requireInScope 一处记 ACCESS_DENIED
        assertEquals(403, patchJson(token, "/users/" + outside.id, Map.of("realName", "x")).status());
        assertEquals(403, postJson(token, "/users", Map.of("username", "n1", "realName", "n", "employeeNo", "N1",
                "departmentId", org.bridge().id)).status());
        assertEquals(403, getJson(token, "/users/" + outside.id).status());
        // 修改全局角色只有超级管理员能做
        assertEquals(403, patchJson(token, "/roles/" + org.docViewer().id, Map.of("roleName", "x")).status());
        JsonNode denied = getJson(root, "/audit-logs?eventType=ACCESS_DENIED&size=50").data();
        assertEquals(4, denied.path("total").asInt(), denied.toString());
        String deniedText = denied.toString();
        assertTrue(deniedText.contains("permission=rbac:user:edit"), deniedText);
        assertTrue(deniedText.contains("permission=rbac:user:create"), deniedText);
        assertTrue(deniedText.contains("GLOBAL_ROLE_SUPER_ADMIN_ONLY"), deniedText);
        denied.path("items").forEach(a -> assertEquals("admin", a.path("actorName").asText()));

        // 修改口令失败
        assertEquals(400, postJson(token, "/auth/password", Map.of("oldPassword", "bad", "newPassword", "N3w-Passw0rd")).status());
        assertEquals(400, postJson(token, "/auth/password", Map.of("oldPassword", PASSWORD, "newPassword", "short")).status());
        String pwd = getJson(root, "/audit-logs?eventType=PASSWORD_CHANGE&result=FAILED").data().toString();
        assertTrue(pwd.contains("CREDENTIAL_INVALID") && pwd.contains("PASSWORD_POLICY"), pwd);

        // 受保护对象：停用超级管理员角色、删除内置角色、批量回收最后一个超级管理员
        assertEquals(409, patchJson(root, "/roles/" + org.superAdmin().id, Map.of("status", "DISABLED")).status());
        assertEquals(409, deleteReq(root, "/roles/" + org.rbacAdmin().id).status());
        assertEquals(409, postJson(root, "/assignments/batch-revoke", Map.of("targetType", "USER",
                "targetIds", List.of(1), "roleId", org.superAdmin().id)).status());
        String roleLogs = getJson(root, "/audit-logs?result=DENIED&size=200").data().toString();
        for (String reason : List.of("SUPER_ADMIN_ROLE_PROTECTED", "BUILTIN_ROLE", "LAST_SUPER_ADMIN")) {
            assertTrue(roleLogs.contains(reason), reason);
        }

        // 这些都是管理操作被拦下，不是「异常访问」，不改变 Dashboard 的口径
        assertEquals(0, getJson(root, "/dashboard").data().path("abnormalCount").asLong());
    }

    @Test
    void undeclaredRoutesAreDeniedByDefault() throws Exception {
        String root = login("root");
        assertEquals(403, getJson(root, "/not/in/contract").status());
        assertEquals(400, postJson(root, "/authz/check", Map.of()).status());
        assertEquals(200, postJson(root, "/authz/check", Map.of("resource", "oa:doc:draft", "action", "view")).status());
        assertEquals(400, call(root, MockMvcRequestBuilders
                .post("/authz/check").contentType("application/json").content("{bad json")).status());
    }
}
