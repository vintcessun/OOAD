# 导论题目 1：Spec Coding 范式调研与工具链对比

这是「软件工程导论」的题目 1，与 RBAC 课程设计无关，只是放在同一个仓库里。Maven 构建和 CI 都不会碰这个目录。

## 产物

| 文件 | 内容 |
|---|---|
| [`docs/01-ppt-script.md`](docs/01-ppt-script.md) | 演讲 PPT 文字稿：逐页的标题、要点和讲稿 |
| [`docs/02-research-report.md`](docs/02-research-report.md) | 调研报告：技术脉络、工具机制对比、3 组对照实验与数据、坏规格实验 |
| [`docs/03-selection-guide.md`](docs/03-selection-guide.md) | 面向中小团队的 Spec Coding 工具选型指南 |
| [`bench/results/summary.md`](bench/results/summary.md) | 实验数据汇总，由脚本生成 |
| `bench/results/runs/<run>/` | 每个 run 的原始记录：prompt、transcript、diff、测试结果、评分卡 |

## 实验工程 `bench/`

```
bench/
  base/                   被测项目：订单服务（TypeScript + Node 24 + node:sqlite + Vitest），Agent 只能看到它
  specs/apply-coupon/     同一个需求的 L1 / L2 / L3 三档规格，以及 3 份埋了缺陷的坏规格（DEFECTS.md 说明埋了什么）
  evaluator/
    hidden-tests/         41 个隐藏验收测试，每个标注 REQ 编号、严重度、类别
    reference/            参考实现：证明隐藏测试本身可以全部通过
    evaluate.sh           评估阶段：类型检查、公开测试、隐藏测试、diff、完整性检查
  evaluation.json         评分模型：0.35C + 0.20A + 0.15R + 0.10Q + 0.10E + 0.10S
  runner/
    run.mjs               跑一个 run：新工作区 → Agent 阶段 → 冻结 → 评估阶段 → 评分
    score.mjs             把评估输出算成评分卡
    spec-lint.mjs         规格质量分（Spec Quality Score）
    aggregate.mjs         汇总所有 run → results/summary.{md,json}
  docker/                 实验镜像：Claude Code 2.1.295 + OpenSpec 1.14.1 + Node 24
```

## 复现

```bash
# 1. API Key 只放在本目录被忽略的 .env 里
echo "DEEPSEEK_API_KEY=sk-..." > intro-spec-coding/.env

# 2. 构建镜像（Claude Code 安装包先在本机下载）
cd intro-spec-coding/bench
bash docker/fetch-vendor.sh
docker build -f docker/Dockerfile -t spec-bench:1 .

# 3. 跑一个 run，例如 L2 规格 + Claude Code 原生
node runner/run.mjs L2 native 1

# 4. 汇总
node runner/aggregate.mjs
```

执行方式有三种：

- `prompt`：单次调用模型，没有工具；
- `native`：Claude Code 原生；
- `openspec`：Claude Code 加 OpenSpec 工作流。

三种执行方式的底层模型都是 DeepSeek `deepseek-flash`（V4.1-Flash），Claude Code 通过 DeepSeek 提供的 Anthropic 兼容接口调用它。Cursor 没有参与实测，原因见调研报告 §3.3。
