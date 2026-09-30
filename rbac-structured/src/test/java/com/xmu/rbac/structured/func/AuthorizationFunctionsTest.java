package com.xmu.rbac.structured.func;

import com.xmu.rbac.structured.data.AuthzResult;
import com.xmu.rbac.structured.data.UserRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.xmu.rbac.structured.func.Fixtures.NOW;
import static com.xmu.rbac.structured.func.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 判定内核的单元测试。不启动 Spring，依赖全部是内存实现。 */
class AuthorizationFunctionsTest {

    private Fixtures.InMemoryUserDao users;
    private Fixtures.InMemoryAssignmentDao assignments;
    private Fixtures.MapCacheHandle cache;

    @BeforeEach
    void setUp() {
        users = new Fixtures.InMemoryUserDao()
                .add(user(1, "alice", UserRecord.STATUS_ACTIVE))
                .add(user(2, "bob", UserRecord.STATUS_DISABLED))
                .add(user(3, "carol", UserRecord.STATUS_DELETED))
                .add(user(4, "root", UserRecord.STATUS_ACTIVE))
                .add(user(5, "root2", UserRecord.STATUS_DISABLED))
                .add(user(6, "dave", UserRecord.STATUS_ACTIVE));
        assignments = new Fixtures.InMemoryAssignmentDao()
                .role(10, "EMPLOYEE", "oa:doc:draft:view", "oa:doc:draft:create")
                .role(11, "DEPT_MANAGER", "oa:doc:draft:approve")
                .role(99, "SUPER_ADMIN")
                .assign(1, 10)
                .assign(2, 10)
                .assign(4, 99)
                .assign(5, 99);
        cache = new Fixtures.MapCacheHandle();
    }

    private AuthzResult check(String subject, String resource, String action) {
        return AuthorizationFunctions.authorize(subject, resource, action, NOW, users, assignments, cache);
    }

    @Test
    void allowsWhenRoleGrantsPermission() {
        AuthzResult r = check("user_1", "oa:doc:draft", "view");
        assertTrue(r.allowed());
        assertNull(r.reason());
        assertEquals("oa:doc:draft:view", r.requiredPermission());
        assertFalse(r.cached());
    }

    @Test
    void acceptsUsernameAsSubject() {
        assertTrue(check("alice", "oa:doc:draft", "create").allowed());
    }

    @Test
    void deniesMissingPermission() {
        AuthzResult r = check("user_1", "oa:doc:draft", "approve");
        assertFalse(r.allowed());
        assertEquals(AuthzResult.MISSING_PERMISSION, r.reason());
    }

    @Test
    void deniesInvalidSubjects() {
        assertEquals(AuthzResult.SUBJECT_INVALID, check(null, "oa:doc:draft", "view").reason());
        assertEquals(AuthzResult.SUBJECT_INVALID, check("  ", "oa:doc:draft", "view").reason());
        assertEquals(AuthzResult.SUBJECT_INVALID, check("user_abc", "oa:doc:draft", "view").reason());
        assertEquals(AuthzResult.SUBJECT_INVALID, check("user_404", "oa:doc:draft", "view").reason());
        assertEquals(AuthzResult.SUBJECT_INVALID, check("nobody", "oa:doc:draft", "view").reason());
    }

    @Test
    void deniesDisabledAndDeletedEvenWithPermission() {
        assertEquals(AuthzResult.SUBJECT_DISABLED, check("user_2", "oa:doc:draft", "view").reason());
        assertEquals(AuthzResult.SUBJECT_DELETED, check("user_3", "oa:doc:draft", "view").reason());
    }

    @Test
    void deniesExpiredTemporaryUser() {
        users.add(new UserRecord(7L, "temp", "h", "temp", null, null, 1, "TEMPORARY",
                NOW.minusMinutes(1), 0, null, false, null));
        assignments.assign(7, 10);
        assertEquals(AuthzResult.SUBJECT_EXPIRED, check("user_7", "oa:doc:draft", "view").reason());
    }

    @Test
    void superAdminBypassesEngineWithReasonCode() {
        AuthzResult r = check("user_4", "any:thing:at", "all");
        assertTrue(r.allowed());
        assertEquals(AuthzResult.SUPER_ADMIN_BYPASS, r.reason());
    }

    @Test
    void disabledSuperAdminIsStillDenied() {
        // 旁路排在状态校验之后：停用的超管照样被拒
        assertEquals(AuthzResult.SUBJECT_DISABLED, check("user_5", "any:thing:at", "all").reason());
    }

    @Test
    void superAdminFlagIsCachedAndInvalidatedTogether() {
        check("user_4", "a:b:c", "view");
        check("user_4", "a:b:c", "view");
        assertEquals(1, assignments.superAdminQueries);

        assignments.revoke(4, 99);
        CacheFunctions.invalidateUser(cache, 4L);
        assertEquals(AuthzResult.MISSING_PERMISSION, check("user_4", "a:b:c", "view").reason());
    }

    @Test
    void secondCallHitsCache() {
        check("user_1", "oa:doc:draft", "view");
        AuthzResult r = check("user_1", "oa:doc:draft", "view");
        assertTrue(r.cached());
        assertEquals(1, assignments.roleQueries);
    }

    @Test
    void permissionChangeTakesEffectOnNextAccessAfterInvalidation() {
        assertFalse(check("user_1", "oa:doc:draft", "approve").allowed());

        assignments.assign(1, 11);
        CacheFunctions.invalidateUser(cache, 1L);

        assertTrue(check("user_1", "oa:doc:draft", "approve").allowed());
    }

    @Test
    void disabledRoleContributesNothing() {
        assignments.disableRole(10);
        assertFalse(check("user_1", "oa:doc:draft", "view").allowed());
    }

    @Test
    void userWithoutRolesHasEmptyPermissionsCachedAsEmpty() {
        assertFalse(check("user_6", "oa:doc:draft", "view").allowed());
        assertEquals(Set.of(), cache.permissions.get(6L));
    }

    @Test
    void effectivePermissionsLoadsOnceThenUsesCache() {
        Set<String> first = AuthorizationFunctions.effectivePermissions(1L, assignments, cache);
        Set<String> second = AuthorizationFunctions.effectivePermissions(1L, assignments, cache);
        assertEquals(Set.of("oa:doc:draft:view", "oa:doc:draft:create"), first);
        assertEquals(first, second);
        assertEquals(1, assignments.roleQueries);
    }

    @Test
    void permissionSetContainsHandlesNull() {
        assertFalse(PermissionSetFunctions.contains(null, "x"));
        assertEquals(Set.of(), PermissionSetFunctions.unionRolePermissions(assignments, null));
    }
}
