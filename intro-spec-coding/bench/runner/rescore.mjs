#!/usr/bin/env node
// Re-parses saved transcripts and re-scores finished runs (after a parser or scoring fix).
// Does not touch the workspace or call any model.   node runner/rescore.mjs
import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { parseClaude, parseCodex, parseOpenCode, tokenCost } from "./parse.mjs";
import { score } from "./score.mjs";

const RUNS = join(dirname(fileURLToPath(import.meta.url)), "..", "results", "runs");
const PARSE = { claude: parseClaude, codex: parseCodex, opencode: parseOpenCode };
let n = 0;
for (const d of readdirSync(RUNS)) {
  const dir = join(RUNS, d);
  if (!existsSync(join(dir, "score.json"))) continue;
  const meta = JSON.parse(readFileSync(join(dir, "meta.json"), "utf8"));
  const agent = meta.executor.replace(/-os$/, "");
  if (PARSE[agent] && existsSync(join(dir, "transcript.jsonl"))) {
    const parsed = PARSE[agent](readFileSync(join(dir, "transcript.jsonl"), "utf8"));
    Object.assign(meta, parsed, { exitCode: meta.exitCode, timedOut: meta.exitCode === 124 || meta.wallSec >= (meta.timeoutSec ?? 900) });
    meta.costCny = tokenCost(meta.tokens, new Date(meta.startedAt));
  }
  writeFileSync(join(dir, "meta.json"), JSON.stringify(meta, null, 2));
  writeFileSync(join(dir, "score.json"), JSON.stringify(score(dir, meta), null, 2));
  n++;
}
console.log(`rescored ${n} runs`);
