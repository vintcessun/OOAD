package com.xmu.rbac.structured.dto;

import java.time.LocalDateTime;

/** 用户的一次角色指派，连同角色与适用部门的名称。 */
public record AssignmentRow(Long assignmentId,
                            Long userId,
                            Long roleId,
                            String roleCode,
                            String roleName,
                            String roleStatus,
                            Long scopeDeptId,
                            String scopeDeptName,
                            String scopePath,
                            Long scopeCompanyId,
                            Long grantedBy,
                            LocalDateTime grantedAt,
                            String reason) {
}
