package com.xmu.rbac.structured.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 用户管理的请求与响应。 */
public final class UserDtos {

    private UserDtos() {
    }

    public record UserView(Long id, String username, String realName, String userType, String employeeNo,
                           String phone, String position, String status, String statusReason, Long companyId,
                           Long departmentId, String departmentName, LocalDateTime expiresAt,
                           boolean mustChangePwd, LocalDateTime createdAt) {
    }

    /** 临时用户（TEMPORARY）不填工号、电话，但必须给出到期时间。password 为空时使用初始口令并要求首次登录修改。 */
    public record CreateUser(String username, String realName, Long departmentId, String userType,
                             String employeeNo, String phone, String position, LocalDateTime expiresAt,
                             String password) {
    }

    public record UpdateUser(String realName, Long departmentId, String phone, String position,
                             LocalDateTime expiresAt) {
    }

    /** status 只能是 ACTIVE 或 DISABLED；删除走 DELETE 接口。 */
    public record ChangeStatus(String status, String reason) {
    }

    /** 批量操作：status 可取 ACTIVE / DISABLED / DELETED。 */
    public record BatchStatus(List<Long> userIds, String status, String reason) {
    }

    public record EffectivePermission(String permissionCode, String roleCode, String roleName,
                                      Long scopeDeptId, String scopeDeptName, boolean selfOnly) {
    }

    /** 用户详情：基本信息、角色（指派）、有效权限。 */
    public record UserDetail(UserView user, List<AssignmentRow> roles, boolean superAdmin,
                             List<EffectivePermission> permissions) {
    }

    public record Assign(Long roleId, Long scopeDepartmentId, String reason) {
    }
}
