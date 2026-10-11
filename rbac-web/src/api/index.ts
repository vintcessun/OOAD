import { del, get, patch, post, type RequestOptions } from './http'
import type {
  ActionView, Assignment, AuditLog, AuthzRequest, BatchAssign, BatchResult, CompanyView, CreateUser, Dashboard,
  Decision, DeptNode, EffectivePermission, Heatmap, ImportReport, LoginResult, MenuNode, Page, PermissionView,
  ResourceType, ResourceView, Role, RoleDetail, SaveMenu, SystemView, UpdateUser, User, UserDetail, UserStatus,
} from './types'

// 45 个接口按契约的分组组织（docs/04-api.md §2）。/authz/check 由业务系统调用，控制台只用 explain。

export const authApi = {
  login: (username: string, password: string) =>
    post<LoginResult>('/auth/login', { username, password }, { silent: true }),
  logout: () => post<void>('/auth/logout', undefined, { silent: true }),
  me: () => get<UserDetail>('/auth/me'),
  changePassword: (oldPassword: string, newPassword: string) =>
    post<void>('/auth/password', { oldPassword, newPassword }, { silent: true }),
}

export const authzApi = {
  check: (req: AuthzRequest) => post<Decision>('/authz/check', req),
  explain: (req: AuthzRequest, opts?: RequestOptions) => post<Decision>('/authz/explain', req, opts),
}

export interface UserQuery {
  keyword?: string
  departmentId?: number | null
  includeDescendants?: boolean
  status?: UserStatus | ''
  userType?: string
  page?: number
  size?: number
}

export const userApi = {
  list: (q: UserQuery) => get<Page<User>>('/users', clean(q)),
  create: (u: CreateUser) => post<User>('/users', u),
  detail: (id: number) => get<UserDetail>(`/users/${id}`),
  update: (id: number, u: UpdateUser) => patch<User>(`/users/${id}`, u),
  changeStatus: (id: number, status: 'ACTIVE' | 'DISABLED', reason?: string) =>
    patch<User>(`/users/${id}/status`, { status, reason }),
  remove: (id: number, reason?: string) => del<void>(`/users/${id}`, clean({ reason })),
  batchStatus: (userIds: number[], status: UserStatus, reason?: string) =>
    post<BatchResult>('/users/batch-status', { userIds, status, reason }),
  roles: (id: number) => get<Assignment[]>(`/users/${id}/roles`),
  assign: (id: number, roleId: number, scopeDepartmentId: number | null, reason?: string) =>
    post<Assignment>(`/users/${id}/roles`, { roleId, scopeDepartmentId, reason }),
  revoke: (id: number, assignmentId: number, reason?: string) =>
    del<void>(`/users/${id}/roles/${assignmentId}`, clean({ reason })),
  permissions: (id: number) => get<EffectivePermission[]>(`/users/${id}/permissions`),
}

export const roleApi = {
  list: (keyword?: string, status?: string) => get<Role[]>('/roles', clean({ keyword, status })),
  create: (r: { roleCode: string; roleName: string; description?: string; companyId?: number | null }) =>
    post<Role>('/roles', r),
  detail: (id: number) => get<RoleDetail>(`/roles/${id}`),
  update: (id: number, r: { roleName?: string; description?: string; status?: string }) => patch<Role>(`/roles/${id}`, r),
  remove: (id: number) => del<void>(`/roles/${id}`),
  users: (id: number, page = 0, size = 20) => get<Page<Assignment>>(`/roles/${id}/users`, { page, size }),
  grant: (id: number, permissionIds: number[]) => post<RoleDetail>(`/roles/${id}/permissions`, { permissionIds }),
  ungrant: (id: number, permissionId: number) => del<void>(`/roles/${id}/permissions/${permissionId}`),
}

export const assignmentApi = {
  batch: (req: BatchAssign) => post<BatchResult>('/assignments/batch', req),
  batchRevoke: (req: BatchAssign) => post<BatchResult>('/assignments/batch-revoke', req),
}

export const catalogApi = {
  systems: () => get<SystemView[]>('/systems'),
  resources: (q: { systemId?: number | null; type?: ResourceType | ''; keyword?: string; page?: number; size?: number }) =>
    get<Page<ResourceView>>('/resources', clean(q)),
  createResource: (r: { moduleId: number; resourceCode: string; resourceName: string; resourceType: ResourceType;
    description?: string; actionCodes: string[] }) => post<ResourceView>('/resources', r),
  actions: () => get<ActionView[]>('/actions'),
  permissions: (q: { systemId?: number | null; resourceId?: number | null; keyword?: string; page?: number; size?: number }) =>
    get<Page<PermissionView>>('/permissions', clean(q)),
  createPermission: (resourceId: number, actionCode: string) =>
    post<PermissionView>('/permissions', { resourceId, actionCode }),
}

export const menuApi = {
  mine: () => get<MenuNode[]>('/menus/me'),
  all: () => get<MenuNode[]>('/menus'),
  create: (m: SaveMenu) => post<MenuNode>('/menus', m),
  update: (id: number, m: SaveMenu) => patch<MenuNode>(`/menus/${id}`, m),
  remove: (id: number) => del<void>(`/menus/${id}`),
}

export const orgApi = {
  companies: () => get<CompanyView[]>('/companies'),
  tree: (companyId: number) => get<DeptNode[]>('/departments/tree', { companyId }),
  importOrganization: (companyCode: string, companyName: string, departments: File, employees: File) => {
    const form = new FormData()
    form.append('departments', departments)
    form.append('employees', employees)
    return post<ImportReport>('/imports/organization', form, { params: { companyCode, companyName } })
  },
  importPermissions: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return post<ImportReport>('/imports/permissions', form)
  },
}

export interface AuditQuery {
  eventType?: string
  actor?: string
  target?: string
  result?: string
  from?: string
  to?: string
  page?: number
  size?: number
}

export const statsApi = {
  auditLogs: (q: AuditQuery) => get<Page<AuditLog>>('/audit-logs', clean(q)),
  dashboard: () => get<Dashboard>('/dashboard'),
  heatmap: (from?: string, to?: string) => get<Heatmap>('/permissions/heatmap', clean({ from, to })),
}

/** 去掉空值参数：后端把空串当作有值的过滤条件。 */
export function clean<T extends object>(q: T): Partial<T> {
  const out: Partial<T> = {}
  for (const [k, v] of Object.entries(q)) {
    if (v !== undefined && v !== null && v !== '') {
      (out as Record<string, unknown>)[k] = v
    }
  }
  return out
}
