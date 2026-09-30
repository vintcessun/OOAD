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

    @Test
    void roundTrip() {
        String token = TokenFunctions.issue(key, 42L, "alice", Instant.now(), 60);
        assertEquals(42L, TokenFunctions.parseUserId(key, token));
    }

    @Test
    void rejectsExpiredForgedAndGarbage() {
        String expired = TokenFunctions.issue(key, 42L, "alice", Instant.now().minusSeconds(7300), 7200);
        assertNull(TokenFunctions.parseUserId(key, expired));

        String forged = TokenFunctions.issue(otherKey, 42L, "alice", Instant.now(), 60);
        assertNull(TokenFunctions.parseUserId(key, forged));

        assertNull(TokenFunctions.parseUserId(key, "not-a-jwt"));
        assertNull(TokenFunctions.parseUserId(key, null));
        assertNull(TokenFunctions.parseUserId(key, " "));
    }
}
