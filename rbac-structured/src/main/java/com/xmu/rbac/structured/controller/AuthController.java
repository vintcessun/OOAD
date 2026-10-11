package com.xmu.rbac.structured.controller;

import com.xmu.rbac.structured.dto.Decision;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.dto.StatsDtos.AuthzRequest;
import com.xmu.rbac.structured.dto.UserDtos.UserDetail;
import com.xmu.rbac.structured.service.AuthenticationService;
import com.xmu.rbac.structured.service.AuthenticationService.Authenticated;
import com.xmu.rbac.structured.service.AuthenticationService.LoginResult;
import com.xmu.rbac.structured.service.AuthzQueryService;
import com.xmu.rbac.structured.service.UserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 登录场景与鉴权场景的接口。Controller 只做参数转换与结果包装。 */
@RestController
public class AuthController {

    public record LoginRequest(String username, String password) {
    }

    public record PasswordRequest(String oldPassword, String newPassword) {
    }

    private final AuthenticationService authentication;
    private final AuthzQueryService authzQuery;
    private final UserManagementService users;

    public AuthController(AuthenticationService authentication, AuthzQueryService authzQuery,
                          UserManagementService users) {
        this.authentication = authentication;
        this.authzQuery = authzQuery;
        this.users = users;
    }

    @PostMapping("/auth/login")
    public ApiResponse<LoginResult> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        return ApiResponse.ok(authentication.login(req.username(), req.password(),
                PermissionInterceptor.clientIp(http)));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(@RequestAttribute(PermissionInterceptor.ATTR_OPERATOR) Operator op,
                                    @RequestAttribute(PermissionInterceptor.ATTR_AUTH) Authenticated auth) {
        authentication.logout(op, auth);
        return ApiResponse.ok(null);
    }

    /** 当前用户：基本信息、角色、有效权限（前端据此控制按钮）。 */
    @GetMapping("/auth/me")
    public ApiResponse<UserDetail> me(@RequestAttribute(PermissionInterceptor.ATTR_OPERATOR) Operator op) {
        return ApiResponse.ok(users.detail(op, op.userId()));
    }

    @PostMapping("/auth/password")
    public ApiResponse<Void> changePassword(@RequestAttribute(PermissionInterceptor.ATTR_OPERATOR) Operator op,
                                            @RequestBody PasswordRequest req) {
        authentication.changePassword(op, req.oldPassword(), req.newPassword());
        return ApiResponse.ok(null);
    }

    /** 鉴权：拒绝也返回 HTTP 200 + allowed=false，那是一次正常完成的判定。 */
    @PostMapping("/authz/check")
    public ApiResponse<Decision> check(@RequestAttribute(PermissionInterceptor.ATTR_OPERATOR) Operator op,
                                       @RequestBody AuthzRequest req) {
        return ApiResponse.ok(authzQuery.check(op, req, false));
    }

    /** 权限解释：与 check 同一个判定过程，另外返回逐步的判定依据。 */
    @PostMapping("/authz/explain")
    public ApiResponse<Decision> explain(@RequestAttribute(PermissionInterceptor.ATTR_OPERATOR) Operator op,
                                         @RequestBody AuthzRequest req) {
        return ApiResponse.ok(authzQuery.check(op, req, true));
    }
}
