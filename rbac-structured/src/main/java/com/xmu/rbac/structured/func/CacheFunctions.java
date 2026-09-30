package com.xmu.rbac.structured.func;

import com.xmu.rbac.structured.dao.AssignmentDao;

import java.util.Set;

/** 3.2 / 3.5 缓存读写。功能内聚：只做缓存。 */
public final class CacheFunctions {

    private CacheFunctions() {}

    public static Set<String> getUserPermissions(CacheHandle cache, Long userId) {
        return cache.getPermissions(userId);
    }

    public static void putUserPermissions(CacheHandle cache, Long userId, Set<String> permissions) {
        cache.putPermissions(userId, Set.copyOf(permissions));
    }

    /**
     * 超管标记与有效权限集一起缓存、一起失效（02-architecture.md §4.2）：
     * 否则撤销 SUPER_ADMIN 后，旁路会凭旧标记继续放行。
     */
    public static boolean isSuperAdmin(CacheHandle cache, AssignmentDao dao, Long userId) {
        Boolean cached = cache.getSuperAdmin(userId);
        if (cached != null) {
            return cached;
        }
        boolean superAdmin = dao.holdsSuperAdmin(userId);
        cache.putSuperAdmin(userId, superAdmin);
        return superAdmin;
    }

    /**
     * 权限变更后调用。必须在事务提交之后：提交前删缓存，并发读会把旧数据写回去。
     * 结构化实现没有事件机制，每个修改权限的接口都要手动调用——这是范式代价，见 06 §4.5。
     */
    public static void invalidateUser(CacheHandle cache, Long userId) {
        cache.invalidateUser(userId);
    }
}
