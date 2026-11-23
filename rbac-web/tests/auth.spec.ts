import { beforeEach, describe, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api'
import type { IdentityVO, MeVO, UiResourceVO } from '@/api/types'
vi.mock('@/api', () => ({ authApi: { login: vi.fn(), logout: vi.fn(), me: vi.fn(), profile: vi.fn(), menus: vi.fn(), canCall: vi.fn() } }))
const who: IdentityVO = { subject: 'rbac-u1', roles: ['admin'], permissions: ['rbac:ui:users:view'], name: '虚构人员', no: 'test' }
const mine = { user: { name: '虚构人员', departmentName: '总部' }, effective: { directRoles: [], effectiveRoles: [], permissions: [], sources: [] } } as unknown as MeVO
beforeEach(() => {
  setActivePinia(createPinia()); vi.resetAllMocks(); vi.mocked(authApi.me).mockResolvedValue(who)
  vi.mocked(authApi.profile).mockResolvedValue(mine); vi.mocked(authApi.menus).mockResolvedValue([])
  vi.mocked(authApi.canCall).mockResolvedValue(false)
})
describe('身份与权限状态', () => {
  it('没有身份默认无权，admin 不能通配，登录读取身份和授权', async () => {
    const store = useAuthStore(); expect(store.loaded).toBe(false); expect(store.has('any')).toBe(false)
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'dummy', tokenType: 'Bearer', expiresIn: 600 })
    await store.login('test', 'not-real')
    expect(authApi.login).toHaveBeenCalledWith('test', 'not-real')
    expect(store.token).toBe('dummy'); expect(localStorage.getItem('rbac.token')).toBe('dummy')
    expect(store.profile).toEqual(mine); expect(store.loaded).toBe(true)
    expect(store.has('rbac:ui:users:view')).toBe(true); expect(store.has('rbac:ui:users')).toBe(false); expect(store.has('*')).toBe(false)
    expect(store.targetSystem).toBe('rbac-center')
    store.targetSystem = 'course-project-management'; expect(store.token).toBe('dummy')
  })
  it('授权刷新替换菜单与权限而不是并集', async () => {
    const store = useAuthStore(); await store.refresh()
    vi.mocked(authApi.me).mockResolvedValue({ ...who, permissions: [] })
    vi.mocked(authApi.menus).mockResolvedValue([{ id: 8 }] as UiResourceVO[])
    await store.refresh(); expect(store.has('rbac:ui:users:view')).toBe(false); expect(store.menus).toEqual([{ id: 8 }])
  })
  it('按按钮资源码定位操作并使用重新绑定的 UI 权限', async () => {
    const store = useAuthStore()
    const code = 'rbac:ui:createUser'
    const rebound = 'rbac:ui:updateUser'
    const button = {
      id: 12, type: 'BUTTON', status: 'ENABLED', code, permissionCode: code,
    } as UiResourceVO
    vi.mocked(authApi.me).mockResolvedValue({ ...who, permissions: [code] })
    vi.mocked(authApi.menus).mockResolvedValue([button])
    await store.refresh()
    expect(store.canAct('createUser')).toBe(true)

    vi.mocked(authApi.menus).mockResolvedValue([{ ...button, permissionCode: rebound }])
    await store.refresh()
    expect(store.canAct('createUser')).toBe(false)

    vi.mocked(authApi.me).mockResolvedValue({ ...who, permissions: [rebound] })
    await store.refresh()
    expect(store.canAct('createUser')).toBe(true)
    expect(store.canAct('updateUser')).toBe(false)

    vi.mocked(authApi.menus).mockResolvedValue([{ ...button, permissionCode: rebound, status: 'DISABLED' }])
    await store.refresh()
    expect(store.canAct('createUser')).toBe(false)
  })
  it('登录失败不制造会话，logout 即使网络失败也清理权限', async () => {
    const store = useAuthStore(); vi.mocked(authApi.login).mockRejectedValue(new Error('凭据错误'))
    await expect(store.login('x', 'x')).rejects.toThrow('凭据错误'); expect(store.token).toBeNull()
    store.setToken('dummy'); await store.refresh(); vi.mocked(authApi.logout).mockRejectedValue(new Error('offline'))
    await expect(store.logout()).rejects.toThrow(); expect(store.token).toBeNull(); expect(store.profile).toBeNull(); expect(store.identity).toBeNull(); expect(store.menus).toEqual([]); expect(store.loadedAt).toBe(0)
    await store.logout(); expect(authApi.logout).toHaveBeenCalledTimes(1)
  })
  it('存储被禁用仍可用内存会话，已有 token 能恢复', () => {
    localStorage.setItem('rbac.token', 'restored'); expect(useAuthStore().token).toBe('restored')
    const get = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('storage') })
    const set = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('storage') })
    setActivePinia(createPinia()); const store = useAuthStore(); expect(store.token).toBeNull(); store.setToken('memory'); expect(store.token).toBe('memory'); store.clear()
    get.mockRestore(); set.mockRestore()
  })
  it('同会话合并刷新，旧会话的响应不能写入新会话', async () => {
    const store = useAuthStore()
    store.setToken('first')
    let finish!: (identity: IdentityVO) => void
    vi.mocked(authApi.me).mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    const old = store.refresh()
    const duplicate = store.refresh()
    expect(authApi.me).toHaveBeenCalledTimes(1)
    store.targetSystem = 'course-project-management'
    store.setToken('new')
    await store.refresh()
    finish({ ...who, name: '旧会话' })
    await Promise.all([old, duplicate])
    expect(store.identity?.name).toBe(who.name)
    expect(store.targetSystem).toBe('rbac-center')
  })
  it('写后刷新重新读取，同会话的旧响应不能覆盖新权限和菜单', async () => {
    const store = useAuthStore()
    store.setToken('dummy')
    const dashboardPermission = 'rbac:ui:dashboard:view'
    const dashboard = {
      id: 8, type: 'MENU', status: 'ENABLED', route: '/dashboard', permissionCode: dashboardPermission,
    } as UiResourceVO
    let finishOld!: (identity: IdentityVO) => void
    vi.mocked(authApi.me).mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    vi.mocked(authApi.menus).mockResolvedValueOnce([{ id: 8 }] as UiResourceVO[])
    vi.mocked(authApi.canCall).mockResolvedValueOnce(true)
    const old = store.refresh()
    vi.mocked(authApi.me).mockResolvedValue({ ...who, permissions: [dashboardPermission] })
    vi.mocked(authApi.menus).mockResolvedValue([dashboard])
    const fresh = store.refresh(true)
    const duplicate = store.refresh()
    expect(authApi.me).toHaveBeenCalledTimes(2)
    await Promise.all([fresh, duplicate])
    finishOld(who)
    await old
    expect(store.has('rbac:ui:users:view')).toBe(false)
    expect(store.menus).toEqual([dashboard])
    expect(store.canVisit('/dashboard')).toBe(false)
  })
  it('写后刷新成功后，同会话的旧请求失败也采用新结果', async () => {
    const store = useAuthStore()
    store.setToken('dummy')
    let failOld!: (reason: Error) => void
    vi.mocked(authApi.me).mockImplementationOnce(() => new Promise((_, reject) => { failOld = reject }))
    vi.mocked(authApi.menus).mockResolvedValueOnce([{ id: 8 }] as UiResourceVO[])
    const old = store.refresh()
    vi.mocked(authApi.me).mockResolvedValue({ ...who, permissions: [] })
    await store.refresh(true)

    failOld(new Error('旧权限请求失败'))

    await expect(old).resolves.toBeUndefined()
    expect(store.has('rbac:ui:users:view')).toBe(false)
    expect(store.menus).toEqual([])
    expect(store.loaded).toBe(true)
  })
  it('会话初始化失败时清理刚获取的令牌', async () => {
    const store = useAuthStore()
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'dummy', tokenType: 'Bearer', expiresIn: 600 })
    vi.mocked(authApi.menus).mockRejectedValue(new Error('offline'))
    await expect(store.login('x', 'x')).rejects.toThrow('offline')
    expect(store.token).toBeNull()
  })
})
