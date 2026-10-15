# C API业务链路卡

静态定位已填写，独立业务预期、间接调用、最终查询、事务及证据由人工补全。每张卡都要追踪共同拦截器、异常响应和契约schema。API封装存在不代表有页面调用，也不代表行为已验证。

[返回任务书](../C.md) · [模板](../templates.md)

<a id="c01"></a>
<!-- api: GET /users -->
## C01 用户列表与搜索（只返回管理范围内的用户）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /users；用户列表与搜索（只返回管理范围内的用户） |
| 契约 | [listUsers](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L219)；权限声明：rbac:user:view |
| 前端封装与调用方 | [userApi.list](../../../rbac-web/src/api/index.ts)；[ExplainView.vue](../../../rbac-web/src/views/ExplainView.vue)；[UserListView.vue](../../../rbac-web/src/views/UserListView.vue) |
| Controller | [UserController.list](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L43) |
| 直接Service入口 | [UserManagementService.list](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L86) |
| 入口方法中的直接Mapper调用 | [DepartmentMapper.findPathById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java)；[UserMapper.findAll](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java) |
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


<a id="c02"></a>
<!-- api: POST /users -->
## C02 新增用户（正式员工、临时用户或业务系统账号）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /users；新增用户（正式员工、临时用户或业务系统账号） |
| 契约 | [createUser](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L251)；权限声明：rbac:user:create |
| 前端封装与调用方 | [userApi.create](../../../rbac-web/src/api/index.ts)；[UserFormDialog.vue](../../../rbac-web/src/components/UserFormDialog.vue) |
| Controller | [UserController.create](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L55) |
| 直接Service入口 | [UserManagementService.create](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L170) |
| 入口方法中的直接Mapper调用 | [DepartmentMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java)；[UserMapper.existsByUsername](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java)；[UserMapper.existsByEmployeeNo](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java)；[UserMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java) |
| 共享Service调用线索 | AuthorizationService.requireInScope；AuditService.record |
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


<a id="c03"></a>
<!-- api: POST /users/batch-status -->
## C03 批量启用 / 停用 / 删除

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /users/batch-status；批量启用 / 停用 / 删除 |
| 契约 | [batchChangeUserStatus](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L275)；权限声明：rbac:user:edit |
| 前端封装与调用方 | [userApi.batchStatus](../../../rbac-web/src/api/index.ts)；[UserListView.vue](../../../rbac-web/src/views/UserListView.vue) |
| Controller | [UserController.batchStatus](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L84) |
| 直接Service入口 | [UserManagementService.batchStatus](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L316) |
| 入口方法中的直接Mapper调用 | [UserMapper.findAllById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java) |
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


<a id="c04"></a>
<!-- api: GET /users/{id} -->
## C04 用户详情：基本信息、角色、有效权限

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /users/{id}；用户详情：基本信息、角色、有效权限 |
| 契约 | [getUser](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L302)；权限声明：@self |
| 前端封装与调用方 | [userApi.detail](../../../rbac-web/src/api/index.ts)；[ExplainView.vue](../../../rbac-web/src/views/ExplainView.vue)；[UserDetailView.vue](../../../rbac-web/src/views/UserDetailView.vue) |
| Controller | [UserController.detail](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L60) |
| 直接Service入口 | [UserManagementService.detail](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L150) |
| 入口方法中的直接Mapper调用 | [UserRoleMapper.findAssignmentsOfUser](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[DepartmentMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java) |
| 共享Service调用线索 | AuthorizationService.requireInScope；AuthorizationService.loadGrants |
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


<a id="c05"></a>
<!-- api: PATCH /users/{id} -->
## C05 修改用户（姓名、部门、电话、岗位、到期时间）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | PATCH /users/{id}；修改用户（姓名、部门、电话、岗位、到期时间） |
| 契约 | [updateUser](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L323)；权限声明：rbac:user:edit |
| 前端封装与调用方 | [userApi.update](../../../rbac-web/src/api/index.ts)；[UserFormDialog.vue](../../../rbac-web/src/components/UserFormDialog.vue) |
| Controller | [UserController.update](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L65) |
| 直接Service入口 | [UserManagementService.update](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L249) |
| 入口方法中的直接Mapper调用 | [DepartmentMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java)；[UserMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java) |
| 共享Service调用线索 | AuthorizationService.requireInScope；AuditService.record |
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


<a id="c06"></a>
<!-- api: DELETE /users/{id} -->
## C06 删除用户（逻辑删除，用户名不可复用，指派保留供审计）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | DELETE /users/{id}；删除用户（逻辑删除，用户名不可复用，指派保留供审计） |
| 契约 | [deleteUser](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L341)；权限声明：rbac:user:delete |
| 前端封装与调用方 | [userApi.remove](../../../rbac-web/src/api/index.ts)；[UserListView.vue](../../../rbac-web/src/views/UserListView.vue) |
| Controller | [UserController.delete](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L77) |
| 直接Service入口 | [UserManagementService.delete](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L304) |
| 入口方法中的直接Mapper调用 | 本方法未直接调用Mapper，继续追踪helper或其他Service |
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


<a id="c07"></a>
<!-- api: PATCH /users/{id}/status -->
## C07 启用或停用

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | PATCH /users/{id}/status；启用或停用 |
| 契约 | [changeUserStatus](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L360)；权限声明：rbac:user:edit |
| 前端封装与调用方 | [userApi.changeStatus](../../../rbac-web/src/api/index.ts)；[UserDetailView.vue](../../../rbac-web/src/views/UserDetailView.vue)；[UserListView.vue](../../../rbac-web/src/views/UserListView.vue) |
| Controller | [UserController.changeStatus](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L71) |
| 直接Service入口 | [UserManagementService.changeStatus](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L291) |
| 入口方法中的直接Mapper调用 | [DepartmentMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java) |
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


<a id="c08"></a>
<!-- api: GET /users/{id}/roles -->
## C08 用户的角色指派

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /users/{id}/roles；用户的角色指派 |
| 契约 | [listUserRoleAssignments](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L387)；权限声明：@self |
| 前端封装与调用方 | [userApi.roles](../../../rbac-web/src/api/index.ts)；当前控制台源码未找到该封装的直接调用；按API验证并核对复用入口 |
| Controller | [UserController.roles](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L89) |
| 直接Service入口 | [AssignmentService.listOfUser](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L95) |
| 入口方法中的直接Mapper调用 | [UserMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java)；[UserRoleMapper.findAssignmentsOfUser](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java) |
| 共享Service调用线索 | AuthorizationService.requireInScope |
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


<a id="c09"></a>
<!-- api: POST /users/{id}/roles -->
## C09 为用户分配角色（ROLE_ASSIGN）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /users/{id}/roles；为用户分配角色（ROLE_ASSIGN） |
| 契约 | [assignUserRole](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L406)；权限声明：rbac:assignment:assign |
| 前端封装与调用方 | [userApi.assign](../../../rbac-web/src/api/index.ts)；[AssignRoleDialog.vue](../../../rbac-web/src/components/AssignRoleDialog.vue) |
| Controller | [UserController.assign](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L94) |
| 直接Service入口 | [AssignmentService.assign](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L106) |
| 入口方法中的直接Mapper调用 | [UserMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java)；[RoleMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[UserRoleMapper.existsByUserIdAndRoleIdAndScopeDeptId](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[UserRoleMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[UserRoleMapper.findAssignmentsOfUser](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java) |
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


<a id="c10"></a>
<!-- api: DELETE /users/{id}/roles/{assignmentId} -->
## C10 回收一次角色指派（ROLE_REVOKE）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | DELETE /users/{id}/roles/{assignmentId}；回收一次角色指派（ROLE_REVOKE） |
| 契约 | [revokeUserRoleAssignment](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L444)；权限声明：rbac:assignment:revoke |
| 前端封装与调用方 | [userApi.revoke](../../../rbac-web/src/api/index.ts)；[UserDetailPanel.vue](../../../rbac-web/src/components/UserDetailPanel.vue) |
| Controller | [UserController.revoke](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L100) |
| 直接Service入口 | [AssignmentService.revoke](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L144) |
| 入口方法中的直接Mapper调用 | [UserRoleMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[UserMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java)；[RoleMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[UserRoleMapper.delete](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[UserRoleMapper.flush](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[UserRoleMapper.countActiveSuperAdmins](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java) |
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


<a id="c11"></a>
<!-- api: GET /users/{id}/permissions -->
## C11 用户的有效权限（每条带来源角色与适用部门）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /users/{id}/permissions；用户的有效权限（每条带来源角色与适用部门） |
| 契约 | [listUserEffectivePermissions](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L466)；权限声明：@self |
| 前端封装与调用方 | [userApi.permissions](../../../rbac-web/src/api/index.ts)；当前控制台源码未找到该封装的直接调用；按API验证并核对复用入口 |
| Controller | [UserController.permissions](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/UserController.java#L108) |
| 直接Service入口 | [UserManagementService.detail](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/UserManagementService.java#L150) |
| 入口方法中的直接Mapper调用 | [UserRoleMapper.findAssignmentsOfUser](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[DepartmentMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java) |
| 共享Service调用线索 | AuthorizationService.requireInScope；AuthorizationService.loadGrants |
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


<a id="c12"></a>
<!-- api: POST /assignments/batch -->
## C12 按分公司 / 部门 / 员工批量分配角色

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /assignments/batch；按分公司 / 部门 / 员工批量分配角色 |
| 契约 | [batchAssignRole](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L679)；权限声明：rbac:assignment:assign |
| 前端封装与调用方 | [assignmentApi.batch](../../../rbac-web/src/api/index.ts)；[BatchAssignDialog.vue](../../../rbac-web/src/components/BatchAssignDialog.vue) |
| Controller | [RoleController.batchAssign](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L90) |
| 直接Service入口 | [AssignmentService.batchAssign](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L192) |
| 入口方法中的直接Mapper调用 | [RoleMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[DepartmentMapper.existsById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java)；[UserRoleMapper.findByRoleIdAndUserIdIn](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[UserRoleMapper.insertForUsers](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java) |
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


<a id="c13"></a>
<!-- api: POST /assignments/batch-revoke -->
## C13 按分公司 / 部门 / 员工批量回收角色

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | C；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /assignments/batch-revoke；按分公司 / 部门 / 员工批量回收角色 |
| 契约 | [batchRevokeRole](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L699)；权限声明：rbac:assignment:revoke |
| 前端封装与调用方 | [assignmentApi.batchRevoke](../../../rbac-web/src/api/index.ts)；[BatchAssignDialog.vue](../../../rbac-web/src/components/BatchAssignDialog.vue) |
| Controller | [RoleController.batchRevoke](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/RoleController.java#L95) |
| 直接Service入口 | [AssignmentService.batchRevoke](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AssignmentService.java#L257) |
| 入口方法中的直接Mapper调用 | [RoleMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[UserRoleMapper.findByRoleIdAndUserIdIn](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[UserRoleMapper.deleteByIds](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java)；[UserRoleMapper.countActiveSuperAdmins](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserRoleMapper.java) |
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
