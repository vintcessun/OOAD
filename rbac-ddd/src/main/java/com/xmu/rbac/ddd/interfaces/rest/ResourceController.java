package com.xmu.rbac.ddd.interfaces.rest;

import com.xmu.rbac.ddd.application.*;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;
import com.xmu.rbac.ddd.domain.port.ResourceRepository.*;
import com.xmu.rbac.ddd.domain.port.ResourceRepository.Mapping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/** UI/API 独立策略及显式映射入口。 */
@RestController
@RequestMapping("/api/v1")
public class ResourceController {
    private final ResourceApplicationService resources;
    private final RestSupport rest;

    public ResourceController(ResourceApplicationService resources, RestSupport rest) {
        this.resources = resources;
        this.rest = rest;
    }

    public record UiInput(
            Long parentId,
            @Pattern(regexp = "MENU|BUTTON") String type,
            @Size(max = 128) String code,
            @Size(max = 128) String name,
            @Size(max = 255) String route,
            @Size(max = 64) String component,
            Integer sortOrder,
            @Pattern(regexp = "ENABLED|DISABLED") String status,
            @Size(max = 128) String permissionCode) {}

    public record ApiInput(
            @NotBlank @Size(max = 128) String operationId,
            @NotBlank @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS") String method,
            @NotBlank @Size(max = 500) String pathTemplate,
            @Size(max = 128) String permissionCode,
            @Pattern(regexp = "teacher|student|admin") String requiredRole,
            @NotNull ApiOperation.Policy accessPolicy,
            @NotNull Boolean enabled) {}

    public record PermissionInput(
            @NotBlank String systemCode,
            @NotBlank @Size(max = 128) String code,
            @NotBlank @Size(max = 128) String name,
            @NotNull Permission.Kind kind) {}

    public record MappingInput(@NotNull List<@Valid Mapping> mappings) {}

    @GetMapping("/systems/{code}/ui-resources")
    public Object ui(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable String code) {
        return RestSupport.ok(ResourceViews.tree(resources.ui(rest.principal(b), code)));
    }

    @PostMapping("/systems/{code}/ui-resources")
    public Object ui(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable String code,
            @Valid @RequestBody com.xmu.rbac.ddd.interfaces.rest.dto.UiChangeRequest i) {
        return RestSupport.ok(
                ResourceViews.ui(
                        resources.createUi(
                                rest.principal(b),
                                code,
                                cn.edu.xmu.clonefactory.util.CloneFactory.copy(
                                        new UiChangeCommand(), i)),
                        List.of()));
    }

    @PatchMapping("/ui-resources/{id}")
    public Object uiPatch(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @Valid @RequestBody com.xmu.rbac.ddd.interfaces.rest.dto.UiChangeRequest i) {
        return RestSupport.ok(
                ResourceViews.ui(
                        resources.patchUi(
                                rest.principal(b),
                                id,
                                cn.edu.xmu.clonefactory.util.CloneFactory.copy(
                                        new UiChangeCommand(), i),
                                i.provided()),
                        List.of()));
    }

    @DeleteMapping("/ui-resources/{id}")
    public Object deleteUi(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id) {
        resources.deleteUi(rest.principal(b), id);
        return RestSupport.ok(null);
    }

    @GetMapping("/menus/me")
    public Object menus(@RequestHeader(value = "Authorization", required = false) String b) {
        return RestSupport.ok(ResourceViews.tree(resources.menus(rest.principal(b))));
    }

    @GetMapping("/systems/{code}/api-operations")
    public Object operations(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable String code,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuthorizationRepository.Page<ApiOperation> result =
                resources.operations(rest.principal(b), code, keyword, page, pageSize);
        return RestSupport.ok(
                Map.of(
                        "page",
                        page,
                        "pageSize",
                        pageSize,
                        "total",
                        result.total(),
                        "items",
                        result.items().stream().map(ResourceViews::operation).toList()));
    }

    @PostMapping("/systems/{code}/api-operations")
    public Object operation(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable String code,
            @Valid @RequestBody ApiInput i) {
        return RestSupport.ok(
                ResourceViews.operation(
                        resources.operation(
                                rest.principal(b),
                                new ApiOperation(
                                        0,
                                        code,
                                        i.operationId(),
                                        i.method(),
                                        i.pathTemplate(),
                                        i.permissionCode(),
                                        i.requiredRole(),
                                        i.accessPolicy(),
                                        i.enabled()))));
    }

    @PatchMapping("/api-operations/{id}")
    public Object patchOperation(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @Valid @RequestBody com.xmu.rbac.ddd.interfaces.rest.dto.ApiUpdateRequest i) {
        return RestSupport.ok(
                ResourceViews.operation(
                        resources.patchOperation(
                                rest.principal(b),
                                id,
                                i.getOperationId(),
                                i.getMethod(),
                                i.getPathTemplate(),
                                i.getPermissionCode(),
                                i.getRequiredRole(),
                                i.getAccessPolicy(),
                                i.getEnabled(),
                                i.provided())));
    }

    @GetMapping("/systems/{code}/ui-api-mappings")
    public Object mappings(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable String code) {
        ResourceApplicationService.MappingCoverage coverage =
                resources.mappings(rest.principal(b), code);
        return RestSupport.ok(
                Map.of(
                        "mappings", coverage.mappings(),
                        "unmappedUi",
                                coverage.unmappedUi().stream()
                                        .map(ui -> ResourceViews.ui(ui, List.of()))
                                        .toList(),
                        "unmappedApi",
                                coverage.unmappedApi().stream()
                                        .map(ResourceViews::operation)
                                        .toList()));
    }

    @PutMapping("/systems/{code}/ui-api-mappings")
    public Object mappings(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable String code,
            @Valid @RequestBody MappingInput i) {
        resources.mappings(rest.principal(b), code, i.mappings());
        return RestSupport.ok(null);
    }

    @GetMapping("/permissions")
    public Object permissions(
            @RequestHeader(value = "Authorization", required = false) String b,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuthorizationRepository.Page<Permission> result =
                resources.permissions(rest.principal(b), systemCode, kind, keyword, page, pageSize);
        return RestSupport.ok(
                Map.of(
                        "page",
                        page,
                        "pageSize",
                        pageSize,
                        "total",
                        result.total(),
                        "items",
                        result.items().stream().map(RestSupport::permission).toList()));
    }

    @PostMapping("/permissions")
    public Object permission(
            @RequestHeader(value = "Authorization", required = false) String b,
            @Valid @RequestBody PermissionInput i) {
        return RestSupport.ok(
                RestSupport.permission(
                        resources.permission(
                                rest.principal(b),
                                new Permission(
                                        0, i.systemCode(), i.code(), i.name(), i.kind(), true))));
    }

    @PutMapping("/permissions/{id}/status")
    public Object permissionStatus(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @Valid @RequestBody RoleController.Status i) {
        return RestSupport.ok(
                RestSupport.permission(
                        resources.status(rest.principal(b), id, i.status().equals("ENABLED"))));
    }
}
