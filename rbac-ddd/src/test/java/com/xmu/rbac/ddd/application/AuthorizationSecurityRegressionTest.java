package com.xmu.rbac.ddd.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;
import com.xmu.rbac.ddd.infrastructure.logging.LayerLoggingAspect;
import com.xmu.rbac.ddd.interfaces.rest.AuthorizationController;
import com.xmu.rbac.ddd.interfaces.rest.AuthorizationController.Check;
import com.xmu.rbac.ddd.interfaces.rest.RestSupport;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 授权回归；latch 确定性安排撤权和缓存命中时序。 */
class AuthorizationSecurityRegressionTest {
    private AuthorizationRepository.Identity user(User.Status status) {
        return new AuthorizationRepository.Identity(
                7, "u7", "u7", "用户", "7", "unused", 1, status, 0);
    }

    private Role role() {
        return new Role(2, "s", "viewer", "查看者", null, true, false, null, 0, Set.of(1L));
    }

    private Permission permission() {
        return new Permission(1, "s", "view", "查看", Permission.Kind.API, true);
    }

    private AuthorizationRepository repository() {
        AuthorizationRepository r = mock(AuthorizationRepository.class);
        when(r.systemEnabled("s")).thenReturn(true);
        when(r.identity(7L)).thenReturn(user(User.Status.ACTIVE));
        when(r.assignments(7, "s")).thenReturn(Set.of(2L));
        when(r.hierarchy("s")).thenReturn(new RoleHierarchy(List.of(role()), Set.of()));
        when(r.permissions("s")).thenReturn(List.of(permission()));
        return r;
    }

    private AuthorizationPolicy.Effective allowed() {
        return new AuthorizationPolicy()
                .calculate(
                        "s",
                        Set.of(2L),
                        new RoleHierarchy(List.of(role()), Set.of()),
                        List.of(permission()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"permission", "operationId"})
    void suppliedAuthorizationSelectorHasLengthBetweenOneAnd128(String field) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validateValue(Check.class, field, "")).hasSize(1);
            assertThat(validator.validateValue(Check.class, field, "x".repeat(129))).hasSize(1);
            assertThat(validator.validateValue(Check.class, field, "x")).isEmpty();
            assertThat(validator.validateValue(Check.class, field, "x".repeat(128))).isEmpty();
            assertThat(validator.validateValue(Check.class, field, null)).isEmpty();
        }
    }

    @Test
    void emptyPermissionSuperAdminStillCannotBeGranted() {
        Role special =
                new Role(
                        1,
                        "rbac-center",
                        "SUPER_ADMIN",
                        "超级管理员",
                        null,
                        true,
                        true,
                        null,
                        0,
                        Set.of());
        AuthorizationPolicy.Effective proposed =
                new AuthorizationPolicy()
                        .calculate(
                                "rbac-center",
                                Set.of(1L),
                                new RoleHierarchy(List.of(special), Set.of()),
                                List.of());
        AuthorizationPolicy.Effective actor =
                new AuthorizationPolicy.Effective(List.of(), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> new AuthorizationPolicy().requireWithin(actor, proposed, false))
                .hasMessageContaining("GRANT_EXCEEDS_ACTOR");
    }

    @Test
    void inactiveAndDeletedTargetsNeverKeepEffectivePermissions() {
        AuthorizationRepository r = repository();
        AuthorizationApplicationService service =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));
        for (User.Status status : List.of(User.Status.DISABLED, User.Status.DELETED)) {
            when(r.identity(7L)).thenReturn(user(status));
            assertThat(service.effective(7, "s").permissions()).isEmpty();
            when(r.operation("s", "publicOp"))
                    .thenReturn(
                            new ApiOperation(
                                    1,
                                    "s",
                                    "publicOp",
                                    "GET",
                                    "/public",
                                    null,
                                    null,
                                    ApiOperation.Policy.PUBLIC,
                                    true));
            assertThat(service.api(7, "s", "publicOp")).isFalse();
        }
    }

    @Test
    void administratorCanViewEmptyPermissionsForDisabledTargetSystem() {
        var principal = new TokenProvider.Principal("u7", "rbac-center", "jti", 0, null);
        DirectoryRepository directory = mock(DirectoryRepository.class);
        AuthorizationRepository grants = mock(AuthorizationRepository.class);
        AuthorizationApplicationService authorization = mock(AuthorizationApplicationService.class);
        var service = new DirectoryApplicationService(directory, grants, authorization, null, null, null);
        when(authorization.require(principal, "rbac:user:view", false))
                .thenReturn(user(User.Status.ACTIVE));
        when(directory.user(8, false))
                .thenReturn(new DirectoryRepository.Profile(
                        8L, "u8", "u8", "用户", "8", null, 1L, "总部", "ACTIVE", 0L, null, 0L));
        when(grants.departmentPath(1)).thenReturn("/1/");
        when(directory.department(1))
                .thenReturn(new DirectoryRepository.Department(
                        1L, null, "root", "总部", "/1/", "ENABLED", 0L));
        when(authorization.effective(8, "disabled")).thenThrow(new DomainException(401, "SYSTEM_DISABLED"));
        when(grants.systemEnabled("unknown")).thenThrow(new DomainException(404, "SYSTEM_NOT_FOUND"));

        assertThat(service.effective(principal, 8, "disabled"))
                .isEqualTo(new AuthorizationPolicy.Effective(List.of(), List.of(), List.of(), List.of()));
        assertThatThrownBy(() -> service.effective(principal, 8, "unknown"))
                .isInstanceOf(DomainException.class).hasMessage("SYSTEM_NOT_FOUND");
        verify(directory).user(8, false);

        when(authorization.require(principal, "rbac:user:view", false))
                .thenThrow(new DomainException(401, "SYSTEM_DISABLED"));
        assertThatThrownBy(() -> service.effective(principal, 8, "disabled"))
                .isInstanceOf(DomainException.class).hasMessage("SYSTEM_DISABLED");
    }

    @Test
    void revocationWhileBuildingSnapshotMustRetryInsteadOfReturningOldGrant() throws Exception {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong(1);
        when(r.version()).thenAnswer(invocation -> version.get());
        CountDownLatch loaded = new CountDownLatch(1), revoked = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        when(r.permissions("s"))
                .thenAnswer(
                        invocation -> {
                            if (loads.getAndIncrement() == 0) {
                                loaded.countDown();
                                assertThat(revoked.await(5, TimeUnit.SECONDS)).isTrue();
                                return List.of(permission());
                            }
                            return List.of();
                        });
        AuthorizationApplicationService service =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<AuthorizationPolicy.Effective> result =
                    worker.submit(() -> service.effective(7, "s"));
            assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue();
            version.incrementAndGet();
            revoked.countDown();
            assertThat(result.get(5, TimeUnit.SECONDS).permissions()).isEmpty();
        } finally {
            revoked.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void revocationDuringCacheHitMustNotReturnHit() throws Exception {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong(1);
        when(r.version()).thenAnswer(invocation -> version.get());
        when(r.permissions("s")).thenReturn(List.of());
        EffectiveCache cache = mock(EffectiveCache.class);
        CountDownLatch hit = new CountDownLatch(1), revoked = new CountDownLatch(1);
        when(cache.get("s", 7, 1))
                .thenAnswer(
                        invocation -> {
                            hit.countDown();
                            assertThat(revoked.await(5, TimeUnit.SECONDS)).isTrue();
                            return allowed();
                        });
        AuthorizationApplicationService service = new AuthorizationApplicationService(r, cache);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<AuthorizationPolicy.Effective> result =
                    worker.submit(() -> service.effective(7, "s"));
            assertThat(hit.await(5, TimeUnit.SECONDS)).isTrue();
            version.incrementAndGet();
            revoked.countDown();
            assertThat(result.get(5, TimeUnit.SECONDS).permissions()).isEmpty();
        } finally {
            revoked.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void continuouslyChangingAuthorityVersionFailsClosed() {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong();
        when(r.version()).thenAnswer(invocation -> version.incrementAndGet());
        assertThatThrownBy(
                        () ->
                                new AuthorizationApplicationService(r, mock(EffectiveCache.class))
                                        .effective(7, "s"))
                .hasMessageContaining("AUTHORIZATION_UNSTABLE");
    }

    @Test
    void operationPolicyChangeDuringResolutionCannotReuseEarlierPublicPolicy() {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong(1);
        when(r.version()).thenAnswer(i -> version.get());
        when(r.permissions("s")).thenReturn(List.of());
        AtomicInteger calls = new AtomicInteger();
        when(r.operation("s", "op"))
                .thenAnswer(
                        i -> {
                            if (calls.getAndIncrement() == 0) {
                                version.set(2);
                                return new ApiOperation(
                                        1,
                                        "s",
                                        "op",
                                        "GET",
                                        "/op",
                                        null,
                                        null,
                                        ApiOperation.Policy.PUBLIC,
                                        true);
                            }
                            return new ApiOperation(
                                    1,
                                    "s",
                                    "op",
                                    "GET",
                                    "/op",
                                    "view",
                                    null,
                                    ApiOperation.Policy.PROTECTED,
                                    true);
                        });
        assertThat(
                        new AuthorizationApplicationService(r, mock(EffectiveCache.class))
                                .api(7, "s", "op"))
                .isFalse();
    }

    @Test
    void apiExplanationUsesTheSameVersionForDecisionAndPermissions() {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong(1);
        when(r.version()).thenAnswer(i -> version.get());
        when(r.permissions("s"))
                .thenAnswer(i -> version.get() == 1 ? List.of(permission()) : List.of());
        ApiOperation operation =
                new ApiOperation(
                        1, "s", "op", "GET", "/op", "view", null,
                        ApiOperation.Policy.PROTECTED, true);
        when(r.operation("s", "op"))
                .thenAnswer(
                        i -> {
                            version.set(2);
                            return operation;
                        });
        AuthorizationApplicationService service =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));

        var result = service.simulate(7, "s", Permission.Kind.API, "op");

        assertThat(result.allowed()).isFalse();
        assertThat(result.unavailableReason()).isNull();
        assertThat(result.effective().permissions()).isEmpty();
    }

    @Test
    void apiExplanationRetriesWhenSystemIsDisabledAfterTheStatusRead() {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong(1);
        when(r.version()).thenAnswer(i -> version.get());
        when(r.systemEnabled("s"))
                .thenReturn(true)
                .thenAnswer(
                        i -> {
                            version.set(2);
                            return false;
                        });
        AuthorizationApplicationService service =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));

        var result = service.simulate(7, "s", Permission.Kind.API, "op");

        assertThat(result.allowed()).isFalse();
        assertThat(result.unavailableReason()).isEqualTo("SYSTEM_DISABLED");
        assertThat(result.effective().permissions()).isEmpty();
        verify(r, never()).operation("s", "op");
    }

    @Test
    void uiExplanationReportsTargetSystemDisabledWithoutInvalidatingTheActor() {
        String bearer = "Bearer fixture";
        var principal = new TokenProvider.Principal("u7", "rbac-center", "jti", 0, null);
        RestSupport rest = mock(RestSupport.class);
        DirectoryApplicationService directory = mock(DirectoryApplicationService.class);
        AuthorizationApplicationService authorization = mock(AuthorizationApplicationService.class);
        var controller = new AuthorizationController(rest, authorization, directory, null);
        var request = new Check("s", 8L, Permission.Kind.UI, "view", null);
        when(rest.principal(bearer)).thenReturn(principal);
        when(authorization.require(principal, "rbac:authz:invoke", false))
                .thenReturn(user(User.Status.ACTIVE));
        var empty = new AuthorizationPolicy.Effective(List.of(), List.of(), List.of(), List.of());
        when(authorization.simulate(8L, "s", Permission.Kind.UI, "view"))
                .thenReturn(new AuthorizationApplicationService.SimulationResult(false, "SYSTEM_DISABLED", empty));

        var response = (Map<?, ?>) controller.explain(bearer, request);
        var result = (Map<?, ?>) response.get("data");

        assertThat(response.get("code")).isEqualTo("OK");
        assertThat(result.get("allowed")).isEqualTo(false);
        assertThat(result.get("reason")).isEqualTo("SYSTEM_DISABLED");
        assertThat(result.get("effective")).isEqualTo(Map.of(
                "directRoles", List.of(), "effectiveRoles", List.of(),
                "permissions", List.of(), "sources", List.of()));

        when(authorization.require(principal, "rbac:authz:invoke", false))
                .thenThrow(new DomainException(401, "SYSTEM_DISABLED"));
        assertThatThrownBy(() -> controller.explain(bearer, request))
                .isInstanceOf(DomainException.class).hasMessage("SYSTEM_DISABLED");
        verify(authorization).simulate(8L, "s", Permission.Kind.UI, "view");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void simulationResponseUsesItsLogTraceId(boolean explain) {
        String bearer = "Bearer fixture";
        var principal = new TokenProvider.Principal("u7", "rbac-center", "jti", 0, null);
        RestSupport rest = mock(RestSupport.class);
        AuthorizationApplicationService authorization = mock(AuthorizationApplicationService.class);
        when(rest.principal(bearer)).thenReturn(principal);
        when(authorization.require(principal, "rbac:authz:invoke", false))
                .thenReturn(user(User.Status.ACTIVE));
        when(authorization.simulate(7L, "s", Permission.Kind.UI, "view"))
                .thenReturn(new AuthorizationApplicationService.SimulationResult(false, null, allowed()));
        var factory = new AspectJProxyFactory(new AuthorizationController(rest, authorization, null, null));
        factory.addAspect(LayerLoggingAspect.class);
        AuthorizationController controller = factory.getProxy();

        var logger = (Logger) LoggerFactory.getLogger(LayerLoggingAspect.class);
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        String originalTrace = MDC.get("traceId");
        try {
            var request = new Check("s", null, Permission.Kind.UI, "view", null);
            var response = (Map<?, ?>) (explain ? controller.explain(bearer, request) : controller.check(bearer, request));
            var result = (Map<?, ?>) response.get("data");
            String traceId = (String) result.get("traceId");
            assertThat(traceId).isNotBlank();
            assertThat(events.list).extracting(ILoggingEvent::getFormattedMessage).anySatisfy(log -> {
                assertThat(log).contains("traceId=" + traceId + " layer=api");
                assertThat(log).contains("method=" + (explain ? "explain" : "check") + " ");
            });
            assertThat(MDC.get("traceId")).isEqualTo(originalTrace);
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    @ParameterizedTest
    @CsvSource({"UI,true", "UI,false", "API,false"})
    void simulationReportsTargetUserDisabledAfterTheStatusRead(Permission.Kind kind, boolean explain) {
        AuthorizationRepository r = repository();
        AtomicLong version = new AtomicLong(1);
        when(r.version()).thenAnswer(i -> version.get());
        when(r.identity(7L))
                .thenReturn(user(User.Status.ACTIVE))
                .thenAnswer(i -> {
                    version.set(2);
                    return user(User.Status.DISABLED);
                });
        when(r.operation("s", "op"))
                .thenReturn(new ApiOperation(
                        1, "s", "op", "GET", "/op", "view", null,
                        ApiOperation.Policy.PROTECTED, true));

        String bearer = "Bearer fixture";
        var principal = new TokenProvider.Principal("u8", "rbac-center", "jti", 0, null);
        var actor = new AuthorizationRepository.Identity(
                8, "u8", "u8", "管理员", "8", "unused", 1, User.Status.ACTIVE, 0);
        RestSupport rest = mock(RestSupport.class);
        DirectoryApplicationService directory = mock(DirectoryApplicationService.class);
        var authorization = spy(new AuthorizationApplicationService(r, mock(EffectiveCache.class)));
        when(rest.principal(bearer)).thenReturn(principal);
        doReturn(actor).when(authorization).require(principal, "rbac:authz:invoke", false);
        var controller = new AuthorizationController(rest, authorization, directory, null);
        var request = kind == Permission.Kind.UI
                ? new Check("s", 7L, kind, "view", null)
                : new Check("s", 7L, kind, null, "op");

        var response = (Map<?, ?>) (explain ? controller.explain(bearer, request) : controller.check(bearer, request));
        var result = (Map<?, ?>) response.get("data");

        assertThat(response.get("code")).isEqualTo("OK");
        assertThat(result.get("allowed")).isEqualTo(false);
        assertThat(result.get("reason")).isEqualTo("USER_DISABLED");
        if (explain) {
            assertThat(result.get("trace")).isEqualTo(List.of("ACTOR_AUTHENTICATED", "USER_DISABLED"));
            assertThat(result.get("effective")).isEqualTo(Map.of(
                    "directRoles", List.of(), "effectiveRoles", List.of(),
                    "permissions", List.of(), "sources", List.of()));
        }
        verify(directory).user(principal, 7L);
    }

    @Test
    void inheritedAndDisabledSpecialCapabilityCannotHideBehindEmptyPermissions() {
        Role special =
                new Role(
                        1,
                        "rbac-center",
                        "SUPER_ADMIN",
                        "超级管理员",
                        null,
                        true,
                        true,
                        null,
                        0,
                        Set.of());
        Role ordinary =
                new Role(
                        2, "rbac-center", "ordinary", "普通角色", null, true, false, null, 0, Set.of());
        RoleHierarchy graph =
                new RoleHierarchy(List.of(special, ordinary), Set.of(new RoleHierarchy.Edge(2, 1)));
        AuthorizationPolicy.Effective inherited =
                new AuthorizationPolicy().calculate("rbac-center", Set.of(2L), graph, List.of());
        assertThatThrownBy(
                        () ->
                                new AuthorizationPolicy()
                                        .requireWithin(
                                                new AuthorizationPolicy.Effective(
                                                        List.of(), List.of(), List.of(), List.of()),
                                                inherited,
                                                false))
                .hasMessageContaining("GRANT_EXCEEDS_ACTOR");
        special.changeStatus(false);
        assertThatThrownBy(() -> graph.requireGrantable(Set.of(2L), false))
                .hasMessageContaining("GRANT_EXCEEDS_ACTOR");
        assertThatThrownBy(() -> special.requireManageable(false))
                .hasMessageContaining("SUPER_ADMIN_PROTECTED");
        graph.requireGrantable(Set.of(2L), true);
    }

    @Test
    void centerSuperAdministratorCanEditRolePermissionsInADisabledSystem() {
        AuthorizationRepository r = repository();
        var actor = user(User.Status.ACTIVE);
        var principal = new TokenProvider.Principal("u7", "rbac-center", "jti", 0, null);
        Role administrator =
                new Role(1, "rbac-center", "SUPER_ADMIN", "超级管理员", null,
                        true, true, null, 0, Set.of());
        when(r.identity("u7")).thenReturn(actor);
        when(r.departmentPath(actor.departmentId())).thenReturn("/1/");
        when(r.systemEnabled("rbac-center")).thenReturn(true);
        when(r.hierarchy("rbac-center"))
                .thenReturn(new RoleHierarchy(List.of(administrator), Set.of()));
        when(r.assignments(actor.id(), "rbac-center")).thenReturn(Set.of(administrator.id()));
        when(r.systemEnabled("s")).thenReturn(false);
        Role target = role();
        target.replacePermissions(List.of());
        when(r.role(2L)).thenReturn(target);
        when(r.permissions(Set.of(1L))).thenReturn(List.of(permission()));
        when(r.saveRole(any(Role.class))).thenAnswer(call -> call.getArgument(0));
        AuthorizationApplicationService authorization =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));
        RoleApplicationService service = new RoleApplicationService(
                r, authorization, mock(AuditApplicationService.class), mock(GrantIntegrityService.class));

        assertThat(service.permissions(principal, 2L, Set.of(1L)).permissionIds()).containsExactly(1L);
        verify(r).saveRole(any(Role.class));
        verify(r).advanceVersion();
        verify(r, never()).systemEnabled("s");
        assertThatThrownBy(() -> authorization.effective(actor.id(), "s"))
                .isInstanceOfSatisfying(DomainException.class, error -> {
                    assertThat(error.status()).isEqualTo(401);
                    assertThat(error.code()).isEqualTo("SYSTEM_DISABLED");
                });

        DirectoryRepository directory = mock(DirectoryRepository.class);
        when(directory.user(8, true)).thenReturn(new DirectoryRepository.Profile(
                8L, "u8", "u8", "用户", "8", null, 1L, "总部", "ACTIVE", 0L, null, 0L));
        when(directory.department(1)).thenReturn(new DirectoryRepository.Department(
                1L, null, "root", "总部", "/1/", "ENABLED", 0L));
        var directoryService = new DirectoryApplicationService(
                directory, r, authorization, null, null, null);
        assertThatThrownBy(() -> directoryService.assign(principal, 8L, 2L))
                .isInstanceOfSatisfying(DomainException.class, error -> {
                    assertThat(error.status()).isEqualTo(409);
                    assertThat(error.code()).isEqualTo("ROLE_DISABLED");
                });
        verify(directory, never()).assign(anyLong(), anyLong(), anyLong());
    }

    @Test
    void ordinaryAdministratorStillCannotGrantPermissionsOutsideTheirOwnSnapshot() {
        AuthorizationRepository r = repository();
        when(r.systemEnabled("rbac-center")).thenReturn(true);
        when(r.hierarchy("rbac-center")).thenReturn(new RoleHierarchy(List.of(), Set.of()));
        when(r.assignments(7, "s")).thenReturn(Set.of());
        AuthorizationApplicationService service =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));

        assertThatThrownBy(() -> service.upperBound(user(User.Status.ACTIVE), "s", allowed()))
                .isInstanceOfSatisfying(DomainException.class, error -> {
                    assertThat(error.status()).isEqualTo(403);
                    assertThat(error.code()).isEqualTo("GRANT_EXCEEDS_ACTOR");
                });
    }

    @ParameterizedTest
    @CsvSource({"permission,true,false", "identity,true,false", "identity,true,true", "permission,false,false"})
    void userRecoveryChecksRetainedRolesBeforeSaving(
            String grant, boolean systemEnabled, boolean actorHasRole) {
        var principal = new TokenProvider.Principal("u7", "rbac-center", "jti", 0, null);
        AuthorizationRepository grants = repository();
        var authorization = managementAuthorization(grants, false);
        var role = new Role(2L, "s", "viewer", "查看者", null, true, false,
                "identity".equals(grant) ? "teacher" : null, 0,
                "permission".equals(grant) ? Set.of(1L) : Set.of());
        when(grants.role(2L)).thenReturn(role);
        when(grants.systemEnabled("s")).thenReturn(systemEnabled);
        when(grants.hierarchy("s")).thenReturn(new RoleHierarchy(List.of(role), Set.of()));
        when(grants.assignments(7L, "s")).thenReturn(actorHasRole ? Set.of(2L) : Set.of());

        var directory = mock(DirectoryRepository.class);
        var audits = mock(AuditApplicationService.class);
        when(directory.department(1L)).thenReturn(new DirectoryRepository.Department(
                1L, null, "root", "总部", "/1/", "ENABLED", 0L));
        when(directory.user(8L, true)).thenReturn(new DirectoryRepository.Profile(
                8L, "u8", "u8", "用户", "8", null, 1L, "总部", "DISABLED", 0L, null, 2L));
        when(directory.assignments(8L, null)).thenReturn(List.of(new DirectoryRepository.Assignment(
                9L, 8L, 2L, "s", role.code(), role.name(), null, 1L)));
        when(directory.saveUser(any())).thenAnswer(call -> call.getArgument(0));
        var service = new DirectoryApplicationService(
                directory, grants, authorization, audits, null, mock(GrantIntegrityService.class));

        if (!systemEnabled || actorHasRole) {
            assertThat(service.statusUser(principal, 8L, User.Status.ACTIVE).status()).isEqualTo("ACTIVE");
            verify(grants).advanceVersion();
        } else {
            assertThatThrownBy(() -> service.statusUser(principal, 8L, User.Status.ACTIVE))
                    .isInstanceOfSatisfying(DomainException.class, error -> {
                        assertThat(error.status()).isEqualTo(403);
                        assertThat(error.code()).isEqualTo("GRANT_EXCEEDS_ACTOR");
                    });
            verify(directory, never()).saveUser(any());
            verify(grants, never()).advanceVersion();
            verifyNoInteractions(audits);
        }
        verify(directory, never()).removeAssignment(anyLong());
    }

    @Test
    void ordinaryAdministratorCannotChangeRoleGrantsInDisabledSystem() {
        AuthorizationRepository r = repository();
        var principal = new TokenProvider.Principal("u7", "rbac-center", "jti", 0, null);
        var authorization = managementAuthorization(r, false);
        when(r.systemEnabled("s")).thenReturn(false);
        when(r.role(2L)).thenReturn(role());
        when(r.permissions(Set.of(1L))).thenReturn(List.of(permission()));
        RoleApplicationService service = new RoleApplicationService(
                r, authorization, mock(AuditApplicationService.class), mock(GrantIntegrityService.class));

        assertThatThrownBy(() -> service.permissions(principal, 2L, Set.of(1L)))
                .isInstanceOfSatisfying(DomainException.class, error -> {
                    assertThat(error.status()).isEqualTo(403);
                    assertThat(com.xmu.rbac.contract.ApiErrors.of(error.status(), error.code()).code())
                            .isEqualTo("PRIVILEGE_ESCALATION");
                });
        verify(r, never()).saveRole(any(Role.class));
        verify(r, never()).advanceVersion();
    }

    @ParameterizedTest
    @CsvSource({"false,empty,true", "false,permission,false", "false,identity,false", "true,permission,true"})
    void systemActivationChecksTheGrantsThatWouldBecomeEffective(
            boolean superAdmin, String configuredGrant, boolean allowed) {
        AuthorizationRepository r = repository();
        var principal = new TokenProvider.Principal("u7", "rbac-center", "jti", 0, null);
        var authorization = managementAuthorization(r, superAdmin);
        var directory = mock(DirectoryRepository.class);
        when(directory.system(2L)).thenReturn(
                new DirectoryRepository.SystemDefinition(2L, "s", "课程", "DISABLED", 1L));
        when(directory.saveSystem(any())).thenAnswer(call -> call.getArgument(0));
        var role = new Role(2L, "s", "viewer", "查看者", null, true, false,
                "identity".equals(configuredGrant) ? "teacher" : null, 0,
                "permission".equals(configuredGrant) ? Set.of(1L) : Set.of());
        when(r.systemEnabled("s")).thenReturn(false);
        when(r.hierarchy("s")).thenReturn(new RoleHierarchy(List.of(role), Set.of()));
        var service = new DirectoryApplicationService(
                directory, r, authorization, mock(AuditApplicationService.class), null,
                mock(GrantIntegrityService.class));

        if (allowed) {
            assertThat(service.system(principal, 2L, null, null, "ENABLED").status())
                    .isEqualTo("ENABLED");
            verify(directory).saveSystem(any());
            verify(r).advanceVersion();
        } else {
            assertThatThrownBy(() -> service.system(principal, 2L, null, null, "ENABLED"))
                    .isInstanceOfSatisfying(DomainException.class, error -> {
                        assertThat(error.status()).isEqualTo(403);
                        assertThat(com.xmu.rbac.contract.ApiErrors.of(error.status(), error.code()).code())
                                .isEqualTo("PRIVILEGE_ESCALATION");
                    });
            verify(directory, never()).saveSystem(any());
            verify(r, never()).advanceVersion();
        }
    }

    private AuthorizationApplicationService managementAuthorization(
            AuthorizationRepository repository, boolean superAdmin) {
        when(repository.identity("u7")).thenReturn(user(User.Status.ACTIVE));
        when(repository.departmentPath(1L)).thenReturn("/1/");
        when(repository.systemEnabled("rbac-center")).thenReturn(true);
        var permissions = List.of(
                new Permission(30L, "rbac-center", "rbac:role:edit", "编辑角色", Permission.Kind.API, true),
                new Permission(31L, "rbac-center", "rbac:system:edit", "编辑系统", Permission.Kind.API, true),
                new Permission(32L, "rbac-center", "rbac:user:edit", "编辑用户", Permission.Kind.API, true));
        var role = new Role(4L, "rbac-center", superAdmin ? "SUPER_ADMIN" : "RBAC_ADMIN", "管理员",
                null, true, superAdmin, null, 0, Set.of(30L, 31L, 32L));
        when(repository.hierarchy("rbac-center")).thenReturn(new RoleHierarchy(List.of(role), Set.of()));
        when(repository.permissions("rbac-center")).thenReturn(permissions);
        when(repository.assignments(7L, "rbac-center")).thenReturn(Set.of(role.id()));
        return new AuthorizationApplicationService(repository, mock(EffectiveCache.class));
    }

    @Test
    void missingUserAndUnknownOperationFailClosed() {
        AuthorizationRepository r = repository();
        when(r.identity(7L)).thenReturn(null);
        AuthorizationApplicationService service =
                new AuthorizationApplicationService(r, mock(EffectiveCache.class));
        assertThatThrownBy(() -> service.effective(7, "s")).hasMessageContaining("USER_NOT_FOUND");
        assertThatThrownBy(() -> service.api(7, "s", "missing"))
                .hasMessageContaining("API_OPERATION_NOT_FOUND");
    }
}
