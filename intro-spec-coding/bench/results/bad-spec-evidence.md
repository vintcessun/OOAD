# 坏规格实验：判读证据（自动摘取，供人工判读）

## apply-coupon_BAD-ambiguous_claude-os_r1

- askAttempts: 0

**最终输出中的相关句子**

**OpenSpec design.md 的 Risks / Open Questions 段**

> ## Risks / Trade-offs
> 
> - [Percent rounding could differ from a reviewer's expectation at half cents] → The rule is
>   stated explicitly in the spec and applied uniformly; test cases use exact values.
> - [Transaction control inside a repository couples it to SQLite] → Acceptable: the
>   repository already depends on `node:sqlite`, and the alternative leaks SQL upward.
> - [Concurrent applies on the same order could race] → Out of scope; the service is
>   in-process and synchronous, and a second apply on the same order is rejected by the
>   existing-coupon check.

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-ambiguous_claude_r1

- askAttempts: 0

**最终输出中的相关句子**

> REQ-07's "cap for large orders" has no standalone numeric value in the spec; the only implementable cap mechanism is `maxDiscountCents` (REQ-08, applies to both types).

> If a hidden test expects a built-in default cap for PERCENT coupons when `maxDiscountCents` is NULL, that value isn't specified anywhere in TASK.md — flag it and I'll add one.

**OpenSpec design.md 的 Risks / Open Questions 段**

（无）

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-ambiguous_codex-os_r1

- askAttempts: 0

**最终输出中的相关句子**

> - **design.md** — layering decision (routes parse, service rules, repository SQL), the transaction approach, rounding, and the cap interpretation.

> Two judgment calls I made and recorded in `design.md`:

> - **Percent cap (REQ-07/AC-03).** The task doesn't give a numeric cap, so I implemented the discount cap via `coupon.maxDiscountCents` exactly as REQ-08 states (applies to both FIXED and PERCENT), plus the existing `min(discount, amountCents)` floor.

**OpenSpec design.md 的 Risks / Open Questions 段**

> ## Risks / Trade-offs
> 
> - [Rounding direction is unspecified for non-divisible percents] → Use round-half-up; it is
>   deterministic and consistent, and the acceptance examples are exact divisions.
> - [Classifying REQ-07's "cap" as `maxDiscountCents` only] → Matches REQ-08 literally; documented
>   here so the assumption is visible.
> - [Synchronous single connection] → The transaction is short and synchronous, so concurrent apply
>   requests serialize without partial writes.
> # Proposal

**OpenSpec 产物中的相关句子**

> (an arbitrary hardcoded limit would make the behavior untestable and is not stated in the spec).

> - [Rounding direction is unspecified for non-divisible percents] → Use round-half-up; it is

> here so the assumption is visible.

## apply-coupon_BAD-ambiguous_codex_r1

- askAttempts: 0

**最终输出中的相关句子**

> ## Two judgment calls worth flagging

> - "大额订单应给予适当的优惠上限" (AC-03) has no numeric cap in the spec, so I implemented it as the schema's `maxDiscountCents` cap applied to PERCENT coupons.

**OpenSpec design.md 的 Risks / Open Questions 段**

（无）

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-ambiguous_opencode-os_r1

- askAttempts: 0

**最终输出中的相关句子**

**OpenSpec design.md 的 Risks / Open Questions 段**

> ## Risks / Trade-offs
> 
> - [Rounding choice differs from a grader's expectation for awkward percentages] -> `Math.round` is the conventional reading of "round"; exact-percentage acceptance cases are unaffected.
> - [Transaction spans tables owned by different repositories] -> kept in a single method on `OrderRepository` with explicit `BEGIN`/`COMMIT`/`ROLLBACK` so a partial write cannot occur.
> - [Clock may be called more than once per request] -> call it once and reuse the value for both the validity checks and `created_at`.

> ## Open Questions
> 
> None.
> # Proposal

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-ambiguous_opencode_r1

- askAttempts: 0

**最终输出中的相关句子**

**OpenSpec design.md 的 Risks / Open Questions 段**

（无）

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-conflict_claude-os_r1

- askAttempts: 0

**最终输出中的相关句子**

> - **proposal.md** — why/what, the new `order-coupons` capability, impact, and the conflict record.

> - **design.md** — layering, transactional write, validation order, discount computation, and the conflict decision record.

> ### ⚠️ Two contradictions in TASK.md (documented, not altered)

> TASK.md's normative rules conflict with its acceptance examples; I resolved both in favour of the `MUST NOT` clauses (standard precedence: normative requirements govern over illustrative examples) and recorded this in proposal.md/design.md:

**OpenSpec design.md 的 Risks / Open Questions 段**

> ## Risks / Trade-offs
> 
> - [AC-08 cannot pass under REQ-10] → Documented in proposal and Decision 3; the normative
>   error table was chosen. Reviewer can flip one check if AC-08 is authoritative.
> - [Transaction left open on an unexpected error] → `applyCoupon` rolls back in a `catch`
>   and rethrows, so a failure never leaves a half-applied order.
> - [Clock/window comparison with string timestamps] → Compare via `Date.parse` on both sides
>   so ISO-8601 strings with differing precision still order correctly.
> - [Coupon version drift] → Validation reads the coupon and the write increments the counter
>   in the same synchronous call path; single-threaded SQLite keeps this safe.

**OpenSpec 产物中的相关句子**

> Follow normative rules over conflicting acceptance examples

> Two conflicts exist (details in proposal.md):

> - [Transaction left open on an unexpected error] → `applyCoupon` rolls back in a `catch`

> ### Requirement conflicts in TASK.md (normative rules vs acceptance examples)

> TASK.md contains two direct contradictions between the normative MUST clauses (§4/§6) and

> check order, whereas AC-03/AC-08 are examples that contradict them — under standard

> Cover the AC-03 scenario (`amountCents = 100000`, PERCENT 20, no `maxDiscountCents`) but assert the REQ-08-capped `discountCents = 10000`; the AC-03 example value of 20000 is the documented conflict and is intentionally not asserted.

## apply-coupon_BAD-conflict_claude_r1

- askAttempts: 0

**最终输出中的相关句子**

> ## Two spec contradictions I had to resolve

> The spec contradicts itself in two places.

**OpenSpec design.md 的 Risks / Open Questions 段**

（无）

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-conflict_codex-os_r1

- askAttempts: 0

**最终输出中的相关句子**

> - [design.md](/work/openspec/changes/apply-coupon-to-order/design.md) — layering decisions, transaction placement, and the two conflict resolutions

> ## Two contradictions I had to resolve

> TASK.md contradicts itself in two places.

> - **REQ-10 vs AC-08** — "reject any second coupon" contradicts "a new coupon replaces the old one".

**OpenSpec design.md 的 Risks / Open Questions 段**

> ## Risks / Trade-offs
> 
> - [Repeatedly swapping coupons inflates `used_count` for the abandoned coupons] → Fine
>   while no reversal rules exist; documented in the resolution above and easy to revisit
>   if a "remove coupon" endpoint is added.
> - [A blanket discount cap is a plausible fraud control, and TASK.md asks for one] → The
>   cap is deliberately not applied, per AC-03; the single-line change to restore it is
>   named above, and the ambiguity is reported to the user rather than silently dropped.
> - [`BEGIN IMMEDIATE` inside a request handler could nest if a future caller already has a
>   transaction open] → `node:sqlite` is synchronous and no other code starts a
>   transaction today; the redemption path is the only writer besides order creation, so
>   nesting is not reachable yet.
> - [Integer division for `PERCENT` could drift] → Use `Math.floor(amountCents * value /
>   100)` with integer inputs, matching AC-05 (999 at 15% → 149).

**OpenSpec 产物中的相关句子**

> Conflict resolution between TASK.md rules and acceptance criteria.** TASK.md

> contains two places where a rule contradicts an acceptance criterion.

> named above, and the ambiguity is reported to the user rather than silently dropped.

## apply-coupon_BAD-conflict_codex_r1

- askAttempts: 0

**最终输出中的相关句子**

> Two places where TASK.md contradicts itself, and how I resolved them

**OpenSpec design.md 的 Risks / Open Questions 段**

（无）

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-conflict_opencode-os_r1

- askAttempts: 0

**最终输出中的相关句子**

> One important note: TASK.md is internally inconsistent in two places, and I followed the normative business rules/error table over those acceptance criteria:

**OpenSpec design.md 的 Risks / Open Questions 段**

> ## Risks / Trade-offs
> 
> - [SQLite transaction nesting] → The repository method is the only transaction owner; no nested services open transactions.
> - [String time comparison] → Only valid for ISO-8601 UTC strings, which is the format produced by `Date.toISOString()` and enforced by coupon creation.
> - [AC-08 ambiguity] → Documented above; behavior follows the normative requirement and error table.
> # Proposal

**OpenSpec 产物中的相关句子**

> Each maps to a `badRequest`/`notFound`/`conflict` `AppError`, so the router produces `{ error: { code, message } }` unchanged.

> ### AC-08 conflict resolution

> The TASK is internally inconsistent: REQ-10 and error-table row 4 require rejecting an order that already has a coupon (`COUPON_ALREADY_APPLIED`, 409), while AC-08 describes replacing the old coupon.

> - [AC-08 ambiguity] → Documented above; behavior follows the normative requirement and error table.

## apply-coupon_BAD-conflict_opencode_r1

- askAttempts: 0

**最终输出中的相关句子**

> Two spots in TASK.md conflict; I resolved them by following the explicit acceptance criteria:

**OpenSpec design.md 的 Risks / Open Questions 段**

（无）

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-missing_claude-os_r1

- askAttempts: 0

**最终输出中的相关句子**

**OpenSpec design.md 的 Risks / Open Questions 段**

> ## Risks / Trade-offs
> 
> - **Usage-limit race** — check-then-write can overshoot `usage_limit` under concurrent requests. SQLite serializes writers and this is an in-process single-writer service, so the window is negligible; a conditional `UPDATE ... WHERE used_count < usage_limit` inside the transaction is the future hardening path if concurrency grows.
> - **Transaction control in a repository** — manual `BEGIN`/`ROLLBACK` is more error-prone than a driver helper; a single method with a `try/finally` keeps the rollback path in one place.
> - **Error precedence is implicit in code order** — reordering checks could silently change responses; the new tests assert the cross-rule precedence cases from section 6.
> # Proposal

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-missing_claude_r1

- askAttempts: 0

**最终输出中的相关句子**

> One thing worth flagging: the spec defines no clamping of `discount` to `amountCents`, so a FIXED coupon whose value exceeds the order amount produces a negative `finalAmountCents` (the schema permits it).

**OpenSpec design.md 的 Risks / Open Questions 段**

（无）

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-missing_codex-os_r1

- askAttempts: 0

**最终输出中的相关句子**

> One deliberate reading worth flagging: the spec defines no floor at zero and the schema does not constrain `final_amount_cents`, so a `FIXED` coupon larger than the order total yields a negative final amount rather than being clamped.

**OpenSpec design.md 的 Risks / Open Questions 段**

> ## Risks / Trade-offs
> 
> - [Validation happens outside the transaction, so a concurrent request could
>   pass the usage-limit check twice] → acceptable for this single-process,
>   single-connection, synchronous app; `BEGIN IMMEDIATE` still serializes the
>   writes. A strictly correct fix needs a conditional
>   `UPDATE ... WHERE used_count < usage_limit`, which the spec's error mapping
>   (check before compute) does not require.
> - [`ROLLBACK` on a failed write could itself throw if no transaction is open]
> → the wrapper only issues `ROLLBACK` after a successful `BEGIN`, and the
>   original error is rethrown.
> - [The `discount` may exceed `amountCents` for a large `FIXED` coupon] → see
>   Decision 7; this mirrors the spec, kept deliberately visible rather than
>   silently adjusted.
> - [Adding constructor parameters to `OrderService` touches app wiring] → single
>   construction site, covered by existing tests that build the app via
>   `createApp`.

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-missing_codex_r1

- askAttempts: 0

**最终输出中的相关句子**

> One design note worth flagging: the three-statement transaction touches the `coupons` table from `OrderRepository` rather than splitting it across two repositories, since a single atomic unit was cleaner than coordinating a cross-repository transaction.

**OpenSpec design.md 的 Risks / Open Questions 段**

（无）

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-missing_opencode-os_r1

- askAttempts: 0

**最终输出中的相关句子**

**OpenSpec design.md 的 Risks / Open Questions 段**

> ## Risks / Trade-offs
> 
> - [Usage-limit race] Two concurrent redemptions could both pass the pre-check. Mitigation: the in-process app is single-threaded and tests are sequential; the check is not made atomic, matching the existing simple model. Can be hardened later with a conditional `UPDATE ... WHERE used_count < usage_limit`.
> - [Validation order is load-bearing] Returning a different code/status than specified fails acceptance tests. Mitigation: encode the order explicitly in the service and cover each transition with tests.
> - [Cross-domain repository] `CouponRedemptionRepository.redeem` writes to `orders` as well as `coupon_redemptions`/`coupons`. Mitigation: it is the single owner of one atomic redemption; the alternative would duplicate transaction logic.

> ## Open Questions
> 
> None.
> # Proposal

**OpenSpec 产物中的相关句子**

（无）

## apply-coupon_BAD-missing_opencode_r1

- askAttempts: 0

**最终输出中的相关句子**

**OpenSpec design.md 的 Risks / Open Questions 段**

（无）

**OpenSpec 产物中的相关句子**

（无）
