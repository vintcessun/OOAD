import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ALL_MENUS, me, menu } from './fixtures'

const api = vi.hoisted(() => ({
  authApi: { login: vi.fn(), me: vi.fn(), logout: vi.fn() },
  menuApi: { mine: vi.fn() },
  statsApi: { dashboard: vi.fn(() => new Promise(() => {})) },
}))
vi.mock('@/api', () => api)

import { buildRoutes, homePath } from '@/router/routes'
import { createAppRouter } from '@/router'
import { useAuthStore } from '@/stores/auth'

describe('路由只来自后端下发的菜单（docs/05-ui.md §4.1）', () => {
  it('按菜单注册页面，列表页带出详情页', () => {
    const names = buildRoutes(ALL_MENUS).map((r) => r.path)
    expect(names).toEqual(['/dashboard', '/users', '/users/:id', '/roles', '/roles/:id', '/permissions', '/menus',
      '/audit-logs', '/org', '/explain', '/heatmap'])
  })

  it('没有菜单的页面不注册；认不出的组件跳过', () => {
    const auditor = [menu(1, 'Dashboard', '/dashboard', 'Dashboard'),
      menu(2, '系统管理', null, null, [menu(7, '审计日志', '/audit-logs', 'AuditLog'), menu(11, '新页面', '/new', 'Unknown')])]
    expect(buildRoutes(auditor).map((r) => r.path)).toEqual(['/dashboard', '/audit-logs'])
  })

  it('落地页：第一个可见菜单；一个菜单都没有时进个人中心', () => {
    expect(homePath(ALL_MENUS)).toBe('/dashboard')
    expect(homePath([menu(2, '系统管理', null, null, [menu(7, '审计日志', '/audit-logs', 'AuditLog')])])).toBe('/audit-logs')
    expect(homePath([])).toBe('/me')
  })
})

describe('路由守卫', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('没有令牌时一律去登录页，并记下原地址', async () => {
    const router = createAppRouter()
    await router.push('/users')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/users')
  })

  it('刷新页面后先取权限、注册路由，再进入原地址；没有菜单的地址落到 403', async () => {
    api.authApi.me.mockResolvedValue(me(['rbac:dashboard:view']))
    api.menuApi.mine.mockResolvedValue([menu(1, 'Dashboard', '/dashboard', 'Dashboard')])
    useAuthStore().setToken('t')
    const router = createAppRouter()
    await router.push('/dashboard')
    expect(router.currentRoute.value.name).toBe('Dashboard')
    await router.push('/audit-logs')
    expect(router.currentRoute.value.path).toBe('/403')
    await router.push('/')
    expect(router.currentRoute.value.path).toBe('/dashboard')
  })

  it('须改口令时只能进改口令页', async () => {
    api.authApi.me.mockResolvedValue(me([], { mustChangePwd: true }))
    useAuthStore().setToken('t')
    const router = createAppRouter()
    await router.push('/me')
    expect(router.currentRoute.value.path).toBe('/password')
  })

  it('令牌已失效（取不到本人信息）时清掉令牌回登录页', async () => {
    api.authApi.me.mockRejectedValue(new Error('401'))
    const auth = useAuthStore()
    auth.setToken('stale')
    const router = createAppRouter()
    await router.push('/dashboard')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(auth.token).toBeNull()
  })
})
