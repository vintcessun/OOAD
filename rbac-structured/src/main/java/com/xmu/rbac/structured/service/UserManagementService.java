package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.AssignmentRow;
import com.xmu.rbac.structured.dto.BatchDtos.BatchResult;
import com.xmu.rbac.structured.dto.BatchDtos.Failure;
import com.xmu.rbac.structured.dto.GrantSet;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.dto.PageResult;
import com.xmu.rbac.structured.dto.Scope;
import com.xmu.rbac.structured.dto.UserDtos.BatchStatus;
import com.xmu.rbac.structured.dto.UserDtos.ChangeStatus;
import com.xmu.rbac.structured.dto.UserDtos.CreateUser;
import com.xmu.rbac.structured.dto.UserDtos.EffectivePermission;
import com.xmu.rbac.structured.dto.UserDtos.UpdateUser;
import com.xmu.rbac.structured.dto.UserDtos.UserDetail;
import com.xmu.rbac.structured.dto.UserDtos.UserView;
import com.xmu.rbac.structured.entity.Department;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.mapper.DepartmentMapper;
import com.xmu.rbac.structured.mapper.UserMapper;
import com.xmu.rbac.structured.mapper.UserRoleMapper;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 4.0 用户管理：列表与搜索、新增、修改、启用停用、删除、批量操作、用户详情（docs/06 §5.3）。
 * 每个处理过程都先确认目标用户所在部门在操作者的管理范围内。
 */
@Service
public class UserManagementService {

    public static final String PERM_VIEW = "rbac:user:view";
    public static final String PERM_CREATE = "rbac:user:create";
    public static final String PERM_EDIT = "rbac:user:edit";
    public static final String PERM_DELETE = "rbac:user:delete";

    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final UserRoleMapper userRoleMapper;
    private final AuthorizationService authz;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String initialPasswordHash;

    public UserManagementService(UserMapper userMapper, DepartmentMapper departmentMapper,
                                 UserRoleMapper userRoleMapper, AuthorizationService authz, AuditService audit,
                                 PasswordEncoder passwordEncoder, Clock clock,
                                 @Value("${rbac.initial-password}") String initialPassword) {
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.userRoleMapper = userRoleMapper;
        this.authz = authz;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        // 初始口令只算一次哈希：导入一万名员工时逐个计算 BCrypt 要好几分钟
        this.initialPasswordHash = passwordEncoder.encode(initialPassword);
    }

    public String initialPasswordHash() {
        return initialPasswordHash;
    }

    // ------------------------------------------------------------------ 4.1 列表与搜索

    @Transactional(readOnly = true)
    public PageResult<UserView> list(Operator op, String keyword, Long departmentId, boolean includeDescendants,
                                     String status, String userType, int page, int size) {
        Scope scope = authz.scopeOf(op.userId(), PERM_VIEW);
        if (scope.isEmpty()) {
            return new PageResult<>(List.of(), 0, page, size);
        }
        String deptPath = null;
        if (departmentId != null) {
            deptPath = departmentMapper.findPathById(departmentId).orElseThrow(() -> BizException.notFound("部门"));
        }
        Specification<User> spec = buildFilter(scope, keyword, departmentId, deptPath, includeDescendants,
                status, userType);
        Page<User> result = userMapper.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 200), Sort.by("id")));
        Map<Long, String> deptNames = deptNames(result.getContent().stream().map(u -> u.departmentId).toList());
        return PageResult.of(result, u -> toView(u, deptNames.get(u.departmentId)));
    }

    static Specification<User> buildFilter(Scope scope, String keyword, Long departmentId, String deptPath,
                                           boolean includeDescendants, String status, String userType) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();
            if (status == null || status.isBlank()) {
                and.add(cb.notEqual(root.get("status"), User.DELETED));
            } else {
                and.add(cb.equal(root.get("status"), status));
            }
            if (userType != null && !userType.isBlank()) {
                and.add(cb.equal(root.get("userType"), userType));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                and.add(cb.or(cb.like(root.get("username"), like), cb.like(root.get("realName"), like),
                        cb.like(root.get("employeeNo"), like)));
            }
            if (departmentId != null) {
                if (includeDescendants) {
                    and.add(root.get("departmentId").in(deptSubquery(query, cb, List.of(deptPath))));
                } else {
                    and.add(cb.equal(root.get("departmentId"), departmentId));
                }
            }
            if (!scope.all()) {
                and.add(root.get("departmentId").in(deptSubquery(query, cb, scope.paths())));
            }
            return cb.and(and.toArray(new Predicate[0]));
        };
    }

    private static Subquery<Long> deptSubquery(jakarta.persistence.criteria.CriteriaQuery<?> query,
                                               jakarta.persistence.criteria.CriteriaBuilder cb,
                                               List<String> paths) {
        Subquery<Long> sub = query.subquery(Long.class);
        var d = sub.from(Department.class);
        sub.select(d.get("id")).where(cb.or(paths.stream()
                .map(p -> cb.like(d.get("path"), p + "%"))
                .toArray(Predicate[]::new)));
        return sub;
    }

    // ------------------------------------------------------------------ 4.2 详情

    /** 用户详情：基本信息、角色、有效权限。本人可以看自己的；看别人需要在其部门持有 rbac:user:view。 */
    @Transactional(readOnly = true)
    public UserDetail detail(Operator op, Long userId) {
        User u = requireUser(userId);
        if (!op.userId().equals(userId)) {
            authz.requireInScope(op.userId(), PERM_VIEW, u.departmentId);
        }
        List<AssignmentRow> roles = userRoleMapper.findAssignmentsOfUser(userId);
        GrantSet grants = authz.loadGrants(userId).grants();
        List<EffectivePermission> perms = grants.grants().stream()
                .map(g -> new EffectivePermission(g.permissionCode(), g.roleCode(), g.roleName(), g.scopeDeptId(),
                        g.scopeDeptName(), g.selfOnly()))
                .sorted(Comparator.comparing(EffectivePermission::permissionCode)
                        .thenComparing(EffectivePermission::scopeDeptId))
                .toList();
        String deptName = departmentMapper.findById(u.departmentId).map(d -> d.deptName).orElse(null);
        return new UserDetail(toView(u, deptName), roles, AuthorizationService.holdsSuperAdmin(grants), perms);
    }

    // ------------------------------------------------------------------ 4.3 新增

    @Transactional
    public UserView create(Operator op, CreateUser req) {
        if (req == null || isBlank(req.username()) || isBlank(req.realName()) || req.departmentId() == null) {
            throw BizException.badRequest("username、realName、departmentId 必填");
        }
        String type = isBlank(req.userType()) ? User.EMPLOYEE : req.userType();
        validateTypeFields(type, req.employeeNo(), req.phone(), req.expiresAt());
        Department dept = departmentMapper.findById(req.departmentId())
                .orElseThrow(() -> BizException.notFound("部门"));
        try {
            authz.requireInScope(op.userId(), PERM_CREATE, dept.id);
        } catch (BizException e) {
            audit.record(op, "USER_CREATE", "USER", null, req.username(), "dept=" + dept.deptName,
                    AuditService.DENIED, "OUT_OF_MANAGE_SCOPE");
            throw e;
        }
        if (userMapper.existsByUsername(req.username())) {
            throw BizException.conflict(BizException.DUPLICATE, "用户名已存在（已删除用户的用户名也不能复用）");
        }
        if (!isBlank(req.employeeNo()) && userMapper.existsByEmployeeNo(req.employeeNo())) {
            throw BizException.conflict(BizException.DUPLICATE, "工号已存在");
        }
        User u = new User();
        u.companyId = dept.companyId;
        u.departmentId = dept.id;
        u.username = req.username().trim();
        u.realName = req.realName().trim();
        u.userType = type;
        u.employeeNo = blankToNull(req.employeeNo());
        u.phone = blankToNull(req.phone());
        u.position = blankToNull(req.position());
        u.expiresAt = req.expiresAt();
        if (isBlank(req.password())) {
            u.passwordHash = initialPasswordHash;
            u.mustChangePwd = true;
        } else {
            if (!AuthenticationService.meetsPolicy(req.password())) {
                throw BizException.badRequest("口令须至少 8 位，含大写、小写、数字与特殊字符");
            }
            u.passwordHash = passwordEncoder.encode(req.password());
        }
        userMapper.save(u);
        audit.record(op, "USER_CREATE", "USER", u.id, u.username,
                "type=" + type + " dept=" + dept.deptName, AuditService.SUCCESS, null);
        return toView(u, dept.deptName);
    }

    /** 临时用户没有工号、电话等员工信息，但必须有到期时间；正式员工必须有工号。 */
    static void validateTypeFields(String type, String employeeNo, String phone, LocalDateTime expiresAt) {
        switch (type) {
            case User.TEMPORARY -> {
                if (!isBlank(employeeNo) || !isBlank(phone)) {
                    throw BizException.badRequest("临时用户没有工号、电话");
                }
                if (expiresAt == null) {
                    throw BizException.badRequest("临时用户必须设置到期时间 expiresAt");
                }
            }
            case User.EMPLOYEE -> {
                if (isBlank(employeeNo)) {
                    throw BizException.badRequest("正式员工必须填写工号 employeeNo");
                }
            }
            case User.SYSTEM -> {
                // 业务系统账号：只用于调用鉴权接口
            }
            default -> throw BizException.badRequest("userType 只能是 EMPLOYEE、TEMPORARY 或 SYSTEM");
        }
    }

    // ------------------------------------------------------------------ 4.4 修改

    @Transactional
    public UserView update(Operator op, Long userId, UpdateUser req) {
        User u = requireUser(userId);
        requireNotDeleted(u);
        requireManageable(op, PERM_EDIT, u);
        Department dept = departmentMapper.findById(u.departmentId).orElseThrow(() -> BizException.notFound("部门"));
        if (req.departmentId() != null && !req.departmentId().equals(u.departmentId)) {
            Department target = departmentMapper.findById(req.departmentId())
                    .orElseThrow(() -> BizException.notFound("部门"));
            if (!target.companyId.equals(u.companyId)) {
                throw BizException.badRequest("不能把用户调到其他公司的部门");
            }
            authz.requireInScope(op.userId(), PERM_EDIT, target.id);   // 调入的部门也必须在管理范围内
            u.departmentId = target.id;
            dept = target;
        }
        if (req.realName() != null) {
            if (req.realName().isBlank()) {
                throw BizException.badRequest("realName 不能为空");
            }
            u.realName = req.realName().trim();
        }
        if (req.phone() != null) {
            if (User.TEMPORARY.equals(u.userType) && !req.phone().isBlank()) {
                throw BizException.badRequest("临时用户没有电话");
            }
            u.phone = blankToNull(req.phone());
        }
        if (req.position() != null) {
            u.position = blankToNull(req.position());
        }
        if (req.expiresAt() != null) {
            u.expiresAt = req.expiresAt();
        }
        userMapper.save(u);
        audit.record(op, "USER_UPDATE", "USER", u.id, u.username, "dept=" + dept.deptName,
                AuditService.SUCCESS, null);
        return toView(u, dept.deptName);
    }

    // ------------------------------------------------------------------ 4.5 启用、停用、删除

    @Transactional
    public UserView changeStatus(Operator op, Long userId, ChangeStatus req) {
        if (req == null || !(User.ACTIVE.equals(req.status()) || User.DISABLED.equals(req.status()))) {
            throw BizException.badRequest("status 只能是 ACTIVE 或 DISABLED；删除请用 DELETE /users/{id}");
        }
        User u = requireUser(userId);
        String failure = applyStatus(op, u, req.status(), req.reason());
        if (failure != null) {
            throw statusFailure(failure);
        }
        return toView(u, departmentMapper.findById(u.departmentId).map(d -> d.deptName).orElse(null));
    }

    @Transactional
    public void delete(Operator op, Long userId, String reason) {
        User u = requireUser(userId);
        String failure = applyStatus(op, u, User.DELETED, reason);
        if (failure != null) {
            throw statusFailure(failure);
        }
    }

    /**
     * 批量启用、停用、删除。逐个校验，通过的执行，不通过的列入失败清单（状态变更彼此独立，不做整批原子）。
     */
    @Transactional
    public BatchResult batchStatus(Operator op, BatchStatus req) {
        if (req == null || req.userIds() == null || req.userIds().isEmpty()
                || !Set.of(User.ACTIVE, User.DISABLED, User.DELETED).contains(req.status())) {
            throw BizException.badRequest("userIds 不能为空，status 取 ACTIVE / DISABLED / DELETED");
        }
        Set<Long> ids = new LinkedHashSet<>(req.userIds());
        Map<Long, User> found = new HashMap<>();
        userMapper.findAllById(ids).forEach(u -> found.put(u.id, u));
        List<Failure> failures = new ArrayList<>();
        int ok = 0;
        for (Long id : ids) {
            User u = found.get(id);
            String failure = (u == null) ? "NOT_FOUND" : applyStatus(op, u, req.status(), req.reason());
            if (failure == null) {
                ok++;
            } else {
                failures.add(new Failure(id, failure));
            }
        }
        return new BatchResult("PARTIAL", ok > 0, ids.size(), ok, 0, failures.size(), failures);
    }

    /**
     * 单个用户的状态变更。返回 null 表示成功，否则返回失败原因（并已记审计）。
     * 不能操作自己；持有超级管理员的账号只有超级管理员能改。
     */
    String applyStatus(Operator op, User u, String status, String reason) {
        String event = User.DELETED.equals(status) ? "USER_DELETE" : "USER_STATUS";
        String perm = User.DELETED.equals(status) ? PERM_DELETE : PERM_EDIT;
        String failure = null;
        if (User.DELETED.equals(u.status)) {
            failure = "ALREADY_DELETED";
        } else if (u.id.equals(op.userId())) {
            failure = "CANNOT_CHANGE_SELF";
        } else if (!authz.decide(op.userId(), perm, u.departmentId, false).allowed()) {
            failure = "OUT_OF_MANAGE_SCOPE";
        } else if (authz.isSuperAdmin(u.id) && !authz.isSuperAdmin(op.userId())) {
            failure = "TARGET_IS_SUPER_ADMIN";
        }
        if (failure != null) {
            audit.record(op, event, "USER", u.id, u.username, "status=" + status, AuditService.DENIED, failure);
            return failure;
        }
        String from = u.status;
        u.status = status;
        u.statusReason = reason;
        if (User.DELETED.equals(status)) {
            u.deletedAt = LocalDateTime.now(clock);
        }
        userMapper.save(u);
        audit.record(op, event, "USER", u.id, u.username, from + " -> " + status, AuditService.SUCCESS, reason);
        return null;
    }

    private static BizException statusFailure(String failure) {
        return switch (failure) {
            case "OUT_OF_MANAGE_SCOPE" -> BizException.forbidden(BizException.OUT_OF_MANAGE_SCOPE, "目标用户不在你的管理范围内");
            case "TARGET_IS_SUPER_ADMIN" -> BizException.forbidden(BizException.FORBIDDEN, "只有超级管理员能变更超级管理员的账号");
            case "CANNOT_CHANGE_SELF" -> BizException.conflict(BizException.STATE_CONFLICT, "不能停用或删除自己的账号");
            default -> BizException.conflict(BizException.STATE_CONFLICT, "用户已删除");
        };
    }

    // ------------------------------------------------------------------ 公共过程

    User requireUser(Long userId) {
        return userMapper.findById(userId).orElseThrow(() -> BizException.notFound("用户"));
    }

    static void requireNotDeleted(User u) {
        if (User.DELETED.equals(u.status)) {
            throw BizException.conflict(BizException.STATE_CONFLICT, "用户已删除");
        }
    }

    private void requireManageable(Operator op, String perm, User u) {
        authz.requireInScope(op.userId(), perm, u.departmentId);
        if (!u.id.equals(op.userId()) && authz.isSuperAdmin(u.id) && !authz.isSuperAdmin(op.userId())) {
            throw BizException.forbidden(BizException.FORBIDDEN, "只有超级管理员能修改超级管理员的账号");
        }
    }

    private Map<Long, String> deptNames(List<Long> ids) {
        Map<Long, String> names = new HashMap<>();
        departmentMapper.findAllById(new LinkedHashSet<>(ids)).forEach(d -> names.put(d.id, d.deptName));
        return names;
    }

    public static UserView toView(User u, String deptName) {
        return new UserView(u.id, u.username, u.realName, u.userType, u.employeeNo, u.phone, u.position, u.status,
                u.statusReason, u.companyId, u.departmentId, deptName, u.expiresAt, u.mustChangePwd, u.createdAt);
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
