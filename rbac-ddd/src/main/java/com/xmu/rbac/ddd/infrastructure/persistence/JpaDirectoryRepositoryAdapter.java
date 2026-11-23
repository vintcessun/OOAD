package com.xmu.rbac.ddd.infrastructure.persistence;

import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;
import com.xmu.rbac.ddd.infrastructure.persistence.entity.*;

import jakarta.persistence.*;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/** 目录仓储的 JPA 适配器，不决定管理范围或指派准入。 */
@Repository
@Transactional(readOnly = true)
public class JpaDirectoryRepositoryAdapter implements DirectoryRepository {
    @PersistenceContext private EntityManager em;
    private final com.xmu.rbac.ddd.infrastructure.persistence.repository.UserJpaRepository
            userRecords;

    public JpaDirectoryRepositoryAdapter(
            com.xmu.rbac.ddd.infrastructure.persistence.repository.UserJpaRepository userRecords) {
        this.userRecords = userRecords;
    }

    private <T> T required(Class<T> type, long id) {
        T e = em.find(type, id);
        DomainException.require(e != null, 404, "NOT_FOUND");
        return e;
    }

    private SystemDefinition system(SystemEntity e) {
        return new SystemDefinition(
                e.getId(), e.getCode(), e.getName(), e.getStatus(), e.getVersion());
    }

    public List<SystemDefinition> systems() {
        return em
                .createQuery("from SystemEntity order by id", SystemEntity.class)
                .getResultList()
                .stream()
                .map(this::system)
                .toList();
    }

    public SystemDefinition system(long id) {
        return system(required(SystemEntity.class, id));
    }

    public SystemDefinition saveSystem(SystemDefinition s) {
        SystemEntity e = s.id() == null ? new SystemEntity() : required(SystemEntity.class, s.id());
        e.setCode(s.code());
        e.setName(s.name());
        e.setStatus(s.status());
        if (s.id() == null) em.persist(e);
        em.flush();
        return system(e);
    }

    private Department department(DepartmentEntity e) {
        return new Department(
                e.getId(),
                e.getParentId(),
                e.getCode(),
                e.getName(),
                e.getPath(),
                e.getStatus(),
                e.getVersion());
    }

    public List<Department> departments() {
        return em
                .createQuery("from DepartmentEntity order by id", DepartmentEntity.class)
                .getResultList()
                .stream()
                .map(this::department)
                .toList();
    }

    public Department department(long id) {
        return department(required(DepartmentEntity.class, id));
    }

    /** 在存储端计算每个部门的展开标记，同时保持分页顺序稳定。 */
    @Override
    public AuthorizationRepository.Page<IdentityDepartment> identityDepartments(
            Long parentId, String keyword, int page, int size) {
        String from = """
                 from DepartmentEntity d where d.status='ENABLED'
                 and ((:parent is null and d.parentId is null) or d.parentId=:parent)
                 and (lower(d.name) like lower(:keyword) escape '!' or lower(d.code) like lower(:keyword) escape '!')
                """;
        TypedQuery<Long> count = em.createQuery("select count(d)" + from, Long.class);
        TypedQuery<IdentityDepartment> items = em.createQuery("""
                select new com.xmu.rbac.ddd.domain.port.DirectoryRepository$IdentityDepartment(
                    d.id, d.code, d.name, d.parentId,
                    case when exists (select 1 from DepartmentEntity c where c.parentId=d.id and c.status='ENABLED')
                         then true else false end)
                """ + from + " order by d.id", IdentityDepartment.class);
        return directoryPage(count.setParameter("parent", parentId), items.setParameter("parent", parentId),
                keyword, page, size);
    }

    /** 直接读取所需字段，避免将整条用户记录载入目录查询。 */
    @Override
    public AuthorizationRepository.Page<IdentityDepartmentUser> identityDepartmentUsers(
            long departmentId, String keyword, int page, int size) {
        String from = """
                 from UserEntity u, DepartmentEntity d where u.departmentId=d.id
                 and u.departmentId=:department and u.status='ACTIVE'
                 and (lower(u.name) like lower(:keyword) escape '!' or lower(u.no) like lower(:keyword) escape '!')
                """;
        TypedQuery<Long> count = em.createQuery("select count(u)" + from, Long.class);
        TypedQuery<IdentityDepartmentUser> items = em.createQuery("""
                select new com.xmu.rbac.ddd.domain.port.DirectoryRepository$IdentityDepartmentUser(
                    u.subject, coalesce(u.no, ''), u.name, d.id, d.name)
                """ + from + " order by u.id", IdentityDepartmentUser.class);
        return directoryPage(count.setParameter("department", departmentId),
                items.setParameter("department", departmentId), keyword, page, size);
    }

    /** 对超出 JPA 偏移量上限的页只返回总数。 */
    private static <T> AuthorizationRepository.Page<T> directoryPage(
            TypedQuery<Long> count, TypedQuery<T> items, String keyword, int page, int size) {
        String pattern = keyword == null ? "%" : "%" + keyword.strip()
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total = count.setParameter("keyword", pattern).getSingleResult();
        long offset = (long) (page - 1) * size;
        List<T> content = offset >= total || offset > Integer.MAX_VALUE ? List.of()
                : items.setParameter("keyword", pattern).setFirstResult((int) offset)
                        .setMaxResults(size).getResultList();
        return new AuthorizationRepository.Page<>(page, size, total, content);
    }

    public Department saveDepartment(Department d) {
        DepartmentEntity e =
                d.id() == null ? new DepartmentEntity() : required(DepartmentEntity.class, d.id());
        e.setParentId(d.parentId());
        e.setCode(d.code());
        e.setName(d.name());
        e.setStatus(d.status());
        e.setPath(d.path());
        if (d.id() == null) {
            em.persist(e);
            em.flush();
            e.setPath(d.path() + e.getId() + "/");
        }
        em.flush();
        return department(e);
    }

    public boolean departmentOccupied(long id) {
        return em.createQuery("select count(*) from UserEntity where departmentId=:d", Long.class)
                                .setParameter("d", id)
                                .getSingleResult()
                        > 0
                || departmentHasChildren(id);
    }

    public boolean departmentHasChildren(long id) {
        return !em.createQuery("select id from DepartmentEntity where parentId=:d", Long.class)
                .setParameter("d", id)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    public void deleteDepartment(long id) {
        em.createQuery("delete from EligibilityEntity where departmentId=:d")
                .setParameter("d", id)
                .executeUpdate();
        em.remove(required(DepartmentEntity.class, id));
    }

    private Profile user(UserEntity e) {
        return new Profile(
                e.getId(),
                e.getSubject(),
                e.getUsername(),
                e.getName(),
                e.getNo(),
                e.getPhone(),
                e.getDepartmentId(),
                required(DepartmentEntity.class, e.getDepartmentId()).getName(),
                e.getStatus(),
                e.getVersion(),
                e.getPasswordHash(),
                e.getTokenVersion());
    }

    public Profile user(long id, boolean lock) {
        UserEntity e =
                lock
                        ? userRecords.lockUser(id).orElse(null)
                        : userRecords.findById(id).orElse(null);
        DomainException.require(e != null, 404, "USER_NOT_FOUND");
        return user(e);
    }

    public Profile saveUser(Profile p) {
        UserEntity e = p.id() == null ? new UserEntity() : required(UserEntity.class, p.id());
        e.setUsername(p.username());
        e.setSubject(p.subject() == null ? "rbac-" + UUID.randomUUID() : p.subject());
        e.setName(p.name());
        e.setNo(p.no());
        e.setPhone(p.phone());
        e.setDepartmentId(p.departmentId());
        e.setStatus(p.status());
        e.setPasswordHash(p.passwordHash());
        e.setTokenVersion(p.tokenVersion());
        if (p.id() == null) {
            e.setCreatedAt(Instant.now());
            e.setMustChangePassword(true);
            e.setFailedLoginCount(0);
            // 在首次INSERT前生成最终不透明subject，避免为临时subject额外UPDATE并消耗版本。
            em.persist(e);
        }
        em.flush();
        return user(e);
    }

    public AuthorizationRepository.Page<Profile> users(
            String keyword,
            Long departmentId,
            String status,
            String actorPath,
            int page,
            int size) {
        org.springframework.data.domain.Page<UserEntity> found =
                userRecords.search(
                        actorPath + "%",
                        keyword == null ? null : "%" + keyword + "%",
                        departmentId,
                        status,
                        org.springframework.data.domain.PageRequest.of(
                                page - 1, size, org.springframework.data.domain.Sort.by("id")));
        return new AuthorizationRepository.Page<>(
                page,
                size,
                found.getTotalElements(),
                found.getContent().stream().map(this::user).toList());
    }

    private Assignment assignment(AssignmentEntity e) {
        RoleEntity r = required(RoleEntity.class, e.getRoleId());
        return new Assignment(
                e.getId(),
                e.getUserId(),
                e.getRoleId(),
                required(SystemEntity.class, r.getSystemId()).getCode(),
                r.getCode(),
                r.getName(),
                e.getAssignedAt(),
                e.getAssignedBy());
    }

    public List<Assignment> assignments(long user, String system) {
        return em
                .createQuery(
                        "select a from AssignmentEntity a,RoleEntity r,SystemEntity s where"
                            + " a.userId=:u and a.roleId=r.id and r.systemId=s.id and (:system is"
                            + " null or s.code=:system) order by a.id",
                        AssignmentEntity.class)
                .setParameter("u", user)
                .setParameter("system", system)
                .getResultList()
                .stream()
                .map(this::assignment)
                .toList();
    }

    public Assignment assign(long user, long role, long actor) {
        AssignmentEntity e = new AssignmentEntity();
        e.setUserId(user);
        e.setRoleId(role);
        e.setAssignedBy(actor);
        e.setAssignedAt(Instant.now());
        em.persist(e);
        em.flush();
        return assignment(e);
    }

    public void removeAssignment(long id) {
        em.remove(required(AssignmentEntity.class, id));
    }

    public Set<Long> allowed(long department) {
        return new HashSet<>(
                em.createQuery(
                                "select roleId from EligibilityEntity where departmentId=:d",
                                Long.class)
                        .setParameter("d", department)
                        .getResultList());
    }

    public void replaceEligibility(long department, Set<Long> roles) {
        em.createQuery("delete from EligibilityEntity where departmentId=:d")
                .setParameter("d", department)
                .executeUpdate();
        for (Long id : roles) {
            EligibilityEntity e = new EligibilityEntity();
            e.setDepartmentId(department);
            e.setRoleId(id);
            em.persist(e);
        }
        List<AssignmentEntity> assigned =
                em.createQuery(
                                "select a from AssignmentEntity a,UserEntity u where a.userId=u.id"
                                        + " and u.departmentId=:d",
                                AssignmentEntity.class)
                        .setParameter("d", department)
                        .getResultList();
        for (AssignmentEntity a : assigned) if (!roles.contains(a.getRoleId())) em.remove(a);
    }
}
