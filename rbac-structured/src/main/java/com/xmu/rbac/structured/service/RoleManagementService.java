package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.AssignmentRow;
import com.xmu.rbac.structured.dto.GrantRow;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.dto.PageResult;
import com.xmu.rbac.structured.dto.RoleDtos.CreateRole;
import com.xmu.rbac.structured.dto.RoleDtos.RoleDetail;
import com.xmu.rbac.structured.dto.RoleDtos.RolePermissionView;
import com.xmu.rbac.structured.dto.RoleDtos.RoleView;
import com.xmu.rbac.structured.dto.RoleDtos.UpdateRole;
import com.xmu.rbac.structured.dto.Scope;
import com.xmu.rbac.structured.entity.Department;
import com.xmu.rbac.structured.entity.Permission;
import com.xmu.rbac.structured.entity.Role;
import com.xmu.rbac.structured.entity.RolePermission;
import com.xmu.rbac.structured.mapper.DepartmentMapper;
import com.xmu.rbac.structured.mapper.PermissionMapper;
import com.xmu.rbac.structured.mapper.RoleMapper;
import com.xmu.rbac.structured.mapper.RolePermissionMapper;
import com.xmu.rbac.structured.mapper.UserRoleMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 角色管理：角色增删改、启用停用、角色权限的分配与回收（docs/06-detail-structured.md §5.6）。
 *
 * <p>角色修改影响所有持有者，所以按「公司级」管理：
 * 全局角色（companyId 为空）只有超级管理员能改；公司内角色需要操作者在该公司根部门持有相应权限。
 * 给角色授权时同样不能授出自己没有的权限。
 */
@Service
public class RoleManagementService {

    public static final String PERM_VIEW = "rbac:role:view";
    public static final String PERM_CREATE = "rbac:role:create";
    public static final String PERM_EDIT = "rbac:role:edit";
    public static final String PERM_DELETE = "rbac:role:delete";
    public static final String PERM_GRANT = "rbac:grant:assign";
    public static final String PERM_UNGRANT = "rbac:grant:revoke";

    // 编码全局唯一、创建后不在更新 DTO 中开放修改；长度 2～64，以大写字母开头。
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final DepartmentMapper departmentMapper;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    public RoleManagementService(RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper,
                                 PermissionMapper permissionMapper, UserRoleMapper userRoleMapper,
                                 DepartmentMapper departmentMapper, AuthorizationService authz, AuditService audit,
                                 Clock clock) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.departmentMapper = departmentMapper;
        this.authz = authz;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 查询

    /**
     * D01：角色列表/指派下拉框；全局角色可见，公司角色按操作者持有 role:view 的授权所在公司筛选。
     * 读取全部角色后在 Java 中筛选，不是数据库分页；可见不等于可以修改。
     */
    @Transactional(readOnly = true)
    public List<RoleView> list(Operator op, String keyword, String status) {
        // 1. 查可见公司集合；超级管理员不受此集合限制。
        Set<Long> companies = companiesOf(op, PERM_VIEW);
        boolean all = authz.isSuperAdmin(op.userId());
        // 2. 两次分组统计避免逐角色计数：用户数 count(distinct userId)，权限数 count(RolePermission)。
        // 这里是已有关系的数量，没有另外按用户状态或指派可见范围过滤。
        Map<Long, Long> users = toCountMap(roleMapper.countUsersByRole());
        Map<Long, Long> perms = toCountMap(roleMapper.countPermissionsByRole());
        // 3. 依次筛可见性、编码/名称关键字、状态，再组装 DTO；没有统计行的角色补 0。
        return roleMapper.findAllByOrderByIdAsc().stream()
                .filter(r -> all || r.companyId == null || companies.contains(r.companyId))
                .filter(r -> keyword == null || keyword.isBlank()
                        || r.roleCode.contains(keyword.trim()) || r.roleName.contains(keyword.trim()))
                .filter(r -> status == null || status.isBlank() || r.status.equals(status))
                .map(r -> toView(r, users.getOrDefault(r.id, 0L), perms.getOrDefault(r.id, 0L)))
                .toList();
    }

    /** D03：先检查角色可见性，再查全部权限；前端“权限”页签的搜索和分页基于这份完整结果。 */
    @Transactional(readOnly = true)
    public RoleDetail detail(Operator op, Long roleId) {
        Role r = requireVisible(op, roleId);
        // Mapper 连 RolePermission、Permission、Resource、Action，按权限码排序。
        // 投影顺序：[permissionId, permissionCode, resourceName, actionName, selfOnly, grantedAt]。
        List<RolePermissionView> perms = rolePermissionMapper.findPermissionDetailsOfRole(roleId).stream()
                .map(row -> new RolePermissionView((Long) row[0], (String) row[1], (String) row[2], (String) row[3],
                        (Boolean) row[4], (LocalDateTime) row[5]))
                .toList();
        // 注意口径：这里 userCount 是指派条数（未 distinct），与 D01 的去重用户数不同。
        return new RoleDetail(toView(r, userRoleMapper.countByRoleId(roleId), perms.size()), perms);
    }

    /**
     * D06，角色详情「用户」：持有该角色的指派，只列操作者能看到的适用部门里的。
     * 超级管理员直接在库里分页；其他人先按管理范围过滤全部指派再分页，保证总数与每页内容一致。
     * 一行是一条“用户 + 角色 + 适用部门”指派，不是一个去重用户；角色停用不代表指派记录已删除。
     */
    @Transactional(readOnly = true)
    public PageResult<AssignmentRow> users(Operator op, Long roleId, int page, int size) {
        // 1. 先检查角色所属公司可见性，再修正分页边界：page >= 0，1 <= size <= 200。
        requireVisible(op, roleId);
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 200);
        // 2. role:view 的部门范围决定能看哪些指派；比较的是指派适用部门，不是用户所属部门。
        Scope scope = authz.scopeOf(op.userId(), PERM_VIEW);
        if (scope.all()) {
            // 超级管理员无需内存过滤，直接由 Mapper 返回数据库分页和总条数。
            return PageResult.of(userRoleMapper.findAssignmentsOfRole(roleId, PageRequest.of(p, s)), a -> a);
        }
        // 3. 普通管理员先过滤再截页；若先查一页再过滤，会导致页数和 total 与可见内容不一致。
        List<AssignmentRow> visible = userRoleMapper.findAllAssignmentsOfRole(roleId).stream()
                .filter(a -> AuthorizationService.inScope(scope, a.scopePath()))
                .toList();
        int from = Math.min(p * s, visible.size());
        return new PageResult<>(visible.subList(from, Math.min(from + s, visible.size())), visible.size(), p, s);
    }

    // ------------------------------------------------------------------ 增删改

    /** D02：新增空角色。记忆顺序：校验输入 → 公司级资格 → 编码查重 → 保存 → 审计。 */
    @Transactional
    public RoleView create(Operator op, CreateRole req) {
        // 1. 编码匹配固定格式，名称不能空白；编码本身不 trim，名称保存时 trim。
        if (req == null || req.roleCode() == null || !CODE.matcher(req.roleCode()).matches()
                || req.roleName() == null || req.roleName().isBlank()) {
            throw BizException.badRequest("roleCode 为大写字母开头的大写字母、数字、下划线；roleName 必填");
        }
        // 2. 全局角色要求超级管理员；公司角色要求 role:create 的范围覆盖公司根部门。
        requireCompanyLevel(op, PERM_CREATE, req.companyId());
        // 3. 预先查重给出 409；数据库 role_code 唯一键兜住并发创建相同编码。
        if (roleMapper.existsByRoleCode(req.roleCode())) {
            throw BizException.conflict(BizException.DUPLICATE, "角色编码已存在");
        }
        // 4. Role 默认 ENABLED、builtin=false；新角色没有权限和持有者，不必更新授权版本。
        Role r = new Role();
        r.companyId = req.companyId();
        r.roleCode = req.roleCode();
        r.roleName = req.roleName().trim();
        r.description = req.description();
        roleMapper.save(r);
        audit.record(op, "ROLE_CREATE", "ROLE", r.id, r.roleCode, "companyId=" + r.companyId,
                AuditService.SUCCESS, null);
        return toView(r, 0, 0);
    }

    /** D04：编辑/启停共享角色；只改变名称、描述、状态，不改变编码、所属公司或已有指派。 */
    @Transactional
    public RoleView update(Operator op, Long roleId, UpdateRole req) {
        // 1. 查目标角色（不存在 404），再检查该角色公司的修改权限，不能只依赖入口 role:edit。
        Role r = roleMapper.findById(roleId).orElseThrow(() -> BizException.notFound("角色"));
        requireCompanyLevel(op, PERM_EDIT, r.companyId);
        // 2. 超级管理员角色不能被停用，记录拒绝审计后抛 40902；并非禁止修改其所有字段。
        if (Role.SUPER_ADMIN.equals(r.roleCode) && req.status() != null && !Role.ENABLED.equals(req.status())) {
            audit.record(op, "ROLE_UPDATE", "ROLE", r.id, r.roleCode, "status -> " + req.status(),
                    AuditService.DENIED, "SUPER_ADMIN_ROLE_PROTECTED");
            throw BizException.conflict(BizException.STATE_CONFLICT, "超级管理员角色不能停用");
        }
        // 3. PATCH 部分更新：null 保留旧值，空白名称也保留；描述空字符串则可清空。
        String before = r.status;
        if (req.roleName() != null && !req.roleName().isBlank()) {
            r.roleName = req.roleName().trim();
        }
        if (req.description() != null) {
            r.description = req.description();
        }
        if (req.status() != null) {
            if (!Role.ENABLED.equals(req.status()) && !Role.DISABLED.equals(req.status())) {
                throw BizException.badRequest("status 只能是 ENABLED 或 DISABLED");
            }
            r.status = req.status();
        }
        // 4. 保存共享角色；只有状态实际变化才更新授权版本，仅改名称/描述不触发失效。
        roleMapper.save(r);
        if (!before.equals(r.status)) {
            authz.bumpGrantVersion();   // 提交后新请求不再获得“该角色贡献”的权限，其他角色的授权仍可生效。
        }
        audit.record(op, "ROLE_UPDATE", "ROLE", r.id, r.roleCode, "status " + before + " -> " + r.status,
                AuditService.SUCCESS, null);
        // 5. 返回最新基本信息；此处 userCount 同详情一样按指派条数统计。
        return toView(r, userRoleMapper.countByRoleId(r.id), rolePermissionMapper.countByRoleId(r.id));
    }

    /** D05：删除角色。还有指派时拒绝，要求先显式回收，避免删除共享角色时隐式撤掉用户授权。 */
    @Transactional
    public void delete(Operator op, Long roleId) {
        // 1. 查角色、验公司级 role:delete；builtin 与 companyId 是不同属性，分别承担保护和范围语义。
        Role r = roleMapper.findById(roleId).orElseThrow(() -> BizException.notFound("角色"));
        requireCompanyLevel(op, PERM_DELETE, r.companyId);
        if (r.builtin) {
            audit.record(op, "ROLE_DELETE", "ROLE", r.id, r.roleCode, null, AuditService.DENIED, "BUILTIN_ROLE");
            throw BizException.conflict(BizException.STATE_CONFLICT, "内置角色不能删除");
        }
        // 2. 任意保留的指派都会阻止删除，不因用户/角色停用或用户软删除就忽略关系。
        long holders = userRoleMapper.countByRoleId(roleId);
        if (holders > 0) {
            audit.record(op, "ROLE_DELETE", "ROLE", r.id, r.roleCode, null, AuditService.DENIED, "ROLE_IN_USE");
            throw BizException.conflict(BizException.STATE_CONFLICT, "仍有 " + holders + " 个指派使用该角色，请先回收");
        }
        // 3. 无指派才允许删除：数据库级联清理角色权限关联，权限定义仍保留。
        // 没有用户持有此角色，因此此路径没有调用 bumpGrantVersion。
        roleMapper.delete(r);   // 角色权限随外键 ON DELETE CASCADE 一并删除
        audit.record(op, "ROLE_DELETE", "ROLE", r.id, r.roleCode, null, AuditService.SUCCESS, null);
    }

    // ------------------------------------------------------------------ 角色权限

    /**
     * D07：追加角色权限（PERMISSION_ASSIGN），影响该共享角色的所有持有者，不是给个人指派角色。
     * 记忆顺序：查角色/验输入 → 验公司级管理权 → 查齐权限 → 保护超级角色 → 验授权上界 → 追加 → 版本/详情。
     */
    @Transactional
    public RoleDetail grant(Operator op, Long roleId, List<Long> permissionIds) {
        // 1. 先查角色，再拒绝 null/空权限集合；未传本人的适用部门，操作对象是整个角色定义。
        Role r = roleMapper.findById(roleId).orElseThrow(() -> BizException.notFound("角色"));
        if (permissionIds == null || permissionIds.isEmpty()) {
            throw BizException.badRequest("permissionIds 不能为空");
        }
        // 2. “能管理此角色”与“能授出所选权限”是两道检查，这里先检查 grant:assign 的公司级范围。
        requireCompanyLevel(op, PERM_GRANT, r.companyId);
        // 3. 请求 ID 去重后一次查齐，数量不符说明部分权限不存在；开始写入前整批拒绝。
        List<Permission> perms = permissionMapper.findAllById(new LinkedHashSet<>(permissionIds));
        if (perms.size() != new LinkedHashSet<>(permissionIds).size()) {
            throw BizException.notFound("部分权限");
        }
        // 4. SUPER_ADMIN 走授权引擎旁路，不靠显式权限集合，拒绝向它添加权限。
        if (Role.SUPER_ADMIN.equals(r.roleCode)) {
            audit.record(op, "PERMISSION_ASSIGN", "ROLE", r.id, r.roleCode, null, AuditService.DENIED,
                    "SUPER_ADMIN_ROLE_PROTECTED");
            throw BizException.conflict(BizException.STATE_CONFLICT, "超级管理员越过权限引擎，不需要也不接受授权");
        }
        // 5. 授权上界：每个待授权限都须有一条覆盖公司根部门、且不限本人的自身授权；超级管理员例外。
        // 这是对全部请求权限的预检，早于循环中“已有授权跳过”的处理。
        String missing = firstNotHeld(op, r.companyId, perms.stream().map(p -> p.permissionCode).toList());
        if (missing != null) {
            audit.record(op, "PERMISSION_ASSIGN", "ROLE", r.id, r.roleCode, "permission=" + missing,
                    AuditService.DENIED, "PRIVILEGE_ESCALATION");
            throw BizException.forbidden(BizException.PRIVILEGE_ESCALATION, "不能授出你自己没有的权限（只有「本人」范围也不行）：" + missing);
        }
        // 6. 已存在的关联跳过，不改变其 selfOnly 或授予时间；新关联按 Java 默认 selfOnly=false 建立。
        // (role_id, permission_id) 唯一键防并发重复；一条新增关联对应一条 PERMISSION_ASSIGN 审计。
        int added = 0;
        for (Permission p : perms) {
            if (rolePermissionMapper.existsByRoleIdAndPermissionId(r.id, p.id)) {
                continue;
            }
            RolePermission rp = new RolePermission();
            rp.roleId = r.id;
            rp.permissionId = p.id;
            rp.grantedBy = op.userId();
            rp.grantedAt = LocalDateTime.now(clock);
            rolePermissionMapper.save(rp);
            added++;
            audit.record(op, "PERMISSION_ASSIGN", "ROLE", r.id, r.roleCode, "permission=" + p.permissionCode,
                    AuditService.SUCCESS, null);
        }
        // 7. 真有新增才提升全局授权版本；重复提交不重复建关系，也不无意义地提升版本。
        if (added > 0) {
            authz.bumpGrantVersion();
        }
        // 8. 在当前写事务内复用详情组装过程（内部调用不新开只读事务）；detail 仍会校验角色可见性。
        return detail(op, roleId);
    }

    /**
     * D08：回收一个角色权限（PERMISSION_REVOKE）；提交后持有者失去该角色贡献的这项权限。
     * 若用户另有角色授予同一权限，仍可能获准；不删除权限定义或用户角色指派，也不要求操作者持有被回收权限。
     */
    @Transactional
    public void ungrant(Operator op, Long roleId, Long permissionId) {
        // 1. 查角色并验公司级 grant:revoke；与 grant 一样不能只靠入口权限放行。
        Role r = roleMapper.findById(roleId).orElseThrow(() -> BizException.notFound("角色"));
        requireCompanyLevel(op, PERM_UNGRANT, r.companyId);
        // 2. 按角色 ID + 权限 ID 找关联；定义存在但未授予此角色也报 404。
        RolePermission rp = rolePermissionMapper.findByRoleIdAndPermissionId(roleId, permissionId)
                .orElseThrow(() -> BizException.notFound("角色权限"));
        // 3. 查权限码供审计显示，删除关联并提升版本；二者在同一业务事务中提交/回滚。
        String code = permissionMapper.findById(permissionId).map(p -> p.permissionCode).orElse(null);
        rolePermissionMapper.delete(rp);
        authz.bumpGrantVersion();
        audit.record(op, "PERMISSION_REVOKE", "ROLE", r.id, r.roleCode, "permission=" + code,
                AuditService.SUCCESS, null);
    }

    // ------------------------------------------------------------------ 校验过程

    /**
     * 公司级操作：全局角色只有超级管理员能改；公司内角色要求操作者在该公司根部门持有权限。
     * 看得到某公司的角色不代表能修改它；子部门授权不能反向覆盖公司根部门。
     */
    void requireCompanyLevel(Operator op, String permission, Long companyId) {
        // 先处理超级管理员旁路，再处理全局角色保护，最后对公司根部门做对象范围判定。
        if (authz.isSuperAdmin(op.userId())) {
            return;
        }
        if (companyId == null) {
            audit.record(op, AuthorizationService.ACCESS_DENIED, "ROLE", null, null, "permission=" + permission,
                    AuditService.DENIED, "GLOBAL_ROLE_SUPER_ADMIN_ONLY");
            throw BizException.forbidden(BizException.FORBIDDEN, "全局角色只有超级管理员能修改");
        }
        Department root = departmentMapper.findByCompanyIdAndParentIdIsNull(companyId)
                .orElseThrow(() -> BizException.notFound("公司"));
        // 此处按 decide.allowed 检查管理权限的覆盖范围；待授业务权限的 !selfOnly 校验在 firstNotHeld。
        authz.requireInScope(op, permission, root.id);
    }

    /**
     * 授权给公司内角色时，每个权限操作者都必须在公司根部门持有，且不能只限本人
     * （这里授出的都是完整权限，selfOnly 只由权限清单导入设置）；返回第一个不满足的权限码。
     * 只检查是否存在一条同时满足“同权限码 + 不限本人 + 覆盖公司根”的授权，不能拆开不同授权的条件来拼接。
     */
    private String firstNotHeld(Operator op, Long companyId, List<String> codes) {
        if (authz.isSuperAdmin(op.userId())) {
            return null;
        }
        Department root = departmentMapper.findByCompanyIdAndParentIdIsNull(companyId).orElseThrow();
        // 一次加载操作者展开授权，在内存逐权限比较；loadGrants 内部处理版本号、Redis 和数据库回退。
        List<GrantRow> mine = authz.loadGrants(op.userId()).grants().grants();
        for (String c : codes) {
            boolean held = mine.stream().anyMatch(g -> g.permissionCode().equals(c) && !g.selfOnly()
                    && AuthorizationService.covers(g.scopePath(), root.path));
            if (!held) {
                return c;
            }
        }
        return null;
    }

    /** D03/D06 共用：先查存在性，再验公司可见性；全局角色可查看，但修改仍受 requireCompanyLevel 限制。 */
    private Role requireVisible(Operator op, Long roleId) {
        Role r = roleMapper.findById(roleId).orElseThrow(() -> BizException.notFound("角色"));
        if (r.companyId != null && !authz.isSuperAdmin(op.userId())
                && !companiesOf(op, PERM_VIEW).contains(r.companyId)) {
            throw BizException.forbidden(BizException.OUT_OF_MANAGE_SCOPE, "该角色属于你管理范围以外的公司");
        }
        return r;
    }

    /**
     * 提取操作者指定权限的指派适用公司，不是操作者账号的所属公司。
     * 这是较宽的“公司角色可见性”口径，不要求覆盖公司根部门，也不在此过滤 selfOnly。
     */
    private Set<Long> companiesOf(Operator op, String permission) {
        Set<Long> out = new java.util.HashSet<>();
        authz.loadGrants(op.userId()).grants().grants().stream()
                .filter(g -> g.permissionCode().equals(permission))
                .forEach(g -> out.add(g.scopeCompanyId()));
        return out;
    }

    /** 将分组计数投影 [roleId, count] 转为映射，便于列表组装时按角色 ID 取值。 */
    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        rows.forEach(row -> m.put((Long) row[0], (Long) row[1]));
        return m;
    }

    /** 仅组装 DTO；users 的统计口径由调用方决定，本方法不会再查库或将指派数去重。 */
    static RoleView toView(Role r, long users, long perms) {
        return new RoleView(r.id, r.roleCode, r.roleName, r.description, r.status, r.builtin, r.companyId,
                users, perms);
    }
}
