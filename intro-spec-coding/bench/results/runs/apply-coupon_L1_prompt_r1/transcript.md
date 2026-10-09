=== FILE: src/repositories/redemptionRepository.ts ===
import type { Db } from "../db.ts";

/**
 * Records a coupon redemption together with the resulting order amounts.
 * Runs as a single transaction so the order, the coupon usage counter and the
 * redemption log can never drift apart.
 */
export class RedemptionRepository {
  constructor(private readonly db: Db) {}

  redeem(
    orderId: number,
    couponCode: string,
    discountCents: number,
    finalAmountCents: number,
    createdAt: string,
  ): void {
    this.db.exec("BEGIN");
    try {
      this.db
        .prepare("UPDATE orders SET coupon_code = ?, discount_cents = ?, final_amount_cents = ? WHERE id = ?")
        .run(couponCode, discountCents, finalAmountCents, orderId);
      this.db.prepare("UPDATE coupons SET used_count = used_count + 1 WHERE code = ?").run(couponCode);
      this.db
        .prepare(
          `INSERT INTO coupon_redemptions (order_id, coupon_code, discount_cents, created_at)
           VALUES (?, ?, ?, ?)`,
        )
        .run(orderId, couponCode, discountCents, createdAt);
      this.db.exec("COMMIT");
    } catch (e) {
      this.db.exec("ROLLBACK");
      throw e;
    }
  }
}


=== FILE: src/services/orderService.ts ===
import type { Clock } from "../clock.ts";
import { badRequest, conflict, notFound } from "../errors.ts";
import type { Coupon, CouponRepository } from "../repositories/couponRepository.ts";
import type { Order, OrderRepository } from "../repositories/orderRepository.ts";
import type { RedemptionRepository } from "../repositories/redemptionRepository.ts";

/**
 * Discount in integer cents for `coupon` applied to `amountCents`.
 * FIXED coupons discount their face value; PERCENT coupons discount
 * `value`% of the order amount, rounded down to whole cents. The result is
 * capped by `maxDiscountCents` (when set) and never exceeds the order amount.
 */
export function computeDiscount(coupon: Coupon, amountCents: number): number {
  const raw = coupon.type === "FIXED" ? coupon.value : Math.floor((amountCents * coupon.value) / 100);
  const capped = coupon.maxDiscountCents === null ? raw : Math.min(raw, coupon.maxDiscountCents);
  return Math.max(0, Math.min(capped, amountCents));
}

export class OrderService {
  constructor(
    private readonly orders: OrderRepository,
    private readonly coupons: CouponRepository,
    private readonly redemptions: RedemptionRepository,
    private readonly clock: Clock,
  ) {}

  create(customer: unknown, amountCents: unknown): Order {
    if (typeof customer !== "string" || customer.trim() === "") {
      throw badRequest("INVALID_REQUEST", "customer is required");
    }
    if (!Number.isInteger(amountCents) || (amountCents as number) <= 0) {
      throw badRequest("INVALID_REQUEST", "amountCents must be a positive integer");
    }
    return this.orders.insert(customer.trim(), amountCents as number, this.clock().toISOString());
  }

  get(id: number): Order {
    const order = this.orders.findById(id);
    if (!order) throw notFound("ORDER_NOT_FOUND", `order ${id} not found`);
    return order;
  }

  list(): Order[] {
    return this.orders.list();
  }

  /** Applies a coupon to a pending order, updating discount and final amount. */
  applyCoupon(id: number, couponCode: unknown): Order {
    if (typeof couponCode !== "string" || couponCode.trim() === "") {
      throw badRequest("INVALID_REQUEST", "couponCode is required");
    }
    const code = couponCode.trim();

    const order = this.get(id);
    if (order.status !== "PENDING") throw conflict("ORDER_NOT_PENDING", `order ${id} is ${order.status}`);
    if (order.couponCode !== null) {
      throw conflict("COUPON_ALREADY_APPLIED", `order ${id} already has coupon ${order.couponCode}`);
    }

    const coupon = this.coupons.findByCode(code);
    if (!coupon) throw notFound("COUPON_NOT_FOUND", `coupon ${code} not found`);
    if (coupon.status !== "ACTIVE") throw conflict("COUPON_DISABLED", `coupon ${code} is disabled`);

    const now = this.clock().getTime();
    if (now < Date.parse(coupon.validFrom)) throw conflict("COUPON_NOT_YET_VALID", `coupon ${code} is not yet valid`);
    if (now > Date.parse(coupon.validUntil)) throw conflict("COUPON_EXPIRED", `coupon ${code} has expired`);
    if (coupon.usageLimit !== null && coupon.usedCount >= coupon.usageLimit) {
      throw conflict("COUPON_USAGE_LIMIT_REACHED", `coupon ${code} has reached its usage limit`);
    }
    if (order.amountCents < coupon.minAmountCents) {
      throw badRequest(
        "COUPON_MIN_AMOUNT_NOT_MET",
        `coupon ${code} requires an order of at least ${coupon.minAmountCents} cents`,
      );
    }

    const discountCents = computeDiscount(coupon, order.amountCents);
    const finalAmountCents = order.amountCents - discountCents;
    this.redemptions.redeem(order.id, coupon.code, discountCents, finalAmountCents, this.clock().toISOString());
    return this.get(id);
  }

  pay(id: number): Order {
    const order = this.get(id);
    if (order.status !== "PENDING") throw conflict("ORDER_NOT_PENDING", `order ${id} is ${order.status}`);
    this.orders.updateStatus(id, "PAID");
    return this.get(id);
  }

  cancel(id: number): Order {
    const order = this.get(id);
    if (order.status !== "PENDING") throw conflict("ORDER_NOT_PENDING", `order ${id} is ${order.status}`);
    this.orders.updateStatus(id, "CANCELLED");
    return this.get(id);
  }
}


=== FILE: src/routes/orders.ts ===
import type { Router } from "../router.ts";
import type { OrderService } from "../services/orderService.ts";
import { parseId } from "./params.ts";

export function registerOrderRoutes(router: Router, orders: OrderService): void {
  router.add("POST", "/orders", (_, b) => ({ status: 201, body: orders.create(b.customer, b.amountCents) }));
  router.add("GET", "/orders", () => ({ status: 200, body: orders.list() }));
  router.add("GET", "/orders/:id", (p) => ({ status: 200, body: orders.get(parseId(p.id)) }));
  router.add("POST", "/orders/:id/pay", (p) => ({ status: 200, body: orders.pay(parseId(p.id)) }));
  router.add("POST", "/orders/:id/cancel", (p) => ({ status: 200, body: orders.cancel(parseId(p.id)) }));
  router.add("POST", "/orders/:id/apply-coupon", (p, b) => ({
    status: 200,
    body: orders.applyCoupon(parseId(p.id), b.couponCode),
  }));
}


=== FILE: src/app.ts ===
import { systemClock, type Clock } from "./clock.ts";
import { openDb, type Db } from "./db.ts";
import { CouponRepository } from "./repositories/couponRepository.ts";
import { OrderRepository } from "./repositories/orderRepository.ts";
import { RedemptionRepository } from "./repositories/redemptionRepository.ts";
import { Router, type HttpResponse } from "./router.ts";
import { registerCouponRoutes } from "./routes/coupons.ts";
import { registerOrderRoutes } from "./routes/orders.ts";
import { CouponService } from "./services/couponService.ts";
import { OrderService } from "./services/orderService.ts";

export interface App {
  db: Db;
  request(method: string, path: string, body?: unknown): HttpResponse;
}

export interface AppOptions {
  db?: Db;
  clock?: Clock;
}

/** Wires repositories, services and routes. Tests call app.request(...) in-process. */
export function createApp(options: AppOptions = {}): App {
  const db = options.db ?? openDb();
  const clock = options.clock ?? systemClock;
  const router = new Router();

  const orderRepo = new OrderRepository(db);
  const couponRepo = new CouponRepository(db);
  const redemptionRepo = new RedemptionRepository(db);
  registerOrderRoutes(router, new OrderService(orderRepo, couponRepo, redemptionRepo, clock));
  registerCouponRoutes(router, new CouponService(couponRepo));

  return { db, request: (method, path, body) => router.handle(method, path, body) };
}
