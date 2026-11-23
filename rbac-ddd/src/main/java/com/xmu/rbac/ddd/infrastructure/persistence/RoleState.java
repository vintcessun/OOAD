package com.xmu.rbac.ddd.infrastructure.persistence;

import cn.edu.xmu.clonefactory.CopyFrom;
import cn.edu.xmu.clonefactory.CopyTo;
import com.xmu.rbac.ddd.infrastructure.persistence.entity.RoleEntity;

/** 角色持久化快照，系统归属、数据库身份和版本由适配器管理。 */
@CopyFrom(RoleEntity.class)
@CopyTo(RoleEntity.class)
public record RoleState(
        String code,
        String name,
        String description,
        String status,
        Boolean builtin) {
}
