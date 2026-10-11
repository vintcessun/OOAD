import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as Icons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import './styles.css'
import App from './App.vue'
import { createAppRouter } from './router'
import { setAuthHooks } from './api/http'
import { useAuthStore } from './stores/auth'
import { vPerm } from './directives/perm'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
const router = createAppRouter()
app.use(router)
app.use(ElementPlus, { locale: zhCn })
for (const [name, comp] of Object.entries(Icons)) {
  app.component(name, comp)
}
app.directive('perm', vPerm)

// 统一错误处理的副作用（决策表见 api/http.ts classifyError）
const auth = useAuthStore()
setAuthHooks({
  getToken: () => auth.token,
  onUnauthenticated: () => {
    auth.clear()
    const here = router.currentRoute.value
    if (here.path !== '/login') router.push({ path: '/login', query: { redirect: here.fullPath } })
  },
  onPasswordRequired: () => {
    auth.mustChangePwd = true
    router.push('/password')
  },
  onForbidden: () => {
    // 前端缓存的权限过期了：下一次路由切换时重新拉取
    auth.loadedAt = 0
  },
})

app.mount('#app')
