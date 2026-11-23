import { afterEach, beforeEach, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { NDropdown } from 'naive-ui'
import { authApi } from '@/api'
import { ApiError } from '@/api/http'
import { createAppRouter, loginRedirectPath, signedInDestination } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { flattenMenus, visibleMenus } from '@/router/routes'
import type { UiResourceVO } from '@/api/types'
import AppLayout from '@/layouts/AppLayout.vue'
import StatusView from '@/views/StatusView.vue'

const menu = (id: number, route: string | null, type: 'MENU' | 'BUTTON' = 'MENU', children: UiResourceVO[] = []): UiResourceVO => ({
  id, systemCode: 'rbac-center', parentId: null, type, code: `m${id}`, name: '菜单', route, component: 'Users', sortOrder: 0, status: 'ENABLED', permissionCode: `p${id}`, children,
})
beforeEach(() => { setActivePinia(createPinia()) })
afterEach(() => { vi.restoreAllMocks() })
it('多层投影只接受启用路由及精确权限，并保留同一路由获准的其中一个入口', () => {
  const tree = [menu(1, null, 'MENU', [menu(2, '/users'), menu(3, '/x', 'BUTTON')]),
    { ...menu(4, '/roles', 'MENU', [menu(6, '/departments')]), status: 'DISABLED' as const }, menu(5, '/users'), menu(7, '/dashboard'), menu(8, '/custom')]
  expect(flattenMenus(tree)).toHaveLength(8)
  expect(visibleMenus(tree, code => ['p2', 'p3', 'p4', 'p5', 'p6', 'p8'].includes(code)).map(row => row.id)).toEqual([2])
  expect(visibleMenus(tree, code => code === 'p5').map(row => row.id)).toEqual([5])
  expect(flattenMenus([{ ...menu(9, '/unknown'), children: undefined } as unknown as UiResourceVO])).toHaveLength(1)
})
it('未登录深链返回登录，登录本身公开', async () => {
  const router = createAppRouter(); await router.push('/users?userId=5')
  expect(router.currentRoute.value.path).toBe('/login')
  expect(router.currentRoute.value.query.redirect).toBe('/users?userId=5')
  await router.push('/login'); expect(router.currentRoute.value.path).toBe('/login')
})
it('首页菜单存在时仍根据服务端仪表盘授权决定首页和深链', async () => {
  const auth = useAuthStore(); auth.setToken('dummy')
  vi.spyOn(authApi, 'me').mockResolvedValue({ subject: 'x', name: 'x', no: '', roles: [], permissions: ['p7', 'p2'] })
  vi.spyOn(authApi, 'profile').mockResolvedValue({} as Awaited<ReturnType<typeof authApi.profile>>)
  vi.spyOn(authApi, 'menus').mockResolvedValue([menu(7, '/dashboard'), menu(2, '/users')])
  const canCall = vi.spyOn(authApi, 'canCall').mockResolvedValue(false)

  const router = createAppRouter()
  await router.push('/')
  expect(router.currentRoute.value.path).toBe('/me')
  expect(auth.navigation.map(item => item.route)).toEqual(['/users'])
  await router.push('/dashboard')
  expect(router.currentRoute.value.path).toBe('/403')

  canCall.mockRejectedValue(new ApiError(403, 'FORBIDDEN', '无权访问'))
  await router.push('/dashboard')
  expect(router.currentRoute.value.path).toBe('/403')

  canCall.mockResolvedValue(true)
  await router.push('/dashboard')
  expect(router.currentRoute.value.path).toBe('/dashboard')
  expect(auth.canVisit('/dashboard')).toBe(true)
})
it.each([0, 503])('首页授权探测失败（%i）可重试且不影响其他页面', async status => {
  const auth = useAuthStore()
  auth.setToken('dummy')
  vi.spyOn(authApi, 'me').mockResolvedValue({ subject: 'x', name: 'x', no: '', roles: [], permissions: ['p7', 'p2'] })
  vi.spyOn(authApi, 'profile').mockResolvedValue({} as Awaited<ReturnType<typeof authApi.profile>>)
  vi.spyOn(authApi, 'menus').mockResolvedValue([menu(7, '/dashboard'), menu(2, '/users')])
  const canCall = vi.spyOn(authApi, 'canCall').mockRejectedValue(new ApiError(status, 'UNAVAILABLE', '无法确认首页权限'))
  const router = createAppRouter()
  const target = '/dashboard?from=login#top'

  await router.push({ path: '/login', query: { redirect: target } })
  expect(router.currentRoute.value.path).toBe('/auth-refresh-failed')
  expect(router.currentRoute.value.query.redirect).toBe(target)
  expect(auth.canVisit('/dashboard')).toBe(false)
  await router.push('/users')
  expect(router.currentRoute.value.path).toBe('/users')
  await router.push(target)
  expect(router.currentRoute.value.query.redirect).toBe(target)

  canCall.mockResolvedValue(true)
  const wrapper = mount(StatusView, { props: { status: 'refresh-failed' }, global: { plugins: [router] } })
  try {
    await wrapper.get('a').trigger('click')
    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe(target))
  } finally { wrapper.unmount() }
})
it('首页探测故障可离开首页，留在首页手动刷新时显示可重试的错误页', async () => {
  const auth = useAuthStore()
  auth.setToken('dummy')
  vi.spyOn(authApi, 'me').mockResolvedValue({ subject: 'x', name: 'x', no: '', roles: [], permissions: ['p7', 'p2'] })
  vi.spyOn(authApi, 'profile').mockResolvedValue({} as Awaited<ReturnType<typeof authApi.profile>>)
  vi.spyOn(authApi, 'menus').mockResolvedValue([menu(7, '/dashboard'), menu(2, '/users')])
  let dashboardAvailable = true
  vi.spyOn(authApi, 'canCall').mockImplementation(async operation => {
    if (operation !== 'getDashboard') return false
    if (!dashboardAvailable) throw new ApiError(503, 'UNAVAILABLE', '无法确认首页权限')
    return true
  })
  const router = createAppRouter()
  const target = '/dashboard?from=refresh#top'
  await router.push(target)
  expect(router.currentRoute.value.path).toBe('/dashboard')

  const wrapper = mount(AppLayout, { global: { plugins: [router], stubs: { RouterView: true } } })
  try {
    dashboardAvailable = false
    await router.push('/users')
    expect(router.currentRoute.value.path).toBe('/users')

    dashboardAvailable = true
    await router.push(target)
    expect(router.currentRoute.value.path).toBe('/dashboard')
    dashboardAvailable = false
    wrapper.getComponent(NDropdown).vm.$emit('select', 'refresh')
    await vi.waitFor(() => expect(router.currentRoute.value.path).toBe('/auth-refresh-failed'))
    expect(router.currentRoute.value.query.redirect).toBe(target)
  } finally { wrapper.unmount() }
})
it('菜单停用或从投影移除时，旧的精确权限不能直达页面', async () => {
  const auth = useAuthStore(); auth.setToken('dummy')
  let navigation = [menu(2, '/users'), menu(7, '/dashboard')]
  vi.spyOn(authApi, 'me').mockResolvedValue({ subject: 'x', name: 'x', no: '', roles: ['admin'], permissions: ['p2', 'p7', 'rbac:ui:roles:view'] })
  vi.spyOn(authApi, 'profile').mockResolvedValue({} as Awaited<ReturnType<typeof authApi.profile>>)
  vi.spyOn(authApi, 'menus').mockImplementation(async () => navigation)
  vi.spyOn(authApi, 'canCall').mockResolvedValue(true)
  const refresh = vi.spyOn(auth, 'refresh')
  const router = createAppRouter()
  await router.push('/users'); expect(router.currentRoute.value.path).toBe('/users')
  expect(signedInDestination(router, '/users?userId=3', auth)).toBe('/users?userId=3')
  expect(signedInDestination(router, '//example.com', auth)).toBe('/dashboard')
  expect(signedInDestination(router, '/roles', auth)).toBe('/dashboard')
  await router.push('/roles'); expect(router.currentRoute.value.path).toBe('/403')
  await router.push('/not-exist'); expect(router.currentRoute.value.path).toBe('/not-exist')
  navigation = [menu(7, '/dashboard')]
  await router.push('/users'); expect(router.currentRoute.value.path).toBe('/403')
  expect(refresh).toHaveBeenCalledTimes(3)
  await router.push('/'); expect(router.currentRoute.value.path).toBe('/dashboard')
  await router.push('/me'); auth.loadedAt = 0
  await router.push('/login'); expect(router.currentRoute.value.path).toBe('/dashboard')
})
it.each(['/users/', '/USERS'])('管理页变体 %s 沿用规范路径的菜单权限', async address => {
  const auth = useAuthStore(); auth.setToken('dummy')
  let navigation: UiResourceVO[] = []
  vi.spyOn(auth, 'refresh').mockImplementation(async () => {
    auth.identity = { subject: 'x', name: 'x', no: '', roles: [], permissions: ['p2'] }
    auth.menus = navigation; auth.loadedAt = Date.now()
  })
  const router = createAppRouter()
  const suffix = '?userId=5#details'
  await router.push(address + suffix)
  expect(router.currentRoute.value.path).toBe('/403')
  navigation = [menu(2, '/users')]
  await router.push(address + suffix)
  expect(router.currentRoute.value.fullPath).toBe('/users' + suffix)
  expect(signedInDestination(router, address + suffix, auth)).toBe(address + suffix)
})
it.each([[0, '/users'], [503, '/users/']])('权限刷新失败（%i）拦截管理页并允许重试原地址', async (status, address) => {
  const auth = useAuthStore(); auth.setToken('dummy')
  const identity = { subject: 'x', name: 'x', no: '', roles: [], permissions: ['p2'] }
  const navigation = [menu(2, '/users')]
  if (status === 503) {
    auth.identity = identity; auth.menus = navigation; auth.loadedAt = Date.now()
  }
  const refresh = vi.spyOn(auth, 'refresh')
    .mockRejectedValueOnce(new ApiError(status, 'UNAVAILABLE', '无法刷新权限'))
    .mockImplementationOnce(async () => {
      auth.identity = identity; auth.menus = navigation; auth.loadedAt = Date.now()
    })
  const router = createAppRouter()
  const target = '/users?userId=5#details'
  await router.push(`${address}?userId=5#details`)
  expect(router.currentRoute.value.path).toBe('/auth-refresh-failed')
  expect(router.currentRoute.value.query.redirect).toBe(target)
  expect(loginRedirectPath(router.currentRoute.value)).toBe(target)
  expect(refresh).toHaveBeenCalledTimes(1)

  const wrapper = mount(StatusView, { props: { status: 'refresh-failed' }, global: { plugins: [router] } })
  expect(wrapper.get('h1').text()).toBe('权限刷新失败')
  const retry = wrapper.get('a')
  expect(retry.text()).toBe('重试')
  expect(retry.attributes('href')).toBe(target)
  await retry.trigger('click')
  await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe(target))
  expect(refresh).toHaveBeenCalledTimes(2)
  wrapper.unmount()
})
it('刷新接口明确拒绝时显示 403，会话清除后返回登录', async () => {
  const auth = useAuthStore(); auth.setToken('dummy')
  vi.spyOn(auth, 'refresh')
    .mockRejectedValueOnce(new ApiError(403, 'FORBIDDEN', '无权访问'))
    .mockImplementationOnce(async () => {
      auth.clear()
      throw new ApiError(401, 'UNAUTHORIZED', '会话已过期')
    })
  const router = createAppRouter()
  await router.push('/users'); expect(router.currentRoute.value.path).toBe('/403')
  await router.push('/roles'); expect(router.currentRoute.value.path).toBe('/login')
  expect(router.currentRoute.value.query.redirect).toBe('/roles')
})
it.each([true, false])('直接打开 403 页时初始化会话（有效令牌 %s）', async valid => {
  const auth = useAuthStore(); auth.setToken('dummy')
  const refresh = vi.spyOn(auth, 'refresh').mockImplementationOnce(async () => {
    if (!valid) {
      auth.clear()
      throw new ApiError(401, 'UNAUTHORIZED', '会话已过期')
    }
    auth.identity = { subject: 'x', name: '用户', no: '', roles: [], permissions: [] }
    auth.loadedAt = Date.now()
  })
  const router = createAppRouter()
  await router.push('/403')
  expect(refresh).toHaveBeenCalledTimes(1)
  expect(auth.loaded).toBe(valid)
  expect(router.currentRoute.value.path).toBe(valid ? '/403' : '/login')
})
it.each([[401, '/login'], [503, '/auth-refresh-failed']])('直接打开权限刷新失败页时校验会话（%i）', async (status, destination) => {
  const auth = useAuthStore(); auth.setToken('dummy')
  const refresh = vi.spyOn(auth, 'refresh').mockImplementationOnce(async () => {
    if (status === 401) auth.clear()
    throw new ApiError(status, 'UNAVAILABLE', '无法刷新权限')
  })
  const router = createAppRouter()
  const target = '/users?userId=5#details'
  await router.push({ path: '/auth-refresh-failed', query: { redirect: target } })
  expect(refresh).toHaveBeenCalledTimes(1)
  expect(router.currentRoute.value.path).toBe(destination)
  expect(router.currentRoute.value.query.redirect).toBe(target)
  expect(auth.token).toBe(status === 401 ? null : 'dummy')
})
it('权限刷新失败页只允许重试内部管理路由', async () => {
  const auth = useAuthStore(); auth.setToken('dummy')
  vi.spyOn(auth, 'refresh').mockRejectedValueOnce(new ApiError(503, 'UNAVAILABLE', '无法刷新权限'))
  const router = createAppRouter()
  await router.push({ path: '/auth-refresh-failed', query: { redirect: '//example.com' } })
  expect(loginRedirectPath(router.currentRoute.value)).toBe(router.currentRoute.value.fullPath)
  const wrapper = mount(StatusView, { props: { status: 'refresh-failed' }, global: { plugins: [router] } })
  expect(wrapper.get('a').attributes('href')).toBe('/me')
  wrapper.unmount()
})
