// Turns one run's evaluator output into a score card (see evaluation.json for the weights).
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const BENCH = join(dirname(fileURLToPath(import.meta.url)), "..");
const CFG = JSON.parse(readFileSync(join(BENCH, "evaluation.json"), "utf8"));
const D = CFG.dimensions;

const readJson = (p) => {
  try {
    return JSON.parse(readFileSync(p, "utf8"));
  } catch {
    return null;
  }
};
const readText = (p) => (existsSync(p) ? readFileSync(p, "utf8") : "");
const r1 = (x) => Math.round(x * 10) / 10;

/** Hidden test titles look like "H01 [REQ-07 critical functional] text". */
const TITLE = /^(\w+) \[(\S+) (critical|major|minor) (functional|boundary|error|state|regression)\]/;

/** All hidden tests, from the source file, so a test that never ran still counts as failed. */
function hiddenCatalogue() {
  const src = readFileSync(join(BENCH, "evaluator/hidden-tests/apply-coupon.hidden.test.ts"), "utf8");
  const out = [];
  for (const m of src.matchAll(/it\(\s*"(\w+ \[[^\]]+\][^"]*)"/g)) out.push(m[1]);
  // error-code cases are generated from a table
  for (const m of src.matchAll(/\[\s*"(E\d+)",\s*"(REQ-\d+)",\s*(\d+),\s*"(\w+)"/g)) {
    out.push(`${m[1]} [${m[2]} major error] returns ${m[3]} ${m[4]}`);
  }
  return out.map((title) => {
    const [, id, req, severity, category] = TITLE.exec(title);
    return { id, req, severity, category, title };
  });
}

function passedTitles(vitestJson) {
  const ok = new Set();
  for (const f of vitestJson?.testResults ?? []) {
    for (const a of f.assertionResults ?? []) if (a.status === "passed") ok.add(a.title);
  }
  return ok;
}

const weighted = (tests) => {
  const w = (t) => CFG.severityWeight[t.severity];
  const total = tests.reduce((s, t) => s + w(t), 0);
  return total === 0 ? 1 : tests.filter((t) => t.passed).reduce((s, t) => s + w(t), 0) / total;
};

export function score(runDir, meta) {
  const ev = join(runDir, "eval");
  const tscExit = Number(readText(join(ev, "tsc.exit")).trim() || 1);
  const tscErrors = (readText(join(ev, "tsc.txt")).match(/error TS\d+/g) ?? []).length;
  const pub = readJson(join(ev, "public.json"));
  const hid = readJson(join(ev, "hidden.json"));
  const own = readJson(join(ev, "agent-tests.json"));
  const integrity = Object.fromEntries(
    readText(join(ev, "integrity.txt"))
      .split("\n")
      .filter(Boolean)
      .map((l) => l.split("=")),
  );

  const passed = passedTitles(hid);
  const tests = hiddenCatalogue().map((t) => ({ ...t, passed: passed.has(t.title) }));
  const cat = (c) => tests.filter((t) => t.category === c);
  const publicTotal = pub?.numTotalTests || 5; // no report (crash, compile error) still means 5 public tests
  const publicPassed = pub?.numPassedTests ?? 0;

  // ---- C: correctness
  const C = {
    build: tscExit === 0 ? D.C_correctness.build : 0,
    publicTests: (publicPassed / publicTotal) * D.C_correctness.publicTests,
    hiddenFunctional: weighted(cat("functional")) * D.C_correctness.hiddenFunctional,
    regression: weighted(cat("regression")) * D.C_correctness.regression,
  };

  // ---- A: adherence
  const reqs = [...new Set(tests.filter((t) => t.req.startsWith("REQ-")).map((t) => t.req))].sort();
  const reqMet = reqs.filter((r) => tests.filter((t) => t.req === r).every((t) => t.passed));
  const violations = [];
  const L3ish = meta.spec === "L3" || meta.spec.startsWith("BAD-");
  if (integrity.task_md_intact === "0") violations.push(["critical", "modified TASK.md"]);
  if (integrity.public_tests_intact === "0") violations.push(["critical", "modified the benchmark's public tests"]);
  const byId = Object.fromEntries(tests.map((t) => [t.id, t]));
  if (L3ish) {
    if (!byId.H09.passed || !byId.H10.passed) violations.push(["critical", "REQ-10 MUST NOT: more than one coupon per order"]);
    if (!byId.B01.passed) violations.push(["critical", "REQ-09 MUST NOT: final amount below 0 / discount above amount"]);
    if (integrity.schema_intact === "0") violations.push(["major", "changed the table schema (forbidden in §8)"]);
    if (integrity.package_json_intact === "0") violations.push(["major", "changed package.json / dependencies (forbidden in §8)"]);
    if (!byId.B05.passed || !byId.S04.passed) violations.push(["minor", "REQ-03 read the system time instead of the injected Clock"]);
  }
  const worst = ["critical", "major", "minor"].find((s) => violations.some((v) => v[0] === s)) ?? "none";
  const A = {
    requirementCoverage: (reqMet.length / reqs.length) * D.A_adherence.requirementCoverage,
    specViolation: CFG.violationScore[worst],
  };

  // ---- R: robustness
  const R = {
    boundary: weighted(cat("boundary")) * D.R_robustness.boundary,
    errorHandling: weighted(cat("error")) * D.R_robustness.errorHandling,
    stateConsistency: weighted(cat("state")) * D.R_robustness.stateConsistency,
  };

  // ---- diff facts
  const numstat = readText(join(ev, "numstat.txt"))
    .split("\n")
    .filter(Boolean)
    .map((l) => {
      const [a, d, f] = l.split("\t");
      return { file: f, added: Number(a) || 0, deleted: Number(d) || 0 };
    });
  const diff = readText(join(ev, "diff.patch"));
  const isProcess = (f) => CFG.processPaths.some((p) => f.startsWith(p));
  const codeFiles = numstat.filter((n) => !isProcess(n.file));
  const unrelated = codeFiles.filter((n) => !CFG.allowedPaths.some((p) => n.file.startsWith(p)));
  const addedLinesOf = (prefix) =>
    diff
      .split(/^diff --git /m)
      .filter((chunk) => chunk.startsWith(`a/${prefix}`) || chunk.includes(` b/${prefix}`))
      .flatMap((chunk) => chunk.split("\n").filter((l) => l.startsWith("+") && !l.startsWith("+++")));
  // untracked files are not in diff.patch: read them from the frozen copy's numstat only (counted as added)
  const sqlOutsideRepo = ["src/routes/", "src/services/", "src/app.ts"].some((p) =>
    addedLinesOf(p).some((l) => /\.prepare\(|\.exec\(|\b(SELECT|UPDATE|INSERT INTO|DELETE FROM)\b/.test(l)),
  );
  const logicInRoutes = addedLinesOf("src/routes/").some((l) => /discount|maxDiscount|minAmount|validUntil/i.test(l) && !/apply|couponCode/.test(l));
  const newRoutes = [...diff.matchAll(/^\+.*router\.add\(\s*"(\w+)",\s*"([^"]+)"/gm)].map((m) => `${m[1]} ${m[2]}`);
  const extraRoutes = newRoutes.filter((r) => !/apply-coupon/.test(r));
  const maxAddedPerFile = Math.max(0, ...codeFiles.filter((n) => n.file.startsWith("src/")).map((n) => n.added));
  const ownTestFiles = (own?.testResults ?? []).filter((f) => !/orders\.test\.ts$/.test(f.name));
  const ownTests = ownTestFiles.flatMap((f) => f.assertionResults ?? []);
  const ownPassed = ownTests.filter((a) => a.status === "passed").length;

  // ---- Q: quality (automated proxies; see report §3.4)
  const Q = {
    typeCheck: tscErrors === 0 && tscExit === 0 ? 3 : tscErrors <= 3 ? 2 : tscErrors <= 10 ? 1 : 0,
    architectureFit: Math.max(0, 3 - (sqlOutsideRepo ? 2 : 0) - (logicInRoutes ? 1 : 0)),
    maintainability: maxAddedPerFile <= 150 ? 2 : maxAddedPerFile <= 300 ? 1.5 : maxAddedPerFile <= 500 ? 1 : 0,
    testQuality: ownTests.length === 0 ? 0 : ownPassed === ownTests.length ? 2 : 1,
  };

  // ---- E: efficiency, scaled by requirement coverage so a fast failure earns nothing
  const cov = reqMet.length / reqs.length;
  const tk = meta.tokens;
  const totalTokens = tk ? (tk.input ?? 0) + (tk.cacheRead ?? 0) + (tk.cacheCreate ?? 0) + (tk.output ?? 0) : null;
  const E = {
    time: D.E_efficiency.time * cov * Math.max(0, 1 - meta.wallSec / D.E_efficiency.refWallSec),
    // unknown usage (hand runs, Codex killed by the timeout) earns no token points
    tokens: totalTokens == null ? 0 : D.E_efficiency.tokens * cov * Math.min(1, D.E_efficiency.refTokens / Math.max(totalTokens, 1)),
  };

  // ---- S: scope
  const changedCodeLines = codeFiles.reduce((s, n) => s + n.added + n.deleted, 0);
  const related = codeFiles
    .filter((n) => /coupon/i.test(n.file) || /coupon/i.test(addedLinesOf(n.file).join("\n")))
    .reduce((s, n) => s + n.added + n.deleted, 0);
  const u = unrelated.length;
  const S = {
    unauthorizedFiles: u === 0 ? 4 : u === 1 ? 3 : u <= 3 ? 2 : u <= 5 ? 1 : 0,
    unrequestedFeatures: Math.max(0, 3 - extraRoutes.length),
    diffLocality: changedCodeLines === 0 ? 0 : (related / changedCodeLines) * D.S_scope.diffLocality,
  };

  const sum = (o) => Object.values(o).reduce((s, x) => s + x, 0);
  const dims = { C: sum(C), A: sum(A), R: sum(R), Q: sum(Q), E: sum(E), S: sum(S) };
  const behaviour = tests.filter((t) => ["functional", "boundary", "state"].includes(t.category));
  const firstPassSuccess =
    tscExit === 0 &&
    publicPassed === publicTotal &&
    cat("regression").every((t) => t.passed) &&
    behaviour.every((t) => t.passed) &&
    worst !== "critical";

  // failure causes (report §4.4)
  const reqAllFailed = (req) => tests.filter((t) => t.req === req).every((t) => !t.passed);
  const failures = tests
    .filter((t) => !t.passed)
    .map((t) => {
      if (tscExit !== 0 && passed.size === 0) return "Syntax / Build";
      if (t.category === "regression") return "Regression";
      if (t.category === "error") return "Error-code mismatch";
      if (t.category === "boundary") return "Boundary Error";
      if (t.category === "state") return "State / Consistency";
      return reqAllFailed(t.req) ? "Missing Requirement" : "Wrong Assumption";
    });
  const failureCauses = failures.reduce((m, c) => ((m[c] = (m[c] ?? 0) + 1), m), {});

  return {
    runId: meta.runId,
    spec: meta.spec,
    executor: meta.executor,
    repeat: meta.repeat,
    total: r1(sum(dims)),
    dimensions: Object.fromEntries(Object.entries(dims).map(([k, v]) => [k, r1(v)])),
    firstPassSuccess,
    strictSuccess: firstPassSuccess && tests.every((t) => t.passed),
    breakdown: {
      C: Object.fromEntries(Object.entries(C).map(([k, v]) => [k, r1(v)])),
      A: Object.fromEntries(Object.entries(A).map(([k, v]) => [k, r1(v)])),
      R: Object.fromEntries(Object.entries(R).map(([k, v]) => [k, r1(v)])),
      Q, E: Object.fromEntries(Object.entries(E).map(([k, v]) => [k, r1(v)])), S: Object.fromEntries(Object.entries(S).map(([k, v]) => [k, r1(v)])),
    },
    detail: {
      tscExit, tscErrors, publicPassed, publicTotal,
      hiddenPassed: tests.filter((t) => t.passed).length,
      hiddenTotal: tests.length,
      hiddenWeightedPassRate: r1(weighted(tests) * 100),
      boundaryPassRate: r1(weighted(cat("boundary")) * 100),
      requirementsMet: reqMet, requirementsTotal: reqs.length,
      violations, worstViolation: worst,
      failedTests: tests.filter((t) => !t.passed).map((t) => t.id),
      failureCauses,
      filesChanged: codeFiles.map((n) => n.file), processFilesChanged: numstat.filter((n) => isProcess(n.file)).length,
      unrelatedFiles: unrelated.map((n) => n.file), extraRoutes, changedCodeLines,
      sqlOutsideRepo, logicInRoutes, maxAddedPerFile, ownTests: ownTests.length, ownTestsPassed: ownPassed,
      totalTokens, tokensSource: meta.tokensSource ?? null, timedOut: !!meta.timedOut, wallSec: r1(meta.wallSec), costCny: meta.costCny,
      turns: meta.turns, toolCalls: meta.toolCalls, askAttempts: meta.askAttempts,
    },
  };
}
