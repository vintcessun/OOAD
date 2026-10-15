# C 用户编号与角色指派

主审姓名：待填写；交叉复核姓名：待填写；日期：待填写；阅读与复现状态：待完成。

预计16小时，负责13个权限中心API。基线提交：a42b8018d6a9c9051fd527dcae05a8da04746082。

## 阅读顺序

用户页面/指派弹窗/个人中心 → userApi/assignmentApi → UserController/RoleController批量入口 → UserManagementService/AssignmentService → 用户与指派表 → 版本/审计 → 页面与身份刷新。

编号链必须串读V3 → User.INTERNAL_ID_LIMIT → create/insertInternal → nextInternalId/原生插入。与A复核个人中心，与B核对上界与失效，与E/F核对迁移、导入、自增重置。

## 主审文件与代码范围

状态由实际审查者填写。完整阅读指负责范围内的业务代码；依赖锁文件、模型和工作簿按范围栏核对。共享文件仅承担本人片段。

| 文件 | 层级 | 重点代码/范围 | 关联人 | 阅读状态 | 证据 |
| --- | --- | --- | --- | --- | --- |
| [rbac-contract/src/main/resources/db/migration/V3__reserve_internal_user_ids.sql](../../rbac-contract/src/main/resources/db/migration/V3__reserve_internal_user_ids.sql) | 数据库迁移 | 内部1—9999及员工/临时用户10000起；已有库迁移E；测试重置F | E/F | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java) | 入口与响应 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/AssignmentRow.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/AssignmentRow.java) | 输入输出 | 指派投影C；授权展开B；角色持有者D；重点：完整阅读参数、字段、调用方及状态变化 | B/D | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/BatchDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/BatchDtos.java) | 输入输出 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/PageResult.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/PageResult.java) | 输入输出 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/UserDtos.java) | 输入输出 | 用户输入输出C；当前用户身份A；重点：完整阅读参数、字段、调用方及状态变化 | A | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/User.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/User.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/UserRole.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/UserRole.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java) | 查询与持久化 | 用户与内部编号C；登录查询/失败计数/重置A；导入定位E；重点：完整阅读参数、字段、调用方及状态变化 | A/E | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java) | 查询与持久化 | 指派写入/删除/批量与有效SA计数C；授权展开B；持有者查询D；重点：完整阅读参数、字段、调用方及状态变化 | B/D | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java) | 业务与事务 | assign/revoke、batchAssign/batchRevoke、context/checkOne/checkRevoke、escalation、resolveTargets、chunks | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java) | 业务与事务 | 用户与编号C；当前用户detail关联A；初始口令与导入关联E；重点：list/buildFilter/deptSubquery、detail、create/insertInternal、validateTypeFields、update、applyStatus、requireManageable | A/E | 未读 | 待填写 |
| [rbac-web/src/components/AssignRoleDialog.vue](../../rbac-web/src/components/AssignRoleDialog.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/components/BatchAssignDialog.vue](../../rbac-web/src/components/BatchAssignDialog.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/components/BatchResultView.vue](../../rbac-web/src/components/BatchResultView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/components/UserDetailPanel.vue](../../rbac-web/src/components/UserDetailPanel.vue) | 前端交互 | 用户详情与自我撤权C；个人中心和身份刷新A | A | 未读 | 待填写 |
| [rbac-web/src/components/UserFormDialog.vue](../../rbac-web/src/components/UserFormDialog.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/UserDetailView.vue](../../rbac-web/src/views/UserDetailView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/UserListView.vue](../../rbac-web/src/views/UserListView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |

## 必须关联阅读

| 文件 | 主审 | 关联范围 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| [diagrams/README.md](../../diagrams/README.md) | E | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | 未读 | 待填写 |
| [diagrams/rbac.mdj](../../diagrams/rbac.mdj) | E | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | 未读 | 待填写 |
| [diagrams/src/domain-model.mmd](../../diagrams/src/domain-model.mmd) | E | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | 未读 | 待填写 |
| [diagrams/src/er-diagram.mmd](../../diagrams/src/er-diagram.mmd) | E | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | 未读 | 待填写 |
| [diagrams/src/usecase-overall.mmd](../../diagrams/src/usecase-overall.mmd) | E | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | 未读 | 待填写 |
| [diagrams/tools/gen-mdj.mjs](../../diagrams/tools/gen-mdj.mjs) | E | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | 未读 | 待填写 |
| [diagrams/tools/mermaid-config.json](../../diagrams/tools/mermaid-config.json) | E | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | 未读 | 待填写 |
| [diagrams/tools/puppeteer-config.json](../../diagrams/tools/puppeteer-config.json) | E | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | 未读 | 待填写 |
| [rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql](../../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql) | E | DDL/FK/唯一键/索引及初始化E；各业务人复核对应表和初始化 | 未读 | 待填写 |
| [rbac-contract/src/main/resources/openapi/rbac-api.yaml](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml) | F | 契约结构与公共schema；各负责人逐API核对权限、参数及响应 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java) | D | 角色与角色权限D；batchAssign/batchRevoke C；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java) | D | 手工维护D；导入同步E；指派授权上界C；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java) | F | 独立审计事务及事件F；各业务人复核实际调用时机与内容；重点：record、recordDecision、search、REQUIRES_NEW、truncate | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java) | B | 判定、范围与失效B；C/D/E复核各自调用；审计副作用F；重点：decide、subjectDenyReason、covers、loadGrants、scopeOf、requireInScope、bumpGrantVersion | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java) | F | 公共测试结构F；具体用例按test-matrix逐方法分配，不要求F重读各业务断言 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java) | E | 四个导入测试E；B失效、C编号归属、D模板同步、F环境 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java) | F | 隔离环境、快照和辅助方法；C/E复核V3自增起点恢复 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java) | F | 公共测试结构F；各业务人按test-matrix主审断言及目标分支 | 未读 | 待填写 |
| [rbac-structured/src/test/resources/application-test.yml](../../rbac-structured/src/test/resources/application-test.yml) | F | 测试数据库及Redis隔离；身份、版本、编号测试环境 | 未读 | 待填写 |
| [rbac-web/e2e/console.spec.ts](../../rbac-web/e2e/console.spec.ts) | F | 隔离运行与执行顺序F；每一步业务预期归对应负责人 | 未读 | 待填写 |
| [rbac-web/e2e/fixtures/departments.xlsx](../../rbac-web/e2e/fixtures/departments.xlsx) | E | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | 未读 | 待填写 |
| [rbac-web/e2e/fixtures/employees.xlsx](../../rbac-web/e2e/fixtures/employees.xlsx) | E | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | 未读 | 待填写 |
| [rbac-web/e2e/fixtures/generate.py](../../rbac-web/e2e/fixtures/generate.py) | E | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | 未读 | 待填写 |
| [rbac-web/e2e/fixtures/permissions.xlsx](../../rbac-web/e2e/fixtures/permissions.xlsx) | E | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | 未读 | 待填写 |
| [rbac-web/e2e/global-setup.ts](../../rbac-web/e2e/global-setup.ts) | F | 隔离运行与执行顺序F；每一步业务预期归对应负责人 | 未读 | 待填写 |
| [rbac-web/src/api/index.ts](../../rbac-web/src/api/index.ts) | F | A auth；B authz；C user/assignment；D role/catalog；E org/import；F menu/stats与公共部分 | 未读 | 待填写 |
| [rbac-web/src/api/types.ts](../../rbac-web/src/api/types.ts) | F | A auth；B authz；C user/assignment；D role/catalog；E org/import；F menu/stats与公共部分 | 未读 | 待填写 |
| [rbac-web/tests/components.spec.ts](../../rbac-web/tests/components.spec.ts) | F | 公共逻辑与环境主审；组件业务断言由各业务负责人按任务书认领 | 未读 | 待填写 |

## API责任

| 卡号 | API | 功能 | 状态 |
| --- | --- | --- | --- |
| [C01](api-cards/C.md#c01) | GET /users | 用户列表与搜索（只返回管理范围内的用户） | 未审查 |
| [C02](api-cards/C.md#c02) | POST /users | 新增用户（正式员工、临时用户或业务系统账号） | 未审查 |
| [C03](api-cards/C.md#c03) | POST /users/batch-status | 批量启用 / 停用 / 删除 | 未审查 |
| [C04](api-cards/C.md#c04) | GET /users/{id} | 用户详情：基本信息、角色、有效权限 | 未审查 |
| [C05](api-cards/C.md#c05) | PATCH /users/{id} | 修改用户（姓名、部门、电话、岗位、到期时间） | 未审查 |
| [C06](api-cards/C.md#c06) | DELETE /users/{id} | 删除用户（逻辑删除，用户名不可复用，指派保留供审计） | 未审查 |
| [C07](api-cards/C.md#c07) | PATCH /users/{id}/status | 启用或停用 | 未审查 |
| [C08](api-cards/C.md#c08) | GET /users/{id}/roles | 用户的角色指派 | 未审查 |
| [C09](api-cards/C.md#c09) | POST /users/{id}/roles | 为用户分配角色（ROLE_ASSIGN） | 未审查 |
| [C10](api-cards/C.md#c10) | DELETE /users/{id}/roles/{assignmentId} | 回收一次角色指派（ROLE_REVOKE） | 未审查 |
| [C11](api-cards/C.md#c11) | GET /users/{id}/permissions | 用户的有效权限（每条带来源角色与适用部门） | 未审查 |
| [C12](api-cards/C.md#c12) | POST /assignments/batch | 按分公司 / 部门 / 员工批量分配角色 | 未审查 |
| [C13](api-cards/C.md#c13) | POST /assignments/batch-revoke | 按分公司 / 部门 / 员工批量回收角色 | 未审查 |

## 人工反例清单

- [ ] 内部最大ID+1并发冲突时，事务回滚、错误码与重试是否合理？
- [ ] 号段耗尽、逻辑删除、已有库升级、手工新增与导入是否保持编号规则？
- [ ] 用户ID、用户类型和SA角色是否混淆；员工成为SA后是否仍合法？
- [ ] 自我撤权免范围检查但入口要求撤权权限，是否符合业务预期和页面说明？
- [ ] 单个/批量自我撤权、最后SA保护与回滚是否一致？
- [ ] 目标用户所属部门与指派适用部门是否都检查？
- [ ] 调岗、停用、删除、重新启用后旧指派应怎样生效？
- [ ] ATOMIC/PARTIAL、重复目标、1000/1001及并发重复提交如何处理？
- [ ] 筛选/分页/勾选/详情请求乱序会不会操作错人？

## 对应测试方法

以下为断言审查责任，不代表测试已运行；跨业务测试由相关人复核自己的片段。

| 测试 | 状态 | 分支到达/最终状态证据 |
| --- | --- | --- |
| [AcceptanceTest.java.t03_temporaryUserHasNoEmployeeInfoButExpires](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L69) | 未读 | 待填写 |
| [AcceptanceTest.java.t08_adminAssignsRoleToSubordinate](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L165) | 未读 | 待填写 |
| [AcceptanceTest.java.t09_adminCannotGrantToAnotherBranchAndItIsAudited](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L176) | 未读 | 待填写 |
| [AcceptanceTest.java.t10_adminCannotGrantRoleWithPermissionsTheyLack](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L197) | 未读 | 待填写 |
| [AcceptanceTest.java.t10b_selfOnlyGrantDoesNotLetAdminGrantTheFullPermission](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L216) | 未读 | 待填写 |
| [AcceptanceTest.java.t14_removingAnAdminDoesNotCascadeToWhatTheyGranted](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L334) | 未读 | 待填写 |
| [AcceptanceTest.java.t14b_adminCanRevokeTheirOwnAssignments](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L356) | 未读 | 待填写 |
| [AcceptanceTest.java.t15_duplicateAssignmentCreatesNoSecondRecord](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L387) | 未读 | 待填写 |
| [AcceptanceTest.java.t16_batchWithOutOfScopeUsersFollowsTheDeclaredMode](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L402) | 未读 | 待填写 |
| [ManagementApiTest.java.userCrudWithinManagementScope](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L88) | 未读 | 待填写 |
| [ManagementApiTest.java.temporaryUserCannotGetAPhoneLater](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L163) | 未读 | 待填写 |
| [ManagementApiTest.java.internalAccountsUseTheReservedIdRange](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L175) | 未读 | 待填写 |
| [ManagementApiTest.java.batchStatusReportsPerUserResults](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L206) | 未读 | 待填写 |
| [ManagementApiTest.java.onlySuperAdminMayTouchASuperAdmin](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L225) | 未读 | 待填写 |
| [ManagementApiTest.java.userDetailShowsRolesAndEffectivePermissions](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L244) | 未读 | 待填写 |
| [ManagementApiTest.java.batchAssignByCompanyAndDepartmentThenRevoke](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L359) | 未读 | 待填写 |
| [ManagementApiTest.java.outOfScopeRevokeAndDisabledRoleAreRefused](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L409) | 未读 | 待填写 |

## 交付检查

- [ ] 文件和关键方法已读并留证据。
- [ ] 全部API卡填写独立预期、间接调用、最终查询及副作用。
- [ ] 业务测试核对关键断言与拒绝原因，未执行明确记录。
- [ ] 问题分类完成，重要结论由另一人挑战。

返回[总入口](README.md)，使用[模板](templates.md)与[复核记录](cross-review.md)。
