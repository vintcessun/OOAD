import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const root = new URL('../', import.meta.url)
const text = (path) => readFileSync(new URL(path, root), 'utf8')

function javaSources(directory) {
  return readdirSync(new URL(directory + '/', root), { withFileTypes: true }).flatMap((entry) => {
    const path = directory + '/' + entry.name
    return entry.isDirectory() ? javaSources(path) : entry.name.endsWith('.java') ? [[path, text(path)]] : []
  })
}

function selectModule(module) {
  return spawnSync(process.env.BASH_TEST_SHELL ?? 'bash', [
    '-c', '. ./deploy/backend-module.sh; select_backend_module && printf "%s" "$MODULE"',
  ], {
    cwd: fileURLToPath(root),
    env: { ...process.env, MODULE: module },
    encoding: 'utf8',
    timeout: 10000,
  })
}

// 本测试只检查工程和配置边界，不代替真实HTTP启动及两后端业务契约测试。
test('M2: 同一部署定义默认结构化后端，并可选择独立DDD镜像', () => {
  assert.match(text('pom.xml'), /<module>rbac-ddd<\/module>/)
  const compose = text('docker-compose.yml')
  assert.match(compose, /^  rbac-backend:/m)
  assert.doesNotMatch(compose, /^  rbac-(?:structured|ddd):/m)
  assert.match(compose, /image:.*\$\{MODULE:-rbac-structured\}/)
  assert.match(compose, /MODULE:.*\$\{MODULE:-rbac-structured\}/)
  // RBAC1 permits a port override, but the backend and both proxies must share it.
  const service = (name) => compose.match(new RegExp(`^  ${name}:\\n([\\s\\S]*?)(?=^  [a-z][\\w-]*:|^networks:)`, 'm'))?.[1]
  const backend = service('rbac-backend'), web = service('rbac-web')
  assert.ok(backend && web, 'shared backend and frontend services must exist')
  assert.match(backend, /SERVER_PORT: \$\{RBAC_PORT:-8081\}/)
  assert.match(web, /RBAC_PORT: \$\{RBAC_PORT:-8081\}/)
  // Swarm readiness is not guaranteed by depends_on; isolation is a real boundary.
  for (const name of ['rbac-backend', 'course-backend', 'mysql', 'redis']) {
    assert.ok(service(name), name)
    assert.doesNotMatch(service(name), /^    ports:/m, `${name} must not publish private ports`)
    assert.match(service(name), /networks: \[private\]/)
  }
  assert.match(compose, /IDENTITY_BASE_URL: http:\/\/rbac-backend:\$\{RBAC_PORT:-8081\}/)
  const nginx = text('rbac-web/nginx.conf')
  assert.match(nginx, /proxy_pass http:\/\/rbac-backend:\$\{RBAC_PORT\};/)
  assert.match(nginx, /listen 443 ssl;/)
  assert.match(nginx, /location ~ \^\/\(integration\|identity\)\(\/\|\$\) \{ return 403; \}/)
  assert.match(nginx, /proxy_set_header X-Audit-Service-Key "";/)
})

test('RBAC1: DDD拥有独立数据库缓存适配，绝不复用结构化业务', () => {
  const pom = text('rbac-ddd/pom.xml')
  assert.match(pom, /<artifactId>rbac-contract<\/artifactId>/)
  assert.doesNotMatch(pom, /<artifactId>rbac-structured<\/artifactId>/)
  // M2's no-database skeleton has been replaced by real independent adapters.
  for (const dependency of ['spring-boot-starter-data-jpa', 'spring-boot-starter-data-redis', 'flyway-core', 'flyway-mysql', 'mysql-connector-j']) {
    assert.ok(pom.includes(`<artifactId>${dependency}</artifactId>`), dependency)
  }
  const sources = javaSources('rbac-ddd/src/main/java')
  assert.ok(sources.length > 20, 'must inspect actual production implementation, not an empty skeleton')
  for (const [path, source] of sources) {
    assert.doesNotMatch(source, /\bcom\.xmu\.rbac\.structured\b/, path)
    if (path.includes('/domain/')) {
      assert.doesNotMatch(source, /^import (?:org\.springframework|jakarta\.persistence|com\.fasterxml\.jackson|com\.xmu\.rbac\.ddd\.(?:application|infrastructure|interfaces))\b/m, path)
      assert.doesNotMatch(source, /\b(?:JpaRepository|@Entity|@PersistenceContext)\b/, path)
    }
    if (path.includes('/application/')) {
      assert.doesNotMatch(source, /^import (?:jakarta\.persistence|org\.springframework\.data\.jpa|com\.xmu\.rbac\.ddd\.(?:infrastructure|interfaces))\b/m, path)
    }
  }
  for (const path of ['domain/Role.java', 'domain/RoleHierarchy.java', 'domain/User.java', 'domain/DepartmentEligibility.java', 'domain/AuthorizationPolicy.java', 'application/RoleApplicationService.java', 'infrastructure/persistence/JpaAuthorizationRepositoryAdapter.java', 'infrastructure/cache/VersionedEffectiveCache.java']) {
    assert.ok(sources.some(([name]) => name.endsWith('/' + path)), path)
  }
})

test('M2: 模块选择默认structured，显式ddd可用，非法值在副作用前拒绝', () => {
  const defaults = selectModule('')
  assert.equal(defaults.status, 0, defaults.stderr)
  assert.equal(defaults.stdout, 'rbac-structured')
  const ddd = selectModule('rbac-ddd')
  assert.equal(ddd.status, 0, ddd.stderr)
  assert.equal(ddd.stdout, 'rbac-ddd')
  const invalid = selectModule('../../arbitrary-module')
  assert.notEqual(invalid.status, 0)
  assert.equal(invalid.stdout, '')
  assert.match(invalid.stderr, /MODULE/)
})

test('M2: 前端业务调用不绑定具体后端实现', () => {
  assert.match(text('rbac-web/src/api/http.ts'), /baseURL: '\/api\/v1'/)
  assert.match(text('rbac-web/vite.config.ts'), /process\.env\.RBAC_API/)
})

test('RBAC1: 68项OAS操作唯一且包含鉴权接口、私有投影与部门启停', () => {
  const oas = text('rbac-contract/src/main/resources/openapi/rbac-api.yaml')
  const operationIds = [...oas.matchAll(/^      operationId: (\S+)$/gm)].map((match) => match[1])
  assert.equal(operationIds.length, 68)
  assert.ok(operationIds.includes('setDepartmentStatus'))
  assert.equal(new Set(operationIds).size, operationIds.length)
  assert.equal([...oas.matchAll(/^      x-required-permission:/gm)].length, operationIds.length)
  for (const path of [
    '/auth/login', '/auth/logout', '/auth/me', '/auth/permissions/{permission}', '/auth/api-permissions/{operationId}',
    '/auth/departments', '/auth/departments/{departmentId}/users', '/auth/users/{userId}', '/auth/audit-logs',
    '/integration/authorities', '/identity/users/{userId}', '/.well-known/jwks.json',
  ]) {
    assert.ok(oas.includes(`  ${path}:`), path)
  }
  assert.match(oas, /- url: http:\/\/localhost:8081\n/)
  for (const path of ['/users:', '/roles:', '/departments/{id}/status:', '/authorization/check:', '/authorization/explain:']) assert.ok(oas.includes('  /api/v1' + path), path)
  assert.doesNotMatch(oas, /^  \/api\/v1\/auth\/(?:login|logout|me):/m)
})

test('RBAC1: V1–V8与15表，禁止恢复一期模型或公开root口令', () => {
  const directory = 'rbac-contract/src/main/resources/db/migration/'
  assert.deepEqual(readdirSync(new URL(directory, root)).filter((name) => /^V.*\.sql$/.test(name)).sort(), [
    'V1__rbac1_schema.sql', 'V2__rbac1_seed.sql', 'V3__role_status_permission.sql', 'V4__course_identity_directory.sql',
    'V5__department_lifecycle_permissions.sql', 'V6__use_effective_role_codes.sql',
    'V7__drop_role_identity_marker.sql', 'V8__teacher_console_permissions.sql',
  ])
  const ddl = text(directory + 'V1__rbac1_schema.sql'), seed = text(directory + 'V2__rbac1_seed.sql')
  const tables = [...ddl.matchAll(/^CREATE TABLE (sys_\w+) \(/gm)].map((match) => match[1]).sort()
  assert.deepEqual(tables, ['sys_system', 'sys_department', 'sys_user', 'sys_role', 'sys_permission', 'sys_role_permission', 'sys_user_role', 'sys_department_allowed_role', 'sys_role_hierarchy', 'sys_ui_resource', 'sys_api_operation', 'sys_ui_api_mapping', 'sys_token_revocation', 'sys_grant_version', 'sys_audit_log'].sort())
  assert.doesNotMatch(ddl, /\b(?:company_id|scope_dept_id|self_only|sys_company|sys_function_module|sys_action|sys_menu)\b/)
  assert.match(text(directory + 'V7__drop_role_identity_marker.sql'), /DROP COLUMN identity_role/)
  assert.match(ddl, /UNIQUE\(system_id,kind,code\)/)
  assert.match(ddl, /CHECK\(senior_role_id<>junior_role_id\)/)
  assert.match(ddl, /request_id CHAR\(36\) NOT NULL UNIQUE/)
  assert.match(ddl, /AUTO_INCREMENT=10000/)
  assert.match(seed, /'rbac-u1', 'root', 'UNINITIALIZED'/)
  assert.equal([...seed.matchAll(/^INSERT INTO sys_user \(/gm)].length, 1)
  assert.equal([...seed.matchAll(/^INSERT INTO sys_user_role \(/gm)].length, 1, 'only root may receive a seed assignment')
  for (const role of ['SUPER_ADMIN', 'RBAC_ADMIN', 'AUDITOR', 'teacher', 'student', 'admin']) assert.ok(seed.includes(`'${role}'`), role)
})

test('RBAC1: 根路由与严格DTO保持，已验证实现标识不冒充外部部署', () => {
  for (const module of ['rbac-structured', 'rbac-ddd']) {
    const config = text(module + '/src/main/resources/application.yml')
    assert.doesNotMatch(config, /context-path:/)
    assert.match(config, /fail-on-unknown-properties: true/)
    assert.match(config, /base-path: \/api\/v1\/actuator/)
    assert.match(config, /include: health,info/)
    assert.match(config, /allow-ephemeral: false/)
    assert.match(config, /RBAC_JWT_PRIVATE_KEY_FILE/)
    assert.match(config, /RBAC_JWT_PUBLIC_KEY_FILE/)
  }
  const health = text('rbac-ddd/src/main/java/com/xmu/rbac/ddd/infrastructure/health/ImplementationHealthIndicator.java')
  assert.match(health, /"backend", "ddd"/)
  assert.match(health, /"stage", "M5-RBAC1-verified"/)
  assert.match(health, /"businessReady", true/)
  assert.match(health, /实现完成不等于外部部署验收/)
  assert.doesNotMatch(health, /System\.getenv|Environment|private-key|password/)
})
