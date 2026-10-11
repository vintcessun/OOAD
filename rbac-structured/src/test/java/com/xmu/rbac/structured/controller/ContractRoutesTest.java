package com.xmu.rbac.structured.controller;

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

/** 「所有接口都受权限控制」的契约侧检查：读的是 rbac-contract 里的真实 rbac-api.yaml。 */
class ContractRoutesTest {

    /** RBAC0 阶段的接口数（docs/04-api.md §2 的总表）。 */
    static final int OPERATIONS = 45;

    private static ContractRoutes routes;

    @BeforeAll
    static void load() throws Exception {
        routes = ContractRoutes.load(new ClassPathResource("openapi/rbac-api.yaml").getInputStream());
    }

    @Test
    void readsAllOperationsOfTheContract() {
        assertEquals(OPERATIONS, routes.size());
    }

    @Test
    void onlyLoginIsAnonymous() {
        assertTrue(routes.find("POST", "/auth/login").anonymous());
        assertEquals(ContractRoutes.SELF, routes.find("post", "/authz/check").permission());
        assertEquals("rbac:assignment:assign", routes.find("POST", "/assignments/batch").permission());
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
        assertEquals(OPERATIONS, keys.size());
        assertEquals(List.of(), routes.undeclared(keys));
    }

    @Test
    void permissionCodesExistInBaselineMigration() throws Exception {
        String sql;
        try (InputStream in = new ClassPathResource("db/migration/V1__rbac0_baseline.sql").getInputStream()) {
            sql = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        // 契约里用到的每个权限码，其资源都在基线脚本里建好了（否则谁都无法被授予它）
        for (String code : routes.permissionCodes()) {
            String resource = code.substring(0, code.lastIndexOf(':'));
            assertTrue(sql.contains("'" + resource + "'"), code);
        }
    }

    @Test
    void unimplementedReportsContractOnlyRoutes() {
        ContractRoutes r = ContractRoutes.fromDocument(Map.of("paths", Map.of(
                "/a", Map.of("get", Map.of("x-required-permission", "x:y:z")),
                "/c", Map.of("post", Map.of("security", List.of())))));
        assertEquals(List.of("POST /c"), r.unimplemented(List.of("GET /a")));
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
