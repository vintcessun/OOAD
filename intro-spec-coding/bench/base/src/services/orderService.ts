import type { Clock } from "../clock.ts";
import { badRequest, conflict, notFound } from "../errors.ts";
import type { Order, OrderRepository } from "../repositories/orderRepository.ts";

export class OrderService {
  constructor(
    private readonly orders: OrderRepository,
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
}
