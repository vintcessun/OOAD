package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RolePermissionMapper extends JpaRepository<RolePermission, Long> {

    Optional<RolePermission> findByRoleIdAndPermissionId(Long roleId, Long permissionId);

    boolean existsByRoleIdAndPermissionId(Long roleId, Long permissionId);

    long countByRoleId(Long roleId);

    /** 角色的权限码，用于「不能授出自己没有的权限」的检查。 */
    @Query("select p.permissionCode from RolePermission rp, Permission p where p.id = rp.permissionId and rp.roleId = :roleId")
    List<String> findPermissionCodesOfRole(@Param("roleId") Long roleId);

    /** 角色详情：[permissionId, permissionCode, resourceName, actionName, selfOnly, grantedAt]。 */
    @Query("""
            select p.id, p.permissionCode, r.resourceName, a.actionName, rp.selfOnly, rp.grantedAt
              from RolePermission rp, Permission p, Resource r, Action a
             where rp.roleId = :roleId and p.id = rp.permissionId and r.id = p.resourceId and a.id = p.actionId
             order by p.permissionCode
            """)
    List<Object[]> findPermissionDetailsOfRole(@Param("roleId") Long roleId);
}
