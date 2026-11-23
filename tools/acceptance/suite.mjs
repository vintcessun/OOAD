import assert from 'node:assert/strict';
import { randomUUID, createPublicKey, verify, sign, createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';
import { clearOwnedNamespace, countOwnedNamespace } from './redis.mjs';
import { createNormalizer } from './normalization.mjs';
import { fetchWithoutRedirect } from './http.mjs';
export { normalize } from './normalization.mjs';

const C='course-project-management', B='rbac-center';
const fixturePassword='Fixture!NotReal2026a';
function keys(o, expected) { assert.deepEqual(Object.keys(o).sort(), [...expected].sort()); }
function identity(roles) {assert.ok(Array.isArray(roles));for(const r of roles) assert.ok(['student','teacher','admin'].includes(r), 'course role codes only');}
export async function runSuite(f, record) {
  let root, user, token, department, sibling, role, ui, apiPermission, operation;
  const observations=[];
  const normalizer=createNormalizer();
  const normalize=value=>normalizer.normalize(value);
  // The only numeric seed PK used here is the frozen bootstrap root identity.
  normalizer.register('user',1,'root');
  async function request(method,path,body,auth=root,i=0,headers={}) {
    const observation=normalizer.observeRequest(method,path,body);
    const comparablePath=normalizer.normalizePath(path);
    let r;
    try {r=await fetchWithoutRedirect(`http://127.0.0.1:${f.ports[i]}${path}`,{method,headers:{...(body===undefined?{}:{'Content-Type':'application/json'}),...(auth?{Authorization:`Bearer ${auth}`} : {}),...headers},...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(15000)});} catch (cause) {
      const detail=[cause?.name,cause?.code,cause?.cause?.code].filter(v=>/^[A-Za-z0-9_]+$/.test(String(v))).join('/');
      const redirect=String(cause?.cause?.message)==='unexpected redirect';
      const e=new Error(redirect?'Owned HTTP instance returned a forbidden redirect':`Owned HTTP instance unreachable or timed out (${detail||'transport failure'}): ${method} ${comparablePath}`);
      e.name=redirect?'AssertionError':'TransportError';throw e;
    }
    const text=await r.text(); let data;
    try {data=text?JSON.parse(text):null;} catch {throw new Error(`${method} ${path}: non-JSON HTTP ${r.status}`);}
    // Only structured non-secret observations; never persist full HTTP bodies.
    const normalizedResponse=normalizer.observeResponse(observation,data);
    const comparable = path==='/auth/login'||path==='/.well-known/jwks.json' ? null : normalizedResponse;
    observations.push({method,path:comparablePath,status:r.status,code:data?.code??null,responseDigest:createHash('sha256').update(JSON.stringify(comparable)).digest('hex')});
    return {status:r.status,body:data};
  }
  function status(r,expected) {assert.equal(r.status,expected,`HTTP expected ${expected}, received ${r.status} (${r.body?.code??'no error code'})`); if(expected>=400) {assert.equal(typeof r.body?.code,'string');assert.equal(typeof r.body?.message,'string');assert.ok(!JSON.stringify(r.body).includes('java.lang.'));}return r.body;}
  function rejects(response, http, code) {
    const body = status(response, http);
    assert.equal(body.code, code);
  }
  async function ok(method,path,body,auth=root) {
    const r=await request(method,'/api/v1'+path,body,auth);assert.ok(r.status>=200&&r.status<300,`${method} ${path}: expected success, received ${r.status} (${r.body?.code??'no code'})`);
    if(r.status===204) {assert.equal(r.body,null);return null;}assert.equal(r.body.code,'OK');assert.equal(r.body.message,'成功');assert.ok(Number.isFinite(Date.parse(r.body.timestamp)));return r.body.data;
  }
  async function login(username,password,system) {
    const b=status(await request('POST','/auth/login',{username,password,systems:[system]},null),200);
    keys(b,['accessToken','tokenType','expiresIn']);assert.equal(b.tokenType,'Bearer');assert.ok(b.expiresIn>0);return b.accessToken;
  }
  async function freshLogin(username,password,system) {
    let t=await login(username,password,system);
    // Contract allows force-change on first login. Explicitly perform it for every new account/root.
    const next=fixturePassword+'X';await ok('POST','/auth/password',{oldPassword:password,newPassword:next},t);
    status(await request('GET','/auth/me',undefined,t),401);
    return login(username,next,system);
  }
  async function scenario(name,fn) {
    const start=Date.now(), mark=observations.length;
    try {const result=await fn();await record({scenario:name,status:'pass',durationMs:Date.now()-start,observations:observations.slice(mark),result:normalize(result??null)});}
    catch(e) {await record({scenario:name,status:'fail',classification:e.name==='TransportError'||e.name==='FixtureEnvironmentError'||['ECONNREFUSED','ETIMEDOUT','PROTOCOL_CONNECTION_LOST'].includes(e.code)?'ENVIRONMENT_BLOCKED':e.name==='AssertionError'?'CONTRACT_RED':'EXECUTION_ERROR',durationMs:Date.now()-start,observations:observations.slice(mark),error:['AssertionError','TransportError'].includes(e.name) ? String(e.message).slice(0,500) : 'Scenario execution failed (no secret-bearing exception retained)'});}
  }
  async function makeRole(code, system=C, baseCode) {
    const role = await ok('POST','/roles',{systemCode:system,code,name:code});
    if (baseCode) {
      const graph = await ok('GET', `/systems/${system}/role-hierarchy`);
      const base = graph.roles.find(item => item.code === baseCode);
      assert.ok(base, `Required base role ${system}/${baseCode} is missing`);
      if (system === C) assert.equal(base.builtin, true, `${baseCode} must be built in`);
      status(await edge(role, base), 200);
    }
    return role;
  }
  async function makePermission(code,kind='API',system=C) {
    // Audit capabilities are explicit catalog seeds; reuse by exact system/kind/code, not by numeric ID.
    const existing = await ok('GET','/permissions?'+new URLSearchParams({systemCode:system,kind,keyword:code,pageSize:'100'}));
    const permission = existing.items.find(item => item.code === code && item.kind === kind && item.systemCode === system);
    return permission ?? ok('POST','/permissions',{systemCode:system,code,name:code,kind});
  }
  async function grant(r,permissions) {await ok('PUT',`/roles/${r.id}/permissions`,{permissionIds:permissions.map(p=>p.id)});}
  async function edge(a,b) {return request('POST',`/api/v1/roles/${a.id}/inherits/${b.id}`);}
  async function effective(r) {return ok('GET',`/roles/${r.id}/effective-permissions`);}
  async function allowed(t,permission=ui.code,i=0) {const b=status(await request('GET',`/auth/permissions/${encodeURIComponent(permission)}`,undefined,t,i),200);keys(b,['allowed']);return b.allowed;}
  async function eligible(dep,roles) {await ok('PUT',`/departments/${dep.id}/assignable-roles`,{roleIds:roles.map(r=>r.id)});}
  async function assign(u,r) {return ok('POST',`/users/${u.id}/role-assignments`,{roleId:r.id});}
  async function createUser(username,dep) {return ok('POST','/users',{username,name:username,departmentId:dep.id,password:fixturePassword,no:`FIX-${username}`});}
  async function createDepartment(parentId, code) {
    return ok('POST', '/departments', {parentId, code, name: code});
  }
  async function storedAssignments(userId) {
    const [rows] = await f.db.query('SELECT id, role_id FROM sys_user_role WHERE user_id=? ORDER BY id', [userId]);
    return rows;
  }
  async function storedEligibility(departmentId) {
    const [rows] = await f.db.query('SELECT role_id FROM sys_department_allowed_role WHERE department_id=? ORDER BY role_id', [departmentId]);
    return rows.map(row => row.role_id);
  }
  async function grantEffects() {
    const [[row]] = await f.db.query("SELECT version, (SELECT COUNT(*) FROM sys_audit_log WHERE result='SUCCESS') successes FROM sys_grant_version WHERE id=1");
    return row;
  }
  const auditHeaders={'X-Audit-Service-Key':f.serviceKey};
  const auditBody=()=>({requestId:randomUUID(),type:'score_changed',objectType:'student',objectId:'cpm-student-42',target:'fixture target',before:{status:'DRAFT'},after:{status:'PUBLISHED'}});
  const audit=(body,t=token,headers=auditHeaders)=>request('POST','/auth/audit-logs',body,t,0,headers);

  await scenario('SETUP | root password change and isolated fixture through HTTP',async()=>{
    root=await freshLogin('root',f.bootstrap,B);
    const [[hq]]=await f.db.query('SELECT id FROM sys_department WHERE parent_id IS NULL ORDER BY id LIMIT 1');assert.ok(hq,'Headquarters seed required');normalizer.register('department',hq.id,['seed','headquarters']);
    department=await ok('POST','/departments',{parentId:hq.id,code:'FX_DEPT',name:'Fixture department'});
    sibling=await ok('POST','/departments',{parentId:hq.id,code:'FX_SIBLING',name:'Fixture sibling'});
    // The independent acceptance database uses custom permission combinations.
    const {roles: courseRoles}=await ok('GET','/systems/'+C+'/role-hierarchy');
    for (const code of ['teacher', 'student', 'admin']) {
      const base = courseRoles.find(item => item.code === code);
      assert.ok(base, `Built-in course role ${code} is missing`);
      assert.equal(base.builtin, true);
      assert.equal(base.status, 'ENABLED');
      assert.ok(!('identityRole' in base));
      const permissions = await ok('GET', `/roles/${base.id}/permissions`);
      assert.ok(permissions.some(p => p.kind === 'API' && p.code === 'course:access'));
      if (code !== 'admin') await grant(base, []);
    }
    role=await makeRole('FX_TEACHER',C,'teacher');ui=await makePermission('fixture:button','UI');apiPermission=await makePermission('fixture:api');
    const ar=await makePermission('audit:read'),aw=await makePermission('audit:write');
    await grant(role,[ui,apiPermission,ar,aw]);await eligible(department,[role]);
    user=await createUser('fixture_user',department);await assign(user,role);token=await freshLogin(user.username,fixturePassword,C);
    operation=await ok('POST',`/systems/${C}/api-operations`,{operationId:'fixtureOperation',method:'POST',pathTemplate:'/fixture/operation',permissionCode:apiPermission.code,accessPolicy:'PROTECTED',enabled:true});
  });
  await scenario('EXT-01 JWT RS256 / JWKS exact claims and bare me',async()=>{
    assert.ok(token,'Fixture token missing');const [h,p,s]=token.split('.');const head=JSON.parse(Buffer.from(h,'base64url')),claims=JSON.parse(Buffer.from(p,'base64url'));
    assert.equal(head.alg,'RS256');assert.equal(head.kid,f.keys.kid);assert.equal(claims.iss,f.keys.issuer);assert.deepEqual(Array.isArray(claims.aud)?claims.aud:[claims.aud],[C]);assert.equal(claims.sub,user.subject);assert.ok(claims.iat<=Date.now()/1000);assert.ok(claims.exp>claims.iat);assert.ok(claims.jti);assert.ok(Number.isInteger(claims.tokenVersion));assert.ok(!('roles'in claims));assert.ok(!('permissions'in claims));
    const jwks=status(await request('GET','/.well-known/jwks.json',undefined,null),200);const jwk=jwks.keys.find(k=>k.kid===head.kid);assert.equal(jwk.kty,'RSA');assert.ok(!jwk.d);assert.ok(verify('RSA-SHA256',Buffer.from(`${h}.${p}`),createPublicKey({key:jwk,format:'jwk'}),Buffer.from(s,'base64url')));
    const me=status(await request('GET','/auth/me',undefined,token),200);keys(me,['subject','roles','permissions','name','no']);identity(me.roles);assert.deepEqual(me.roles,['teacher']);assert.ok(me.permissions.includes(ui.code));assert.ok(!me.permissions.includes(apiPermission.code));
    const inherited = await effective(role);
    assert.deepEqual(inherited.directRoles.map(item => item.code), [role.code]);
    assert.deepEqual(inherited.effectiveRoles.map(item => item.code).sort(), [role.code, 'teacher'].sort());
    assert.equal(await allowed(token),true);const decision=status(await request('GET','/auth/api-permissions/fixtureOperation',undefined,token),200);keys(decision,['operationId','allowed']);assert.deepEqual(decision,{operationId:'fixtureOperation',allowed:true});return me;
  });
  await scenario('EXT-01 login input boundaries and invalid credentials',async()=>{
    status(await request('POST','/auth/login',{username:'fixture_user',password:'NotTheFixturePassword!1',systems:[C]},null),401);
    for(const body of [{username:'',password:'fixture',systems:[C]},{username:'x'.repeat(129),password:'fixture',systems:[C]},{username:'fixture_user',password:'x'.repeat(257),systems:[C]},{username:'fixture_user',password:'fixture',systems:[]},{username:'fixture_user',password:'fixture',systems:[C,B]},{username:'fixture_user',password:'fixture',systems:[C],roles:['admin']}]) status(await request('POST','/auth/login',body,null),400);
  });
  await scenario('AUTHZ simulation UI API explanation and inactive target never invalidates caller',async()=>{
    const check={systemCode:C,userId:user.id,kind:'UI',permission:ui.code};
    const decision=await ok('POST','/authorization/check',check);
    keys(decision,['allowed','reason','traceId']);assert.equal(decision.allowed,true);assert.equal(decision.reason,'ALLOW');
    const explanation=await ok('POST','/authorization/explain',check);
    keys(explanation,['allowed','reason','traceId','trace','effective']);assert.equal(explanation.allowed,true);
    assert.deepEqual(explanation.trace,['TOKEN_VALID','USER_SYSTEM_ACTIVE','REQUIREMENT_SATISFIED']);
    assert.ok(explanation.effective.permissions.some(p=>p.code===ui.code));
    assert.equal((await ok('POST','/authorization/check',{systemCode:C,userId:user.id,kind:'API',operationId:'fixtureOperation'})).allowed,true);
    const denied=await ok('POST','/authorization/check',{...check,permission:'missing-fixture-ui'});assert.equal(denied.allowed,false);assert.equal(denied.reason,'DENY');
    status(await request('POST','/api/v1/authorization/check',{...check,operationId:'fixtureOperation'}),400);
    await ok('PUT',`/users/${user.id}/status`,{status:'DISABLED'});
    const inactive=await ok('POST','/authorization/explain',check);assert.equal(inactive.allowed,false);assert.equal(inactive.reason,'USER_DISABLED');
    assert.deepEqual(inactive.trace,['ACTOR_AUTHENTICATED','USER_DISABLED']);assert.deepEqual(inactive.effective.permissions,[]);
    status(await request('GET','/auth/me',undefined,root),200);
    await ok('PUT',`/users/${user.id}/status`,{status:'ACTIVE'});
    token=await login(user.username,fixturePassword+'X',C);
  });
  await scenario('NFR-02 reject signed wrong aud iss kid future iat expired exp and forged signature',async()=>{
    const [h,p]=token.split('.'),header=JSON.parse(Buffer.from(h,'base64url')),payload=JSON.parse(Buffer.from(p,'base64url'));
    for(const change of [{aud:'unknown-system'},{aud:[B,C]},{aud:null},{iss:'evil'},{iss:null},{iat:null},{exp:null},{jti:null},{sub:null},{iat:Math.floor(Date.now()/1000)+3600},{exp:Math.floor(Date.now()/1000)-1}]) {
      const a=Buffer.from(JSON.stringify(header)).toString('base64url'),b=Buffer.from(JSON.stringify({...payload,...change})).toString('base64url');const sig=sign('RSA-SHA256',Buffer.from(`${a}.${b}`),f.keys.privateKey).toString('base64url');status(await request('GET','/auth/me',undefined,`${a}.${b}.${sig}`),401);
    }
    const a=Buffer.from(JSON.stringify({...header,kid:'unknown-key'})).toString('base64url');status(await request('GET','/auth/me',undefined,`${a}.${p}.${sign('RSA-SHA256',Buffer.from(`${a}.${p}`),f.keys.privateKey).toString('base64url')}`),401);
    status(await request('GET','/auth/me',undefined,`${h}.${p}.${Buffer.alloc(256).toString('base64url')}`),401);
    status(await request('GET','/auth/me',undefined,null),401);
  });
  await scenario('ID-06 root course aud never grants center / system isolation and disable',async()=>{
    const rt=await login('root',fixturePassword+'X',C);
    for(const t of [token,rt]) {const r=await request('GET','/api/v1/users',undefined,t);assert.ok([401,403].includes(r.status));}
    const centerMe=status(await request('GET','/auth/me',undefined,root),200);identity(centerMe.roles);assert.ok(!centerMe.permissions.includes(ui.code));
    assert.equal(status(await request('GET','/auth/permissions/unknown-fixture-permission',undefined,token),200).allowed,false);
    const missing=status(await request('GET','/auth/api-permissions/unknownFixtureOperation',undefined,token),404);assert.equal(missing.code,'API_OPERATION_NOT_FOUND');
    const systems=await ok('GET','/systems'),course=systems.find(s=>s.code===C);assert.ok(course);
    try {await ok('PUT',`/systems/${course.id}/status`,{status:'DISABLED'});const r=await request('GET','/auth/me',undefined,token);assert.ok([401,403].includes(r.status));} finally {await ok('PUT',`/systems/${course.id}/status`,{status:'ENABLED'});}
  });
  await scenario('ROLE-02/03 DAG single multiple transitive diamond dedup source paths',async()=>{
    const graphRoles=[];for(const n of ['DAG_A','DAG_B','DAG_C','DAG_D']) graphRoles.push(await makeRole(n));const [a,b,c,d]=graphRoles;
    const p=await makePermission('fixture:dag','UI');await grant(d,[p]);
    assert.equal((await effective(a)).permissions.length,0,'no edge grants nothing');
    assert.ok((await edge(a,b)).status<300);assert.equal((await effective(a)).effectiveRoles.length,2,'single parent');
    assert.ok((await edge(b,d)).status<300);assert.ok((await effective(a)).permissions.some(x=>x.code===p.code),'transitive third level');
    for(const [x,y] of [[a,c],[c,d]]) assert.ok((await edge(x,y)).status<300);
    status(await edge(a,b),409);
    const eff=await effective(a);assert.deepEqual(eff.permissions.filter(x=>x.code===p.code).map(x=>x.code),[p.code]);assert.equal(eff.effectiveRoles.length,4);
    for(const source of eff.sources.filter(x=>x.permissionCode===p.code)) {keys(source,['permissionCode','permissionKind','roleCode','inherited','path']);assert.equal(source.permissionKind,p.kind);assert.equal(source.inherited,true);assert.ok(source.path.length>=2);}
    assert.ok(eff.sources.some(x=>x.permissionCode===p.code));
    status(await edge(a,a),409);const cycle=await edge(d,a);status(cycle,409);assert.equal(cycle.body.code,'ROLE_CYCLE');
    const cross=await makeRole('DAG_CENTER',B);assert.ok([400,409].includes((await edge(a,cross)).status));
    await ok('PUT',`/roles/${b.id}/status`,{status:'DISABLED'});assert.ok((await effective(a)).permissions.some(x=>x.code===p.code),'alternate enabled diamond path survives');
    await ok('PUT',`/roles/${c.id}/status`,{status:'DISABLED'});assert.ok(!(await effective(a)).permissions.some(x=>x.code===p.code),'disabled intermediate cuts traversal');
    await ok('PUT',`/roles/${b.id}/status`,{status:'ENABLED'});await ok('PUT',`/roles/${c.id}/status`,{status:'ENABLED'});
    await ok('DELETE',`/roles/${a.id}/inherits/${b.id}`);assert.ok((await effective(a)).permissions.some(x=>x.code===p.code),'delete with alternate path retains grant');
    await ok('DELETE',`/roles/${a.id}/inherits/${c.id}`);assert.ok(!(await effective(a)).permissions.some(x=>x.code===p.code),'delete last path revokes');
    return {roles:4,permission: p.code};
  });
  await scenario('ROLE-03 concurrent reverse edges serialized with DB invariant',async()=>{
    const a=await makeRole('RACE_A'),b=await makeRole('RACE_B');const r=await Promise.all([edge(a,b),edge(b,a)]);
    assert.equal(r.filter(x=>x.status>=200&&x.status<300).length,1);assert.equal(r.filter(x=>x.status===409&&x.body.code==='ROLE_CYCLE').length,1);
    const [[row]]=await f.db.query('SELECT COUNT(*) n FROM sys_role_hierarchy WHERE (senior_role_id=? AND junior_role_id=?) OR (senior_role_id=? AND junior_role_id=?)',[a.id,b.id,b.id,a.id]);assert.equal(row.n,1);
    const [stored]=await f.db.query('SELECT senior_role_id,junior_role_id FROM sys_role_hierarchy WHERE (senior_role_id=? AND junior_role_id=?) OR (senior_role_id=? AND junior_role_id=?)',[a.id,b.id,b.id,a.id]);
    const winner=r.findIndex(x=>x.status===200);assert.ok(winner===0||winner===1);
    assert.deepEqual(stored.map(row=>({...row})),[{senior_role_id:winner===0?a.id:b.id,junior_role_id:winner===0?b.id:a.id}],'Persisted edge must be exactly the request that succeeded, not merely one arbitrary edge');
    return {edges:row.n};
  });
  await scenario('ROLE-05 eligibility even root / PATCH cannot smuggle status department',async()=>{
    const r=await makeRole('NOT_ELIGIBLE');const deny=await request('POST',`/api/v1/users/${user.id}/role-assignments`,{roleId:r.id});assert.ok([403,409].includes(deny.status));
    status(await request('POST',`/api/v1/users/${user.id}/role-assignments`,{roleId:r.id,departmentId:sibling.id}),400);
    for(const body of [{status:'DISABLED'},{departmentId:sibling.id}]) status(await request('PATCH',`/api/v1/users/${user.id}`,body),400);
    status(await request('PATCH',`/api/v1/roles/${role.id}`,{status:'DISABLED'}),400);
  });
  await scenario('ID-05 department move revokes only moved user not shared role',async()=>{
    const peer=await createUser('fixture_peer',department);await assign(peer,role);const pt=await freshLogin(peer.username,fixturePassword,C);
    const before=await ok('GET',`/roles/${role.id}/permissions`);
    // Even when the receiving department allows the same role, a cross-department move clears it.
    await eligible(sibling,[role]);
    await ok('PUT',`/users/${user.id}/department`,{departmentId:sibling.id});
    assert.deepEqual(await ok('GET',`/users/${user.id}/role-assignments?systemCode=${C}`),[]);assert.equal(await allowed(token),false);assert.equal(await allowed(pt),true);
    assert.deepEqual(await ok('GET',`/roles/${role.id}/permissions`),before);
    await ok('PUT',`/users/${user.id}/department`,{departmentId:department.id});await assign(user,role);
  });
  await scenario('RES-03 UI-only no API / mapping kind same-system / unknown deny',async()=>{
    const r=await makeRole('UI_ONLY',C,'teacher');await grant(r,[ui]);await eligible(sibling,[r]);const u=await createUser('fixture_ui_only',sibling);await assign(u,r);const t=await freshLogin(u.username,fixturePassword,C);
    assert.equal(await allowed(t),true);assert.deepEqual(status(await request('GET','/auth/api-permissions/fixtureOperation',undefined,t),200),{operationId:'fixtureOperation',allowed:false});
    status(await audit(auditBody(),t),403);status(await request('GET','/auth/audit-logs',undefined,t,0,auditHeaders),403);
    const menu=await ok('POST',`/systems/${C}/ui-resources`,{type:'MENU',code:'fx_menu',name:'fixture menu',route:'/fixture',permissionCode:ui.code});
    const resource=await ok('POST',`/systems/${C}/ui-resources`,{type:'BUTTON',parentId:menu.id,code:'fx_button',name:'fixture button',permissionCode:ui.code});
    const rejectTypedReference=response=>{assert.ok([400,404,409].includes(response.status));if(response.status===404)assert.equal(response.body.code,'PERMISSION_NOT_FOUND');};
    // permissionCode resolves within the requested system AND kind. A missing
    // typed reference is legitimately 404, not an unimplemented-route success.
    const wrong=await request('POST',`/api/v1/systems/${C}/api-operations`,{operationId:'wrongKind',method:'GET',pathTemplate:'/fixture/wrong',permissionCode:ui.code,accessPolicy:'PROTECTED',enabled:true});rejectTypedReference(wrong);
    const wrongUi=await request('POST',`/api/v1/systems/${C}/ui-resources`,{type:'BUTTON',parentId:menu.id,code:'wrong_ui',name:'fixture',permissionCode:apiPermission.code});rejectTypedReference(wrongUi);
    const cp=await makePermission('fixture:center:api','API',B);const wrongSystem=await request('POST',`/api/v1/systems/${C}/api-operations`,{operationId:'wrongSystem',method:'GET',pathTemplate:'/fixture/wrong-system',permissionCode:cp.code,accessPolicy:'PROTECTED',enabled:true});rejectTypedReference(wrongSystem);
    const [[invalidOps]]=await f.db.query("SELECT COUNT(*) n FROM sys_api_operation WHERE operation_id IN ('wrongKind','wrongSystem')");assert.equal(invalidOps.n,0);
    const [[invalidUi]]=await f.db.query("SELECT COUNT(*) n FROM sys_ui_resource WHERE code='wrong_ui'");assert.equal(invalidUi.n,0);
    await ok('PUT',`/systems/${C}/ui-api-mappings`,{mappings:[{uiResourceId:resource.id,apiOperationId:operation.id}]});assert.equal(status(await request('GET','/auth/api-permissions/fixtureOperation',undefined,t),200).allowed,false);
    const centerOperations=await ok('GET',`/systems/${B}/api-operations?keyword=listUsers&pageSize=100`),centerOp=centerOperations.items.find(x=>x.operationId==='listUsers');assert.ok(centerOp,'Registered center listUsers operation required');
    assert.ok([400,409].includes((await request('PUT',`/api/v1/systems/${C}/ui-api-mappings`,{mappings:[{uiResourceId:resource.id,apiOperationId:centerOp.id}]})).status));
    const unknown=await request('GET','/api/v1/fixture-not-registered',undefined,t);assert.ok([403,404].includes(unknown.status));
  });
  await scenario('EXT-03 frozen real course operation metadata six classes / copyCourse teacher not CC',async()=>{
    // Derived from teacher OAS snapshot 7f27e605f37f540253c3f2ff73227424250a4ec8, not UI labels or names.
    const samples=[['createTerm','POST','/terms','TC','admin'],['copyCourse','POST','/courses/copy','course:access','teacher'],['importRoster','POST','/classes/{classId}/roster/import','course:access',null],['createGroupJoinRequest','POST','/groups/{groupId}/join-requests','course:access','student'],['submitMaterials','POST','/groups/{groupId}/milestones/{milestoneId}/submissions','course:access','student'],['bookOralSlot','POST','/oral-slots/{slotId}/bookings','course:access',null]];
    for(const [operationId,method,pathTemplate,permissionCode,requiredRole] of samples) {const page=await ok('GET',`/systems/${C}/api-operations?keyword=${operationId}&pageSize=100`);const op=page.items.find(x=>x.operationId===operationId);assert.ok(op,`Missing teacher operation ${operationId}`);assert.deepEqual({method:op.method,pathTemplate:op.pathTemplate,permissionCode:op.permissionCode,requiredRole:op.requiredRole,accessPolicy:op.accessPolicy,enabled:op.enabled},{method,pathTemplate,permissionCode,requiredRole,accessPolicy:'PROTECTED',enabled:true});}
    const page=await ok('GET',`/permissions?systemCode=${C}&kind=API&keyword=course%3Aaccess&pageSize=100`),access=page.items.find(p=>p.code==='course:access'&&p.kind==='API');assert.ok(access);const before=await ok('GET',`/roles/${role.id}/permissions`);await grant(role,[...before,access]);assert.ok(!before.some(p=>p.code==='CC'),'teacher fixture has no CC');assert.equal(status(await request('GET','/auth/api-permissions/copyCourse',undefined,token),200).allowed,true);
    const r=await makeRole('FX_STUDENT',C,'student');await grant(r,[access]);const dep=await ok('POST','/departments',{parentId:department.parentId,code:'FX_STUDENT_DEPT',name:'Fixture student'});await eligible(dep,[r]);const u=await createUser('fixture_student',dep);await assign(u,r);const t=await freshLogin(u.username,fixturePassword,C);assert.equal(status(await request('GET','/auth/api-permissions/copyCourse',undefined,t),200).allowed,false);
  });
  await scenario('EXT private service projection never confuses teacher entry with CC and forbids public identity leaks',async()=>{
    status(await request('GET','/integration/authorities',undefined,token),403);
    const projection=status(await request('GET','/integration/authorities',undefined,token,0,auditHeaders),200);
    keys(projection,['authorities']);assert.ok(projection.authorities.includes('ROLE_teacher'));
    assert.ok(projection.authorities.includes(apiPermission.code));assert.ok(!projection.authorities.includes(ui.code));
    assert.ok(!projection.authorities.includes('CC'),'teacher entry qualification is not a CC grant');
    status(await request('GET','/identity/users/'+encodeURIComponent(user.subject),undefined,token,0,auditHeaders),403);
    const builtins=await ok('GET',`/roles?systemCode=${C}&pageSize=100`),admin=builtins.items.find(item=>item.code==='admin');assert.ok(admin);
    const allowedRoles=await ok('GET',`/departments/${department.id}/assignable-roles`);
    await eligible(department,[...allowedRoles,admin].filter((item,index,all)=>all.findIndex(other=>other.id===item.id)===index));
    const operator=await createUser('fixture_course_admin',department);await assign(operator,admin);
    const adminToken=await freshLogin(operator.username,fixturePassword,C);
    const lookup=status(await request('GET','/identity/users/'+encodeURIComponent(user.subject),undefined,adminToken,0,auditHeaders),200);
    keys(lookup,['userId','name','no']);assert.equal(lookup.userId,user.subject);assert.equal(lookup.name,user.name);
    status(await request('GET','/identity/users/missing-fixture-subject',undefined,adminToken,0,auditHeaders),404);
    const lookupPath='/auth/users/'+encodeURIComponent(user.subject);
    assert.deepEqual(status(await request('GET',lookupPath,undefined,adminToken),200),lookup);
    status(await request('GET',lookupPath,undefined,token),403);
    status(await request('GET',lookupPath,undefined,root),403);
    status(await request('GET',lookupPath,undefined,null),401);
    const departments=status(await request('GET','/auth/departments',undefined,adminToken),200);
    keys(departments,['items','page','pageSize','total']);
    const rootDepartment=departments.items.find(d=>d.id===department.parentId);
    assert(rootDepartment?.hasChildren);
    assert.equal(rootDepartment.parentId,null);
    const children=status(await request('GET','/auth/departments?parentId='+rootDepartment.id+'&keyword=FX_DEPT&pageSize=1',undefined,adminToken),200);
    assert.equal(children.total,1);
    assert.equal(children.items[0].id,department.id);
    const members=status(await request('GET','/auth/departments/'+department.id+'/users?keyword='+encodeURIComponent(user.name),undefined,adminToken),200);
    assert.deepEqual(members.items.find(entry=>entry.userId===user.subject),
      {userId:user.subject,no:user.no??'',name:user.name,departmentId:department.id,departmentName:department.name});
    status(await request('GET','/auth/departments?page=0',undefined,adminToken),400);
    status(await request('GET','/auth/departments/999999/users',undefined,adminToken),404);
    const loginPage=await ok('GET','/systems/'+C+'/api-operations?keyword=courseLogin');
    assert.equal(loginPage.items.find(item=>item.operationId==='courseLogin')?.accessPolicy,'PUBLIC');
  });
  await scenario('ROLE-06 nonroot department admin no shared writes or sibling management',async()=>{
    const centerRoles=await ok('GET',`/roles?systemCode=${B}&pageSize=100`),admin=centerRoles.items.find(r=>r.code==='RBAC_ADMIN');assert.ok(admin);
    await eligible(department,[role,admin]);const u=await createUser('fixture_dept_admin',department);await assign(u,admin);const t=await freshLogin(u.username,fixturePassword,B);
    await ok('PATCH',`/users/${u.id}`,{name:'Fixture department administrator'},t);
    status(await request('PATCH',`/api/v1/roles/${role.id}`,{name:'forbidden shared write'},t),403);
    const outsider=await createUser('fixture_sibling',sibling);
    status(await request('PATCH',`/api/v1/users/${outsider.id}`,{name:'forbidden sibling'},t),403);
    status(await request('PUT',`/api/v1/users/${outsider.id}/department`,{departmentId:department.id},t),403);
  });
  await scenario('ORG-07 department edit, status and delete require distinct capabilities and subtree scope', async () => {
    const home = await createDepartment(department.parentId, 'FX_DEPARTMENT_OPERATOR_HOME');
    const operatorRole = await makeRole('FX_DEPARTMENT_OPERATOR', B);
    const permissions = await ok('GET', `/permissions?systemCode=${B}&kind=API&keyword=rbac%3Adepartment%3A`);
    const required = suffix => {
      const permission = permissions.items.find(p => p.code === `rbac:department:${suffix}`);
      assert.ok(permission, `Department ${suffix} API permission must come from the migration`);
      return permission;
    };
    const [edit, changeStatus, remove] = ['edit', 'status', 'delete'].map(required);
    await grant(operatorRole, [edit]);
    await eligible(home, [operatorRole]);
    const operator = await createUser('fixture_department_operator', home);
    await assign(operator, operatorRole);
    const operatorToken = await freshLogin(operator.username, fixturePassword, B);
    const leaf = await ok('POST', '/departments', {
      parentId: home.id, code: 'FX_DEPARTMENT_OPERATOR_LEAF', name: 'Operator leaf',
    }, operatorToken);
    const leafPath = `/departments/${leaf.id}`;
    const guarded = (method, path, body) => request(method, `/api/v1${path}`, body, operatorToken);
    assert.equal((await ok('PATCH', leafPath, {name: 'Renamed leaf'}, operatorToken)).name, 'Renamed leaf');
    rejects(await guarded('PUT', `${leafPath}/status`, {status: 'DISABLED'}), 403, 'PERMISSION_DENIED');
    rejects(await guarded('DELETE', leafPath), 403, 'PERMISSION_DENIED');

    const refuseProfileEdits = async () => {
      rejects(await guarded('POST', '/departments', {parentId: home.id, code: 'FX_FORBIDDEN_CHILD', name: 'Forbidden'}), 403, 'PERMISSION_DENIED');
      rejects(await guarded('PATCH', leafPath, {name: 'Forbidden rename'}), 403, 'PERMISSION_DENIED');
    };
    await grant(operatorRole, [changeStatus]);
    await refuseProfileEdits();
    rejects(await guarded('DELETE', leafPath), 403, 'PERMISSION_DENIED');
    rejects(await guarded('PUT', `/departments/${sibling.id}/status`, {status: 'DISABLED'}), 403, 'OUT_OF_MANAGE_SCOPE');
    status(await guarded('PUT', `${leafPath}/status`, {status: 'ACTIVE'}), 400);
    assert.equal((await ok('PUT', `${leafPath}/status`, {status: 'DISABLED'}, operatorToken)).status, 'DISABLED');
    assert.equal((await ok('PUT', `${leafPath}/status`, {status: 'ENABLED'}, operatorToken)).status, 'ENABLED');

    await grant(operatorRole, [remove]);
    await refuseProfileEdits();
    rejects(await guarded('PUT', `${leafPath}/status`, {status: 'DISABLED'}), 403, 'PERMISSION_DENIED');
    rejects(await guarded('DELETE', `/departments/${sibling.id}`), 403, 'OUT_OF_MANAGE_SCOPE');
    rejects(await guarded('DELETE', `/departments/${home.id}`), 409, 'DEPARTMENT_NOT_EMPTY');
    await eligible(leaf, [operatorRole]);
    assert.equal(await ok('DELETE', leafPath, undefined, operatorToken), null);
    const [remaining] = await f.db.query('SELECT id FROM sys_department WHERE id=?', [leaf.id]);
    assert.deepEqual(remaining, []);
    assert.deepEqual(await storedEligibility(leaf.id), []);
  });
  await scenario('ORG department suspension preserves existing member access and closes new admissions', async () => {
    const parent = await createDepartment(department.parentId, 'FX_LIFECYCLE_PARENT');
    const leaf = await createDepartment(parent.id, 'FX_LIFECYCLE_LEAF');
    const courseRoles = await ok('GET', `/roles?systemCode=${C}&pageSize=100`);
    const courseAdmin = courseRoles.items.find(candidate => candidate.code === 'admin');
    assert.ok(courseAdmin);
    const extraRole = await makeRole('FX_LIFECYCLE_EXTRA');
    await eligible(leaf, [courseAdmin, role, extraRole]);
    await eligible(parent, [extraRole]);
    const member = await createUser('fixture_lifecycle_member', leaf);
    await assign(member, courseAdmin);
    const revocable = await assign(member, role);
    const memberToken = await freshLogin(member.username, fixturePassword, C);
    const outgoing = await createUser('fixture_lifecycle_outgoing', leaf);
    await assign(outgoing, extraRole);
    const incoming = await createUser('fixture_lifecycle_incoming', parent);
    const statusPath = `/departments/${leaf.id}/status`;
    const courseChildren = `/auth/departments?parentId=${parent.id}&keyword=${leaf.code}`;
    const courseMembers = `/auth/departments/${leaf.id}/users`;
    const readCourse = path => request('GET', path, undefined, memberToken);
    assert.equal(status(await readCourse(courseChildren), 200).items[0]?.id, leaf.id);
    assert.equal(status(await readCourse(courseMembers), 200).total, 2);

    const precondition = await grantEffects();
    rejects(await request('PUT', `/api/v1/departments/${department.parentId}/status`, {status: 'DISABLED'}), 409, 'ROOT_DEPARTMENT_DISABLE_FORBIDDEN');
    rejects(await request('PUT', `/api/v1/departments/${parent.id}/status`, {status: 'DISABLED'}), 409, 'DEPARTMENT_HAS_CHILDREN');
    assert.deepEqual(await grantEffects(), precondition);
    const [[memberBefore]] = await f.db.query('SELECT status, token_version, version FROM sys_user WHERE id=?', [member.id]);
    const memberAssignments = await storedAssignments(member.id);

    assert.equal((await ok('PUT', statusPath, {status: 'DISABLED'})).status, 'DISABLED');
    const [[disabled]] = await f.db.query('SELECT status, version FROM sys_department WHERE id=?', [leaf.id]);
    assert.equal(disabled.status, 'DISABLED');
    const afterDisable = await grantEffects();
    assert.deepEqual(afterDisable, {version: precondition.version + 1, successes: precondition.successes + 1});
    const [[statusAudit]] = await f.db.query("SELECT COUNT(*) n FROM sys_audit_log WHERE event_type='DEPARTMENT_STATUS_CHANGED' AND actor_id=1 AND target=? AND result='SUCCESS'", [String(leaf.id)]);
    assert.equal(statusAudit.n, 1);

    assert.equal((await ok('PUT', statusPath, {status: 'DISABLED'})).status, 'DISABLED');
    const departmentTree = await ok('GET', '/departments/tree');
    const parentNode = departmentTree.flatMap(dept => dept.children).find(dept => dept.id === parent.id);
    assert.equal(parentNode?.children.find(dept => dept.id === leaf.id)?.status, 'DISABLED');
    assert.deepEqual(status(await readCourse(courseChildren), 200).items, []);
    rejects(await readCourse(courseMembers), 404, 'NOT_FOUND');
    assert.equal(await allowed(memberToken, ui.code), true);
    const newUser = {username: 'fixture_lifecycle_blocked', name: 'Blocked', departmentId: leaf.id, password: fixturePassword};
    for (const [method, path, body] of [
      ['POST', '/departments', {parentId: leaf.id, code: 'FX_LIFECYCLE_BLOCKED', name: 'Blocked'}],
      ['POST', '/users', newUser],
      ['PUT', `/users/${incoming.id}/department`, {departmentId: leaf.id}],
      ['POST', `/users/${member.id}/role-assignments`, {roleId: extraRole.id}],
    ]) rejects(await request(method, `/api/v1${path}`, body), 409, 'DEPARTMENT_DISABLED');
    assert.deepEqual(await grantEffects(), afterDisable);
    const [[departmentAfter]] = await f.db.query('SELECT status, version FROM sys_department WHERE id=?', [leaf.id]);
    assert.deepEqual(departmentAfter, disabled);
    const [[memberAfter]] = await f.db.query('SELECT status, token_version, version FROM sys_user WHERE id=?', [member.id]);
    assert.deepEqual(memberAfter, memberBefore);
    assert.deepEqual(await storedAssignments(member.id), memberAssignments);
    const [[incomingAfter]] = await f.db.query('SELECT department_id FROM sys_user WHERE id=?', [incoming.id]);
    assert.equal(incomingAfter.department_id, parent.id);
    const [[newRecords]] = await f.db.query("SELECT (SELECT COUNT(*) FROM sys_user WHERE username=?) + (SELECT COUNT(*) FROM sys_department WHERE code='FX_LIFECYCLE_BLOCKED') n", [newUser.username]);
    assert.equal(newRecords.n, 0);

    const outgoingAssignments = await storedAssignments(outgoing.id);
    assert.equal((await ok('PUT', `/users/${outgoing.id}/department`, {departmentId: leaf.id})).departmentId, leaf.id);
    assert.deepEqual(await storedAssignments(outgoing.id), outgoingAssignments);
    assert.equal((await ok('PUT', `/users/${outgoing.id}/department`, {departmentId: parent.id})).departmentId, parent.id);
    assert.deepEqual(await storedAssignments(outgoing.id), []);
    await ok('DELETE', `/users/${member.id}/role-assignments/${revocable.id}`);
    assert.equal(await allowed(memberToken, ui.code), false);

    assert.equal((await ok('PUT', statusPath, {status: 'ENABLED'})).status, 'ENABLED');
    assert.equal(status(await readCourse(courseChildren), 200).items[0]?.id, leaf.id);
    assert.equal(status(await readCourse(courseMembers), 200).total, 1);
    assert.equal((await ok('PUT', `/users/${incoming.id}/department`, {departmentId: leaf.id})).departmentId, leaf.id);
    assert.equal((await assign(member, extraRole)).roleId, extraRole.id);
    assert.equal((await createUser('fixture_lifecycle_reopened', leaf)).departmentId, leaf.id);
    assert.equal((await createDepartment(leaf.id, 'FX_LIFECYCLE_REOPENED_CHILD')).parentId, leaf.id);
  });
  await scenario('ID-04 user restoration checks retained permissions and inherited course roles independently', async () => {
    const home = await createDepartment(department.parentId, 'FX_RESTORATION_HOME');
    const operatorRole = await makeRole('FX_RESTORATION_OPERATOR', B);
    await grant(operatorRole, [await makePermission('rbac:user:edit', 'API', B)]);
    const permissionRole = await makeRole('FX_RESTORATION_PERMISSION');
    const permissionJunior = await makeRole('FX_RESTORATION_PERMISSION_JUNIOR');
    await grant(permissionJunior, [apiPermission]);
    status(await edge(permissionRole, permissionJunior), 200);
    const identitySenior = await makeRole('FX_RESTORATION_IDENTITY');
    const identityJunior = await makeRole('FX_RESTORATION_IDENTITY_JUNIOR', C, 'student');
    status(await edge(identitySenior, identityJunior), 200);
    await eligible(home, [operatorRole, permissionRole, identitySenior]);
    const operator = await createUser('fixture_restoration_operator', home);
    await assign(operator, operatorRole);
    const operatorToken = await freshLogin(operator.username, fixturePassword, B);

    const storedStatus = async id => {
      const [[row]] = await f.db.query('SELECT status, token_version, version FROM sys_user WHERE id=?', [id]);
      return row;
    };
    const targets = [];
    for (const [label, retainedRole] of [['permission', permissionRole], ['identity', identitySenior]]) {
      const target = await createUser(`fixture_restoration_${label}`, home);
      const assignment = await assign(target, retainedRole);
      const path = `/users/${target.id}/status`;
      await ok('PUT', path, {status: 'DISABLED'});
      const profile = await storedStatus(target.id);
      assert.equal(profile.status, 'DISABLED');
      const assignments = await storedAssignments(target.id);
      assert.deepEqual(assignments, [{id: assignment.id, role_id: retainedRole.id}]);
      const before = await grantEffects();
      rejects(await request('PUT', `/api/v1${path}`, {status: 'ACTIVE'}, operatorToken), 403, 'PRIVILEGE_ESCALATION');
      assert.deepEqual(await storedStatus(target.id), profile);
      assert.deepEqual(await storedAssignments(target.id), assignments);
      assert.deepEqual(await grantEffects(), before);
      targets.push({target, path, assignments});
    }

    const systems = await ok('GET', '/systems');
    const course = systems.find(system => system.code === C);
    assert.ok(course);
    const paused = targets[0];
    await ok('PUT', `/systems/${course.id}/status`, {status: 'DISABLED'});
    try {
      assert.equal((await ok('PUT', paused.path, {status: 'ACTIVE'}, operatorToken)).status, 'ACTIVE');
      assert.deepEqual(await storedAssignments(paused.target.id), paused.assignments);
      await ok('PUT', paused.path, {status: 'DISABLED'});
    } finally {
      await ok('PUT', `/systems/${course.id}/status`, {status: 'ENABLED'});
    }

    await assign(operator, permissionRole);
    await assign(operator, identitySenior);
    for (const {target, path, assignments} of targets) {
      const before = await grantEffects();
      assert.equal((await ok('PUT', path, {status: 'ACTIVE'}, operatorToken)).status, 'ACTIVE');
      assert.equal((await storedStatus(target.id)).status, 'ACTIVE');
      assert.deepEqual(await storedAssignments(target.id), assignments);
      assert.deepEqual(await grantEffects(), {version: before.version + 1, successes: before.successes + 1});
    }
  });
  await scenario('ROLE-05 edit-only replacement preserves unchanged disabled system eligibility', async () => {
    const home = await createDepartment(department.parentId, 'FX_ELIGIBILITY_OPERATOR_HOME');
    const target = await createDepartment(home.id, 'FX_ELIGIBILITY_TARGET');
    const operatorRole = await makeRole('FX_ELIGIBILITY_OPERATOR', B);
    await grant(operatorRole, [await makePermission('rbac:department:edit', 'API', B)]);
    await eligible(home, [operatorRole]);
    const operator = await createUser('fixture_eligibility_operator', home);
    await assign(operator, operatorRole);
    const operatorToken = await freshLogin(operator.username, fixturePassword, B);
    const activeRole = await makeRole('FX_ELIGIBILITY_ENABLED', B);
    const pausedSystem = await ok('POST', '/systems', {code: 'fixture-paused-eligibility', name: 'Fixture paused eligibility'});
    const retained = await makeRole('FX_ELIGIBILITY_RETAINED', pausedSystem.code);
    const added = await makeRole('FX_ELIGIBILITY_ADDED', pausedSystem.code);
    await eligible(target, [retained]);
    const member = await createUser('fixture_eligibility_member', target);
    await assign(member, retained);
    const path = `/departments/${target.id}/assignable-roles`;
    rejects(await request('GET', `/api/v1${path}`, undefined, operatorToken), 403, 'PERMISSION_DENIED');
    await ok('PUT', `/systems/${pausedSystem.id}/status`, {status: 'DISABLED'});
    const before = await grantEffects();
    const retainedAssignment = await storedAssignments(member.id);
    for (const roleIds of [[retained.id, added.id], []]) {
      rejects(await request('PUT', `/api/v1${path}`, {roleIds}, operatorToken), 403, 'PRIVILEGE_ESCALATION');
      assert.deepEqual(await storedEligibility(target.id), [retained.id]);
      assert.deepEqual(await storedAssignments(member.id), retainedAssignment);
      assert.deepEqual(await grantEffects(), before);
    }
    assert.equal(await ok('PUT', path, {roleIds: [retained.id, activeRole.id]}, operatorToken), null);
    assert.deepEqual(await storedEligibility(target.id), [activeRole.id, retained.id]);
    assert.deepEqual(await storedAssignments(member.id), retainedAssignment);
    assert.deepEqual(await grantEffects(), {version: before.version + 1, successes: before.successes + 1});

    assert.equal(await ok('PUT', path, {roleIds: [activeRole.id, added.id]}), null);
    assert.deepEqual(await storedEligibility(target.id), [activeRole.id, added.id]);
    assert.deepEqual(await storedAssignments(member.id), []);
  });
  await scenario('ROLE status and profile changes require separate API permissions',async()=>{
    const [[hq]]=await f.db.query('SELECT id FROM sys_department WHERE parent_id IS NULL ORDER BY id LIMIT 1');
    const operatorRole=await makeRole('FX_ROLE_OPERATOR',B);
    const target=await makeRole('FX_ROLE_STATUS_TARGET',B);
    const editPermission=await makePermission('rbac:role:edit','API',B);
    await grant(operatorRole,[editPermission]);
    const existing=await ok('GET',`/departments/${hq.id}/assignable-roles`);
    await eligible(hq,[...existing,operatorRole]);
    try {
      const operator=await createUser('fixture_role_operator',hq);
      await assign(operator,operatorRole);
      const opToken=await freshLogin(operator.username,fixturePassword,B);

      assert.equal((await ok('PATCH',`/roles/${target.id}`,{name:'Renamed role'},opToken)).name,'Renamed role');
      status(await request('PUT',`/api/v1/roles/${target.id}/status`,{status:'DISABLED'},opToken),403);
      assert.equal((await ok('GET',`/roles/${target.id}`)).status,'ENABLED');

      const statusPermission=await makePermission('rbac:role:status','API',B);
      await grant(operatorRole,[statusPermission]);
      assert.equal((await ok('PUT',`/roles/${target.id}/status`,{status:'DISABLED'},opToken)).status,'DISABLED');
      assert.equal((await ok('PUT',`/roles/${target.id}/status`,{status:'ENABLED'},opToken)).status,'ENABLED');
      status(await request('PATCH',`/api/v1/roles/${target.id}`,{name:'Unexpected rename'},opToken),403);
      assert.equal((await ok('GET',`/roles/${target.id}`)).name,'Renamed role');
    } finally {
      await eligible(hq,existing);
    }
  });
  await scenario('ROLE upper bound / enable and inheritance cannot elevate root-department non-super admin',async()=>{
    const [[hq]]=await f.db.query('SELECT id FROM sys_department WHERE parent_id IS NULL ORDER BY id LIMIT 1');
    const roles=await ok('GET',`/roles?systemCode=${B}&pageSize=100`),admin=roles.items.find(r=>r.code==='RBAC_ADMIN');assert.ok(admin);
    const allow=await ok('GET',`/departments/${hq.id}/assignable-roles`);await eligible(hq,[...allow,admin].filter((r,i,a)=>a.findIndex(x=>x.id===r.id)===i));
    const u=await createUser('fixture_bound_admin',hq);await assign(u,admin);const t=await freshLogin(u.username,fixturePassword,B);
    const weak=await ok('POST','/roles',{systemCode:C,code:'BOUND_WEAK',name:'BOUND_WEAK'},t),strong=await makeRole('BOUND_STRONG',C,'teacher');await grant(strong,[apiPermission]);
    status(await request('PUT',`/api/v1/roles/${weak.id}/permissions`,{permissionIds:[apiPermission.id]},t),403);
    status(await request('POST',`/api/v1/roles/${weak.id}/inherits/${strong.id}`,undefined,t),403);
    const adminOnly = await makeRole('BOUND_IDENTITY', C, 'admin');
    status(await request('POST',`/api/v1/roles/${weak.id}/inherits/${adminOnly.id}`,undefined,t),403);
    await eligible(hq,[...allow,admin,strong].filter((r,i,a)=>a.findIndex(x=>x.id===r.id)===i));const target=await createUser('fixture_bound_target',hq);status(await request('POST',`/api/v1/users/${target.id}/role-assignments`,{roleId:strong.id},t),403);
    await ok('PUT',`/roles/${strong.id}/status`,{status:'DISABLED'});status(await request('PUT',`/api/v1/roles/${strong.id}/status`,{status:'ENABLED'},t),403);
  });
  await scenario('NFR-03 two JVM L1/L2 warmed revoke next access both false',async()=>{
    for(let n=0;n<3;n++) for(const i of [0,1]) {assert.equal(await allowed(token,ui.code,i),true);assert.equal(status(await request('GET','/auth/api-permissions/fixtureOperation',undefined,token,i),200).allowed,true);}
    assert.ok(await countOwnedNamespace(f.name)>0,'Owned namespace must contain warmed Redis L2 entries');
    const before=await ok('GET',`/roles/${role.id}/permissions`);await grant(role,before.filter(p=>p.id!==ui.id&&p.id!==apiPermission.id));
    for(const i of [0,1]) {assert.equal(await allowed(token,ui.code,i),false);assert.equal(status(await request('GET','/auth/api-permissions/fixtureOperation',undefined,token,i),200).allowed,false);}
    await grant(role,before);for(const i of [0,1]) assert.equal(await allowed(token,ui.code,i),true);
  });
  await scenario('AUD-01 trusted key both methods / input enum UUID prefix target / actor spoof',async()=>{
    status(await audit(auditBody(),token,{}),403);status(await audit(auditBody(),token,{'X-Audit-Service-Key':'fixture-wrong-key'}),403);status(await request('GET','/auth/audit-logs',undefined,token),403);
    status(await request('POST','/auth/audit-logs',auditBody(),null,0,auditHeaders),401);status(await request('GET','/auth/audit-logs',undefined,null,0,auditHeaders),401);
    for(const patch of [{requestId:'not-a-uuid'},{type:'other'},{objectId:'42'},{objectId:'cpm-course-42'},{objectType:'unknown'},{target:'x'.repeat(501)},{actor:{id:1}},{actorId:1},{before:{password:'fixture-secret'}}]) status(await audit({...auditBody(),...patch}),400);
    for(const type of ['score_changed','group_changed','milestone_changed','roster_changed']) {const b=auditBody();b.type=type;b.target='x'.repeat(500);const row=status(await audit(b),201);assert.deepEqual(Object.keys(row).filter(k=>!['objectType','objectId'].includes(k)).sort(),['id','at','actor','type','target','before','after'].sort());keys(row.actor,['id','name','roles','deleted']);assert.equal(row.actor.deleted,false);assert.equal(row.type,type);assert.equal(row.actor.id,user.id);identity(row.actor.roles);assert.deepEqual(row.actor.roles,['teacher']);assert.equal(typeof row.before,'object');assert.equal(row.target.length,500);}
  });
  await scenario('AUD-01 same key same body same ID / conflict / 20 concurrent one DB row',async()=>{
    const b=auditBody();const first=status(await audit(b),201),second=status(await audit(b),201);assert.equal(first.id,second.id);const conflict=await audit({...b,target:'different'});status(conflict,400);assert.equal(conflict.body.code,'AUDIT_DUPLICATE_CONFLICT');
    const otherUser=await createUser('fixture_audit_peer',department);await assign(otherUser,role);const otherToken=await freshLogin(otherUser.username,fixturePassword,C);const subjectConflict=await audit(b,otherToken);status(subjectConflict,400);assert.equal(subjectConflict.body.code,'AUDIT_DUPLICATE_CONFLICT');
    const race=auditBody();const responses=await Promise.all(Array.from({length:20},()=>audit(race)));for(const r of responses) status(r,201);assert.equal(new Set(responses.map(r=>r.body.id)).size,1);
    const [[count]]=await f.db.query('SELECT COUNT(*) n FROM sys_audit_log WHERE request_id=?',[race.requestId]);assert.equal(count.n,1);
    const [[row]]=await f.db.query('SELECT source_system_code,actor_id,actor_roles_json,origin FROM sys_audit_log WHERE request_id=?',[b.requestId]);assert.equal(row.source_system_code,C);assert.equal(row.origin,'COURSE');assert.equal(row.actor_id,user.id);identity(typeof row.actor_roles_json==='string'?JSON.parse(row.actor_roles_json):row.actor_roles_json);return {rows:count.n};
  });
  await scenario('AUD-02 system isolation paging actor target time inclusive boundaries',async()=>{
    const b=auditBody();b.target='fixture_boundary';const created=status(await audit(b),201);normalizer.registerTime('audit-boundary',created.at);
    const p1=status(await request('GET','/auth/audit-logs?page=1&pageSize=2',undefined,token,0,auditHeaders),200),p2=status(await request('GET','/auth/audit-logs?page=2&pageSize=2',undefined,token,0,auditHeaders),200);assert.equal(p1.total,p2.total);assert.equal(p1.items.length,2);assert.equal(p2.items.length,2);assert.equal(new Set([...p1.items,...p2.items].map(x=>x.id)).size,4);
    const ordered=[...p1.items,...p2.items];for(let i=1;i<ordered.length;i++) assert.ok(Date.parse(ordered[i-1].at)>Date.parse(ordered[i].at)||(ordered[i-1].at===ordered[i].at&&ordered[i-1].id>ordered[i].id),'audit order at DESC then id DESC');
    const url='/auth/audit-logs?'+new URLSearchParams({actorId:String(user.id),actor:user.name,target:b.target,from:created.at,to:created.at,page:'1',pageSize:'1'});
    const page=status(await request('GET',url,undefined,token,0,auditHeaders),200);keys(page,['page','pageSize','total','items']);assert.equal(page.total,1);assert.equal(page.items[0].id,created.id);assert.equal(page.page,1);assert.equal(page.pageSize,1);
    const after=status(await request('GET','/auth/audit-logs?'+new URLSearchParams({target:b.target,from:new Date(Date.parse(created.at)+1).toISOString()}),undefined,token,0,auditHeaders),200);assert.equal(after.total,0);
    for(const q of ['page=0','pageSize=0','from=invalid','from=2030-01-01T00:00:00Z&to=2020-01-01T00:00:00Z']) status(await request('GET','/auth/audit-logs?'+q,undefined,token,0,auditHeaders),400);
    const other=await ok('POST','/systems',{code:'fixture-other',name:'fixture other'});assert.equal(other.code,'fixture-other');
    await makeRole('student', other.code);
    const rr=await makeRole('OTHER_AUDIT',other.code,'student');const read=await makePermission('audit:read','API',other.code),write=await makePermission('audit:write','API',other.code);await grant(rr,[read,write]);await eligible(sibling,[rr]);const u=await createUser('fixture_other_system',sibling);await assign(u,rr);const t=await freshLogin(u.username,fixturePassword,other.code);
    const cross=await audit(b,t);status(cross,400);assert.equal(cross.body.code,'AUDIT_DUPLICATE_CONFLICT');const empty=status(await request('GET','/auth/audit-logs?target=fixture_boundary',undefined,t,0,auditHeaders),200);assert.equal(empty.total,0);
  });
  await scenario('AUD-05 trigger failure role mutation rollback no false SUCCESS',async()=>{
    const r=await makeRole('ROLLBACK_ROLE');const [[before]]=await f.db.query('SELECT name,version FROM sys_role WHERE id=?',[r.id]);const [[v]]=await f.db.query('SELECT version FROM sys_grant_version WHERE id=1');const [[count]]=await f.db.query("SELECT COUNT(*) n FROM sys_audit_log WHERE result='SUCCESS'");
    try {
      await f.db.query("CREATE TRIGGER acceptance_fail_audit BEFORE INSERT ON sys_audit_log FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture audit failure'");
      const response=await request('PATCH',`/api/v1/roles/${r.id}`,{name:'must roll back'});status(response,503);
      const [[after]]=await f.db.query('SELECT name,version FROM sys_role WHERE id=?',[r.id]);assert.deepEqual(after,before);const [[afterV]]=await f.db.query('SELECT version FROM sys_grant_version WHERE id=1');assert.deepEqual(afterV,v);
      const [[afterCount]]=await f.db.query("SELECT COUNT(*) n FROM sys_audit_log WHERE result='SUCCESS'");assert.equal(afterCount.n,count.n);
    } finally {await f.db.query('DROP TRIGGER IF EXISTS acceptance_fail_audit');}
  });
  await scenario('ID-04 10000 synthetic SQL users keyword department DB pagination max100',async()=>{
    assert.match(f.name,/^rbac1_contract_/);
    // Reuse only a fictitious fixture hash, never a real user hash or plaintext credentials.
    const [[seed]]=await f.db.query('SELECT password_hash FROM sys_user WHERE id=?',[user.id]);assert.ok(seed.password_hash);
    for(let offset=0;offset<10000;offset+=500) {
      const values=Array.from({length:500},(_,j)=>{const n=offset+j,tag=String(n).padStart(5,'0');return [`fixture-bulk-${tag}`,`fixture_bulk_${tag}`,seed.password_hash,`Fixture Bulk ${tag}`,n%2===0?department.id:sibling.id,'ACTIVE',0,false,0,0,new Date()];});
      await f.db.query('INSERT INTO sys_user (subject,username,password_hash,name,department_id,status,token_version,must_change_password,failed_login_count,version,created_at) VALUES ?', [values]);
    }
    for(const page of [1,2,50,51,10000]) {
      const actual=await ok('GET','/users?'+new URLSearchParams({keyword:'fixture_bulk_',departmentId:String(department.id),page:String(page),pageSize:'100'}));assert.equal(actual.page,page);assert.equal(actual.pageSize,100);assert.equal(actual.total,5000);
      const [rows]=await f.db.query("SELECT id,username FROM sys_user WHERE (username LIKE ? OR name LIKE ?) AND department_id=? ORDER BY id LIMIT 100 OFFSET ?",['%fixture_bulk_%','%fixture_bulk_%',department.id,(page-1)*100]);assert.deepEqual(actual.items.map(x=>({id:x.id,username:x.username})),rows.map(x=>({...x})));
      for(const item of actual.items) {assert.ok(!('passwordHash'in item));assert.ok(!('tokenVersion'in item));}
    }
    const boundary=await request('GET','/api/v1/users?pageSize=101');
    // Frozen contract says maximum100, either reject invalid input or clamp; never return >100.
    if(boundary.status===200) {assert.equal(boundary.body.data.pageSize,100);assert.ok(boundary.body.data.items.length<=100);}else status(boundary,400);
    for(const q of ['page=0','pageSize=0','page=-1']) status(await request('GET','/api/v1/users?'+q),400);
    return {inserted:10000,matching:5000};
  });
  await scenario('ID-03 disable enable never resurrects previous token',async()=>{
    const t=await login(user.username,fixturePassword+'X',C);await ok('PUT',`/users/${user.id}/status`,{status:'DISABLED'});for(const i of [0,1]) status(await request('GET','/auth/me',undefined,t,i),401);
    await ok('PUT',`/users/${user.id}/status`,{status:'ACTIVE'});for(const i of [0,1]) status(await request('GET','/auth/me',undefined,t,i),401);token=await login(user.username,fixturePassword+'X',C);
  });
  await scenario('ID-03 logout204 durable DB jti across owned process restart',async()=>{
    const t=await login(user.username,fixturePassword+'X',C),payload=JSON.parse(Buffer.from(t.split('.')[1],'base64url'));
    status(await request('POST','/auth/logout',undefined,t),204);for(const i of [0,1]) status(await request('GET','/auth/me',undefined,t,i),401);
    const [[row]]=await f.db.query('SELECT COUNT(*) n FROM sys_token_revocation WHERE jti=?',[payload.jti]);assert.equal(row.n,1);
    await clearOwnedNamespace(f.name);assert.equal(await countOwnedNamespace(f.name),0);await f.restart(0);for(const i of [0,1]) status(await request('GET','/auth/me',undefined,t,i),401);
    return {persistentRevocations:row.n};
  });
  await scenario('NFR-06 external JVM logs never contain fixture secrets or JWT',async()=>{
    for(const i of [0,1]) {const text=await readFile(join(f.dir,`${f.module}-${i}.log`),'utf8');for(const secret of [f.bootstrap,f.serviceKey,f.keys.privateKey,fixturePassword,token,root]) if(secret) assert.ok(!text.includes(secret),'Sensitive value found in owned JVM log');assert.ok(!/eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(text),'JWT found in log');}
  });
  await scenario('NFR-04 final database postconditions and no orphan/cross-system facts',async()=>{
    const checks={users:'SELECT COUNT(*) n FROM sys_user',bulk:"SELECT COUNT(*) n FROM sys_user WHERE username LIKE 'fixture_bulk_%'",crossEdges:'SELECT COUNT(*) n FROM sys_role_hierarchy h JOIN sys_role a ON a.id=h.senior_role_id JOIN sys_role b ON b.id=h.junior_role_id WHERE a.system_id<>b.system_id',crossPermissions:'SELECT COUNT(*) n FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.system_id<>p.system_id',ineligible:'SELECT COUNT(*) n FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id LEFT JOIN sys_department_allowed_role d ON d.department_id=u.department_id AND d.role_id=ur.role_id WHERE d.id IS NULL',legacyMarker:"SELECT COUNT(*) n FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_role' AND column_name='identity_role'"};
    const result={};for(const [key,sql] of Object.entries(checks)) {const [[r]]=await f.db.query(sql);result[key]=r.n;}assert.equal(result.bulk,10000);for(const k of ['crossEdges','crossPermissions','ineligible','legacyMarker']) assert.equal(result[k],0);return result;
  });
}
