package com.xmu.rbac.ddd;

import static org.assertj.core.api.Assertions.*;

import cn.edu.xmu.clonefactory.util.CloneFactory;

import com.xmu.rbac.ddd.application.*;
import com.xmu.rbac.ddd.infrastructure.persistence.*;
import com.xmu.rbac.ddd.infrastructure.persistence.entity.RoleEntity;
import com.xmu.rbac.ddd.interfaces.rest.dto.*;

import org.junit.jupiter.api.Test;

/** 真正的教师生成器边界，不拷贝 id、actor、密码或乐观锁版本。 */
class CloneBoundaryTest {
    @Test
    void requestToCommand() throws Exception {
        CreateRoleRequest input = new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                """
                {"systemCode":"course-project-management","code":"TEACHER","name":"教师"}
                """, CreateRoleRequest.class);
        RoleCommand command = CloneFactory.copy(new RoleCommand(), input);
        assertThat(command.getCode()).isEqualTo("TEACHER");
        assertThat(command.getName()).isEqualTo("教师");
        assertThat(command.getSystemCode()).isEqualTo("course-project-management");
    }

    @Test
    void resultToVo() {
        RoleResult result = new RoleResult(7L, "course", "teacher", "安全角色",
                null, "ENABLED", false, 3L);
        RoleView view = CloneFactory.copy(
                new RoleView(null, null, null, null, null, null, null, null), result);
        assertThat(view.id()).isEqualTo(7L);
        assertThat(view.name()).isEqualTo("安全角色");
        assertThat(view.builtin()).isFalse();
    }

    @Test
    void persistenceBoundaryDoesNotOverwriteIdentityOrVersion() {
        RoleEntity entity = new RoleEntity();
        entity.setId(9L);
        entity.setSystemId(2L);
        entity.setVersion(4L);
        entity.setCode("teacher");
        entity.setStatus("ENABLED");
        entity.setBuiltin(true);
        entity.setName("更新名称");
        RoleState state = CloneFactory.copy(new RoleState(null, null, null, null, null), entity);
        assertThat(state.name()).isEqualTo("更新名称");
        assertThat(state.code()).isEqualTo("teacher");
        entity.setName("旧名称");
        CloneFactory.copy(entity, state);
        assertThat(entity.getId()).isEqualTo(9L);
        assertThat(entity.getVersion()).isEqualTo(4L);
        assertThat(entity.getSystemId()).isEqualTo(2L);
        assertThat(entity.getName()).isEqualTo("更新名称");
    }
}
