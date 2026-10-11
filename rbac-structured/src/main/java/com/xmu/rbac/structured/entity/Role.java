package com.xmu.rbac.structured.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sys_role")
public class Role {
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    /** 空 = 全局角色 */
    public Long companyId;
    public String roleCode;
    public String roleName;
    public String description;
    public String status = ENABLED;
    public boolean builtin;
    @Column(insertable = false, updatable = false)
    public LocalDateTime createdAt;
    @Column(insertable = false, updatable = false)
    public LocalDateTime updatedAt;
}
