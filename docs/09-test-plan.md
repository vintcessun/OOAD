# 测试计划与 Jacoco 覆盖率

**版本 0.1 · 2026 年 9 月 20 日**

> **Jacoco 报告是每次检查的提交门槛**：《成绩计算办法》明确要求「在检查前需在课程网站上提交课程设计的详细设计和 Jacoco 测试报告（如无法提供可以提供代码）」。「如无法提供可以提供代码」是退路，不是选项——能提交报告而提交代码，在相对评分中就是失分。

---

## 1. 测试策略

### 1.1 测试金字塔

```
           ╱╲
          ╱  ╲        端到端测试（~10 条）
         ╱    ╲       Playwright，覆盖演示脚本的每一步
        ╱──────╲
       ╱        ╲     契约测试（~50 条）★本项目特有
      ╱          ╲    同一套用例对三种范式实现分别执行
     ╱────────────╲
    ╱              ╲  集成测试（~80 条）
   ╱                ╲ Testcontainers（MySQL + Redis），验证 SQL、事务、缓存失效
  ╱──────────────────╲
 ╱                    ╲ 单元测试（~400 条）
╱______________________╲纯函数、无容器、无 Mock 框架
```

### 1.2 契约测试：本项目的核心测试手段

`rbac-test-suite` 模块中的用例**不针对任何具体实现**，而是针对 `rbac-contract` 定义的接口编写，通过 JUnit 5 的 `@ParameterizedTest` 对三个实现分别执行：

```java
@ParameterizedTest(name = "[{0}] 用户有权限时应当允许")
@MethodSource("allImplementations")
void 有权限时应当允许(String paradigm, AuthorizationService svc) {
    var r = svc.check(new AuthzRequest("SG000013", "oa:doc:draft", "create", null));
    assertTrue(r.allowed());
}

static Stream<Arguments> allImplementations() {
    return Stream.of(
        Arguments.of("结构化", new StructuredAuthorizationService(...)),
        Arguments.of("面向对象", new OoAuthorizationService(...)),
        Arguments.of("函数式", new FunctionalAuthorizationService(...)));
}
```

**这套测试的意义**：三种范式的对比只有在**行为等价**的前提下才成立。如果结构化实现和面向对象实现在某个边界情况上结果不同，那么性能对比、可维护性对比就都失去意义——因为它们根本不是同一个系统的两种实现。契约测试全绿，是对比实验有效性的**前置条件**。

> 答辩时这是一个很有力的点：「我们怎么证明三种实现做的是同一件事？我们有 50 条契约测试，对三个实现跑完全相同的输入，断言完全相同的输出。」

---

## 2. Jacoco 配置与覆盖率门槛

### 2.1 Maven 配置

父 POM 统一配置，三个实现模块继承：

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.12</version>
  <executions>
    <execution><id>prepare</id><goals><goal>prepare-agent</goal></goals></execution>
    <execution><id>report</id><phase>test</phase><goals><goal>report</goal></goals></execution>
    <execution>
      <id>check</id><phase>verify</phase><goals><goal>check</goal></goals>
      <configuration>
        <rules>
          <rule>
            <element>BUNDLE</element>
            <limits>
              <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.70</minimum></limit>
              <limit><counter>BRANCH</counter><value>COVEREDRATIO</value><minimum>0.60</minimum></limit>
            </limits>
          </rule>
          <rule>
            <element>PACKAGE</element>
            <includes><include>*.func.*</include><include>*.domain.*</include><include>*.kernel.*</include></includes>
            <limits>
              <limit><counter>LINE</counter><value>COVEREDRATIO</value><minimum>0.90</minimum></limit>
            </limits>
          </rule>
        </rules>
      </configuration>
    </execution>
  </executions>
</plugin>
```

另配 `jacoco:report-aggregate` 在 `rbac-test-suite` 模块汇总三个实现的报告，输出一份总报告。

### 2.2 覆盖率门槛

| 范围 | 行覆盖 | 分支覆盖 | 理由 |
|---|---|---|---|
| **授权判定内核**<br/>（`func` / `domain` / `kernel` 包） | **≥ 90%** | ≥ 85% | 这是系统的核心，且是纯逻辑，没有难以覆盖的理由 |
| 约束求值、闭包计算 | ≥ 90% | ≥ 85% | 边界情况多（环、自环、深度超限），必须逐一覆盖 |
| 整体 | ≥ 70% | ≥ 60% | |
| 接入层 Controller | 不设门槛 | — | 只做参数转换，由集成测试覆盖 |
| DTO / record | **排除** | — | 自动生成的访问器，纳入统计会虚高覆盖率 |

**排除配置**：

```xml
<excludes>
  <exclude>**/dto/**</exclude>
  <exclude>**/data/*Record.class</exclude>
  <exclude>**/config/**</exclude>
  <exclude>**/*Application.class</exclude>
</excludes>
```

> **为什么要排除 DTO**。一个项目若把几百个 getter 计入覆盖率，很容易「看起来」有 85% 覆盖率，而核心逻辑其实只覆盖了 40%。我们主动排除它们，让报告上的数字反映真实情况。这一点本身值得在答辩中说明——它表明我们理解覆盖率是手段而非目的。

### 2.3 覆盖率不是目的

需要在答辩中准备好回答这个问题：

> 「你们 90% 的覆盖率，能保证没有 bug 吗？」

不能。覆盖率只说明代码被执行过，不说明断言是否正确。因此我们补充两类测试：

1. **基于属性的测试**（jqwik）：对授权内核验证数学性质，而非具体用例；
2. **变异测试**（PIT，可选）：故意改变代码逻辑，看测试是否能发现。这直接度量断言的质量。

---

## 3. 测试用例设计

### 3.1 需求到测试的追溯

每条需求用例至少对应一条测试，测试方法名中带需求编号：

```java
@Test
@DisplayName("RBAC-REQ-AUTHZ-001 用户被禁用时应当拒绝且不查询角色")
void authz001_禁用用户短路拒绝() { ... }
```

CI 中用脚本校验：`01-srs.md` 中每个「必须」级需求编号，在测试代码中至少出现一次；缺失则构建失败。

> 这个脚本保证了需求文档与测试不会随时间脱节——这是多数课程项目做不到的事，也是「用软件工程方法组织实施」的具体体现。

### 3.2 授权判定的测试用例

| # | 场景 | 输入 | 期望 |
|---|---|---|---|
| 1 | 有权限 | 许禄 + `oa:doc:draft:create` | allow |
| 2 | 无权限 | 许禄 + `oa:doc:draft:approve` | deny, MISSING_PERMISSION |
| 3 | 用户不存在 | 不存在的 ID | deny, SUBJECT_INVALID |
| 4 | 用户被禁用 | 禁用用户 + 有权限的操作 | deny, SUBJECT_DISABLED，**且不产生角色查询** |
| 5 | 角色被禁用 | 用户持有禁用角色 | deny |
| 6 | 用户无任何角色 | 新建用户 | deny |
| 7 | 缓存命中 | 连续两次相同请求 | 第二次 `cacheHit=true`，且 DAO 调用数为 0 |
| 8 | 权限撤销后立即失效 | 撤销 → 立即判定 | deny（**关键安全用例**） |
| 9 | 数据库异常 | DAO 抛异常 | deny，**绝不放行**（失败关闭） |
| 10 | 权限码格式非法 | `不合法的码` | deny，不抛异常 |
| 11 | 继承获得的权限（迭代二） | 子角色 + 父角色的权限 | allow |
| 12 | 继承链上角色被禁用（迭代二） | 父角色禁用 | deny |
| 13 | 数据范围 SELF（迭代二） | 许禄查他人考勤 | deny, OUT_OF_DATA_SCOPE |
| 14 | 数据范围 DEPT（迭代二） | 袁国智查本部门考勤 | allow |
| 15 | 数据范围 DEPT 越界（迭代二） | 袁国智查其他部门考勤 | deny, OUT_OF_DATA_SCOPE |

> 用例 4、8、9 是**安全用例**，任何一条失败都意味着系统存在可被利用的漏洞。这三条在 CI 中单独标记 `@Tag("security")`，失败时阻断合并。

### 3.3 角色继承的测试用例（迭代二）

| # | 场景 | 期望 |
|---|---|---|
| 1 | 简单继承 A→B | A 获得 B 的权限 |
| 2 | 多级继承 A→B→C | A 获得 B 与 C 的权限 |
| 3 | 多重继承 A→B，A→C | A 获得 B ∪ C 的权限 |
| 4 | 菱形继承 A→B, A→C, B→D, C→D | D 的权限只计一次，不重复 |
| 5 | **自环** A→A | 拒绝，`HIERARCHY_SELF_LOOP` |
| 6 | **直接环** A→B 后 B→A | 拒绝，返回环路径 `[A,B,A]` |
| 7 | **间接环** A→B→C 后 C→A | 拒绝，返回完整环路径 `[C,A,B,C]` |
| 8 | 深度超限 | 拒绝，`HIERARCHY_DEPTH_EXCEEDED` |
| 9 | 解除继承后权限收回 | 立即失去继承权限 |
| 10 | 继承变更触发向下失效 | 所有子角色持有者的缓存被清 |

### 3.4 约束的测试用例（迭代三）

覆盖三类基数约束（`ROLE_USER_MAX` / `USER_ROLE_MAX` / `ROLE_PERM_MAX`）、SSD、DSD、先决条件，每类至少：通过 / 恰好在边界 / 超出边界 / 存量豁免 四条。

**最重要的一条**：**经由继承间接获得互斥角色**——用户被分配角色 A，而 A 继承了与用户已有角色互斥的角色 B。若约束只检查直接角色，这条会漏过去。这是 SRS A-21 决策（约束作用于有效角色）的验证用例。

### 3.5 数据导入的测试用例

| # | 场景 | 期望 |
|---|---|---|
| 1 | 完整导入三份 xlsx | 行数符合 `03-database.md` §5.3 的预期表 |
| 2 | 部门表成环 | 中止导入并报错 |
| 3 | 部门层级 > 3 | 报警告 |
| 4 | 员工的部门不存在 | 记入异常报告，该员工 `department_id` 置空 |
| 5 | 单元格取值 `view/新增` | 归一化为 `查看/新增`，并产生 warning |
| 6 | 单元格为空 | 按「无」处理，并产生 warning |
| 7 | 部门经理姓名同名歧义 | 记入异常报告，不随意选一个 |
| 8 | 重复导入 | 幂等，不产生重复数据 |

> 用例 8 很重要：导入脚本会在开发过程中反复运行，不幂等会导致数据混乱。

---

## 4. 性能测试

### 4.1 压测方案

| 项 | 内容 |
|---|---|
| 工具 | JMeter（出报告）+ wrk（快速迭代） |
| 数据 | **真实导入的 10000 用户、1795 权限、约 1800 条授予** |
| 请求分布 | Zipf 分布模拟热点用户（少数用户高频访问），检验缓存命中率 |
| 场景 A | 纯读：100% `authz/check`，缓存热态 |
| 场景 B | 读写混合：10000:1，含权限变更引发的缓存失效 |
| 场景 C | 冷启动：清空缓存后的首批请求延迟 |
| 场景 D | 批量判定：100 条/请求 |

### 4.2 验收指标

对照 SRS §5.2：

| 指标 | 目标 | 实测 | 结论 |
|---|---|---|---|
| 鉴权 QPS | ≥ 10,000 | 待测 | |
| P95 延迟 | < 20 ms | 待测 | |
| P99 延迟 | < 50 ms | 待测 | |
| 缓存命中率 | ≥ 95% | 待测 | |
| 权限变更生效延迟 | ≤ 1 s | 待测 | |

**未达标时的处理**：在测试报告中**如实记录**，并说明瓶颈定位与优化方向。课程环境（单机、共享云主机）下未必能达到指标，隐瞒比未达标更严重。

### 4.3 三种范式的性能对比

同一套压测脚本分别打到 `:8081`（结构化）与 `:8082`（面向对象），对比：

| 维度 | 说明 |
|---|---|
| 吞吐与延迟 | 期望差异很小（判定路径都是一次集合查找） |
| GC 表现 | 函数式实现的不可变集合可能产生更多短命对象 |
| 冷启动 | 反射与代理的使用量不同 |

> **预期结论**：三者性能差异在同一数量级内，不构成选型依据。范式的真正差异在**可维护性与可扩展性**，而非性能。这个结论如果实测支持，本身就是一个有价值的发现——它反驳了「面向对象因为虚函数调用所以更慢」这类流行说法。详见 `10-paradigm-comparison.md`。

---

## 5. CI 流水线

```yaml
# .github/workflows/ci.yml
on: [push, pull_request]
jobs:
  build:
    steps:
      - mvn -B clean verify              # 编译 + 全部测试 + Jacoco check
      - 校验需求追溯脚本                    # 每个必须级需求有对应测试
      - mvn jacoco:report-aggregate      # 汇总报告
      - 上传 target/site/jacoco-aggregate # 作为构建产物
      - 覆盖率低于门槛 → 构建失败
```

**分支保护**：`main` 分支要求 CI 通过才能合并；覆盖率下降的 PR 不合入（`00-charter.md` §4.4）。

> 从第一次提交就配好 Jacoco 与 CI，而不是等到检查前一周才补。理由很实际：覆盖率是**逐步积累**的，最后一周补测试只能补出表面覆盖（调用一遍不断言），既骗不过老师的提问，也没有任何工程价值。

---

## 6. 各次检查的提交清单

| 检查 | 提交物 |
|---|---|
| 第一次<br/>10/27–10/29 | 详细设计（`06-detail-structured.md`）、Jacoco 报告（结构化模块，行覆盖 ≥70%、内核 ≥90%）、数据导入异常报告、压测初步结果 |
| 第二次<br/>11/24–11/26 | 增补 `07-detail-oo.md`、两个实现的 Jacoco 报告、**契约测试全绿的证明**、两种实现的性能对比数据 |
| 第三次<br/>12/22–12/24 | 增补 `08-detail-functional.md`、`10-paradigm-comparison.md`、三个实现的 Jacoco 汇总报告、完整压测报告、可扩展性验收实验结果（新增时间窗口约束需改动几个文件） |

---

## 7. 本文档对应的答辩设计点

**DP-6 的测试侧论据**

| 项 | 内容 |
|---|---|
| **需求是什么** | 每次检查须提交 Jacoco 报告；且要能证明三种范式实现的是同一个系统 |
| **存在什么问题** | ① 覆盖率容易造假——把 DTO 的 getter 计入就能虚高；② 三种实现若行为不等价，一切对比都无效；③ 需求文档与测试容易随迭代脱节 |
| **采用什么方法与理由** | ① 排除 DTO 与配置类，对内核单独设 90% 的包级门槛，让数字反映真实情况；② `rbac-test-suite` 用参数化测试对三个实现跑同一套断言，**行为等价成为可验证的事实而非声明**；③ CI 脚本校验每个必须级需求编号在测试中出现，缺失即构建失败 |
| **实现效果** | 覆盖率数字可信；三范式对比有实验基础；需求-测试追溯自动维持；三条安全用例（禁用短路、撤销立即失效、失败关闭）单独打标，失败即阻断合并 |
