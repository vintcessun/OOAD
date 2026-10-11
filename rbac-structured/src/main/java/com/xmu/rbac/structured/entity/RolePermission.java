package com.xmu.rbac.structured.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sys_role_permission")
public class RolePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long roleId;
    public Long permissionId;
    /** 权限清单中的「本人」：随鉴权结果返回，由业务系统按记录归属过滤 */
    public boolean selfOnly;
    public Long grantedBy;
    public LocalDateTime grantedAt;
}
