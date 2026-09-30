package com.xmu.rbac.structured.func;

import com.xmu.rbac.structured.dao.AssignmentDao;
import com.xmu.rbac.structured.dao.UserDao;
import com.xmu.rbac.structured.data.RoleRecord;
import com.xmu.rbac.structured.data.UserRecord;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内存版 DAO 与缓存。功能层的依赖都是参数，测试直接传这些实现，不启动 Spring、不用 Mock 框架——
 * 这正是「判定内核不依赖框架」这条架构原则（02 §2.2）带来的可测试性。
 */
public final class Fixtures {

    private Fixtures() {}

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 9, 0);

    public static UserRecord user(long id, String username, int status) {
        return new UserRecord(id, username, "hash-" + username, username, 1L, null, status,
                "EMPLOYEE", null, 0, null, false, null);
    }

    public static final class InMemoryUserDao implements UserDao {
        public final Map<Long, UserRecord> users = new LinkedHashMap<>();
        public int findByIdCalls;

        public InMemoryUserDao add(UserRecord u) {
            users.put(u.id(), u);
            return this;
        }

        @Override
        public UserRecord findById(Long id) {
            findByIdCalls++;
            return users.get(id);
        }

        @Override
        public UserRecord findByUsername(String username) {
            return users.values().stream().filter(u -> u.username().equals(username)).findFirst().orElse(null);
        }

        @Override
        public int updateLoginFailure(Long id, int failCount, LocalDateTime lockedUntil) {
            UserRecord u = users.get(id);
            users.put(id, new UserRecord(u.id(), u.username(), u.passwordHash(), u.realName(), u.departmentId(),
                    u.position(), u.status(), u.userType(), u.expiresAt(), failCount, lockedUntil,
                    u.mustChangePwd(), u.deletedAt()));
            return 1;
        }

        @Override
        public int resetLoginFailure(Long id) {
            return updateLoginFailure(id, 0, null);
        }

        @Override
        public int updatePassword(Long id, String passwordHash) {
            UserRecord u = users.get(id);
            users.put(id, new UserRecord(u.id(), u.username(), passwordHash, u.realName(), u.departmentId(),
                    u.position(), u.status(), u.userType(), u.expiresAt(), u.loginFailCount(), u.lockedUntil(),
                    false, u.deletedAt()));
            return 1;
        }
    }

    public static final class InMemoryAssignmentDao implements AssignmentDao {
        public final Map<Long, RoleRecord> roles = new HashMap<>();
        public final Map<Long, Set<Long>> userRoles = new HashMap<>();
        public final Map<Long, Set<String>> rolePermissions = new HashMap<>();
        public int roleQueries;
        public int superAdminQueries;

        public InMemoryAssignmentDao role(long id, String code, String... permissions) {
            roles.put(id, new RoleRecord(id, code, code, 1, false));
            rolePermissions.put(id, new HashSet<>(List.of(permissions)));
            return this;
        }

        public InMemoryAssignmentDao disableRole(long id) {
            RoleRecord r = roles.get(id);
            roles.put(id, new RoleRecord(r.id(), r.roleCode(), r.roleName(), 0, r.builtin()));
            return this;
        }

        public InMemoryAssignmentDao assign(long userId, long roleId) {
            userRoles.computeIfAbsent(userId, k -> new HashSet<>()).add(roleId);
            return this;
        }

        public InMemoryAssignmentDao revoke(long userId, long roleId) {
            userRoles.getOrDefault(userId, new HashSet<>()).remove(roleId);
            return this;
        }

        @Override
        public Set<Long> findEnabledRoleIdsByUser(Long userId) {
            roleQueries++;
            Set<Long> result = new HashSet<>();
            for (Long rid : userRoles.getOrDefault(userId, Set.of())) {
                if (roles.get(rid).status() == 1) {
                    result.add(rid);
                }
            }
            return result;
        }

        @Override
        public List<String> findPermissionCodesByRoleIds(Set<Long> roleIds) {
            List<String> codes = new ArrayList<>();
            roleIds.forEach(rid -> codes.addAll(rolePermissions.getOrDefault(rid, Set.of())));
            return codes;
        }

        @Override
        public boolean holdsSuperAdmin(Long userId) {
            superAdminQueries++;
            return userRoles.getOrDefault(userId, Set.of()).stream()
                    .map(roles::get)
                    .anyMatch(r -> r.status() == 1 && r.roleCode().equals("SUPER_ADMIN"));
        }

        @Override
        public List<RoleRecord> findRolesByUser(Long userId) {
            return userRoles.getOrDefault(userId, Set.of()).stream().sorted().map(roles::get).toList();
        }
    }

    public static final class MapCacheHandle implements CacheHandle {
        public final Map<Long, Set<String>> permissions = new HashMap<>();
        public final Map<Long, Boolean> superAdmin = new HashMap<>();

        @Override
        public Set<String> getPermissions(Long userId) {
            return permissions.get(userId);
        }

        @Override
        public void putPermissions(Long userId, Set<String> perms) {
            permissions.put(userId, perms);
        }

        @Override
        public Boolean getSuperAdmin(Long userId) {
            return superAdmin.get(userId);
        }

        @Override
        public void putSuperAdmin(Long userId, boolean value) {
            superAdmin.put(userId, value);
        }

        @Override
        public void invalidateUser(Long userId) {
            permissions.remove(userId);
            superAdmin.remove(userId);
        }
    }
}
