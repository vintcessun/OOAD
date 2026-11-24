package com.xmu.rbac.ddd.application;

import com.xmu.rbac.contract.DashboardAccess;
import com.xmu.rbac.contract.CourseRoleCodes;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 用例协调器，每次命中缓存前读取权威版本和当前用户/系统状态。 */
@Service
public class AuthorizationApplicationService {
    private static final String COURSE_SYSTEM = "course-project-management";
    // AuthorityCodes from the teacher backend. A code lookup is separate from route authorization.
    private static final java.util.Set<String> COURSE_API_CODES = java.util.Set.of(
            "TC", "TE", "TD", "TCM", "CV", "CR", "CRS", "CE", "CD",
            "CC", "CTM", "TV", "TEC", "TEE", "TED", "AV", "UL");
    private final AuthorizationRepository repository;
    private final EffectiveCache cache;
    private final AuthorizationReadTransaction reads;
    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    public AuthorizationApplicationService(
            AuthorizationRepository repository, EffectiveCache cache) {
        this(repository, cache, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AuthorizationApplicationService(
            AuthorizationRepository repository,
            EffectiveCache cache,
            AuthorizationReadTransaction reads) {
        this.repository = repository;
        this.cache = cache;
        this.reads = reads;
    }

    public AuthorizationRepository.Identity validate(TokenProvider.Principal principal) {
        AuthorizationRepository.Identity user = repository.identity(principal.subject());
        DomainException.require(
                user != null
                        && user.status() == User.Status.ACTIVE
                        && user.tokenVersion() == principal.tokenVersion()
                        && !repository.revoked(principal.jti()),
                401,
                "TOKEN_INVALID");
        boolean systemEnabled;
        try {
            systemEnabled = repository.systemEnabled(principal.systemCode());
        } catch (DomainException error) {
            // aud 是令牌声明，不是资源查询：未知 aud 必须按无效身份拒绝，不能泄漏为 404。
            if (error.status() == 404) throw new DomainException(401, "TOKEN_INVALID");
            throw error;
        }
        DomainException.require(systemEnabled, 401, "SYSTEM_DISABLED");
        return user;
    }

    /** 管理端模拟的目标状态；停用目标是拒绝结果，不是调用管理员的认证失败。 */
    public String unavailableReason(long userId, String system) {
        boolean enabled = repository.systemEnabled(system);
        AuthorizationRepository.Identity target = repository.identity(userId);
        DomainException.require(target != null, 404, "USER_NOT_FOUND");
        if (target.status() != User.Status.ACTIVE) return "USER_DISABLED";
        return enabled ? null : "SYSTEM_DISABLED";
    }

    /** 每次计算或缓存命中都被权威版本括住；变更则重试，持续不稳定返回 503。 */
    public AuthorizationPolicy.Effective effective(long userId, String system) {
        if (reads != null
                && TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            return reads.fresh(() -> stableEffective(userId, system));
        return stableEffective(userId, system);
    }

    private AuthorizationPolicy.Effective stableEffective(long userId, String system) {
        boolean transaction = TransactionSynchronizationManager.isActualTransactionActive();
        for (int attempt = 0; attempt < 3; attempt++) {
            if (transaction && TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                repository.freshenRead();
            long version = repository.version();
            DomainException.require(repository.systemEnabled(system), 401, "SYSTEM_DISABLED");
            AuthorizationRepository.Identity target = repository.identity(userId);
            DomainException.require(target != null, 404, "USER_NOT_FOUND");
            boolean active = target.status() == User.Status.ACTIVE;
            AuthorizationPolicy.Effective result =
                    active && !transaction ? cache.get(system, userId, version) : null;
            if (!active)
                result =
                        new AuthorizationPolicy.Effective(
                                java.util.List.of(),
                                java.util.List.of(),
                                java.util.List.of(),
                                java.util.List.of());
            if (result == null)
                result =
                        policy.calculate(
                                system,
                                repository.assignments(userId, system),
                                repository.hierarchy(system),
                                repository.permissions(system));
            if (version != repository.version()) continue;
            if (active && !transaction) cache.put(system, userId, version, result);
            if (version == repository.version()) return result;
        }
        throw new DomainException(503, "AUTHORIZATION_UNSTABLE");
    }

    public AuthorizationRepository.Identity require(
            TokenProvider.Principal principal, String permission, boolean sharedWrite) {
        AuthorizationRepository.Identity actor = centerActor(principal);
        AuthorizationPolicy.Effective effective = effective(actor.id(), "rbac-center");
        DomainException.require(
                effective.superAdministrator() || effective.has(permission, Permission.Kind.API),
                403,
                "PERMISSION_DENIED");
        if (sharedWrite)
            DomainException.require(
                    repository.departmentPath(actor.departmentId()).equals("/1/"),
                    403,
                    "ROOT_DEPARTMENT_REQUIRED");
        return actor;
    }

    public AuthorizationRepository.Identity requireDashboard(TokenProvider.Principal principal) {
        AuthorizationRepository.Identity actor = centerActor(principal);
        DomainException.require(
                api(actor.id(), DashboardAccess.SYSTEM, DashboardAccess.OPERATION),
                403,
                "PERMISSION_DENIED");
        return actor;
    }

    private AuthorizationRepository.Identity centerActor(TokenProvider.Principal principal) {
        AuthorizationRepository.Identity actor = validate(principal);
        DomainException.require(
                principal.systemCode().equals("rbac-center"), 403, "AUDIENCE_FORBIDDEN");
        return actor;
    }

    /** 操作策略、目标身份和有效权限共同受同一个版本括号保护。 */
    public boolean api(long user, String system, String operation) {
        return api(user, system, operation, false);
    }

    /** The teacher identity client can send an AuthorityCodes value in the operation ID field. */
    public boolean identityApi(long user, String system, String identifier) {
        return api(user, system, identifier, COURSE_SYSTEM.equals(system) && COURSE_API_CODES.contains(identifier));
    }

    private boolean api(long user, String system, String operation, boolean allowCode) {
        if (reads != null
                && TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            return reads.fresh(() -> stableApi(user, system, operation, allowCode));
        return stableApi(user, system, operation, allowCode);
    }

    private boolean stableApi(long user, String system, String operation) {
        return stableApi(user, system, operation, false);
    }

    private boolean stableApi(long user, String system, String operation, boolean allowCode) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                repository.freshenRead();
            long version = repository.version();
            ApiOperation registered = repository.operation(system, operation);
            if (registered == null && !allowCode) {
                if (version != repository.version()) continue;
                throw new DomainException(404, "API_OPERATION_NOT_FOUND");
            }
            AuthorizationRepository.Identity target = repository.identity(user);
            boolean allowed = false;
            if (target != null && target.status() == User.Status.ACTIVE) {
                AuthorizationPolicy.Effective effective = effective(user, system);
                target = repository.identity(user);
                allowed =
                        target != null
                                && target.status() == User.Status.ACTIVE
                                && (registered == null
                                        ? effective.has(operation, Permission.Kind.API)
                                        : DashboardAccess.matches(system, operation)
                                        ? registered.enabled() && DashboardAccess.REQUIRED_PERMISSIONS.stream()
                                                .allMatch(code -> effective.has(code, Permission.Kind.API))
                                        : policy.allows(registered, effective));
            }
            if (version == repository.version()) return allowed;
        }
        throw new DomainException(503, "AUTHORIZATION_UNSTABLE");
    }

    public record SimulationResult(
            boolean allowed, String unavailableReason, AuthorizationPolicy.Effective effective) {}

    /** 模拟的目标状态、判定和展示快照必须对应同一授权版本。 */
    public SimulationResult simulate(long user, String system, Permission.Kind kind, String identifier) {
        if (reads != null
                && TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            return reads.fresh(() -> stableSimulation(user, system, kind, identifier));
        return stableSimulation(user, system, kind, identifier);
    }

    private SimulationResult stableSimulation(
            long user, String system, Permission.Kind kind, String identifier) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isCurrentTransactionReadOnly())
                repository.freshenRead();
            long version = repository.version();
            try {
                String unavailable = unavailableReason(user, system);
                AuthorizationPolicy.Effective effective =
                        unavailable == null
                                ? stableEffective(user, system)
                                : new AuthorizationPolicy.Effective(
                                        java.util.List.of(),
                                        java.util.List.of(),
                                        java.util.List.of(),
                                        java.util.List.of());
                boolean allowed = unavailable == null
                        && (kind == Permission.Kind.UI
                                ? effective.has(identifier, Permission.Kind.UI)
                                : stableApi(user, system, identifier));
                if (version == repository.version())
                    return new SimulationResult(allowed, unavailable, effective);
            } catch (DomainException error) {
                if (error.status() >= 500 || version == repository.version()) throw error;
            }
        }
        throw new DomainException(503, "AUTHORIZATION_UNSTABLE");
    }

    public void upperBound(
            AuthorizationRepository.Identity actor,
            String system,
            AuthorizationPolicy.Effective proposed) {
        boolean privileged = effective(actor.id(), "rbac-center").superAdministrator();
        java.util.Set<Long> direct = new java.util.HashSet<>();
        for (Role role : proposed.directRoles()) direct.add(role.id());
        repository.hierarchy(system).requireGrantable(direct, privileged);
        if (!privileged)
            policy.requireWithin(effective(actor.id(), system), proposed, false,
                    protectedRoleCodes(system));
    }

    /** 停用系统没有可委派的有效授权，中心超级管理员仍可维护其角色配置。 */
    public void requireRoleGrantSystem(AuthorizationRepository.Identity actor, String system) {
        DomainException.require(
                effective(actor.id(), "rbac-center").superAdministrator()
                        || repository.systemEnabled(system),
                403,
                "GRANT_EXCEEDS_ACTOR");
    }

    /** 启用前停用系统的有效上界为空，启用后的全部角色闭包都不得超出此上界。 */
    public void requireSystemActivation(AuthorizationRepository.Identity actor, String system) {
        if (effective(actor.id(), "rbac-center").superAdministrator()) return;
        RoleHierarchy graph = repository.hierarchy(system);
        java.util.Set<Long> roleIds = new java.util.HashSet<>();
        for (Role role : graph.roles()) roleIds.add(role.id());
        graph.requireGrantable(roleIds, false);
        var before = new AuthorizationPolicy.Effective(
                java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of());
        policy.requireWithin(
                before, policy.calculate(system, roleIds, graph, repository.permissions(system)),
                false, protectedRoleCodes(system));
    }

    private java.util.Set<String> protectedRoleCodes(String system) {
        var codes = new java.util.HashSet<>(CourseRoleCodes.ALL);
        codes.addAll(repository.requiredRoleCodes(system));
        return codes;
    }

    /** 已登记业务路由仍受数据库当前启停状态约束，超级管理员不能绕过停用操作。 */
    public void requireRegisteredOperation(String operationId) {
        ApiOperation operation = repository.operation("rbac-center", operationId);
        DomainException.require(operation != null, 403, "API_UNREGISTERED");
        DomainException.require(operation.enabled(), 403, "API_OPERATION_DISABLED");
    }

    public void requireRoleManagement(AuthorizationRepository.Identity actor, Role role) {
        role.requireManageable(effective(actor.id(), "rbac-center").superAdministrator());
    }

    public void requireGraphGrant(
            AuthorizationRepository.Identity actor, RoleHierarchy graph, long direct) {
        graph.requireGrantable(
                java.util.Set.of(direct),
                effective(actor.id(), "rbac-center").superAdministrator());
    }
}
