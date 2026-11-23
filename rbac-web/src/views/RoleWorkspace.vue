<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton } from 'naive-ui'
import { get, post, patch, put, del } from '@/api'
import type { Page, RoleVO, PermissionVO, EffectiveVO } from '@/api/types'
import RecordForm from '@/components/RecordForm.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import EffectivePermissions from '@/components/EffectivePermissions.vue'
import DetailTabs from '@/components/DetailTabs.vue'
import Notice from '@/components/Notice.vue'
import PageHeader from '@/components/PageHeader.vue'
import Pager from '@/components/Pager.vue'
import ReadState from '@/components/ReadState.vue'
import StatusTag from '@/components/StatusTag.vue'
import TechText from '@/components/TechText.vue'
import WorkspaceFeedback from '@/components/WorkspaceFeedback.vue'
import RowActions from '@/components/RowActions.vue'
import { positiveId, sameIds, text, useRead, useWorkspace } from './workspace'

const route = useRoute(), router = useRouter()
const roleId = computed(() => positiveId(route.query.roleId))
const tabs = ['profile', 'direct', 'effective']
const tab = computed(() => tabs.includes(String(route.query.tab)) ? String(route.query.tab) : 'profile')
const page = ref(1), permissionPage = ref(1)
const filters = reactive({ keyword: '', status: '' }), applied = ref({ ...filters })
const permissionSearch = ref(''), permissionKeyword = ref(''), permissionKind = ref('')
const list = useRead<Page<RoleVO>>(() => ({ page: 1, pageSize: 20, total: 0, items: [] }))
const detail = useRead<RoleVO | null>(() => null)
const direct = useRead<PermissionVO[]>(() => [])
const effective = useRead<EffectiveVO | null>(() => null)
const directory = useRead<Page<PermissionVO>>(() => ({ page: 1, pageSize: 20, total: 0, items: [] }))
const selected = ref<number[]>([])
const { auth, form, confirmation, message, stale, error, busy, mutate, run, confirm, refresh, reset, allowed } = useWorkspace(reload, () => `${auth.targetSystem}:${roleId.value}`)
const rows = list.data, role = detail.data
const baseline = computed(() => direct.data.value.map(permission => permission.id))
const dirty = computed(() => !sameIds(baseline.value, selected.value))
function loadList() {
  return list.read(() => get<Page<RoleVO>>('/roles', { systemCode: auth.targetSystem, keyword: applied.value.keyword || undefined, status: applied.value.status || undefined, page: page.value, pageSize: 20 }))
}
function loadDirectory() {
  return directory.read(() => get<Page<PermissionVO>>('/permissions', { systemCode: auth.targetSystem, keyword: permissionKeyword.value || undefined, kind: permissionKind.value || undefined, page: permissionPage.value, pageSize: 20 }))
}
async function loadDetail() {
  const id = roleId.value, system = auth.targetSystem
  effective.reset()
  if (id === null) return true
  const loaded = await detail.read(async () => {
    const result = await get<RoleVO>(`/roles/${id}`)
    if (result.systemCode !== system) throw new Error('该角色属于其他系统，请切换目标系统后再打开。')
    return result
  })
  if (id !== roleId.value || system !== auth.targetSystem) return true
  if (!loaded) return false
  const [loadedDirect, loadedEffective, loadedDirectory] = await Promise.all([
    direct.read(() => get<PermissionVO[]>(`/roles/${id}/permissions`)),
    effective.read(() => get<EffectiveVO>(`/roles/${id}/effective-permissions`)),
    allowed('replaceRolePermissions') ? loadDirectory() : true,
  ])
  if (loadedDirect && id === roleId.value && system === auth.targetSystem) selected.value = [...baseline.value]
  return loadedDirect && loadedEffective && loadedDirectory
}
async function reloadList() {
  const loaded = await loadList()
  const lastPage = Math.max(1, Math.ceil(rows.value.total / rows.value.pageSize))
  return loaded && (page.value <= lastPage || await changePage(lastPage))
}
async function reload() {
  const results = await Promise.all([reloadList(), loadDetail()])
  return results.every(Boolean)
}
function changePage(next: number) { page.value = next; return loadList() }
function search() { applied.value = { ...filters, keyword: filters.keyword.trim() }; void changePage(1) }
function clearFilters() { filters.keyword = ''; filters.status = ''; search() }
function edit(row?: RoleVO) {
  const system = auth.targetSystem
  form.value = {
    title: row ? '编辑角色资料' : '创建角色',
    initial: row ? { name: row.name, description: row.description } : undefined,
    fields: row ? [text('name', '角色名称'), text('description', '说明', false)] : [
      text('code', '角色编码'), text('name', '角色名称'), text('description', '说明', false),
    ],
    submit: data => mutate(() => row ? patch(`/roles/${row.id}`, { ...data, version: row.version }) : post('/roles', { ...data, systemCode: system })),
  }
}
function options() {
  return [
    { label: '编辑资料', key: 'edit', show: allowed('updateRole') },
    { label: '变更角色状态', key: 'status', show: allowed('setRoleStatus') },
    { label: '退役角色', key: 'delete', show: allowed('deleteRole') },
  ].filter(option => option.show)
}
async function retire(row: RoleVO) {
  const session = auth.session
  await del(`/roles/${row.id}`)
  const current = router.currentRoute.value
  if (auth.session !== session || current.path !== '/roles' || roleId.value !== row.id || auth.targetSystem !== row.systemCode) return
  await router.replace({ path: '/roles', query: { ...current.query, roleId: undefined, tab: undefined } })
  if (auth.session === session && router.currentRoute.value.path === '/roles' && roleId.value === null && auth.targetSystem === row.systemCode) {
    list.reset()
    await reloadList()
  }
}
function rowAction(action: string | number, row: RoleVO) {
  if (action === 'edit') edit(row)
  if (action === 'status') {
    const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    confirm(next === 'DISABLED' ? '停用角色' : '启用角色',
      next === 'DISABLED' ? `停用「${row.name}」将切断经由此角色的有效继承路径。` : `启用「${row.name}」后，系统将重新计算受影响的继承权限。`,
      () => put(`/roles/${row.id}/status`, { status: next }))
  }
  if (action === 'delete') confirm('退役角色', `退役「${row.name}」会清理其指派、继承关系、准入与直接权限，历史审计保留。`, () => retire(row))
}
function setTab(next: string) {
  void router.replace({ path: '/roles', query: { ...route.query, tab: next === 'profile' ? undefined : next } })
}
function changePermissionPage(value: number) { permissionPage.value = value; return loadDirectory() }
function searchPermissions() { permissionKeyword.value = permissionSearch.value.trim(); void changePermissionPage(1) }
function toggle(id: number, checked: boolean) {
  selected.value = checked ? [...selected.value, id] : selected.value.filter(value => value !== id)
}
async function savePermissions() {
  const id = roleId.value, system = auth.targetSystem, before = [...baseline.value], after = [...selected.value]
  if (id === null || !dirty.value) return
  await run(async () => {
    const latest = await get<PermissionVO[]>(`/roles/${id}/permissions`)
    if (id !== roleId.value || system !== auth.targetSystem) throw new Error('管理范围已切换，请重新确认。')
    if (!sameIds(latest.map(permission => permission.id), before)) {
      direct.data.value = latest
      throw new Error('直接权限已更新。草稿已保留，请核对增删项后重新保存。')
    }
    await put(`/roles/${id}/permissions`, { permissionIds: after })
  })
}
watch(() => auth.targetSystem, () => { list.reset(); page.value = 1; void loadList() }, { immediate: true })
watch([roleId, () => auth.targetSystem], () => {
  reset(); detail.reset(); directory.reset(); direct.reset()
  selected.value = []; permissionSearch.value = ''; permissionKeyword.value = ''; permissionKind.value = ''; permissionPage.value = 1
  void loadDetail()
}, { immediate: true })
</script>

<template>
  <WorkspaceFeedback :error="error" :message="message" :stale="stale" @retry="refresh" />
  <template v-if="!roleId">
    <PageHeader title="角色管理" description="按目标系统查找角色，维护角色资料和直接权限。">
      <template #actions><NButton v-if="allowed('createRole')" type="primary" @click="edit()">创建角色</NButton></template>
    </PageHeader>
    <section class="surface">
      <form class="toolbar" @submit.prevent="search">
        <div class="field wide"><label for="roles-keyword">角色检索</label><input id="roles-keyword" v-model="filters.keyword" placeholder="输入角色名称或编码" /></div>
        <div class="field"><label for="roles-status">角色状态</label><select id="roles-status" v-model="filters.status"><option value="">全部状态</option><option value="ENABLED">启用</option><option value="DISABLED">停用</option></select></div>
        <NButton type="primary" attr-type="submit">检索</NButton><NButton @click="clearFilters">重置</NButton>
      </form>
      <div class="table-scroll" tabindex="0" aria-label="角色列表"><table class="data-table"><thead><tr><th>角色</th><th>状态</th><th>说明</th><th>操作</th></tr></thead><tbody>
        <tr v-for="row in rows.items" :key="row.id">
          <td><RouterLink class="row-title" :to="{ path: '/roles', query: { roleId: row.id, system: auth.targetSystem } }">{{ row.name }}</RouterLink><div><TechText :value="row.code" /></div></td>
          <td><StatusTag :value="row.status" /></td><td>{{ row.description ?? '—' }}</td>
          <td><div class="row-actions"><RouterLink :to="{ path: '/roles', query: { roleId: row.id, system: auth.targetSystem, tab: 'direct' } }">直接权限</RouterLink><RowActions :options="options()" :label="'更多操作 ' + row.name" @select="rowAction($event, row)" /></div></td>
        </tr>
      </tbody></table></div>
      <ReadState :loading="list.loading.value" :error="list.error.value" :empty="!rows.items.length" description="未找到匹配的角色" @retry="loadList" />
      <Pager :page="page" :page-size="rows.pageSize" :total="rows.total" :loading="list.loading.value" @change="changePage" />
    </section>
  </template>
  <template v-else>
    <PageHeader :title="role?.name ?? '角色详情'" :description="role?.code">
      <template #before><RouterLink class="back-link" to="/roles">← 返回角色列表</RouterLink></template>
      <template #actions v-if="role"><StatusTag :value="role.status" /><RouterLink v-if="auth.canVisit('/hierarchy')" :to="{ path: '/hierarchy', query: { roleId: role.id, system: role.systemCode } }">查看继承关系 ↗</RouterLink><RowActions :options="options()" label="管理角色" text="管理角色" @select="rowAction($event, role!)" /></template>
    </PageHeader>
    <ReadState :loading="detail.loading.value" :error="detail.error.value" @retry="loadDetail" />
    <section v-if="role" class="surface padded">
      <DetailTabs :value="tab" :tabs="[{ key: 'profile', label: '角色资料' }, { key: 'direct', label: '直接权限' }, { key: 'effective', label: '继承来源与有效权限' }]" label="角色详情" @change="setTab">
        <template #profile>
          <dl class="description-grid"><div><dt>角色名称</dt><dd>{{ role.name }}</dd></div><div><dt>角色编码</dt><dd><TechText :value="role.code" /></dd></div><div><dt>所属系统</dt><dd><TechText :value="role.systemCode" /></dd></div><div><dt>来源</dt><dd>{{ role.builtin ? '内置角色' : '自定义角色' }}</dd></div><div><dt>说明</dt><dd>{{ role.description ?? '—' }}</dd></div></dl>
        </template>
        <template #direct>
          <ReadState :loading="direct.loading.value" :error="direct.error.value" @retry="loadDetail" />
          <div v-if="allowed('replaceRolePermissions')">
            <p class="help-text">勾选直接授予此角色的权限。跨页保留已选项，继承来源可在有效权限页签查看。</p>
            <form class="toolbar" @submit.prevent="searchPermissions"><div class="field wide"><label for="grant-search">权限检索</label><input id="grant-search" v-model="permissionSearch" placeholder="名称或权限码" /></div><div class="field"><label for="grant-kind">种类</label><select id="grant-kind" v-model="permissionKind" @change="searchPermissions"><option value="">全部</option><option>UI</option><option>API</option></select></div><NButton attr-type="submit">检索</NButton></form>
            <ReadState :loading="directory.loading.value" :error="directory.error.value" :empty="!directory.data.value.items.length" description="未找到匹配的权限" @retry="loadDirectory" />
            <div class="check-list"><label v-for="permission in directory.data.value.items" :key="permission.id"><input type="checkbox" :checked="selected.includes(permission.id)" :disabled="busy || direct.loading.value || Boolean(direct.error.value)" @change="toggle(permission.id, ($event.target as HTMLInputElement).checked)" /><span>{{ permission.name }}<code>{{ permission.code }}</code></span><span>{{ permission.kind }}</span><StatusTag :value="permission.status" /></label></div>
            <Pager :page="permissionPage" :page-size="directory.data.value.pageSize" :total="directory.data.value.total" @change="changePermissionPage" />
            <div class="section-heading"><span>共选中 {{ selected.length }} 项 <template v-if="dirty">· 新增 {{ selected.filter(id => !baseline.includes(id)).length }} · 移除 {{ baseline.filter(id => !selected.includes(id)).length }}</template></span><NButton type="primary" :loading="busy" :disabled="!dirty || direct.loading.value || Boolean(direct.error.value)" @click="savePermissions">保存直接权限</NButton></div>
          </div>
          <template v-else><ReadState v-if="!direct.loading.value && !direct.error.value && !direct.data.value.length" empty description="此角色暂无直接权限" /><ul class="compact-list"><li v-for="permission in direct.data.value" :key="permission.id"><span>{{ permission.name }} <TechText :value="permission.code" /></span><StatusTag :value="permission.status" /></li></ul></template>
        </template>
        <template #effective>
          <Notice v-if="role.status === 'DISABLED'" type="warning">角色已停用，经过此角色的继承路径会被切断。</Notice>
          <ReadState :loading="effective.loading.value" :error="effective.error.value" @retry="loadDetail" />
          <EffectivePermissions v-if="effective.data.value" :value="effective.data.value" :show-roles="false" />
        </template>
      </DetailTabs>
    </section>
  </template>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
  <ConfirmAction v-if="confirmation" v-bind="confirmation" @close="confirmation = null" />
</template>
