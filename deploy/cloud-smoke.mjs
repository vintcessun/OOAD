import assert from 'node:assert/strict';
import {randomBytes} from 'node:crypto';
import {readFile, writeFile} from 'node:fs/promises';

const center = 'https://127.0.0.1:8443';
const course = 'https://127.0.0.1:9443';
const runtime = '/runtime';
const system = 'course-project-management';
const accounts = [
  {role: 'teacher', no: 'CLOUD-T1', name: 'Cloud Test Teacher'},
  {role: 'student', no: 'CLOUD-S1', name: 'Cloud Test Student'},
];
const bootstrap = (await readFile(runtime + '/bootstrap-password', 'utf8')).trim();

async function accountPassword(username, create = false) {
  const file = runtime + '/login-' + username;
  if (create) {
    try {
      await writeFile(file, 'Cloud!A1' + randomBytes(24).toString('hex'), {flag: 'wx', mode: 0o600});
    } catch (error) {
      if (error.code !== 'EEXIST') throw error;
    }
  }
  return (await readFile(file, 'utf8')).trim();
}

async function request(origin, path, {method = 'GET', status = 200, token, body} = {}) {
  const response = await fetch(origin + path, {
    method,
    headers: {
      ...(token ? {Authorization: 'Bearer ' + token} : {}),
      ...(body ? {'Content-Type': 'application/json'} : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
    redirect: 'error',
    signal: AbortSignal.timeout(20_000),
  });
  const result = await response.json().catch(() => undefined);
  if (status !== null) {
    assert.equal(response.status, status, method + ' ' + path + ' ' + (result?.code ?? ''));
    console.log(method, path, response.status);
  }
  return {status: response.status, result};
}

async function login(username, password, audience, status = 200) {
  return request(center, '/auth/login', {
    method: 'POST', body: {username, password, systems: [audience]}, status,
  });
}

async function preparePassword(username, initial, changed, audience) {
  const current = await login(username, changed, audience, null);
  if (current.status === 200) return current.result.accessToken;
  assert.equal(current.status, 401, 'Check whether the account already uses a different password');
  const first = await login(username, initial, audience);
  await request(center, '/api/v1/auth/password', {
    method: 'POST', token: first.result.accessToken,
    body: {oldPassword: initial, newPassword: changed},
  });
  return (await login(username, changed, audience)).result.accessToken;
}

async function provision() {
  const operatorPassword = await accountPassword('root', true);
  const token = await preparePassword('root', bootstrap, operatorPassword, 'rbac-center');
  async function manage(method, path, body) {
    const {result} = await request(center, '/api/v1' + path, {method, body, token});
    assert.equal(result.code, 'OK');
    return result.data;
  }
  const [root] = await manage('GET', '/departments/tree');
  const code = 'CLOUD_SMOKE';
  const existing = (root.children ?? []).find(item => item.code === code);
  const department = existing ?? await manage('POST', '/departments', {
    parentId: root.id, code, name: 'Cloud integration smoke test',
  });
  const graph = await manage('GET', '/systems/' + system + '/role-hierarchy');
  for (const account of accounts) {
    const role = graph.roles.find(item => item.code === account.role);
    assert(role?.builtin && role.status === 'ENABLED', 'Enabled built-in course role is required: ' + account.role);
    account.roleId = role.id;
  }
  await manage('PUT', '/departments/' + department.id + '/assignable-roles', {
    roleIds: accounts.map(account => account.roleId),
  });
  for (const account of accounts) {
    const password = await accountPassword(account.no, true);
    const initial = password + 'Init';
    const users = await manage('GET', '/users?' + new URLSearchParams({keyword: account.no}));
    const user = users.items.find(item => item.username === account.no) ??
      await manage('POST', '/users', {
        username: account.no, no: account.no, name: account.name,
        departmentId: department.id, password: initial,
      });
    assert.equal(user.departmentId, department.id);
    assert.match(user.subject, /^[A-Za-z0-9_.:-]+$/);
    account.subject = user.subject;
    const assignments = '/users/' + user.id + '/role-assignments';
    if (!(await manage('GET', assignments)).some(item => item.roleId === account.roleId)) {
      await manage('POST', assignments, {roleId: account.roleId});
    }
    await preparePassword(account.no, initial, password, system);
  }
  await writeFile(runtime + '/smoke-subjects.json', JSON.stringify(accounts), {mode: 0o600});
  const [teacher, student] = accounts;
  const sql = `
START TRANSACTION;
INSERT INTO teacher(user_id,employee_no,name,creator,created_at,modifier,modi_at)
VALUES ('${teacher.subject}','CLOUD-T1','Cloud Test Teacher','cloud-smoke',NOW(6),'cloud-smoke',NOW(6))
ON DUPLICATE KEY UPDATE user_id=VALUES(user_id);
INSERT INTO student(id,user_id,student_no,name,status,creator,created_at,modifier,modi_at)
VALUES (619001,'${student.subject}','CLOUD-S1','Cloud Test Student','active','cloud-smoke',NOW(6),'cloud-smoke',NOW(6))
ON DUPLICATE KEY UPDATE user_id=VALUES(user_id);
INSERT INTO term(id,name,starts_on,ends_on,is_current,creator,created_at,modifier,modi_at)
VALUES (619001,'Cloud Smoke Term','2026-01-01','2036-12-31',FALSE,'cloud-smoke',NOW(6),'cloud-smoke',NOW(6))
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO course(id,term_id,code,name,description,work_mode,status,creator,created_at,modifier,modi_at)
VALUES (619001,619001,'CLOUD-SMOKE','Cloud Smoke Course','','group',3,'cloud-smoke',NOW(6),'cloud-smoke',NOW(6))
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO course_leader_assignment(course_id,teacher_id,creator,created_at,modifier,modi_at)
SELECT 619001,id,'cloud-smoke',NOW(6),'cloud-smoke',NOW(6) FROM teacher WHERE user_id='${teacher.subject}'
ON DUPLICATE KEY UPDATE teacher_id=VALUES(teacher_id);
INSERT INTO teaching_class(id,course_id,no,name,creator,created_at,modifier,modi_at)
VALUES (619001,619001,1,'Cloud Smoke Class','cloud-smoke',NOW(6),'cloud-smoke',NOW(6))
ON DUPLICATE KEY UPDATE id=VALUES(id);
INSERT INTO class_teacher_assignment(class_id,teacher_id,creator,created_at,modifier,modi_at)
SELECT 619001,id,'cloud-smoke',NOW(6),'cloud-smoke',NOW(6) FROM teacher WHERE user_id='${teacher.subject}'
ON DUPLICATE KEY UPDATE teacher_id=VALUES(teacher_id);
INSERT INTO enrollment(class_id,student_id,course_id,source,creator,created_at,modifier,modi_at)
VALUES (619001,619001,619001,'manual','cloud-smoke',NOW(6),'cloud-smoke',NOW(6))
ON DUPLICATE KEY UPDATE student_id=VALUES(student_id);
COMMIT;
`;
  await writeFile(runtime + '/smoke.sql', sql, {mode: 0o600});
  console.log('Synthetic accounts provisioned');
}

async function verify() {
  const admin = await login('root', await accountPassword('root'), 'rbac-center');
  await request(center, '/api/v1/departments/tree', {token: admin.result.accessToken});
  const subjects = JSON.parse(await readFile(runtime + '/smoke-subjects.json', 'utf8'));
  await request(course, '/api/terms', {status: 401});
  for (const account of subjects) {
    const {result} = await request(course, account.role === 'teacher' ? '/login' : '/api/login', {
      method: 'POST', body: {role: account.role, account: account.no, password: await accountPassword(account.no)},
    });
    const token = result.accessToken;
    assert(token);
    const me = await request(course, '/api/me', {token});
    assert.equal(me.result.userId, account.subject);
    const detail = await request(course, '/api/courses/619001', {token});
    assert.equal(detail.result.name, 'Cloud Smoke Course');
    if (account.role === 'student') {
      await request(course, '/api/courses/copy', {
        method: 'POST', status: 403, token,
        body: {sourceCourseId: 619001, targetTermId: 619001, code: 'CLOUD-DENIED'},
      });
    }
    await request(course, '/auth/logout', {method: 'POST', status: 204, token});
  }
  console.log('Course login and authorization checks passed');
}

const command = process.argv[2];
if (command === 'provision') await provision();
else if (command === 'verify') await verify();
else throw new Error('Expected provision or verify');
