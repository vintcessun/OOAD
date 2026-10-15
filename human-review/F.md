# F 菜单审计统计与工程验证

主审姓名：待填写；交叉复核姓名：待填写；日期：待填写；阅读与复现状态：待完成。

预计16小时，负责8个权限中心API。基线提交：a42b8018d6a9c9051fd527dcae05a8da04746082。

## 阅读顺序

菜单/审计/统计页面 → menuApi/statsApi → CatalogController菜单/StatsController → MenuService/AuditService/DashboardService → 查询聚合 → DTO与展示；另核对测试环境、构建及部署。

各业务测试断言由其负责人认领，F只统筹运行、报告、公共测试结构。关联各业务审计调用，与A/B复核JWT/Redis/代理/时区；共享部署文件仅检查权限中心及控制台相关段。

## 主审文件与代码范围

状态由实际审查者填写。完整阅读指负责范围内的业务代码；依赖锁文件、模型和工作簿按范围栏核对。共享文件仅承担本人片段。

| 文件 | 层级 | 重点代码/范围 | 关联人 | 阅读状态 | 证据 |
| --- | --- | --- | --- | --- | --- |
| [.gitattributes](../../.gitattributes) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [.github/workflows/ci.yml](../../.github/workflows/ci.yml) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [.gitignore](../../.gitignore) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [.mvn/wrapper/maven-wrapper.properties](../../.mvn/wrapper/maven-wrapper.properties) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [Dockerfile](../../Dockerfile) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [deploy/build.sh](../../deploy/build.sh) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [deploy/env.example](../../deploy/env.example) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | A/B | 未读 | 待填写 |
| [deploy/maven-settings-huawei.xml](../../deploy/maven-settings-huawei.xml) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [deploy/rbac-demo.http](../../deploy/rbac-demo.http) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [deploy/setup-server.sh](../../deploy/setup-server.sh) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [deploy/stack.sh](../../deploy/stack.sh) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [docker-compose.yml](../../docker-compose.yml) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | A/B | 未读 | 待填写 |
| [mvnw](../../mvnw) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [mvnw.cmd](../../mvnw.cmd) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [rbac-contract/src/main/resources/openapi/rbac-api.yaml](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml) | 接口契约 | 契约结构与公共schema；各负责人逐API核对权限、参数及响应 | A/B/C/D/E | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/RbacStructuredApplication.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/RbacStructuredApplication.java) | 构建与运行配置 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/StatsController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/StatsController.java) | 入口与响应 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/StatsDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/StatsDtos.java) | 输入输出 | 统计类型F；AuthzRequest B；重点：完整阅读参数、字段、调用方及状态变化 | B | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/AuditLog.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/AuditLog.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Menu.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/Menu.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/entity/package-info.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/entity/package-info.java) | 数据模型 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/AuditLogMapper.java) | 查询与持久化 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/MenuMapper.java) | 查询与持久化 | 完整阅读参数、字段、调用方及状态变化 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java) | 业务与事务 | 独立审计事务及事件F；各业务人复核实际调用时机与内容；重点：record、recordDecision、search、REQUIRES_NEW、truncate | A/B/C/D/E | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/DashboardService.java) | 业务与事务 | metrics、heatmap、percentileMs/percentileOffset、ratio与时间窗 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/MenuService.java) | 业务与事务 | myMenus、tree/build、apply、createsCycle、增改删 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/main/resources/application.yml](../../rbac-structured/src/main/resources/application.yml) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | A/B | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java) | 后端测试 | 架构约束及规则证明边界 | 按问题安排 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java) | 后端测试 | 契约结构、权限声明、双向路由核对；A复核入口语义 | A | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java) | 后端测试 | 公共测试结构F；具体用例按test-matrix逐方法分配，不要求F重读各业务断言 | A/B/C/D/E | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/IntegrationTestBase.java) | 后端测试 | 隔离环境、快照和辅助方法；C/E复核V3自增起点恢复 | A/B/C/D/E | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java) | 后端测试 | 公共测试结构F；各业务人按test-matrix主审断言及目标分支 | A/B/C/D/E | 未读 | 待填写 |
| [rbac-structured/src/test/resources/application-test.yml](../../rbac-structured/src/test/resources/application-test.yml) | 后端测试 | 测试数据库及Redis隔离；身份、版本、编号测试环境 | A/B/C/E | 未读 | 待填写 |
| [rbac-web/.dockerignore](../../rbac-web/.dockerignore) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/Dockerfile](../../rbac-web/Dockerfile) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/e2e/console.spec.ts](../../rbac-web/e2e/console.spec.ts) | 端到端测试与样本 | 隔离运行与执行顺序F；每一步业务预期归对应负责人 | A/B/C/D/E | 未读 | 待填写 |
| [rbac-web/e2e/global-setup.ts](../../rbac-web/e2e/global-setup.ts) | 端到端测试与样本 | 隔离运行与执行顺序F；每一步业务预期归对应负责人 | A/B/C/D/E | 未读 | 待填写 |
| [rbac-web/index.html](../../rbac-web/index.html) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/nginx.conf](../../rbac-web/nginx.conf) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | A/B | 未读 | 待填写 |
| [rbac-web/playwright.config.ts](../../rbac-web/playwright.config.ts) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/App.vue](../../rbac-web/src/App.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/api/index.ts](../../rbac-web/src/api/index.ts) | 前端交互 | A auth；B authz；C user/assignment；D role/catalog；E org/import；F menu/stats与公共部分 | A/B/C/D/E | 未读 | 待填写 |
| [rbac-web/src/api/types.ts](../../rbac-web/src/api/types.ts) | 前端交互 | A auth；B authz；C user/assignment；D role/catalog；E org/import；F menu/stats与公共部分 | A/B/C/D/E | 未读 | 待填写 |
| [rbac-web/src/directives/perm.ts](../../rbac-web/src/directives/perm.ts) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/env.d.ts](../../rbac-web/src/env.d.ts) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/layouts/AppLayout.vue](../../rbac-web/src/layouts/AppLayout.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/main.ts](../../rbac-web/src/main.ts) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/styles.css](../../rbac-web/src/styles.css) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/utils/format.ts](../../rbac-web/src/utils/format.ts) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/utils/heatmap.ts](../../rbac-web/src/utils/heatmap.ts) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/AuditLogView.vue](../../rbac-web/src/views/AuditLogView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/DashboardView.vue](../../rbac-web/src/views/DashboardView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/HeatmapView.vue](../../rbac-web/src/views/HeatmapView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/src/views/MenuListView.vue](../../rbac-web/src/views/MenuListView.vue) | 前端交互 | 组件状态、请求参数、权限控制、错误反馈、请求乱序与刷新 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/tests/components.spec.ts](../../rbac-web/tests/components.spec.ts) | 前端测试 | 公共逻辑与环境主审；组件业务断言由各业务负责人按任务书认领 | A/B/C/D/E | 未读 | 待填写 |
| [rbac-web/tests/fixtures.ts](../../rbac-web/tests/fixtures.ts) | 前端测试 | 公共逻辑与环境主审；组件业务断言由各业务负责人按任务书认领 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/tests/setup.ts](../../rbac-web/tests/setup.ts) | 前端测试 | 公共逻辑与环境主审；组件业务断言由各业务负责人按任务书认领 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/tests/utils.spec.ts](../../rbac-web/tests/utils.spec.ts) | 前端测试 | 公共逻辑与环境主审；组件业务断言由各业务负责人按任务书认领 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/tsconfig.json](../../rbac-web/tsconfig.json) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |
| [rbac-web/vite.config.ts](../../rbac-web/vite.config.ts) | 构建与运行配置 | 权限中心、数据库、缓存及控制台相关构建、测试与运行配置；其余服务段不审查 | 按问题安排 | 未读 | 待填写 |

## 必须关联阅读

| 文件 | 主审 | 关联范围 | 状态 | 证据 |
| --- | --- | --- | --- | --- |
| [pom.xml](../../pom.xml) | D | 权限中心/契约依赖及版本锁定；传递依赖按异常差异抽查；不检查外部模拟模块功能 | 未读 | 待填写 |
| [rbac-contract/pom.xml](../../rbac-contract/pom.xml) | D | 权限中心/契约依赖及版本锁定；传递依赖按异常差异抽查；不检查外部模拟模块功能 | 未读 | 待填写 |
| [rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql](../../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql) | E | DDL/FK/唯一键/索引及初始化E；各业务人复核对应表和初始化 | 未读 | 待填写 |
| [rbac-contract/src/main/resources/db/migration/V2__grant_version.sql](../../rbac-contract/src/main/resources/db/migration/V2__grant_version.sql) | B | 授权版本结构及初始化B；迁移E；运行验证F | 未读 | 待填写 |
| [rbac-contract/src/main/resources/db/migration/V3__reserve_internal_user_ids.sql](../../rbac-contract/src/main/resources/db/migration/V3__reserve_internal_user_ids.sql) | C | 内部1—9999及员工/临时用户10000起；已有库迁移E；测试重置F | 未读 | 待填写 |
| [rbac-structured/pom.xml](../../rbac-structured/pom.xml) | D | 权限中心/契约依赖及版本锁定；传递依赖按异常差异抽查；不检查外部模拟模块功能 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/config/AppConfig.java) | A | 认证和装配A；Clock、Redis与运行语义B/F；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java) | E | 组织与导入E；系统资源操作权限D；菜单F；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java) | D | 目录D；组织E；菜单F；重点：完整阅读参数、字段、调用方及状态变化 | 未读 | 待填写 |
| [rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java) | B | 判定、范围与失效B；C/D/E复核各自调用；审计副作用F；重点：decide、subjectDenyReason、covers、loadGrants、scopeOf、requireInScope、bumpGrantVersion | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ExcelImportIntegrationTest.java) | E | 四个导入测试E；B失效、C编号归属、D模板同步、F环境 | 未读 | 待填写 |
| [rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java](../../rbac-structured/src/test/java/com/xmu/rbac/structured/mapper/RedisFailureTest.java) | B | 授权缓存B；注销失败A；环境与报告F | 未读 | 待填写 |
| [rbac-web/e2e/fixtures/departments.xlsx](../../rbac-web/e2e/fixtures/departments.xlsx) | E | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | 未读 | 待填写 |
| [rbac-web/e2e/fixtures/employees.xlsx](../../rbac-web/e2e/fixtures/employees.xlsx) | E | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | 未读 | 待填写 |
| [rbac-web/e2e/fixtures/generate.py](../../rbac-web/e2e/fixtures/generate.py) | E | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | 未读 | 待填写 |
| [rbac-web/e2e/fixtures/permissions.xlsx](../../rbac-web/e2e/fixtures/permissions.xlsx) | E | 导入样本及生成脚本；使用虚构数据，工作簿按内容核对而非逐字节阅读 | 未读 | 待填写 |
| [rbac-web/package-lock.json](../../rbac-web/package-lock.json) | D | 权限中心/契约依赖及版本锁定；传递依赖按异常差异抽查；不检查外部模拟模块功能 | 未读 | 待填写 |
| [rbac-web/package.json](../../rbac-web/package.json) | D | 权限中心/契约依赖及版本锁定；传递依赖按异常差异抽查；不检查外部模拟模块功能 | 未读 | 待填写 |

## API责任

| 卡号 | API | 功能 | 状态 |
| --- | --- | --- | --- |
| [F01](api-cards/F.md#f01) | GET /permissions/heatmap | 权限使用热力图（业务系统 × 操作，成功鉴权次数） | 未审查 |
| [F02](api-cards/F.md#f02) | GET /menus/me | 当前用户可见的菜单树（按「菜单资源:view」动态生成） | 未审查 |
| [F03](api-cards/F.md#f03) | GET /menus | 完整菜单树 | 未审查 |
| [F04](api-cards/F.md#f04) | POST /menus | 新增菜单 | 未审查 |
| [F05](api-cards/F.md#f05) | PATCH /menus/{id} | 修改菜单 | 未审查 |
| [F06](api-cards/F.md#f06) | DELETE /menus/{id} | 删除菜单（有子菜单时 409） | 未审查 |
| [F07](api-cards/F.md#f07) | GET /audit-logs | 审计日志：谁、什么时候、在哪里、对谁、做了什么、结果如何、为什么 | 未审查 |
| [F08](api-cards/F.md#f08) | GET /dashboard | Dashboard 十一项指标 | 未审查 |

## 人工反例清单

- [ ] 动态菜单、按钮和后端权限是否一致，权限变化后是否刷新？
- [ ] ACCESS_DENIED的产生、展示与筛选是否一致？
- [ ] 独立审计SUCCESS写入后主事务失败，该记录如何解释？
- [ ] 审计独立事务是否引起锁等待，审计失败是否影响业务？
- [ ] 管理拒绝是否误计入异常访问？
- [ ] 空数据、跨日、时区、负分页、百分位是否有独立预期？
- [ ] 测试快照恢复是否补回V3自增起点，是否掩盖已有库升级问题？
- [ ] final字段、无继承及覆盖率排除分别能证明什么，不能证明什么？

## 对应测试方法

以下为断言审查责任，不代表测试已运行；跨业务测试由相关人复核自己的片段。

| 测试 | 状态 | 分支到达/最终状态证据 |
| --- | --- | --- |
| [ArchitectureTest.java.onlyTheThreeLayersAndHelpers](../../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L30) | 未读 | 待填写 |
| [ArchitectureTest.java.dependenciesPointDownward](../../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L39) | 未读 | 待填写 |
| [ArchitectureTest.java.noMyBatis](../../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L56) | 未读 | 待填写 |
| [ArchitectureTest.java.entitiesAreDataOnly](../../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L63) | 未读 | 待填写 |
| [ArchitectureTest.java.servicesAreStateless](../../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L73) | 未读 | 待填写 |
| [ArchitectureTest.java.servicesDoNotInherit](../../rbac-structured/src/test/java/com/xmu/rbac/structured/ArchitectureTest.java#L82) | 未读 | 待填写 |
| [ContractRoutesTest.java.readsAllOperationsOfTheContract](../../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L30) | 未读 | 待填写 |
| [ContractRoutesTest.java.onlyLoginIsAnonymous](../../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L35) | 未读 | 待填写 |
| [ContractRoutesTest.java.everyOperationDeclaresPermissionUnlessAnonymous](../../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L44) | 未读 | 待填写 |
| [ContractRoutesTest.java.permissionCodesExistInBaselineMigration](../../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L60) | 未读 | 待填写 |
| [ContractRoutesTest.java.unimplementedReportsContractOnlyRoutes](../../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L73) | 未读 | 待填写 |
| [ContractRoutesTest.java.undeclaredReportsMissingAndPermissionlessRoutes](../../rbac-structured/src/test/java/com/xmu/rbac/structured/controller/ContractRoutesTest.java#L81) | 未读 | 待填写 |
| [AcceptanceTest.java.t18_hiddenMenuDoesNotMeanTheApiIsOpen](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/AcceptanceTest.java#L451) | 未读 | 待填写 |
| [ManagementApiTest.java.menuManagementAndDynamicMenus](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L467) | 未读 | 待填写 |
| [ManagementApiTest.java.auditDashboardAndHeatmapComeFromRealEvents](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L520) | 未读 | 待填写 |
| [ManagementApiTest.java.abnormalAuditFilterMatchesDashboardCount](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L559) | 未读 | 待填写 |
| [ManagementApiTest.java.deniedAndFailedManagementOperationsAreAudited](../../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java#L587) | 未读 | 待填写 |

## 交付检查

- [ ] 文件和关键方法已读并留证据。
- [ ] 全部API卡填写独立预期、间接调用、最终查询及副作用。
- [ ] 业务测试核对关键断言与拒绝原因，未执行明确记录。
- [ ] 问题分类完成，重要结论由另一人挑战。

返回[总入口](README.md)，使用[模板](templates.md)与[复核记录](cross-review.md)。
