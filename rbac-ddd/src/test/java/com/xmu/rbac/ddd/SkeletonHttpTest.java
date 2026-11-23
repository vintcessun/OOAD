package com.xmu.rbac.ddd;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.*;
import org.springframework.http.*;

import java.util.*;

/** M2 的全部业务404断言已被实现合同下的真实 HTTP 认证断言替换。 */
class SkeletonHttpTest extends DddHttpTestSupport {
    @Test
    void healthAndInfoReportVerifiedImplementationWithoutDeploymentClaims() {
        JsonNode body = http.getForObject("/api/v1/actuator/health", JsonNode.class);
        assertThat(body.path("status").asText()).isEqualTo("UP");
        assertThat(
                        body.path("components")
                                .path("implementation")
                                .path("details")
                                .path("businessReady")
                                .asBoolean())
                .isTrue();
        assertThat(
                        http.getForObject("/api/v1/actuator/info", JsonNode.class)
                                .path("backend")
                                .asText())
                .isEqualTo("ddd");
    }

    @Test
    void mappingCoverageReturnsResourceAndOperationObjects() {
        String token = login("root", "rbac-center");
        JsonNode coverage = successful(HttpMethod.GET,
                "/api/v1/systems/rbac-center/ui-api-mappings", token, null);

        assertThat(coverage.path("unmappedUi"))
                .anySatisfy(ui -> {
                    assertThat(ui.path("id").asLong()).isPositive();
                    assertThat(ui.path("code").asText()).isEqualTo("rbac:ui:authorization:view");
                    assertThat(ui.path("name").asText()).isNotBlank();
                    assertThat(ui.path("children").isArray()).isTrue();
                });
        assertThat(coverage.path("unmappedApi"))
                .anySatisfy(api -> {
                    assertThat(api.path("id").asLong()).isPositive();
                    assertThat(api.path("operationId").asText()).isEqualTo("login");
                    assertThat(api.path("method").asText()).isEqualTo("POST");
                    assertThat(api.path("pathTemplate").asText()).isEqualTo("/auth/login");
                });
    }

    @Test
    void loginJwtMeAndPersistentLogout() {
        ResponseEntity<JsonNode> login =
                http.postForEntity(
                        "/auth/login",
                        Map.of(
                                "username",
                                "root",
                                "password",
                                "Ddd-Test-Only-Password-2026!",
                                "systems",
                                List.of("rbac-center")),
                        JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = login.getBody().path("accessToken").asText();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<JsonNode> me =
                http.exchange(
                        "/auth/me", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().path("subject").asText()).isEqualTo("rbac-u1");
        assertThat(
                        http.exchange(
                                        "/api/v1/roles?systemCode=rbac-center",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        JsonNode.class)
                                .getBody()
                                .path("data")
                                .path("items")
                                .size())
                .isGreaterThanOrEqualTo(3);
        assertThat(
                        http.exchange(
                                        "/auth/logout",
                                        HttpMethod.POST,
                                        new HttpEntity<>(headers),
                                        Void.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(
                        http.exchange(
                                        "/auth/me",
                                        HttpMethod.GET,
                                        new HttpEntity<>(headers),
                                        JsonNode.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void managementWithoutTokenIs401NotSkeleton404() {
        assertThat(http.getForEntity("/api/v1/users", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void publicJwksAndInvalidLogin() {
        assertThat(
                        http.getForObject("/.well-known/jwks.json", JsonNode.class)
                                .path("keys")
                                .get(0)
                                .path("alg")
                                .asText())
                .isEqualTo("RS256");
        assertThat(
                        http.postForEntity(
                                        "/auth/login",
                                        Map.of(
                                                "username",
                                                "missing",
                                                "password",
                                                "not-a-secret",
                                                "systems",
                                                List.of("rbac-center")),
                                        JsonNode.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownDtoFieldIsRejected() {
        assertThat(
                        http.postForEntity(
                                        "/auth/login",
                                        Map.of(
                                                "username",
                                                "root",
                                                "password",
                                                "x",
                                                "systems",
                                                List.of("rbac-center"),
                                                "actorId",
                                                1),
                                        JsonNode.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
