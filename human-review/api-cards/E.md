# E API业务链路卡

静态定位已填写，独立业务预期、间接调用、最终查询、事务及证据由人工补全。每张卡都要追踪共同拦截器、异常响应和契约schema。API封装存在不代表有页面调用，也不代表行为已验证。

[返回任务书](../E.md) · [模板](../templates.md)

<a id="e01"></a>
<!-- api: GET /companies -->
## E01 公司列表（只含管理范围涉及的公司）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | E；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /companies；公司列表（只含管理范围涉及的公司） |
| 契约 | [listCompanies](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L923)；权限声明：rbac:org:view |
| 前端封装与调用方 | [orgApi.companies](../../../rbac-web/src/api/index.ts)；[BatchAssignDialog.vue](../../../rbac-web/src/components/BatchAssignDialog.vue)；[DeptTreeSelect.vue](../../../rbac-web/src/components/DeptTreeSelect.vue)；[OrgView.vue](../../../rbac-web/src/views/OrgView.vue)；[RoleListView.vue](../../../rbac-web/src/views/RoleListView.vue) |
| Controller | [CatalogController.companies](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L128) |
| 直接Service入口 | [OrgService.companies](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java#L39) |
| 入口方法中的直接Mapper调用 | [CompanyMapper.findAll](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java)；[DepartmentMapper.findByCompanyIdAndParentIdIsNull](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java) |
| 共享Service调用线索 | AuthorizationService.scopeOf |
| 独立业务预期及依据 | 先填；正常、无身份、缺权限、越界、状态冲突、目标不存在、重复、失败；不适用写原因 |
| 调用者及对象 | 待填；区分调用者、被查用户、被操作用户及指派/角色/资源 |
| 范围与记录依据 | 待填；公司、用户所属部门、角色所属公司、指派适用部门、selfOnly分别说明 |
| 完整间接调用及最终查询 | 待填；helper、Mapper方法、SQL/JPQL、表、过滤条件；读接口核对count和分页 |
| 事务与最终状态 | 待填；入口注解/传播、flush/原生SQL、提交或回滚后表状态；无写入说明原因 |
| 版本缓存审计与影响面 | 待填；版本更新、缓存读写、审计独立事务、其他持有者及页面刷新 |
| 反例时序与边界 | 待填；本人/同级/SA、跨部门/公司、调岗、失效、重复、失败点、T1/T2执行顺序 |
| 对应测试与断言 | 从任务书和测试矩阵定位；填写实际方法、原因码、分支到达、最终表状态及范围外未变断言 |
| 执行证据 | 待填；命令、提交、环境、日期、报告、结果；未执行明确注明 |
| 结论与复核 | 待填；通过/缺陷/规则待确认/测试缺口，关联问题编号，主审与第二人分别签字 |


<a id="e02"></a>
<!-- api: GET /departments/tree -->
## E02 部门树（管理范围内的部门及通往它们的祖先）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | E；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /departments/tree；部门树（管理范围内的部门及通往它们的祖先） |
| 契约 | [getDepartmentTree](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L935)；权限声明：rbac:org:view |
| 前端封装与调用方 | [orgApi.tree](../../../rbac-web/src/api/index.ts)；[DeptTreeSelect.vue](../../../rbac-web/src/components/DeptTreeSelect.vue)；[OrgView.vue](../../../rbac-web/src/views/OrgView.vue) |
| Controller | [CatalogController.departmentTree](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L133) |
| 直接Service入口 | [OrgService.tree](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java#L56) |
| 入口方法中的直接Mapper调用 | [CompanyMapper.existsById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java)；[DepartmentMapper.findByCompanyIdOrderByLevelAscSortOrderAscIdAsc](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java) |
| 共享Service调用线索 | AuthorizationService.scopeOf |
| 独立业务预期及依据 | 先填；正常、无身份、缺权限、越界、状态冲突、目标不存在、重复、失败；不适用写原因 |
| 调用者及对象 | 待填；区分调用者、被查用户、被操作用户及指派/角色/资源 |
| 范围与记录依据 | 待填；公司、用户所属部门、角色所属公司、指派适用部门、selfOnly分别说明 |
| 完整间接调用及最终查询 | 待填；helper、Mapper方法、SQL/JPQL、表、过滤条件；读接口核对count和分页 |
| 事务与最终状态 | 待填；入口注解/传播、flush/原生SQL、提交或回滚后表状态；无写入说明原因 |
| 版本缓存审计与影响面 | 待填；版本更新、缓存读写、审计独立事务、其他持有者及页面刷新 |
| 反例时序与边界 | 待填；本人/同级/SA、跨部门/公司、调岗、失效、重复、失败点、T1/T2执行顺序 |
| 对应测试与断言 | 从任务书和测试矩阵定位；填写实际方法、原因码、分支到达、最终表状态及范围外未变断言 |
| 执行证据 | 待填；命令、提交、环境、日期、报告、结果；未执行明确注明 |
| 结论与复核 | 待填；通过/缺陷/规则待确认/测试缺口，关联问题编号，主审与第二人分别签字 |


<a id="e03"></a>
<!-- api: POST /imports/organization -->
## E03 导入部门信息表与员工信息表

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | E；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /imports/organization；导入部门信息表与员工信息表 |
| 契约 | [importOrganization](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L950)；权限声明：rbac:org:import |
| 前端封装与调用方 | [orgApi.importOrganization](../../../rbac-web/src/api/index.ts)；[OrgView.vue](../../../rbac-web/src/views/OrgView.vue) |
| Controller | [CatalogController.importOrganization](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L139) |
| 直接Service入口 | [ImportService.importOrganization](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L140) |
| 入口方法中的直接Mapper调用 | [CompanyMapper.findByCompanyCode](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java)；[CompanyMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/CompanyMapper.java) |
| 共享Service调用线索 | AuditService.record |
| 独立业务预期及依据 | 先填；正常、无身份、缺权限、越界、状态冲突、目标不存在、重复、失败；不适用写原因 |
| 调用者及对象 | 待填；区分调用者、被查用户、被操作用户及指派/角色/资源 |
| 范围与记录依据 | 待填；公司、用户所属部门、角色所属公司、指派适用部门、selfOnly分别说明 |
| 完整间接调用及最终查询 | 待填；helper、Mapper方法、SQL/JPQL、表、过滤条件；读接口核对count和分页 |
| 事务与最终状态 | 待填；入口注解/传播、flush/原生SQL、提交或回滚后表状态；无写入说明原因 |
| 版本缓存审计与影响面 | 待填；版本更新、缓存读写、审计独立事务、其他持有者及页面刷新 |
| 反例时序与边界 | 待填；本人/同级/SA、跨部门/公司、调岗、失效、重复、失败点、T1/T2执行顺序 |
| 对应测试与断言 | 从任务书和测试矩阵定位；填写实际方法、原因码、分支到达、最终表状态及范围外未变断言 |
| 执行证据 | 待填；命令、提交、环境、日期、报告、结果；未执行明确注明 |
| 结论与复核 | 待填；通过/缺陷/规则待确认/测试缺口，关联问题编号，主审与第二人分别签字 |


<a id="e04"></a>
<!-- api: POST /imports/permissions -->
## E04 导入应用软件功能权限清单（业务系统、模块、资源、权限、五类角色模板）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | E；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /imports/permissions；导入应用软件功能权限清单（业务系统、模块、资源、权限、五类角色模板） |
| 契约 | [importPermissions](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L977)；权限声明：rbac:permission:create |
| 前端封装与调用方 | [orgApi.importPermissions](../../../rbac-web/src/api/index.ts)；[OrgView.vue](../../../rbac-web/src/views/OrgView.vue) |
| Controller | [CatalogController.importPermissions](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L152) |
| 直接Service入口 | [ImportService.importPermissions](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java#L369) |
| 入口方法中的直接Mapper调用 | [ActionMapper.findByActionCode](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ActionMapper.java)；[PermissionMapper.findByResourceId](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java)；[PermissionMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java)；[RolePermissionMapper.findByRoleIdAndPermissionId](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java)；[RolePermissionMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java) |
| 共享Service调用线索 | AuditService.record；AuthorizationService.bumpGrantVersion |
| 独立业务预期及依据 | 先填；正常、无身份、缺权限、越界、状态冲突、目标不存在、重复、失败；不适用写原因 |
| 调用者及对象 | 待填；区分调用者、被查用户、被操作用户及指派/角色/资源 |
| 范围与记录依据 | 待填；公司、用户所属部门、角色所属公司、指派适用部门、selfOnly分别说明 |
| 完整间接调用及最终查询 | 待填；helper、Mapper方法、SQL/JPQL、表、过滤条件；读接口核对count和分页 |
| 事务与最终状态 | 待填；入口注解/传播、flush/原生SQL、提交或回滚后表状态；无写入说明原因 |
| 版本缓存审计与影响面 | 待填；版本更新、缓存读写、审计独立事务、其他持有者及页面刷新 |
| 反例时序与边界 | 待填；本人/同级/SA、跨部门/公司、调岗、失效、重复、失败点、T1/T2执行顺序 |
| 对应测试与断言 | 从任务书和测试矩阵定位；填写实际方法、原因码、分支到达、最终表状态及范围外未变断言 |
| 执行证据 | 待填；命令、提交、环境、日期、报告、结果；未执行明确注明 |
| 结论与复核 | 待填；通过/缺陷/规则待确认/测试缺口，关联问题编号，主审与第二人分别签字 |
