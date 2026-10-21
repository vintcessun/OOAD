// 显示用的格式化函数（纯函数，便于单测）

/** 列表中电话脱敏：13812345678 → 138****5678（docs/05-ui.md §6）。 */
export function maskPhone(phone: string | null | undefined): string {
  if (!phone) return '—'
  if (phone.length < 7) return phone
  return phone.slice(0, 3) + '****' + phone.slice(-4)
}

export const USER_TYPE_LABEL: Record<string, string> = {
  EMPLOYEE: '正式员工',
  TEMPORARY: '临时用户',
  SYSTEM: '系统账号',
}

export const USER_STATUS_LABEL: Record<string, string> = {
  ACTIVE: '启用',
  DISABLED: '停用',
  DELETED: '已删除',
}

export const ROLE_STATUS_LABEL: Record<string, string> = {
  ENABLED: '启用',
  DISABLED: '停用',
}

export const RESOURCE_TYPE_LABEL: Record<string, string> = {
  BUSINESS: '业务',
  MENU: '菜单',
  API: '接口',
}

/** 判定原因码的中文说明（docs/05-ui.md §3.10）。 */
export const REASON_LABEL: Record<string, string> = {
  ROLE_GRANTED: '允许：角色授予',
  SUPER_ADMIN_BYPASS: '允许：超级管理员越过权限引擎',
  SUBJECT_NOT_FOUND: '用户不存在',
  SUBJECT_DISABLED: '用户已停用',
  SUBJECT_DELETED: '用户已删除',
  MISSING_PERMISSION: '当前角色均不包含该权限',
  OUT_OF_DATA_SCOPE: '有角色具备该权限，但适用部门不覆盖目标部门',
  TARGET_DEPT_NOT_FOUND: '目标部门不存在',
}

/** 批量操作失败原因的中文说明。 */
export const FAILURE_LABEL: Record<string, string> = {
  NOT_FOUND: '用户不存在',
  SUBJECT_DELETED: '用户已删除',
  ROLE_DISABLED: '角色已停用',
  SCOPE_DEPT_NOT_FOUND: '适用部门不存在',
  ROLE_COMPANY_MISMATCH: '公司内角色只能指派到本公司',
  SUPER_ADMIN_ONLY: '只有超级管理员能操作超级管理员角色',
  OUT_OF_MANAGE_SCOPE: '不在你的管理范围内',
  PRIVILEGE_ESCALATION: '该角色含你在适用部门没有的权限',
  ALREADY_DELETED: '用户已删除',
  CANNOT_CHANGE_SELF: '不能操作自己的账号',
  TARGET_IS_SUPER_ADMIN: '只有超级管理员能变更超级管理员的账号',
}

export function label(map: Record<string, string>, key: string | null | undefined): string {
  if (!key) return '—'
  return map[key] ?? key
}

export function percent(v: number): string {
  return (v * 100).toFixed(1) + '%'
}

/** 2026-10-11T21:58:03.21 → 2026-10-11 21:58:03 */
export function formatTime(v: string | null | undefined): string {
  if (!v) return '—'
  return v.replace('T', ' ').slice(0, 19)
}

/** Date → 后端 LocalDateTime 需要的「YYYY-MM-DDTHH:mm:ss」（不带时区）。 */
export function toLocalDateTime(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 权限码按冒号拆段，用于分段着色显示。 */
export function splitPermissionCode(code: string): string[] {
  return code.split(':')
}

/** 「角色 @ 适用部门」：凡是出现角色的地方都带适用部门（docs/05-ui.md §6）。 */
export function roleAtScope(roleName: string, scopeDeptName: string): string {
  return `${roleName} @ ${scopeDeptName}`
}
