I'll implement the coupon application feature following the existing layering.

Analysis of the changes needed:
- `OrderRepository`: add an atomic transaction method (all SQL in repositories).
- `OrderService`: add `applyCoupon` with the validation rules in the required order.
- `routes/orders.ts`: add the route.
- `app.ts`: wire the `CouponRepository` into `OrderService`.
- New tests for the feature.

=== FILE: src/repositories/orderRepository.ts ===
import type { Db } from "../db.ts";

export interface Order {
  id: number;
  customer: string;
  amountCents: number;
  discountCents: number;
  finalAmountCents: number;
  couponCode: string | null;
  status: "PENDING" | "PAID" | "CANCELLED";
  createdAt: string;
}

interface OrderRow {
  id: number;
  customer: string;
  amount_cents: number;
  discount_cents: number;
  final_amount_cents: number;
  coupon_code: string | null;
  status: Order["status"];
  created_at: string;
}

const toOrder = (r: OrderRow): Order => ({
  id: r.id,
  customer: r.customer,
  amountCents: r.amount_cents,
  discountCents: r.discount_cents,
  finalAmountCents: r.final_amount_cents,
  couponCode: r.coupon_code,
  status: r.status,
  createdAt: r.created_at,
});

export class OrderRepository {
  constructor(private readonly db: Db) {}

  insert(customer: string, amountCents: number, createdAt: string): Order {
    const res = this.db
      .prepare(
        "INSERT INTO orders (customer, amount_cents, final_amount_cents, created_at) VALUES (?, ?, ?, ?)",
      )
      .run(customer, amountCents, amountCents, createdAt);
    return this.findById(Number(res.lastInsertRowid))!;
  }

  findById(id: number): Order | undefined {
    const row = this.db.prepare("SELECT * FROM orders WHERE id = ?").get(id) as OrderRow | undefined;
    return row && toOrder(row);
  }

  list(): Order[] {
    return (this.db.prepare("SELECT * FROM orders ORDER BY id").all() as unknown as OrderRow[]).map(toOrder);
  }

  updateStatus(id: number, status: Order["status"]): void {
    this.db.prepare("UPDATE orders SET status = ? WHERE id = ?").run(status, id);
  }

  /**
   * Applies a coupon to an order as one unit of work (REQ-12): the order totals,
   * the redemption record and the coupon usage counter are updated in a single
   * transaction, so a failure at any step rolls every change back.
   */
  applyCoupon(orderId: number, couponCode: string, discountCents: number, createdAt: string): Order {
    this.db.exec("BEGIN");
    try {
      this.db
        .prepare(
          "UPDATE orders SET discount_cents = ?, final_amount_cents = amount_cents - ?, coupon_code = ? WHERE id = ?",
        )
        .run(discountCents, discountCents, couponCode, orderId);
      this.db
        .prepare(
          "INSERT INTO coupon_redemptions (order_id, coupon_code, discount_cents, created_at) VALUES (?, ?, ?, ?)",
        )
        .run(orderId, couponCode, discountCents, createdAt);
      this.db.prepare("UPDATE coupons SET used_count = used_count + 1 WHERE code = ?").run(couponCode);
      this.db.exec("COMMIT");
    } catch (error) {
      this.db.exec("ROLLBACK");
      throw error;
    }
    return this.findById(orderId)!;
  }
}

=== FILE: src/services/orderService.ts ===
import type { Clock } from "../clock.ts";
import { badRequest, conflict, notFound } from "../errors.ts";
import type { CouponRepository } from "../repositories/couponRepository.ts";
import type { Order, OrderRepository } from "../repositories/orderRepository.ts";

export class OrderService {
  constructor(
    private readonly orders: OrderRepository,
    private readonly clock: Clock,
    private readonly coupons: CouponRepository,
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

  /** Applies a coupon to a pending order. Checks run in the order fixed by TASK.md §6. */
  applyCoupon(id: number, couponCode: unknown): Order {
    // 1. REQ-13: request body must carry a non-empty string code.
    if (typeof couponCode !== "string" || couponCode.trim() === "") {
      throw badRequest("INVALID_REQUEST", "couponCode is required");
    }
    // REQ-01: trim, then upper-case, then look up by coupons.code.
    const code = couponCode.trim().toUpperCase();

    // 2. Order must exist.
    const order = this.orders.findById(id);
    if (!order) throw notFound("ORDER_NOT_FOUND", `order ${id} not found`);
    // 3. REQ-04: order must still be PENDING.
    if (order.status !== "PENDING") throw conflict("ORDER_NOT_PENDING", `order ${id} is ${order.status}`);
    // 4. REQ-10: one coupon per order.
    if (order.couponCode !== null) {
      throw conflict("COUPON_ALREADY_APPLIED", `order ${id} already used coupon ${order.couponCode}`);
    }

    // 5. Coupon must exist.
    const coupon = this.coupons.findByCode(code);
    if (!coupon) throw notFound("COUPON_NOT_FOUND", `coupon ${code} not found`);
    // 6. REQ-02: coupon must be ACTIVE.
    if (coupon.status !== "ACTIVE") throw badRequest("COUPON_INACTIVE", `coupon ${code} is not active`);

    // 7/8. REQ-03: validFrom <= now < validUntil, using the injected clock.
    const now = this.clock();
    if (now.getTime() < Date.parse(coupon.validFrom)) {
      throw badRequest("COUPON_NOT_YET_VALID", `coupon ${code} is not valid yet`);
    }
    if (now.getTime() >= Date.parse(coupon.validUntil)) {
      throw badRequest("COUPON_EXPIRED", `coupon ${code} has expired`);
    }

    // 9. REQ-11: usage limit.
    if (coupon.usageLimit !== null && coupon.usedCount >= coupon.usageLimit) {
      throw badRequest("COUPON_USAGE_EXHAUSTED", `coupon ${code} has no uses left`);
    }

    // 10. REQ-05: minimum amount (equality allowed).
    if (order.amountCents < coupon.minAmountCents) {
      throw badRequest("MINIMUM_NOT_MET", `order amount is below coupon minimum`);
    }

    // REQ-06/REQ-07: base discount.
    let discount =
      coupon.type === "FIXED"
        ? coupon.value
        : Math.floor((order.amountCents * coupon.value) / 100);
    // REQ-08: optional cap.
    if (coupon.maxDiscountCents !== null) discount = Math.min(discount, coupon.maxDiscountCents);
    // REQ-09: never discount more than the order amount.
    discount = Math.min(discount, order.amountCents);

    return this.orders.applyCoupon(id, code, discount, now.toISOString());
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
  registerOrderRoutes(router, new OrderService(orderRepo, clock, couponRepo));
  registerCouponRoutes(router, new CouponService(couponRepo));

  return { db, request: (method, path, body) => router.handle(method, path, body) };
}

=== FILE: tests/applyCoupon.test.ts ===
import { describe, expect, it } from "vitest";
import { createApp, type App } from "../src/app.ts";

const NOW_ISO = "2026-10-01T08:00:00Z";
const DEFAULT_VALID_FROM = "2026-01-01T00:00:00Z";
const DEFAULT_VALID_UNTIL = "2027-01-01T00:00:00Z";

function newApp(nowIso: string = NOW_ISO): App {
  return createApp({ clock: () => new Date(nowIso) });
}

function createOrder(app: App, amountCents: number): number {
  const res = app.request("POST", "/orders", { customer: "alice", amountCents });
  expect(res.status).toBe(201);
  return (res.body as { id: number }).id;
}

function createCoupon(app: App, overrides: Record<string, unknown> = {}): void {
  const res = app.request("POST", "/coupons", {
    code: "SAVE20",
    type: "PERCENT",
    value: 20,
    validFrom: DEFAULT_VALID_FROM,
    validUntil: DEFAULT_VALID_UNTIL,
    ...overrides,
  });
  expect(res.status).toBe(201);
}

function apply(app: App, orderId: number, couponCode: unknown) {
  return app.request("POST", `/orders/${orderId}/apply-coupon`, { couponCode });
}

describe("apply-coupon", () => {
  it("AC-01 applies a percent coupon", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    createCoupon(app, { code: "SAVE20", type: "PERCENT", value: 20 });

    const res = apply(app, id, "SAVE20");
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({
      discountCents: 2000,
      finalAmountCents: 8000,
      couponCode: "SAVE20",
      status: "PENDING",
    });
    // REQ-14: the response equals the subsequent GET /orders/:id.
    expect(app.request("GET", `/orders/${id}`).body).toEqual(res.body);
  });

  it("AC-02 applies a fixed coupon", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    createCoupon(app, { code: "FLAT15", type: "FIXED", value: 1500 });

    const res = apply(app, id, "FLAT15");
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ discountCents: 1500, finalAmountCents: 8500, couponCode: "FLAT15" });
  });

  it("AC-03 caps the discount", () => {
    const app = newApp();
    const id = createOrder(app, 100000);
    createCoupon(app, { code: "SAVE20", type: "PERCENT", value: 20, maxDiscountCents: 5000 });

    const res = apply(app, id, "SAVE20");
    expect(res.body).toMatchObject({ discountCents: 5000, finalAmountCents: 95000 });
  });

  it("AC-04 never discounts below zero", () => {
    const app = newApp();
    const id = createOrder(app, 1000);
    createCoupon(app, { code: "BIG", type: "FIXED", value: 3000 });

    const res = apply(app, id, "BIG");
    expect(res.body).toMatchObject({ discountCents: 1000, finalAmountCents: 0 });
  });

  it("AC-05 floors percent discounts", () => {
    const app = newApp();
    const id = createOrder(app, 999);
    createCoupon(app, { code: "P15", type: "PERCENT", value: 15 });

    const res = apply(app, id, "P15");
    expect(res.body).toMatchObject({ discountCents: 149, finalAmountCents: 850 });
  });

  it("AC-06 allows an amount equal to the minimum", () => {
    const app = newApp();
    const id = createOrder(app, 5000);
    createCoupon(app, { code: "MIN5", type: "FIXED", value: 500, minAmountCents: 5000 });

    const res = apply(app, id, "MIN5");
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ discountCents: 500, finalAmountCents: 4500 });
  });

  it("AC-07a succeeds when now equals validFrom", () => {
    const app = newApp(NOW_ISO);
    const id = createOrder(app, 10000);
    createCoupon(app, { code: "START", validFrom: NOW_ISO });

    expect(apply(app, id, "START").status).toBe(200);
  });

  it("AC-07b rejects when now equals validUntil", () => {
    const app = newApp(NOW_ISO);
    const id = createOrder(app, 10000);
    createCoupon(app, { code: "END", validUntil: NOW_ISO });

    const res = apply(app, id, "END");
    expect(res.status).toBe(400);
    expect((res.body as any).error.code).toBe("COUPON_EXPIRED");
  });

  it("AC-08 rejects a second coupon on the same order and leaves it unchanged", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    createCoupon(app, { code: "SAVE20" });
    createCoupon(app, { code: "OTHER", type: "FIXED", value: 100 });

    expect(apply(app, id, "SAVE20").status).toBe(200);

    const res = apply(app, id, "OTHER");
    expect(res.status).toBe(409);
    expect((res.body as any).error.code).toBe("COUPON_ALREADY_APPLIED");
    expect(app.request("GET", `/orders/${id}`).body).toMatchObject({
      discountCents: 2000,
      finalAmountCents: 8000,
    });
  });

  it("AC-09 records the redemption and bumps usedCount", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    createCoupon(app, { code: "SAVE20" });

    expect(apply(app, id, "SAVE20").status).toBe(200);
    expect((app.request("GET", "/coupons/SAVE20").body as any).usedCount).toBe(1);

    const rows = app.db.prepare("SELECT * FROM coupon_redemptions WHERE order_id = ?").all(id);
    expect(rows.length).toBe(1);
  });

  it("AC-10 normalizes the coupon code", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    createCoupon(app, { code: "SAVE20" });

    const res = apply(app, id, " save20 ");
    expect(res.status).toBe(200);
    expect(res.body).toMatchObject({ couponCode: "SAVE20" });
  });
});

describe("apply-coupon errors", () => {
  it("rejects a missing couponCode", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    const res = app.request("POST", `/orders/${id}/apply-coupon`, {});
    expect(res.status).toBe(400);
    expect((res.body as any).error.code).toBe("INVALID_REQUEST");
  });

  it("rejects a non-string couponCode", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    const res = apply(app, id, 20);
    expect(res.status).toBe(400);
    expect((res.body as any).error.code).toBe("INVALID_REQUEST");
  });

  it("rejects a blank couponCode", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    const res = apply(app, id, "   ");
    expect(res.status).toBe(400);
    expect((res.body as any).error.code).toBe("INVALID_REQUEST");
  });

  it("rejects an unknown order", () => {
    const app = newApp();
    const res = apply(app, 999, "SAVE20");
    expect(res.status).toBe(404);
    expect((res.body as any).error.code).toBe("ORDER_NOT_FOUND");
  });

  it("rejects a non-pending order", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    createCoupon(app, { code: "SAVE20" });
    app.request("POST", `/orders/${id}/cancel`);

    const res = apply(app, id, "SAVE20");
    expect(res.status).toBe(409);
    expect((res.body as any).error.code).toBe("ORDER_NOT_PENDING");
  });

  it("rejects a missing coupon", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    const res = apply(app, id, "NOPE");
    expect(res.status).toBe(404);
    expect((res.body as any).error.code).toBe("COUPON_NOT_FOUND");
  });

  it("rejects an inactive coupon", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    createCoupon(app, { code: "OFF", status: "DISABLED" });
    const res = apply(app, id, "OFF");
    expect(res.status).toBe(400);
    expect((res.body as any).error.code).toBe("COUPON_INACTIVE");
  });

  it("rejects a not-yet-valid coupon", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    createCoupon(app, { code: "LATER", validFrom: "2027-01-01T00:00:00Z" });
    const res = apply(app, id, "LATER");
    expect(res.status).toBe(400);
    expect((res.body as any).error.code).toBe("COUPON_NOT_YET_VALID");
  });

  it("rejects an expired coupon", () => {
    const app = newApp();
    const id = createOrder(app, 10000);
    createCoupon(app, { code: "OLD", validUntil: "2026-01-01T00:00:00Z" });
    const res = apply(app, id, "OLD");
    expect(res.status).toBe(400);
    expect((res.body as any).error.code).toBe("COUPON_EXPIRED");
  });

  it("rejects an exhausted coupon", () => {
    const app = newApp();
    const first = createOrder(app, 10000);
    const second = createOrder(app, 10000);
    createCoupon(app, { code: "ONCE", usageLimit: 1 });

    expect(apply(app, first, "ONCE").status).toBe(200);

    const res = apply(app, second, "ONCE");
    expect(res.status).toBe(400);
    expect((res.body as any).error.code).toBe("COUPON_USAGE_EXHAUSTED");
  });

  it("rejects when the minimum amount is not met", () => {
    const app = newApp();
    const id = createOrder(app, 1000);
    createCoupon(app, { code: "MIN5", minAmountCents: 5000 });
    const res = apply(app, id, "MIN5");
    expect(res.status).toBe(400);
    expect((res.body as any).error.code).toBe("MINIMUM_NOT_MET");
  });

  it("leaves the order untouched when a rule fails", () => {
    const app = newApp();
    const id = createOrder(app, 1000);
    createCoupon(app, { code: "MIN5", minAmountCents: 5000 });

    apply(app, id, "MIN5");

    expect(app.request("GET", `/orders/${id}`).body).toMatchObject({
      discountCents: 0,
      finalAmountCents: 1000,
      couponCode: null,
      status: "PENDING",
    });
  });
});