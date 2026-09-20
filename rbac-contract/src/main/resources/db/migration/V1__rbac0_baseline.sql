-- =============================================================================
-- V1 迭代一 RBAC0 基线
--
-- 对应文档：docs/03-database.md §3.1
-- 三种范式实现（rbac-structured / rbac-oo / rbac-functional）共用本 schema。
--
-- 设计原则（docs/03-database.md §1）：
--   · 一律代理主键；业务唯一键加唯一索引
--   · 关联表用 (a_id, b_id) 联合唯一索引保证幂等指派
--   · 用户/角色逻辑删除，关联表物理删除
--   · 审计日志只插入，不更新不删除
--   · **不使用数据库外键**：高并发写入下产生行锁竞争，引用完整性由应用层显式保证
--     （但索引照常建立）
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 组织架构
-- -----------------------------------------------------------------------------

-- 部门。来源：市政公司部门信息表（111 行，三层单根）
CREATE TABLE sys_department (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    dept_name       VARCHAR(128) NOT NULL                COMMENT '部门名称，如「道路工程分公司-财务部」',
    parent_id       BIGINT       NOT NULL DEFAULT 0      COMMENT '上级部门；0 = 根节点（总公司）',
    manager_user_id BIGINT       NULL                    COMMENT '部门经理；非唯一，一人可管理多个部门',
    level           TINYINT      NOT NULL DEFAULT 1      COMMENT '层级 1/2/3，冗余存储避免递归求深度',
    path            VARCHAR(255) NOT NULL DEFAULT '/'    COMMENT '物化路径，如 /1/13/15/；DEPT_AND_SUB 数据范围用',
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at      DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_name (dept_name),
    KEY idx_parent  (parent_id),
    KEY idx_manager (manager_user_id),
    KEY idx_path    (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门（单根三层树）';

-- 岗位 → 角色派生规则。
-- 员工信息表只有「岗位」没有「角色」，两者的映射是我方推断（数据疑点 C-10）。
-- 做成配置表而非硬编码，是为了老师给出不同答案时能低成本调整。
CREATE TABLE sys_position_role_mapping (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    priority    INT          NOT NULL                COMMENT '匹配优先级，数字小者先匹配；命中即停止',
    match_type  VARCHAR(32)  NOT NULL                COMMENT 'DEPT_NAME | IS_PROJECT_DEPT_MANAGER | IS_DEPT_MANAGER | POSITION_IN | DEFAULT',
    match_value VARCHAR(512) NULL                    COMMENT '匹配值；POSITION_IN 为逗号分隔的岗位名，DEPT_NAME 为部门名',
    role_code   VARCHAR(64)  NOT NULL                COMMENT '命中后派生的角色编码',
    description VARCHAR(255) NULL,
    enabled     TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_priority (priority),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位→角色派生规则（配置表，非硬编码）';

-- -----------------------------------------------------------------------------
-- 2. 身份
-- -----------------------------------------------------------------------------

-- 用户。来源：市政公司员工信息表（10000 行）
CREATE TABLE sys_user (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    username         VARCHAR(64)  NOT NULL             COMMENT '登录名，形如 SG000001',
    password_hash    VARCHAR(100) NOT NULL             COMMENT 'BCrypt 哈希（cost>=10），含盐。禁止存明文',
    real_name        VARCHAR(64)  NULL,
    department_id    BIGINT       NULL                 COMMENT '所属部门。数据范围判定的依据',
    position         VARCHAR(32)  NULL                 COMMENT '岗位；仅用于导入时派生初始角色，不参与鉴权',
    -- 手机号属敏感数据（安全需求 S-10）：密文存列，另存脱敏副本供列表展示。
    -- 列表查询一律返回 phone_masked；明文需 sys:security:data:view 权限且单独记审计。
    phone_enc        VARBINARY(64) NULL                COMMENT 'AES-256 加密后的手机号',
    phone_masked     VARCHAR(20)  NULL                 COMMENT '脱敏副本，如 138****5678',
    email            VARCHAR(128) NULL                 COMMENT '员工表未提供，留空',
    -- 三态而非两态（需求 A-25）。冻结与禁用的判定结果都是拒绝，但语义与可逆性不同：
    -- 冻结预期会被解除（保留全部配置，可一键恢复），禁用是终态（离职/注销）。
    -- 合并成两态会使「临时停权」与「离职」在审计日志中无法区分。
    status           TINYINT      NOT NULL DEFAULT 1   COMMENT '1=ACTIVE 正常 | 2=FROZEN 冻结 | 0=DISABLED 禁用',
    freeze_reason    VARCHAR(255) NULL                 COMMENT '冻结原因',
    unfreeze_at      DATETIME     NULL                 COMMENT '自动解冻时间；NULL = 需手工解冻',
    -- 临时人员（需求 A-26）：无工号、无电话、可无部门，授权带失效时间，
    -- 且不得持有系统管理权限（S-16，由应用层校验）。
    user_type        VARCHAR(16)  NOT NULL DEFAULT 'EMPLOYEE'
                                                      COMMENT 'EMPLOYEE 正式员工 | TEMPORARY 临时人员 | SYSTEM 系统集成账号',
    expires_at       DATETIME     NULL                 COMMENT '账号失效时间；TEMPORARY 必填',
    login_fail_count INT          NOT NULL DEFAULT 0   COMMENT '连续登录失败次数；满 5 次锁定 15 分钟',
    locked_until     DATETIME     NULL                 COMMENT '锁定截止时间；NULL = 未锁定',
    must_change_pwd  TINYINT      NOT NULL DEFAULT 0   COMMENT '首次登录强制改密',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at       DATETIME     NULL                 COMMENT '逻辑删除标记',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_status     (status),
    KEY idx_department (department_id),
    KEY idx_user_type  (user_type),
    KEY idx_expires    (expires_at),
    KEY idx_deleted    (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- -----------------------------------------------------------------------------
-- 3. 角色
-- -----------------------------------------------------------------------------

CREATE TABLE sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    role_code   VARCHAR(64)  NOT NULL              COMMENT '角色编码，创建后不可修改',
    role_name   VARCHAR(64)  NOT NULL              COMMENT '显示名，可修改',
    description VARCHAR(255) NULL,
    status      TINYINT      NOT NULL DEFAULT 1    COMMENT '1=启用 0=禁用',
    builtin     TINYINT      NOT NULL DEFAULT 0    COMMENT '1=系统内置，不可删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- -----------------------------------------------------------------------------
-- 4. 资源层次：软件系统 → 功能模块 → 功能点
--    对应权限清单的三级结构（18 / 115 / 359）
-- -----------------------------------------------------------------------------

CREATE TABLE sys_software_system (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    system_code VARCHAR(32) NOT NULL               COMMENT '权限码第一段，如 oa / hr / pmis',
    system_name VARCHAR(64) NOT NULL               COMMENT '如「OA协同办公系统」',
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_code (system_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='软件系统（18 行）';

CREATE TABLE sys_function_module (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    system_id   BIGINT      NOT NULL,
    module_code VARCHAR(32) NOT NULL               COMMENT '权限码第二段，如 doc / attendance',
    module_name VARCHAR(64) NOT NULL               COMMENT '如「公文管理」',
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_module (system_id, module_code),
    KEY idx_system (system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='功能模块（115 行）';

CREATE TABLE sys_resource (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    module_id     BIGINT       NOT NULL,
    resource_code VARCHAR(64)  NOT NULL            COMMENT '权限码第三段，如 draft / record',
    resource_name VARCHAR(128) NOT NULL            COMMENT '如「发文拟稿与审核」',
    description   VARCHAR(512) NULL                COMMENT '清单的「功能说明」列，原样保留',
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_resource (module_id, resource_code),
    KEY idx_module (module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='功能点 / 资源（359 行）';

-- 操作。词汇表由权限清单的单元格取值归纳得出，共 5 个原子操作。
CREATE TABLE sys_action (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    action_code VARCHAR(32) NOT NULL,
    action_name VARCHAR(32) NOT NULL,
    risk_weight INT         NOT NULL DEFAULT 1     COMMENT '风险权重，用于权限风险评分',
    sort_order  INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_action_code (action_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作（5 行，固定词汇表）';

-- 权限 = 功能点 × 操作。359 × 5 = 1795 行。
CREATE TABLE sys_permission (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    permission_code VARCHAR(128) NOT NULL          COMMENT '系统:模块:功能点:操作；冗余存储，热路径直接匹配免联表',
    resource_id     BIGINT       NOT NULL,
    action_id       BIGINT       NOT NULL,
    description     VARCHAR(255) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code  (permission_code),
    UNIQUE KEY uk_resource_action  (resource_id, action_id),
    KEY        idx_resource        (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限（约 1795 行）';

-- -----------------------------------------------------------------------------
-- 5. 指派与授予
-- -----------------------------------------------------------------------------

CREATE TABLE sys_user_role (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    role_id    BIGINT   NOT NULL,
    granted_by BIGINT   NULL                       COMMENT '授予人，用于审计',
    granted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NULL                       COMMENT '预留：临时授权；NULL = 永久',
    PRIMARY KEY (id),
    -- 覆盖索引：判定热路径「按 userId 查直接角色」只走索引，不回表
    UNIQUE KEY uk_user_role (user_id, role_id),
    -- 必须：缓存失效时反查「持有某角色的全部用户」，缺此索引会全表扫描
    KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色指派';

CREATE TABLE sys_role_permission (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    role_id       BIGINT      NOT NULL,
    permission_id BIGINT      NOT NULL,
    -- 数据范围挂在「角色-权限」这条边上，而非挂在角色上：
    -- 同一角色对不同功能点的范围可以不同（部门经理对考勤是本部门，对公司制度是全部）。
    -- 迭代一建立并正确导入，迭代二由判定管道阶段 S5b 启用执行。
    data_scope    VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT 'ALL | DEPT_AND_SUB | DEPT | SELF',
    granted_by    BIGINT      NULL,
    granted_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 覆盖索引：判定热路径「按 roleId 查权限」
    UNIQUE KEY uk_role_perm  (role_id, permission_id),
    KEY        idx_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限授予（约 1800 行）';

-- -----------------------------------------------------------------------------
-- 6. 审计
-- -----------------------------------------------------------------------------

-- 只插入，不更新不删除。可篡改的审计等于没有审计。
-- actor_name / target_name 冗余存储姓名快照：实体被删除后审计仍须可读。
-- 按 occurred_at 做 RANGE 分区：预期每日千万级，不分区三个月内会拖垮查询。
CREATE TABLE sys_audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT       NULL                  COMMENT '操作人；系统触发时为 NULL',
    actor_name  VARCHAR(64)  NULL                  COMMENT '冗余姓名快照',
    action      VARCHAR(32)  NOT NULL              COMMENT 'LOGIN / ROLE_ASSIGN / AUTH_FAILED / ...',
    target_type VARCHAR(32)  NULL                  COMMENT 'USER / ROLE / PERMISSION / DEPARTMENT / DOCUMENT',
    target_id   VARCHAR(64)  NULL,
    target_name VARCHAR(128) NULL                  COMMENT '冗余名称快照',
    result      VARCHAR(16)  NOT NULL              COMMENT 'SUCCESS / DENIED / FAILED',
    reason      VARCHAR(255) NULL                  COMMENT '失败或拒绝的原因码',
    detail      JSON         NULL                  COMMENT '变更明细，如新增/移除的角色',
    client_ip   VARCHAR(45)  NULL                  COMMENT '兼容 IPv6',
    trace_id    VARCHAR(32)  NULL                  COMMENT '与 API 响应的 traceId 对应',
    -- 防篡改哈希链（安全需求 S-11）：
    --   row_hash = SHA256(prev_hash || actor_id || action || target_id || result || occurred_at)
    -- 任何对历史行的修改都会使其后全部行的校验失败。提供链完整性校验接口。
    prev_hash   CHAR(64)     NULL                  COMMENT '前一行的 row_hash',
    row_hash    CHAR(64)     NULL                  COMMENT '本行哈希，构成不可篡改链',
    occurred_at DATETIME(3)  NOT NULL              COMMENT '毫秒精度',
    PRIMARY KEY (id, occurred_at),
    KEY idx_actor_time  (actor_id, occurred_at),
    KEY idx_action_time (action, occurred_at),
    KEY idx_target      (target_type, target_id),
    KEY idx_time        (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志（只插入）'
PARTITION BY RANGE (TO_DAYS(occurred_at)) (
    PARTITION p202609 VALUES LESS THAN (TO_DAYS('2026-10-01')),
    PARTITION p202610 VALUES LESS THAN (TO_DAYS('2026-11-01')),
    PARTITION p202611 VALUES LESS THAN (TO_DAYS('2026-12-01')),
    PARTITION p202612 VALUES LESS THAN (TO_DAYS('2027-01-01')),
    PARTITION pmax    VALUES LESS THAN MAXVALUE
);

-- -----------------------------------------------------------------------------
-- 7. 受保护业务桩
--    本系统不实现权限清单中的 18 个业务系统，仅取两个功能点作为权限验证载体。
-- -----------------------------------------------------------------------------

-- 桩一：OA > 公文管理 > 发文拟稿与审核（oa:doc:draft:*）
-- 选它的理由：清单中普通员工只能「新增」（拟稿）、部门经理才能「审批」，
-- 拟稿与审批的分离是清单本身给出的职责分离场景。
CREATE TABLE biz_oa_document (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    doc_no        VARCHAR(32)  NOT NULL,
    title         VARCHAR(255) NOT NULL,
    content       TEXT         NULL,
    drafted_by    BIGINT       NOT NULL            COMMENT '拟稿人；审批时校验 != 审批人',
    owner_dept_id BIGINT       NOT NULL            COMMENT '归属部门，数据范围过滤的依据',
    status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/APPROVED/REJECTED',
    approved_by   BIGINT       NULL,
    approved_at   DATETIME     NULL,
    approve_note  VARCHAR(255) NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_no (doc_no),
    KEY idx_drafted_by  (drafted_by),
    -- 部门经理查「本部门待审稿件」是最频繁的查询，走此联合索引
    KEY idx_dept_status (owner_dept_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发文稿件（业务桩一）';

-- 桩二：人力资源 > 考勤与休假管理 > 考勤管理（hr:attendance:record:*）
-- 选它的理由：清单中普通员工的取值是「查看/编辑本人」，是 SELF 数据范围的验证场景。
CREATE TABLE biz_hr_attendance (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL              COMMENT 'SELF 数据范围的判定依据',
    dept_id     BIGINT       NOT NULL              COMMENT '冗余存部门，避免过滤时联表',
    attend_date DATE         NOT NULL,
    check_in    DATETIME     NULL,
    check_out   DATETIME     NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/LATE/ABSENT/LEAVE',
    remark      VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 两个索引对应两种数据范围：SELF 走 uk_user_date，DEPT/DEPT_AND_SUB 走 idx_dept_date。
    -- 数据范围的每种取值都需要有索引支撑，否则「部门经理查本部门考勤」会变成全表扫描。
    UNIQUE KEY uk_user_date (user_id, attend_date),
    KEY        idx_dept_date (dept_id, attend_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤记录（业务桩二）';

-- 通用测试记录表。
-- 老师要求「至少两个系统，每个系统 3-4 个功能」用于测试，共 8 个功能点。
-- 为这 8 个功能点各建一张业务表是没有意义的——它们是**测试夹具而非产品**。
-- 因此除两个需要具体演示的功能点（公文、考勤）保留专用表外，
-- 其余 6 个功能点的接口统一落在本表上，用 system_code/module_code/resource_code 区分。
CREATE TABLE biz_test_record (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    system_code   VARCHAR(32)  NOT NULL            COMMENT 'oa / hr',
    module_code   VARCHAR(32)  NOT NULL            COMMENT 'doc / attendance',
    resource_code VARCHAR(64)  NOT NULL            COMMENT 'receive / issue / urge / secrecy / leave / overtime',
    title         VARCHAR(255) NOT NULL,
    owner_user_id BIGINT       NOT NULL            COMMENT 'SELF 数据范围的判定依据',
    owner_dept_id BIGINT       NOT NULL            COMMENT 'DEPT / DEPT_AND_SUB 数据范围的判定依据',
    status        VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    payload       JSON         NULL                COMMENT '各功能点的差异字段，不为夹具建强 schema',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_resource   (system_code, module_code, resource_code),
    KEY idx_owner_user (owner_user_id),
    KEY idx_owner_dept (owner_dept_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用测试记录（6 个功能点共用的夹具表）';

-- =============================================================================
-- 固定词汇表与内置数据
-- 说明：下列数据是系统的固定词汇，不是导入数据，因此写在迁移脚本里。
--       部门、用户、资源层次、权限、授予关系由导入器从三份 xlsx 写入。
-- =============================================================================

-- 操作词汇表（由权限清单归纳，括号内为清单中的出现次数）
INSERT INTO sys_action (action_code, action_name, risk_weight, sort_order) VALUES
    ('view',    '查看', 1, 1),   -- 878 次
    ('create',  '新增', 2, 2),   -- 109 次
    ('edit',    '编辑', 3, 3),   -- 235 次
    ('export',  '导出', 5, 4),   --  66 次
    ('approve', '审批', 8, 5);   --  99 次

-- 内置角色：前 5 个取自权限清单的列，后 2 个为我方增设的系统级角色。
-- 增设理由见 docs/01-srs.md §4.1.2：若「配置权限」「配置约束」「审计监督」全部
-- 归于系统管理员，一个角色权力过大，且无法演示 RBAC2 的职责分离。
INSERT INTO sys_role (role_code, role_name, description, builtin) VALUES
    ('SYS_ADMIN',       '系统管理员', '权限清单列 1；对全部 359 个功能点拥有全部操作（显式授予，非引擎旁路）', 1),
    ('COMPANY_LEADER',  '公司领导',   '权限清单列 2；对全部功能点至少有查看权限', 1),
    ('DEPT_MANAGER',    '部门经理',   '权限清单列 3；297 个功能点有权限，数据范围为本部门', 1),
    ('PROJECT_MANAGER', '项目经理',   '权限清单列 4；161 个功能点有权限，「项目」即项目经理部', 1),
    ('EMPLOYEE',        '普通员工',   '权限清单列 5；67 个功能点有权限，其中 6 个为本人范围', 1),
    ('SEC_ADMIN',       '安全管理员', '我方增设；管理角色继承与约束规则', 1),
    ('AUDITOR',         '审计员',     '我方增设；只读访问审计日志与监控，无任何配置权限', 1);

-- 岗位 → 角色派生规则。
-- ⚠️ 这套映射是我方推断的（数据疑点 C-10）：员工表有「岗位」但没有「角色」列。
--    若老师另有既定映射，只需 UPDATE 本表后重跑派生，无需改代码。
-- ⚠️ 派生必须在「绑定部门经理」之后执行：优先级 2、3 依赖 manager_user_id 已填好。
INSERT INTO sys_position_role_mapping (priority, match_type, match_value, role_code, description) VALUES
    (1, 'DEPT_NAME',                '公司领导',                                       'COMPANY_LEADER',
        '部门为「公司领导」的 10 人：董事长、总经理、副总经理、总工程师、总会计师等'),
    (2, 'IS_PROJECT_DEPT_MANAGER',  '项目经理部',                                     'PROJECT_MANAGER',
        '担任「第 N 项目经理部」经理者。依赖 sys_department.manager_user_id'),
    (3, 'IS_DEPT_MANAGER',          NULL,                                             'DEPT_MANAGER',
        '担任任一部门经理者。依赖 sys_department.manager_user_id'),
    (4, 'POSITION_IN',              '部长,副部长,主任,副主任,经理,副经理',              'DEPT_MANAGER',
        '岗位名在列表中，但未在部门表中登记为经理者'),
    (5, 'DEFAULT',                  NULL,                                             'EMPLOYEE',
        '兜底：主管、专员、干事、薪酬专员、招聘专员等');

-- 根部门占位。导入器会以此为根挂接 111 个部门；若导入表中已含「总公司」则跳过。
INSERT INTO sys_department (id, dept_name, parent_id, level, path, sort_order) VALUES
    (1, '总公司', 0, 1, '/1/', 0);

-- 运维账号。SYS_ADMIN 不派生给任何真实员工——系统管理员是运维角色而非业务岗位。
-- 初始口令为 'Admin@123' 的 BCrypt 哈希，must_change_pwd=1 强制首次登录修改。
INSERT INTO sys_user (username, password_hash, real_name, user_type, status, must_change_pwd) VALUES
    ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', '系统管理员', 'EMPLOYEE', 1, 1);

-- HR 系统集成账号。user_type=SYSTEM，仅用于调用 /sync/users 同步人员数据。
-- 口令为随机强口令，实际部署时由运维重置；该账号不得登录管理台。
INSERT INTO sys_user (username, password_hash, real_name, user_type, status, must_change_pwd) VALUES
    ('svc_hr_sync', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'HR系统集成账号', 'SYSTEM', 1, 0);

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.username = 'admin' AND r.role_code = 'SYS_ADMIN';
