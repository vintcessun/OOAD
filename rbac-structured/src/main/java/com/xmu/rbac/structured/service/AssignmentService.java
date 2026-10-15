package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.AssignmentRow;
import com.xmu.rbac.structured.dto.BatchDtos;
import com.xmu.rbac.structured.dto.BatchDtos.BatchAssign;
import com.xmu.rbac.structured.dto.BatchDtos.BatchResult;
import com.xmu.rbac.structured.dto.BatchDtos.Failure;
import com.xmu.rbac.structured.dto.GrantRow;
import com.xmu.rbac.structured.dto.GrantSet;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.dto.Scope;
import com.xmu.rbac.structured.dto.UserDtos.Assign;
import com.xmu.rbac.structured.entity.Company;
import com.xmu.rbac.structured.entity.Department;
import com.xmu.rbac.structured.entity.Role;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.entity.UserRole;
import com.xmu.rbac.structured.mapper.CompanyMapper;
import com.xmu.rbac.structured.mapper.DepartmentMapper;
import com.xmu.rbac.structured.mapper.RoleMapper;
import com.xmu.rbac.structured.mapper.RolePermissionMapper;
import com.xmu.rbac.structured.mapper.UserMapper;
import com.xmu.rbac.structured.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 5.0 授权管理：人工分配与回收用户角色，单个与批量（docs/06-detail-structured.md §5.4、§5.5）。
 *
 * <p>一次指派需要通过的校验（单个与批量共用 {@link #checkOne}）：
 * <ol>
 *   <li>用户存在且未删除；角色存在且启用；</li>
 *   <li>适用部门与用户在同一家公司；公司内角色只能指派到本公司；</li>
 *   <li>操作者对用户所属部门、对适用部门都持有 rbac:assignment:assign（管理范围）；</li>
 *   <li>超级管理员角色只有超级管理员能分配；</li>
 *   <li>不能授出自己没有的权限：角色的每个权限，操作者在适用部门都必须持有。</li>
 * </ol>
 * 回收只检查管理范围；回收自己的指派连管理范围也不查（{@link #checkRevoke}）。
 */
@Service
public class AssignmentService {

    public static final String PERM_ASSIGN = "rbac:assignment:assign";
    public static final String PERM_REVOKE = "rbac:assignment:revoke";

    static final int CHUNK = 1000;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final DepartmentMapper departmentMapper;
    private final CompanyMapper companyMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final Clock clock;

    public AssignmentService(UserMapper userMapper, RoleMapper roleMapper, DepartmentMapper departmentMapper,
                             CompanyMapper companyMapper, UserRoleMapper userRoleMapper,
                             RolePermissionMapper rolePermissionMapper, AuthorizationService authz,
                             AuditService audit, Clock clock) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.departmentMapper = departmentMapper;
        this.companyMapper = companyMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.authz = authz;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * 一批校验共用的上下文：角色、操作者的管理范围，以及「这个适用部门能否授出该角色」的判定缓存。
     * rolePermissions：角色的权限码 → 是否只限本人。
     */
    record Context(Operator op, Role role, Map<String, Boolean> rolePermissions, boolean operatorIsSuperAdmin,
                   Scope operatorScope, List<GrantRow> operatorGrants, Map<Long, Department> depts,
                   Map<Long, String> escalationByScope) {
    }

    // ------------------------------------------------------------------ 5.1 查询

    @Transactional(readOnly = true)
    public List<AssignmentRow> listOfUser(Operator op, Long userId) {
        User u = userMapper.findById(userId).orElseThrow(() -> BizException.notFound("用户"));
        if (!op.userId().equals(userId)) {
            authz.requireInScope(op.userId(), UserManagementService.PERM_VIEW, u.departmentId);
        }
        return userRoleMapper.findAssignmentsOfUser(userId);
    }

    // ------------------------------------------------------------------ 5.2 单个分配

    @Transactional
    public AssignmentRow assign(Operator op, Long userId, Assign req) {
        if (req == null || req.roleId() == null) {
            throw BizException.badRequest("roleId 必填");
        }
        User u = userMapper.findById(userId).orElseThrow(() -> BizException.notFound("用户"));
        Role role = roleMapper.findById(req.roleId()).orElseThrow(() -> BizException.notFound("角色"));
        Long scopeId = req.scopeDepartmentId() == null ? u.departmentId : req.scopeDepartmentId();
        Context ctx = context(op, role);
        String failure = checkOne(ctx, u, scopeId);
        String detail = "role=" + role.roleCode + " scope=" + deptName(ctx, scopeId);
        if (failure != null) {
            audit.record(op, "ROLE_ASSIGN", "USER", u.id, u.username, detail, AuditService.DENIED, failure);
            throw failureException(failure);
        }
        if (userRoleMapper.existsByUserIdAndRoleIdAndScopeDeptId(u.id, role.id, scopeId)) {
            throw BizException.conflict(BizException.DUPLICATE, "该用户在该部门已持有此角色");
        }
        UserRole ur = new UserRole();
        ur.userId = u.id;
        ur.roleId = role.id;
        ur.scopeDeptId = scopeId;
        ur.grantedBy = op.userId();
        ur.grantedAt = LocalDateTime.now(clock);
        ur.reason = req.reason();
        userRoleMapper.save(ur);
        authz.bumpGrantVersion();
        audit.record(op, "ROLE_ASSIGN", "USER", u.id, u.username, detail, AuditService.SUCCESS, req.reason());
        return userRoleMapper.findAssignmentsOfUser(u.id).stream()
                .filter(a -> a.assignmentId().equals(ur.id)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ 5.3 单个回收

    /**
     * 回收一次指派。只删这一条：被回收者过去授给别人的指派不受影响（granted_by 只是审计属性），
     * 这就是需求「移除管理员权限，不会导致分配的权限级联删除」。
     */
    @Transactional
    public void revoke(Operator op, Long userId, Long assignmentId, String reason) {
        UserRole ur = userRoleMapper.findById(assignmentId)
                .filter(a -> a.userId.equals(userId))
                .orElseThrow(() -> BizException.notFound("指派"));
        User u = userMapper.findById(userId).orElseThrow(() -> BizException.notFound("用户"));
        Role role = roleMapper.findById(ur.roleId).orElseThrow(() -> BizException.notFound("角色"));
        String failure = checkRevoke(op, u, role, ur.scopeDeptId);
        String detail = "role=" + role.roleCode + " scopeDeptId=" + ur.scopeDeptId;
        if (failure != null) {
            audit.record(op, "ROLE_REVOKE", "USER", u.id, u.username, detail, AuditService.DENIED, failure);
            throw failureException(failure);
        }
        userRoleMapper.delete(ur);
        userRoleMapper.flush();
        if (Role.SUPER_ADMIN.equals(role.roleCode) && userRoleMapper.countActiveSuperAdmins() == 0) {
            audit.record(op, "ROLE_REVOKE", "USER", u.id, u.username, detail, AuditService.DENIED, "LAST_SUPER_ADMIN");
            throw BizException.conflict(BizException.STATE_CONFLICT, "不能回收最后一个有效的超级管理员");
        }
        authz.bumpGrantVersion();
        audit.record(op, "ROLE_REVOKE", "USER", u.id, u.username, detail, AuditService.SUCCESS, reason);
    }

    /**
     * 回收自己的指派不受管理范围限制：放弃权限不会扩大任何人的能力（10/15 课堂要求「管理员可以自己撤销自己权限」）。
     * 唯一例外是最后一个有效的超级管理员，由调用方在删除后统一拦截。
     */
    private String checkRevoke(Operator op, User u, Role role, Long scopeDeptId) {
        if (u.id.equals(op.userId())) {
            return null;
        }
        if (Role.SUPER_ADMIN.equals(role.roleCode) && !authz.isSuperAdmin(op.userId())) {
            return "SUPER_ADMIN_ONLY";
        }
        if (!authz.decide(op.userId(), PERM_REVOKE, u.departmentId, false).allowed()
                || !authz.decide(op.userId(), PERM_REVOKE, scopeDeptId, false).allowed()) {
            return "OUT_OF_MANAGE_SCOPE";
        }
        return null;
    }

    // ------------------------------------------------------------------ 5.4 批量分配

    /**
     * 按分公司、部门、员工批量指派。
     * 默认 ATOMIC：先对每个人做完整校验，有任何一人不通过就整批不写，返回失败清单；
     * PARTIAL：写入通过的，失败清单逐人给出原因。已持有相同指派的人计入 duplicated，不算失败。
     */
    @Transactional
    public BatchResult batchAssign(Operator op, BatchAssign req) {
        String mode = normalizeMode(req);
        Role role = roleMapper.findById(req.roleId()).orElseThrow(() -> BizException.notFound("角色"));
        if (req.scopeDepartmentId() != null && !departmentMapper.existsById(req.scopeDepartmentId())) {
            throw BizException.notFound("适用部门");
        }
        Context ctx = context(op, role);
        Targets targets = resolveTargets(req);

        List<Failure> failures = new ArrayList<>(targets.failures());
        List<User> valid = new ArrayList<>();
        for (User u : targets.users()) {
            Long scopeId = req.scopeDepartmentId() == null ? u.departmentId : req.scopeDepartmentId();
            String failure = checkOne(ctx, u, scopeId);
            if (failure == null) {
                valid.add(u);
            } else {
                failures.add(new Failure(u.id, failure));
            }
        }
        // 已持有相同指派（同角色、同适用部门）的人跳过
        Set<Long> already = new HashSet<>();
        Map<Long, Long> homeDept = new HashMap<>();
        valid.forEach(u -> homeDept.put(u.id, u.departmentId));
        for (List<Long> chunk : chunks(valid.stream().map(u -> u.id).toList())) {
            for (UserRole ur : userRoleMapper.findByRoleIdAndUserIdIn(role.id, chunk)) {
                Long expectedScope = req.scopeDepartmentId() == null ? homeDept.get(ur.userId) : req.scopeDepartmentId();
                if (ur.scopeDeptId.equals(expectedScope)) {
                    already.add(ur.userId);
                }
            }
        }
        List<Long> toInsert = valid.stream().map(u -> u.id).filter(id -> !already.contains(id)).toList();
        int requested = targets.users().size() + targets.failures().size();
        String detail = "role=" + role.roleCode + " target=" + req.targetType() + req.targetIds()
                + " scope=" + (req.scopeDepartmentId() == null ? "各自所属部门" : deptName(ctx, req.scopeDepartmentId()))
                + " requested=" + requested + " failed=" + failures.size();

        if (BatchDtos.ATOMIC.equals(mode) && !failures.isEmpty()) {
            audit.record(op, "ROLE_ASSIGN", "BATCH", role.id, role.roleCode, detail, AuditService.DENIED,
                    "BATCH_REJECTED: " + failures.size() + " 人未通过校验");
            return new BatchResult(mode, false, requested, 0, already.size(), failures.size(), failures);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int written = 0;
        for (List<Long> chunk : chunks(toInsert)) {
            written += userRoleMapper.insertForUsers(chunk, role.id, req.scopeDepartmentId(), op.userId(), now,
                    req.reason());
        }
        if (written > 0) {
            authz.bumpGrantVersion();
        }
        audit.record(op, "ROLE_ASSIGN", "BATCH", role.id, role.roleCode,
                detail + " succeeded=" + written + " duplicated=" + already.size(),
                failures.isEmpty() ? AuditService.SUCCESS : AuditService.FAILED, req.reason());
        return new BatchResult(mode, true, requested, written, already.size(), failures.size(), failures);
    }

    // ------------------------------------------------------------------ 5.5 批量回收

    /**
     * 按分公司、部门、员工批量回收某角色。scopeDepartmentId 为空时回收这些人持有的该角色的全部指派，
     * 否则只回收适用于该部门的那一条。校验与写入规则同批量分配。
     */
    @Transactional
    public BatchResult batchRevoke(Operator op, BatchAssign req) {
        String mode = normalizeMode(req);
        Role role = roleMapper.findById(req.roleId()).orElseThrow(() -> BizException.notFound("角色"));
        Targets targets = resolveTargets(req);
        List<Failure> failures = new ArrayList<>(targets.failures());
        List<Long> toDelete = new ArrayList<>();
        int skipped = 0;
        Map<Long, User> byId = new LinkedHashMap<>();
        targets.users().forEach(u -> byId.put(u.id, u));
        Map<Long, List<UserRole>> held = new HashMap<>();
        for (List<Long> chunk : chunks(new ArrayList<>(byId.keySet()))) {
            for (UserRole ur : userRoleMapper.findByRoleIdAndUserIdIn(role.id, chunk)) {
                if (req.scopeDepartmentId() == null || req.scopeDepartmentId().equals(ur.scopeDeptId)) {
                    held.computeIfAbsent(ur.userId, k -> new ArrayList<>()).add(ur);
                }
            }
        }
        for (User u : byId.values()) {
            List<UserRole> rows = held.getOrDefault(u.id, List.of());
            if (rows.isEmpty()) {
                skipped++;   // 本来就没有，不算失败
                continue;
            }
            String failure = null;
            for (UserRole ur : rows) {
                failure = checkRevoke(op, u, role, ur.scopeDeptId);
                if (failure != null) {
                    break;
                }
            }
            if (failure == null) {
                rows.forEach(ur -> toDelete.add(ur.id));
            } else {
                failures.add(new Failure(u.id, failure));
            }
        }
        int requested = targets.users().size() + targets.failures().size();
        String detail = "role=" + role.roleCode + " target=" + req.targetType() + req.targetIds()
                + " requested=" + requested + " failed=" + failures.size();
        if (BatchDtos.ATOMIC.equals(mode) && !failures.isEmpty()) {
            audit.record(op, "ROLE_REVOKE", "BATCH", role.id, role.roleCode, detail, AuditService.DENIED,
                    "BATCH_REJECTED: " + failures.size() + " 人未通过校验");
            return new BatchResult(mode, false, requested, 0, skipped, failures.size(), failures);
        }
        int deleted = 0;
        for (List<Long> chunk : chunks(toDelete)) {
            deleted += userRoleMapper.deleteByIds(chunk);
        }
        if (Role.SUPER_ADMIN.equals(role.roleCode) && deleted > 0 && userRoleMapper.countActiveSuperAdmins() == 0) {
            throw BizException.conflict(BizException.STATE_CONFLICT, "不能回收最后一个有效的超级管理员");
        }
        if (deleted > 0) {
            authz.bumpGrantVersion();
        }
        audit.record(op, "ROLE_REVOKE", "BATCH", role.id, role.roleCode, detail + " revoked=" + deleted,
                failures.isEmpty() ? AuditService.SUCCESS : AuditService.FAILED, req.reason());
        int users = (int) byId.values().stream().filter(u -> held.containsKey(u.id))
                .filter(u -> failures.stream().noneMatch(f -> f.userId().equals(u.id))).count();
        return new BatchResult(mode, true, requested, users, skipped, failures.size(), failures);
    }

    // ------------------------------------------------------------------ 校验过程

    Context context(Operator op, Role role) {
        GrantSet grants = authz.loadGrants(op.userId()).grants();
        boolean opSuper = AuthorizationService.holdsSuperAdmin(grants);
        Map<String, Boolean> rolePermissions = new HashMap<>();
        for (Object[] row : rolePermissionMapper.findGrantScopesOfRole(role.id)) {
            rolePermissions.merge((String) row[0], (Boolean) row[1], Boolean::logicalAnd);
        }
        return new Context(op, role, rolePermissions, opSuper,
                authz.scopeOf(op.userId(), PERM_ASSIGN), grants.grants(), new HashMap<>(), new HashMap<>());
    }

    /** 一个用户的完整校验。返回 null 表示通过，否则为失败原因码。 */
    String checkOne(Context ctx, User u, Long scopeDeptId) {
        if (User.DELETED.equals(u.status)) {
            return "SUBJECT_DELETED";
        }
        if (!Role.ENABLED.equals(ctx.role().status)) {
            return "ROLE_DISABLED";
        }
        Department scope = dept(ctx, scopeDeptId);
        Department home = dept(ctx, u.departmentId);
        if (scope == null) {
            return "SCOPE_DEPT_NOT_FOUND";
        }
        if (!scope.companyId.equals(u.companyId)) {
            return "CROSS_COMPANY";
        }
        if (ctx.role().companyId != null && !ctx.role().companyId.equals(scope.companyId)) {
            return "ROLE_COMPANY_MISMATCH";
        }
        if (ctx.operatorIsSuperAdmin()) {
            return null;
        }
        if (Role.SUPER_ADMIN.equals(ctx.role().roleCode)) {
            return "SUPER_ADMIN_ONLY";
        }
        if (!AuthorizationService.inScope(ctx.operatorScope(), home.path)
                || !AuthorizationService.inScope(ctx.operatorScope(), scope.path)) {
            return "OUT_OF_MANAGE_SCOPE";
        }
        // 同一适用部门只算一次；空串表示通过（HashMap 不缓存 null）
        String r = ctx.escalationByScope().computeIfAbsent(scope.id, id -> {
            String e = escalation(ctx, scope.path);
            return e == null ? "" : e;
        });
        return r.isEmpty() ? null : r;
    }

    /**
     * 不能授出自己没有的权限：角色的每个权限，操作者都必须有一条覆盖适用部门的授权，
     * 而且范围不能比角色的窄——角色给的是完整权限，操作者手里的只限本人就不够；角色本身只限本人，操作者哪种都行。
     * 与 decide 的 3.4、3.5 步相同，只是一次检查一整组权限，不逐个往返缓存。
     */
    static String escalation(Context ctx, String scopePath) {
        // 权限码 → 操作者在该部门是否持有不限本人的授权
        Map<String, Boolean> held = new HashMap<>();
        for (GrantRow g : ctx.operatorGrants()) {
            if (AuthorizationService.covers(g.scopePath(), scopePath)) {
                held.merge(g.permissionCode(), !g.selfOnly(), Boolean::logicalOr);
            }
        }
        for (Map.Entry<String, Boolean> p : ctx.rolePermissions().entrySet()) {
            Boolean unrestricted = held.get(p.getKey());
            if (unrestricted == null || (!p.getValue() && !unrestricted)) {
                return "PRIVILEGE_ESCALATION";
            }
        }
        return null;
    }

    /** 批量目标展开成用户：分公司 → 整棵子树；部门 → 本部门或含下级；员工 → 指定的人。 */
    record Targets(List<User> users, List<Failure> failures) {
    }

    Targets resolveTargets(BatchAssign req) {
        if (req.targetIds() == null || req.targetIds().isEmpty()) {
            throw BizException.badRequest("targetIds 不能为空");
        }
        Map<Long, User> users = new LinkedHashMap<>();
        List<Failure> failures = new ArrayList<>();
        String type = req.targetType() == null ? "" : req.targetType();
        switch (type) {
            case "USER" -> {
                Map<Long, User> found = new HashMap<>();
                userMapper.findAllById(req.targetIds()).forEach(u -> found.put(u.id, u));
                for (Long id : req.targetIds()) {
                    User u = found.get(id);
                    if (u == null) {
                        failures.add(new Failure(id, "NOT_FOUND"));
                    } else if (User.DELETED.equals(u.status)) {
                        failures.add(new Failure(id, "SUBJECT_DELETED"));
                    } else {
                        users.putIfAbsent(id, u);
                    }
                }
            }
            case "DEPARTMENT" -> {
                boolean descendants = req.includeDescendants() == null || req.includeDescendants();
                for (Long deptId : req.targetIds()) {
                    Department d = departmentMapper.findById(deptId).orElseThrow(() -> BizException.notFound("部门 " + deptId));
                    List<User> found = descendants ? userMapper.findActiveInSubtree(d.path)
                            : userMapper.findActiveInDepartments(List.of(d.id));
                    found.forEach(u -> users.putIfAbsent(u.id, u));
                }
            }
            case "COMPANY" -> {
                for (Long companyId : req.targetIds()) {
                    Company c = companyMapper.findById(companyId).orElseThrow(() -> BizException.notFound("公司 " + companyId));
                    Department root = departmentMapper.findByCompanyIdAndParentIdIsNull(c.id)
                            .orElseThrow(() -> BizException.notFound("公司根部门"));
                    userMapper.findActiveInSubtree(root.path).forEach(u -> users.putIfAbsent(u.id, u));
                }
            }
            default -> throw BizException.badRequest("targetType 只能是 COMPANY、DEPARTMENT 或 USER");
        }
        return new Targets(new ArrayList<>(users.values()), failures);
    }

    private Department dept(Context ctx, Long id) {
        if (id == null) {
            return null;
        }
        return ctx.depts().computeIfAbsent(id, k -> departmentMapper.findById(k).orElse(null));
    }

    private String deptName(Context ctx, Long id) {
        Department d = dept(ctx, id);
        return d == null ? String.valueOf(id) : d.deptName;
    }

    static String normalizeMode(BatchAssign req) {
        if (req == null || req.roleId() == null) {
            throw BizException.badRequest("roleId 必填");
        }
        String mode = req.mode() == null ? BatchDtos.ATOMIC : req.mode();
        if (!BatchDtos.ATOMIC.equals(mode) && !BatchDtos.PARTIAL.equals(mode)) {
            throw BizException.badRequest("mode 只能是 ATOMIC 或 PARTIAL");
        }
        return mode;
    }

    static <T> List<List<T>> chunks(List<T> all) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i += CHUNK) {
            out.add(all.subList(i, Math.min(all.size(), i + CHUNK)));
        }
        return out;
    }

    static BizException failureException(String failure) {
        return switch (failure) {
            case "OUT_OF_MANAGE_SCOPE" -> BizException.forbidden(BizException.OUT_OF_MANAGE_SCOPE,
                    "用户或适用部门不在你的管理范围内");
            case "PRIVILEGE_ESCALATION" -> BizException.forbidden(BizException.PRIVILEGE_ESCALATION,
                    "不能分配超出你自身权限的角色：该角色含有你在适用部门不具备的权限，或你只有其「本人」范围");
            case "SUPER_ADMIN_ONLY" -> BizException.forbidden(BizException.FORBIDDEN, "只有超级管理员能分配或回收超级管理员角色");
            case "ROLE_DISABLED" -> BizException.conflict(BizException.STATE_CONFLICT, "角色已停用");
            case "SUBJECT_DELETED" -> BizException.conflict(BizException.STATE_CONFLICT, "用户已删除");
            case "CROSS_COMPANY" -> BizException.badRequest("适用部门必须与用户在同一家公司");
            case "ROLE_COMPANY_MISMATCH" -> BizException.badRequest("公司内角色只能指派到本公司的部门");
            default -> BizException.badRequest(failure);
        };
    }
}
