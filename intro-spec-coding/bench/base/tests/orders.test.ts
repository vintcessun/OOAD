import { describe, expect, it } from "vitest";
import { createApp } from "../src/app.ts";

const fixedClock = () => new Date("2026-10-01T08:00:00Z");

describe("orders", () => {
  it("creates an order whose final amount equals its amount", () => {
    const app = createApp({ clock: fixedClock });
    const res = app.request("POST", "/orders", { customer: "alice", amountCents: 12000 });
    expect(res.status).toBe(201);
    expect(res.body).toMatchObject({
      customer: "alice",
      amountCents: 12000,
      discountCents: 0,
      finalAmountCents: 12000,
      couponCode: null,
      status: "PENDING",
      createdAt: "2026-10-01T08:00:00.000Z",
    });
  });

  it("rejects a non-positive amount", () => {
    const res = createApp().request("POST", "/orders", { customer: "bob", amountCents: 0 });
    expect(res.status).toBe(400);
    expect(res.body).toEqual({ error: { code: "INVALID_REQUEST", message: expect.any(String) } });
  });

  it("returns 404 for an unknown order", () => {
    const res = createApp().request("GET", "/orders/99");
    expect(res.status).toBe(404);
    expect((res.body as any).error.code).toBe("ORDER_NOT_FOUND");
  });

  it("cancels a pending order once", () => {
    const app = createApp();
    app.request("POST", "/orders", { customer: "carol", amountCents: 500 });
    expect(app.request("POST", "/orders/1/cancel").body).toMatchObject({ status: "CANCELLED" });
    expect(app.request("POST", "/orders/1/cancel").status).toBe(409);
  });
});

describe("coupons admin", () => {
  it("creates and reads a coupon", () => {
    const app = createApp();
    const res = app.request("POST", "/coupons", {
      code: "SAVE20",
      type: "PERCENT",
      value: 20,
      validFrom: "2026-01-01T00:00:00Z",
      validUntil: "2027-01-01T00:00:00Z",
    });
    expect(res.status).toBe(201);
    expect(app.request("GET", "/coupons/SAVE20").body).toMatchObject({ code: "SAVE20", usedCount: 0, status: "ACTIVE" });
  });
});
