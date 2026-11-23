import { it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { vPerm } from '@/directives/perm'
import { useAuthStore } from '@/stores/auth'
import { meetsPolicy } from '@/utils/password'
import EffectivePermissions from '@/components/EffectivePermissions.vue'
import RecordForm from '@/components/RecordForm.vue'
import { inheritancePaths } from '@/views/hierarchy'
import type { EffectiveVO, HierarchyVO } from '@/api/types'
it('权限指令禁用、恢复、隐藏，处理子按钮及非按钮宿主', async () => {
  setActivePinia(createPinia()); const auth = useAuthStore()
  const wrapper = mount({ template: '<div><button v-perm="\'edit\'">edit</button><div v-perm="\'edit\'"><button>nested</button></div><span v-perm="\'edit\'">text</span><button v-perm.hide="\'hide\'">hide</button><button v-perm="\'other\'" disabled>native</button></div>' }, { global: { directives: { perm: vPerm } } })
  expect((wrapper.find('button').element as HTMLButtonElement).disabled).toBe(true)
  auth.identity = { subject: 'x', name: '', no: '', roles: ['admin'], permissions: ['edit', 'hide'] }; await wrapper.vm.$nextTick()
  wrapper.vm.$forceUpdate(); await wrapper.vm.$nextTick()
  expect((wrapper.find('button').element as HTMLButtonElement).disabled).toBe(false)
  expect(wrapper.findAll('button')[2].attributes('style')).not.toContain('display: none')
  // 初始已有权限时不覆盖原生 disabled。
  const native = mount({ template: '<button disabled v-perm="\'edit\'">native</button>' }, { global: { directives: { perm: vPerm } } }); expect((native.element as HTMLButtonElement).disabled).toBe(true)
})
it.each([null, undefined, '', 'Ab1!', 'abcdefgh', 'ABCDEFGH', 'Abcdefgh', 'Abc12345', 'Abcd123!'])('密码强度边界 %s', password => { expect(meetsPolicy(password)).toBe(password === 'Abcd123!') })
it('表单只发送字段白名单，number / checkbox 保持类型，密码不留存', async () => {
  const submit = vi.fn().mockResolvedValue(undefined)
  const wrapper = mount(RecordForm, { global: { stubs: { Modal: { template: '<div><slot /></div>' } } }, props: { title: '测试', fields: [{ key: 'name', label: '名称' }, { key: 'parentId', label: '上级', type: 'number' }, { key: 'enabled', label: '开启', type: 'checkbox' }, { key: 'password', label: '口令', type: 'password' }], initial: { name: 'x', enabled: false, secretNotAllowed: 'no' }, submit } })
  await wrapper.find('input[type=number]').setValue('12'); await wrapper.find('input[type=password]').setValue('fictional'); await wrapper.find('form').trigger('submit'); await vi.waitFor(() => expect(submit).toHaveBeenCalled())
  expect(submit).toHaveBeenCalledWith({ name: 'x', parentId: 12, enabled: false, password: 'fictional' })
})
it('DAG 菱形保留两条路径，标明停用断路并排除无关边', () => {
  const graph = {
    roles: [1, 2, 3, 4, 5].map(id => ({ id, status: id === 2 ? 'DISABLED' : 'ENABLED' })),
    edges: [[1, 2], [1, 3], [2, 4], [3, 4], [1, 5]].map(([seniorRoleId, juniorRoleId]) => ({ seniorRoleId, juniorRoleId })),
  } as HierarchyVO
  const result = inheritancePaths(graph, 1, 4)
  expect(result.everyEdge).toEqual(new Set(['1:2', '1:3', '2:4', '3:4']))
  expect(result.paths.map(path => [path.nodes, path.effective])).toEqual([[[1, 2, 4], false], [[1, 3, 4], true]])
  expect(inheritancePaths(graph, 4, 1).paths).toEqual([])
})

it('分别显示同码 UI 和 API 权限的来源', () => {
  const value: EffectiveVO = {
    directRoles: [], effectiveRoles: [],
    permissions: [
      { id: 1, systemCode: 'course', code: 'shared', name: '界面权限', kind: 'UI', status: 'ENABLED' },
      { id: 2, systemCode: 'course', code: 'shared', name: '接口权限', kind: 'API', status: 'ENABLED' },
    ],
    sources: [
      { permissionCode: 'shared', permissionKind: 'UI', roleCode: 'UI_ROLE', inherited: false, path: ['UI_ROLE'] },
      { permissionCode: 'shared', permissionKind: 'API', roleCode: 'API_ROLE', inherited: false, path: ['API_ROLE'] },
    ],
  }
  const wrapper = mount(EffectivePermissions, { props: { value } })
  const sources = wrapper.findAll('tbody tr').map(row => row.findAll('.source-list li > span[title]').map(path => path.text()))
  expect(sources).toEqual([['UI_ROLE'], ['API_ROLE']])
  wrapper.unmount()
})

it('筛选有效权限后收到空权限快照时显示暂无有效权限', async () => {
  const value: EffectiveVO = {
    directRoles: [], effectiveRoles: [], sources: [],
    permissions: [{ id: 1, systemCode: 'course', code: 'course:view', name: '查看课程', kind: 'UI', status: 'ENABLED' }],
  }
  const wrapper = mount(EffectivePermissions, { props: { value } })
  try {
    await wrapper.get('input[aria-label="筛选有效权限"]').setValue('不匹配')
    expect(wrapper.text()).toContain('无匹配权限')

    await wrapper.setProps({ value: { ...value, permissions: [] } })
    expect(wrapper.find('input[aria-label="筛选有效权限"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无有效权限')
    expect(wrapper.text()).not.toContain('无匹配权限')
  } finally {
    wrapper.unmount()
  }
})
