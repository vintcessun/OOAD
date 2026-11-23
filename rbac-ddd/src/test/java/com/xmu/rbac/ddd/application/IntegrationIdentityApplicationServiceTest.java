package com.xmu.rbac.ddd.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;

/** 私有协议应用边界单测；不替代 MySQL/HTTP 集成测试。 */
class IntegrationIdentityApplicationServiceTest {
    private final AuthorizationApplicationService authorization =
            mock(AuthorizationApplicationService.class);
    private final DirectoryRepository directory = mock(DirectoryRepository.class);
    private final AuthorizationRepository repository = mock(AuthorizationRepository.class);
    private final TokenProvider.Principal principal =
            new TokenProvider.Principal(
                    "subject",
                    "course-project-management",
                    "jti",
                    0,
                    Instant.now().plusSeconds(300));
    private final AuthorizationRepository.Identity actor =
            new AuthorizationRepository.Identity(
                    7, "subject", "user", "教师", "7", "unused", 2, User.Status.ACTIVE, 0);
    private final IntegrationIdentityApplicationService service =
            new IntegrationIdentityApplicationService(
                    authorization, repository, directory, "trusted-test-key");

    private AuthorizationPolicy.Effective effective(String identity, boolean tec) {
        Role role =
                new Role(
                        1,
                        "course-project-management",
                        identity == null ? "INTERNAL_NOT_AN_AUTHORITY" : identity,
                        "角色",
                        null,
                        true,
                        false,
                        0,
                        Set.of());
        List<Permission> permissions = new ArrayList<>();
        permissions.add(
                new Permission(
                        1,
                        "course-project-management",
                        "UI_BUTTON",
                        "按钮",
                        Permission.Kind.UI,
                        true));
        permissions.add(
                new Permission(
                        2,
                        "course-project-management",
                        "course:access",
                        "访问",
                        Permission.Kind.API,
                        true));
        if (tec)
            permissions.add(
                    new Permission(
                            3,
                            "course-project-management",
                            "TEC",
                            "查询",
                            Permission.Kind.API,
                            true));
        return new AuthorizationPolicy.Effective(
                List.of(role), List.of(role), permissions, List.of());
    }

    @BeforeEach
    void actor() {
        when(authorization.validate(principal)).thenReturn(actor);
    }

    @Test
    void projectionContainsOnlyApiAndControlledIdentity() {
        when(authorization.effective(7, "course-project-management"))
                .thenReturn(effective("teacher", false));
        assertThat(service.authorities(principal, "trusted-test-key").authorities())
                .containsExactly("ROLE_teacher", "course:access")
                .doesNotContain("UI_BUTTON", "INTERNAL_NOT_AN_AUTHORITY");
    }

    @Test
    void noServiceKeyOrWrongLengthKeyCannotUseBearerAsProof() {
        assertThatThrownBy(() -> service.authorities(principal, null))
                .hasMessageContaining("AUDIT_SERVICE_REQUIRED");
        assertThatThrownBy(() -> service.authorities(principal, "x"))
                .hasMessageContaining("AUDIT_SERVICE_REQUIRED");
        assertThatThrownBy(() -> service.authorities(principal, "trusted-test-key-wrong"))
                .hasMessageContaining("AUDIT_SERVICE_REQUIRED");
        verify(authorization, never()).validate(any());
    }

    @Test
    void emptyConfiguredServiceKeyIsNeverAccepted() {
        IntegrationIdentityApplicationService disabled =
                new IntegrationIdentityApplicationService(authorization, repository, directory, "");
        assertThatThrownBy(() -> disabled.authorities(principal, ""))
                .hasMessageContaining("AUDIT_SERVICE_REQUIRED");
    }

    @Test
    void identityLookupRequiresBothAdminIdentityAndTecApiPermission() {
        when(authorization.effective(7, "course-project-management"))
                .thenReturn(effective("admin", false));
        assertThatThrownBy(() -> service.lookup(principal, "trusted-test-key", "target"))
                .hasMessageContaining("FORBIDDEN");
        when(authorization.effective(7, "course-project-management"))
                .thenReturn(effective("teacher", true));
        assertThatThrownBy(() -> service.lookup(principal, "trusted-test-key", "target"))
                .hasMessageContaining("FORBIDDEN");
    }

    @Test
    void lookupOnlyReturnsMinimalStableIdentity() {
        when(authorization.effective(7, "course-project-management"))
                .thenReturn(effective("admin", true));
        when(repository.identity("target"))
                .thenReturn(
                        new AuthorizationRepository.Identity(
                                8,
                                "target",
                                "never-exposed",
                                "姓名",
                                null,
                                "never-exposed",
                                3,
                                User.Status.ACTIVE,
                                5));
        assertThat(service.lookup(principal, "trusted-test-key", "target"))
                .isEqualTo(
                        new IntegrationIdentityApplicationService.IdentityResult(
                                "target", "姓名", ""));
    }

    @Test
    void missingOrDeletedIdentityIs404() {
        when(authorization.effective(7, "course-project-management"))
                .thenReturn(effective("admin", true));
        assertThatThrownBy(() -> service.lookup(principal, "trusted-test-key", "missing"))
                .hasMessageContaining("IDENTITY_USER_NOT_FOUND");
        when(repository.identity("deleted"))
                .thenReturn(
                        new AuthorizationRepository.Identity(
                                8, "deleted", "user", "姓名", "8", "unused", 3, User.Status.DELETED, 5));
        assertThatThrownBy(() -> service.lookup(principal, "trusted-test-key", "deleted"))
                .hasMessageContaining("IDENTITY_USER_NOT_FOUND");
    }
}
