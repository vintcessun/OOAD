package com.xmu.rbac.structured.controller;

import com.xmu.rbac.structured.dto.AssignmentRow;
import com.xmu.rbac.structured.dto.BatchDtos.BatchResult;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.dto.PageResult;
import com.xmu.rbac.structured.dto.UserDtos.Assign;
import com.xmu.rbac.structured.dto.UserDtos.BatchStatus;
import com.xmu.rbac.structured.dto.UserDtos.ChangeStatus;
import com.xmu.rbac.structured.dto.UserDtos.CreateUser;
import com.xmu.rbac.structured.dto.UserDtos.EffectivePermission;
import com.xmu.rbac.structured.dto.UserDtos.UpdateUser;
import com.xmu.rbac.structured.dto.UserDtos.UserDetail;
import com.xmu.rbac.structured.dto.UserDtos.UserView;
import com.xmu.rbac.structured.service.AssignmentService;
import com.xmu.rbac.structured.service.UserManagementService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 用户管理与用户的角色指派。 */
@RestController
public class UserController {

    private static final String OP = PermissionInterceptor.ATTR_OPERATOR;

    private final UserManagementService users;
    private final AssignmentService assignments;

    public UserController(UserManagementService users, AssignmentService assignments) {
        this.users = users;
        this.assignments = assignments;
    }

    @GetMapping("/users")
    public ApiResponse<PageResult<UserView>> list(@RequestAttribute(OP) Operator op,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Long departmentId,
                                                  @RequestParam(defaultValue = "true") boolean includeDescendants,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String userType,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(users.list(op, keyword, departmentId, includeDescendants, status, userType, page, size));
    }

    @PostMapping("/users")
    public ApiResponse<UserView> create(@RequestAttribute(OP) Operator op, @RequestBody CreateUser req) {
        return ApiResponse.ok(users.create(op, req));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserDetail> detail(@RequestAttribute(OP) Operator op, @PathVariable Long id) {
        return ApiResponse.ok(users.detail(op, id));
    }

    @PatchMapping("/users/{id}")
    public ApiResponse<UserView> update(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                        @RequestBody UpdateUser req) {
        return ApiResponse.ok(users.update(op, id, req));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<UserView> changeStatus(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                              @RequestBody ChangeStatus req) {
        return ApiResponse.ok(users.changeStatus(op, id, req));
    }

    @DeleteMapping("/users/{id}")
    public ApiResponse<Void> delete(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                    @RequestParam(required = false) String reason) {
        users.delete(op, id, reason);
        return ApiResponse.ok(null);
    }

    @PostMapping("/users/batch-status")
    public ApiResponse<BatchResult> batchStatus(@RequestAttribute(OP) Operator op, @RequestBody BatchStatus req) {
        return ApiResponse.ok(users.batchStatus(op, req));
    }

    @GetMapping("/users/{id}/roles")
    public ApiResponse<List<AssignmentRow>> roles(@RequestAttribute(OP) Operator op, @PathVariable Long id) {
        return ApiResponse.ok(assignments.listOfUser(op, id));
    }

    @PostMapping("/users/{id}/roles")
    public ApiResponse<AssignmentRow> assign(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                             @RequestBody Assign req) {
        return ApiResponse.ok(assignments.assign(op, id, req));
    }

    @DeleteMapping("/users/{id}/roles/{assignmentId}")
    public ApiResponse<Void> revoke(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                    @PathVariable Long assignmentId,
                                    @RequestParam(required = false) String reason) {
        assignments.revoke(op, id, assignmentId, reason);
        return ApiResponse.ok(null);
    }

    @GetMapping("/users/{id}/permissions")
    public ApiResponse<List<EffectivePermission>> permissions(@RequestAttribute(OP) Operator op,
                                                              @PathVariable Long id) {
        return ApiResponse.ok(users.detail(op, id).permissions());
    }
}
