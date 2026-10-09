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
  registerOrderRoutes(router, new OrderService(orderRepo, clock));
  registerCouponRoutes(router, new CouponService(couponRepo));

  return { db, request: (method, path, body) => router.handle(method, path, body) };
}
