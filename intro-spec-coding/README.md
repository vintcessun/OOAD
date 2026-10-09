# 导论题目 1：Spec Coding 范式调研与工具链对比

这是「软件工程导论」的题目 1，与 RBAC 课程设计无关，只是放在同一个仓库里。Maven 构建和 CI 都不会碰这个目录。

## 产物

| 文件 | 内容 |
|---|---|
| [`docs/01-ppt-script.md`](docs/01-ppt-script.md) | 演讲 PPT 文字稿：24 页，每页给出标题、画面和讲稿 |
| [`docs/02-research-report.md`](docs/02-research-report.md) | 调研报告：技术脉络、工具机制对比、3 组对照实验（规格粒度 × 执行方式）与数据、坏规格实验、局限 |
| [`docs/03-selection-guide.md`](docs/03-selection-guide.md) | 面向中小团队的 Spec Coding 工具选型指南 |
| [`bench/results/summary.md`](bench/results/summary.md) | 实验数据汇总，由 `runner/aggregate.mjs` 生成 |

## 一句话结论

在同一个模型下：

- 规格写到可判定（L3），7 种执行方式 21/21 一次通过；
- 只写一半的规格（L2），通过率反而低于一句话需求（L1）；
- 换 Agent 带来的差距远小于换规格带来的差距；
- OpenSpec 不会让结果更对，但能让 Agent 的理解在写代码之前变得可以审查。

## 实验工程 `bench/`

```
bench/
  base/                   被测项目：订单服务（TypeScript + Node 24 + node:sqlite + Vitest），Agent 只能看到它
  specs/apply-coupon/     同一需求的 L1 / L2 / L3 三档规格，以及 3 份坏规格（DEFECTS.md 说明埋了什么）
  evaluator/
    hidden-tests/         41 个隐藏验收测试，每个标注 REQ 编号、严重度、类别
    reference/            参考实现：证明隐藏测试本身可以全部通过
    evaluate.sh           评估阶段：类型检查、公开测试、隐藏测试、diff、完整性检查
  evaluation.json         评分模型：0.35C + 0.20A + 0.15R + 0.10Q + 0.10E + 0.10S
  runner/
    run.mjs               跑一个 run：新工作区 → Agent 阶段（容器）→ 冻结 → 评估阶段（断网容器）→ 评分
    batch.mjs             批量跑实验矩阵，可中断续跑
    prompts.mjs           两条固定提示词
    parse.mjs             三个 Agent 的事件流解析、token 计价
    score.mjs             评分卡
    spec-lint.mjs         规格质量分（Spec Quality Score）
    aggregate.mjs         汇总 → results/summary.{md,json}
    rescore.mjs           修改评分规则后，从已保存的输出重新评分（不调用模型）
    bad-spec-evidence.mjs 摘取坏规格 run 的相关语句，供人工判读
    prepare-workspace.mjs / evaluate-only.mjs   手工补测通道（如 Cursor）
  docker/                 实验镜像：Claude Code 2.1.295、Codex 0.162.0、OpenCode 1.18.35、OpenSpec 1.14.1、Node 24.8
  results/
    runs/<run>/           81 个计入的 run：prompt、transcript、diff、测试报告、meta、评分卡
    timeouts-15min/       15 分钟上限下超时、已按 25 分钟重跑的原始 run（报告 §6）
    pilot/                正式实验前的试跑（不计入）
    batch-*.log           批量运行日志，包括失败与重试
```

`results/` 约 58 MB，主要是 Agent 的事件流。为了可追溯，全部保留。

## 复现

```bash
# 1. API Key 只放在本目录被忽略的 .env 里
echo "DEEPSEEK_API_KEY=sk-..." > intro-spec-coding/.env

# 2. 构建镜像：先在本机下载安装包，再在容器内离线安装
cd intro-spec-coding/bench
bash docker/fetch-vendor.sh
bash docker/build-image.sh           # 或 docker build -f docker/Dockerfile -t spec-bench:1 .

# 3. 跑实验
node runner/run.mjs L2 claude 1      # 单个 run
node runner/batch.mjs main 3         # 主矩阵 63 个 run
node runner/batch.mjs bad 2          # 坏规格 18 个 run

# 4. 汇总
node runner/aggregate.mjs
```

执行方式共 7 种：

- `prompt`：单次调用，无工具；
- `claude` / `codex` / `opencode`：Agent 原生；
- `claude-os` / `codex-os` / `opencode-os`：同一个 Agent，先执行 `openspec init`，再用 OpenSpec 提示词。

底层模型统一为 DeepSeek `deepseek-flash`，各 Agent 都按 DeepSeek 官方接入文档配置。Cursor 只能使用它托管的模型，因此没有进入受控实验。组员可以用以下两个脚本手工补测：

- `runner/prepare-workspace.mjs`：生成初始工作区和提示词；
- `runner/evaluate-only.mjs`：用同一套隐藏测试评分。
