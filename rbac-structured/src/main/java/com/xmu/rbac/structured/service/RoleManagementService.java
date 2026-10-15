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

    /** 角色列表：全局角色 + 操作者能管理的公司的公司内角色，附用户数与权限数。 */
    @Transactional(readOnly = true)
    public List<RoleView> list(Operator op, String keyword, String status) {
        Set<Long> companies = companiesOf(op, PERM_VIEW);
        boolean all = authz.isSuperAdmin(op.userId());
        Map<Long, Long> users = toCountMap(roleMapper.countUsersByRole());
        Map<Long, Long> perms = toCountMap(roleMapper.countPermissionsByRole());
        return roleMapper.findAllByOrderByIdAsc().stream()
                .filter(r -> all || r.companyId == null || companies.contains(r.companyId))
                .filter(r -> keyword == null || keyword.isBlank()
                        || r.roleCode.contains(keyword.trim()) || r.roleName.contains(keyword.trim()))
                .filter(r -> status == null || status.isBlank() || r.status.equals(status))
                .map(r -> toView(r, users.getOrDefault(r.id, 0L), perms.getOrDefault(r.id, 0L)))
                .toList();
    }

    /** 角色详情：基本信息与权限。 */
    @Transactional(readOnly = true)
    public RoleDetail detail(Operator op, Long roleId) {
        Role r = requireVisible(op, roleId);
        List<RolePermissionView> perms = rolePermissionMapper.findPermissionDetailsOfRole(roleId).stream()
                .map(row -> new RolePermissionView((Long) row[0], (String) row[1], (String) row[2], (String) row[3],
                        (Boolean) row[4], (LocalDateTime) row[5]))
                .toList();
        return new RoleDetail(toView(r, userRoleMapper.countByRoleId(roleId), perms.size()), perms);
    }

    /**
     * 角色详情「用户」：持有该角色的指派，只列操作者能看到的部门里的。
     * 超级管理员直接在库里分页；其他人先按管理范围过滤全部指派再分页，保证总数与每页内容一致。
     */
    @Transactional(readOnly = true)
    public PageResult<AssignmentRow> users(Operator op, Long roleId, int page, int size) {
        requireVisible(op, roleId);
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 200);
        Scope scope = authz.scopeOf(op.userId(), PERM_VIEW);
        if (scope.all()) {
            return PageResult.of(userRoleMapper.findAssignmentsOfRole(roleId, PageRequest.of(p, s)), a -> a);
        }
        List<AssignmentRow> visible = userRoleMapper.findAllAssignmentsOfRole(roleId).stream()
                .filter(a -> AuthorizationService.inScope(scope, a.scopePath()))
                .toList();
        int from = Math.min(p * s, visible.size());
        return new PageResult<>(visible.subList(from, Math.min(from + s, visible.size())), visible.size(), p, s);
    }

    // ------------------------------------------------------------------ 增删改

    @Transactional
    public RoleView create(Operator op, CreateRole req) {
        if (req == null || req.roleCode() == null || !CODE.matcher(req.roleCode()).matches()
                || req.roleName() == null || req.roleName().isBlank()) {
            throw BizException.badRequest("roleCode 为大写字母开头的大写字母、数字、下划线；roleName 必填");
        }
        requireCompanyLevel(op, PERM_CREATE, req.companyId());
        if (roleMapper.existsByRoleCode(req.roleCode())) {
            throw BizException.conflict(BizException.DUPLICATE, "角色编码已存在");
        }
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

    @Transactional
    public RoleView update(Operator op, Long roleId, UpdateRole req) {
        Role r = roleMapper.findById(roleId).orElseThrow(() -> BizException.notFound("角色"));
        requireCompanyLevel(op, PERM_EDIT, r.companyId);
        if (Role.SUPER_ADMIN.equals(r.roleCode) && req.status() != null && !Role.ENABLED.equals(req.status())) {
            audit.record(op, "ROLE_UPDATE", "ROLE", r.id, r.roleCode, "status -> " + req.status(),
                    AuditService.DENIED, "SUPER_ADMIN_ROLE_PROTECTED");
            throw BizException.conflict(BizException.STATE_CONFLICT, "超级管理员角色不能停用");
        }
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
        roleMapper.save(r);
        if (!before.equals(r.status)) {
            authz.bumpGrantVersion();   // 停用角色后，持有者下一次请求即失去其权限
        }
        audit.record(op, "ROLE_UPDATE", "ROLE", r.id, r.roleCode, "status " + before + " -> " + r.status,
                AuditService.SUCCESS, null);
        return toView(r, userRoleMapper.countByRoleId(r.id), rolePermissionMapper.countByRoleId(r.id));
    }

    /** 删除角色。还有人持有时拒绝：权限一律人工回收，不能借删除角色悄悄收回别人的权限。 */
    @Transactional
    public void delete(Operator op, Long roleId) {
        Role r = roleMapper.findById(roleId).orElseThrow(() -> BizException.notFound("角色"));
        requireCompanyLevel(op, PERM_DELETE, r.companyId);
        if (r.builtin) {
            audit.record(op, "ROLE_DELETE", "ROLE", r.id, r.roleCode, null, AuditService.DENIED, "BUILTIN_ROLE");
            throw BizException.conflict(BizException.STATE_CONFLICT, "内置角色不能删除");
        }
        long holders = userRoleMapper.countByRoleId(roleId);
        if (holders > 0) {
            audit.record(op, "ROLE_DELETE", "ROLE", r.id, r.roleCode, null, AuditService.DENIED, "ROLE_IN_USE");
            throw BizException.conflict(BizException.STATE_CONFLICT, "仍有 " + holders + " 个指派使用该角色，请先回收");
        }
        roleMapper.delete(r);   // 角色权限随外键 ON DELETE CASCADE 一并删除
        audit.record(op, "ROLE_DELETE", "ROLE", r.id, r.roleCode, null, AuditService.SUCCESS, null);
    }

    // ------------------------------------------------------------------ 角色权限

    /** 给角色授予权限（PERMISSION_ASSIGN）。已有的跳过；操作者必须在公司根部门持有这些权限。 */
    @Transactional
    public RoleDetail grant(Operator op, Long roleId, List<Long> permissionIds) {
        Role r = roleMapper.findById(roleId).orElseThrow(() -> BizException.notFound("角色"));
        if (permissionIds == null || permissionIds.isEmpty()) {
            throw BizException.badRequest("permissionIds 不能为空");
        }
        requireCompanyLevel(op, PERM_GRANT, r.companyId);
        List<Permission> perms = permissionMapper.findAllById(new LinkedHashSet<>(permissionIds));
        if (perms.size() != new LinkedHashSet<>(permissionIds).size()) {
            throw BizException.notFound("部分权限");
        }
        if (Role.SUPER_ADMIN.equals(r.roleCode)) {
            audit.record(op, "PERMISSION_ASSIGN", "ROLE", r.id, r.roleCode, null, AuditService.DENIED,
                    "SUPER_ADMIN_ROLE_PROTECTED");
            throw BizException.conflict(BizException.STATE_CONFLICT, "超级管理员越过权限引擎，不需要也不接受授权");
        }
        String missing = firstNotHeld(op, r.companyId, perms.stream().map(p -> p.permissionCode).toList());
        if (missing != null) {
            audit.record(op, "PERMISSION_ASSIGN", "ROLE", r.id, r.roleCode, "permission=" + missing,
                    AuditService.DENIED, "PRIVILEGE_ESCALATION");
            throw BizException.forbidden(BizException.PRIVILEGE_ESCALATION, "不能授出你自己没有的权限（只有「本人」范围也不行）：" + missing);
        }
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
        if (added > 0) {
            authz.bumpGrantVersion();
        }
        return detail(op, roleId);
    }

    /** 回收角色的一个权限（PERMISSION_REVOKE），持有该角色的全部用户下一次请求即失去它。 */
    @Transactional
    public void ungrant(Operator op, Long roleId, Long permissionId) {
        Role r = roleMapper.findById(roleId).orElseThrow(() -> BizException.notFound("角色"));
        requireCompanyLevel(op, PERM_UNGRANT, r.companyId);
        RolePermission rp = rolePermissionMapper.findByRoleIdAndPermissionId(roleId, permissionId)
                .orElseThrow(() -> BizException.notFound("角色权限"));
        String code = permissionMapper.findById(permissionId).map(p -> p.permissionCode).orElse(null);
        rolePermissionMapper.delete(rp);
        authz.bumpGrantVersion();
        audit.record(op, "PERMISSION_REVOKE", "ROLE", r.id, r.roleCode, "permission=" + code,
                AuditService.SUCCESS, null);
    }

    // ------------------------------------------------------------------ 校验过程

    /**
     * 公司级操作：全局角色只有超级管理员能改；公司内角色要求操作者在该公司根部门持有权限。
     */
    void requireCompanyLevel(Operator op, String permission, Long companyId) {
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
        authz.requireInScope(op, permission, root.id);
    }

    /**
     * 授权给公司内角色时，每个权限操作者都必须在公司根部门持有，且不能只限本人
     * （这里授出的都是完整权限，selfOnly 只由权限清单导入设置）；返回第一个不满足的权限码。
     */
    private String firstNotHeld(Operator op, Long companyId, List<String> codes) {
        if (authz.isSuperAdmin(op.userId())) {
            return null;
        }
        Department root = departmentMapper.findByCompanyIdAndParentIdIsNull(companyId).orElseThrow();
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

    private Role requireVisible(Operator op, Long roleId) {
        Role r = roleMapper.findById(roleId).orElseThrow(() -> BizException.notFound("角色"));
        if (r.companyId != null && !authz.isSuperAdmin(op.userId())
                && !companiesOf(op, PERM_VIEW).contains(r.companyId)) {
            throw BizException.forbidden(BizException.OUT_OF_MANAGE_SCOPE, "该角色属于你管理范围以外的公司");
        }
        return r;
    }

    /** 操作者持有某权限的那些指派所在的公司。 */
    private Set<Long> companiesOf(Operator op, String permission) {
        Set<Long> out = new java.util.HashSet<>();
        authz.loadGrants(op.userId()).grants().grants().stream()
                .filter(g -> g.permissionCode().equals(permission))
                .forEach(g -> out.add(g.scopeCompanyId()));
        return out;
    }

    private static Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        rows.forEach(row -> m.put((Long) row[0], (Long) row[1]));
        return m;
    }

    static RoleView toView(Role r, long users, long perms) {
        return new RoleView(r.id, r.roleCode, r.roleName, r.description, r.status, r.builtin, r.companyId,
                users, perms);
    }
}
