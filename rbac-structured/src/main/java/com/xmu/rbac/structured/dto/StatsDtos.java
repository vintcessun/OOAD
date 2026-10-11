package com.xmu.rbac.structured.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 鉴权接口、Dashboard 与热力图。 */
public final class StatsDtos {

    private StatsDtos() {
    }

    /**
     * 鉴权请求。主体用 userId 或 username 指定，都为空表示调用者本人；
     * 权限用 permission（如 oa:m01:f02:approve）或 resource + action 指定。
     * targetDeptId 是被操作数据所属的部门，为空表示不涉及具体部门。
     */
    public record AuthzRequest(Long userId, String username, String permission, String resource, String action,
                               Long targetDeptId) {
    }

    /** Dashboard 的十一项指标，统计口径见 docs/05-ui.md §3.1。延迟单位毫秒，比率为 0–1。 */
    public record DashboardView(long userCount, long roleCount, long permissionCount, long todayAuthzCount,
                                double successRate, double failureRate, double p95LatencyMs, double p99LatencyMs,
                                double qps, double cacheHitRate, long abnormalCount, LocalDateTime generatedAt) {
    }

    /** 热力图：横轴操作，纵轴业务系统；cells 每项为 [操作下标, 系统下标, 次数]。 */
    public record Heatmap(LocalDateTime from, LocalDateTime to, List<String> actions, List<String> systems,
                          List<long[]> cells) {
    }
}
