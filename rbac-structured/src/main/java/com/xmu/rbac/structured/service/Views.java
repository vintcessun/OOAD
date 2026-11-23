package com.xmu.rbac.structured.service;
import com.xmu.rbac.structured.entity.*;
import java.util.*;
import java.util.function.Function;
/** 输出白名单装配，不序列化实体或内部认证属性。 */
public final class Views {
    private Views() {
    }
    /** 执行map操作，保持服务到持久化层的单向依赖。 */
    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) out.put((String) pairs[i], pairs[i + 1]);
        return out;
    }
    /** 执行system操作，保持服务到持久化层的单向依赖。 */
    public static Map<String, Object> system(SoftwareSystem s) {
        return map("id", s.id, "code", s.code, "name", s.name, "status", s.status);
    }
    /** 执行role操作，保持服务到持久化层的单向依赖。 */
    public static Map<String, Object> role(Role r, String system) {
        return map("id", r.id, "systemCode", system, "code", r.code, "name", r.name, "description",
            r.description, "status", r.status, "builtin", r.builtin,
            "version", r.version);
    }
    /** 执行permission操作，保持服务到持久化层的单向依赖。 */
    public static Map<String, Object> permission(Permission p, String system) {
        return map("id", p.id, "systemCode", system, "code", p.code, "name", p.name, "kind", p.kind,
            "status", p.status);
    }
    /** 执行department操作，保持服务到持久化层的单向依赖。 */
    public static Map<String, Object> department(Department d) {
        return map("id", d.id, "parentId", d.parentId, "code", d.code, "name", d.name, "path",
            d.path, "status", d.status, "children", new ArrayList<>());
    }
    /** 执行user操作，保持服务到持久化层的单向依赖。 */
    public static Map<String, Object> user(User u, String departmentName) {
        String phone = u.phone;
        if (phone != null)
            phone = phone.length() > 7
                ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4)
                : "****";
        return map("id", u.id, "subject", u.subject, "username", u.username, "name", u.name, "no",
            u.no, "phone", phone, "departmentId", u.departmentId, "departmentName", departmentName,
            "status", u.status, "version", u.version);
    }
    /** 执行api操作，保持服务到持久化层的单向依赖。 */
    public static Map<String, Object> api(ApiOperation a, String system, String permission) {
        return map("id", a.id, "systemCode", system, "operationId", a.operationId, "method",
            a.method, "pathTemplate", a.pathTemplate, "permissionCode", permission, "requiredRole",
            a.requiredRole, "accessPolicy", a.accessPolicy, "enabled", a.enabled);
    }
    /** 执行ui操作，保持服务到持久化层的单向依赖。 */
    public static Map<String, Object> ui(UiResource a, String system, String permission) {
        return map("id", a.id, "systemCode", system, "parentId", a.parentId, "type", a.type, "code",
            a.code, "name", a.name, "route", a.route, "component", a.component, "sortOrder",
            a.sortOrder, "status", a.status, "permissionCode", permission, "children",
            new ArrayList<>());
    }
    /** 执行tree操作，保持服务到持久化层的单向依赖。 */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> tree(List<Map<String, Object>> nodes) {
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> n : nodes) byId.put((Long) n.get("id"), n);
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> n : nodes) {
            Map<String, Object> p = byId.get(n.get("parentId"));
            if (p == null)
                roots.add(n);
            else
                ((List<Map<String, Object>>) p.get("children")).add(n);
        }
        return roots;
    }
}
