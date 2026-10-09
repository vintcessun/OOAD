/**
 * Hidden acceptance tests. Never visible to the agent: the runner copies this file into the frozen
 * workspace only after the agent has exited.
 *
 * Every title is "<id> [<REQ> <severity> <category>] <text>"; score.mjs parses it.
 *   severity: critical=5 major=3 minor=1
 *   category: functional | boundary | error | state | regression
 *     functional/boundary/state  -> behaviour (rejected or accepted, amounts, persisted state)
 *     error                      -> exact HTTP status + error code from the spec
 *     regression                 -> pre-existing features still work
 */
import { describe, expect, it } from "vitest";
import { createApp } from "../src/app.ts";

type Res = { status: number; body: any };

const DAY = 86_400_000;

function world(clockAt?: Date) {
  const now = clockAt ?? new Date();
  const app = createApp({ clock: () => now });
  const call = (m: string, p: string, b?: unknown) => app.request(m, p, b) as Res;
  const order = (amountCents: number) => call("POST", "/orders", { customer: "t", amountCents }).body.id as number;
  const coupon = (c: Record<string, unknown>) => {
    const res = call("POST", "/coupons", {
      type: "FIXED",
      value: 100,
      validFrom: new Date(now.getTime() - DAY).toISOString(),
      validUntil: new Date(now.getTime() + DAY).toISOString(),
      ...c,
    });
    if (res.status !== 201) throw new Error(`seed coupon failed: ${JSON.stringify(res.body)}`);
  };
  const apply = (id: number, couponCode: unknown) => call("POST", `/orders/${id}/apply-coupon`, { couponCode });
  const get = (id: number) => call("GET", `/orders/${id}`).body;
  const redemptions = () => {
    try {
      return app.db.prepare("SELECT * FROM coupon_redemptions").all() as any[];
    } catch {
      return [];
    }
  };
  return { app, call, order, coupon, apply, get, redemptions, now };
}

const rejected = (r: Res) => r.status >= 400 && r.status < 500;

function expectUntouched(w: ReturnType<typeof world>, id: number, amount: number) {
  const o = w.get(id);
  expect(o.finalAmountCents).toBe(amount);
  expect(o.couponCode ?? null).toBeNull();
  expect(w.redemptions()).toHaveLength(0);
}

const errorCode = (r: Res) => r.body?.error?.code;

describe("apply-coupon: functional", () => {
  it("H01 [REQ-07 critical functional] percent coupon discounts the order", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "SAVE20", type: "PERCENT", value: 20 });
    const r = w.apply(id, "SAVE20");
    expect(r.status).toBe(200);
    expect(w.get(id)).toMatchObject({ discountCents: 2000, finalAmountCents: 8000, couponCode: "SAVE20" });
  });

  it("H02 [REQ-06 critical functional] fixed coupon subtracts its value", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "FIX15", type: "FIXED", value: 1500 });
    expect(w.apply(id, "FIX15").status).toBe(200);
    expect(w.get(id)).toMatchObject({ discountCents: 1500, finalAmountCents: 8500 });
  });

  it("H03 [REQ-08 major functional] percent discount is capped by maxDiscountCents", () => {
    const w = world();
    const id = w.order(100000);
    w.coupon({ code: "CAP", type: "PERCENT", value: 20, maxDiscountCents: 5000 });
    w.apply(id, "CAP");
    expect(w.get(id)).toMatchObject({ discountCents: 5000, finalAmountCents: 95000 });
  });

  it("H04 [REQ-01 critical functional] unknown coupon is rejected and order unchanged", () => {
    const w = world();
    const id = w.order(10000);
    expect(rejected(w.apply(id, "NOPE"))).toBe(true);
    expectUntouched(w, id, 10000);
  });

  it("H05 [REQ-02 critical functional] disabled coupon is rejected", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "OFF", status: "DISABLED" });
    expect(rejected(w.apply(id, "OFF"))).toBe(true);
    expectUntouched(w, id, 10000);
  });

  it("H06 [REQ-03 critical functional] expired coupon is rejected", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({
      code: "OLD",
      validFrom: new Date(w.now.getTime() - 10 * DAY).toISOString(),
      validUntil: new Date(w.now.getTime() - DAY).toISOString(),
    });
    expect(rejected(w.apply(id, "OLD"))).toBe(true);
    expectUntouched(w, id, 10000);
  });

  it("H07 [REQ-03 major functional] not-yet-valid coupon is rejected", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({
      code: "SOON",
      validFrom: new Date(w.now.getTime() + DAY).toISOString(),
      validUntil: new Date(w.now.getTime() + 10 * DAY).toISOString(),
    });
    expect(rejected(w.apply(id, "SOON"))).toBe(true);
    expectUntouched(w, id, 10000);
  });

  it("H08 [REQ-05 critical functional] order below minimum amount is rejected", () => {
    const w = world();
    const id = w.order(4999);
    w.coupon({ code: "MIN50", minAmountCents: 5000 });
    expect(rejected(w.apply(id, "MIN50"))).toBe(true);
    expectUntouched(w, id, 4999);
  });

  it("H09 [REQ-10 critical functional] a second coupon on the same order is rejected", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "AAA", value: 1000 });
    w.coupon({ code: "BBB", value: 2000 });
    expect(w.apply(id, "AAA").status).toBe(200);
    expect(rejected(w.apply(id, "BBB"))).toBe(true);
    expect(w.get(id)).toMatchObject({ finalAmountCents: 9000, couponCode: "AAA" });
  });

  it("H10 [REQ-10 major functional] the same coupon cannot be applied twice to one order", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "AAA", value: 1000 });
    w.apply(id, "AAA");
    expect(rejected(w.apply(id, "AAA"))).toBe(true);
    expect(w.get(id).finalAmountCents).toBe(9000);
  });

  it("H11 [REQ-04 major functional] cancelled order cannot use a coupon", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "AAA", value: 1000 });
    w.call("POST", `/orders/${id}/cancel`);
    expect(rejected(w.apply(id, "AAA"))).toBe(true);
    expect(w.get(id).finalAmountCents).toBe(10000);
  });

  it("H12 [REQ-04 major functional] paid order cannot use a coupon", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "AAA", value: 1000 });
    w.call("POST", `/orders/${id}/pay`);
    expect(rejected(w.apply(id, "AAA"))).toBe(true);
    expect(w.get(id).finalAmountCents).toBe(10000);
  });

  it("H13 [REQ-11 major functional] exhausted usage limit is rejected", () => {
    const w = world();
    const a = w.order(10000);
    const b = w.order(10000);
    w.coupon({ code: "ONCE", value: 1000, usageLimit: 1 });
    expect(w.apply(a, "ONCE").status).toBe(200);
    expect(rejected(w.apply(b, "ONCE"))).toBe(true);
    expect(w.get(b).finalAmountCents).toBe(10000);
  });

  it("H14 [REQ-13 major functional] missing couponCode is rejected", () => {
    const w = world();
    const id = w.order(10000);
    const r = w.call("POST", `/orders/${id}/apply-coupon`, {});
    expect(rejected(r)).toBe(true);
    expect(w.get(id).finalAmountCents).toBe(10000);
  });

  it("H15 [REQ-14 major functional] success response is the updated order", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "SAVE20", type: "PERCENT", value: 20 });
    const r = w.apply(id, "SAVE20");
    expect(r.status).toBe(200);
    expect(r.body).toEqual(w.get(id));
  });
});

describe("apply-coupon: boundary", () => {
  it("B01 [REQ-09 major boundary] fixed discount larger than amount brings final to 0, not negative", () => {
    const w = world();
    const id = w.order(1000);
    w.coupon({ code: "BIG", value: 3000 });
    w.apply(id, "BIG");
    expect(w.get(id)).toMatchObject({ discountCents: 1000, finalAmountCents: 0 });
  });

  it("B02 [REQ-05 major boundary] amount equal to minimum is accepted", () => {
    const w = world();
    const id = w.order(5000);
    w.coupon({ code: "MIN50", value: 500, minAmountCents: 5000 });
    expect(w.apply(id, "MIN50").status).toBe(200);
    expect(w.get(id).finalAmountCents).toBe(4500);
  });

  it("B03 [REQ-07 minor boundary] percent discount rounds down to whole cents", () => {
    const w = world();
    const id = w.order(999);
    w.coupon({ code: "P15", type: "PERCENT", value: 15 });
    w.apply(id, "P15");
    expect(w.get(id)).toMatchObject({ discountCents: 149, finalAmountCents: 850 });
  });

  it("B04 [REQ-03 major boundary] coupon is expired at exactly validUntil (injected clock)", () => {
    const w = world(new Date("2026-03-01T00:00:00.000Z"));
    const id = w.order(10000);
    w.coupon({ code: "EDGE", validFrom: "2026-01-01T00:00:00.000Z", validUntil: "2026-03-01T00:00:00.000Z" });
    expect(rejected(w.apply(id, "EDGE"))).toBe(true);
    expect(w.get(id).finalAmountCents).toBe(10000);
  });

  it("B05 [REQ-03 major boundary] coupon is valid at exactly validFrom (injected clock)", () => {
    const w = world(new Date("2026-03-01T00:00:00.000Z"));
    const id = w.order(10000);
    w.coupon({ code: "START", value: 1000, validFrom: "2026-03-01T00:00:00.000Z", validUntil: "2026-04-01T00:00:00.000Z" });
    expect(w.apply(id, "START").status).toBe(200);
    expect(w.get(id).finalAmountCents).toBe(9000);
  });

  it("B06 [REQ-08 minor boundary] maxDiscountCents also caps FIXED coupons", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "FCAP", value: 3000, maxDiscountCents: 2000 });
    w.apply(id, "FCAP");
    expect(w.get(id).discountCents).toBe(2000);
  });

  it("B07 [REQ-01 minor boundary] coupon code is trimmed and case-insensitive", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "SAVE20", type: "PERCENT", value: 20 });
    expect(w.apply(id, "  save20 ").status).toBe(200);
    expect(w.get(id)).toMatchObject({ finalAmountCents: 8000, couponCode: "SAVE20" });
  });

  it("B08 [REQ-07 minor boundary] 100% coupon makes the order free", () => {
    const w = world();
    const id = w.order(7777);
    w.coupon({ code: "FREE", type: "PERCENT", value: 100 });
    w.apply(id, "FREE");
    expect(w.get(id).finalAmountCents).toBe(0);
  });
});

describe("apply-coupon: error codes", () => {
  const cases: [string, string, number, string, (w: ReturnType<typeof world>) => Res][] = [
    ["E01", "REQ-01", 404, "COUPON_NOT_FOUND", (w) => w.apply(w.order(1000), "NOPE")],
    ["E02", "REQ-02", 400, "COUPON_INACTIVE", (w) => (w.coupon({ code: "OFF", status: "DISABLED" }), w.apply(w.order(1000), "OFF"))],
    [
      "E03",
      "REQ-03",
      400,
      "COUPON_EXPIRED",
      (w) => (
        w.coupon({ code: "OLD", validFrom: new Date(w.now.getTime() - 9 * DAY).toISOString(), validUntil: new Date(w.now.getTime() - DAY).toISOString() }),
        w.apply(w.order(1000), "OLD")
      ),
    ],
    [
      "E04",
      "REQ-03",
      400,
      "COUPON_NOT_YET_VALID",
      (w) => (
        w.coupon({ code: "SOON", validFrom: new Date(w.now.getTime() + DAY).toISOString(), validUntil: new Date(w.now.getTime() + 9 * DAY).toISOString() }),
        w.apply(w.order(1000), "SOON")
      ),
    ],
    ["E05", "REQ-05", 400, "MINIMUM_NOT_MET", (w) => (w.coupon({ code: "MIN", minAmountCents: 5000 }), w.apply(w.order(1000), "MIN"))],
    [
      "E06",
      "REQ-10",
      409,
      "COUPON_ALREADY_APPLIED",
      (w) => {
        const id = w.order(1000);
        w.coupon({ code: "AAA" });
        w.coupon({ code: "BBB" });
        w.apply(id, "AAA");
        return w.apply(id, "BBB");
      },
    ],
    ["E07", "REQ-04", 404, "ORDER_NOT_FOUND", (w) => (w.coupon({ code: "AAA" }), w.apply(999, "AAA"))],
    [
      "E08",
      "REQ-04",
      409,
      "ORDER_NOT_PENDING",
      (w) => {
        const id = w.order(1000);
        w.coupon({ code: "AAA" });
        w.call("POST", `/orders/${id}/cancel`);
        return w.apply(id, "AAA");
      },
    ],
    [
      "E09",
      "REQ-11",
      400,
      "COUPON_USAGE_EXHAUSTED",
      (w) => {
        w.coupon({ code: "ONCE", usageLimit: 1 });
        w.apply(w.order(1000), "ONCE");
        return w.apply(w.order(1000), "ONCE");
      },
    ],
    ["E10", "REQ-13", 400, "INVALID_REQUEST", (w) => w.call("POST", `/orders/${w.order(1000)}/apply-coupon`, { couponCode: "   " })],
  ];

  for (const [id, req, status, code, act] of cases) {
    it(`${id} [${req} major error] returns ${status} ${code}`, () => {
      const r = act(world());
      expect(r.status).toBe(status);
      expect(errorCode(r)).toBe(code);
      expect(typeof r.body.error.message).toBe("string");
    });
  }
});

describe("apply-coupon: state consistency", () => {
  it("S01 [REQ-12 critical state] success records a redemption and increments usedCount", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "SAVE20", type: "PERCENT", value: 20 });
    w.apply(id, "SAVE20");
    expect(w.call("GET", "/coupons/SAVE20").body.usedCount).toBe(1);
    const rows = w.redemptions();
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ order_id: id, coupon_code: "SAVE20", discount_cents: 2000 });
  });

  it("S02 [REQ-12 major state] rejected application leaves usedCount untouched", () => {
    const w = world();
    const id = w.order(1000);
    w.coupon({ code: "MIN", minAmountCents: 5000 });
    w.apply(id, "MIN");
    expect(w.call("GET", "/coupons/MIN").body.usedCount).toBe(0);
  });

  it("S03 [REQ-12 major state] a failure inside the write rolls everything back", () => {
    const w = world();
    const id = w.order(10000);
    w.coupon({ code: "SAVE20", type: "PERCENT", value: 20 });
    // Make the redemption insert fail after the order update would have run.
    w.app.db.exec(`CREATE TRIGGER boom BEFORE INSERT ON coupon_redemptions BEGIN SELECT RAISE(ABORT, 'boom'); END;`);
    const r = w.apply(id, "SAVE20");
    expect(r.status).toBeGreaterThanOrEqual(500);
    expect(w.get(id)).toMatchObject({ finalAmountCents: 10000, discountCents: 0 });
    expect(w.call("GET", "/coupons/SAVE20").body.usedCount).toBe(0);
  });

  it("S04 [REQ-12 minor state] redemption timestamp comes from the injected clock", () => {
    const at = new Date("2026-02-14T12:00:00.000Z");
    const w = world(at);
    const id = w.order(10000);
    w.coupon({ code: "LOVE", value: 520, validFrom: "2026-02-01T00:00:00.000Z", validUntil: "2026-03-01T00:00:00.000Z" });
    w.apply(id, "LOVE");
    expect(w.redemptions()[0]?.created_at).toBe(at.toISOString());
  });
});

describe("regression: existing features", () => {
  it("R01 [REG-01 critical regression] create/get order still works", () => {
    const w = world();
    const id = w.order(1234);
    expect(w.get(id)).toMatchObject({ amountCents: 1234, finalAmountCents: 1234, discountCents: 0, couponCode: null, status: "PENDING" });
  });

  it("R02 [REG-02 major regression] pay and cancel transitions still guarded", () => {
    const w = world();
    const id = w.order(1000);
    expect(w.call("POST", `/orders/${id}/pay`).body.status).toBe("PAID");
    expect(w.call("POST", `/orders/${id}/cancel`).status).toBe(409);
  });

  it("R03 [REG-03 major regression] coupon admin still works and starts unused", () => {
    const w = world();
    w.coupon({ code: "ADMIN1", type: "PERCENT", value: 5 });
    expect(w.call("GET", "/coupons/ADMIN1").body).toMatchObject({ usedCount: 0, status: "ACTIVE" });
    expect(w.call("GET", "/coupons/NONE").status).toBe(404);
  });

  it("R04 [REG-04 minor regression] unknown route still answers ROUTE_NOT_FOUND", () => {
    const w = world();
    expect(errorCode(w.call("DELETE", "/orders/1"))).toBe("ROUTE_NOT_FOUND");
  });
});
