// Applies the reference implementation onto a copy of base/ (node patch.mjs <workspace>).
// Small text edits rather than whole files so the diff against base stays readable.
import { copyFileSync, readFileSync, writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ws = process.argv[2];
const here = dirname(fileURLToPath(import.meta.url));

function edit(rel, from, to) {
  const p = join(ws, rel);
  const s = readFileSync(p, "utf8");
  if (!s.includes(from)) throw new Error(`${rel}: anchor not found: ${from}`);
  writeFileSync(p, s.replace(from, to));
}

copyFileSync(join(here, "src/services/couponService.ts"), join(ws, "src/services/couponService.ts"));

edit(
  "src/repositories/orderRepository.ts",
  "  updateStatus(",
  `  applyDiscount(id: number, couponCode: string, discountCents: number, finalAmountCents: number): void {
    this.db
      .prepare("UPDATE orders SET coupon_code = ?, discount_cents = ?, final_amount_cents = ? WHERE id = ?")
      .run(couponCode, discountCents, finalAmountCents, id);
  }

  updateStatus(`,
);

edit(
  "src/repositories/couponRepository.ts",
  "  findByCode(",
  `  recordRedemption(orderId: number, code: string, discountCents: number, at: string): void {
    this.db
      .prepare("INSERT INTO coupon_redemptions (order_id, coupon_code, discount_cents, created_at) VALUES (?, ?, ?, ?)")
      .run(orderId, code, discountCents, at);
    this.db.prepare("UPDATE coupons SET used_count = used_count + 1 WHERE code = ?").run(code);
  }

  findByCode(`,
);

edit("src/app.ts", "new CouponService(couponRepo)", "new CouponService(couponRepo, orderRepo, clock, db)");

edit(
  "src/routes/coupons.ts",
  `  router.add("GET", "/coupons/:code"`,
  `  router.add("POST", "/orders/:id/apply-coupon", (p, b) => ({ status: 200, body: coupons.apply(parseId(p.id), b.couponCode) }));
  router.add("GET", "/coupons/:code"`,
);
edit("src/routes/coupons.ts", `import type { CouponService }`, `import { parseId } from "./params.ts";\nimport type { CouponService }`);
console.log("reference applied");
