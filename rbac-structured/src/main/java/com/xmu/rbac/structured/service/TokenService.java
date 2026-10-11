package com.xmu.rbac.structured.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * 1.3 令牌签发与解析。令牌只证明「你是谁」，不携带权限：
 * 权限每次请求都由判定过程重新计算，撤销角色后下一次请求立即生效（docs/02 §4.3）。
 */
@Service
public class TokenService {

    public static final long TTL_SECONDS = 2 * 3600;

    /** 令牌里的身份信息。 */
    public record TokenClaims(Long userId, String username, int version, String jti, Instant expiresAt) {
    }

    private final SecretKey key;

    public TokenService(SecretKey jwtKey) {
        this.key = jwtKey;
    }

    public String issue(Long userId, String username, int tokenVersion, Instant now) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("ver", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(TTL_SECONDS)))
                .signWith(key)
                .compact();
    }

    /** 签名错误、过期、格式不对一律返回 null。 */
    public TokenClaims parse(String token, Instant now) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims c = Jwts.parser().verifyWith(key).clock(() -> Date.from(now)).build()
                    .parseSignedClaims(token).getPayload();
            Integer version = c.get("ver", Integer.class);
            return new TokenClaims(Long.parseLong(c.getSubject()), c.get("username", String.class),
                    version == null ? -1 : version, c.getId(), c.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
