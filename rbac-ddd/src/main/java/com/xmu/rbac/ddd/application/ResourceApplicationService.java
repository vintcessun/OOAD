package com.xmu.rbac.ddd.application;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;
import com.xmu.rbac.ddd.domain.port.ResourceRepository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 独立资源用例；显式映射仅作追踪，不产生授权事实。 */
@Service
public class ResourceApplicationService {
    private final GrantIntegrityService integrity;
    private final ResourceRepository resources;
    private final AuthorizationRepository grants;
    private final AuthorizationApplicationService authorization;
    private final AuditApplicationService audits;
    private final OperationContract contract;
    private final ResourcePolicy policy = new ResourcePolicy();

    public ResourceApplicationService(
            ResourceRepository resources,
            AuthorizationRepository grants,
            AuthorizationApplicationService authorization,
            AuditApplicationService audits,
            OperationContract contract,
            GrantIntegrityService integrity) {
        this.integrity = integrity;
        this.resources = resources;
        this.grants = grants;
        this.authorization = authorization;
        this.audits = audits;
        this.contract = contract;
    }

    private void page(int page, int size) {
        DomainException.require(page > 0 && size > 0 && size <= 100, 400, "INVALID_PAGE");
    }

    private void done(AuthorizationRepository.Identity actor, String system, String target) {
        integrity.requireAdministrator();
        grants.advanceVersion();
        audits.local(actor, system, "RESOURCE_CHANGED", target, "SUCCESS", null);
    }

    public List<Ui> ui(TokenProvider.Principal principal, String system) {
        authorization.require(principal, "rbac:resource:view", false);
        return resources.ui(system);
    }

    public List<Ui> menus(TokenProvider.Principal principal) {
        AuthorizationRepository.Identity user = authorization.validate(principal);
        AuthorizationPolicy.Effective effective =
                authorization.effective(user.id(), principal.systemCode());
        List<Ui> all = resources.ui(principal.systemCode());
        return policy.visible(all, effective);
    }

    private Ui values(UiChangeCommand command) {
        return new Ui(
                null,
                null,
                command.getParentId(),
                command.getType(),
                command.getCode(),
                command.getName(),
                command.getRoute(),
                command.getComponent(),
                command.getSortOrder(),
                command.getStatus(),
                command.getPermissionCode());
    }

    private Ui storeUi(AuthorizationRepository.Identity actor, Ui proposed) {
        policy.validate(proposed, resources.ui(proposed.systemCode()));
        Ui saved = resources.saveUi(proposed);
        done(actor, saved.systemCode(), saved.code());
        return saved;
    }

    @Transactional
    public Ui createUi(TokenProvider.Principal principal, String system, UiChangeCommand command) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(principal, "rbac:resource:edit", true);
        DomainException.require(
                command.getType() != null
                        && command.getCode() != null
                        && !command.getCode().isBlank()
                        && command.getName() != null
                        && !command.getName().isBlank()
                        && command.getPermissionCode() != null
                        && !command.getPermissionCode().isBlank(),
                400,
                "INVALID_UI_RESOURCE");
        return storeUi(
                actor,
                new Ui(
                        null,
                        system,
                        command.getParentId(),
                        command.getType(),
                        command.getCode(),
                        command.getName(),
                        command.getRoute(),
                        command.getComponent(),
                        command.getSortOrder() == null ? 0 : command.getSortOrder(),
                        command.getStatus() == null ? "ENABLED" : command.getStatus(),
                        command.getPermissionCode()));
    }

    @Transactional
    public Ui patchUi(
            TokenProvider.Principal principal,
            long id,
            UiChangeCommand command,
            Set<String> fields) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(principal, "rbac:resource:edit", true);
        Ui old = resources.ui(id);
        return storeUi(actor, policy.patch(old, values(command), fields));
    }

    @Transactional
    public void deleteUi(TokenProvider.Principal principal, long id) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(principal, "rbac:resource:edit", true);
        Ui ui = resources.ui(id);
        policy.requireDeletable(
                ui, resources.ui(ui.systemCode()), resources.mappings(ui.systemCode()));
        resources.deleteUi(id);
        done(actor, ui.systemCode(), ui.code());
    }

    public AuthorizationRepository.Page<ApiOperation> operations(
            TokenProvider.Principal p, String system, String keyword, int page, int size) {
        authorization.require(p, "rbac:resource:view", false);
        page(page, size);
        return resources.operations(system, keyword, page, size);
    }

    @Transactional
    public ApiOperation operation(TokenProvider.Principal principal, ApiOperation proposed) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(principal, "rbac:resource:edit", true);
        String requiredRole = proposed.requiredRole();
        DomainException.require(requiredRole == null || !requiredRole.isBlank(), 400, "VALIDATION_ERROR");
        OperationSystem system = resources.operationSystem(proposed.systemCode());
        ApiOperation old = proposed.id() == 0 ? null : resources.operation(proposed.id());
        DomainException.require(
                old == null || old.systemCode().equals(system.code()), 409, "CROSS_SYSTEM_OPERATION");
        if ("rbac-center".equals(system.code())) {
            ApiOperation frozen =
                    contract.operations().stream()
                            .filter(o -> o.operationId().equals(proposed.operationId()))
                            .findFirst()
                            .orElse(null);
            DomainException.require(
                    (old == null || old.operationId().equals(proposed.operationId()))
                            && proposed.matchesDefinition(frozen),
                    400,
                    "CENTER_OPERATION_IMMUTABLE");
        }
        Long permissionId = proposed.permissionCode() == null
                ? null
                : resources.apiPermissionId(system.id(), proposed.permissionCode());
        boolean hasCondition = permissionId != null || requiredRole != null;
        DomainException.require(
                proposed.policy() != ApiOperation.Policy.PROTECTED || hasCondition,
                400,
                "API_REQUIREMENT_MISSING");
        if (hasCondition
                && !proposed.onlyDisables(old)
                && !authorization.effective(actor.id(), "rbac-center").superAdministrator()) {
            DomainException.require(system.enabled(), 403, "GRANT_EXCEEDS_ACTOR");
            AuthorizationPolicy.Effective target = authorization.effective(actor.id(), system.code());
            DomainException.require(
                    (permissionId == null
                                    || target.permissions().stream().anyMatch(p -> p.id() == permissionId))
                            && (requiredRole == null || target.identities().contains(requiredRole)),
                    403,
                    "GRANT_EXCEEDS_ACTOR");
        }
        ApiOperation saved = resources.saveOperation(proposed, system.id(), permissionId);
        done(actor, system.code(), proposed.operationId());
        return saved;
    }

    public ApiOperation operation(TokenProvider.Principal p, long id) {
        authorization.require(p, "rbac:resource:view", false);
        return resources.operation(id);
    }

    public record MappingCoverage(
            List<Mapping> mappings, List<Ui> unmappedUi, List<ApiOperation> unmappedApi) {}

    public MappingCoverage mappings(TokenProvider.Principal p, String system) {
        authorization.require(p, "rbac:resource:view", false);
        List<Mapping> mappings = resources.mappings(system);
        List<Ui> unmappedUi =
                resources.ui(system).stream()
                        .filter(u -> mappings.stream().noneMatch(m -> m.uiResourceId() == u.id()))
                        .toList();
        List<ApiOperation> unmappedApi = new ArrayList<>();
        int page = 1;
        AuthorizationRepository.Page<ApiOperation> batch;
        do {
            batch = resources.operations(system, null, page++, 100);
            for (ApiOperation operation : batch.items())
                if (mappings.stream().noneMatch(m -> m.apiOperationId() == operation.id()))
                    unmappedApi.add(operation);
        } while ((long) (page - 1) * 100 < batch.total());
        return new MappingCoverage(mappings, unmappedUi, unmappedApi);
    }

    @Transactional
    public void mappings(TokenProvider.Principal p, String system, List<Mapping> mappings) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(p, "rbac:resource:edit", true);
        DomainException.require(
                new HashSet<>(mappings).size() == mappings.size(), 400, "DUPLICATE_MAPPING");
        for (Mapping mapping : mappings)
            policy.validateMapping(
                    system,
                    resources.ui(mapping.uiResourceId()),
                    resources.operation(mapping.apiOperationId()));
        resources.mappings(system, mappings);
        done(actor, system, "UI_API_MAPPINGS");
    }

    public AuthorizationRepository.Page<Permission> permissions(
            TokenProvider.Principal p,
            String system,
            String kind,
            String keyword,
            int page,
            int size) {
        authorization.require(p, "rbac:permission:view", false);
        page(page, size);
        return resources.permissions(system, kind, keyword, page, size);
    }

    @Transactional
    public Permission permission(TokenProvider.Principal p, Permission proposed) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(p, "rbac:permission:edit", true);
        if (proposed.id() != 0 && proposed.enabled()) {
            AuthorizationPolicy.Effective target =
                    authorization.effective(actor.id(), proposed.systemCode());
            DomainException.require(
                    authorization.effective(actor.id(), "rbac-center").superAdministrator()
                            || target.has(proposed.code(), proposed.kind()),
                    403,
                    "GRANT_EXCEEDS_ACTOR");
        }
        Permission saved = resources.savePermission(proposed);
        done(actor, proposed.systemCode(), proposed.code());
        return saved;
    }

    @Transactional
    public Permission status(TokenProvider.Principal p, long id, boolean enabled) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor =
                authorization.require(p, "rbac:permission:edit", true);
        Permission old = resources.permission(id);
        Permission proposed =
                new Permission(
                        old.id(), old.systemCode(), old.code(), old.name(), old.kind(), enabled);
        if (enabled)
            DomainException.require(
                    authorization.effective(actor.id(), "rbac-center").superAdministrator()
                            || authorization
                                    .effective(actor.id(), old.systemCode())
                                    .has(old.code(), old.kind()),
                    403,
                    "GRANT_EXCEEDS_ACTOR");
        Permission saved = resources.savePermission(proposed);
        done(actor, old.systemCode(), old.code());
        return saved;
    }

    @Transactional
    public ApiOperation patchOperation(
            TokenProvider.Principal principal,
            long id,
            String operationId,
            String method,
            String path,
            String permissionCode,
            String requiredRole,
            ApiOperation.Policy policy,
            Boolean enabled,
            Set<String> present) {
        grants.lockGrants();
        authorization.require(principal, "rbac:resource:edit", true);
        ApiOperation old = resources.operation(id);
        DomainException.require(
                (!present.contains("operationId") || operationId != null)
                        && (!present.contains("method") || method != null)
                        && (!present.contains("pathTemplate") || path != null)
                        && (!present.contains("accessPolicy") || policy != null)
                        && (!present.contains("enabled") || enabled != null),
                400,
                "INVALID_OPERATION_PATCH");
        return operation(
                principal,
                new ApiOperation(
                        id,
                        old.systemCode(),
                        present.contains("operationId") ? operationId : old.operationId(),
                        present.contains("method") ? method : old.method(),
                        present.contains("pathTemplate") ? path : old.pathTemplate(),
                        present.contains("permissionCode") ? permissionCode : old.permissionCode(),
                        present.contains("requiredRole") ? requiredRole : old.requiredRole(),
                        present.contains("accessPolicy") ? policy : old.policy(),
                        present.contains("enabled") ? enabled : old.enabled()));
    }
}
