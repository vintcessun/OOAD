<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NSelect, NTreeSelect } from 'naive-ui'
import { get, post, patch, put, del } from '@/api'
import type { Page, UserVO, DepartmentVO, RoleVO, AssignmentVO, EffectiveVO } from '@/api/types'
import RecordForm, { type Field } from '@/components/RecordForm.vue'
import EffectivePermissions from '@/components/EffectivePermissions.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import DetailTabs from '@/components/DetailTabs.vue'
import PageHeader from '@/components/PageHeader.vue'
import Pager from '@/components/Pager.vue'
import ReadState from '@/components/ReadState.vue'
import StatusTag from '@/components/StatusTag.vue'
import TechText from '@/components/TechText.vue'
import WorkspaceFeedback from '@/components/WorkspaceFeedback.vue'
import RowActions from '@/components/RowActions.vue'
import { useWorkspace, useRead, text, choices, positiveId, departmentOptions, flatten } from './workspace'

const route = useRoute(), router = useRouter()
const userId = computed(() => positiveId(route.query.userId))
const tabs = ['profile', 'assignments', 'permissions']
const tab = computed(() => tabs.includes(String(route.query.tab)) ? String(route.query.tab) : 'profile')
const page = ref(1)
const freshFilters = () => ({ keyword: '', departmentId: null as number | null, status: '' })
const filters = reactive(freshFilters())
const applied = ref(freshFilters())
const list = useRead<Page<UserVO>>(() => ({ page: 1, pageSize: 20, total: 0, items: [] }))
const directory = useRead<DepartmentVO[]>(() => [])
const detail = useRead<UserVO | null>(() => null)
const assignments = useRead<AssignmentVO[]>(() => [])
const options = useRead<RoleVO[]>(() => [])
const permissions = useRead<EffectiveVO | null>(() => null)
const roleId = ref<number | null>(null)
const { auth, error, message, stale, form, confirmation, busy, mutate, run, confirm, reset, refresh, allowed } = useWorkspace(reload, () => [auth.targetSystem, userId.value].join(':'))
const departments = computed(() => departmentOptions(directory.data.value))
const enabledDepartments = computed(() => departmentOptions(directory.data.value, true))
const allDepartments = computed(() => flatten(directory.data.value))
const userDepartment = computed(() => allDepartments.value.find(department => department.id === detail.data.value?.departmentId))
const hasAssignmentPermission = computed(() => allowed('createUserRoleAssignment'))
const canAssign = computed(() => hasAssignmentPermission.value && detail.data.value?.status === 'ACTIVE' && userDepartment.value?.status !== 'DISABLED')
const eligible = computed(() => options.data.value.filter(role => role.status === 'ENABLED' && !assignments.data.value.some(link => link.roleId === role.id)))
const departmentField = (): Field => ({ key: 'departmentId', label: '所属部门', type: 'tree', required: true, tree: enabledDepartments.value })
function loadDirectory() { return directory.read(() => get<DepartmentVO[]>('/departments/tree')) }
function loadList() {
  return list.read(() => get<Page<UserVO>>('/users', {
    keyword: applied.value.keyword || undefined,
    departmentId: applied.value.departmentId ?? undefined,
    status: applied.value.status || undefined,
    page: page.value, pageSize: 20,
  }))
}
function changePage(value: number) {
  page.value = value
  return loadList()
}
function search() {
  applied.value = { ...filters, keyword: filters.keyword.trim() }
  void changePage(1)
}
function clearFilters() {
  Object.assign(filters, freshFilters())
  search()
}
function openUser(id: number, panel = 'profile') {
  void router.push({ path: '/users', query: { userId: id, tab: panel === 'profile' ? undefined : panel } })
}
function setTab(panel: string) {
  void router.replace({ path: '/users', query: { userId: userId.value, tab: panel === 'profile' ? undefined : panel } })
}
function loadAssignments(id = userId.value) {
  return id === null ? Promise.resolve(true) : assignments.read(() => get<AssignmentVO[]>(`/users/${id}/role-assignments`, { systemCode: auth.targetSystem }))
}
function loadOptions(user = detail.data.value) {
  return !user ? Promise.resolve(true) : options.read(() => get<RoleVO[]>(`/departments/${user.departmentId}/assignable-roles`, { systemCode: auth.targetSystem }))
}
function loadPermissions(id = userId.value) {
  return id === null ? Promise.resolve(true) : permissions.read(() => get<EffectiveVO>(`/users/${id}/systems/${encodeURIComponent(auth.targetSystem)}/permissions`))
}
async function loadDetail() {
  const id = userId.value, system = auth.targetSystem
  permissions.reset()
  if (id === null) return true
  const loaded = await detail.read(() => get<UserVO>(`/users/${id}`))
  if (id !== userId.value || system !== auth.targetSystem) return true
  if (!loaded || !detail.data.value) return false
  const loadRoleOptions = hasAssignmentPermission.value && detail.data.value.status === 'ACTIVE'
  const results = await Promise.all([loadAssignments(id), loadPermissions(id), loadRoleOptions ? loadOptions() : true])
  return results.every(Boolean)
}
async function reload() {
  const results = await Promise.all([loadList(), loadDirectory(), userId.value ? loadDetail() : true])
  const lastPage = Math.max(1, Math.ceil(list.data.value.total / list.data.value.pageSize))
  if (results[0] && page.value > lastPage) results[0] = await changePage(lastPage)
  return results.every(Boolean)
}
function create() {
  form.value = {
    title: '创建用户',
    fields: [
      { ...text('username', '账号'), maxLength: 128 }, { ...text('name', '姓名'), maxLength: 128 },
      text('no', '学工号', false), text('phone', '电话', false), departmentField(),
      { key: 'password', label: '初始密码', type: 'password', required: true, minLength: 12, maxLength: 256, hint: '至少 12 个字符，包含大小写字母、数字和特殊字符' },
    ],
    submit: data => mutate(() => post('/users', data)),
  }
}
function edit(user: UserVO) {
  form.value = {
    title: '编辑资料', description: user.name, initial: { name: user.name, no: user.no },
    fields: [text('name', '姓名'), text('no', '学工号', false), { ...text('phone', '新电话', false), hint: '留空保留当前电话' }],
    submit: data => mutate(() => patch(`/users/${user.id}`, { ...data, version: user.version })),
  }
}
function changeStatus(user: UserVO) {
  form.value = {
    title: '变更用户状态', description: `${user.name} · ${user.username}。停用后该账号将无法登录。`,
    initial: { status: user.status }, fields: [choices('status', '状态', ['ACTIVE', 'DISABLED'])],
    submit: data => mutate(() => put(`/users/${user.id}/status`, data)),
  }
}
function move(user: UserVO) {
  const current = allDepartments.value.find(department => department.id === user.departmentId)
  form.value = {
    title: '调动所属部门', description: `跨部门调动 ${user.name} 会清空该用户的全部直接角色指派。同部门提交保留指派。`,
    initial: current?.status === 'ENABLED' ? { departmentId: current.id } : undefined,
    fields: [departmentField()],
    submit: data => mutate(() => put(`/users/${user.id}/department`, { ...data, version: user.version })),
  }
}
function remove(user: UserVO) {
  confirm('删除用户', `将删除 ${user.name}（${user.username}）并清理角色指派，历史审计保留。`, () => del(`/users/${user.id}`))
}
function actions(user: UserVO) {
  if (user.status === 'DELETED') return []
  return [
    { key: 'edit', label: '编辑资料', show: allowed('updateUser') },
    { key: 'status', label: '变更状态', show: allowed('setUserStatus') },
    { key: 'move', label: '调动部门', show: allowed('changeUserDepartment'), disabled: !enabledDepartments.value.length },
    { key: 'delete', label: '删除用户', show: allowed('deleteUser') },
  ].filter(action => action.show)
}
function rowAction(action: string | number, user: UserVO) {
  if (action === 'edit') edit(user)
  if (action === 'status') changeStatus(user)
  if (action === 'move') move(user)
  if (action === 'delete') remove(user)
}
async function assign() {
  const id = userId.value, role = roleId.value
  if (id === null || role === null) return
  await run(async () => {
    await post(`/users/${id}/role-assignments`, { roleId: role })
    if (id === userId.value && role === roleId.value) roleId.value = null
  })
}
function revoke(link: AssignmentVO) {
  confirm('撤销角色指派', `将撤销 ${detail.data.value?.name ?? '该用户'} 的「${link.roleName}」直接指派。系统将重新计算有效权限。`, () => del(`/users/${link.userId}/role-assignments/${link.id}`))
}
watch([userId, () => auth.targetSystem], () => {
  reset(); detail.reset(); assignments.reset(); options.reset()
  roleId.value = null
  void loadDetail()
}, { immediate: true })
watch([hasAssignmentPermission, userId, () => auth.targetSystem], ([allowedNow, id, system], [allowedBefore, previousId, previousSystem]) => {
  if (id !== previousId || system !== previousSystem) return
  if (!allowedNow) {
    options.reset()
    roleId.value = null
  } else if (!allowedBefore && canAssign.value && detail.data.value?.id === id && !detail.loading.value && !detail.error.value && !options.loading.value) {
    void loadOptions()
  }
})
onMounted(() => { void loadList(); void loadDirectory() })
const user = detail.data, rows = list.data
</script>

<template>
  <WorkspaceFeedback :error="error" :message="message" :stale="stale" @retry="refresh" />
  <template v-if="!userId">
    <PageHeader title="用户管理" description="查找组织内的用户，维护资料与角色指派。">
      <template #actions><NButton v-if="allowed('createUser')" type="primary" :disabled="!enabledDepartments.length" @click="create">创建用户</NButton></template>
    </PageHeader>
    <section class="surface">
      <form class="toolbar" @submit.prevent="search">
        <div class="field wide"><label for="user-keyword">姓名 / 账号</label><input id="user-keyword" v-model="filters.keyword" placeholder="输入姓名或账号" /></div>
        <div class="field"><label for="user-department">部门</label><NTreeSelect id="user-department" v-model:value="filters.departmentId" aria-label="部门" :options="departments" :loading="directory.loading.value" filterable clearable placeholder="全部部门" /></div>
        <div class="field"><label for="user-status">用户状态</label><select id="user-status" v-model="filters.status"><option value="">全部状态</option><option value="ACTIVE">正常</option><option value="DISABLED">停用</option><option value="DELETED">已删除</option></select></div>
        <NButton type="primary" attr-type="submit">检索</NButton><NButton @click="clearFilters">重置</NButton>
      </form>
      <ReadState v-if="directory.error.value" :error="directory.error.value" @retry="loadDirectory" />
      <div class="table-scroll" tabindex="0" aria-label="用户列表">
        <table class="data-table"><thead><tr><th>姓名</th><th>账号</th><th>部门</th><th>状态</th><th>操作</th></tr></thead>
          <tbody><tr v-for="row in rows.items" :key="row.id">
            <td><RouterLink class="row-title" :to="{ path: '/users', query: { userId: row.id } }">{{ row.name }}</RouterLink></td>
            <td class="one-line"><TechText :value="row.username" /></td><td>{{ row.departmentName }}</td><td><StatusTag :value="row.status" /></td>
            <td><div class="row-actions"><NButton text type="primary" @click="openUser(row.id, 'assignments')">角色指派</NButton><RowActions :options="actions(row)" :label="'更多操作 ' + row.name" @select="rowAction($event, row)" /></div></td>
          </tr></tbody>
        </table>
      </div>
      <ReadState :loading="list.loading.value" :error="list.error.value" :empty="!rows.items.length" description="未找到匹配的用户" @retry="loadList" />
      <Pager :page="page" :page-size="rows.pageSize" :total="rows.total" :loading="list.loading.value" @change="changePage" />
    </section>
  </template>
  <template v-else>
    <PageHeader :title="user?.name ?? '用户详情'" :description="user?.username">
      <template #before><RouterLink class="back-link" to="/users">← 返回用户列表</RouterLink></template>
      <template #actions v-if="user"><StatusTag :value="user.status" /><RowActions :options="actions(user)" label="管理用户" text="管理用户" @select="rowAction($event, user!)" /></template>
    </PageHeader>
    <ReadState :loading="detail.loading.value" :error="detail.error.value" @retry="loadDetail" />
    <section v-if="user" class="surface padded">
      <DetailTabs :value="tab" :tabs="[{ key: 'profile', label: '基本资料' }, { key: 'assignments', label: '角色指派' }, { key: 'permissions', label: '有效权限' }]" label="用户详情" @change="setTab">
        <template #profile>
          <dl class="description-grid"><div><dt>姓名</dt><dd>{{ user.name }}</dd></div><div><dt>账号</dt><dd><TechText :value="user.username" /></dd></div><div><dt>所属部门</dt><dd>{{ user.departmentName }}</dd></div><div><dt>学工号</dt><dd>{{ user.no || '—' }}</dd></div><div><dt>电话</dt><dd>{{ user.phone || '—' }}</dd></div><div><dt>统一身份标识</dt><dd><TechText :value="user.subject" /></dd></div></dl>
        </template>
        <template #assignments>
          <div class="section-heading"><h3>已指派角色</h3><TechText :value="auth.targetSystem" /></div>
          <p v-if="userDepartment?.status === 'DISABLED'" class="help-text">所属部门已停用，暂停新增角色指派。</p>
          <p v-else class="help-text">角色选项由当前部门和目标系统的准入规则决定。</p>
          <form v-if="canAssign" class="toolbar assignment-form" @submit.prevent="assign">
            <div class="field wide"><label for="assign-role">可指派角色</label><NSelect id="assign-role" v-model:value="roleId" :options="eligible.map(role => ({ label: role.name + ' · ' + role.code, value: role.id }))" :loading="options.loading.value" aria-label="可指派角色" filterable placeholder="选择角色" /></div>
            <NButton type="primary" attr-type="submit" :loading="busy" :disabled="roleId === null || assignments.loading.value">指派角色</NButton>
          </form>
          <ReadState v-if="canAssign && options.error.value" :error="options.error.value" @retry="loadOptions()" />
          <ReadState :loading="assignments.loading.value" :error="assignments.error.value" :empty="!assignments.data.value.length" description="当前系统暂无直接角色指派" @retry="loadAssignments()" />
          <ul class="compact-list"><li v-for="link in assignments.data.value" :key="link.id"><span><strong>{{ link.roleName }}</strong><TechText :value="link.roleCode" /></span><NButton v-if="allowed('deleteUserRoleAssignment')" size="small" @click="revoke(link)">撤销指派</NButton></li></ul>
        </template>
        <template #permissions>
          <ReadState :loading="permissions.loading.value" :error="permissions.error.value" @retry="loadPermissions()" />
          <EffectivePermissions v-if="permissions.data.value" :value="permissions.data.value" />
        </template>
      </DetailTabs>
    </section>
  </template>
  <RecordForm v-if="form" v-bind="form" @close="form = null" />
  <ConfirmAction v-if="confirmation" v-bind="confirmation" @close="confirmation = null" />
</template>
