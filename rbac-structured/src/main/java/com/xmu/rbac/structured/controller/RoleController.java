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

    // 操作者来自拦截器认证后写入的请求属性，不是前端请求体中的用户 ID。
    private static final String OP = PermissionInterceptor.ATTR_OPERATOR;

    private final RoleManagementService roles;
    private final AssignmentService assignments;

    public RoleController(RoleManagementService roles, AssignmentService assignments) {
        this.roles = roles;
        this.assignments = assignments;
    }

    /** D01：角色列表搜索，也供指派弹窗查询启用角色；返回可见角色及用户数、权限数，不分页。 */
    @GetMapping("/roles")
    public ApiResponse<List<RoleView>> list(@RequestAttribute(OP) Operator op,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status) {
        return ApiResponse.ok(roles.list(op, keyword, status));
    }

    /** D02：新增角色表单；companyId 不填表示全局角色，具体创建资格由 Service 校验。 */
    @PostMapping("/roles")
    public ApiResponse<RoleView> create(@RequestAttribute(OP) Operator op, @RequestBody CreateRole req) {
        return ApiResponse.ok(roles.create(op, req));
    }

    /** D03：点击角色名称/详情；一次返回基本信息和全部角色权限，持有者另走 users 分页接口。 */
    @GetMapping("/roles/{id}")
    public ApiResponse<RoleDetail> detail(@RequestAttribute(OP) Operator op, @PathVariable Long id) {
        return ApiResponse.ok(roles.detail(op, id));
    }

    /** D04：编辑名称/描述和启用/停用共用 PATCH；请求 DTO 不包含角色编码、所属公司。 */
    @PatchMapping("/roles/{id}")
    public ApiResponse<RoleView> update(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                        @RequestBody UpdateRole req) {
        return ApiResponse.ok(roles.update(op, id, req));
    }

    /** D05：删除共享角色；Service 拒绝内置角色和仍有指派的角色，成功返回 data=null。 */
    @DeleteMapping("/roles/{id}")
    public ApiResponse<Void> delete(@RequestAttribute(OP) Operator op, @PathVariable Long id) {
        roles.delete(op, id);
        return ApiResponse.ok(null);
    }

    /** D06：详情“用户”页签；page 从 0 开始，total 是可见指派条数，同一用户可能占多条。 */
    @GetMapping("/roles/{id}/users")
    public ApiResponse<PageResult<AssignmentRow>> users(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(roles.users(op, id, page, size));
    }

    /**
     * D07：授予权限弹窗跨页勾选后提交 permissionIds，含义是追加角色权限，不是替换整个集合。
     * Service 校验公司级管理权与授权上界，再跳过重复关联；返回更新后的角色详情。
     */
    @PostMapping("/roles/{id}/permissions")
    public ApiResponse<RoleDetail> grant(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                         @RequestBody GrantPermissions req) {
        return ApiResponse.ok(roles.grant(op, id, req.permissionIds()));
    }

    /** D08：回收一个角色权限；permissionId 是权限定义 ID，删除的是关联，不是权限定义本身。 */
    @DeleteMapping("/roles/{id}/permissions/{permissionId}")
    public ApiResponse<Void> ungrant(@RequestAttribute(OP) Operator op, @PathVariable Long id,
                                     @PathVariable Long permissionId) {
        roles.ungrant(op, id, permissionId);
        return ApiResponse.ok(null);
    }

    /**
     * C 交叉链路：用户/组织页面批量分配角色，写用户角色指派，复用 D 定义的角色与角色权限。
     * HTTP 200 只表示批量处理完成，页面还须读取 committed、failures，不能直接认为全部成功。
     */
    @PostMapping("/assignments/batch")
    public ApiResponse<BatchResult> batchAssign(@RequestAttribute(OP) Operator op, @RequestBody BatchAssign req) {
        return ApiResponse.ok(assignments.batchAssign(op, req));
    }

    /** C 交叉链路：批量回收用户持有的角色；不删除角色定义，也不回收该角色对其他用户的指派。 */
    @PostMapping("/assignments/batch-revoke")
    public ApiResponse<BatchResult> batchRevoke(@RequestAttribute(OP) Operator op, @RequestBody BatchAssign req) {
        return ApiResponse.ok(assignments.batchRevoke(op, req));
    }
}
