import { AppError } from "./errors.ts";

export interface HttpResponse {
  status: number;
  body: unknown;
}

export type Handler = (params: Record<string, string>, body: any) => HttpResponse;

interface Route {
  method: string;
  pattern: RegExp;
  keys: string[];
  handler: Handler;
}

/** Minimal path router: "/orders/:id" style patterns, JSON in, JSON out. */
export class Router {
  private readonly routes: Route[] = [];

  add(method: string, path: string, handler: Handler): void {
    const keys: string[] = [];
    const source = path.replace(/:(\w+)/g, (_, k: string) => {
      keys.push(k);
      return "([^/]+)";
    });
    this.routes.push({ method, pattern: new RegExp(`^${source}$`), keys, handler });
  }

  handle(method: string, path: string, body: unknown): HttpResponse {
    for (const r of this.routes) {
      if (r.method !== method) continue;
      const m = r.pattern.exec(path);
      if (!m) continue;
      const params = Object.fromEntries(r.keys.map((k, i) => [k, decodeURIComponent(m[i + 1])]));
      try {
        return r.handler(params, body ?? {});
      } catch (e) {
        if (e instanceof AppError) return { status: e.status, body: { error: { code: e.code, message: e.message } } };
        return { status: 500, body: { error: { code: "INTERNAL_ERROR", message: String(e) } } };
      }
    }
    return { status: 404, body: { error: { code: "ROUTE_NOT_FOUND", message: `${method} ${path}` } } };
  }
}
