import { DatabaseSync } from "node:sqlite";

export type Db = DatabaseSync;

const SCHEMA = `
CREATE TABLE IF NOT EXISTS orders (
  id                 INTEGER PRIMARY KEY AUTOINCREMENT,
  customer           TEXT    NOT NULL,
  amount_cents       INTEGER NOT NULL CHECK (amount_cents > 0),
  discount_cents     INTEGER NOT NULL DEFAULT 0,
  final_amount_cents INTEGER NOT NULL,
  coupon_code        TEXT,
  status             TEXT    NOT NULL DEFAULT 'PENDING',   -- PENDING | PAID | CANCELLED
  created_at         TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS coupons (
  code               TEXT    PRIMARY KEY,
  type               TEXT    NOT NULL,                     -- FIXED | PERCENT
  value              INTEGER NOT NULL,                     -- FIXED: cents; PERCENT: 0..100
  min_amount_cents   INTEGER NOT NULL DEFAULT 0,
  max_discount_cents INTEGER,                              -- NULL = no cap
  valid_from         TEXT    NOT NULL,                     -- ISO-8601 UTC
  valid_until        TEXT    NOT NULL,                     -- ISO-8601 UTC
  status             TEXT    NOT NULL DEFAULT 'ACTIVE',    -- ACTIVE | DISABLED
  usage_limit        INTEGER,                              -- NULL = unlimited
  used_count         INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS coupon_redemptions (
  id             INTEGER PRIMARY KEY AUTOINCREMENT,
  order_id       INTEGER NOT NULL REFERENCES orders(id),
  coupon_code    TEXT    NOT NULL REFERENCES coupons(code),
  discount_cents INTEGER NOT NULL,
  created_at     TEXT    NOT NULL
);
`;

export function openDb(path = ":memory:"): Db {
  const db = new DatabaseSync(path);
  db.exec("PRAGMA foreign_keys = ON");
  db.exec(SCHEMA);
  return db;
}
