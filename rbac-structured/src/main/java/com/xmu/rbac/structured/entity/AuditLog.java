package com.xmu.rbac.structured.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 谁、什么时候、在哪里、对谁、做了什么、结果如何、为什么。 */
@Entity
@Table(name = "sys_audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public LocalDateTime occurredAt;
    public Long actorId;
    public String actorName;
    public String clientIp;
    public String eventType;
    public String targetType;
    public String targetId;
    public String targetName;
    public String detail;
    public String result;
    public String reason;
    public String permissionCode;
    public Integer latencyUs;
    public Boolean cacheHit;
}
