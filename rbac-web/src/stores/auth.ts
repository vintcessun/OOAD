import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi, menuApi } from '@/api'
import type { MenuNode, UserDetail } from '@/api/types'

const TOKEN_KEY = 'rbac.token'

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

/**
 * 登录状态与权限状态（docs/05-ui.md §4.2）。
 * 前端缓存的权限只用于菜单与按钮的显示，能否真正执行由后端每次判定。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(readToken())
  const me = ref<UserDetail | null>(null)
  const menus = ref<MenuNode[]>([])
  const mustChangePwd = ref(false)
  const loadedAt = ref(0)

  const superAdmin = computed(() => me.value?.superAdmin ?? false)
  const permissions = computed(() => new Set((me.value?.permissions ?? []).map((p) => p.permissionCode)))
  const loaded = computed(() => me.value !== null)

  function has(code: string): boolean {
    return superAdmin.value || permissions.value.has(code)
  }

  function setToken(t: string | null) {
    token.value = t
    try {
      if (t) localStorage.setItem(TOKEN_KEY, t)
      else localStorage.removeItem(TOKEN_KEY)
    } catch {
      // 浏览器禁用存储时只保留在内存里
    }
  }

  async function login(username: string, password: string) {
    const r = await authApi.login(username, password)
    setToken(r.token)
    mustChangePwd.value = r.mustChangePwd
    return r
  }

  /** 重新取「我是谁、有哪些权限、能看到哪些菜单」。须改口令时菜单接口不可用，只取本人信息。 */
  async function refresh() {
    me.value = await authApi.me()
    mustChangePwd.value = me.value.user.mustChangePwd
    menus.value = mustChangePwd.value ? [] : await menuApi.mine()
    loadedAt.value = Date.now()
  }

  function clear() {
    setToken(null)
    me.value = null
    menus.value = []
    mustChangePwd.value = false
    loadedAt.value = 0
  }

  async function logout() {
    try {
      if (token.value) await authApi.logout()
    } finally {
      clear()
    }
  }

  return { token, me, menus, mustChangePwd, loadedAt, superAdmin, permissions, loaded, has, setToken, login, refresh, clear, logout }
})
