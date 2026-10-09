import type { Clock } from "../clock.ts";
import type { Db } from "../db.ts";
import { badRequest, conflict, notFound } from "../errors.ts";
import type { Coupon, CouponRepository } from "../repositories/couponRepository.ts";
import type { Order, OrderRepository } from "../repositories/orderRepository.ts";

/** Reference implementation of L3. Used only to prove the hidden tests are satisfiable. */
export class CouponService {
  constructor(
    private readonly coupons: CouponRepository,
    private readonly orders?: OrderRepository,
    private readonly clock?: Clock,
    private readonly db?: Db,
  ) {}

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

  apply(orderId: number, rawCode: unknown): Order {
    if (typeof rawCode !== "string" || rawCode.trim() === "") {
      throw badRequest("INVALID_REQUEST", "couponCode is required");
    }
    const code = rawCode.trim().toUpperCase();
    const order = this.orders!.findById(orderId);
    if (!order) throw notFound("ORDER_NOT_FOUND", `order ${orderId} not found`);
    if (order.status !== "PENDING") throw conflict("ORDER_NOT_PENDING", `order ${orderId} is ${order.status}`);
    if (order.couponCode) throw conflict("COUPON_ALREADY_APPLIED", `order ${orderId} already uses ${order.couponCode}`);
    const coupon = this.get(code);
    if (coupon.status !== "ACTIVE") throw badRequest("COUPON_INACTIVE", `coupon ${code} is ${coupon.status}`);
    const now = this.clock!();
    if (now.getTime() < Date.parse(coupon.validFrom)) throw badRequest("COUPON_NOT_YET_VALID", `coupon ${code} not yet valid`);
    if (now.getTime() >= Date.parse(coupon.validUntil)) throw badRequest("COUPON_EXPIRED", `coupon ${code} expired`);
    if (coupon.usageLimit !== null && coupon.usedCount >= coupon.usageLimit) {
      throw badRequest("COUPON_USAGE_EXHAUSTED", `coupon ${code} used up`);
    }
    if (order.amountCents < coupon.minAmountCents) throw badRequest("MINIMUM_NOT_MET", `order below ${coupon.minAmountCents}`);

    let discount = coupon.type === "FIXED" ? coupon.value : Math.floor((order.amountCents * coupon.value) / 100);
    if (coupon.maxDiscountCents !== null) discount = Math.min(discount, coupon.maxDiscountCents);
    discount = Math.min(discount, order.amountCents);

    this.db!.exec("BEGIN");
    try {
      this.orders!.applyDiscount(orderId, code, discount, order.amountCents - discount);
      this.coupons.recordRedemption(orderId, code, discount, now.toISOString());
      this.db!.exec("COMMIT");
    } catch (e) {
      this.db!.exec("ROLLBACK");
      throw e;
    }
    return this.orders!.findById(orderId)!;
  }
}
