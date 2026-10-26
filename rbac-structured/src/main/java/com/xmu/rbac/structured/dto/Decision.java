package com.xmu.rbac.structured.dto;

import java.util.List;

/**
 * 一次鉴权判定的结论。
 *
 * @param allowed        是否放行；正常权限拒绝也是一次成功完成的判定
 * @param reason         放行时为 ROLE_GRANTED / SUPER_ADMIN_BYPASS；拒绝时为拒绝原因码
 * @param userId         被判定主体 ID
 * @param username       被判定主体用户名快照
 * @param permissionCode 本次检查的完整权限码
 * @param targetDeptId   被操作数据所属部门；不涉及具体数据对象时为空
 * @param matched        放行所依据的代表性授权（超级管理员旁路或拒绝时为空）
 * @param selfOnly       所有覆盖授权都只限本人时为 true，由业务系统继续按记录归属过滤
 * @param cacheHit       授权集是否直接来自当前版本的 Redis 缓存
 * @param latencyUs      判定耗时，单位微秒
 * @param trace          只有权限解释接口才填写：逐步说明判定依据
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
