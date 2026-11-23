package com.xmu.rbac.structured.controller;
import com.xmu.rbac.structured.dto.*;
import com.xmu.rbac.structured.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
/** 冻结管理HTTP合同；本层只进行格式校验和服务调用。 */
@RestController
@Validated
@RequestMapping("/api/v1")
public class ManagementController {
    private final ManagementService management;
    private final ResourceService resources;
    private final AuthenticationService identity;
    private final AuditService audit;
    public ManagementController(ManagementService management, ResourceService resources,
        AuthenticationService identity, AuditService audit) {
        this.management = management;
        this.resources = resources;
        this.identity = identity;
        this.audit = audit;
    }
    private Object ok(Object data) {
        return Views.map("code", "OK", "message", "成功", "data", data, "timestamp", Instant.now());
    }
    private NameChange name(DisplayName dto) {
        return cn.edu.xmu.clonefactory.util.CloneFactory.copy(new NameChange(null, null, null), dto);
    }
    @GetMapping("/me")
    public Object getMe(@RequestAttribute Actor actor) {
        return ok(identity.me(actor));
    }
    @PostMapping("/auth/password")
    public Object changePassword(
        @RequestAttribute Actor actor, @Valid @RequestBody Inputs.Password input) {
        identity.password(actor, input);
        return ok(null);
    }
    @GetMapping("/systems")
    public Object listSystems(@RequestAttribute Actor actor) {
        return ok(management.systems(actor));
    }
    @PostMapping("/systems")
    public Object createSystem(
        @RequestAttribute Actor actor, @Valid @RequestBody Inputs.SystemCreate input) {
        return ok(management.createSystem(actor, input));
    }
    @PatchMapping("/systems/{id}")
    public Object updateSystem(@RequestAttribute Actor actor, @PathVariable @Positive Long id,
        @Valid @RequestBody DisplayName input) {
        return ok(management.renameSystem(actor, id, name(input)));
    }
    @PutMapping("/systems/{id}/status")
    public Object setSystemStatus(@RequestAttribute Actor actor, @PathVariable @Positive Long id,
        @Valid @RequestBody Inputs.State input) {
        return ok(management.systemStatus(actor, id, input.status()));
    }
    @GetMapping("/departments/tree")
    public Object getDepartmentTree(@RequestAttribute Actor actor) {
        return ok(management.departments(actor));
    }
    @PostMapping("/departments")
    public Object createDepartment(
        @RequestAttribute Actor actor, @Valid @RequestBody Inputs.DepartmentCreate input) {
        return ok(management.createDepartment(actor, input));
    }
    @PatchMapping("/departments/{id}")
    public Object updateDepartment(@RequestAttribute Actor actor, @PathVariable @Positive Long id,
        @Valid @RequestBody DisplayName input) {
        return ok(management.renameDepartment(actor, id, name(input)));
    }
    @PutMapping("/departments/{id}/status")
    public Object setDepartmentStatus(@RequestAttribute Actor actor, @PathVariable @Positive Long id,
        @Valid @RequestBody Inputs.EnabledState input) {
        return ok(management.departmentStatus(actor, id, input.status()));
    }
    @DeleteMapping("/departments/{id}")
    public Object deleteDepartment(@RequestAttribute Actor actor, @PathVariable @Positive Long id) {
        management.deleteDepartment(actor, id);
        return ok(null);
    }
    @GetMapping("/departments/{id}/assignable-roles")
    public Object getDepartmentAssignableRoles(@RequestAttribute Actor actor,
        @PathVariable @Positive Long id, @RequestParam(required = false) String systemCode) {
        return ok(management.allowedRoles(actor, id, systemCode));
    }
    @PutMapping("/departments/{id}/assignable-roles")
    public Object replaceDepartmentAssignableRoles(@RequestAttribute Actor actor,
        @PathVariable @Positive Long id, @Valid @RequestBody Inputs.Roles input) {
        management.replaceAllowed(actor, id, input.roleIds());
        return ok(null);
    }
    @GetMapping("/users")
    public Object listUsers(@RequestAttribute Actor actor,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ok(management.users(actor, keyword, departmentId, status, page, pageSize));
    }
    @PostMapping("/users")
    public Object createUser(
        @RequestAttribute Actor actor, @Valid @RequestBody Inputs.UserCreate input) {
        return ok(management.createUser(actor, input));
    }
    @GetMapping("/users/{id}")
    public Object getUser(@RequestAttribute Actor actor, @PathVariable @Positive Long id) {
        return ok(management.user(actor, id));
    }
    @PatchMapping("/users/{id}")
    public Object updateUser(@RequestAttribute Actor actor, @PathVariable @Positive Long id,
        @Valid @RequestBody Inputs.UserPatch input) {
        return ok(management.patchUser(actor, id, input));
    }
    @DeleteMapping("/users/{id}")
    public Object deleteUser(@RequestAttribute Actor actor, @PathVariable @Positive Long id) {
        management.userStatus(actor, id, "DELETED");
        return ok(null);
    }
    @PutMapping("/users/{id}/status")
    public Object setUserStatus(@RequestAttribute Actor actor, @PathVariable @Positive Long id,
        @Valid @RequestBody Inputs.State input) {
        return ok(management.userStatus(actor, id, input.status()));
    }
    @PutMapping("/users/{id}/department")
    public Object changeUserDepartment(@RequestAttribute Actor actor,
        @PathVariable @Positive Long id, @Valid @RequestBody Inputs.Move input) {
        return ok(management.moveUser(actor, id, input));
    }
    @GetMapping("/roles")
    public Object listRoles(@RequestAttribute Actor actor, @RequestParam String systemCode,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ok(management.roles(actor, systemCode, keyword, status, page, pageSize));
    }
    @PostMapping("/roles")
    public Object createRole(
        @RequestAttribute Actor actor, @Valid @RequestBody Inputs.RoleCreate input) {
        return ok(management.createRole(actor, input));
    }
    @GetMapping("/roles/{id}")
    public Object getRole(@RequestAttribute Actor actor, @PathVariable @Positive Long id) {
        return ok(management.role(actor, id));
    }
    @PatchMapping("/roles/{id}")
    public Object updateRole(@RequestAttribute Actor actor, @PathVariable @Positive Long id,
        @Valid @RequestBody Inputs.RolePatch input) {
        return ok(management.patchRole(actor, id, input));
    }
    @DeleteMapping("/roles/{id}")
    public Object deleteRole(@RequestAttribute Actor actor, @PathVariable @Positive Long id) {
        management.deleteRole(actor, id);
        return ok(null);
    }
    @PutMapping("/roles/{id}/status")
    public Object setRoleStatus(@RequestAttribute Actor actor, @PathVariable @Positive Long id,
        @Valid @RequestBody Inputs.State input) {
        return ok(management.roleStatus(actor, id, input.status()));
    }
    @GetMapping("/roles/{id}/permissions")
    public Object getRolePermissions(
        @RequestAttribute Actor actor, @PathVariable @Positive Long id) {
        return ok(management.directPermissions(actor, id));
    }
    @PutMapping("/roles/{id}/permissions")
    public Object replaceRolePermissions(@RequestAttribute Actor actor,
        @PathVariable @Positive Long id, @Valid @RequestBody Inputs.Permissions input) {
        management.replacePermissions(actor, id, input.permissionIds());
        return ok(null);
    }
    @GetMapping("/roles/{id}/effective-permissions")
    public Object getRoleEffectivePermissions(
        @RequestAttribute Actor actor, @PathVariable @Positive Long id) {
        return ok(management.effectiveRole(actor, id));
    }
    @GetMapping("/systems/{code}/role-hierarchy")
    public Object getRoleHierarchy(@RequestAttribute Actor actor, @PathVariable String code) {
        return ok(management.hierarchy(actor, code));
    }
    @PostMapping("/roles/{seniorId}/inherits/{juniorId}")
    public Object addRoleInheritance(@RequestAttribute Actor actor,
        @PathVariable @Positive Long seniorId, @PathVariable @Positive Long juniorId) {
        management.inherit(actor, seniorId, juniorId, true);
        return ok(null);
    }
    @DeleteMapping("/roles/{seniorId}/inherits/{juniorId}")
    public Object deleteRoleInheritance(@RequestAttribute Actor actor,
        @PathVariable @Positive Long seniorId, @PathVariable @Positive Long juniorId) {
        management.inherit(actor, seniorId, juniorId, false);
        return ok(null);
    }
    @GetMapping("/users/{id}/role-assignments")
    public Object listUserRoleAssignments(@RequestAttribute Actor actor,
        @PathVariable @Positive Long id, @RequestParam(required = false) String systemCode) {
        return ok(management.assignments(actor, id, systemCode));
    }
    @PostMapping("/users/{id}/role-assignments")
    public Object createUserRoleAssignment(@RequestAttribute Actor actor,
        @PathVariable @Positive Long id, @Valid @RequestBody Inputs.Assignment input) {
        return ok(management.assign(actor, id, input.roleId()));
    }
    @DeleteMapping("/users/{id}/role-assignments/{assignmentId}")
    public Object deleteUserRoleAssignment(@RequestAttribute Actor actor,
        @PathVariable @Positive Long id, @PathVariable @Positive Long assignmentId) {
        management.unassign(actor, id, assignmentId);
        return ok(null);
    }
    @GetMapping("/users/{id}/systems/{code}/permissions")
    public Object getUserEffectivePermissions(
        @RequestAttribute Actor actor, @PathVariable @Positive Long id, @PathVariable String code) {
        return ok(management.effectiveUser(actor, id, code));
    }
    @GetMapping("/permissions")
    public Object listPermissions(@RequestAttribute Actor actor,
        @RequestParam(required = false) String systemCode,
        @RequestParam(required = false) String kind, @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ok(management.permissions(actor, systemCode, kind, keyword, page, pageSize));
    }
    @PostMapping("/permissions")
    public Object createPermission(
        @RequestAttribute Actor actor, @Valid @RequestBody Inputs.PermissionCreate input) {
        return ok(management.createPermission(actor, input));
    }
    @PutMapping("/permissions/{id}/status")
    public Object setPermissionStatus(@RequestAttribute Actor actor,
        @PathVariable @Positive Long id, @Valid @RequestBody Inputs.State input) {
        return ok(management.permissionStatus(actor, id, input.status()));
    }
    @GetMapping("/systems/{code}/ui-resources")
    public Object getUiResources(@RequestAttribute Actor actor, @PathVariable String code) {
        return ok(resources.ui(actor, code));
    }
    @PostMapping("/systems/{code}/ui-resources")
    public Object createUiResource(@RequestAttribute Actor actor, @PathVariable String code,
        @Valid @RequestBody Inputs.Ui input) {
        return ok(resources.saveUi(actor, code, null, input));
    }
    @PatchMapping("/ui-resources/{id}")
    public Object updateUiResource(@RequestAttribute Actor actor, @PathVariable @Positive Long id,
        @Valid @RequestBody Inputs.UiPatch input) {
        return ok(resources.patchUi(actor, id, input));
    }
    @DeleteMapping("/ui-resources/{id}")
    public Object deleteUiResource(@RequestAttribute Actor actor, @PathVariable @Positive Long id) {
        resources.deleteUi(actor, id);
        return ok(null);
    }
    @GetMapping("/menus/me")
    public Object getMyMenus(@RequestAttribute Actor actor) {
        return ok(resources.menus(actor));
    }
    @GetMapping("/systems/{code}/api-operations")
    public Object listApiOperations(@RequestAttribute Actor actor, @PathVariable String code,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ok(resources.apis(actor, code, keyword, page, pageSize));
    }
    @PostMapping("/systems/{code}/api-operations")
    public Object createApiOperation(@RequestAttribute Actor actor, @PathVariable String code,
        @Valid @RequestBody Inputs.Api input) {
        return ok(resources.saveApi(actor, code, null, input));
    }
    @PatchMapping("/api-operations/{id}")
    public Object updateApiOperation(@RequestAttribute Actor actor, @PathVariable @Positive Long id,
        @Valid @RequestBody Inputs.ApiPatch input) {
        return ok(resources.patchApi(actor, id, input));
    }
    @GetMapping("/systems/{code}/ui-api-mappings")
    public Object getUiApiMappings(@RequestAttribute Actor actor, @PathVariable String code) {
        return ok(resources.mappings(actor, code));
    }
    @PutMapping("/systems/{code}/ui-api-mappings")
    public Object replaceUiApiMappings(@RequestAttribute Actor actor, @PathVariable String code,
        @Valid @RequestBody Inputs.Mappings input) {
        resources.replaceMappings(actor, code, input);
        return ok(null);
    }
    @PostMapping("/authorization/check")
    public Object checkAuthorization(
        @RequestAttribute Actor actor, @Valid @RequestBody Inputs.Check input) {
        return ok(resources.check(actor, input, false));
    }
    @PostMapping("/authorization/explain")
    public Object explainAuthorization(
        @RequestAttribute Actor actor, @Valid @RequestBody Inputs.Check input) {
        return ok(resources.check(actor, input, true));
    }
    @GetMapping("/audit-logs")
    public Object queryAuditLogs(@RequestAttribute Actor actor,
        @RequestParam(required = false) String systemCode,
        @RequestParam(name = "actor", required = false) String actorName,
        @RequestParam(required = false) Long actorId, @RequestParam(required = false) String target,
        @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return ok(audit.query(
            actor, false, null, systemCode, actorName, actorId, target, from, to, page, pageSize));
    }
    @GetMapping("/dashboard")
    public Object getDashboard(@RequestAttribute Actor actor) {
        return ok(management.dashboard(actor));
    }
}
