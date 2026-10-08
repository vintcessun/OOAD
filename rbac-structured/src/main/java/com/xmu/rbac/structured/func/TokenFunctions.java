package com.xmu.rbac.structured.func;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * 访问令牌（JWT）的签发与解析。
 * 载荷只放身份，不放权限：权限每次请求实时判定，改权限后下一次访问即生效（02-architecture.md §4.6）。
 */
public final class TokenFunctions {

    public static final long DEFAULT_TTL_SECONDS = 7200;

    private TokenFunctions() {}

    public static String issue(SecretKey key, Long userId, String username, Instant now, long ttlSeconds) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * 返回令牌中的用户 ID；令牌缺失、签名错误或在 now 时刻已过期返回 null。
     * now 由调用方传入，与签发一致：判定内核不自己读系统时钟，测试才能用固定时间重现。
     */
    public static Long parseUserId(SecretKey key, String token, Instant now) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String sub = Jwts.parser().verifyWith(key).clock(() -> Date.from(now)).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return Long.valueOf(sub);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
