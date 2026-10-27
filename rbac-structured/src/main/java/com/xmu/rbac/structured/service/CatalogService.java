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

    // 资源编码至少两段，以冒号分隔；格式校验之外，创建时还检查所属系统前缀和全局唯一性。
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

    /** D09：一次查全模块与系统，在内存中按 systemId 归组，组装前端系统/模块树；不分页。 */
    @Transactional(readOnly = true)
    public List<SystemView> systems() {
        // 先批量查模块，再遍历有序系统，避免为每个系统分别发一条模块查询。
        List<FunctionModule> modules = moduleMapper.findAllByOrderBySystemIdAscSortOrderAsc();
        return systemMapper.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(s -> new SystemView(s.id, s.systemCode, s.systemName, modules.stream()
                        .filter(m -> m.systemId.equals(s.id))
                        .map(m -> new ModuleView(m.id, m.moduleCode, m.moduleName))
                        .toList()))
                .toList();
    }

    /** D10：资源分页查询；关键字匹配资源编码/名称，Mapper 连模块和系统补齐显示名称。 */
    @Transactional(readOnly = true)
    public PageResult<ResourceView> resources(Long systemId, String type, String keyword, int page, int size) {
        // 1. 空白条件归一为 null（不筛选），修正分页边界；筛选和分页在数据库完成。
        var result = resourceMapper.search(systemId, blankToNull(type), blankToNull(keyword), pageable(page, size));
        // 2. search 投影固定为 [Resource, moduleName, systemName]，PageResult 保留 total/page/size。
        return PageResult.of(result, row -> {
            Resource r = (Resource) row[0];
            return new ResourceView(r.id, r.resourceCode, r.resourceName, r.resourceType, r.description,
                    (String) row[1], (String) row[2]);
        });
    }

    /** D12：读取已有操作词汇表，按 sortOrder 排序；本方法不创建操作，也不为所有资源自动生成权限。 */
    @Transactional(readOnly = true)
    public List<ActionView> actions() {
        return actionMapper.findAllByOrderBySortOrderAsc().stream()
                .map(a -> new ActionView(a.id, a.actionCode, a.actionName))
                .toList();
    }

    /** D13：权限目录查询，也供角色授予弹窗使用；目录中“可查询”不代表操作者“可授出”。 */
    @Transactional(readOnly = true)
    public PageResult<PermissionView> permissions(Long systemId, Long resourceId, String keyword, int page, int size) {
        // 1. Mapper 连权限、资源、操作、模块、系统；按系统/资源筛选，关键字匹配权限码或资源名。
        var result = permissionMapper.search(systemId, resourceId, blankToNull(keyword), pageable(page, size));
        // 2. 投影为 [Permission, resourceName, resourceType, actionName, systemName]，返回分页 DTO。
        return PageResult.of(result, row -> {
            Permission p = (Permission) row[0];
            return new PermissionView(p.id, p.permissionCode, (String) row[1], (String) row[2], (String) row[3],
                    (String) row[4]);
        });
    }

    /**
     * D11：新建资源，可同时建立多个“资源 × 操作”权限；不创建菜单节点、不授予角色。
     * 记忆顺序：校验字段 → 查模块/系统 → 检查前缀/重复 → 保存资源 → 建权限 → 审计 → 返回。
     */
    @Transactional
    public ResourceView createResource(Operator op, CreateResource req) {
        // 1. 检查必填字段和资源编码格式；不将不合法输入静默修正成另一资源编码。
        if (req == null || req.moduleId() == null || req.resourceCode() == null
                || !RESOURCE_CODE.matcher(req.resourceCode()).matches()
                || req.resourceName() == null || req.resourceName().isBlank()) {
            throw BizException.badRequest("moduleId、resourceName 必填；resourceCode 形如 oa:doc:draft（小写，冒号分段）");
        }
        // 2. 未传类型默认 BUSINESS；MENU 是菜单可挂接资源，API 是接口能力资源。
        String type = req.resourceType() == null ? Resource.BUSINESS : req.resourceType();
        if (!TYPES.contains(type)) {
            throw BizException.badRequest("resourceType 只能是 BUSINESS、MENU 或 API");
        }
        // 3. moduleId 决定归属系统；例如 oa 模块下只能建以 oa: 开头的资源。
        FunctionModule m = moduleMapper.findById(req.moduleId()).orElseThrow(() -> BizException.notFound("功能模块"));
        SoftwareSystem s = systemMapper.findById(m.systemId).orElseThrow();
        if (!req.resourceCode().startsWith(s.systemCode + ":")) {
            throw BizException.badRequest("资源编码必须以所属业务系统编码「" + s.systemCode + ":」开头");
        }
        // 4. 先查重给出业务提示，数据库 resource_code 唯一键仍负责挡住并发重复。
        if (resourceMapper.existsByResourceCode(req.resourceCode())) {
            throw BizException.conflict(BizException.DUPLICATE, "资源编码已存在");
        }
        // 5. 保存 sys_resource，获得主键供后续权限引用；名称去首尾空白。
        Resource r = new Resource();
        r.moduleId = m.id;
        r.resourceCode = req.resourceCode();
        r.resourceName = req.resourceName().trim();
        r.resourceType = type;
        r.description = req.description();
        resourceMapper.save(r);
        // 6. 逐操作调用公共建权限过程；省略/空集合只建资源。
        // 未知操作抛 404，重复操作可能抛 409；这里不去重，异常会使本事务的资源和权限一起回滚。
        if (req.actionCodes() != null) {
            for (String a : req.actionCodes()) {
                addPermission(r, a);
            }
        }
        // 7. 记录资源创建事件，返回已知模块/系统名称；不额外重查目录，也不更新授权版本。
        audit.record(op, "RESOURCE_CREATE", "RESOURCE", r.id, r.resourceCode, "actions=" + req.actionCodes(),
                AuditService.SUCCESS, null);
        return new ResourceView(r.id, r.resourceCode, r.resourceName, r.resourceType, r.description,
                m.moduleName, s.systemName);
    }

    /** D14：给已有资源补一个操作权限，复用 addPermission；定义创建后还须另行授予角色。 */
    @Transactional
    public PermissionView createPermission(Operator op, CreatePermission req) {
        // 1. 检查 ID/操作码非 null，再查资源是否存在；具体操作码是否存在由 addPermission 检查。
        if (req == null || req.resourceId() == null || req.actionCode() == null) {
            throw BizException.badRequest("resourceId、actionCode 必填");
        }
        Resource r = resourceMapper.findById(req.resourceId()).orElseThrow(() -> BizException.notFound("资源"));
        // 2. 建立唯一的资源-操作组合；再取操作显示名，用于返回页面。
        Permission p = addPermission(r, req.actionCode());
        Action a = actionMapper.findById(p.actionId).orElseThrow();
        // 3. 只记权限定义创建事件。此返回未查询所属系统，因此 systemName 为 null；列表查询会补齐。
        audit.record(op, "PERMISSION_CREATE", "RESOURCE", r.id, r.resourceCode, "permission=" + p.permissionCode,
                AuditService.SUCCESS, null);
        return new PermissionView(p.id, p.permissionCode, r.resourceName, r.resourceType, a.actionName, null);
    }

    /** D11/D14 共用的建权限过程，在调用者事务中执行：查已有操作 → 查组合是否重复 → 生成权限码并保存。 */
    private Permission addPermission(Resource r, String actionCode) {
        // actionCode 必须来自 sys_action；传入新字符串并不会自动扩展操作词汇表。
        Action a = actionMapper.findByActionCode(actionCode).orElseThrow(() -> BizException.notFound("操作 " + actionCode));
        if (permissionMapper.existsByResourceIdAndActionId(r.id, a.id)) {
            throw BizException.conflict(BizException.DUPLICATE, "权限 " + r.resourceCode + ":" + actionCode + " 已存在");
        }
        // permission_code 由服务端拼接，resource_id/action_id 分别保留外键，不能由前端任意指定权限码。
        // 数据库同时约束权限码唯一、资源-操作组合唯一；仅 save 权限定义，不写 sys_role_permission。
        Permission p = new Permission();
        p.resourceId = r.id;
        p.actionId = a.id;
        p.permissionCode = r.resourceCode + ":" + a.actionCode;
        return permissionMapper.save(p);
    }

    /** 目录分页保护：page 至少 0，size 限制为 1～500；Controller 默认每页 50。 */
    private static PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500));
    }

    /** JPQL 用 null 表示不筛选；非空条件去首尾空白，避免空输入被当成实际过滤条件。 */
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
