package com.xmu.rbac.structured.dto;

import java.util.List;

/**
 * 某用户持有某权限的部门范围：all = 超级管理员（不受部门限制）；
 * 否则是若干适用部门的物化路径，目标部门的路径以其中之一为前缀即在范围内。
 */
public record Scope(boolean all, List<String> paths) {

    public static final Scope NONE = new Scope(false, List.of());
    public static final Scope ALL = new Scope(true, List.of());

    public boolean isEmpty() {
        return !all && paths.isEmpty();
    }
}
