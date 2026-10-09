#!/usr/bin/env node
// One experiment run:  node runner/run.mjs <spec> <executor> <repeat>
//   spec      L1 | L2 | L3 | BAD-ambiguous | BAD-missing | BAD-conflict
//   executor  prompt                          one chat completion, no tools ("Prompt Coding" baseline)
//             claude | codex | opencode       the agent on its own
//             claude-os | codex-os | opencode-os   the same agent after `openspec init` + the OpenSpec prompt
// Steps: fresh workspace -> agent stage (container, no hidden tests) -> freeze -> evaluator stage -> score.
// The runner never talks back to the agent (no "continue", no test feedback).
import { spawnSync } from "node:child_process";
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { score } from "./score.mjs";
import { parseClaude, parseCodex, parseOpenCode, tokenCost } from "./parse.mjs";

const BENCH = join(dirname(fileURLToPath(import.meta.url)), "..");
const ROOT = join(BENCH, "..");
const IMAGE = "spec-bench:1";
const MODEL = process.env.BENCH_MODEL ?? "deepseek-flash";
const TIMEOUT_S = 1500; // was 900 for the first 32 runs; raised after DeepSeek output speed dropped ~3x (report §6)
const MAX_TURNS = 80;

const [spec, executor, repeat] = process.argv.slice(2);
const AGENTS = ["claude", "codex", "opencode"];
const EXECUTORS = ["prompt", ...AGENTS, ...AGENTS.map((a) => `${a}-os`)];
if (!spec || !EXECUTORS.includes(executor) || !repeat) {
  console.error(`usage: run.mjs <L1|L2|L3|BAD-*> <${EXECUTORS.join("|")}> <repeat>`);
  process.exit(2);
}

const env = Object.fromEntries(
  readFileSync(join(ROOT, ".env"), "utf8")
    .split(/\r?\n/)
    .filter((l) => l.includes("="))
    .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1).trim()]),
);
const KEY = env.DEEPSEEK_API_KEY;
const agent = executor.replace(/-os$/, "");
const withOpenSpec = executor.endsWith("-os");

const runId = `apply-coupon_${spec}_${executor}_r${repeat}`;
const specFile = join(BENCH, "specs", "apply-coupon", `${spec}.md`);
const outDir = join(BENCH, "results", "runs", runId);
const wsRoot = process.env.BENCH_WORK ?? join(process.env.TEMP ?? "/tmp", "spec-bench-ws");
const ws = join(wsRoot, runId);

const { NATIVE_PROMPT, OPENSPEC_PROMPT } = await import("./prompts.mjs");

// Codex config from DeepSeek's setup script (cdn.deepseek.com/api-docs/codex-deepseek-setup.sh),
// except the key is read from the environment (env_key) instead of being written to the file.
const CODEX_CONFIG = [
  `model = "${MODEL}"`,
  'model_provider = "deepseek"',
  'forced_login_method = "api"',
  'model_reasoning_effort = "high"',
  'web_search = "disabled"',
  "show_raw_agent_reasoning = true",
  'model_catalog_json = "/opt/codex/models.json"',
  "[model_providers.deepseek]",
  'name = "deepseek"',
  'base_url = "https://api.deepseek.com/"',
  'wire_api = "responses"',
  'env_key = "DEEPSEEK_API_KEY"',
];

const sh = (cmd, args, opts = {}) => {
  const r = spawnSync(cmd, args, { encoding: "utf8", maxBuffer: 1 << 28, ...opts });
  // A hung container must not crash the runner: record it and let the caller clean up.
  if (r.error && r.error.code !== "ETIMEDOUT") throw r.error;
  return r;
};
const docker = (args, opts) => sh("docker", args, opts);
const vol = (host, ctr, ro = false) => ["-v", `${host}:${ctr}${ro ? ":ro" : ""}`];
// Docker Desktop injects the host proxy (host.docker.internal:7890), unreachable from containers here.
const NO_PROXY = ["-e", "http_proxy=", "-e", "https_proxy=", "-e", "HTTP_PROXY=", "-e", "HTTPS_PROXY="];

// ---------- 0. one process per run id (two batch processes may share the queue) ----------
if (existsSync(join(outDir, "score.json")) && !process.env.BENCH_FORCE) {
  console.log(`${runId}: skipped (already scored; BENCH_FORCE=1 to redo)`);
  process.exit(0);
}
const lock = join(BENCH, "results", "runs", `${runId}.lock`);
mkdirSync(dirname(lock), { recursive: true });
try {
  writeFileSync(lock, String(process.pid), { flag: "wx" });
} catch {
  console.log(`${runId}: skipped (locked by another process)`);
  process.exit(0);
}
process.on("exit", () => rmSync(lock, { force: true }));

// ---------- 1. fresh workspace ----------
rmSync(ws, { recursive: true, force: true });
rmSync(outDir, { recursive: true, force: true });
mkdirSync(ws, { recursive: true });
mkdirSync(outDir, { recursive: true });
cpSync(join(BENCH, "base"), ws, { recursive: true, filter: (s) => !s.includes("node_modules") });
cpSync(specFile, join(ws, "TASK.md"));
writeFileSync(join(ws, ".gitignore"), "node_modules\n*.db\n");

// Each agent is pointed at the same DeepSeek model, following DeepSeek's own integration guides
// (api-docs.deepseek.com/zh-cn/quick_start/agent_integrations/{claude_code,codex,opencode}).
const agentEnv = [
  "-e", `DEEPSEEK_API_KEY=${KEY}`,
  "-e", "ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic",
  "-e", `ANTHROPIC_AUTH_TOKEN=${KEY}`,
  "-e", `ANTHROPIC_MODEL=${MODEL}`,
  "-e", `ANTHROPIC_SMALL_FAST_MODEL=${MODEL}`,
  "-e", `ANTHROPIC_DEFAULT_HAIKU_MODEL=${MODEL}`,
  "-e", `ANTHROPIC_DEFAULT_SONNET_MODEL=${MODEL}`,
  "-e", `ANTHROPIC_DEFAULT_OPUS_MODEL=${MODEL}`,
];

// Base commit inside the container (also runs `openspec init` for the treatment group,
// so OpenSpec's scaffolding is part of "base" and not counted as the agent's diff).
const setup = [
  "git config --global --add safe.directory '*'",
  "ln -sfn /opt/deps/node_modules node_modules",
  withOpenSpec ? `openspec init --tools ${agent} --no-animation . > /tmp/openspec-init.log 2>&1 || (cat /tmp/openspec-init.log; exit 1)` : "true",
  "git init -q && git add -A && git commit -qm base && git tag base",
].join(" && ");
let r = docker(["run", "--rm", ...NO_PROXY, ...vol(ws, "/work"), IMAGE, "bash", "-lc", setup]);
// Docker Desktop sometimes answers 500 while waiting for `--rm` cleanup although the container
// finished; trust the workspace, not the exit code.
const baseOk = () => sh("git", ["-c", "safe.directory=*", "-C", ws, "rev-parse", "--verify", "-q", "base"]).status === 0;
if (r.status !== 0 && !baseOk()) throw new Error(`setup failed: ${r.stdout}${r.stderr}`);

// ---------- 2. agent stage ----------
const balanceBefore = await balance();
const t0 = Date.now();
let agentMeta;
if (executor === "prompt") {
  agentMeta = await promptOnly();
} else {
  const prompt = withOpenSpec ? OPENSPEC_PROMPT : NATIVE_PROMPT;
  writeFileSync(join(outDir, "prompt.txt"), prompt);
  const cli = {
    claude: `claude -p "$BENCH_PROMPT" --model ${MODEL} --output-format stream-json --verbose --max-turns ${MAX_TURNS} --dangerously-skip-permissions`,
    codex: `codex exec --json --dangerously-bypass-approvals-and-sandbox --skip-git-repo-check -m ${MODEL} "$BENCH_PROMPT" < /dev/null`,
    opencode: `opencode run --format json --auto -m deepseek/${MODEL} "$BENCH_PROMPT" < /dev/null`,
  }[agent];
  const cmd = [
    "git config --global --add safe.directory '*'",
    agent === "codex" ? `mkdir -p ~/.codex && printf '%s\\n' ${CODEX_CONFIG.map((l) => `'${l}'`).join(" ")} > ~/.codex/config.toml` : "true",
    // -k: OpenCode ignores SIGTERM, so follow up with SIGKILL
    `timeout -k 20 ${TIMEOUT_S} ${cli}`,
  ].join(" && ");
  r = docker(
    ["run", "--rm", "--name", runId.replace(/[^a-zA-Z0-9_.-]/g, "-"), ...NO_PROXY, ...vol(ws, "/work"), ...agentEnv,
      "-e", "IS_SANDBOX=1", "-e", `BENCH_PROMPT=${prompt}`, IMAGE, "bash", "-lc", cmd],
    { timeout: (TIMEOUT_S + 120) * 1000 },
  );
  if (r.error) docker(["rm", "-f", runId.replace(/[^a-zA-Z0-9_.-]/g, "-")]);
  writeFileSync(join(outDir, "transcript.jsonl"), r.stdout ?? "");
  if (r.stderr) writeFileSync(join(outDir, "agent-stderr.txt"), r.stderr);
  const parse = { claude: parseClaude, codex: parseCodex, opencode: parseOpenCode }[agent];
  agentMeta = { exitCode: r.status, timedOut: r.status === 124 || (Date.now() - t0) / 1000 >= TIMEOUT_S, ...parse(r.stdout ?? "") };
}
const wallSec = (Date.now() - t0) / 1000;
const balanceAfter = await balance();

// ---------- 3. freeze + evaluate ----------
const evalOut = join(outDir, "eval");
mkdirSync(evalOut, { recursive: true });
r = docker([
  "run", "--rm", "--network", "none",
  ...vol(ws, "/frozen", true),
  ...vol(BENCH, "/bench", true),
  ...vol(specFile, "/task.md", true),
  ...vol(evalOut, "/out"),
  IMAGE, "bash", "/bench/evaluator/evaluate.sh",
], { timeout: 600_000 });
if (r.status !== 0) console.error("evaluator:", r.stderr);

const meta = {
  runId, task: "apply-coupon", spec, executor, repeat: Number(repeat), model: MODEL,
  startedAt: new Date(t0).toISOString(), wallSec, timeoutSec: TIMEOUT_S,
  balanceBefore, balanceAfter,
  balanceDeltaCny: balanceBefore != null && balanceAfter != null ? +(balanceBefore - balanceAfter).toFixed(4) : null,
  ...agentMeta,
};
meta.costCny = tokenCost(meta.tokens, new Date(t0));
meta.costCnyPeak = tokenCost(meta.tokens, new Date(t0), true);
writeFileSync(join(outDir, "meta.json"), JSON.stringify(meta, null, 2));
const card = score(outDir, meta);
writeFileSync(join(outDir, "score.json"), JSON.stringify(card, null, 2));
console.log(`${runId}: total=${card.total} firstPass=${card.firstPassSuccess} hidden=${card.detail.hiddenPassed}/${card.detail.hiddenTotal} cost=¥${meta.costCny} ${wallSec.toFixed(0)}s`);

// ---------- helpers ----------
async function balance() {
  try {
    const res = await fetch("https://api.deepseek.com/user/balance", { headers: { Authorization: `Bearer ${KEY}` }, signal: AbortSignal.timeout(15_000) });
    const j = await res.json();
    return Number(j.balance_infos.find((b) => b.currency === "CNY").total_balance);
  } catch {
    return null;
  }
}

// Prompt Coding baseline: one chat completion, no tools, no iteration.
async function promptOnly() {
  const files = [];
  const walk = (d) => {
    for (const n of readdirSync(d)) {
      const p = join(d, n);
      if ([".git", "node_modules", ".gitignore"].includes(n)) continue;
      if (statSync(p).isDirectory()) walk(p);
      else files.push(p);
    }
  };
  walk(ws);
  const listing = files
    .map((p) => `=== FILE: ${relative(ws, p).replaceAll("\\", "/")} ===\n${readFileSync(p, "utf8")}`)
    .join("\n\n");
  const system = "You are a software engineer. You cannot run commands; you only return file contents.";
  const user = `${NATIVE_PROMPT}

The full repository is below. Reply with every file you create or change, each as
=== FILE: <path> ===
<complete new file content>
and nothing else outside those blocks.

${listing}`;
  writeFileSync(join(outDir, "prompt.txt"), user.slice(0, user.indexOf("=== FILE:")) + "[repository files omitted here]\n");
  const res = await fetch("https://api.deepseek.com/chat/completions", {
    method: "POST",
    headers: { "content-type": "application/json", Authorization: `Bearer ${KEY}` },
    body: JSON.stringify({ model: MODEL, messages: [{ role: "system", content: system }, { role: "user", content: user }] }),
  });
  const j = await res.json();
  const out = j.choices?.[0]?.message?.content ?? "";
  writeFileSync(join(outDir, "transcript.md"), out);
  const written = [];
  for (const m of out.matchAll(/=== FILE: (.+?) ===\r?\n([\s\S]*?)(?=\r?\n=== FILE: |\s*$)/g)) {
    const rel = m[1].trim().replace(/^\.?\//, "");
    if (rel.includes("..")) continue;
    const dest = join(ws, rel);
    mkdirSync(dirname(dest), { recursive: true });
    writeFileSync(dest, m[2].replace(/^```\w*\r?\n/, "").replace(/\r?\n```\s*$/, "") + "\n");
    written.push(rel);
  }
  const u = j.usage ?? {};
  return {
    exitCode: res.ok ? 0 : 1, turns: 1, toolCalls: 0, toolNames: {}, askAttempts: 0, filesRead: 0,
    stopReason: j.choices?.[0]?.finish_reason ?? "error", finalText: out.slice(-1500), filesWritten: written,
    tokens: {
      input: u.prompt_cache_miss_tokens ?? u.prompt_tokens ?? 0,
      cacheRead: u.prompt_cache_hit_tokens ?? 0,
      cacheCreate: 0,
      output: u.completion_tokens ?? 0,
    },
  };
}
