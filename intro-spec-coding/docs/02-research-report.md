# Spec Coding 范式深度调研与工具链对比：调研报告

**2026 年 10 月 9 日** · 实验数据：`bench/results/summary.md`（81 个 run）

## 摘要

我们搭建了一个可复现的小型 Spec Coding benchmark，在同一个模型（DeepSeek `deepseek-flash`）下比较了以下组合：

- 3 档规格粒度（L1 一句话、L2 中粒度、L3 可判定的完整规格）；
- 7 种执行方式：单次调用基线，以及 Claude Code、Codex、OpenCode 各自的原生模式与「+OpenSpec 工作流」模式；
- 每格重复 3 次，共 63 个 run；另做 18 个坏规格 run。

评判依据是 41 个 Agent 看不到的隐藏验收测试，按 6 个维度打分。

主要发现：

1. **规格粒度是影响最大的因素。** L3 下 21/21 一次通过；L1、L2 下 0/42（§5.1）。
2. **但关系不是单调的。** 照搬思路文档示例的中粒度规格（L2），通过率低于一句话需求（L1），7 种执行方式无一例外。主要原因是 L2 没写的规则，在 L1 下被 Agent 从代码库里补上了，在 L2 下却被漏掉了（§5.2）。
3. **同一模型下，Agent 之间差距很小。** 通过率极差最多 5.6 个百分点，规格档位之间的差距约 17 个百分点（§5.4）。
4. **OpenSpec 没有稳定提高正确率。** 代价是 1.4–2.6 倍花费、1.6–3.4 倍耗时。它的价值在于把 Agent 对需求的理解和取舍，在写代码之前写成可审查的文档（§5.5）。
5. **规格自相矛盾时，所有 Agent 都能发现；歧义和缺失大多被静默处理。** OpenSpec 组和原生组发现缺陷的次数相近（10/15 vs 9/15）（§5.7）。

本报告所有数字都来自实验输出，没有预设任何结果。局限见第 6 节。

## 1. 问题：Prompt Coding 缺的是什么

一句自然语言需求，往往有多种「都说得通」的实现。以本报告的实验任务为例：「用户可以给订单使用优惠券，支持固定金额和百分比折扣」。这句话至少没有回答以下问题：

- 百分比折扣算出小数怎么取整？
- 折扣比订单金额还大怎么办？
- 过期时间那一刻算不算过期？
- 同一订单能不能叠加两张券？
- 优惠券不存在时返回 400 还是 404？

模型并不是不会写这些代码，而是不知道该选哪一种。**它缺的是决策边界（decision boundary），而不是编码能力。** 在 Prompt Coding 里，这些决策由模型隐式地「猜」出来，人只能在代码写完以后靠读代码、跑测试去发现猜错的地方。

Spec Coding（规格驱动编码，也叫 Spec-Driven Development）把顺序倒过来：

```
Idea → Spec（定义什么是「对」）→ Plan → Code → Verify（对照 Spec 验收）
```

先解决 What，再解决 How。规格是人和 AI 共同遵守的契约，也是验收的依据。

## 2. 技术演进脉络

「先定规格再写代码」不是 AI 时代的新发明。AI 改变的是：规格的读者从「人」变成了「人 + Agent」，规格因此必须**可被机器执行、可被机器验证**。

| 阶段 | 代表做法 | 规格的形态 | 谁来读规格 |
|---|---|---|---|
| 形式化方法（1970s–） | Z、VDM、B 方法 | 数学化的前置/后置条件 | 人（证明） |
| 契约式设计（1986–） | Eiffel 的 Design by Contract | 代码里的 `require` / `ensure` / 不变式 | 人 + 运行时检查 |
| 测试驱动（2000s） | TDD、BDD（Given/When/Then、Cucumber） | 可执行的测试和场景 | 人 + 测试框架 |
| 接口先行（2010s） | OpenAPI / Swagger、契约测试 | 机器可读的接口描述 | 人 + 代码生成器 |
| Prompt Coding（2021–） | Copilot 补全、对话式生成 | 一句话需求、注释 | 模型 |
| Agentic Coding（2025–） | Claude Code、Codex、OpenCode、Cursor Agent | 项目级规则文件：`CLAUDE.md`、`AGENTS.md`、`.cursor/rules` | Agent |
| Spec-Driven Workflow（2025–） | OpenSpec、GitHub Spec Kit、Kiro | 版本化的 proposal / spec / design / tasks | 人 + Agent，且贯穿整个变更 |

演进的主线是：规格**越来越可执行**（从散文到测试、到接口描述），**越来越贴近代码库**（从需求文档到仓库里的 Markdown）。Agent 出现以后，规格又多了一个新角色：限制 Agent 的行动范围。

## 3. 工具机制对比

### 3.1 统一比较口径

几种工具不在同一层次。比较之前先把「它是什么」说清楚：

| 工具 | 本质定位 | 和 Spec 相关的能力 | 规格存放位置 |
|---|---|---|---|
| Cursor | IDE + Coding Agent | Plan Mode（先出可编辑的实施计划，再 Build）、Rules、`AGENTS.md` | `.cursor/rules/`、`AGENTS.md` |
| Claude Code | 终端 / CLI Coding Agent | Plan 权限模式、`CLAUDE.md` 项目记忆、Skills、Slash Commands、Hooks、headless（`-p`）自动化 | `CLAUDE.md`、`.claude/` |
| Codex（OpenAI） | 终端 / CLI Coding Agent | `AGENTS.md`、Skills、沙箱与审批模式、`codex exec` 非交互执行 | `AGENTS.md`、`.agents/skills/` |
| OpenCode | 开源终端 Coding Agent，可接任意模型 | `AGENTS.md`、Build / Plan 两种 Agent、自定义命令与 Skills | `AGENTS.md`、`.opencode/` |
| OpenSpec | **Spec 工作流 / 协议层**，本身不写代码 | `propose → apply → archive`；每个变更产出 `proposal.md`、`specs/`（增量需求 + 场景）、`design.md`、`tasks.md`；`openspec validate --strict` 校验 | `openspec/` |

> **一句话主线：Cursor、Claude Code、Codex、OpenCode 解决「AI 怎么写代码」；OpenSpec 解决「AI 在写代码之前，如何确定该写什么」。**

### 3.2 OpenSpec 怎么接入 Agent

OpenSpec 不自带模型，靠「把工作流教给 Agent」来生效。`openspec init --tools <agent>` 会往仓库里写入该 Agent 能识别的说明文件：

| Agent | `openspec init` 写入的文件（1.14.1 实测） |
|---|---|
| Claude Code | `.claude/skills/openspec-*/SKILL.md`（6 个）+ `.claude/commands/opsx/*.md`（6 个） |
| Codex | `.agents/skills/openspec-*/SKILL.md`（6 个） |
| OpenCode | `.opencode/skills/openspec-*/SKILL.md`（6 个）+ `.opencode/commands/opsx-*.md`（6 个） |
| Cursor | `.cursor/skills/openspec-*/SKILL.md`（6 个）+ `.cursor/commands/opsx-*.md`（6 个）（只验证了 init 的产物，Cursor 本身未进入受控实验） |

此后 Agent 按 Skill 的指引调用 `openspec` 命令行：`openspec new change` 建变更目录 → 依次写 proposal、specs、design、tasks → `openspec instructions apply` 拿到任务清单 → 逐项实现、逐项勾选。实施要等任务定义完成之后才开始。

所以，严格的对照不是「Cursor vs Claude Code vs OpenSpec」三选一，而是 **Agent × 是否使用 OpenSpec** 的因子设计（第 4 节）。

### 3.3 为什么 Cursor 没有进入受控实验

要比较工具，必须让底层模型相同，否则测出来的是模型差异，不是工具差异。本实验所有执行方式都使用 DeepSeek `deepseek-flash`（V4.1-Flash）。接入方式都按 DeepSeek 官方的 Agent 接入文档：

- Claude Code 走 Anthropic 兼容接口；
- Codex 走 Responses 接口；
- OpenCode 用内置的 deepseek 提供方。

Cursor 的 Agent 和命令行都要登录 Cursor 账号，并使用 Cursor 托管的模型。我们没有找到让它在非交互模式下改用这个 DeepSeek 端点的办法，所以 Cursor 只做了机制调研，以及 `openspec init --tools cursor` 产物的核对。

仓库提供了手工补测的通道：

- `runner/prepare-workspace.mjs` 生成与自动 run 完全相同的初始工作区和固定提示词；
- 组员在 Cursor 里按「新对话、原样粘贴、不追问、不回答问题」的规则跑完；
- 再用 `runner/evaluate-only.mjs` 按同一套隐藏测试评分。

这类结果单独列在汇总表 4e，不与受控矩阵混合比较。目前表 4e 暂无数据。

## 4. 对照实验设计

### 4.1 研究问题

| RQ | 问题 | 怎么回答 |
|---|---|---|
| RQ1 | 规格粒度提升，是否提高一次通过率？ | 固定任务和执行方式，比较 L1 / L2 / L3 |
| RQ2 | 同一规格、同一模型下，不同 Coding Agent 有没有明显差异？ | 固定规格和模型，比较 Claude Code / Codex / OpenCode |
| RQ3 | 控制住 Agent 以后，OpenSpec 工作流是否还有独立增益？ | 每个 Agent 各跑「原生」与「+OpenSpec」两组 |
| 补充 | 规格本身有问题时，谁能发现？ | 三份埋了缺陷的坏规格（第 5 节） |

### 4.2 实验矩阵

```
规格  L1 / L2 / L3                                       （3 档）
  ×
执行方式  Prompt-only（单次调用，无工具，作为 Prompt Coding 基线）
          Claude Code │ Codex │ OpenCode                 （原生）
          Claude Code │ Codex │ OpenCode  + OpenSpec     （先走 proposal/spec/design/tasks 再 apply）
  ×
重复  3 次
= 63 个 run；另有坏规格 3 份 × 6 种 Agent 执行方式 × 1 次 = 18 个 run
```

Agent 与 OpenSpec 构成 3 × 2 因子设计：

- 同一行内比较（原生 vs +OpenSpec），得到 OpenSpec 效应；
- 同一列内比较（三个 Agent），得到 Agent 效应。

### 4.3 被测项目与任务

被测项目是一个小型订单服务（TypeScript + Node 24 + 内置 `node:sqlite` + Vitest），约 400 行（src 共 396 行），分层清晰：`routes → services → repositories`。项目已经有订单的创建、支付、取消，以及优惠券的后台管理接口；数据库里已经有 `coupons`、`coupon_redemptions` 表，以及 `usage_limit`、`used_count` 字段。

实验任务统一为：实现 `POST /orders/:id/apply-coupon`，即给订单使用优惠券。它同时涉及以下内容，复杂度适中，又足够考验规格：

- API 契约；
- 9 类业务规则（存在、状态、有效期、最低金额、两种折扣、封顶、一单一券、用量上限）；
- 边界条件（取整、等号归属、折扣大于金额）；
- 10 种错误码；
- 三张表的事务一致性。

### 4.4 三档规格

| 档 | 内容 | 行数 | 规格质量分 |
|---|---|---|---|
| L1 低粒度 | 接口路径和请求体，加一句「用户可以给订单使用优惠券，支持固定金额和百分比折扣」 | 5 | 5 |
| L2 中粒度 | API、5 条规则、3 条验收标准（照搬思路文档的示例） | 25 | 19 |
| L3 高粒度 | 14 条编号需求（REQ-01…14）、数据模型、状态变化、带检查顺序的错误码表、10 条 Given/When/Then 验收、非功能约束 | 88 | 100 |

规格质量分由 `runner/spec-lint.mjs` 计算，是一个故意做得很简单的规则检查：7 个维度满分 100，每个模糊词扣 5 分。它只衡量规格**有没有包含** Agent 需要的部分，不能发现矛盾（第 5 节）。

> **注意**：L1 的一句话是在一个**结构清晰、表结构已经包含大部分业务概念**的代码库上下发的。这一点对结果的解释很重要（§5.2）。

### 4.5 一个 run 怎么执行

```
1. 新工作区   复制 base/ → 写入 TASK.md（被测规格）→ git commit + tag base
              （OpenSpec 组在这一步执行 openspec init --tools <agent>，脚手架算作基线，不计入 Agent 的改动）
2. Agent 阶段  Docker 容器，只挂载工作区；不挂宿主机任何配置，不挂隐藏测试
              固定提示词（所有原生组完全相同；所有 OpenSpec 组完全相同）
              Agent 自己决定何时结束；runner 不追问、不反馈测试结果
              超时上限：前 33 个 run 为 15 分钟，之后为 25 分钟（原因见 §6）
3. 冻结        Agent 退出即冻结工作区
4. 评估阶段    另一个容器，断网；把工作区复制一份，再放入原始公开测试和隐藏测试
              类型检查 → 公开测试 → 隐藏测试 → diff → 完整性检查（TASK.md、公开测试、表结构、package.json 是否被改）
5. 评分        score.mjs 生成评分卡（score.json）
```

固定提示词（原生组）：

```
Implement the requirements defined in TASK.md.
You may inspect and modify the repository and run existing development commands.
Do not modify TASK.md.
Do not modify benchmark tests.
When you believe the task is complete, stop.
```

OpenSpec 组把第一句换成「Use OpenSpec to implement the requirements described in TASK.md. Follow the complete OpenSpec workflow: proposal/spec/design/tasks/apply.」，并多一句「Do not alter the original requirements.」（完整文本见 `runner/prompts.mjs`）。OpenSpec 带来的额外上下文正是被测的「处理」，不能为了公平把它去掉。

**隔离措施**：

- Agent 在容器里运行，看不到宿主机的 `CLAUDE.md`、记忆、MCP 服务器等配置；
- 工作区里没有任何项目级规则文件，除非它就是实验变量（OpenSpec 的 skills）；
- 隐藏测试只在评估阶段出现；
- 每个 run 都从同一个提交开始。

### 4.6 隐藏测试与评分

41 个隐藏测试，每个都标注了需求编号、严重度（critical 5 / major 3 / minor 1）和类别：

| 类别 | 个数 | 测的是什么 |
|---|---|---|
| functional | 15 | 行为对不对：该拒绝的拒绝了、金额算对了、订单没被改坏 |
| boundary | 8 | 取整、`==` 的归属、折扣大于金额、100% 折扣、编码大小写 |
| error | 10 | 状态码和错误码是否**与规格一致** |
| state | 4 | 用券记录、`used_count`、事务回滚、时间取自注入的 Clock |
| regression | 4 | 原有功能没被改坏 |

参考实现（`evaluator/reference/`）能通过全部 41 个测试，证明测试本身可以全部通过。

**总分** `Total = 0.35C + 0.20A + 0.15R + 0.10Q + 0.10E + 0.10S`：

| 维度 | 满分 | 组成 |
|---|---|---|
| C 正确性 | 35 | 类型检查 5 + 公开测试 5 + 隐藏功能测试（按严重度加权）20 + 回归 5 |
| A 规格符合度 | 20 | 需求覆盖 12（一条 REQ 的所有测试都过才算满足）+ 违反禁止性约束 8 |
| R 鲁棒性 | 15 | 边界 6 + 错误码 5 + 状态一致性 4 |
| Q 工程质量 | 10 | 类型错误数 3 + 分层（SQL 是否出现在 routes/services）3 + 单文件新增行数 2 + 自带测试 2 |
| E 效率 | 10 | 时间 5 + token 5，都乘以需求覆盖率（做错的快不算快） |
| S 范围控制 | 10 | 改了无关文件 4 + 加了没要求的接口 3 + 改动集中度 3 |

**一次通过（First-pass Success）** 的条件是以下全部成立：

- 类型检查通过；
- 公开测试全部通过；
- 回归测试全部通过；
- 所有 functional / boundary / state 隐藏测试通过；
- 没有 critical 级违规。

错误码是否完全一致不算在一次通过里，而计入 A 和 R，这样才能把「功能对了但不符合规格」和「功能错了」区分开（correctness ≠ adherence）。

**需求覆盖与测试通过率为什么分开算**：一条需求下面可能有 8 个测试，另一条只有 1 个。测试通过率高，不代表需求都实现了。

**质量维度是自动化的近似。** 原方案建议对 Architecture Fit 做人工盲审，我们改用可复现的启发式规则（SQL 是否出现在分层之外、业务计算是否写进了路由），在局限性一节说明。

## 5. 结果

所有表号指 `bench/results/summary.md`（由 `runner/aggregate.mjs` 生成）。每个格子 n = 3。下文「通过率」默认指**隐藏测试加权通过率**（按严重度加权）；「一次通过」作为更严格的二值指标并列给出。

### 5.1 RQ1：规格粒度 → 正确率

| 执行方式 | L1 | L2 | L3 |
|---|---|---|---|
| Prompt-only（单次调用） | 82.9% | 47.3%（单次：65.1 / 0 / 76.7） | 100% |
| Claude Code | 83.2% | 69.3% | 100% |
| Codex | 82.7% | 74.9% | 100% |
| OpenCode | 83.7% | 69.5% | 100% |
| Claude Code + OpenSpec | 83.7% | 79.3% | 100% |
| Codex + OpenSpec | 83.2% | 69.8% | 100% |
| OpenCode + OpenSpec | 83.4% | 71.6% | 100% |
| **一次通过（7 种执行方式合计）** | **0 / 21** | **0 / 21** | **21 / 21** |

（表 1、表 4c）

**观察**

1. **L3 下 21 个 run 全部一次通过，41 个隐藏测试全过。** 连「不给工具、只调用一次模型」的基线也是 3/3。规格写到可判定以后，「用哪个工具」在这个任务上已经不影响正确性。
2. **L1 和 L2 没有一个 run 一次通过。** 一次通过要求所有行为类测试都过；L1、L2 至少会在「到期那一刻算不算过期」（B04）和「券码大小写、空格」（B07）上失败。这两条规格里都没写，而 18/18 的 Agent run 都做了相反的选择：把 `validUntil` 当作包含在内，券码按原样匹配。
3. **规格质量分与通过率的相关系数 r = 0.66**（63 个 run）。规格质量分只有 3 个取值，这个数主要反映三档之间的差异，而且三档的关系不是单调的（见 5.2）。
4. 一次通过这个指标在本任务上表现为「悬崖」（0 → 0 → 100%）：隐藏测试按 L3 的意图编写，L1、L2 总会漏掉若干条精确边界。所以本报告**以加权通过率为主**，一次通过作为严格的辅助指标。

### 5.2 反直觉：中粒度规格（L2）比一句话（L1）更差

每一种执行方式都是 L2 < L1，差距 4–36 个百分点（表 4c）。注意，L2 是思路文档里给出的中粒度示例，原样照搬。

把失败的隐藏测试按编号拆开（18 个 Agent run/档，不含 Prompt-only）：

| 隐藏测试 | 测什么 | L1 失败 | L2 失败 | 归因 |
|---|---|---|---|---|
| H09 / H10 | 一单不能用两张券 / 同一张券不能重复用 | 0 / 0 | 17 / 17 | L2 没写，L1 的 Agent 自己补上了 |
| H11 / H12 | 已取消 / 已支付的订单不能用券 | 0 / 0 | 13 / 13 | 同上 |
| H13 | 用量上限 `usage_limit` | 0 | 5 | 同上 |
| E08 | 订单不是 PENDING 时返回 409 | 0 | 14 | 同上（连带） |
| E01 | 券不存在返回 404 | 0 | **18** | **L2 自己写了「无效券返回 400」**，Agent 照做 |
| E02 / E03 / E04 | 停用 / 过期 / 未生效的错误码 | 18 / 17 / 17 | 10 / 0 / 4 | L1 无任何错误码提示，各自起名 |
| B04 / B07 | 到期那一刻 / 券码大小写 | 18 / 18 | 18 / 18 | 两档都没写，统一选错 |

（由各 run 的 `score.json` 统计；方法见附录）

L2 的额外失败可以分成两类：

- **规格自身与真实意图冲突**：只有 E01 这 1 个测试。L2 的「无效的优惠券返回 HTTP 400」与真实意图（不存在返回 404）不一致。几个 Agent 在最终输出里明确说了自己是「按验收标准返回 400」。这是规格写错的代价，不是 Agent 的错。
- **规格没写、L1 下却被补上的规则**：H09–H13、E08 等。这是主要部分。

**一个假设：清单锚定。** 拿到一句话需求时，Agent 会通读代码库。表结构里的 `usage_limit`、`used_count`、`coupon_redemptions`，已有的 `ORDER_NOT_PENDING` 状态检查，都在提示它「用券应该检查订单状态、记录用量」。拿到一份「看起来完整」的 5 条规则清单时，Agent 倾向于把清单当成全部需求。

一份 L2 + OpenSpec run 的 `design.md` 把这种心态写得很直白：

> **Non-Goals:** Enforcing `usage_limit` or preventing a coupon from being applied more than once. `TASK.md` does not list these rules; `used_count` is updated for bookkeeping only.

（`results/runs/apply-coupon_L2_claude-os_r1/eval/diff.patch`）

这是一个**有证据支持的假设，不是已证实的机制**：我们只有一个任务、一个代码库，而这个代码库的表结构本身就很「会说话」。换一个约定不清晰的老代码库，L1 很可能明显更差。

**L1 的说明**：本实验的 L1 带有接口路径和请求体（思路文档的 L1 没有），否则隐藏测试根本调不到代码。所以 L1 实际是「一句话 + 一个路由」。

### 5.3 规格改变的是「错在哪」，而不只是「错多少」

| 失败原因 | L1（145 个失败测试） | L2（219 个） | L3（0 个） |
|---|---|---|---|
| 错误码不一致（Error-code mismatch） | 66% | 45% | — |
| 边界（Boundary Error） | 29% | 20% | — |
| 漏需求（Missing Requirement：该需求的测试全挂） | **0%** | 18% | — |
| 错误假设（Wrong Assumption：需求做了但做错） | **0%** | 12% | — |
| 状态一致性 | 5% | 5% | — |

（表 4；6 种 Agent 执行方式合计）

- L1 的失败几乎全是「约定类」：错误码叫什么、边界的等号归哪边。功能本身是对的，L1 的 C 维度（正确性）全部 35/35（表 2）。
- L2 多出了「漏需求」和「错误假设」两类，这正是 5.2 说的锚定效应。
- L3 没有失败。

这和思路文档的预期一致：**提高规格粒度的主要作用是减少错误假设，而不是让代码写得更漂亮。** 不过在本实验里，「错误假设」主要出现在 L2，而不是 L1。

### 5.4 RQ2：同一模型下，Agent 之间差距很小

| 规格 | Claude Code | Codex | OpenCode | 极差 | 对比：L3 − L1 |
|---|---|---|---|---|---|
| L1 | 83.2% | 82.7% | 83.7% | 1.0 | |
| L2 | 69.3% | 74.9% | 69.5% | 5.6 | |
| L3 | 100% | 100% | 100% | 0 | 约 17 个百分点 |

（表 1、表 4c；原生组）

- 同一规格下，三个 Agent 的通过率极差最多 5.6 个百分点（L2），在 L1、L3 下几乎为零；而规格从 L1 到 L3 带来约 17 个百分点。**换规格的效应远大于换 Agent。**
- 成本和速度有差异（表 3，按高峰价折算）：
  - L3 下单次花费，Codex 与 OpenCode 约 ¥0.11，Claude Code 约 ¥0.16；
  - Codex 的工具调用最少（L3 平均 13.7 次，Claude Code 27.7 次，OpenCode 29.0 次）；
  - 耗时三者在 4–7 分钟。
- 工程质量（Q）和范围控制（S）几乎都是满分（表 2）。三个 Agent 都遵守了分层（SQL 只在 repositories），没有改无关文件，也没有加多余接口。
- **Prompt-only 的特殊失败**：L2 的第 2 次运行，模型在输出文件末尾带出了一个 `</parameter>` 标记，导致类型检查失败，隐藏测试 0 分。没有工具循环，就没有机会自己编译、发现并修掉这种错误。这是 Agent 相对单次调用的一个实际价值，但在 L3 下没有体现出来。

### 5.5 RQ3：OpenSpec 的账

| 规格 | Agent | 通过率 原生 → +OpenSpec | 花费（高峰价） | 耗时 |
|---|---|---|---|---|
| L1 | Claude Code / Codex / OpenCode | +0.5 / +0.5 / −0.3 | ×1.9 / ×2.1 / ×1.4 | ×3.4 / ×2.5 / ×1.8 |
| L2 | Claude Code / Codex / OpenCode | **+10.1** / −5.2 / +2.1 | ×2.3 / ×2.6 / ×2.0 | ×1.7 / ×1.8 / ×2.1 |
| L3 | Claude Code / Codex / OpenCode | 0 / 0 / 0 | ×2.1 / ×2.6 / ×2.3 | ×2.4 / ×2.4 / ×1.6 |

（表 4b、表 3）

1. **OpenSpec 没有稳定提高正确率。**
   - L1 和 L3 下几乎为零；
   - L2 下三个 Agent 方向不一（+10.1、−5.2、+2.1），每格只有 3 次，标准差 2–8 个百分点（表 1），不能据此宣称 OpenSpec 有正效应。
2. **代价稳定**：花费约 1.4–2.6 倍，耗时约 1.6–3.4 倍，token 约 2.3–5.5 倍。主表中 13 个 run 超过 15 分钟，全部是 OpenSpec 组。
3. L3 下 OpenSpec 组总分约 93，原生组约 98。**这 5 分差距全部来自效率维度 E**（3 vs 8），正确性、符合度、鲁棒性都满分（表 2），不代表质量更差。
4. **OpenSpec 组的工程质量 Q 略高**（L1/L2 的 Q 为 9.3–10 vs 8.0–8.7），主要因为它们几乎都按 tasks.md 写了自己的测试。
5. **真正的价值是可审查。** 5.2 引用的那段 `Non-Goals`，就是 OpenSpec 在写代码之前产出的文字。审查者读到它，就能发现「一单一券」和「用量上限」被排除在外，而这两条正是后来隐藏测试失败的地方。原生 Agent 也会做同样的取舍，但取舍只出现在代码里，或者最终输出的一句话里。

### 5.6 成本：模型费用不是瓶颈

- 单个 run 的模型费用（按高峰价）：原生 Agent ¥0.10–0.17，加 OpenSpec ¥0.20–0.33（表 3）。
- 全部 81 个 run 按 token 估算共 ¥13.36。账户实际扣费 ¥15.30（余额 27.62 → 12.32），包括试跑、超时重跑和冒烟测试。

思路文档设想的 Spec ROI 是：总成本 = 写规格 + Agent 执行 + 人工返修。我们没有测人工返修时间，所以不给 ROI 数字，只给一个定性判断：在这个量级上，一个需求的模型费用不到一毛到三毛钱，**真正的成本是人：写 L3 规格的时间，以及审 OpenSpec 产物的时间。** 选型时应该比较流程成本，而不是 token 单价。

### 5.7 坏规格：谁能发现规格本身的问题

三份坏规格都由 L3 改出，每份 × 6 种 Agent 执行方式 × 1 次（表 5，人工判读见 `results/bad-spec-judgement.json`）：

| 坏规格 | 埋入的缺陷 | 指出缺陷（原生 3 个 run 合计） | 指出缺陷（+OpenSpec 3 个 run 合计） |
|---|---|---|---|
| 歧义 | 「按合适的方式取整」「大额订单适当上限」 | 2 / 6 | 3 / 6 |
| 缺失 | 折扣大于订单金额怎么办 | 1 / 3 | 1 / 3 |
| 冲突 | 100 元总上限 vs 示例 200 元；禁止第二张券 vs 示例换券 | 6 / 6 | 6 / 6 |
| **合计** | | **9 / 15** | **10 / 15** |

- **冲突最容易被发现**：6 个 run 全部指出了两处矛盾，并说明了自己选哪一边。
  - 3 个原生 run 都按验收示例实现。
  - OpenSpec 组里有 2 个按 MUST 条款实现，1 个按示例实现。每格只有 1 次，这个差异不能算作效应。
- **歧义和缺失大多被静默处理**。
  - 「折扣大于订单金额」这一处，6 个 run 全部按字面实现（折扣不截断），只有 2 个提了一句。
  - 有一个 OpenSpec run 的 `design.md` 把取整列为风险，同时却说「规格已经明确规定」。工作流本身不保证 Agent 真的发现了问题。
- **OpenSpec 的区别在记录方式**：原生 Agent 的判断只出现在最终输出的聊天文字里，会话结束就没了；OpenSpec 组把冲突和取舍写进 `proposal.md` / `design.md`，留在仓库里供审查。
- **规格质量分发现不了冲突**：冲突版本得 100 分，和 L3 一样（表 5）。规则检查只能查「有没有」，查不出「对不对」。
- **`AskUserQuestion` 尝试次数全部为 0**：这是运行模式造成的，不是 Agent 的行为。三个工具都以非交互模式运行（`-p` / `exec` / `run`），没有人可以回答问题。所以本节衡量的是「有没有在输出或文档里指出」，而不是「有没有提问」。
- **事后发现的设计缺口**：BAD-missing 删掉了有效期边界的说明，但错误码表里仍然写着 `now >= validUntil`，边界其实还能推出来，6 个 run 也都实现对了。所以缺失版实际只有 1 处缺陷（见 `specs/apply-coupon/DEFECTS.md`）。

## 6. 局限与有效性威胁

| 威胁 | 具体情况 | 对结论的影响 |
|---|---|---|
| 只有一个任务、一个代码库 | 订单服务结构清晰，表结构本身就带着大部分业务概念 | L1 的表现偏乐观；「L2 比 L1 差」可能只在「代码会说话」的仓库里成立 |
| 只有一个模型 | 全部是 `deepseek-flash`；更强或更弱的模型幅度可能不同 | RQ2 的结论限定为「同一模型下」 |
| 推理强度没有统一 | Codex 按 DeepSeek 配置脚本设为 `model_reasoning_effort = "high"`；Claude Code 与 OpenCode 用各自默认值。DeepSeek 的 Claude Code 指南建议的 `CLAUDE_CODE_EFFORT_LEVEL=max` 与 `[1m]` 上下文没有设置 | 「同模型」不等于「同推理预算」，RQ2 的比较有这个前提 |
| 轮数上限不对称 | Claude Code 有 `--max-turns 80`，Codex、OpenCode 只有墙钟超时；实际没有 run 触到 80 轮 | 本次数据未受影响 |
| 样本量 | 主矩阵每格 3 次，坏规格每格 1 次；没有做显著性检验 | 适合看方向和量级；L2 下 OpenSpec 的 ±5–10 个百分点不能当作效应 |
| 隐藏测试按 L3 编写 | L1/L2 的「失败」部分是规格没写的约定，比如错误码的名字。错误码是否一致单独计在 A/R 维度，不影响一次通过 | 这正是要测的「规格不完整的代价」，但错误码命名本身带有任意性 |
| 整个仓库能放进一次上下文 | 约 400 行，Prompt-only 基线可以看到全部代码 | Prompt-only 在 L3 下也 100%；在真实大仓库里，Agent 的价值会更明显 |
| 超时上限中途调整 | 前 33 个 run 为 15 分钟，之后为 25 分钟。期间 DeepSeek 输出速度从约 32 token/s 降到约 11 token/s。6 个 15 分钟超时的 run 移到 `results/timeouts-15min/`，按 25 分钟重跑（表 4d）；其中 2 个 L3 + OpenSpec 的 run 在 15 分钟时冻结，隐藏测试加权通过率只有 38.8%，重跑后都是 100% | OpenSpec 组的耗时是本研究里最不可靠的数字；正确性结论不受影响 |
| 重跑与重试 | 以下几个 run 不止跑了一次：L1_opencode-os_r1 共 3 次（runner 崩溃、Docker 500 错误、成功）；L1_claude-os_r1 共 3 次（15 分钟超时、容器卡在 Created、成功）；L1_opencode_r1 共 3 次（15 分钟超时、Docker 500、成功）；L3_codex-os_r3 共 2 次（第 1 次 runner 崩溃，原因没有记录：当时还没加错误日志）；BAD-ambiguous_codex_r1 共 2 次（查询余额时网络挂起，被手动终止） | 失败都发生在评分之前，或者明确属于基础设施故障；没有因为分数不理想而重跑任何 run |
| 质量维度是近似 | 用启发式规则代替人工盲审 | Q 维度只能区分明显违反分层的情况 |
| 坏规格的判读是人工的 | 由本组一人按 `DEFECTS.md` 的口径判读，证据全部摘录在 `results/bad-spec-evidence.md` | 读者可以复核 |
| Cursor 未参与受控对比 | 见 §3.3 | 对 Cursor 只有机制层面的结论 |

## 7. 结论

回到三个研究问题：

- **RQ1（规格粒度）**：是，而且是本实验里影响最大的因素。
  - 可判定的完整规格（L3）让 7 种执行方式 21/21 一次通过；
  - 一句话和中粒度规格下，一次通过为 0/42。
  
  但这个关系**不是单调的**：只写一半的规格（L2），通过率反而低于一句话（L1）。
- **RQ2（Agent 差异）**：同一模型下很小。不同 Agent 之间的通过率差距最多 5.6 个百分点，而规格档位之间的差距约 17 个百分点。各 Agent 的差别主要在成本、速度和工作方式。
- **RQ3（OpenSpec 增益）**：在正确率上没有稳定的独立增益，代价稳定在 1.4–2.6 倍花费、1.6–3.4 倍耗时。它的价值体现在别处：
  - Agent 对需求的理解、取舍和冲突处理，在写代码之前以文档形式留在仓库里，可以审查；
  - 这正好对应 L2 下最致命的那类错误（漏需求）。

由此得到的工程判断：

1. **Spec Coding 的核心不是规格越长越好，而是规格必须可判定、可验证、无歧义。** L3 只有 88 行。
2. **不要交出「看起来完整」的半规格。** 要么写到可判定，要么明确声明「未列出的规则按现有代码约定处理」。
3. **OpenSpec 这类工作流是审查工具，不是正确性工具。** 没有人审 proposal 和 design，它只是多花时间。
4. **工具选型放在最后。** 同一模型下工具差距很小，而且会随版本变化。

> **Prompt Coding**：Tell AI what you want.
> **Spec Coding**：Define what "correct" means before AI writes code.

---

## 附录 A：复现与文件

```bash
cd intro-spec-coding/bench
bash docker/fetch-vendor.sh && bash docker/build-image.sh   # 镜像 spec-bench:1
echo "DEEPSEEK_API_KEY=sk-..." > ../.env                     # 不入库
node runner/batch.mjs main 3                                  # 63 个 run
node runner/batch.mjs bad 2                                   # 18 个 run
node runner/aggregate.mjs                                     # → results/summary.md
```

| 路径 | 内容 |
|---|---|
| `results/runs/<run>/prompt.txt` | 实际发给 Agent 的提示词 |
| `results/runs/<run>/transcript.jsonl` | Agent 的完整事件流（Prompt-only 为 `transcript.md`） |
| `results/runs/<run>/eval/` | 类型检查输出、公开 / 隐藏 / Agent 自带测试的 JSON 报告、`diff.patch`、完整性检查 |
| `results/runs/<run>/meta.json`、`score.json` | 过程数据与评分卡 |
| `results/timeouts-15min/` | 15 分钟上限下超时的原始 run（表 4d） |
| `results/pilot/` | 正式实验前的试跑（不计入） |
| `results/bad-spec-evidence.md`、`bad-spec-judgement.json` | 坏规格的判读证据与结论 |
| `results/batch-*.log`、`batch-errors.log` | 批量运行日志，包括失败与重试 |

§5.2 的逐测试失败计数，是对 `results/runs/apply-coupon_{L1,L2}_*_r[123]/score.json` 中 `detail.failedTests` 的计数，不含 Prompt-only。

## 附录 B：花费

| 项 | 金额 |
|---|---|
| 81 个计入的 run（token × 官方价格，按实际时段） | ¥13.36 |
| 账户实际扣费（含试跑、超时重跑、冒烟测试） | ¥15.30 |
| 单价（deepseek-flash，2026-10-09 官网） | 输入缓存命中 ¥0.04/M，未命中 ¥2/M，输出 ¥8/M（高峰）；空闲时段半价 |
