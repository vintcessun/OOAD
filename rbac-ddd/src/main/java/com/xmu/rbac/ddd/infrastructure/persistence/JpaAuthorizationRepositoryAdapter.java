package com.xmu.rbac.ddd.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;
import com.xmu.rbac.ddd.infrastructure.persistence.entity.*;

import jakarta.persistence.*;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** 通过独立 JPA 持久化模型实现聚合仓储；SQL 分页由数据库执行。 */
@Repository
@Transactional(readOnly = true)
public class JpaAuthorizationRepositoryAdapter implements AuthorizationRepository {
    @PersistenceContext private EntityManager em;
    private final com.xmu.rbac.ddd.infrastructure.persistence.repository.UserJpaRepository
            userRecords;
    private final com.xmu.rbac.ddd.infrastructure.persistence.repository.GrantVersionJpaRepository
            grantVersions;
    private final com.xmu.rbac.ddd.infrastructure.persistence.repository.RoleJpaRepository
            roleRecords;

    public JpaAuthorizationRepositoryAdapter(
            com.xmu.rbac.ddd.infrastructure.persistence.repository.GrantVersionJpaRepository
                    grantVersions,
            com.xmu.rbac.ddd.infrastructure.persistence.repository.RoleJpaRepository roleRecords,
            com.xmu.rbac.ddd.infrastructure.persistence.repository.UserJpaRepository userRecords) {
        this.userRecords = userRecords;
        this.grantVersions = grantVersions;
        this.roleRecords = roleRecords;
    }

    @Override
    public void freshenRead() {
        em.clear();
    }

    @Override
    public void lockGrants() {
        grantVersions.lockAuthority();
        em.clear();
    }

    @Override
    public long version() {
        return grantVersions.authorityVersion();
    }

    @Override
    public void advanceVersion() {
        grantVersions.increment();
    }

    private SystemEntity system(String code) {
        return em.createQuery("from SystemEntity where code=:code", SystemEntity.class)
                .setParameter("code", code)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new DomainException(404, "SYSTEM_NOT_FOUND"));
    }

    @Override
    public boolean systemEnabled(String code) {
        return system(code).getStatus().equals("ENABLED");
    }

    private Role restore(RoleEntity entity, String code) {
        RoleState state = CloneFactory.copy(new RoleState(null, null, null, null, null), entity);
        Set<Long> permissions =
                new HashSet<>(
                        em.createQuery(
                                        "select permissionId from RolePermissionEntity where"
                                                + " roleId=:id",
                                        Long.class)
                                .setParameter("id", entity.getId())
                                .getResultList());
        return new Role(
                entity.getId(),
                code,
                state.code(),
                state.name(),
                state.description(),
                state.status().equals("ENABLED"),
                state.builtin(),
                entity.getVersion(),
                permissions);
    }

    @Override
    public Role role(long id) {
        RoleEntity e = em.find(RoleEntity.class, id);
        DomainException.require(e != null, 404, "ROLE_NOT_FOUND");
        return restore(e, em.find(SystemEntity.class, e.getSystemId()).getCode());
    }

    @Override
    public RoleHierarchy hierarchy(String code) {
        long system = system(code).getId();
        List<Role> roles =
                em
                        .createQuery(
                                "from RoleEntity where systemId=:system order by id",
                                RoleEntity.class)
                        .setParameter("system", system)
                        .getResultList()
                        .stream()
                        .map(e -> restore(e, code))
                        .toList();
        Set<Long> ids = new HashSet<>();
        for (Role r : roles) ids.add(r.id());
        List<RoleHierarchy.Edge> edges =
                ids.isEmpty()
                        ? List.of()
                        : em
                                .createQuery(
                                        "from HierarchyEntity where seniorRoleId in :ids",
                                        HierarchyEntity.class)
                                .setParameter("ids", ids)
                                .getResultList()
                                .stream()
                                .map(
                                        e ->
                                                new RoleHierarchy.Edge(
                                                        e.getSeniorRoleId(), e.getJuniorRoleId()))
                                .toList();
        return new RoleHierarchy(roles, edges);
    }

    @Override
    public List<Permission> permissions(String code) {
        return em
                .createQuery(
                        "from PermissionEntity where systemId=:s order by id",
                        PermissionEntity.class)
                .setParameter("s", system(code).getId())
                .getResultList()
                .stream()
                .map(
                        e ->
                                new Permission(
                                        e.getId(),
                                        code,
                                        e.getCode(),
                                        e.getName(),
                                        Permission.Kind.valueOf(e.getKind()),
                                        e.getStatus().equals("ENABLED")))
                .toList();
    }

    @Override
    public List<Permission> permissions(Set<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return em.createQuery("from PermissionEntity where id in :ids order by id", PermissionEntity.class)
                .setParameter("ids", ids).getResultList().stream()
                .map(e -> new Permission(e.getId(), em.find(SystemEntity.class, e.getSystemId()).getCode(),
                        e.getCode(), e.getName(), Permission.Kind.valueOf(e.getKind()),
                        e.getStatus().equals("ENABLED")))
                .toList();
    }

    @Override
    public Set<Long> assignments(long user, String code) {
        return new HashSet<>(
                em.createQuery(
                                "select a.roleId from AssignmentEntity a,RoleEntity r where"
                                        + " a.userId=:u and a.roleId=r.id and r.systemId=:s",
                                Long.class)
                        .setParameter("u", user)
                        .setParameter("s", system(code).getId())
                        .getResultList());
    }

    @Override
    public Role saveRole(Role role) {
        RoleEntity entity =
                role.id() == 0 ? new RoleEntity() : em.find(RoleEntity.class, role.id());
        if (role.id() != 0)
            DomainException.require(entity.getVersion() == role.version(), 409, "VERSION_CONFLICT");
        RoleState state = new RoleState(role.code(), role.name(), role.description(),
                role.enabled() ? "ENABLED" : "DISABLED", role.builtin());
        CloneFactory.copy(entity, state);
        entity.setSystemId(system(role.systemCode()).getId());
        roleRecords.saveAndFlush(entity);
        em.createQuery("delete from RolePermissionEntity where roleId=:id")
                .setParameter("id", entity.getId())
                .executeUpdate();
        for (Long id : role.permissionIds()) {
            RolePermissionEntity link = new RolePermissionEntity();
            link.setRoleId(entity.getId());
            link.setPermissionId(id);
            em.persist(link);
        }
        return restore(entity, role.systemCode());
    }

    @Override
    public void deleteRole(long id) {
        RoleEntity entity = em.find(RoleEntity.class, id);
        DomainException.require(entity != null, 404, "ROLE_NOT_FOUND");
        for (String table :
                List.of("AssignmentEntity", "RolePermissionEntity", "EligibilityEntity"))
            em.createQuery("delete from " + table + " where roleId=:id")
                    .setParameter("id", id)
                    .executeUpdate();
        em.createQuery("delete from HierarchyEntity where seniorRoleId=:id or juniorRoleId=:id")
                .setParameter("id", id)
                .executeUpdate();
        em.remove(entity);
    }

    @Override
    public void saveEdges(String code, Set<RoleHierarchy.Edge> edges) {
        List<Long> ids =
                em.createQuery("select id from RoleEntity where systemId=:s", Long.class)
                        .setParameter("s", system(code).getId())
                        .getResultList();
        if (!ids.isEmpty())
            em.createQuery("delete from HierarchyEntity where seniorRoleId in :ids")
                    .setParameter("ids", ids)
                    .executeUpdate();
        for (RoleHierarchy.Edge edge : edges) {
            HierarchyEntity e = new HierarchyEntity();
            e.setSeniorRoleId(edge.seniorRoleId());
            e.setJuniorRoleId(edge.juniorRoleId());
            em.persist(e);
        }
    }

    @Override
    public Page<Role> roles(String code, String keyword, String status, int page, int size) {
        org.springframework.data.domain.Page<RoleEntity> found =
                roleRecords.search(
                        system(code).getId(),
                        keyword == null ? null : "%" + keyword + "%",
                        status,
                        org.springframework.data.domain.PageRequest.of(
                                page - 1, size, org.springframework.data.domain.Sort.by("id")));
        return new Page<>(
                page,
                size,
                found.getTotalElements(),
                found.getContent().stream().map(e -> restore(e, code)).toList());
    }

    private Identity identity(UserEntity e) {
        return e == null
                ? null
                : new Identity(
                        e.getId(),
                        e.getSubject(),
                        e.getUsername(),
                        e.getName(),
                        e.getNo(),
                        e.getPasswordHash(),
                        e.getDepartmentId(),
                        User.Status.valueOf(e.getStatus()),
                        e.getTokenVersion());
    }

    @Override
    public List<Identity> activeIdentities() {
        return em
                .createQuery("from UserEntity where status='ACTIVE'", UserEntity.class)
                .getResultList()
                .stream()
                .map(this::identity)
                .toList();
    }

    @Override
    public Identity identity(long id) {
        return identity(em.find(UserEntity.class, id));
    }

    @Override
    public Identity identity(String subject) {
        return em.createQuery("from UserEntity where subject=:s", UserEntity.class)
                .setParameter("s", subject)
                .getResultStream()
                .findFirst()
                .map(this::identity)
                .orElse(null);
    }

    @Override
    public Identity username(String username) {
        return em.createQuery("from UserEntity where username=:s", UserEntity.class)
                .setParameter("s", username)
                .getResultStream()
                .findFirst()
                .map(this::identity)
                .orElse(null);
    }

    @Override
    public DepartmentEligibility eligibility(long department) {
        return new DepartmentEligibility(
                department,
                new HashSet<>(
                        em.createQuery(
                                        "select roleId from EligibilityEntity where"
                                                + " departmentId=:d",
                                        Long.class)
                                .setParameter("d", department)
                                .getResultList()));
    }

    @Override
    public String departmentPath(long department) {
        DepartmentEntity e = em.find(DepartmentEntity.class, department);
        DomainException.require(e != null, 404, "DEPARTMENT_NOT_FOUND");
        return e.getPath();
    }

    @Override
    public Set<String> requiredRoleCodes(String code) {
        return Set.copyOf(em.createQuery(
                        "select a.requiredRole from ApiOperationEntity a "
                                + "where a.systemId=:system and a.accessPolicy='PROTECTED' "
                                + "and a.requiredRole is not null",
                        String.class)
                .setParameter("system", system(code).getId())
                .getResultList());
    }

    @Override
    public ApiOperation operation(String code, String operation) {
        return em.createQuery(
                        "from ApiOperationEntity where systemId=:s and operationId=:op",
                        ApiOperationEntity.class)
                .setParameter("s", system(code).getId())
                .setParameter("op", operation)
                .getResultStream()
                .findFirst()
                .map(
                        e ->
                                new ApiOperation(
                                        e.getId(),
                                        code,
                                        e.getOperationId(),
                                        e.getMethod(),
                                        e.getPathTemplate(),
                                        e.getPermissionId() == null
                                                ? null
                                                : em.find(
                                                                PermissionEntity.class,
                                                                e.getPermissionId())
                                                        .getCode(),
                                        e.getRequiredRole(),
                                        ApiOperation.Policy.valueOf(e.getAccessPolicy()),
                                        e.getEnabled()))
                .orElse(null);
    }

    @Override
    public boolean revoked(String jti) {
        return ((Number)
                                em.createNativeQuery(
                                                "select count(*) from sys_token_revocation where"
                                                        + " jti=:jti")
                                        .setParameter("jti", jti)
                                        .getSingleResult())
                        .intValue()
                > 0;
    }

    @Override
    public void revoke(String jti, String subject, Instant expires) {
        em.createNativeQuery(
                        "insert into sys_token_revocation(jti,subject,expires_at,created_at)"
                                + " values(:jti,:subject,:expires,:now)")
                .setParameter("jti", jti)
                .setParameter("subject", subject)
                .setParameter("expires", expires)
                .setParameter("now", Instant.now())
                .executeUpdate();
    }

    @Override
    public void password(long id, String hash) {
        UserEntity e = em.find(UserEntity.class, id);
        e.setPasswordHash(hash);
        e.setMustChangePassword(false);
        e.setTokenVersion(e.getTokenVersion() + 1);
    }

    @Override
    public LoginState lockLogin(String username) {
        return userRecords
                .lockLogin(username)
                .map(e -> new LoginState(identity(e), e.getFailedLoginCount(), e.getLockedUntil()))
                .orElse(null);
    }

    @Override
    public void saveLoginState(long id, int failedCount, Instant lockedUntil) {
        UserEntity user =
                userRecords
                        .findById(id)
                        .orElseThrow(() -> new DomainException(404, "USER_NOT_FOUND"));
        user.setFailedLoginCount(failedCount);
        user.setLockedUntil(lockedUntil);
        userRecords.saveAndFlush(user);
    }
}
