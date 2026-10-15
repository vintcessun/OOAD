# F API业务链路卡

静态定位已填写，独立业务预期、间接调用、最终查询、事务及证据由人工补全。每张卡都要追踪共同拦截器、异常响应和契约schema。API封装存在不代表有页面调用，也不代表行为已验证。

[返回任务书](../F.md) · [模板](../templates.md)

<a id="f01"></a>
<!-- api: GET /permissions/heatmap -->
## F01 权限使用热力图（业务系统 × 操作，成功鉴权次数）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | F；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /permissions/heatmap；权限使用热力图（业务系统 × 操作，成功鉴权次数） |
| 契约 | [getPermissionHeatmap](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L831)；权限声明：rbac:heatmap:view |
| 前端封装与调用方 | [statsApi.heatmap](../../../rbac-web/src/api/index.ts)；[HeatmapView.vue](../../../rbac-web/src/views/HeatmapView.vue) |
| Controller | [StatsController.heatmap](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/StatsController.java#L48) |
| 直接Service入口 | [DashboardService.heatmap](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java#L105) |
| 入口方法中的直接Mapper调用 | [SoftwareSystemMapper.findAllByOrderBySortOrderAscIdAsc](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/SoftwareSystemMapper.java)；[ActionMapper.findAllByOrderBySortOrderAsc](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/ActionMapper.java)；[AuditLogMapper.heatmap](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java) |
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


<a id="f02"></a>
<!-- api: GET /menus/me -->
## F02 当前用户可见的菜单树（按「菜单资源:view」动态生成）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | F；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /menus/me；当前用户可见的菜单树（按「菜单资源:view」动态生成） |
| 契约 | [getCurrentUserMenus](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L849)；权限声明：@self |
| 前端封装与调用方 | [menuApi.mine](../../../rbac-web/src/api/index.ts)；[auth.ts](../../../rbac-web/src/stores/auth.ts) |
| Controller | [CatalogController.myMenus](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L99) |
| 直接Service入口 | [MenuService.myMenus](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L42) |
| 入口方法中的直接Mapper调用 | 本方法未直接调用Mapper，继续追踪helper或其他Service |
| 共享Service调用线索 | AuthorizationService.decide |
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


<a id="f03"></a>
<!-- api: GET /menus -->
## F03 完整菜单树

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | F；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /menus；完整菜单树 |
| 契约 | [listMenus](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L862)；权限声明：rbac:menu:view |
| 前端封装与调用方 | [menuApi.all](../../../rbac-web/src/api/index.ts)；[MenuListView.vue](../../../rbac-web/src/views/MenuListView.vue) |
| Controller | [CatalogController.allMenus](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L104) |
| 直接Service入口 | [MenuService.all](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L56) |
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


<a id="f04"></a>
<!-- api: POST /menus -->
## F04 新增菜单

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | F；未审查；姓名/日期/复核人待填写 |
| API与功能 | POST /menus；新增菜单 |
| 契约 | [createMenu](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L872)；权限声明：rbac:menu:create |
| 前端封装与调用方 | [menuApi.create](../../../rbac-web/src/api/index.ts)；[MenuListView.vue](../../../rbac-web/src/views/MenuListView.vue) |
| Controller | [CatalogController.createMenu](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L109) |
| 直接Service入口 | [MenuService.create](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L61) |
| 入口方法中的直接Mapper调用 | [MenuMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java) |
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


<a id="f05"></a>
<!-- api: PATCH /menus/{id} -->
## F05 修改菜单

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | F；未审查；姓名/日期/复核人待填写 |
| API与功能 | PATCH /menus/{id}；修改菜单 |
| 契约 | [updateMenu](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L892)；权限声明：rbac:menu:edit |
| 前端封装与调用方 | [menuApi.update](../../../rbac-web/src/api/index.ts)；[MenuListView.vue](../../../rbac-web/src/views/MenuListView.vue) |
| Controller | [CatalogController.updateMenu](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L114) |
| 直接Service入口 | [MenuService.update](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L70) |
| 入口方法中的直接Mapper调用 | [MenuMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java)；[MenuMapper.save](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java) |
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


<a id="f06"></a>
<!-- api: DELETE /menus/{id} -->
## F06 删除菜单（有子菜单时 409）

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | F；未审查；姓名/日期/复核人待填写 |
| API与功能 | DELETE /menus/{id}；删除菜单（有子菜单时 409） |
| 契约 | [deleteMenu](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L909)；权限声明：rbac:menu:delete |
| 前端封装与调用方 | [menuApi.remove](../../../rbac-web/src/api/index.ts)；[MenuListView.vue](../../../rbac-web/src/views/MenuListView.vue) |
| Controller | [CatalogController.deleteMenu](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java#L120) |
| 直接Service入口 | [MenuService.delete](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java#L82) |
| 入口方法中的直接Mapper调用 | [MenuMapper.findById](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java)；[MenuMapper.existsByParentId](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java)；[MenuMapper.delete](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java) |
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


<a id="f07"></a>
<!-- api: GET /audit-logs -->
## F07 审计日志：谁、什么时候、在哪里、对谁、做了什么、结果如何、为什么

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | F；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /audit-logs；审计日志：谁、什么时候、在哪里、对谁、做了什么、结果如何、为什么 |
| 契约 | [listAuditLogs](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L1002)；权限声明：rbac:audit:view |
| 前端封装与调用方 | [statsApi.auditLogs](../../../rbac-web/src/api/index.ts)；[AuditLogView.vue](../../../rbac-web/src/views/AuditLogView.vue) |
| Controller | [StatsController.auditLogs](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/StatsController.java#L28) |
| 直接Service入口 | [AuditService.search](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java#L84) |
| 入口方法中的直接Mapper调用 | [AuditLogMapper.search](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java) |
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


<a id="f08"></a>
<!-- api: GET /dashboard -->
## F08 Dashboard 十一项指标

| 项目 | 审查记录 |
| --- | --- |
| 主审与状态 | F；未审查；姓名/日期/复核人待填写 |
| API与功能 | GET /dashboard；Dashboard 十一项指标 |
| 契约 | [getDashboard](../../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L1026)；权限声明：rbac:dashboard:view |
| 前端封装与调用方 | [statsApi.dashboard](../../../rbac-web/src/api/index.ts)；[DashboardView.vue](../../../rbac-web/src/views/DashboardView.vue) |
| Controller | [StatsController.dashboard](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/StatsController.java#L43) |
| 直接Service入口 | [DashboardService.metrics](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java#L55) |
| 入口方法中的直接Mapper调用 | [AuditLogMapper.countByEventTypeInAndOccurredAtGreaterThanEqual](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java)；[AuditLogMapper.countByEventTypeAndOccurredAtGreaterThanEqual](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java)；[AuditLogMapper.countAuthzWithLatency](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java)；[AuditLogMapper.countAuthzWithCacheInfo](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java)；[AuditLogMapper.countAuthzCacheHits](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java)；[UserMapper.countByStatusNot](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/UserMapper.java)；[RoleMapper.count](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RoleMapper.java)；[PermissionMapper.count](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/PermissionMapper.java)；[AuditLogMapper.countAbnormal](../../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java) |
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
