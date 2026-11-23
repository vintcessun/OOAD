<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { NButton, NSelect, NTag } from 'naive-ui'
import { authApi, get, post } from '@/api'
import { ApiError } from '@/api/http'
import type { ApiOperationVO, EffectiveVO, Page, PermissionVO, UserVO } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import Notice from '@/components/Notice.vue'
import PageHeader from '@/components/PageHeader.vue'
import EffectivePermissions from '@/components/EffectivePermissions.vue'
import TechText from '@/components/TechText.vue'
import { errorText, useRead } from './workspace'

type Decision = { allowed: boolean; reason: string; traceId: string; trace?: string[]; effective?: EffectiveVO | null }
const reasons: Record<string, string> = {
  ALLOW: '主体具有执行该操作所需的权限。',
  DENY: '当前主体未满足该操作的授权条件。',
  USER_DISABLED: '该用户已停用。',
  SYSTEM_DISABLED: '目标系统已停用。',
  OPERATION_NOT_FOUND: '目标系统中找不到该操作。',
  API_OPERATION_NOT_FOUND: '目标系统中找不到该 API 操作。',
}
const traceLabels: Record<string, string> = {
  TOKEN_VALID: '身份凭证有效',
  ACTOR_AUTHENTICATED: '操作者身份已认证',
  USER_SYSTEM_ACTIVE: '用户和目标系统已启用',
  USER_DISABLED: '目标用户已停用',
  SYSTEM_DISABLED: '目标系统已停用',
  REQUIREMENT_SATISFIED: '满足授权条件',
  REQUIREMENT_NOT_SATISFIED: '未满足授权条件',
}
const auth = useAuthStore()
const kind = ref<'API' | 'UI'>('API'), subject = ref(''), identifier = ref('')
const decision = ref<Decision | null>(null), mode = ref<'check' | 'explain'>('check')
const failure = ref(''), forbidden = ref(false), loading = ref(false)
const capabilities = useRead(() => ({ users: false, api: false, ui: false }))
const users = useRead<UserVO[]>(() => [])
const operations = useRead<{ label: string; value: string }[]>(() => [])
const candidates = computed(() => users.data.value.map(row => ({ label: row.name + ' · ' + row.username, value: String(row.id) })))
const canSearchOperation = computed(() => kind.value === 'API' ? capabilities.data.value.api : capabilities.data.value.ui)
let revision = 0
async function loadCapabilities() {
  await capabilities.read(async () => {
    const [users, api, ui] = await Promise.all(['listUsers', 'listApiOperations', 'listPermissions'].map(operation => authApi.canCall(operation).catch(() => false)))
    return { users, api, ui }
  })
}
function searchUsers(query: string) {
  void users.read(async () => (await get<Page<UserVO>>('/users', { keyword: query.trim() || undefined, page: 1, pageSize: 20 })).items)
}
function searchOperations(query: string) {
  const system = auth.targetSystem, selectedKind = kind.value
  void operations.read(async () => {
    const params = { keyword: query.trim() || undefined, page: 1, pageSize: 20 }
    if (selectedKind === 'API') {
      const result = await get<Page<ApiOperationVO>>(`/systems/${encodeURIComponent(system)}/api-operations`, params)
      return result.items.map(row => ({ label: row.operationId + ' · ' + row.method + ' ' + row.pathTemplate, value: row.operationId }))
    }
    const result = await get<Page<PermissionVO>>('/permissions', { ...params, systemCode: system, kind: 'UI' })
    return result.items.map(row => ({ label: row.name + ' · ' + row.code, value: row.code }))
  })
}
function clearDecision() { revision++; loading.value = false; decision.value = null; failure.value = ''; forbidden.value = false }
async function check(currentMode: 'check' | 'explain') {
  clearDecision()
  const current = revision, value = identifier.value.trim()
  if (!value || (subject.value && (!/^[1-9]\d*$/.test(subject.value) || !Number.isSafeInteger(Number(subject.value))))) {
    failure.value = '请填写有效的操作标识和用户 ID，或留空用户 ID 以查询本人。'
    return
  }
  loading.value = true
  mode.value = currentMode
  try {
    const result = await post<Decision>(`/authorization/${currentMode}`, {
      systemCode: auth.targetSystem, kind: kind.value,
      ...(subject.value ? { userId: Number(subject.value) } : {}),
      ...(kind.value === 'API' ? { operationId: value } : { permission: value }),
    })
    if (current === revision) decision.value = result
  } catch (cause) {
    if (current === revision) {
      failure.value = errorText(cause)
      forbidden.value = cause instanceof ApiError && cause.status === 403
    }
  } finally { if (current === revision) loading.value = false }
}
watch(() => auth.loadedAt, loadCapabilities, { immediate: true })
watch([() => auth.targetSystem, kind], () => {
  identifier.value = ''; operations.reset(); clearDecision()
  if (canSearchOperation.value) searchOperations('')
})
watch(canSearchOperation, value => { if (value) searchOperations('') })
watch(() => capabilities.data.value.users, value => { if (value) searchUsers('') })
watch([subject, identifier], clearDecision)
</script>
<template>
  <PageHeader title="鉴权解释" description="选择主体和目标操作，确认能否访问，并查看授权来源。" />
  <div class="diagnostics-grid">
    <section class="surface padded">
      <h2>查询条件</h2>
      <form class="record-form subsection" @submit.prevent="check('check')">
        <div class="field"><label for="auth-subject">用户 ID</label><input id="auth-subject" :value="subject" type="number" min="1" placeholder="留空查询本人" @input="subject = ($event.target as HTMLInputElement).value" /></div>
        <div v-if="capabilities.data.value.users" class="field"><label for="auth-user-search">按名称查找用户</label><NSelect id="auth-user-search" :value="subject || null" aria-label="按名称查找用户" :options="candidates" filterable remote clearable :loading="users.loading.value" placeholder="输入姓名或账号" @search="searchUsers" @update:value="subject = $event ?? ''" /></div>
        <div class="field"><label for="auth-kind">鉴权种类</label><select id="auth-kind" v-model="kind"><option>API</option><option>UI</option></select></div>
        <div class="field"><label for="auth-operation">{{ kind === 'API' ? '操作标识' : 'UI 权限码' }}</label><input id="auth-operation" v-model="identifier" maxlength="128" :placeholder="kind === 'API' ? '例如 listUsers' : '输入完整权限码'" required /></div>
        <div v-if="canSearchOperation" class="field"><label for="auth-operation-search">从目录查找</label><NSelect id="auth-operation-search" :value="identifier || null" aria-label="从目录查找" :options="operations.data.value" filterable remote clearable :loading="operations.loading.value" :placeholder="kind === 'API' ? '输入操作标识或路径' : '输入权限名称或权限码'" @search="searchOperations" @update:value="identifier = $event ?? ''" /></div>
        <p v-if="users.error.value || operations.error.value" class="help-text">目录暂时不可用，可以直接输入技术标识。</p>
        <TechText :value="auth.targetSystem" />
        <div class="row-actions"><NButton v-if="auth.canAct('checkAuthorization')" attr-type="submit" :loading="loading && mode === 'check'" :disabled="loading">检查权限</NButton><NButton v-if="auth.canAct('explainAuthorization')" type="primary" :loading="loading && mode === 'explain'" :disabled="loading" @click="check('explain')">解释来源</NButton></div>
      </form>
    </section>
    <section class="surface padded">
      <h2>鉴权结果</h2>
      <Notice v-if="failure" :type="forbidden ? 'warning' : 'error'" :title="forbidden ? '无权执行此查询（403）' : '查询未完成'" urgent>{{ failure }}</Notice>
      <p v-else-if="loading" class="subsection muted" role="status">正在查询…</p>
      <p v-else-if="!decision" class="subsection muted">填写条件后检查权限或解释来源。</p>
      <template v-else>
        <Notice :type="decision.allowed ? 'success' : 'warning'" :title="decision.allowed ? '允许访问' : '拒绝访问'">{{ reasons[decision.reason] ?? decision.reason }}</Notice>
        <div class="section-heading"><span>请求追踪</span><TechText :value="decision.traceId" /></div>
        <div v-if="decision.trace?.length" class="trace-list"><NTag v-for="item in decision.trace" :key="item" size="small">{{ traceLabels[item] ?? item }}</NTag></div>
        <EffectivePermissions v-if="decision.effective" :value="decision.effective" />
        <details class="subsection"><summary>原始响应</summary><pre>{{ JSON.stringify(decision, null, 2) }}</pre></details>
      </template>
    </section>
  </div>
</template>
