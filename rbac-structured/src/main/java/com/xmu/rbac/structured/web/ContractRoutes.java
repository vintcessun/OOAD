package com.xmu.rbac.structured.web;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 契约中每个接口声明的权限，是运行时拦截器的唯一权限来源（02-architecture.md §4.7）。
 * 键为「METHOD 路径模板」，如 {@code GET /users/{id}}，与 Spring 的最佳匹配模式逐字相同。
 */
public final class ContractRoutes {

    /** 本人自助接口：只校验令牌有效与账号启用。 */
    public static final String SELF = "@self";

    /** 一个接口的权限声明。anonymous = 契约里 security: []（只有登录）。 */
    public record Route(String permission, boolean anonymous) {}

    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

    private final Map<String, Route> routes;

    private ContractRoutes(Map<String, Route> routes) {
        this.routes = routes;
    }

    public static ContractRoutes load(InputStream yaml) throws IOException {
        try (yaml) {
            LoaderOptions options = new LoaderOptions();
            options.setCodePointLimit(16 * 1024 * 1024);
            Map<String, Object> doc = new Yaml(options).load(yaml);
            return fromDocument(doc);
        }
    }

    @SuppressWarnings("unchecked")
    static ContractRoutes fromDocument(Map<String, Object> doc) {
        Map<String, Route> routes = new TreeMap<>();
        Map<String, Object> paths = (Map<String, Object>) doc.getOrDefault("paths", Map.of());
        for (Map.Entry<String, Object> path : paths.entrySet()) {
            Map<String, Object> ops = (Map<String, Object>) path.getValue();
            for (Map.Entry<String, Object> op : ops.entrySet()) {
                if (!METHODS.contains(op.getKey())) {
                    continue;
                }
                Map<String, Object> operation = (Map<String, Object>) op.getValue();
                Object security = operation.get("security");
                boolean anonymous = security instanceof List<?> list && list.isEmpty();
                String permission = (String) operation.get("x-required-permission");
                routes.put(key(op.getKey(), path.getKey()), new Route(permission, anonymous));
            }
        }
        return new ContractRoutes(routes);
    }

    public static String key(String method, String pathPattern) {
        return method.toUpperCase(Locale.ROOT) + " " + pathPattern;
    }

    /** 未在契约中出现的路由返回 null——拦截器据此一律拒绝。 */
    public Route find(String method, String pathPattern) {
        return routes.get(key(method, pathPattern));
    }

    /** 启动自检与 CI：返回实现了、但契约里没有声明权限的路由。非空即不允许启动。 */
    public List<String> undeclared(Collection<String> implementedKeys) {
        return implementedKeys.stream()
                .filter(k -> {
                    Route r = routes.get(k);
                    return r == null || (!r.anonymous() && r.permission() == null);
                })
                .sorted()
                .toList();
    }

    public int size() {
        return routes.size();
    }
}
