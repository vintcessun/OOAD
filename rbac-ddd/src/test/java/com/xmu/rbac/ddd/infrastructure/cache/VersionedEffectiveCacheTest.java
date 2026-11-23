package com.xmu.rbac.ddd.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xmu.rbac.ddd.domain.AuthorizationPolicy;
import com.xmu.rbac.ddd.domain.Permission;
import com.xmu.rbac.ddd.domain.Role;
import com.xmu.rbac.ddd.domain.RoleHierarchy;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Set;

class VersionedEffectiveCacheTest {
    @Test
    @SuppressWarnings("unchecked")
    void keepsPermissionKindOnRedisRoundTripAndRejectsUntypedSources() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var json = new ObjectMapper();
        var ui = new Permission(11, "course", "shared", "界面", Permission.Kind.UI, true);
        var api = new Permission(12, "course", "shared", "接口", Permission.Kind.API, true);
        var uiRole = new Role(1, "course", "UI_ROLE", "界面角色", null, true, false, 0, Set.of(ui.id()));
        var apiRole = new Role(2, "course", "API_ROLE", "接口角色", null, true, false, 0, Set.of(api.id()));
        var graph = new RoleHierarchy(List.of(uiRole, apiRole), List.of());
        var effective = new AuthorizationPolicy().calculate("course", Set.of(1L, 2L), graph, List.of(ui, api));
        var writer = new VersionedEffectiveCache(redis, json, "ddd");
        writer.put("course", 42, 7, effective);
        String key = "ddd:authz:course:42:7";
        var payload = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq(key), payload.capture(), any(Duration.class));
        var legacy = json.readTree(payload.getValue());
        for (var source : legacy.path("sources")) ((ObjectNode) source).remove("permissionKind");
        when(values.get(key)).thenReturn(json.writeValueAsString(legacy), payload.getValue());

        var reader = new VersionedEffectiveCache(redis, json, "ddd");
        assertThat(reader.get("course", 42, 7)).isNull();
        assertThat(reader.get("course", 42, 7).sources()).containsExactly(
                new AuthorizationPolicy.Source("shared", Permission.Kind.UI, "UI_ROLE", false, List.of("UI_ROLE")),
                new AuthorizationPolicy.Source("shared", Permission.Kind.API, "API_ROLE", false, List.of("API_ROLE")));
    }
}
