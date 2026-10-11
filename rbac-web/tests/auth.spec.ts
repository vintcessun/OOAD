import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ALL_MENUS, me } from './fixtures'

const api = vi.hoisted(() => ({
  authApi: { login: vi.fn(), me: vi.fn(), logout: vi.fn() },
  menuApi: { mine: vi.fn() },
}))
vi.mock('@/api', () => api)

import { useAuthStore } from '@/stores/auth'

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  vi.clearAllMocks()
})

describe('登录状态与权限状态', () => {
  it('has() 按 /auth/me 的有效权限判断；超级管理员一律为真', async () => {
    api.authApi.me.mockResolvedValue(me(['rbac:user:view']))
    api.menuApi.mine.mockResolvedValue(ALL_MENUS)
    const auth = useAuthStore()
    await auth.refresh()
    expect(auth.has('rbac:user:view')).toBe(true)
    expect(auth.has('rbac:user:delete')).toBe(false)
    expect(auth.menus).toHaveLength(4)

    api.authApi.me.mockResolvedValue(me([], { superAdmin: true }))
    await auth.refresh()
    expect(auth.has('anything:at:all')).toBe(true)
  })

  it('登录把令牌存起来；须改口令时不取菜单（后端只放行本人与改口令接口）', async () => {
    api.authApi.login.mockResolvedValue({ token: 't1', mustChangePwd: true })
    api.authApi.me.mockResolvedValue(me([], { mustChangePwd: true }))
    const auth = useAuthStore()
    await auth.login('u9', 'x')
    expect(localStorage.getItem('rbac.token')).toBe('t1')
    expect(auth.mustChangePwd).toBe(true)
    await auth.refresh()
    expect(api.menuApi.mine).not.toHaveBeenCalled()
    expect(auth.menus).toEqual([])
  })

  it('登出通知后端注销令牌，并清空本地状态；后端失败也照样清空', async () => {
    const auth = useAuthStore()
    auth.setToken('t2')
    api.authApi.logout.mockRejectedValue(new Error('down'))
    await expect(auth.logout()).rejects.toThrow('down')
    expect(auth.token).toBeNull()
    expect(localStorage.getItem('rbac.token')).toBeNull()
    expect(auth.loaded).toBe(false)
  })
})
