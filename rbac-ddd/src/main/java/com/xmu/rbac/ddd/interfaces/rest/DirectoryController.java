package com.xmu.rbac.ddd.interfaces.rest;

import com.xmu.rbac.ddd.application.*;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;
import com.xmu.rbac.ddd.domain.port.DirectoryRepository.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 人员、单一部门和系统管理的独立接口。 */
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping("/api/v1")
public class DirectoryController {
    private final DirectoryApplicationService directory;
    private final AuthorizationApplicationService authorization;
    private final RestSupport rest;

    public DirectoryController(
            DirectoryApplicationService directory,
            AuthorizationApplicationService authorization,
            RestSupport rest) {
        this.directory = directory;
        this.authorization = authorization;
        this.rest = rest;
    }

    public record Named(@NotBlank @Size(max = 128) String name) {}

    public record CreateSystem(
            @NotBlank @Size(max = 64) String code, @NotBlank @Size(max = 128) String name) {}

    public record CreateDepartment(
            Long parentId,
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 128) String name) {}

    public record RoleIds(@NotNull Set<@Positive Long> roleIds) {}

    public record CreateUser(
            @NotBlank @Size(max = 128) String username,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 64) String no,
            @Size(max = 32) String phone,
            @NotNull @Positive Long departmentId,
            @NotBlank @Size(min = 8, max = 256) String password) {}

    public record PatchUser(
            @Size(min = 1, max = 128) String name,
            @Size(max = 64) String no,
            @Size(max = 32) String phone,
            Long version) {}

    public record UserStatus(@NotNull User.Status status) {}

    public record Transfer(@Positive long departmentId, Long version) {}

    public record Assign(@Positive long roleId) {}

    @GetMapping("/systems")
    public Object systems(@RequestHeader(value = "Authorization", required = false) String b) {
        return RestSupport.ok(
                directory.systems(rest.principal(b)).stream().map(DirectoryViews::system).toList());
    }

    @PostMapping("/systems")
    public Object createSystem(
            @RequestHeader(value = "Authorization", required = false) String b,
            @Valid @RequestBody CreateSystem i) {
        return RestSupport.ok(
                DirectoryViews.system(
                        directory.system(rest.principal(b), null, i.code(), i.name(), null)));
    }

    @PatchMapping("/systems/{id}")
    public Object patchSystem(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @Valid @RequestBody Named i) {
        return RestSupport.ok(
                DirectoryViews.system(
                        directory.system(rest.principal(b), id, null, i.name(), null)));
    }

    @PutMapping("/systems/{id}/status")
    public Object systemStatus(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @Valid @RequestBody RoleController.Status i) {
        return RestSupport.ok(
                DirectoryViews.system(
                        directory.system(rest.principal(b), id, null, null, i.status())));
    }

    @GetMapping("/departments/tree")
    public Object departments(@RequestHeader(value = "Authorization", required = false) String b) {
        return RestSupport.ok(DirectoryViews.departments(directory.departments(rest.principal(b))));
    }

    @PostMapping("/departments")
    public Object createDepartment(
            @RequestHeader(value = "Authorization", required = false) String b,
            @Valid @RequestBody CreateDepartment i) {
        return RestSupport.ok(
                DirectoryViews.departments(
                                List.of(
                                        directory.department(
                                                rest.principal(b),
                                                null,
                                                i.parentId(),
                                                i.code(),
                                                i.name())))
                        .getFirst());
    }

    @PatchMapping("/departments/{id}")
    public Object patchDepartment(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @Valid @RequestBody Named i) {
        return RestSupport.ok(
                DirectoryViews.departments(
                                List.of(
                                        directory.department(
                                                rest.principal(b), id, null, null, i.name())))
                        .getFirst());
    }

    @PutMapping("/departments/{id}/status")
    public Object setDepartmentStatus(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable @Positive long id,
            @Valid @RequestBody RoleController.Status i) {
        return RestSupport.ok(DirectoryViews.departments(
                        List.of(directory.departmentStatus(rest.principal(b), id, i.status())))
                .getFirst());
    }

    @DeleteMapping("/departments/{id}")
    public Object deleteDepartment(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id) {
        directory.deleteDepartment(rest.principal(b), id);
        return RestSupport.ok(null);
    }

    @GetMapping("/departments/{id}/assignable-roles")
    public Object allowed(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @RequestParam(required = false) String systemCode) {
        return RestSupport.ok(
                directory.allowed(rest.principal(b), id, systemCode).stream()
                        .map(RestSupport::role)
                        .toList());
    }

    @PutMapping("/departments/{id}/assignable-roles")
    public Object allowed(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @Valid @RequestBody RoleIds i) {
        directory.allowed(rest.principal(b), id, i.roleIds());
        return RestSupport.ok(null);
    }

    @GetMapping("/users")
    public Object users(
            @RequestHeader(value = "Authorization", required = false) String b,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        AuthorizationRepository.Page<Profile> result =
                directory.users(rest.principal(b), keyword, departmentId, status, page, pageSize);
        return RestSupport.ok(
                Map.of(
                        "page",
                        page,
                        "pageSize",
                        pageSize,
                        "total",
                        result.total(),
                        "items",
                        result.items().stream().map(DirectoryViews::user).toList()));
    }

    @PostMapping("/users")
    public Object createUser(
            @RequestHeader(value = "Authorization", required = false) String b,
            @Valid @RequestBody CreateUser i) {
        return RestSupport.ok(
                DirectoryViews.user(
                        directory.createUser(
                                rest.principal(b),
                                i.username(),
                                i.name(),
                                i.no(),
                                i.phone(),
                                i.departmentId(),
                                i.password())));
    }

    @GetMapping("/users/{id}")
    public Object user(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id) {
        return RestSupport.ok(DirectoryViews.user(directory.user(rest.principal(b), id)));
    }

    @PatchMapping("/users/{id}")
    public Object patchUser(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @Valid @RequestBody com.xmu.rbac.ddd.interfaces.rest.dto.UserUpdateRequest i) {
        DomainException.require(
                !i.provided("name") || i.getName() != null, 400, "INVALID_USER_NAME");
        return RestSupport.ok(
                DirectoryViews.user(
                        directory.patchUser(
                                rest.principal(b),
                                id,
                                cn.edu.xmu.clonefactory.util.CloneFactory.copy(
                                        new UserUpdateCommand(), i),
                                i.provided("no"),
                                i.provided("phone"))));
    }

    @PutMapping("/users/{id}/status")
    public Object userStatus(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @Valid @RequestBody UserStatus i) {
        return RestSupport.ok(
                DirectoryViews.user(directory.statusUser(rest.principal(b), id, i.status())));
    }

    @DeleteMapping("/users/{id}")
    public Object deleteUser(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id) {
        directory.statusUser(rest.principal(b), id, User.Status.DELETED);
        return RestSupport.ok(null);
    }

    @PutMapping("/users/{id}/department")
    public Object transfer(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @Valid @RequestBody Transfer i) {
        return RestSupport.ok(
                DirectoryViews.user(
                        directory.transfer(rest.principal(b), id, i.departmentId(), i.version())));
    }

    @GetMapping("/users/{id}/role-assignments")
    public Object assignments(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @RequestParam(required = false) String systemCode) {
        return RestSupport.ok(directory.assignments(rest.principal(b), id, systemCode));
    }

    @PostMapping("/users/{id}/role-assignments")
    public Object assign(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @Valid @RequestBody Assign i) {
        return RestSupport.ok(directory.assign(rest.principal(b), id, i.roleId()));
    }

    @DeleteMapping("/users/{id}/role-assignments/{assignmentId}")
    public Object unassign(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @PathVariable long assignmentId) {
        directory.unassign(rest.principal(b), id, assignmentId);
        return RestSupport.ok(null);
    }

    @GetMapping("/users/{id}/systems/{code}/permissions")
    public Object effective(
            @RequestHeader(value = "Authorization", required = false) String b,
            @PathVariable long id,
            @PathVariable String code) {
        return RestSupport.ok(
                RestSupport.effective(directory.effective(rest.principal(b), id, code)));
    }

    @GetMapping("/me")
    public Object self(@RequestHeader(value = "Authorization", required = false) String b) {
        TokenProvider.Principal p = rest.principal(b);
        Profile user = directory.self(p);
        return RestSupport.ok(
                Map.of(
                        "user",
                        DirectoryViews.user(user),
                        "effective",
                        RestSupport.effective(authorization.effective(user.id(), p.systemCode()))));
    }

    @GetMapping("/dashboard")
    public Object dashboard(@RequestHeader(value = "Authorization", required = false) String b) {
        return RestSupport.ok(directory.dashboard(rest.principal(b)));
    }
}
