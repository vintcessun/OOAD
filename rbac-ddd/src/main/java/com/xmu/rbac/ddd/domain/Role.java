package com.xmu.rbac.ddd.domain;

import java.util.*;

/** 角色聚合：封装启停和直接权限的不变量；持久化仅恢复合法状态。 */
public final class Role {
    private final long id;
    private final String systemCode;
    private final String code;
    private String name;
    private String description;
    private boolean enabled;
    private final boolean builtin;
    private final long version;
    private Set<Long> permissionIds;

    public Role(
            long id,
            String systemCode,
            String code,
            String name,
            String description,
            boolean enabled,
            boolean builtin,
            long version,
            Set<Long> permissionIds) {
        DomainException.require(
                systemCode != null && code != null && name != null, 400, "INVALID_ROLE");
        this.id = id;
        this.systemCode = systemCode;
        this.code = code;
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.builtin = builtin;
        this.version = version;
        this.permissionIds = Set.copyOf(permissionIds);
    }

    public void rename(String name, String description) {
        if (name != null) {
            DomainException.require(!name.isBlank(), 400, "INVALID_ROLE_NAME");
            this.name = name;
        }
        this.description = description;
    }

    public void changeStatus(boolean enabled) {
        this.enabled = enabled;
    }

    public void replacePermissions(Collection<Permission> permissions) {
        Set<Long> ids = new HashSet<>();
        for (Permission p : permissions) {
            DomainException.require(
                    systemCode.equals(p.systemCode()), 400, "CROSS_SYSTEM_PERMISSION");
            ids.add(p.id());
        }
        permissionIds = Set.copyOf(ids);
    }

    public long id() {
        return id;
    }

    public String systemCode() {
        return systemCode;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean builtin() {
        return builtin;
    }

    public long version() {
        return version;
    }

    public Set<Long> permissionIds() {
        return permissionIds;
    }

    /** 超级管理员是独立能力，不等同于直接权限集合是否为空或角色当前是否启用。 */
    public boolean superAdministratorCapability() {
        return systemCode.equals("rbac-center") && code.equals("SUPER_ADMIN");
    }

    public void requireManageable(boolean actorSuperAdministrator) {
        DomainException.require(
                !superAdministratorCapability() || actorSuperAdministrator,
                403,
                "SUPER_ADMIN_PROTECTED");
    }
}
