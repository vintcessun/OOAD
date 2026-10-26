package com.xmu.rbac.structured.dto;

import java.time.LocalDateTime;

/**
 * 用户的一次角色指派，连同角色与适用部门快照。
 * scopeDeptId/scopePath 决定该角色权限在哪个部门子树生效；grantedBy 仅用于追溯授予人，不形成级联依赖。
 */
public record AssignmentRow(Long assignmentId,
                            Long userId,
                            String username,
                            String userRealName,
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
