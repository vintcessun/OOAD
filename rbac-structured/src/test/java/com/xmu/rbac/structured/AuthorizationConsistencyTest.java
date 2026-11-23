package com.xmu.rbac.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.structured.dto.Actor;
import com.xmu.rbac.structured.dto.Inputs;
import com.xmu.rbac.structured.entity.ApiOperation;
import com.xmu.rbac.structured.entity.GrantVersion;
import com.xmu.rbac.structured.entity.Permission;
import com.xmu.rbac.structured.entity.Role;
import com.xmu.rbac.structured.entity.RoleHierarchy;
import com.xmu.rbac.structured.entity.RolePermission;
import com.xmu.rbac.structured.entity.SoftwareSystem;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.entity.UserRole;
import com.xmu.rbac.structured.mapper.Store;
import com.xmu.rbac.structured.service.AuditService;
import com.xmu.rbac.structured.service.AuthorizationService;
import com.xmu.rbac.structured.service.BizException;
import com.xmu.rbac.structured.service.ManagementService;
import com.xmu.rbac.structured.service.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;

class AuthorizationConsistencyTest {
    private final Store store = mock(Store.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AuthorizationService authorization = new AuthorizationService(store, redis, json);

    @BeforeEach
    void setUp() {
        SoftwareSystem system = new SoftwareSystem();
        system.id = 2L;
        system.code = "course";
        system.status = "ENABLED";
        when(store.one(SoftwareSystem.class, "e.code=?1", "course")).thenReturn(system);
        when(store.get(SoftwareSystem.class, 2L)).thenReturn(system);
        when(store.get(GrantVersion.class, 1L)).thenReturn(version(7), version(8));
    }

    @Test
    void apiReadsOperationAgainWhenVersionChanges() {
        when(store.get(User.class, 42L)).thenReturn(user("ACTIVE"));
        ApiOperation enabled = new ApiOperation();
        enabled.enabled = true;
        enabled.accessPolicy = "PUBLIC";
        ApiOperation disabled = new ApiOperation();
        disabled.enabled = false;
        when(store.one(ApiOperation.class, "e.systemId=?1 and e.operationId=?2", 2L, "operation"))
            .thenReturn(enabled, disabled);

        assertThat(authorization.api(42L, "course", "operation")).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshotChecksUserStatusAgainWhenVersionChanges() {
        when(store.get(User.class, 42L)).thenReturn(user("ACTIVE"), user("DISABLED"));
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));

        assertThatExceptionOfType(BizException.class)
            .isThrownBy(() -> authorization.snapshot(42L, "course"))
            .satisfies(error -> assertThat(error.status).isEqualTo(401));
    }

    @Test
    void enabledCenterOperationRecognizesSuperAdminWithoutExplicitPermission() {
        SoftwareSystem center = new SoftwareSystem();
        center.id = 1L;
        center.status = "ENABLED";
        Role role = role(3L, center.id, "SUPER_ADMIN");
        role.builtin = true;
        ApiOperation operation = new ApiOperation();
        operation.enabled = true;
        operation.accessPolicy = "PROTECTED";
        operation.permissionId = 4L;
        when(store.one(SoftwareSystem.class, "e.code=?1", "rbac-center")).thenReturn(center);
        when(store.get(SoftwareSystem.class, center.id)).thenReturn(center);
        when(store.get(User.class, 42L)).thenReturn(user("ACTIVE"));
        when(store.get(Role.class, role.id)).thenReturn(role);
        when(store.where(UserRole.class, "e.userId=?1", 42L))
            .thenReturn(List.of(assignment(role.id)));
        when(store.where(Role.class, "e.systemId=?1", center.id)).thenReturn(List.of(role));
        when(store.one(ApiOperation.class, "e.systemId=?1 and e.operationId=?2", center.id,
                      "listSystems")).thenReturn(operation);

        assertThat(authorization.api(42L, "rbac-center", "listSystems")).isTrue();

        operation.enabled = false;
        assertThat(authorization.api(42L, "rbac-center", "listSystems")).isFalse();
    }

    @Test
    void rejectsBlankIdentityRequirementsInNewAndExistingCourseOperations() {
        Actor actor = new Actor(42L, "admin", "rbac-center", "jti", null);
        var authorized = mock(AuthorizationService.class);
        var service = new ResourceService(store, authorized, mock(AuditService.class), null);
        var old = new ApiOperation();
        old.id = 9L;
        old.systemId = 2L;
        old.operationId = "operation";
        old.method = "GET";
        old.pathTemplate = "/fixture";
        old.requiredRole = "  ";
        old.accessPolicy = "PROTECTED";
        old.enabled = true;
        when(authorized.require(ApiOperation.class, old.id)).thenReturn(old);
        var create = new Inputs.Api("operation", "GET", "/fixture", null, "  ", "PROTECTED", true);
        var patch = new Inputs.ApiPatch();
        patch.setEnabled(false);

        for (Runnable save : List.<Runnable>of(() -> service.saveApi(actor, "course", null, create),
                 () -> service.patchApi(actor, old.id, patch))) {
            assertThatExceptionOfType(BizException.class)
                .isThrownBy(save::run)
                .satisfies(error -> {
                    assertThat(error.status).isEqualTo(400);
                    assertThat(error.code).isEqualTo("VALIDATION_ERROR");
                });
        }
        verify(store, never()).flush();
    }

    @Test
    @SuppressWarnings("unchecked")
    void reloadsUntypedCachedSourcesForPermissionsWithTheSameCode() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Role uiRole = role(10L, 2L, "UI_ROLE");
        Role apiRole = role(20L, 2L, "API_ROLE");
        when(store.get(User.class, 42L)).thenReturn(user("ACTIVE"));
        when(store.get(GrantVersion.class, 1L)).thenReturn(version(7));
        when(store.get(Role.class, 10L)).thenReturn(uiRole);
        when(store.get(Role.class, 20L)).thenReturn(apiRole);
        when(store.where(UserRole.class, "e.userId=?1", 42L))
            .thenReturn(List.of(assignment(10L), assignment(20L)));
        when(store.where(Role.class, "e.systemId=?1", 2L)).thenReturn(List.of(uiRole, apiRole));
        when(store.where(Permission.class, "e.systemId=?1 and e.status=?2", 2L, "ENABLED"))
            .thenReturn(List.of(permission(100L, "UI"), permission(200L, "API")));
        when(store.all(RolePermission.class))
            .thenReturn(List.of(grant(10L, 100L), grant(20L, 200L)));
        var previous = new AuthorizationService.Snapshot(List.of(10L, 20L), List.of(10L, 20L),
            List.of(100L, 200L), List.of(Map.of("permissionCode", "shared", "roleCode", "UI_ROLE",
                "inherited", false, "path", List.of("UI_ROLE"))));
        when(values.get("structured:authz:course:42:7")).thenReturn(json.writeValueAsString(previous));

        var result = authorization.snapshot(42L, "course");

        assertThat(result.sources()).containsExactly(
            Map.of("permissionCode", "shared", "permissionKind", "UI", "roleCode", "UI_ROLE",
                "inherited", false, "path", List.of("UI_ROLE")),
            Map.of("permissionCode", "shared", "permissionKind", "API", "roleCode", "API_ROLE",
                "inherited", false, "path", List.of("API_ROLE")));
    }

    @Test
    void apiRetriesMissingOperationWhenVersionChanges() {
        when(store.get(User.class, 42L)).thenReturn(user("ACTIVE"));
        ApiOperation operation = new ApiOperation();
        operation.enabled = true;
        operation.accessPolicy = "PUBLIC";
        when(store.one(ApiOperation.class, "e.systemId=?1 and e.operationId=?2", 2L, "operation"))
            .thenReturn(null, operation);

        assertThat(authorization.api(42L, "course", "operation")).isTrue();
        assertThatExceptionOfType(BizException.class)
            .isThrownBy(() -> authorization.api(42L, "course", "missing"))
            .satisfies(error -> {
                assertThat(error.status).isEqualTo(404);
                assertThat(error.code).isEqualTo("API_OPERATION_NOT_FOUND");
            });
    }

    @Test
    void disabledSystemRetainsConfiguredRolePermissionsButRejectsRuntimeAuthorization() {
        SoftwareSystem system = new SoftwareSystem();
        system.id = 2L;
        system.code = "course";
        system.status = "DISABLED";
        Role senior = role(10L, system.id, "SENIOR");
        Role junior = role(20L, system.id, "JUNIOR");
        RoleHierarchy edge = new RoleHierarchy();
        edge.seniorRoleId = senior.id;
        edge.juniorRoleId = junior.id;
        Permission direct = permission(100L, "UI");
        direct.code = "direct";
        Permission inherited = permission(200L, "API");
        inherited.code = "inherited";
        when(store.get(SoftwareSystem.class, system.id)).thenReturn(system);
        when(store.one(SoftwareSystem.class, "e.code=?1", system.code)).thenReturn(system);
        when(store.get(Role.class, senior.id)).thenReturn(senior);
        when(store.get(Role.class, junior.id)).thenReturn(junior);
        when(store.where(Role.class, "e.systemId=?1", system.id)).thenReturn(List.of(senior, junior));
        when(store.all(RoleHierarchy.class)).thenReturn(List.of(edge));
        when(store.where(Permission.class, "e.systemId=?1 and e.status=?2", system.id, "ENABLED"))
            .thenReturn(List.of(direct, inherited));
        when(store.get(Permission.class, direct.id)).thenReturn(direct);
        when(store.get(Permission.class, inherited.id)).thenReturn(inherited);
        when(store.all(RolePermission.class))
            .thenReturn(List.of(grant(senior.id, direct.id), grant(junior.id, inherited.id)));

        Actor actor = new Actor(42L, "admin", "rbac-center", "jti", null);
        var authorized = spy(authorization);
        doNothing().when(authorized).center(actor, "rbac:role:view");
        var management = new ManagementService(store, authorized, null, null);

        JsonNode result = json.valueToTree(management.effectiveRole(actor, senior.id));

        verify(authorized).center(actor, "rbac:role:view");
        assertThat(result.path("directRoles").findValuesAsText("code")).containsExactly("SENIOR");
        assertThat(result.path("effectiveRoles").findValuesAsText("code"))
            .containsExactlyInAnyOrder("SENIOR", "JUNIOR");
        assertThat(result.path("permissions").findValuesAsText("code"))
            .containsExactlyInAnyOrder("direct", "inherited");
        assertThat(authorization.calculate(List.of(senior.id), system.id).permissions()).isEmpty();
        when(store.get(User.class, 42L)).thenReturn(user("ACTIVE"));
        assertThatExceptionOfType(BizException.class)
            .isThrownBy(() -> authorization.snapshot(42L, system.code))
            .satisfies(error -> assertThat(error.status).isEqualTo(401));
    }

    @Test
    void unknownSystemIsReportedBeforeTargetUserLookup() {
        Actor actor = new Actor(42L, "admin", "rbac-center", "jti", null);
        var authorized = spy(authorization);
        doNothing().when(authorized).center(actor, "rbac:user:view");
        var management = new ManagementService(store, authorized, null, null);
        when(store.get(User.class, 17L)).thenReturn(user("DISABLED"));

        for (long target : List.of(17L, 18L)) {
            assertThatExceptionOfType(BizException.class)
                .isThrownBy(() -> management.effectiveUser(actor, target, "missing-system"))
                .satisfies(error -> {
                    assertThat(error.status).isEqualTo(404);
                    assertThat(error.code).isEqualTo("SYSTEM_NOT_FOUND");
                });
            verify(store, never()).get(User.class, target);
        }
    }

    @Test
    void ordinaryAdministratorCannotChangeRoleGrantsInDisabledSystem() {
        var system = store.get(SoftwareSystem.class, 2L);
        system.status = "DISABLED";
        Role target = role(10L, system.id, "VIEWER");
        when(store.get(Role.class, target.id)).thenReturn(target);
        Actor actor = new Actor(42L, "admin", "rbac-center", "jti", null);
        GrantVersion grantVersion = version(7);
        var management = management(actor, "rbac:role:edit", false, grantVersion);

        withWriteSynchronization(() -> {
            assertThatExceptionOfType(BizException.class)
                .isThrownBy(() -> management.replacePermissions(actor, target.id, List.of()))
                .satisfies(error -> {
                    assertThat(error.status).isEqualTo(403);
                    assertThat(error.code).isEqualTo("PRIVILEGE_ESCALATION");
                });
            assertThat(grantVersion.version).isEqualTo(7);
            verify(store, never()).flush();
        });
    }

    @ParameterizedTest
    @CsvSource({"false,empty,true", "false,permission,false", "false,identity,false", "true,permission,true"})
    void systemActivationChecksTheGrantsThatWouldBecomeEffective(
        boolean superAdmin, String configuredGrant, boolean allowed) {
        var system = store.get(SoftwareSystem.class, 2L);
        system.status = "DISABLED";
        Role target = role(10L, system.id, "VIEWER");
        when(store.get(Role.class, target.id)).thenReturn(target);
        when(store.where(Role.class, "e.systemId=?1", system.id)).thenReturn(List.of(target));
        if ("identity".equals(configuredGrant))
            target.code = "teacher";
        if ("permission".equals(configuredGrant)) {
            Permission permission = permission(100L, "API");
            when(store.where(Permission.class, "e.systemId=?1 and e.status=?2", system.id, "ENABLED"))
                .thenReturn(List.of(permission));
            when(store.all(RolePermission.class)).thenReturn(List.of(grant(target.id, permission.id)));
        }
        Actor actor = new Actor(42L, "admin", "rbac-center", "jti", null);
        GrantVersion grantVersion = version(7);
        var management = management(actor, "rbac:system:edit", superAdmin, grantVersion);

        withWriteSynchronization(() -> {
            if (allowed) {
                management.systemStatus(actor, system.id, "ENABLED");
                assertThat(system.status).isEqualTo("ENABLED");
                assertThat(grantVersion.version).isEqualTo(8);
            } else {
                assertThatExceptionOfType(BizException.class)
                    .isThrownBy(() -> management.systemStatus(actor, system.id, "ENABLED"))
                    .satisfies(error -> {
                        assertThat(error.status).isEqualTo(403);
                        assertThat(error.code).isEqualTo("PRIVILEGE_ESCALATION");
                    });
                assertThat(system.status).isEqualTo("DISABLED");
                assertThat(grantVersion.version).isEqualTo(7);
            }
        });
    }

    private ManagementService management(
        Actor actor, String permission, boolean superAdmin, GrantVersion grantVersion) {
        var authorizer = spy(authorization);
        when(store.lock(GrantVersion.class, 1L)).thenReturn(grantVersion);
        doReturn(superAdmin).when(authorizer).superAdmin(actor);
        doNothing().when(authorizer).center(actor, permission);
        doNothing().when(authorizer).root(actor);
        doNothing().when(authorizer).preserveAdministrator();
        return new ManagementService(store, authorizer, mock(AuditService.class), null);
    }

    private void withWriteSynchronization(Runnable action) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            action.run();
        } finally {
            for (var synchronization : TransactionSynchronizationManager.getSynchronizations())
                synchronization.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static GrantVersion version(long number) {
        GrantVersion version = new GrantVersion();
        version.version = number;
        return version;
    }

    private static User user(String status) {
        User user = new User();
        user.status = status;
        return user;
    }

    private static Role role(long id, long systemId, String code) {
        Role role = new Role();
        role.id = id;
        role.systemId = systemId;
        role.status = "ENABLED";
        role.code = code;
        return role;
    }

    private static UserRole assignment(long roleId) {
        UserRole assignment = new UserRole();
        assignment.roleId = roleId;
        return assignment;
    }

    private static Permission permission(long id, String kind) {
        Permission permission = new Permission();
        permission.id = id;
        permission.code = "shared";
        permission.kind = kind;
        return permission;
    }

    private static RolePermission grant(long roleId, long permissionId) {
        RolePermission grant = new RolePermission();
        grant.roleId = roleId;
        grant.permissionId = permissionId;
        return grant;
    }
}
