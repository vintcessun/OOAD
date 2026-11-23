package com.xmu.rbac.structured.controller;

import com.xmu.rbac.structured.dto.Actor;
import com.xmu.rbac.structured.dto.IntegrationViews.AuthoritiesVO;
import com.xmu.rbac.structured.dto.IntegrationViews.IdentityUserVO;
import com.xmu.rbac.structured.service.IntegrationIdentityService;
import java.util.Map;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 对课程管理员提供统一身份目录，并承载可信服务能力查询。 */
@RestController
@Validated
public class IntegrationIdentityController {
    private final IntegrationIdentityService identities;

    public IntegrationIdentityController(IntegrationIdentityService identities) {
        this.identities = identities;
    }

    /** 返回当前令牌aud下的服务器计算能力，不接受客户端指定其他主体。 */
    @GetMapping("/integration/authorities")
    public AuthoritiesVO authorities(@RequestAttribute("actor") Actor actor,
            @RequestHeader(name = "X-Audit-Service-Key", required = false) String key) {
        return identities.authorities(actor, key);
    }

    /** 接入教师已有的稳定subject姓名工号查询。 */
    @GetMapping("/identity/users/{userId}")
    public IdentityUserVO lookup(@RequestAttribute("actor") Actor actor,
            @RequestHeader(name = "X-Audit-Service-Key", required = false) String key,
            @PathVariable @Size(min = 1, max = 128) String userId) {
        return identities.lookup(actor, key, userId);
    }

    /** 按上级部门逐层展开，分页参数与教师身份接口一致。 */
    @GetMapping("/auth/departments")
    public Map<String, Object> departments(
            @RequestAttribute("actor")
            Actor actor,
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
        return identities.departments(actor, parentId, keyword, page, pageSize);
    }

    /** 查询指定部门的活跃直属成员。 */
    @GetMapping("/auth/departments/{departmentId}/users")
    public Map<String, Object> departmentUsers(
            @RequestAttribute("actor")
            Actor actor,
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
        return identities.departmentUsers(actor, departmentId, keyword, page, pageSize);
    }

    /** 使用稳定 subject 查询用户的公开身份。 */
    @GetMapping("/auth/users/{userId}")
    public IdentityUserVO user(
            @RequestAttribute("actor")
            Actor actor,
            @PathVariable
            @Size(min = 1, max = 128)
            String userId) {
        return identities.lookup(actor, userId);
    }
}
