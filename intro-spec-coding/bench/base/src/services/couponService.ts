import { badRequest, conflict, notFound } from "../errors.ts";
import type { Coupon, CouponRepository } from "../repositories/couponRepository.ts";

/** Coupon administration (create / look up). Applying coupons to orders is not implemented yet. */
export class CouponService {
  constructor(private readonly coupons: CouponRepository) {}

  create(input: Record<string, unknown>): Coupon {
    const code = input.code;
    if (typeof code !== "string" || !/^[A-Z0-9_-]{3,32}$/.test(code)) {
      throw badRequest("INVALID_REQUEST", "code must match [A-Z0-9_-]{3,32}");
    }
    if (input.type !== "FIXED" && input.type !== "PERCENT") {
      throw badRequest("INVALID_REQUEST", "type must be FIXED or PERCENT");
    }
    const value = input.value;
    if (!Number.isInteger(value) || (value as number) < 0 || (input.type === "PERCENT" && (value as number) > 100)) {
      throw badRequest("INVALID_REQUEST", "value out of range");
    }
    if (this.coupons.findByCode(code)) throw conflict("COUPON_EXISTS", `coupon ${code} exists`);
    const validFrom = String(input.validFrom ?? "");
    const validUntil = String(input.validUntil ?? "");
    if (Number.isNaN(Date.parse(validFrom)) || Number.isNaN(Date.parse(validUntil))) {
      throw badRequest("INVALID_REQUEST", "validFrom / validUntil must be ISO-8601");
    }
    return this.coupons.insert({
      code,
      type: input.type,
      value: value as number,
      minAmountCents: (input.minAmountCents as number | undefined) ?? 0,
      maxDiscountCents: (input.maxDiscountCents as number | null | undefined) ?? null,
      validFrom,
      validUntil,
      status: input.status === "DISABLED" ? "DISABLED" : "ACTIVE",
      usageLimit: (input.usageLimit as number | null | undefined) ?? null,
    });
  }

  get(code: string): Coupon {
    const coupon = this.coupons.findByCode(code);
    if (!coupon) throw notFound("COUPON_NOT_FOUND", `coupon ${code} not found`);
    return coupon;
  }
}
