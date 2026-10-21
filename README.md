# 基于 RBAC 模型的权限管理系统

> 厦门大学《面向对象分析与设计》(OOAD) 2026 秋季学期课程设计
> 与《软件工程》《JavaEE 平台技术》共用同一选题与分组

本仓库存放本组课程设计的**全部文档、模型与代码**。每次检查前需要提交的「详细设计 + Jacoco 测试报告」均从本仓库导出。

---

## 一、这个项目在做什么

为一家**市政公司**实现**统一授权中心**：公司的 18 个业务系统不自己管权限，而是把「某用户能否对某部门的数据做某操作」交给本系统判定。

**当前阶段（第一次检查）**按课程的 RBAC0 需求清单实现：

- 权限模型只做 **RBAC0**：User — Role — Permission（= Resource × Action），不做角色继承与约束；
- 用**面向功能的结构化设计**，技术栈 Spring MVC + JPA + MySQL + Redis，分层只用 Controller + Service + Mapper；
- 登录、鉴权两个场景；一个用户属于一个部门，但可以在多个部门有权限；临时用户；按分公司 / 部门 / 员工批量授权；多公司；超级管理员越过权限引擎；管理员只能管理自身及以下层级；权限修改后下一次访问即生效。

系统建立在课程提供的三份**真实数据**之上，已全量导入核对：

| 数据 | 导入结果 |
|---|---|
| 部门信息表 | 111 个部门（总公司 → 9 个分公司 + 职能部门 → 下级部门），全部建成 |
| 员工信息表 | 10,000 名员工，全部按「分公司 + 部门」唯一定位到部门 |
| 应用软件功能权限清单 | 18 个业务系统 / 115 个功能模块 / 359 个资源；1,795 个权限（资源 × 五类操作）；五类角色共 3,190 条角色权限 |

外部模拟业务系统 `rbac-mock-biz` 取清单中的两个功能点（发文拟稿与审核、考勤管理），每次操作带着用户令牌调用权限中心，验证权限在业务系统里真实生效。

需求与实现的逐条对照、尚未完成的事项见 [`docs/14-rbac0-gap.md`](docs/14-rbac0-gap.md)。

---

## 二、课程检查节点与对应交付物

> 依据：《课程设计成绩计算办法(V1)-OOAD_Grading_2026》与《课程介绍》

| 时间 | 分数 | 内容要求 | 本仓库对应交付物 |
|---|---|---|---|
| **10/27 – 10/29** | 20 | 前端界面设计、API 设计、数据库设计；用**结构化**方式实现 **RBAC0** 后端 | [`01`](docs/01-srs.md) 需求、[`02`](docs/02-architecture.md) 概要设计、[`03`](docs/03-database.md) 数据库、[`04`](docs/04-api.md) API、[`05`](docs/05-ui.md) 界面、[`06`](docs/06-detail-structured.md) **详细设计**、[`09`](docs/09-test-plan.md) 测试与 Jacoco |
| 11/24 – 11/26 | 30 | 课程介绍中为 RBAC1、结构化与面向对象两种实现 + 部分前端 | 届时以老师当期需求清单为准 |
| 12/22 – 12/24 | 20 | 课程介绍中为 RBAC2/3 + 前端，部分功能函数式 | 同上 |
| 考勤 | −10 | 缺勤超 1/3 −10；超 1/4 −5；超 1/5 −2 | — |

**每次检查前必须提交：详细设计文档 + Jacoco 测试报告**（无法提供报告则提供代码）。

### 检查方式：无领导小组面试

分**个人陈述**与**自由讨论**两阶段。个人陈述阶段，每位同学依次陈述文档中的**一个设计**，讲清：

1. 这个设计的**需求是什么，存在什么问题**
2. 采用**何种设计方法，采用的理由**是什么
3. 展示**实现效果**

六个设计点与讲稿模板见 [`docs/11-defense.md`](docs/11-defense.md)。成绩 = 个人表现 50% + 小组整体表现 50%。

---

## 三、文档导航

| 文档 | 内容 | 主要用于 |
|---|---|---|
| [`00-charter.md`](docs/00-charter.md) | 项目章程、设计点分工、里程碑、完成定义 | 全程 |
| [`01-srs.md`](docs/01-srs.md) | **需求规格说明书**：领域模型、需求总表、用例、数据疑点 | 检查一 ★ |
| [`02-architecture.md`](docs/02-architecture.md) | **概要设计**：质量属性与取舍、三层架构、判定、缓存与失效、认证、部署 | 检查一 ★ |
| [`03-database.md`](docs/03-database.md) | **数据库设计**：ER 图、表结构、多公司与作用域、三份 xlsx 的导入规则与真实结果 | 检查一 ★ |
| [`04-api.md`](docs/04-api.md) | **API 设计**：约定、错误码、45 个接口与所需权限 | 检查一 ★ |
| [`05-ui.md`](docs/05-ui.md) | **前端界面设计**：页面结构、线框图、Dashboard 统计口径；已在 `rbac-web/` 实现 | 检查一 ★ |
| [`06-detail-structured.md`](docs/06-detail-structured.md) | **详细设计 · 结构化**：功能层次图、数据流图、数据字典、处理规约、耦合与内聚 | 检查一 ★ |
| [`09-test-plan.md`](docs/09-test-plan.md) | 测试策略、T01–T22 验收用例与追溯、Jacoco | 检查一 ★ |
| [`11-defense.md`](docs/11-defense.md) | 六个设计点与个人陈述讲稿模板、高频追问 | 每次检查前 ★ |
| [`12-design-log.md`](docs/12-design-log.md) | **设计问题与改正记录**（#15–#29 为本次按审查整改） | 检查二 ★ |
| [`13-deployment.md`](docs/13-deployment.md) | 编译、打包与 Swarm 部署手册，部署后的初始化步骤 | 个人验收 ★ |
| [`14-rbac0-gap.md`](docs/14-rbac0-gap.md) | RBAC0 需求逐条对照、审查问题的处理、遗留事项 | 检查一 ★ |
| [`15-debug-report.md`](docs/15-debug-report.md) | 验收前 Debug 记录：问题复现、定位、修复与验证；RBAC0 验收场景实测结果 | 检查一 ★ |
| [`07`](docs/07-detail-oo.md) / [`08`](docs/08-detail-functional.md) / [`10`](docs/10-paradigm-comparison.md) | 面向对象、函数式、范式对比的早期骨架（**当前阶段不实现**） | 后续检查 |
| [`archive/sql/`](docs/archive/sql/) | 旧版 V1–V3 迁移脚本（含 RBAC1/2/3，已不执行） | 参考 |
| [`rbac-contract/`](rbac-contract/) | Flyway 建表脚本与 OpenAPI 契约（数据库与接口的唯一来源） | 全程 ★ |
| [`data/raw/`](data/raw/) | 课程提供的三份原始 xlsx（**含真实人员信息，不入库**，需自行下载） | 导入 |
| [`intro-spec-coding/`](intro-spec-coding/) | **导论题目 1**（与 RBAC 无关）：Spec Coding 调研报告、PPT 文字稿、选型指南，以及对照实验工程与数据 | 导论 |

---

## 四、技术选型

| 层 | 选型 | 说明 |
|---|---|---|
| 语言 | Java 21 | Jacoco 是课程提交门槛 |
| 构建 | Maven 3.9.9 多模块，仓库自带 `./mvnw` | 依赖走华为云镜像 |
| 后端 | Spring Boot 3.3（Spring MVC） | |
| 持久化 | **Spring Data JPA** + Flyway | 课程规定 JPA，不使用 MyBatis |
| 数据库 | MySQL 8.0 | 组织与授权关系建外键 |
| 缓存 | Redis 7 | 授权数据缓存（版本号在 MySQL，与修改同一事务）、已注销令牌 |
| 认证 | JWT（只证明身份，不带权限）+ BCrypt | |
| 数据导入 | Apache POI | 三份 xlsx，产出异常清单 |
| 测试 | JUnit 5 + MockMvc + ArchUnit + **Jacoco** | 集成测试连真实 MySQL 与 Redis |
| 部署 | Docker + Docker Swarm，华为云 3 台 2 核 2G | 见 [`13-deployment.md`](docs/13-deployment.md) |
| 前端 | Vue 3 + TypeScript + Element Plus + Pinia + ECharts | `rbac-web/`，界面设计见 `05-ui.md`；Nginx 镜像提供页面 |
| 前端测试 | Vitest + Playwright | 单元测试 + 连真实后端的端到端测试 |

### 工程结构

```
rbac-parent/
├── rbac-contract/     Flyway 建表脚本 V1（RBAC0 基线）与 OpenAPI 契约 rbac-api.yaml
├── rbac-structured/   权限中心（:8081），结构化设计
│   └── src/main/java/com/xmu/rbac/structured/
│       ├── controller/   Spring MVC 控制器、按契约统一鉴权的拦截器、统一响应与异常处理
│       ├── service/      处理过程：认证、权限判定、用户 / 角色 / 授权管理、批量、导入、审计、统计
│       ├── mapper/       Spring Data JPA 接口；Redis 访问（授权缓存、已注销令牌）
│       ├── entity/       与表一一对应的记录（只有字段，没有方法）
│       ├── dto/          请求、响应与过程间传递的数据（record）
│       └── config/       装配
├── rbac-mock-biz/     外部模拟业务系统（:8090）：带用户令牌调用 /authz/check，允许才执行
└── rbac-web/          前端（Vue 3 + TypeScript，不在 Maven 里）：15 个页面，菜单与路由由 /menus/me 动态生成
    ├── src/api/          45 个接口的类型与调用、统一错误处理
    ├── src/stores/       登录与权限状态
    ├── src/router/       按菜单注册路由、路由守卫
    ├── src/views/        页面；src/components/ 共用组件（批量授权、部门选择、用户详情……）
    ├── tests/            Vitest 单元测试
    └── e2e/              Playwright 端到端测试与虚构的 xlsx 样本
```

依赖方向、包结构、「不用 MyBatis」「实体没有方法」「Service 无状态」由 `ArchitectureTest` 检查，违反即构建失败。

---

## 五、快速开始

```bash
git clone https://github.com/vintcessun/OOAD.git
cd OOAD

# 测试用的 MySQL 与 Redis（端口与 application-test.yml 一致）
docker run -d --name rbac-test-mysql -p 13306:3306 -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=rbac_test mysql:8.0 --character-set-server=utf8mb4
docker run -d --name rbac-test-redis -p 16379:6379 redis:7-alpine

# 构建：编译 + 69 个测试 + Jacoco 门槛（只需装 JDK 21，Maven 由 ./mvnw 下载）
./mvnw -s deploy/maven-settings-huawei.xml verify          # Windows 用 mvnw.cmd
# Jacoco 报告：rbac-structured/target/site/jacoco/index.html

# 前端：单元测试、类型检查、端到端测试（需要 Node 20+；端到端会自己启动上面打出的 jar）
cd rbac-web && npm ci && npm run typecheck && npm run test:coverage
npx playwright install chromium && npm run e2e
npm run dev                                                # 开发：http://localhost:5173，/api 转发到 8081
cd ..

# 部署：MySQL + Redis + 权限中心 + 外部模拟系统 + 前端
docker compose up -d --build
curl http://localhost:8081/api/v1/actuator/health          # {"status":"UP"}
# 浏览器打开 http://localhost
```

部署后的初始化（详见 [`13-deployment.md`](docs/13-deployment.md)；可在前端页面操作，也可按 [`deploy/rbac-demo.http`](deploy/rbac-demo.http) 顺序调接口）：

1. `root` / `Admin@123` 登录，**先修改口令**（改之前其他接口都返回 40304）；
2. `POST /imports/permissions` 上传功能权限清单；
3. `POST /imports/organization?companyCode=SZ&companyName=市政公司` 上传部门表与员工表；
4. 核对导入报告中的异常清单；
5. 用 `POST /assignments/batch` **人工**分配角色（导入不分配任何角色），例如全公司分配普通员工。

### 当前进度

| 模块 | 状态 |
|---|---|
| `rbac-contract` | ✅ RBAC0 基线 V1；45 个接口的 OpenAPI 契约 |
| `rbac-structured` | ✅ 45 个接口全部实现；T01–T22 验收用例通过；69 个测试，行覆盖率 97%、分支 84% |
| `rbac-mock-biz` | ✅ 发文拟稿 / 审批、考勤（只限本人）调用权限中心 |
| `rbac-web` | ✅ 15 个页面；41 个单元测试、9 个连真实后端的端到端测试 |
| 三台服务器部署 | 🟡 编排已含前端；新版本按组内安排暂未部署（旧库需删库重建） |

### 组员请先做三件事

1. **通读 [`docs/11-defense.md`](docs/11-defense.md)** —— 检查是无领导小组面试，每人要讲**文档中的一个设计**。先了解六个设计点各自在讲什么。
2. **读 [`docs/14-rbac0-gap.md`](docs/14-rbac0-gap.md)** —— 需求逐条对照，以及还没做完的事（服务器部署、老师的测试、StarUML 图）。
3. **把三份 xlsx 放进 `data/raw/`** —— 它们不入库（含一万条真实姓名和手机号），需要自己从课程网站下载。

### 有异议的地方

文档里的每一条决策都写了理由。**如果不同意，直接改文档并说明新理由**，再改代码；凡是改了设计，就在 [`12-design-log.md`](docs/12-design-log.md) 追加一条。

---

## 六、参考资料

1. Craig Larman. *Applying UML and Patterns*, 3rd ed.
2. Alistair Cockburn. *Writing Effective Use Cases*.
3. Edward Yourdon, Larry Constantine. *Structured Design*.（变换分析、事务分析、耦合与内聚）
4. ANSI/INCITS 359-2012, *Role Based Access Control*.
5. 《支付模块需求规格说明书》(PayReqSPEC) —— 本项目需求文档的格式范本。
6. 《面向对象分析与设计》课件 1–4（软件设计概述、需求分析、结构化软件设计、软件体系结构），2026 秋季学期。
