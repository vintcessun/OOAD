# A 身份认证与请求入口

主审姓名：待填写；交叉复核姓名：待填写；日期：待填写；阅读与复现状态：待完成。

预计16小时，负责4个权限中心API。基线提交：a42b8018d6a9c9051fd527dcae05a8da04746082。

## 阅读顺序

登录/改密/个人中心 → authApi/http → 拦截器与契约 → AuthController → AuthenticationService/TokenService → 用户与注销记录 → 身份store/路由/页面。/auth/me继续追踪UserManagementService.detail。

关联阅读UserMapper登录失败/重置、UserManagementService.detail、UserDetailPanel的自我撤权及运行配置。与B区分身份有效和权限有效，与C复核自我撤权，与F复核运行安全。

## 主审文件与代码范围

状态由实际审查者填写。完整阅读指负责范围内的业务代码；依赖锁文件、模型和工作簿按范围栏核对。共享文件仅承担本人片段。

| 文件 | 层级 | 重点代码/范围 | 关联人 | 阅读状态 | 证据 |
| --- | --- | --- | --- | --- | --- |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java) | 装配与拦截边界 | 认证和装配A；Clock、Redis与运行语义B/F；重点：完整阅读参数、字段、调用方及状态变化 | B/F | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/config/WebConfig.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/WebConfig.java) | 装配与拦截边界 | addInterceptors、verifyEveryRouteDeclared及/error排除 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ApiResponse.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ApiResponse.java) | 入口与响应 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java) | 入口与响应 | 认证四入口A；check/explain B；重点：完整阅读参数、字段、调用方及状态变化 | B | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/ContractRoutes.java) | 入口与响应 | load/fromDocument、路由匹配、undeclared/unimplemented及权限码 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/GlobalExceptionHandler.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/GlobalExceptionHandler.java) | 入口与响应 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/PermissionInterceptor.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/PermissionInterceptor.java) | 入口与响应 | preHandle、首次改密、@self、bearerToken、clientIp | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Operator.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Operator.java) | 输入输出 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/TokenRevocationMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/TokenRevocationMapper.java) | 查询与持久化 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java) | 业务与事务 | login、authenticate、logout、changePassword、loginFailed、meetsPolicy | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/BizException.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/BizException.java) | 业务与事务 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/TokenService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/TokenService.java) | 业务与事务 | issue、parse、时钟、签名及有效期 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/api/http.ts](../../rbac-web/src/api/http.ts) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/router/index.ts](../../rbac-web/src/router/index.ts) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/router/routes.ts](../../rbac-web/src/router/routes.ts) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/stores/auth.ts](../../rbac-web/src/stores/auth.ts) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/utils/password.ts](../../rbac-web/src/utils/password.ts) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/ForbiddenView.vue](../../rbac-web/src/views/ForbiddenView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/LoginView.vue](../../rbac-web/src/views/LoginView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/MeView.vue](../../rbac-web/src/views/MeView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/PasswordView.vue](../../rbac-web/src/views/PasswordView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/tests/auth.spec.ts](../../rbac-web/tests/auth.spec.ts) | 前端测试 | 公共逻辑与环境主审；组件业务断言由各业务负责人按任务书认领 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/tests/http.spec.ts](../../rbac-web/tests/http.spec.ts) | 前端测试 | 公共逻辑与环境主审；组件业务断言由各业务负责人按任务书认领 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/tests/router.spec.ts](../../rbac-web/tests/router.spec.ts) | 前端测试 | 公共逻辑与环境主审；组件业务断言由各业务负责人按任务书认领 | 按问题安排 | 未读 | 待填写 |

## 必须关联阅读

| 文件 | 主审 | 关联范围 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| [deploy/env.example](../../deploy/env.example) | F | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 未读 | 待填写 |
| [docker-compose.yml](../../docker-compose.yml) | F | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 未读 | 待填写 |
| [rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql](../../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql) | E | DDL/FK/唯一键/索引及初始化E；各业务人复核对应表和初始化 | 未读 | 待填写 |
| [rbac-contract/src/main/resources/openapi/rbac-api.yaml](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml) | F | 契约结构与公共schema；各负责人逐API核对权限、参数及响应 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java) | C | 用户输入输出C；当前用户身份A；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java) | C | 用户与内部编号C；登录查询/失败计数/重置A；导入定位E；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java) | F | 独立审计事务及事件F；各业务人复核实际调用时机与内容；重点：record、recordDecision、search、REQUIRES_NEW、truncate | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java) | C | 用户与编号C；当前用户detail关联A；初始口令与导入关联E；重点：list/buildFilter/deptSubquery、detail、create/insertInternal、validateTypeFields、update、applyStatus、requireManageable | 未读 | 待填写 |
| [rbac-structured/src/main/resources/application.yml](../../rbac-structured/src/main/resources/application.yml) | F | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java) | F | 契约结构、权限声明、双向路由核对；A复核入口语义 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java) | F | 公共测试结构F；具体用例按test-matrix逐方法分配，不要求F重读各业务断言 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java) | F | 隔离环境、快照和辅助方法；C/E复核V3自增起点恢复 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java) | F | 公共测试结构F；各业务人按test-matrix主审断言及目标分支 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java) | B | 授权缓存B；注销失败A；环境与报告F | 未读 | 待填写 |
| [rbac-structured/src/test/resources/application-test.yml](../../rbac-structured/src/test/resources/application-test.yml) | F | 测试数据库及Redis隔离；身份、版本、编号测试环境 | 未读 | 待填写 |
| [rbac-web/e2e/console.spec.ts](../../rbac-web/e2e/console.spec.ts) | F | 隔离运行与执行顺序F；每一步业务预期归对应负责人 | 未读 | 待填写 |
| [rbac-web/e2e/global-setup.ts](../../rbac-web/e2e/global-setup.ts) | F | 隔离运行与执行顺序F；每一步业务预期归对应负责人 | 未读 | 待填写 |
| [rbac-web/nginx.conf](../../rbac-web/nginx.conf) | F | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 未读 | 待填写 |
| [rbac-web/src/api/index.ts](../../rbac-web/src/api/index.ts) | F | A auth；B authz；C user/assignment；D role/catalog；E org/import；F menu/stats与公共部分 | 未读 | 待填写 |
| [rbac-web/src/api/types.ts](../../rbac-web/src/api/types.ts) | F | A auth；B authz；C user/assignment；D role/catalog；E org/import；F menu/stats与公共部分 | 未读 | 待填写 |
| [rbac-web/src/components/UserDetailPanel.vue](../../rbac-web/src/components/UserDetailPanel.vue) | C | 用户详情与自我撤权C；个人中心和身份刷新A | 未读 | 待填写 |
| [rbac-web/tests/components.spec.ts](../../rbac-web/tests/components.spec.ts) | F | 公共逻辑与环境主审；组件业务断言由各业务负责人按任务书认领 | 未读 | 待填写 |

## API责任

| 卡号 | API | 功能 | 状态 |
| --- | --- | --- | --- |
| [A01](api-cards/A.md#a01) | POST /auth/login | 登录 | 未审查 |
| [A02](api-cards/A.md#a02) | POST /auth/logout | 登出 | 未审查 |
| [A03](api-cards/A.md#a03) | GET /auth/me | 当前用户的基本信息、角色与有效权限 | 未审查 |
| [A04](api-cards/A.md#a04) | POST /auth/password | 修改本人口令 | 未审查 |

## 人工反例清单

- [ ] 账号登录后停用、删除、过期或改密，旧令牌何时失效？
- [ ] 并发失败登录与成功登录重置交错时，锁定规则是否正确？
- [ ] 注销记录写入失败、被淘汰或Redis重启后，失效承诺是什么？
- [ ] @self放宽接口权限后，目标用户限制是否仍然正确？
- [ ] 多个请求同时401时，是否残留旧用户数据、重复跳转或覆盖新身份？
- [ ] 自我撤权后身份store、菜单、按钮、路由是否一起刷新？

## 对应测试方法

以下为断言审查责任，不代表测试已运行；跨业务测试由相关人复核自己的片段。

| 测试 | 状态 | 分支到达/最终状态证据 |
| --- | --- | --- |
| [AcceptanceTest.java.t01_loginWithCorrectCredentialsGivesUsableToken](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L35) | 未读 | 待填写 |
| [AcceptanceTest.java.t02_wrongPasswordDisabledAndDeletedAccountsCannotLogin](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L44) | 未读 | 待填写 |
| [AcceptanceTest.java.t17_tokenIsRejectedAfterLogout](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L440) | 未读 | 待填写 |
| [ManagementApiTest.java.firstLoginMustChangePasswordAndOldTokensDie](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L26) | 未读 | 待填写 |
| [ManagementApiTest.java.fiveFailuresLockTheAccountEvenWhenConcurrent](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L45) | 未读 | 待填写 |
| [ManagementApiTest.java.invalidTokensAreRejected](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L73) | 未读 | 待填写 |
| [ManagementApiTest.java.undeclaredRoutesAreDeniedByDefault](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L630) | 未读 | 待填写 |
| [RedisFailureTest.java.revocationLookupFailsClosed](../../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java#L59) | 未读 | 待填写 |
| [RedisFailureTest.java.revocationLookupRetriesOnceBeforeFailingClosed](../../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java#L66) | 未读 | 待填写 |
| [RedisFailureTest.java.revocationOfExpiredTokenIsNotStored](../../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java#L74) | 未读 | 待填写 |

## 交付检查

- [ ] 文件和关键方法已读并留证据。
- [ ] 全部API卡填写独立预期、间接调用、最终查询及副作用。
- [ ] 业务测试核对关键断言与拒绝原因，未执行明确记录。
- [ ] 问题分类完成，重要结论由另一人挑战。

返回[总入口](README.md)，使用[模板](templates.md)与[复核记录](cross-review.md)。
