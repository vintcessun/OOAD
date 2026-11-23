package com.xmu.rbac.ddd.application;

import com.xmu.rbac.ddd.domain.AuthorizationPolicy;
import com.xmu.rbac.ddd.domain.DomainException;
import com.xmu.rbac.ddd.domain.Permission;
import com.xmu.rbac.ddd.domain.User;
import com.xmu.rbac.ddd.domain.port.AuthorizationRepository;
import com.xmu.rbac.ddd.domain.port.DirectoryRepository;
import com.xmu.rbac.ddd.domain.port.DirectoryRepository.IdentityDepartment;
import com.xmu.rbac.ddd.domain.port.DirectoryRepository.IdentityDepartmentUser;
import com.xmu.rbac.ddd.domain.port.TokenProvider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.TreeSet;

/** 通过领域仓储端口执行课程目录查询和私有能力查询。 */
@Service
@Transactional(readOnly = true)
public class IntegrationIdentityApplicationService {
    private final AuthorizationApplicationService authorization;
    private final AuthorizationRepository repository;
    private final DirectoryRepository directory;
    private final byte[] serviceDigest;
    private final boolean configured;

    public IntegrationIdentityApplicationService(
            AuthorizationApplicationService authorization,
            AuthorizationRepository repository,
            DirectoryRepository directory,
            @Value("${rbac.audit-service-key:}") String key) {
        this.authorization = authorization;
        this.repository = repository;
        this.directory = directory;
        this.configured = !key.isBlank();
        this.serviceDigest = digest(key);
    }

    /** 用固定长度摘要进行恒定时间比较，不把用户bearer当成可信服务来源。 */
    private void requireService(String supplied) {
        DomainException.require(
                configured
                        && supplied != null
                        && MessageDigest.isEqual(serviceDigest, digest(supplied)),
                403,
                "AUDIT_SERVICE_REQUIRED");
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256不可用", error);
        }
    }

    /** 只投影当前aud的API权限与身份标记，保持菜单与AuthorityCodes分离。 */
    public AuthoritiesResult authorities(TokenProvider.Principal principal, String key) {
        requireService(key);
        AuthorizationRepository.Identity actor = authorization.validate(principal);
        AuthorizationPolicy.Effective effective =
                authorization.effective(actor.id(), principal.systemCode());
        TreeSet<String> values = new TreeSet<>();
        effective.permissions().stream()
                .filter(permission -> permission.kind() == Permission.Kind.API)
                .forEach(permission -> values.add(permission.code()));
        effective.courseRoleCodes().forEach(identity -> values.add("ROLE_" + identity));
        return new AuthoritiesResult(List.copyOf(values));
    }

    /** 受控最少身份字段查询，主体由稳定subject定位而非姓名猜测。 */
    public IdentityResult lookup(TokenProvider.Principal principal, String key, String subject) {
        requireService(key);
        return lookup(principal, subject);
    }

    /** 使用当前课程管理员的有效能力查询稳定用户身份。 */
    public IdentityResult lookup(TokenProvider.Principal principal, String subject) {
        requireTeacherAdministration(principal);
        AuthorizationRepository.Identity target = repository.identity(subject);
        DomainException.require(
                target != null && target.status() == User.Status.ACTIVE,
                404,
                "IDENTITY_USER_NOT_FOUND");
        return new IdentityResult(
                target.subject(), target.name(), target.no() == null ? "" : target.no());
    }

    /** 按层级分页浏览启用部门。 */
    public AuthorizationRepository.Page<IdentityDepartment> departments(
            TokenProvider.Principal principal, Long parentId, String keyword, int page, int size) {
        requireTeacherAdministration(principal);
        if (parentId != null) {
            requireDepartment(parentId);
        }
        return directory.identityDepartments(parentId, keyword, page, size);
    }

    /** 分页浏览启用部门的活跃直属成员。 */
    public AuthorizationRepository.Page<IdentityDepartmentUser> departmentUsers(
            TokenProvider.Principal principal, long departmentId, String keyword, int page, int size) {
        requireTeacherAdministration(principal);
        requireDepartment(departmentId);
        return directory.identityDepartmentUsers(departmentId, keyword, page, size);
    }

    /** 目录仅允许课程管理员使用有效的 TEC API 权限。 */
    private void requireTeacherAdministration(TokenProvider.Principal principal) {
        AuthorizationRepository.Identity actor = authorization.validate(principal);
        DomainException.require("course-project-management".equals(principal.systemCode()), 403, "FORBIDDEN");
        AuthorizationPolicy.Effective effective = authorization.effective(actor.id(), principal.systemCode());
        DomainException.require(effective.courseRoleCodes().contains("admin")
                && effective.has("TEC", Permission.Kind.API), 403, "FORBIDDEN");
    }

    private void requireDepartment(long id) {
        DomainException.require("ENABLED".equals(directory.department(id).status()), 404, "NOT_FOUND");
    }

    /** 应用结果不携带实体或凭据，接入层只需白名单序列化。 */
    public record AuthoritiesResult(List<String> authorities) {}

    /** 最少身份结果。 */
    public record IdentityResult(String userId, String name, String no) {}
}
