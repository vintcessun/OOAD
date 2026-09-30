package com.xmu.rbac.structured.data;

/** 角色记录。 */
public record RoleRecord(
        Long id,
        String roleCode,
        String roleName,
        int status,       // 1=启用 0=禁用
        boolean builtin
) {}
