<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NButton, NDrawer, NDrawerContent, NTag } from 'naive-ui'
import { get, post, patch, del } from '@/api'
import type { UiResourceVO } from '@/api/types'
import { CENTER } from '@/stores/auth'
import RecordForm, { type Field } from '@/components/RecordForm.vue'
import PageHeader from '@/components/PageHeader.vue'
import ReadState from '@/components/ReadState.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import StatusTag from '@/components/StatusTag.vue'
import TechText from '@/components/TechText.vue'
import WorkspaceFeedback from '@/components/WorkspaceFeedback.vue'
import { choices, flatten, label, text, useRead, useWorkspace } from './workspace'

const directory = useRead<UiResourceVO[]>(() => [])
const selectedId = ref<number | null>(null), keyword = ref('')
const collapsed = ref(new Set<number>())
const { auth, form, confirmation, error, message, stale, allowed, confirm, mutate, refresh, reset } = useWorkspace(load, () => auth.targetSystem)
const all = computed(() => flatten(directory.data.value))
const byId = computed(() => new Map(all.value.map(node => [node.id, node])))
const selected = computed(() => selectedId.value === null ? undefined : byId.value.get(selectedId.value))
const rows = computed(() => {
  const pattern = keyword.value.trim().toLowerCase()
  function visit(nodes: UiResourceVO[], depth: number): { node: UiResourceVO; depth: number }[] {
    return nodes.flatMap(node => {
      const matchingChildren = visit(node.children ?? [], depth + 1)
      if (pattern && !matchingChildren.length && ![node.name, node.code, node.route ?? ''].some(value => value.toLowerCase().includes(pattern))) return []
      return [{ node, depth }, ...(pattern || !collapsed.value.has(node.id) ? matchingChildren : [])]
    })
  }
  return visit(directory.data.value, 0)
})
function endpoint() { return `/systems/${encodeURIComponent(auth.targetSystem)}/ui-resources` }
function load() { return directory.read(() => get<UiResourceVO[]>(endpoint())) }
function toggle(id: number) {
  const next = new Set(collapsed.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsed.value = next
}
function edit(row?: UiResourceVO, parentId?: number) {
  const base = endpoint(), invalidParents = new Set(row ? [row.id, ...flatten(row.children ?? []).map(node => node.id)] : [])
  const parentOptions = all.value.filter(node => node.type === 'MENU' && !invalidParents.has(node.id)).map(node => ({ value: node.id, label: node.name + ' · ' + node.code }))
  const codeField = text('code', '资源码')
  if (row?.type === 'BUTTON' && row.systemCode === CENTER && row.code.startsWith('rbac:ui:')) {
    codeField.hint = '内置按钮的资源码对应前端操作。更换授权时请保留资源码，只修改 UI 权限码。'
  }
  const fields: Field[] = [
    choices('type', '资源类型', ['MENU', 'BUTTON']),
    { key: 'parentId', label: '上级菜单', type: 'select', options: parentOptions, hint: '按钮必须选择上级菜单' },
    text('name', '名称'), codeField, text('route', '前端路由', false), text('component', '组件标识', false),
    text('permissionCode', 'UI 权限码'), { key: 'sortOrder', label: '排序', type: 'number' },
    choices('status', '状态', ['ENABLED', 'DISABLED']),
  ]
  form.value = {
    title: row ? '编辑菜单或按钮' : '创建菜单或按钮', fields,
    initial: row ? Object.fromEntries(fields.map(field => [field.key, row[field.key as keyof UiResourceVO]])) : { type: 'MENU', status: 'ENABLED', sortOrder: 0, ...(parentId ? { parentId } : {}) },
    submit: async data => {
      if (data.type === 'BUTTON' && !data.parentId) throw new Error('按钮必须选择上级菜单。')
      await mutate(() => row ? patch(`/ui-resources/${row.id}`, data) : post(base, data))
    },
  }
}
function remove(resource: UiResourceVO) {
  confirm('删除资源', `将删除「${resource.name}」。若仍有子资源或 API 映射，系统将拒绝删除。`, async () => {
    await del(`/ui-resources/${resource.id}`)
    if (selectedId.value === resource.id) selectedId.value = null
  })
}
watch(() => auth.targetSystem, () => {
  reset(); directory.reset(); selectedId.value = null; keyword.value = ''; collapsed.value = new Set(); void load()
}, { immediate: true })
</script>

<template>
  <PageHeader title="菜单与按钮" description="配置导航和操作入口。菜单可见权限与按钮权限分别生效。">
    <template #actions><NButton v-if="allowed('createUiResource')" type="primary" @click="edit()">创建资源</NButton></template>
  </PageHeader>
  <WorkspaceFeedback :error="error" :message="message" :stale="stale" @retry="refresh" />
  <section class="surface">
    <div class="toolbar"><div class="field wide"><label for="resource-search">搜索资源</label><input id="resource-search" v-model="keyword" placeholder="名称、资源码或路由" /></div><NButton v-if="keyword" @click="keyword = ''">重置</NButton></div>
    <div class="table-scroll" tabindex="0" aria-label="菜单按钮树表"><table class="data-table"><thead><tr><th>资源名称</th><th>类型</th><th>路由 / 权限</th><th>状态</th><th>排序</th><th>操作</th></tr></thead>
      <tbody><tr v-for="{ node, depth } in rows" :key="node.id">
        <td><div class="tree-cell" :style="{ paddingInlineStart: Math.min(depth, 9) * 17 + 'px' }">
          <button v-if="node.children?.length" class="tree-toggle" type="button" :disabled="Boolean(keyword)" :aria-expanded="!collapsed.has(node.id) || Boolean(keyword)" :aria-label="(collapsed.has(node.id) ? '展开 ' : '折叠 ') + node.name" @click="toggle(node.id)">{{ collapsed.has(node.id) && !keyword ? '▸' : '▾' }}</button>
          <span v-else class="tree-spacer" aria-hidden="true"></span><button class="text-button row-title" @click="selectedId = node.id">{{ node.name }}</button>
        </div></td>
        <td><NTag size="small" :type="node.type === 'MENU' ? 'info' : 'default'">{{ label(node.type) }}</NTag></td><td><strong v-if="node.route">{{ node.route }}</strong><TechText :value="node.permissionCode" /></td><td><StatusTag :value="node.status" /></td><td>{{ node.sortOrder }}</td><td><NButton text type="primary" @click="selectedId = node.id">详情</NButton></td>
      </tr></tbody>
    </table></div>
    <ReadState :loading="directory.loading.value" :error="directory.error.value" :empty="!rows.length" :description="keyword ? '未找到匹配的资源' : '当前系统暂无资源'" @retry="load" />
  </section>
  <NDrawer :show="Boolean(selected)" :width="470" @update:show="value => { if (!value) selectedId = null }">
    <NDrawerContent v-if="selected" :title="selected.name" closable>
      <div class="row-actions"><NTag size="small">{{ label(selected.type) }}</NTag><StatusTag :value="selected.status" /></div>
      <dl class="description-grid"><div><dt>资源码</dt><dd><TechText :value="selected.code" /></dd></div><div><dt>父菜单</dt><dd>{{ selected.parentId ? byId.get(selected.parentId)?.name : '根节点' }}</dd></div><div><dt>前端路由</dt><dd>{{ selected.route ?? '—' }}</dd></div><div><dt>组件</dt><dd>{{ selected.component ?? '—' }}</dd></div><div><dt>UI 权限码</dt><dd><TechText :value="selected.permissionCode" /></dd></div><div><dt>排序</dt><dd>{{ selected.sortOrder }}</dd></div></dl>
      <template #footer><div class="row-actions"><NButton v-if="allowed('deleteUiResource')" @click="remove(selected)">删除</NButton><NButton v-if="allowed('createUiResource') && selected.type === 'MENU'" @click="edit(undefined, selected.id)">新增子资源</NButton><NButton v-if="allowed('updateUiResource')" type="primary" @click="edit(selected)">编辑</NButton></div></template>
    </NDrawerContent>
  </NDrawer>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
  <ConfirmAction v-if="confirmation" v-bind="confirmation" @close="confirmation = null" />
</template>
