import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api'
import { ApiError } from '@/api/http'
import { enabledResources, visibleMenus } from '@/router/routes'
import type { IdentityVO, MeVO, UiResourceVO } from '@/api/types'

const KEY = 'rbac.token'
export const CENTER = 'rbac-center'

function readToken() {
  try { return localStorage.getItem(KEY) } catch { return null }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(readToken())
  const identity = ref<IdentityVO | null>(null)
  const profile = ref<MeVO | null>(null)
  const menus = ref<UiResourceVO[]>([])
  const dashboardAllowed = ref<boolean | undefined>(false)
  const loadedAt = ref(0)
  const targetSystem = ref(CENTER)
  const session = ref(0)
  const loaded = computed(() => identity.value !== null)
  const permissions = computed(() => new Set(identity.value?.permissions ?? []))
  const permittedMenus = computed(() => visibleMenus(menus.value, has))
  const dashboardUnavailable = computed(() => dashboardAllowed.value === undefined
    && permittedMenus.value.some(item => item.route === '/dashboard'))
  const navigation = computed(() => permittedMenus.value
    .filter(item => item.route !== '/dashboard' || dashboardAllowed.value))
  const buttons = computed(() => new Set(enabledResources(menus.value)
    .filter(item => item.type === 'BUTTON' && has(item.permissionCode))
    .map(item => item.code)))
  let latest: { session: number; promise: Promise<void>; settled: boolean } | undefined

  function has(code: string) { return permissions.value.has(code) }
  function canVisit(path: string) { return navigation.value.some(item => item.route === path) }
  function canAct(operation: string) {
    return buttons.value.has(`rbac:ui:${operation}`)
  }
  function setToken(value: string | null) {
    if (token.value !== value) {
      session.value++
      identity.value = null
      profile.value = null
      menus.value = []
      dashboardAllowed.value = false
      loadedAt.value = 0
      targetSystem.value = CENTER
    }
    token.value = value
    try {
      if (value) localStorage.setItem(KEY, value)
      else localStorage.removeItem(KEY)
    } catch { /* 禁用存储时使用内存会话 */ }
  }
  function clear() { setToken(null) }
  function refresh(afterWrite = false): Promise<void> {
    const current = session.value
    if (!afterWrite && latest?.session === current && !latest.settled) return latest.promise
    const promise: Promise<void> = Promise.all([
      authApi.me(), authApi.profile(), authApi.menus(),
      authApi.canCall('getDashboard').catch(error => error instanceof ApiError && error.status === 403 ? false : undefined),
    ]).then(([who, mine, navigation, allowed]) => {
      if (current !== session.value) return
      if (latest?.promise !== promise) return latest?.promise
      identity.value = who
      profile.value = mine
      menus.value = navigation
      dashboardAllowed.value = allowed
      loadedAt.value = Date.now()
    }, error => {
      if (current === session.value && latest?.promise !== promise) return latest?.promise
      throw error
    }).finally(() => {
      if (latest?.promise === promise) latest.settled = true
    })
    latest = { session: current, promise, settled: false }
    return promise
  }
  async function login(username: string, password: string) {
    const result = await authApi.login(username, password)
    setToken(result.accessToken)
    try { await refresh() } catch (error) {
      if (token.value === result.accessToken) clear()
      throw error
    }
  }
  async function logout() {
    const current = session.value
    try { if (token.value) await authApi.logout() } finally {
      if (session.value === current) clear()
    }
  }
  return { token, identity, profile, menus, loadedAt, targetSystem, session, loaded, permissions, navigation, dashboardUnavailable, has, canVisit, canAct, setToken, clear, refresh, login, logout }
})
