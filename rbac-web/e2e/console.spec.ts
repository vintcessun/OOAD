import { expect, test, type Page } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * 控制台端到端流程：超级管理员首次登录改口令 → 导入三份 Excel（虚构数据）→ 按部门批量授权
 * → 用户详情看到「角色 @ 适用部门」→ 回收 → 权限解释显示拒绝 → 角色授权 → 审计日志
 * → 普通员工只看到个人中心，直接访问管理页落到 403。
 * 各步骤依赖前一步的数据，按顺序执行。
 */
test.describe.configure({ mode: 'serial' })

const FIXTURES = path.join(path.dirname(fileURLToPath(import.meta.url)), 'fixtures')
const ROOT_PASSWORD = 'Root@E2e2026'
let page: Page

test.beforeAll(async ({ browser }) => {
  page = await browser.newPage()
})

test.afterAll(async () => {
  await page.close()
})

async function login(username: string, password: string) {
  await page.goto('/login')
  await page.getByTestId('username').fill(username)
  await page.getByTestId('password').fill(password)
  await page.getByTestId('login').click()
}

async function changePassword(oldPassword: string, newPassword: string) {
  await expect(page).toHaveURL(/\/password/)
  await page.getByTestId('old-password').fill(oldPassword)
  await page.getByTestId('new-password').fill(newPassword)
  await page.getByTestId('confirm-password').fill(newPassword)
  await page.getByTestId('change-password').click()
  await expect(page).toHaveURL(/\/login/)
}

/** 打开 Element Plus 下拉并选中可见的选项 */
async function choose(trigger: ReturnType<Page['getByTestId']>, option: string) {
  await trigger.click()
  await page.locator('.el-select-dropdown__item:visible', { hasText: option }).first().click()
}

test('超级管理员首次登录必须先改口令，之后进入 Dashboard', async () => {
  await login('root', 'wrong-password')
  await expect(page.getByTestId('login-error')).toContainText('用户名或口令错误')

  await login('root', 'Admin@123')
  await changePassword('Admin@123', ROOT_PASSWORD)
  await login('root', ROOT_PASSWORD)
  await expect(page).toHaveURL(/\/dashboard/)
  for (const key of ['userCount', 'roleCount', 'permissionCount', 'todayAuthzCount', 'successRate', 'failureRate',
    'p95LatencyMs', 'p99LatencyMs', 'qps', 'cacheHitRate', 'abnormalCount']) {
    await expect(page.getByTestId('metric-' + key)).toBeVisible()
  }
  await expect(page.getByTestId('side-menu')).toContainText('用户管理')
  await expect(page.getByTestId('side-menu')).toContainText('权限使用热力图')
})

async function shot(name: string) {
  await page.screenshot({ path: `test-results/screens/${name}.png`, fullPage: true })
}

async function menu(name: string) {
  await page.getByTestId('side-menu').getByText(name, { exact: true }).click()
}

/** 在当前可见的弹出层里点选一个节点或选项 */
async function pickInPopper(text: string) {
  await page.locator('.el-popper:visible').getByText(text, { exact: true }).first().click()
}

test('导入功能权限清单与组织、员工（虚构数据），导入不分配角色', async () => {
  await shot('01-dashboard')
  await menu('组织管理')
  await page.getByTestId('open-import-perm').click()
  await page.getByTestId('import-perm-file').setInputFiles(path.join(FIXTURES, 'permissions.xlsx'))
  await page.getByTestId('import-perm-submit').click()
  const permDialog = page.getByTestId('import-perm-dialog')
  await expect(permDialog.getByTestId('count-permissions')).toHaveText('20')
  await expect(permDialog.getByTestId('count-rolePermissions')).toHaveText('45')
  await shot('02-import-permissions')
  await permDialog.locator('.el-dialog__footer').getByRole('button', { name: '关闭' }).click()

  await page.getByTestId('open-import-org').click()
  await page.getByTestId('import-dept-file').setInputFiles(path.join(FIXTURES, 'departments.xlsx'))
  await page.getByTestId('import-emp-file').setInputFiles(path.join(FIXTURES, 'employees.xlsx'))
  await page.getByTestId('import-org-submit').click()
  const orgDialog = page.getByTestId('import-org-dialog')
  await expect(orgDialog.getByTestId('count-departmentsCreated')).toHaveText('11')
  await expect(orgDialog.getByTestId('count-usersCreated')).toHaveText('28')
  await expect(orgDialog).toContainText('导入不分配任何角色')
  await shot('03-import-org')
  await orgDialog.locator('.el-dialog__footer').getByRole('button', { name: '关闭' }).click()
  await expect(page.getByTestId('org-tree')).toContainText('道路工程分公司')
})

test('按部门批量授权：道路工程分公司及下级 13 人获得普通员工角色', async () => {
  await page.getByTestId('org-tree').getByText('道路工程分公司', { exact: true }).click()
  await page.getByTestId('org-batch').click()
  const dialog = page.getByTestId('batch-dialog')
  await choose(dialog.getByTestId('batch-role'), '普通员工')
  await dialog.getByTestId('batch-reason').fill('端到端测试')
  await dialog.getByTestId('batch-submit').click()
  await expect(dialog.getByTestId('batch-summary')).toContainText('成功 13')
  await shot('04-batch-assign')
  await dialog.locator('.el-dialog__footer').getByRole('button', { name: '关闭' }).click()
})

let userId = ''

test('用户列表脱敏电话；用户详情显示角色与适用部门、有效权限；回收后立即生效', async () => {
  await menu('用户管理')
  await page.getByTestId('user-keyword').fill('道路财务员1')
  await page.getByTestId('user-search').click()
  const table = page.getByTestId('user-table')
  await expect(table).not.toContainText('E2E0001')
  await expect(table.locator('.el-link')).toHaveCount(1)
  await expect(table).toContainText('道路工程分公司-财务部')
  await expect(table).toContainText('139****')
  await shot('05-user-list')
  await table.locator('.el-link').first().click()
  await expect(page).toHaveURL(/\/users\/\d+/)
  userId = page.url().split('/').pop()!
  const roles = page.getByTestId('role-table')
  await expect(roles).toContainText('普通员工')
  await expect(roles).toContainText('道路工程分公司-财务部')
  await shot('06-user-detail')
  await page.getByRole('tab', { name: /有效权限/ }).click()
  await expect(page.getByTestId('perm-table')).toContainText('oa')
  await page.getByRole('tab', { name: /角色/ }).click()

  await roles.getByTestId('revoke').first().click()
  await page.locator('.el-message-box').getByRole('button', { name: /确定|OK/ }).click()
  await expect(roles).not.toContainText('普通员工')
})

test('权限解释：回收后再问拟稿权限，判定为拒绝并给出逐步依据', async () => {
  await page.goto(`/explain?userId=${userId}`)
  await page.getByTestId('explain-permission').fill('oa:m01:f02:create')
  await page.getByTestId('explain-submit').click()
  await expect(page.getByTestId('explain-subject')).toHaveText('E2E0009')
  await expect(page.getByTestId('explain-verdict')).toContainText('DENY')
  await expect(page.getByTestId('explain-trace')).toContainText('没有任何启用中的角色')
  await shot('07-explain')
})

test('新增临时用户：没有工号与电话，必须有到期时间', async () => {
  await menu('用户管理')
  await page.getByTestId('user-create').click()
  const form = page.getByTestId('user-form')
  await form.getByTestId('form-username').fill('tmp01')
  await form.getByTestId('form-realname').fill('临时人员一')
  await form.getByTestId('dept-select').click()
  await pickInPopper('道路工程分公司-第一项目经理部')
  await form.getByText('临时用户', { exact: true }).click()
  await expect(form.getByTestId('form-employee-no')).toHaveCount(0)
  await form.getByTestId('form-submit').click()
  await expect(form).toContainText('临时用户必须设置到期时间')
  const expires = form.locator('.el-date-editor input')
  await expires.fill('2030-01-01 00:00:00')
  await expires.press('Enter')
  await form.getByTestId('form-submit').click()
  await expect(form).toBeHidden()
  await page.getByTestId('user-keyword').fill('tmp01')
  await page.getByTestId('user-search').click()
  await expect(page.getByTestId('user-table')).toContainText('临时用户')
})

test('角色管理：新建公司内角色并授予权限', async () => {
  await menu('角色管理')
  await expect(page.getByTestId('role-list')).toContainText('SUPER_ADMIN')
  await shot('08-role-list')
  await page.getByTestId('role-create').click()
  const form = page.getByTestId('role-form')
  await form.getByTestId('role-code').fill('E2E_APPROVER')
  await form.getByTestId('role-name').fill('端到端审批员')
  await choose(form.getByTestId('role-company'), '市政公司')
  await form.getByTestId('role-save').click()
  await page.getByTestId('role-list').getByText('端到端审批员').click()
  await expect(page).toHaveURL(/\/roles\/\d+/)
  await page.getByTestId('open-grant').click()
  const dialog = page.getByTestId('grant-dialog')
  await dialog.getByTestId('grant-keyword').fill('oa:m01:f02')
  await dialog.getByTestId('grant-search').click()
  await expect(dialog.getByTestId('grant-table')).toContainText('approve')
  await dialog.getByTestId('grant-table').locator('thead .el-checkbox').click()
  await dialog.getByTestId('grant-submit').click()
  await expect(page.getByTestId('role-perms')).toContainText('approve')
  await shot('09-role-detail')
})

test('审计日志记录了回收；权限、菜单、热力图页面显示真实数据', async () => {
  await menu('审计日志')
  await choose(page.getByTestId('audit-event'), 'ROLE_REVOKE')
  await page.getByTestId('audit-search').click()
  await expect(page.getByTestId('audit-table')).toContainText('ROLE_REVOKE')
  await expect(page.getByTestId('audit-table')).toContainText('root')
  await shot('10-audit')

  await menu('权限管理')
  await expect(page.getByTestId('resource-table')).toContainText('rbac')
  await shot('11-permissions')
  await menu('菜单管理')
  await expect(page.getByTestId('menu-table')).toContainText('Dashboard')
  await shot('12-menus')
  await menu('权限使用热力图')
  await expect(page.getByTestId('heatmap-chart').locator('canvas')).toBeVisible()
  await expect(page.getByTestId('heatmap-total')).not.toContainText('共 0 次')
  await shot('13-heatmap')
  await menu('Dashboard')
  await expect(page.getByTestId('metric-todayAuthzCount')).not.toContainText('—')
  // 异常访问：点进去看到的就是计入指标的记录（第一个用例里 root 输错过一次口令）
  await expect(page.getByTestId('metric-abnormalCount')).not.toContainText('—')
  await page.getByTestId('metric-abnormalCount').click()
  await expect(page).toHaveURL(/abnormal=1/)
  await expect(page.getByTestId('audit-abnormal').locator('input')).toBeChecked()
  await expect(page.getByTestId('audit-table')).toContainText('LOGIN')
  await expect(page.getByTestId('audit-table')).not.toContainText('OUT_OF_DATA_SCOPE')
  await expect(page.getByTestId('audit-table')).not.toContainText('MISSING_PERMISSION')
  await shot('13b-abnormal')
})

test('普通员工：首次登录改口令，没有管理菜单，直接访问管理页落到 403', async () => {
  await page.getByTestId('user-menu').click()
  await page.locator('.el-dropdown-menu:visible').getByText('退出登录').click()
  await expect(page).toHaveURL(/\/login/)
  await login('E2E0001', 'Init@2026')
  await changePassword('Init@2026', 'Emp@E2e2026')
  await login('E2E0001', 'Emp@E2e2026')
  await expect(page).toHaveURL(/\/me/)
  await expect(page.getByTestId('user-detail')).toContainText('E2E0001')
  // 普通员工没有「权限解释」菜单，个人中心不显示解释入口
  await expect(page.getByTestId('explain-link')).toHaveCount(0)
  await shot('14-me')
  await page.goto('/audit-logs')
  await expect(page.getByTestId('forbidden')).toBeVisible()
  await shot('15-forbidden')
})
