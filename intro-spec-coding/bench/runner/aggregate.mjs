#!/usr/bin/env node
// Aggregates results/runs/*/score.json into results/summary.json + results/summary.md.
import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { lint } from "./spec-lint.mjs";

const BENCH = join(dirname(fileURLToPath(import.meta.url)), "..");
const RUNS = join(BENCH, "results", "runs");
const cards = readdirSync(RUNS)
  .filter((d) => existsSync(join(RUNS, d, "score.json")) && !/_r0$/.test(d)) // r0 = pilot runs, excluded
  .map((d) => ({ ...JSON.parse(readFileSync(join(RUNS, d, "score.json"), "utf8")), meta: JSON.parse(readFileSync(join(RUNS, d, "meta.json"), "utf8")) }));

const SPECS = ["L1", "L2", "L3"];
const AGENTS = ["claude", "codex", "opencode"];
const EXECS = ["prompt", ...AGENTS, ...AGENTS.map((a) => `${a}-os`)];
const AGENT_LABEL = { claude: "Claude Code", codex: "Codex", opencode: "OpenCode" };
const EXEC_LABEL = {
  prompt: "Prompt-only（单次调用）",
  ...Object.fromEntries(AGENTS.map((a) => [a, AGENT_LABEL[a]])),
  ...Object.fromEntries(AGENTS.map((a) => [`${a}-os`, `${AGENT_LABEL[a]} + OpenSpec`])),
};
const mean = (xs) => (xs.length ? xs.reduce((s, x) => s + x, 0) / xs.length : NaN);
const sd = (xs) => {
  if (xs.length < 2) return NaN;
  const m = mean(xs);
  return Math.sqrt(xs.reduce((s, x) => s + (x - m) ** 2, 0) / (xs.length - 1));
};
const f1 = (x) => (Number.isNaN(x) ? "—" : x.toFixed(1));
const f2 = (x) => (Number.isNaN(x) ? "—" : x.toFixed(2));
const pct = (x) => (Number.isNaN(x) ? "—" : `${Math.round(x * 100)}%`);
const pick = (spec, ex) => cards.filter((c) => c.spec === spec && c.executor === ex);

const specScore = Object.fromEntries(
  readdirSync(join(BENCH, "specs/apply-coupon"))
    .filter((f) => /^(L\d|BAD-.+)\.md$/.test(f))
    .map((f) => [f.replace(".md", ""), lint(readFileSync(join(BENCH, "specs/apply-coupon", f), "utf8")).score]),
);

const cells = [];
for (const spec of SPECS)
  for (const ex of EXECS) {
    const cs = pick(spec, ex);
    if (!cs.length) continue;
    cells.push({
      spec, executor: ex, n: cs.length,
      firstPass: cs.filter((c) => c.firstPassSuccess).length,
      strict: cs.filter((c) => c.strictSuccess).length,
      hiddenRate: mean(cs.map((c) => c.detail.hiddenWeightedPassRate)),
      hiddenRateSd: sd(cs.map((c) => c.detail.hiddenWeightedPassRate)),
      boundaryRate: mean(cs.map((c) => c.detail.boundaryPassRate)),
      reqMet: mean(cs.map((c) => c.detail.requirementsMet.length)),
      reqTotal: cs[0].detail.requirementsTotal,
      total: mean(cs.map((c) => c.total)),
      totalSd: sd(cs.map((c) => c.total)),
      dims: Object.fromEntries(["C", "A", "R", "Q", "E", "S"].map((k) => [k, mean(cs.map((c) => c.dimensions[k]))])),
      wallSec: mean(cs.map((c) => c.detail.wallSec)),
      tokens: mean(cs.map((c) => c.detail.totalTokens).filter((x) => x != null)),
      timeouts: cs.filter((c) => c.detail.timedOut).length,
      unknownCost: cs.filter((c) => c.detail.costCny == null).length,
      costCny: mean(cs.map((c) => c.detail.costCny ?? NaN).filter((x) => !Number.isNaN(x))),
      turns: mean(cs.map((c) => c.detail.turns ?? 0)),
      toolCalls: mean(cs.map((c) => c.detail.toolCalls ?? 0)),
      changedLines: mean(cs.map((c) => c.detail.changedCodeLines)),
      violations: cs.flatMap((c) => c.detail.violations.map((v) => v[1])),
    });
  }

const failureBySpec = {};
for (const spec of SPECS) {
  const agg = {};
  for (const c of cards.filter((c) => c.spec === spec && EXECS.includes(c.executor) && c.executor !== "prompt"))
    for (const [k, v] of Object.entries(c.detail.failureCauses)) agg[k] = (agg[k] ?? 0) + v;
  failureBySpec[spec] = agg;
}

const bad = cards.filter((c) => c.spec.startsWith("BAD-"));
const judgementFile = join(BENCH, "results", "bad-spec-judgement.json");
const judgement = existsSync(judgementFile) ? JSON.parse(readFileSync(judgementFile, "utf8")) : {};

const spend = cards.reduce((s, c) => s + (c.detail.costCny ?? 0), 0);
const summaryJson = () => JSON.stringify({ generatedAt: new Date().toISOString(), runs: cards.length, spendCny: spend, specScore, cells, failureBySpec, factorial: fx, specScoreCorrelation: corr }, null, 2);

// ---------- markdown ----------
const L = [];
L.push(`# 实验结果汇总（自动生成，勿手改）`, ``, `生成时间 ${new Date().toISOString()} · 共 ${cards.length} 个 run（不含 r0 试跑）· 模型 deepseek-flash · 花费合计 ¥${spend.toFixed(2)}（token 用量 × 官方价格；并行运行时余额差无法分摊到单个 run）`, ``);
L.push(`## 表 1  规格粒度 × 执行方式：一次通过率（First-pass Success）`, ``);
L.push(`| 规格 | 规格质量分 | 执行方式 | n | 一次通过 | 严格通过 | 隐藏测试加权通过率 | 需求满足 | 总分 |`, `|---|---|---|---|---|---|---|---|---|`);
for (const c of cells)
  L.push(`| ${c.spec} | ${specScore[c.spec]} | ${EXEC_LABEL[c.executor]} | ${c.n} | ${c.firstPass}/${c.n}（${pct(c.firstPass / c.n)}） | ${c.strict}/${c.n} | ${f1(c.hiddenRate)}% ± ${f1(c.hiddenRateSd)} | ${f1(c.reqMet)}/${c.reqTotal} | ${f1(c.total)} ± ${f1(c.totalSd)} |`);
L.push(``, `## 表 2  六维得分（均值）`, ``, `| 规格 | 执行方式 | C 正确性/35 | A 符合度/20 | R 鲁棒性/15 | Q 质量/10 | E 效率/10 | S 范围/10 |`, `|---|---|---|---|---|---|---|---|`);
for (const c of cells) L.push(`| ${c.spec} | ${EXEC_LABEL[c.executor]} | ${f1(c.dims.C)} | ${f1(c.dims.A)} | ${f1(c.dims.R)} | ${f1(c.dims.Q)} | ${f1(c.dims.E)} | ${f1(c.dims.S)} |`);
L.push(``, `## 表 3  成本与过程（均值）`, ``, `| 规格 | 执行方式 | 耗时 s | 超时（15 min） | Token（含缓存） | 花费 ¥ | 工具调用 | 改动代码行 | 边界测试通过率 |`, `|---|---|---|---|---|---|---|---|---|`);
for (const c of cells) L.push(`| ${c.spec} | ${EXEC_LABEL[c.executor]} | ${f1(c.wallSec)} | ${c.timeouts}/${c.n} | ${Number.isNaN(c.tokens) ? "—" : Math.round(c.tokens).toLocaleString("en")} | ${f2(c.costCny)}${c.unknownCost ? `（${c.unknownCost} 个未知）` : ""} | ${f1(c.toolCalls)} | ${f1(c.changedLines)} | ${f1(c.boundaryRate)}% |`);
L.push(``, `说明：超时的 run 按「冻结当时的工作区」评分，不重跑。Claude Code 超时时只能累加主循环逐条消息的用量（偏低）；Codex 超时时没有用量数据，记为未知，不计入均值。各工具对「回合」的定义不同，因此不比较回合数。`);
L.push(``, `## 表 4  失败原因分布（6 种 Agent 执行方式合计，按失败的隐藏测试计数；括号内为占该档全部失败的比例）`, ``);
const causes = [...new Set(Object.values(failureBySpec).flatMap((o) => Object.keys(o)))].sort();
const runsBySpec = Object.fromEntries(SPECS.map((s) => [s, cards.filter((c) => c.spec === s && EXECS.includes(c.executor) && c.executor !== "prompt").length]));
L.push(`| 原因 | ${SPECS.join(" | ")} |`, `|---|${SPECS.map(() => "---").join("|")}|`);
for (const k of causes) {
  L.push(`| ${k} | ${SPECS.map((s) => {
    const tot = Object.values(failureBySpec[s]).reduce((a, b) => a + b, 0);
    const v = failureBySpec[s][k] ?? 0;
    return tot ? `${v}（${pct(v / tot)}）` : "—";
  }).join(" | ")} |`);
}
// ---- factorial view: agent effect and OpenSpec effect, per spec level
const fx = [];
for (const spec of SPECS) {
  for (const a of AGENTS) {
    const nat = pick(spec, a), os = pick(spec, `${a}-os`);
    if (!nat.length || !os.length) continue;
    const m = (cs, f) => mean(cs.map(f));
    fx.push({
      spec, agent: a, nNative: nat.length, nOs: os.length,
      fpNative: nat.filter((c) => c.firstPassSuccess).length / nat.length,
      fpOs: os.filter((c) => c.firstPassSuccess).length / os.length,
      hidNative: m(nat, (c) => c.detail.hiddenWeightedPassRate),
      hidOs: m(os, (c) => c.detail.hiddenWeightedPassRate),
      costNative: m(nat, (c) => c.detail.costCny), costOs: m(os, (c) => c.detail.costCny),
      timeNative: m(nat, (c) => c.detail.wallSec), timeOs: m(os, (c) => c.detail.wallSec),
    });
  }
}
L.push(``, `每档失败测试总数（分母）：${SPECS.map((s) => `${s} ${Object.values(failureBySpec[s]).reduce((a, b) => a + b, 0)} 个，来自 ${runsBySpec[s]} 个 run`).join("；")}。`);
L.push(``, `## 表 4b  OpenSpec 效应：同一 Agent、同一规格，加与不加 OpenSpec`, ``,
  `| 规格 | Agent | n | 一次通过 原生 → +OpenSpec | 隐藏测试加权通过率 原生 → +OpenSpec | 花费 ¥ 原生 → +OpenSpec | 耗时 s 原生 → +OpenSpec |`, `|---|---|---|---|---|---|---|`);
for (const f of fx)
  L.push(`| ${f.spec} | ${AGENT_LABEL[f.agent]} | ${f.nNative}/${f.nOs} | ${pct(f.fpNative)} → ${pct(f.fpOs)} | ${f1(f.hidNative)}% → ${f1(f.hidOs)}%（${f.hidOs - f.hidNative >= 0 ? "+" : ""}${f1(f.hidOs - f.hidNative)}） | ${f2(f.costNative)} → ${f2(f.costOs)} | ${f1(f.timeNative)} → ${f1(f.timeOs)} |`);
L.push(``, `## 表 4c  规格粒度效应（按执行方式汇总，隐藏测试加权通过率均值 %）`, ``, `| 执行方式 | L1 | L2 | L3 | L3 − L1 |`, `|---|---|---|---|---|`);
for (const ex of EXECS) {
  const v = SPECS.map((s) => mean(pick(s, ex).map((c) => c.detail.hiddenWeightedPassRate)));
  if (v.every((x) => Number.isNaN(x))) continue;
  L.push(`| ${EXEC_LABEL[ex]} | ${v.map(f1).join(" | ")} | ${f1(v[2] - v[0])} |`);
}
const corr = (() => {
  const pts = cards.filter((c) => !c.spec.startsWith("BAD-")).map((c) => [specScore[c.spec], c.detail.hiddenWeightedPassRate]);
  const mx = mean(pts.map((q) => q[0])), my = mean(pts.map((q) => q[1]));
  const sxy = pts.reduce((t, q) => t + (q[0] - mx) * (q[1] - my), 0);
  const sxx = pts.reduce((t, q) => t + (q[0] - mx) ** 2, 0), syy = pts.reduce((t, q) => t + (q[1] - my) ** 2, 0);
  return { n: pts.length, r: sxy / Math.sqrt(sxx * syy) };
})();
L.push(``, `规格质量分与隐藏测试加权通过率的 Pearson 相关系数：r = ${corr.r.toFixed(2)}（n = ${corr.n} 个 run；规格质量分只有 3 个取值，r 主要反映三档之间的差异）。`);

const manual = cards.filter((c) => c.meta.manual);
L.push(``, `## 表 4d  手工运行（如 Cursor）：同一套规格与隐藏测试，但模型由工具托管、不是 deepseek-flash，只能并列参考`, ``);
if (!manual.length) L.push(`暂无数据。`);
else {
  L.push(`| run | 模型 | 一次通过 | 隐藏测试加权通过率 | 总分 | 耗时 s |`, `|---|---|---|---|---|---|`);
  for (const c of manual) L.push(`| ${c.runId} | ${c.meta.model} | ${c.firstPassSuccess ? "✓" : "✗"} | ${c.detail.hiddenWeightedPassRate}% | ${c.total} | ${c.detail.wallSec} |`);
}

L.push(``, `## 表 5  坏规格实验`, ``, `| run | 规格质量分 | 隐藏测试加权通过率 | AskUserQuestion 尝试 | 是否指出缺陷（人工判读） | 说明 |`, `|---|---|---|---|---|---|`);
for (const c of bad.sort((a, b) => a.runId.localeCompare(b.runId))) {
  const j = judgement[c.runId] ?? {};
  L.push(`| ${c.runId} | ${specScore[c.spec]} | ${f1(c.detail.hiddenWeightedPassRate)}% | ${c.detail.askAttempts ?? 0} | ${j.detected ?? "待判读"} | ${j.note ?? ""} |`);
}
L.push(``, `## 表 6  逐个 run`, ``, `| run | 一次通过 | 隐藏 | 总分 | C/A/R/Q/E/S | 耗时 s | ¥ | 违规 |`, `|---|---|---|---|---|---|---|---|`);
for (const c of cards.sort((a, b) => a.runId.localeCompare(b.runId)))
  L.push(`| ${c.runId} | ${c.firstPassSuccess ? "✓" : "✗"} | ${c.detail.hiddenPassed}/${c.detail.hiddenTotal} | ${c.total} | ${Object.values(c.dimensions).join("/")} | ${c.detail.wallSec} | ${c.detail.costCny ?? "—"} | ${c.detail.violations.map((v) => v[0]).join(",") || "—"} |`);
writeFileSync(join(BENCH, "results", "summary.json"), summaryJson());
writeFileSync(join(BENCH, "results", "summary.md"), L.join("\n") + "\n");
console.log(L.slice(0, 16).join("\n"));
