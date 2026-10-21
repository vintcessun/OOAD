package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.Decision;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.dto.StatsDtos.AuthzRequest;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 3.6 鉴权接口与权限解释接口（/authz/check、/authz/explain）。
 *
 * <p>先确定「谁能查谁」（docs/06 §5.2.3），再调用与拦截器相同的 {@link AuthorizationService#decide}：
 * <ul>
 *   <li>查自己：任何已登录用户都可以；</li>
 *   <li>查别人（业务系统代用户鉴权、管理员查看他人）：调用者必须在被查用户的部门持有
 *       rbac:authz:invoke（解释为 rbac:authz:explain）；鉴权也可凭目标部门获准，此时只返回结论。</li>
 * </ul>
 * 调用者无权查询时返回 403；调用者有权、被查用户无权时，是一次正常完成的判定，返回 200 与 allowed=false。
 */
@Service
public class AuthzQueryService {

    public static final String PERM_INVOKE = "rbac:authz:invoke";
    public static final String PERM_EXPLAIN = "rbac:authz:explain";

    private final AuthorizationService authz;
    private final UserMapper userMapper;
    private final AuditService audit;

    public AuthzQueryService(AuthorizationService authz, UserMapper userMapper, AuditService audit) {
        this.authz = authz;
        this.userMapper = userMapper;
        this.audit = audit;
    }

    public Decision check(Operator caller, AuthzRequest req, boolean explain) {
        String permission = permissionOf(req);
        Subject subject = resolveSubject(caller, req, explain ? PERM_EXPLAIN : PERM_INVOKE);
        Decision d = authz.decide(subject.id(), permission, req.targetDeptId(), explain);
        audit.recordDecision(d, caller, explain ? "explain" : "check");
        return subject.targetOnly() ? conclusionOnly(d) : d;
    }

    /** 被查用户，以及调用者是否只凭目标部门获准查询。 */
    record Subject(Long id, boolean targetOnly) {
    }

    /**
     * 只凭目标部门获准时，只回答「能 / 不能」与是否只限本人：调用者管不到这个人，不能借此逐个探出别家公司的人的
     * 用户名、账号状态、是否超级管理员和他在别处的指派。放行一律报 ROLE_GRANTED，拒绝一律报 MISSING_PERMISSION。
     */
    static Decision conclusionOnly(Decision d) {
        return new Decision(d.allowed(), d.allowed() ? AuthorizationService.ROLE_GRANTED : AuthorizationService.MISSING_PERMISSION,
                d.userId(), null, d.permissionCode(), d.targetDeptId(), null, d.selfOnly(), d.cacheHit(), d.latencyUs(), null);
    }

    static String permissionOf(AuthzRequest req) {
        if (req == null) {
            throw BizException.badRequest("请求体不能为空");
        }
        if (req.permission() != null && !req.permission().isBlank()) {
            return req.permission().trim();
        }
        if (req.resource() == null || req.resource().isBlank() || req.action() == null || req.action().isBlank()) {
            throw BizException.badRequest("permission，或 resource + action，必须给出一种");
        }
        return req.resource().trim() + ":" + req.action().trim();
    }

    /**
     * 返回被查用户的 id。查别人时校验调用者的查询范围：调用者在被查用户所属部门持有调用权限；
     * 鉴权（check）另外允许调用者在目标部门持有调用权限——跨公司授权后，A 公司的业务系统要能问
     * 「B 公司的这个人能否操作 A 的这个部门」（docs/12-design-log.md R-20）。
     * 只凭目标部门获准时只返回结论（{@link #conclusionOnly}）。解释（explain）不放宽：解释过程会列出被查用户的全部指派。
     */
    Subject resolveSubject(Operator caller, AuthzRequest req, String callerPermission) {
        User subject;
        if (req.userId() != null) {
            subject = userMapper.findById(req.userId()).orElse(null);
        } else if (req.username() != null && !req.username().isBlank()) {
            subject = userMapper.findByUsername(req.username().trim()).orElse(null);
        } else {
            return new Subject(caller.userId(), false);
        }
        if (subject != null && subject.id.equals(caller.userId())) {
            return new Subject(caller.userId(), false);
        }
        // 先确认调用者能查别人，再透露被查用户是否存在
        if (authz.scopeOf(caller.userId(), callerPermission).isEmpty()) {
            throw denied(caller, callerPermission);
        }
        if (subject == null) {
            return new Subject(req.userId(), false);   // 判定结果为 SUBJECT_NOT_FOUND
        }
        boolean coversSubject = authz.decide(caller.userId(), callerPermission, subject.departmentId, false).allowed();
        boolean coversTarget = PERM_INVOKE.equals(callerPermission) && req.targetDeptId() != null
                && authz.decide(caller.userId(), callerPermission, req.targetDeptId(), false).allowed();
        if (!coversSubject && !coversTarget) {
            throw denied(caller, callerPermission);
        }
        return new Subject(subject.id, !coversSubject);
    }

    private BizException denied(Operator caller, String permission) {
        audit.record(caller, "AUTHZ_QUERY", "USER", null, null, "需要 " + permission, AuditService.DENIED,
                "CALLER_NOT_ALLOWED");
        return BizException.forbidden(BizException.FORBIDDEN, "无权查询其他用户的权限（需要在其部门持有 " + permission + "）");
    }
}
