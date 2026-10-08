package com.xmu.rbac.structured.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.structured.dao.AssignmentDao;
import com.xmu.rbac.structured.dao.UserDao;
import com.xmu.rbac.structured.data.AuthzResult;
import com.xmu.rbac.structured.func.AuthenticationFunctions;
import com.xmu.rbac.structured.func.AuthorizationFunctions;
import com.xmu.rbac.structured.func.CacheHandle;
import com.xmu.rbac.structured.func.TokenFunctions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 所有后端接口都受权限控制（02-architecture.md §4.7）。
 * 权限来源只有一个：契约中该接口的 x-required-permission。
 * <ul>
 *   <li>契约里没有这个路由 → 403，默认拒绝；</li>
 *   <li>security: [] → 免鉴权（只有登录）；</li>
 *   <li>@self → 令牌有效且账号启用即可，只能作用于本人；</li>
 *   <li>具体权限码 → 调判定内核，与业务系统调用 /authz/check 走同一条管道。</li>
 * </ul>
 */
public class PermissionInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "rbac.userId";

    private final ContractRoutes routes;
    private final SecretKey jwtKey;
    private final UserDao userDao;
    private final AssignmentDao assignmentDao;
    private final CacheHandle cache;
    private final ObjectMapper json;
    private final Clock clock;

    public PermissionInterceptor(ContractRoutes routes, SecretKey jwtKey, UserDao userDao,
                                 AssignmentDao assignmentDao, CacheHandle cache,
                                 ObjectMapper json, Clock clock) {
        this.routes = routes;
        this.jwtKey = jwtKey;
        this.userDao = userDao;
        this.assignmentDao = assignmentDao;
        this.cache = cache;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws IOException {
        Object pattern = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        ContractRoutes.Route route = pattern == null ? null : routes.find(req.getMethod(), pattern.toString());
        if (route == null || (!route.anonymous() && route.permission() == null)) {
            return reject(resp, 403, 10003, "接口未在契约中声明权限，默认拒绝", null);
        }
        if (route.anonymous()) {
            return true;
        }

        Long userId = TokenFunctions.parseUserId(jwtKey, bearerToken(req), clock.instant());
        if (userId == null) {
            return reject(resp, 401, 10002, "未登录或令牌已失效", null);
        }
        LocalDateTime now = LocalDateTime.now(clock);

        if (ContractRoutes.SELF.equals(route.permission())) {
            String deny = AuthenticationFunctions.subjectDenyReason(userDao.findById(userId), now);
            if (deny != null) {
                return reject(resp, 401, 10002, "账号不可用：" + deny, null);
            }
            req.setAttribute(ATTR_USER_ID, userId);
            return true;
        }

        String permission = route.permission();
        int split = permission.lastIndexOf(':');
        AuthzResult result = AuthorizationFunctions.authorize("user_" + userId,
                permission.substring(0, split), permission.substring(split + 1),
                now, userDao, assignmentDao, cache);
        if (!result.allowed()) {
            return reject(resp, 403, 10003, "缺少权限 " + permission, result);
        }
        req.setAttribute(ATTR_USER_ID, userId);
        return true;
    }

    static String bearerToken(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        return (h != null && h.startsWith("Bearer ")) ? h.substring(7).trim() : null;
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
