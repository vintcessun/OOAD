package com.xmu.rbac.structured.service;

import com.xmu.rbac.structured.dto.CatalogDtos.MenuNode;
import com.xmu.rbac.structured.dto.CatalogDtos.SaveMenu;
import com.xmu.rbac.structured.dto.Decision;
import com.xmu.rbac.structured.dto.Operator;
import com.xmu.rbac.structured.entity.Menu;
import com.xmu.rbac.structured.entity.Resource;
import com.xmu.rbac.structured.mapper.MenuMapper;
import com.xmu.rbac.structured.mapper.ResourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 菜单管理与动态菜单。菜单也是一种资源：叶子菜单挂一个资源，用户持有「资源:view」才显示；
 * 目录节点在至少一个子菜单可见时显示。隐藏菜单不等于禁止访问——每个接口仍由拦截器独立鉴权。
 */
@Service
public class MenuService {

    private final MenuMapper menuMapper;
    private final ResourceMapper resourceMapper;
    private final AuthorizationService authz;
    private final AuditService audit;

    public MenuService(MenuMapper menuMapper, ResourceMapper resourceMapper, AuthorizationService authz,
                       AuditService audit) {
        this.menuMapper = menuMapper;
        this.resourceMapper = resourceMapper;
        this.authz = authz;
        this.audit = audit;
    }

    /** 当前用户可见的菜单树。 */
    @Transactional(readOnly = true)
    public List<MenuNode> myMenus(Operator op) {
        Map<Long, String> codes = resourceCodes();
        Map<String, Boolean> seen = new HashMap<>();
        return tree(codes, m -> {
            String code = codes.get(m.resourceId);
            return seen.computeIfAbsent(code, c -> {
                Decision d = authz.decide(op.userId(), c + ":view", null, false);
                return d.allowed();
            });
        }, true);
    }

    /** 菜单管理页：完整菜单树。 */
    @Transactional(readOnly = true)
    public List<MenuNode> all() {
        return tree(resourceCodes(), m -> true, false);
    }

    @Transactional
    public MenuNode create(Operator op, SaveMenu req) {
        Menu m = new Menu();
        apply(m, req);
        menuMapper.save(m);
        audit.record(op, "MENU_CREATE", "MENU", m.id, m.menuName, null, AuditService.SUCCESS, null);
        return node(m, resourceCodes(), List.of());
    }

    @Transactional
    public MenuNode update(Operator op, Long id, SaveMenu req) {
        Menu m = menuMapper.findById(id).orElseThrow(() -> BizException.notFound("菜单"));
        if (req.parentId() != null && createsCycle(id, req.parentId())) {
            throw BizException.badRequest("不能把菜单移到它自己或它的子菜单下面");
        }
        apply(m, req);
        menuMapper.save(m);
        audit.record(op, "MENU_UPDATE", "MENU", m.id, m.menuName, null, AuditService.SUCCESS, null);
        return node(m, resourceCodes(), List.of());
    }

    @Transactional
    public void delete(Operator op, Long id) {
        Menu m = menuMapper.findById(id).orElseThrow(() -> BizException.notFound("菜单"));
        if (menuMapper.existsByParentId(id)) {
            throw BizException.conflict(BizException.STATE_CONFLICT, "请先删除子菜单");
        }
        menuMapper.delete(m);
        audit.record(op, "MENU_DELETE", "MENU", m.id, m.menuName, null, AuditService.SUCCESS, null);
    }

    // ------------------------------------------------------------------ 内部过程

    private void apply(Menu m, SaveMenu req) {
        if (req == null || req.menuName() == null || req.menuName().isBlank()) {
            throw BizException.badRequest("menuName 必填");
        }
        if (req.parentId() != null && !menuMapper.existsById(req.parentId())) {
            throw BizException.notFound("上级菜单");
        }
        if (req.resourceId() != null) {
            Resource r = resourceMapper.findById(req.resourceId()).orElseThrow(() -> BizException.notFound("资源"));
            if (!Resource.MENU.equals(r.resourceType)) {
                throw BizException.badRequest("菜单只能挂 MENU 类型的资源");
            }
        }
        m.parentId = req.parentId();
        m.resourceId = req.resourceId();
        m.menuName = req.menuName().trim();
        m.routePath = req.routePath();
        m.componentKey = req.componentKey();
        m.icon = req.icon();
        m.sortOrder = req.sortOrder() == null ? 0 : req.sortOrder();
    }

    private boolean createsCycle(Long id, Long newParent) {
        Map<Long, Long> parent = new HashMap<>();
        menuMapper.findAll().forEach(x -> parent.put(x.id, x.parentId));
        for (Long p = newParent; p != null; p = parent.get(p)) {
            if (p.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private Map<Long, String> resourceCodes() {
        Map<Long, String> codes = new HashMap<>();
        resourceMapper.findAll().forEach(r -> codes.put(r.id, r.resourceCode));
        return codes;
    }

    /**
     * 自上而下组装菜单树。pruneEmptyDirs 为 true 时（动态菜单），叶子按 visible 过滤，没有可见子节点的目录不显示。
     */
    List<MenuNode> tree(Map<Long, String> codes, Predicate<Menu> visible, boolean pruneEmptyDirs) {
        List<Menu> menus = menuMapper.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<Menu>> children = new HashMap<>();
        List<Menu> roots = new ArrayList<>();
        for (Menu m : menus) {
            if (m.parentId == null) {
                roots.add(m);
            } else {
                children.computeIfAbsent(m.parentId, k -> new ArrayList<>()).add(m);
            }
        }
        return build(roots, children, codes, visible, pruneEmptyDirs);
    }

    private List<MenuNode> build(List<Menu> level, Map<Long, List<Menu>> children, Map<Long, String> codes,
                                 Predicate<Menu> visible, boolean prune) {
        List<MenuNode> out = new ArrayList<>();
        for (Menu m : level) {
            List<MenuNode> kids = build(children.getOrDefault(m.id, List.of()), children, codes, visible, prune);
            boolean show;
            if (m.resourceId == null) {
                show = !prune || !kids.isEmpty();
            } else {
                show = !prune || visible.test(m);
            }
            if (show) {
                out.add(node(m, codes, kids));
            }
        }
        return out;
    }

    private static MenuNode node(Menu m, Map<Long, String> codes, List<MenuNode> kids) {
        return new MenuNode(m.id, m.parentId, m.menuName, m.routePath, m.componentKey, m.icon, m.sortOrder,
                m.resourceId, m.resourceId == null ? null : codes.get(m.resourceId), kids);
    }
}
