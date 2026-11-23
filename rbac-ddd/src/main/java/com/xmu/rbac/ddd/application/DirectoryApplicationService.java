package com.xmu.rbac.ddd.application;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;
import com.xmu.rbac.ddd.domain.port.DirectoryRepository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 组织用例编排。范围、唯一部门和直接指派均由领域规则校验。 */
@Service
public class DirectoryApplicationService {
    private final GrantIntegrityService integrity;
    private final DirectoryRepository directory;
    private final AuthorizationRepository grants;
    private final AuthorizationApplicationService authorization;
    private final AuditApplicationService audits;
    private final CredentialEncoder encoder;
    private final OrganizationPolicy policy = new OrganizationPolicy();

    public DirectoryApplicationService(
            DirectoryRepository directory,
            AuthorizationRepository grants,
            AuthorizationApplicationService authorization,
            AuditApplicationService audits,
            CredentialEncoder encoder,
            GrantIntegrityService integrity) {
        this.integrity = integrity;
        this.directory = directory;
        this.grants = grants;
        this.authorization = authorization;
        this.audits = audits;
        this.encoder = encoder;
    }

    private AuthorizationRepository.Identity actor(
            TokenProvider.Principal p, String permission, boolean shared) {
        return authorization.require(p, permission, shared);
    }

    private Department managed(AuthorizationRepository.Identity actor, long department) {
        String actorPath = grants.departmentPath(actor.departmentId());
        Department target = directory.department(department);
        policy.requireManaged(actorPath, target);
        return target;
    }

    private void done(AuthorizationRepository.Identity actor, String type, String target) {
        integrity.requireAdministrator();
        grants.advanceVersion();
        audits.local(actor, "rbac-center", type, target, "SUCCESS", null);
    }

    public List<SystemDefinition> systems(TokenProvider.Principal p) {
        actor(p, "rbac:system:view", false);
        return directory.systems();
    }

    @Transactional
    public SystemDefinition system(
            TokenProvider.Principal p, Long id, String code, String name, String status) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor = actor(p, "rbac:system:edit", true);
        SystemDefinition old = id == null ? null : directory.system(id);
        if (old != null && "ENABLED".equals(status) && !"ENABLED".equals(old.status()))
            authorization.requireSystemActivation(actor, old.code());
        SystemDefinition saved =
                directory.saveSystem(
                        new SystemDefinition(
                                id,
                                old == null ? code : old.code(),
                                name == null ? old.name() : name,
                                status == null ? (old == null ? "ENABLED" : old.status()) : status,
                                old == null ? 0 : old.version()));
        done(actor, "SYSTEM_CHANGED", saved.code());
        return saved;
    }

    public List<Department> departments(TokenProvider.Principal p) {
        AuthorizationRepository.Identity actor = actor(p, "rbac:department:view", false);
        String path = grants.departmentPath(actor.departmentId());
        return directory.departments().stream().filter(d -> d.path().startsWith(path)).toList();
    }

    @Transactional
    public Department department(
            TokenProvider.Principal p, Long id, Long parentId, String code, String name) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor = actor(p, "rbac:department:edit", false);
        DomainException.require(id != null || parentId != null, 409, "ROOT_DEPARTMENT_EXISTS");
        Department target = managed(actor, id == null ? parentId : id);
        Department old = id == null ? null : target;
        Long parent = old == null ? parentId : old.parentId();
        if (id == null) policy.requireAccepting(target);
        Department saved =
                directory.saveDepartment(
                        new Department(
                                id,
                                parent,
                                old == null ? code : old.code(),
                                name,
                                target.path(),
                                old == null ? "ENABLED" : old.status(),
                                old == null ? 0 : old.version()));
        done(actor, "DEPARTMENT_CHANGED", saved.code());
        return saved;
    }

    @Transactional
    public Department departmentStatus(TokenProvider.Principal p, long id, String status) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor = actor(p, "rbac:department:status", false);
        Department old = managed(actor, id);
        DomainException.require(
                "DISABLED".equals(status) || "ENABLED".equals(status), 400, "VALIDATION_ERROR");
        if (status.equals(old.status())) return old;
        if ("DISABLED".equals(status))
            policy.requireDisableable(old, directory.departmentHasChildren(id));
        Department saved = directory.saveDepartment(
                new Department(
                        id, old.parentId(), old.code(), old.name(), old.path(), status, old.version()));
        done(actor, "DEPARTMENT_STATUS_CHANGED", Long.toString(id));
        return saved;
    }

    @Transactional
    public void deleteDepartment(TokenProvider.Principal p, long id) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor = actor(p, "rbac:department:delete", false);
        Department target = managed(actor, id);
        policy.requireDeletable(target, directory.departmentOccupied(id));
        directory.deleteDepartment(id);
        done(actor, "DEPARTMENT_DELETED", Long.toString(id));
    }

    public List<Role> allowed(TokenProvider.Principal p, long id, String system) {
        AuthorizationRepository.Identity actor = actor(p, "rbac:department:view", false);
        managed(actor, id);
        return directory.allowed(id).stream()
                .map(grants::role)
                .filter(r -> system == null || r.systemCode().equals(system))
                .toList();
    }

    @Transactional
    public void allowed(TokenProvider.Principal p, long id, Set<Long> roleIds) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor = actor(p, "rbac:department:edit", false);
        managed(actor, id);
        Set<Long> current = directory.allowed(id);
        for (Long roleId : roleIds) {
            Role role = grants.role(roleId);
            if (grants.systemEnabled(role.systemCode())) {
                requireRoleWithin(actor, role);
            } else if (!current.contains(roleId)) {
                authorization.requireRoleGrantSystem(actor, role.systemCode());
            }
        }
        for (Long roleId : current) {
            if (roleIds.contains(roleId)) continue;
            String system = grants.role(roleId).systemCode();
            if (!grants.systemEnabled(system)) authorization.requireRoleGrantSystem(actor, system);
        }
        directory.replaceEligibility(id, roleIds);
        done(actor, "DEPARTMENT_ELIGIBILITY_CHANGED", Long.toString(id));
    }

    private void requireRoleWithin(AuthorizationRepository.Identity actor, Role role) {
        String system = role.systemCode();
        authorization.upperBound(
                actor,
                system,
                new AuthorizationPolicy()
                        .calculate(
                                system,
                                Set.of(role.id()),
                                grants.hierarchy(system),
                                grants.permissions(system)));
    }

    public AuthorizationRepository.Page<Profile> users(
            TokenProvider.Principal p,
            String keyword,
            Long department,
            String status,
            int page,
            int size) {
        AuthorizationRepository.Identity actor = actor(p, "rbac:user:view", false);
        DomainException.require(page > 0 && size > 0 && size <= 100, 400, "INVALID_PAGE");
        if (department != null) managed(actor, department);
        return directory.users(
                keyword,
                department,
                status,
                grants.departmentPath(actor.departmentId()),
                page,
                size);
    }

    public Profile user(TokenProvider.Principal p, long id) {
        return managedUser(actor(p, "rbac:user:view", false), id);
    }

    private Profile managedUser(AuthorizationRepository.Identity actor, long id) {
        Profile user = directory.user(id, false);
        managed(actor, user.departmentId());
        return user;
    }

    @Transactional
    public Profile createUser(
            TokenProvider.Principal p,
            String username,
            String name,
            String no,
            String phone,
            long department,
            String password) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor = actor(p, "rbac:user:edit", false);
        policy.requireAccepting(managed(actor, department));
        Profile saved =
                directory.saveUser(
                        new Profile(
                                null,
                                null,
                                username,
                                name,
                                no,
                                phone,
                                department,
                                null,
                                "ACTIVE",
                                0L,
                                encoder.encode(password),
                                0L));
        done(actor, "USER_CREATED", saved.subject());
        return saved;
    }

    @Transactional
    public Profile patchUser(
            TokenProvider.Principal p,
            long id,
            UserUpdateCommand command,
            boolean noPresent,
            boolean phonePresent) {
        String name = command.getName();
        String no = command.getNo();
        String phone = command.getPhone();
        Long version = command.getVersion();
        grants.lockGrants();
        AuthorizationRepository.Identity actor = actor(p, "rbac:user:edit", false);
        Profile old = directory.user(id, true);
        managed(actor, old.departmentId());
        policy.requireVersion(version, old.version());
        Profile saved =
                directory.saveUser(
                        new Profile(
                                old.id(),
                                old.subject(),
                                old.username(),
                                name == null ? old.name() : name,
                                noPresent ? no : old.no(),
                                phonePresent ? phone : old.phone(),
                                old.departmentId(),
                                old.departmentName(),
                                old.status(),
                                old.version(),
                                old.passwordHash(),
                                old.tokenVersion()));
        done(actor, "USER_UPDATED", old.subject());
        return saved;
    }

    private User aggregate(Profile profile) {
        Set<Long> ids = new HashSet<>();
        for (Assignment a : directory.assignments(profile.id(), null)) ids.add(a.roleId());
        return new User(
                profile.id(),
                profile.subject(),
                profile.departmentId(),
                User.Status.valueOf(profile.status()),
                profile.tokenVersion(),
                ids);
    }

    private Profile save(Profile old, User user) {
        for (Assignment a : directory.assignments(old.id(), null))
            if (!user.roleIds().contains(a.roleId())) directory.removeAssignment(a.id());
        return directory.saveUser(
                new Profile(
                        old.id(),
                        old.subject(),
                        old.username(),
                        old.name(),
                        old.no(),
                        old.phone(),
                        user.departmentId(),
                        null,
                        user.status().name(),
                        old.version(),
                        old.passwordHash(),
                        user.tokenVersion()));
    }

    @Transactional
    public Profile statusUser(TokenProvider.Principal p, long id, User.Status status) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor = actor(p, "rbac:user:edit", false);
        Profile old = directory.user(id, true);
        managed(actor, old.departmentId());
        User user = aggregate(old);
        if (user.status() == User.Status.DISABLED && status == User.Status.ACTIVE) {
            for (Long roleId : user.roleIds()) {
                Role role = grants.role(roleId);
                if (grants.systemEnabled(role.systemCode())) requireRoleWithin(actor, role);
            }
        }
        user.changeStatus(status);
        Profile saved = save(old, user);
        done(actor, "USER_STATUS_CHANGED", old.subject());
        return saved;
    }

    @Transactional
    public Profile transfer(TokenProvider.Principal p, long id, long department, Long version) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor = actor(p, "rbac:user:edit", false);
        Profile old = directory.user(id, true);
        managed(actor, old.departmentId());
        Department destination = managed(actor, department);
        policy.requireVersion(version, old.version());
        if (old.departmentId() != department) policy.requireAccepting(destination);
        User user = aggregate(old);
        user.transfer(department);
        Profile saved = save(old, user);
        done(actor, "USER_DEPARTMENT_CHANGED", old.subject());
        return saved;
    }

    public List<Assignment> assignments(TokenProvider.Principal p, long id, String system) {
        user(p, id);
        return directory.assignments(id, system);
    }

    @Transactional
    public Assignment assign(TokenProvider.Principal p, long id, long roleId) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor = actor(p, "rbac:assignment:edit", false);
        Profile profile = directory.user(id, true);
        policy.requireAccepting(managed(actor, profile.departmentId()));
        Role role = grants.role(roleId);
        if (!grants.systemEnabled(role.systemCode()))
            throw DomainException.auditedDenial(409, "ROLE_DISABLED");
        requireRoleWithin(actor, role);
        aggregate(profile).assign(role, grants.eligibility(profile.departmentId()));
        Assignment saved = directory.assign(id, roleId, actor.id());
        done(actor, "ROLE_ASSIGNED", profile.subject());
        return saved;
    }

    @Transactional
    public void unassign(TokenProvider.Principal p, long id, long assignmentId) {
        grants.lockGrants();
        AuthorizationRepository.Identity actor = actor(p, "rbac:assignment:edit", false);
        Profile profile = directory.user(id, true);
        managed(actor, profile.departmentId());
        Assignment assignment =
                directory.assignments(id, null).stream()
                        .filter(a -> a.id() == assignmentId)
                        .findFirst()
                        .orElseThrow(() -> new DomainException(404, "ASSIGNMENT_NOT_FOUND"));
        directory.removeAssignment(assignmentId);
        done(actor, "ROLE_UNASSIGNED", profile.subject());
    }

    public Profile self(TokenProvider.Principal p) {
        return directory.user(authorization.validate(p).id(), false);
    }

    public AuthorizationPolicy.Effective effective(
            TokenProvider.Principal p, long id, String system) {
        AuthorizationRepository.Identity actor = actor(p, "rbac:user:view", false);
        // 先确认系统存在。快照读取会再次校验系统状态和授权版本。
        grants.systemEnabled(system);
        managedUser(actor, id);
        try {
            return authorization.effective(id, system);
        } catch (DomainException error) {
            // 管理目标的停用状态不影响已校验的操作者会话。
            if (error.status() != 401 || !"SYSTEM_DISABLED".equals(error.code())) throw error;
            return new AuthorizationPolicy.Effective(List.of(), List.of(), List.of(), List.of());
        }
    }

    public Map<String, Long> dashboard(TokenProvider.Principal p) {
        AuthorizationRepository.Identity actor = authorization.requireDashboard(p);
        long users =
                directory
                        .users(null, null, null, grants.departmentPath(actor.departmentId()), 1, 1)
                        .total();
        long roles = 0;
        for (SystemDefinition system : directory.systems())
            roles += grants.roles(system.code(), null, null, 1, 1).total();
        return Map.of("users", users, "roles", roles, "systems", (long) directory.systems().size());
    }
}
