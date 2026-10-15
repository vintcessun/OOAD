import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import { defineComponent, h, withDirectives, resolveDirective } from 'vue'
import { me } from './fixtures'

const api = vi.hoisted(() => ({
  authApi: { me: vi.fn() },
  menuApi: { mine: vi.fn() },
  statsApi: { dashboard: vi.fn(), auditLogs: vi.fn() },
  authzApi: { explain: vi.fn() },
  userApi: { list: vi.fn(), detail: vi.fn() },
  orgApi: { companies: vi.fn(async () => []), tree: vi.fn(async () => []) },
}))
vi.mock('@/api', () => api)
const nav = vi.hoisted(() => ({ push: vi.fn(), back: vi.fn(), query: {} as Record<string, string> }))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: nav.push, back: nav.back }),
  useRoute: () => ({ query: nav.query, params: {}, path: '/' }),
}))

import { vPerm } from '@/directives/perm'
import { useAuthStore } from '@/stores/auth'
import BatchResultView from '@/components/BatchResultView.vue'
import ImportReportView from '@/components/ImportReportView.vue'
import PermissionCode from '@/components/PermissionCode.vue'
import UserDetailPanel from '@/components/UserDetailPanel.vue'
import AuditLogView from '@/views/AuditLogView.vue'
import DashboardView from '@/views/DashboardView.vue'
import ExplainView from '@/views/ExplainView.vue'

const global = { plugins: [ElementPlus], directives: { perm: vPerm } }

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  nav.query = {}
})

describe('v-perm（按钮级控制，只为体验）', () => {
  const Btn = (code: string, hide = false, disabled = false) => defineComponent({
    render: () => withDirectives(h('button', { disabled }, '删除'), [[resolveDirective('perm')!, code, '', { hide }]]),
  })

  it('没有权限时禁用并提示缺少的权限码', () => {
    useAuthStore().me = me([])
    const w = mount(Btn('rbac:user:delete'), { global })
    expect(w.find('button').element.disabled).toBe(true)
    expect(w.find('button').attributes('title')).toBe('缺少权限 rbac:user:delete')
  })

  it('.hide 修饰符隐藏按钮', () => {
    useAuthStore().me = me([])
    const w = mount(Btn('rbac:org:import', true), { global })
    expect(w.find('button').element.style.display).toBe('none')
  })

  it('有权限时不改变按钮原本的禁用状态', () => {
    useAuthStore().me = me(['rbac:user:edit'])
    expect(mount(Btn('rbac:user:edit'), { global }).find('button').element.disabled).toBe(false)
    expect(mount(Btn('rbac:user:edit', false, true), { global }).find('button').element.disabled).toBe(true)
  })
})

describe('批量结果', () => {
  it('整批原子未执行时，明确说一个都没写入，并列出原因', () => {
    const w = mount(BatchResultView, {
      global,
      props: { result: { mode: 'ATOMIC', committed: false, requested: 120, succeeded: 0, duplicated: 4, failed: 2,
        failures: [{ userId: 1043, reason: 'OUT_OF_MANAGE_SCOPE' }, { userId: 9999, reason: 'NOT_FOUND' }] } },
    })
    expect(w.text()).toContain('一个都没有写入')
    expect(w.text()).toContain('请求 120 人 · 成功 0 · 已持有 / 跳过 4 · 失败 2')
    expect(w.text()).toContain('不在你的管理范围内')
    expect(w.text()).toContain('用户不存在')
  })

  it('部分执行与全部成功', () => {
    const partial = mount(BatchResultView, { global, props: { result: { mode: 'PARTIAL', committed: true, requested: 3,
      succeeded: 2, duplicated: 0, failed: 1, failures: [{ userId: 1, reason: 'PRIVILEGE_ESCALATION' }] } } })
    expect(partial.text()).toContain('部分执行：成功 2 人，失败 1 人')
    const ok = mount(BatchResultView, { global, props: { result: { mode: 'ATOMIC', committed: true, requested: 13,
      succeeded: 13, duplicated: 0, failed: 0, failures: [] } } })
    expect(ok.text()).toContain('已执行：成功 13 人')
  })
})

describe('导入报告与权限码', () => {
  it('计数、说明、异常清单', () => {
    const w = mount(ImportReportView, { global, props: { report: {
      counts: { permissions: 1795, 'rolePermissions.EMPLOYEE': 94 },
      notes: ['单元格中的英文「view」已按「查看」处理'],
      anomalies: [{ sheet: '功能权限清单', row: 340, reason: '空白权限单元格，未授权', value: 'x' }],
    } } })
    expect(w.text()).toContain('权限')
    expect(w.text()).toContain('1795')
    expect(w.text()).toContain('角色权限 · EMPLOYEE')
    expect(w.text()).toContain('异常清单（1）')
  })

  it('权限码按四段显示', () => {
    const w = mount(PermissionCode, { props: { code: 'oa:m01:f02:approve' } })
    expect(w.findAll('.seg')).toHaveLength(4)
    expect(w.find('.seg3').text()).toBe('approve')
  })
})

describe('Dashboard', () => {
  it('显示十一项指标', async () => {
    api.statsApi.dashboard.mockResolvedValue({ userCount: 10001, roleCount: 8, permissionCount: 1821, todayAuthzCount: 12,
      successRate: 0.8333, failureRate: 0.1667, p95LatencyMs: 2.1, p99LatencyMs: 6.4, qps: 0.18, cacheHitRate: 0.6667,
      abnormalCount: 3, generatedAt: '2026-10-11T21:59:55' })
    const w = mount(DashboardView, { global })
    await flushPromises()
    expect(w.findAll('.metric')).toHaveLength(11)
    for (const v of ['10,001', '1,821', '83.3%', '16.7%', '2.10 ms', '6.40 ms', '0.18', '66.7%']) {
      expect(w.text()).toContain(v)
    }
    expect(w.find('[data-test="metric-abnormalCount"] .value').classes()).toContain('warn')
    // 点进审计日志用与指标相同的口径，而不是只看 AUTH_FAILED
    await w.find('[data-test="metric-abnormalCount"]').trigger('click')
    expect(nav.push).toHaveBeenCalledWith({ path: '/audit-logs',
      query: { abnormal: '1', from: expect.stringMatching(/T00:00:00$/) } })
    w.unmount()
  })
})

describe('审计日志', () => {
  it('从 Dashboard 带 abnormal=1 进来时只查异常访问', async () => {
    nav.query = { abnormal: '1', from: '2026-10-14T00:00:00' }
    api.statsApi.auditLogs.mockResolvedValue({ items: [], total: 0, page: 0, size: 20 })
    const w = mount(AuditLogView, { global })
    await flushPromises()
    expect(api.statsApi.auditLogs).toHaveBeenCalledWith(expect.objectContaining({ abnormal: true, from: '2026-10-14T00:00:00' }))
    expect((w.find('[data-test="audit-abnormal"] input').element as HTMLInputElement).checked).toBe(true)
  })

  it('默认不带 abnormal 条件', async () => {
    api.statsApi.auditLogs.mockResolvedValue({ items: [], total: 0, page: 0, size: 20 })
    mount(AuditLogView, { global })
    await flushPromises()
    expect(api.statsApi.auditLogs.mock.calls[0][0].abnormal).toBeUndefined()
  })
})

describe('「解释某个权限」入口', () => {
  const other = () => ({ ...me([]), user: { ...me([]).user, id: 20, username: 'u20' } })
  const link = (detail: ReturnType<typeof me>) =>
    mount(UserDetailPanel, { props: { detail, initialTab: 'perms' }, global }).find('[data-test="explain-link"]')

  it('没有「权限解释」菜单的账号看不到入口（审计员、业务角色）', () => {
    useAuthStore().me = me(['rbac:user:view', 'rbac:audit:view'])
    expect(link(other()).exists()).toBe(false)
    expect(link(useAuthStore().me!).exists()).toBe(false)
  })

  it('解释自己只要菜单权限；解释别人还要 rbac:authz:explain', () => {
    useAuthStore().me = me(['rbac:explain:view'])
    expect(link(useAuthStore().me!).exists()).toBe(true)
    expect(link(other()).exists()).toBe(false)
    useAuthStore().me = me(['rbac:explain:view', 'rbac:authz:explain'])
    expect(link(other()).exists()).toBe(true)
  })

  it('超级管理员可见', () => {
    useAuthStore().me = me([], { superAdmin: true })
    expect(link(other()).exists()).toBe(true)
  })
})

describe('撤销自己的指派', () => {
  const withRole = (detail: ReturnType<typeof me>) => ({ ...detail, roles: [{ assignmentId: 7, userId: detail.user.id,
    username: detail.user.username, userRealName: detail.user.realName, roleId: 2, roleCode: 'RBAC_ADMIN',
    roleName: '权限管理员', roleStatus: 'ACTIVE', scopeDeptId: 5, scopeDeptName: '财务部', scopePath: '/2/5/',
    scopeCompanyId: 2, grantedBy: 1, grantedAt: '2026-10-11T10:00:00', reason: null }] })
  const revokeButton = async (detail: ReturnType<typeof me>) => {
    const w = mount(UserDetailPanel, { props: { detail, initialTab: 'roles' }, global })
    await flushPromises()
    return w.find('[data-test="revoke"]')
  }

  it('个人中心（不是管理视图）里，自己的指派有回收按钮，别人的没有', async () => {
    useAuthStore().me = me(['rbac:assignment:revoke'])
    expect((await revokeButton(withRole(useAuthStore().me!))).exists()).toBe(true)
    const other = { ...me([]), user: { ...me([]).user, id: 20, username: 'u20' } }
    expect((await revokeButton(withRole(other))).exists()).toBe(false)
  })
})

describe('权限解释', () => {
  it('展示后端的结论与逐步依据，前端不做推断', async () => {
    useAuthStore().me = me([])
    api.authzApi.explain.mockResolvedValue({ allowed: false, reason: 'OUT_OF_DATA_SCOPE', userId: 9, username: 'u9',
      permissionCode: 'oa:m01:f02:approve', targetDeptId: 27, matched: null, selfOnly: false, cacheHit: true, latencyUs: 800,
      trace: ['用户 u9（测试员）账号有效', '角色 部门经理 具有该权限，但只适用于「道路工程分公司」及其下级，目标部门不在其中'] })
    const w = mount(ExplainView, { global })
    await w.find('[data-test="explain-permission"]').setValue('oa:m01:f02:approve')
    await w.find('form').trigger('submit')
    await flushPromises()
    expect(api.authzApi.explain).toHaveBeenCalledWith({ userId: 9, permission: 'oa:m01:f02:approve', targetDeptId: null })
    expect(w.find('[data-test="explain-verdict"]').text()).toContain('DENY')
    expect(w.findAll('[data-test="explain-trace"] li')).toHaveLength(2)
    expect(w.text()).toContain('有角色具备该权限，但适用部门不覆盖目标部门')
    expect(w.text()).toContain('只能解释自己的权限')
  })
})
