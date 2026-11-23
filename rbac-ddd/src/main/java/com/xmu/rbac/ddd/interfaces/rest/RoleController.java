package com.xmu.rbac.ddd.interfaces.rest;

import com.xmu.rbac.ddd.application.*;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 角色 HTTP 边界；普通修改与状态、权限、继承分别建模。 */
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/api/v1")
public class RoleController {
    private final RestSupport rest;
    private final RoleApplicationService roles;
    private final AuthorizationRepository repository;

    public RoleController(
            RestSupport rest, RoleApplicationService roles, AuthorizationRepository repository) {
        this.rest = rest;
        this.roles = roles;
        this.repository = repository;
    }

    public record Create(
            @NotBlank @Size(max = 64) String systemCode,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 500) String description) {}

    public record Patch(
            @Size(min = 1, max = 128) String name,
            @Size(max = 500) String description,
            Long version) {}

    public record Status(@NotBlank @Pattern(regexp = "ENABLED|DISABLED") String status) {}

    public record Permissions(@NotNull @Size(max = 1000) List<@NotNull @Positive Long> permissionIds) {}

    @GetMapping("/roles")
    public Map<String, Object> list(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestParam String systemCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuthorizationRepository.Page<Role> result =
                roles.list(rest.principal(bearer), systemCode, keyword, status, page, pageSize);
        return RestSupport.ok(
                Map.of(
                        "page",
                        page,
                        "pageSize",
                        pageSize,
                        "total",
                        result.total(),
                        "items",
                        result.items().stream().map(RestSupport::role).toList()));
    }

    @PostMapping("/roles")
    public Map<String, Object> create(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @Valid @RequestBody com.xmu.rbac.ddd.interfaces.rest.dto.CreateRoleRequest input) {
        return RestSupport.ok(
                RestSupport.role(
                        roles.create(
                                rest.principal(bearer),
                                cn.edu.xmu.clonefactory.util.CloneFactory.copy(
                                        new RoleCommand(), input))));
    }

    @GetMapping("/roles/{id}")
    public Map<String, Object> get(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable @Positive long id) {
        return RestSupport.ok(RestSupport.role(roles.get(rest.principal(bearer), id)));
    }

    @PatchMapping("/roles/{id}")
    public Map<String, Object> patch(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable @Positive long id,
            @Valid @RequestBody com.xmu.rbac.ddd.interfaces.rest.dto.RoleUpdateRequest input) {
        DomainException.require(
                !input.provided("name") || input.getName() != null, 400, "INVALID_REQUEST");
        return RestSupport.ok(
                RestSupport.role(
                        roles.rename(
                                rest.principal(bearer),
                                id,
                                cn.edu.xmu.clonefactory.util.CloneFactory.copy(
                                        new RoleUpdateCommand(), input),
                                input.provided("description"))));
    }

    @DeleteMapping("/roles/{id}")
    public Map<String, Object> deleteRole(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable @Positive long id) {
        roles.deleteRole(rest.principal(bearer), id);
        return RestSupport.ok(null);
    }

    @PutMapping("/roles/{id}/status")
    public Map<String, Object> status(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable @Positive long id,
            @Valid @RequestBody Status input) {
        return RestSupport.ok(
                RestSupport.role(
                        roles.status(
                                rest.principal(bearer), id, input.status().equals("ENABLED"))));
    }

    @GetMapping("/roles/{id}/permissions")
    public Map<String, Object> permissions(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable @Positive long id) {
        Role role = roles.get(rest.principal(bearer), id);
        return RestSupport.ok(
                repository.permissions(role.systemCode()).stream()
                        .filter(p -> role.permissionIds().contains(p.id()))
                        .map(RestSupport::permission)
                        .toList());
    }

    @PutMapping("/roles/{id}/permissions")
    public Map<String, Object> permissions(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable @Positive long id,
            @Valid @RequestBody Permissions input) {
        roles.permissions(rest.principal(bearer), id, new LinkedHashSet<>(input.permissionIds()));
        return RestSupport.ok(null);
    }

    @GetMapping("/roles/{id}/effective-permissions")
    public Map<String, Object> effective(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable @Positive long id) {
        return RestSupport.ok(RestSupport.effective(roles.effective(rest.principal(bearer), id)));
    }

    @GetMapping("/systems/{code}/role-hierarchy")
    public Map<String, Object> graph(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable String code) {
        RoleHierarchy graph = roles.hierarchy(rest.principal(bearer), code);
        return RestSupport.ok(
                Map.of(
                        "roles",
                        graph.roles().stream().map(RestSupport::role).toList(),
                        "edges",
                        graph.edges()));
    }

    @PostMapping("/roles/{seniorId}/inherits/{juniorId}")
    public Map<String, Object> link(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable @Positive long seniorId,
            @PathVariable @Positive long juniorId) {
        roles.link(rest.principal(bearer), seniorId, juniorId, true);
        return RestSupport.ok(null);
    }

    @DeleteMapping("/roles/{seniorId}/inherits/{juniorId}")
    public Map<String, Object> unlink(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable @Positive long seniorId,
            @PathVariable @Positive long juniorId) {
        roles.link(rest.principal(bearer), seniorId, juniorId, false);
        return RestSupport.ok(null);
    }
}
