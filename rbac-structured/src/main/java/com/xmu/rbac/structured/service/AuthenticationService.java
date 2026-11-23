package com.xmu.rbac.structured.service;
import static com.xmu.rbac.structured.service.Views.*;

import com.xmu.rbac.structured.dto.*;
import com.xmu.rbac.structured.entity.*;
import com.xmu.rbac.structured.mapper.Store;
import java.time.Instant;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 认证事务脚本；身份只从数据库和验证过的令牌取得。 */
@Service
public class AuthenticationService {
    private final Store db;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final TokenService tokens;
    private final PasswordEncoder passwords;
    private final org.springframework.transaction.support.TransactionTemplate tx;
    public AuthenticationService(Store db, AuthorizationService authz, AuditService audit,
        TokenService tokens, PasswordEncoder passwords,
        org.springframework.transaction.PlatformTransactionManager manager) {
        this.db = db;
        this.authz = authz;
        this.audit = audit;
        this.tokens = tokens;
        this.passwords = passwords;
        this.tx = new org.springframework.transaction.support.TransactionTemplate(manager);
    }
    /** 验证凭据并持久化并发失败锁定状态。 */
    public Object login(Inputs.Login input) {
        User user = tx.execute(status -> {
            GrantVersion g = authz.lock();
            User u = db.one(User.class, "e.username=?1", input.username());
            boolean valid = u != null && "ACTIVE".equals(u.status)
                && (u.lockedUntil == null || !u.lockedUntil.isAfter(Instant.now()))
                && passwords.matches(input.password(), u.passwordHash);
            if (!valid) {
                if (u != null && "ACTIVE".equals(u.status)
                    && (u.lockedUntil == null || !u.lockedUntil.isAfter(Instant.now()))) {
                    u.failedLoginCount++;
                    if (u.failedLoginCount >= 5) {
                        u.lockedUntil = Instant.now().plusSeconds(900);
                        u.failedLoginCount = 0;
                    }
                }
                return null;
            }
            SoftwareSystem system =
                db.one(SoftwareSystem.class, "e.code=?1", input.systems().getFirst());
            if (system == null)
                throw BizException.unauthorized();
            authz.active(u, system);
            u.failedLoginCount = 0;
            u.lockedUntil = null;
            Actor actor = new Actor(u.id, u.subject, system.code, null, null);
            audit.success(actor, "LOGIN", system.code);
            return u;
        });
        if (user == null) {
            throw new BizException(401, "CREDENTIAL_INVALID");
        }
        return tokens.issue(user, input.systems().getFirst());
    }

    /** 持久撤销当前令牌，Redis清空不影响拒绝结果。 */
    @Transactional
    public void logout(Actor actor) {
        GrantVersion g = authz.lock();
        if (db.get(TokenRevocation.class, actor.jti()) == null) {
            TokenRevocation revoked = new TokenRevocation();
            revoked.jti = actor.jti();
            revoked.subject = actor.subject();
            revoked.expiresAt = actor.expiresAt();
            revoked.createdAt = Instant.now();
            db.save(revoked);
        }
        g.version++;
        audit.success(actor, "LOGOUT", actor.subject());
    }
    /** 校验旧口令并更新令牌版本以失效旧令牌。 */
    @Transactional
    public void password(Actor actor, Inputs.Password input) {
        GrantVersion g = authz.lock();
        User user = db.lock(User.class, actor.userId());
        if (!passwords.matches(input.oldPassword(), user.passwordHash))
            throw new BizException(401, "CREDENTIAL_INVALID");
        user.passwordHash = passwords.encode(input.newPassword());
        user.tokenVersion++;
        user.mustChangePassword = false;
        g.version++;
        audit.success(actor, "PASSWORD_CHANGED", actor.subject());
    }
    /** 执行externalMe操作，保持服务到持久化层的单向依赖。 */
    public Object externalMe(Actor actor) {
        User user = authz.require(User.class, actor.userId());
        AuthorizationService.Snapshot snapshot = authz.snapshot(user.id, actor.systemCode());
        return map("subject", user.subject, "roles", authz.courseRoleCodes(snapshot), "permissions",
            snapshot.permissions()
                .stream()
                .map(id -> authz.require(Permission.class, id))
                .filter(p -> "UI".equals(p.kind))
                .map(p -> p.code)
                .sorted()
                .distinct()
                .toList(),
            "name", user.name, "no", user.no == null ? "" : user.no);
    }
    /** 执行me操作，保持服务到持久化层的单向依赖。 */
    public Object me(Actor actor) {
        User user = authz.require(User.class, actor.userId());
        return map("user",
            Views.user(user, authz.require(Department.class, user.departmentId).name), "effective",
            authz.effective(authz.snapshot(user.id, actor.systemCode()), actor.systemCode()));
    }
}
