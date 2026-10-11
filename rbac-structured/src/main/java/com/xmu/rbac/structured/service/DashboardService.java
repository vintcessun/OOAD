package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.StatsDtos.DashboardView;
import com.xmu.rbac.structured.dto.StatsDtos.Heatmap;
import com.xmu.rbac.structured.entity.Action;
import com.xmu.rbac.structured.entity.SoftwareSystem;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.mapper.ActionMapper;
import com.xmu.rbac.structured.mapper.AuditLogMapper;
import com.xmu.rbac.structured.mapper.PermissionMapper;
import com.xmu.rbac.structured.mapper.RoleMapper;
import com.xmu.rbac.structured.mapper.SoftwareSystemMapper;
import com.xmu.rbac.structured.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 7.0 Dashboard 与权限使用热力图。全部从真实数据统计：用户、角色、权限三张表，
 * 以及审计日志里的鉴权事件（AUTH_SUCCESS / AUTH_FAILED）。统计口径见 docs/05-ui.md §3.1。
 */
@Service
public class DashboardService {

    static final List<String> AUTHZ_EVENTS = List.of("AUTH_SUCCESS", "AUTH_FAILED");
    static final int QPS_WINDOW_SECONDS = 60;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final AuditLogMapper auditLogMapper;
    private final SoftwareSystemMapper systemMapper;
    private final ActionMapper actionMapper;
    private final Clock clock;

    public DashboardService(UserMapper userMapper, RoleMapper roleMapper, PermissionMapper permissionMapper,
                            AuditLogMapper auditLogMapper, SoftwareSystemMapper systemMapper,
                            ActionMapper actionMapper, Clock clock) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.auditLogMapper = auditLogMapper;
        this.systemMapper = systemMapper;
        this.actionMapper = actionMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardView metrics() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime today = now.toLocalDate().atStartOfDay();

        long total = auditLogMapper.countByEventTypeInAndOccurredAtGreaterThanEqual(AUTHZ_EVENTS, today);
        long success = auditLogMapper.countByEventTypeAndOccurredAtGreaterThanEqual("AUTH_SUCCESS", today);
        long lastMinute = auditLogMapper.countByEventTypeInAndOccurredAtGreaterThanEqual(AUTHZ_EVENTS,
                now.minusSeconds(QPS_WINDOW_SECONDS));
        long withLatency = auditLogMapper.countAuthzWithLatency(today);
        long withCache = auditLogMapper.countAuthzWithCacheInfo(today);
        long hits = auditLogMapper.countAuthzCacheHits(today);

        return new DashboardView(
                userMapper.countByStatusNot(User.DELETED),
                roleMapper.count(),
                permissionMapper.count(),
                total,
                ratio(success, total),
                ratio(total - success, total),
                percentileMs(today, withLatency, 0.95),
                percentileMs(today, withLatency, 0.99),
                (double) lastMinute / QPS_WINDOW_SECONDS,
                ratio(hits, withCache),
                auditLogMapper.countAbnormal(today),
                now);
    }

    /** 第 p 百分位：按耗时排序后取第 ceil(p·n) 个（最近秩法）。 */
    private double percentileMs(LocalDateTime from, long n, double p) {
        if (n == 0) {
            return 0;
        }
        long rank = percentileOffset(n, p);
        Integer us = auditLogMapper.findLatencyAt(from, rank);
        return us == null ? 0 : us / 1000.0;
    }

    static long percentileOffset(long n, double p) {
        return Math.max(0, (long) Math.ceil(p * n) - 1);
    }

    static double ratio(long a, long b) {
        return b == 0 ? 0 : (double) a / b;
    }

    /**
     * 权限使用热力图：横轴为操作，纵轴为业务系统，格子为时间段内成功鉴权的次数。
     * from、to 为空时取最近 7 天。
     */
    @Transactional(readOnly = true)
    public Heatmap heatmap(LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to == null ? LocalDateTime.now(clock) : to;
        LocalDateTime start = from == null ? end.minusDays(7) : from;
        if (!start.isBefore(end)) {
            throw BizException.badRequest("from 必须早于 to");
        }
        List<SoftwareSystem> systems = systemMapper.findAllByOrderBySortOrderAscIdAsc();
        List<Action> actions = actionMapper.findAllByOrderBySortOrderAsc();
        Map<String, Integer> sysIndex = new HashMap<>();
        Map<String, Integer> actIndex = new HashMap<>();
        for (int i = 0; i < systems.size(); i++) {
            sysIndex.put(systems.get(i).systemCode, i);
        }
        for (int i = 0; i < actions.size(); i++) {
            actIndex.put(actions.get(i).actionCode, i);
        }
        List<long[]> cells = new ArrayList<>();
        for (Object[] row : auditLogMapper.heatmap(start, end)) {
            Integer y = sysIndex.get((String) row[0]);
            Integer x = actIndex.get((String) row[1]);
            if (x != null && y != null) {
                cells.add(new long[]{x, y, ((Number) row[2]).longValue()});
            }
        }
        return new Heatmap(start, end, actions.stream().map(a -> a.actionName).toList(),
                systems.stream().map(s -> s.systemName).toList(), cells);
    }
}
