# D 有效权限与缓存一致性问题记录

更新于 2026-11-23。D-01 和 D-02 已在提交 `bfd3cfd` 中修复。
结论以当前源码、公开接口和前端调用链为依据。双后端切换时，检查同一业务配置下原有功能能否继续使用，权限判定和页面结果是否正确。

## 问题索引

| 编号 | 问题 | 状态 | 处理结果 |
| --- | --- | --- | --- |
| D-01 | Structured 的 `snapshot()` 写完缓存后直接返回。授权版本在写入期间推进时，本次调用仍会返回旧权限。 | 已解决 | L1 和 Redis 写入尝试结束后再次读取数据库授权版本。版本变化时重试。[Structured 源码](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L93-L129) |
| D-02 | DDD 的 S03 类图中，`AuthorizationApplicationService` 的 `cache` 字段比其他字段向右缩进。 | 已解决 | 修正字段区的 Graphviz 对齐设置，同步更新图源和生成器，重生成 SVG 与 PNG。[DOT](../../docs/design/S03-ddd-class.dot) · [SVG](../../docs/design/S03-ddd-class.svg) · [PNG](../../docs/design/S03-ddd-class.png) |
| D-03 | Structured 的 S03 时序图曾漏画缓存回填后的版本复查和重试。 | 已解决 | 同步场景定义和时序图，标出写后读取版本、版本变化时重试及重试耗尽返回 503。[场景维护源](../../tools/render-rbac1-design.scenarios.py) · [PUML](../../docs/design/S03-structured-sequence.puml) · [SVG](../../docs/design/S03-structured-sequence.svg) · [PNG](../../docs/design/S03-structured-sequence.png) |
| D-04 | Structured 事务外的 API 点判定曾缺少覆盖操作策略和权限快照的版本检查。 | 已解决 | 读取策略前和返回结果前检查授权版本，判定或 4xx 异常遇到版本变化时重做整次判定，三次不稳定返回 `503 AUTHORIZATION_UNAVAILABLE`。[Structured API 判定](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L244-L283) · [回归测试](../../rbac-structured/src/test/java/com/xmu/rbac/structured/AuthorizationConsistencyTest.java) |
| D-05 | Structured 事务外的快照重试曾沿用进入循环前的用户启用状态。 | 已解决 | 每轮读取授权版本后重新读取并校验用户和系统。用户已停用时返回 `401`，事务内继续沿原路径现场计算。[状态检查与重试](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L81-L129) · [回归测试](../../rbac-structured/src/test/java/com/xmu/rbac/structured/AuthorizationConsistencyTest.java) |
| D-06 | DDD 用户详情查询停用目标系统时，曾将目标状态当作操作者认证失败。 | 已解决 | 调用者通过权限和部门范围检查后，查询已停用的目标系统返回 `200` 和空权限。调用者认证失败返回 `401`，未知系统按 D-15 返回 `404`。[DDD 查询](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/DirectoryApplicationService.java#L359) · [Structured 查询](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L568) · [回归测试](../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/application/AuthorizationSecurityRegressionTest.java#L129) |
| D-07 | DDD 的 API `explain` 曾将不同版本的判定和解释快照放进同一响应。 | 已解决 | 对目标状态、权限快照和判定结果一起检查版本。版本变化时重读，稳定停用后返回 `allowed=false` 和空快照。D-14 将普通 `check` 和 UI 解释也纳入这套机制。[版本检查](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L166) |
| D-08 | 同一系统的 UI 权限和 API 权限同码时，页面曾混合显示两种来源。 | 已解决 | 两套后端和响应契约增加 `permissionKind`，前端按种类与编码归组。旧缓存若包含缺少种类的来源则重新计算。[响应契约](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L4034-L4061) · [Structured 来源](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L192-L196) · [DDD 来源](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java#L7) · [前端回归测试](../../rbac-web/tests/components.spec.ts#L40) |
| D-09 | SIM 共享接口曾允许空串或超长的权限码、操作 ID 进入业务判定。 | 已解决 | 两套实现均校验已提供字段的长度为 1–128。原有分支仍按 UI/API 类型要求对应字段，非法输入返回 `400 VALIDATION_ERROR`。[输入契约](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml#L4577-L4601) · [Structured 校验](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/Inputs.java#L197) · [DDD 校验](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/interfaces/rest/AuthorizationController.java#L35) |
| D-10 | 前端保存成功后的权限刷新曾复用写入前已发出的同会话请求。 | 已解决 | 保存成功后发起一轮新权限读取，较早的同会话请求以最新刷新结果为准，避免过期成功覆盖权限和菜单，也避免过期失败误导路由。普通刷新继续合并同一会话中尚未完成的最新请求。[刷新顺序](../../rbac-web/src/stores/auth.ts#L50-L67) · [写后刷新](../../rbac-web/src/views/workspace.ts#L56-L81) · [回归测试](../../rbac-web/tests/auth.spec.ts#L58) |
| D-11 | S04 时序图曾采用独立网关链路，前端分工表曾指向旧鉴权页。 | 已解决 | 两版 S04 主时序图已更新为权限后端内嵌的 `CourseProxyFilter` 链路，后续补齐该链路在两版中的当前类图。独立网关类图保留为历史图。前端分工指出 `/authorization` 的 `AuthorizationView`。[Structured 类图](../design/S04-structured-current-class.svg) · [DDD 类图](../design/S04-ddd-current-class.svg) · [Structured 时序图](../../docs/design/S04-structured-sequence.svg) · [DDD 时序图](../../docs/design/S04-ddd-sequence.svg) · [设计图索引](../../docs/design/index.adoc) · [前端分工](../../docs/oral-review/frontend.adoc) |
| D-12 | 中心超管在 Structured 中缺少某个已登记操作的显式权限时，点判定曾拒绝，影响管理系统列表加载。 | 已解决 | Structured 在校验用户、系统和已登记操作处于启用状态后，允许持有中心内置 `SUPER_ADMIN` 角色的用户执行中心操作。[Structured 判定](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L260-L283) · [DDD 判定](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java#L96-L105) · [回归测试](../../rbac-structured/src/test/java/com/xmu/rbac/structured/AuthorizationConsistencyTest.java#L70) |
| D-13 | 停用系统下的角色详情在两版之间显示不一致。 | 已解决 | Structured 的角色详情沿用 DDD 的展示规则，返回启用角色配置及继承出的权限和来源。用户快照和实际鉴权继续校验系统状态。[Structured 查询](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L452) · [DDD 查询](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/RoleApplicationService.java#L53) · [回归测试](../../rbac-structured/src/test/java/com/xmu/rbac/structured/AuthorizationConsistencyTest.java#L162) |
| D-14 | DDD 普通模拟和 UI 解释可能混用停用前的目标状态与停用后的权限快照。 | 已解决 | UI 和 API 的 `check`、`explain` 都通过 `simulate()` 在同一授权版本内读取状态、快照和判定。并发停用时重试，返回对应的停用原因。[DDD 实现](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L158) · [回归测试](../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/application/AuthorizationSecurityRegressionTest.java#L397) · [模拟时序图](../../docs/design/SIM-ddd-sequence.svg) |
| D-15 | 用户详情先读目标用户。在系统不存在时，响应原先可能受用户是否存在或停用影响。 | 已解决 | 两版先校验调用者，再确认目标系统存在，最后查询目标用户和检查部门范围。系统不存在时固定返回 `404 SYSTEM_NOT_FOUND`。[Structured 实现](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L568) · [DDD 实现](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/DirectoryApplicationService.java#L359) · [Structured 回归](../../rbac-structured/src/test/java/com/xmu/rbac/structured/AuthorizationConsistencyTest.java#L210) · [DDD 回归](../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/application/AuthorizationSecurityRegressionTest.java#L129) |
| D-16 | DDD 外部 API 点查询未校验 `operationId` 长度。 | 已解决 | 与 Structured 一致，长度超出 1–128 的输入返回 `400 VALIDATION_ERROR`，格式有效但未登记的操作返回 `404 API_OPERATION_NOT_FOUND`。共享 OpenAPI 补列 400。[DDD 入口](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/interfaces/rest/IdentityController.java#L127) · [回归测试](../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/application/AuthorizationTokenAudienceTest.java#L72) |
| D-17 | 前端进入管理页时遇到权限刷新失败，可能显示暂无访问权限或跳回登录。 | 已解决 | 有会话时，网络错误或 503 会拦截管理页，复用状态页提示刷新失败并允许重试。明确拒绝显示 403。失败页原本在直达时也跳过会话校验。直接打开或刷新失败页及 `/403` 现在都会校验会话。失败页遇到会话过期会回到登录并保留原管理页地址，同轮刷新先收到 503 再收到 401 时也保留该地址。[路由守卫](../../rbac-web/src/router/index.ts#L8) · [回归测试](../../rbac-web/tests/router.spec.ts#L127) |
| D-18 | DDD 中心超管配置停用系统的角色时，读取目标系统的权限快照会返回 401。 | 已解决 | 中心超管可维护停用系统的角色配置，实际访问和新增角色指派仍检查系统状态。指派改为 `409 ROLE_DISABLED` 后，原审计入口漏掉了这类拒绝，现已补记 `AUTHZ_DENIED` 并保留操作者会话。普通管理员继续按自身快照校验上界。[DDD 上界](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L196) · [指派](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/DirectoryApplicationService.java#L308) · [审计回归](../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/DddAuditIntegrationTest.java#L205) |
| D-19 | 普通管理员编辑停用系统的角色授权时，两版结果不同。 | 已解决 | 两版在修改前检查目标系统状态，普通管理员收到 `403 PRIVILEGE_ESCALATION` 并保留中心会话。中心超管仍可维护停用系统的角色配置。[Structured 校验](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L305) · [DDD 校验](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L209) |
| D-20 | 非超管重新启用系统时，两版的授权上界规则不同。 | 已解决 | 两版在保存启用状态前计算即将生效的角色闭包，将其中的权限和身份标记与操作者变更前的上界比较。超出上界返回 403，空授权的系统仍可由具备系统编辑权的普通管理员恢复。[Structured 恢复](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L101) · [DDD 恢复](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L218) |
| D-21 | 自定义角色代查权限时，DDD 额外要求用户查看权限。 | 未解决 | 共享契约和 Structured 使用 `rbac:authz:invoke` 与部门范围。DDD 代查他人还要求 `rbac:user:view`。种子管理员同时拥有这两项权限。[共享契约](../../docs/implementation-contract.adoc#L111) · [Structured SIM](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ResourceService.java#L274) · [DDD SIM](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/interfaces/rest/AuthorizationController.java#L52) |
| D-22 | DDD 模拟结果的请求追踪 ID 曾和日志 ID 不一致。 | 已解决 | 模拟 `check` 和 `explain` 的响应复用日志切面放在 MDC 中的 `traceId`。[DDD 响应](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/interfaces/rest/AuthorizationController.java#L62) · [日志关联回归](../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/application/AuthorizationSecurityRegressionTest.java#L359) |
| D-23 | 详情刷新失败时，共享页面曾继续显示上一次有效权限。 | 已解决 | 重新加载用户或角色详情前清除旧的有效权限结果。请求失败时显示错误和重试入口，成功重试后显示新结果。[角色详情](../../rbac-web/src/views/RoleWorkspace.vue#L44) · [用户详情](../../rbac-web/src/views/UserWorkspace.vue#L78) · [刷新与重试回归](../../rbac-web/tests/user-workspace.spec.ts#L47) |
| D-24 | DDD 可保存停用的固定中心 API 或额外的中心登记，影响切换到 Structured。 | 已解决 | 两版写入和启动核验以共享 OpenAPI 为准，固定中心登记须启用，并保持 HTTP 方法、路径、权限、身份角色及访问策略一致。额外或不匹配的登记在保存前返回 400。[DDD 保存](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/ResourceApplicationService.java#L147) · [DDD 启动核验](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/infrastructure/config/RouteContractVerifier.java#L46) · [Structured 保存](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ResourceService.java#L175) |
| D-25 | 两版课程 API 配置使用的授权上界和空条件校验不同。 | 已解决 | 保存授权条件时，两版检查非超管是否拥有所选 API 权限和身份标记。此前补上的校验误拦了 DDD 仅停用已有课程 API 的请求，现已恢复该操作并允许重试同一停用请求。带授权条件的操作重新启用或同时修改其他字段时仍校验上界，固定中心 API 仍受登记冻结规则保护。停用系统的条件绑定返回业务 403。缺少两项条件的 `PROTECTED` 操作返回 `400 API_REQUIREMENT_MISSING`，空白的 `requiredRole` 返回 `400 VALIDATION_ERROR`。[DDD 保存](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/ResourceApplicationService.java#L147) · [DDD 回归](../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/application/ResourceOperationConsistencyTest.java#L139) · [Structured 保存](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ResourceService.java#L174) |
| D-26 | 管理页的非标准路径可能跳过前端菜单权限校验。 | 已解决 | 路由守卫按匹配到的页面记录将地址转成登记路径，并保留查询参数和锚点。大小写及尾斜杠变体执行同一套菜单权限检查和刷新失败处理。[路由守卫](../../rbac-web/src/router/index.ts#L30) · [回归测试](../../rbac-web/tests/router.spec.ts#L52) |
| D-27 | Structured 的 S03 时序图缺少正常响应，并混入事务外快照分支。 | 已解决 | 补齐正常及停用目标的快照转换与 HTTP 200。事务外的缓存和版本检查放在独立调用段，保留重试和 503 出口。回复用虚线空心箭头，调用箭头对齐执行条顶边。[源码](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L568-L579) · [PUML](../../docs/design/S03-structured-sequence.puml) · [SVG](../../docs/design/S03-structured-sequence.svg) · [PNG](../../docs/design/S03-structured-sequence.png) |
| D-28 | 更换中心按钮资源绑定的 UI 权限后，有新权限的用户仍看不到该按钮。 | 已解决 | 用启用的按钮资源码定位页面操作，再检查当前绑定的 UI 权限。权限重绑保留同一资源 ID 对应的 API 映射。内置按钮改资源码时需同步调整前端操作使用的资源码。[按钮判断](../../rbac-web/src/stores/auth.ts) · [映射约定](../implementation-contract.adoc) · [回归测试](../../rbac-web/tests/auth.spec.ts) |
| D-29 | 在用户详情原地刷新权限时，新获指派权限的管理员看得到表单，但角色选项为空。 | 已解决 | 权限刷新开放指派表单时，为当前用户及目标系统加载可指派角色。失去指派权限时清理选项和选择。[用户详情](../../rbac-web/src/views/UserWorkspace.vue) · [回归测试](../../rbac-web/tests/user-workspace.spec.ts) |
| D-30 | SIM 的 API 搜索提示声称支持权限码，解释步骤也有未翻译的状态码。 | 已解决 | API 目录提示使用操作标识或路径，UI 目录提示使用权限名称或权限码。解释步骤按两版实际返回的状态码显示中文。[鉴权解释页](../../rbac-web/src/views/AuthorizationView.vue) · [回归测试](../../rbac-web/tests/authorization-view.spec.ts) |
| D-31 | 有效权限筛选后刷新为空集合时，筛选框消失，页面仍显示「无匹配权限」。 | 已解决 | 原始权限集合为空时显示「暂无有效权限」。有权限但筛选无结果时显示「无匹配权限」。[有效权限组件](../../rbac-web/src/components/EffectivePermissions.vue) · [回归测试](../../rbac-web/tests/components.spec.ts) |
| D-32 | 删除用户或角色分页末页最后一项后，自动回退页加载失败未进入保存后的刷新提示。 | 已解决 | 用户和角色列表把回退页查询结果纳入重载结果，失败时显示保存已完成及刷新失败提示。[用户界面](../../rbac-web/src/views/UserWorkspace.vue) · [角色界面](../../rbac-web/src/views/RoleWorkspace.vue) · [回归测试](../../rbac-web/tests/user-workspace.spec.ts) |
| D-33 | 从角色详情退役角色后，旧详情刷新收到 404，页面继续展示旧资料和操作。 | 已解决 | 成功后退出已退役角色的详情，保留当前系统并刷新列表。失败时留在详情显示错误。[角色详情](../../rbac-web/src/views/RoleWorkspace.vue) · [回归测试](../../rbac-web/tests/user-workspace.spec.ts) |

### D-17 前端复核补充

已解决工作台单独探测失败时误报 403 的场景。`getDashboard` 遇到网络错误或 503 时，打开工作台或在工作台手动刷新会进入权限刷新失败页，并保留原地址供重试。明确拒绝仍显示 403，其他页面可使用本轮正常取得的菜单权限。[权限刷新](../../rbac-web/src/stores/auth.ts) · [路由守卫](../../rbac-web/src/router/index.ts) · [回归测试](../../rbac-web/tests/router.spec.ts)

定向测试仍复现两个场景。工作台已打开时，自动刷新遇到 503 会清空概览，页面缺少重试入口。手动刷新仍在等待时，用户点击有权访问的其他菜单，工作台的失败跳转可能中断这次导航。这两条路径尚未修复。[页面刷新](../../rbac-web/src/layouts/AppLayout.vue) · [概览加载](../../rbac-web/src/views/DashboardView.vue)

## 本轮范围复核

- **默认管理员代查。** `root` 的中心超级管理员身份通过两版的具体权限检查。种子 `RBAC_ADMIN` 同时持有 `rbac:authz:invoke` 和 `rbac:user:view`，两版都能代查其管理范围内的用户。两版检查范围的差异只影响另行配置了模拟权限、缺少目录查看权限的自定义普通角色。[Structured 校验](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L285-L289) · [DDD 校验](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L104-L113) · [种子权限](../../rbac-contract/src/main/resources/db/migration/V2__rbac1_seed.sql#L648-L657)
- **课程接口的身份角色要求。** 教师接口快照中全部 16 处 `x-required-role` 只使用 `teacher`、`student`、`admin`，`copyCourse` 要求 `teacher`。两版都支持这些身份角色。Structured 额外支持普通内部角色编码，只有实际配置了这个扩展用法的操作才涉及切换差异。[课程接入约定](../../docs/implementation-contract.adoc#L134-L135) · [教师接口快照](../../rbac-contract/src/main/resources/catalog/course-openapi.source.yaml#L1211-L1212) · [DDD 身份角色匹配](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java#L105-L108)
- **D-24 旧库升级。** 旧版 DDD 曾允许保存停用的固定中心 API 或额外中心登记。含这两类记录的旧库会被新版 DDD 的启动校验拒绝，Structured 原先也会拒绝。现有迁移没有处置这些旧记录。共库切换已验证的是中心登记与共享 OpenAPI 一致的数据库。[DDD 启动核验](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/infrastructure/config/RouteContractVerifier.java#L47-L61) · [Structured 启动核验](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/BootstrapService.java#L42-L56)
- **接口输入和错误码。** 两版在目标用户、角色缺失时都返回 404，业务码分别是 Structured 的 `NOT_FOUND` 与 DDD 的 `USER_NOT_FOUND` 或 `ROLE_NOT_FOUND`。外部 UI 权限码超过 128 字符时都返回 400，业务码为 `VALIDATION_ERROR` 或 `INVALID_PERMISSION`。SIM 的共享契约要求字符串 `kind` 和整数 `userId`，DDD 的默认 JSON 绑定允许数字枚举和小数转整数。现有前端发送契约规定的类型。[错误码映射](../../rbac-contract/src/main/java/com/xmu/rbac/contract/ApiErrors.java#L12) · [DDD UI 查询](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/interfaces/rest/IdentityController.java#L114) · [Structured JSON 配置](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/WebConfig.java#L29)
- **课程代理拒绝顺序。** 对签名和有效期正常、已撤销且 audience 为中心的令牌，Structured 先判撤销并返回 401，DDD 先判 audience 并返回 403。两版均拒绝此类请求。[Structured 代理](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/CourseProxyConfiguration.java#L29) · [DDD 代理](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/infrastructure/config/CourseProxyConfiguration.java#L29)

## D-01 的实现对照与验证

事务外，两套实现都先读取数据库授权版本，再按该版本查缓存或计算权限。写入前复查版本，写入后再次复查。写入后读到新版本时，本次调用进入下一轮，旧缓存条目仍对应旧版本的键。三次尝试仍无法取得稳定结果时，两套实现均返回 HTTP 503，Structured 使用 `AUTHORIZATION_UNAVAILABLE`，DDD 使用 `AUTHORIZATION_UNSTABLE`。[DDD 对应实现](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L71-L101)

事务内，两套实现都绕开缓存。Structured 直接从数据库计算快照。DDD 仍执行版本检查与重试，并可在外层只读事务中使用独立的 `READ_COMMITTED` 事务。[Structured 事务分支](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L88-L92) · [DDD 事务入口](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L62-L76) · [DDD 只读事务配置](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationReadTransaction.java#L8-L16)

修复时模拟 Redis 写入期间推进授权版本。修复前，一次推进会返回旧权限，连续推进也会提前返回。修复后，一次推进会触发重试并取得新快照，连续三次推进会返回 503。这两项回归验证以及 Structured 其余 14 项测试均通过。验证范围为模拟与单元测试。

## D-02 的验证

SVG 中 `repository`、`policy`、`reads`、`cache` 的文字起点横坐标一致，PNG 中也已目视确认四项左对齐。绘图器的 19 项测试通过。[图生成器](../../tools/render-rbac1-design.py)

## 并发修复与已有保护

本轮需求要求撤权在提交后的下一次访问生效。写入锁定全局授权版本行，变更和版本推进随同一事务提交。新请求重新校验用户、系统、令牌并读取提交后的事实。D-04、D-05 复核的是已经开始且与变更交叠的事务外点查询。[撤权要求](../../docs/sources/requirements-final.md#L168) · [写入与版本推进](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ResourceService.java#L24-L35) · [令牌校验](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/TokenService.java#L137-L148)

- **D-04。** 中心的固定操作策略受 `CENTER_OPERATION_IMMUTABLE` 保护。Structured 的 SIM 和 S03 管理查询另有外层只读事务。事务外 API 点查询和代理判定现在都通过 `api()` 的版本复查。[中心策略约束](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ResourceService.java#L179-L191) · [SIM 事务](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ResourceService.java#L273-L275) · [代理内判定](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/CourseProxyConfiguration.java#L26-L35)
- **D-05。** 停用同时递增 `tokenVersion`，后续携带旧令牌的请求会被拒绝。本次补上的检查覆盖已通过入口校验、需要重试的事务外快照。[停用写入](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java#L294-L299) · [快照重试](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java#L88-L129)
- **实际课程链路。** 课程后端在进入业务方法前重新向中心查询 API 判定，再取 `/integration/authorities`。这两次请求会重新校验令牌和用户状态。课程后端的 API 查询与代理判定共用修复后的 Structured 判定入口。[课程后端复查](../../course-integration/overlay/backend/src/main/java/cn/edu/xmu/projectmgt/integration/OperationGate.java#L44-L70) · [中心调用](../../course-integration/overlay/backend/src/main/java/cn/edu/xmu/projectmgt/integration/CentralClient.java#L12-L23)
- **D-07、D-14。** DDD 的四种模拟路径共用 `stableSimulation`。稳定的错误返回原状态，授权版本变化期间出现的 4xx 会在下一轮重读。停用结果以 `USER_DISABLED` 或 `SYSTEM_DISABLED` 判拒，调用者仍保留登录会话。[模拟状态回归](../../rbac-ddd/src/test/java/com/xmu/rbac/ddd/application/AuthorizationSecurityRegressionTest.java#L302) · [模拟版本检查](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/application/AuthorizationApplicationService.java#L166)
- **D-10。** 会话编号仍保护换账号后的状态，页面修订号仍保护切换页面或业务范围后的反馈。保存成功触发的新读取覆盖了同一会话、同一页面的旧刷新交叠。[会话保护](../../rbac-web/src/stores/auth.ts#L34-L67) · [页面修订号](../../rbac-web/src/views/workspace.ts#L56-L81)

Structured 两项新增回归在修复前失败、修复后通过。后端定向测试共通过 58 项，前端 44 项测试和类型检查通过。此次验证使用模拟依赖，未运行真实 MySQL 和 Redis 联测。

## 全部修复后的回归

提交前 Java 测试共通过 156 项，覆盖 Structured、DDD、共享契约及代理。前端 46 项测试、类型检查和生产构建通过，验收工具 21 项及绘图器 19 项测试通过。OpenAPI 生成结果与接口约定一致。已目视检查本轮重生成的 S03、S04 和 SIM 时序图。新增回归覆盖权限变更中的暂时缺失、模拟期间停用目标系统及旧刷新迟到失败。本地 Docker 引擎未运行，未执行依赖 MySQL 和 Redis 的集成测试。

D-14 至 D-17 修复后，Java 测试通过 162 项，前端测试通过 49 项，类型检查和生产构建均通过。验收工具的 21 项单元测试、绘图器的 19 项测试和 OpenAPI 生成检查均通过。本地 Docker 引擎未启动，依赖 MySQL 和 Redis 的集成测试未运行。

D-18 的回归在修复前因 `SYSTEM_DISABLED` 失败，修复后通过。更新后的后端测试共通过 164 项。回归覆盖超管保存角色权限、停用系统角色拒绝新指派、停用系统实际访问被拒绝，以及普通管理员不得授予超出自身上界的权限。

D-22 的回归用真实日志切面比对两个模拟入口的响应 ID 和 Logback 记录。D-23 的回归覆盖用户权限读取失败、角色资料读取失败和成功重试。新增用例在修复前复现问题，修复后通过。后端 166 项测试、前端 51 项测试和生产构建通过。

D-19、D-20、D-24、D-25 修复后，183 项常规测试和 6 项 MySQL/Redis 集成测试通过。同一独立 MySQL 库、Redis 和签名密钥上完成 DDD → Structured → DDD 实际切换，切换前签发的令牌继续可用。两版均拦截不合规的中心 API 登记、缺少条件的受保护操作及超出上界的配置和启用请求。

本轮全量复核通过 257 项 Maven 测试，含 MySQL 和 Redis 集成测试。补齐空白身份角色校验后，21 项后端定向测试与打包通过。最新产物通过 53 项 HTTP 验收，Structured 和 DDD 各通过 26 项，另有 1 项比较确认两版的业务响应和最终数据库状态一致。前端 53 项测试、类型检查和生产构建通过。共享 OpenAPI 校验、验收工具 21 项测试、绘图器 19 项测试及 31 条源码调用链检查通过。

2026-11-23 前端复核修复后，72 项 Vitest 测试、TypeScript 类型检查、生产构建和共享 OpenAPI 校验通过。页面回归使用模拟接口响应，本轮未重跑真实后端端到端测试。

补修角色退役并核对按钮映射后，74 项前端测试、类型检查、生产构建和共享 OpenAPI 校验通过。Structured、DDD 各通过 2 项真实浏览器定向测试，覆盖权限重绑时保留映射与按钮，以及退役成功后返回列表并停止读取旧详情。
