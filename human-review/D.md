# D 角色权限与权限目录

主审姓名：待填写；交叉复核姓名：待填写；日期：待填写；阅读与复现状态：待完成。

预计16小时，负责14个权限中心API。基线提交：a42b8018d6a9c9051fd527dcae05a8da04746082。

## 阅读顺序

角色/权限目录页面 → roleApi/catalogApi → RoleController/CatalogController目录 → RoleManagementService/CatalogService → 角色权限、资源操作表 → 全部持有者影响与前端反馈。

关联UserRoleMapper持有者查询、B判定上界、E权限矩阵同步。核对父/权限中心/契约POM；前端依赖版本、来源与锁文件一致性，传递依赖按异常差异抽查。

## 主审文件与代码范围

状态由实际审查者填写。完整阅读指负责范围内的业务代码；依赖锁文件、模型和工作簿按范围栏核对。共享文件仅承担本人片段。

| 文件 | 层级 | 重点代码/范围 | 关联人 | 阅读状态 | 证据 |
| --- | --- | --- | --- | --- | --- |
| [pom.xml](../../pom.xml) | 构建与运行配置 | 权限中心/契约依赖及版本锁定；传递依赖按异常差异抽查；不检查外部模拟模块功能 | F | 未读 | 待填写 |
| [rbac-contract/pom.xml](../../rbac-contract/pom.xml) | 构建与运行配置 | 权限中心/契约依赖及版本锁定；传递依赖按异常差异抽查；不检查外部模拟模块功能 | F | 未读 | 待填写 |
| [rbac-structured/pom.xml](../../rbac-structured/pom.xml) | 构建与运行配置 | 权限中心/契约依赖及版本锁定；传递依赖按异常差异抽查；不检查外部模拟模块功能 | F | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java) | 入口与响应 | 角色与角色权限D；batchAssign/batchRevoke C；重点：完整阅读参数、字段、调用方及状态变化 | C | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java) | 输入输出 | 目录D；组织E；菜单F；重点：完整阅读参数、字段、调用方及状态变化 | E/F | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/RoleDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/RoleDtos.java) | 输入输出 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Action.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Action.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/FunctionModule.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/FunctionModule.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Permission.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Permission.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Resource.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Resource.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Role.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Role.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/RolePermission.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/RolePermission.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/SoftwareSystem.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/SoftwareSystem.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ActionMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ActionMapper.java) | 查询与持久化 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/FunctionModuleMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/FunctionModuleMapper.java) | 查询与持久化 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java) | 查询与持久化 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java) | 查询与持久化 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java) | 查询与持久化 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java) | 查询与持久化 | 手工维护D；导入同步E；指派授权上界C；重点：完整阅读参数、字段、调用方及状态变化 | C/E | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/SoftwareSystemMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/SoftwareSystemMapper.java) | 查询与持久化 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java) | 业务与事务 | 目录查询、createResource、createPermission、addPermission、分页参数 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java) | 业务与事务 | list/detail/users、create/update/delete、grant/ungrant、requireCompanyLevel、firstNotHeld | 按问题安排 | 未读 | 待填写 |
| [rbac-web/package-lock.json](../../rbac-web/package-lock.json) | 构建与运行配置 | 权限中心/契约依赖及版本锁定；传递依赖按异常差异抽查；不检查外部模拟模块功能 | F | 未读 | 待填写 |
| [rbac-web/package.json](../../rbac-web/package.json) | 构建与运行配置 | 权限中心/契约依赖及版本锁定；传递依赖按异常差异抽查；不检查外部模拟模块功能 | F | 未读 | 待填写 |
| [rbac-web/src/components/GrantPermissionDialog.vue](../../rbac-web/src/components/GrantPermissionDialog.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/components/PermissionCode.vue](../../rbac-web/src/components/PermissionCode.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/PermissionView.vue](../../rbac-web/src/views/PermissionView.vue) | 前端交互 | 权限目录D；清单导入交互E | E | 未读 | 待填写 |
| [rbac-web/src/views/RoleDetailView.vue](../../rbac-web/src/views/RoleDetailView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/RoleListView.vue](../../rbac-web/src/views/RoleListView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |

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
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java) | E | 组织与导入E；系统资源操作权限D；菜单F；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/AssignmentRow.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/AssignmentRow.java) | C | 指派投影C；授权展开B；角色持有者D；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java) | C | 指派写入/删除/批量与有效SA计数C；授权展开B；持有者查询D；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java) | F | 独立审计事务及事件F；各业务人复核实际调用时机与内容；重点：record、recordDecision、search、REQUIRES_NEW、truncate | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java) | B | 判定、范围与失效B；C/D/E复核各自调用；审计副作用F；重点：decide、subjectDenyReason、covers、loadGrants、scopeOf、requireInScope、bumpGrantVersion | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java) | F | 公共测试结构F；具体用例按test-matrix逐方法分配，不要求F重读各业务断言 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java) | E | 四个导入测试E；B失效、C编号归属、D模板同步、F环境 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java) | F | 隔离环境、快照和辅助方法；C/E复核V3自增起点恢复 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java) | F | 公共测试结构F；各业务人按test-matrix主审断言及目标分支 | 未读 | 待填写 |
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
| [D01](api-cards/D.md#d01) | GET /roles | 角色列表：名称、编码、用户数、权限数、状态 | 未审查 |
| [D02](api-cards/D.md#d02) | POST /roles | 新增角色 | 未审查 |
| [D03](api-cards/D.md#d03) | GET /roles/{id} | 角色详情：基本信息与权限 | 未审查 |
| [D04](api-cards/D.md#d04) | PATCH /roles/{id} | 修改角色名称、描述、启用停用 | 未审查 |
| [D05](api-cards/D.md#d05) | DELETE /roles/{id} | 删除角色（内置角色不能删；仍有人持有时 409） | 未审查 |
| [D06](api-cards/D.md#d06) | GET /roles/{id}/users | 角色详情「用户」：持有该角色的指派 | 未审查 |
| [D07](api-cards/D.md#d07) | POST /roles/{id}/permissions | 为角色授予权限（PERMISSION_ASSIGN） | 未审查 |
| [D08](api-cards/D.md#d08) | DELETE /roles/{id}/permissions/{permissionId} | 回收角色的一个权限（PERMISSION_REVOKE） | 未审查 |
| [D09](api-cards/D.md#d09) | GET /systems | 业务系统与功能模块（权限域） | 未审查 |
| [D10](api-cards/D.md#d10) | GET /resources | 资源列表（BUSINESS 业务功能点 / MENU 菜单 / API 接口能力） | 未审查 |
| [D11](api-cards/D.md#d11) | POST /resources | 新增资源，并为给出的操作建立权限 | 未审查 |
| [D12](api-cards/D.md#d12) | GET /actions | 操作词汇表 | 未审查 |
| [D13](api-cards/D.md#d13) | GET /permissions | 权限列表（Permission = Resource × Action） | 未审查 |
| [D14](api-cards/D.md#d14) | POST /permissions | 为已有资源新增一个操作的权限 | 未审查 |

## 人工反例清单

- [ ] 修改共享角色与给个人指派角色是否明确区分影响面？
- [ ] 全局角色、公司角色、指派部门是否混淆？
- [ ] 自己没有某业务权限但能回收角色中的该权限，是否符合团队规则？
- [ ] 角色列表、持有者列表、总数与分页是否都受管理范围约束？
- [ ] 停用、删除、授权、撤权是否及时影响全部持有者？
- [ ] 并发创建、重复授权及中途失败是否由唯一键和事务正确兜底？
- [ ] 拒绝审计是否位于正确分支，原因码与HTTP结果是否匹配？

## 对应测试方法

以下为断言审查责任，不代表测试已运行；跨业务测试由相关人复核自己的片段。

| 测试 | 状态 | 分支到达/最终状态证据 |
| --- | --- | --- |
| [AcceptanceTest.java.t13_changingRolePermissionsAffectsAllHoldersOnTheNextRequest](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L311) | 未读 | 待填写 |
| [ManagementApiTest.java.roleLifecycleAndPermissions](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L264) | 未读 | 待填写 |
| [ManagementApiTest.java.roleHoldersAreLimitedToManagementScopeWithConsistentTotals](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L313) | 未读 | 待填写 |
| [ManagementApiTest.java.companyAdminManagesOnlyCompanyRoles](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L329) | 未读 | 待填写 |
| [ManagementApiTest.java.catalogResourcesActionsPermissions](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L431) | 未读 | 待填写 |

## 交付检查

- [ ] 文件和关键方法已读并留证据。
- [ ] 全部API卡填写独立预期、间接调用、最终查询及副作用。
- [ ] 业务测试核对关键断言与拒绝原因，未执行明确记录。
- [ ] 问题分类完成，重要结论由另一人挑战。

返回[总入口](README.md)，使用[模板](templates.md)与[复核记录](cross-review.md)。
