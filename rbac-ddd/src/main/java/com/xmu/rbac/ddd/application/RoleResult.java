package com.xmu.rbac.ddd.application;

import com.xmu.rbac.ddd.domain.Role;

/** 用于对外映射的角色应用快照。 */
public record RoleResult(
        Long id,
        String systemCode,
        String code,
        String name,
        String description,
        String status,
        Boolean builtin,
        Long version) {
    /** 将领域启用状态转换为对外状态值。 */
    public static RoleResult from(Role role) {
        return new RoleResult(role.id(), role.systemCode(), role.code(), role.name(), role.description(),
                role.enabled() ? "ENABLED" : "DISABLED", role.builtin(), role.version());
    }
}
