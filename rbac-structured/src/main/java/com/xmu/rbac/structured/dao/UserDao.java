package com.xmu.rbac.structured.dao;

import com.xmu.rbac.structured.data.UserRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户表的数据访问。纯 SQL，返回记录，不含业务判断。
 * 其他模块要读用户，走功能层函数，不直接调本接口（02-architecture.md §2.3：禁止跨模块调 Mapper）。
 */
@Mapper
public interface UserDao {

    String COLUMNS = """
            id, username, password_hash AS passwordHash, real_name AS realName,
            department_id AS departmentId, position, status, user_type AS userType,
            expires_at AS expiresAt, login_fail_count AS loginFailCount,
            locked_until AS lockedUntil, must_change_pwd AS mustChangePwd, deleted_at AS deletedAt
            """;

    @Select("SELECT " + COLUMNS + " FROM sys_user WHERE id = #{id}")
    UserRecord findById(@Param("id") Long id);

    @Select("SELECT " + COLUMNS + " FROM sys_user WHERE username = #{username}")
    UserRecord findByUsername(@Param("username") String username);

    /** 记一次登录失败；lockedUntil 非空时同时锁定。 */
    @Update("UPDATE sys_user SET login_fail_count = #{failCount}, locked_until = #{lockedUntil} WHERE id = #{id}")
    int updateLoginFailure(@Param("id") Long id,
                           @Param("failCount") int failCount,
                           @Param("lockedUntil") LocalDateTime lockedUntil);

    @Update("UPDATE sys_user SET login_fail_count = 0, locked_until = NULL WHERE id = #{id}")
    int resetLoginFailure(@Param("id") Long id);

    /** 改口令的同时清除「首次登录强制改密」标记。 */
    @Update("UPDATE sys_user SET password_hash = #{hash}, must_change_pwd = 0 WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("hash") String passwordHash);
}
