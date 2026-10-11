// 与后端 DTO 一一对应（rbac-contract/src/main/resources/openapi/rbac-api.yaml）

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface Page<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export type UserStatus = 'ACTIVE' | 'DISABLED' | 'DELETED'
export type UserType = 'EMPLOYEE' | 'TEMPORARY' | 'SYSTEM'

export interface LoginResult {
  token: string
  expiresIn: number
  userId: number
  username: string
  realName: string
  mustChangePwd: boolean
}

export interface User {
  id: number
  username: string
  realName: string
  userType: UserType
  employeeNo: string | null
  phone: string | null
  position: string | null
  status: UserStatus
  statusReason: string | null
  companyId: number
  departmentId: number
  departmentName: string | null
  expiresAt: string | null
  mustChangePwd: boolean
  createdAt: string | null
}

export interface Assignment {
  assignmentId: number
  userId: number
  username: string
  userRealName: string
  roleId: number
  roleCode: string
  roleName: string
  roleStatus: string
  scopeDeptId: number
  scopeDeptName: string
  scopePath: string
  scopeCompanyId: number
  grantedBy: number | null
  grantedAt: string
  reason: string | null
}

export interface EffectivePermission {
  permissionCode: string
  roleCode: string
  roleName: string
  scopeDeptId: number
  scopeDeptName: string
  selfOnly: boolean
}

export interface UserDetail {
  user: User
  roles: Assignment[]
  superAdmin: boolean
  permissions: EffectivePermission[]
}

export interface CreateUser {
  username: string
  realName: string
  departmentId: number | null
  userType: UserType
  employeeNo?: string | null
  phone?: string | null
  position?: string | null
  expiresAt?: string | null
  password?: string | null
}

export interface UpdateUser {
  realName?: string
  departmentId?: number | null
  phone?: string | null
  position?: string | null
  expiresAt?: string | null
}

export interface BatchFailure {
  userId: number
  reason: string
}

export interface BatchResult {
  mode: string
  committed: boolean
  requested: number
  succeeded: number
  duplicated: number
  failed: number
  failures: BatchFailure[]
}

export type TargetType = 'COMPANY' | 'DEPARTMENT' | 'USER'
export type BatchMode = 'ATOMIC' | 'PARTIAL'

export interface BatchAssign {
  targetType: TargetType
  targetIds: number[]
  includeDescendants?: boolean
  roleId: number
  scopeDepartmentId?: number | null
  reason?: string
  mode?: BatchMode
}

export interface Role {
  id: number
  roleCode: string
  roleName: string
  description: string | null
  status: 'ENABLED' | 'DISABLED'
  builtin: boolean
  companyId: number | null
  userCount: number
  permissionCount: number
}

export interface RolePermission {
  permissionId: number
  permissionCode: string
  resourceName: string
  actionName: string
  selfOnly: boolean
  grantedAt: string
}

export interface RoleDetail {
  role: Role
  permissions: RolePermission[]
}

export interface ModuleView {
  id: number
  moduleCode: string
  moduleName: string
}

export interface SystemView {
  id: number
  systemCode: string
  systemName: string
  modules: ModuleView[]
}

export type ResourceType = 'BUSINESS' | 'MENU' | 'API'

export interface ResourceView {
  id: number
  resourceCode: string
  resourceName: string
  resourceType: ResourceType
  description: string | null
  moduleName: string
  systemName: string
}

export interface ActionView {
  id: number
  actionCode: string
  actionName: string
}

export interface PermissionView {
  id: number
  permissionCode: string
  resourceName: string
  resourceType: ResourceType
  actionName: string
  systemName: string | null
}

export interface MenuNode {
  id: number
  parentId: number | null
  menuName: string
  routePath: string | null
  componentKey: string | null
  icon: string | null
  sortOrder: number
  resourceId: number | null
  resourceCode: string | null
  children: MenuNode[]
}

export interface SaveMenu {
  parentId: number | null
  resourceId: number | null
  menuName: string
  routePath: string | null
  componentKey: string | null
  icon: string | null
  sortOrder: number | null
}

export interface CompanyView {
  id: number
  companyCode: string
  companyName: string
  rootDepartmentId: number
}

export interface DeptNode {
  id: number
  parentId: number | null
  deptName: string
  level: number
  managerName: string | null
  managerUserId: number | null
  children: DeptNode[]
}

export interface ImportAnomaly {
  sheet: string
  row: number
  reason: string
  value: string
}

export interface ImportReport {
  counts: Record<string, number>
  notes: string[]
  anomalies: ImportAnomaly[]
}

export interface AuditLog {
  id: number
  occurredAt: string
  actorId: number | null
  actorName: string | null
  clientIp: string | null
  eventType: string
  targetType: string | null
  targetId: string | null
  targetName: string | null
  detail: string | null
  result: 'SUCCESS' | 'DENIED' | 'FAILED'
  reason: string | null
  permissionCode: string | null
  latencyUs: number | null
  cacheHit: boolean | null
}

export interface Dashboard {
  userCount: number
  roleCount: number
  permissionCount: number
  todayAuthzCount: number
  successRate: number
  failureRate: number
  p95LatencyMs: number
  p99LatencyMs: number
  qps: number
  cacheHitRate: number
  abnormalCount: number
  generatedAt: string
}

export interface Heatmap {
  from: string
  to: string
  actions: string[]
  systems: string[]
  cells: [number, number, number][]
}

export interface AuthzRequest {
  userId?: number | null
  username?: string | null
  permission?: string | null
  resource?: string | null
  action?: string | null
  targetDeptId?: number | null
}

export interface GrantRow {
  permissionCode: string
  assignmentId: number
  roleId: number
  roleCode: string
  roleName: string
  scopeDeptId: number
  scopeDeptName: string
  scopePath: string
  scopeCompanyId: number
  selfOnly: boolean
}

export interface Decision {
  allowed: boolean
  reason: string
  userId: number | null
  username: string | null
  permissionCode: string
  targetDeptId: number | null
  matched: GrantRow | null
  selfOnly: boolean
  cacheHit: boolean
  latencyUs: number
  trace: string[] | null
}
