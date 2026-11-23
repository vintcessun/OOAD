package com.xmu.rbac.ddd.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

/** 状态、资格、权限上界和不可变审计事实的纯 JDK 测试。 */
class DomainRulesTest {
    private Role role(long id, String identity, Set<Long> permissions) {
        return new Role(id, "s", identity == null ? "R" + id : identity, "角色", null, true, false, 0, permissions);
    }

    private Permission permission(long id) {
        return new Permission(id, "s", "p" + id, "权限", Permission.Kind.API, true);
    }

    private AuthorizationPolicy.Effective effective(Role... roles) {
        return new AuthorizationPolicy()
                .calculate(
                        "s",
                        Arrays.stream(roles)
                                .map(Role::id)
                                .collect(java.util.stream.Collectors.toSet()),
                        new RoleHierarchy(List.of(roles), Set.of()),
                        List.of(permission(1), permission(2)));
    }

    @Test
    void roleOwnsDirectPermissionInvariant() {
        Role role = role(1, null, Set.of());
        assertThatThrownBy(
                        () ->
                                role.replacePermissions(
                                        List.of(
                                                new Permission(
                                                        2,
                                                        "other",
                                                        "p",
                                                        "P",
                                                        Permission.Kind.UI,
                                                        true))))
                .hasMessageContaining("CROSS_SYSTEM_PERMISSION");
        role.replacePermissions(List.of(permission(1), permission(1)));
        assertThat(role.permissionIds()).containsExactly(1L);
    }

    @Test
    void disableRemovesAllEffectiveGrants() {
        Role r = role(1, "teacher", Set.of(1L));
        assertThat(effective(r).has("p1", Permission.Kind.API)).isTrue();
        r.changeStatus(false);
        assertThat(effective(r).permissions()).isEmpty();
        assertThat(effective(r).roleCodes()).isEmpty();
    }

    @Test
    void upperBoundIncludesPermission() {
        AuthorizationPolicy p = new AuthorizationPolicy();
        assertThatThrownBy(
                        () ->
                                p.requireWithin(
                                        effective(role(1, null, Set.of(1L))),
                                        effective(role(2, null, Set.of(2L))),
                                        false, Set.of("teacher", "student", "admin")))
                .hasMessageContaining("GRANT_EXCEEDS_ACTOR");
    }

    @Test
    void upperBoundIncludesIdentity() {
        AuthorizationPolicy p = new AuthorizationPolicy();
        assertThatThrownBy(
                        () ->
                                p.requireWithin(
                                        effective(role(1, "student", Set.of(1L))),
                                        effective(role(2, "teacher", Set.of(1L))),
                                        false, Set.of("teacher", "student", "admin")))
                .hasMessageContaining("GRANT_EXCEEDS_ACTOR");
    }

    @Test
    void superAdminCanGrantBeyondOwnTargetSystem() {
        new AuthorizationPolicy()
                .requireWithin(effective(), effective(role(2, "teacher", Set.of(2L))), true, Set.of("teacher", "student", "admin"));
    }

    @Test
    void upperBoundAllowsSubset() {
        new AuthorizationPolicy()
                .requireWithin(
                        effective(role(1, "teacher", Set.of(1L, 2L))),
                        effective(role(2, "teacher", Set.of(1L))),
                        false, Set.of("teacher", "student", "admin"));
    }

    @Test
    void uiDoesNotGrantApi() {
        Role role = role(1, null, Set.of(1L));
        AuthorizationPolicy.Effective e =
                new AuthorizationPolicy()
                        .calculate(
                                "s",
                                Set.of(1L),
                                new RoleHierarchy(List.of(role), Set.of()),
                                List.of(
                                        new Permission(
                                                1, "s", "p1", "P", Permission.Kind.UI, true)));
        assertThat(e.has("p1", Permission.Kind.API)).isFalse();
    }

    @Test
    void protectedWithoutConditionDenies() {
        assertThat(
                        new AuthorizationPolicy()
                                .allows(
                                        new ApiOperation(
                                                1,
                                                "s",
                                                "op",
                                                "GET",
                                                "/op",
                                                null,
                                                null,
                                                ApiOperation.Policy.PROTECTED,
                                                true),
                                        effective()))
                .isFalse();
    }

    @Test
    void courseCopyOnlyRequiresTeacherGlobally() {
        ApiOperation op =
                new ApiOperation(
                        1,
                        "s",
                        "copyCourse",
                        "POST",
                        "/course",
                        null,
                        "teacher",
                        ApiOperation.Policy.PROTECTED,
                        true);
        assertThat(new AuthorizationPolicy().allows(op, effective(role(1, "teacher", Set.of()))))
                .isTrue();
        assertThat(new AuthorizationPolicy().allows(op, effective(role(1, "student", Set.of()))))
                .isFalse();
    }

    @Test
    void unknownOrDisabledOperationDenies() {
        assertThat(
                        new AuthorizationPolicy()
                                .allows(
                                        new ApiOperation(
                                                1,
                                                "s",
                                                "op",
                                                "GET",
                                                "/op",
                                                null,
                                                null,
                                                ApiOperation.Policy.PUBLIC,
                                                false),
                                        effective()))
                .isFalse();
    }

    @Test
    void departmentChecksDirectEligibility() {
        User u = new User(2, "subject", 1, User.Status.ACTIVE, 0, Set.of());
        Role r = role(1, null, Set.of());
        assertThatThrownBy(() -> u.assign(r, new DepartmentEligibility(1, Set.of())))
                .hasMessageContaining("ROLE_NOT_ASSIGNABLE_IN_DEPARTMENT");
        u.assign(r, new DepartmentEligibility(1, Set.of(1L)));
        assertThat(u.roleIds()).containsExactly(1L);
    }

    @Test
    void userTransferClearsAllAssignments() {
        User u = new User(2, "subject", 1, User.Status.ACTIVE, 0, Set.of(1L, 2L));
        u.transfer(2);
        assertThat(u.departmentId()).isEqualTo(2);
        assertThat(u.roleIds()).isEmpty();
    }

    @Test
    void userTransferToCurrentDepartmentKeepsAssignments() {
        User u = new User(2, "subject", 1, User.Status.ACTIVE, 0, Set.of(1L));
        u.transfer(1);
        assertThat(u.roleIds()).containsExactly(1L);
    }

    @Test
    void statusRevokesTokenAndDeletedCannotReactivate() {
        User u = new User(2, "subject", 1, User.Status.ACTIVE, 3, Set.of(1L));
        u.changeStatus(User.Status.DISABLED);
        assertThat(u.tokenVersion()).isEqualTo(4);
        u.changeStatus(User.Status.DELETED);
        assertThat(u.roleIds()).isEmpty();
        assertThatThrownBy(() -> u.changeStatus(User.Status.ACTIVE))
                .hasMessageContaining("USER_DELETED");
    }

    @Test
    void departmentPathIsSegmentSafe() {
        DepartmentEligibility.requireManaged("/1/2/", "/1/2/3/");
        assertThatThrownBy(() -> DepartmentEligibility.requireManaged("/1/2/", "/1/20/"))
                .hasMessageContaining("DEPARTMENT_OUT_OF_SCOPE");
    }

    @Test
    void duplicateAssignmentAndDisabledRoleRejected() {
        User u = new User(2, "s", 1, User.Status.ACTIVE, 0, Set.of(1L));
        Role r = role(1, null, Set.of());
        assertThatThrownBy(() -> u.assign(r, new DepartmentEligibility(1, Set.of(1L))))
                .hasMessageContaining("ASSIGNMENT_EXISTS");
        r.changeStatus(false);
        assertThatThrownBy(() -> new DepartmentEligibility(1, Set.of(1L)).requireEligible(r))
                .hasMessageContaining("ROLE_DISABLED");
    }

    @Test
    void auditReplayCannotCrossSystemOrSubject() {
        String uuid = UUID.randomUUID().toString();
        AuditFact a = fact(uuid, "s", "u", "h");
        a.requireReplay(fact(uuid, "s", "u", "h"));
        assertThatThrownBy(() -> a.requireReplay(fact(uuid, "other", "u", "h")))
                .hasMessageContaining("AUDIT_DUPLICATE_CONFLICT");
        assertThatThrownBy(() -> a.requireReplay(fact(uuid, "s", "other", "h")))
                .hasMessageContaining("AUDIT_DUPLICATE_CONFLICT");
        assertThatThrownBy(() -> a.requireReplay(fact(uuid, "s", "u", "other")))
                .hasMessageContaining("AUDIT_DUPLICATE_CONFLICT");
    }

    private AuditFact fact(String id, String system, String user, String hash) {
        return new AuditFact(
                1L,
                id,
                system,
                "COURSE",
                1L,
                user,
                "Name",
                List.of("teacher"),
                "score_changed",
                "course",
                "cpm-course-1",
                "target",
                null,
                null,
                "SUCCESS",
                null,
                "trace",
                hash,
                Instant.now());
    }
}
