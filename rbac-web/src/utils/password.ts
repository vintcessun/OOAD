/** 与后端 AuthenticationService.meetsPolicy 相同的口令策略，只用于提前提示；以后端校验为准。 */
export function meetsPolicy(pw: string | null | undefined): boolean {
  if (!pw || pw.length < 8) return false
  return /[A-Z]/.test(pw) && /[a-z]/.test(pw) && /\d/.test(pw) && /[^A-Za-z0-9]/.test(pw)
}
