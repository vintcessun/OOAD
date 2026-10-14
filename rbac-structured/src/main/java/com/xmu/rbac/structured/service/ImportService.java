package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.ImportReport;
import com.xmu.rbac.structured.dto.ImportReport.Anomaly;
import com.xmu.rbac.structured.dto.Operator;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 8.0 数据导入：三份 xlsx（docs/03-database.md §5）。
 *
 * <p>导入只建立组织、人员和权限模板，<b>不给任何人分配角色</b>：整个系统的权限都由人工分配，
 * 岗位不等于系统角色。导入之后由管理员审核，用批量授权接口正式分配（docs/12-design-log.md #18）。
 *
 * <p>无法唯一确定的数据一律进入异常清单、不写入，绝不「取第一个同名的」。
 */
@Service
public class ImportService {

    public static final String PERM_IMPORT_ORG = "rbac:org:import";
    public static final String PERM_IMPORT_PERMISSIONS = "rbac:permission:create";

    /** 权限清单的业务系统 → 权限码第一段。不在表中的系统按出现顺序编为 sysNN。 */
    static final Map<String, String> SYSTEM_CODES = Map.ofEntries(
            Map.entry("OA协同办公系统", "oa"),
            Map.entry("企业即时通讯系统", "im"),
            Map.entry("企业知识库管理系统", "kb"),
            Map.entry("人力资源管理系统", "hr"),
            Map.entry("财务管理系统", "fin"),
            Map.entry("工程项目管理系统(PMIS)", "pmis"),
            Map.entry("招投标管理系统", "bid"),
            Map.entry("物资设备管理系统", "mat"),
            Map.entry("移动巡检APP", "insp"),
            Map.entry("工地AI视频监控系统", "video"),
            Map.entry("GIS地下管线管理系统", "gis"),
            Map.entry("企业档案管理系统", "arch"),
            Map.entry("车辆管理系统", "veh"),
            Map.entry("企业宣传平台", "pr"),
            Map.entry("安全生产管理系统", "safe"),
            Map.entry("合同全生命周期管理系统", "contract"),
            Map.entry("成本管控系统", "cost"),
            Map.entry("系统管理后台", "sysadm"));

    /** 权限清单的角色列 → 角色编码。 */
    static final Map<String, String> ROLE_CODES = Map.of(
            "系统管理员", "SYS_ADMIN",
            "公司领导", "COMPANY_LEADER",
            "部门经理", "DEPT_MANAGER",
            "项目经理", "PROJECT_MANAGER",
            "普通员工", "EMPLOYEE");

    /** 五类业务操作（「全部」展开为这五个）。 */
    static final List<String> BUSINESS_ACTIONS = List.of("view", "create", "edit", "approve", "export");

    /** 单元格取值的词汇 → 操作编码。英文 view 是原表中的混合语言取值。 */
    static final Map<String, String> ACTION_WORDS = Map.of(
            "查看", "view", "view", "view", "新增", "create", "编辑", "edit", "审批", "approve", "导出", "export");

    private final CompanyMapper companyMapper;
    private final DepartmentMapper departmentMapper;
    private final UserMapper userMapper;
    private final SoftwareSystemMapper systemMapper;
    private final FunctionModuleMapper moduleMapper;
    private final ResourceMapper resourceMapper;
    private final ActionMapper actionMapper;
    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserManagementService users;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    public ImportService(CompanyMapper companyMapper, DepartmentMapper departmentMapper, UserMapper userMapper,
                         SoftwareSystemMapper systemMapper, FunctionModuleMapper moduleMapper,
                         ResourceMapper resourceMapper, ActionMapper actionMapper, PermissionMapper permissionMapper,
                         RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper,
                         UserManagementService users, AuthorizationService authz, AuditService audit, Clock clock) {
        this.companyMapper = companyMapper;
        this.departmentMapper = departmentMapper;
        this.userMapper = userMapper;
        this.systemMapper = systemMapper;
        this.moduleMapper = moduleMapper;
        this.resourceMapper = resourceMapper;
        this.actionMapper = actionMapper;
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.users = users;
        this.authz = authz;
        this.audit = audit;
        this.clock = clock;
    }

    // ================================================================== 8.1 组织与人员

    /**
     * 导入部门信息表与员工信息表到一家公司（不存在则创建）。可重复执行：已存在的部门、用户名跳过。
     */
    @Transactional
    public ImportReport importOrganization(Operator op, String companyCode, String companyName,
                                           InputStream deptXlsx, InputStream employeeXlsx) {
        if (companyCode == null || companyCode.isBlank() || companyName == null || companyName.isBlank()) {
            throw BizException.badRequest("companyCode、companyName 必填");
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        List<Anomaly> anomalies = new ArrayList<>();

        Company company = companyMapper.findByCompanyCode(companyCode.trim()).orElseGet(() -> {
            Company c = new Company();
            c.companyCode = companyCode.trim();
            c.companyName = companyName.trim();
            return companyMapper.save(c);
        });

        List<List<String>> deptRows = readSheet(deptXlsx, "部门名称");
        List<List<String>> empRows = readSheet(employeeXlsx, "用户名");

        Map<String, Department> byName = importDepartments(company, deptRows, counts, anomalies);
        Map<Long, Department> byId = new HashMap<>();
        byName.values().forEach(d -> byId.put(d.id, d));
        List<User> created = importEmployees(company, byName, empRows, counts, anomalies);
        linkManagers(company, deptRows, byName, byId, created, counts, notes, anomalies);

        notes.add("导入不分配任何角色：请审核后用批量授权（POST /assignments/batch）人工分配");
        audit.record(op, "IMPORT_ORG", "COMPANY", company.id, company.companyName, counts.toString(),
                anomalies.isEmpty() ? AuditService.SUCCESS : AuditService.FAILED,
                anomalies.isEmpty() ? null : anomalies.size() + " 行异常");
        return new ImportReport(counts, notes, anomalies);
    }

    /** 按「上级部门」列建树。名称在表内唯一；上级缺失、重名、成环的行进入异常清单。 */
    Map<String, Department> importDepartments(Company company, List<List<String>> rows,
                                              Map<String, Integer> counts, List<Anomaly> anomalies) {
        Map<String, List<String>> pending = new LinkedHashMap<>();   // 名称 → 行
        Map<String, Integer> rowNo = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            String name = cell(r, 0);
            if (name == null) {
                anomalies.add(new Anomaly("部门信息", i + 2, "部门名称为空", String.join(",", r)));
                continue;
            }
            if (pending.containsKey(name)) {
                anomalies.add(new Anomaly("部门信息", i + 2, "部门名称重复", name));
                continue;
            }
            pending.put(name, r);
            rowNo.put(name, i + 2);
        }
        Map<String, Department> done = new LinkedHashMap<>();
        int created = 0;
        boolean progress = true;
        while (!pending.isEmpty() && progress) {
            progress = false;
            for (var it = pending.entrySet().iterator(); it.hasNext(); ) {
                var e = it.next();
                String parentName = cell(e.getValue(), 2);
                Department parent = parentName == null ? null : done.get(parentName);
                if (parentName != null && parent == null) {
                    continue;   // 上级还没建好，下一轮
                }
                if (parentName == null && done.values().stream().anyMatch(d -> d.parentId == null)) {
                    anomalies.add(new Anomaly("部门信息", rowNo.get(e.getKey()), "一家公司只能有一个根部门", e.getKey()));
                    it.remove();
                    continue;
                }
                Long parentId = parent == null ? null : parent.id;
                Department d = (parentId == null
                        ? departmentMapper.findByCompanyIdAndParentIdIsNull(company.id)
                        : departmentMapper.findByCompanyIdAndParentIdAndDeptName(company.id, parentId, e.getKey()))
                        .orElse(null);
                if (d == null) {
                    d = new Department();
                    d.companyId = company.id;
                    d.parentId = parentId;
                    d.deptName = e.getKey();
                    d.level = parent == null ? 1 : parent.level + 1;
                    d.path = "/";   // 拿到 id 后再写真正的路径
                    d.sortOrder = rowNo.get(e.getKey());
                    departmentMapper.save(d);
                    d.path = (parent == null ? "/" : parent.path) + d.id + "/";
                    created++;
                }
                d.managerName = cell(e.getValue(), 1);
                departmentMapper.save(d);
                done.put(e.getKey(), d);
                it.remove();
                progress = true;
            }
        }
        pending.forEach((name, r) -> anomalies.add(new Anomaly("部门信息", rowNo.get(name),
                "上级部门不存在或成环", name + " ← " + cell(r, 2))));
        counts.put("departmentsInSheet", rows.size());
        counts.put("departmentsCreated", created);
        return done;
    }

    /**
     * 员工的部门按「分公司 + 部门」两列定位，并校验部门确实在该分公司之下：
     * <ul>
     *   <li>分公司 = 根部门（总公司）：部门是根部门的直接下级；</li>
     *   <li>部门 = 分公司：分公司直属人员，归到分公司节点本身；</li>
     *   <li>否则：在该分公司的下级中找「分公司-部门」或「部门」。</li>
     * </ul>
     * 只凭部门名查找会把「财务部」错配到总公司财务部或别的分公司财务部。
     */
    List<User> importEmployees(Company company, Map<String, Department> byName, List<List<String>> rows,
                               Map<String, Integer> counts, List<Anomaly> anomalies) {
        Department root = byName.values().stream().filter(d -> d.parentId == null).findFirst().orElse(null);
        if (root == null) {
            throw BizException.badRequest("部门信息表中没有根部门");
        }
        Map<String, Department> child = new HashMap<>();   // parentId|name → dept
        byName.values().forEach(d -> child.put(d.parentId + "|" + d.deptName, d));

        Set<String> existing = new HashSet<>(userMapper.findAllUsernames());
        Set<String> seen = new HashSet<>();
        String hash = users.initialPasswordHash();
        List<User> created = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            int line = i + 2;
            String name = cell(r, 0);
            String username = cell(r, 1);
            String branch = cell(r, 2);
            String deptName = cell(r, 3);
            if (name == null || username == null || branch == null || deptName == null) {
                anomalies.add(new Anomaly("员工信息", line, "姓名、用户名、分公司、部门不能为空", String.join(",", r)));
                continue;
            }
            if (!seen.add(username)) {
                anomalies.add(new Anomaly("员工信息", line, "用户名在表内重复", username));
                continue;
            }
            if (existing.contains(username)) {
                skipped++;
                continue;
            }
            Department dept = locate(root, child, branch, deptName);
            if (dept == null) {
                anomalies.add(new Anomaly("员工信息", line, "无法在该分公司下唯一定位部门", branch + " / " + deptName));
                continue;
            }
            User u = new User();
            u.companyId = company.id;
            u.departmentId = dept.id;
            u.username = username;
            u.employeeNo = username;
            u.realName = name;
            u.position = cell(r, 4);
            u.phone = cell(r, 5);
            u.userType = User.EMPLOYEE;
            u.passwordHash = hash;
            u.mustChangePwd = true;
            created.add(u);
        }
        userMapper.saveAll(created);
        counts.put("employeesInSheet", rows.size());
        counts.put("usersCreated", created.size());
        counts.put("usersAlreadyPresent", skipped);
        return created;
    }

    static Department locate(Department root, Map<String, Department> child, String branch, String deptName) {
        if (branch.equals(root.deptName)) {
            return deptName.equals(root.deptName) ? root : child.get(root.id + "|" + deptName);
        }
        Department branchNode = child.get(root.id + "|" + branch);
        if (branchNode == null) {
            return null;
        }
        if (deptName.equals(branch)) {
            return branchNode;
        }
        Department d = child.get(branchNode.id + "|" + branch + "-" + deptName);
        return d != null ? d : child.get(branchNode.id + "|" + deptName);
    }

    /**
     * 部门负责人只按姓名登记，可能重名。先在该部门子树内找，找不到再在全公司找；恰好一人才关联。
     * 关联只是组织信息，不产生任何管理权限。
     */
    void linkManagers(Company company, List<List<String>> deptRows, Map<String, Department> byName,
                      Map<Long, Department> byId, List<User> created, Map<String, Integer> counts,
                      List<String> notes, List<Anomaly> anomalies) {
        Map<String, List<User>> byRealName = new HashMap<>();
        for (User u : created) {
            byRealName.computeIfAbsent(u.realName, k -> new ArrayList<>()).add(u);
        }
        int linked = 0;
        for (int i = 0; i < deptRows.size(); i++) {
            String deptName = cell(deptRows.get(i), 0);
            String manager = cell(deptRows.get(i), 1);
            Department d = deptName == null ? null : byName.get(deptName);
            if (d == null || manager == null) {
                continue;
            }
            List<User> sameName = byRealName.getOrDefault(manager, List.of());
            List<User> inSubtree = sameName.stream()
                    .filter(u -> AuthorizationService.covers(d.path, byId.get(u.departmentId).path))
                    .toList();
            List<User> pick = inSubtree.size() == 1 ? inSubtree : (inSubtree.isEmpty() ? sameName : inSubtree);
            if (pick.size() == 1) {
                d.managerUserId = pick.get(0).id;
                departmentMapper.save(d);
                linked++;
            } else {
                anomalies.add(new Anomaly("部门信息", i + 2,
                        pick.isEmpty() ? "负责人在员工表中找不到" : "负责人重名，无法唯一确定（" + pick.size() + " 人）",
                        deptName + " / " + manager));
            }
        }
        counts.put("managersLinked", linked);
        notes.add("部门负责人只作为组织信息，不会因此获得任何管理权限");
    }

    // ================================================================== 8.2 功能权限清单

    /**
     * 导入应用软件功能权限清单：业务系统、功能模块、功能点（资源），每个资源 × 五类操作的权限，
     * 以及五类角色模板的角色权限。可重复执行：资源与权限已存在的沿用；角色模板在业务系统上的授权
     * 与最新清单对齐——清单新增的写入，「只限本人」变了的更新，清单里已去掉的（改为「无」、删掉某个操作、
     * 整行删掉功能点）回收。单元格有异常的，该角色在该功能点上的原有授权保持不动；
     * 有功能点定位不到所属系统或模块时整份清单不做回收，避免把认不出的行当成删除。
     */
    @Transactional
    public ImportReport importPermissions(Operator op, InputStream xlsx) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        List<Anomaly> anomalies = new ArrayList<>();
        Sheet sheet;
        Workbook wb;
        try {
            wb = WorkbookFactory.create(xlsx);
            sheet = wb.getSheetAt(0);
        } catch (IOException | RuntimeException e) {
            throw BizException.badRequest("无法读取 xlsx：" + e.getMessage());
        }
        DataFormatter fmt = new DataFormatter();
        int header = findHeaderRow(sheet, fmt, "软件系统");
        Row h = sheet.getRow(header);
        List<String> roleColumns = new ArrayList<>();
        for (int c = 4; c < h.getLastCellNum(); c++) {
            String v = text(fmt, h.getCell(c));
            if (v != null) {
                roleColumns.add(v);
            }
        }

        Map<String, Action> actions = new HashMap<>();
        for (String a : BUSINESS_ACTIONS) {
            actions.put(a, actionMapper.findByActionCode(a).orElseThrow());
        }
        List<Role> roles = new ArrayList<>();
        for (String col : roleColumns) {
            roles.add(roleTemplate(col, roles.size()));
        }

        Set<String> normalized = new LinkedHashSet<>();
        int systems = 0;
        int modules = 0;
        int resources = 0;
        int permissions = 0;
        int grants = 0;
        int[] perRole = new int[roles.size()];
        int selfOnlyChanged = 0;
        boolean unresolvedRows = false;
        // 每个角色：清单要求的 permissionId → 是否只限本人；单元格异常、原有授权不动的 resourceId
        List<Map<Long, Boolean>> wanted = new ArrayList<>();
        List<Set<Long>> untouched = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            wanted.add(new HashMap<>());
            untouched.add(new HashSet<>());
        }
        SoftwareSystem sys = null;
        FunctionModule mod = null;
        int resourceSeq = 0;
        Map<Long, Map<String, Integer>> seenNames = new HashMap<>();
        for (int r = header + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            String sysName = text(fmt, row.getCell(0));
            String modName = text(fmt, row.getCell(1));
            String resName = text(fmt, row.getCell(2));
            if (sysName != null) {
                SoftwareSystem before = sys;
                sys = system(sysName);
                systems += (before == null || !before.id.equals(sys.id)) ? 1 : 0;
                mod = null;
            }
            if (modName != null && sys != null) {
                mod = module(sys, modName);
                modules++;
                resourceSeq = 0;
            }
            if (resName == null) {
                continue;
            }
            if (sys == null || mod == null) {
                anomalies.add(new Anomaly("功能权限清单", r + 1, "功能点缺少所属系统或模块", resName));
                unresolvedRows = true;
                continue;
            }
            resourceSeq++;
            int occurrence = seenNames.computeIfAbsent(mod.id, k -> new HashMap<>()).merge(resName, 1, Integer::sum);
            Resource res = resource(sys, mod, resourceSeq, occurrence, resName, text(fmt, row.getCell(3)));
            resources++;
            Map<String, Permission> permOf = new HashMap<>();
            for (Permission p : permissionMapper.findByResourceId(res.id)) {
                permOf.put(p.permissionCode, p);
            }
            for (String a : BUSINESS_ACTIONS) {
                String code = res.resourceCode + ":" + a;
                if (!permOf.containsKey(code)) {
                    Permission p = new Permission();
                    p.resourceId = res.id;
                    p.actionId = actions.get(a).id;
                    p.permissionCode = code;
                    permOf.put(code, permissionMapper.save(p));
                }
                permissions++;
            }
            for (int i = 0; i < roles.size(); i++) {
                String raw = text(fmt, row.getCell(4 + i));
                ParsedCell parsed = parseCell(raw);
                if (parsed.error() != null) {
                    anomalies.add(new Anomaly("功能权限清单", r + 1, parsed.error(),
                            sysName(sys) + " / " + resName + " / " + roleColumns.get(i) + " = " + raw));
                    untouched.get(i).add(res.id);
                    continue;
                }
                if (parsed.note() != null) {
                    normalized.add(parsed.note());
                }
                for (String a : parsed.actions()) {
                    Permission p = permOf.get(res.resourceCode + ":" + a);
                    boolean selfOnly = parsed.selfOnly().contains(a);
                    wanted.get(i).put(p.id, selfOnly);
                    RolePermission rp = rolePermissionMapper.findByRoleIdAndPermissionId(roles.get(i).id, p.id)
                            .orElse(null);
                    if (rp == null) {
                        rp = new RolePermission();
                        rp.roleId = roles.get(i).id;
                        rp.permissionId = p.id;
                        rp.selfOnly = selfOnly;
                        rp.grantedBy = op.userId();
                        rp.grantedAt = LocalDateTime.now(clock);
                        rolePermissionMapper.save(rp);
                    } else if (rp.selfOnly != selfOnly) {
                        rp.selfOnly = selfOnly;
                        rolePermissionMapper.save(rp);
                        selfOnlyChanged++;
                        audit.record(op, "PERMISSION_ASSIGN", "ROLE", roles.get(i).id, roles.get(i).roleCode,
                                "permission=" + p.permissionCode + " selfOnly=" + selfOnly + " source=import",
                                AuditService.SUCCESS, null);
                    }
                    grants++;
                    perRole[i]++;
                }
            }
        }
        try {
            wb.close();
        } catch (IOException ignored) {
            // 输入流由调用方关闭
        }
        int revoked = 0;
        if (unresolvedRows) {
            notes.add("有功能点定位不到所属系统或模块，本次只新增与更新授权，未回收清单中去掉的授权");
        } else {
            for (int i = 0; i < roles.size(); i++) {
                revoked += revokeMissing(op, roles.get(i), wanted.get(i), untouched.get(i));
            }
        }
        counts.put("systems", systems);
        counts.put("modules", modules);
        counts.put("resources", resources);
        counts.put("permissions", permissions);
        counts.put("rolePermissions", grants);
        counts.put("rolePermissionsSelfOnlyChanged", selfOnlyChanged);
        counts.put("rolePermissionsRevoked", revoked);
        for (int i = 0; i < roles.size(); i++) {
            counts.put("rolePermissions." + roles.get(i).roleCode, perRole[i]);
        }
        notes.addAll(normalized);
        notes.add("每个功能点按五类操作建立权限（可配置集合），角色实际获得哪些由清单单元格决定");
        authz.bumpGrantVersion();
        audit.record(op, "IMPORT_PERMISSIONS", "RESOURCE", null, "功能权限清单", counts.toString(),
                anomalies.isEmpty() ? AuditService.SUCCESS : AuditService.FAILED,
                anomalies.isEmpty() ? null : anomalies.size() + " 个单元格异常");
        return new ImportReport(counts, notes, anomalies);
    }

    /**
     * 回收角色模板在业务系统上、最新清单里已不再要求的授权（权限中心自身的 rbac:* 权限不归清单管）。
     * 清单是角色模板业务权限的唯一来源，界面上给模板角色加的业务权限也会在下次导入时按清单回收。
     * 每回收一条记一条 PERMISSION_REVOKE，与界面上回收一致。
     */
    private int revokeMissing(Operator op, Role role, Map<Long, Boolean> wanted, Set<Long> untouched) {
        List<RolePermission> stale = new ArrayList<>();
        for (Object[] row : rolePermissionMapper.findBusinessGrantsOfRole(role.id)) {
            RolePermission rp = (RolePermission) row[0];
            if (!wanted.containsKey(rp.permissionId) && !untouched.contains((Long) row[1])) {
                stale.add(rp);
            }
        }
        rolePermissionMapper.deleteAll(stale);
        Map<Long, String> codes = new HashMap<>();
        permissionMapper.findAllById(stale.stream().map(rp -> rp.permissionId).toList())
                .forEach(p -> codes.put(p.id, p.permissionCode));
        for (RolePermission rp : stale) {
            audit.record(op, "PERMISSION_REVOKE", "ROLE", role.id, role.roleCode,
                    "permission=" + codes.get(rp.permissionId) + " source=import", AuditService.SUCCESS, null);
        }
        return stale.size();
    }

    /** 一个权限单元格的解析结果。error 非空表示该单元格无法使用。 */
    record ParsedCell(Set<String> actions, Set<String> selfOnly, String note, String error) {
    }

    /**
     * 解析单元格：「全部」→ 五类操作；「无」→ 不授权；「查看/编辑本人」→ 查看 + 编辑（只限本人）；
     * 「view/新增」中的英文取值归一为中文同义词；空白单元格不能默认当作「无」，列为异常。
     */
    static ParsedCell parseCell(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedCell(Set.of(), Set.of(), null, "空白权限单元格，未授权");
        }
        String v = raw.trim();
        if (v.equals("无")) {
            return new ParsedCell(Set.of(), Set.of(), null, null);
        }
        if (v.equals("全部")) {
            return new ParsedCell(new LinkedHashSet<>(BUSINESS_ACTIONS), Set.of(), null, null);
        }
        Set<String> acts = new LinkedHashSet<>();
        Set<String> self = new HashSet<>();
        String note = null;
        for (String token : v.split("/")) {
            String t = token.trim();
            boolean onlySelf = t.endsWith("本人");
            if (onlySelf) {
                t = t.substring(0, t.length() - 2);
            }
            String a = ACTION_WORDS.get(t);
            if (a == null) {
                return new ParsedCell(Set.of(), Set.of(), null, "无法识别的操作「" + token + "」");
            }
            if (t.equals("view")) {
                note = "单元格中的英文「view」已按「查看」处理";
            }
            acts.add(a);
            if (onlySelf) {
                self.add(a);
            }
        }
        return new ParsedCell(acts, self, note, null);
    }

    // ================================================================== 内部过程

    private Role roleTemplate(String column, int index) {
        String code = ROLE_CODES.getOrDefault(column, "ROLE_COL" + (index + 1));
        return roleMapper.findByRoleCode(code).orElseGet(() -> {
            Role r = new Role();
            r.roleCode = code;
            r.roleName = column;
            r.description = "权限清单「" + column + "」列的角色模板（全局角色）";
            return roleMapper.save(r);
        });
    }

    private SoftwareSystem system(String name) {
        String code = SYSTEM_CODES.get(name);
        if (code == null) {
            code = "sys" + (systemMapper.count() + 1);
        }
        String c = code;
        return systemMapper.findBySystemCode(code).orElseGet(() -> {
            SoftwareSystem s = new SoftwareSystem();
            s.systemCode = c;
            s.systemName = name;
            s.sortOrder = (int) systemMapper.count() + 1;
            return systemMapper.save(s);
        });
    }

    private FunctionModule module(SoftwareSystem sys, String name) {
        List<FunctionModule> existing = moduleMapper.findBySystemIdOrderBySortOrderAsc(sys.id);
        for (FunctionModule m : existing) {
            if (m.moduleName.equals(name)) {
                return m;
            }
        }
        FunctionModule m = new FunctionModule();
        m.systemId = sys.id;
        m.moduleCode = String.format("m%02d", existing.size() + 1);
        m.moduleName = name;
        m.sortOrder = existing.size() + 1;
        return moduleMapper.save(m);
    }

    /**
     * 功能点按「所属模块 + 名称」认领已有资源（同一模块里同名的按出现次序对应），不按行号：
     * 清单中间删掉或插入一行时，后面的功能点不会错认成别的资源，授权也就不会挪到别的功能点上。
     * 新功能点的编码取该模块已用的最大序号 + 1。
     */
    private Resource resource(SoftwareSystem sys, FunctionModule mod, int seq, int occurrence, String name,
                              String description) {
        List<Resource> existing = resourceMapper.findByModuleIdOrderByIdAsc(mod.id);
        int sameName = 0;
        int maxSeq = 0;
        String prefix = sys.systemCode + ":" + mod.moduleCode + ":f";
        for (Resource r : existing) {
            if (r.resourceName.equals(name) && ++sameName == occurrence) {
                return r;
            }
            if (r.resourceCode.startsWith(prefix) && r.resourceCode.substring(prefix.length()).matches("\\d+")) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(r.resourceCode.substring(prefix.length())));
            }
        }
        Resource r = new Resource();
        r.moduleId = mod.id;
        r.resourceCode = prefix + String.format("%02d", maxSeq + 1);
        r.resourceName = name;
        r.resourceType = Resource.BUSINESS;
        r.description = description;
        r.sortOrder = seq;
        return resourceMapper.save(r);
    }

    private static String sysName(SoftwareSystem s) {
        return s == null ? "?" : s.systemName;
    }

    /** 读出第一张表中表头之后的所有行（每个单元格转成去掉首尾空白的文本，空单元格为 null）。 */
    static List<List<String>> readSheet(InputStream xlsx, String headerMarker) {
        if (xlsx == null) {
            throw BizException.badRequest("缺少 xlsx 文件");
        }
        try (Workbook wb = WorkbookFactory.create(xlsx)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            int header = findHeaderRow(sheet, fmt, headerMarker);
            int width = sheet.getRow(header).getLastCellNum();
            List<List<String>> rows = new ArrayList<>();
            for (int r = header + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                boolean any = false;
                for (int c = 0; c < width; c++) {
                    String v = text(fmt, row.getCell(c));
                    any |= v != null;
                    cells.add(v);
                }
                if (any) {
                    rows.add(cells);
                }
            }
            return rows;
        } catch (IOException | RuntimeException e) {
            if (e instanceof BizException be) {
                throw be;
            }
            throw BizException.badRequest("无法读取 xlsx：" + e.getMessage());
        }
    }

    static int findHeaderRow(Sheet sheet, DataFormatter fmt, String marker) {
        for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), 10); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell c : row) {
                if (marker.equals(text(fmt, c))) {
                    return r;
                }
            }
        }
        throw BizException.badRequest("找不到表头「" + marker + "」，请确认上传的是正确的表格");
    }

    static String text(DataFormatter fmt, Cell c) {
        if (c == null) {
            return null;
        }
        String v = fmt.formatCellValue(c).trim();
        return v.isEmpty() ? null : v;
    }

    static String cell(List<String> row, int i) {
        return i < row.size() ? row.get(i) : null;
    }
}
