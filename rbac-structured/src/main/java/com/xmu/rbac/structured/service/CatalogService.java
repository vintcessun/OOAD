package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.CatalogDtos.ActionView;
import com.xmu.rbac.structured.dto.CatalogDtos.CreatePermission;
import com.xmu.rbac.structured.dto.CatalogDtos.CreateResource;
import com.xmu.rbac.structured.dto.CatalogDtos.ModuleView;
import com.xmu.rbac.structured.dto.CatalogDtos.PermissionView;
import com.xmu.rbac.structured.dto.CatalogDtos.ResourceView;
import com.xmu.rbac.structured.dto.CatalogDtos.SystemView;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.dto.PageResult;
import com.xmu.rbac.structured.entity.Action;
import com.xmu.rbac.structured.entity.FunctionModule;
import com.xmu.rbac.structured.entity.Permission;
import com.xmu.rbac.structured.entity.Resource;
import com.xmu.rbac.structured.entity.SoftwareSystem;
import com.xmu.rbac.structured.mapper.ActionMapper;
import com.xmu.rbac.structured.mapper.FunctionModuleMapper;
import com.xmu.rbac.structured.mapper.PermissionMapper;
import com.xmu.rbac.structured.mapper.ResourceMapper;
import com.xmu.rbac.structured.mapper.SoftwareSystemMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 权限管理：区分 Resource、Action、Permission 三者（Permission = Resource × Action）。
 * 资源与权限的定义属于全平台的配置，新建只允许超级管理员（拦截器检查 rbac:permission:create，
 * 而该权限没有授给任何内置角色）。
 */
@Service
public class CatalogService {

    private static final Pattern RESOURCE_CODE = Pattern.compile("[a-z][a-z0-9-]*(:[a-z0-9][a-z0-9-]*)+");
    private static final Set<String> TYPES = Set.of(Resource.BUSINESS, Resource.MENU, Resource.API);

    private final SoftwareSystemMapper systemMapper;
    private final FunctionModuleMapper moduleMapper;
    private final ResourceMapper resourceMapper;
    private final ActionMapper actionMapper;
    private final PermissionMapper permissionMapper;
    private final AuditService audit;

    public CatalogService(SoftwareSystemMapper systemMapper, FunctionModuleMapper moduleMapper,
                          ResourceMapper resourceMapper, ActionMapper actionMapper,
                          PermissionMapper permissionMapper, AuditService audit) {
        this.systemMapper = systemMapper;
        this.moduleMapper = moduleMapper;
        this.resourceMapper = resourceMapper;
        this.actionMapper = actionMapper;
        this.permissionMapper = permissionMapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SystemView> systems() {
        List<FunctionModule> modules = moduleMapper.findAllByOrderBySystemIdAscSortOrderAsc();
        return systemMapper.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(s -> new SystemView(s.id, s.systemCode, s.systemName, modules.stream()
                        .filter(m -> m.systemId.equals(s.id))
                        .map(m -> new ModuleView(m.id, m.moduleCode, m.moduleName))
                        .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<ResourceView> resources(Long systemId, String type, String keyword, int page, int size) {
        var result = resourceMapper.search(systemId, blankToNull(type), blankToNull(keyword), pageable(page, size));
        return PageResult.of(result, row -> {
            Resource r = (Resource) row[0];
            return new ResourceView(r.id, r.resourceCode, r.resourceName, r.resourceType, r.description,
                    (String) row[1], (String) row[2]);
        });
    }

    @Transactional(readOnly = true)
    public List<ActionView> actions() {
        return actionMapper.findAllByOrderBySortOrderAsc().stream()
                .map(a -> new ActionView(a.id, a.actionCode, a.actionName))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<PermissionView> permissions(Long systemId, Long resourceId, String keyword, int page, int size) {
        var result = permissionMapper.search(systemId, resourceId, blankToNull(keyword), pageable(page, size));
        return PageResult.of(result, row -> {
            Permission p = (Permission) row[0];
            return new PermissionView(p.id, p.permissionCode, (String) row[1], (String) row[2], (String) row[3],
                    (String) row[4]);
        });
    }

    /** 新建资源，并为给出的每个操作建立权限。 */
    @Transactional
    public ResourceView createResource(Operator op, CreateResource req) {
        if (req == null || req.moduleId() == null || req.resourceCode() == null
                || !RESOURCE_CODE.matcher(req.resourceCode()).matches()
                || req.resourceName() == null || req.resourceName().isBlank()) {
            throw BizException.badRequest("moduleId、resourceName 必填；resourceCode 形如 oa:doc:draft（小写，冒号分段）");
        }
        String type = req.resourceType() == null ? Resource.BUSINESS : req.resourceType();
        if (!TYPES.contains(type)) {
            throw BizException.badRequest("resourceType 只能是 BUSINESS、MENU 或 API");
        }
        FunctionModule m = moduleMapper.findById(req.moduleId()).orElseThrow(() -> BizException.notFound("功能模块"));
        SoftwareSystem s = systemMapper.findById(m.systemId).orElseThrow();
        if (!req.resourceCode().startsWith(s.systemCode + ":")) {
            throw BizException.badRequest("资源编码必须以所属业务系统编码「" + s.systemCode + ":」开头");
        }
        if (resourceMapper.existsByResourceCode(req.resourceCode())) {
            throw BizException.conflict(BizException.DUPLICATE, "资源编码已存在");
        }
        Resource r = new Resource();
        r.moduleId = m.id;
        r.resourceCode = req.resourceCode();
        r.resourceName = req.resourceName().trim();
        r.resourceType = type;
        r.description = req.description();
        resourceMapper.save(r);
        if (req.actionCodes() != null) {
            for (String a : req.actionCodes()) {
                addPermission(r, a);
            }
        }
        audit.record(op, "RESOURCE_CREATE", "RESOURCE", r.id, r.resourceCode, "actions=" + req.actionCodes(),
                AuditService.SUCCESS, null);
        return new ResourceView(r.id, r.resourceCode, r.resourceName, r.resourceType, r.description,
                m.moduleName, s.systemName);
    }

    @Transactional
    public PermissionView createPermission(Operator op, CreatePermission req) {
        if (req == null || req.resourceId() == null || req.actionCode() == null) {
            throw BizException.badRequest("resourceId、actionCode 必填");
        }
        Resource r = resourceMapper.findById(req.resourceId()).orElseThrow(() -> BizException.notFound("资源"));
        Permission p = addPermission(r, req.actionCode());
        Action a = actionMapper.findById(p.actionId).orElseThrow();
        audit.record(op, "PERMISSION_CREATE", "RESOURCE", r.id, r.resourceCode, "permission=" + p.permissionCode,
                AuditService.SUCCESS, null);
        return new PermissionView(p.id, p.permissionCode, r.resourceName, r.resourceType, a.actionName, null);
    }

    private Permission addPermission(Resource r, String actionCode) {
        Action a = actionMapper.findByActionCode(actionCode).orElseThrow(() -> BizException.notFound("操作 " + actionCode));
        if (permissionMapper.existsByResourceIdAndActionId(r.id, a.id)) {
            throw BizException.conflict(BizException.DUPLICATE, "权限 " + r.resourceCode + ":" + actionCode + " 已存在");
        }
        Permission p = new Permission();
        p.resourceId = r.id;
        p.actionId = a.id;
        p.permissionCode = r.resourceCode + ":" + a.actionCode;
        return permissionMapper.save(p);
    }

    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
