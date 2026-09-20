-- =============================================================================
-- V2 迭代二 RBAC1 角色继承 + 菜单 + 数据范围生效
--
-- 对应文档：docs/03-database.md §3.2
-- 本脚本新增的表在迭代二启用；V1 中的 sys_role_permission.data_scope 字段
-- 也在本迭代由判定管道阶段 S5b 开始执行（字段本身 V1 已建立）。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 角色继承
-- -----------------------------------------------------------------------------

-- 角色继承关系。构成**有向无环图（DAG）**而非树——一个角色可继承多个角色。
--
-- ⚠️ 语义约定（父子方向是本表最常见的缺陷来源，务必反复确认）：
--    child 继承 parent  =>  child 拥有 parent 的全部权限
--    例：部门经理(child) 继承 普通员工(parent)
--
-- 权限清单给出的 5 个角色是**扁平**的，彼此没有现成的继承关系，且不存在严格
-- 包含链（公司领导有「查看」之处，部门经理常有「编辑」）。迭代二的一项工作是
-- 对展开后的权限集合做两两包含关系分析，凡 Perms(A) ⊆ Perms(B) 者令 B 继承 A。
-- 这使角色层次是**从真实数据推导**出来的，而非凭空设计。
CREATE TABLE sys_role_inheritance (
    id             BIGINT   NOT NULL AUTO_INCREMENT,
    parent_role_id BIGINT   NOT NULL COMMENT '父角色：被继承者，权限的提供方',
    child_role_id  BIGINT   NOT NULL COMMENT '子角色：继承者，获得父角色的全部权限',
    created_by     BIGINT   NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inherit (parent_role_id, child_role_id),
    -- 两个方向的索引都必须建：
    --   idx_child  用于「求某角色的祖先」——闭包计算（读路径）
    --   idx_parent 用于「求某角色的后代」——缓存失效向下传播（写路径）
    -- 少建一个就会在对应路径上全表扫描。
    KEY idx_child  (child_role_id),
    KEY idx_parent (parent_role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色继承（DAG，非树）';

-- -----------------------------------------------------------------------------
-- 1.1 权限冻结（会议：「存在冻结权限」）
-- -----------------------------------------------------------------------------

-- 在角色-权限授予关系上增加冻结标记。
-- 冻结 ≠ 撤销：撤销会删除授予记录（配置丢失，恢复需重配），
-- 冻结保留记录但判定时视为不存在，可一键恢复。与账号冻结是同一思路的两处应用。
ALTER TABLE sys_role_permission
    ADD COLUMN frozen        TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = 冻结，判定时视为未授予但保留配置',
    ADD COLUMN frozen_reason VARCHAR(255) NULL,
    ADD COLUMN frozen_at     DATETIME     NULL,
    ADD KEY idx_frozen (role_id, frozen);

-- 同理，用户-角色指派也可冻结（保留指派关系但暂停生效）
ALTER TABLE sys_user_role
    ADD COLUMN frozen    TINYINT  NOT NULL DEFAULT 0 COMMENT '1 = 冻结该角色指派',
    ADD COLUMN frozen_at DATETIME NULL,
    ADD KEY idx_user_frozen (user_id, frozen);

-- -----------------------------------------------------------------------------
-- 2. 菜单
-- -----------------------------------------------------------------------------

-- 菜单按有效权限过滤后**不下发**（而非下发后由前端隐藏）：
-- 响应体本身不应泄露系统有哪些功能模块。
CREATE TABLE sys_menu (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id     BIGINT       NOT NULL DEFAULT 0 COMMENT '0 = 根节点',
    name          VARCHAR(64)  NOT NULL,
    path          VARCHAR(128) NULL               COMMENT '前端路由',
    icon          VARCHAR(64)  NULL,
    permission_id BIGINT       NULL               COMMENT '保护该菜单的权限；NULL = 公开',
    sort_order    INT          NOT NULL DEFAULT 0,
    visible       TINYINT      NOT NULL DEFAULT 1,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent     (parent_id),
    KEY idx_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单';

-- -----------------------------------------------------------------------------
-- 3. 导入任务与异常报告
--    ORG-005 的产出物。导入报告是检查时的展示材料之一。
-- -----------------------------------------------------------------------------

CREATE TABLE sys_import_task (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    task_no      VARCHAR(64)  NOT NULL,
    status       VARCHAR(32)  NOT NULL COMMENT 'RUNNING / COMPLETED / COMPLETED_WITH_WARNINGS / FAILED',
    counts       JSON         NULL     COMMENT '各表最终行数',
    started_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at  DATETIME     NULL,
    operator_id  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据导入任务';

CREATE TABLE sys_import_issue (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    task_id   BIGINT       NOT NULL,
    severity  VARCHAR(16)  NOT NULL COMMENT 'WARNING / ERROR',
    code      VARCHAR(64)  NOT NULL COMMENT 'CELL_VALUE_NORMALIZED / CELL_EMPTY / MANAGER_NAME_AMBIGUOUS / ...',
    location  VARCHAR(128) NULL     COMMENT '如「权限清单!行117」',
    detail    VARCHAR(512) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_task     (task_id),
    KEY idx_severity (task_id, severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入异常明细（对应 SRS 附录 C）';
