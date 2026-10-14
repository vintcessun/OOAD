# API 设计说明书

> 接口的唯一来源是 [`rbac-api.yaml`](../rbac-contract/src/main/resources/openapi/rbac-api.yaml)（OpenAPI 3.0），本文说明约定、权限声明与关键接口的语义。
> 本阶段共 **45 个接口**，全部已实现，并由集成测试经 HTTP 调用过。旧契约中的角色继承、约束、会话、身份模拟等接口已删除（见 [12 #22](12-design-log.md)、[#28](12-design-log.md)）。

---

## 1. 通用约定

### 1.1 地址与格式

- 基础路径 `/api/v1`，权限中心端口 8081，外部模拟业务系统端口 8090。
- 请求、响应均为 JSON（导入接口为 `multipart/form-data`）。时间为 ISO-8601 本地时间，如 `2026-10-11T09:30:00`。
- 分页参数 `page`（从 0 开始）、`size`（默认 20，最大 200）；分页结果 `{items, total, page, size}`。

### 1.2 统一响应

```json
{ "code": 0, "message": "success", "data": { }, "timestamp": "2026-10-11T09:30:00+08:00" }
```

### 1.3 HTTP 状态与错误码

| HTTP | code | 含义 | 典型场景 |
|---|---|---|---|
| 200 | 0 | 成功 | 包括鉴权判定为「不允许」：`data.allowed = false` |
| 400 | 40001 | 参数不合法 | 临时用户填了工号；`targetType` 不认识；JSON 格式错误 |
| 401 | 40101 | 未认证 | 没带令牌、令牌过期、已登出、改过口令、账号已停用 / 删除 / 到期 |
| 401 | 40102 | 用户名或口令错误 | 不区分「用户不存在」与「口令错误」 |
| 401 | 40103 | 账号已锁定 | 连续失败 5 次，锁 15 分钟 |
| 401 | 40104 | 账号已停用或已到期 | 口令正确之后才告知 |
| 403 | 40301 | 没有该操作的权限 | 拦截器判定拒绝；无权查询他人权限 |
| 403 | 40302 | 目标超出管理范围 | 给别的分公司的人分配角色 |
| 403 | 40303 | 越权授予 | 分配的角色含有操作者在适用部门不具备的权限 |
| 403 | 40304 | 首次登录须先修改口令 | 改口令前只能调用 `/auth/me`、`/auth/logout`、`/auth/password` |
| 404 | 40401 | 目标不存在 | |
| 409 | 40901 | 重复 | 同一用户、角色、适用部门的指派已存在；用户名已存在 |
| 409 | 40902 | 状态冲突 | 删除仍有人持有的角色；回收最后一个超级管理员；停用自己 |

**鉴权接口判定为「无权限」时返回 200 + `allowed=false`。** 业务系统问「他能不能做」，权限中心答「不能」，这是一次正常完成的调用；403 只表示「调用者自己无权调用这个接口」。两者混在一起，业务系统就分不清「用户没权限」和「我自己的账号配错了」。

### 1.4 所有接口都受权限控制

每个接口在契约中声明 `x-required-permission`，运行时拦截器 `PermissionInterceptor` **只认这里**：

| 取值 | 含义 |
|---|---|
| 权限码，如 `rbac:user:view` | 拦截器调用判定过程（与业务系统调用 `/authz/check` 是同一个过程），没有该权限返回 403，并记一条 `AUTH_SUCCESS` / `AUTH_FAILED` |
| `@self` | 登录即可。能操作谁由 Service 判断：本人，或在管理范围内的人 |
| `security: []` | 免登录，只有 `/auth/login` |

拦截器只确认「能执行这一类操作」；被操作的对象是否在管理范围内，由 Service 按目标对象所在部门再判定一次（见 [06 §5.4](06-detail-structured.md)）。

两个方向都有自动检查，任何一边不一致应用都**拒绝启动**：

1. 代码里有、契约里没声明权限的路由（防止漏控）；
2. 契约里有、代码没实现的接口（防止「YAML 里写了」被当成「已经交付」）。

契约里用到的每个权限码，其资源都在 V1 脚本中建好（`ContractRoutesTest` 检查）。

## 2. 接口总表

| # | 分组 | 接口 | 所需权限 | 说明 |
|---|---|---|---|---|
| 1 | 登录 | `POST /auth/login` | （免登录） | 登录 |
| 2 | 登录 | `POST /auth/logout` | `@self` | 登出 |
| 3 | 登录 | `GET /auth/me` | `@self` | 当前用户的基本信息、角色与有效权限 |
| 4 | 登录 | `POST /auth/password` | `@self` | 修改本人口令 |
| 5 | 鉴权 | `POST /authz/check` | `@self` | 鉴权：某用户能否对某部门的数据执行某操作 |
| 6 | 鉴权 | `POST /authz/explain` | `@self` | 权限解释：与 check 相同的判定，另返回逐步的判定依据（trace） |
| 7 | 用户 | `GET /users` | `rbac:user:view` | 用户列表与搜索（只返回管理范围内的用户） |
| 8 | 用户 | `POST /users` | `rbac:user:create` | 新增用户（正式员工或临时用户） |
| 9 | 用户 | `POST /users/batch-status` | `rbac:user:edit` | 批量启用 / 停用 / 删除 |
| 10 | 用户 | `GET /users/{id}` | `@self` | 用户详情：基本信息、角色、有效权限 |
| 11 | 用户 | `PATCH /users/{id}` | `rbac:user:edit` | 修改用户（姓名、部门、电话、岗位、到期时间） |
| 12 | 用户 | `DELETE /users/{id}` | `rbac:user:delete` | 删除用户（逻辑删除，用户名不可复用，指派保留供审计） |
| 13 | 用户 | `PATCH /users/{id}/status` | `rbac:user:edit` | 启用或停用 |
| 14 | 用户 | `GET /users/{id}/roles` | `@self` | 用户的角色指派 |
| 15 | 用户 | `POST /users/{id}/roles` | `rbac:assignment:assign` | 为用户分配角色（ROLE_ASSIGN） |
| 16 | 用户 | `DELETE /users/{id}/roles/{assignmentId}` | `rbac:assignment:revoke` | 回收一次角色指派（ROLE_REVOKE） |
| 17 | 用户 | `GET /users/{id}/permissions` | `@self` | 用户的有效权限（每条带来源角色与适用部门） |
| 18 | 角色 | `GET /roles` | `rbac:role:view` | 角色列表：名称、编码、用户数、权限数、状态 |
| 19 | 角色 | `POST /roles` | `rbac:role:create` | 新增角色 |
| 20 | 角色 | `GET /roles/{id}` | `rbac:role:view` | 角色详情：基本信息与权限 |
| 21 | 角色 | `PATCH /roles/{id}` | `rbac:role:edit` | 修改角色名称、描述、启用停用 |
| 22 | 角色 | `DELETE /roles/{id}` | `rbac:role:delete` | 删除角色（内置角色不能删；仍有人持有时 409） |
| 23 | 角色 | `GET /roles/{id}/users` | `rbac:role:view` | 角色详情「用户」：持有该角色的指派 |
| 24 | 角色 | `POST /roles/{id}/permissions` | `rbac:grant:assign` | 为角色授予权限（PERMISSION_ASSIGN） |
| 25 | 角色 | `DELETE /roles/{id}/permissions/{permissionId}` | `rbac:grant:revoke` | 回收角色的一个权限（PERMISSION_REVOKE） |
| 26 | 批量授权 | `POST /assignments/batch` | `rbac:assignment:assign` | 按分公司 / 部门 / 员工批量分配角色 |
| 27 | 批量授权 | `POST /assignments/batch-revoke` | `rbac:assignment:revoke` | 按分公司 / 部门 / 员工批量回收角色 |
| 28 | 资源与权限 | `GET /systems` | `rbac:permission:view` | 业务系统与功能模块（权限域） |
| 29 | 资源与权限 | `GET /resources` | `rbac:permission:view` | 资源列表（BUSINESS / MENU / API） |
| 30 | 资源与权限 | `POST /resources` | `rbac:permission:create` | 新增资源，并为给出的操作建立权限 |
| 31 | 资源与权限 | `GET /actions` | `rbac:permission:view` | 操作词汇表 |
| 32 | 资源与权限 | `GET /permissions` | `rbac:permission:view` | 权限列表（Permission = Resource × Action） |
| 33 | 资源与权限 | `POST /permissions` | `rbac:permission:create` | 为已有资源新增一个操作的权限 |
| 34 | 菜单 | `GET /menus/me` | `@self` | 当前用户可见的菜单树（按「菜单资源:view」动态生成） |
| 35 | 菜单 | `GET /menus` | `rbac:menu:view` | 完整菜单树 |
| 36 | 菜单 | `POST /menus` | `rbac:menu:create` | 新增菜单 |
| 37 | 菜单 | `PATCH /menus/{id}` | `rbac:menu:edit` | 修改菜单 |
| 38 | 菜单 | `DELETE /menus/{id}` | `rbac:menu:delete` | 删除菜单（有子菜单时 409） |
| 39 | 组织与导入 | `GET /companies` | `rbac:org:view` | 公司列表（只含管理范围涉及的公司） |
| 40 | 组织与导入 | `GET /departments/tree` | `rbac:org:view` | 部门树（管理范围内的部门及通往它们的祖先） |
| 41 | 组织与导入 | `POST /imports/organization` | `rbac:org:import` | 导入部门信息表与员工信息表 |
| 42 | 组织与导入 | `POST /imports/permissions` | `rbac:permission:create` | 导入应用软件功能权限清单 |
| 43 | 审计与统计 | `GET /permissions/heatmap` | `rbac:heatmap:view` | 权限使用热力图（业务系统 × 操作，成功鉴权次数） |
| 44 | 审计与统计 | `GET /audit-logs` | `rbac:audit:view` | 审计日志：谁、什么时候、在哪里、对谁、做了什么、结果如何、为什么；`abnormal=true` 只看异常访问，口径与 Dashboard 的 `abnormalCount` 相同 |
| 45 | 审计与统计 | `GET /dashboard` | `rbac:dashboard:view` | Dashboard 十一项指标 |

内置角色持有的平台权限：

| 角色 | 平台权限 |
|---|---|
| `SUPER_ADMIN` 超级管理员 | 不需要授权，越过权限引擎（账号必须有效） |
| `RBAC_ADMIN` 权限管理员 | 除 `rbac:org:import`、`rbac:permission:create` 以外的 24 个；管理范围 = 指派的适用部门及其下级 |
| `AUDITOR` 审计员 | `rbac:audit:view`、`rbac:dashboard:view`、`rbac:heatmap:view` |

导入与新建权限定义改动的是全平台的配置，默认只有超级管理员能做。

## 3. 关键接口

### 3.1 登录与令牌

```http
POST /api/v1/auth/login
{ "username": "SG000123", "password": "..." }

→ 200 { "data": { "token": "eyJ...", "expiresIn": 7200, "userId": 105, "username": "SG000123",
                  "realName": "示例员工", "mustChangePwd": true } }
```

- 令牌（JWT，2 小时）只证明「你是谁」，不携带权限：每次请求重新判定，撤销角色后下一次请求立即生效。
- 每次请求都查账号当前状态：停用、删除、到期在下一次请求生效。
- `POST /auth/logout` 把令牌编号登记到 Redis，原有效期内一直被拒绝。
- `POST /auth/password` 成功后，此前签发的全部令牌作废（令牌里的版本号不再匹配）。

### 3.2 鉴权与权限解释

```http
POST /api/v1/authz/check
Authorization: Bearer <调用者的令牌>
{ "userId": 105, "permission": "oa:m01:f02:approve", "targetDeptId": 27 }
```

| 字段 | 说明 |
|---|---|
| `userId` / `username` | 被判定的用户；都不填表示调用者本人 |
| `permission` | 权限码；也可以用 `resource` + `action` 两个字段给出 |
| `targetDeptId` | 被操作的数据所属部门；不填表示不涉及具体部门（只问「能否执行该操作」） |

```json
{ "code": 0, "data": {
    "allowed": false, "reason": "OUT_OF_DATA_SCOPE",
    "userId": 105, "username": "SG000123", "permissionCode": "oa:m01:f02:approve", "targetDeptId": 27,
    "matched": null, "selfOnly": false, "cacheHit": true, "latencyUs": 412, "trace": null } }
```

| `reason` | 含义 |
|---|---|
| `ROLE_GRANTED` | 允许；`matched` 给出所依据的指派（角色 + 适用部门） |
| `SUPER_ADMIN_BYPASS` | 允许；超级管理员旁路 |
| `SUBJECT_NOT_FOUND` / `SUBJECT_DISABLED` / `SUBJECT_DELETED` / `SUBJECT_EXPIRED` | 拒绝；主体无效 |
| `MISSING_PERMISSION` | 拒绝；没有任何启用中的角色包含该权限 |
| `OUT_OF_DATA_SCOPE` | 拒绝；有该权限，但适用部门都覆盖不到目标部门 |
| `TARGET_DEPT_NOT_FOUND` | 拒绝；目标部门不存在 |

`selfOnly = true` 表示依据的授权只限本人记录（权限清单的「查看本人」等），由业务系统按记录归属过滤。

**谁能查谁**：查自己不需要额外权限；查别人（业务系统代用户鉴权、管理员查看他人），调用者必须在被查用户所在部门持有 `rbac:authz:invoke`（解释接口为 `rbac:authz:explain`），否则 403。业务系统账号也有所属部门，所以查不到别家公司的人。

`POST /authz/explain` 与 check 调用**同一个判定过程**，只多返回 `trace`：

```json
"trace": [
  "用户 SG000123（示例员工）账号有效",
  "角色 公文审批 具有该权限，但只适用于「道路工程分公司」及其下级，目标部门不在其中"
]
```

### 3.3 分配与回收角色

```http
POST /api/v1/users/105/roles
{ "roleId": 7, "scopeDepartmentId": 15, "reason": "兼任道路分公司财务审核" }
```

`scopeDepartmentId` 是适用部门（含下级），默认用户所属部门。依次校验：

1. 用户存在且未删除，角色存在且启用；
2. 适用部门与用户同公司；公司内角色只能指派到本公司；
3. 用户所在部门、适用部门都在操作者的管理范围内（操作者在这两个部门都持有 `rbac:assignment:assign`），否则 40302；
4. 超级管理员角色只有超级管理员能分配；
5. **不能授出自己没有的权限**：角色的每个权限，操作者在适用部门都必须持有，否则 40303；
6. 相同（用户、角色、适用部门）已存在 → 40901。

`DELETE /users/{id}/roles/{assignmentId}` 只删这一条指派。被回收者过去授给别人的指派不受影响（`granted_by` 只是审计属性）。不能回收最后一个有效的超级管理员。

每次分配、回收、以及被拒绝的尝试都写审计（`ROLE_ASSIGN` / `ROLE_REVOKE`，结果 `SUCCESS` / `DENIED`，原因码）。

### 3.4 批量授权

```http
POST /api/v1/assignments/batch
{ "targetType": "DEPARTMENT", "targetIds": [12, 18], "includeDescendants": true,
  "roleId": 5, "scopeDepartmentId": 12, "reason": "项目授权调整", "mode": "ATOMIC" }
```

| 字段 | 说明 |
|---|---|
| `targetType` | `COMPANY`（整家公司）/ `DEPARTMENT`（部门，默认含下级）/ `USER`（指定员工） |
| `scopeDepartmentId` | 为空表示每个人各自的所属部门 |
| `mode` | `ATOMIC`（默认）：先逐人完整校验，有任何一人不通过就整批不写；`PARTIAL`：写入通过的，失败逐人给出原因 |

```json
{ "data": { "mode": "ATOMIC", "committed": false, "requested": 1000, "succeeded": 0,
            "duplicated": 12, "failed": 3,
            "failures": [ { "userId": 3301, "reason": "OUT_OF_MANAGE_SCOPE" },
                          { "userId": 999999, "reason": "NOT_FOUND" } ] } }
```

默认 ATOMIC 是为了避免管理员误以为「全体已授权」。已持有相同指派的人计入 `duplicated`，不算失败。逐人校验规则与单个分配相同（同一份代码）。写入用 `INSERT … SELECT` 分批执行，一次批量一条审计记录。

`POST /assignments/batch-revoke` 规则相同；`scopeDepartmentId` 为空时回收这些人持有的该角色的全部指派。

### 3.5 导入

```http
POST /api/v1/imports/permissions                  （multipart：file）
POST /api/v1/imports/organization?companyCode=SZ&companyName=市政公司   （multipart：departments、employees）
```

返回导入报告 `{counts, notes, anomalies}`。导入不分配任何角色；无法唯一确定的行进 `anomalies`、不写入；可重复执行。重复导入功能权限清单时，角色模板在业务系统上的授权与最新清单对齐：新增的写入、「只限本人」变了的更新（`rolePermissionsSelfOnlyChanged`）、清单里去掉的回收（`rolePermissionsRevoked`）。规则与真实数据的导入结果见 [03 §5](03-database.md)。

### 3.6 菜单、Dashboard、热力图

- `GET /menus/me`：叶子菜单按「菜单资源:view」过滤，没有可见子菜单的目录不返回。隐藏菜单只是界面行为，对应接口仍各自鉴权（T18）。
- `GET /dashboard`：十一项指标，统计口径见 [05 §3.1](05-ui.md)，全部来自真实审计事件。
- `GET /permissions/heatmap?from&to`：横轴操作、纵轴业务系统，单元格为时间段内成功鉴权的次数，默认最近 7 天。

## 4. 外部模拟业务系统

`rbac-mock-biz`（:8090）从权限清单取两个功能点，验证权限在业务系统里真实生效。每个接口把用户的令牌原样带去 `/authz/check`（查自己），允许才执行。

| 接口 | 权限 | 说明 |
|---|---|---|
| `GET /oa/documents?deptId=` | `oa:m01:f02:view` | 发文列表 |
| `POST /oa/documents` | `oa:m01:f02:create` | 拟稿 |
| `POST /oa/documents/{id}/approve` | `oa:m01:f02:approve` | 审批：普通员工没有，部门经理有 |
| `POST /hr/attendance`、`PUT /hr/attendance/{id}` | `hr:m04:f01:edit` | 普通员工为「编辑本人」：`selfOnly = true` 时只能改自己的 |
| `GET /hr/attendance?deptId=` | `hr:m04:f01:view` | `selfOnly = true` 时只返回自己的 |

资源编码由导入生成：`oa:m01:f02` 是 OA › 公文管理 › 发文拟稿与审核，`hr:m04:f01` 是人力资源 › 考勤与休假管理 › 考勤管理。

## 5. 本文档对应的答辩设计点

| 设计点 | 需求与问题 | 方法与理由 | 效果 |
|---|---|---|---|
| 契约驱动的统一鉴权 | 「所有接口都受权限控制」；在每个 Controller 里手写判断容易遗漏 | 权限只写在契约里，拦截器统一执行；启动时双向核对契约与实现 | 45 个接口，0 个未声明；`/not/in/contract` → 403 |
| 「不允许」不是错误 | 业务系统需要区分「用户没权限」与「调用者配置错误」 | 判定拒绝 = 200 + `allowed=false`；调用者无权 = 403 | T04、T20b |
| 批量授权的语义 | 1,000 人里 3 人不合规，整批失败还是部分成功？ | 默认 ATOMIC，显式 PARTIAL；两种都逐人给原因 | T16 |
