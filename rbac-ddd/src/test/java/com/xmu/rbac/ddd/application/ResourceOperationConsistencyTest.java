package com.xmu.rbac.ddd.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;
import com.xmu.rbac.ddd.domain.port.ResourceRepository.OperationSystem;
import com.xmu.rbac.ddd.infrastructure.config.RouteContractVerifier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;

class ResourceOperationConsistencyTest {
    private static final String CENTER = "rbac-center";
    private static final String COURSE = "course-project-management";
    private static final ApiOperation DIRECTORY = new ApiOperation(
            9, CENTER, "listIdentityDepartments", "GET", "/auth/departments", null, "admin",
            ApiOperation.Policy.AUTHENTICATED, true);
    private static final ApiOperation COPY_COURSE = new ApiOperation(
            10, COURSE, "copyCourse", "POST", "/courses/copy", "course:access", "teacher",
            ApiOperation.Policy.PROTECTED, true);
    private static final ApiOperation DISABLED_COPY_COURSE = new ApiOperation(
            10, COURSE, "copyCourse", "POST", "/courses/copy", "course:access", "teacher",
            ApiOperation.Policy.PROTECTED, false);
    private static final AuthorizationPolicy.Effective EMPTY =
            new AuthorizationPolicy.Effective(List.of(), List.of(), List.of(), List.of());
    private final AuthorizationRepository grants = mock(AuthorizationRepository.class);
    private final ResourceRepository resources = mock(ResourceRepository.class);
    private final AuthorizationApplicationService authorization = mock(AuthorizationApplicationService.class);
    private final TokenProvider.Principal principal =
            new TokenProvider.Principal("fixture-user", CENTER, "jti", 0, null);
    private final ResourceApplicationService service = new ResourceApplicationService(
            resources, grants, authorization, mock(AuditApplicationService.class),
            () -> List.of(DIRECTORY), mock(GrantIntegrityService.class));

    @BeforeEach
    void administrator() {
        when(authorization.require(principal, "rbac:resource:edit", true))
                .thenReturn(new AuthorizationRepository.Identity(
                        7, "fixture-user", "fixture", "用户", "FIX-7", "unused", 1, User.Status.ACTIVE, 0));
        when(authorization.effective(7L, CENTER)).thenReturn(EMPTY);
    }

    @Test
    void rejectsUnlistedCenterRegistrationAfterResolvingTheSystemCode() {
        when(resources.operationSystem("RBAC-CENTER")).thenReturn(new OperationSystem(1, CENTER, true));
        ApiOperation additional = new ApiOperation(
                0, "RBAC-CENTER", "unlisted", "GET", "/api/v1/unlisted", null, null,
                ApiOperation.Policy.AUTHENTICATED, true);

        rejects(400, "CENTER_OPERATION_IMMUTABLE", () -> service.operation(principal, additional));
        verify(resources, never()).saveOperation(any(), anyLong(), nullable(Long.class));
    }

    @Test
    void protectedCenterRegistrationCannotBeDisabledOrHaveItsIdentityRoleCleared() {
        when(resources.operationSystem(CENTER)).thenReturn(new OperationSystem(1, CENTER, true));
        when(resources.operation(9L)).thenReturn(DIRECTORY);

        rejects(400, "CENTER_OPERATION_IMMUTABLE", () -> service.patchOperation(
                principal, 9L, null, null, null, null, null, null, false, Set.of("enabled")));
        rejects(400, "CENTER_OPERATION_IMMUTABLE", () -> service.patchOperation(
                principal, 9L, null, null, null, null, null, null, null, Set.of("requiredRole")));
        verify(resources, never()).saveOperation(any(), anyLong(), nullable(Long.class));
    }

    @Test
    void savesAnUnchangedCenterRegistrationWithItsRequiredIdentityRole() {
        when(resources.operationSystem(CENTER)).thenReturn(new OperationSystem(1, CENTER, true));
        when(resources.operation(9L)).thenReturn(DIRECTORY);
        Role root = new Role(1, CENTER, "SUPER_ADMIN", "超管", null, true, true, 0, Set.of());
        when(authorization.effective(7L, CENTER)).thenReturn(
                new AuthorizationPolicy.Effective(List.of(root), List.of(root), List.of(), List.of()));
        when(resources.saveOperation(DIRECTORY, 1, null)).thenReturn(DIRECTORY);

        assertThat(service.operation(principal, DIRECTORY)).isEqualTo(DIRECTORY);
        verify(resources).saveOperation(DIRECTORY, 1, null);
    }

    @Test
    void rejectsAProtectedCourseOperationWithoutAnyConditionBeforeSaving() {
        when(resources.operationSystem(COURSE)).thenReturn(new OperationSystem(2, COURSE, true));
        ApiOperation incomplete = new ApiOperation(
                0, COURSE, "fixture", "GET", "/fixture", null, null, ApiOperation.Policy.PROTECTED, true);

        rejects(400, "API_REQUIREMENT_MISSING", () -> service.operation(principal, incomplete));
        verify(resources, never()).saveOperation(any(), anyLong(), nullable(Long.class));
    }

    @Test
    void rejectsBlankIdentityRequirementsInNewAndExistingCourseOperations() {
        ApiOperation proposed = new ApiOperation(
                0, COURSE, "fixture", "GET", "/fixture", null, "  ", ApiOperation.Policy.PROTECTED, true);
        ApiOperation existing = new ApiOperation(
                9, COURSE, "fixture", "GET", "/fixture", null, "  ", ApiOperation.Policy.PROTECTED, true);
        when(resources.operation(9L)).thenReturn(existing);

        rejects(400, "VALIDATION_ERROR", () -> service.operation(principal, proposed));
        rejects(400, "VALIDATION_ERROR", () -> service.patchOperation(
                principal, 9L, null, null, null, null, "", null, null, Set.of("requiredRole")));
        rejects(400, "VALIDATION_ERROR", () -> service.patchOperation(
                principal, 9L, null, null, null, null, null, null, false, Set.of("enabled")));
        verify(resources, never()).saveOperation(any(), anyLong(), nullable(Long.class));
    }

    @Test
    void validatesTheCoursePermissionAndIdentityAgainstTheActorsOwnGrants() {
        when(resources.operationSystem(COURSE)).thenReturn(new OperationSystem(2, COURSE, true));
        when(resources.apiPermissionId(2, "course:view")).thenReturn(80L);
        when(resources.apiPermissionId(2, "course:edit")).thenReturn(81L);
        Role teacher = new Role(4, COURSE, "teacher", "教师", null, true, false, 0, Set.of(80L));
        Permission view = new Permission(80, COURSE, "course:view", "查看", Permission.Kind.API, true);
        when(authorization.effective(7L, COURSE)).thenReturn(
                new AuthorizationPolicy.Effective(List.of(teacher), List.of(teacher), List.of(view), List.of()));
        ApiOperation allowed = new ApiOperation(
                0, COURSE, "view", "GET", "/fixture", "course:view", "teacher",
                ApiOperation.Policy.PROTECTED, true);
        ApiOperation strongerPermission = new ApiOperation(
                0, COURSE, "edit", "POST", "/fixture", "course:edit", "teacher",
                ApiOperation.Policy.PROTECTED, true);
        ApiOperation strongerRole = new ApiOperation(
                0, COURSE, "admin", "DELETE", "/fixture", "course:view", "admin",
                ApiOperation.Policy.PROTECTED, true);
        when(resources.saveOperation(allowed, 2, 80L)).thenReturn(allowed);

        assertThat(service.operation(principal, allowed)).isEqualTo(allowed);
        rejects(403, "GRANT_EXCEEDS_ACTOR", () -> service.operation(principal, strongerPermission));
        rejects(403, "GRANT_EXCEEDS_ACTOR", () -> service.operation(principal, strongerRole));
        verify(resources).saveOperation(allowed, 2, 80L);
        verify(resources, never()).saveOperation(eq(strongerPermission), anyLong(), nullable(Long.class));
        verify(resources, never()).saveOperation(eq(strongerRole), anyLong(), nullable(Long.class));
    }

    @Test
    void canDisableAndRetryAnExistingCourseOperationWithoutItsOriginalIdentityRole() {
        when(resources.operationSystem(COURSE)).thenReturn(new OperationSystem(2, COURSE, true));
        when(resources.operation(10L)).thenReturn(COPY_COURSE);
        when(resources.apiPermissionId(2, "course:access")).thenReturn(80L);
        when(authorization.effective(7L, COURSE)).thenReturn(EMPTY);
        when(resources.saveOperation(any(ApiOperation.class), eq(2L), eq(80L)))
                .thenAnswer(call -> call.getArgument(0));

        ApiOperation saved = service.patchOperation(
                principal, 10L, "copyCourse", "POST", "/courses/copy", "course:access", "teacher",
                ApiOperation.Policy.PROTECTED, false,
                Set.of("operationId", "method", "pathTemplate", "permissionCode", "requiredRole",
                        "accessPolicy", "enabled"));

        assertThat(saved).isEqualTo(DISABLED_COPY_COURSE);
        when(resources.operation(10L)).thenReturn(saved);
        assertThat(service.patchOperation(
                        principal, 10L, null, null, null, null, null, null, false, Set.of("enabled")))
                .isEqualTo(saved);
        verify(authorization, never()).effective(7L, COURSE);
    }

    @Test
    void stillChecksTheUpperBoundWhenDisablingWithOtherChangesOrReenabling() {
        when(resources.operationSystem(COURSE)).thenReturn(new OperationSystem(2, COURSE, true));
        when(resources.operation(10L)).thenReturn(COPY_COURSE);
        when(resources.apiPermissionId(2, "course:access")).thenReturn(80L);
        when(authorization.effective(7L, COURSE)).thenReturn(EMPTY);

        rejects(403, "GRANT_EXCEEDS_ACTOR", () -> service.patchOperation(
                principal, 10L, null, null, "/courses/other", null, null, null, false,
                Set.of("pathTemplate", "enabled")));

        when(resources.operation(10L)).thenReturn(DISABLED_COPY_COURSE);
        rejects(403, "GRANT_EXCEEDS_ACTOR", () -> service.patchOperation(
                principal, 10L, null, null, null, null, null, null, true, Set.of("enabled")));
        verify(resources, never()).saveOperation(any(), anyLong(), nullable(Long.class));
    }

    @Test
    void bindingACourseConditionInADisabledSystemDoesNotInvalidateTheAdministratorSession() {
        when(resources.operationSystem(COURSE)).thenReturn(new OperationSystem(2, COURSE, false));
        ApiOperation conditional = new ApiOperation(
                0, COURSE, "fixture", "GET", "/fixture", null, "teacher", ApiOperation.Policy.PROTECTED, true);
        ApiOperation authenticated = new ApiOperation(
                0, COURSE, "fixture", "GET", "/fixture", null, null, ApiOperation.Policy.AUTHENTICATED, true);
        when(resources.saveOperation(authenticated, 2, null)).thenReturn(authenticated);

        rejects(403, "GRANT_EXCEEDS_ACTOR", () -> service.operation(principal, conditional));
        assertThat(service.operation(principal, authenticated)).isEqualTo(authenticated);
        verify(authorization, never()).effective(7L, COURSE);
    }

    @Test
    void startupRejectsStaleExtraOrDisabledCenterRows() {
        ApiOperation health = new ApiOperation(
                1, CENTER, "health", "GET", "/api/v1/actuator/health", null, null,
                ApiOperation.Policy.PUBLIC, true);
        ApiOperation disabled = new ApiOperation(
                1, CENTER, "health", "GET", "/api/v1/actuator/health", null, null,
                ApiOperation.Policy.PUBLIC, false);
        RouteContractVerifier verifier = new RouteContractVerifier(
                mock(RequestMappingHandlerMapping.class), () -> List.of(health), resources);
        when(resources.operations(CENTER, null, 1, 1))
                .thenReturn(new AuthorizationRepository.Page<>(1, 1, 1, List.of(health)))
                .thenReturn(new AuthorizationRepository.Page<>(1, 1, 2, List.of(health)))
                .thenReturn(new AuthorizationRepository.Page<>(1, 1, 1, List.of(disabled)))
                .thenReturn(new AuthorizationRepository.Page<>(1, 1, 0, List.of()));

        verifier.run(null);
        for (int i = 0; i < 3; i++)
            rejects(503, "API_REGISTRY_CONTRACT_MISMATCH", () -> verifier.run(null));
    }

    private void rejects(int status, String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class, error -> {
            assertThat(error.status()).isEqualTo(status);
            assertThat(error.code()).isEqualTo(code);
        });
    }
}
