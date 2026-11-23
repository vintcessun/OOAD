<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NButton, NSelect, NTag } from 'naive-ui'
import { get, put } from '@/api'
import type { ApiOperationVO, CoverageVO, MappingVO, Page, UiResourceVO } from '@/api/types'
import Notice from '@/components/Notice.vue'
import PageHeader from '@/components/PageHeader.vue'
import Pager from '@/components/Pager.vue'
import ReadState from '@/components/ReadState.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import TechText from '@/components/TechText.vue'
import WorkspaceFeedback from '@/components/WorkspaceFeedback.vue'
import { flatten, sameIds, useRead, useWorkspace } from './workspace'

const tree = useRead<UiResourceVO[]>(() => [])
const report = useRead<CoverageVO>(() => ({ mappings: [], unmappedUi: [], unmappedApi: [] })), coverage = report.data
const api = useRead<Page<ApiOperationVO>>(() => ({ page: 1, pageSize: 20, total: 0, items: [] }))
const page = ref(1), query = ref(''), keyword = ref(''), selectedUi = ref<number | null>(null), draft = ref<number[]>([])
const knownApi = ref(new Map<number, ApiOperationVO>())
const selectedApi = useRead<ApiOperationVO[]>(() => [])
const selectedApiById = computed(() => new Map(selectedApi.data.value.map(operation => [operation.id, operation])))
const uiOptions = computed(() => flatten(tree.data.value).map(resource => ({ value: resource.id, label: resource.name + ' · ' + resource.code })))
const selectedName = computed(() => uiOptions.value.find(resource => resource.value === selectedUi.value)?.label)
const baseline = computed(() => idsFor(coverage.value.mappings, selectedUi.value))
const added = computed(() => draft.value.filter(id => !baseline.value.includes(id)))
const removed = computed(() => baseline.value.filter(id => !draft.value.includes(id)))
const dirty = computed(() => added.value.length + removed.value.length > 0)
const { auth, confirmation, error, message, stale, busy, allowed, confirm, refresh, reset } = useWorkspace(reload, () => `${auth.targetSystem}:${selectedUi.value}`)
const base = () => `/systems/${encodeURIComponent(auth.targetSystem)}`
function idsFor(mappings: MappingVO[], id: number | null) {
  return mappings.filter(mapping => mapping.uiResourceId === id).map(mapping => mapping.apiOperationId)
}
function name(id: number) {
  return knownApi.value.get(id)?.operationId ?? selectedApiById.value.get(id)?.operationId ?? 'API #' + id
}
async function loadSelectedApi() {
  selectedApi.reset()
  const ids = baseline.value
  const missing = new Set(ids.filter(id => !knownApi.value.has(id)))
  if (!missing.size) return

  const path = base() + '/api-operations'
  const pageSize = 100
  await selectedApi.read(async () => {
    const found: ApiOperationVO[] = []
    for (let currentPage = 1; ; currentPage++) {
      const batch = await get<Page<ApiOperationVO>>(path, { page: currentPage, pageSize })
      if (path !== base() + '/api-operations' || ids !== baseline.value) break
      for (const operation of batch.items) {
        if (missing.delete(operation.id)) found.push(operation)
      }
      if (!missing.size || batch.page * batch.pageSize >= batch.total) break
    }
    return found
  })
}
async function loadReport() {
  const system = auth.targetSystem
  const loaded = await report.read(() => get<CoverageVO>(base() + '/ui-api-mappings'))
  if (loaded && system === auth.targetSystem) {
    draft.value = [...baseline.value]
    remember(coverage.value.unmappedApi)
  }
  return loaded
}
function loadTree() { return tree.read(() => get<UiResourceVO[]>(base() + '/ui-resources')) }
function remember(items: ApiOperationVO[]) {
  knownApi.value = new Map([...knownApi.value, ...items.map(operation => [operation.id, operation] as const)])
}
async function loadApi() {
  const system = auth.targetSystem
  const loaded = await api.read(() => get<Page<ApiOperationVO>>(base() + '/api-operations', { keyword: keyword.value || undefined, page: page.value, pageSize: 20 }))
  if (loaded && system === auth.targetSystem) remember(api.data.value.items)
  return loaded
}
async function reload() {
  const results = await Promise.all([loadTree(), loadReport(), loadApi()])
  return results.every(Boolean)
}
function changePage(value: number) { page.value = value; return loadApi() }
function search() { keyword.value = query.value.trim(); void changePage(1) }
function selectUi(id: number | null) {
  selectedUi.value = id
  draft.value = [...baseline.value]
  confirmation.value = null
}
function toggle(id: number, checked: boolean) {
  draft.value = checked ? [...draft.value, id] : draft.value.filter(value => value !== id)
}
function locate(operation: ApiOperationVO) {
  query.value = operation.operationId
  search()
  document.getElementById('mapping-search')?.focus()
}
function save() {
  const id = selectedUi.value, path = base() + '/ui-api-mappings', before = [...baseline.value], after = [...draft.value]
  if (id === null || !dirty.value) return
  confirm('保存映射', `将为「${selectedName.value}」新增 ${added.value.length} 条映射、移除 ${removed.value.length} 条映射。映射用于覆盖追踪。`, async () => {
    const latest = await get<CoverageVO>(path)
    if (id !== selectedUi.value || path !== base() + '/ui-api-mappings') throw new Error('管理范围已切换，请重新确认。')
    if (!sameIds(idsFor(latest.mappings, id), before)) {
      coverage.value = latest
      remember(latest.unmappedApi)
      throw new Error('该资源的映射已更新。草稿仍保留，关闭确认框并核对差异后可重新保存。')
    }
    const mappings = [...latest.mappings.filter(mapping => mapping.uiResourceId !== id), ...after.map(apiOperationId => ({ uiResourceId: id, apiOperationId }))]
    await put(path, { mappings })
  })
}
watch(baseline, () => { void loadSelectedApi() })
watch(() => auth.targetSystem, () => {
  reset(); tree.reset(); report.reset(); api.reset(); selectedApi.reset(); knownApi.value = new Map()
  page.value = 1; query.value = ''; keyword.value = ''; selectUi(null)
  void reload()
}, { immediate: true })
</script>
<template>
  <PageHeader title="UI ↔ API 映射" description="记录页面与接口的对应关系，用于追踪覆盖情况。API 的调用权限由独立授权决定。" />
  <WorkspaceFeedback :error="error" :message="message" :stale="stale" @retry="refresh" />
  <div class="mapping-grid">
    <section class="surface padded">
      <div class="field"><label for="mapping-ui">UI 资源</label><NSelect id="mapping-ui" :value="selectedUi" aria-label="UI 资源" :options="uiOptions" :loading="tree.loading.value" filterable clearable placeholder="搜索并选择菜单或按钮" @update:value="selectUi" /></div>
      <ReadState :loading="tree.loading.value" :error="tree.error.value" @retry="loadTree" />
      <form class="toolbar" @submit.prevent="search"><div class="field wide"><label for="mapping-search">查找 API 操作</label><input id="mapping-search" v-model="query" placeholder="操作标识或路径" /></div><NButton attr-type="submit">检索</NButton><NButton v-if="query" @click="query = ''; search()">重置</NButton></form>
      <Notice v-if="selectedUi === null" type="info">选择 UI 资源后，可查看并调整它关联的 API 操作。</Notice>
      <ReadState :loading="api.loading.value" :error="api.error.value" :empty="!api.data.value.items.length" description="未找到匹配的 API 操作" @retry="loadApi" />
      <div class="check-list">
        <label v-for="operation in api.data.value.items" :key="operation.id">
          <input type="checkbox" :checked="draft.includes(operation.id)" :disabled="selectedUi === null || !allowed('replaceUiApiMappings') || report.loading.value || Boolean(report.error.value) || busy" @change="toggle(operation.id, ($event.target as HTMLInputElement).checked)" />
          <span>{{ operation.operationId }} <code>{{ operation.method }} {{ operation.pathTemplate }}</code></span>
        </label>
      </div>
      <Pager :page="page" :page-size="api.data.value.pageSize" :total="api.data.value.total" :loading="api.loading.value" @change="changePage" />
    </section>
    <section class="surface padded">
      <h2>待保存变更</h2>
      <p v-if="selectedUi !== null" class="help-text">{{ selectedName }} · 已选 {{ draft.length }} 项</p>
      <ReadState :loading="report.loading.value" :error="report.error.value" @retry="loadReport" />
      <div v-if="dirty" class="diff-grid">
        <section><NTag size="small" type="success">新增 {{ added.length }}</NTag><ul><li v-for="id in added" :key="id">{{ name(id) }}</li></ul></section>
        <section><NTag size="small" type="warning">移除 {{ removed.length }}</NTag><ul><li v-for="id in removed" :key="id">{{ name(id) }}</li></ul></section>
      </div>
      <p v-else class="muted subsection">暂无待保存变更</p>
      <ReadState :loading="selectedApi.loading.value" :error="selectedApi.error.value" @retry="loadSelectedApi" />
      <details v-if="selectedUi !== null && draft.length" class="subsection"><summary>查看所选 API</summary><ul class="compact-list"><li v-for="id in draft" :key="id"><code>{{ name(id) }}</code><NButton v-if="allowed('replaceUiApiMappings')" text size="small" :disabled="busy" @click="toggle(id, false)">移除</NButton></li></ul></details>
      <div v-if="allowed('replaceUiApiMappings')" class="dialog-actions"><NButton :disabled="!dirty || busy" @click="draft = [...baseline]">还原</NButton><NButton type="primary" :disabled="!dirty || busy || report.loading.value || Boolean(report.error.value)" @click="save">保存映射</NButton></div>
    </section>
  </div>
  <section class="surface padded subsection">
    <h2>覆盖报告</h2>
    <div class="diff-grid subsection">
      <details><summary>未映射 UI（{{ coverage.unmappedUi.length }}）</summary><ul class="compact-list"><li v-for="resource in coverage.unmappedUi" :key="resource.id"><span>{{ resource.name }} <TechText :value="resource.code" /></span><NButton text type="primary" @click="selectUi(resource.id)">定位</NButton></li></ul></details>
      <details><summary>未映射 API（{{ coverage.unmappedApi.length }}）</summary><ul class="compact-list"><li v-for="operation in coverage.unmappedApi" :key="operation.id"><code>{{ operation.operationId }}</code><NButton text type="primary" @click="locate(operation)">定位</NButton></li></ul></details>
    </div>
  </section>
  <ConfirmAction v-if="confirmation" v-bind="confirmation" @close="confirmation = null" />
</template>
