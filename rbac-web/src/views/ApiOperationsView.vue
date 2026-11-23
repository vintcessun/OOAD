<script setup lang="ts">
import { ref, watch } from 'vue'
import { NButton, NTag } from 'naive-ui'
import { get, post, patch } from '@/api'
import type { ApiOperationVO, Page } from '@/api/types'
import PageHeader from '@/components/PageHeader.vue'
import Pager from '@/components/Pager.vue'
import ReadState from '@/components/ReadState.vue'
import RecordForm, { type Field } from '@/components/RecordForm.vue'
import StatusTag from '@/components/StatusTag.vue'
import TechText from '@/components/TechText.vue'
import WorkspaceFeedback from '@/components/WorkspaceFeedback.vue'
import { choices, label, text, useRead, useWorkspace } from './workspace'

const list = useRead<Page<ApiOperationVO>>(() => ({ page: 1, pageSize: 20, total: 0, items: [] })), rows = list.data
const page = ref(1), keyword = ref(''), applied = ref('')
const { auth, form, error, message, stale, allowed, mutate, refresh, reset } = useWorkspace(load, () => auth.targetSystem)
function endpoint() { return `/systems/${encodeURIComponent(auth.targetSystem)}/api-operations` }
function load() { return list.read(() => get<Page<ApiOperationVO>>(endpoint(), { keyword: applied.value || undefined, page: page.value, pageSize: 20 })) }
function changePage(next: number) { page.value = next; return load() }
function search() { applied.value = keyword.value.trim(); void changePage(1) }
function edit(row?: ApiOperationVO) {
  const base = endpoint()
  const fields: Field[] = [
    text('operationId', '操作标识'), choices('method', 'HTTP 方法', ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']),
    text('pathTemplate', '路径模板'), choices('accessPolicy', '访问策略', ['PUBLIC', 'AUTHENTICATED', 'PROTECTED']),
    text('permissionCode', 'API 权限码', false), text('requiredRole', '所需角色码', false), { key: 'enabled', label: '启用操作', type: 'checkbox' },
  ]
  form.value = {
    title: row ? '编辑 API 操作' : '登记 API 操作', fields,
    initial: row ? Object.fromEntries(fields.map(field => [field.key, row[field.key as keyof ApiOperationVO]])) : { method: 'GET', accessPolicy: 'PROTECTED', enabled: true },
    submit: data => mutate(() => row ? patch(`/api-operations/${row.id}`, data) : post(base, data)),
  }
}
watch(() => auth.targetSystem, () => { reset(); list.reset(); page.value = 1; void load() }, { immediate: true })
</script>
<template>
  <PageHeader title="API 操作" description="查找目标系统的操作、访问策略和权限要求。">
    <template #actions><NButton v-if="allowed('createApiOperation')" type="primary" @click="edit()">登记 API 操作</NButton></template>
  </PageHeader>
  <WorkspaceFeedback :error="error" :message="message" :stale="stale" @retry="refresh" />
  <section class="surface">
    <form class="toolbar" @submit.prevent="search"><div class="field wide"><label for="api-keyword">API 检索</label><input id="api-keyword" v-model="keyword" placeholder="操作标识或路径" /></div><NButton type="primary" attr-type="submit">检索</NButton><NButton @click="keyword = ''; search()">重置</NButton></form>
    <div class="table-scroll" tabindex="0" aria-label="API 操作列表"><table class="data-table api-table"><thead><tr><th>方法 / 路径</th><th>操作标识</th><th>访问策略</th><th>权限 / 身份角色</th><th>状态</th><th v-if="allowed('updateApiOperation')">操作</th></tr></thead>
      <tbody><tr v-for="row in rows.items" :key="row.id">
        <td><div class="api-path"><NTag size="small" :type="row.method === 'GET' ? 'info' : 'default'">{{ row.method }}</NTag><TechText :value="row.pathTemplate" /></div></td><td><TechText :value="row.operationId" /></td>
        <td class="one-line">{{ label(row.accessPolicy) }}</td><td><TechText v-if="row.permissionCode" :value="row.permissionCode" /><span v-else>—</span><small v-if="row.requiredRole" class="secondary-block">{{ row.requiredRole }}</small></td>
        <td><StatusTag :value="row.enabled ? 'ENABLED' : 'DISABLED'" /></td><td v-if="allowed('updateApiOperation')"><NButton text type="primary" @click="edit(row)">编辑</NButton></td>
      </tr></tbody>
    </table></div>
    <ReadState :loading="list.loading.value" :error="list.error.value" :empty="!rows.items.length" description="未找到匹配的 API 操作" @retry="load" />
    <Pager :page="page" :page-size="rows.pageSize" :total="rows.total" :loading="list.loading.value" @change="changePage" />
  </section>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
</template>
