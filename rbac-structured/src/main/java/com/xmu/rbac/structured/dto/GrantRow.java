package com.xmu.rbac.structured.dto;

/**
 * 判定用的一条授权依据：用户经由哪次指派（角色 + 适用部门）获得了哪个权限码。
 * 作用域跟着指派记录走，不能先把所有角色的权限并成一个集合再单独判断部门
 * ——那样会拼出「A 角色的权限 + B 角色的部门」这种实际不存在的组合（docs/06 §5.2）。
 */
public record GrantRow(String permissionCode,
                       Long assignmentId,
                       Long roleId,
                       String roleCode,
                       String roleName,
                       Long scopeDeptId,
                       String scopeDeptName,
                       String scopePath,
                       Long scopeCompanyId,
                       boolean selfOnly) {
}
