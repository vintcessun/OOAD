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
}
