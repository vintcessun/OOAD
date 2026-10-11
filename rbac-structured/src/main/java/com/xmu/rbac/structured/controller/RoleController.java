package com.xmu.rbac.structured.controller;

import com.xmu.rbac.structured.dto.AssignmentRow;
import com.xmu.rbac.structured.dto.BatchDtos.BatchAssign;
import com.xmu.rbac.structured.dto.BatchDtos.BatchResult;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.dto.PageResult;
import com.xmu.rbac.structured.dto.RoleDtos.CreateRole;
import com.xmu.rbac.structured.dto.RoleDtos.GrantPermissions;
import com.xmu.rbac.structured.dto.RoleDtos.RoleDetail;
import com.xmu.rbac.structured.dto.RoleDtos.RoleView;
import com.xmu.rbac.structured.dto.RoleDtos.UpdateRole;
import com.xmu.rbac.structured.service.AssignmentService;
import com.xmu.rbac.structured.service.RoleManagementService;
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

/** 角色管理、角色权限，以及按分公司 / 部门 / 员工的批量授权。 */
@RestController
public class RoleController {

    private static final String OP = PermissionInterceptor.ATTR_OPERATOR;

    private final RoleManagementService roles;
    private final AssignmentService assignments;

    public RoleController(RoleManagementService roles, AssignmentService assignments) {
        this.roles = roles;
        this.assignments = assignments;
    }

    @GetMapping("/roles")
    public ApiResponse<List<RoleView>> list(@RequestAttribute(OP) Operator op,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status) {
        return ApiResponse.ok(roles.list(op, keyword, status));
    }

    @PostMapping("/roles")
    public ApiResponse<RoleView> create(@RequestAttribute(OP) Operator op, @RequestBody CreateRole req) {
        return ApiResponse.ok(roles.create(op, req));
    }

    @GetMapping("/roles/{id}")
    public ApiResponse<RoleDetail> detail(@RequestAttribute(OP) Operator op, @PathVariable Long id) {
        return ApiResponse.ok(roles.detail(op, id));
    }

    @PatchMapping("/roles/{id}")
    public ApiResponse<RoleView> update(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                        @RequestBody UpdateRole req) {
        return ApiResponse.ok(roles.update(op, id, req));
    }

    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> delete(@RequestAttribute(OP) Operator op, @PathVariable Long id) {
        roles.delete(op, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/roles/{id}/users")
    public ApiResponse<PageResult<AssignmentRow>> users(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(roles.users(op, id, page, size));
    }

    @PostMapping("/roles/{id}/permissions")
    public ApiResponse<RoleDetail> grant(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                         @RequestBody GrantPermissions req) {
        return ApiResponse.ok(roles.grant(op, id, req.permissionIds()));
    }

    @DeleteMapping("/roles/{id}/permissions/{permissionId}")
    public ApiResponse<Void> ungrant(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                     @PathVariable Long permissionId) {
        roles.ungrant(op, id, permissionId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/assignments/batch")
    public ApiResponse<BatchResult> batchAssign(@RequestAttribute(OP) Operator op, @RequestBody BatchAssign req) {
        return ApiResponse.ok(assignments.batchAssign(op, req));
    }

    @PostMapping("/assignments/batch-revoke")
    public ApiResponse<BatchResult> batchRevoke(@RequestAttribute(OP) Operator op, @RequestBody BatchAssign req) {
        return ApiResponse.ok(assignments.batchRevoke(op, req));
    }
}
