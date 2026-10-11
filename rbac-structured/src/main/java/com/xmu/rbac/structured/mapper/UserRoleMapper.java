package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.dto.AssignmentRow;
import com.xmu.rbac.structured.dto.GrantRow;
import com.xmu.rbac.structured.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface UserRoleMapper extends JpaRepository<UserRole, Long> {

    String ASSIGNMENT_ROW = """
            select new com.xmu.rbac.structured.dto.AssignmentRow(
                   ur.id, ur.userId, u.username, u.realName, r.id, r.roleCode, r.roleName, r.status,
                   d.id, d.deptName, d.path, d.companyId, ur.grantedBy, ur.grantedAt, ur.reason)
              from UserRole ur, Role r, Department d, User u
             where r.id = ur.roleId and d.id = ur.scopeDeptId and u.id = ur.userId
            """;

    /** 用户的全部指派（含已停用的角色，用户详情页展示用）。 */
    @Query(ASSIGNMENT_ROW + " and ur.userId = :userId order by ur.id")
    List<AssignmentRow> findAssignmentsOfUser(@Param("userId") Long userId);

    /** 持有某角色的指派（角色详情页「用户」）。 */
    @Query(value = ASSIGNMENT_ROW + " and ur.roleId = :roleId order by ur.id",
            countQuery = "select count(ur) from UserRole ur where ur.roleId = :roleId")
    Page<AssignmentRow> findAssignmentsOfRole(@Param("roleId") Long roleId, Pageable pageable);

    /** 持有某角色的全部指派（按管理范围过滤后再分页时用）。 */
    @Query(ASSIGNMENT_ROW + " and ur.roleId = :roleId order by ur.id")
    List<AssignmentRow> findAllAssignmentsOfRole(@Param("roleId") Long roleId);

    /** 判定 S3：用户经由启用中的角色获得的全部权限码，每一行都带着它来自哪次指派。 */
    @Query("""
            select new com.xmu.rbac.structured.dto.GrantRow(
                   p.permissionCode, ur.id, r.id, r.roleCode, r.roleName,
                   d.id, d.deptName, d.path, d.companyId, rp.selfOnly)
              from UserRole ur, Role r, RolePermission rp, Permission p, Department d
             where ur.userId = :userId and r.id = ur.roleId and r.status = 'ENABLED'
               and rp.roleId = r.id and p.id = rp.permissionId and d.id = ur.scopeDeptId
            """)
    List<GrantRow> findGrantsOfUser(@Param("userId") Long userId);

    boolean existsByUserIdAndRoleIdAndScopeDeptId(Long userId, Long roleId, Long scopeDeptId);

    List<UserRole> findByRoleIdAndUserIdIn(Long roleId, Collection<Long> userIds);

    long countByRoleId(Long roleId);

    /** 仍然有效的超级管理员人数：防止撤销最后一个。 */
    @Query("""
            select count(distinct ur.userId) from UserRole ur, Role r, User u
             where r.id = ur.roleId and u.id = ur.userId
               and r.roleCode = 'SUPER_ADMIN' and u.status = 'ACTIVE'
            """)
    long countActiveSuperAdmins();

    /**
     * 批量指派的写入：一条 INSERT ... SELECT 写一批用户，不逐行往返。
     * scopeDeptId 为空表示「各自的所属部门」。重复由调用方事先排除。
     */
    @Modifying
    @Query(value = """
            INSERT INTO sys_user_role (user_id, role_id, scope_dept_id, granted_by, granted_at, reason)
            SELECT u.id, :roleId, COALESCE(:scopeDeptId, u.department_id), :grantedBy, :grantedAt, :reason
              FROM sys_user u WHERE u.id IN (:userIds)
            """, nativeQuery = true)
    int insertForUsers(@Param("userIds") Collection<Long> userIds,
                       @Param("roleId") Long roleId,
                       @Param("scopeDeptId") Long scopeDeptId,
                       @Param("grantedBy") Long grantedBy,
                       @Param("grantedAt") LocalDateTime grantedAt,
                       @Param("reason") String reason);

    @Modifying
    @Query("delete from UserRole ur where ur.id in :ids")
    int deleteByIds(@Param("ids") Collection<Long> ids);
}
