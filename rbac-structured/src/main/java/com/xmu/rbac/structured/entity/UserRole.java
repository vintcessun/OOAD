package com.xmu.rbac.structured.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 一次指派：用户在 scopeDeptId 及其下级部门持有角色。 */
@Entity
@Table(name = "sys_user_role")
public class UserRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long userId;
    public Long roleId;
    /** 适用部门，覆盖其全部下级部门 */
    public Long scopeDeptId;
    /** 只用于审计，不构成授权依赖 */
    public Long grantedBy;
    public LocalDateTime grantedAt;
    public String reason;
}
