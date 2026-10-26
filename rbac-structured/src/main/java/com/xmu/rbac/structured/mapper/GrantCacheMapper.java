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
 * <p>键里带全局版本号：{@code rbac:grants:v<版本>:u<用户id>}。版本号存在 MySQL（{@link GrantVersionMapper}），
 * 和授权数据在同一个事务里加一；之后的请求读的是新版本的键，旧键没人再读，等 TTL 清掉。为什么不逐个删键：
 * 删键与「另一个请求刚从数据库读到旧数据、正要写回缓存」之间有竞争，旧数据可能被写回去；
 * 带版本号的键不会，因为那个请求写回的是旧版本的键，没有人再读它（docs/02-architecture.md §4.2）。
 *
 * <p>Redis 不可用时缓存视为未命中，判定直接查数据库，结论不受影响。
 * Redis 只存数据、不管失效，所以它出任何故障都不会让旧授权继续生效。
 */
@Repository
public class GrantCacheMapper {

    private static final Logger log = LoggerFactory.getLogger(GrantCacheMapper.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    public GrantCacheMapper(StringRedisTemplate redis, ObjectMapper json,
                            @org.springframework.beans.factory.annotation.Value("${rbac.cache.ttl:PT10M}") Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    /**
     * 读取指定授权版本下的用户授权快照。键不存在、Redis 故障或 JSON 损坏都按缓存未命中处理，
     * 调用方随后查询 MySQL，不能因为缓存异常改变 ALLOW/DENY 结论。
     */
    public Optional<GrantSet> find(long version, Long userId) {
        try {
            String v = redis.opsForValue().get(key(version, userId));
            return v == null ? Optional.empty() : Optional.of(json.readValue(v, GrantSet.class));
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("读取授权缓存失败：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 最佳努力写入缓存；写失败只损失性能，下一次请求仍可从 MySQL 重建。 */
    public void save(long version, Long userId, GrantSet grants) {
        try {
            redis.opsForValue().set(key(version, userId), json.writeValueAsString(grants), ttl);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("写入授权缓存失败：{}", e.getMessage());
        }
    }

    /** 版本号进入键名后，授权变更只需 bump 版本，不需要逐个删除用户旧键。 */
    static String key(long version, Long userId) {
        return "rbac:grants:v" + version + ":u" + userId;
    }
}
