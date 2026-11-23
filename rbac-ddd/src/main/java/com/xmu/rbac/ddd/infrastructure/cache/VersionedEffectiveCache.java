package com.xmu.rbac.ddd.infrastructure.cache;

import com.fasterxml.jackson.databind.*;
import com.github.benmanes.caffeine.cache.*;
import com.xmu.rbac.ddd.domain.*;
import com.xmu.rbac.ddd.domain.port.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/** L1/L2 只接受携带 MySQL 权威全局版本的键；Redis 失败回源，不消费旧键。 */
@Component
public class VersionedEffectiveCache implements EffectiveCache {
    private final Cache<String, AuthorizationPolicy.Effective> l1 =
            Caffeine.newBuilder()
                    .maximumSize(10000)
                    .expireAfterWrite(Duration.ofSeconds(30))
                    .build();
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String namespace;

    public VersionedEffectiveCache(
            StringRedisTemplate redis,
            ObjectMapper mapper,
            @Value("${rbac.cache.namespace:ddd}") String namespace) {
        this.redis = redis;
        this.mapper = mapper;
        this.namespace = namespace;
    }

    private String key(String system, long user, long version) {
        return namespace + ":authz:" + system + ":" + user + ":" + version;
    }

    public AuthorizationPolicy.Effective get(String system, long user, long version) {
        String key = key(system, user, version);
        AuthorizationPolicy.Effective value = l1.getIfPresent(key);
        if (value != null) return value;
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) return null;
            JsonNode n = mapper.readTree(json);
            List<Role> roles = new ArrayList<>();
            Set<Long> direct = new HashSet<>();
            for (JsonNode r : n.path("roles")) {
                Set<Long> permissions = new HashSet<>();
                for (JsonNode id : r.path("permissionIds")) permissions.add(id.asLong());
                Role role =
                        new Role(
                                r.path("id").asLong(),
                                system,
                                r.path("code").asText(),
                                r.path("name").asText(),
                                r.path("description").isNull()
                                        ? null
                                        : r.path("description").asText(),
                                true,
                                r.path("builtin").asBoolean(),
                                r.path("version").asLong(),
                                permissions);
                roles.add(role);
                if (r.path("direct").asBoolean()) direct.add(role.id());
            }
            List<Permission> permissions = new ArrayList<>();
            for (JsonNode p : n.path("permissions"))
                permissions.add(
                        new Permission(
                                p.path("id").asLong(),
                                system,
                                p.path("code").asText(),
                                p.path("name").asText(),
                                Permission.Kind.valueOf(p.path("kind").asText()),
                                true));
            List<AuthorizationPolicy.Source> sources = new ArrayList<>();
            for (JsonNode s : n.path("sources")) {
                // 旧快照的来源缺少权限种类，需要重新计算。
                if (!s.hasNonNull("permissionKind")) return null;
                List<String> path = new ArrayList<>();
                for (JsonNode p : s.path("path")) path.add(p.asText());
                sources.add(
                        new AuthorizationPolicy.Source(
                                s.path("permissionCode").asText(),
                                Permission.Kind.valueOf(s.path("permissionKind").asText()),
                                s.path("roleCode").asText(),
                                s.path("inherited").asBoolean(),
                                List.copyOf(path)));
            }
            value =
                    new AuthorizationPolicy.Effective(
                            roles.stream().filter(r -> direct.contains(r.id())).toList(),
                            List.copyOf(roles),
                            List.copyOf(permissions),
                            List.copyOf(sources));
            l1.put(key, value);
            return value;
        } catch (Exception failure) {
            return null;
        }
    }

    public void put(String system, long user, long version, AuthorizationPolicy.Effective value) {
        String key = key(system, user, version);
        l1.put(key, value);
        try {
            List<Map<String, Object>> roles = new ArrayList<>();
            Set<Long> direct = new HashSet<>();
            for (Role r : value.directRoles()) direct.add(r.id());
            for (Role r : value.effectiveRoles()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", r.id());
                entry.put("code", r.code());
                entry.put("name", r.name());
                entry.put("description", r.description());
                entry.put("builtin", r.builtin());
                entry.put("version", r.version());
                entry.put("permissionIds", r.permissionIds());
                entry.put("direct", direct.contains(r.id()));
                roles.add(entry);
            }
            redis.opsForValue()
                    .set(
                            key,
                            mapper.writeValueAsString(
                                    Map.of(
                                            "roles",
                                            roles,
                                            "permissions",
                                            value.permissions(),
                                            "sources",
                                            value.sources())),
                            Duration.ofSeconds(60));
        } catch (Exception failure) {
            /* DB 是权威，缓存不可用不影响安全。 */
        }
    }
}
