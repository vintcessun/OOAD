# Apifox 管理员与普通用户测试指南

本文使用项目的 [OpenAPI 契约](../rbac-contract/src/main/resources/openapi/rbac-api.yaml)，验证登录、管理接口权限、查询他人限制，以及授权、回收后的即时生效。请求中的路径均相对于 `/api/v1`，操作前先按 [部署说明](13-deployment.md) 启动权限中心、MySQL、Redis。

以下是可复现的操作步骤和预期结果，不是已经在你的运行环境中执行过的测试报告。请在开发或测试环境创建本文专用账号。

## 1. 先区分三种身份

| 身份 | 用途 | 权限特点 |
|---|---|---|
| 已有超级管理员 `root` | 准备测试账号、角色和授权 | `SUPER_ADMIN` 直接旁路权限判定；`permissions` 数组可以为空 |
| 新建权限管理员 | 本文主要测试的管理员 | 指派内置 `RBAC_ADMIN`，管理范围由适用部门决定 |
| 新建普通用户 | 本文主要测试的普通用户 | `userType=EMPLOYEE`，起初不指派任何角色，再按需授予一个最小权限角色 |

`EMPLOYEE` 用户类型不等于导入清单里的 `EMPLOYEE` 角色。用户刚创建时不会自动获得任何角色。内置 `RBAC_ADMIN` 也不是超级管理员：默认不含 `rbac:org:import`、`rbac:permission:create`，不能修改全局角色。

先用 `root` 准备数据，然后分别使用管理员、普通用户的 Token 测试。仅用 `root` 测所有接口，无法验证普通管理员的权限边界。

## 2. 导入接口与设置环境

1. 在 Apifox 新建或打开 API 项目，通过“导入”选择 OpenAPI/Swagger 文件，选择仓库中的 `rbac-contract/src/main/resources/openapi/rbac-api.yaml`。
2. 预览应有 **45 个接口操作**，包括 `login`、`getCurrentUser`、`assignUserRole` 等 `operationId`，确认说明已更新后导入。已有项目先预览差异，避免覆盖自己保存的调试用例。
3. 新建“RBAC 本地测试”环境，把服务的前置 URL 设置为 `http://localhost:8081/api/v1`；如果后端部署在别处，替换主机与端口。
4. 选择这个实际后端环境，不要选择“本地 Mock”或“云端 Mock”。检查发送的最终 URL，例如当前用户接口应为 `http://localhost:8081/api/v1/auth/me`，不要重复拼接 `/api/v1`。
5. 导入的 `bearerAuth` 对应 Bearer Token。每个测试用例的 Auth 中填写其身份对应的变量；Token 输入框只填 `{{admin_token}}` 等变量，不再加 `Bearer ` 前缀。登录接口使用“无需认证”。

导入及鉴权组件的操作依据 Apifox 官方说明：[手动导入](https://docs.apifox.com/manual-import)、[创建鉴权组件](https://docs.apifox.com/create-security-scheme)。

在环境管理中建立以下变量。账号口令和 Token 只填写**本地值**，远程值留空；本文不提供实际口令。口令需至少 8 位，同时包含大写字母、小写字母、数字和特殊字符。

| 变量 | 填写方式 |
|---|---|
| `root_password` | 已有超级管理员当前口令，本地填写 |
| `root_new_password` | 仅首次登录需要，填写符合策略且不同于旧口令的新口令 |
| `admin_username` / `user_username` | 例如 `apifox_admin_01` / `apifox_user_01`；重跑时使用未占用的新名称 |
| `admin_password` / `user_password` | 为这两个测试账号分别设置的口令，本地填写 |
| `root_token` / `admin_token` / `user_token` | 分别从三次登录响应的 `data.token` 保存 |
| `company_id` / `test_dept_id` | 从公司列表保存测试公司 ID 和该公司的 `rootDepartmentId` |
| `admin_role_id` | 从角色列表中找到 `roleCode=RBAC_ADMIN` 的 `id` |
| `admin_user_id` / `user_id` | 创建两个测试账号后分别保存响应 `data.id` |
| `reader_role_id` / `menu_view_permission_id` | 创建最小权限角色及查找权限后保存 |
| `user_assignment_id` | 给普通用户指派测试角色后保存 `data.assignmentId` |

变量在请求中使用 `{{变量名}}`。本地值和远程值的区别、脚本设置本地值的行为见 [Apifox 变量说明](https://docs.apifox.com/global-environment-session-variables)。

## 3. 用超级管理员准备测试数据

### 3.1 登录并处理首次改密

`POST /auth/login`，无需认证，JSON Body：

```json
{
  "username": "root",
  "password": "{{root_password}}"
}
```

预期 HTTP 200、`code=0`。将 `data.token` 保存到 `root_token`，不要把完整响应或 Token 复制到仓库、共享文档或调试日志。

如果 `data.mustChangePwd=true`，用 Auth=`{{root_token}}` 调用 `POST /auth/password`：

```json
{
  "oldPassword": "{{root_password}}",
  "newPassword": "{{root_new_password}}"
}
```

成功后旧 Token 全部失效。把本地 `root_password` 更新为新口令，重新登录并更新 `root_token`。首次改密前调用管理接口会返回 HTTP 403、`code=40304`。

### 3.2 获取公司、部门和内置角色 ID

以下请求都使用 Auth=`{{root_token}}`：

| 请求 | 要保存的数据 |
|---|---|
| `GET /companies` | 选定公司的 `id` → `company_id`，`rootDepartmentId` → `test_dept_id` |
| `GET /departments/tree?companyId={{company_id}}` | 确认部门树；本流程使用根部门，便于覆盖同公司的测试用户 |
| `GET /roles?keyword=RBAC_ADMIN` | 找到编码精确等于 `RBAC_ADMIN` 的角色，保存 `id` → `admin_role_id` |

不要假定用户 ID、角色 ID 或部门 ID 的固定数值。员工与临时用户的 ID 从 10000 起分配，业务系统账号使用内部编号段。

### 3.3 创建管理员和普通用户

先用 Auth=`{{root_token}}` 调用 `POST /users` 创建管理员账号：

```json
{
  "username": "{{admin_username}}",
  "realName": "Apifox 测试管理员",
  "departmentId": {{test_dept_id}},
  "userType": "EMPLOYEE",
  "employeeNo": "{{admin_username}}",
  "password": "{{admin_password}}"
}
```

预期 HTTP 200，保存 `data.id` 到 `admin_user_id`。再调用同一接口创建普通用户：

```json
{
  "username": "{{user_username}}",
  "realName": "Apifox 测试普通用户",
  "departmentId": {{test_dept_id}},
  "userType": "EMPLOYEE",
  "employeeNo": "{{user_username}}",
  "password": "{{user_password}}"
}
```

保存 `data.id` 到 `user_id`。上述数字字段的变量不加引号，发送前确认变量已替换为整数。显式设置有效口令时，新账号通常不需要首次改密；仍以登录响应的 `mustChangePwd` 为准。

### 3.4 给管理员指派 `RBAC_ADMIN`

使用 Auth=`{{root_token}}`，调用 `POST /users/{{admin_user_id}}/roles`：

```json
{
  "roleId": {{admin_role_id}},
  "scopeDepartmentId": {{test_dept_id}},
  "reason": "Apifox 管理员测试"
}
```

预期 HTTP 200。管理员将能管理该适用部门及下级；普通用户暂时不分配角色。

### 3.5 准备一个仅可查看菜单的角色

本步骤让普通用户的授权结果可以通过真实接口验证，不依赖导入 Excel。

使用 Auth=`{{root_token}}`，调用 `POST /roles`：

```json
{
  "roleCode": "APIFOX_MENU_READER_01",
  "roleName": "Apifox 菜单只读测试角色",
  "description": "仅用于验证普通用户授权及回收"
}
```

保存 `data.id` 到 `reader_role_id`。不传 `companyId` 会创建全局角色，因此这里必须由超级管理员创建。重复运行请换一个唯一的 `roleCode`，或确认原测试角色及其权限后复用。

再调用 `GET /permissions?keyword=rbac%3Amenu%3Aview`，从 `data.items` 找到 `permissionCode` **精确等于** `rbac:menu:view` 的项，把其 `id` 保存到 `menu_view_permission_id`。在 Apifox Query 表格填写时，可直接输入 `keyword=rbac:menu:view`，让客户端编码。

最后调用 `POST /roles/{{reader_role_id}}/permissions`：

```json
{
  "permissionIds": [{{menu_view_permission_id}}]
}
```

预期 HTTP 200，`data.permissions` 只有该菜单查看权限。此时普通用户尚未持有该角色。

## 4. 分别登录，保存两个 Token

为 `POST /auth/login` 保存两个独立调试用例：

| 用例 | JSON Body | 保存位置 |
|---|---|---|
| 管理员登录 | `{"username":"{{admin_username}}","password":"{{admin_password}}"}` | `data.token` → `admin_token` |
| 普通用户登录 | `{"username":"{{user_username}}","password":"{{user_password}}"}` | `data.token` → `user_token` |

可在普通用户登录用例的“后置操作 → 自定义脚本”中保存 Token：

```javascript
pm.test("普通用户登录成功", function () {
  pm.response.to.have.status(200);
  const body = pm.response.json();
  pm.expect(body.code).to.eql(0);
  pm.expect(body.data.token).to.be.a("string").and.not.empty;
  pm.environment.set("user_token", body.data.token);
  pm.environment.set("user_id", body.data.userId);
});
```

管理员用例将变量名改为 `admin_token`、`admin_user_id`；超级管理员登录只保存到 `root_token`。不要把上述脚本配置为整个项目的公共后置操作，以免后面的普通用户登录覆盖管理员 Token。脚本 API 见 [Apifox 官方文档](https://docs.apifox.com/postman-script-api)。

## 5. 先验证管理员与普通用户的差异

每个用例都显式指定 Auth 中的 Token 变量。无认证用例选择“无需认证”，并确认没有手工或公共参数添加的 `Authorization` 请求头。

| 编号 | 请求 | 身份 | 预期结果 |
|---|---|---|---|
| A1 | `GET /auth/me` | `admin_token` | 200、`code=0`；用户是测试管理员，角色含 `RBAC_ADMIN`，`superAdmin=false` |
| A2 | `GET /users?keyword={{user_username}}&departmentId={{test_dept_id}}&page=0&size=20` | `admin_token` | 200；只返回管理范围内用户，测试普通用户应可查到 |
| A3 | `GET /users/{{user_id}}` | `admin_token` | 200；可查看范围内普通用户 |
| A4 | `GET /dashboard` | `admin_token` | 200，包含 Dashboard 指标 |
| U1 | `GET /auth/me` | `user_token` | 200；用户是测试普通用户，`superAdmin=false`，起初 `roles`、`permissions` 为空 |
| U2 | `GET /users/{{user_id}}` | `user_token` | 200；本人可查自己 |
| U3 | `GET /users` | `user_token` | 403、`code=40301`；没有用户管理权限 |
| U4 | `GET /users/{{admin_user_id}}` | `user_token` | 403、`code=40302`；无权查看其他用户所属部门的信息 |
| U5 | `GET /dashboard` | `user_token` | 403、`code=40301` |
| U6 | `GET /menus` | `user_token` | 403、`code=40301`；完整菜单树需要 `rbac:menu:view` |
| U7 | `GET /menus/me` | `user_token` | 200，当前没有菜单资源权限时通常为 `data=[]` |
| N1 | `GET /auth/me` | 无认证 | 401、`code=40101` |

`GET /menus/me` 是登录用户查询动态菜单的接口；`GET /menus` 是需要权限的完整菜单管理接口。两者预期不同。`rbac:menu:view` 只赋予查询完整菜单树的能力，不等于获得每个菜单资源的查看权限。

## 6. 区分“鉴权判定拒绝”和“调用接口被拒绝”

普通用户使用 Auth=`{{user_token}}` 调用 `POST /authz/check`，Body：

```json
{
  "permission": "rbac:user:view",
  "targetDeptId": {{test_dept_id}}
}
```

预期 **HTTP 200、`code=0`、`data.allowed=false`、`data.reason=MISSING_PERMISSION`**。这是普通用户查询自己的权限，调用本身成功。

保持普通用户 Token，在同一 Body 加上 `"userId": {{admin_user_id}}`，预期 **HTTP 403、`code=40301`**，因为普通用户没有查询他人所需的 `rbac:authz:invoke`。

再用 Auth=`{{admin_token}}` 发送这个带 `userId` 的请求，预期 HTTP 200、`allowed=true`、`reason=ROLE_GRANTED`：管理员有权查询，且被查询的管理员确实持有该权限。

若改用 `POST /authz/explain`，查询他人要求 `rbac:authz:explain`；正常完成时还会在 `data.trace` 返回解释。

## 7. 验证“授权后可访问，回收后立即拒绝”

### 7.1 管理员向普通用户分配最小权限角色

使用 Auth=`{{admin_token}}`，调用 `POST /users/{{user_id}}/roles`：

```json
{
  "roleId": {{reader_role_id}},
  "scopeDepartmentId": {{test_dept_id}},
  "reason": "Apifox 验证菜单只读权限"
}
```

预期 HTTP 200，保存 `data.assignmentId` 到 `user_assignment_id`。管理员自身持有 `rbac:menu:view`，因此可以在其范围内授出这个角色。

### 7.2 普通用户用原 Token 再次请求

无需重新登录，继续使用原 `user_token`：

| 请求 | 预期结果 |
|---|---|
| `GET /auth/me` | 200，`permissions` 出现 `rbac:menu:view`，`roles` 出现测试角色 |
| `GET /menus` | 从之前的 403 变为 200 |
| `GET /users` | 仍为 403，最小权限角色没有授予用户管理能力 |
| `POST /authz/check`，Body 为 `{"permission":"rbac:menu:view","targetDeptId":{{test_dept_id}}}` | 200、`allowed=true`、`reason=ROLE_GRANTED` |

### 7.3 管理员回收该指派

使用 Auth=`{{admin_token}}`，调用：

```http
DELETE /users/{{user_id}}/roles/{{user_assignment_id}}?reason=Apifox测试结束回收
```

预期 HTTP 200、`code=0`、`data=null`。再使用**同一个** `user_token` 调用 `GET /menus`，预期立即恢复为 HTTP 403、`code=40301`；调用本人 `/authz/check` 检查 `rbac:menu:view`，预期 200、`allowed=false`。

角色权限由服务端逐次判定，JWT 不保存权限列表，因此不需要等 Token 到期。`GET /auth/me` 此时仍应为 200，说明登录状态有效，只是对应权限已回收。

## 8. 可选：停用与登出验证

这些操作放在授权、回收测试之后执行：

1. 管理员使用 `admin_token` 调用 `PATCH /users/{{user_id}}/status`，Body 为 `{"status":"DISABLED","reason":"Apifox停用测试"}`，预期 200。
2. 普通用户用原 `user_token` 调用 `GET /auth/me`，预期 HTTP 401、`code=40101`；账号停用在下一次请求生效。
3. 管理员将同一接口的 `status` 改为 `ACTIVE`，恢复测试账号，再让普通用户重新登录并更新 `user_token`。
4. 普通用户使用 `user_token` 调用 `POST /auth/logout`，预期 200。再次用这个 Token 调用 `GET /auth/me`，预期 HTTP 401、`code=40101`。

要进一步验证部门隔离，可把管理员的适用部门选为某个子部门，并由 `root` 在另一分支创建测试用户。管理员的 `/users` 列表不应出现范围外用户，直接请求范围外用户详情应返回 HTTP 403、`code=40302`。若管理员仍持有根部门指派，需要先由 `root` 回收该宽范围指派，否则两个范围会合并，无法测出隔离效果。

## 9. 在 Apifox 保存断言与测试记录

为每个表格用例保存请求、对应 Auth 和断言，可按第 5～8 节顺序加入自动化测试场景。准备数据只执行一次；再次运行授权、回收场景前，确认普通用户没有遗留的测试角色指派。

以下是普通用户访问管理接口时的后置断言：

```javascript
pm.test("普通用户没有用户管理权限", function () {
  pm.response.to.have.status(403);
  pm.expect(pm.response.json().code).to.eql(40301);
});
```

以下是本人权限查询被正常拒绝时的断言：

```javascript
pm.test("本人鉴权查询成功，但无用户查看权限", function () {
  pm.response.to.have.status(200);
  const body = pm.response.json();
  pm.expect(body.code).to.eql(0);
  pm.expect(body.data.allowed).to.eql(false);
  pm.expect(body.data.reason).to.eql("MISSING_PERMISSION");
});
```

再用管理员查询 `GET /audit-logs?actor={{user_username}}&page=0&size=20` 核对用户鉴权记录；`eventType=ROLE_ASSIGN`、`ROLE_REVOKE` 可核对管理员的授权和回收操作。这些操作的操作者是管理员，应使用 `actor={{admin_username}}` 筛选。管理权限鉴权会产生 `AUTH_SUCCESS`、`AUTH_FAILED`，状态变更产生 `USER_STATUS`，登出产生 `LOGOUT`。

测试记录至少包含：用例编号、身份、请求路径、HTTP 状态、业务 `code`、关键 `data` 字段和结论。截图隐藏 Token、口令和 Authorization 头。

## 10. 常见问题

| 现象 | 排查方式 |
|---|---|
| 普通用户访问什么都成功 | 先查 `/auth/me` 的用户 ID；检查是否继承了 root 或管理员的 Auth，或重复添加 Authorization |
| 全部请求都是 401 | 确认 Token 变量有本地值，Token 输入框未重复加 Bearer，且未到期、登出或改过口令 |
| 管理员接口返回 40304 | 先完成该账号的首次改密，再重新登录更新 Token |
| 新账号登录成功但没有管理权限 | 用户类型不自动授权；检查是否指派了 `RBAC_ADMIN`、角色是否启用及适用部门是否正确 |
| 创建用户或角色返回 409 | 用户名、工号、角色编码可能重复；已删除用户名也不能复用 |
| 普通管理员修改测试角色返回 403 | 本文创建的是全局角色，修改其定义、权限须使用 root；管理员仍可按范围给用户指派该角色 |
| `/authz/check` 返回 200 但 `allowed=false` | 查看 reason；这是判定结果，不是登录或调用失败 |
| 授权或回收后结果不符 | 确认是实际后端而非 Mock，查询用户详情，检查是否仍通过其他角色或适用部门拥有同一权限 |
| 返回 `OUT_OF_DATA_SCOPE` | 核对请求 targetDeptId 是否落在该权限自身的指派适用部门及下级，不能借其他角色的范围拼接放行 |

完成后可由 root 删除这两个专用测试账号。用户删除会保留指派供审计，因此若要同时删除测试角色，应先回收测试用户持有的该角色全部指派，再由 root 删除角色。管理员不能删除自己；用户删除是逻辑删除，再次从头测试请使用新的用户名和工号。
