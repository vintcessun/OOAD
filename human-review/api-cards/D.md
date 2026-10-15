# D API业务链路卡

静态定位已填写，独立业务预期、间接调用、最终查询、事务及证据由人工补全。每张卡都要追踪共同拦截器、异常响应和契约schema。API封装存在不代表有页面调用，也不代表行为已验证。

[返回任务书](../D.md) · [模板](../templates.md)

<a id="d01"></a>
<!-- api: GET /roles -->
## D01 角色列表：名称、编码、用户数、权限数、状态

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /roles；角色列表：名称、编码、用户数、权限数、状态 |
| 契约 | [listRoles](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L488)；权限声明：rbac:role:view |
| 前端封装与调用方 | [roleApi.list](../../../rbac-web/src/api/index.ts)；[AssignRoleDialog.vue](../../../rbac-web/src/components/AssignRoleDialog.vue)；[BatchAssignDialog.vue](../../../rbac-web/src/components/BatchAssignDialog.vue)；[RoleListView.vue](../../../rbac-web/src/views/RoleListView.vue) |
| Controller | [RoleController.list](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L41) |
| 直接Service入口 | [RoleManagementService.list](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L81) |
| 入口方法中的直接Mapper调用 | [RoleMapper.countUsersByRole](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[RoleMapper.countPermissionsByRole](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[RoleMapper.findAllByOrderByIdAsc](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java) |
| 共享Service调用线索 | AuthorizationService.isSuperAdmin |
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


<a id="d02"></a>
<!-- api: POST /roles -->
## D02 新增角色

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /roles；新增角色 |
| 契约 | [createRole](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L510)；权限声明：rbac:role:create |
| 前端封装与调用方 | [roleApi.create](../../../rbac-web/src/api/index.ts)；[RoleListView.vue](../../../rbac-web/src/views/RoleListView.vue) |
| Controller | [RoleController.create](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L48) |
| 直接Service入口 | [RoleManagementService.create](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L129) |
| 入口方法中的直接Mapper调用 | [RoleMapper.existsByRoleCode](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[RoleMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java) |
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


<a id="d03"></a>
<!-- api: GET /roles/{id} -->
## D03 角色详情：基本信息与权限

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /roles/{id}；角色详情：基本信息与权限 |
| 契约 | [getRole](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L540)；权限声明：rbac:role:view |
| 前端封装与调用方 | [roleApi.detail](../../../rbac-web/src/api/index.ts)；[RoleDetailView.vue](../../../rbac-web/src/views/RoleDetailView.vue) |
| Controller | [RoleController.detail](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L53) |
| 直接Service入口 | [RoleManagementService.detail](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L97) |
| 入口方法中的直接Mapper调用 | [RolePermissionMapper.findPermissionDetailsOfRole](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java)；[UserRoleMapper.countByRoleId](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java) |
| 共享Service调用线索 | 结合前置拦截器和内部helper补充 |
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


<a id="d04"></a>
<!-- api: PATCH /roles/{id} -->
## D04 修改角色名称、描述、启用停用

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | PATCH /roles/{id}；修改角色名称、描述、启用停用 |
| 契约 | [updateRole](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L560)；权限声明：rbac:role:edit |
| 前端封装与调用方 | [roleApi.update](../../../rbac-web/src/api/index.ts)；[RoleListView.vue](../../../rbac-web/src/views/RoleListView.vue) |
| Controller | [RoleController.update](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L58) |
| 直接Service入口 | [RoleManagementService.update](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L150) |
| 入口方法中的直接Mapper调用 | [RoleMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[RoleMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[UserRoleMapper.countByRoleId](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[RolePermissionMapper.countByRoleId](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java) |
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


<a id="d05"></a>
<!-- api: DELETE /roles/{id} -->
## D05 删除角色（内置角色不能删；仍有人持有时 409）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | DELETE /roles/{id}；删除角色（内置角色不能删；仍有人持有时 409） |
| 契约 | [deleteRole](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L583)；权限声明：rbac:role:delete |
| 前端封装与调用方 | [roleApi.remove](../../../rbac-web/src/api/index.ts)；[RoleListView.vue](../../../rbac-web/src/views/RoleListView.vue) |
| Controller | [RoleController.delete](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L64) |
| 直接Service入口 | [RoleManagementService.delete](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L182) |
| 入口方法中的直接Mapper调用 | [RoleMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[UserRoleMapper.countByRoleId](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[RoleMapper.delete](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java) |
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


<a id="d06"></a>
<!-- api: GET /roles/{id}/users -->
## D06 角色详情「用户」：持有该角色的指派

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /roles/{id}/users；角色详情「用户」：持有该角色的指派 |
| 契约 | [listRoleUserAssignments](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L599)；权限声明：rbac:role:view |
| 前端封装与调用方 | [roleApi.users](../../../rbac-web/src/api/index.ts)；[RoleDetailView.vue](../../../rbac-web/src/views/RoleDetailView.vue) |
| Controller | [RoleController.users](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L70) |
| 直接Service入口 | [RoleManagementService.users](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L111) |
| 入口方法中的直接Mapper调用 | [UserRoleMapper.findAssignmentsOfRole](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[UserRoleMapper.findAllAssignmentsOfRole](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java) |
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


<a id="d07"></a>
<!-- api: POST /roles/{id}/permissions -->
## D07 为角色授予权限（PERMISSION_ASSIGN）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /roles/{id}/permissions；为角色授予权限（PERMISSION_ASSIGN） |
| 契约 | [grantRolePermissions](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L629)；权限声明：rbac:grant:assign |
| 前端封装与调用方 | [roleApi.grant](../../../rbac-web/src/api/index.ts)；[GrantPermissionDialog.vue](../../../rbac-web/src/components/GrantPermissionDialog.vue) |
| Controller | [RoleController.grant](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L77) |
| 直接Service入口 | [RoleManagementService.grant](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L202) |
| 入口方法中的直接Mapper调用 | [RoleMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[PermissionMapper.findAllById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java)；[RolePermissionMapper.existsByRoleIdAndPermissionId](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java)；[RolePermissionMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java) |
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


<a id="d08"></a>
<!-- api: DELETE /roles/{id}/permissions/{permissionId} -->
## D08 回收角色的一个权限（PERMISSION_REVOKE）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | DELETE /roles/{id}/permissions/{permissionId}；回收角色的一个权限（PERMISSION_REVOKE） |
| 契约 | [revokeRolePermission](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L664)；权限声明：rbac:grant:revoke |
| 前端封装与调用方 | [roleApi.ungrant](../../../rbac-web/src/api/index.ts)；[RoleDetailView.vue](../../../rbac-web/src/views/RoleDetailView.vue) |
| Controller | [RoleController.ungrant](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L83) |
| 直接Service入口 | [RoleManagementService.ungrant](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/RoleManagementService.java#L246) |
| 入口方法中的直接Mapper调用 | [RoleMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[RolePermissionMapper.findByRoleIdAndPermissionId](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java)；[PermissionMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java)；[RolePermissionMapper.delete](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java) |
| 共享Service调用线索 | AuthorizationService.bumpGrantVersion；AuditService.record |
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


<a id="d09"></a>
<!-- api: GET /systems -->
## D09 业务系统与功能模块（权限域）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /systems；业务系统与功能模块（权限域） |
| 契约 | [listSystems](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L720)；权限声明：rbac:permission:view |
| 前端封装与调用方 | [catalogApi.systems](../../../rbac-web/src/api/index.ts)；[GrantPermissionDialog.vue](../../../rbac-web/src/components/GrantPermissionDialog.vue)；[PermissionView.vue](../../../rbac-web/src/views/PermissionView.vue) |
| Controller | [CatalogController.systems](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L57) |
| 直接Service入口 | [CatalogService.systems](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L60) |
| 入口方法中的直接Mapper调用 | [FunctionModuleMapper.findAllByOrderBySystemIdAscSortOrderAsc](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/FunctionModuleMapper.java)；[SoftwareSystemMapper.findAllByOrderBySortOrderAscIdAsc](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/SoftwareSystemMapper.java) |
| 共享Service调用线索 | 结合前置拦截器和内部helper补充 |
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


<a id="d10"></a>
<!-- api: GET /resources -->
## D10 资源列表（BUSINESS 业务功能点 / MENU 菜单 / API 接口能力）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /resources；资源列表（BUSINESS 业务功能点 / MENU 菜单 / API 接口能力） |
| 契约 | [listResources](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L732)；权限声明：rbac:permission:view |
| 前端封装与调用方 | [catalogApi.resources](../../../rbac-web/src/api/index.ts)；[MenuListView.vue](../../../rbac-web/src/views/MenuListView.vue)；[PermissionView.vue](../../../rbac-web/src/views/PermissionView.vue) |
| Controller | [CatalogController.resources](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L62) |
| 直接Service入口 | [CatalogService.resources](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L71) |
| 入口方法中的直接Mapper调用 | [ResourceMapper.search](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java) |
| 共享Service调用线索 | 结合前置拦截器和内部helper补充 |
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


<a id="d11"></a>
<!-- api: POST /resources -->
## D11 新增资源，并为给出的操作建立权限

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /resources；新增资源，并为给出的操作建立权限 |
| 契约 | [createResource](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L749)；权限声明：rbac:permission:create |
| 前端封装与调用方 | [catalogApi.createResource](../../../rbac-web/src/api/index.ts)；[PermissionView.vue](../../../rbac-web/src/views/PermissionView.vue) |
| Controller | [CatalogController.createResource](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L71) |
| 直接Service入口 | [CatalogService.createResource](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L99) |
| 入口方法中的直接Mapper调用 | [FunctionModuleMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/FunctionModuleMapper.java)；[SoftwareSystemMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/SoftwareSystemMapper.java)；[ResourceMapper.existsByResourceCode](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java)；[ResourceMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java) |
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


<a id="d12"></a>
<!-- api: GET /actions -->
## D12 操作词汇表

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /actions；操作词汇表 |
| 契约 | [listActions](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L778)；权限声明：rbac:permission:view |
| 前端封装与调用方 | [catalogApi.actions](../../../rbac-web/src/api/index.ts)；[PermissionView.vue](../../../rbac-web/src/views/PermissionView.vue) |
| Controller | [CatalogController.actions](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L77) |
| 直接Service入口 | [CatalogService.actions](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L81) |
| 入口方法中的直接Mapper调用 | [ActionMapper.findAllByOrderBySortOrderAsc](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ActionMapper.java) |
| 共享Service调用线索 | 结合前置拦截器和内部helper补充 |
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


<a id="d13"></a>
<!-- api: GET /permissions -->
## D13 权限列表（Permission = Resource × Action）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /permissions；权限列表（Permission = Resource × Action） |
| 契约 | [listPermissions](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L790)；权限声明：rbac:permission:view |
| 前端封装与调用方 | [catalogApi.permissions](../../../rbac-web/src/api/index.ts)；[GrantPermissionDialog.vue](../../../rbac-web/src/components/GrantPermissionDialog.vue)；[PermissionView.vue](../../../rbac-web/src/views/PermissionView.vue) |
| Controller | [CatalogController.permissions](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L82) |
| 直接Service入口 | [CatalogService.permissions](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L88) |
| 入口方法中的直接Mapper调用 | [PermissionMapper.search](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java) |
| 共享Service调用线索 | 结合前置拦截器和内部helper补充 |
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


<a id="d14"></a>
<!-- api: POST /permissions -->
## D14 为已有资源新增一个操作的权限

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | D；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /permissions；为已有资源新增一个操作的权限 |
| 契约 | [createPermission](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L807)；权限声明：rbac:permission:create |
| 前端封装与调用方 | [catalogApi.createPermission](../../../rbac-web/src/api/index.ts)；[PermissionView.vue](../../../rbac-web/src/views/PermissionView.vue) |
| Controller | [CatalogController.createPermission](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L91) |
| 直接Service入口 | [CatalogService.createPermission](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/CatalogService.java#L136) |
| 入口方法中的直接Mapper调用 | [ResourceMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ResourceMapper.java)；[ActionMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ActionMapper.java) |
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
