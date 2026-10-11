package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoleMapper extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleCode(String roleCode);

    boolean existsByRoleCode(String roleCode);

    List<Role> findAllByOrderByIdAsc();

    /** 每个角色的用户数（同一用户在多个部门持有同一角色只算一次）：[roleId, count]。 */
    @Query("select ur.roleId, count(distinct ur.userId) from UserRole ur group by ur.roleId")
    List<Object[]> countUsersByRole();

    /** 每个角色的权限数：[roleId, count]。 */
    @Query("select rp.roleId, count(rp.id) from RolePermission rp group by rp.roleId")
    List<Object[]> countPermissionsByRole();
}
