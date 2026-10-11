package com.xmu.rbac.structured.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmu.rbac.structured.dto.GrantSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 授权数据缓存（Redis）。
 *
 * <p>键里带全局版本号：{@code rbac:grants:v<版本>:u<用户id>}。任何授权数据变更提交后版本号加一，
 * 之后的请求读的是新版本的键，旧键自然失效，等 TTL 清掉。为什么不逐个删键：
 * 删键与「另一个请求刚从数据库读到旧数据、正要写回缓存」之间有竞争，旧数据可能被写回去；
 * 带版本号的键不会，因为那个请求写回的是旧版本的键，没有人再读它（docs/02-architecture.md §4.2）。
 *
 * <p>Redis 不可用时缓存视为未命中，判定直接查数据库，结论不受影响。
 */
@Repository
public class GrantCacheMapper {

    private static final Logger log = LoggerFactory.getLogger(GrantCacheMapper.class);
    static final String VERSION_KEY = "rbac:grants:version";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    public GrantCacheMapper(StringRedisTemplate redis, ObjectMapper json,
                            @org.springframework.beans.factory.annotation.Value("${rbac.cache.ttl:PT10M}") Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    /** 当前版本号；Redis 不可用时返回 null（本次请求不用缓存）。 */
    public Long currentVersion() {
        try {
            String v = redis.opsForValue().get(VERSION_KEY);
            return v == null ? 0L : Long.parseLong(v);
        } catch (RuntimeException e) {
            log.warn("读取授权缓存版本失败，本次直接查库：{}", e.getMessage());
            return null;
        }
    }

    public Optional<GrantSet> find(long version, Long userId) {
        try {
            String v = redis.opsForValue().get(key(version, userId));
            return v == null ? Optional.empty() : Optional.of(json.readValue(v, GrantSet.class));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("读取授权缓存失败：{}", e.getMessage());
            return Optional.empty();
        }
    }

    public void save(long version, Long userId, GrantSet grants) {
        try {
            redis.opsForValue().set(key(version, userId), json.writeValueAsString(grants), ttl);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("写入授权缓存失败：{}", e.getMessage());
        }
    }

    /**
     * 授权数据变更后调用（事务提交之后）。失败时记错误日志：此时缓存可能在 TTL 内给出旧结论，
     * 这是本设计唯一的不一致窗口，由运维告警兜底。
     */
    public void bumpVersion() {
        try {
            redis.opsForValue().increment(VERSION_KEY);
        } catch (RuntimeException e) {
            log.error("授权缓存版本号递增失败，缓存可能在 TTL 内返回旧授权：{}", e.getMessage());
        }
    }

    static String key(long version, Long userId) {
        return "rbac:grants:v" + version + ":u" + userId;
    }
}
