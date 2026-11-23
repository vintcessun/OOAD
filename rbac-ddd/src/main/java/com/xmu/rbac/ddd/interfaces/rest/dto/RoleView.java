package com.xmu.rbac.ddd.interfaces.rest.dto;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.xmu.rbac.ddd.application.RoleResult;

/** 角色公开响应，字段由应用结果填充。 */
@CopyFrom(RoleResult.class)
public record RoleView(
        Long id,
        String systemCode,
        String code,
        String name,
        String description,
        String status,
        Boolean builtin,
        Long version) {
}
