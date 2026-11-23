package com.xmu.rbac.structured.service;
import cn.edu.xmu.clonefactory.util.CloneFactory;
import static com.xmu.rbac.structured.service.Views.*;

import com.xmu.rbac.structured.dto.*;
import com.xmu.rbac.structured.entity.*;
import com.xmu.rbac.structured.mapper.Store;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 管理用例事务脚本：持全局授权锁后执行范围、准入、上界及不变量校验。 */
@Service
public class ManagementService {
    private final Store db;
    private final AuthorizationService az;
    private final AuditService audit;
    private final PasswordEncoder passwords;
    public ManagementService(
        Store db, AuthorizationService az, AuditService audit, PasswordEncoder passwords) {
        this.db = db;
        this.az = az;
        this.audit = audit;
        this.passwords = passwords;
    }
    private GrantVersion begin(Actor a, String permission, boolean shared) {
        GrantVersion version = az.lock();
        az.center(a, permission);
        if (shared)
            az.root(a);
        az.captureUpper(a);
        return version;
    }
    private void finish(GrantVersion g, Actor a, String operation, Object target) {
        db.flush();
        az.preserveAdministrator();
        g.version++;
        audit.success(a, operation, String.valueOf(target));
    }
    private String code(Long systemId) {
        return az.require(SoftwareSystem.class, systemId).code;
    }
    private void version(Long expected, Long actual) {
        if (expected != null && !expected.equals(actual))
            throw BizException.conflict("VERSION_CONFLICT");
    }
    private void enabled(String state) {
        if (!Set.of("ENABLED", "DISABLED").contains(state))
            throw BizException.bad("INVALID_STATUS");
    }
    private Object userView(User u) {
        return Views.user(u, az.require(Department.class, u.departmentId).name);
    }
    private Object roleView(Role r) {
        return Views.role(r, code(r.systemId));
    }
    private void checkPage(int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw BizException.bad("INVALID_PAGE");
    }
    private Permission permission(Long system, String kind, String code) {
        if (code == null)
            return null;
        Permission p = db.one(
            Permission.class, "e.systemId=?1 and e.kind=?2 and e.code=?3", system, kind, code);
        if (p == null)
            throw BizException.missing("PERMISSION_NOT_FOUND");
        return p;
    }
    /** 执行systems操作，保持服务到持久化层的单向依赖。 */
    public Object systems(Actor a) {
        az.center(a, "rbac:system:view");
        return db.all(SoftwareSystem.class)
            .stream()
            .sorted(Comparator.comparing(s -> s.id))
            .map(Views::system)
            .toList();
    }
    /** 执行createSystem操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object createSystem(Actor a, Inputs.SystemCreate input) {
        GrantVersion g = begin(a, "rbac:system:edit", true);
        SoftwareSystem s = CloneFactory.copy(new SoftwareSystem(), input);
        s.status = "ENABLED";
        db.save(s);
        finish(g, a, "SYSTEM_CREATED", s.code);
        return system(s);
    }
    /** 执行renameSystem操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object renameSystem(Actor a, Long id, NameChange input) {
        GrantVersion g = begin(a, "rbac:system:edit", true);
        SoftwareSystem s = az.require(SoftwareSystem.class, id);
        s.name = input.name();
        finish(g, a, "SYSTEM_UPDATED", id);
        return system(s);
    }
    /** 执行systemStatus操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object systemStatus(Actor a, Long id, String status) {
        GrantVersion g = begin(a, "rbac:system:edit", true);
        enabled(status);
        SoftwareSystem s = az.require(SoftwareSystem.class, id);
        if ("ENABLED".equals(status) && !"ENABLED".equals(s.status)) {
            List<Long> roles = db.where(Role.class, "e.systemId=?1", id)
                                   .stream().map(role -> role.id).toList();
            az.upper(a, s.code, az.calculateConfiguredGrants(roles, id));
        }
        s.status = status;
        finish(g, a, "SYSTEM_STATUS", id);
        return system(s);
    }
    /** 执行departments操作，保持服务到持久化层的单向依赖。 */
    public Object departments(Actor a) {
        az.center(a, "rbac:department:view");
        Department own =
            az.require(Department.class, az.require(User.class, a.userId()).departmentId);
        return tree(db.where(Department.class, "e.path like ?1", own.path + "%")
                .stream()
                .sorted(Comparator.comparing(d -> d.id))
                .map(Views::department)
                .toList());
    }
    /** 执行createDepartment操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object createDepartment(Actor a, Inputs.DepartmentCreate input) {
        GrantVersion g = begin(a, "rbac:department:edit", false);
        if (input.parentId() == null) {
            az.root(a);
            throw BizException.conflict("ROOT_DEPARTMENT_EXISTS");
        }
        az.scope(a, input.parentId());
        Department parent = az.require(Department.class, input.parentId());
        requireAccepting(parent);
        Department d = CloneFactory.copy(new Department(), input);
        d.parentId = parent.id;
        d.path = parent.path;
        d.status = "ENABLED";
        db.save(d);
        db.flush();
        d.path = parent.path + d.id + "/";
        finish(g, a, "DEPARTMENT_CREATED", d.id);
        return department(d);
    }
    /** 执行renameDepartment操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object renameDepartment(Actor a, Long id, NameChange input) {
        GrantVersion g = begin(a, "rbac:department:edit", false);
        az.scope(a, id);
        Department d = az.require(Department.class, id);
        d.name = input.name();
        finish(g, a, "DEPARTMENT_UPDATED", id);
        return department(d);
    }
    /** 启停只改变部门接纳状态，已有成员和直接指派保留。 */
    @Transactional
    public Object departmentStatus(Actor a, Long id, String status) {
        GrantVersion g = begin(a, "rbac:department:status", false);
        az.scope(a, id);
        Department d = az.require(Department.class, id);
        if (!"DISABLED".equals(status) && !"ENABLED".equals(status))
            throw BizException.bad("VALIDATION_ERROR");
        if (status.equals(d.status)) return department(d);
        if ("DISABLED".equals(status)) {
            if (d.parentId == null)
                throw BizException.conflict("ROOT_DEPARTMENT_DISABLE_FORBIDDEN");
            if (departmentHasChildren(id))
                throw BizException.conflict("DEPARTMENT_HAS_CHILDREN");
        }
        d.status = status;
        finish(g, a, "DEPARTMENT_STATUS_CHANGED", id);
        return department(d);
    }
    private boolean departmentHasChildren(Long id) {
        return !db.where(Department.class, "e.parentId=?1", id).isEmpty();
    }
    private void requireAccepting(Department department) {
        if (!"ENABLED".equals(department.status))
            throw BizException.conflict("DEPARTMENT_DISABLED");
    }
    /** 执行deleteDepartment操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public void deleteDepartment(Actor a, Long id) {
        GrantVersion g = begin(a, "rbac:department:delete", false);
        az.scope(a, id);
        Department d = az.require(Department.class, id);
        if (d.parentId == null || !db.where(User.class, "e.departmentId=?1", id).isEmpty()
            || departmentHasChildren(id))
            throw BizException.conflict("DEPARTMENT_NOT_EMPTY");
        for (DepartmentAllowedRole r :
            db.where(DepartmentAllowedRole.class, "e.departmentId=?1", id))
            db.remove(r);
        db.remove(d);
        finish(g, a, "DEPARTMENT_DELETED", id);
    }
    /** 执行allowedRoles操作，保持服务到持久化层的单向依赖。 */
    public Object allowedRoles(Actor a, Long id, String system) {
        az.center(a, "rbac:department:view");
        az.scope(a, id);
        return db.where(DepartmentAllowedRole.class, "e.departmentId=?1", id)
            .stream()
            .map(r -> az.require(Role.class, r.roleId))
            .filter(r -> system == null || code(r.systemId).equals(system))
            .sorted(Comparator.comparing(r -> r.id))
            .map(this::roleView)
            .toList();
    }
    /** 原子替换部门角色准入并回收不合资格指派。 */
    @Transactional
    public void replaceAllowed(Actor a, Long id, List<Long> roles) {
        GrantVersion g = begin(a, "rbac:department:edit", false);
        az.scope(a, id);
        Set<Long> wanted = new HashSet<>(roles);
        List<DepartmentAllowedRole> current = db.where(DepartmentAllowedRole.class,
            "e.departmentId=?1", id);
        Set<Long> existing = new HashSet<>();
        for (DepartmentAllowedRole row : current) existing.add(row.roleId);
        for (Long roleId : wanted) {
            Role role = az.require(Role.class, roleId);
            SoftwareSystem system = az.require(SoftwareSystem.class, role.systemId);
            if ("ENABLED".equals(system.status)) {
                az.upper(a, system.code, az.calculate(List.of(roleId), system.id));
            } else if (!existing.contains(roleId)) {
                az.requireRoleGrantSystem(a, system);
            }
        }
        for (Long roleId : existing) {
            if (wanted.contains(roleId)) continue;
            Role role = az.require(Role.class, roleId);
            az.requireRoleGrantSystem(a, az.require(SoftwareSystem.class, role.systemId));
        }
        for (DepartmentAllowedRole old : current) db.remove(old);
        db.flush();
        for (Long role : wanted) {
            DepartmentAllowedRole allowed = new DepartmentAllowedRole();
            allowed.departmentId = id;
            allowed.roleId = role;
            db.save(allowed);
        }
        for (User u : db.where(User.class, "e.departmentId=?1", id))
            for (UserRole assignment : db.where(UserRole.class, "e.userId=?1", u.id))
                if (!wanted.contains(assignment.roleId))
                    db.remove(assignment);
        finish(g, a, "DEPARTMENT_ROLES_REPLACED", id);
    }
    /** 执行users操作，保持服务到持久化层的单向依赖。 */
    public Object users(
        Actor a, String keyword, Long department, String status, int page, int size) {
        az.center(a, "rbac:user:view");
        checkPage(page, size);
        Department own =
            az.require(Department.class, az.require(User.class, a.userId()).departmentId);
        String clause = "e.departmentId in (select d.id from Department d where d.path like ?1)";
        List<Object> args = new ArrayList<>(List.of(own.path + "%"));
        if (keyword != null) {
            args.add("%" + keyword + "%");
            clause +=
                " and (e.username like ?" + args.size() + " or e.name like ?" + args.size() + ")";
        }
        if (department != null) {
            az.scope(a, department);
            args.add(department);
            clause += " and e.departmentId=?" + args.size();
        }
        if (status != null) {
            args.add(status);
            clause += " and e.status=?" + args.size();
        }
        return db.page(User.class, clause, args, page, size, this::userView, "e.id");
    }
    /** 执行user操作，保持服务到持久化层的单向依赖。 */
    public Object user(Actor a, Long id) {
        az.center(a, "rbac:user:view");
        User u = az.require(User.class, id);
        az.scope(a, u.departmentId);
        return userView(u);
    }
    /** 执行createUser操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object createUser(Actor a, Inputs.UserCreate input) {
        GrantVersion g = begin(a, "rbac:user:edit", false);
        az.scope(a, input.departmentId());
        requireAccepting(az.require(Department.class, input.departmentId()));
        User u = CloneFactory.copy(new User(), input);
        u.subject = "rbac-" + UUID.randomUUID();
        u.passwordHash = passwords.encode(input.password());
        u.departmentId = input.departmentId();
        u.status = "ACTIVE";
        u.tokenVersion = 0L;
        u.mustChangePassword = true;
        u.failedLoginCount = 0;
        u.createdAt = Instant.now();
        db.save(u);
        finish(g, a, "USER_CREATED", u.subject);
        return userView(u);
    }
    /** 执行patchUser操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object patchUser(Actor a, Long id, Inputs.UserPatch input) {
        GrantVersion g = begin(a, "rbac:user:edit", false);
        User u = az.require(User.class, id);
        az.scope(a, u.departmentId);
        version(input.version(), u.version);
        if (input.supplied("name") && input.name() == null)
            throw BizException.bad("VALIDATION_ERROR");
        if (input.name() != null)
            u.name = input.name();
        if (input.supplied("no"))
            u.no = input.no();
        if (input.supplied("phone"))
            u.phone = input.phone();
        finish(g, a, "USER_UPDATED", id);
        return userView(u);
    }
    /** 执行userStatus操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object userStatus(Actor a, Long id, String status) {
        GrantVersion g = begin(a, "rbac:user:edit", false);
        if (!Set.of("ACTIVE", "DISABLED", "DELETED").contains(status))
            throw BizException.bad("INVALID_STATUS");
        User u = az.require(User.class, id);
        az.scope(a, u.departmentId);
        if ("DELETED".equals(u.status) && !"DELETED".equals(status))
            throw BizException.conflict("USER_DELETED");
        if ("ACTIVE".equals(status) && !"ACTIVE".equals(u.status)) {
            for (UserRole assignment : db.where(UserRole.class, "e.userId=?1", id)) {
                Role role = az.require(Role.class, assignment.roleId);
                az.upper(a, code(role.systemId), az.calculate(List.of(role.id), role.systemId));
            }
        }
        u.status = status;
        if (!"ACTIVE".equals(status))
            u.tokenVersion++;
        if ("DELETED".equals(status))
            for (UserRole ur : db.where(UserRole.class, "e.userId=?1", id)) db.remove(ur);
        finish(g, a, "USER_STATUS", id);
        return userView(u);
    }
    /** 在同一事务中更新部门并清空本人的角色指派。 */
    @Transactional
    public Object moveUser(Actor a, Long id, Inputs.Move input) {
        GrantVersion g = begin(a, "rbac:user:edit", false);
        User u = db.lock(User.class, id);
        if (u == null)
            throw BizException.missing("USER_NOT_FOUND");
        version(input.version(), u.version);
        az.scope(a, u.departmentId);
        az.scope(a, input.departmentId());
        if (!Objects.equals(u.departmentId, input.departmentId())) {
            requireAccepting(az.require(Department.class, input.departmentId()));
            u.departmentId = input.departmentId();
            for (UserRole ur : db.where(UserRole.class, "e.userId=?1", id))
                db.remove(ur);
        }
        finish(g, a, "USER_DEPARTMENT_CHANGED", id);
        return userView(u);
    }
    /** 执行roles操作，保持服务到持久化层的单向依赖。 */
    public Object roles(Actor a, String system, String keyword, String status, int page, int size) {
        az.center(a, "rbac:role:view");
        if (system == null)
            throw BizException.bad("SYSTEM_REQUIRED");
        checkPage(page, size);
        List<Object> args = new ArrayList<>(List.of(az.system(system).id));
        String clause = "e.systemId=?1";
        if (keyword != null) {
            args.add("%" + keyword + "%");
            clause += " and (e.code like ?" + args.size() + " or e.name like ?" + args.size() + ")";
        }
        if (status != null) {
            args.add(status);
            clause += " and e.status=?" + args.size();
        }
        return db.page(Role.class, clause, args, page, size, this::roleView, "e.id");
    }
    /** 执行role操作，保持服务到持久化层的单向依赖。 */
    public Object role(Actor a, Long id) {
        az.center(a, "rbac:role:view");
        return roleView(az.require(Role.class, id));
    }
    /** 执行createRole操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object createRole(Actor a, Inputs.RoleCreate input) {
        GrantVersion g = begin(a, "rbac:role:edit", true);
        SoftwareSystem system = az.system(input.systemCode());
        az.requireRoleGrantSystem(a, system);
        Role r = CloneFactory.copy(new Role(), input);
        r.systemId = system.id;
        r.status = "ENABLED";
        r.builtin = false;
        db.save(r);
        db.flush();
        az.upper(a, input.systemCode(), az.calculate(List.of(r.id), r.systemId));
        finish(g, a, "ROLE_CREATED", r.id);
        return roleView(r);
    }
    /** 执行patchRole操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object patchRole(Actor a, Long id, Inputs.RolePatch input) {
        GrantVersion g = begin(a, "rbac:role:edit", true);
        Role r = az.require(Role.class, id);
        az.protectRole(a, r);
        version(input.version(), r.version);
        if (input.supplied("name") && input.name() == null)
            throw BizException.bad("VALIDATION_ERROR");
        if (input.name() != null)
            r.name = input.name();
        if (input.supplied("description"))
            r.description = input.description();
        finish(g, a, "ROLE_UPDATED", id);
        return roleView(r);
    }
    private void changedUpper(Actor a, Role role) {
        db.flush();
        Set<Long> affected = new HashSet<>(Set.of(role.id));
        boolean changed;
        do {
            changed = false;
            for (RoleHierarchy e : db.all(RoleHierarchy.class))
                if (affected.contains(e.juniorRoleId))
                    changed |= affected.add(e.seniorRoleId);
        } while (changed);
        for (Long id : affected)
            az.upper(a, code(role.systemId), az.calculate(List.of(id), role.systemId));
    }
    /** 执行roleStatus操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object roleStatus(Actor a, Long id, String status) {
        GrantVersion g = begin(a, "rbac:role:status", true);
        enabled(status);
        Role r = az.require(Role.class, id);
        az.protectRole(a, r);
        if ("ENABLED".equals(status))
            az.requireRoleGrantSystem(a, az.require(SoftwareSystem.class, r.systemId));
        r.status = status;
        if ("ENABLED".equals(status))
            changedUpper(a, r);
        finish(g, a, "ROLE_STATUS", id);
        return roleView(r);
    }
    /** 执行deleteRole操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public void deleteRole(Actor a, Long id) {
        GrantVersion g = begin(a, "rbac:role:edit", true);
        Role r = az.require(Role.class, id);
        az.protectRole(a, r);
        for (UserRole x : db.where(UserRole.class, "e.roleId=?1", id)) db.remove(x);
        for (RolePermission x : db.where(RolePermission.class, "e.roleId=?1", id)) db.remove(x);
        for (DepartmentAllowedRole x : db.where(DepartmentAllowedRole.class, "e.roleId=?1", id))
            db.remove(x);
        for (RoleHierarchy x :
            db.where(RoleHierarchy.class, "e.seniorRoleId=?1 or e.juniorRoleId=?1", id))
            db.remove(x);
        db.flush();
        db.remove(r);
        finish(g, a, "ROLE_DELETED", id);
    }
    /** 执行directPermissions操作，保持服务到持久化层的单向依赖。 */
    public Object directPermissions(Actor a, Long id) {
        az.center(a, "rbac:role:view");
        Role r = az.require(Role.class, id);
        return db.where(RolePermission.class, "e.roleId=?1", id)
            .stream()
            .map(x
                -> Views.permission(az.require(Permission.class, x.permissionId), code(r.systemId)))
            .toList();
    }
    /** 原子替换直接权限并校验完整新继承闭包。 */
    @Transactional
    public Object replacePermissions(Actor a, Long id, List<Long> ids) {
        GrantVersion g = begin(a, "rbac:role:edit", true);
        Role r = az.require(Role.class, id);
        az.protectRole(a, r);
        az.requireRoleGrantSystem(a, az.require(SoftwareSystem.class, r.systemId));
        for (Long pid : ids)
            if (!az.require(Permission.class, pid).systemId.equals(r.systemId))
                throw BizException.bad("CROSS_SYSTEM_PERMISSION");
        for (RolePermission p : db.where(RolePermission.class, "e.roleId=?1", id)) db.remove(p);
        db.flush();
        for (Long pid : new HashSet<>(ids)) {
            RolePermission p = new RolePermission();
            p.roleId = id;
            p.permissionId = pid;
            db.save(p);
        }
        changedUpper(a, r);
        finish(g, a, "ROLE_PERMISSIONS_REPLACED", id);
        return directPermissions(a, id);
    }
    /** 角色详情展示该角色配置的权限及继承来源。 */
    public Object effectiveRole(Actor a, Long id) {
        az.center(a, "rbac:role:view");
        Role r = az.require(Role.class, id);
        return az.effective(az.calculateConfiguredGrants(List.of(id), r.systemId), code(r.systemId));
    }
    /** 执行hierarchy操作，保持服务到持久化层的单向依赖。 */
    public Object hierarchy(Actor a, String system) {
        az.center(a, "rbac:role:view");
        Long id = az.system(system).id;
        List<Role> roles = db.where(Role.class, "e.systemId=?1", id);
        Set<Long> ids = new HashSet<>(roles.stream().map(r -> r.id).toList());
        return map("roles", roles.stream().map(this::roleView).toList(), "edges",
            db.all(RoleHierarchy.class)
                .stream()
                .filter(e -> ids.contains(e.seniorRoleId) && ids.contains(e.juniorRoleId))
                .map(e -> map("seniorRoleId", e.seniorRoleId, "juniorRoleId", e.juniorRoleId))
                .toList());
    }
    private boolean reachable(Long from, Long to) {
        Set<Long> seen = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>(List.of(from));
        while (!pending.isEmpty()) {
            Long id = pending.pop();
            if (id.equals(to))
                return true;
            if (seen.add(id))
                for (RoleHierarchy e : db.where(RoleHierarchy.class, "e.seniorRoleId=?1", id))
                    pending.push(e.juniorRoleId);
        }
        return false;
    }
    /** 持全局锁检查环路并原子修改角色继承关系。 */
    @Transactional
    public void inherit(Actor a, Long senior, Long junior, boolean add) {
        GrantVersion g = begin(a, "rbac:role:edit", true);
        Role s = az.require(Role.class, senior), j = az.require(Role.class, junior);
        az.protectRole(a, s);
        az.protectRole(a, j);
        if (!s.systemId.equals(j.systemId))
            throw BizException.bad("CROSS_SYSTEM_ROLE");
        if (add)
            az.requireRoleGrantSystem(a, az.require(SoftwareSystem.class, s.systemId));
        RoleHierarchy edge =
            db.one(RoleHierarchy.class, "e.seniorRoleId=?1 and e.juniorRoleId=?2", senior, junior);
        if (add) {
            if (edge != null)
                throw BizException.conflict("ROLE_EDGE_EXISTS");
            if (reachable(junior, senior))
                throw BizException.conflict("ROLE_CYCLE");
            edge = new RoleHierarchy();
            edge.seniorRoleId = senior;
            edge.juniorRoleId = junior;
            db.save(edge);
            changedUpper(a, s);
        } else {
            if (edge == null)
                throw BizException.missing("ROLE_EDGE_NOT_FOUND");
            db.remove(edge);
        }
        finish(g, a, add ? "ROLE_INHERITS_ADDED" : "ROLE_INHERITS_REMOVED", senior + "->" + junior);
    }
    private Map<String, Object> assignment(UserRole ur) {
        Role r = az.require(Role.class, ur.roleId);
        return map("id", ur.id, "userId", ur.userId, "roleId", r.id, "systemCode", code(r.systemId),
            "roleCode", r.code, "roleName", r.name, "assignedAt", ur.assignedAt, "assignedBy",
            ur.assignedBy);
    }
    /** 执行assignments操作，保持服务到持久化层的单向依赖。 */
    public Object assignments(Actor a, Long user, String system) {
        az.center(a, "rbac:user:view");
        az.scope(a, az.require(User.class, user).departmentId);
        return db.where(UserRole.class, "e.userId=?1", user)
            .stream()
            .map(this::assignment)
            .filter(v -> system == null || system.equals(v.get("systemCode")))
            .toList();
    }
    /** 校验管理范围、部门准入和授权上界后指派角色。 */
    @Transactional
    public Object assign(Actor a, Long id, Long role) {
        GrantVersion g = begin(a, "rbac:assignment:edit", false);
        User u = az.require(User.class, id);
        az.scope(a, u.departmentId);
        requireAccepting(az.require(Department.class, u.departmentId));
        if (!"ACTIVE".equals(u.status))
            throw BizException.conflict("USER_DISABLED");
        Role r = az.require(Role.class, role);
        if (!"ENABLED".equals(r.status)
            || !"ENABLED".equals(az.require(SoftwareSystem.class, r.systemId).status))
            throw BizException.conflict("ROLE_DISABLED");
        if (db.one(DepartmentAllowedRole.class, "e.departmentId=?1 and e.roleId=?2", u.departmentId,
                role)
            == null)
            throw BizException.forbidden("ROLE_NOT_ASSIGNABLE_IN_DEPARTMENT");
        az.upper(a, code(r.systemId), az.calculate(List.of(role), r.systemId));
        UserRole ur = new UserRole();
        ur.userId = id;
        ur.roleId = role;
        ur.assignedAt = Instant.now();
        ur.assignedBy = a.userId();
        db.save(ur);
        finish(g, a, "ROLE_ASSIGNED", id);
        return assignment(ur);
    }
    /** 核对指派所属用户后撤销角色指派。 */
    @Transactional
    public void unassign(Actor a, Long user, Long assignment) {
        GrantVersion g = begin(a, "rbac:assignment:edit", false);
        az.scope(a, az.require(User.class, user).departmentId);
        UserRole ur = az.require(UserRole.class, assignment);
        if (!user.equals(ur.userId))
            throw BizException.missing("ASSIGNMENT_NOT_FOUND");
        db.remove(ur);
        finish(g, a, "ROLE_UNASSIGNED", user);
    }
    /** 执行effectiveUser操作，保持服务到持久化层的单向依赖。 */
    @Transactional(readOnly = true)
    public Object effectiveUser(Actor a, Long user, String system) {
        az.center(a, "rbac:user:view");
        SoftwareSystem targetSystem = az.system(system);
        User target = az.require(User.class, user);
        az.scope(a, target.departmentId);
        if (!"ACTIVE".equals(target.status) || !"ENABLED".equals(targetSystem.status)) {
            return az.effective(
                new AuthorizationService.Snapshot(List.of(), List.of(), List.of(), List.of()),
                system);
        }
        return az.effective(az.snapshot(user, system), system);
    }
    /** 执行permissions操作，保持服务到持久化层的单向依赖。 */
    public Object permissions(
        Actor a, String system, String kind, String keyword, int page, int size) {
        az.center(a, "rbac:permission:view");
        checkPage(page, size);
        String clause = "1=1";
        List<Object> args = new ArrayList<>();
        if (system != null) {
            args.add(az.system(system).id);
            clause += " and e.systemId=?" + args.size();
        }
        if (kind != null) {
            if (!Set.of("API", "UI").contains(kind))
                throw BizException.bad("INVALID_KIND");
            args.add(kind);
            clause += " and e.kind=?" + args.size();
        }
        if (keyword != null) {
            args.add("%" + keyword + "%");
            clause += " and (e.code like ?" + args.size() + " or e.name like ?" + args.size() + ")";
        }
        return db.page(Permission.class, clause, args, page, size,
            p -> Views.permission(p, code(p.systemId)), "e.id");
    }
    /** 执行createPermission操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object createPermission(Actor a, Inputs.PermissionCreate input) {
        GrantVersion g = begin(a, "rbac:permission:edit", true);
        if (!az.superAdmin(a))
            throw BizException.forbidden("PRIVILEGE_ESCALATION");
        Permission p = CloneFactory.copy(new Permission(), input);
        p.systemId = az.system(input.systemCode()).id;
        p.status = "ENABLED";
        db.save(p);
        finish(g, a, "PERMISSION_CREATED", p.code);
        return Views.permission(p, input.systemCode());
    }
    /** 执行permissionStatus操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object permissionStatus(Actor a, Long id, String status) {
        GrantVersion g = begin(a, "rbac:permission:edit", true);
        enabled(status);
        Permission p = az.require(Permission.class, id);
        if ("ENABLED".equals(status) && !az.superAdmin(a)
            && !az.permission(a.userId(), code(p.systemId), p.kind, p.code))
            throw BizException.forbidden("PRIVILEGE_ESCALATION");
        p.status = status;
        finish(g, a, "PERMISSION_STATUS", id);
        return Views.permission(p, code(p.systemId));
    }
    /** 执行dashboard操作，保持服务到持久化层的单向依赖。 */
    public Object dashboard(Actor a) {
        az.requireDashboard(a);
        return map("users", db.count(User.class), "roles", db.count(Role.class), "systems",
            db.count(SoftwareSystem.class));
    }
}
