import { flushPromises, mount } from '@vue/test-utils'
import { NSelect } from 'naive-ui'
import { createPinia } from 'pinia'
import { beforeEach, expect, it, vi } from 'vitest'
import { authApi, get, post } from '@/api'
import { useAuthStore } from '@/stores/auth'
import AuthorizationView from '@/views/AuthorizationView.vue'

vi.mock('@/api', () => ({ authApi: { canCall: vi.fn() }, get: vi.fn(), post: vi.fn() }))

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(authApi.canCall).mockResolvedValue(false)
})

it('切换 API 和 UI 时提示各自目录支持的搜索条件', async () => {
  vi.mocked(authApi.canCall).mockImplementation(async operation => operation !== 'listUsers')
  vi.mocked(get).mockResolvedValue({ items: [] })
  const wrapper = mount(AuthorizationView, { global: { plugins: [createPinia()] } })
  try {
    await flushPromises()
    expect(wrapper.getComponent(NSelect).props('placeholder')).toBe('输入操作标识或路径')

    await wrapper.get('#auth-kind').setValue('UI')
    await flushPromises()
    expect(wrapper.getComponent(NSelect).props('placeholder')).toBe('输入权限名称或权限码')
  } finally {
    wrapper.unmount()
  }
})

it.each([
  { reason: 'ALLOW', trace: ['TOKEN_VALID', 'USER_SYSTEM_ACTIVE', 'REQUIREMENT_SATISFIED'],
    labels: ['身份凭证有效', '用户和目标系统已启用', '满足授权条件'] },
  { reason: 'DENY', trace: ['TOKEN_VALID', 'USER_SYSTEM_ACTIVE', 'REQUIREMENT_NOT_SATISFIED'],
    labels: ['身份凭证有效', '用户和目标系统已启用', '未满足授权条件'] },
  { reason: 'USER_DISABLED', trace: ['ACTOR_AUTHENTICATED', 'USER_DISABLED'],
    labels: ['操作者身份已认证', '目标用户已停用'] },
  { reason: 'SYSTEM_DISABLED', trace: ['ACTOR_AUTHENTICATED', 'SYSTEM_DISABLED', 'ADDITIONAL_STEP'],
    labels: ['操作者身份已认证', '目标系统已停用', 'ADDITIONAL_STEP'] },
])('翻译鉴权解释步骤 $reason，并保留未知步骤原文', async ({ reason, trace, labels }) => {
  vi.mocked(post).mockResolvedValue({ allowed: reason === 'ALLOW', reason, trace, traceId: 'trace-id' })
  const pinia = createPinia()
  vi.spyOn(useAuthStore(pinia), 'canAct').mockImplementation(operation => operation === 'explainAuthorization')
  const wrapper = mount(AuthorizationView, { global: { plugins: [pinia] } })
  try {
    await wrapper.get('#auth-operation').setValue('listUsers')
    await wrapper.get('.row-actions button').trigger('click')
    await flushPromises()

    const steps = wrapper.get('.trace-list').findAll('.n-tag').map(step => step.text())
    expect(steps).toEqual(labels)
  } finally {
    wrapper.unmount()
  }
})
