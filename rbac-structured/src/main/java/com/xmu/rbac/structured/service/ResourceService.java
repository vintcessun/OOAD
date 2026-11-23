package com.xmu.rbac.structured.service;
import static com.xmu.rbac.structured.service.Views.*;

import com.xmu.rbac.structured.dto.*;
import com.xmu.rbac.structured.entity.*;
import com.xmu.rbac.structured.mapper.Store;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** UI/API独立资源事务脚本；显式映射仅用于覆盖追踪。 */
@Service
public class ResourceService {
    private final Store db;
    private final AuthorizationService az;
    private final AuditService audit;
    private final RouteRegistry routes;
    public ResourceService(
        Store db, AuthorizationService az, AuditService audit, RouteRegistry routes) {
        this.db = db;
        this.az = az;
        this.audit = audit;
        this.routes = routes;
    }
    private GrantVersion begin(Actor a) {
        GrantVersion g = az.lock();
        az.center(a, "rbac:resource:edit");
        az.root(a);
        az.captureUpper(a);
        return g;
    }
    private void finish(GrantVersion g, Actor a, String event, Object id) {
        db.flush();
        az.preserveAdministrator();
        g.version++;
        audit.success(a, event, String.valueOf(id));
    }
    private String system(Long id) {
        return az.require(SoftwareSystem.class, id).code;
    }
    private String permission(Long id) {
        return id == null ? null : az.require(Permission.class, id).code;
    }
    private Long permission(Long system, String kind, String code) {
        if (code == null)
            return null;
        Permission p = db.one(
            Permission.class, "e.systemId=?1 and e.kind=?2 and e.code=?3", system, kind, code);
        if (p == null)
            throw BizException.missing("PERMISSION_NOT_FOUND");
        return p.id;
    }
    private Map<String, Object> uiView(UiResource r) {
        return Views.ui(r, system(r.systemId), permission(r.permissionId));
    }
    private Map<String, Object> apiView(ApiOperation r) {
        return api(r, system(r.systemId), permission(r.permissionId));
    }
    /** 执行ui操作，保持服务到持久化层的单向依赖。 */
    public Object ui(Actor a, String code) {
        az.center(a, "rbac:resource:view");
        return tree(db.where(UiResource.class, "e.systemId=?1", az.system(code).id)
                .stream()
                .sorted(
                    Comparator.comparing((UiResource u) -> u.sortOrder).thenComparing(u -> u.id))
                .map(this::uiView)
                .toList());
    }
    /** 按UI权限裁剪菜单树，不将父菜单许可赋予子按钮。 */
    public Object menus(Actor a) {
        SoftwareSystem system = az.system(a.systemCode());
        AuthorizationService.Snapshot snapshot = az.snapshot(a.userId(), a.systemCode());
        List<UiResource> nodes = db.where(UiResource.class, "e.systemId=?1", system.id);
        Set<Long> visible = new HashSet<>();
        Map<Long, UiResource> byId = new HashMap<>();
        for (UiResource n : nodes) byId.put(n.id, n);
        for (UiResource n : nodes)
            if ("ENABLED".equals(n.status) && n.permissionId != null
                && snapshot.permissions().contains(n.permissionId)) {
                boolean valid = true;
                UiResource parent = n;
                Set<Long> visited = new HashSet<>();
                while (parent.parentId != null && visited.add(parent.id)) {
                    parent = byId.get(parent.parentId);
                    if (parent == null || !"ENABLED".equals(parent.status)) {
                        valid = false;
                        break;
                    }
                }
                if (valid) {
                    visible.add(n.id);
                    parent = n;
                    while (parent.parentId != null) {
                        parent = byId.get(parent.parentId);
                        visible.add(parent.id);
                    }
                }
            }
        return tree(nodes.stream()
                .filter(n -> visible.contains(n.id))
                .sorted(
                    Comparator.comparing((UiResource u) -> u.sortOrder).thenComparing(u -> u.id))
                .map(this::uiView)
                .toList());
    }
    /** 执行saveUi操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object saveUi(Actor a, String code, Long id, Inputs.Ui input) {
        GrantVersion g = begin(a);
        if (input.type() == null || input.code() == null || input.name() == null
            || input.permissionCode() == null)
            throw BizException.bad("VALIDATION_ERROR");
        UiResource r = id == null ? new UiResource() : az.require(UiResource.class, id);
        Long system = id == null ? az.system(code).id : r.systemId;
        Long parent = input.parentId();
        if (parent != null) {
            UiResource p = az.require(UiResource.class, parent);
            if (!p.systemId.equals(system))
                throw BizException.bad("CROSS_SYSTEM_RESOURCE");
            if (!"MENU".equals(p.type))
                throw BizException.bad("BUTTON_CANNOT_HAVE_CHILDREN");
            Set<Long> seen = new HashSet<>();
            while (p != null) {
                if (Objects.equals(p.id, id) || !seen.add(p.id))
                    throw BizException.conflict("UI_RESOURCE_CYCLE");
                p = p.parentId == null ? null : az.require(UiResource.class, p.parentId);
            }
        }
        if ("BUTTON".equals(input.type()) && parent == null)
            throw BizException.bad("BUTTON_PARENT_REQUIRED");
        if (id != null && "BUTTON".equals(input.type())
            && !db.where(UiResource.class, "e.parentId=?1", id).isEmpty())
            throw BizException.conflict("RESOURCE_HAS_CHILDREN");
        r.systemId = system;
        r.parentId = parent;
        r.type = input.type();
        r.code = input.code();
        r.name = input.name();
        r.route = input.route();
        r.component = input.component();
        r.sortOrder = input.sortOrder() == null ? 0 : input.sortOrder();
        r.status = input.status() == null ? "ENABLED" : input.status();
        r.permissionId = permission(system, "UI", input.permissionCode());
        if (id == null)
            db.save(r);
        finish(g, a, "UI_RESOURCE_SAVED", r.id);
        return uiView(r);
    }
    /** 执行deleteUi操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public void deleteUi(Actor a, Long id) {
        GrantVersion g = begin(a);
        UiResource r = az.require(UiResource.class, id);
        if (!db.where(UiResource.class, "e.parentId=?1", id).isEmpty()
            || !db.where(UiApiMapping.class, "e.uiResourceId=?1", id).isEmpty())
            throw BizException.conflict("RESOURCE_IN_USE");
        db.remove(r);
        finish(g, a, "UI_RESOURCE_DELETED", id);
    }
    /** 执行apis操作，保持服务到持久化层的单向依赖。 */
    public Object apis(Actor a, String code, String keyword, int page, int size) {
        az.center(a, "rbac:resource:view");
        if (page < 1 || size < 1 || size > 100)
            throw BizException.bad("INVALID_PAGE");
        List<Object> args = new ArrayList<>(List.of(az.system(code).id));
        String clause = "e.systemId=?1";
        if (keyword != null) {
            args.add("%" + keyword + "%");
            clause += " and (e.operationId like ?2 or e.pathTemplate like ?2)";
        }
        return db.page(ApiOperation.class, clause, args, page, size, r -> apiView(r), "e.id");
    }
    /** 执行saveApi操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object saveApi(Actor a, String code, Long id, Inputs.Api input) {
        GrantVersion g = begin(a);
        String requiredRole = input.requiredRole();
        if (input.operationId() == null || input.method() == null || input.pathTemplate() == null
            || input.accessPolicy() == null || input.enabled() == null
            || (requiredRole != null && requiredRole.isBlank()))
            throw BizException.bad("VALIDATION_ERROR");
        ApiOperation r = id == null ? new ApiOperation() : az.require(ApiOperation.class, id);
        SoftwareSystem target = id == null ? az.system(code) : az.require(SoftwareSystem.class, r.systemId);
        Long system = target.id;
        String systemCode = target.code;
        if ("rbac-center".equals(systemCode)) {
            RouteRegistry.Route existing = id == null ? null : routes.operation(r.operationId);
            RouteRegistry.Route expected = routes.operation(input.operationId());
            if (existing != null && !r.operationId.equals(input.operationId()) || expected == null
                || !expected.method().equals(input.method())
                || !expected.path().equals(input.pathTemplate())
                || !Objects.equals(expected.permission(), input.permissionCode())
                || !input.enabled() || !Objects.equals(expected.policy(), input.accessPolicy())
                || !Objects.equals(expected.requiredRole(), requiredRole))
                throw BizException.bad("CENTER_OPERATION_IMMUTABLE");
        }
        Long pid = permission(system, "API", input.permissionCode());
        boolean hasCondition = pid != null || requiredRole != null;
        if ("PROTECTED".equals(input.accessPolicy()) && !hasCondition)
            throw BizException.bad("API_REQUIREMENT_MISSING");
        if (hasCondition && !az.superAdmin(a)) {
            if (!"ENABLED".equals(target.status))
                throw BizException.forbidden("PRIVILEGE_ESCALATION");
            AuthorizationService.Snapshot actor = az.snapshot(a.userId(), systemCode);
            if (pid != null && !actor.permissions().contains(pid))
                throw BizException.forbidden("PRIVILEGE_ESCALATION");
            if (requiredRole != null && !az.roleCodes(actor).contains(requiredRole))
                throw BizException.forbidden("PRIVILEGE_ESCALATION");
        }
        r.systemId = system;
        r.operationId = input.operationId();
        r.method = input.method();
        r.pathTemplate = input.pathTemplate();
        r.permissionId = pid;
        r.requiredRole = requiredRole;
        r.accessPolicy = input.accessPolicy();
        r.enabled = input.enabled();
        if (id == null)
            db.save(r);
        finish(g, a, "API_OPERATION_SAVED", r.operationId);
        return apiView(r);
    }
    /** 执行mappings操作，保持服务到持久化层的单向依赖。 */
    public Object mappings(Actor a, String code) {
        az.center(a, "rbac:resource:view");
        Long system = az.system(code).id;
        Set<Long> ui = new HashSet<>(
            db.where(UiResource.class, "e.systemId=?1", system).stream().map(u -> u.id).toList());
        Set<Long> api = new HashSet<>(
            db.where(ApiOperation.class, "e.systemId=?1", system).stream().map(u -> u.id).toList());
        List<Map<String, Object>> mappings = new ArrayList<>();
        for (UiApiMapping m : db.all(UiApiMapping.class))
            if (ui.contains(m.uiResourceId) && api.contains(m.apiOperationId)) {
                mappings.add(
                    map("uiResourceId", m.uiResourceId, "apiOperationId", m.apiOperationId));
            }
        Set<Long> mappedUi = new HashSet<>(), mappedApi = new HashSet<>();
        for (Map<String, Object> m : mappings) {
            mappedUi.add((Long) m.get("uiResourceId"));
            mappedApi.add((Long) m.get("apiOperationId"));
        }
        return map("mappings", mappings, "unmappedUi",
            ui.stream()
                .filter(id -> !mappedUi.contains(id))
                .sorted()
                .map(id -> uiView(az.require(UiResource.class, id)))
                .toList(),
            "unmappedApi",
            api.stream()
                .filter(id -> !mappedApi.contains(id))
                .sorted()
                .map(id -> apiView(az.require(ApiOperation.class, id)))
                .toList());
    }
    /** 执行replaceMappings操作，保持服务到持久化层的单向依赖。 */
    @Transactional
    public Object replaceMappings(Actor a, String code, Inputs.Mappings input) {
        GrantVersion g = begin(a);
        Long system = az.system(code).id;
        for (Inputs.Mapping m : input.mappings())
            if (!az.require(UiResource.class, m.uiResourceId()).systemId.equals(system)
                || !az.require(ApiOperation.class, m.apiOperationId()).systemId.equals(system))
                throw BizException.bad("CROSS_SYSTEM_MAPPING");
        Set<Long> ui = new HashSet<>(
            db.where(UiResource.class, "e.systemId=?1", system).stream().map(u -> u.id).toList());
        for (UiApiMapping m : db.all(UiApiMapping.class))
            if (ui.contains(m.uiResourceId))
                db.remove(m);
        db.flush();
        for (Inputs.Mapping m : new LinkedHashSet<>(input.mappings())) {
            UiApiMapping row = new UiApiMapping();
            row.uiResourceId = m.uiResourceId();
            row.apiOperationId = m.apiOperationId();
            db.save(row);
        }
        finish(g, a, "UI_API_MAPPINGS_REPLACED", code);
        return mappings(a, code);
    }
    /** 执行check操作，保持服务到持久化层的单向依赖。 */
    @Transactional(readOnly = true)
    public Object check(Actor a, Inputs.Check input, boolean explain) {
        az.center(a, "rbac:authz:invoke");
        boolean uiRequest = "UI".equals(input.kind());
        if (uiRequest ? input.permission() == null || input.operationId() != null
                      : input.operationId() == null || input.permission() != null)
            throw BizException.bad("INVALID_AUTHORIZATION_REQUEST");
        Long user = input.userId() == null ? a.userId() : input.userId();
        User target = az.require(User.class, user);
        az.scope(a, target.departmentId);
        SoftwareSystem targetSystem = az.system(input.systemCode());
        if (!"ACTIVE".equals(target.status) || !"ENABLED".equals(targetSystem.status)) {
            String reason = !"ACTIVE".equals(target.status) ? "USER_DISABLED" : "SYSTEM_DISABLED";
            Map<String, Object> denied = map("allowed", false, "reason", reason, "traceId",
                Optional.ofNullable(org.slf4j.MDC.get("traceId"))
                    .orElseGet(() -> UUID.randomUUID().toString()));
            if (explain) {
                denied.put("trace", List.of("ACTOR_AUTHENTICATED", reason));
                denied.put("effective",
                    az.effective(new AuthorizationService.Snapshot(
                                     List.of(), List.of(), List.of(), List.of()),
                        input.systemCode()));
            }
            return denied;
        }
        boolean allowed;
        if ("UI".equals(input.kind())) {
            if (input.permission() == null || input.operationId() != null)
                throw BizException.bad("INVALID_AUTHORIZATION_REQUEST");
            allowed = az.permission(user, input.systemCode(), "UI", input.permission());
        } else {
            if (input.operationId() == null || input.permission() != null)
                throw BizException.bad("INVALID_AUTHORIZATION_REQUEST");
            allowed = az.api(user, input.systemCode(), input.operationId());
        }
        Map<String, Object> result =
            map("allowed", allowed, "reason", allowed ? "ALLOW" : "DENY", "traceId",
                Optional.ofNullable(org.slf4j.MDC.get("traceId"))
                    .orElseGet(() -> UUID.randomUUID().toString()));
        if (explain) {
            result.put("trace",
                List.of("TOKEN_VALID", "USER_SYSTEM_ACTIVE",
                    allowed ? "REQUIREMENT_SATISFIED" : "REQUIREMENT_NOT_SATISFIED"));
            result.put("effective",
                az.effective(az.snapshot(user, input.systemCode()), input.systemCode()));
        }
        return result;
    }

    /** PATCH保留缺省字段，并区分显式null清空关联/可空属性。 */
    @Transactional
    public Object patchUi(Actor a, Long id, Inputs.UiPatch input) {
        begin(a);
        UiResource old = az.require(UiResource.class, id);
        return saveUi(a, null, id,
            new Inputs.Ui(input.supplied("parentId") ? input.parentId() : old.parentId,
                input.supplied("type") ? input.type() : old.type,
                input.supplied("code") ? input.code() : old.code,
                input.supplied("name") ? input.name() : old.name,
                input.supplied("route") ? input.route() : old.route,
                input.supplied("component") ? input.component() : old.component,
                input.supplied("sortOrder") ? input.sortOrder() : old.sortOrder,
                input.supplied("permissionCode") ? input.permissionCode()
                                                 : permission(old.permissionId),
                input.supplied("status") ? input.status() : old.status));
    }
    /** 中心受保护路由在合并PATCH之后仍逐项与OAS核对。 */
    @Transactional
    public Object patchApi(Actor a, Long id, Inputs.ApiPatch input) {
        begin(a);
        ApiOperation old = az.require(ApiOperation.class, id);
        return saveApi(a, null, id,
            new Inputs.Api(input.supplied("operationId") ? input.operationId() : old.operationId,
                input.supplied("method") ? input.method() : old.method,
                input.supplied("pathTemplate") ? input.pathTemplate() : old.pathTemplate,
                input.supplied("permissionCode") ? input.permissionCode()
                                                 : permission(old.permissionId),
                input.supplied("requiredRole") ? input.requiredRole() : old.requiredRole,
                input.supplied("accessPolicy") ? input.accessPolicy() : old.accessPolicy,
                input.supplied("enabled") ? input.enabled() : old.enabled));
    }
}
