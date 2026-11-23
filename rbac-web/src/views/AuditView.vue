<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { NAlert, NButton, NDrawer, NDrawerContent, NTag } from 'naive-ui'
import { get } from '@/api'
import type { AuditVO, Page } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { toInstant } from '@/utils/format'
import PageHeader from '@/components/PageHeader.vue'
import Pager from '@/components/Pager.vue'
import ReadState from '@/components/ReadState.vue'
import TechText from '@/components/TechText.vue'
import { useRead } from './workspace'

const eventLabels: Record<string, string> = {
  API_OPERATION_SAVED: '保存 API 操作', AUTHZ_DENIED: '鉴权拒绝', DENIED: '请求拒绝', LOGIN_DENIED: '登录失败',
  DEPARTMENT_CHANGED: '调动部门', DEPARTMENT_CREATED: '创建部门', DEPARTMENT_DELETED: '删除部门',
  DEPARTMENT_STATUS_CHANGED: '变更部门状态', DEPARTMENT_UPDATED: '更新部门',
  DEPARTMENT_ELIGIBILITY_CHANGED: '更新部门准入', DEPARTMENT_ROLES_REPLACED: '更新部门准入',
  LOGIN: '登录成功', LOGOUT: '退出登录', PASSWORD_CHANGED: '修改密码', PERMISSION_CREATED: '创建权限', PERMISSION_STATUS: '变更权限状态',
  RESOURCE_CHANGED: '更新资源', ROLE_ASSIGNED: '指派角色', ROLE_CREATED: '创建角色', ROLE_DELETED: '退役角色', ROLE_UPDATED: '更新角色',
  ROLE_INHERITANCE_ADDED: '添加角色继承', ROLE_INHERITANCE_REMOVED: '移除角色继承', ROLE_INHERITS_REMOVED: '移除角色继承',
  ROLE_PERMISSIONS_CHANGED: '更新角色直接权限', ROLE_PERMISSIONS_REPLACED: '更新角色直接权限', ROLE_STATUS_CHANGED: '变更角色状态',
  ROLE_STATUS: '变更角色状态', ROLE_UNASSIGNED: '撤销角色指派', SYSTEM_CHANGED: '更新系统', SYSTEM_CREATED: '登记系统',
  SYSTEM_STATUS: '变更系统状态', SYSTEM_UPDATED: '更新系统', UI_API_MAPPINGS_REPLACED: '更新资源映射',
  UI_RESOURCE_DELETED: '删除菜单或按钮', UI_RESOURCE_SAVED: '保存菜单或按钮', USER_CREATED: '创建用户', USER_DELETED: '删除用户',
  USER_DEPARTMENT_CHANGED: '调动用户部门', USER_STATUS_CHANGED: '变更用户状态', USER_STATUS: '变更用户状态', USER_UPDATED: '更新用户资料',
}
const fieldLabels: Record<string, string> = { name: '名称', username: '账号', status: '状态', roleId: '角色 ID', departmentId: '部门 ID', version: '版本', code: '编码', systemCode: '系统码' }
const auth = useAuthStore()
const emptyFilters = () => ({ actor: '', actorId: '', target: '', from: '', to: '' })
const form = reactive(emptyFilters()), filters = ref(emptyFilters()), validation = ref('')
const list = useRead<Page<AuditVO>>(() => ({ page: 1, pageSize: 20, total: 0, items: [] })), rows = list.data
const page = ref(1), selected = ref<AuditVO | null>(null)
const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone
const dateFormat = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium', hour12: false, timeZone })
function displayDate(raw: string) {
  const date = new Date(raw)
  return Number.isNaN(date.getTime()) ? raw : dateFormat.format(date)
}
function load() {
  const { actor, actorId, target, from, to } = filters.value
  return list.read(() => get<Page<AuditVO>>('/audit-logs', {
    systemCode: auth.targetSystem, actor: actor.trim() || undefined, actorId: actorId || undefined, target: target.trim() || undefined,
    from: toInstant(from), to: toInstant(to), page: page.value, pageSize: 20,
  }))
}
function changePage(next: number) { selected.value = null; page.value = next; return load() }
function submit() {
  validation.value = ''
  const range = [form.from, form.to].filter(Boolean).map(value => new Date(value).getTime())
  if (range.some(value => !Number.isFinite(value)) || (range.length === 2 && range[0] > range[1])) {
    validation.value = '请输入有效的时间范围，结束时间应晚于或等于开始时间。'
    return
  }
  filters.value = { ...form }
  void changePage(1)
}
function record(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}
const changes = computed(() => {
  const before = record(selected.value?.before), after = record(selected.value?.after)
  return [...new Set([...Object.keys(before), ...Object.keys(after)])].filter(key => JSON.stringify(before[key]) !== JSON.stringify(after[key]))
    .map(key => ({ key, name: fieldLabels[key] ?? key, before: before[key], after: after[key] }))
})
const formatValue = (value: unknown) => value === undefined ? '—' : typeof value === 'string' ? value : JSON.stringify(value, null, 2)
watch(() => auth.targetSystem, () => { list.reset(); void changePage(1) }, { immediate: true })
</script>
<template>
  <PageHeader title="操作审计" description="按操作者、目标和时间定位操作，查看变更记录。" />
  <section class="surface">
    <form class="toolbar" @submit.prevent="submit">
      <div class="field"><label for="audit-actor">操作者</label><input id="audit-actor" v-model="form.actor" placeholder="输入姓名或账号" /></div>
      <div class="field small-field"><label for="audit-actor-id">操作者 ID</label><input id="audit-actor-id" v-model="form.actorId" type="number" min="1" /></div>
      <div class="field"><label for="audit-target">目标</label><input id="audit-target" v-model="form.target" placeholder="输入目标标识" /></div>
      <div class="field"><label for="audit-from">开始时间</label><input id="audit-from" v-model="form.from" type="datetime-local" /></div>
      <div class="field"><label for="audit-to">结束时间</label><input id="audit-to" v-model="form.to" type="datetime-local" /></div>
      <NButton type="primary" attr-type="submit">查询审计</NButton><NButton @click="Object.assign(form, emptyFilters()); submit()">重置</NButton>
    </form>
    <NAlert v-if="validation" type="warning" role="alert">{{ validation }}</NAlert>
    <p class="table-note">筛选和显示时区 · {{ timeZone }}</p>
    <div class="table-scroll" tabindex="0" aria-label="审计列表"><table class="data-table"><thead><tr><th>时间</th><th>操作者</th><th>事件</th><th>目标</th><th>操作</th></tr></thead>
      <tbody><tr v-for="row in rows.items" :key="row.id"><td class="one-line audit-time" :title="row.at">{{ displayDate(row.at) }}</td>
        <td>{{ row.actor.name || '系统' }} <NTag v-if="row.actor.deleted" size="small" type="warning">已删除</NTag></td>
        <td><strong>{{ eventLabels[row.type] ?? row.type }}</strong><TechText v-if="eventLabels[row.type]" :value="row.type" /></td><td><TechText v-if="row.target" :value="row.target" /><span v-else>—</span></td>
        <td><NButton text type="primary" @click="selected = row">详情</NButton></td></tr></tbody>
    </table></div>
    <ReadState :loading="list.loading.value" :error="list.error.value" :empty="!rows.items.length" description="无匹配审计记录" @retry="load" />
    <Pager :page="page" :page-size="rows.pageSize" :total="rows.total" :loading="list.loading.value" @change="changePage" />
  </section>
  <NDrawer :show="Boolean(selected)" :width="570" @update:show="value => { if (!value) selected = null }">
    <NDrawerContent v-if="selected" title="审计详情" closable>
      <h2>{{ eventLabels[selected.type] ?? selected.type }}</h2><TechText :value="selected.type" />
      <dl class="description-grid">
        <div><dt>时间 · {{ timeZone }}</dt><dd>{{ displayDate(selected.at) }}</dd></div><div><dt>操作者</dt><dd>{{ selected.actor.name }}{{ selected.actor.deleted ? '（已删除）' : '' }}</dd></div>
        <div><dt>操作者角色</dt><dd>{{ selected.actor.roles.join('、') || '—' }}</dd></div><div><dt>目标</dt><dd>{{ selected.target || '—' }}</dd></div>
      </dl>
      <div class="section-heading"><h3>字段变更</h3></div>
      <ReadState v-if="!changes.length" empty description="此事件没有记录字段差异" />
      <div v-else class="table-scroll" tabindex="0" aria-label="字段变更对照"><table class="data-table"><thead><tr><th>字段</th><th>变更前</th><th>变更后</th></tr></thead><tbody>
        <tr v-for="change in changes" :key="change.key"><td :title="change.key">{{ change.name }}</td><td><code>{{ formatValue(change.before) }}</code></td><td><code>{{ formatValue(change.after) }}</code></td></tr>
      </tbody></table></div>
      <details class="subsection"><summary>原始审计数据</summary><pre>{{ JSON.stringify(selected, null, 2) }}</pre></details>
    </NDrawerContent>
  </NDrawer>
</template>
