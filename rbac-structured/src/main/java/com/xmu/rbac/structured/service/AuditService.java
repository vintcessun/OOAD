package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.Decision;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.entity.AuditLog;
import com.xmu.rbac.structured.mapper.AuditLogMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 6.0 审计：记录「谁、什么时候、在哪里、对谁、做了什么、结果如何、为什么」。
 * 写入用独立事务：业务操作因拒绝或失败回滚时，审计记录照样留下。
 */
@Service
public class AuditService {

    public static final String SUCCESS = "SUCCESS";
    public static final String DENIED = "DENIED";
    public static final String FAILED = "FAILED";

    private final AuditLogMapper auditLogMapper;
    private final Clock clock;

    public AuditService(AuditLogMapper auditLogMapper, Clock clock) {
        this.auditLogMapper = auditLogMapper;
        this.clock = clock;
    }

    /** 6.1 记录管理操作、登录登出。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Operator op, String eventType, String targetType, Object targetId, String targetName,
                       String detail, String result, String reason) {
        AuditLog a = new AuditLog();
        a.occurredAt = LocalDateTime.now(clock);
        if (op != null) {
            a.actorId = op.userId();
            a.actorName = op.username();
            a.clientIp = op.clientIp();
        }
        a.eventType = eventType;
        a.targetType = targetType;
        a.targetId = targetId == null ? null : String.valueOf(targetId);
        a.targetName = truncate(targetName, 128);
        a.detail = truncate(detail, 1000);
        a.result = result;
        a.reason = truncate(reason, 255);
        auditLogMapper.save(a);
    }

    /**
     * 6.2 记录一次鉴权判定（AUTH_SUCCESS / AUTH_FAILED）。
     * 「谁」是被判定的主体；调用方不是本人时（业务系统代用户鉴权），调用方写进 detail。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDecision(Decision d, Operator caller, String via) {
        AuditLog a = new AuditLog();
        a.occurredAt = LocalDateTime.now(clock);
        a.actorId = d.userId();
        a.actorName = d.username();
        a.clientIp = caller == null ? null : caller.clientIp();
        a.eventType = d.allowed() ? "AUTH_SUCCESS" : "AUTH_FAILED";
        if (d.targetDeptId() != null) {
            a.targetType = "DEPARTMENT";
            a.targetId = String.valueOf(d.targetDeptId());
        }
        String callerPart = (caller == null || caller.userId().equals(d.userId())) ? "" : " caller=" + caller.username();
        a.detail = truncate("via=" + via + callerPart, 1000);
        a.result = d.allowed() ? SUCCESS : DENIED;
        a.reason = d.reason();
        a.permissionCode = d.permissionCode();
        a.latencyUs = (int) Math.min(Integer.MAX_VALUE, d.latencyUs());
        a.cacheHit = d.cacheHit();
        auditLogMapper.save(a);
    }

    /** 6.3 审计查询。 */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(String eventType, String actorName, String targetName, String result,
                                 LocalDateTime from, LocalDateTime to, boolean abnormal, int page, int size) {
        return auditLogMapper.search(blankToNull(eventType), blankToNull(actorName), blankToNull(targetName),
                blankToNull(result), from, to, abnormal, PageRequest.of(page, Math.min(Math.max(size, 1), 200)));
    }

    static String truncate(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }

    static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
