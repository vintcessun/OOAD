<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NButton, NTree } from 'naive-ui'
import { get, post, patch, del, put } from '@/api'
import type { DepartmentVO, HierarchyVO, RoleVO } from '@/api/types'
import PageHeader from '@/components/PageHeader.vue'
import ReadState from '@/components/ReadState.vue'
import RecordForm from '@/components/RecordForm.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import StatusTag from '@/components/StatusTag.vue'
import TechText from '@/components/TechText.vue'
import WorkspaceFeedback from '@/components/WorkspaceFeedback.vue'
import { departmentOptions, flatten, sameIds, text, useRead, useWorkspace } from './workspace'

const tree = useRead<DepartmentVO[]>(() => [])
const approved = useRead<RoleVO[]>(() => [])
const catalog = useRead<RoleVO[]>(() => [])
const currentId = ref<number | null>(null), pattern = ref(''), roleSearch = ref('')
const draft = ref<number[]>([])
const all = computed(() => flatten(tree.data.value))
const current = computed(() => all.value.find(dept => dept.id === currentId.value))
const baseline = computed(() => approved.data.value.map(role => role.id))
const selected = computed(() => new Set(draft.value))
const dirty = computed(() => !sameIds(baseline.value, draft.value))
const candidates = computed(() => [...new Map([...catalog.data.value, ...approved.data.value].map(role => [role.id, role])).values()]
  .filter(role => (role.name + ' ' + role.code).toLowerCase().includes(roleSearch.value.trim().toLowerCase())))
const { auth, form, confirmation, error, message, stale, busy, allowed, confirm, mutate, refresh, reset } = useWorkspace(reload, () => `${currentId.value}:${auth.targetSystem}`)
const treeOptions = computed(() => departmentOptions(tree.data.value))
const parentOptions = computed(() => departmentOptions(tree.data.value, true))
const cannotDisable = computed(() => {
  const department = current.value
  if (department?.status !== 'ENABLED') return undefined
  if (department.parentId === null) return '根部门必须保持启用。'
  if (department.children?.length) return '该部门仍有子部门，无法停用。'
  return undefined
})
function loadTree() {
  return tree.read(() => get<DepartmentVO[]>('/departments/tree'))
}
async function loadEligibility() {
  const id = currentId.value, system = auth.targetSystem
  if (id === null) return true
  const results = await Promise.all([
    approved.read(() => get<RoleVO[]>(`/departments/${id}/assignable-roles`, { systemCode: system })),
    catalog.read(async () => (await get<HierarchyVO>(`/systems/${encodeURIComponent(system)}/role-hierarchy`)).roles),
  ])
  if (results[0] && id === currentId.value && system === auth.targetSystem) draft.value = [...baseline.value]
  return results.every(Boolean)
}
async function reload() {
  const loaded = await loadTree()
  if (loaded && !current.value) {
    currentId.value = tree.data.value[0]?.id ?? null
    return true
  }
  return loaded && await loadEligibility()
}
function pick(keys: Array<string | number>) {
  if (typeof keys[0] === 'number') currentId.value = keys[0]
}
function toggle(id: number, checked: boolean) {
  draft.value = checked ? [...draft.value, id] : draft.value.filter(value => value !== id)
}
function edit(department?: DepartmentVO) {
  const parent = current.value?.status === 'ENABLED' ? current.value : undefined
  form.value = {
    title: department ? '编辑部门' : '创建部门',
    initial: department ? { name: department.name } : parent ? { parentId: parent.id } : undefined,
    fields: department ? [text('name', '部门名称')] : [
      text('code', '部门编码'), text('name', '部门名称'),
      { key: 'parentId', label: '上级部门', type: 'tree', required: true, tree: parentOptions.value },
    ],
    submit: data => mutate(() => department ? patch(`/departments/${department.id}`, data) : post('/departments', data)),
  }
}
function changeStatus(department: DepartmentVO) {
  const enable = department.status === 'DISABLED'
  confirm(enable ? '启用部门' : '停用部门', enable
    ? `启用「${department.name}」后，将恢复接纳新成员、新子部门和新增角色指派，并在课程目录中显示。`
    : `停用「${department.name}」后，将暂停接纳新成员、新子部门和新增角色指派，并从课程目录隐藏。已有成员的账号状态和角色指派会保留。`,
    () => put<DepartmentVO>(`/departments/${department.id}/status`, { status: enable ? 'ENABLED' : 'DISABLED' }))
}
function remove(department: DepartmentVO) {
  confirm('删除部门', `将删除「${department.name}」。该部门仍有成员或子部门时，系统将拒绝删除。`, () => del(`/departments/${department.id}`))
}
function save() {
  const id = currentId.value, system = auth.targetSystem, before = [...baseline.value], after = [...draft.value]
  if (id === null || !dirty.value) return
  const removed = approved.data.value.filter(role => !after.includes(role.id)).map(role => role.name)
  confirm('保存部门准入', removed.length
    ? `将取消「${current.value?.name}」对 ${removed.join('、')} 的准入，并回收该部门中相应的角色指派。`
    : `将更新「${current.value?.name}」在当前系统的可指派角色。`, async () => {
    const latest = await get<RoleVO[]>(`/departments/${id}/assignable-roles`)
    if (id !== currentId.value || system !== auth.targetSystem) throw new Error('管理范围已切换，请重新确认。')
    const latestForSystem = latest.filter(role => role.systemCode === system)
    if (!sameIds(latestForSystem.map(role => role.id), before)) {
      approved.data.value = latestForSystem
      throw new Error('该部门的准入已更新。草稿仍保留，关闭确认框并核对后可重新保存。')
    }
    await put(`/departments/${id}/assignable-roles`, { roleIds: [...new Set([...latest.filter(role => role.systemCode !== system).map(role => role.id), ...after])] })
  })
}
watch([currentId, () => auth.targetSystem], () => {
  reset(); approved.reset(); catalog.reset()
  draft.value = []; roleSearch.value = ''
  void loadEligibility()
})
void reload()
</script>

<template>
  <PageHeader title="部门准入" description="管理成员的唯一所属部门，以及各部门可以直接指派的角色。">
    <template #actions><NButton v-if="allowed('createDepartment')" type="primary" :disabled="!parentOptions.length" @click="edit()">创建部门</NButton></template>
  </PageHeader>
  <WorkspaceFeedback :error="error" :message="message" :stale="stale" @retry="refresh" />
  <div class="split-grid">
    <section class="surface tree-pane">
      <h2>组织结构</h2>
      <input v-model="pattern" aria-label="搜索部门" placeholder="搜索部门名称" />
      <ReadState :loading="tree.loading.value" :error="tree.error.value" :empty="!treeOptions.length" @retry="reload" />
      <NTree v-if="treeOptions.length" :data="treeOptions" :pattern="pattern" :selected-keys="currentId ? [currentId] : []" selectable block-line default-expand-all @update:selected-keys="pick" />
    </section>
    <section v-if="current" class="surface padded">
      <div class="section-heading department-heading">
        <div><h2>{{ current.name }}</h2><TechText :value="current.code" /></div>
        <div class="row-actions">
          <StatusTag :value="current.status" />
          <NButton v-if="allowed('updateDepartment')" size="small" @click="edit(current)">编辑</NButton>
          <NButton v-if="allowed('setDepartmentStatus')" size="small" :disabled="busy || Boolean(cannotDisable)" :title="cannotDisable" @click="changeStatus(current)">{{ current.status === 'ENABLED' ? '停用部门' : '启用部门' }}</NButton>
          <NButton v-if="allowed('deleteDepartment')" size="small" @click="remove(current)">删除</NButton>
        </div>
      </div>
      <dl class="description-grid"><div><dt>上级部门</dt><dd>{{ all.find(department => department.id === current?.parentId)?.name ?? '根部门' }}</dd></div><div><dt>部门路径</dt><dd><code>{{ current.path }}</code></dd></div></dl>
      <div class="section-heading subsection"><h3>可指派角色</h3><TechText :value="auth.targetSystem" /></div>
      <p class="help-text">准入限制直接指派。角色自身的权限和继承关系在角色管理中维护。</p>
      <ReadState :loading="approved.loading.value || catalog.loading.value" :error="approved.error.value || catalog.error.value" :empty="!candidates.length && !roleSearch" description="当前系统暂无可配置角色" @retry="loadEligibility" />
      <div v-if="catalog.data.value.length" class="section-heading"><input v-model="roleSearch" aria-label="搜索准入角色" placeholder="按名称或编码筛选" /></div>
      <ReadState v-if="roleSearch && !candidates.length" empty description="无匹配角色" />
      <div class="check-list"><label v-for="role in candidates" :key="role.id"><input type="checkbox" :checked="selected.has(role.id)" :disabled="!allowed('replaceDepartmentAssignableRoles') || Boolean(approved.error.value || catalog.error.value) || busy" @change="toggle(role.id, ($event.target as HTMLInputElement).checked)" /><span>{{ role.name }} <code>{{ role.code }}</code></span><StatusTag :value="role.status" /></label></div>
      <div v-if="allowed('replaceDepartmentAssignableRoles')" class="section-heading subsection"><span class="muted">已选 {{ draft.length }} 项</span><div class="row-actions"><NButton v-if="dirty" @click="draft = [...baseline]">还原</NButton><NButton type="primary" :disabled="!dirty || busy || approved.loading.value || catalog.loading.value" @click="save">保存准入</NButton></div></div>
    </section>
    <section v-else class="surface padded"><ReadState empty description="选择一个部门查看详情与角色准入" /></section>
  </div>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
  <ConfirmAction v-if="confirmation" v-bind="confirmation" @close="confirmation = null" />
</template>

<style scoped>
.department-heading,
.department-heading .row-actions {
  flex-wrap: wrap;
}
</style>
