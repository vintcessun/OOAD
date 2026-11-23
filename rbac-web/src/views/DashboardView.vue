<script setup lang="ts">
import { computed, watch } from 'vue'
import { authApi, get } from '@/api'
import type { Page, UserVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { pageByPath } from '@/router/routes'
import Notice from '@/components/Notice.vue'
import PageHeader from '@/components/PageHeader.vue'
import ReadState from '@/components/ReadState.vue'
import { useRead } from './workspace'

const auth = useAuthStore()
const links = computed(() => auth.navigation.flatMap(menu => menu.route !== '/dashboard' ? pageByPath.get(menu.route!) ?? [] : []))
const users = useRead<number | null>(() => null)
const catalog = useRead<{ roles?: number; systems?: number }>(() => ({}))
function loadUsers() {
  return users.read(async () => (await authApi.canCall('listUsers')) ? (await get<Page<UserVO>>('/users', { page: 1, pageSize: 1 })).total : null)
}
function loadCatalog() {
  return catalog.read(async () => {
    const [roles, systems] = await Promise.all([authApi.canCall('listRoles'), authApi.canCall('listSystems')])
    if (!roles && !systems) return {}
    const result = await get<{ roles: number; systems: number }>('/dashboard')
    return { roles: roles ? result.roles : undefined, systems: systems ? result.systems : undefined }
  })
}
watch(() => auth.loadedAt, time => {
  if (!time) return
  users.reset(); catalog.reset()
  if (!auth.canVisit('/dashboard')) return
  void loadUsers(); void loadCatalog()
}, { immediate: true })
</script>

<template>
  <PageHeader title="工作台" :description="`你好，${auth.identity?.name ?? '欢迎回来'}`" />
  <div class="dashboard-grid">
    <section v-if="users.data.value !== null || Object.values(catalog.data.value).some(value => value !== undefined) || users.loading.value || catalog.loading.value || users.error.value || catalog.error.value" class="dashboard-section">
      <h2>管理概览</h2>
      <div class="counts">
        <article v-if="users.data.value !== null"><h3>可管理用户</h3><strong>{{ users.data.value.toLocaleString('zh-CN') }}</strong><span>当前部门及下级部门</span></article>
        <article v-if="catalog.data.value.roles !== undefined"><h3>已登记角色</h3><strong>{{ catalog.data.value.roles.toLocaleString('zh-CN') }}</strong><span>全部管理系统</span></article>
        <article v-if="catalog.data.value.systems !== undefined"><h3>已登记系统</h3><strong>{{ catalog.data.value.systems.toLocaleString('zh-CN') }}</strong><span>系统登记目录</span></article>
      </div>
      <ReadState :loading="users.loading.value" :error="users.error.value" @retry="loadUsers" />
      <ReadState :loading="catalog.loading.value" :error="catalog.error.value" @retry="loadCatalog" />
    </section>
    <section class="dashboard-section">
      <h2>工作入口</h2>
      <div class="quick-links">
        <RouterLink to="/me"><span>本人资料</span><span aria-hidden="true">→</span></RouterLink>
        <RouterLink v-for="page in links" :key="page.path" :to="page.path"><span>{{ page.title }}</span><span aria-hidden="true">→</span></RouterLink>
      </div>
    </section>
    <Notice v-if="!links.length && !users.loading.value && !catalog.loading.value" type="info">可从本人资料查看当前账号及有效权限。</Notice>
  </div>
</template>
