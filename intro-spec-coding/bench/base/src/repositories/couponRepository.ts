import type { Db } from "../db.ts";

export interface Coupon {
  code: string;
  type: "FIXED" | "PERCENT";
  value: number;
  minAmountCents: number;
  maxDiscountCents: number | null;
  validFrom: string;
  validUntil: string;
  status: "ACTIVE" | "DISABLED";
  usageLimit: number | null;
  usedCount: number;
}

interface CouponRow {
  code: string;
  type: Coupon["type"];
  value: number;
  min_amount_cents: number;
  max_discount_cents: number | null;
  valid_from: string;
  valid_until: string;
  status: Coupon["status"];
  usage_limit: number | null;
  used_count: number;
}

const toCoupon = (r: CouponRow): Coupon => ({
  code: r.code,
  type: r.type,
  value: r.value,
  minAmountCents: r.min_amount_cents,
  maxDiscountCents: r.max_discount_cents,
  validFrom: r.valid_from,
  validUntil: r.valid_until,
  status: r.status,
  usageLimit: r.usage_limit,
  usedCount: r.used_count,
});

export class CouponRepository {
  constructor(private readonly db: Db) {}

  insert(c: Omit<Coupon, "usedCount">): Coupon {
    this.db
      .prepare(
        `INSERT INTO coupons (code, type, value, min_amount_cents, max_discount_cents,
                              valid_from, valid_until, status, usage_limit)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      )
      .run(c.code, c.type, c.value, c.minAmountCents, c.maxDiscountCents, c.validFrom, c.validUntil, c.status, c.usageLimit);
    return this.findByCode(c.code)!;
  }

  findByCode(code: string): Coupon | undefined {
    const row = this.db.prepare("SELECT * FROM coupons WHERE code = ?").get(code) as CouponRow | undefined;
    return row && toCoupon(row);
  }
}
