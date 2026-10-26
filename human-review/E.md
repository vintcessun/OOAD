# E 组织导入与数据库模型

主审姓名：刘翔宇；交叉复核姓名：待填写；日期：待填写；阅读状态：部分已读（AI 辅助）；实测与人工复核未完成，主审本人及复核人尚未签字。核对记录见 [E 的代码证据](problems/E/E-review-20261026.md)。

预计16小时，负责4个权限中心API。基线提交：a42b8018d6a9c9051fd527dcae05a8da04746082。

## 阅读顺序

组织页/权限导入 → orgApi → CatalogController → OrgService/ImportService → 公司部门、人员、资源与角色权限表 → 报告、版本更新 → 异常清单。

关联C用户编号、D角色权限Mapper、B版本失效；V1整体结构主审，V2复核B、V3复核C。核对设计模型及DDL，导出图只抽查一致性。用虚构最小数据记录复现，不复制真实人员信息。

## 主审文件与代码范围

状态由实际审查者填写。完整阅读指负责范围内的业务代码；依赖锁文件、模型和工作簿按范围栏核对。共享文件仅承担本人片段。

| 文件 | 层级 | 重点代码/范围 | 关联人 | 阅读状态 | 证据 |
| --- | --- | --- | --- | --- | --- |
| [diagrams/README.md](../diagrams/README.md) | 设计模型 | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | C/D | 未读 | 待填写 |
| [diagrams/rbac.mdj](../diagrams/rbac.mdj) | 设计模型 | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | C/D | 已读（关键字段范围）；其余未读 | `rbac.mdj:4746,5347` 仍为 V1+V2 且含 `expires_at`；见 E 记录 E-04 |
| [diagrams/src/domain-model.mmd](../diagrams/src/domain-model.mmd) | 设计模型 | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | C/D | 未读 | 待填写 |
| [diagrams/src/er-diagram.mmd](../diagrams/src/er-diagram.mmd) | 设计模型 | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | C/D | 未读 | 待填写 |
| [diagrams/src/usecase-overall.mmd](../diagrams/src/usecase-overall.mmd) | 设计模型 | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | C/D | 未读 | 待填写 |
| [diagrams/tools/gen-mdj.mjs](../diagrams/tools/gen-mdj.mjs) | 设计模型 | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | C/D | 未读 | 待填写 |
| [diagrams/tools/mermaid-config.json](../diagrams/tools/mermaid-config.json) | 设计模型 | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | C/D | 未读 | 待填写 |
| [diagrams/tools/puppeteer-config.json](../diagrams/tools/puppeteer-config.json) | 设计模型 | 模型与DDL、用例边界一致性；脚本及配置核对模型生成语义 | C/D | 未读 | 待填写 |
| [rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql](../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql) | 数据库迁移 | DDL/FK/唯一键/索引及初始化E；各业务人复核对应表和初始化 | A/B/C/D/F | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java) | 入口与响应 | 组织与导入E；系统资源操作权限D；菜单F；重点：完整阅读参数、字段、调用方及状态变化 | D/F | 已读（E 负责段）；未实测 | `CatalogController:128-158` 四个路由、参数与 Service 转发；见 E01-E04 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/ImportReport.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/ImportReport.java) | 输入输出 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 已读（静态审查）；未实测 | `ImportReport.Anomaly(sheet,row,reason,value)`；行号来源见 E-01 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Company.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Company.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 已读（静态审查）；未实测 | `sys_company` 映射 `id/companyCode/companyName/createdAt`；与 V1 表段对照 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Department.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Department.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 已读（静态审查）；未实测 | `sys_department` 的 `companyId/parentId/path/managerUserId` 与 V1 表段对照 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java) | 查询与持久化 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 已读（静态审查）；未实测 | `JpaRepository` 与 `findByCompanyCode`；E01/E03 调用 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java) | 查询与持久化 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 已读（静态审查）；未实测 | `findByCompanyIdAndParentIdIsNull`、公司树排序查询及 `findSubtree`；E01/E02 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java) | 业务与事务 | importOrganization/importDepartments/importEmployees、locate/linkManagers、importPermissions/revokeMissing/parseCell、roleTemplate/resource/readSheet/findHeaderRow | 按问题安排 | 已读（静态审查）；未实测 | `:139-169,172-356,368-560,570-743`；E-01/E-02/E-03 的路径证据 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java) | 业务与事务 | companies、tree、祖先展示与子树过滤 | 按问题安排 | 已读（静态审查）；未实测 | `companies:39-53`、`tree:56-80`；核对根部门、作用域及祖先可见条件 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java) | 后端测试 | 四个导入测试E；B失效、C编号归属、D模板同步、F环境 | B/C/D/F | 已读（断言）；未运行 | `:33-109,111-162,164-259,261-280`；现有测试不覆盖 E-01/E-02/E-03 反例 |
| [rbac-web/e2e/fixtures/departments.xlsx](../rbac-web/e2e/fixtures/departments.xlsx) | 端到端测试与样本 | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | C/D/F | 未读 | 待填写 |
| [rbac-web/e2e/fixtures/employees.xlsx](../rbac-web/e2e/fixtures/employees.xlsx) | 端到端测试与样本 | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | C/D/F | 未读 | 待填写 |
| [rbac-web/e2e/fixtures/generate.py](../rbac-web/e2e/fixtures/generate.py) | 端到端测试与样本 | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | C/D/F | 未读 | 待填写 |
| [rbac-web/e2e/fixtures/permissions.xlsx](../rbac-web/e2e/fixtures/permissions.xlsx) | 端到端测试与样本 | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | C/D/F | 未读 | 待填写 |
| [rbac-web/src/components/DeptTreeSelect.vue](../rbac-web/src/components/DeptTreeSelect.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/components/ImportReportView.vue](../rbac-web/src/components/ImportReportView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/OrgView.vue](../rbac-web/src/views/OrgView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |

## 必须关联阅读

| 文件 | 主审 | 关联范围 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| [rbac-contract/src/main/resources/db/migration/V2__grant_version.sql](../rbac-contract/src/main/resources/db/migration/V2__grant_version.sql) | B | 授权版本结构及初始化B；迁移E；运行验证F | 未读 | 待填写 |
| [rbac-contract/src/main/resources/db/migration/V3__reserve_internal_user_ids.sql](../rbac-contract/src/main/resources/db/migration/V3__reserve_internal_user_ids.sql) | C | 内部1—9999及员工/临时用户10000起；已有库迁移E；测试重置F | 未读 | 待填写 |
| [rbac-contract/src/main/resources/openapi/rbac-api.yaml](../rbac-contract/src/main/resources/openapi/rbac-api.yaml) | F | 契约结构与公共schema；各负责人逐API核对权限、参数及响应 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java) | D | 目录D；组织E；菜单F；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java) | D | 手工维护D；导入同步E；指派授权上界C；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java) | C | 用户与内部编号C；登录查询/失败计数/重置A；导入定位E；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java) | F | 独立审计事务及事件F；各业务人复核实际调用时机与内容；重点：record、recordDecision、search、REQUIRES_NEW、truncate | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java) | B | 判定、范围与失效B；C/D/E复核各自调用；审计副作用F；重点：decide、subjectDenyReason、covers、loadGrants、scopeOf、requireInScope、bumpGrantVersion | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java) | C | 用户与编号C；当前用户detail关联A；初始口令与导入关联E；重点：list/buildFilter/deptSubquery、detail、create/insertInternal、validateTypeFields、update、applyStatus、requireManageable | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java) | F | 公共测试结构F；具体用例按test-matrix逐方法分配，不要求F重读各业务断言 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java) | F | 隔离环境、快照和辅助方法；C/E复核V3自增起点恢复 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java) | F | 公共测试结构F；各业务人按test-matrix主审断言及目标分支 | 未读 | 待填写 |
| [rbac-structured/src/test/resources/application-test.yml](../rbac-structured/src/test/resources/application-test.yml) | F | 测试数据库及Redis隔离；身份、版本、编号测试环境 | 未读 | 待填写 |
| [rbac-web/e2e/console.spec.ts](../rbac-web/e2e/console.spec.ts) | F | 隔离运行与执行顺序F；每一步业务预期归对应负责人 | 未读 | 待填写 |
| [rbac-web/e2e/global-setup.ts](../rbac-web/e2e/global-setup.ts) | F | 隔离运行与执行顺序F；每一步业务预期归对应负责人 | 未读 | 待填写 |
| [rbac-web/src/api/index.ts](../rbac-web/src/api/index.ts) | F | A auth；B authz；C user/assignment；D role/catalog；E org/import；F menu/stats与公共部分 | 未读 | 待填写 |
| [rbac-web/src/api/types.ts](../rbac-web/src/api/types.ts) | F | A auth；B authz；C user/assignment；D role/catalog；E org/import；F menu/stats与公共部分 | 未读 | 待填写 |
| [rbac-web/src/views/PermissionView.vue](../rbac-web/src/views/PermissionView.vue) | D | 权限目录D；清单导入交互E | 未读 | 待填写 |
| [rbac-web/tests/components.spec.ts](../rbac-web/tests/components.spec.ts) | F | 公共逻辑与环境主审；组件业务断言由各业务负责人按任务书认领 | 未读 | 待填写 |

## API责任

| 卡号 | API | 功能 | 状态 |
| --- | --- | --- | --- |
| [E01](api-cards/E.md#e01) | GET /companies | 公司列表（只含管理范围涉及的公司） | 已读（静态链路）；待实测、待主审确认 |
| [E02](api-cards/E.md#e02) | GET /departments/tree | 部门树（管理范围内的部门及通往它们的祖先） | 已读（静态链路）；待实测、待主审确认 |
| [E03](api-cards/E.md#e03) | POST /imports/organization | 导入部门信息表与员工信息表 | 已读（静态链路）；E-01/E-02 待复现 |
| [E04](api-cards/E.md#e04) | POST /imports/permissions | 导入应用软件功能权限清单（业务系统、模块、资源、权限、五类角色模板） | 已读（静态链路）；E-03 待复现 |

## 人工反例清单

- [ ] 同名部门、直属人员、重名负责人、上级缺失和成环如何处理？
- [ ] 岗位/负责人是否始终只作为组织信息，不隐式授权？
- [ ] 清单插行、删行、换序、改名与重名后编码是否稳定标识同一资源？
- [ ] 未知词、空白、无与仅本人限制是否导致误撤权？
- [ ] 清单与人工维护权限冲突时，优先规则是什么？
- [ ] 逐行异常与系统异常分别允许哪些数据提交？
- [ ] 全新初始化与已有库升级是否保持编号、外键和唯一约束？
- [ ] 异常报告是否准确定位源行并支持人工修正？

## 对应测试方法

以下为断言审查责任，不代表测试已运行；跨业务测试由相关人复核自己的片段。

| 测试 | 状态 | 分支到达/最终状态证据 |
| --- | --- | --- |
| [AcceptanceTest.java.t19_sameNamedDepartmentsInTwoCompaniesStaySeparate](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L472) | 未读 | 待填写 |
| [ExcelImportIntegrationTest.java.t21_importsOrganizationWithCorrectDepartmentOwnership](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java#L33) | 已读（断言）；未运行 | 验证归属、初始口令、无自动角色和重复导入数量；未验证异常原始行号及已有员工负责人改名 |
| [ExcelImportIntegrationTest.java.t21_importsPermissionMatrixWithNormalizationAndAnomalies](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java#L111) | 已读（断言）；未运行 | 验证权限数、空白单元格、selfOnly 与重导数量；未验证同名资源换序 |
| [ExcelImportIntegrationTest.java.reimportingPermissionMatrixRevokesRemovedGrantsAndUpdatesSelfOnly](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java#L164) | 已读（断言）；未运行 | 验证删行回收、selfOnly 更新与缓存失效；测试行名称不同，不能覆盖 E-03 的同名换序 |
| [ExcelImportIntegrationTest.java.rejectsWrongFiles](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java#L261) | 已读（断言）；未运行 | 错误文件、错误表头和普通用户 403；未验证部分提交后的状态 |
| [ManagementApiTest.java.organizationTreeIsLimitedToScope](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L500) | 未读 | 待填写 |

## 交付检查

- [ ] 文件和关键方法已读并留证据。
- [ ] 全部API卡填写独立预期、间接调用、最终查询及副作用。
- [ ] 业务测试核对关键断言与拒绝原因，未执行明确记录。
- [ ] 问题分类完成，重要结论由另一人挑战。

返回[总入口](README.md)，使用[模板](templates.md)与[复核记录](cross-review.md)。
