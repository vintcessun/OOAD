package com.xmu.rbac.ddd.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.xmu.rbac.ddd.application.*;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** 教师七个外部接口保持根路径及裸响应。 */
@RestController
public class IdentityController {
    private final RestSupport rest;
    private final IdentityApplicationService identities;
    private final AuthorizationApplicationService authorization;
    private final AuditApplicationService audits;
    private final TokenProvider tokens;
    private final byte[] serviceDigest;
    private final boolean serviceConfigured;

    public IdentityController(
            RestSupport rest,
            IdentityApplicationService identities,
            AuthorizationApplicationService authorization,
            AuditApplicationService audits,
            TokenProvider tokens,
            @Value("${rbac.audit-service-key:}") String serviceKey) {
        this.rest = rest;
        this.identities = identities;
        this.authorization = authorization;
        this.audits = audits;
        this.tokens = tokens;
        this.serviceDigest = digest(serviceKey);
        this.serviceConfigured = !serviceKey.isBlank();
    }

    public record Login(
            @NotBlank @Size(max = 128) String username,
            @NotBlank @Size(max = 256) String password,
            @NotNull @Size(min = 1, max = 1) List<@NotBlank String> systems) {}

    public record Password(
            @NotBlank String oldPassword, @NotBlank @Size(min = 8, max = 256) String newPassword) {}

    public record AuditInput(
            @NotBlank
                    @Pattern(
                            regexp =
                                    "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                    String requestId,
            @NotBlank String type,
            @NotBlank String objectType,
            @NotBlank String objectId,
            @NotBlank @Size(max = 500) String target,
            JsonNode before,
            JsonNode after) {}

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody Login input) {
        return Map.of(
                "accessToken",
                identities.login(input.username(), input.password(), input.systems().getFirst()),
                "tokenType",
                "Bearer",
                "expiresIn",
                7200);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String bearer) {
        identities.logout(rest.principal(bearer));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return tokens.jwks();
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me(
            @RequestHeader(value = "Authorization", required = false) String bearer) {
        TokenProvider.Principal principal = rest.principal(bearer);
        AuthorizationRepository.Identity user = authorization.validate(principal);
        AuthorizationPolicy.Effective e =
                authorization.effective(user.id(), principal.systemCode());
        return Map.of(
                "subject",
                user.subject(),
                "roles",
                e.courseRoleCodes(),
                "permissions",
                e.permissions().stream()
                        .filter(p -> p.kind() == Permission.Kind.UI)
                        .map(Permission::code)
                        .toList(),
                "name",
                user.name(),
                "no",
                user.no() == null ? "" : user.no());
    }

    @GetMapping("/auth/permissions/{permission}")
    public Map<String, Object> permission(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable String permission) {
        DomainException.require(permission.length() <= 128, 400, "INVALID_PERMISSION");
        TokenProvider.Principal principal = rest.principal(bearer);
        return Map.of(
                "allowed",
                authorization
                        .effective(authorization.validate(principal).id(), principal.systemCode())
                        .has(permission, Permission.Kind.UI));
    }

    @GetMapping("/auth/api-permissions/{operationId}")
    public Map<String, Object> api(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @PathVariable String operationId) {
        DomainException.require(
                !operationId.isEmpty() && operationId.length() <= 128, 400, "INVALID_REQUEST");
        TokenProvider.Principal principal = rest.principal(bearer);
        return Map.of(
                "operationId",
                operationId,
                "allowed",
                authorization.api(
                        authorization.validate(principal).id(),
                        principal.systemCode(),
                        operationId));
    }

    @PostMapping("/api/v1/auth/password")
    public Map<String, Object> password(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @Valid @RequestBody Password input) {
        identities.password(rest.principal(bearer), input.oldPassword(), input.newPassword());
        return RestSupport.ok(null);
    }

    /** 固定长度摘要比较，服务凭据不参与日志或响应。 */
    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256不可用", error);
        }
    }

    private AuthorizationRepository.Identity auditActor(
            TokenProvider.Principal principal, String key, String permission) {
        DomainException.require(
                serviceConfigured
                        && key != null
                        && MessageDigest.isEqual(serviceDigest, digest(key)),
                403,
                "AUDIT_SERVICE_FORBIDDEN");
        AuthorizationRepository.Identity actor = authorization.validate(principal);
        DomainException.require(
                authorization
                        .effective(actor.id(), principal.systemCode())
                        .has(permission, Permission.Kind.API),
                403,
                "PERMISSION_DENIED");
        return actor;
    }

    @PostMapping("/auth/audit-logs")
    public ResponseEntity<Map<String, Object>> audit(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Audit-Service-Key", required = false) String key,
            @Valid @RequestBody AuditInput input) {
        TokenProvider.Principal principal = rest.principal(bearer);
        AuthorizationRepository.Identity actor = auditActor(principal, key, "audit:write");
        AuditFact fact =
                audits.external(
                        actor,
                        principal.systemCode(),
                        new ArrayList<>(
                                authorization
                                        .effective(actor.id(), principal.systemCode())
                                        .courseRoleCodes()),
                        input.requestId(),
                        input.type(),
                        input.objectType(),
                        input.objectId(),
                        input.target(),
                        input.before(),
                        input.after());
        return ResponseEntity.status(201).body(rest.audit(fact));
    }

    @GetMapping("/auth/audit-logs")
    public Map<String, Object> auditLogs(
            @RequestHeader(value = "Authorization", required = false) String bearer,
            @RequestHeader(value = "X-Audit-Service-Key", required = false) String key,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String target,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        TokenProvider.Principal principal = rest.principal(bearer);
        auditActor(principal, key, "audit:read");
        AuthorizationRepository.Page<AuditFact> result =
                audits.query(
                        principal.systemCode(),
                        true,
                        actor,
                        actorId,
                        target,
                        from,
                        to,
                        page,
                        pageSize);
        return Map.of(
                "page",
                page,
                "pageSize",
                pageSize,
                "total",
                result.total(),
                "items",
                result.items().stream().map(rest::audit).toList());
    }
}
