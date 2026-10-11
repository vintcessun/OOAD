import { defineConfig, devices } from '@playwright/test'

/**
 * 端到端测试：真实的权限中心（jar）+ MySQL + Redis + Vite 前端，在浏览器里走完整流程。
 * 需要 MySQL（13306）与 Redis（16379），与后端集成测试相同（docs/09-test-plan.md §3）。
 * 每次运行用一个新数据库（createDatabaseIfNotExist），Redis 用 5 号库并在开始前清空。
 */
process.env.E2E_DB ??= `rbac_e2e_${Date.now()}`
const API_PORT = Number(process.env.E2E_API_PORT ?? 18081)
const WEB_PORT = Number(process.env.E2E_WEB_PORT ?? 5174)
const java = process.env.JAVA_BIN ?? 'java'
const dbHost = process.env.E2E_DB_HOST ?? 'localhost:13306'

export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  globalSetup: './e2e/global-setup.ts',
  use: {
    baseURL: `http://localhost:${WEB_PORT}`,
    testIdAttribute: 'data-test',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ...devices['Desktop Chrome'],
    viewport: { width: 1440, height: 900 },
    // 本机已有别的版本的 Chromium 时，用 PW_CHROMIUM 指过去，省去下载
    launchOptions: process.env.PW_CHROMIUM ? { executablePath: process.env.PW_CHROMIUM } : {},
  },
  webServer: [
    {
      // 后端日志写到 test-results/backend.log，失败时随报告一起上传
      command: `"${java}" -jar ../rbac-structured/target/rbac-structured.jar --logging.file.name=test-results/backend.log`,
      url: `http://localhost:${API_PORT}/api/v1/actuator/health`,
      timeout: 180_000,
      reuseExistingServer: false,
      stdout: 'ignore',
      stderr: 'pipe',
      env: {
        SERVER_PORT: String(API_PORT),
        DB_URL: `jdbc:mysql://${dbHost}/${process.env.E2E_DB}?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false`,
        DB_USER: process.env.E2E_DB_USER ?? 'root',
        DB_PASSWORD: process.env.E2E_DB_PASSWORD ?? 'root',
        REDIS_HOST: process.env.E2E_REDIS_HOST ?? 'localhost',
        REDIS_PORT: process.env.E2E_REDIS_PORT ?? '16379',
        SPRING_DATA_REDIS_DATABASE: '5',
        // Windows 上 Docker Desktop 的端口转发偶有 1–2 秒的停顿（实测），测试环境放宽超时，避免令牌注销查询超时被当作已注销
        SPRING_DATA_REDIS_TIMEOUT: '10s',
      },
    },
    {
      // 用生产构建测：与部署的产物一致，也避免开发服务器首次访问时重新打包依赖导致页面重载
      command: `npx vite build && npx vite preview --port ${WEB_PORT} --strictPort`,
      url: `http://localhost:${WEB_PORT}`,
      timeout: 240_000,
      reuseExistingServer: false,
      env: { RBAC_API: `http://localhost:${API_PORT}` },
    },
  ],
})
