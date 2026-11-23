# RBAC1 改版前的前端验收记录

当前页面、桌面 Chromium 流程和证据见 [桌面前端验收](../docs/acceptance/frontend-redesign.adoc)。以下内容记录改版前的实现与运行结果。

## 合同和页面

已完整读取 `docs/implementation-contract.adoc`、`docs/schema-contract.adoc` 和 `docs/sources/requirements-final.md`。内部响应仅接受字符串 `OK`，分页保持 `page/pageSize/total/items`；身份协议根路径裸响应使用独立 client。

| 需求 | 页面 / 实现 | 验证 |
|---|---|---|
| ID-01/03/07、UI-01 | LoginView、MeView、auth store、双 HTTP client | auth/http/protocol/router.spec.ts |
| ID-04/05/06、ROLE-05/08 | UserWorkspace：数据库分页检索、资料编辑、独立状态/迁部门、分系统指派与来源 | console.spec.ts J01/J02 |
| ROLE-01/02/03/04 | RoleWorkspace + RoleDag SVG：直接权限、继承、多父 DAG、独立状态、明确循环错误 | components.spec.ts、console.spec.ts |
| ROLE-05/06 | CatalogWorkspace 部门树与全系统准入原子替换（保留其它系统） | console.spec.ts 不准入与迁部门回收 |
| RES-01/02/03 | ResourceWorkspace 菜单按钮 / 独立 API / 显式映射 / 覆盖报告 | console.spec.ts 真实 API、UI 与映射配置 |
| AUD-03/06 | CatalogWorkspace 审计过滤分页详情、UTC 时间 | safety.spec.ts、console.spec.ts |
| UI-02/05/06 | 原生响应式表单表格、403/404、简洁三计数首页 | desktop/mobile Playwright 项目 |

管理目标系统只是过滤，不更换管理 JWT audience。菜单、路由、按钮只做精确 UI 权限检查；课程角色码 admin 不通配。所有 mutation 后刷新身份、本人有效权限和菜单；HTTP 403 显示真实错误，正常 allowed:false 不当作服务故障。

## 一期测试替换映射（非静默删安全预期）

| 旧测试/能力 | 新位置 / 处置 |
|---|---|
| http 数字 code、旧统一认证包装 | http.spec.ts、protocol.spec.ts：OK 字符串、两个 client、401/403/404/409/503、裸响应/204 |
| auth superAdmin 通配、scope/selfOnly | auth.spec.ts：精确 has、不允许 admin 通配、撤权刷新替换、会话恢复/清理 |
| router 旧菜单 componentKey/routePath | router.spec.ts：新 UiResourceVO 字段、深层菜单、菜单/按钮区分、无权深链403、未知404 |
| components 旧批量导入/公司/范围指派 | 按冻结需求退役；RecordForm 白名单、类型/密码清理、DAG多父、指令禁用/恢复保留在 components.spec.ts |
| utils 热力图/角色@范围、无时区时间 | 明确废弃；password 边界保留，UTC审计时间和权限码展示由 safety/components.spec.ts 覆盖 |
| M2 骨架切换 e2e | 删除专用 M2 config/测试，最终同一 console.spec.ts 由 E2E_MODULE 选择真实后端执行 |
| e2e Redis FLUSHDB、复用/指定数据库 | 安全 globalSetup 全新库、仅自有库清理，绝不 FLUSHDB；safety.spec.ts 断言危险输入拒绝 |

保留并复用了 `vPerm`、`PermissionCode`、密码策略工具；旧公司/范围/导入/热力图组件已不适配冻结模型，重写。ECharts 依赖及 lockfile 项已移除。覆盖配置的逻辑目录与 lines85/branches80 门槛未放宽、未添加排除。

## 运行

```sh
npm ci
npm run typecheck
npm run test:coverage
npm run build
# 默认 structured:8081；不改业务源码可切 DDD
RBAC_API=http://127.0.0.1:8082 npm run dev
# 先由后端负责人 clean package 对应 jar；环境提供 Java21。
E2E_MODULE=rbac-structured npm run e2e
E2E_MODULE=rbac-ddd npm run e2e
```

Vite dev/preview 同时代理 `/api`、`/auth`、`/.well-known`。不修改 Dockerfile/nginx.conf，容器代理由主代理处理。

E2E 使用真实 MySQL `127.0.0.1:13306`，新建 `rbac1_e2e_<module>_<timestamp>`；默认本机专用 root/root 可通过 E2E_DB_USER/PASSWORD 改。Redis 只能 `127.0.0.1:16379`，每次独立 namespace，不发清理命令。后端默认18081/18082、Vite5174。`JAVA_HOME`/`JAVA_BIN` 和 `PW_CHROMIUM` 由环境指定，源码不含机器路径。虚构强 bootstrap 密码每次生成。审计服务密钥仅给后端进程，不给浏览器。停止后只清理本次创建库和子进程。

日志在用户 Downloads/rbac1-context-extracted/m8-web-*.log；截图/trace 在 test-results，HTML 在 playwright-report。

## 本轮真实结果（2026-11-14）

- `npm run build`：成功。
- `npm run test:coverage`：初次29通过/1失败（同路径导航不会重新执行 guard 的测试前置错误），改成先离开页面再进入后30/30通过；覆盖 lines100%、branches100%，门槛不变。最终新增安全/UTC用例后32/32通过，lines100%、branches100%（6测试文件，0跳过），详见 m8-web-vitest-final.log。
- structured E2E：Java8默认路径首次启动失败；环境指向Java21后现有 jar 启动因 Flyway 重复 V1 失败。需要后端 clean package，不能以旧 target/classes 混合迁移。
- DDD E2E：现有 M2 jar health 启动成功，但根 `/auth/login` 返回404，desktop/mobile均真实失败（2失败，0跳过）。现有 jar 仍带 `/api/v1` context-path，是旧骨架，需最终后端重新打包。
- `E2E_MODULE=unknown npm run e2e`：真实启动即拒绝未知模块，无数据库/后端副作用。
- 实测上述失败 teardown 后 `SHOW DATABASES LIKE 'rbac1_e2e_%'` 返回0个，未遗留本次测试库。
- 以上 RED 不代表完整业务旅程已完成；PC/手机成功业务截图待最终两个后端 jar 可用后重跑，不把失败空白截图当可用验收。
- 未提交 Git。未改后端、部署代理文件。主代理需在后端完成后重新跑两模块完整E2E并关闭真实失败。

## P1 资源隔离修复与最终交接（本节覆盖上文旧 jar 结果）

- `e2e/sandbox.ts` 白名单继承基础系统变量，不继承 Spring/DB/Redis/JVM/Node 注入变量；显式验证 JAVA_HOME 为 Java21，拒绝 JAVA_BIN。
- 后端仅接收自建 SPRING_APPLICATION_JSON，钉死 datasource 与 Flyway 的同一自有新库及凭据、Redis 127.0.0.1:16379、独立 cache namespace、JWT 临时密钥开关与 dev profile、bootstrap/audit 秘密。CLI 只有 jar 路径和 `--spring.config.location=classpath:/`，无密码。
- E2E API 使用原生 fetch `redirect:error`；健康探测也拒绝重定向，端口通过 TCP 绑定探测。除 health 之外等待 readiness，避免 ApplicationRunner 完成前登录 UNINITIALIZED 种子。
- 自有 ChildProcess 句柄跟踪 exit/error，不再用 taskkill/PID；等待退出后才 DROP 本次 guarded 新库。
- 最后补充：先复制 target jar 到本次 mkdtemp 私有目录，再执行副本，退出后删除副本；禁止直接运行 target。复制时检查源 size/mtime 未变，防止发布途中读取。
- 修复 RecordForm 稳定 aria-label、部门选项未到达就打开快照表单的问题。E2E 正常导航使用真实菜单；登出等待204/登录页面，并验证原 bearer 下次401，不再抢在登出完成前硬跳转。
- 测试未跳过，安全断言与 lines85/branches80 门槛保留。

真实执行结果：

1. DDD 最终完整 desktop/mobile **2/2 GREEN**，0跳过，4.4分钟。日志 `Downloads/rbac1-context-extracted/m8-web-p1-e2e-ddd-final.log`；报告 `playwright-report/rbac-ddd/index.html`；PC/手机 DAG、用户、继承UI、审计截图位于 `test-results/rbac-ddd/`。
2. structured 最新完整重跑 **1通过/1失败**：mobile全链路通过；desktop在 HTTP 200 的 `/api/v1/users/{id}/systems/rbac-center/permissions` 中返回上一次测试库的有效权限来源，触发正确的精确断言失败。当前用例权限码后缀 `desktop_1794671602200`，错误响应含前次 `desktop_1794671380147` 的角色/权限。日志 `m8-web-p1-e2e-structured-rerun.log`，报告 `playwright-report/rbac-structured/index.html`，未绕过缓存安全断言。更前一次 desktop通过/mobile20秒无响应超时见 `m8-web-p1-e2e-structured-final-green.log`（文件名不表示整个测试GREEN，实际为1/2）。必须由 main 对最终47项jar重新验证；本轮不声称 structured 2/2 GREEN。
3. 其它真实 RED 日志保留：早期 structured thin jar 无 Main-Class，后续发布后已能启动；部分早期本机接口延迟超过20秒，日志显示 structured login22455ms、DDD授权22180ms；没有因此改后端或放宽权限断言。
4. 最后 Java 临时副本改动后：typecheck通过、Vitest **38/38通过，lines100%、branches100%**，日志 `m8-web-p1-typecheck-handoff.log`、`m8-web-p1-vitest-handoff.log`。此前 build已通过（`m8-web-p1-build-final.log`）。临时副本改动后没有继续测旧jar。
5. 交接检查：`Get-CimInstance Win32_Process -Filter 'ProcessId = 73772'` 无结果，PID73772已不存在，未对其它进程发送信号。专用端口18081/18082/5174均无监听；`rbac1_e2e_%`库剩余0，见 `m8-web-p1-cleanup-handoff.log`。不清Redis，不杀其它进程。

main 后续：发布 `D:/m3-structured-final47.jar` 至约定target，再运行同一配置 `E2E_MODULE=rbac-structured npm run e2e`。启动器将运行私有副本，不再锁target。无需继续扩展UI。本轮仅修改前端非nginx/Dockerfile文件，未提交；工作区已有的这两个部署文件改动属于主代理，未触碰。

## 最终 canonical 完整重跑（2026-11-15，本节为最新结论）

已等待所有测试与 teardown 完成，不存在后台待执行任务；本轮未修改测试安全断言或后端。

| 后端 canonical SHA256 | 桌面 / 手机 | 日志 |
|---|---|---|
| structured `59f773f0e3348feeaa7049c3e12dd31f314dad679452ba907226c17696f2c548`（00:22发布） | **2/2通过，0跳过**；59.6s / 33.5s，总2.5分钟 | `Downloads/rbac1-context-extracted/m8-web-final-canonical-structured.log` |
| DDD `1b410d6b5a31e211d37aec7306a00a41424b49d3fc7899bfb960c6a8cb42a650` | **最终重跑2/2通过，0跳过**；约1.0m / 2.0m，总3.9分钟 | `m8-web-final-canonical-ddd-rerun.log` |

DDD此次第一次运行1通过/1失败（desktop等待本人资料15秒超时），真实RED保留于 `m8-web-final-canonical-ddd.log`；不修改源码/断言，重新完整运行后2/2通过。不能据此声称本机延迟完全稳定。structured本次安全配置下未再出现跨测试库有效权限来源污染。

同一测试覆盖继承多父/环拒绝、跨系统拒绝、菜单/API配置映射、准入指派、普通编辑不夹带状态/部门、角色与用户独立状态、浏览器普通用户继承菜单、真实API、迁部门回收、显式撤权下一请求403、审计、登出撤销401、403/404。

HTML报告：`playwright-report/rbac-structured/index.html`、`playwright-report/rbac-ddd/index.html`。
截图：`test-results/{rbac-structured,rbac-ddd}/` 下 desktop/mobile 各有 `*-dag.png`、`*-users.png`、`*-inherited-ui.png`、`*-audit.png`，共16张。注意截图按现有用例在页面/导航可见后截取，部分含请求仍在加载的瞬间，并非逐像素加载完成断言；UI/API权限结果以真实HTTP及浏览器测试断言为证据。

本次仍采用私有临时jar副本、安全JSON配置、白名单环境与精确自有进程清理。退出后检查 `rbac1_e2e_%` 剩余0个、18081/18082/5174无监听，见 `m8-web-final-cleanup.log`；SHA证据见 `m8-web-final-canonical-sha256.log`。未清Redis、未触碰其它进程、未提交。

## 最终视觉收尾（2026-11-15 02:03，本节为最新截图证据）

本轮只追加必要移动端 CSS、E2E 数据就绪断言及不覆盖旧证据的 runId 目录/日志；没有修改后端、nginx/Dockerfile、业务权限规则，没有提交。

- 截图前断言 main 中“加载中…”消失、无页面请求错误、字体就绪和整页 scrollWidth 不超过 viewport。
- DAG：中心系统 option 已加载且选中；A/B/C 三个目标节点可见；实际 API 中 A→B、C→B 两边存在；SVG 节点数/有向边数与真实 graph 一致后才截图，不能把空 SVG 当成功证据。
- users：姓名更新行已渲染；手机明确断言表容器可横滚，滚到操作列验证编辑按钮在 viewport，再回到首列截图。移动表单元格不逐字换行、最小表宽960px，整页没有横溢，长姓名/账号不会压成极高行。
- inherited-ui：浏览器实际 GET dashboard 返回200/OK，三计数渲染值与 HTTP data 一致且用户数大于0，然后截图。
- audit：等待非空初始数据，再用真实撤权目标过滤，HTTP200、ROLE_UNASSIGNED目标行可见后截图。
- 不使用固定 sleep，不跳过任何安全断言。旧 RED、旧截图和HTML均保留，后续每次运行自动采用唯一 runId。

### 本轮真实结果与选择路径

| 模块 | 完整测试结果 | 唯一 runId | 外部日志 |
|---|---|---|---|
| structured | 最终 **desktop/mobile 2/2通过，0跳过**，42.1s/56.9s，总2.3分钟 | `visual-structured-20261115-020039` | `Downloads/rbac1-context-extracted/m8-web-visual-structured-20261115-020039.log` |
| DDD | **desktop/mobile 2/2通过，0跳过**，约1.1m/1.6m，总3.5分钟 | `visual-ddd-20261115-015638` | `m8-web-visual-ddd-20261115-015638.log` |

structured首次视觉运行 `visual-structured-20261115-015235` 的 desktop通过、mobile在最终再次登录等本人资料15秒超时；真实RED独立保留，未覆盖。未改源码/断言，完整重跑后2/2通过。启动和后端日志亦带runId，不覆盖旧日志。

报告：`playwright-report/{模块}/{runId}/index.html`。
截图根：`test-results/{模块}/{runId}/`；其 `...-desktop/` 和 `...-mobile/` 各有 `{模块}-{desktop|mobile}-{dag|users|inherited-ui|audit}.png`，两模块共16张完整数据截图。main可从这两个GREEN runId选择4～8张复制到docs/acceptance/screenshots；不应选择旧的空图/加载瞬间截图。

已用 read 实际目视确认：DDD desktop-dag 非空且多父箭头和节点可读、DDD inherited-ui 三计数已到达2/9/2、DDD mobile-audit存在真实过滤行、structured mobile-users姓名不再逐字拉高、structured desktop-audit有ROLE_UNASSIGNED/部门调动行。截图只含合成fixture/内置系统管理员，不含密码/令牌/真实人员。

保留一项原有视觉现状而没有扩展UI：普通用户没有system:view权限，继承首页的header仍会显示真实的系统列表403和空管理目标select；首页计数和继承菜单已真实成功，并未靠CSS隐藏403来美化证据。管理员DAG等截图的系统select已明确断言非空。

验证：typecheck、build成功；Vitest 38/38通过，lines100%、branches100%，原85/80门槛未动。日志 `m8-web-visual-typecheck.log`、`m8-web-visual-unit.log`、`m8-web-visual-build.log`。全部teardown完成；测试库0残留、18081/18082/5174无监听，见 `m8-web-visual-cleanup.log`。
