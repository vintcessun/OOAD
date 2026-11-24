const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const origin = process.argv[2]
const accountData = fs.readFileSync(process.argv[3], 'utf8')
let accounts
if (process.argv.includes('--legacy')) {
  const passwords = new Map(accountData.trim().split(/\r?\n/).map(row => row.split('\t')))
  accounts = Object.fromEntries([
    ['teacher', 'CLOUD-T1'],
    ['student', 'CLOUD-S1'],
  ].map(([role, username]) => [
    role, { username, password: passwords.get(username), course: 619001, term: '619001' },
  ]))
} else {
  accounts = JSON.parse(accountData)
}
const { chromium } = require(path.resolve(process.env.PLAYWRIGHT_MODULE || 'rbac-web/node_modules/playwright'))

async function statusFor(page, pathname, action, method = 'GET') {
  const [response] = await Promise.all([
    page.waitForResponse(res => new URL(res.url()).pathname === pathname && res.request().method() === method),
    action(),
  ])
  return response
}

async function login(page, role, account) {
  await page.goto(origin + '/course/')
  await page.getByRole('heading', { name: '登录工作台' }).waitFor()
  await page.getByRole('tab', { name: role === 'teacher' ? '教师登录' : '学生登录' }).click()
  await page.locator('[name=username]').fill(account.username)
  await page.locator('[name=password]').fill(account.password)
  const response = await statusFor(page, '/api/login', () => page.locator('button[type=submit]').click(), 'POST')
  assert.equal(response.status(), 200)
  await page.waitForURL('**/course/' + role + '/courses')
}

async function verifyCourse(page, role, account) {
  const endpoint = role === 'teacher' ? '/api/teacher/courses' : '/api/me/courses'
  await login(page, role, account)
  let response
  if (account.term) {
    const select = page.locator('select').filter({ has: page.locator('option[value="' + account.term + '"]') })
    const [termResponse] = await Promise.all([
      page.waitForResponse(res => {
        const url = new URL(res.url())
        return url.pathname === endpoint && url.searchParams.get('termId') === account.term
      }),
      select.selectOption(account.term),
    ])
    response = termResponse
  } else {
    response = await statusFor(page, endpoint, () => page.reload())
  }
  assert.equal(response.status(), 200)
  const data = await response.json()
  const course = data.items.find(item => Number(item.id ?? item.courseId) === account.course)
  assert(course, 'Expected imported course on the workspace list')
  await page.getByText(course.name || course.courseName, { exact: true }).first().waitFor()
  const detail = await page.evaluate(async id => {
    const token = localStorage.getItem('cpm.accessToken')
    const res = await fetch('/api/courses/' + id, { headers: { Authorization: 'Bearer ' + token } })
    return res.status
  }, account.course)
  assert.equal(detail, 200)
}

async function verifyAdministrator(page, account, create) {
  await login(page, 'teacher', account)
  for (const [menu, permission, endpoint] of [
    ['课程', 'MCO', '/api/admin/courses'],
    ['学期', 'MTR', '/api/terms'],
    ['日志', 'MLG', '/api/audit-logs'],
    ['教师', 'MTE', '/api/teachers'],
  ]) {
    const link = page.locator('a[href*="/admin/"]:visible')
      .filter({ hasText: new RegExp('^' + menu + '$') }).first()
    const [query, result] = await Promise.all([
      statusFor(page, '/auth/permissions/' + permission, () => link.click()),
      page.waitForResponse(res => new URL(res.url()).pathname === endpoint),
    ])
    assert.equal(query.status(), 200)
    assert.equal((await query.json()).allowed, true)
    assert.equal(result.status(), 200)
  }
  await page.getByRole('button', { name: '添加教师', exact: true }).click()
  const directory = page.getByRole('tree', { name: '统一身份部门树' })
  await directory.getByRole('button', { name: '展开总部', exact: true }).click()
  await directory.getByRole('button', { name: '展开课程联调组织', exact: true }).click()
  await directory.getByRole('button', { name: '联调教师组', exact: true }).click()
  await page.getByRole('button').filter({ hasText: 'CLOUD-T2' }).click()
  const saved = await statusFor(page, '/api/teachers', () => page.getByRole('button', { name: '确认添加' }).click(), 'POST')
  assert.equal(saved.status(), create ? 201 : 409)
  if (!create) await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByPlaceholder('搜索姓名、工号或统一身份标识').fill('CLOUD-T2')
  const refreshed = await statusFor(page, '/api/teachers', () => page.getByRole('button', { name: '查询', exact: true }).click())
  const list = await refreshed.json()
  assert(list.items.some(row => row.userId === 'course-align-cloud-t2'))
}

async function verifyRevocation(page, account) {
  await login(page, 'teacher', account)
  const result = await statusFor(page, '/auth/permissions/MTE', () =>
    page.goto(origin + '/course/admin/teachers'))
  assert.equal(result.status(), 200)
  assert.equal((await result.json()).allowed, false)
  await page.waitForURL(url => url.pathname !== '/course/admin/teachers')
  const code = await page.evaluate(async () => {
    const token = localStorage.getItem('cpm.accessToken')
    const res = await fetch('/auth/api-permissions/CV', { headers: { Authorization: 'Bearer ' + token } })
    return { status: res.status, body: await res.json() }
  })
  assert.deepEqual(code, { status: 200, body: { operationId: 'CV', allowed: false } })
}

async function main() {
  const browser = await chromium.launch({ channel: 'msedge', headless: true })
  try {
    for (const [role, account] of Object.entries(accounts)) {
      const revoked = process.argv.includes('--revoked')
      if (revoked && role !== 'administrator') continue
      const browserContext = await browser.newContext({ ignoreHTTPSErrors: true })
      const page = await browserContext.newPage()
      page.setDefaultTimeout(15000)
      try {
        if (revoked) await verifyRevocation(page, account)
        else if (role === 'administrator') await verifyAdministrator(page, account, process.argv.includes('--create'))
        else await verifyCourse(page, role, account)
        console.log(role + ' browser flow passed')
      } catch (error) {
        const detail = error.message.startsWith('locator.') ? error.message.split('\n')[0] : error.message
        throw Error(role + ' browser flow at ' + new URL(page.url()).pathname + ' – ' + detail)
      } finally {
        await browserContext.close()
      }
    }
  } finally {
    await browser.close()
  }
}
main().catch(error => { console.error(error.message); process.exitCode = 1 })
