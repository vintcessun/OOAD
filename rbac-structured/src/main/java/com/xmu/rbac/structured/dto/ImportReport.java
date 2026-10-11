package com.xmu.rbac.structured.dto;

import java.util.List;
import java.util.Map;

/**
 * 导入结果：各类数量 + 异常清单。异常行不写入，也不会被「取第一个同名」之类的猜测掩盖。
 *
 * @param counts    如 departmentsCreated、usersCreated、permissions、rolePermissions
 * @param notes     标准化说明（如把「view」归一为「查看」）
 * @param anomalies 无法处理的行
 */
public record ImportReport(Map<String, Integer> counts, List<String> notes, List<Anomaly> anomalies) {

    public record Anomaly(String sheet, int row, String reason, String value) {
    }
}
