# 详细设计说明书 · 函数式实现

**版本 0.1（骨架）· 2026 年 9 月 20 日** · 模块：`rbac-functional` · 对应迭代三

> ⚠️ **本文档为骨架**，将在迭代三（12/22 检查）前填充完成。
> 课程要求「**部分功能**采用函数式设计完成」——因此函数式不覆盖整个系统，而是覆盖最适合它的三块。

---

## 1. 函数式覆盖范围

| 覆盖 | 不覆盖 |
|---|---|
| ✅ 授权判定内核 | ❌ REST 控制器 |
| ✅ 有效权限计算与继承闭包 | ❌ 数据库访问 |
| ✅ 约束求值 | ❌ 缓存管理（有状态） |
| ✅ 数据范围求值 | ❌ 审计写入（副作用） |

**选择依据**：函数式的优势在**纯计算**——无副作用、引用透明、可组合。上述三块恰好全是纯计算：输入是已加载的数据快照，输出是判定结果，中间不碰 I/O。而控制器、数据库、缓存本质上就是副作用，强行函数式化只会写出一堆 `IO` 包装器，徒增复杂度而无收益。

> 这个取舍本身就是答辩内容：**「部分功能采用函数式」不是妥协，而是正确的工程判断。** 函数式不是越多越好，而是用在它擅长的地方。

---

## 2. 准入标准

见 `02-architecture.md` §5.3：

| # | 要求 |
|---|---|
| 1 | 所有数据结构**不可变**（`record` + 不可变集合） |
| 2 | 核心计算为**纯函数**：无副作用、引用透明 |
| 3 | I/O 推到边界：内核只接收数据快照 |
| 4 | 用**函数组合**表达逻辑，而非控制流 |
| 5 | 错误用 `Either` / `Result` 表达，不用异常做控制流 |
| 6 | 关键性质用基于属性的测试（jqwik）验证 |

---

## 3. 核心设计草案

### 3.1 规格说明即实现

SRS §3.2.5 的数学定义：

```
EffectiveRoles(u)       = ⋃ { ↑r | r ∈ DirectRoles(u) ∧ r.enabled }
EffectivePermissions(u) = ⋃ { Permissions(r) | r ∈ EffectiveRoles(u) }
```

函数式实现**逐行对应**：

```java
Set<RoleId> effectiveRoles(UserSnapshot u, RoleGraph g) {
    return u.directRoles().stream()
            .filter(g::isEnabled)
            .flatMap(r -> g.closureOf(r).stream())
            .collect(toUnmodifiableSet());
}

Set<PermissionCode> effectivePermissions(UserSnapshot u, RoleGraph g) {
    return effectiveRoles(u, g).stream()
            .flatMap(r -> g.permissionsOf(r).stream())
            .collect(toUnmodifiableSet());
}
```

> **这是函数式范式最有力的论据**：实现与规格说明的结构一一对应，几乎可以逐行比对验证。结构化实现要写循环与临时集合，面向对象实现要在多个类之间分散这段逻辑；只有函数式能让代码读起来就是定义本身。

### 3.2 判定管道 = 函数组合

结构化用顺序调用 + 短路返回，面向对象用责任链，函数式用 **Kleisli 组合**：

```java
// 每个阶段：Context -> Either<Denial, Context>
Function<AuthzContext, Either<Denial, AuthzContext>> pipeline =
        validateSubject
            .andThen(bind(resolveRoles))
            .andThen(bind(computePermissions))
            .andThen(bind(matchPermission))
            .andThen(bind(checkDataScope))
            .andThen(bind(checkConstraints));

Decision decide(AuthzRequest req, Snapshot snap) {
    return pipeline.apply(AuthzContext.of(req, snap))
                   .fold(Decision::deny, ctx -> Decision.allow(ctx.trace()));
}
```

`bind` 负责短路：一旦某阶段返回 `Left(Denial)`，后续阶段全部跳过。**短路不是靠 `return` 语句，而是 `Either` 的代数性质自带的**——这是函数式与另外两种范式在控制流表达上的根本差异。

### 3.3 约束求值 = 谓词组合

```java
// 约束就是一个函数
type ConstraintRule = Function<ConstraintContext, Optional<Violation>>;

ConstraintRule mutuallyExclusive(Set<RoleId> group, int max) {
    return ctx -> ctx.effectiveRoles().stream().filter(group::contains).count() > max
            ? Optional.of(Violation.ssd(group)) : Optional.empty();
}

ConstraintRule cardinality(RoleId role, int max, ToIntFunction<RoleId> counter) { ... }
ConstraintRule prerequisite(RoleId target, Set<RoleId> required) { ... }

// 求值 = 归约
List<Violation> evaluateAll(List<ConstraintRule> rules, ConstraintContext ctx) {
    return rules.stream().map(r -> r.apply(ctx)).flatMap(Optional::stream).toList();
}
```

**新增一类约束 = 新增一个函数**，不改任何既有代码。对比：

| 范式 | 新增一类约束需要 |
|---|---|
| 结构化 | 新增检查函数 + **修改** `switch` 分派 + **修改**枚举 |
| 面向对象 | 新增一个实现类 + 注册（Spring 自动注册则无需改代码） |
| 函数式 | 新增一个函数 + 加入列表 |

> 迭代三会用**新增时间窗口约束**做实地验收，统计三种范式各需改动几个已有文件。这是 `10-paradigm-comparison.md` 最硬的一组数据。

### 3.4 数据范围 = 谓词

数据范围判定天然是一个谓词，函数式表达最为贴切：

```java
Predicate<TargetRef> scopePredicate(DataScope scope, UserSnapshot u, DepartmentTree tree) {
    return switch (scope) {
        case ALL          -> t -> true;
        case DEPT_AND_SUB -> t -> tree.descendantsOf(u.deptId()).contains(t.deptId());
        case DEPT         -> t -> Objects.equals(u.deptId(), t.deptId());
        case SELF         -> t -> Objects.equals(u.userId(), t.ownerId());
    };
}
```

同一个谓词可用于两个场景：**单条判定**（`predicate.test(target)`）与**列表过滤**（转译为 SQL 的 WHERE 条件）。一处定义、两处使用，两者不会不一致——而这正是数据权限最容易出错的地方（判定放行了，但列表查询漏了过滤，或反之）。

---

## 4. 基于属性的测试计划

用 jqwik 验证数学性质，而非具体用例：

| 性质 | 断言 |
|---|---|
| 幂等性 | `effectivePermissions(u)` 重复调用结果相同 |
| 单调性 | 给用户增加角色，有效权限集合只增不减 |
| 闭包性 | `closureOf(closureOf(r)) == closureOf(r)` |
| 并集交换律 | 角色指派顺序不影响最终权限集合 |
| 纯粹性 | 相同输入任意次调用结果相同，且不改变输入快照 |
| 无环不变量 | 任意合法操作序列后，角色图始终无环 |

> 属性测试能覆盖人写不出来的边界组合。这类测试对结构化与面向对象实现同样适用（它们测的是行为不是实现），因此也纳入 `rbac-test-suite`。

---

## 5. 待完成清单

- [ ] `Either` / `Result` 类型的实现或选型（Vavr？自行实现？）
- [ ] 不可变集合的选型（`Set.copyOf` 够用，还是需要持久化数据结构？）
- [ ] 快照加载边界的定义：哪些数据、何时加载、如何保证一致性
- [ ] 完整的管道阶段函数实现
- [ ] jqwik 属性测试
- [ ] 与另两种范式的性能对比（关注不可变集合带来的 GC 压力）
- [ ] Jacoco 报告

---

## 6. 本文档对应的答辩设计点

**DP-4 约束规则的可扩展设计**（迭代三陈述）

| 项 | 内容（待实现后补充实测数据） |
|---|---|
| **需求是什么** | 实现互斥角色、三类基数约束、先决条件角色；且 SRS M-1 要求新增一类约束不得修改既有代码 |
| **存在什么问题** | ① 结构化的 `switch` 分派天然违反开闭原则；② 约束必须作用于**有效角色**而非直接角色，否则可经由继承绕过；③ 约束求值要在「分配角色」与「建立继承」两个入口同时生效，容易漏一处；④ 数据范围的判定与列表过滤若各写一套，必然不一致 |
| **采用什么方法与理由** | ① 约束表达为 `ConstraintContext -> Optional<Violation>` 的纯函数，求值为归约，新增即新增函数；② 约束求值排在闭包计算之后；③ 两个入口调用同一个求值器；④ 数据范围定义为谓词，一处定义、两处使用（单条判定 + 转译为 SQL 条件） |
| **实现效果** | 待填充。**关键数据：新增一类时间窗口约束，三种范式各需修改几个已有文件** |
