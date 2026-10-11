package com.xmu.rbac.structured.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sys_department")
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long companyId;
    /** 根节点为空 */
    public Long parentId;
    public String deptName;
    /** 物化路径 /祖先id/.../本id/ */
    public String path;
    public int level;
    public String managerName;
    /** 只是组织信息，不产生管理权限 */
    public Long managerUserId;
    public int sortOrder;
    @Column(insertable = false, updatable = false)
    public LocalDateTime createdAt;
}
