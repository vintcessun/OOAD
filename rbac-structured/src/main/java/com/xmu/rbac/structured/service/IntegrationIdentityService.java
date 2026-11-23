package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.Actor;
import com.xmu.rbac.structured.dto.IntegrationViews.AuthoritiesVO;
import com.xmu.rbac.structured.dto.IntegrationViews.DepartmentUserVO;
import com.xmu.rbac.structured.dto.IntegrationViews.DepartmentVO;
import com.xmu.rbac.structured.dto.IntegrationViews.IdentityUserVO;
import com.xmu.rbac.structured.entity.Department;
import com.xmu.rbac.structured.entity.Permission;
import com.xmu.rbac.structured.entity.User;
import com.xmu.rbac.structured.mapper.DepartmentMapper;
import com.xmu.rbac.structured.mapper.Store;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 课程目录与私有能力查询，按入口验证课程权限和服务来源。 */
@Service
@Transactional(readOnly = true)
public class IntegrationIdentityService {
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final Store store;
    private final DepartmentMapper departments;

    public IntegrationIdentityService(AuthorizationService authorization, AuditService audit, Store store,
            DepartmentMapper departments) {
        this.authorization = authorization;
        this.audit = audit;
        this.store = store;
        this.departments = departments;
    }

    /** 从已验证aud计算真实API能力，不能从菜单或操作准入推导CC等细粒度权限。 */
    public AuthoritiesVO authorities(Actor actor, String serviceKey) {
        audit.trusted(serviceKey);
        AuthorizationService.Snapshot snapshot = authorization.snapshot(actor.userId(), actor.systemCode());
        TreeSet<String> values = new TreeSet<>();
        for (Long id : snapshot.permissions()) {
            Permission permission = authorization.require(Permission.class, id);
            if ("API".equals(permission.kind) && "ENABLED".equals(permission.status)) {
                values.add(permission.code);
            }
        }
        authorization.courseRoleCodes(snapshot).forEach(identity -> values.add("ROLE_" + identity));
        return new AuthoritiesVO(java.util.List.copyOf(values));
    }

    /** 只有拥有教师创建能力的受信管理员可查询最少身份字段。 */
    public IdentityUserVO lookup(Actor actor, String serviceKey, String subject) {
        audit.trusted(serviceKey);
        return lookup(actor, subject);
    }

    /** 使用课程管理员的有效 TEC 能力查询稳定身份。 */
    public IdentityUserVO lookup(Actor actor, String subject) {
        requireTeacherAdministration(actor);
        User target = store.one(User.class, "e.subject=?1", subject);
        if (target == null || !"ACTIVE".equals(target.status)) {
            throw new BizException(404, "IDENTITY_USER_NOT_FOUND");
        }
        return new IdentityUserVO(target.subject, target.name, target.no == null ? "" : target.no);
    }

    /** 返回指定层级的启用部门，并标识是否可以继续展开。 */
    public Map<String, Object> departments(Actor actor, Long parentId, String keyword, int page, int size) {
        requireTeacherAdministration(actor);
        if (parentId != null) {
            requireDepartment(parentId);
        }
        return store.page(Department.class,
                "e.status='ENABLED' and ((?1 is null and e.parentId is null) or e.parentId=?1)"
                        + " and (lower(e.code) like lower(?2) escape '!' or lower(e.name) like lower(?2) escape '!')",
                java.util.Arrays.asList(parentId, keywordPattern(keyword)), page, size,
                dep -> new DepartmentVO(dep.id, dep.code, dep.name, dep.parentId,
                        departments.existsByParentIdAndStatus(dep.id, "ENABLED")), "e.id");
    }

    /** 只查询指定部门的活跃用户，可按姓名、工号或学号搜索。 */
    public Map<String, Object> departmentUsers(Actor actor, Long departmentId, String keyword, int page, int size) {
        requireTeacherAdministration(actor);
        Department department = requireDepartment(departmentId);
        return store.page(User.class,
                "e.status='ACTIVE' and e.departmentId=?1"
                        + " and (lower(e.name) like lower(?2) escape '!' or lower(e.no) like lower(?2) escape '!')",
                List.of(departmentId, keywordPattern(keyword)), page, size,
                user -> new DepartmentUserVO(user.subject, user.no == null ? "" : user.no, user.name,
                        department.id, department.name), "e.id");
    }

    /** 仅课程系统的管理员拥有目录查询入口，且 TEC 必须在 API 命名空间生效。 */
    private void requireTeacherAdministration(Actor actor) {
        if (!"course-project-management".equals(actor.systemCode())) {
            throw BizException.forbidden("FORBIDDEN");
        }
        AuthorizationService.Snapshot snapshot = authorization.snapshot(actor.userId(), actor.systemCode());
        if (!authorization.courseRoleCodes(snapshot).contains("admin")
                || !authorization.permission(actor.userId(), actor.systemCode(), "API", "TEC")) {
            throw BizException.forbidden("FORBIDDEN");
        }
    }

    private Department requireDepartment(Long id) {
        Department department = store.get(Department.class, id);
        if (department == null || !"ENABLED".equals(department.status)) {
            throw new BizException(404, "NOT_FOUND");
        }
        return department;
    }

    /** 将搜索词作为字面量匹配，转义 SQL LIKE 通配符。 */
    private static String keywordPattern(String keyword) {
        return keyword == null ? "%" : "%" + keyword.strip()
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
