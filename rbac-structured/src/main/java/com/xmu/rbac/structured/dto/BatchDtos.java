package com.xmu.rbac.structured.dto;

import java.util.List;

/** 批量授权与批量操作。 */
public final class BatchDtos {

    private BatchDtos() {
    }

    public static final String ATOMIC = "ATOMIC";
    public static final String PARTIAL = "PARTIAL";

    /**
     * 按分公司 / 部门 / 员工批量指派或回收角色。
     *
     * @param targetType         COMPANY | DEPARTMENT | USER
     * @param includeDescendants DEPARTMENT 时是否包含下级部门，默认 true
     * @param scopeDepartmentId  指派的适用部门；为空表示每个人各自的所属部门（回收时为空表示该角色的全部指派）
     * @param mode               ATOMIC（默认）：有任何一人不通过校验就整批不执行；PARTIAL：执行通过的，返回失败清单
     */
    public record BatchAssign(String targetType, List<Long> targetIds, Boolean includeDescendants, Long roleId,
                              Long scopeDepartmentId, String reason, String mode) {
    }

    public record Failure(Long userId, String reason) {
    }

    /**
     * @param committed  是否已写入；ATOMIC 模式下有失败时为 false
     * @param duplicated 已持有相同指派而跳过的人数（不算失败）
     */
    public record BatchResult(String mode, boolean committed, int requested, int succeeded, int duplicated,
                              int failed, List<Failure> failures) {
    }
}
