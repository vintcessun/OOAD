<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NDropdown, NPopover } from 'naive-ui'
import { CENTER, useAuthStore } from '@/stores/auth'
import { refreshFailureRedirect } from '@/router'
import { groups, pageByPath } from '@/router/routes'
import { authApi, get } from '@/api'
import type { SystemVO } from '@/api/types'
import { useRead } from '@/views/workspace'

const auth = useAuthStore(), router = useRouter(), route = useRoute()
const compact = ref(false), accountFeedback = ref(''), refreshing = ref(false)
const { data: systems, loading: loadingSystems, error: systemsError, read, reset } = useRead<SystemVO[]>(() => [])
const navigation = computed(() => auth.navigation.flatMap(menu => pageByPath.get(menu.route!) ?? []))
const currentSystem = computed(() => systems.value.find(system => system.code === auth.targetSystem))
const systemLabel = computed(() => currentSystem.value?.name ?? (auth.targetSystem === CENTER ? '统一权限中心' : auth.targetSystem))
const accountOptions = [
  { label: '本人资料', key: 'profile' },
  { label: '修改密码', key: 'password' },
  { label: '刷新权限', key: 'refresh', disabled: false },
  { type: 'divider' as const, key: 'divider' },
  { label: '退出登录', key: 'logout' },
]
const icons: Record<string, string> = { home: '⌂', users: '♙', tree: '⋔', key: '◇', graph: '⋈', systems: '▣', shield: '◈', menu: '☷', code: '⌘', link: '↔', search: '⌕', audit: '≡' }

async function loadSystems() {
  const session = auth.session
  const loaded = await read(async () => await authApi.canCall('listSystems') ? get<SystemVO[]>('/systems') : [])
  if (loaded && session === auth.session && !systems.value.some(system => system.code === auth.targetSystem)) {
    auth.targetSystem = systems.value.find(system => system.code === CENTER)?.code ?? systems.value[0]?.code ?? CENTER
  }
}
watch(() => [route.path, route.query.system, systems.value] as const, ([path, code, options]) => {
  if (['/roles', '/hierarchy'].includes(path) && typeof code === 'string' && options.some(system => system.code === code)) {
    auth.targetSystem = code
  }
}, { immediate: true })
async function account(action: string) {
  if (action === 'profile') await router.push('/me')
  if (action === 'password') await router.push({ path: '/me', query: { password: '1' } })
  if (action === 'logout') {
    try { await auth.logout() } catch { /* 会话仍在本机清理 */ }
    await router.replace('/login')
  }
  if (action === 'refresh' && !refreshing.value) {
    refreshing.value = true
    accountFeedback.value = ''
    try {
      await auth.refresh()
      if (auth.dashboardUnavailable && route.path === '/dashboard') {
        await router.replace(refreshFailureRedirect(route.fullPath))
      } else {
        accountFeedback.value = auth.dashboardUnavailable ? '首页权限刷新失败，请重试' : '权限已刷新'
      }
    }
    catch { accountFeedback.value = '权限刷新失败，请重试' }
    finally { refreshing.value = false }
  }
}
function selectSystem(event: Event) {
  const code = (event.target as HTMLSelectElement).value
  auth.targetSystem = code
  if (['/roles', '/hierarchy'].includes(route.path) && route.query.system) {
    void router.replace({ path: route.path })
  }
}
watch(() => auth.loadedAt, value => { if (value) void loadSystems() }, { immediate: true })
watch(() => auth.session, reset)
watch(() => [auth.navigation, route.path] as const, ([, path]) => {
  if (path === '/dashboard' && auth.dashboardUnavailable) return
  if (auth.token && auth.loaded && pageByPath.has(path) && !auth.canVisit(path)) void router.replace('/403')
}, { flush: 'post' })
</script>

<template>
  <div class="shell" :class="{ 'sidebar-compact': compact }">
    <a class="skip-link" href="#main-content">跳到主要内容</a>
    <aside class="sidebar">
      <RouterLink to="/" class="brand" aria-label="统一权限中心">
        <span class="brand-mark" aria-hidden="true"><i></i><i></i><i></i></span>
        <span class="brand-name">统一权限中心<small>RBAC1</small></span>
      </RouterLink>
      <nav aria-label="主导航" class="main-nav">
        <RouterLink v-if="auth.canVisit('/dashboard')" to="/dashboard" title="首页"><span class="nav-icon" aria-hidden="true">{{ icons.home }}</span><span class="nav-label">首页</span></RouterLink>
        <RouterLink to="/me" title="本人资料"><span class="nav-icon" aria-hidden="true">◎</span><span class="nav-label">本人资料</span></RouterLink>
        <template v-for="group in groups" :key="group">
          <div v-if="navigation.some(page => page.group === group)" class="nav-group">
            <p>{{ group }}</p>
            <RouterLink v-for="page in navigation.filter(page => page.group === group)" :key="page.path" :to="page.path" :title="page.title">
              <span class="nav-icon" aria-hidden="true">{{ icons[page.icon] }}</span><span class="nav-label">{{ page.title }}</span>
            </RouterLink>
          </div>
        </template>
      </nav>
      <div class="sidebar-footer">
        <button type="button" class="collapse-button" :aria-label="compact ? '展开侧栏' : '收起侧栏'" :aria-expanded="!compact" @click="compact = !compact"><span aria-hidden="true">{{ compact ? '»' : '«' }}</span><span class="nav-label">收起侧栏</span></button>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <nav class="breadcrumbs" aria-label="面包屑">
          <RouterLink to="/">工作台</RouterLink><span aria-hidden="true">/</span>
          <template v-if="route.meta.group"><span>{{ route.meta.group }}</span><span aria-hidden="true">/</span></template>
          <span aria-current="page">{{ route.meta.title }}</span>
        </nav>
        <div class="topbar-actions">
          <div class="system-context">
            <label v-if="systems.length > 1" class="system-picker">
              <span>目标系统</span>
              <select aria-label="管理目标系统" :value="auth.targetSystem" @change="selectSystem">
                <option v-for="system in systems" :key="system.id" :value="system.code">{{ system.name }}{{ system.status === 'DISABLED' ? '（停用）' : '' }}</option>
              </select>
            </label>
            <span v-else-if="systems.length === 1" class="system-caption"><span>目标系统</span> {{ systemLabel }}</span>
            <NPopover v-if="systems.length || systemsError" trigger="click">
              <template #trigger><NButton text class="context-info" aria-label="目标系统说明">ⓘ</NButton></template>
              <p class="popover-copy">目标系统只限定管理操作的范围。登录身份始终属于统一权限中心。</p>
              <p v-if="systemsError" class="field-error">{{ systemsError }} <NButton text type="primary" :loading="loadingSystems" @click="loadSystems">重试</NButton></p>
            </NPopover>
          </div>
          <NDropdown :options="accountOptions" trigger="click" @select="account">
            <button type="button" class="account-button" aria-label="账号菜单">
              <span class="avatar" aria-hidden="true">{{ auth.identity?.name?.slice(0, 1) ?? '我' }}</span>
              <span>{{ auth.identity?.name }}</span><span aria-hidden="true">⌄</span>
            </button>
          </NDropdown>
        </div>
      </header>
      <main id="main-content" tabindex="-1">
        <p v-if="accountFeedback" class="account-feedback" role="status">{{ accountFeedback }}</p>
        <RouterView :key="route.path" />
      </main>
    </div>
  </div>
</template>
