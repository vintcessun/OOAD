import { onScopeDispose, ref, shallowRef } from 'vue'
import type { TreeSelectOption } from 'naive-ui'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/http'
import type { DepartmentVO } from '@/api/types'
import type { Field, RecordEditor } from '@/components/RecordForm.vue'

export function errorText(error: unknown) {
  if (error instanceof ApiError && error.code === 'ROLE_CYCLE') return '这条关系会形成循环。请选择另一个被继承角色，或先移除冲突的继承关系（ROLE_CYCLE）。'
  if (error instanceof ApiError && error.code === 'DEPARTMENT_DISABLED') return '该部门已停用，暂停接纳。请刷新部门状态或选择启用的部门。'
  if (error instanceof ApiError && error.code === 'DEPARTMENT_HAS_CHILDREN') return '该部门仍有子部门，无法停用。'
  if (error instanceof ApiError && error.code === 'ROOT_DEPARTMENT_DISABLE_FORBIDDEN') return '根部门必须保持启用。'
  return error instanceof Error ? error.message : '操作未完成，请稍后重试。'
}

export function useRead<T>(empty: () => T) {
  const data = shallowRef<T>(empty()), loading = ref(false), error = ref('')
  let revision = 0
  function reset() {
    revision++
    data.value = empty()
    loading.value = false
    error.value = ''
  }
  async function read(fetch: () => Promise<T>) {
    const current = ++revision
    loading.value = true
    error.value = ''
    try {
      const result = await fetch()
      if (current === revision) data.value = result
      return true
    } catch (cause) {
      if (current === revision) error.value = errorText(cause)
      return current !== revision
    } finally {
      if (current === revision) loading.value = false
    }
  }
  onScopeDispose(reset)
  return { data, loading, error, read, reset }
}

export function useWorkspace(reload: () => Promise<unknown>, context: () => unknown = () => undefined) {
  const auth = useAuthStore()
  const busy = ref(false), error = ref(''), message = ref(''), stale = ref(false)
  const form = shallowRef<RecordEditor | null>(null)
  const confirmation = shallowRef<{ title: string; description: string; submit: () => Promise<void> } | null>(null)
  let revision = 0
  let refreshRevision = 0
  function reset() {
    revision++
    form.value = null
    confirmation.value = null
    busy.value = false
    stale.value = false
    message.value = ''
    error.value = ''
  }
  onScopeDispose(reset)
  async function refresh(afterWrite = false) {
    const current = revision, key = context(), update = ++refreshRevision
    const results = await Promise.allSettled([auth.refresh(afterWrite), Promise.resolve().then(reload)])
    if (current === revision && key === context() && update === refreshRevision) {
      stale.value = results.some(result => result.status === 'rejected' || result.value === false)
      message.value = stale.value ? '已保存。页面或权限刷新失败，请重试刷新。' : '已保存'
    }
  }
  async function mutate(action: () => Promise<unknown>) {
    if (busy.value) throw new Error('正在更新前一次操作的结果，请稍后再次保存。')
    const current = revision, key = context(), session = auth.session
    busy.value = true
    stale.value = false
    message.value = ''
    try {
      await action()
    } finally {
      if (current === revision) busy.value = false
    }
    if (session !== auth.session) return
    if (current === revision && key === context()) {
      message.value = '已保存，正在刷新…'
      void refresh(true)
    } else {
      void auth.refresh(true).catch(() => undefined)
    }
  }
  async function run(action: () => Promise<unknown>) {
    error.value = ''
    const current = revision
    try { await mutate(action) } catch (cause) { if (current === revision) error.value = errorText(cause) }
  }
  function confirm(title: string, description: string, action: () => Promise<unknown>) {
    confirmation.value = { title, description, submit: () => mutate(action) }
  }
  return { auth, form, confirmation, busy, error, message, stale, refresh, mutate, run, confirm, reset, allowed: auth.canAct }
}

export const text = (key: string, label: string, required = true): Field => ({ key, label, required })
export const choices = (key: string, label: string, values: string[]): Field => ({
  key, label, required: true, type: 'select',
  options: values.map(value => ({ value, label: labels[value] ?? value })),
})
const labels: Record<string, string> = { ENABLED: '启用', ACTIVE: '正常', DISABLED: '停用', DELETED: '已删除', MENU: '菜单', BUTTON: '按钮', PUBLIC: '公开', AUTHENTICATED: '需登录', PROTECTED: '需授权' }
export function label(value: string) { return labels[value] ?? value }
export function tone(value: string) {
  if (['ACTIVE', 'ENABLED'].includes(value)) return 'success'
  if (value === 'DELETED') return 'error'
  if (value === 'DISABLED') return 'warning'
  return 'default'
}
export function flatten<T extends { children: T[] }>(rows: T[]): T[] {
  return rows.flatMap(row => [row, ...flatten(row.children ?? [])])
}
export function departmentOptions(rows: DepartmentVO[], enabledOnly = false): TreeSelectOption[] {
  return rows
    .filter(department => !enabledOnly || department.status === 'ENABLED')
    .map(department => ({
      key: department.id,
      label: department.name,
      children: departmentOptions(department.children ?? [], enabledOnly),
    }))
}
export function positiveId(value: unknown) {
  return typeof value === 'string' && /^[1-9]\d*$/.test(value) && Number.isSafeInteger(Number(value)) ? Number(value) : null
}
export function sameIds<T extends number | string>(left: readonly T[], right: readonly T[]) {
  const keys = new Set(left)
  return keys.size === new Set(right).size && right.every(value => keys.has(value))
}
