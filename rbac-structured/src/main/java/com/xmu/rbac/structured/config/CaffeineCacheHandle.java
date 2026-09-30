package com.xmu.rbac.structured.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xmu.rbac.structured.func.CacheHandle;

import java.time.Duration;
import java.util.Set;

/** L1 本地缓存。TTL 是兜底，正确性靠变更时的显式失效（02-architecture.md §4.2）。 */
public class CaffeineCacheHandle implements CacheHandle {

    private final Cache<Long, Set<String>> permissions;
    private final Cache<Long, Boolean> superAdmin;

    public CaffeineCacheHandle(long maximumSize, Duration ttl) {
        this.permissions = Caffeine.newBuilder().maximumSize(maximumSize).expireAfterWrite(ttl).build();
        this.superAdmin = Caffeine.newBuilder().maximumSize(maximumSize).expireAfterWrite(ttl).build();
    }

    @Override
    public Set<String> getPermissions(Long userId) {
        return permissions.getIfPresent(userId);
    }

    @Override
    public void putPermissions(Long userId, Set<String> perms) {
        permissions.put(userId, perms);
    }

    @Override
    public Boolean getSuperAdmin(Long userId) {
        return superAdmin.getIfPresent(userId);
    }

    @Override
    public void putSuperAdmin(Long userId, boolean value) {
        superAdmin.put(userId, value);
    }

    @Override
    public void invalidateUser(Long userId) {
        permissions.invalidate(userId);
        superAdmin.invalidate(userId);
    }
}
