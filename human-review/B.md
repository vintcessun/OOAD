# B 权限判定与缓存一致性

主审姓名：蔡俊晨；交叉复核姓名：待填写；日期：10.20；阅读与复现状态：待完成。

预计16小时，负责2个权限中心API。基线提交：a42b8018d6a9c9051fd527dcae05a8da04746082。

当前复核提交：`506440d2babbb232e184b3ef356f53daecb8b389`。账号到期已取消、跨公司角色指派仍保留；中间版本新增的 check target-only 代查已在 `363939a` 撤回，当前查询他人只按被查用户所属部门校验。原基线保留用于说明审查包来源，当前结论以本提交为准。

可上传的完整问题、覆盖关系、解决方案与测试缺口见 [B 详细审核报告](problems/B/B-review-20261021.md)。

## 阅读顺序

鉴权API/ExplainView及所有管理调用 → AuthzQueryService/AuthorizationService → 账号与角色指派查询 → 授权版本与缓存 → Decision/范围检查 → 返回及审计。

关联StatsDtos.AuthzRequest、AssignmentRow、V2迁移、拦截器、审计及C/D/E全部授权写入。只审查权限中心判定与输出，不检查外部业务系统运行。

## 主审文件与代码范围

状态由实际审查者填写。完整阅读指负责范围内的业务代码；依赖锁文件、模型和工作簿按范围栏核对。共享文件仅承担本人片段。

| 文件 | 层级 | 重点代码/范围 | 关联人 | 阅读状态 | 证据 |
| --- | --- | --- | --- | --- | --- |
| [rbac-contract/src/main/resources/db/migration/V2__grant_version.sql](../rbac-contract/src/main/resources/db/migration/V2__grant_version.sql) | 数据库迁移 | 授权版本结构及初始化B；迁移E；运行验证F | E/F | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Decision.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Decision.java) | 输入输出 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/GrantRow.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/GrantRow.java) | 输入输出 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/GrantSet.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/GrantSet.java) | 输入输出 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Scope.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Scope.java) | 输入输出 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/GrantVersion.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/GrantVersion.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantCacheMapper.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantCacheMapper.java) | 查询与持久化 | 带版本缓存键、读取、写入、损坏值与Redis故障 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantVersionMapper.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/GrantVersionMapper.java) | 查询与持久化 | current、bump与事务内更新 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java) | 业务与事务 | 判定、范围与失效B；C/D/E复核各自调用；审计副作用F；重点：decide、subjectDenyReason、covers、loadGrants、scopeOf、requireInScope、bumpGrantVersion | C/D/E/F | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java) | 业务与事务 | check、permissionOf、resolveSubject、denied | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java](../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java) | 后端测试 | 授权缓存B；注销失败A；环境与报告F | A/F | 未读 | 待填写 |
| [rbac-web/src/views/ExplainView.vue](../rbac-web/src/views/ExplainView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |

## 必须关联阅读

| 文件 | 主审 | 关联范围 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| [deploy/env.example](../deploy/env.example) | F | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 未读 | 待填写 |
| [docker-compose.yml](../docker-compose.yml) | F | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 未读 | 待填写 |
| [rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql](../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql) | E | DDL/FK/唯一键/索引及初始化E；各业务人复核对应表和初始化 | 未读 | 待填写 |
| [rbac-contract/src/main/resources/openapi/rbac-api.yaml](../rbac-contract/src/main/resources/openapi/rbac-api.yaml) | F | 契约结构与公共schema；各负责人逐API核对权限、参数及响应 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java) | A | 认证和装配A；Clock、Redis与运行语义B/F；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java) | A | 认证四入口A；check/explain B；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/AssignmentRow.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/AssignmentRow.java) | C | 指派投影C；授权展开B；角色持有者D；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/StatsDtos.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/StatsDtos.java) | F | 统计类型F；AuthzRequest B；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java) | C | 指派写入/删除/批量与有效SA计数C；授权展开B；持有者查询D；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java) | F | 独立审计事务及事件F；各业务人复核实际调用时机与内容；重点：record、recordDecision、search、REQUIRES_NEW、truncate | 未读 | 待填写 |
| [rbac-structured/src/main/resources/application.yml](../rbac-structured/src/main/resources/application.yml) | F | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java) | F | 公共测试结构F；具体用例按test-matrix逐方法分配，不要求F重读各业务断言 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java) | E | 四个导入测试E；B失效、C编号归属、D模板同步、F环境 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java) | F | 隔离环境、快照和辅助方法；C/E复核V3自增起点恢复 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java) | F | 公共测试结构F；各业务人按test-matrix主审断言及目标分支 | 未读 | 待填写 |
| [rbac-structured/src/test/resources/application-test.yml](../rbac-structured/src/test/resources/application-test.yml) | F | 测试数据库及Redis隔离；身份、版本、编号测试环境 | 未读 | 待填写 |
| [rbac-web/e2e/console.spec.ts](../rbac-web/e2e/console.spec.ts) | F | 隔离运行与执行顺序F；每一步业务预期归对应负责人 | 未读 | 待填写 |
| [rbac-web/e2e/global-setup.ts](../rbac-web/e2e/global-setup.ts) | F | 隔离运行与执行顺序F；每一步业务预期归对应负责人 | 未读 | 待填写 |
| [rbac-web/nginx.conf](../rbac-web/nginx.conf) | F | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 未读 | 待填写 |
| [rbac-web/src/api/index.ts](../rbac-web/src/api/index.ts) | F | A auth；B authz；C user/assignment；D role/catalog；E org/import；F menu/stats与公共部分 | 未读 | 待填写 |
| [rbac-web/src/api/types.ts](../rbac-web/src/api/types.ts) | F | A auth；B authz；C user/assignment；D role/catalog；E org/import；F menu/stats与公共部分 | 未读 | 待填写 |
| [rbac-web/tests/components.spec.ts](../rbac-web/tests/components.spec.ts) | F | 公共逻辑与环境主审；组件业务断言由各业务负责人按任务书认领 | 未读 | 待填写 |

## API责任

| 卡号 | API | 功能 | 状态 |
| --- | --- | --- | --- |
| [B01](api-cards/B.md#b01) | POST /authz/check | 鉴权：某用户能否对某部门的数据执行某操作 | 未审查 |
| [B02](api-cards/B.md#b02) | POST /authz/explain | 权限解释：与 check 相同的判定，另返回逐步的判定依据（trace） | 未审查 |

## 人工反例清单

- [ ] 权限码与范围是否来自同一指派，是否会拼接出不存在的授权？
- [ ] 同一权限的selfOnly与不限本人授权如何合并，是否符合预期？
- [ ] SA旁路前后保留哪些检查，不存在权限/部门时如何解释？
- [ ] requireInScope拒绝时，审计是否重复、错记调用者或丢失原因？
- [ ] 读版本、读授权、写缓存与撤权提交交错时，下一次请求的边界是什么？
- [ ] 是否存在改变有效权限但未更新版本的写入口？
- [ ] 事务内读缓存是否观察到未提交授权，回滚后是否留下错误缓存？
- [x] target-only 及相关脱敏字段问题已随 `363939a` 撤回，不再属于当前实现。
- [ ] 跨公司指派后，权限作用域是否仍跟随每次指派的scopeDeptId，而不是用户所属公司？
- [x] 已撤回 target-only；业务系统转发最终用户 JWT，代查他人只按被查用户所属部门校验（`363939a`）。

## 对应测试方法

以下为断言审查责任，不代表测试已运行；跨业务测试由相关人复核自己的片段。

| 测试 | 状态 | 分支到达/最终状态证据 |
| --- | --- | --- |
| [AcceptanceTest.java.t04_userWithoutRolesIsDeniedByDefault](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L106) | 未读 | 待填写 |
| [AcceptanceTest.java.t05_roleWithoutTargetPermissionIsDenied](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L116) | 未读 | 待填写 |
| [AcceptanceTest.java.t06_multipleRolesAreUnioned](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L126) | 未读 | 待填写 |
| [AcceptanceTest.java.t07_permissionInDepartmentADoesNotReachDepartmentB](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L137) | 未读 | 待填写 |
| [AcceptanceTest.java.t07b_scopeBelongsToTheAssignmentNotToTheUnionOfRoles](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L152) | 未读 | 待填写 |
| [AcceptanceTest.java.t11_superAdminBypassesEngineButMustBeActive](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L251) | 未读 | 待填写 |
| [AcceptanceTest.java.t12_revokedRoleIsDeniedOnTheNextRequest](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L269) | 未读 | 待填写 |
| [AcceptanceTest.java.t12b_revocationDoesNotDependOnRedis](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L283) | 未读 | 待填写 |
| [AcceptanceTest.java.t20_explainAndCheckAlwaysAgree](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L509) | 未读 | 待填写 |
| [AcceptanceTest.java.t20b_ordinaryUserCannotProbeOthersThroughAuthzApis](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L540) | 未读 | 待填写 |
| [AcceptanceTest.java.t22_anotherReplicaSeesTheRevocationOnItsNextRequest](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L554) | 未读 | 待填写 |
| [ManagementApiTest.java.assignmentsMayCrossCompaniesWithinManagementScope](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L363) | 未读 | 重点复核 L418-L429：本人令牌跨公司权限生效，A 公司管理员不能凭目标部门代查 B 公司用户 |
| [RedisFailureTest.java.grantCacheTreatsRedisFailureAsMiss](../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java#L35) | 未读 | 待填写 |
| [RedisFailureTest.java.grantCacheRoundTripAndCorruptValue](../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java#L47) | 未读 | 待填写 |

## 交付检查

- [ ] 文件和关键方法已读并留证据。
- [ ] 全部API卡填写独立预期、间接调用、最终查询及副作用。
- [ ] 业务测试核对关键断言与拒绝原因，未执行明确记录。
- [ ] 问题分类完成，重要结论由另一人挑战。

返回[总入口](README.md)，使用[模板](templates.md)与[复核记录](cross-review.md)。
