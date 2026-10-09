import type { Router } from "../router.ts";
import type { OrderService } from "../services/orderService.ts";
import { parseId } from "./params.ts";

export function registerOrderRoutes(router: Router, orders: OrderService): void {
  router.add("POST", "/orders", (_, b) => ({ status: 201, body: orders.create(b.customer, b.amountCents) }));
  router.add("GET", "/orders", () => ({ status: 200, body: orders.list() }));
  router.add("GET", "/orders/:id", (p) => ({ status: 200, body: orders.get(parseId(p.id)) }));
  router.add("POST", "/orders/:id/pay", (p) => ({ status: 200, body: orders.pay(parseId(p.id)) }));
  router.add("POST", "/orders/:id/cancel", (p) => ({ status: 200, body: orders.cancel(parseId(p.id)) }));
}
