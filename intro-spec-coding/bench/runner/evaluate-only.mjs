#!/usr/bin/env node
// Scores a workspace that a person ran by hand (see prepare-workspace.mjs).
//   node runner/evaluate-only.mjs <workspace> <spec> <label> <repeat> --wall <seconds> [--model <name>]
// Token usage is unknown for hand runs, so Efficiency only uses wall time (token part scored 0)
// and the run is marked manual; aggregate.mjs lists these separately from the controlled matrix.
import { spawnSync } from "node:child_process";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { score } from "./score.mjs";

const BENCH = join(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const [ws, spec, label, repeat] = args;
const opt = (k) => (args.indexOf(k) >= 0 ? args[args.indexOf(k) + 1] : null);
const wallSec = Number(opt("--wall"));
if (!ws || !spec || !label || !repeat || !wallSec) {
  console.error("usage: evaluate-only.mjs <workspace> <spec> <label> <repeat> --wall <seconds> [--model <name>]");
  process.exit(2);
}

const runId = `apply-coupon_${spec}_${label}_r${repeat}`;
const outDir = join(BENCH, "results", "runs", runId);
rmSync(outDir, { recursive: true, force: true });
mkdirSync(join(outDir, "eval"), { recursive: true });
const specFile = join(BENCH, "specs", "apply-coupon", `${spec}.md`);
const r = spawnSync("docker", [
  "run", "--rm", "--network", "none",
  "-v", `${ws}:/frozen:ro`, "-v", `${BENCH}:/bench:ro`, "-v", `${specFile}:/task.md:ro`, "-v", `${join(outDir, "eval")}:/out`,
  "spec-bench:1", "bash", "/bench/evaluator/evaluate.sh",
], { encoding: "utf8" });
if (r.status !== 0) console.error(r.stderr);

const meta = {
  runId, task: "apply-coupon", spec, executor: label, repeat: Number(repeat), manual: true,
  model: opt("--model") ?? "unknown (tool-hosted)", wallSec,
  tokens: null, costCny: null, turns: null, toolCalls: null, askAttempts: null,
};
writeFileSync(join(outDir, "meta.json"), JSON.stringify(meta, null, 2));
const card = score(outDir, meta);
writeFileSync(join(outDir, "score.json"), JSON.stringify(card, null, 2));
console.log(`${runId}: total=${card.total} firstPass=${card.firstPassSuccess} hidden=${card.detail.hiddenPassed}/${card.detail.hiddenTotal}`);
