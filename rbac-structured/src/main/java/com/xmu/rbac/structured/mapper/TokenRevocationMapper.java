package com.xmu.rbac.structured.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * 已注销令牌的登记（Redis）。键在令牌原本到期时自动删除，登记表不会无限增长。
 * 查询失败时先重试一次（连接偶发超时后客户端会重连），仍失败才按「已注销」处理：
 * 宁可让用户重新登录，也不让注销过的令牌继续可用。
 */
@Repository
public class TokenRevocationMapper {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationMapper.class);
    static final int ATTEMPTS = 2;

    private final StringRedisTemplate redis;

    public TokenRevocationMapper(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void revoke(String jti, Duration remaining) {
        if (remaining.isNegative() || remaining.isZero()) {
            return;   // 已经过期的令牌不必登记
        }
        redis.opsForValue().set(key(jti), "1", remaining);
    }

    public boolean isRevoked(String jti) {
        for (int attempt = 1; ; attempt++) {
            try {
                return Boolean.TRUE.equals(redis.hasKey(key(jti)));
            } catch (RuntimeException e) {
                if (attempt >= ATTEMPTS) {
                    log.error("查询令牌注销状态失败，按已注销处理：{}", e.getMessage());
                    return true;
                }
                log.warn("查询令牌注销状态失败，重试：{}", e.getMessage());
            }
        }
    }

    static String key(String jti) {
        return "rbac:revoked:" + jti;
    }
}
