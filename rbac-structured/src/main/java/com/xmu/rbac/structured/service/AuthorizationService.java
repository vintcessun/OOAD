package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.AssignmentRow;
import com.xmu.rbac.structured.dto.Decision;
import com.xmu.rbac.structured.dto.GrantRow;
import com.xmu.rbac.structured.dto.GrantSet;
import com.xmu.rbac.structured.dto.Scope;
import com.xmu.rbac.structured.entity.Role;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.mapper.DepartmentMapper;
import com.xmu.rbac.structured.mapper.GrantCacheMapper;
import com.xmu.rbac.structured.mapper.GrantVersionMapper;
import com.xmu.rbac.structured.mapper.UserMapper;
import com.xmu.rbac.structured.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 3.0 授权判定（docs/06-detail-structured.md §5.2）。
 *
 * <pre>
 * 3.1 校验主体状态 → 3.2 读取授权数据（缓存 / 数据库）→ 3.3 超级管理员旁路
 *   → 3.4 找出含目标权限的授权 → 3.5 作用域判定（逐条授权）→ ALLOW / DENY
 * </pre>
 *
 * 鉴权接口 /authz/check、权限解释 /authz/explain、所有管理接口的权限拦截、
 * 以及 Service 里「目标对象是否在管理范围内」的检查，全部调用同一个 {@link #decide}。
 */
@Service
public class AuthorizationService {

    public static final String ROLE_GRANTED = "ROLE_GRANTED";
    public static final String SUPER_ADMIN_BYPASS = "SUPER_ADMIN_BYPASS";
    public static final String SUBJECT_NOT_FOUND = "SUBJECT_NOT_FOUND";
    public static final String SUBJECT_DISABLED = "SUBJECT_DISABLED";
    public static final String SUBJECT_DELETED = "SUBJECT_DELETED";
    public static final String SUBJECT_EXPIRED = "SUBJECT_EXPIRED";
    public static final String MISSING_PERMISSION = "MISSING_PERMISSION";
    public static final String OUT_OF_DATA_SCOPE = "OUT_OF_DATA_SCOPE";
    public static final String TARGET_DEPT_NOT_FOUND = "TARGET_DEPT_NOT_FOUND";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final DepartmentMapper departmentMapper;
    private final GrantCacheMapper grantCacheMapper;
    private final GrantVersionMapper grantVersionMapper;
    private final Clock clock;

    public AuthorizationService(UserMapper userMapper, UserRoleMapper userRoleMapper,
                                DepartmentMapper departmentMapper, GrantCacheMapper grantCacheMapper,
                                GrantVersionMapper grantVersionMapper, Clock clock) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.departmentMapper = departmentMapper;
        this.grantCacheMapper = grantCacheMapper;
        this.grantVersionMapper = grantVersionMapper;
        this.clock = clock;
    }

    /** 读取授权数据的结果：数据本身，以及是否来自缓存。 */
    public record LoadedGrants(GrantSet grants, boolean cacheHit) {
    }

    // ------------------------------------------------------------------ 3.0 判定

    /**
     * @param targetDeptId 被操作对象所属部门；为空表示不涉及具体部门的数据（只问「能否执行该操作」）
     * @param explain      true 时逐步记录判定依据（权限解释接口）
     */
    public Decision decide(Long userId, String permissionCode, Long targetDeptId, boolean explain) {
        long start = System.nanoTime();
        List<String> trace = explain ? new ArrayList<>() : null;

        // ---- 3.1 校验主体状态 ----
        User user = userId == null ? null : userMapper.findById(userId).orElse(null);
        String username = user == null ? null : user.username;
        String deny = subjectDenyReason(user, LocalDateTime.now(clock));
        if (deny != null) {
            note(trace, "主体无效：" + deny);
            return result(false, deny, userId, username, permissionCode, targetDeptId, null, false, start, trace);
        }
        note(trace, "用户 " + user.username + "（" + user.realName + "）账号有效");

        // ---- 3.2 读取授权数据 ----
        LoadedGrants loaded = loadGrants(userId);
        GrantSet grants = loaded.grants();

        // ---- 3.3 超级管理员旁路 ----
        if (holdsSuperAdmin(grants)) {
            note(trace, "持有超级管理员角色，越过权限引擎直接放行");
            return result(true, SUPER_ADMIN_BYPASS, userId, username, permissionCode, targetDeptId,
                    null, loaded.cacheHit(), start, trace);
        }

        // ---- 3.4 找出含目标权限的授权 ----
        List<GrantRow> matching = grants.grants().stream()
                .filter(g -> g.permissionCode().equals(permissionCode))
                .toList();
        if (matching.isEmpty()) {
            if (trace != null) {
                if (grants.assignments().isEmpty()) {
                    trace.add("没有任何启用中的角色");
                } else {
                    trace.add("当前角色：" + describeAssignments(grants.assignments()) + "，均不包含 " + permissionCode);
                }
            }
            return result(false, MISSING_PERMISSION, userId, username, permissionCode, targetDeptId,
                    null, loaded.cacheHit(), start, trace);
        }

        // ---- 3.5 作用域判定：逐条授权检查适用部门是否覆盖目标部门 ----
        if (targetDeptId == null) {
            GrantRow g = preferUnrestricted(matching);
            note(trace, "角色 " + g.roleName() + "（适用部门：" + g.scopeDeptName() + "）具有 " + permissionCode
                    + "；本次请求不涉及具体部门");
            return allow(g, matching, userId, username, permissionCode, null, loaded.cacheHit(), start, trace);
        }
        Optional<String> targetPath = departmentMapper.findPathById(targetDeptId);
        if (targetPath.isEmpty()) {
            note(trace, "目标部门 " + targetDeptId + " 不存在");
            return result(false, TARGET_DEPT_NOT_FOUND, userId, username, permissionCode, targetDeptId,
                    null, loaded.cacheHit(), start, trace);
        }
        List<GrantRow> covering = matching.stream()
                .filter(g -> covers(g.scopePath(), targetPath.get()))
                .toList();
        if (covering.isEmpty()) {
            if (trace != null) {
                for (GrantRow g : matching) {
                    trace.add("角色 " + g.roleName() + " 具有该权限，但只适用于「" + g.scopeDeptName()
                            + "」及其下级，目标部门不在其中");
                }
            }
            return result(false, OUT_OF_DATA_SCOPE, userId, username, permissionCode, targetDeptId,
                    null, loaded.cacheHit(), start, trace);
        }
        GrantRow g = preferUnrestricted(covering);
        note(trace, "角色 " + g.roleName() + " 具有 " + permissionCode + "，适用部门「" + g.scopeDeptName()
                + "」覆盖目标部门");
        return allow(g, covering, userId, username, permissionCode, targetDeptId, loaded.cacheHit(), start, trace);
    }

    /** 3.1 主体有效性：不存在、已删除、已停用、已到期。有效时返回 null。 */
    public static String subjectDenyReason(User u, LocalDateTime now) {
        if (u == null) {
            return SUBJECT_NOT_FOUND;
        }
        if (User.DELETED.equals(u.status)) {
            return SUBJECT_DELETED;
        }
        if (!User.ACTIVE.equals(u.status)) {
            return SUBJECT_DISABLED;
        }
        if (u.expiresAt != null && !now.isBefore(u.expiresAt)) {
            return SUBJECT_EXPIRED;
        }
        return null;
    }

    /** 物化路径前缀比较：目标部门在适用部门之下（含其本身）。 */
    public static boolean covers(String scopePath, String targetPath) {
        return scopePath != null && targetPath != null && targetPath.startsWith(scopePath);
    }

    // ------------------------------------------------------------------ 3.2 授权数据与缓存

    /**
     * 3.2 读取用户的授权数据。先从 MySQL 读版本号，按它查 Redis，未命中再查库并写回。
     *
     * <p>版本号必须在授权数据之前读：授权变更与版本号加一在同一个事务里提交，
     * 读到新版本号的请求随后读到的授权数据一定也是新的；读到旧版本号的请求即使读到了新数据，
     * 写回的也是旧版本的键，之后没有人再读它。所以 Redis 出任何故障都不会让回收的权限继续生效
     * （docs/12-design-log.md #37）。代价是每次判定多一次按主键读一行，判定本来就要查 sys_user，缓存省下的是授权连表查询。
     *
     * <p>在读写事务里不写回缓存：这时读到的可能包含本事务尚未提交的修改，事务回滚后它就是错的。
     */
    public LoadedGrants loadGrants(Long userId) {
        Long version = grantVersionMapper.current().orElse(null);
        if (version != null) {
            Optional<GrantSet> cached = grantCacheMapper.find(version, userId);
            if (cached.isPresent()) {
                return new LoadedGrants(cached.get(), true);
            }
        }
        List<AssignmentRow> assignments = userRoleMapper.findAssignmentsOfUser(userId).stream()
                .filter(a -> Role.ENABLED.equals(a.roleStatus()))
                .toList();
        GrantSet grants = new GrantSet(assignments, userRoleMapper.findGrantsOfUser(userId));
        if (version != null && !inReadWriteTransaction()) {
            grantCacheMapper.save(version, userId, grants);
        }
        return new LoadedGrants(grants, false);
    }

    /**
     * 授权数据（指派、角色权限、角色状态）变更时调用：在当前事务里让版本号加一，随修改一起提交或回滚。
     * 提交前其他请求读不到新版本号，读的仍是旧键里与已提交数据一致的内容；提交后它们读新版本号，缓存未命中，查库得到新数据。
     * 版本号那一行会被锁到本事务提交，并发的授权修改因此排队，读请求不受影响。
     * 版本行缺失时抛异常让整个修改回滚，宁可改不成，也不留下缓存与数据库不一致的状态。
     */
    public void bumpGrantVersion() {
        if (grantVersionMapper.bump() != 1) {
            throw new IllegalStateException("sys_grant_version 缺少 id = 1 的行，授权修改已回滚");
        }
    }

    private static boolean inReadWriteTransaction() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    // ------------------------------------------------------------------ 管理范围（供各管理 Service 使用）

    public boolean isSuperAdmin(Long userId) {
        return holdsSuperAdmin(loadGrants(userId).grants());
    }

    /** 用户持有某权限的部门范围（超级管理员为全部）。 */
    public Scope scopeOf(Long userId, String permissionCode) {
        GrantSet grants = loadGrants(userId).grants();
        if (holdsSuperAdmin(grants)) {
            return Scope.ALL;
        }
        List<String> paths = grants.grants().stream()
                .filter(g -> g.permissionCode().equals(permissionCode))
                .map(GrantRow::scopePath)
                .distinct()
                .toList();
        return new Scope(false, paths);
    }

    public static boolean inScope(Scope scope, String targetPath) {
        return scope.all() || scope.paths().stream().anyMatch(p -> covers(p, targetPath));
    }

    /**
     * 管理操作的目标对象检查：操作者对目标部门持有该权限，否则 403。
     * 拦截器只确认「能执行这一类操作」，具体对象是否在管理范围内由这里确认（docs/06 §5.4）。
     */
    public void requireInScope(Long operatorId, String permissionCode, Long targetDeptId) {
        Decision d = decide(operatorId, permissionCode, targetDeptId, false);
        if (!d.allowed()) {
            throw BizException.forbidden(BizException.OUT_OF_MANAGE_SCOPE,
                    "目标不在你的管理范围内（需要在该部门持有 " + permissionCode + "）");
        }
    }

    // ------------------------------------------------------------------ 内部过程

    static boolean holdsSuperAdmin(GrantSet grants) {
        return grants.assignments().stream().anyMatch(a -> Role.SUPER_ADMIN.equals(a.roleCode()));
    }

    /** 同一权限有多条授权时，优先取不限本人的那条。 */
    static GrantRow preferUnrestricted(List<GrantRow> rows) {
        return rows.stream().filter(r -> !r.selfOnly()).findFirst().orElse(rows.get(0));
    }

    private static Decision allow(GrantRow g, List<GrantRow> candidates, Long userId, String username,
                                  String permissionCode, Long targetDeptId, boolean cacheHit, long start,
                                  List<String> trace) {
        boolean selfOnly = candidates.stream().allMatch(GrantRow::selfOnly);
        if (selfOnly) {
            note(trace, "该权限只限本人记录，由业务系统按记录归属过滤");
        }
        return new Decision(true, ROLE_GRANTED, userId, username, permissionCode, targetDeptId, g, selfOnly,
                cacheHit, elapsedMicros(start), trace);
    }

    private static Decision result(boolean allowed, String reason, Long userId, String username,
                                   String permissionCode, Long targetDeptId, GrantRow matched, boolean cacheHit,
                                   long start, List<String> trace) {
        return new Decision(allowed, reason, userId, username, permissionCode, targetDeptId, matched, false,
                cacheHit, elapsedMicros(start), trace);
    }

    private static String describeAssignments(List<AssignmentRow> rows) {
        return String.join("、", rows.stream()
                .map(a -> a.roleName() + "@" + a.scopeDeptName())
                .toList());
    }

    private static void note(List<String> trace, String line) {
        if (trace != null) {
            trace.add(line);
        }
    }

    private static long elapsedMicros(long start) {
        return (System.nanoTime() - start) / 1000;
    }
}
