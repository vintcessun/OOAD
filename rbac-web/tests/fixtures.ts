import type { MenuNode, UserDetail } from '@/api/types'

export function menu(id: number, name: string, routePath: string | null, componentKey: string | null,
  children: MenuNode[] = []): MenuNode {
  return { id, parentId: null, menuName: name, routePath, componentKey, icon: null, sortOrder: id, resourceId: null,
    resourceCode: null, children }
}

/** 与 V1 内置菜单相同的结构 */
export const ALL_MENUS: MenuNode[] = [
  menu(1, 'Dashboard', '/dashboard', 'Dashboard'),
  menu(2, '系统管理', null, null, [
    menu(3, '用户管理', '/users', 'UserList'),
    menu(4, '角色管理', '/roles', 'RoleList'),
    menu(5, '权限管理', '/permissions', 'Permission'),
    menu(6, '菜单管理', '/menus', 'MenuList'),
    menu(7, '审计日志', '/audit-logs', 'AuditLog'),
    menu(8, '组织管理', '/org', 'OrgTree'),
  ]),
  menu(9, '权限解释', '/explain', 'Explain'),
  menu(10, '权限使用热力图', '/heatmap', 'Heatmap'),
]

export function me(permissions: string[], opts: { superAdmin?: boolean; mustChangePwd?: boolean } = {}): UserDetail {
  return {
    user: {
      id: 9, username: 'u9', realName: '测试员', userType: 'EMPLOYEE', employeeNo: 'u9', phone: '13812345678',
      position: null, status: 'ACTIVE', statusReason: null, companyId: 2, departmentId: 5, departmentName: '财务部',
      mustChangePwd: opts.mustChangePwd ?? false, createdAt: '2026-10-11T10:00:00',
    },
    roles: [],
    superAdmin: opts.superAdmin ?? false,
    permissions: permissions.map((p) => ({ permissionCode: p, roleCode: 'R', roleName: '角色', scopeDeptId: 5,
      scopeDeptName: '财务部', selfOnly: false })),
  }
}
