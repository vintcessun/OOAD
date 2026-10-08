package com.xmu.rbac.structured.func;

import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TokenFunctionsTest {

    private final SecretKey key = Keys.hmacShaKeyFor("unit-test-secret-unit-test-secret-32b".getBytes(StandardCharsets.UTF_8));
    private final SecretKey otherKey = Keys.hmacShaKeyFor("another-secret-another-secret-another".getBytes(StandardCharsets.UTF_8));

    /** 固定时刻：与系统时间无关，任何一天跑结果都一样。 */
    private final Instant now = Instant.parse("2026-10-01T08:00:00Z");

    @Test
    void roundTrip() {
        String token = TokenFunctions.issue(key, 42L, "alice", now, 60);
        assertEquals(42L, TokenFunctions.parseUserId(key, token, now));
        assertEquals(42L, TokenFunctions.parseUserId(key, token, now.plusSeconds(59)));
    }

    @Test
    void rejectsExpiredForgedAndGarbage() {
        String expired = TokenFunctions.issue(key, 42L, "alice", now.minusSeconds(7300), 7200);
        assertNull(TokenFunctions.parseUserId(key, expired, now));

        String forged = TokenFunctions.issue(otherKey, 42L, "alice", now, 60);
        assertNull(TokenFunctions.parseUserId(key, forged, now));

        assertNull(TokenFunctions.parseUserId(key, "not-a-jwt", now));
        assertNull(TokenFunctions.parseUserId(key, null, now));
        assertNull(TokenFunctions.parseUserId(key, " ", now));
    }
}
