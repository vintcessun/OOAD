-- =============================================================================
-- V3 迭代三 RBAC2/3 约束体系 + 会话
--
-- 对应文档：docs/03-database.md §3.3
-- RBAC3 = RBAC1（V2 的角色继承）+ RBAC2（本脚本的约束）
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 约束规则
-- -----------------------------------------------------------------------------

-- 单表 + 类型字段，而非每类约束一张表。
-- 理由：约束的检查流程高度同构（加载规则 → 求值 → 返回违规），单表使
-- ConstraintEvaluator 可以统一加载；差异被推到求值策略中，而非数据模型中。
--
-- ⚠️ 约束作用于**有效角色**（含继承展开后），而非直接角色。
--    否则可通过「继承一个含互斥角色的父角色」绕过 SSD。
--    代价是约束求值必须排在闭包计算之后（判定管道 S6 在 S3 之后）。
CREATE TABLE sys_constraint (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    constraint_name VARCHAR(64)  NOT NULL,
    constraint_type VARCHAR(32)  NOT NULL COMMENT '见下方取值说明',
    threshold       INT          NULL     COMMENT '阈值，语义随类型而异',
    target_role_id  BIGINT       NULL     COMMENT '单角色类约束的目标；CARD_USER_ROLE_MAX 为 NULL（全局）',
    description     VARCHAR(255) NULL,
    enabled         TINYINT      NOT NULL DEFAULT 1,
    legacy_exempt   TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = 存量豁免，仅拦截新增',
    created_by      BIGINT       NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_constraint_name (constraint_name),
    KEY idx_type_enabled (constraint_type, enabled),
    KEY idx_target_role  (target_role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='约束规则';

-- constraint_type 取值与 threshold 语义：
--
--   SSD_MUTEX           静态互斥角色      threshold = 最多可同时拥有的数量
--                                         关联表存互斥角色集合
--   DSD_MUTEX           动态互斥（会话级） threshold = 同一会话最多激活数量
--                                         关联表存互斥角色集合
--   CARD_ROLE_USER_MAX  单角色用户数上限  threshold = 用户数上限，target_role_id 指定角色
--   CARD_USER_ROLE_MAX  单用户角色数上限  threshold = 角色数上限，target_role_id 为 NULL（全局）
--   CARD_ROLE_PERM_MAX  单角色权限数上限  threshold = 权限数上限，target_role_id 指定角色
--   PREREQUISITE        先决条件角色      threshold 不使用
--                                         target_role_id = 目标角色，关联表存前置角色
--
-- 三类基数约束**全部实现**——课程课件明确列出这三项。

CREATE TABLE sys_constraint_role (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    constraint_id BIGINT      NOT NULL,
    role_id       BIGINT      NOT NULL,
    role_position VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT 'MEMBER = 互斥集合成员；PREREQUISITE = 前置角色',
    PRIMARY KEY (id),
    UNIQUE KEY uk_constraint_role (constraint_id, role_id),
    KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='约束涉及的角色';

-- -----------------------------------------------------------------------------
-- 2. 会话（DSD 用）
-- -----------------------------------------------------------------------------

-- 会话的**权威存储在 Redis**（性能需求）；本表仅用于审计与「强制下线」等
-- 管理操作的持久化记录。两者不一致时以 Redis 为准，本表为最终一致。
CREATE TABLE sys_session (
    session_id  VARCHAR(64) NOT NULL,
    user_id     BIGINT      NOT NULL,
    client_ip   VARCHAR(45) NULL,
    user_agent  VARCHAR(255) NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  DATETIME    NOT NULL,
    revoked     TINYINT     NOT NULL DEFAULT 0 COMMENT '用户被禁用时置 1',
    PRIMARY KEY (session_id),
    KEY idx_user    (user_id),
    KEY idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话';

CREATE TABLE sys_session_role (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(64) NOT NULL,
    role_id    BIGINT      NOT NULL COMMENT '本次会话激活的角色（DSD 用）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_role (session_id, role_id),
    KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话激活角色';

-- =============================================================================
-- 演示用约束
--
-- ⚠️ 互斥角色对**不得存在继承关系**。若 A 继承 B 而规则声明 A 与 B 互斥，
--    则任何持有 A 的用户都会因继承获得 B 而违反该约束——该约束不可满足。
--    创建 SSD 规则时必须校验：规则中任意两角色在继承图上不得互为祖先，
--    违反则返回 24005 CONSTRAINT_INEFFECTIVE。
--
--    这正是 SSD 演示对选用「审计员 ⟂ 系统管理员」而非「普通员工 ⟂ 部门经理」
--    的原因——后者在 V2 引入继承后（部门经理继承普通员工）将变得不可满足。
-- =============================================================================

INSERT INTO sys_constraint (constraint_name, constraint_type, threshold, description) VALUES
    ('审计独立性', 'SSD_MUTEX', 1,
     '审计员与系统管理员不得为同一人。审计员的职责是监督系统管理员的操作，两者合一则审计失去独立性');

INSERT INTO sys_constraint_role (constraint_id, role_id, role_position)
SELECT c.id, r.id, 'MEMBER' FROM sys_constraint c, sys_role r
WHERE c.constraint_name = '审计独立性' AND r.role_code IN ('AUDITOR', 'SYS_ADMIN');

-- 三类基数约束各一条
INSERT INTO sys_constraint (constraint_name, constraint_type, threshold, target_role_id, description)
SELECT '运维账号上限', 'CARD_ROLE_USER_MAX', 3, id,
       '持有 SYS_ADMIN 的运维账号最多 3 个。注意约束的是运维账号，不是「系统管理后台」这个业务系统的使用者'
FROM sys_role WHERE role_code = 'SYS_ADMIN';

INSERT INTO sys_constraint (constraint_name, constraint_type, threshold, target_role_id, description) VALUES
    ('单用户角色数上限', 'CARD_USER_ROLE_MAX', 5, NULL, '一个用户最多拥有 5 个角色（全局规则）');

-- ⚠️ 预置但**默认停用**：会议纪要写「用户不存在多个角色，但可能后续拓展」。
--    数据模型按多对多建（不改 schema 即可支持两种口径），单角色只需启用本条约束。
--    待老师确认后 UPDATE enabled=1 即可，无需改动任何代码。见 SRS 附录 A-4、C.3 Q14。
INSERT INTO sys_constraint (constraint_name, constraint_type, threshold, target_role_id, enabled, description) VALUES
    ('单角色模式', 'CARD_USER_ROLE_MAX', 1, NULL, 0,
     '【默认停用】一个用户只能拥有 1 个角色。老师确认单角色口径后启用本条并停用「单用户角色数上限」');

INSERT INTO sys_constraint (constraint_name, constraint_type, threshold, target_role_id, description)
SELECT '普通员工权限上限', 'CARD_ROLE_PERM_MAX', 120, id,
       '普通员工角色最多关联 120 条权限（清单展开后实际约 96 条）'
FROM sys_role WHERE role_code = 'EMPLOYEE';

-- 先决条件角色
INSERT INTO sys_constraint (constraint_name, constraint_type, target_role_id, description)
SELECT '部门经理须先为员工', 'PREREQUISITE', id,
       '必须先拥有「普通员工」角色，才能被指派「部门经理」'
FROM sys_role WHERE role_code = 'DEPT_MANAGER';

INSERT INTO sys_constraint_role (constraint_id, role_id, role_position)
SELECT c.id, r.id, 'PREREQUISITE' FROM sys_constraint c, sys_role r
WHERE c.constraint_name = '部门经理须先为员工' AND r.role_code = 'EMPLOYEE';
