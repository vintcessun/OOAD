package com.xmu.rbac.structured.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "sys_user")
public class User {
    public static final String ACTIVE = "ACTIVE";
    public static final String DISABLED = "DISABLED";
    public static final String DELETED = "DELETED";

    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String TEMPORARY = "TEMPORARY";
    public static final String SYSTEM = "SYSTEM";

    /** 编号小于它的是内部账号（root、业务系统账号），员工与临时用户从它开始自增（V3 迁移） */
    public static final long INTERNAL_ID_LIMIT = 10000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long companyId;
    public Long departmentId;
    public String username;
    public String passwordHash;
    public String realName;
    public String userType = EMPLOYEE;
    public String employeeNo;
    public String phone;
    public String position;
    public String status = ACTIVE;
    public String statusReason;
    public int loginFailCount;
    public LocalDateTime lockedUntil;
    public boolean mustChangePwd;
    /** 修改口令时加一；令牌中的版本号与它不等即失效 */
    public int tokenVersion;
    @Column(insertable = false, updatable = false)
    public LocalDateTime createdAt;
    @Column(insertable = false, updatable = false)
    public LocalDateTime updatedAt;
    public LocalDateTime deletedAt;
}
