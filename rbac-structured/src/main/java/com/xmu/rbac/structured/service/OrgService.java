package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.CatalogDtos.CompanyView;
import com.xmu.rbac.structured.dto.CatalogDtos.DeptNode;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.dto.Scope;
import com.xmu.rbac.structured.entity.Company;
import com.xmu.rbac.structured.entity.Department;
import com.xmu.rbac.structured.mapper.CompanyMapper;
import com.xmu.rbac.structured.mapper.DepartmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织浏览：公司列表与部门树。只返回操作者持有 rbac:org:view 的部门及其下级
 * （以及通往它们的祖先节点，方便在树上定位），批量授权页面据此选择目标范围。
 */
@Service
public class OrgService {

    public static final String PERM_VIEW = "rbac:org:view";

    private final CompanyMapper companyMapper;
    private final DepartmentMapper departmentMapper;
    private final AuthorizationService authz;

    public OrgService(CompanyMapper companyMapper, DepartmentMapper departmentMapper, AuthorizationService authz) {
        this.companyMapper = companyMapper;
        this.departmentMapper = departmentMapper;
        this.authz = authz;
    }

    @Transactional(readOnly = true)
    public List<CompanyView> companies(Operator op) {
        Scope scope = authz.scopeOf(op.userId(), PERM_VIEW);
        List<CompanyView> out = new ArrayList<>();
        for (Company c : companyMapper.findAll()) {
            Department root = departmentMapper.findByCompanyIdAndParentIdIsNull(c.id).orElse(null);
            if (root == null) {
                continue;
            }
            boolean visible = scope.all() || scope.paths().stream().anyMatch(p -> p.startsWith(root.path));
            if (visible) {
                out.add(new CompanyView(c.id, c.companyCode, c.companyName, root.id));
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<DeptNode> tree(Operator op, Long companyId) {
        if (!companyMapper.existsById(companyId)) {
            throw BizException.notFound("公司");
        }
        Scope scope = authz.scopeOf(op.userId(), PERM_VIEW);
        List<Department> all = departmentMapper.findByCompanyIdOrderByLevelAscSortOrderAscIdAsc(companyId);
        // 可见：在范围内，或是范围内某节点的祖先
        List<Department> visible = all.stream()
                .filter(d -> scope.all() || AuthorizationService.inScope(scope, d.path)
                        || scope.paths().stream().anyMatch(p -> p.startsWith(d.path)))
                .toList();
        Map<Long, List<Department>> children = new HashMap<>();
        List<Department> roots = new ArrayList<>();
        for (Department d : visible) {
            if (d.parentId == null || visible.stream().noneMatch(x -> x.id.equals(d.parentId))) {
                roots.add(d);
            } else {
                children.computeIfAbsent(d.parentId, k -> new ArrayList<>()).add(d);
            }
        }
        return build(roots, children);
    }

    private static List<DeptNode> build(List<Department> level, Map<Long, List<Department>> children) {
        return level.stream()
                .map(d -> new DeptNode(d.id, d.parentId, d.deptName, d.level, d.managerName, d.managerUserId,
                        build(children.getOrDefault(d.id, List.of()), children)))
                .toList();
    }
}
