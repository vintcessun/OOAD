package com.xmu.rbac.ddd.interfaces.rest;

import com.xmu.rbac.ddd.application.IntegrationIdentityApplicationService;
import com.xmu.rbac.ddd.application.IntegrationIdentityApplicationService.AuthoritiesResult;
import com.xmu.rbac.ddd.application.IntegrationIdentityApplicationService.IdentityResult;

import com.xmu.rbac.ddd.domain.port.AuthorizationRepository.Page;
import com.xmu.rbac.ddd.domain.port.DirectoryRepository.IdentityDepartment;
import com.xmu.rbac.ddd.domain.port.DirectoryRepository.IdentityDepartmentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 负责统一身份目录和私有能力查询的 HTTP 适配。 */
@RestController
@Validated
public class IntegrationIdentityController {
    private final RestSupport rest;
    private final IntegrationIdentityApplicationService identities;

    public IntegrationIdentityController(
            RestSupport rest, IntegrationIdentityApplicationService identities) {
        this.rest = rest;
        this.identities = identities;
    }

    /** 返回当前用户/当前系统的服务器计算能力。 */
    @GetMapping("/integration/authorities")
    public AuthoritiesResult authorities(
            @RequestHeader(name = "Authorization", required = false) String bearer,
            @RequestHeader(name = "X-Audit-Service-Key", required = false) String key) {
        return identities.authorities(rest.principal(bearer), key);
    }

    /** 按教师原接口解析稳定subject，应用服务负责访问规则。 */
    @GetMapping("/identity/users/{userId}")
    public IdentityResult lookup(
            @RequestHeader(name = "Authorization", required = false) String bearer,
            @RequestHeader(name = "X-Audit-Service-Key", required = false) String key,
            @PathVariable @Size(min = 1, max = 128) String userId) {
        return identities.lookup(rest.principal(bearer), key, userId);
    }

    /** 按上级部门逐层展开，分页参数与教师身份接口一致。 */
    @GetMapping("/auth/departments")
    public Page<IdentityDepartment> departments(
            @RequestHeader(name = "Authorization", required = false)
            String bearer,
            @RequestParam(required = false)
            @Positive
            Long parentId,
            @RequestParam(required = false)
            @Size(max = 100)
            String keyword,
            @RequestParam(defaultValue = "1")
            @Positive
            int page,
            @RequestParam(defaultValue = "20")
            @Positive
            @Max(100)
            int pageSize) {
        return identities.departments(rest.principal(bearer), parentId, keyword, page, pageSize);
    }

    /** 查询指定部门的活跃直属成员。 */
    @GetMapping("/auth/departments/{departmentId}/users")
    public Page<IdentityDepartmentUser> departmentUsers(
            @RequestHeader(name = "Authorization", required = false)
            String bearer,
            @PathVariable
            @Positive
            Long departmentId,
            @RequestParam(required = false)
            @Size(max = 100)
            String keyword,
            @RequestParam(defaultValue = "1")
            @Positive
            int page,
            @RequestParam(defaultValue = "20")
            @Positive
            @Max(100)
            int pageSize) {
        return identities.departmentUsers(rest.principal(bearer), departmentId, keyword, page, pageSize);
    }

    /** 使用稳定 subject 查询用户的公开身份。 */
    @GetMapping("/auth/users/{userId}")
    public IdentityResult user(
            @RequestHeader(name = "Authorization", required = false)
            String bearer,
            @PathVariable
            @Size(min = 1, max = 128)
            String userId) {
        return identities.lookup(rest.principal(bearer), userId);
    }
}
