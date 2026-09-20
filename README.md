# 基于 RBAC 模型的高负载大并发权限管理系统

> 厦门大学《面向对象分析与设计》(OOAD) 2026 秋季学期课程设计
> 与《软件工程》《JavaEE 平台技术》共用同一选题与分组

本仓库存放本组课程设计的**全部文档、模型与代码**。三次检查前需要提交的「详细设计 + Jacoco 测试报告」均从本仓库导出。

---

## 一、这个项目在做什么

为一家**市政工程公司**实现**统一授权中心（Authorization Center）**：该公司的 18 个业务系统不自己管权限，而是把「某用户能否对某资源做某操作」这个问题交给本系统回答。

系统建立在课程提供的三份**真实数据**之上：

| 数据 | 规模 |
|---|---|
| 部门信息表 | **111 个部门**，三层单根（总公司 → 9 分公司 + 10 职能部门 → 子部门） |
| 员工信息表 | **10,000 名员工**，含分公司、部门、岗位 |
| 应用软件功能权限清单 | **18 个软件系统 / 115 个功能模块 / 359 个功能点 × 5 个角色** |

展开后约 **1,795 条原子权限**、**1,800 条角色授予**。系统不实现那 18 个业务系统，只从清单中选取两个功能点作为**受保护业务桩**来验证权限真实生效。

系统按 RBAC 标准模型（ANSI/INCITS 359）分三个迭代逐级增强：

| 迭代 | 模型 | 核心能力 |
|---|---|---|
| 迭代一 | RBAC0 | 用户 → 角色 → 权限的基本授权 |
| 迭代二 | RBAC1 | RBAC0 + 角色继承（层级角色） |
| 迭代三 | RBAC2/3 | RBAC1 + 约束（互斥角色、基数、先决条件角色） |

并且用**三种设计范式**分别实现同一套需求，用于横向对比：**结构化（面向功能）设计 / 面向对象设计 / 函数式设计**。

---

## 二、课程检查节点与对应交付物

> 依据：《课程设计成绩计算办法(V1)-OOAD_Grading_2026》与《课程介绍》

| 时间 | 分数 | 内容要求 | 本仓库对应交付物 |
|---|---|---|---|
| **10/27 – 10/29** | 20 | 前端界面设计、API 设计、数据库设计；用**结构化**方式实现 **RBAC0** 后端 | [`docs/01-srs.md`](docs/01-srs.md)、[`docs/02-architecture.md`](docs/02-architecture.md)、[`docs/03-database.md`](docs/03-database.md)、[`docs/04-api.md`](docs/04-api.md)、[`docs/05-ui.md`](docs/05-ui.md)、[`docs/06-detail-structured.md`](docs/06-detail-structured.md) |
| **11/24 – 11/26** | 30 | 分别用**结构化和面向对象**实现 **RBAC1** 后端 + 部分前端 | 上述文档增补 + [`docs/07-detail-oo.md`](docs/07-detail-oo.md) |
| **12/22 – 12/24** | 20 | 分别用结构化和面向对象实现 **RBAC2/3** 后端 + 前端；**部分功能采用函数式设计** | + [`docs/08-detail-functional.md`](docs/08-detail-functional.md)、[`docs/10-paradigm-comparison.md`](docs/10-paradigm-comparison.md) |
| 考勤 | −10 | 缺勤超 1/3 −10；超 1/4 −5；超 1/5 −2 | — |

**每次检查前必须提交：详细设计文档 + Jacoco 测试报告**（无法提供报告则提供代码）。见 [`docs/09-test-plan.md`](docs/09-test-plan.md)。

### 检查方式：无领导小组面试

分**个人陈述**与**自由讨论**两阶段。个人陈述阶段，每位同学依次陈述文档中的**一个设计**，必须讲清楚：

1. 这个设计的**需求是什么，存在什么问题**
2. 采用**何种设计方法，采用的理由**是什么
3. 展示**实现效果**

> **本仓库所有设计章节都按这个结构撰写**，见 [`docs/11-defense.md`](docs/11-defense.md) 的讲稿模板。
> 成绩 = 个人表现 50% + 小组整体表现 50%。

---

## 三、文档导航

| 文档 | 内容 | 主要用于 |
|---|---|---|
| [`00-charter.md`](docs/00-charter.md) | 项目章程、工作分解、里程碑、完成定义(DoD)、协作规范 | 全程 |
| [`01-srs.md`](docs/01-srs.md) | **需求规格说明书**（领域模型、用例总图、用例表、性能需求、**数据疑点附录 C**） | 检查一 ★ |
| [`02-architecture.md`](docs/02-architecture.md) | **概要设计**：分层架构、三范式模块划分、授权内核、缓存与失效 | 检查一 ★ |
| [`03-database.md`](docs/03-database.md) | **数据库设计**：ER 图、表结构、索引、**三份 xlsx 的导入设计** | 检查一 ★ |
| [`04-api.md`](docs/04-api.md) | **API 设计**：统一响应、错误码、全部接口契约 | 检查一 ★ |
| [`05-ui.md`](docs/05-ui.md) | **前端界面设计**：信息架构、页面清单、线框图、交互约定 | 检查一 ★ |
| [`06-detail-structured.md`](docs/06-detail-structured.md) | **详细设计 · 结构化（面向功能）** | 检查一 ★ |
| [`07-detail-oo.md`](docs/07-detail-oo.md) | 详细设计 · 面向对象 | 检查二 |
| [`08-detail-functional.md`](docs/08-detail-functional.md) | 详细设计 · 函数式 | 检查三 |
| [`09-test-plan.md`](docs/09-test-plan.md) | 测试计划、Jacoco 覆盖率门槛、压测方案 | 全程 ★ |
| [`10-paradigm-comparison.md`](docs/10-paradigm-comparison.md) | **三种范式对比分析**（本项目的差异化亮点） | 检查三 ★ |
| [`11-defense.md`](docs/11-defense.md) | 六个设计点与个人陈述讲稿模板 | 每次检查前 ★ |
| [`diagrams/`](diagrams/) | StarUML 模型源文件 (.mdj) 与导出图 | 全程 |
| [`rbac-contract/`](rbac-contract/) | **已落地的契约产物**：Flyway 建表脚本、OpenAPI 规范 | 全程 ★ |
| [`data/raw/`](data/raw/) | 课程提供的三份原始 xlsx（**含真实人员信息，不入库**，需自行下载） | 全程 |

---

## 四、技术选型

| 层 | 选型 | 说明 |
|---|---|---|
| 语言 | **Java 17** | Jacoco 是课程提交门槛，必须 JVM 系 |
| 构建 | Maven 多模块 | 三种范式各成一个模块，见下 |
| 后端框架 | Spring Boot 3.x | 与《JavaEE 平台技术》课程一致 |
| 数据库 | MySQL 8.0 | |
| 缓存 | Redis / Caffeine 两级 | 支撑「高负载大并发」的性能需求 |
| 前端 | Vue 3 + TypeScript + Element Plus | 单套前端，通过切换 baseURL 对接不同范式后端 |
| 数据导入 | Apache POI | 解析三份 xlsx，产出导入异常报告 |
| 测试 | JUnit 5 + Mockito + **Jacoco** | 覆盖率门槛见测试计划 |
| 压测 | JMeter / wrk | 验证 §性能需求 的 QPS 与 P99 指标 |
| 建模 | **StarUML** | 课程指定，源文件存 `diagrams/` |

### 已落地的契约产物

文档不是空谈——下列两份文件已存在，是数据库与接口的**单一事实来源**，与文档不一致时以文件为准：

| 文件 | 内容 |
|---|---|
| [`rbac-contract/.../db/migration/V1__rbac0_baseline.sql`](rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql) | 迭代一 14 张表 + 操作词汇表 + 7 个内置角色 + 岗位→角色配置表及其 5 条规则 |
| [`V2__rbac1_hierarchy.sql`](rbac-contract/src/main/resources/db/migration/V2__rbac1_hierarchy.sql) | 迭代二：角色继承、菜单、导入任务与异常明细 |
| [`V3__rbac2_constraints.sql`](rbac-contract/src/main/resources/db/migration/V3__rbac2_constraints.sql) | 迭代三：约束体系、会话；含 6 条演示约束（覆盖三类基数约束） |
| [`openapi/rbac-api.yaml`](rbac-contract/src/main/resources/openapi/rbac-api.yaml) | 59 个操作、40 个 schema、43 个错误码。两种范式实现必须满足同一份契约 |

三个 SQL 脚本按迭代编号，本身就清晰展示了数据模型随 RBAC0 → RBAC1 → RBAC2/3 的演进，可直接作为答辩材料。

### 「分别用结构化和面向对象实现」的工程结构

```
rbac-parent/
├── rbac-contract/      公共契约：DTO、错误码、OpenAPI 规范（两种实现共用，保证可比性）
├── rbac-structured/    结构化（面向功能）实现：过程 + 记录 + JDBC，不使用多态
├── rbac-oo/            面向对象实现：领域模型 + GRASP + 设计模式
├── rbac-functional/    函数式授权内核：不可变数据 + 纯函数组合
├── rbac-test-suite/    契约一致性测试集：同一套用例对三种实现分别执行
└── rbac-bench/         压测与对比基准
```

两种实现**共用同一份数据库 schema 与同一份 API 契约**，因此同一套集成测试和同一套前端可以同时验证两者 —— 这既是范式对比的实验基础，也是 Jacoco 报告的统一出口。

---

## 五、快速开始

```bash
git clone https://github.com/vintcessun/OOAD.git
cd OOAD
```

代码尚未开工，当前阶段为**文档与设计**。开工顺序见 [`docs/00-charter.md`](docs/00-charter.md) 的里程碑表。

### 组员请先做三件事

1. **通读 [`docs/11-defense.md`](docs/11-defense.md)** —— 检查是无领导小组面试，每人要讲**文档中的一个设计**，并讲清「需求是什么 / 存在什么问题 / 为什么这么设计 / 效果如何」。先了解这六个设计点各自在讲什么。
2. **看 [`docs/01-srs.md`](docs/01-srs.md) 附录 C** —— 老师给的三份数据里我们发现了 12 处疑点（中英文混用、空单元格、同名经理、岗位没有对应角色等），以及 12 个架构级待确认问题。**答疑课要问的就是这些。**
3. **把三份 xlsx 放进 `data/raw/`** —— 它们不入库（含一万条真实姓名和手机号），需要自己从课程网站下载。

### 岗位→角色映射先用配置表

员工表只有「岗位」没有「角色」，两者的映射是我们推断的。已做成配置表 `sys_position_role_mapping`（见 `V1__rbac0_baseline.sql`），5 条规则按优先级匹配。**老师若给出不同答案，只需 UPDATE 这张表后重跑派生，不改一行代码。**

### 有异议的地方

文档里的每一条决策都写了理由。**如果不同意，直接改文档并说明新理由**，不要私下按自己的想法实现——三份实现必须行为等价，不一致会让整个对比实验失效。

---

## 六、参考资料

1. Craig Larman. *Applying UML and Patterns*, 3rd ed. （GRASP、用例驱动开发）
2. Alistair Cockburn. *Writing Effective Use Cases*. （用例表格式）
3. Martin Fowler. *UML Distilled*, 3rd ed.
4. Vaughn Vernon. *Implementing Domain-Driven Design*.
5. Neal Ford. *Functional Thinking*.
6. Erich Gamma et al. *设计模式：可复用面向对象软件的基础*.
7. ANSI/INCITS 359-2012, *Role Based Access Control*.
8. 邱明.《支付模块需求规格说明书》(PayReqSPEC) —— 本项目需求文档的格式范本。
