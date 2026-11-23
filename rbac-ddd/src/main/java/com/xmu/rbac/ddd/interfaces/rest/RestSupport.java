package com.xmu.rbac.ddd.interfaces.rest;

import com.fasterxml.jackson.databind.*;
import com.xmu.rbac.ddd.application.*;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/** HTTP 组装白名单，不把聚合或持久化实体作为响应。 */
@Component
public class RestSupport {
    private final TokenProvider tokens;
    private final AuthorizationApplicationService authorization;
    private final ObjectMapper mapper;
    private final AuditApplicationService audits;

    public RestSupport(
            TokenProvider tokens,
            AuthorizationApplicationService authorization,
            ObjectMapper mapper,
            AuditApplicationService audits) {
        this.tokens = tokens;
        this.authorization = authorization;
        this.mapper = mapper;
        this.audits = audits;
    }

    public TokenProvider.Principal principal(String header) {
        DomainException.require(
                header != null && header.startsWith("Bearer "), 401, "TOKEN_REQUIRED");
        TokenProvider.Principal principal = tokens.verify(header.substring(7));
        authorization.validate(principal);
        return principal;
    }

    public static Map<String, Object> ok(Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", "OK");
        result.put("message", "成功");
        result.put("data", data);
        result.put("timestamp", Instant.now());
        return result;
    }

    public static com.xmu.rbac.ddd.interfaces.rest.dto.RoleView role(Role r) {
        return cn.edu.xmu.clonefactory.util.CloneFactory.copy(
                new com.xmu.rbac.ddd.interfaces.rest.dto.RoleView(null, null, null, null,
                        null, null, null, null),
                com.xmu.rbac.ddd.application.RoleResult.from(r));
    }

    public static Map<String, Object> permission(Permission p) {
        return Map.of(
                "id",
                p.id(),
                "systemCode",
                p.systemCode(),
                "code",
                p.code(),
                "name",
                p.name(),
                "kind",
                p.kind(),
                "status",
                p.enabled() ? "ENABLED" : "DISABLED");
    }

    public static Map<String, Object> effective(AuthorizationPolicy.Effective e) {
        return Map.of(
                "directRoles",
                e.directRoles().stream().map(RestSupport::role).toList(),
                "effectiveRoles",
                e.effectiveRoles().stream().map(RestSupport::role).toList(),
                "permissions",
                e.permissions().stream().map(RestSupport::permission).toList(),
                "sources",
                e.sources());
    }

    public Map<String, Object> audit(AuditFact fact) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", fact.id());
        result.put("at", fact.at());
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("id", fact.actorId());
        actor.put("name", fact.actorName());
        actor.put("roles", fact.actorRoles());
        actor.put("deleted", audits.actorDeleted(fact));
        result.put("actor", actor);
        result.put("type", fact.type());
        result.put("target", fact.target());
        result.put("before", json(fact.beforeJson()));
        result.put("after", json(fact.afterJson()));
        result.put("objectType", fact.objectType());
        result.put("objectId", fact.objectId());
        return result;
    }

    private JsonNode json(String json) {
        if (json == null) return null;
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new DomainException(503, "AUDIT_UNAVAILABLE");
        }
    }
}
