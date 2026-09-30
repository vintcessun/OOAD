package com.xmu.rbac.structured.func;

import java.util.Set;

/**
 * 缓存句柄。功能层只依赖这个接口，测试时传入内存实现即可，无需 Mock 框架。
 * 当前实现为 L1 本地缓存（config.CaffeineCacheHandle）；L2 Redis 与跨实例失效广播待接入，
 * 见 docs/02-architecture.md §4.2。
 */
public interface CacheHandle {

    /** 未命中返回 null；命中空集合表示「确实没有任何权限」。 */
    Set<String> getPermissions(Long userId);

    void putPermissions(Long userId, Set<String> permissions);

    /** 未命中返回 null。 */
    Boolean getSuperAdmin(Long userId);

    void putSuperAdmin(Long userId, boolean superAdmin);

    /** 删除该用户的全部缓存项（有效权限集 + 超管标记）。 */
    void invalidateUser(Long userId);
}
