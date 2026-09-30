package com.xmu.rbac.structured.func;

import com.xmu.rbac.structured.dao.AssignmentDao;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** 3.4 / 3.6 权限集合运算。功能内聚：只做集合运算。 */
public final class PermissionSetFunctions {

    private PermissionSetFunctions() {}

    /** 3.4 汇总：多个角色的权限并集。 */
    public static Set<String> unionRolePermissions(AssignmentDao dao, Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(dao.findPermissionCodesByRoleIds(roleIds));
    }

    /** 3.6 匹配：O(1) 集合查找。整个系统调用频率最高的一行。 */
    public static boolean contains(Set<String> effective, String permissionCode) {
        return effective != null && effective.contains(permissionCode);
    }
}
