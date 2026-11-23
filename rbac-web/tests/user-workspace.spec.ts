import { it, expect, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { NSelect } from 'naive-ui'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import UserWorkspace from '@/views/UserWorkspace.vue'
import RoleWorkspace from '@/views/RoleWorkspace.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import Pager from '@/components/Pager.vue'
import RecordForm from '@/components/RecordForm.vue'
import RowActions from '@/components/RowActions.vue'
import { useAuthStore } from '@/stores/auth'
import { authApi, del, get } from '@/api'
import type { DepartmentVO, EffectiveVO, RoleVO, UiResourceVO, UserVO } from '@/api/types'
vi.mock('@/api', () => ({
  get: vi.fn(), post: vi.fn(), patch: vi.fn(), put: vi.fn(), del: vi.fn(),
  authApi: { me: vi.fn(), profile: vi.fn(), menus: vi.fn(), canCall: vi.fn() },
}))

function enabledButton(code: string): UiResourceVO {
  return {
    id: 3, systemCode: 'rbac-center', parentId: 1, type: 'BUTTON', code, name: code,
    route: null, component: null, sortOrder: 0, status: 'ENABLED', permissionCode: code, children: [],
  }
}

it('部门选项加载前禁止打开创建表单，返回后使用真实部门树', async () => {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.identity = { subject: 'fictional', name: '', no: '', roles: ['admin'], permissions: ['rbac:ui:createUser'] }
  auth.menus = [enabledButton('rbac:ui:createUser')]
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/users', component: UserWorkspace }] })
  await router.push('/users')
  let resolveDepartments!: (value: DepartmentVO[]) => void
  const pending = new Promise<DepartmentVO[]>(resolve => { resolveDepartments = resolve })
  vi.mocked(get).mockImplementation(async url => url === '/departments/tree' ? pending : { page: 1, pageSize: 20, total: 0, items: [] })
  const wrapper = mount(UserWorkspace, { global: { plugins: [router] } })
  await flushPromises()
  const create = wrapper.findAll('button').find(button => button.text() === '创建用户')!
  expect((create.element as HTMLButtonElement).disabled).toBe(true)
  resolveDepartments([{ id: 77, name: '虚构部门', code: 'TEST', parentId: null, children: [], path: '/77/', status: 'ENABLED' }])
  await flushPromises()
  expect((create.element as HTMLButtonElement).disabled).toBe(false)
  await create.trigger('click')
  expect(wrapper.getComponent(RecordForm).props('fields').find(field => field.key === 'departmentId')?.tree).toEqual([{ key: 77, label: '虚构部门', children: [] }])
  wrapper.unmount()
})

const user: UserVO = {
  id: 7, subject: 'fictional', username: 'demo', name: '测试用户', no: null, phone: null,
  departmentId: 1, departmentName: '总部', status: 'ACTIVE', version: 1,
}
const role: RoleVO = {
  id: 7, systemCode: 'rbac-center', code: 'demo', name: '测试角色', description: null,
  status: 'ENABLED', builtin: false, identityRole: null, version: 1,
}

it('权限刷新开放指派表单时为当前用户加载准入角色', async () => {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  const permission = 'rbac:ui:createUserRoleAssignment'
  const eligiblePath = '/departments/1/assignable-roles'
  const effective: EffectiveVO = { directRoles: [], effectiveRoles: [], permissions: [], sources: [] }
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/users', component: UserWorkspace }] })
  await router.push('/users?userId=7&tab=assignments')
  vi.mocked(get).mockClear()
  vi.mocked(get).mockImplementation(async endpoint => {
    if (endpoint === '/users/7') return user
    if (endpoint === eligiblePath) return [role]
    if (endpoint === '/users/7/systems/rbac-center/permissions') return effective
    if (endpoint === '/users') return { page: 1, pageSize: 20, total: 0, items: [] }
    return []
  })
  const wrapper = mount(UserWorkspace, { global: { plugins: [router] } })
  await flushPromises()
  expect(wrapper.findComponent(NSelect).exists()).toBe(false)
  expect(vi.mocked(get).mock.calls.some(([endpoint]) => endpoint === eligiblePath)).toBe(false)

  vi.mocked(authApi.me).mockResolvedValue({ subject: 'fictional', name: '', no: '', roles: [], permissions: [permission] })
  vi.mocked(authApi.profile).mockResolvedValue({ user, effective })
  vi.mocked(authApi.menus).mockResolvedValue([enabledButton(permission)])
  vi.mocked(authApi.canCall).mockResolvedValue(false)
  await auth.refresh()
  await flushPromises()
  expect(wrapper.getComponent(NSelect).props('options')).toEqual([{ label: '测试角色 · demo', value: 7 }])
  expect(vi.mocked(get).mock.calls.filter(([endpoint]) => endpoint === eligiblePath)).toEqual([
    [eligiblePath, { systemCode: 'rbac-center' }],
  ])
  wrapper.unmount()
})

it.each([
  { title: '用户', component: UserWorkspace, path: '/users', row: user, action: 'deleteUser' },
  { title: '角色', component: RoleWorkspace, path: '/roles', row: role, action: 'deleteRole' },
])('$title删除末页最后一项后，回退页读取失败提示刷新失败', async ({ component, path, row, action }) => {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  const permission = `rbac:ui:${action}`
  auth.identity = { subject: 'fictional', name: '', no: '', roles: [], permissions: [permission] }
  auth.menus = [enabledButton(permission)]
  vi.spyOn(auth, 'refresh').mockResolvedValue(undefined)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path, component }] })
  await router.push(path)
  let removed = false
  vi.mocked(del).mockImplementation(async () => { removed = true })
  vi.mocked(get).mockImplementation(async (endpoint, params) => {
    if (endpoint !== path) return []
    const page = (params as { page: number }).page
    if (removed && page === 1) throw new Error('暂时无法读取回退页')
    const items = page === 1
      ? Array.from({ length: 20 }, (_, index) => ({ ...row, id: index + 100 }))
      : removed ? [] : [row]
    return { page, pageSize: 20, total: removed ? 20 : 21, items }
  })
  const wrapper = mount(component, { global: { plugins: [router] } })
  await flushPromises()
  wrapper.getComponent(Pager).vm.$emit('change', 2)
  await flushPromises()
  wrapper.getComponent(RowActions).vm.$emit('select', 'delete')
  await flushPromises()
  await wrapper.getComponent(ConfirmAction).props('submit')()
  await flushPromises()
  expect(wrapper.text()).toContain('已保存。页面或权限刷新失败，请重试刷新。')
  expect(wrapper.text()).toContain('暂时无法读取回退页')
  wrapper.unmount()
})

it.each([true, false])('从详情退役角色后仅在成功时返回列表（%s）', async succeeds => {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  const permission = 'rbac:ui:deleteRole'
  auth.identity = { subject: 'fictional', name: '', no: '', roles: [], permissions: [permission] }
  auth.menus = [enabledButton(permission)]
  vi.spyOn(auth, 'refresh').mockResolvedValue(undefined)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/roles', component: RoleWorkspace }] })
  await router.push('/roles?roleId=7&tab=direct&system=rbac-center')
  let retired = false
  const remaining = { ...role, id: 8, name: '可用角色' }
  vi.mocked(del).mockImplementation(async () => {
    if (!succeeds) throw new Error('退役失败')
    retired = true
  })
  vi.mocked(get).mockClear()
  vi.mocked(get).mockImplementation(async endpoint => {
    if (endpoint === '/roles/7') {
      if (retired) throw new Error('角色不存在')
      return role
    }
    if (endpoint === '/roles/7/permissions') return [{ id: 1, code: 'old:grant', name: '旧直接权限', kind: 'API', status: 'ENABLED' }]
    if (endpoint === '/roles/7/effective-permissions') return { directRoles: [], effectiveRoles: [], permissions: [], sources: [] }
    const items = retired ? [remaining] : [role, remaining]
    return { page: 1, pageSize: 20, total: items.length, items }
  })
  const wrapper = mount(RoleWorkspace, { global: { plugins: [router] } })
  await flushPromises()
  expect(wrapper.text()).toContain('旧直接权限')
  wrapper.getComponent(RowActions).vm.$emit('select', 'delete')
  await flushPromises()
  const submit = wrapper.getComponent(ConfirmAction).props('submit')
  if (succeeds) {
    await submit()
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ system: 'rbac-center' })
    expect(wrapper.text()).toContain('可用角色')
    expect(wrapper.text()).not.toContain(role.name)
    expect(wrapper.text()).not.toContain('旧直接权限')
    expect(wrapper.text()).not.toContain('角色不存在')
  } else {
    await expect(submit()).rejects.toThrow('退役失败')
    expect(router.currentRoute.value.query.roleId).toBe('7')
    expect(wrapper.text()).toContain('旧直接权限')
  }
  expect(vi.mocked(get).mock.calls.filter(([endpoint]) => endpoint === '/roles/7')).toHaveLength(1)
  wrapper.unmount()
})

it.each([
  {
    title: '用户详情',
    component: UserWorkspace,
    path: '/users',
    url: '/users?userId=7&tab=permissions',
    profile: user,
    canEdit: 'updateUser',
    effectivePath: '/users/7/systems/rbac-center/permissions',
    rejectProfile: false,
  },
  {
    title: '角色详情',
    component: RoleWorkspace,
    path: '/roles',
    url: '/roles?roleId=7&tab=effective',
    profile: role,
    canEdit: 'updateRole',
    effectivePath: '/roles/7/effective-permissions',
    rejectProfile: true,
  },
])('$title刷新失败时隐藏原有权限，重试成功后展示新权限', async ({
  component, path, url, profile, canEdit, effectivePath, rejectProfile,
}) => {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  const editPermission = `rbac:ui:${canEdit}`
  auth.identity = { subject: 'fictional', name: '', no: '', roles: ['admin'], permissions: [editPermission] }
  auth.menus = [enabledButton(editPermission)]
  vi.spyOn(auth, 'refresh').mockResolvedValue(undefined)
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path, component }] })
  await router.push(url)
  const profilePath = `${path}/7`
  let rejected = false
  let grant = '旧权限'
  vi.mocked(get).mockImplementation(async endpoint => {
    if (rejected && endpoint === (rejectProfile ? profilePath : effectivePath)) throw new Error('暂时无法读取权限资料')
    if (endpoint === profilePath) return profile
    if (endpoint === effectivePath) {
      return {
        directRoles: [], effectiveRoles: [], sources: [],
        permissions: [{ id: 1, systemCode: 'rbac-center', code: 'demo:grant', name: grant, kind: 'API', status: 'ENABLED' }],
      } satisfies EffectiveVO
    }
    if (endpoint === path) return { page: 1, pageSize: 20, total: 0, items: [] }
    return []
  })
  const wrapper = mount(component, { global: { plugins: [router] } })
  await flushPromises()
  expect(wrapper.text()).toContain('旧权限')

  rejected = true
  wrapper.getComponent(RowActions).vm.$emit('select', 'edit')
  await flushPromises()
  await wrapper.getComponent(RecordForm).props('submit')({ name: '修改后的资料' })
  await flushPromises()
  const alert = wrapper.get('[role="alert"]')
  expect(alert.text()).toContain('暂时无法读取权限资料')
  expect(wrapper.text()).not.toContain('旧权限')

  rejected = false
  grant = '当前权限'
  await alert.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('当前权限')
  expect(wrapper.text()).not.toContain('暂时无法读取权限资料')
  wrapper.unmount()
})
