package com.xmu.rbac.structured.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xmu.rbac.structured.dto.GrantSet;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 出故障时的降级口径（docs/02-architecture.md §4.2）：
 * 授权缓存失效 → 当作未命中，直接查库，结论不变；令牌注销查询失败 → 按已注销处理（宁可重新登录）。
 */
class RedisFailureTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void grantCacheTreatsRedisFailureAsMiss() {
        when(redis.opsForValue()).thenReturn(ops);
        RedisConnectionFailureException down = new RedisConnectionFailureException("down");
        when(ops.get(anyString())).thenThrow(down);
        when(ops.increment(anyString())).thenThrow(down);
        org.mockito.Mockito.doThrow(down).when(ops).set(anyString(), anyString(), any(Duration.class));
        GrantCacheMapper cache = new GrantCacheMapper(redis, json, Duration.ofMinutes(10));

        assertNull(cache.currentVersion());
        assertTrue(cache.find(0, 1L).isEmpty());
        cache.save(0, 1L, new GrantSet(List.of(), List.of()));   // 不抛异常
        cache.bumpVersion();                                      // 不抛异常
    }

    @Test
    void grantCacheRoundTripAndCorruptValue() throws Exception {
        when(redis.opsForValue()).thenReturn(ops);
        GrantCacheMapper cache = new GrantCacheMapper(redis, json, Duration.ofMinutes(10));
        when(ops.get(GrantCacheMapper.VERSION_KEY)).thenReturn(null, "7");
        assertEquals(0L, cache.currentVersion());
        assertEquals(7L, cache.currentVersion());

        GrantSet empty = new GrantSet(List.of(), List.of());
        when(ops.get(GrantCacheMapper.key(7, 1L))).thenReturn(json.writeValueAsString(empty));
        assertEquals(empty, cache.find(7, 1L).orElseThrow());
        when(ops.get(GrantCacheMapper.key(7, 2L))).thenReturn("{not json");
        assertTrue(cache.find(7, 2L).isEmpty());
        assertEquals("rbac:grants:v7:u1", GrantCacheMapper.key(7, 1L));
    }

    @Test
    void revocationLookupFailsClosed() {
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        TokenRevocationMapper revocations = new TokenRevocationMapper(redis);
        assertTrue(revocations.isRevoked("jti-1"));
    }

    @Test
    void revocationLookupRetriesOnceBeforeFailingClosed() {
        when(redis.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("blip"))
                .thenReturn(false);
        assertFalse(new TokenRevocationMapper(redis).isRevoked("jti-1"));
    }

    @Test
    void revocationOfExpiredTokenIsNotStored() {
        when(redis.opsForValue()).thenReturn(ops);
        TokenRevocationMapper revocations = new TokenRevocationMapper(redis);
        revocations.revoke("jti-1", Duration.ZERO);
        revocations.revoke("jti-2", Duration.ofSeconds(-5));
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
        when(redis.hasKey("rbac:revoked:jti-3")).thenReturn(false);
        assertFalse(revocations.isRevoked("jti-3"));
    }
}
