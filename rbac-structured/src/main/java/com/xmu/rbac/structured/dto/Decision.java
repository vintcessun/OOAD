package com.xmu.rbac.structured.dto;

import java.util.List;

/**
 * 一次鉴权判定的结论。
 *
 * @param reason   放行时为 ROLE_GRANTED / SUPER_ADMIN_BYPASS；拒绝时为拒绝原因码
 * @param matched  放行所依据的那条授权（超级管理员旁路时为空）
 * @param selfOnly 依据的授权只限本人记录（权限清单中的「查看本人」等），由业务系统按记录归属过滤
 * @param trace    只有权限解释接口才填写：逐步说明判定依据
 */
public record Decision(boolean allowed,
                       String reason,
                       Long userId,
                       String username,
                       String permissionCode,
                       Long targetDeptId,
                       GrantRow matched,
                       boolean selfOnly,
                       boolean cacheHit,
                       long latencyUs,
                       List<String> trace) {
}
