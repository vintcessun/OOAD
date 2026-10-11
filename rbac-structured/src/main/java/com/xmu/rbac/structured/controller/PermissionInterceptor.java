package com.xmu.rbac.structured.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.structured.dto.Decision;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.service.AuditService;
import com.xmu.rbac.structured.service.AuthenticationService;
import com.xmu.rbac.structured.service.AuthenticationService.Authenticated;
import com.xmu.rbac.structured.service.AuthorizationService;
import com.xmu.rbac.structured.service.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 所有接口统一鉴权。每个接口需要什么权限只写在一个地方：契约 rbac-api.yaml 的 x-required-permission。
 * <ul>
 *   <li>契约里没有这个路由 → 403，默认拒绝；</li>
 *   <li>security: [] → 免登录（只有登录接口）；</li>
 *   <li>@self → 登录即可，具体能看谁由 Service 判断（本人或管理范围内）；</li>
 *   <li>权限码 → 调用判定过程，与业务系统调用 /authz/check 走同一条路径，并记 AUTH_SUCCESS / AUTH_FAILED。</li>
 * </ul>
 * 这里只确认「能执行这一类操作」；被操作的对象是否在管理范围内，由 Service 再按目标部门确认。
 */
public class PermissionInterceptor implements HandlerInterceptor {

    public static final String ATTR_OPERATOR = "rbac.operator";
    public static final String ATTR_AUTH = "rbac.auth";

    /** 首次登录必须先改口令；改之前只能用这几个接口。 */
    static final Set<String> BEFORE_PASSWORD_CHANGE = Set.of("GET /auth/me", "POST /auth/logout", "POST /auth/password");

    private final ContractRoutes routes;
    private final AuthenticationService authentication;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final ObjectMapper json;

    public PermissionInterceptor(ContractRoutes routes, AuthenticationService authentication,
                                 AuthorizationService authorization, AuditService audit, ObjectMapper json) {
        this.routes = routes;
        this.authentication = authentication;
        this.authorization = authorization;
        this.audit = audit;
        this.json = json;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws IOException {
        Object pattern = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String key = pattern == null ? null : ContractRoutes.key(req.getMethod(), pattern.toString());
        ContractRoutes.Route route = key == null ? null : routes.find(req.getMethod(), pattern.toString());
        if (route == null || (!route.anonymous() && route.permission() == null)) {
            return reject(resp, 403, BizException.FORBIDDEN, "接口未在契约中声明权限，默认拒绝", null);
        }
        if (route.anonymous()) {
            return true;
        }

        Authenticated auth;
        try {
            auth = authentication.authenticate(bearerToken(req));
        } catch (BizException e) {
            return reject(resp, e.httpStatus(), e.code(), e.getMessage(), null);
        }
        Operator op = new Operator(auth.userId(), auth.username(), clientIp(req));
        req.setAttribute(ATTR_OPERATOR, op);
        req.setAttribute(ATTR_AUTH, auth);

        if (auth.mustChangePwd() && !BEFORE_PASSWORD_CHANGE.contains(key)) {
            return reject(resp, 403, BizException.PASSWORD_CHANGE_REQUIRED, "首次登录请先修改口令", null);
        }
        if (ContractRoutes.SELF.equals(route.permission())) {
            return true;
        }
        Decision d = authorization.decide(auth.userId(), route.permission(), null, false);
        audit.recordDecision(d, op, "api " + key);
        if (!d.allowed()) {
            return reject(resp, 403, BizException.FORBIDDEN, "缺少权限 " + route.permission(), d.reason());
        }
        return true;
    }

    static String bearerToken(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return (h != null && h.startsWith("Bearer ")) ? h.substring(7).trim() : null;
    }

    /** 部署在 Nginx 之后时取 X-Forwarded-For 的第一段，否则取直连地址。 */
    static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private boolean reject(HttpServletResponse resp, int status, int code, String message, Object data)
            throws IOException {
        resp.setStatus(status);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(resp.getOutputStream(), ApiResponse.fail(code, message, data));
        return false;
    }
}
