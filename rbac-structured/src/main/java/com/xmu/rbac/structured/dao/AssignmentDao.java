package com.xmu.rbac.structured.dao;

import com.xmu.rbac.structured.data.RoleRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 指派与授予的数据访问：用户-角色、角色-权限两种关联。
 * 按功能而非按表划分——两种关联属于同一类功能（建立关联关系）。docs/06-detail-structured.md §3
 */
@Mapper
public interface AssignmentDao {

    /** 判定 S3：用户的直接角色，只取启用且未删除的角色。迭代一不看时间约束。 */
    @Select("""
            SELECT ur.role_id FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId} AND r.status = 1 AND r.deleted_at IS NULL
            """)
    Set<Long> findEnabledRoleIdsByUser(@Param("userId") Long userId);

    /** 判定 S4：多个角色的权限码，一次批量查询，避免 N+1。 */
    @Select("""
            <script>
            SELECT DISTINCT p.permission_code FROM sys_role_permission rp
            JOIN sys_permission p ON p.id = rp.permission_id
            WHERE rp.role_id IN
            <foreach collection="roleIds" item="rid" open="(" separator="," close=")">#{rid}</foreach>
            </script>
            """)
    List<String> findPermissionCodesByRoleIds(@Param("roleIds") Set<Long> roleIds);

    /** 判定 S1b：是否直接持有启用中的 SUPER_ADMIN。 */
    @Select("""
            SELECT COUNT(*) > 0 FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId} AND r.role_code = 'SUPER_ADMIN'
              AND r.status = 1 AND r.deleted_at IS NULL
            """)
    boolean holdsSuperAdmin(@Param("userId") Long userId);

    @Select("""
            SELECT r.id, r.role_code AS roleCode, r.role_name AS roleName, r.status, r.builtin
            FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId} AND r.deleted_at IS NULL
            ORDER BY r.id
            """)
    List<RoleRecord> findRolesByUser(@Param("userId") Long userId);
}
