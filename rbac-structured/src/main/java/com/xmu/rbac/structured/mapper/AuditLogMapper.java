package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** 审计日志只插入、只查询，没有更新与删除方法。 */
public interface AuditLogMapper extends JpaRepository<AuditLog, Long> {

    @Query(value = """
            select a from AuditLog a
             where (:eventType is null or a.eventType = :eventType)
               and (:actorName is null or a.actorName = :actorName)
               and (:targetName is null or a.targetName like concat('%', :targetName, '%'))
               and (:result is null or a.result = :result)
               and (:from is null or a.occurredAt >= :from)
               and (:to is null or a.occurredAt < :to)
               and (:abnormal = false
                 or (a.eventType = 'LOGIN' and a.result = 'FAILED')
                 or (a.eventType = 'AUTH_FAILED' and a.reason in
                     ('SUBJECT_DISABLED', 'SUBJECT_DELETED', 'SUBJECT_NOT_FOUND')))
             order by a.id desc
            """)
    Page<AuditLog> search(@Param("eventType") String eventType,
                          @Param("actorName") String actorName,
                          @Param("targetName") String targetName,
                          @Param("result") String result,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to,
                          @Param("abnormal") boolean abnormal,
                          Pageable pageable);

    long countByEventTypeInAndOccurredAtGreaterThanEqual(Collection<String> eventTypes, LocalDateTime from);

    long countByEventTypeAndOccurredAtGreaterThanEqual(String eventType, LocalDateTime from);

    @Query("""
            select count(a) from AuditLog a
             where a.eventType in ('AUTH_SUCCESS', 'AUTH_FAILED') and a.cacheHit = true and a.occurredAt >= :from
            """)
    long countAuthzCacheHits(@Param("from") LocalDateTime from);

    @Query("""
            select count(a) from AuditLog a
             where a.eventType in ('AUTH_SUCCESS', 'AUTH_FAILED') and a.cacheHit is not null and a.occurredAt >= :from
            """)
    long countAuthzWithCacheInfo(@Param("from") LocalDateTime from);

    /** 第 offset 小的鉴权耗时（求 P95 / P99）。 */
    @Query(value = """
            SELECT latency_us FROM sys_audit_log
             WHERE event_type IN ('AUTH_SUCCESS', 'AUTH_FAILED') AND occurred_at >= :from AND latency_us IS NOT NULL
             ORDER BY latency_us LIMIT 1 OFFSET :offset
            """, nativeQuery = true)
    Integer findLatencyAt(@Param("from") LocalDateTime from, @Param("offset") long offset);

    @Query(value = """
            SELECT COUNT(*) FROM sys_audit_log
             WHERE event_type IN ('AUTH_SUCCESS', 'AUTH_FAILED') AND occurred_at >= :from AND latency_us IS NOT NULL
            """, nativeQuery = true)
    long countAuthzWithLatency(@Param("from") LocalDateTime from);

    /**
     * 异常访问：登录失败，以及已停用、已删除、已过期或不存在的主体发起的鉴权。
     * 这类请求说明有人在用不该再有效的身份访问系统（docs/05-ui.md §3.1 的统计口径）。
     * 与 {@link #search} 的 abnormal 条件必须一致：Dashboard 点进审计日志看到的就是这些记录。
     */
    @Query("""
            select count(a) from AuditLog a
             where a.occurredAt >= :from
               and ((a.eventType = 'LOGIN' and a.result = 'FAILED')
                 or (a.eventType = 'AUTH_FAILED' and a.reason in
                     ('SUBJECT_DISABLED', 'SUBJECT_DELETED', 'SUBJECT_NOT_FOUND')))
            """)
    long countAbnormal(@Param("from") LocalDateTime from);

    /** 权限使用热力图：[业务系统编码, 操作编码, 次数]，从成功的鉴权事件统计。 */
    @Query(value = """
            SELECT SUBSTRING_INDEX(permission_code, ':', 1) AS system_code,
                   SUBSTRING_INDEX(permission_code, ':', -1) AS action_code,
                   COUNT(*) AS cnt
              FROM sys_audit_log
             WHERE event_type = 'AUTH_SUCCESS' AND permission_code IS NOT NULL
               AND occurred_at >= :from AND occurred_at < :to
             GROUP BY system_code, action_code
            """, nativeQuery = true)
    List<Object[]> heatmap(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
