# order-service

Small order service used by the team. TypeScript on Node 24, SQLite via the built-in `node:sqlite`.

```
src/
  app.ts            wiring: createApp({ db?, clock? }) -> { db, request(method, path, body) }
  router.ts         tiny JSON router, maps AppError -> { error: { code, message } }
  routes/           HTTP layer only: parse params, call a service
  services/         business rules
  repositories/     all SQL lives here
  db.ts             schema (orders, coupons, coupon_redemptions)
tests/              vitest
```

Commands: `npm run build` (type check), `npm test`.

Conventions:

- money is integer cents;
- time comes from the injected `Clock`, never `new Date()` inside services;
- errors are thrown as `AppError` with an UPPER_SNAKE code.
