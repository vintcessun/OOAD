package com.xmu.rbac.ddd.domain.port;

import com.xmu.rbac.ddd.domain.*;

import java.util.*;

/** 授权事实仓储端口；锁必须先于任何授权写读取。 */
public interface AuthorizationRepository {
    void lockGrants();

    long version();

    void freshenRead();

    void advanceVersion();

    boolean systemEnabled(String code);

    RoleHierarchy hierarchy(String systemCode);

    List<Permission> permissions(String systemCode);

    List<Permission> permissions(Set<Long> ids);

    Set<Long> assignments(long userId, String systemCode);

    Role role(long id);

    Role saveRole(Role role);

    void deleteRole(long id);

    void saveEdges(String systemCode, Set<RoleHierarchy.Edge> edges);

    Page<Role> roles(String systemCode, String keyword, String status, int page, int pageSize);

    List<Identity> activeIdentities();

    Identity identity(String subject);

    Identity identity(long id);

    Identity username(String username);

    LoginState lockLogin(String username);

    void saveLoginState(long id, int failedCount, java.time.Instant lockedUntil);

    record LoginState(Identity identity, int failedCount, java.time.Instant lockedUntil) {}

    DepartmentEligibility eligibility(long departmentId);

    String departmentPath(long departmentId);

    ApiOperation operation(String systemCode, String operationId);

    Set<String> requiredRoleCodes(String systemCode);

    boolean revoked(String jti);

    void revoke(String jti, String subject, java.time.Instant expires);

    void password(long id, String hash);

    record Page<T>(int page, int pageSize, long total, List<T> items) {}

    record Identity(
            long id,
            String subject,
            String username,
            String name,
            String no,
            String passwordHash,
            long departmentId,
            User.Status status,
            long tokenVersion) {}
}
