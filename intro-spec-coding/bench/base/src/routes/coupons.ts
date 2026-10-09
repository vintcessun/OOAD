import type { Router } from "../router.ts";
import type { CouponService } from "../services/couponService.ts";

export function registerCouponRoutes(router: Router, coupons: CouponService): void {
  router.add("POST", "/coupons", (_, b) => ({ status: 201, body: coupons.create(b) }));
  router.add("GET", "/coupons/:code", (p) => ({ status: 200, body: coupons.get(p.code) }));
}
