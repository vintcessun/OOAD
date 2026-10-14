package com.xmu.rbac.structured.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmu.rbac.structured.entity.Department;
import com.xmu.rbac.structured.entity.Permission;
import com.xmu.rbac.structured.entity.Role;
import com.xmu.rbac.structured.entity.RolePermission;
import com.xmu.rbac.structured.entity.User;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * T21：导入三份 Excel。真实数据含个人信息不能进仓库，这里按真实表格的格式造一份小样本（姓名均为虚构），
 * 覆盖真实数据里的各种情形：分公司直属人员、各分公司同名的「财务部」、合并单元格、
 * 「查看/编辑本人」、混合语言「view/新增」、空白单元格、重名负责人。
 * 用真实三份表格的全量导入结果记在 docs/03-database.md §5.4。
 */
class ExcelImportIntegrationTest extends IntegrationTestBase {

    @Test
    void t21_importsOrganizationWithCorrectDepartmentOwnership() throws Exception {
        byte[] depts = workbook("部门信息", null, new String[][]{
                {"部门名称", "部门经理", "上级部门"},
                {"总公司", "甲一", null},
                {"财务部", "乙二", "总公司"},
                {"道路工程分公司", "丙三", "总公司"},
                {"道路工程分公司-财务部", "丁四", "道路工程分公司"},
                {"道路工程分公司-第一项目经理部", "同名", "道路工程分公司"},
                {"桥梁工程分公司", "戊五", "总公司"},
                {"桥梁工程分公司-财务部", "己六", "桥梁工程分公司"},
                {"孤儿部门", "无人", "不存在的上级"},
        });
        byte[] employees = workbook("员工信息", null, new String[][]{
                {"姓名", "用户名", "分公司", "部门", "岗位", "电话号码"},
                {"甲一", "T0001", "总公司", "公司领导", "董事长", "13800000001"},       // 部门不存在 → 异常
                {"乙二", "T0002", "总公司", "财务部", "部长", "13800000002"},
                {"丙三", "T0003", "道路工程分公司", "道路工程分公司", "经理", "13800000003"},  // 分公司直属
                {"丁四", "T0004", "道路工程分公司", "财务部", "部长", "13800000004"},
                {"同名", "T0005", "道路工程分公司", "第一项目经理部", "项目经理", "13800000005"},
                {"同名", "T0006", "桥梁工程分公司", "财务部", "会计", "13800000006"},
                {"己六", "T0007", "桥梁工程分公司", "财务部", "部长", "13800000007"},
                {"庚七", "T0007", "桥梁工程分公司", "财务部", "会计", "13800000008"},  // 用户名重复 → 异常
                {"辛八", "T0009", "隧道工程分公司", "财务部", "会计", "13800000009"},  // 分公司不存在 → 异常
        });
        String root = login("root");
        Resp r = call(root, multipart("/imports/organization")
                .file(new MockMultipartFile("departments", "d.xlsx", null, depts))
                .file(new MockMultipartFile("employees", "e.xlsx", null, employees))
                .param("companyCode", "IMP")
                .param("companyName", "导入测试公司"));
        assertEquals(200, r.status(), r.toString());
        JsonNode counts = r.data().path("counts");
        assertEquals(7, counts.path("departmentsCreated").asInt());
        assertEquals(6, counts.path("usersCreated").asInt());
        List<String> reasons = new ArrayList<>();
        r.data().path("anomalies").forEach(a -> reasons.add(a.path("reason").asText()));
        assertTrue(reasons.contains("上级部门不存在或成环"), reasons.toString());
        assertTrue(reasons.contains("用户名在表内重复"), reasons.toString());
        assertTrue(reasons.stream().filter(x -> x.startsWith("无法在该分公司下唯一定位部门")).count() == 2,
                reasons.toString());

        // 「财务部」按分公司归属，不会串到总公司或别的分公司
        assertEquals("财务部", deptOf("T0002").deptName);
        assertEquals("道路工程分公司-财务部", deptOf("T0004").deptName);
        assertEquals("桥梁工程分公司-财务部", deptOf("T0006").deptName);
        assertEquals("道路工程分公司", deptOf("T0003").deptName);   // 分公司直属人员
        User imported = userMapper.findByUsername("T0004").orElseThrow();
        assertTrue(imported.mustChangePwd);
        assertEquals("T0004", imported.employeeNo);

        // 导入不分配任何角色
        assertEquals(0, userRoleMapper.findAll().stream().filter(ur -> !ur.userId.equals(1L)).count());

        // 负责人：唯一匹配的关联上；子树内重名则进异常清单，且关联不产生任何权限
        Department roadFinance = deptOf("T0004");
        assertEquals(userMapper.findByUsername("T0004").orElseThrow().id, roadFinance.managerUserId);
        assertTrue(r.data().path("anomalies").toString().contains("负责人"), r.toString());
        String managerToken = login("T0004", "Init@2026");
        assertEquals(403, getJson(managerToken, "/users").status());   // 首次登录先改口令
        assertEquals(200, postJson(managerToken, "/auth/password",
                java.util.Map.of("oldPassword", "Init@2026", "newPassword", "N3w-Passw0rd")).status());
        assertEquals(403, getJson(login("T0004", "N3w-Passw0rd"), "/users").status());

        // 可重复执行：再导一次不新增
        Resp again = call(root, multipart("/imports/organization")
                .file(new MockMultipartFile("departments", "d.xlsx", null, depts))
                .file(new MockMultipartFile("employees", "e.xlsx", null, employees))
                .param("companyCode", "IMP")
                .param("companyName", "导入测试公司"));
        assertEquals(0, again.data().path("counts").path("departmentsCreated").asInt());
        assertEquals(0, again.data().path("counts").path("usersCreated").asInt());
        assertEquals(6, again.data().path("counts").path("usersAlreadyPresent").asInt());
    }

    @Test
    void t21_importsPermissionMatrixWithNormalizationAndAnomalies() throws Exception {
        byte[] matrix = workbook("功能权限", new int[][]{{2, 4, 0, 0}, {2, 3, 1, 1}}, new String[][]{
                {"市政公司应用软件功能与权限清单", null, null, null, null, null, null, null, null},
                {"软件系统", "功能模块", "功能点", "功能说明", "系统管理员", "公司领导", "部门经理", "项目经理", "普通员工"},
                {"OA协同办公系统", "公文管理", "收文登记与流转", "说明", "全部", "查看", "查看/编辑", "查看", "查看"},
                {null, null, "发文拟稿与审核", "说明", "全部", "查看/审批", "查看/编辑/审批", "view/新增", "无"},
                {null, "会议管理", "会议室预约", "说明", "全部", "查看", "查看/编辑", "查看/新增", "查看/编辑本人"},
                {"成本管控系统", "成本分析", "报表汇总分析", "说明", "全部", "查看/导出", "无", "无", null},
        });
        String root = login("root");
        Resp r = call(root, multipart("/imports/permissions").file(new MockMultipartFile("file", "p.xlsx", null, matrix)));
        assertEquals(200, r.status(), r.toString());
        JsonNode c = r.data().path("counts");
        assertEquals(2, c.path("systems").asInt());
        assertEquals(4, c.path("resources").asInt());
        assertEquals(20, c.path("permissions").asInt());
        // 全部 4×5=20；领导 1+2+1+2=6；经理 2+3+2+0=7；项目经理 1+2+2+0=5；员工 1+0+2+(空白)=3
        assertEquals(20, c.path("rolePermissions.SYS_ADMIN").asInt());
        assertEquals(6, c.path("rolePermissions.COMPANY_LEADER").asInt());
        assertEquals(7, c.path("rolePermissions.DEPT_MANAGER").asInt());
        assertEquals(5, c.path("rolePermissions.PROJECT_MANAGER").asInt());
        assertEquals(3, c.path("rolePermissions.EMPLOYEE").asInt());
        assertEquals(41, c.path("rolePermissions").asInt());
        assertEquals(1, r.data().path("anomalies").size());
        assertEquals("空白权限单元格，未授权", r.data().path("anomalies").get(0).path("reason").asText());
        assertTrue(r.data().path("notes").toString().contains("view"));

        // 编码：系统固定码 + 模块编码 + 功能点序号。测试夹具已建有同名模块「公文管理」（doc）和其中的
        // 「发文拟稿与审核」（oa:doc:draft），导入按名称沿用；「会议管理」是新模块，按序号编为 m02
        assertTrue(permissionMapper.findByPermissionCode("oa:doc:draft:approve").isPresent());
        assertTrue(permissionMapper.findByPermissionCode("oa:doc:f01:view").isPresent());
        assertFalse(permissionMapper.findByPermissionCode("oa:doc:f02:view").isPresent());
        assertTrue(permissionMapper.findByPermissionCode("cost:m01:f01:export").isPresent());
        // 「编辑本人」只限本人
        JsonNode employee = getJson(root, "/roles/" + roleMapper.findByRoleCode("EMPLOYEE").orElseThrow().id).data();
        boolean selfOnlyEdit = false;
        for (JsonNode p : employee.path("permissions")) {
            if (p.path("permissionCode").asText().equals("oa:m02:f01:edit")) {
                selfOnlyEdit = p.path("selfOnly").asBoolean();
            }
        }
        assertTrue(selfOnlyEdit);

        // 重复导入不产生重复的资源、权限与角色权限
        long before = rolePermissionMapper.count();
        long permissionsBefore = permissionMapper.count();
        Resp again = call(root, multipart("/imports/permissions").file(new MockMultipartFile("file", "p.xlsx", null, matrix)));
        assertEquals(200, again.status());
        assertEquals(before, rolePermissionMapper.count());
        assertEquals(permissionsBefore, permissionMapper.count());
    }

    @Test
    void reimportingPermissionMatrixRevokesRemovedGrantsAndUpdatesSelfOnly() throws Exception {
        String[] head = {"软件系统", "功能模块", "功能点", "功能说明", "系统管理员", "公司领导", "部门经理", "项目经理", "普通员工"};
        byte[] first = workbook("功能权限", null, new String[][]{
                {"清单"}, head,
                {"OA协同办公系统", "公文管理", "收文登记与流转", "说明", "全部", "查看/审批", "查看/编辑", "查看", "查看"},
                {null, null, "发文拟稿与审核", "说明", "全部", "查看", "查看/编辑", "查看", "查看本人"},
                {null, null, "公文归档", "说明", "全部", "查看", "查看", "查看", "查看"},
                {null, "会议管理", "会议室预约", "说明", "全部", "查看", "查看", "查看", "查看/编辑"},
        });
        String root = login("root");
        assertEquals(200, call(root, multipart("/imports/permissions")
                .file(new MockMultipartFile("file", "p.xlsx", null, first))).status());
        Role leader = roleMapper.findByRoleCode("COMPANY_LEADER").orElseThrow();
        Role manager = roleMapper.findByRoleCode("DEPT_MANAGER").orElseThrow();
        Role employee = roleMapper.findByRoleCode("EMPLOYEE").orElseThrow();
        // 权限中心自己的权限不归清单管，重复导入不能动它
        Permission dashboard = permissionMapper.findByPermissionCode("rbac:dashboard:view").orElseThrow();
        RolePermission extra = new RolePermission();
        extra.roleId = employee.id;
        extra.permissionId = dashboard.id;
        extra.grantedAt = java.time.LocalDateTime.now();
        rolePermissionMapper.save(extra);
        User u = user("u1", org.aFinance());
        grant(u, leader, org.aFinance());
        String token = login("u1");
        assertTrue(check(token, "oa:doc:f01:approve", org.aFinance().id).path("allowed").asBoolean());
        assertTrue(check(token, "oa:doc:f01:approve", org.aFinance().id).path("cacheHit").asBoolean());
        String archive = "oa:doc:f02:view";   // 「发文拟稿与审核」沿用夹具里的 oa:doc:draft
        assertTrue(rolePermissionMapper.findPermissionCodesOfRole(leader.id).contains(archive));

        // 领导收文去掉「审批」；员工发文「查看本人」改「查看」；经理发文改成空白（异常，原授权不动）；
        // 删掉中间的「发文拟稿与审核」之后的「公文归档」保持原编码；整行删掉「会议室预约」
        byte[] second = workbook("功能权限", null, new String[][]{
                {"清单"}, head,
                {"OA协同办公系统", "公文管理", "收文登记与流转", "说明", "全部", "查看", "查看/编辑", "查看", "查看"},
                {null, null, "发文拟稿与审核", "说明", "全部", "查看", null, "查看", "查看"},
                {null, null, "公文归档", "说明", "全部", "查看", "查看", "查看", "查看"},
        });
        Resp r = call(root, multipart("/imports/permissions").file(new MockMultipartFile("file", "p.xlsx", null, second)));
        assertEquals(200, r.status(), r.toString());
        JsonNode c = r.data().path("counts");
        // 领导 approve 1 + 会议室预约：管理员 5、领导 1、经理 1、项目经理 1、员工 2
        assertEquals(11, c.path("rolePermissionsRevoked").asInt());
        assertEquals(1, c.path("rolePermissionsSelfOnlyChanged").asInt());
        assertEquals(1, r.data().path("anomalies").size());

        List<String> leaderCodes = rolePermissionMapper.findPermissionCodesOfRole(leader.id);
        assertFalse(leaderCodes.contains("oa:doc:f01:approve"));
        assertTrue(leaderCodes.contains("oa:doc:f01:view"));
        assertTrue(leaderCodes.contains(archive));
        for (Role role : roleMapper.findAll()) {
            assertTrue(rolePermissionMapper.findPermissionCodesOfRole(role.id).stream()
                    .noneMatch(code -> code.startsWith("oa:m02:f01:")), role.roleCode);
        }
        assertTrue(rolePermissionMapper.findPermissionCodesOfRole(manager.id).contains("oa:doc:draft:edit"));
        Permission draftView = permissionMapper.findByPermissionCode("oa:doc:draft:view").orElseThrow();
        assertFalse(rolePermissionMapper.findByRoleIdAndPermissionId(employee.id, draftView.id).orElseThrow().selfOnly);
        assertTrue(rolePermissionMapper.findPermissionCodesOfRole(employee.id).contains("rbac:dashboard:view"));
        // 下一次请求立即生效：缓存不再命中，按新数据拒绝
        JsonNode after = check(token, "oa:doc:f01:approve", org.aFinance().id);
        assertFalse(after.path("allowed").asBoolean());
        assertFalse(after.path("cacheHit").asBoolean());
        // 每条回收、每条「只限本人」变化都有审计记录
        JsonNode revokeLogs = getJson(root, "/audit-logs?eventType=PERMISSION_REVOKE&size=50").data();
        assertEquals(11, revokeLogs.path("total").asInt());
        assertTrue(revokeLogs.toString().contains("permission=oa:doc:f01:approve source=import"));
        JsonNode selfOnlyLogs = getJson(root, "/audit-logs?eventType=PERMISSION_ASSIGN").data();
        assertEquals(1, selfOnlyLogs.path("total").asInt());
        assertTrue(selfOnlyLogs.toString().contains("permission=oa:doc:draft:view selfOnly=false source=import"));

        // 中间删掉一行：后面的功能点按名称认领原资源，不会挪到别的编码上
        byte[] third = workbook("功能权限", null, new String[][]{
                {"清单"}, head,
                {"OA协同办公系统", "公文管理", "收文登记与流转", "说明", "全部", "查看", "查看/编辑", "查看", "查看"},
                {null, null, "公文归档", "说明", "全部", "无", "查看", "查看", "查看"},
        });
        Resp r3 = call(root, multipart("/imports/permissions").file(new MockMultipartFile("file", "p.xlsx", null, third)));
        assertEquals(200, r3.status(), r3.toString());
        leaderCodes = rolePermissionMapper.findPermissionCodesOfRole(leader.id);
        assertFalse(leaderCodes.contains(archive));
        assertFalse(leaderCodes.contains("oa:doc:draft:view"));
        assertTrue(rolePermissionMapper.findPermissionCodesOfRole(manager.id).contains(archive));

        // 有功能点认不出所属系统时整份不回收
        byte[] broken = workbook("功能权限", null, new String[][]{
                {"清单"}, head,
                {null, null, "认不出的功能点", "说明", "全部", "全部", "全部", "全部", "全部"},
        });
        long before = rolePermissionMapper.count();
        Resp r4 = call(root, multipart("/imports/permissions").file(new MockMultipartFile("file", "p.xlsx", null, broken)));
        assertEquals(200, r4.status(), r4.toString());
        assertEquals(0, r4.data().path("counts").path("rolePermissionsRevoked").asInt());
        assertTrue(r4.data().path("notes").toString().contains("未回收"));
        assertEquals(before, rolePermissionMapper.count());
    }

    @Test
    void rejectsWrongFiles() throws Exception {
        String root = login("root");
        byte[] notExcel = "hello".getBytes();
        Resp r = call(root, multipart("/imports/permissions").file(new MockMultipartFile("file", "p.xlsx", null, notExcel)));
        assertEquals(400, r.status());
        byte[] wrongHeader = workbook("x", null, new String[][]{{"a", "b"}, {"1", "2"}});
        Resp r2 = call(root, multipart("/imports/permissions").file(new MockMultipartFile("file", "p.xlsx", null, wrongHeader)));
        assertEquals(400, r2.status());
        Resp r3 = call(root, multipart("/imports/organization")
                .file(new MockMultipartFile("departments", "d.xlsx", null, wrongHeader))
                .file(new MockMultipartFile("employees", "e.xlsx", null, wrongHeader))
                .param("companyCode", "X").param("companyName", "X"));
        assertEquals(400, r3.status());
        // 普通用户不能导入
        user("u1", org.aFinance());
        assertEquals(403, call(login("u1"), multipart("/imports/permissions")
                .file(new MockMultipartFile("file", "p.xlsx", null, wrongHeader))).status());
        assertFalse(companyMapper.findByCompanyCode("X").isPresent());
    }

    private Department deptOf(String username) {
        User u = userMapper.findByUsername(username).orElseThrow();
        return departmentMapper.findById(u.departmentId).orElseThrow();
    }

    /** 造一个 xlsx。merges 每项为 {首行, 末行, 首列, 末列}，用来模拟真实表格里合并的「软件系统」「功能模块」列。 */
    static byte[] workbook(String sheetName, int[][] merges, String[][] rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i);
                for (int j = 0; j < rows[i].length; j++) {
                    if (rows[i][j] != null) {
                        row.createCell(j).setCellValue(rows[i][j]);
                    }
                }
            }
            if (merges != null) {
                for (int[] m : merges) {
                    sheet.addMergedRegion(new CellRangeAddress(m[0], m[1], m[2], m[3]));
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
