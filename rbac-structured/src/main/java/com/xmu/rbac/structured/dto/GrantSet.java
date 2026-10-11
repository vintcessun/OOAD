package com.xmu.rbac.structured.dto;

import java.util.List;

/**
 * 一个用户的全部授权数据，是缓存的单位。
 * assignments 只含启用中的角色；grants 是这些指派展开到权限码之后的结果。
 */
public record GrantSet(List<AssignmentRow> assignments, List<GrantRow> grants) {
}
