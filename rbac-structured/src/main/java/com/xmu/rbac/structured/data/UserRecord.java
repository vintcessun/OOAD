package com.xmu.rbac.structured.data;

import java.time.LocalDateTime;

/**
 * 用户记录。贫血：只有数据，没有任何业务方法。
 * 判断状态是功能层的事，见 {@code AuthenticationFunctions}。docs/06-detail-structured.md §4.1
 */
public record UserRecord(
        Long id,
        String username,
        String passwordHash,
        String realName,
        Long departmentId,       // 数据范围判定依据
        String position,         // 岗位，仅首次导入时派生角色用
        int status,              // 1=启用 0=停用 2=删除
        String userType,         // EMPLOYEE | TEMPORARY | SYSTEM
        LocalDateTime expiresAt, // 账号失效时间；TEMPORARY 必填
        int loginFailCount,
        LocalDateTime lockedUntil,
        boolean mustChangePwd,
        LocalDateTime deletedAt
) {
    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_DELETED = 2;
}
