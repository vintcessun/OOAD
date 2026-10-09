#!/usr/bin/env node
// Spec Quality Score (0-100): a deliberately simple, rule-based completeness check.
// It measures whether a spec *contains* the parts a coding agent needs; it cannot
// detect contradictions (BAD-conflict scores like L3 — see report §5).
//   node runner/spec-lint.mjs specs/apply-coupon/*.md
import { readFileSync } from "node:fs";
import { basename } from "node:path";

const clamp = (x, max) => Math.max(0, Math.min(max, x));
const count = (s, re) => (s.match(re) ?? []).length;

export const RULES = [
  ["Requirement Completeness", 20, (s) => clamp(count(s, /\bREQ-\d+/g) * 1.5 + count(s, /\bMUST\b|必须/g) * 1, 20)],
  ["Acceptance Criteria", 15, (s) => clamp(count(s, /\bAC-\d+|Given\b/g) * 1.5 + (/验收/.test(s) ? 3 : 0), 15)],
  [
    "Edge Cases",
    15,
    (s) => clamp(count(s, /<=|>=|==|等于时|边界|向下取整|floor\(|min\(|max\(|不小于|不得小于|小于 0/g) * 1.5, 15),
  ],
  [
    "Interface Contract",
    15,
    (s) =>
      (/\b(GET|POST|PUT|PATCH|DELETE) \/\S+/.test(s) ? 5 : 0) +
      (/```json[\s\S]*?\{[\s\S]*?\}[\s\S]*?```/.test(s) ? 4 : 0) +
      (/HTTP\s*\d{3}|\b(200|201|400|404|409)\b/.test(s) ? 3 : 0) +
      (/响应|response/i.test(s) ? 3 : 0),
  ],
  [
    "Data Constraints",
    10,
    (s) => clamp((/数据模型|data model/i.test(s) ? 4 : 0) + (/分」|整数/.test(s) ? 3 : 0) + (/NULL/.test(s) ? 3 : 0), 10),
  ],
  ["Error Handling", 15, (s) => clamp(new Set(s.match(/\b[A-Z]+(?:_[A-Z]+)+\b/g) ?? []).size * 1.5, 15)],
  [
    "Non-functional Constraints",
    10,
    (s) => clamp((/非功能|non-functional/i.test(s) ? 4 : 0) + count(s, /不修改|不改|不加|MUST NOT|事务|回滚|分层/g) * 1.5, 10),
  ],
];

/** Vague words a reader cannot verify. Each occurrence costs 5 points (max 20). */
export const VAGUE = /适当|合适|合理|大额|尽量|尽可能|等等|一般来说|不应过大/g;

export function lint(text) {
  const parts = RULES.map(([name, max, f]) => ({ name, max, score: Math.round(clamp(f(text), max) * 10) / 10 }));
  const vague = text.match(VAGUE) ?? [];
  const raw = parts.reduce((s, p) => s + p.score, 0);
  return { parts, vague, penalty: Math.min(20, vague.length * 5), score: Math.round(clamp(raw - Math.min(20, vague.length * 5), 100)) };
}

if (process.argv[1] && process.argv[1].endsWith("spec-lint.mjs")) {
  for (const f of process.argv.slice(2)) {
    const r = lint(readFileSync(f, "utf8"));
    console.log(`${basename(f, ".md").padEnd(14)} ${String(r.score).padStart(3)}  ${r.parts.map((p) => `${p.name.split(" ")[0]}=${p.score}`).join(" ")}  vague=[${r.vague.join(",")}]`);
  }
}
