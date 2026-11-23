/** 冻结实施合同的 HTTP 输出；身份协议不使用 Envelope。 */
export interface Envelope<T> { code: string; message: string; data: T; timestamp: string }
export interface Page<T> { page: number; pageSize: number; total: number; items: T[] }
export type Status = 'ENABLED' | 'DISABLED'
export interface SystemVO { id: number; code: string; name: string; status: Status }
export interface DepartmentVO { id: number; parentId: number | null; code: string; name: string; path: string; status: Status; children: DepartmentVO[] }
export interface UserVO { id: number; subject: string; username: string; name: string; no: string | null; phone: string | null; departmentId: number; departmentName: string; status: 'ACTIVE' | 'DISABLED' | 'DELETED'; version: number }
export interface RoleVO { id: number; systemCode: string; code: string; name: string; description: string | null; status: Status; builtin: boolean; version: number }
export interface PermissionVO { id: number; systemCode: string; code: string; name: string; kind: 'UI' | 'API'; status: Status }
export interface AssignmentVO { id: number; userId: number; roleId: number; systemCode: string; roleCode: string; roleName: string; assignedAt: string; assignedBy: number }
export interface EffectiveVO { directRoles: RoleVO[]; effectiveRoles: RoleVO[]; permissions: PermissionVO[]; sources: { permissionCode: string; permissionKind: PermissionVO['kind']; roleCode: string; inherited: boolean; path: string[] }[] }
export interface UiResourceVO { id: number; systemCode: string; parentId: number | null; type: 'MENU' | 'BUTTON'; code: string; name: string; route: string | null; component: string | null; sortOrder: number; status: Status; permissionCode: string; children: UiResourceVO[] }
export interface ApiOperationVO { id: number; systemCode: string; operationId: string; method: string; pathTemplate: string; permissionCode: string | null; requiredRole: string | null; accessPolicy: 'PUBLIC' | 'AUTHENTICATED' | 'PROTECTED'; enabled: boolean }
export interface MappingVO { uiResourceId: number; apiOperationId: number }
export interface CoverageVO { mappings: MappingVO[]; unmappedUi: UiResourceVO[]; unmappedApi: ApiOperationVO[] }
export interface HierarchyVO { roles: RoleVO[]; edges: { seniorRoleId: number; juniorRoleId: number }[] }
export interface IdentityVO { subject: string; roles: ('student' | 'teacher' | 'admin')[]; permissions: string[]; name: string; no: string }
export interface MeVO { user: UserVO; effective: EffectiveVO }
export interface AuditVO { id: number; at: string; actor: { id: number | null; name: string; roles: string[]; deleted: boolean }; type: string; target: string; before: unknown; after: unknown; objectType?: string | null; objectId?: string | null }
