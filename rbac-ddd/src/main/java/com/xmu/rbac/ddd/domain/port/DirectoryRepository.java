package com.xmu.rbac.ddd.domain.port;

import java.time.Instant;
import java.util.*;

/** 人员与组织聚合仓储，查询分页在存储侧执行。 */
public interface DirectoryRepository {
    record SystemDefinition(Long id, String code, String name, String status, Long version) {}

    record Department(
            Long id,
            Long parentId,
            String code,
            String name,
            String path,
            String status,
            Long version) {}

    record Profile(
            Long id,
            String subject,
            String username,
            String name,
            String no,
            String phone,
            Long departmentId,
            String departmentName,
            String status,
            Long version,
            String passwordHash,
            Long tokenVersion) {}

    /** 启用部门的层级浏览投影。 */
    record IdentityDepartment(Long id, String code, String name, Long parentId, boolean hasChildren) {}

    /** 只公开活跃成员的稳定标识、姓名、编号和直属部门。 */
    record IdentityDepartmentUser(String userId, String no, String name, Long departmentId, String departmentName) {}

    record Assignment(
            Long id,
            Long userId,
            Long roleId,
            String systemCode,
            String roleCode,
            String roleName,
            Instant assignedAt,
            Long assignedBy) {}

    List<SystemDefinition> systems();

    SystemDefinition system(long id);

    SystemDefinition saveSystem(SystemDefinition system);

    List<Department> departments();

    Department department(long id);

    /** 在一个层级内按部门名称或编码分页。 */
    AuthorizationRepository.Page<IdentityDepartment> identityDepartments(
            Long parentId, String keyword, int page, int size);

    /** 按姓名、学号或工号分页读取活跃直属成员。 */
    AuthorizationRepository.Page<IdentityDepartmentUser> identityDepartmentUsers(
            long departmentId, String keyword, int page, int size);

    Department saveDepartment(Department department);

    void deleteDepartment(long id);

    boolean departmentOccupied(long id);

    boolean departmentHasChildren(long id);

    AuthorizationRepository.Page<Profile> users(
            String keyword, Long departmentId, String status, String actorPath, int page, int size);

    Profile user(long id, boolean lock);

    Profile saveUser(Profile user);

    List<Assignment> assignments(long userId, String system);

    Assignment assign(long userId, long roleId, long actor);

    void removeAssignment(long id);

    void replaceEligibility(long department, Set<Long> roleIds);

    Set<Long> allowed(long department);
}
