package com.xmu.rbac.structured.controller;

import com.xmu.rbac.structured.dto.PageResult;
import com.xmu.rbac.structured.dto.StatsDtos.DashboardView;
import com.xmu.rbac.structured.dto.StatsDtos.Heatmap;
import com.xmu.rbac.structured.entity.AuditLog;
import com.xmu.rbac.structured.service.AuditService;
import com.xmu.rbac.structured.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 审计日志、Dashboard、权限使用热力图。 */
@RestController
public class StatsController {

    private final AuditService audit;
    private final DashboardService dashboard;

    public StatsController(AuditService audit, DashboardService dashboard) {
        this.audit = audit;
        this.dashboard = dashboard;
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<AuditLog>> auditLogs(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResult.of(audit.search(eventType, actor, target, result, from, to, page, size),
                a -> a));
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardView> dashboard() {
        return ApiResponse.ok(dashboard.metrics());
    }

    @GetMapping("/permissions/heatmap")
    public ApiResponse<Heatmap> heatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ApiResponse.ok(dashboard.heatmap(from, to));
    }
}
