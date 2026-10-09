#!/usr/bin/env node
// Collects what each bad-spec run said about the defects, for manual judgement (results/bad-spec-judgement.json).
// Sources: the agent's final message, and any OpenSpec artifacts it wrote (from the diff).
// Output: results/bad-spec-evidence.md — every sentence that mentions ambiguity, missing info, conflicts or assumptions.
import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BENCH = join(dirname(fileURLToPath(import.meta.url)), "..");
const RUNS = join(BENCH, "results", "runs");
const CUE =
  /ambigu|unspecif|not specif|isn'?t specified|is not specified|no (standalone )?numeric|isn'?t (given|defined|stated)|not stated|flag|doesn'?t (say|specify|give|define)|does not (say|specify|give|define)|unclear|interpret|assum|conflict|contradict|inconsisten|judg(e)?ment|discrepan|silent on|no numeric|not defined|undefined|left open|clarif|歧义|冲突|矛盾|未(说明|规定|指定|定义)|没有(说明|规定|给出)|不明确|假设|理解为/i;

const out = ["# 坏规格实验：判读证据（自动摘取，供人工判读）", ""];
for (const d of readdirSync(RUNS).filter((d) => d.includes("_BAD-")).sort()) {
  const metaPath = join(RUNS, d, "meta.json");
  if (!existsSync(metaPath)) continue;
  const meta = JSON.parse(readFileSync(metaPath, "utf8"));
  const diff = existsSync(join(RUNS, d, "eval", "diff.patch")) ? readFileSync(join(RUNS, d, "eval", "diff.patch"), "utf8") : "";
  const openspecText = diff
    .split(/^diff --git /m)
    .filter((c) => / b\/openspec\/changes\/.*\.md/.test(c.split("\n")[0]))
    .map((c) => c.split("\n").filter((l) => l.startsWith("+") && !l.startsWith("+++")).map((l) => l.slice(1)).join("\n"))
    .join("\n");
  const hits = (text) =>
    text
      .split(/(?<=[.。!?！？])\s+|\n/)
      .map((s) => s.trim())
      .filter((s) => s.length > 15 && CUE.test(s));
  out.push(`## ${d}`, "", `- askAttempts: ${meta.askAttempts ?? 0}`, "", "**最终输出中的相关句子**", "");
  for (const s of hits(meta.finalText ?? "")) out.push(`> ${s}`, "");
  // OpenSpec's design template has "Risks / Trade-offs" and "Open Questions": quote them whole.
  const sections = openspecText
    .split(/^(?=## )/m)
    .filter((sec) => /^## (Risks|Open Questions)/.test(sec))
    .map((sec) => sec.trim());
  out.push("**OpenSpec design.md 的 Risks / Open Questions 段**", "");
  if (!sections.length) out.push("（无）", "");
  for (const sec of sections) out.push(sec.split("\n").map((l) => `> ${l}`).join("\n"), "");
  out.push("**OpenSpec 产物中的相关句子**", "");
  const os = hits(openspecText);
  if (!os.length) out.push("（无）", "");
  for (const s of os.slice(0, 25)) out.push(`> ${s}`, "");
}
writeFileSync(join(BENCH, "results", "bad-spec-evidence.md"), out.join("\n"));
console.log(`wrote results/bad-spec-evidence.md`);
