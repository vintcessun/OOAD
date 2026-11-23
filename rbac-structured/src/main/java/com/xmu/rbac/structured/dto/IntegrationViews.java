package com.xmu.rbac.structured.dto;

import java.util.List;

/** 仅供受信课程后端读取的白名单输出，不包含凭据和持久化记录。 */
public final class IntegrationViews {
    private IntegrationViews() { }

    /** 当前目标系统的真实API能力及课程角色码。 */
    public record AuthoritiesVO(List<String> authorities) { }

    /** 部门目录仅公开层级导航需要的字段。 */
    public record DepartmentVO(Long id, String code, String name, Long parentId, boolean hasChildren) { }

    /** 部门成员以稳定标识对外提供，不携带账户和联系方式。 */
    public record DepartmentUserVO(String userId, String no, String name, Long departmentId, String departmentName) { }

    /** 教师系统既有统一身份查询协议。 */
    public record IdentityUserVO(String userId, String name, String no) { }
}
