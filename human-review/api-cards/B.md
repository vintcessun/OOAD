# B API 业务链路卡

当前内容按源码提交 `506440d2babbb232e184b3ef356f53daecb8b389` 核对；当前 HEAD `f23c71b` 仅含审核文档。target-only 已在 `363939a` 撤回。当前后端 69/69、JaCoCo 门槛、前端 typecheck、42/42、build 和 Playwright 9/9 均通过。交叉复核签字仍待完成。

[返回任务书](../B.md) · [模板](../templates.md) · [详细审核报告](../problems/B/B-review-20261021.md)

<a id="b01"></a>
<!-- api: POST /authz/check -->
## B01 鉴权：某用户能否对某部门的数据执行某操作

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | B；主体调用链、责任测试静态审核及当前后端/前端自动测试已完成；专项缺口、当前 E2E 和交叉复核签字待完成 |
| API 与功能 | `POST /authz/check`；判断指定主体是否具有目标权限，以及该权限的指派范围是否覆盖 `targetDeptId` |
| 契约 | [checkAuthorization](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L162)；入口权限为 `@self`，表示有效登录即可进入接口，查询他人的附加限制由 `AuthzQueryService` 校验 |
| 前端封装与调用方 | [authzApi.check](../../rbac-web/src/api/index.ts#L19)；权限控制台当前没有直接调用；`rbac-mock-biz` 原样转发最终用户 JWT，查询本人无需代查权限 |
| Controller | [AuthController.check](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L67)；只接收 `Operator` 和 `AuthzRequest`，调用 `authzQuery.check(op, req, false)` 并包装响应 |
| 直接 Service 入口 | [AuthzQueryService.check](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java#L37) |
| 调用者及被查主体 | 调用者是 Bearer JWT 对应的 `Operator`，不能由请求体伪造。被查主体优先使用 `userId`，其次使用去空格后的 `username`；两者都不填时查询调用者本人 |
| 权限参数 | 非空 `permission` 优先；否则要求同时提供 `resource` 和 `action`，拼成 `resource:action`；两种形式都不完整时返回 HTTP 400 |
| 谁能查谁 | 查本人不需要附加权限。查他人时，调用者须持有 `rbac:authz:invoke`，并覆盖被查用户所属部门；不能凭 `targetDeptId` 放宽。无权时返回 HTTP 403 |
| 独立业务预期 | 主体不存在、删除、停用分别返回 `SUBJECT_NOT_FOUND`、`SUBJECT_DELETED`、`SUBJECT_DISABLED`；当前版本没有账号到期分支。有效超级管理员返回 `SUPER_ADMIN_BYPASS`。普通用户无目标权限返回 `MISSING_PERMISSION`；目标部门不存在返回 `TARGET_DEPT_NOT_FOUND`；有权限但范围不覆盖返回 `OUT_OF_DATA_SCOPE`；覆盖时返回 `ROLE_GRANTED` |
| 范围与 `selfOnly` | `targetDeptId` 为空时只判断是否具有目标权限；非空时读取目标部门路径，并逐条判断同一 `GrantRow` 的 `scopePath` 是否覆盖目标路径，不能把一条指派的权限与另一条指派的部门范围拼接。所有覆盖候选都为 `selfOnly=true` 时，最终结果才为 `true` |
| 完整间接调用及最终查询 | `AuthzQueryService.permissionOf` → `resolveSubject` → `AuthorizationService.scopeOf/decide` → `UserMapper.findById` → `loadGrants` → `GrantVersionMapper.current` → `GrantCacheMapper.find`；缓存未命中时调用 `UserRoleMapper.findAssignmentsOfUser` 和 `findGrantsOfUser`；有目标部门时调用 `DepartmentMapper.findPathById`；最后调用 `AuditService.recordDecision` |
| 最终数据来源 | 主体状态来自 `sys_user`。指派来自 `sys_user_role`，并关联 `sys_role`、`sys_department` 和 `sys_user`。有效授权由 `sys_user_role`、`sys_role`、`sys_role_permission`、`sys_permission`、`sys_department` 展开为 `GrantRow`。授权版本来自 `sys_grant_version` |
| 事务与最终状态 | check 是读接口，不修改授权表。`AuditService.recordDecision` 使用 `REQUIRES_NEW` 独立事务写入 `sys_audit_log`。正常权限拒绝仍是一次完成的判定，返回 HTTP 200 + `allowed=false`；只有请求参数错误、未认证或调用者无权查询他人时使用 4xx |
| 版本缓存与 Redis 键 | 先读 MySQL 全局版本，再以 `rbac:grants:v<version>:u<userId>` 为键读取 Redis。命中直接反序列化 `GrantSet`；未命中查数据库并在非读写事务中写回。Redis 异常按未命中处理，不改变判定结果。单个/批量指派、角色状态与权限增删、权限清单导入都在授权写事务内调用 `bumpGrantVersion`；提交后的请求使用新键，旧键等待 TTL 清理 |
| 审计 | 完成判定后调用 `recordDecision`。允许记录 `AUTH_SUCCESS`，拒绝记录 `AUTH_FAILED`；`actorId/actorName` 使用被判定主体，IP 来自 caller，调用者与主体不同时在 detail 中追加 `caller=<username>`；另记录权限码、目标部门、原因、耗时和缓存命中状态。该模型缺少结构化 caller 和 matched 授权依据，见 HR-B-015 |
| 反例与边界 | 应检查：无角色；角色无权限；权限与范围来自不同指派；同一权限同时存在 `selfOnly=true/false`；目标部门不存在；SA 已停用；Redis 故障；撤权与缓存写回交错；跨公司指派不应放宽查询他人的主体部门边界 |
| 对应测试 | `AcceptanceTest.t04/t05/t06/t07/t07b/t11/t12/t12b/t20/t20b/t22`，`RedisFailureTest` 两项缓存测试，以及跨公司指派管理测试。T04-T06 已静态复核：主分支可到达，但缺 matched/selfOnly/AUTH 审计字段断言；T05 未直接断言 allowed=false；T06 未断言匹配角色和第二次 cacheHit |
| 执行证据 | 当前源码在隔离 MySQL/Redis + Linux Maven/JDK 21 中后端 69/69、JaCoCo 门槛通过；Windows 前端 typecheck、42/42 coverage 和 build 通过；E2E 改用 Linux 容器后端、Windows Vite/Chromium 后 9/9 通过。详见[详细审核报告](../problems/B/B-review-20261021.md#9-本轮执行证据) |
| 当前结论 | target-only 已按审核意见撤回，当前谁能查谁的边界回到被查用户所属部门。仍有角色改名缓存、契约机器表达、ExplainView 请求乱序、审计提交时序、唯一 root、集团模型等问题；审计测试尚未覆盖合法代查的 caller detail、外层最终提交失败和审计 INSERT 失败。最终结论和签字由主审与复核人填写 |

### B01 分支核验表

| 前置条件 | HTTP | `allowed` | `reason` / 结果 |
| --- | ---: | ---: | --- |
| 请求体为空，或权限参数不完整 | 400 | 不适用 | 参数错误 |
| 调用者未认证 | 401 | 不适用 | 未认证 |
| 调用者无权查询另一个用户 | 403 | 不适用 | `CALLER_NOT_ALLOWED` 语义，不进入正常主体判定 |
| 被查主体不存在 | 200 | false | `SUBJECT_NOT_FOUND` |
| 被查主体已删除 | 200 | false | `SUBJECT_DELETED` |
| 被查主体已停用 | 200 | false | `SUBJECT_DISABLED` |
| 有效超级管理员 | 200 | true | `SUPER_ADMIN_BYPASS` |
| 普通主体没有目标权限 | 200 | false | `MISSING_PERMISSION` |
| 目标部门不存在 | 200 | false | `TARGET_DEPT_NOT_FOUND` |
| 有权限但指派范围不覆盖目标部门 | 200 | false | `OUT_OF_DATA_SCOPE` |
| 有权限且范围覆盖 | 200 | true | `ROLE_GRANTED` |

<a id="b02"></a>
<!-- api: POST /authz/explain -->
## B02 权限解释：与 check 共用判定过程并返回 trace

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | B；主体调用链、T20/T20b 静态审核及当前后端/前端自动测试已完成；专项缺口、当前 E2E 和交叉复核签字待完成 |
| API 与功能 | `POST /authz/explain`；使用与 check 相同的判定引擎，同时返回逐步判定依据 `trace` |
| 契约 | [explainAuthorization](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L192)；入口权限为 `@self`，查询他人由 Service 校验 `rbac:authz:explain` 与部门范围 |
| 前端封装与调用方 | [authzApi.explain](../../rbac-web/src/api/index.ts#L21)；控制台页面为 [ExplainView.vue](../../rbac-web/src/views/ExplainView.vue) |
| Controller | [AuthController.explain](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L74)；调用 `authzQuery.check(op, req, true)` |
| 直接 Service 入口 | [AuthzQueryService.check](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java#L37)；`explain=true` 使 `AuthorizationService.decide` 创建并逐步填充 `trace` |
| 调用者及被查主体 | 主体和权限参数解析规则与 B01 相同。查本人不需要附加权限；查他人必须由 `rbac:authz:explain` 覆盖被查用户所属部门 |
| 与 B01 的关键差异 | 查询他人分别要求 `rbac:authz:invoke` / `rbac:authz:explain` 覆盖被查用户所属部门；explain 额外返回完整 `trace`，check 不返回 trace |
| 判定结果 | `allowed` 和真实 `reason` 与同一输入下的完整 check 判定一致；`trace` 说明主体状态、角色、权限、作用域、SA 旁路或拒绝原因。普通拒绝仍返回 HTTP 200，调用者无权解释他人时返回 HTTP 403 |
| 完整间接调用及最终查询 | 与 B01 共用 `permissionOf`、`resolveSubject`、`AuthorizationService.decide`、`loadGrants`、用户/授权/部门 Mapper 和 `AuditService.recordDecision`；区别是 `decide(..., true)` 创建 trace |
| 事务、缓存与审计 | 授权读取和版本化 Redis 缓存逻辑与 B01 相同；审计的 `via` 为 `explain`，页面显示完整 Decision |
| 前端核验 | `ExplainView.vue` 校验权限输入、调用 explain 并展示结论、主体、匹配依据和 trace。当前缺少请求序号或取消旧请求的保护，连续提交时较早请求的迟到响应可能覆盖新结果，见 `HR-B-003` |
| 对应测试 | `AcceptanceTest.t20_explainAndCheckAlwaysAgree` 对七组完整可见主体比较 check/explain 的 `allowed` 与 `reason`，并断言 explain 有 trace、check 无 trace；`t20b` 验证普通用户不能探测他人；跨公司测试断言只凭目标部门不能调用 explain |
| 执行证据 | 与 B01 使用同一次当前验证；后端 T20/T20b、前端单元测试和当前 E2E 9/9 已通过。T20 只比较 check/explain 彼此相等而未逐案断言期望；ExplainView 逆序返回、合法代查 actor/caller 和审计事务失败仍未覆盖 |
| 当前结论 | 后端 explain 与 check 共用判定核心，查询他人均按主体所属部门校验；前端请求乱序和契约字段完整性仍需处理。最终通过状态和签字由主审与复核人填写 |

## 共用术语：Redis 键

Redis 保存数据时使用“键 → 值”的结构，可以把“键”理解为数据库记录的唯一名称或索引。例如：

```text
键：rbac:grants:v12:u105
值：用户 105 在授权版本 12 下的 GrantSet JSON
```

该键分成四段：`rbac:grants` 表示授权缓存；`v12` 表示 MySQL 中的全局授权版本 12；`u105` 表示用户 105。读取时必须使用完全相同的键。授权变化将版本递增到 13，下一次请求改读 `rbac:grants:v13:u105`，因此版本 12 的旧值即使还在 Redis 中也不会再被使用，之后由 TTL 自动删除。

这里的“键”不是密码、JWT 或数据库主键；它只是 Redis 中定位某个缓存值的字符串名称。对应实现见 [GrantCacheMapper.key](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantCacheMapper.java#L59)。
