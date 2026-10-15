# B API业务链路卡

静态定位已填写，独立业务预期、间接调用、最终查询、事务及证据由人工补全。每张卡都要追踪共同拦截器、异常响应和契约schema。API封装存在不代表有页面调用，也不代表行为已验证。

[返回任务书](../B.md) · [模板](../templates.md)

<a id="b01"></a>
<!-- api: POST /authz/check -->
## B01 鉴权：某用户能否对某部门的数据执行某操作

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | B；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /authz/check；鉴权：某用户能否对某部门的数据执行某操作 |
| 契约 | [checkAuthorization](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L160)；权限声明：@self |
| 前端封装与调用方 | [authzApi.check](../../../rbac-web/src/api/index.ts)；当前控制台源码未找到该封装的直接调用；按API验证并核对复用入口 |
| Controller | [AuthController.check](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L67) |
| 直接Service入口 | [AuthzQueryService.check](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java#L37) |
| 入口方法中的直接Mapper调用 | 本方法未直接调用Mapper，继续追踪helper或其他Service |
| 共享Service调用线索 | AuthorizationService.decide；AuditService.recordDecision |
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


<a id="b02"></a>
<!-- api: POST /authz/explain -->
## B02 权限解释：与 check 相同的判定，另返回逐步的判定依据（trace）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | B；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /authz/explain；权限解释：与 check 相同的判定，另返回逐步的判定依据（trace） |
| 契约 | [explainAuthorization](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L190)；权限声明：@self |
| 前端封装与调用方 | [authzApi.explain](../../../rbac-web/src/api/index.ts)；[ExplainView.vue](../../../rbac-web/src/views/ExplainView.vue) |
| Controller | [AuthController.explain](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java#L74) |
| 直接Service入口 | [AuthzQueryService.check](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthzQueryService.java#L37) |
| 入口方法中的直接Mapper调用 | 本方法未直接调用Mapper，继续追踪helper或其他Service |
| 共享Service调用线索 | AuthorizationService.decide；AuditService.recordDecision |
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
