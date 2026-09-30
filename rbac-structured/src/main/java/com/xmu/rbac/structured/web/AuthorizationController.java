package com.xmu.rbac.structured.web;

import com.xmu.rbac.structured.dao.AssignmentDao;
import com.xmu.rbac.structured.dao.UserDao;
import com.xmu.rbac.structured.data.AuthzResult;
import com.xmu.rbac.structured.func.AuthorizationFunctions;
import com.xmu.rbac.structured.func.CacheHandle;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/** 系统的核心接口：业务系统问「某用户能否对某资源做某操作」。 */
@RestController
public class AuthorizationController {

    /** context 迭代一忽略，但协议里从一开始就有（契约 AuthzContext）。 */
    public record AuthzRequest(String subject, String resource, String action, Map<String, Object> context) {}

    private final UserDao userDao;
    private final AssignmentDao assignmentDao;
    private final CacheHandle cache;
    private final Clock clock;

    public AuthorizationController(UserDao userDao, AssignmentDao assignmentDao, CacheHandle cache, Clock clock) {
        this.userDao = userDao;
        this.assignmentDao = assignmentDao;
        this.cache = cache;
        this.clock = clock;
    }

    /** 拒绝也返回 code = 0：判定成功完成，结论是「不能」。HTTP 403 只用于调用方自己无权调用本接口。 */
    @PostMapping("/authz/check")
    public ResponseEntity<ApiResponse<AuthzResult>> check(@RequestBody AuthzRequest req) {
        if (req.subject() == null || req.resource() == null || req.action() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail(10001, "subject、resource、action 均必填"));
        }
        AuthzResult result = AuthorizationFunctions.authorize(req.subject(), req.resource(), req.action(),
                LocalDateTime.now(clock), userDao, assignmentDao, cache);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
