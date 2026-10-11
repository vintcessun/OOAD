package com.xmu.rbac.structured.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 角色管理的请求与响应。 */
public final class RoleDtos {

    private RoleDtos() {
    }

    /** 角色列表：角色名称、角色编码、用户数、权限数、状态。 */
    public record RoleView(Long id, String roleCode, String roleName, String description, String status,
                           boolean builtin, Long companyId, long userCount, long permissionCount) {
    }

    /** companyId 为空表示全局角色（只有超级管理员可以创建）。 */
    public record CreateRole(String roleCode, String roleName, String description, Long companyId) {
    }

    public record UpdateRole(String roleName, String description, String status) {
    }

    public record RolePermissionView(Long permissionId, String permissionCode, String resourceName,
                                     String actionName, boolean selfOnly, LocalDateTime grantedAt) {
    }

    /** 角色详情：基本信息 + 权限。用户另走分页接口。 */
    public record RoleDetail(RoleView role, List<RolePermissionView> permissions) {
    }

    public record GrantPermissions(List<Long> permissionIds) {
    }
}
