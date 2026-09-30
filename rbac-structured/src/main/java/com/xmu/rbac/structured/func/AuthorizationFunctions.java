package com.xmu.rbac.structured.func;

import com.xmu.rbac.structured.dao.AssignmentDao;
import com.xmu.rbac.structured.dao.UserDao;
import com.xmu.rbac.structured.data.AuthzResult;
import com.xmu.rbac.structured.data.UserRecord;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 3.0 授权判定 ★核心。对应结构图顶层模块、DFD 加工 3.0。
 * 依赖（DAO、缓存）作为参数传入，函数本身无状态，不依赖 Spring。docs/06-detail-structured.md §4.2
 */
public final class AuthorizationFunctions {

    private AuthorizationFunctions() {}   // 纯函数集合，禁止实例化

    /** 严格按 3.1 → 3.1b → 3.2 → 3.3 → 3.4 → 3.5 → 3.6 的顺序调用子模块。 */
    public static AuthzResult authorize(String subject,
                                        String resource,
                                        String action,
                                        LocalDateTime now,
                                        UserDao userDao,
                                        AssignmentDao assignmentDao,
                                        CacheHandle cache) {
        long start = System.nanoTime();
        String permissionCode = buildPermissionCode(resource, action);

        // ---- 3.1 校验主体 ----
        UserRecord user = resolveSubject(subject, userDao);
        String deny = AuthenticationFunctions.subjectDenyReason(user, now);
        if (deny != null) {
            return AuthzResult.deny(deny, permissionCode, elapsed(start));
        }
        Long userId = user.id();

        // ---- 3.1b 超级管理员旁路 ----
        // 需求「越过权限引擎」。排在状态校验之后：停用的超管照样被拒。
        // 放行时带原因码，审计里能看出这是旁路而非命中某条权限。见 02-architecture.md §4.1 S1b
        if (CacheFunctions.isSuperAdmin(cache, assignmentDao, userId)) {
            return AuthzResult.bypass(permissionCode, elapsed(start));
        }

        // ---- 3.2 查缓存 ----
        Set<String> effective = CacheFunctions.getUserPermissions(cache, userId);
        boolean cacheHit = (effective != null);

        if (!cacheHit) {
            // ---- 3.3 加载直接角色 ----
            Set<Long> roleIds = assignmentDao.findEnabledRoleIdsByUser(userId);

            // 迭代二在此处插入：roleIds = ClosureFunctions.expand(roleIds, ...);

            // ---- 3.4 汇总角色权限 ----
            effective = PermissionSetFunctions.unionRolePermissions(assignmentDao, roleIds);

            // ---- 3.5 写入缓存 ----
            CacheFunctions.putUserPermissions(cache, userId, effective);
        }

        // ---- 3.6 匹配权限码 ----
        boolean allowed = PermissionSetFunctions.contains(effective, permissionCode);

        // ---- 3.7 数据范围过滤（迭代二插入） ----
        // ---- 3.8 约束校验（迭代三插入） ----

        return allowed
                ? AuthzResult.allow(permissionCode, cacheHit, elapsed(start))
                : AuthzResult.deny(AuthzResult.MISSING_PERMISSION, permissionCode, elapsed(start));
    }

    /** 当前用户的有效权限集（登录与 /auth/me 返回给前端用于按钮级控制）。 */
    public static Set<String> effectivePermissions(Long userId, AssignmentDao assignmentDao, CacheHandle cache) {
        Set<String> effective = CacheFunctions.getUserPermissions(cache, userId);
        if (effective == null) {
            effective = PermissionSetFunctions.unionRolePermissions(
                    assignmentDao, assignmentDao.findEnabledRoleIdsByUser(userId));
            CacheFunctions.putUserPermissions(cache, userId, effective);
        }
        return effective;
    }

    static String buildPermissionCode(String resource, String action) {
        return resource + ":" + action;
    }

    /** 主体标识：「user_」+ 用户 ID，或用户名（契约 AuthzRequest.subject）。 */
    static UserRecord resolveSubject(String subject, UserDao userDao) {
        if (subject == null || subject.isBlank()) {
            return null;
        }
        if (subject.startsWith("user_")) {
            try {
                return userDao.findById(Long.parseLong(subject.substring(5)));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return userDao.findByUsername(subject);
    }

    static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1000;   // 微秒
    }
}
