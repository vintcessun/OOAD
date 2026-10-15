package com.xmu.rbac.structured.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.structured.entity.Action;
import com.xmu.rbac.structured.entity.Company;
import com.xmu.rbac.structured.entity.Department;
import com.xmu.rbac.structured.entity.FunctionModule;
import com.xmu.rbac.structured.entity.Permission;
import com.xmu.rbac.structured.entity.Resource;
import com.xmu.rbac.structured.entity.Role;
import com.xmu.rbac.structured.entity.RolePermission;
import com.xmu.rbac.structured.entity.SoftwareSystem;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.entity.UserRole;
import com.xmu.rbac.structured.mapper.ActionMapper;
import com.xmu.rbac.structured.mapper.CompanyMapper;
import com.xmu.rbac.structured.mapper.DepartmentMapper;
import com.xmu.rbac.structured.mapper.FunctionModuleMapper;
import com.xmu.rbac.structured.mapper.PermissionMapper;
import com.xmu.rbac.structured.mapper.ResourceMapper;
import com.xmu.rbac.structured.mapper.RoleMapper;
import com.xmu.rbac.structured.mapper.RolePermissionMapper;
import com.xmu.rbac.structured.mapper.SoftwareSystemMapper;
import com.xmu.rbac.structured.mapper.UserMapper;
import com.xmu.rbac.structured.mapper.UserRoleMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 集成测试基类：真实的 MySQL + Redis + 完整的 Spring 上下文，经 HTTP（MockMvc）调用接口。
 * 每个测试前把数据库恢复成只含内置数据的状态，并清空 Redis：
 * 整个测试进程只 clean + migrate 一次并给每张表拍快照，之后每个测试前「清空 + 从快照复制」，
 * 比每次重建表快一个数量级。
 *
 * <p>测试组织（{@link #org}）：
 * <pre>
 * 公司 A（SZ）  总公司
 *               ├ 财务部
 *               ├ 道路分公司 ─┬ 道路分公司-财务部
 *               │             └ 道路分公司-第一项目部
 *               └ 桥梁分公司 ── 桥梁分公司-财务部
 * 公司 B（SZ2） 总公司 ── 财务部          （与 A 同名，用于多公司隔离）
 * </pre>
 * 业务资源 oa:doc:draft 有 view / approve 两个权限；角色 DOC_VIEWER（view）、DOC_APPROVER（view + approve）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    public static final String PASSWORD = "Passw0rd!";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected Flyway flyway;
    @Autowired protected StringRedisTemplate redis;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected CompanyMapper companyMapper;
    @Autowired protected DepartmentMapper departmentMapper;
    @Autowired protected UserMapper userMapper;
    @Autowired protected RoleMapper roleMapper;
    @Autowired protected UserRoleMapper userRoleMapper;
    @Autowired protected RolePermissionMapper rolePermissionMapper;
    @Autowired protected PermissionMapper permissionMapper;
    @Autowired protected ResourceMapper resourceMapper;
    @Autowired protected ActionMapper actionMapper;
    @Autowired protected SoftwareSystemMapper systemMapper;
    @Autowired protected FunctionModuleMapper moduleMapper;

    private static String passwordHash;

    /** 测试组织的各节点。 */
    protected Org org;

    public record Org(Company a, Department aRoot, Department aFinance, Department road, Department roadFinance,
                      Department roadProject, Department bridge, Department bridgeFinance,
                      Company b, Department bRoot, Department bFinance,
                      Permission docView, Permission docApprove, Role docViewer, Role docApprover,
                      Role rbacAdmin, Role superAdmin) {
    }

    @Autowired protected org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static java.util.List<String> tables;

    @BeforeEach
    void resetDatabase() {
        if (tables == null) {
            flyway.clean();
            flyway.migrate();
            tables = jdbc.queryForList("""
                    SELECT table_name FROM information_schema.tables
                     WHERE table_schema = DATABASE() AND table_name LIKE 'sys!_%' ESCAPE '!'
                    """, String.class);
            for (String t : tables) {
                jdbc.execute("DROP TABLE IF EXISTS snap_" + t);
                jdbc.execute("CREATE TABLE snap_" + t + " AS SELECT * FROM " + t);
            }
        }
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) con -> {
            try (var st = con.createStatement()) {
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String t : tables) {
                    st.execute("TRUNCATE TABLE " + t);
                    st.execute("INSERT INTO " + t + " SELECT * FROM snap_" + t);
                }
                st.execute("SET FOREIGN_KEY_CHECKS = 1");
                // TRUNCATE 把自增计数器退回起点，补回 V3 迁移设的员工编号起点
                st.execute("ALTER TABLE sys_user AUTO_INCREMENT = " + User.INTERNAL_ID_LIMIT);
            }
            return null;
        });
        redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> {
            c.serverCommands().flushDb();
            return null;
        });
        if (passwordHash == null) {
            passwordHash = passwordEncoder.encode(PASSWORD);
        }
        // 内置超级管理员：测试里免去首次改密，并统一口令
        User root = userMapper.findByUsername("root").orElseThrow();
        root.mustChangePwd = false;
        root.passwordHash = passwordHash;
        userMapper.save(root);
        org = buildOrg();
    }

    private Org buildOrg() {
        Company a = company("SZ", "市政公司");
        Department aRoot = dept(a, null, "总公司");
        Department aFinance = dept(a, aRoot, "财务部");
        Department road = dept(a, aRoot, "道路分公司");
        Department roadFinance = dept(a, road, "道路分公司-财务部");
        Department roadProject = dept(a, road, "道路分公司-第一项目部");
        Department bridge = dept(a, aRoot, "桥梁分公司");
        Department bridgeFinance = dept(a, bridge, "桥梁分公司-财务部");
        Company b = company("SZ2", "第二市政公司");
        Department bRoot = dept(b, null, "总公司");
        Department bFinance = dept(b, bRoot, "财务部");

        SoftwareSystem oa = new SoftwareSystem();
        oa.systemCode = "oa";
        oa.systemName = "OA协同办公系统";
        oa.sortOrder = 1;
        systemMapper.save(oa);
        FunctionModule doc = new FunctionModule();
        doc.systemId = oa.id;
        doc.moduleCode = "doc";
        doc.moduleName = "公文管理";
        moduleMapper.save(doc);
        Resource draft = new Resource();
        draft.moduleId = doc.id;
        draft.resourceCode = "oa:doc:draft";
        draft.resourceName = "发文拟稿与审核";
        resourceMapper.save(draft);
        Permission docView = permission(draft, "view");
        Permission docApprove = permission(draft, "approve");

        Role viewer = role("DOC_VIEWER", "公文查阅", null, docView);
        Role approver = role("DOC_APPROVER", "公文审批", null, docView, docApprove);
        return new Org(a, aRoot, aFinance, road, roadFinance, roadProject, bridge, bridgeFinance, b, bRoot, bFinance,
                docView, docApprove, viewer, approver,
                roleMapper.findByRoleCode("RBAC_ADMIN").orElseThrow(),
                roleMapper.findByRoleCode("SUPER_ADMIN").orElseThrow());
    }

    // ------------------------------------------------------------------ 造数据

    protected Company company(String code, String name) {
        Company c = new Company();
        c.companyCode = code;
        c.companyName = name;
        return companyMapper.save(c);
    }

    protected Department dept(Company c, Department parent, String name) {
        Department d = new Department();
        d.companyId = c.id;
        d.parentId = parent == null ? null : parent.id;
        d.deptName = name;
        d.level = parent == null ? 1 : parent.level + 1;
        d.path = "/";
        departmentMapper.save(d);
        d.path = (parent == null ? "/" : parent.path) + d.id + "/";
        return departmentMapper.save(d);
    }

    protected Permission permission(Resource r, String actionCode) {
        Action a = actionMapper.findByActionCode(actionCode).orElseThrow();
        Permission p = new Permission();
        p.resourceId = r.id;
        p.actionId = a.id;
        p.permissionCode = r.resourceCode + ":" + actionCode;
        return permissionMapper.save(p);
    }

    protected Role role(String code, String name, Long companyId, Permission... perms) {
        Role r = new Role();
        r.roleCode = code;
        r.roleName = name;
        r.companyId = companyId;
        roleMapper.save(r);
        for (Permission p : perms) {
            RolePermission rp = new RolePermission();
            rp.roleId = r.id;
            rp.permissionId = p.id;
            rp.grantedAt = LocalDateTime.now();
            rolePermissionMapper.save(rp);
        }
        return r;
    }

    protected User user(String username, Department d) {
        User u = new User();
        u.companyId = d.companyId;
        u.departmentId = d.id;
        u.username = username;
        u.realName = "测试" + username;
        u.employeeNo = username;
        u.passwordHash = passwordHash;
        return userMapper.save(u);
    }

    /** 直接写库建立指派（测试前置条件，不经过接口校验）。 */
    protected UserRole grant(User u, Role r, Department scope) {
        UserRole ur = new UserRole();
        ur.userId = u.id;
        ur.roleId = r.id;
        ur.scopeDeptId = scope.id;
        ur.grantedAt = LocalDateTime.now();
        return userRoleMapper.save(ur);
    }

    // ------------------------------------------------------------------ 调接口

    protected String login(String username) throws Exception {
        return login(username, PASSWORD);
    }

    protected String login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", password))))
                .andReturn();
        JsonNode body = json.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        if (r.getResponse().getStatus() != 200) {
            throw new AssertionError("登录失败 " + username + "：" + body);
        }
        return body.path("data").path("token").asText();
    }

    /** 发请求，返回 [HTTP 状态, 响应体]。 */
    protected Resp call(String token, MockHttpServletRequestBuilder req) throws Exception {
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        MvcResult r = mvc.perform(req).andReturn();
        String body = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return new Resp(r.getResponse().getStatus(), body.isEmpty() ? json.nullNode() : json.readTree(body));
    }

    protected Resp getJson(String token, String url) throws Exception {
        return call(token, get(url));
    }

    protected Resp postJson(String token, String url, Object body) throws Exception {
        return call(token, post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected Resp patchJson(String token, String url, Object body) throws Exception {
        return call(token, patch(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected Resp deleteReq(String token, String url) throws Exception {
        return call(token, delete(url));
    }

    /** 以 token 的主人身份问「我能否对某部门做某事」。 */
    protected JsonNode check(String token, String permission, Long targetDeptId) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("permission", permission);
        body.put("targetDeptId", targetDeptId);
        Resp r = postJson(token, "/authz/check", body);
        if (r.status() != 200) {
            throw new AssertionError("鉴权调用失败：" + r);
        }
        return r.data();
    }

    public record Resp(int status, JsonNode body) {
        public JsonNode data() {
            return body.path("data");
        }

        public int code() {
            return body.path("code").asInt();
        }
    }
}
