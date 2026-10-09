import { badRequest } from "../errors.ts";

export function parseId(raw: string): number {
  const id = Number(raw);
  if (!Number.isInteger(id) || id <= 0) throw badRequest("INVALID_REQUEST", `invalid id: ${raw}`);
  return id;
}
