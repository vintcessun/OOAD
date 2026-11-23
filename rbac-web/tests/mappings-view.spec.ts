import { flushPromises, mount } from '@vue/test-utils'
import { NSelect } from 'naive-ui'
import { createPinia } from 'pinia'
import { expect, it, vi } from 'vitest'
import MappingsView from '@/views/MappingsView.vue'
import { get } from '@/api'

vi.mock('@/api', () => ({ get: vi.fn(), put: vi.fn() }))

it('shows a saved operation name even when it is outside the visible API page', async () => {
  const ui = { id: 39, name: '保存映射', code: 'save-mappings', children: [] }
  const visible = { id: 1, operationId: 'login', method: 'POST', pathTemplate: '/auth/login' }
  const saved = { id: 155, operationId: 'replaceUiApiMappings', method: 'PUT', pathTemplate: '/api/v1/systems/{code}/ui-api-mappings' }
  vi.mocked(get).mockImplementation(async (url, params) => {
    if (url.endsWith('/ui-resources')) return [ui]
    if (url.endsWith('/ui-api-mappings')) {
      return { mappings: [{ uiResourceId: ui.id, apiOperationId: saved.id }], unmappedUi: [], unmappedApi: [] }
    }
    if (!url.endsWith('/api-operations')) throw new Error('Unexpected endpoint: ' + url)
    const { page, pageSize } = params as { page: number; pageSize: number }
    return { page, pageSize, total: 101, items: [page === 1 ? visible : saved] }
  })

  const wrapper = mount(MappingsView, { global: { plugins: [createPinia()] } })
  try {
    await flushPromises()
    wrapper.getComponent(NSelect).vm.$emit('update:value', ui.id)
    await flushPromises()

    const selected = wrapper.findAll('details').find(details => details.text().includes('查看所选 API'))
    expect(selected?.text()).toContain(saved.operationId)
    expect(selected?.text()).not.toContain('API #155')
    expect(wrapper.get('.check-list').text()).toContain(visible.operationId)
    expect(wrapper.get('.check-list').text()).not.toContain(saved.operationId)
    expect(get).toHaveBeenCalledWith('/systems/rbac-center/api-operations', { page: 2, pageSize: 100 })
  } finally {
    wrapper.unmount()
  }
})
