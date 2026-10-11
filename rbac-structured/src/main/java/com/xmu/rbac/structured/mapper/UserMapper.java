package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserMapper extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmployeeNo(String employeeNo);

    long countByStatusNot(String status);

    @Query("select u.username from User u")
    List<String> findAllUsernames();

    /**
     * 登录失败计数在数据库里原子地加一，并发的失败请求不会互相覆盖。
     * MySQL 按书写顺序执行 SET：先算 locked_until 时 login_fail_count 还是旧值。
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            UPDATE sys_user
               SET locked_until = CASE WHEN login_fail_count + 1 >= :max THEN :lockUntil ELSE locked_until END,
                   login_fail_count = CASE WHEN login_fail_count + 1 >= :max THEN 0 ELSE login_fail_count + 1 END
             WHERE id = :id
            """, nativeQuery = true)
    int recordLoginFailure(@Param("id") Long id, @Param("max") int max, @Param("lockUntil") LocalDateTime lockUntil);

    @Modifying(clearAutomatically = true)
    @Query("update User u set u.loginFailCount = 0, u.lockedUntil = null where u.id = :id")
    int resetLoginFailure(@Param("id") Long id);

    /** 某部门子树内未删除的用户（批量授权按部门、分公司展开）。 */
    @Query("""
            select u from User u, Department d
             where u.departmentId = d.id and d.path like concat(:path, '%') and u.status <> 'DELETED'
            """)
    List<User> findActiveInSubtree(@Param("path") String path);

    @Query("select u from User u where u.departmentId in :deptIds and u.status <> 'DELETED'")
    List<User> findActiveInDepartments(@Param("deptIds") Collection<Long> deptIds);

    /** 部门负责人匹配：在部门子树内按姓名找人。 */
    @Query("""
            select u from User u, Department d
             where u.departmentId = d.id and d.path like concat(:path, '%') and u.realName = :name
               and u.status <> 'DELETED'
            """)
    List<User> findByRealNameInSubtree(@Param("path") String path, @Param("name") String name);

    List<User> findByCompanyIdAndRealNameAndStatusNot(Long companyId, String realName, String status);
}
