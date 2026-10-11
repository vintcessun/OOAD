import { createRouter, createWebHistory, type Router, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { buildRoutes, homePath } from './routes'

const PUBLIC = new Set(['/login'])
/** 首次登录改口令之前只能进的页面（后端同样只放行这几个接口） */
const BEFORE_PASSWORD = new Set(['/password', '/login'])

const staticRoutes: RouteRecordRaw[] = [
  { path: '/login', name: 'Login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
  { path: '/password', name: 'Password', component: () => import('@/views/PasswordView.vue'), meta: { title: '修改口令' } },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      { path: 'me', name: 'Me', component: () => import('@/views/MeView.vue'), meta: { title: '个人中心' } },
      { path: '403', name: 'Forbidden', component: () => import('@/views/ForbiddenView.vue'), meta: { title: '无权限' } },
    ],
  },
]

let registered: string[] = []

/** 按菜单注册页面路由。每次刷新权限后重建：被收回的菜单对应的路由随之移除。 */
export function syncRoutes(router: Router) {
  const auth = useAuthStore()
  registered.forEach((name) => router.hasRoute(name) && router.removeRoute(name))
  registered = []
  for (const r of buildRoutes(auth.menus)) {
    router.addRoute('Layout', { path: r.path, name: r.name, component: r.load, meta: { title: r.title } })
    registered.push(r.name)
  }
  if (!router.hasRoute('CatchAll')) {
    router.addRoute({ path: '/:pathMatch(.*)*', name: 'CatchAll', redirect: '/403' })
  }
}

export function createAppRouter(): Router {
  const router = createRouter({ history: createWebHistory(), routes: staticRoutes })

  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    if (!auth.token) {
      return PUBLIC.has(to.path) ? true : { path: '/login', query: { redirect: to.fullPath } }
    }
    if (!auth.loaded || Date.now() - auth.loadedAt > 60_000) {
      // 刷新页面（F5）后 store 是空的：先取权限、注册路由，再重新匹配一次
      const firstLoad = !auth.loaded
      try {
        await auth.refresh()
      } catch {
        auth.clear()
        return { path: '/login', query: { redirect: to.fullPath } }
      }
      syncRoutes(router)
      if (firstLoad) return { path: to.path, query: to.query, hash: to.hash, replace: true }
    }
    if (auth.mustChangePwd && !BEFORE_PASSWORD.has(to.path)) {
      return '/password'
    }
    if (to.path === '/login') return homePath(auth.menus)
    if (to.path === '/') return homePath(auth.menus)
    return true
  })

  router.afterEach((to) => {
    document.title = `${(to.meta.title as string) ?? ''} · 统一授权中心`
  })
  return router
}
