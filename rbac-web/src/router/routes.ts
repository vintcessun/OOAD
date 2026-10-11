import type { RouteComponent } from 'vue-router'
import type { MenuNode } from '@/api/types'

/** 菜单的 componentKey → 页面组件（与 V1 脚本内置的 sys_menu.component_key 一致）。 */
export const COMPONENTS: Record<string, () => Promise<RouteComponent>> = {
  Dashboard: () => import('@/views/DashboardView.vue'),
  UserList: () => import('@/views/UserListView.vue'),
  RoleList: () => import('@/views/RoleListView.vue'),
  Permission: () => import('@/views/PermissionView.vue'),
  MenuList: () => import('@/views/MenuListView.vue'),
  AuditLog: () => import('@/views/AuditLogView.vue'),
  OrgTree: () => import('@/views/OrgView.vue'),
  Explain: () => import('@/views/ExplainView.vue'),
  Heatmap: () => import('@/views/HeatmapView.vue'),
}

/** 列表页带出的详情页：有列表页的菜单，才注册对应的详情页。 */
const DETAILS: Record<string, { path: string; name: string; title: string; load: () => Promise<RouteComponent> }> = {
  UserList: { path: '/users/:id', name: 'UserDetail', title: '用户详情', load: () => import('@/views/UserDetailView.vue') },
  RoleList: { path: '/roles/:id', name: 'RoleDetail', title: '角色详情', load: () => import('@/views/RoleDetailView.vue') },
}

export interface DynamicRoute {
  path: string
  name: string
  title: string
  componentKey: string
  load: () => Promise<RouteComponent>
}

/**
 * 从 GET /menus/me 返回的菜单树得出要注册的路由（docs/05-ui.md §4.1「路由级」）。
 * 路由只来自后端下发的菜单：没有菜单的页面根本不注册，直接输入地址会落到 403 页。
 * 认不出的 componentKey 跳过（新菜单可能先于前端页面上线）。
 */
export function buildRoutes(menus: MenuNode[]): DynamicRoute[] {
  const out: DynamicRoute[] = []
  const walk = (nodes: MenuNode[]) => {
    for (const m of nodes) {
      if (m.routePath && m.componentKey && COMPONENTS[m.componentKey]) {
        out.push({ path: m.routePath, name: m.componentKey, title: m.menuName, componentKey: m.componentKey,
          load: COMPONENTS[m.componentKey] })
        const d = DETAILS[m.componentKey]
        if (d) {
          out.push({ path: d.path, name: d.name, title: d.title, componentKey: m.componentKey, load: d.load })
        }
      }
      walk(m.children ?? [])
    }
  }
  walk(menus)
  return out
}

/** 登录后的落地页：第一个可见的叶子菜单；一个菜单都没有的普通员工落在个人中心。 */
export function homePath(menus: MenuNode[]): string {
  for (const m of menus) {
    if (m.routePath && m.componentKey && COMPONENTS[m.componentKey]) return m.routePath
    const child = homePath(m.children ?? [])
    if (child !== '/me') return child
  }
  return '/me'
}
