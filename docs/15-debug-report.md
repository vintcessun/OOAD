# 15 验收前 Debug 记录（RBAC0）

> 对应 2026-10-14 的《RBAC0 验收前 Debug 问题清单与实操指南》。范围：第一次课程检查（RBAC0、结构化设计、JPA、Controller + Service + Mapper）。
> 每个问题按清单 §四 的格式记录；行号指修复后的代码。设计层面的「为什么错、为什么这样改」见 [12 #36–#38](12-design-log.md)。

## 0. 验收前确认项（清单 §一）

| 确认项 | 状态 |
|---|---|
| 最新版本能否在本机完整通过 `mvnw verify` | ✅ 2026-10-14 本机 `./mvnw -s deploy/maven-settings-huawei.xml clean verify` 通过：65 个后端测试全部通过，行覆盖率 97.4%（1,768 / 1,815），分支 83.3%（846 / 1,016），Jacoco 门槛达标；CI 同样执行 |
| 新版本是否已重新部署到三台服务器 | ⏸ 未部署（组内决定暂缓）。部署时 Flyway 会自动执行新增的 `V2__grant_version.sql`；跑过 10 月 11 日之前旧脚本的库须按 [13 §5](13-deployment.md) 重建 |
| 老师的正式验收测试是否已接入 | ⏸ 尚未发布，发布后接入 |
| StarUML 模型是否与当前 RBAC0 代码一致 | ⏸ 待重画（以 [03](03-database.md) 的 ER 图与 [06](06-detail-structured.md) 的数据流图为准） |

## 1. 问题记录

### 问题 1

```text
问题编号：DBG-1
问题标题：授权上界校验没有考虑「只限本人」

前置数据：
  角色 DOC_SELF_VIEWER = {oa:doc:draft:view，self_only = 1}；角色 DOC_VIEWER = {oa:doc:draft:view，完整范围}。
  操作者 admin：总公司 RBAC_ADMIN + 总公司 DOC_SELF_VIEWER。被授权人 staff：道路分公司-财务部。

复现步骤：
1. admin 登录，POST /users/{staff}/roles {roleId: DOC_VIEWER}
2. admin 新建本公司角色 A_ROLE，POST /roles/{A_ROLE}/permissions {permissionIds: [oa:doc:draft:view]}
3. root 查询 /audit-logs?eventType=ROLE_ASSIGN&result=DENIED 与 PERMISSION_ASSIGN

预期结果：
  1、2 均 403，错误码 40303，审计原因 PRIVILEGE_ESCALATION；分配 DOC_SELF_VIEWER 本身可以。

实际结果（修复前）：
  1、2 均 200。授权上界只比权限码与部门作用域，admin 的「查看本人」被当成完整「查看」。

影响：
  只能看本人记录的管理员，可以借下属账号（或给自己的公司内角色）把权限放大到整个部门，是越权。

代码定位：
  rbac-structured/.../service/AssignmentService.java  context()（修复前 309 行）、escalation()（修复前 357 行）
  rbac-structured/.../service/RoleManagementService.java  firstNotHeld()（修复前 271 行）

唯一修复方案：
  授权上界同时比较权限码、部门作用域、self_only：
  - AssignmentService.context()（312 行）用新查询 RolePermissionMapper.findGrantScopesOfRole()（25 行）取角色的
    「权限码 → 是否只限本人」；escalation()（365 行）要求角色给完整范围时，操作者覆盖适用部门的授权中至少一条不限本人；
    角色本身只限本人时，哪种都可以。
  - RoleManagementService.firstNotHeld()（274 行）要求操作者在公司根部门的那条授权不限本人
    （角色管理授出的都是完整范围，self_only 只由权限清单导入设置）。
  拒绝原因仍为 PRIVILEGE_ESCALATION（40303），审计照常写入。

修复后验证：
  AcceptanceTest.t10b_selfOnlyGrantDoesNotLetAdminGrantTheFullPermission：步骤 1、2 均 403 / 40303，
  两条 DENIED 审计的原因都是 PRIVILEGE_ESCALATION；分配 DOC_SELF_VIEWER 200。原有 T10 仍通过。
```

### 问题 2

```text
问题编号：DBG-2
问题标题：Redis 版本号递增失败时继续使用旧授权

前置数据：
  用户 u1 在财务部持有 DOC_VIEWER，已鉴权两次（第二次 cacheHit = true，授权进入 Redis 键 rbac:grants:v<N>:u<u1>）。

复现步骤：
1. root 回收 u1 的 DOC_VIEWER
2. 修复前：回收在事务提交后对 Redis 执行 INCR rbac:grants:version；若此时 Redis 超时或断开，INCR 失败只记错误日志
3. u1 再次鉴权 oa:doc:draft:view

预期结果：
  allowed = false，reason = MISSING_PERMISSION，不得命中旧缓存。

实际结果（修复前）：
  步骤 2 失败后版本号仍是 N，u1 的下一次请求读到旧键，allowed = true，cacheHit = true，持续到 TTL（10 分钟）结束。
  旧代码把这个窗口写成「唯一的不一致窗口，由运维告警兜底」。

影响：
  违反「权限修改后，在下一次访问时更新」；方向是该拒绝的放行。

代码定位：
  修复前 GrantCacheMapper.currentVersion()（42 行）、bumpVersion()（74 行）；
  AuthorizationService.loadGrants()（174 行）、invalidateAfterCommit()（196 行）。

唯一修复方案：
  版本号移到 MySQL，与授权修改同一事务提交（清单给出的第三个方向「可证明不会继续读取旧授权的失效机制」）：
  - V2__grant_version.sql 新建 sys_grant_version（一行）；GrantVersion 实体、GrantVersionMapper.current() / bump()。
  - AuthorizationService.bumpGrantVersion()（207 行，原 invalidateAfterCommit）在当前事务里 version + 1，
    更新行数不是 1 就抛异常让整个修改回滚。9 个调用点都在 @Transactional 过程里。
  - loadGrants()（183 行）先从 MySQL 读版本号、再按它读 Redis；在读写事务里不写回缓存（195 行），
    避免把本事务未提交的数据写进缓存。
  - GrantCacheMapper 只存取数据，不再管版本号。
  为什么可证明：读到新版本号的请求随后读到的授权数据一定是新的（同一事务提交）；读到旧版本号的请求即使读到新数据，
  写回的也是旧版本的键，之后没人再读。失效完全不经过 Redis，Redis 的任何故障最多让缓存不命中。
  未采用：「判定直接读库」违背「提供数据库缓存设计」；「Redis 失败时本副本绕过缓存」其他副本不知道，仍读旧键。

修复后验证：
  AcceptanceTest.t12b_revocationDoesNotDependOnRedis：先让授权进缓存；回收后旧键仍在 Redis（证明回收没有依赖 Redis），
  MySQL 版本号 + 1；下一次鉴权 allowed = false、cacheHit = false、reason = MISSING_PERMISSION；
  一次被拒绝的分配不改变版本号（随事务回滚）。T12、T13、T22（第二个副本）仍通过。
```

### 问题 3

```text
问题编号：DBG-3
问题标题：重复导入权限清单的回收逻辑实测（含审计、缓存）

前置数据：
  按原表格式造的小样本清单（ExcelImportIntegrationTest，内容虚构），首次导入后 u1 持有「公司领导」，
  已鉴权 oa:doc:f01:approve 两次（第二次命中缓存）。

复现步骤：
1. 第二次导入：领导「查看/审批」→「查看」（删除单个操作）；员工「查看本人」→「查看」；
   经理某单元格改成空白（异常）；整行删掉「会议室预约」；功能点改「无」
2. 检查 role_permission、self_only、下一次鉴权、审计日志
3. 第三次导入：中间删掉一行，确认后面的功能点不错位
4. 第四次导入：含一个认不出所属系统的功能点

预期结果：
  去掉的授权删除、self_only 更新、下一次鉴权立即变化、每条变化有审计；异常单元格保留原授权；认不准时不回收。

实际结果（本次修复前）：
  删除、更新、立即生效、异常保留都已正确（10 月 14 日上一轮修复）。但只写一条 IMPORT_PERMISSIONS，
  审计里查不到哪个角色失去了哪个权限，与界面回收逐条写 PERMISSION_REVOKE 不一致。

影响：
  经导入的权限回收与范围变化无法逐条追溯。

代码定位：
  rbac-structured/.../service/ImportService.java  importPermissions()（self_only 更新 493–499 行）、revokeMissing()（543 行）

唯一修复方案：
  每回收一条记一条 PERMISSION_REVOKE、每改一条 self_only 记一条 PERMISSION_ASSIGN，详情带 source=import；
  首次导入新增的授权仍只汇总在 IMPORT_PERMISSIONS 的计数里。缓存失效走 DBG-2 的同一事务版本号（531 行）。

修复后验证：
  ExcelImportIntegrationTest.reimportingPermissionMatrixRevokesRemovedGrantsAndUpdatesSelfOnly：
  回收 11 条、self_only 更新 1 条、异常 1 个；审计 PERMISSION_REVOKE 共 11 条（含 permission=oa:doc:f01:approve source=import），
  PERMISSION_ASSIGN 1 条（permission=oa:doc:draft:view selfOnly=false source=import）；
  下一次鉴权 allowed = false 且 cacheHit = false；第三次导入不错位；第四次导入回收 0 条并在报告中说明。
```

## 2. RBAC0 验收场景实测结果（清单 §三）

全部经 HTTP 接口、连真实 MySQL 8.0 与 Redis 7 执行；2026-10-14 本机 `verify` 结果。

| 场景 | 预期 | 测试 | 实际结果 |
|---|---|---|---|
| 正确账号密码登录 | 成功返回令牌 | `AcceptanceTest.t01_…` | ✅ 200，令牌可调用接口 |
| 错误密码 | 拒绝登录并记录失败日志 | `t02_…`；`ManagementApiTest.fiveFailuresLockTheAccountEvenWhenConcurrent` | ✅ 401（40102），审计 `LOGIN / FAILED`（原因 `CREDENTIAL_INVALID`） |
| 停用账号登录 | 拒绝登录 | `t02_…` | ✅ 401（40104），审计原因 `SUBJECT_DISABLED`；已删除账号同样拒绝（按「用户名或口令错误」回答） |
| 用户无角色 | 鉴权拒绝 | `t04_…` | ✅ `allowed=false`，`MISSING_PERMISSION` |
| 用户有角色但无目标权限 | 鉴权拒绝 | `t05_…` | ✅ `MISSING_PERMISSION` |
| 用户有多个角色 | 权限取并集 | `t06_…` | ✅ 两个角色的权限都放行 |
| 部门 A 权限访问部门 B | 拒绝 | `t07_…`、`t07b_…` | ✅ `OUT_OF_DATA_SCOPE`；拼不出「A 的审批 + B 的部门」 |
| 管理员给下级授权 | 在管理范围内成功 | `t08_…` | ✅ 200，适用部门默认员工所属部门 |
| 管理员跨公司授权 | 拒绝 | `t19_…`；`ManagementApiTest.companyAdminManagesOnlyCompanyRoles` | ✅ A 公司管理员给 B 公司员工授权 403（40302）；超级管理员把适用部门设到别的公司 400（`CROSS_COMPANY`）；公司内角色指派到别的公司 400；跨分公司 `t09_…` 403 并写审计 |
| 管理员授出自己没有的权限 | 拒绝 | `t10_…`、`t10b_…` | ✅ 403（40303 `PRIVILEGE_ESCALATION`）；只有「本人」范围也拒绝 |
| 回收角色后再次鉴权 | 立即拒绝 | `t12_…`、`t12b_…`、`t13_…`、`t22_…` | ✅ 下一次请求拒绝，含已命中缓存、另一副本、Redis 不参与失效 |
| 重复角色指派 | 不产生重复关联 | `t15_…` | ✅ 第二次 409，库中只有一条 |
| 批量授权包含越权对象 | 按 ATOMIC/PARTIAL 约定处理 | `t16_…` | ✅ ATOMIC 整批不写；PARTIAL 写通过的、逐人给原因 |
| 登出后携带旧令牌 | 拒绝 | `t17_…` | ✅ 401 |
| 隐藏菜单后直接调用 API | 后端仍拒绝 | `t18_…` | ✅ 403 |
| 两家公司同名部门 | 授权不串公司 | `t19_…` | ✅ 两个「财务部」互不影响 |
| 权限解释与正式鉴权 | 结论一致 | `t20_…` | ✅ 7 组输入结论全同 |

## 3. 尚未闭环的事项

- 服务器部署、老师正式测试、StarUML 模型：见 §0，按组内安排处理。
- 真实 Excel 的全量导入以 [03 §5.4](03-database.md) 的记录为准；本轮改动不涉及解析规则，只增加了回收与范围变化的审计。
