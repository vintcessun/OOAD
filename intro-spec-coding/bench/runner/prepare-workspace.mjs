#!/usr/bin/env node
// For tools that cannot be driven by the runner (Cursor): build the same starting workspace a run
// gets, so a person can run the tool by hand, then score it with evaluate-only.mjs.
//   node runner/prepare-workspace.mjs <spec> <label> <repeat> [--openspec <openspec tool id, e.g. cursor>]
// Prints the workspace path and the exact prompt to paste. Rules for the person running it:
//   new chat, paste the prompt verbatim, no follow-up messages, do not answer questions, note the wall time.
import { spawnSync } from "node:child_process";
import { cpSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BENCH = join(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const [spec, label, repeat] = args;
const osIdx = args.indexOf("--openspec");
const osTool = osIdx >= 0 ? args[osIdx + 1] : null;
if (!spec || !label || !repeat) {
  console.error("usage: prepare-workspace.mjs <spec> <label> <repeat> [--openspec cursor]");
  process.exit(2);
}

const runId = `apply-coupon_${spec}_${label}_r${repeat}`;
const ws = join(process.env.BENCH_WORK ?? join(process.env.TEMP ?? "/tmp", "spec-bench-ws"), runId);
rmSync(ws, { recursive: true, force: true });
mkdirSync(ws, { recursive: true });
cpSync(join(BENCH, "base"), ws, { recursive: true, filter: (s) => !s.includes("node_modules") });
cpSync(join(BENCH, "specs", "apply-coupon", `${spec}.md`), join(ws, "TASK.md"));
writeFileSync(join(ws, ".gitignore"), "node_modules\n*.db\n");

const NO_PROXY = ["-e", "http_proxy=", "-e", "https_proxy=", "-e", "HTTP_PROXY=", "-e", "HTTPS_PROXY="];
const setup = [
  "git config --global --add safe.directory '*'",
  osTool ? `openspec init --tools ${osTool} --no-animation . > /dev/null` : "true",
  "git init -q && git add -A && git commit -qm base && git tag base",
].join(" && ");
const r = spawnSync("docker", ["run", "--rm", ...NO_PROXY, "-v", `${ws}:/work`, "spec-bench:1", "bash", "-lc", setup], { encoding: "utf8" });
if (r.status !== 0) throw new Error(r.stderr);

const { NATIVE_PROMPT, OPENSPEC_PROMPT } = await import("./prompts.mjs");
console.log(`workspace: ${ws}\n`);
console.log("Run `npm install` in the workspace first (the tool may run npm test). Then paste exactly:\n");
console.log(osTool ? OPENSPEC_PROMPT : NATIVE_PROMPT);
console.log(`\nAfterwards: node runner/evaluate-only.mjs "${ws}" ${spec} ${label} ${repeat} --wall <seconds> --model "<model shown in the tool>"`);
