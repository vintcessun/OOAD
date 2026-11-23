package com.xmu.rbac.ddd.interfaces.rest.dto;

import cn.edu.xmu.clonefactory.CopyTo;
import com.xmu.rbac.ddd.application.RoleCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 验证角色创建请求并导出允许进入应用层的业务字段。 */
@CopyTo(RoleCommand.class)
public record CreateRoleRequest(
        @NotBlank
        @Size(max = 64)
        String systemCode,
        @NotBlank
        @Size(max = 64)
        String code,
        @NotBlank
        @Size(max = 128)
        String name,
        @Size(max = 500)
        String description) {
}
