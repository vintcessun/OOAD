# API 设计说明书

**版本 0.1 · 2026 年 9 月 20 日**

> 本文档是**唯一的接口契约**。`rbac-structured` 与 `rbac-oo` 两个模块必须实现完全相同的契约——这是它们可以共用一套前端、一套集成测试的前提（见 `02-architecture.md` §3.2）。
>
> 📄 机器可读版本已落地：[`rbac-contract/src/main/resources/openapi/rbac-api.yaml`](../rbac-contract/src/main/resources/openapi/rbac-api.yaml)（OpenAPI 3.0.3，59 个操作 / 40 个 schema / 43 个错误码）。
>
> **本文档与该文件不一致时，以 OpenAPI 文件为准。** 该文件中每个操作都带 `x-iteration` 标注所属迭代、`x-required-permission` 标注所需权限，可直接用于生成客户端与契约测试。

---

## 1. 通用约定

### 1.1 基础信息

| 项 | 值 |
|---|---|
| 基础路径 | `/api/v1` |
| 协议 | HTTPS |
| 编码 | UTF-8 |
| 内容类型 | `application/json` |
| 时间格式 | ISO-8601，`2026-09-20T12:00:00+08:00` |
| 结构化实现 | `http://host:8081/api/v1` |
| 面向对象实现 | `http://host:8082/api/v1` |

### 1.2 统一响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": { },
  "traceId": "7f3a9c2e1b84",
  "timestamp": "2026-09-20T12:00:00+08:00"
}
```

| 字段 | 说明 |
|---|---|
| `code` | 0 表示成功，非 0 表示业务错误码，见 §1.4 |
| `message` | 面向用户的可读信息 |
| `data` | 业务数据；无返回值时为 `null` |
| `traceId` | 链路追踪 ID，排查问题时用；日志与审计中同样记录 |

分页响应的 `data` 统一为：

```json
{
  "list": [],
  "total": 128,
  "pageNum": 1,
  "pageSize": 20
}
```

### 1.3 HTTP 状态码使用

| 状态码 | 使用场景 |
|---|---|
| 200 | 业务成功，或业务失败但请求本身合法（此时 `code` 非 0） |
| 400 | 请求参数格式错误、校验不通过 |
| 401 | 未认证：令牌缺失、无效或过期 |
| 403 | **已认证但无权限** |
| 404 | 资源不存在 |
| 409 | 冲突：唯一键重复、**约束违反**、状态冲突 |
| 429 | 触发限流 |
| 503 | 依赖不可用（数据库故障等） |

> **401 与 403 必须严格区分**：401 表示「你是谁我不知道」，403 表示「我知道你是谁，但你不能做这件事」。前端据此决定是跳转登录页还是提示无权限。
>
> **约束违反用 409 而非 400**：约束违反不是参数格式问题，而是当前系统状态与请求的冲突，语义上属于 Conflict。前端需要据此展示冲突详情而非字段校验错误。

### 1.4 错误码

错误码按子系统分段，便于定位：

| 区段 | 子系统 | 示例 |
|---|---|---|
| 0 | 成功 | `0 SUCCESS` |
| 10000–10999 | 通用 | `10001 PARAM_INVALID`、`10002 UNAUTHORIZED`、`10003 FORBIDDEN`、`10004 NOT_FOUND`、`10005 RATE_LIMITED`、`10006 SYSTEM_UNAVAILABLE` |
| 20000–20999 | 身份 IDM | `20001 USER_NOT_FOUND`、`20002 CREDENTIAL_INVALID`、`20003 USER_DISABLED`、`20004 USER_LOCKED`、`20005 USERNAME_DUPLICATED` |
| 21000–21999 | 角色 ROLE | `21001 ROLE_NOT_FOUND`、`21002 ROLE_CODE_DUPLICATED`、`21003 ROLE_IN_USE`、`21004 ROLE_DISABLED`、`21005 ROLE_BUILTIN_IMMUTABLE` |
| 22000–22999 | 权限 PERM | `22001 PERMISSION_NOT_FOUND`、`22002 PERMISSION_CODE_DUPLICATED`、`22003 PERMISSION_IN_USE` |
| 23000–23999 | 继承 HIER | `23001 HIERARCHY_CYCLE_DETECTED`、`23002 HIERARCHY_SELF_LOOP`、`23003 HIERARCHY_DEPTH_EXCEEDED`、`23004 HIERARCHY_DUPLICATED` |
| 24000–24999 | 约束 CSTR | `24001 SSD_VIOLATION`、`24002 CARDINALITY_VIOLATION`、`24003 PREREQUISITE_VIOLATION`、`24004 DSD_VIOLATION`、`24005 CONSTRAINT_INEFFECTIVE` |
| 25000–25999 | 授权 AUTHZ | `25001 MISSING_PERMISSION`、`25002 SUBJECT_DISABLED`、`25003 BATCH_SIZE_EXCEEDED`、`25004 OUT_OF_DATA_SCOPE` |
| 26000–26999 | 业务 BIZ | `26001 DOC_NOT_FOUND`、`26002 DOC_STATE_INVALID`、`26003 SELF_APPROVAL_FORBIDDEN`、`26004 ATTENDANCE_NOT_FOUND` |
| 27000–27999 | 组织 ORG | `27001 DEPT_NOT_FOUND`、`27002 DEPT_HAS_CHILDREN`、`27003 DEPT_HAS_USERS`、`27004 DEPT_CYCLE_DETECTED`、`27005 MANAGER_NOT_IN_DEPT`、`27006 IMPORT_VALIDATION_FAILED` |

### 1.5 认证

除登录接口外，所有接口需在请求头携带：

```
Authorization: Bearer <JWT>
```

令牌有效期 2 小时。JWT 载荷**只含身份标识与所属部门，不含权限列表**：

```json
{ "sub": "1013", "username": "SG000013", "deptId": 11, "sid": "sess_xxx", "exp": 1758345600 }
```

> **为什么权限不放进 JWT**：JWT 一旦签发就无法撤销，若把权限写入其中，管理员撤销权限后用户在令牌有效期内仍持有旧权限——这与 SRS A-12「权限变更立即生效」直接冲突。因此权限每次在服务端实时查询（走缓存，O(1)）。这是一个**用极小的性能代价换取安全正确性**的取舍，需要在答辩时讲清楚。

### 1.6 幂等性

`PUT` 与角色/权限指派类 `POST` 接口均为幂等：重复提交相同内容返回成功，不产生重复数据、不重复记审计。

---

## 2. 授权服务 AUTHZ

> 这是系统的核心接口组。其余所有接口的调用量总和，可能不及本组的万分之一。

### 2.1 单次权限校验

```
POST /api/v1/authz/check
```

**请求**

```json
{
  "subject": "SG000013",
  "resource": "oa:doc:draft",
  "action": "approve",
  "context": {
    "targetDeptId": 15,
    "targetUserId": null,
    "targetResourceId": "doc_10086"
  }
}
```

**`context` 字段从迭代一起就存在于协议中，但迭代一忽略其内容。**

| 迭代 | `context` 的作用 |
|---|---|
| 一 | 忽略。判定只看功能权限 |
| 二 | `targetDeptId` / `targetUserId` 用于**数据范围过滤**（管道阶段 S5b） |
| 三 | 可扩展承载时间、IP 等环境属性，供约束阶段使用 |

> **为什么迭代一就要留这个字段。** 权限清单里有「查看本人」，这意味着判定迟早需要知道「目标数据是谁的」。如果迭代一把接口定成 `{subject, resource, action}`，迭代二加 `context` 时，所有已接入的业务系统都要改调用方、所有测试用例都要改、OpenAPI 契约要升版本。现在留一个恒被忽略的可选字段，成本为零。
>
> 这是本项目对「面向未来变化设计」的一个具体落点——不是提前实现功能，而是**提前把扩展点开在协议上**。

**响应（允许）**

```json
{
  "code": 0,
  "data": { "allowed": true, "cached": true, "elapsedMicros": 83 }
}
```

**响应（拒绝：无功能权限）**

```json
{
  "code": 0,
  "data": {
    "allowed": false,
    "reason": "MISSING_PERMISSION",
    "requiredPermission": "oa:doc:draft:approve"
  }
}
```

**响应（拒绝：功能权限具备但数据范围不允许，迭代二起）**

```json
{
  "code": 0,
  "data": {
    "allowed": false,
    "reason": "OUT_OF_DATA_SCOPE",
    "requiredPermission": "oa:doc:draft:approve",
    "grantedScope": "DEPT",
    "subjectDeptId": 11,
    "targetDeptId": 15
  }
}
```

> 把「无权限」与「超出数据范围」区分为两个 reason，而不是都返回 `MISSING_PERMISSION`。理由：两者的处理方式完全不同——前者要去申请权限，后者是访问了不该访问的数据，可能需要告警。混为一谈会让排查和风控都失去依据。

> 注意：**判定为拒绝时 `code` 仍是 0**。「无权限」是本接口的正常业务结果而非错误——调用方问「他能不能做」，系统答「不能」，这是一次成功的调用。接口层面的 403 只用于「调用方自己无权调用本接口」。这一区分若做错，业务系统会把正常的拒绝判定当成故障告警。

| 对应需求 | RBAC-REQ-AUTHZ-001 |
|---|---|
| 所需权限 | `authz:check:invoke` |
| 性能目标 | P95 < 20ms，P99 < 50ms |
| 限流 | 单调用方 20000 QPS |

### 2.2 批量权限校验

```
POST /api/v1/authz/batch-check
```

```json
{
  "subject": "SG000013",
  "context": { "targetDeptId": 11 },
  "requests": [
    { "resource": "oa:doc:draft",        "action": "view" },
    { "resource": "oa:doc:draft",        "action": "create" },
    { "resource": "oa:doc:draft",        "action": "approve" },
    { "resource": "hr:attendance:record","action": "edit" }
  ]
}
```

```json
{
  "code": 0,
  "data": {
    "results": [
      { "resource": "oa:doc:draft",         "action": "view",    "allowed": true },
      { "resource": "oa:doc:draft",         "action": "create",  "allowed": true },
      { "resource": "oa:doc:draft",         "action": "approve", "allowed": false, "reason": "MISSING_PERMISSION" },
      { "resource": "hr:attendance:record", "action": "edit",    "allowed": true, "scope": "SELF" }
    ],
    "elapsedMicros": 96
  }
}
```

> 批量接口的价值不在于省网络往返，而在于**只加载一次权限集合**：N 次单独调用要查 N 次缓存，一次批量调用查一次缓存后在内存中做 N 次集合查找。单次请求上限 100 条，超出返回 `25003 BATCH_SIZE_EXCEEDED`。
>
> 批量判定也是**前端页面加载时的主力接口**：一个页面往往有十几个按钮各需一条权限，前端用一次批量请求拿到全部结果，而不是发十几个请求。

| 对应需求 | RBAC-REQ-AUTHZ-002 · 迭代二 |
|---|---|

### 2.3 权限解释

```
POST /api/v1/authz/explain
```

请求体同 §2.1。响应见 SRS §4.2.4 的示例。

| 对应需求 | RBAC-REQ-AUTHZ-003 · 迭代二 |
|---|---|
| 所需权限 | `authz:explain:invoke`（独立于 `authz:check:invoke`） |
| 限流 | 单调用方 100 QPS。解释模式不走缓存快路径，开销约为普通判定的 20 倍 |

### 2.4 缓存管理

```
POST   /api/v1/authz/cache/invalidate    { "scope": "USER|ROLE|ALL", "targetId": 1001 }
GET    /api/v1/authz/cache/stats
```

`stats` 返回命中率、条目数、平均重建耗时，是监控面板的数据源。

| 对应需求 | RBAC-REQ-AUTHZ-004 · 迭代二 |
|---|---|
| 所需权限 | `system:perm:grant`（缓存操作视为高危运维操作） |

---

## 3. 身份管理 IDM

| 方法 | 路径 | 说明 | 所需权限 | 需求 |
|---|---|---|---|---|
| POST | `/auth/login` | 登录，返回令牌 + 有效权限 + 菜单树 | 无 | IDM-001 |
| POST | `/auth/logout` | 登出，销毁会话 | 已登录 | IDM-002 |
| GET | `/auth/me` | 当前用户信息与有效权限 | 已登录 | IDM-001 |
| POST | `/auth/change-password` | 修改本人口令 | 已登录 | — |
| GET | `/users` | 用户列表（分页/过滤，支持 `departmentId`、`includeSubDept`、`position`、`roleId`、`keyword`） | `system:user:list` | IDM-007 |
| POST | `/users` | 新增用户 | `system:user:create` | IDM-003 |
| GET | `/users/{id}` | 用户详情 | `system:user:list` | IDM-007 |
| PUT | `/users/{id}` | 修改用户信息 | `system:user:update` | IDM-004 |
| PATCH | `/users/{id}/status` | 启用/禁用 | `system:user:update` | IDM-005 |
| DELETE | `/users/{id}` | 逻辑删除 | `system:user:delete` | IDM-006 |
| GET | `/users/{id}/roles` | 用户的角色列表 | `system:user:list` | ROLE-005 |
| GET | `/users/{id}/permissions` | **有效权限及来源** | `system:user:list` | IDM-008 |

### 3.1 登录

```
POST /api/v1/auth/login
```

```json
{ "username": "SG000013", "password": "P@ssw0rd" }
```

```json
{
  "code": 0,
  "data": {
    "token": "eyJhbGciOi...",
    "expiresIn": 7200,
    "user": {
      "id": 1013, "username": "SG000013", "realName": "许禄",
      "departmentId": 11, "departmentName": "综合办公室",
      "departmentPath": "总公司 / 综合办公室",
      "position": "干事", "mustChangePwd": false
    },
    "roles": [ { "roleCode": "EMPLOYEE", "roleName": "普通员工" } ],
    "permissions": ["oa:doc:draft:view", "oa:doc:draft:create", "hr:attendance:record:view", "hr:attendance:record:edit"],
    "dataScopes": { "hr:attendance:record:view": "SELF", "hr:attendance:record:edit": "SELF" },
    "menus": [ { "id": 5, "name": "公文管理", "path": "/oa/doc", "children": [] } ]
  }
}
```

> 登录响应中的 `dataScopes` 只列出**非 ALL** 的条目。全部列出会让一万名普通员工每次登录都下发几十条恒为 `ALL` 的冗余数据。

失败一律返回 `20002 CREDENTIAL_INVALID`，**不区分用户不存在与口令错误**（SRS S-3）。账号锁定返回 `20004 USER_LOCKED` 并附解锁时间。

### 3.2 查看用户有效权限及来源

```
GET /api/v1/users/{id}/permissions
```

```json
{
  "code": 0,
  "data": {
    "user": { "id": 1011, "username": "SG000011", "realName": "袁国智",
              "departmentId": 11, "departmentName": "综合办公室", "position": "主任" },
    "directRoles":    [ { "id": 3, "roleCode": "DEPT_MANAGER", "roleName": "部门经理" } ],
    "inheritedRoles": [ { "id": 5, "roleCode": "EMPLOYEE", "roleName": "普通员工", "via": ["DEPT_MANAGER"] } ],
    "managedDepartments": [ { "id": 11, "deptName": "综合办公室" } ],
    "permissions": [
      { "code": "oa:doc:draft:approve",      "source": "DIRECT",    "fromRole": "DEPT_MANAGER", "dataScope": "DEPT" },
      { "code": "oa:doc:draft:create",       "source": "INHERITED", "fromRole": "EMPLOYEE",
        "path": ["DEPT_MANAGER", "EMPLOYEE"], "dataScope": "ALL" },
      { "code": "hr:attendance:record:edit", "source": "DIRECT",    "fromRole": "DEPT_MANAGER", "dataScope": "DEPT" }
    ],
    "permissionCount": 412,
    "riskScore": 34
  }
}
```

> `managedDepartments` 来自 `sys_department.manager_user_id` 的反查，是 `DEPT` 数据范围的实际作用域。**没有这个字段，「部门经理」的数据范围就是一句无法验证的话**——管理员看不出这个人到底管的是哪几个部门。

> **`source` 与 `path` 两个字段是这个接口的价值所在。** 普通系统只回答「他有哪些权限」，本系统回答「他为什么有这些权限」。在角色继承引入后，管理员面对一个拥有 80 条权限的用户时，唯一能理解这些权限来源的途径就是这张溯源表。

---

## 4. 角色与权限管理 ROLE / PERM

| 方法 | 路径 | 说明 | 所需权限 | 需求 |
|---|---|---|---|---|
| GET | `/roles` | 角色列表（含用户数/权限数/父角色数） | `system:role:list` | ROLE-004 |
| POST | `/roles` | 创建角色 | `system:role:create` | ROLE-001 |
| GET | `/roles/{id}` | 角色详情 | `system:role:list` | ROLE-004 |
| PUT | `/roles/{id}` | 修改角色 | `system:role:update` | ROLE-002 |
| PATCH | `/roles/{id}/status` | 启用/禁用角色 | `system:role:update` | ROLE-002 |
| DELETE | `/roles/{id}` | 删除角色 | `system:role:delete` | ROLE-003 |
| POST | `/users/{userId}/roles` | **为用户分配角色** | `system:role:assign` | ROLE-005 |
| DELETE | `/users/{userId}/roles/{roleId}` | 撤销用户角色 | `system:role:revoke` | ROLE-006 |
| GET | `/roles/{id}/permissions` | 角色权限（区分直接/继承） | `system:role:list` | HIER-004 |
| POST | `/roles/{id}/permissions` | **为角色授予权限** | `system:perm:grant` | PERM-003 |
| DELETE | `/roles/{id}/permissions/{permId}` | 撤销角色权限 | `system:perm:revoke` | PERM-004 |
| GET | `/permissions` | 权限列表（树形/扁平） | `system:perm:list` | PERM-005 |
| POST | `/permissions` | 新增权限 | `system:perm:grant` | PERM-002 |
| DELETE | `/permissions/{id}` | 删除权限 | `system:perm:revoke` | PERM-002 |
| GET | `/resources` | 资源列表 | `system:perm:list` | PERM-001 |
| POST | `/resources` | 新增资源 | `system:perm:grant` | PERM-001 |

### 4.1 为用户分配角色

```
POST /api/v1/users/{userId}/roles
```

```json
{ "roleIds": [3, 7], "reason": "转岗至财务部" }
```

**成功**

```json
{
  "code": 0,
  "data": {
    "assigned": [3, 7],
    "skipped": [],
    "permissionDiff": {
      "added":   ["oa:doc:draft:approve", "hr:attendance:record:edit"],
      "removed": []
    },
    "affectedCacheKeys": 1
  }
}
```

**约束违反（HTTP 409）**

```json
{
  "code": 24001,
  "message": "违反互斥角色约束「审计独立性」",
  "data": {
    "constraintName": "审计独立性",
    "constraintType": "SSD_MUTEX",
    "conflictingRoles": [
      { "id": 1, "roleCode": "SYS_ADMIN", "roleName": "系统管理员", "status": "拟分配" },
      { "id": 7, "roleCode": "AUDITOR",   "roleName": "审计员",     "status": "已拥有" }
    ],
    "maxAllowed": 1,
    "suggestion": "请先撤销「审计员」角色。审计员的职责是监督系统管理员，两者合一将使审计失去独立性"
  }
}
```

> 错误响应携带**结构化的冲突详情**而不只是一句文案。原因：前端需要把冲突角色高亮出来并提供「撤销冲突角色」的快捷操作；若只返回文案，前端只能弹一个无法操作的提示框。这是「可解释性」原则在错误处理上的延伸。

### 4.2 删除角色的引用检查

```
DELETE /api/v1/roles/{id}
```

角色被引用时返回 409：

```json
{
  "code": 21003,
  "message": "角色正在被使用，无法删除",
  "data": {
    "userCount": 12,
    "childRoleCount": 2,
    "constraintCount": 1,
    "hint": "请先撤销 12 个用户的该角色、解除 2 个子角色的继承关系、移除 1 条约束引用"
  }
}
```

---

## 4A. 组织架构 ORG

| 方法 | 路径 | 说明 | 所需权限 | 需求 |
|---|---|---|---|---|
| GET | `/departments/tree` | 部门树（111 节点，三层） | `system:org:view` | ORG-001 |
| GET | `/departments/{id}` | 部门详情（人数、经理、上下级） | `system:org:view` | ORG-001 |
| POST | `/departments` | 新增部门 | `system:org:manage` | ORG-002 |
| PUT | `/departments/{id}` | 修改部门（含调整上级） | `system:org:manage` | ORG-002 |
| DELETE | `/departments/{id}` | 删除部门 | `system:org:manage` | ORG-002 |
| PUT | `/departments/{id}/manager` | 设置部门经理 | `system:org:manage` | ORG-003 |
| GET | `/departments/{id}/users` | 部门下员工（`includeSub` 控制是否含下属） | `system:user:list` | ORG-004 |
| GET | `/departments/{id}/descendants` | 全部下属部门 ID（数据范围用） | `system:org:view` | ORG-004 |
| POST | `/org/import` | 从 xlsx 导入组织与员工 | `system:org:manage` | ORG-005 |
| GET | `/org/import/{taskId}/report` | 导入异常报告 | `system:org:manage` | ORG-005 |

### 4A.1 部门树

```
GET /api/v1/departments/tree
```

```json
{
  "code": 0,
  "data": {
    "root": {
      "id": 1, "deptName": "总公司", "level": 1, "path": "/1/",
      "manager": { "userId": 1001, "realName": "薛峰" },
      "userCount": 10000, "directUserCount": 0,
      "children": [
        { "id": 11, "deptName": "综合办公室", "level": 2, "path": "/1/11/",
          "manager": { "userId": 1011, "realName": "袁国智" },
          "userCount": 18, "children": [] },
        { "id": 22, "deptName": "道路工程分公司", "level": 2, "path": "/1/22/",
          "manager": { "userId": 1234, "realName": "曾禄" },
          "userCount": 1120,
          "children": [
            { "id": 23, "deptName": "道路工程分公司-综合办公室", "level": 3, "path": "/1/22/23/",
              "userCount": 12, "children": [] }
          ] }
      ]
    },
    "totalDepartments": 111,
    "maxLevel": 3
  }
}
```

> `userCount` 是**含下属部门**的累计人数，`directUserCount` 是直属人数。两个数字都要给：前者用于展示组织规模，后者用于判断一个部门是否可以删除。

### 4A.2 修改部门上级

```
PUT /api/v1/departments/{id}
```

移动部门会改变整棵子树的 `path` 与 `level`，因此：

- 校验不成环（不能把部门移到自己的子孙下），违反返回 `27004 DEPT_CYCLE_DETECTED`；
- 校验移动后层数不超过 3；
- 同一事务内批量更新子树的 `path` 与 `level`；
- **失效全部数据范围相关缓存**——部门移动会改变很多人的可见范围。

> 环检测在这里**复用角色继承的同一个算法**（见 `02-architecture.md` §4.3.1），只是邻接关系换成了部门的父子关系。一处实现、两处使用。

### 4A.3 数据导入

```
POST /api/v1/org/import        multipart: departments.xlsx, employees.xlsx, permissions.xlsx
```

异步任务，返回 `taskId`。导入报告：

```json
{
  "code": 0,
  "data": {
    "status": "COMPLETED_WITH_WARNINGS",
    "counts": {
      "departments": 111, "users": 10000, "softwareSystems": 18,
      "functionModules": 115, "resources": 359, "permissions": 1795,
      "roles": 7, "rolePermissions": 1803, "userRoles": 10000
    },
    "warnings": [
      { "code": "CELL_VALUE_NORMALIZED", "location": "权限清单!行117",
        "detail": "「项目经理」列取值 'view/新增' 中英文混用，已归一化为「查看/新增」" },
      { "code": "CELL_VALUE_NORMALIZED", "location": "权限清单!行310",
        "detail": "「部门经理」列取值 'view/编辑/审批'，已归一化" },
      { "code": "CELL_EMPTY", "location": "权限清单!行340",
        "detail": "「普通员工」列为空，已按「无」处理" },
      { "code": "MANAGER_MULTI_DEPT", "location": "部门信息!薛峰",
        "detail": "同时担任「总公司」与「公司领导」两个部门的经理" },
      { "code": "MANAGER_NAME_AMBIGUOUS", "location": "部门信息!钟晓",
        "detail": "员工表中存在 2 名同名员工，已按部门就近匹配，请人工确认" }
    ],
    "errors": []
  }
}
```

> **这份报告是第一次检查的展示材料之一。** 它证明我们逐条校验了老师给的数据，而不是草草塞进数据库——每一条 warning 都对应 SRS 附录 C 的一个数据疑点。

---

## 5. 角色继承 HIER（迭代二）

| 方法 | 路径 | 说明 | 所需权限 | 需求 |
|---|---|---|---|---|
| GET | `/hierarchy` | 全量继承关系（DAG） | `system:hier:view` | HIER-003 |
| POST | `/hierarchy` | 建立继承关系 | `system:hier:manage` | HIER-001 |
| DELETE | `/hierarchy` | 解除继承关系 | `system:hier:manage` | HIER-002 |
| GET | `/roles/{id}/ancestors` | 祖先角色（权限来源） | `system:hier:view` | HIER-004 |
| GET | `/roles/{id}/descendants` | 后代角色（影响范围） | `system:hier:view` | HIER-004 |

### 5.1 建立继承关系

```
POST /api/v1/hierarchy
```

```json
{ "childRoleId": 3, "parentRoleId": 5 }
```

> 语义：`child` 继承 `parent`，`child` 获得 `parent` 的全部权限。例：部门经理(child) 继承 普通员工(parent)。

**成功**

```json
{
  "code": 0,
  "data": {
    "affectedRoles": 4,
    "affectedUsers": 128,
    "newPermissions": ["oa:doc:draft:create", "oa:doc:draft:view"],
    "newDepth": 3
  }
}
```

**检测到环（HTTP 409）**

```json
{
  "code": 23001,
  "message": "该继承关系将形成环",
  "data": {
    "cyclePath": [
      { "id": 3, "roleCode": "DEPT_MANAGER" },
      { "id": 5, "roleCode": "EMPLOYEE" },
      { "id": 4, "roleCode": "PROJECT_MANAGER" },
      { "id": 3, "roleCode": "DEPT_MANAGER" }
    ]
  }
}
```

> **返回完整环路径而不只是「存在环」**。角色数量上百时，管理员无法自行找出环在哪里；返回路径使错误可直接定位。这个字段的实现要求环检测算法保留遍历栈而不只返回布尔值——一个在设计阶段就要决定、事后补很麻烦的细节。

### 5.2 继承关系图

```
GET /api/v1/hierarchy
```

```json
{
  "code": 0,
  "data": {
    "nodes": [
      { "id": 1, "roleCode": "SYS_ADMIN", "roleName": "系统管理员", "userCount": 2, "directPermCount": 1795 },
      { "id": 5, "roleCode": "EMPLOYEE",    "roleName": "普通员工",   "userCount": 9312, "directPermCount": 96 }
    ],
    "edges": [ { "childRoleId": 3, "parentRoleId": 5 } ],
    "maxDepth": 4,
    "hasCycle": false
  }
}
```

---

## 6. 约束管理 CSTR（迭代三）

| 方法 | 路径 | 说明 | 所需权限 | 需求 |
|---|---|---|---|---|
| GET | `/constraints` | 约束列表 | `system:constraint:manage` | CSTR-001~003 |
| POST | `/constraints` | 创建约束 | `system:constraint:manage` | CSTR-001~003 |
| PUT | `/constraints/{id}` | 修改约束 | `system:constraint:manage` | — |
| PATCH | `/constraints/{id}/status` | 启用/停用 | `system:constraint:manage` | — |
| DELETE | `/constraints/{id}` | 删除约束 | `system:constraint:manage` | — |
| POST | `/constraints/scan` | **存量冲突扫描** | `system:constraint:manage` | CSTR-004 |
| POST | `/constraints/what-if` | **What-if 影响分析** | `system:role:assign` | CSTR-005 |

### 6.1 创建约束

```
POST /api/v1/constraints
```

互斥角色：

```json
{
  "constraintName": "审计独立性",
  "constraintType": "SSD_MUTEX",
  "roleIds": [1, 7],
  "threshold": 1,
  "description": "审计员与系统管理员不得为同一人，否则审计失去独立性"
}
```

基数约束（三类）：

```json
{ "constraintName": "运维账号上限", "constraintType": "CARD_ROLE_USER_MAX", "targetRoleId": 1, "threshold": 3 }
{ "constraintName": "单用户角色上限", "constraintType": "CARD_USER_ROLE_MAX", "threshold": 5 }
{ "constraintName": "普通员工权限上限", "constraintType": "CARD_ROLE_PERM_MAX", "targetRoleId": 5, "threshold": 120 }
```

先决条件角色：

```json
{ "constraintName": "部门经理须先为员工", "constraintType": "PREREQUISITE", "targetRoleId": 3, "roleIds": [5] }
```

**响应包含存量冲突报告：**

```json
{
  "code": 0,
  "data": {
    "constraintId": 12,
    "legacyConflicts": {
      "count": 3,
      "samples": [ { "userId": 1011, "username": "SG000011", "realName": "袁国智", "conflictingRoles": ["AUDITOR", "SYS_ADMIN"] } ],
      "exportUrl": "/api/v1/constraints/12/conflicts/export"
    },
    "appliedMode": "LEGACY_EXEMPT"
  }
}
```

### 6.2 What-if 影响分析

```
POST /api/v1/constraints/what-if
```

```json
{ "userId": 1001, "addRoleIds": [3], "removeRoleIds": [] }
```

```json
{
  "code": 0,
  "data": {
    "wouldSucceed": false,
    "permissionDiff": {
      "added":   ["oa:doc:draft:approve", "fin:payment:order:edit"],
      "removed": []
    },
    "violations": [
      { "constraintName": "审计独立性", "constraintType": "SSD_MUTEX", "conflictingRoles": ["AUDITOR", "SYS_ADMIN"] }
    ],
    "riskScoreChange": { "before": 34, "after": 71, "level": "HIGH" }
  }
}
```

> **本接口不写任何数据。** 它完整复用授权内核与约束求值器，只是在内存中构造一个假想的用户快照。这正是 `02-architecture.md` §2.2「内核是无 I/O 纯逻辑」这一设计约束带来的直接收益——如果判定逻辑与数据库访问纠缠在一起，What-if 功能就必须写库再回滚，既慢又危险。答辩时这是「好的设计带来意外能力」的最佳例证。

---

## 7. 其余接口

### 7.1 菜单 MENU（迭代二）

| 方法 | 路径 | 说明 | 需求 |
|---|---|---|---|
| GET | `/menus/mine` | 当前用户可见菜单树 | MENU-001 |
| GET | `/menus` | 全量菜单（管理用） | MENU-002 |
| POST/PUT/DELETE | `/menus[/{id}]` | 菜单维护 | MENU-002 |

> `/menus/mine` **按权限过滤后再下发**，无权限的菜单不出现在响应中，而非下发后由前端隐藏。理由：响应体本身不应泄露系统有哪些功能模块。

### 7.2 审计 AUDIT

| 方法 | 路径 | 说明 | 所需权限 | 需求 |
|---|---|---|---|---|
| GET | `/audit/logs` | 审计日志查询（多维过滤/分页） | `audit:log:list` | AUDIT-002 |
| GET | `/audit/logs/export` | 导出 | `audit:log:list` | AUDIT-002 |

审计日志**只有查询接口，没有任何写入、修改、删除接口**（SRS S-8）。

### 7.3 监控 OBSV（迭代二）

| 方法 | 路径 | 说明 | 需求 |
|---|---|---|---|
| GET | `/observability/dashboard` | 概览指标 | OBSV-001 |
| GET | `/observability/authz-metrics` | 鉴权 QPS / 延迟分布 / 命中率时序 | OBSV-001 |

```json
{
  "code": 0,
  "data": {
    "userCount": 102384, "roleCount": 486, "permissionCount": 1920,
    "todayAuthzCount": 12839201,
    "authzSuccessRate": 0.9932,
    "qps": 8521, "p95Millis": 8.7, "p99Millis": 21.3,
    "cacheHitRate": 0.993,
    "topDenyReasons": [ { "reason": "MISSING_PERMISSION", "count": 78214 } ]
  }
}
```

### 7.4 模拟业务 BIZ

| 方法 | 路径 | 说明 | 所需权限 | 需求 |
|---|---|---|---|---|
| GET | `/oa/documents` | 发文稿件列表 | `oa:doc:draft:view` | BIZ-001 |
| POST | `/oa/documents` | 拟稿 | `oa:doc:draft:create` | BIZ-001 |
| POST | `/oa/documents/{id}/approve` | 审批稿件 | `oa:doc:draft:approve` | BIZ-002 |
| GET | `/hr/attendance` | 考勤记录列表 | `hr:attendance:record:view` | BIZ-003 |
| PUT | `/hr/attendance/{id}` | 修改考勤记录 | `hr:attendance:record:edit` | BIZ-004 |

`/oa/documents/{id}/approve` 在权限校验之外还要检查拟稿人 ≠ 审批人，违反返回 `26003 SELF_APPROVAL_FORBIDDEN`。

**列表接口必须按数据范围过滤（迭代二起）**：

```
GET /api/v1/hr/attendance?month=2026-09
```

同一个请求，不同角色返回不同结果：

| 调用者 | 授予的 data_scope | 返回 |
|---|---|---|
| 许禄（普通员工） | `SELF` | 仅本人当月记录 |
| 袁国智（部门经理，管综合办公室） | `DEPT` | 综合办公室 18 人的记录 |
| 曾禄（道路工程分公司负责人） | `DEPT_AND_SUB` | 该分公司及下属 9 个部门共 1120 人 |
| 薛峰（公司领导） | `ALL` | 全部 10000 人 |

> 这四行是**数据范围功能最直观的验收用例**：同一个 URL、同一份代码，四个人看到四种结果。迭代二的验收演示就用这一张表。
>
> ⚠️ 实现要点：过滤必须发生在 **SQL 的 WHERE 子句**中，不能先查全部再在内存里筛——后者在 1 万条记录时还能跑，在真实数据量下会直接拖垮系统，而且分页总数会算错。

---

## 8. 接口与需求追溯矩阵

| 迭代 | 接口数 | 覆盖需求 |
|---|---|---|
| 一（RBAC0） | 27 | IDM-001~007, ROLE-001~006, PERM-001~005, AUTHZ-001, AUDIT-001, BIZ-001~002 |
| 二（RBAC1） | 14 | IDM-008, HIER-001~004, AUTHZ-002~004, MENU-001~002, AUDIT-002, OBSV-001 |
| 三（RBAC2/3） | 7 | CSTR-001~005, SESS-001 |

每个接口在 `rbac-test-suite` 中必须有至少一条契约测试，并对 `rbac-structured` 与 `rbac-oo` 分别执行。

---

## 9. 本文档对应的答辩设计点

**DP-2 的接口侧论据**

| 项 | 内容 |
|---|---|
| 需求是什么 | 对外提供一个可被任何业务系统调用、万级 QPS、且权限变更立即生效的授权接口 |
| 存在什么问题 | ① 把权限塞进 JWT 性能最好，但无法撤销；② 判定为「拒绝」到底算成功还是失败，直接影响调用方的告警逻辑；③ 约束违反只返回文案，前端无法提供修复操作 |
| 采用什么方法与理由 | ① JWT 只放身份，权限实时查（走缓存，代价约 0.1ms），换取撤销可立即生效；② 拒绝判定返回 `code:0`，403 只用于调用方自身越权；③ 错误响应携带结构化冲突详情，使错误可操作 |
| 实现效果 | 撤销权限后下一次请求即被拒绝；业务系统不会把正常拒绝误报为故障；前端可在约束冲突时一键跳转处理 |
