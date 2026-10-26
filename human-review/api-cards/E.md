# E API业务链路卡

静态定位和 E 范围的部分代码证据已由 AI 辅助补充；主审本人、第二人仍需核对，未执行测试的条目不代表行为通过。每张卡仍要追踪共同拦截器、异常响应和契约 schema。API 封装存在不代表有页面调用。

[返回任务书](../E.md) · [模板](../templates.md)

<a id="e01"></a>
<!-- api: GET /companies -->
## E01 公司列表（只含管理范围涉及的公司）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | E；2026-10-26 已读（AI 辅助静态链路）；主审刘翔宇待确认，复核人待填写；未实测 |
| API与功能 | GET /companies；公司列表（只含管理范围涉及的公司） |
| 契约 | [listCompanies](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L923)；权限声明：rbac:org:view |
| 前端封装与调用方 | [orgApi.companies](../../rbac-web/src/api/index.ts)；[BatchAssignDialog.vue](../../rbac-web/src/components/BatchAssignDialog.vue)；[DeptTreeSelect.vue](../../rbac-web/src/components/DeptTreeSelect.vue)；[OrgView.vue](../../rbac-web/src/views/OrgView.vue)；[RoleListView.vue](../../rbac-web/src/views/RoleListView.vue) |
| Controller | [CatalogController.companies](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L128) |
| 直接Service入口 | [OrgService.companies](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java#L39) |
| 入口方法中的直接Mapper调用 | [CompanyMapper.findAll](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java)；[DepartmentMapper.findByCompanyIdAndParentIdIsNull](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java) |
| 共享Service调用线索 | AuthorizationService.scopeOf |
| 独立业务预期及依据 | `rbac-api.yaml:923-932`：仅返回有根部门且操作者的 `rbac:org:view` 范围涉及的公司；SA 可看全部有根部门公司。缺身份/权限由共同拦截器拒绝，待运行核对。 |
| 调用者及对象 | 待填；区分调用者、被查用户、被操作用户及指派/角色/资源 |
| 范围与记录依据 | 待填；公司、用户所属部门、角色所属公司、指派适用部门、selfOnly分别说明 |
| 完整间接调用及最终查询 | `CatalogController:128-130` → `OrgService:39-53` → `scopeOf`、`CompanyMapper.findAll`、`DepartmentMapper.findByCompanyIdAndParentIdIsNull`；按根路径筛选，不分页。共同拦截器及 `scopeOf` 内部查询待复核。 |
| 事务与最终状态 | `OrgService.companies` 为 `@Transactional(readOnly=true)`；只读查询，不写业务表、不更新授权版本。 |
| 版本缓存审计与影响面 | 待填；版本更新、缓存读写、审计独立事务、其他持有者及页面刷新 |
| 反例时序与边界 | 待填；本人/同级/SA、跨部门/公司、调岗、失效、重复、失败点、T1/T2执行顺序 |
| 对应测试与断言 | 从任务书和测试矩阵定位；填写实际方法、原因码、分支到达、最终表状态及范围外未变断言 |
| 执行证据 | 静态核对提交 `c3de993`、2026-10-26；未执行测试，无 HTTP 响应或覆盖率证据。详见 [E 记录](../problems/E/E-review-20261026.md)。 |
| 结论与复核 | 静态调用链已核对；运行行为、主审确认及第二人复核待完成。 |


<a id="e02"></a>
<!-- api: GET /departments/tree -->
## E02 部门树（管理范围内的部门及通往它们的祖先）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | E；2026-10-26 已读（AI 辅助静态链路）；主审刘翔宇待确认，复核人待填写；未实测 |
| API与功能 | GET /departments/tree；部门树（管理范围内的部门及通往它们的祖先） |
| 契约 | [getDepartmentTree](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L935)；权限声明：rbac:org:view |
| 前端封装与调用方 | [orgApi.tree](../../rbac-web/src/api/index.ts)；[DeptTreeSelect.vue](../../rbac-web/src/components/DeptTreeSelect.vue)；[OrgView.vue](../../rbac-web/src/views/OrgView.vue) |
| Controller | [CatalogController.departmentTree](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L133) |
| 直接Service入口 | [OrgService.tree](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java#L56) |
| 入口方法中的直接Mapper调用 | [CompanyMapper.existsById](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java)；[DepartmentMapper.findByCompanyIdOrderByLevelAscSortOrderAscIdAsc](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java) |
| 共享Service调用线索 | AuthorizationService.scopeOf |
| 独立业务预期及依据 | `rbac-api.yaml:934-947`：公司不存在 404；范围内部门及通往它们的祖先可见，祖先的其他分支不可见；无可见部门返回空数组。 |
| 调用者及对象 | 待填；区分调用者、被查用户、被操作用户及指派/角色/资源 |
| 范围与记录依据 | 待填；公司、用户所属部门、角色所属公司、指派适用部门、selfOnly分别说明 |
| 完整间接调用及最终查询 | `CatalogController:133-136` → `OrgService:56-85` → `CompanyMapper.existsById`、`AuthorizationService.scopeOf/inScope`、`DepartmentMapper.findByCompanyIdOrderByLevelAscSortOrderAscIdAsc`；按路径过滤并构树，不分页。 |
| 事务与最终状态 | `OrgService.tree` 为 `@Transactional(readOnly=true)`；只读查询，不写业务表、不更新授权版本。 |
| 版本缓存审计与影响面 | 待填；版本更新、缓存读写、审计独立事务、其他持有者及页面刷新 |
| 反例时序与边界 | 待填；本人/同级/SA、跨部门/公司、调岗、失效、重复、失败点、T1/T2执行顺序 |
| 对应测试与断言 | 从任务书和测试矩阵定位；填写实际方法、原因码、分支到达、最终表状态及范围外未变断言 |
| 执行证据 | 静态核对提交 `c3de993`、2026-10-26；未执行测试，尤其缺跨公司及祖先/兄弟分支实际响应。详见 [E 记录](../problems/E/E-review-20261026.md)。 |
| 结论与复核 | 静态调用链已核对；运行行为、主审确认及第二人复核待完成。 |


<a id="e03"></a>
<!-- api: POST /imports/organization -->
## E03 导入部门信息表与员工信息表

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | E；2026-10-26 已读（AI 辅助静态链路）；主审刘翔宇待确认，复核人待填写；HR-002/003 待实测 |
| API与功能 | POST /imports/organization；导入部门信息表与员工信息表 |
| 契约 | [importOrganization](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L950)；权限声明：rbac:org:import |
| 前端封装与调用方 | [orgApi.importOrganization](../../rbac-web/src/api/index.ts)；[OrgView.vue](../../rbac-web/src/views/OrgView.vue) |
| Controller | [CatalogController.importOrganization](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L139) |
| 直接Service入口 | [ImportService.importOrganization](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L140) |
| 入口方法中的直接Mapper调用 | [CompanyMapper.findByCompanyCode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java)；[CompanyMapper.save](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java) |
| 共享Service调用线索 | AuditService.record |
| 独立业务预期及依据 | `rbac-api.yaml:949-974`：有效部门和员工导入，异常行报告应指向原始工作簿位置，负责人只作组织信息；重复导入不新增同一员工，应正确处理负责人变更；绝不自动分配角色。 |
| 调用者及对象 | 待填；区分调用者、被查用户、被操作用户及指派/角色/资源 |
| 范围与记录依据 | 待填；公司、用户所属部门、角色所属公司、指派适用部门、selfOnly分别说明 |
| 完整间接调用及最终查询 | `CatalogController:139-149` → `ImportService:139-169` → `readSheet`、`importDepartments`、`importEmployees`、`linkManagers` → `CompanyMapper/DepartmentMapper/UserMapper`；报告 `counts/notes/anomalies`。行号与已有负责人见 HR-002/003。 |
| 事务与最终状态 | `importOrganization` 标注 `@Transactional`；异常数据行累积为报告，合法行继续处理；运行中的提交/回滚与独立审计最终状态待验证。 |
| 版本缓存审计与影响面 | 待填；版本更新、缓存读写、审计独立事务、其他持有者及页面刷新 |
| 反例时序与边界 | 待填；本人/同级/SA、跨部门/公司、调岗、失效、重复、失败点、T1/T2执行顺序 |
| 对应测试与断言 | 从任务书和测试矩阵定位；填写实际方法、原因码、分支到达、最终表状态及范围外未变断言 |
| 执行证据 | 静态核对提交 `c3de993`、2026-10-26；现有 `ExcelImportIntegrationTest:33-109` 已读断言但未运行；标题行/空行与已有员工负责人变更均未覆盖。详见 [E 记录](../problems/E/E-review-20261026.md)。 |
| 结论与复核 | HR-002/003 为静态路径证据，待隔离环境复现、主审确认和第二人复核。 |


<a id="e04"></a>
<!-- api: POST /imports/permissions -->
## E04 导入应用软件功能权限清单（业务系统、模块、资源、权限、五类角色模板）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | E；2026-10-26 已读（AI 辅助静态链路）；主审刘翔宇待确认，复核人待填写；HR-004 规则与测试待确认 |
| API与功能 | POST /imports/permissions；导入应用软件功能权限清单（业务系统、模块、资源、权限、五类角色模板） |
| 契约 | [importPermissions](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L977)；权限声明：rbac:permission:create |
| 前端封装与调用方 | [orgApi.importPermissions](../../rbac-web/src/api/index.ts)；[OrgView.vue](../../rbac-web/src/views/OrgView.vue) |
| Controller | [CatalogController.importPermissions](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L152) |
| 直接Service入口 | [ImportService.importPermissions](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L369) |
| 入口方法中的直接Mapper调用 | [ActionMapper.findByActionCode](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ActionMapper.java)；[PermissionMapper.findByResourceId](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java)；[PermissionMapper.save](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java)；[RolePermissionMapper.findByRoleIdAndPermissionId](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java)；[RolePermissionMapper.save](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java) |
| 共享Service调用线索 | AuditService.record；AuthorizationService.bumpGrantVersion |
| 独立业务预期及依据 | `rbac-api.yaml:976-998`：导入与清单一致的五类模板授权，重复导入应回收去掉的业务授权、更新 selfOnly，但不能把一个业务功能点错认成另一个；异常单元格保留原授权。 |
| 调用者及对象 | 待填；区分调用者、被查用户、被操作用户及指派/角色/资源 |
| 范围与记录依据 | 待填；公司、用户所属部门、角色所属公司、指派适用部门、selfOnly分别说明 |
| 完整间接调用及最终查询 | `CatalogController:152-158` → `ImportService:368-535` → `system/module/resource/parseCell/revokeMissing` → 系统、模块、资源、权限和角色权限 Mapper；`:531` 更新授权版本。资源认领见 HR-004。 |
| 事务与最终状态 | `importPermissions` 标注 `@Transactional`；业务权限写入/回收及 `bumpGrantVersion` 位于方法内；授权版本、独立审计及缓存下一次请求的实际最终状态待运行核对。 |
| 版本缓存审计与影响面 | 待填；版本更新、缓存读写、审计独立事务、其他持有者及页面刷新 |
| 反例时序与边界 | 待填；本人/同级/SA、跨部门/公司、调岗、失效、重复、失败点、T1/T2执行顺序 |
| 对应测试与断言 | 从任务书和测试矩阵定位；填写实际方法、原因码、分支到达、最终表状态及范围外未变断言 |
| 执行证据 | 静态核对提交 `c3de993`、2026-10-26；现有 `ExcelImportIntegrationTest:111-259` 已读断言但未运行；同名换序反例缺失。详见 [E 记录](../problems/E/E-review-20261026.md)。 |
| 结论与复核 | HR-004 先确认同名业务规则并补反例；主审与第二人签字待完成。 |
