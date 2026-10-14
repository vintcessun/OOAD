-- =============================================================================
-- V2 授权数据版本号（docs/02-architecture.md §4.2，docs/12-design-log.md #37）
--
-- 授权缓存的键带版本号 rbac:grants:v<版本>:u<用户>。版本号原来存在 Redis，
-- 在事务提交后递增；递增失败时旧键在 TTL 内仍会被读到，「下一次访问生效」就不成立。
-- 现在版本号存在这里，和授权数据的修改在同一个事务里递增：两者一起提交或一起回滚，
-- 鉴权时先读版本号再读授权数据，读到新版本号就一定读得到新数据，与 Redis 是否可用无关。
-- =============================================================================

CREATE TABLE sys_grant_version (
    id      TINYINT NOT NULL COMMENT '只有一行，id = 1',
    version BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='授权数据版本号';

INSERT INTO sys_grant_version (id, version) VALUES (1, 0);
