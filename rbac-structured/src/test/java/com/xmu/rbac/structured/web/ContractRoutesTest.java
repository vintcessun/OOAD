package com.xmu.rbac.structured.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §4.7「所有接口都受权限控制」的契约侧检查：读的是 rbac-contract 里的真实 rbac-api.yaml。 */
class ContractRoutesTest {

    private static ContractRoutes routes;

    @BeforeAll
    static void load() throws Exception {
        routes = ContractRoutes.load(new ClassPathResource("openapi/rbac-api.yaml").getInputStream());
    }

    @Test
    void readsAllOperationsOfTheContract() {
        assertEquals(66, routes.size());
    }

    @Test
    void onlyLoginIsAnonymous() {
        assertTrue(routes.find("POST", "/auth/login").anonymous());
        assertEquals("authz:check:invoke", routes.find("post", "/authz/check").permission());
        assertEquals(ContractRoutes.SELF, routes.find("GET", "/auth/me").permission());
        assertNull(routes.find("GET", "/not/in/contract"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyOperationDeclaresPermissionUnlessAnonymous() throws Exception {
        Map<String, Object> doc;
        try (InputStream in = new ClassPathResource("openapi/rbac-api.yaml").getInputStream()) {
            doc = new Yaml().load(in);
        }
        List<String> keys = new ArrayList<>();
        ((Map<String, Map<String, Object>>) doc.get("paths")).forEach((path, ops) ->
                ops.keySet().stream()
                        .filter(m -> List.of("get", "post", "put", "patch", "delete").contains(m))
                        .forEach(m -> keys.add(ContractRoutes.key(m, path))));
        assertEquals(66, keys.size());
        assertEquals(List.of(), routes.undeclared(keys));
    }

    @Test
    void undeclaredReportsMissingAndPermissionlessRoutes() {
        ContractRoutes r = ContractRoutes.fromDocument(Map.of("paths", Map.of(
                "/a", Map.of("get", Map.of("x-required-permission", "x:y:z")),
                "/b", Map.of("get", Map.of()),
                "/c", Map.of("post", Map.of("security", List.of())))));
        assertEquals(List.of("GET /b", "GET /nowhere"),
                r.undeclared(List.of("GET /a", "GET /b", "POST /c", "GET /nowhere")));
    }
}
