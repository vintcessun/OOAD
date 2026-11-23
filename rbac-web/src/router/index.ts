import { createRouter, createWebHistory, type RouteLocationNormalized, type Router } from 'vue-router'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { homePath, managementRetryPath, pageByPath, pages } from './routes'

const refreshFailurePath = '/auth-refresh-failed'

export function refreshFailureRedirect(fullPath: string) {
  return { path: refreshFailurePath, query: { redirect: fullPath } }
}

export function loginRedirectPath(route: RouteLocationNormalized) {
  const retry = route.matched.at(-1)?.path === refreshFailurePath
    ? managementRetryPath(route.query.redirect)
    : undefined
  return retry ?? route.fullPath
}

export function signedInDestination(router: Router, redirect: unknown, auth: ReturnType<typeof useAuthStore>) {
  if (typeof redirect === 'string' && /^\/(?![\/\\])/.test(redirect)) {
    const target = router.resolve(redirect)
    const matchedPath = target.matched.at(-1)?.path
    if (target.path === '/me' || (matchedPath && auth.canVisit(matchedPath))
        || (matchedPath === '/dashboard' && auth.dashboardUnavailable)) return target.fullPath
  }
  return homePath(auth.navigation)
}

export function createAppRouter() {
  const router = createRouter({ history: createWebHistory(), routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
    { path: '/', component: () => import('@/layouts/AppLayout.vue'), children: [
      { path: '', component: () => import('@/views/MeView.vue') },
      { path: 'me', component: () => import('@/views/MeView.vue'), meta: { title: '本人资料' } },
      ...pages.map(page => ({ path: page.path, component: page.view, meta: { title: page.title, group: page.group } })),
      { path: '/403', component: () => import('@/views/StatusView.vue'), props: { status: 403 }, meta: { title: '暂无访问权限' } },
      { path: refreshFailurePath, component: () => import('@/views/StatusView.vue'), props: { status: 'refresh-failed' }, meta: { title: '权限刷新失败' } },
      { path: '/:pathMatch(.*)*', component: () => import('@/views/StatusView.vue'), props: { status: 404 }, meta: { title: '页面未找到' } },
    ] },
  ] })
  router.beforeEach(async (to, from) => {
    const matchedPath = to.matched.at(-1)?.path
    if (matchedPath && matchedPath !== to.path && pageByPath.has(matchedPath)) {
      return { path: matchedPath, query: to.query, hash: to.hash }
    }
    const auth = useAuthStore()
    const refreshFailed = matchedPath === refreshFailurePath
    const login = { path: '/login', query: { redirect: loginRedirectPath(to) } }
    if (!auth.token) return to.path === '/login' ? true : login
    if ((refreshFailed || to.path === '/403') && to.redirectedFrom) return true
    const management = pageByPath.has(to.path)
    if (!auth.loaded || Date.now() - auth.loadedAt > 30000 || (management && to.path !== from.path)) {
      try {
        await auth.refresh()
      } catch (error) {
        if (auth.token && management) {
          if (error instanceof ApiError && error.status === 403) return '/403'
          return refreshFailureRedirect(to.fullPath)
        }
        if (auth.token && (auth.loaded || refreshFailed)) return true
        return to.path === '/login' ? true : login
      }
    }
    if (!auth.token) return to.path === '/login' ? true : '/login'
    if (to.path === '/login' || to.path === '/') return signedInDestination(router, to.query.redirect, auth)
    if (to.path === '/dashboard' && auth.dashboardUnavailable) return refreshFailureRedirect(to.fullPath)
    if (management && !auth.canVisit(to.path)) return '/403'
    return true
  })
  router.afterEach(to => { document.title = `${String(to.meta.title ?? '统一权限中心')} · RBAC1` })
  return router
}
