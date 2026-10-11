-- =============================================================================
-- V1 RBAC0 基线（docs/03-database.md）
--
-- 本阶段只实现 RBAC0：User — Role — Permission(Resource × Action)。
-- 不含角色继承、职责分离、基数与时间约束、会话角色（旧版 V2/V3 已移到 docs/archive/sql/，不再执行）。
--
-- 设计原则：
--   · 组织关系与授权关系一律建外键：数据只有万级，外键带来的锁开销可以忽略，
--     却能挡住「指向不存在的用户/角色/部门」这类脏数据（docs/12-design-log.md #16）。
--   · 关联表用联合唯一键保证重复提交不产生重复记录。
--   · 用户逻辑删除（status = DELETED），行保留供审计，用户名不可复用；关联表物理删除。
--   · 审计日志只插入，不更新不删除。
-- =============================================================================

SET NAMES utf8mb4;

-- -----------------------------------------------------------------------------
-- 1. 组织：公司 → 部门树
-- -----------------------------------------------------------------------------

-- 公司是多公司隔离的边界（需求「能够应对多个公司」）。
-- 部门、用户、公司内角色都归属一家公司；部门名只在同一上级下唯一，两家公司都可以有「财务部」。
CREATE TABLE sys_company (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    company_code VARCHAR(32)  NOT NULL,
    company_name VARCHAR(128) NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_code (company_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公司';

-- 部门树。每家公司一个根节点（parent_id 为空）。
-- path 是物化路径（如 /3/15/27/）：判断「目标部门是否在某部门之下」只需一次前缀比较，不用递归。
CREATE TABLE sys_department (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    company_id      BIGINT       NOT NULL,
    parent_id       BIGINT       NULL                 COMMENT '上级部门；根节点为空',
    dept_name       VARCHAR(128) NOT NULL             COMMENT '原表名称，如「道路工程分公司-财务部」',
    path            VARCHAR(255) NOT NULL             COMMENT '物化路径 /祖先id/.../本id/',
    level           INT          NOT NULL             COMMENT '根为 1',
    manager_name    VARCHAR(64)  NULL                 COMMENT '部门表登记的负责人姓名（原样保留）',
    manager_user_id BIGINT       NULL                 COMMENT '负责人对应的用户；只是组织信息，不产生任何管理权限',
    sort_order      INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_sibling (company_id, parent_id, dept_name),
    KEY idx_parent (parent_id),
    KEY idx_path (path),
    CONSTRAINT fk_dept_company FOREIGN KEY (company_id) REFERENCES sys_company (id),
    CONSTRAINT fk_dept_parent  FOREIGN KEY (parent_id)  REFERENCES sys_department (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门（每家公司一棵树）';

-- -----------------------------------------------------------------------------
-- 2. 用户
-- -----------------------------------------------------------------------------

-- 一个用户只属于一个部门（department_id）；他能在哪些部门行使权限，由 sys_user_role.scope_dept_id 决定。
CREATE TABLE sys_user (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    company_id             BIGINT       NOT NULL,
    department_id          BIGINT       NOT NULL,
    username               VARCHAR(64)  NOT NULL          COMMENT '登录名；正式员工为工号，如 SG000001',
    password_hash          VARCHAR(100) NOT NULL          COMMENT 'BCrypt',
    real_name              VARCHAR(64)  NOT NULL,
    user_type              VARCHAR(16)  NOT NULL DEFAULT 'EMPLOYEE' COMMENT 'EMPLOYEE 正式员工 | TEMPORARY 临时用户 | SYSTEM 业务系统账号',
    employee_no            VARCHAR(32)  NULL              COMMENT '工号；临时用户没有',
    phone                  VARCHAR(20)  NULL              COMMENT '电话；临时用户没有',
    position               VARCHAR(32)  NULL              COMMENT '岗位；只是人事信息，不参与鉴权',
    status                 VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 启用 | DISABLED 停用 | DELETED 删除',
    status_reason          VARCHAR(255) NULL,
    expires_at             DATETIME     NULL              COMMENT '账号到期时间；临时用户必填',
    login_fail_count       INT          NOT NULL DEFAULT 0,
    locked_until           DATETIME     NULL,
    must_change_pwd        TINYINT      NOT NULL DEFAULT 0,
    token_version          INT          NOT NULL DEFAULT 0 COMMENT '修改口令时加一；令牌中的版本号与它不等即失效',
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at             DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_employee_no (employee_no),
    KEY idx_dept_status (department_id, status),
    KEY idx_company (company_id),
    CONSTRAINT fk_user_company FOREIGN KEY (company_id)    REFERENCES sys_company (id),
    CONSTRAINT fk_user_dept    FOREIGN KEY (department_id) REFERENCES sys_department (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

ALTER TABLE sys_department
    ADD CONSTRAINT fk_dept_manager FOREIGN KEY (manager_user_id) REFERENCES sys_user (id);

-- -----------------------------------------------------------------------------
-- 3. 资源与权限：业务系统 → 功能模块 → 资源；Permission = Resource × Action
-- -----------------------------------------------------------------------------

-- 业务系统（多业务系统）。权限清单的 18 个系统 + 权限中心自身（rbac）。
CREATE TABLE sys_software_system (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    system_code VARCHAR(32) NOT NULL COMMENT '权限码第一段',
    system_name VARCHAR(64) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_code (system_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务系统';

CREATE TABLE sys_function_module (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    system_id   BIGINT      NOT NULL,
    module_code VARCHAR(32) NOT NULL,
    module_name VARCHAR(64) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module (system_id, module_code),
    CONSTRAINT fk_module_system FOREIGN KEY (system_id) REFERENCES sys_software_system (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='功能模块';

-- 资源。resource_type：
--   BUSINESS 业务功能点（权限清单导入）；
--   MENU     权限中心自身的管理页面，菜单挂在它上面（「前端菜单也是一种 Resource」）；
--   API      不对应页面的接口能力，如供业务系统调用的鉴权接口。
CREATE TABLE sys_resource (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    module_id     BIGINT       NOT NULL,
    resource_code VARCHAR(96)  NOT NULL COMMENT '完整编码，如 oa:m01:f02、rbac:user',
    resource_name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(16)  NOT NULL DEFAULT 'BUSINESS' COMMENT 'BUSINESS | MENU | API',
    description   VARCHAR(512) NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_code (resource_code),
    KEY idx_module (module_id),
    CONSTRAINT fk_resource_module FOREIGN KEY (module_id) REFERENCES sys_function_module (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源';

CREATE TABLE sys_action (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    action_code VARCHAR(32) NOT NULL,
    action_name VARCHAR(32) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_action_code (action_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作';

-- 权限 = 资源 × 操作。permission_code = resource_code:action_code，是鉴权时比对的字符串。
CREATE TABLE sys_permission (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    resource_id     BIGINT       NOT NULL,
    action_id       BIGINT       NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code),
    UNIQUE KEY uk_resource_action (resource_id, action_id),
    CONSTRAINT fk_perm_resource FOREIGN KEY (resource_id) REFERENCES sys_resource (id),
    CONSTRAINT fk_perm_action   FOREIGN KEY (action_id)   REFERENCES sys_action (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限';

-- -----------------------------------------------------------------------------
-- 4. 角色、角色授权、用户指派
-- -----------------------------------------------------------------------------

-- company_id 为空 = 全局角色（内置角色与权限清单的五类角色模板），只有超级管理员能修改；
-- 非空 = 公司内角色，只能指派到本公司的部门。
CREATE TABLE sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    company_id  BIGINT       NULL,
    role_code   VARCHAR(64)  NOT NULL,
    role_name   VARCHAR(64)  NOT NULL,
    description VARCHAR(255) NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED | DISABLED',
    builtin     TINYINT      NOT NULL DEFAULT 0 COMMENT '内置角色不可删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code),
    CONSTRAINT fk_role_company FOREIGN KEY (company_id) REFERENCES sys_company (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

-- self_only：权限清单中「查看本人」「编辑本人」这类取值。RBAC0 只判断能否执行该操作，
-- 「只限本人记录」随鉴权结果返回给业务系统，由业务系统按记录归属过滤（docs/03-database.md §4.3）。
CREATE TABLE sys_role_permission (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    role_id       BIGINT   NOT NULL,
    permission_id BIGINT   NOT NULL,
    self_only     TINYINT  NOT NULL DEFAULT 0,
    granted_by    BIGINT   NULL COMMENT '授权人，只用于审计',
    granted_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    KEY idx_permission (permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id)       REFERENCES sys_role (id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限';

-- 一次指派 = 用户 + 角色 + 适用部门。scope_dept_id 覆盖该部门及其全部下级部门。
-- 同一用户可以在不同部门持有不同角色，或在多个部门持有同一角色（需求「可以有属于多个部门的权限」）。
-- granted_by 只是审计属性：撤销授权人自己的角色，不影响他过去授出的指派（需求「不会导致级联删除」）。
CREATE TABLE sys_user_role (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    role_id       BIGINT       NOT NULL,
    scope_dept_id BIGINT       NOT NULL COMMENT '适用部门（含下级）',
    granted_by    BIGINT       NULL,
    granted_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason        VARCHAR(255) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role_scope (user_id, role_id, scope_dept_id),
    KEY idx_role (role_id),
    KEY idx_scope (scope_dept_id),
    CONSTRAINT fk_ur_user  FOREIGN KEY (user_id)       REFERENCES sys_user (id),
    CONSTRAINT fk_ur_role  FOREIGN KEY (role_id)       REFERENCES sys_role (id),
    CONSTRAINT fk_ur_scope FOREIGN KEY (scope_dept_id) REFERENCES sys_department (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色指派';

-- -----------------------------------------------------------------------------
-- 5. 菜单
-- -----------------------------------------------------------------------------

-- 叶子菜单挂一个 MENU 资源，用户持有「该资源:view」才显示；目录节点（resource_id 为空）
-- 在至少有一个子菜单可见时显示。隐藏菜单只是界面行为，后端接口仍各自鉴权。
CREATE TABLE sys_menu (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    parent_id     BIGINT      NULL,
    resource_id   BIGINT      NULL,
    menu_name     VARCHAR(64) NOT NULL,
    route_path    VARCHAR(128) NULL,
    component_key VARCHAR(64) NULL,
    icon          VARCHAR(32) NULL,
    sort_order    INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_parent (parent_id),
    CONSTRAINT fk_menu_parent   FOREIGN KEY (parent_id)   REFERENCES sys_menu (id),
    CONSTRAINT fk_menu_resource FOREIGN KEY (resource_id) REFERENCES sys_resource (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单';

-- -----------------------------------------------------------------------------
-- 6. 审计
-- -----------------------------------------------------------------------------

-- 一张表记录全部事件：谁(actor) 什么时候(occurred_at) 在哪里(client_ip) 对谁(target)
-- 做了什么(event_type + detail) 结果如何(result) 为什么(reason)。
-- 鉴权事件（AUTH_SUCCESS / AUTH_FAILED）另带 permission_code、latency_us、cache_hit，
-- Dashboard 与权限使用热力图都从这里统计。
CREATE TABLE sys_audit_log (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    occurred_at     DATETIME(3)   NOT NULL,
    actor_id        BIGINT        NULL,
    actor_name      VARCHAR(64)   NULL     COMMENT '用户名快照',
    client_ip       VARCHAR(45)   NULL,
    event_type      VARCHAR(32)   NOT NULL COMMENT 'LOGIN / LOGOUT / AUTH_SUCCESS / AUTH_FAILED / ROLE_ASSIGN / ...',
    target_type     VARCHAR(16)   NULL     COMMENT 'USER / ROLE / MENU / RESOURCE / DEPARTMENT',
    target_id       VARCHAR(64)   NULL,
    target_name     VARCHAR(128)  NULL,
    detail          VARCHAR(1000) NULL,
    result          VARCHAR(16)   NOT NULL COMMENT 'SUCCESS / DENIED / FAILED',
    reason          VARCHAR(255)  NULL,
    permission_code VARCHAR(128)  NULL,
    latency_us      INT           NULL,
    cache_hit       TINYINT       NULL,
    PRIMARY KEY (id),
    KEY idx_time (occurred_at),
    KEY idx_event_time (event_type, occurred_at),
    KEY idx_actor_time (actor_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志（只插入）';

-- =============================================================================
-- 内置数据：操作词汇、权限中心自身的资源与权限、内置角色、菜单、平台公司与超级管理员。
-- 部门、员工、18 个业务系统的资源与五类角色模板由导入接口从三份 xlsx 写入。
-- =============================================================================

INSERT INTO sys_action (action_code, action_name, sort_order) VALUES
    ('view',    '查看', 1),
    ('create',  '新增', 2),
    ('edit',    '编辑', 3),
    ('approve', '审批', 4),
    ('export',  '导出', 5),
    ('delete',  '删除', 6),
    ('assign',  '分配', 7),
    ('revoke',  '回收', 8),
    ('import',  '导入', 9),
    ('invoke',  '调用', 10),
    ('explain', '解释', 11);

INSERT INTO sys_software_system (id, system_code, system_name, sort_order) VALUES
    (1, 'rbac', '权限中心', 0);

INSERT INTO sys_function_module (id, system_id, module_code, module_name, sort_order) VALUES
    (1, 1, 'admin', '系统管理', 1),
    (2, 1, 'authz', '鉴权服务', 2);

INSERT INTO sys_resource (id, module_id, resource_code, resource_name, resource_type, sort_order) VALUES
    (1,  1, 'rbac:dashboard',  'Dashboard',      'MENU', 1),
    (2,  1, 'rbac:user',       '用户管理',        'MENU', 2),
    (3,  1, 'rbac:role',       '角色管理',        'MENU', 3),
    (4,  1, 'rbac:permission', '权限管理',        'MENU', 4),
    (5,  1, 'rbac:menu',       '菜单管理',        'MENU', 5),
    (6,  1, 'rbac:audit',      '审计日志',        'MENU', 6),
    (7,  1, 'rbac:org',        '组织管理',        'MENU', 7),
    (8,  1, 'rbac:assignment', '用户角色指派',    'API',  8),
    (9,  1, 'rbac:grant',      '角色权限授予',    'API',  9),
    (10, 2, 'rbac:authz',      '鉴权接口',        'API',  10),
    (11, 1, 'rbac:heatmap',    '权限使用热力图',  'MENU', 11),
    (12, 1, 'rbac:explain',    '权限解释',        'MENU', 12);

-- 权限中心自身的权限，取值与 rbac-api.yaml 的 x-required-permission 一一对应。
INSERT INTO sys_permission (resource_id, action_id, permission_code)
SELECT r.id, a.id, CONCAT(r.resource_code, ':', a.action_code)
FROM sys_resource r JOIN sys_action a ON
     (r.resource_code = 'rbac:dashboard'  AND a.action_code = 'view')
  OR (r.resource_code = 'rbac:user'       AND a.action_code IN ('view', 'create', 'edit', 'delete'))
  OR (r.resource_code = 'rbac:role'       AND a.action_code IN ('view', 'create', 'edit', 'delete'))
  OR (r.resource_code = 'rbac:permission' AND a.action_code IN ('view', 'create'))
  OR (r.resource_code = 'rbac:menu'       AND a.action_code IN ('view', 'create', 'edit', 'delete'))
  OR (r.resource_code = 'rbac:audit'      AND a.action_code = 'view')
  OR (r.resource_code = 'rbac:org'        AND a.action_code IN ('view', 'import'))
  OR (r.resource_code = 'rbac:assignment' AND a.action_code IN ('assign', 'revoke'))
  OR (r.resource_code = 'rbac:grant'      AND a.action_code IN ('assign', 'revoke'))
  OR (r.resource_code = 'rbac:authz'      AND a.action_code IN ('invoke', 'explain'))
  OR (r.resource_code = 'rbac:heatmap'    AND a.action_code = 'view')
  OR (r.resource_code = 'rbac:explain'    AND a.action_code = 'view');

-- 内置角色：
--   SUPER_ADMIN  越过权限引擎（判定时账号有效即放行），没有也不需要任何授权记录；
--   RBAC_ADMIN   权限管理员：权限中心的全部管理权限，管理范围由指派的适用部门决定；
--   AUDITOR      审计员：只读审计、Dashboard、热力图。
INSERT INTO sys_role (id, role_code, role_name, description, builtin) VALUES
    (1, 'SUPER_ADMIN', '超级管理员', '越过权限引擎；账号必须有效', 1),
    (2, 'RBAC_ADMIN',  '权限管理员', '管理指派部门及其下级部门的用户、角色指派', 1),
    (3, 'AUDITOR',     '审计员',     '只读：审计日志、Dashboard、热力图', 1);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, p.id FROM sys_permission p WHERE p.permission_code LIKE 'rbac:%'
  AND p.permission_code NOT IN ('rbac:org:import', 'rbac:permission:create');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 3, p.id FROM sys_permission p
WHERE p.permission_code IN ('rbac:audit:view', 'rbac:dashboard:view', 'rbac:heatmap:view');

-- 菜单（需求「系统管理」菜单树 + Dashboard、组织管理、两项创新功能）
INSERT INTO sys_menu (id, parent_id, resource_id, menu_name, route_path, component_key, icon, sort_order) VALUES
    (1,  NULL, 1,    'Dashboard',      '/dashboard',   'Dashboard',   'odometer', 1),
    (2,  NULL, NULL, '系统管理',        NULL,           NULL,          'setting',  2),
    (3,  2,    2,    '用户管理',        '/users',       'UserList',    'user',     1),
    (4,  2,    3,    '角色管理',        '/roles',       'RoleList',    'avatar',   2),
    (5,  2,    4,    '权限管理',        '/permissions', 'Permission',  'key',      3),
    (6,  2,    5,    '菜单管理',        '/menus',       'MenuList',    'menu',     4),
    (7,  2,    6,    '审计日志',        '/audit-logs',  'AuditLog',    'document', 5),
    (8,  2,    7,    '组织管理',        '/org',         'OrgTree',     'office',   6),
    (9,  NULL, 12,   '权限解释',        '/explain',     'Explain',     'search',   3),
    (10, NULL, 11,   '权限使用热力图',  '/heatmap',     'Heatmap',     'grid',     4);

-- 平台运维：超级管理员所在的公司与部门。业务公司由导入接口创建。
INSERT INTO sys_company (id, company_code, company_name) VALUES (1, 'PLATFORM', '平台运维');
INSERT INTO sys_department (id, company_id, parent_id, dept_name, path, level) VALUES
    (1, 1, NULL, '平台运维部', '/1/', 1);

-- 超级管理员。初始口令 Admin@123（BCrypt），首次登录必须修改。
INSERT INTO sys_user (id, company_id, department_id, username, password_hash, real_name, user_type, must_change_pwd) VALUES
    (1, 1, 1, 'root', '$2a$10$QML7HXgspyFoS7XwRrS1meN2EnGnUOGtXCLQteHHodYiCZQIFb/Jm', '超级管理员', 'SYSTEM', 1);

INSERT INTO sys_user_role (user_id, role_id, scope_dept_id, reason) VALUES (1, 1, 1, '系统初始化');
