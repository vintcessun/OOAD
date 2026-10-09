# 任务：订单使用优惠券

## 1. 需求

顾客可以在下单后、付款前给订单使用一张优惠券，订单的应付金额随之降低。

## 2. API 契约

`POST /orders/:id/apply-coupon`

请求体：

```json
{ "couponCode": "SAVE20" }
```

成功：HTTP 200，响应体是更新后的订单，字段与 `GET /orders/:id` 完全相同
（`id, customer, amountCents, discountCents, finalAmountCents, couponCode, status, createdAt`）。

失败：沿用项目现有格式 `{ "error": { "code": "...", "message": "..." } }`，状态码与 code 见第 6 节。

## 3. 数据模型（已存在，不要改表结构）

- `orders.discount_cents`、`orders.final_amount_cents`、`orders.coupon_code`
- `coupons`：`type`（FIXED | PERCENT）、`value`、`min_amount_cents`、`max_discount_cents`（NULL 表示不封顶）、
  `valid_from`、`valid_until`、`status`（ACTIVE | DISABLED）、`usage_limit`（NULL 表示不限）、`used_count`
- `coupon_redemptions`：每成功使用一次插入一行

金额一律为整数「分」。

## 4. 业务规则

- **REQ-01** `couponCode` 先去掉首尾空格再转大写，再按 `coupons.code` 查找；查不到则拒绝。
- **REQ-02** `coupon.status` 必须等于 `ACTIVE`。
- **REQ-03** 当前时间 `now` 必须满足 `validFrom <= now < validUntil`。`now` 取自注入的 `Clock`，不得直接 `new Date()`。
- **REQ-04** 订单必须存在，且 `status` 必须是 `PENDING`。
- **REQ-05** `order.amountCents >= coupon.minAmountCents`（等于时允许）。
- **REQ-06** FIXED：`discount = coupon.value`。
- **REQ-07** PERCENT：`discount = floor(order.amountCents * coupon.value / 100)`。
- **REQ-08** 若 `coupon.maxDiscountCents` 不为 NULL，`discount = min(discount, coupon.maxDiscountCents)`（两种类型都适用）。任何订单单次优惠 MUST NOT 超过 10000 分（100 元）。
- **REQ-09** `discount = min(discount, order.amountCents)`；`finalAmountCents = amountCents - discount`，MUST NOT 小于 0。
- **REQ-10** 一个订单 MUST NOT 使用多于一张优惠券：订单已有 `couponCode` 时拒绝（同一张券再用一次也拒绝）。
- **REQ-11** 若 `coupon.usageLimit` 不为 NULL 且 `usedCount >= usageLimit`，拒绝。
- **REQ-12** 成功时在**同一个事务**里完成：更新订单的 `discount_cents`、`final_amount_cents`、`coupon_code`（存规范化后的大写 code）；
  向 `coupon_redemptions` 插入一行（`order_id, coupon_code, discount_cents, created_at=now`）；`coupons.used_count + 1`。
  任何一步失败则全部回滚。
- **REQ-13** 请求体缺少 `couponCode`、不是字符串、或去空格后为空，拒绝。
- **REQ-14** 成功返回 HTTP 200，响应体与随后 `GET /orders/:id` 的结果完全相同。

## 5. 状态变化

订单只有在 `PENDING` 时可以用券；用券不改变订单 `status`。`PAID`、`CANCELLED` 的订单不能用券。

## 6. 错误码（按下表顺序检查，命中第一条即返回）

| 顺序 | 条件 | HTTP | code |
|---|---|---|---|
| 1 | REQ-13 请求体不合法 | 400 | `INVALID_REQUEST` |
| 2 | 订单不存在 | 404 | `ORDER_NOT_FOUND` |
| 3 | 订单不是 PENDING | 409 | `ORDER_NOT_PENDING` |
| 4 | 订单已用过券（REQ-10） | 409 | `COUPON_ALREADY_APPLIED` |
| 5 | 优惠券不存在 | 404 | `COUPON_NOT_FOUND` |
| 6 | 优惠券不是 ACTIVE | 400 | `COUPON_INACTIVE` |
| 7 | `now < validFrom` | 400 | `COUPON_NOT_YET_VALID` |
| 8 | `now >= validUntil` | 400 | `COUPON_EXPIRED` |
| 9 | 用量已满（REQ-11） | 400 | `COUPON_USAGE_EXHAUSTED` |
| 10 | 未达最低金额（REQ-05） | 400 | `MINIMUM_NOT_MET` |

被拒绝时数据库不得有任何变化。

## 7. 验收标准

- **AC-01** Given `amount=10000`, PERCENT `value=20` → 200，`discountCents=2000`，`finalAmountCents=8000`。
- **AC-02** Given `amount=10000`, FIXED `value=1500` → `finalAmountCents=8500`。
- **AC-03** Given `amount=100000`, PERCENT 20，`maxDiscountCents=NULL` → `discountCents=20000`（200 元）。
- **AC-04** Given `amount=1000`, FIXED `value=3000` → `discountCents=1000`，`finalAmountCents=0`。
- **AC-05** Given `amount=999`, PERCENT 15 → `discountCents=149`（向下取整）。
- **AC-06** Given `amount == minAmountCents` → 成功。
- **AC-07** Given `now == validUntil` → `COUPON_EXPIRED`；Given `now == validFrom` → 成功。
- **AC-08** 同一订单第二次用券时，用新券替换旧券，按新券重新计算金额。
- **AC-09** 成功后 `GET /coupons/:code` 的 `usedCount` 加 1，`coupon_redemptions` 多一行。
- **AC-10** `" save20 "` 能匹配 `SAVE20`。

## 8. 非功能约束

- 遵守现有分层：`routes/` 只解析参数，规则写在 `services/`，SQL 只出现在 `repositories/`。
- 不修改已有测试，不改表结构，不加新依赖。
- 为新功能补充测试；`npm run build` 与 `npm test` 必须通过。
