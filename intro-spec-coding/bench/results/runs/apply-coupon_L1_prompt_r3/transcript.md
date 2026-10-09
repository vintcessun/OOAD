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

  /** Records the coupon applied to an order and recomputes the final amount. */
  applyCoupon(id: number, couponCode: string, discountCents: number): void {
    this.db
      .prepare(
        `UPDATE orders
            SET coupon_code = ?, discount_cents = ?, final_amount_cents = amount_cents - ?
          WHERE id = ?`,
      )
      .run(couponCode, discountCents, discountCents, id);
  }
}


=== FILE: src/repositories/couponRepository.ts ===
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

  incrementUsedCount(code: string): void {
    this.db.prepare("UPDATE coupons SET used_count = used_count + 1 WHERE code = ?").run(code);
  }

  recordRedemption(orderId: number, couponCode: string, discountCents: number, createdAt: string): void {
    this.db
      .prepare(
        `INSERT INTO coupon_redemptions (order_id, coupon_code, discount_cents, created_at)
         VALUES (?, ?, ?, ?)`,
      )
      .run(orderId, couponCode, discountCents, createdAt);
  }
}


=== FILE: src/services/orderService.ts ===
import type { Clock } from "../clock.ts";
import { badRequest, conflict, notFound } from "../errors.ts";
import type { Coupon, CouponRepository } from "../repositories/couponRepository.ts";
import type { Order, OrderRepository } from "../repositories/orderRepository.ts";

/** Discount (in cents) a coupon grants on an order amount. */
function computeDiscount(coupon: Coupon, amountCents: number): number {
  let discount =
    coupon.type === "FIXED" ? coupon.value : Math.floor((amountCents * coupon.value) / 100);
  if (coupon.maxDiscountCents !== null) discount = Math.min(discount, coupon.maxDiscountCents);
  return Math.max(0, Math.min(discount, amountCents));
}

export class OrderService {
  constructor(
    private readonly orders: OrderRepository,
    private readonly coupons: CouponRepository,
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

  applyCoupon(id: number, couponCode: unknown): Order {
    if (typeof couponCode !== "string" || couponCode.trim() === "") {
      throw badRequest("INVALID_REQUEST", "couponCode is required");
    }
    const code = couponCode.trim();

    const order = this.get(id);
    if (order.status !== "PENDING") {
      throw conflict("ORDER_NOT_PENDING", `order ${id} is ${order.status}`);
    }
    if (order.couponCode !== null) {
      throw conflict("ORDER_ALREADY_HAS_COUPON", `order ${id} already uses coupon ${order.couponCode}`);
    }

    const coupon = this.coupons.findByCode(code);
    if (!coupon) throw notFound("COUPON_NOT_FOUND", `coupon ${code} not found`);
    if (coupon.status !== "ACTIVE") throw conflict("COUPON_DISABLED", `coupon ${coupon.code} is disabled`);

    const now = this.clock();
    if (now.getTime() < Date.parse(coupon.validFrom)) {
      throw conflict("COUPON_NOT_YET_VALID", `coupon ${coupon.code} is not valid yet`);
    }
    if (now.getTime() > Date.parse(coupon.validUntil)) {
      throw conflict("COUPON_EXPIRED", `coupon ${coupon.code} has expired`);
    }
    if (order.amountCents < coupon.minAmountCents) {
      throw conflict(
        "COUPON_MIN_AMOUNT_NOT_MET",
        `order amount ${order.amountCents} is below minimum ${coupon.minAmountCents} for coupon ${coupon.code}`,
      );
    }
    if (coupon.usageLimit !== null && coupon.usedCount >= coupon.usageLimit) {
      throw conflict("COUPON_USAGE_LIMIT_REACHED", `coupon ${coupon.code} usage limit reached`);
    }

    const discountCents = computeDiscount(coupon, order.amountCents);
    this.orders.applyCoupon(id, coupon.code, discountCents);
    this.coupons.incrementUsedCount(coupon.code);
    this.coupons.recordRedemption(id, coupon.code, discountCents, now.toISOString());
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
  registerOrderRoutes(router, new OrderService(orderRepo, couponRepo, clock));
  registerCouponRoutes(router, new CouponService(couponRepo));

  return { db, request: (method, path, body) => router.handle(method, path, body) };
}
