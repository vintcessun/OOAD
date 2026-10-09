#!/usr/bin/env node
// Runs the experiment matrix with a small worker pool; skips runs that already have a score.json,
// so it can be stopped and resumed.   node runner/batch.mjs <plan> [concurrency]
//   plan  main  = L1/L2/L3 x 7 executors x repeats 1..3
//         bad   = 3 bad specs x 6 agent executors x repeat 1
import { spawn } from "node:child_process";
import { appendFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BENCH = join(dirname(fileURLToPath(import.meta.url)), "..");
const [plan = "main", conc = "3"] = process.argv.slice(2);
const AGENT_EXECS = ["claude", "codex", "opencode", "claude-os", "codex-os", "opencode-os"];

const jobs = [];
if (plan === "main") {
  // repeat-major order: every cell gets n=1 before any cell gets n=2
  for (const r of [1, 2, 3])
    for (const s of ["L3", "L2", "L1"]) for (const e of ["prompt", ...AGENT_EXECS]) jobs.push([s, e, r]);
} else if (plan === "bad") {
  for (const s of ["BAD-ambiguous", "BAD-missing", "BAD-conflict"]) for (const e of AGENT_EXECS) jobs.push([s, e, 1]);
} else {
  console.error("plan must be main or bad");
  process.exit(2);
}

// BENCH_REPEATS=3 limits a batch to some repeats, so a second batch process can share the work
const only = process.env.BENCH_REPEATS?.split(",").map(Number);
const todo = jobs.filter(
  ([s, e, r]) =>
    (!only || only.includes(r)) && !existsSync(join(BENCH, "results/runs", `apply-coupon_${s}_${e}_r${r}`, "score.json")),
);
console.log(`${todo.length} of ${jobs.length} runs to do, concurrency ${conc}`);

let next = 0;
async function worker() {
  while (next < todo.length) {
    const [s, e, r] = todo[next++];
    if (existsSync(join(BENCH, "results/runs", `apply-coupon_${s}_${e}_r${r}`, "score.json"))) continue; // done by the other batch
    await new Promise((resolve) => {
      const p = spawn(process.execPath, [join(BENCH, "runner/run.mjs"), s, e, String(r)], { stdio: ["ignore", "pipe", "pipe"] });
      let out = "";
      p.stdout.on("data", (d) => (out += d));
      p.stderr.on("data", (d) => (out += d));
      p.on("close", (code) => {
        console.log(`[${new Date().toTimeString().slice(0, 8)}] ${code === 0 ? "" : `EXIT ${code} `}${out.trim().split("\n").slice(-1)[0]}`);
        if (code !== 0) appendFileSync(join(BENCH, "results", "batch-errors.log"), `\n=== ${s} ${e} r${r} exit ${code}\n${out}\n`);
        resolve();
      });
    });
  }
}
await Promise.all(Array.from({ length: Number(conc) }, worker));
console.log("batch done");
