import { createServer } from "node:http";
import { createApp } from "./app.ts";
import { openDb } from "./db.ts";

const app = createApp({ db: openDb(process.env.DB_PATH ?? "orders.db") });

createServer((req, res) => {
  let raw = "";
  req.on("data", (c) => (raw += c));
  req.on("end", () => {
    let body: unknown = {};
    try {
      body = raw ? JSON.parse(raw) : {};
    } catch {
      res.writeHead(400, { "content-type": "application/json" });
      res.end(JSON.stringify({ error: { code: "INVALID_JSON", message: "body is not JSON" } }));
      return;
    }
    const out = app.request(req.method ?? "GET", (req.url ?? "/").split("?")[0], body);
    res.writeHead(out.status, { "content-type": "application/json" });
    res.end(JSON.stringify(out.body));
  });
}).listen(Number(process.env.PORT ?? 3000));
