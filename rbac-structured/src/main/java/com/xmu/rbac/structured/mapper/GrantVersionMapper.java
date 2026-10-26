package com.xmu.rbac.structured.mapper;

import com.xmu.rbac.structured.entity.GrantVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** 授权数据版本号（sys_grant_version 唯一的一行）。 */
public interface GrantVersionMapper extends JpaRepository<GrantVersion, Integer> {

    /** 当前全局授权版本；缺少唯一版本行时返回 empty，由读取方降级查库、写入方拒绝提交修改。 */
    @Query("select g.version from GrantVersion g where g.id = 1")
    Optional<Long> current();

    /** 加一，返回更新的行数（正常为 1）。在调用方的事务里执行，随授权数据一起提交或回滚。 */
    @Transactional
    @Modifying
    @Query("update GrantVersion g set g.version = g.version + 1 where g.id = 1")
    int bump();
}
