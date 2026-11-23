package com.xmu.rbac.structured.service;
import static com.xmu.rbac.structured.service.Views.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.contract.DashboardAccess;
import com.xmu.rbac.structured.dto.Actor;
import com.xmu.rbac.structured.entity.*;
import com.xmu.rbac.structured.mapper.Store;
import java.time.*;
import com.xmu.rbac.contract.CourseRoleCodes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionSynchronizationManager;
/** 结构化授权过程：同系统DAG闭包、有效状态切断、版本化两级缓存。 */
@Service
public class AuthorizationService {
    private final Store db;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final String namespace;
    private final ConcurrentHashMap<String, Entry> l1 = new ConcurrentHashMap<>();
    private final Object upperKey = new Object();
    private record Upper(Long userId, boolean superAdmin, Map<String, Snapshot> systems) {}
    /** 在授权变更之前冻结操作者上界，防止修改自己的角色后用新权限证明新权限。 */
    public void captureUpper(Actor actor) {
        if (TransactionSynchronizationManager.hasResource(upperKey))
            return;
        Map<String, Snapshot> snapshots = new HashMap<>();
        for (SoftwareSystem system : db.all(SoftwareSystem.class))
            if ("ENABLED".equals(system.status))
                snapshots.put(system.code, calculate(direct(actor.userId(), system.id), system.id));
        Upper upper = new Upper(actor.userId(), superAdmin(actor), Map.copyOf(snapshots));
        TransactionSynchronizationManager.bindResource(upperKey, upper);
        TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(upperKey);
                }
            });
    }
    /** 执行Snapshot操作，保持服务到持久化层的单向依赖。 */
    public record Snapshot(List<Long> direct, List<Long> roles, List<Long> permissions,
        List<Map<String, Object>> sources) {}
    private record Entry(Snapshot snapshot, Instant expires) {}
    public AuthorizationService(Store db, StringRedisTemplate redis, ObjectMapper json) {
        this(db, redis, json, "structured");
    }
    /** 缓存命名空间属于运行实例的数据边界，不得跨独立数据库复用授权快照。 */
    @Autowired
    public AuthorizationService(Store db, StringRedisTemplate redis, ObjectMapper json,
        @Value("${rbac.cache.namespace:structured}") String namespace) {
        this.db = db;
        this.redis = redis;
        this.json = json;
        if (namespace == null || namespace.isBlank())
            throw new IllegalArgumentException("授权缓存命名空间不能为空");
        this.namespace = namespace;
    }
    /** 执行require操作，保持服务到持久化层的单向依赖。 */
    public <T> T require(Class<T> type, Long id) {
        T value = db.get(type, id);
        if (value == null)
            throw BizException.missing("NOT_FOUND");
        return value;
    }
    /** 执行system操作，保持服务到持久化层的单向依赖。 */
    public SoftwareSystem system(String code) {
        SoftwareSystem s = db.one(SoftwareSystem.class, "e.code=?1", code);
        if (s == null)
            throw BizException.missing("SYSTEM_NOT_FOUND");
        return s;
    }
    /** 执行active操作，保持服务到持久化层的单向依赖。 */
    public void active(User u, SoftwareSystem s) {
        if (!"ACTIVE".equals(u.status) || !"ENABLED".equals(s.status))
            throw BizException.unauthorized();
    }
    private SoftwareSystem activeSystem(Long userId, String code) {
        User u = require(User.class, userId);
        SoftwareSystem s = system(code);
        active(u, s);
        return s;
    }
    /** 每次调用读取数据库全局版本；事务内禁止读写缓存。 */
    public Snapshot snapshot(Long userId, String code) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            SoftwareSystem s = activeSystem(userId, code);
            return calculate(direct(userId, s.id), s.id);
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            long version = version();
            SoftwareSystem s = activeSystem(userId, code);
            String key = namespace + ":authz:" + code + ":" + userId + ":" + version;
            Entry entry = l1.get(key);
            Snapshot result = null;
            if (entry != null && entry.expires.isAfter(Instant.now()))
                result = entry.snapshot;
            if (result == null) {
                try {
                    String value = redis.opsForValue().get(key);
                    if (value != null) {
                        Snapshot cached = json.readValue(value, Snapshot.class);
                        // 旧快照的来源缺少权限种类，需要重新计算。
                        if (cached.sources.stream()
                                .allMatch(source -> source.get("permissionKind") != null))
                            result = cached;
                    }
                } catch (Exception ignored) { /* Redis不是事实来源，受限回退数据库。 */
                }
            }
            if (result == null)
                result = calculate(direct(userId, s.id), s.id);
            if (version != version())
                continue;
            if (l1.size() > 10000)
                l1.clear();
            l1.put(key, new Entry(result, Instant.now().plusSeconds(15)));
            try {
                redis.opsForValue().set(
                    key, json.writeValueAsString(result), Duration.ofMinutes(5));
            } catch (Exception ignored) {
            }
            if (version == version())
                return result;
        }
        throw new BizException(503, "AUTHORIZATION_UNAVAILABLE");
    }
    /** 执行version操作，保持服务到持久化层的单向依赖。 */
    public long version() {
        GrantVersion g = db.get(GrantVersion.class, 1L);
        if (g == null)
            throw new BizException(503, "AUTHORIZATION_UNAVAILABLE");
        return g.version;
    }
    /** 执行direct操作，保持服务到持久化层的单向依赖。 */
    public List<Long> direct(Long userId, Long systemId) {
        return db.where(UserRole.class, "e.userId=?1", userId)
            .stream()
            .map(a -> db.get(Role.class, a.roleId))
            .filter(Objects::nonNull)
            .filter(r -> r.systemId.equals(systemId) && "ENABLED".equals(r.status))
            .map(r -> r.id)
            .toList();
    }
    /** 计算启用系统中的角色权限。 */
    public Snapshot calculate(List<Long> direct, Long systemId) {
        if (!"ENABLED".equals(require(SoftwareSystem.class, systemId).status))
            return new Snapshot(List.of(), List.of(), List.of(), List.of());
        return calculateConfiguredGrants(direct, systemId);
    }
    /** 计算角色配置的同系统DAG闭包，切断停用角色并保留来源路径。 */
    Snapshot calculateConfiguredGrants(List<Long> direct, Long systemId) {
        Map<Long, Role> roles = new HashMap<>();
        for (Role r : db.where(Role.class, "e.systemId=?1", systemId)) roles.put(r.id, r);
        Map<Long, List<Long>> edges = new HashMap<>();
        for (RoleHierarchy edge : db.all(RoleHierarchy.class))
            if (roles.containsKey(edge.seniorRoleId) && roles.containsKey(edge.juniorRoleId))
                edges.computeIfAbsent(edge.seniorRoleId, k -> new ArrayList<>())
                    .add(edge.juniorRoleId);
        Map<Long, Permission> permissionById = new HashMap<>();
        for (Permission p :
            db.where(Permission.class, "e.systemId=?1 and e.status=?2", systemId, "ENABLED"))
            permissionById.put(p.id, p);
        Map<Long, List<Permission>> grants = new HashMap<>();
        for (RolePermission rp : db.all(RolePermission.class)) {
            Permission p = permissionById.get(rp.permissionId);
            if (p != null)
                grants.computeIfAbsent(rp.roleId, k -> new ArrayList<>()).add(p);
        }
        Set<Long> effective = new LinkedHashSet<>(), permissions = new LinkedHashSet<>();
        List<Map<String, Object>> sources = new ArrayList<>();
        List<Long> validDirect =
            direct.stream()
                .filter(id -> roles.containsKey(id) && "ENABLED".equals(roles.get(id).status))
                .toList();
        for (Long root : validDirect)
            walk(
                root, roles, edges, grants, new LinkedHashSet<>(), effective, permissions, sources);
        return new Snapshot(validDirect, List.copyOf(effective), List.copyOf(permissions), sources);
    }
    private void walk(Long id, Map<Long, Role> roles, Map<Long, List<Long>> edges,
        Map<Long, List<Permission>> grants, LinkedHashSet<Long> path, Set<Long> effective,
        Set<Long> permissions, List<Map<String, Object>> sources) {
        Role role = roles.get(id);
        if (role == null || !"ENABLED".equals(role.status) || path.contains(id))
            return;
        path.add(id);
        effective.add(id);
        for (Permission p : grants.getOrDefault(id, List.of())) {
            permissions.add(p.id);
            sources.add(map("permissionCode", p.code, "permissionKind", p.kind,
                "roleCode", role.code, "inherited", path.size() > 1,
                "path", path.stream().map(x -> roles.get(x).code).toList()));
        }
        for (Long next : edges.getOrDefault(id, List.of()))
            walk(next, roles, edges, grants, path, effective, permissions, sources);
        path.remove(id);
    }
    /** 执行effective操作，保持服务到持久化层的单向依赖。 */
    public Map<String, Object> effective(Snapshot s, String code) {
        return map("directRoles",
            s.direct.stream().map(id -> role(require(Role.class, id), code)).toList(),
            "effectiveRoles",
            s.roles.stream().map(id -> role(require(Role.class, id), code)).toList(), "permissions",
            s.permissions.stream()
                .map(id -> Views.permission(require(Permission.class, id), code))
                .toList(),
            "sources", s.sources);
    }
    /** 执行superAdmin操作，保持服务到持久化层的单向依赖。 */
    public boolean superAdmin(Actor a) {
        return "rbac-center".equals(a.systemCode())
            && hasSuperAdminRole(snapshot(a.userId(), a.systemCode()));
    }
    private boolean hasSuperAdminRole(Snapshot snapshot) {
        return snapshot.roles.stream()
            .map(id -> require(Role.class, id))
            .anyMatch(r -> "SUPER_ADMIN".equals(r.code) && Boolean.TRUE.equals(r.builtin));
    }
    public Set<String> roleCodes(Snapshot snapshot) {
        Set<String> result = new TreeSet<>();
        snapshot.roles.forEach(id -> result.add(require(Role.class, id).code));
        return result;
    }

    public Set<String> courseRoleCodes(Snapshot snapshot) {
        return CourseRoleCodes.retainFrom(roleCodes(snapshot));
    }

    private Set<String> protectedRoleCodes(String code) {
        Set<String> result = new HashSet<>(CourseRoleCodes.ALL);
        db.where(ApiOperation.class, "e.systemId=?1 and e.accessPolicy=?2 and e.requiredRole is not null",
                system(code).id, "PROTECTED").forEach(operation -> result.add(operation.requiredRole));
        return result;
    }
    /** 执行permission操作，保持服务到持久化层的单向依赖。 */
    public boolean permission(Long user, String code, String kind, String permission) {
        if (permission == null)
            return false;
        Snapshot s = snapshot(user, code);
        return !s.permissions.isEmpty()
            && !db.where(Permission.class, "e.id in ?1 and e.kind=?2 and e.code=?3", s.permissions,
                      kind, permission)
                    .isEmpty();
    }
    /** 事务外按同一授权版本读取操作策略并完成判定。 */
    public boolean api(Long user, String code, String operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            return decideApi(user, code, operation);
        for (int attempt = 0; attempt < 3; attempt++) {
            long version = version();
            try {
                boolean allowed = decideApi(user, code, operation);
                if (version == version())
                    return allowed;
            } catch (BizException error) {
                if (error.status >= 500 || version == version())
                    throw error;
            }
        }
        throw new BizException(503, "AUTHORIZATION_UNAVAILABLE");
    }
    private boolean decideApi(Long user, String code, String operation) {
        SoftwareSystem system = system(code);
        User u = require(User.class, user);
        active(u, system);
        ApiOperation op =
            db.one(ApiOperation.class, "e.systemId=?1 and e.operationId=?2", system.id, operation);
        if (op == null)
            throw BizException.missing("API_OPERATION_NOT_FOUND");
        if (!Boolean.TRUE.equals(op.enabled))
            return false;
        if (DashboardAccess.matches(code, operation)) {
            Snapshot s = snapshot(user, code);
            return !s.permissions.isEmpty()
                && db.where(Permission.class, "e.id in ?1 and e.kind=?2 and e.code in ?3",
                         s.permissions, "API", DashboardAccess.REQUIRED_PERMISSIONS)
                       .size() == DashboardAccess.REQUIRED_PERMISSIONS.size();
        }
        if ("PUBLIC".equals(op.accessPolicy) || "AUTHENTICATED".equals(op.accessPolicy))
            return true;
        Snapshot s = snapshot(user, code);
        if ("rbac-center".equals(code) && hasSuperAdminRole(s))
            return true;
        boolean hasRole = op.requiredRole == null || roleCodes(s).contains(op.requiredRole);
        boolean hasPermission = op.permissionId == null || s.permissions.contains(op.permissionId);
        return hasRole && hasPermission && (op.requiredRole != null || op.permissionId != null);
    }
    /** 执行center操作，保持服务到持久化层的单向依赖。 */
    public void center(Actor a, String permission) {
        if (!"rbac-center".equals(a.systemCode()))
            throw BizException.forbidden("AUDIENCE_MISMATCH");
        if (!superAdmin(a) && !permission(a.userId(), a.systemCode(), "API", permission))
            throw BizException.forbidden("FORBIDDEN");
    }
    public void requireDashboard(Actor a) {
        if (!DashboardAccess.SYSTEM.equals(a.systemCode()))
            throw BizException.forbidden("AUDIENCE_MISMATCH");
        if (!api(a.userId(), DashboardAccess.SYSTEM, DashboardAccess.OPERATION))
            throw BizException.forbidden("FORBIDDEN");
    }
    /** 执行root操作，保持服务到持久化层的单向依赖。 */
    public void root(Actor a) {
        if (require(Department.class, require(User.class, a.userId()).departmentId).parentId
            != null)
            throw BizException.forbidden("ROOT_DEPARTMENT_REQUIRED");
    }
    /** 执行scope操作，保持服务到持久化层的单向依赖。 */
    public void scope(Actor a, Long department) {
        Department own = require(Department.class, require(User.class, a.userId()).departmentId),
                   target = require(Department.class, department);
        if (!target.path.startsWith(own.path))
            throw BizException.forbidden("OUT_OF_MANAGE_SCOPE");
    }
    /** 停用系统没有可委派的有效授权，中心超级管理员仍可维护其角色配置。 */
    public void requireRoleGrantSystem(Actor a, SoftwareSystem system) {
        if (!"ENABLED".equals(system.status) && !superAdmin(a))
            throw BizException.forbidden("PRIVILEGE_ESCALATION");
    }
    /** 授权上界同时检查权限和受保护的角色码，包含新继承闭包。 */
    public void upper(Actor a, String code, Snapshot proposed) {
        Upper captured = (Upper) TransactionSynchronizationManager.getResource(upperKey);
        if (captured != null && !captured.userId.equals(a.userId()))
            throw BizException.forbidden("PRIVILEGE_ESCALATION");
        if (captured == null ? superAdmin(a) : captured.superAdmin)
            return;
        // 特殊能力检查不因停用节点切断，防止预埋继承边等待恢复。
        Set<Long> seen = new HashSet<>();
        Deque<Long> pending = new ArrayDeque<>(proposed.direct);
        while (!pending.isEmpty()) {
            Long id = pending.pop();
            if (!seen.add(id))
                continue;
            Role role = require(Role.class, id);
            if (Boolean.TRUE.equals(role.builtin) && "SUPER_ADMIN".equals(role.code))
                throw BizException.forbidden("PRIVILEGE_ESCALATION");
            for (RoleHierarchy edge : db.where(RoleHierarchy.class, "e.seniorRoleId=?1", id))
                pending.push(edge.juniorRoleId);
        }
        Snapshot own = captured == null
            ? snapshot(a.userId(), code)
            : captured.systems.getOrDefault(
                  code, new Snapshot(List.of(), List.of(), List.of(), List.of()));
        Set<String> ownRoleCodes = roleCodes(own);
        Set<String> protectedCodes = protectedRoleCodes(code);
        if (!own.permissions.containsAll(proposed.permissions)
            || roleCodes(proposed).stream().filter(protectedCodes::contains)
                .anyMatch(role -> !ownRoleCodes.contains(role)))
            throw BizException.forbidden("PRIVILEGE_ESCALATION");
    }
    /** 内置超级管理员的定义不能由普通管理员修改。 */
    public void protectRole(Actor a, Role r) {
        if (Boolean.TRUE.equals(r.builtin) && "SUPER_ADMIN".equals(r.code) && !superAdmin(a))
            throw BizException.forbidden("SUPER_ADMIN_PROTECTED");
    }
    /** 所有授权事实事务的第一个持久化操作。 */
    public GrantVersion lock() {
        GrantVersion g = db.lock(GrantVersion.class, 1L);
        if (g == null)
            throw new BizException(503, "AUTHORIZATION_UNAVAILABLE");
        return g;
    }
    /** 保护最后一个有效超级管理员。 */
    public void preserveAdministrator() {
        SoftwareSystem center = system("rbac-center");
        for (User u : db.where(User.class, "e.status=?1", "ACTIVE"))
            for (Long id : calculate(direct(u.id, center.id), center.id).roles) {
                Role r = require(Role.class, id);
                Department d = require(Department.class, u.departmentId);
                if ("SUPER_ADMIN".equals(r.code) && Boolean.TRUE.equals(r.builtin)
                    && d.parentId == null
                    && db.one(DepartmentAllowedRole.class, "e.departmentId=?1 and e.roleId=?2",
                           d.id, r.id)
                        != null
                    && "ENABLED".equals(center.status))
                    return;
            }
        throw BizException.conflict("LAST_SUPER_ADMIN");
    }
}
