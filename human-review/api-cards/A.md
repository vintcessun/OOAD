# A API业务链路卡

静态定位已填写，独立业务预期、间接调用、最终查询、事务及证据由人工补全。每张卡都要追踪共同拦截器、异常响应和契约schema。API封装存在不代表有页面调用，也不代表行为已验证。

[返回任务书](../A.md) · [模板](../templates.md)

<a id="a01"></a>
<!-- api: POST /auth/login -->
## A01 登录

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | A；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /auth/login；登录 |
| 契约 | [login](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L66)；权限声明：anonymous |
| 前端封装与调用方 | [authApi.login](../../../rbac-web/src/api/index.ts)；[auth.ts](../../../rbac-web/src/stores/auth.ts) |
| Controller | [AuthController.login](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L40) |
| 直接Service入口 | [AuthenticationService.login](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L60) |
| 入口方法中的直接Mapper调用 | [UserMapper.findByUsername](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java)；[UserMapper.recordLoginFailure](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java)；[UserMapper.resetLoginFailure](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java) |
| 共享Service调用线索 | TokenService.issue；AuditService.record |
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


<a id="a02"></a>
<!-- api: POST /auth/logout -->
## A02 登出

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | A；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /auth/logout；登出 |
| 契约 | [logout](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L99)；权限声明：@self |
| 前端封装与调用方 | [authApi.logout](../../../rbac-web/src/api/index.ts)；[auth.ts](../../../rbac-web/src/stores/auth.ts) |
| Controller | [AuthController.logout](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L46) |
| 直接Service入口 | [AuthenticationService.logout](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L116) |
| 入口方法中的直接Mapper调用 | [TokenRevocationMapper.revoke](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/TokenRevocationMapper.java) |
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


<a id="a03"></a>
<!-- api: GET /auth/me -->
## A03 当前用户的基本信息、角色与有效权限

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | A；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /auth/me；当前用户的基本信息、角色与有效权限 |
| 契约 | [getCurrentUser](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L112)；权限声明：@self |
| 前端封装与调用方 | [authApi.me](../../../rbac-web/src/api/index.ts)；[auth.ts](../../../rbac-web/src/stores/auth.ts)；[MeView.vue](../../../rbac-web/src/views/MeView.vue) |
| Controller | [AuthController.me](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L54) |
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


<a id="a04"></a>
<!-- api: POST /auth/password -->
## A04 修改本人口令

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | A；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /auth/password；修改本人口令 |
| 契约 | [changeOwnPassword](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L134)；权限声明：@self |
| 前端封装与调用方 | [authApi.changePassword](../../../rbac-web/src/api/index.ts)；[PasswordView.vue](../../../rbac-web/src/views/PasswordView.vue) |
| Controller | [AuthController.changePassword](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L59) |
| 直接Service入口 | [AuthenticationService.changePassword](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthenticationService.java#L123) |
| 入口方法中的直接Mapper调用 | [UserMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java)；[UserMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java) |
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
