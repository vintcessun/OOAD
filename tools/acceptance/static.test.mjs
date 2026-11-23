import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdtemp, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { spawnSync } from 'node:child_process';
import { guardDatabase } from './fixture-guard.mjs';
import { parseResp } from './redis.mjs';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { createNormalizer, normalize } from './normalization.mjs';
import { fetchWithoutRedirect } from './http.mjs';

const here=import.meta.dirname;
test('database guard permits only timestamped owned prefix at literal loopback dedicated port',()=>{
  for(const module of ['structured','ddd']) assert.equal(guardDatabase('127.0.0.1',13306,`rbac1_contract_${module}_1780000000000_abcdef12`),'`'+`rbac1_contract_${module}_1780000000000_abcdef12`+'`');
  for(const host of ['localhost','::1','127.0.0.2','remote.example','127.0.0.1.evil']) assert.throws(()=>guardDatabase(host,13306,'rbac1_contract_ddd_1780000000000_abcdef12'));
  for(const port of [3306,0,13307,NaN]) assert.throws(()=>guardDatabase('127.0.0.1',port,'rbac1_contract_ddd_1780000000000_abcdef12'));
  for(const name of ['rbac','rbac1_ddd_test','rbac1_contract_ddd','rbac1_contract_ddd_1780000000000_abcdef12;DROP DATABASE rbac','rbac1_contract_other_1780000000000_abcdef12','rbac1_contract_ddd_1780000000000_abcdef12`']) assert.throws(()=>guardDatabase('127.0.0.1',13306,name));
});
test('RESP parser handles chunked scan arrays, strings, integers without assumptions about packet boundaries',()=>{
  const response=Buffer.from('*2\r\n$1\r\n0\r\n*2\r\n$3\r\none\r\n$3\r\ntwo\r\n');
  for(let n=0;n<response.length;n++) assert.equal(parseResp(response.subarray(0,n)),null);
  assert.deepEqual(parseResp(response).value,['0',['one','two']]);assert.equal(parseResp(Buffer.from(':2\r\n')).value,2);assert.throws(()=>parseResp(Buffer.from('-ERR failure\r\n')));
});
function registry(offset=0) {
  const n=createNormalizer();
  const ids={departmentA:10+offset,departmentB:11+offset,userA:20+offset,userB:21+offset,roleA:30+offset,roleB:31+offset,permission:40+offset};
  const departmentA={id:ids.departmentA,code:'A',parentId:null,path:`/${ids.departmentA}/`,version:0};
  const departmentB={id:ids.departmentB,code:'B',parentId:ids.departmentA,path:`/${ids.departmentA}/${ids.departmentB}/`,version:0};
  const userA={id:ids.userA,username:'fixture_A',subject:`generated-subject-A-${offset}`,departmentId:ids.departmentA,version:3};
  const userB={id:ids.userB,username:'fixture_B',subject:`generated-subject-B-${offset}`,departmentId:ids.departmentB,version:3};
  n.observeResponse(n.observeRequest('GET','/api/v1/departments/tree'),{data:[departmentA,departmentB]});
  n.observeResponse(n.observeRequest('GET','/api/v1/roles?systemCode=course'),{data:{items:[{id:ids.roleA,systemCode:'course',code:'A',builtin:false},{id:ids.roleB,systemCode:'course',code:'B',builtin:false}]}});
  n.observeResponse(n.observeRequest('GET','/api/v1/users'),{data:{items:[userA,userB]}});
  n.observeResponse(n.observeRequest('GET','/api/v1/permissions?systemCode=course'),{data:{items:[{id:ids.permission,systemCode:'course',code:'read',kind:'API'}]}});
  return {n,ids,userA,userB,departmentA,departmentB};
}
test('different generated IDs and subjects compare equal only when their declared business relationships agree',()=>{
  const a=registry(),b=registry(1000);
  assert.deepEqual(a.n.normalize(a.userA),b.n.normalize(b.userA));
  assert.deepEqual(a.n.normalize(a.departmentB),b.n.normalize(b.departmentB));
  const relations=x=>({userId:x.ids.userA,departmentId:x.ids.departmentB,roleId:x.ids.roleA,assignedBy:x.ids.userB,seniorRoleId:x.ids.roleA,juniorRoleId:x.ids.roleB,permissionIds:[x.ids.permission],version:4});
  assert.deepEqual(a.n.normalize(relations(a)),b.n.normalize(relations(b)));
  assert.equal(a.n.normalizePath(`/api/v1/users/${a.ids.userA}/role-assignments?departmentId=${a.ids.departmentB}&page=2`),b.n.normalizePath(`/api/v1/users/${b.ids.userA}/role-assignments?page=2&departmentId=${b.ids.departmentB}`));
});
test('departmentId, subject, parentId, userId, roleId or version alone changing can never be erased',()=>{
  const {n,ids,userA,userB,departmentB}=registry();const baseline=n.normalize(userA);
  assert.notDeepEqual(baseline,n.normalize({...userA,departmentId:ids.departmentB}));
  assert.notDeepEqual(baseline,n.normalize({...userA,subject:userB.subject}));
  assert.notDeepEqual(baseline,n.normalize({...userA,subject:'a-new-unexpected-subject'}));
  assert.notDeepEqual(baseline,n.normalize({...userA,version:4}));
  assert.notDeepEqual(n.normalize(departmentB),n.normalize({...departmentB,parentId:ids.departmentB}));
  for(const [field,first,second] of [['roleId',ids.roleA,ids.roleB],['userId',ids.userA,ids.userB]])assert.notDeepEqual(n.normalize({[field]:first}),n.normalize({[field]:second}));
  assert.notEqual(n.normalizePath(`/api/v1/users/${ids.userA}`),n.normalizePath(`/api/v1/users/${ids.userB}`));
});
test('inheritance path element order and department ancestry path are semantic, not sets or noise',()=>{
  const a=registry(),b=registry(1000);
  const source=x=>({sources:[{permissionCode:'read',roleCode:'A',inherited:true,path:[String(x.ids.roleA),String(x.ids.roleB)]}]});
  assert.deepEqual(a.n.normalize(source(a)),b.n.normalize(source(b)));
  const changed=source(a);changed.sources[0].path.reverse();assert.notDeepEqual(a.n.normalize(source(a)),a.n.normalize(changed));
  assert.notDeepEqual(a.n.normalize({sources:[{path:['A','B','C']}]}),a.n.normalize({sources:[{path:['A','C','B']}]}));
  assert.notDeepEqual(a.n.normalize(a.departmentB),a.n.normalize({...a.departmentB,path:`/${a.ids.departmentB}/${a.ids.departmentA}/`}));
});
test('page items, children and unknown arrays retain order; only declared sets may sort',()=>{
  const {n,userA,userB}=registry();const page={page:1,pageSize:2,total:2,items:[userA,userB]};
  assert.notDeepEqual(n.normalize(page),n.normalize({...page,items:[userB,userA]}));
  assert.notDeepEqual(normalize({children:['A','B']}),normalize({children:['B','A']}));
  assert.notDeepEqual(normalize({arbitrary:['A','B']}),normalize({arbitrary:['B','A']}));
  assert.deepEqual(normalize({permissions:['write','read'],roles:['teacher','student']}),normalize({roles:['student','teacher'],permissions:['read','write']}));
});
test('UUIDs alias per run at request dispatch, preserve identity/reuse and do not mask arbitrary UUID fields',()=>{
  const a=createNormalizer(),b=createNormalizer();
  const u=['11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333','44444444-4444-4444-8444-444444444444'];
  const reqA=a.observeRequest('POST','/auth/audit-logs',{requestId:u[0]}),reqB=b.observeRequest('POST','/auth/audit-logs',{requestId:u[2]});
  const secondA=a.observeRequest('POST','/auth/audit-logs',{requestId:u[1]}),secondB=b.observeRequest('POST','/auth/audit-logs',{requestId:u[3]});
  assert.deepEqual(a.normalize({requestId:u[0],auditUUID:u[1]}),b.normalize({requestId:u[2],auditUUID:u[3]}));
  assert.notDeepEqual(a.normalize({requestId:u[0]}),a.normalize({requestId:u[1]}));
  const row={id:1,actor:{id:5},type:'score_changed',target:'fixture',at:'2026-01-01T00:00:00Z',version:0};
  a.register('user',5,'fixture');b.register('user',50,'fixture');
  const responseA=a.observeResponse(reqA,row),secondResponseB=b.observeResponse(secondB,{...row,id:200,actor:{id:50}});
  assert.deepEqual(responseA,b.observeResponse(reqB,{...row,id:100,actor:{id:50},at:'2027-01-01T00:00:00Z'}));
  assert.deepEqual(a.observeResponse(secondA,{...row,id:2}),secondResponseB,'Concurrent completion order does not choose audit aliases');
  assert.notDeepEqual(a.normalize({opaqueUUID:u[0]}),b.normalize({opaqueUUID:u[2]}));
  assert.equal(a.normalize({requestId:'not-a-uuid'}).requestId,'not-a-uuid');
});
test('reference registries are per run, typed, collision-safe, and unknown references fail closed',()=>{
  const a=createNormalizer(),b=createNormalizer();a.register('department',1,'A');a.register('role',1,['course','R']);
  assert.notDeepEqual(a.normalize({departmentId:1}),b.normalize({departmentId:1}));
  assert.notDeepEqual(a.normalize({departmentId:1}).departmentId,a.normalize({roleId:1}).roleId);
  assert.notDeepEqual(normalize({departmentId:1}),normalize({departmentId:2}));
  assert.notDeepEqual(normalize({subject:'unknown-A'}),normalize({subject:'unknown-B'}));
  a.register('department',1,'B');assert.notEqual(a.normalize({departmentId:1}).departmentId,'@department:"B"');
  assert.throws(()=>a.register('role',2,['course','R']),/same business key/);
  a.register('assignment',1,['user-A','role-R']);a.register('assignment',2,['user-A','role-R']);assert.notDeepEqual(a.normalize({id:1,userId:10,roleId:20}),a.normalize({id:2,userId:10,roleId:20}));
});
test('identity projections normalize stable subjects without confusing numeric user IDs',()=>{
  const a=registry(),b=registry(1000);
  for (const base of ['/identity/users/','/auth/users/']) {
    const path=x=>base+encodeURIComponent(x.userA.subject);
    const lookup=(x,subject)=>x.n.observeResponse(x.n.observeRequest('GET',path(x)),{userId:subject,name:'Fixture',no:'FX'});
    assert.equal(a.n.normalizePath(path(a)),b.n.normalizePath(path(b)));
    assert.deepEqual(lookup(a,a.userA.subject),lookup(b,b.userA.subject));
    assert.notDeepEqual(lookup(a,a.userA.subject),lookup(a,a.userB.subject));
  }
  a.n.register('department',1101,'directory');
  b.n.register('department',1201,'directory');
  const members=(x,id)=>x.n.observeResponse(
      x.n.observeRequest('GET','/auth/departments/'+id+'/users'),
      {items:[{userId:x.userA.subject,departmentId:id,name:'Fixture',no:'FX'}]});
  assert.deepEqual(members(a,1101),members(b,1201));
  assert.equal(a.n.normalizePath('/auth/departments?parentId=1101'),b.n.normalizePath('/auth/departments?parentId=1201'));
  a.n.registerSubject(String(a.ids.userA),'a-different-numeric-subject');
  assert.notDeepEqual(a.n.normalize({userId:String(a.ids.userA)}).userId,a.n.normalize({subject:String(a.ids.userA)}).subject);
});
test('metadata times/trace normalize only in declared locations, business snapshots and time-bound offsets remain meaningful',()=>{
  assert.deepEqual(normalize({code:'OK',message:'成功',timestamp:'first',traceId:'a',data:{version:1}}),normalize({code:'OK',message:'成功',timestamp:'second',traceId:'b',data:{version:1}}));
  assert.notDeepEqual(normalize({at:'business-first'}),normalize({at:'business-second'}));
  const snapshot={before:{id:1,subject:'business-subject',at:'first',path:['B','A'],permissions:['B','A']}};
  assert.deepEqual(normalize(snapshot),snapshot);assert.notDeepEqual(normalize(snapshot),normalize({before:{...snapshot.before,at:'second'}}));
  const a=createNormalizer(),b=createNormalizer();a.registerTime('boundary','2026-01-01T00:00:00Z');b.registerTime('boundary','2027-01-01T00:00:00Z');
  assert.equal(a.normalizePath('/auth/audit-logs?from=2026-01-01T00%3A00%3A00Z'),b.normalizePath('/auth/audit-logs?from=2027-01-01T00%3A00%3A00Z'));
  assert.notEqual(a.normalizePath('/auth/audit-logs?from=2026-01-01T00%3A00%3A00Z'),a.normalizePath('/auth/audit-logs?from=2026-01-01T00%3A00%3A00.001Z'));
});

async function redirectFixture(t) {
  const observed={source:0,target:0};
  const target=createServer((req,res)=>{observed.target++;req.resume();res.end('must not be reached');});
  target.listen(0,'127.0.0.1');await once(target,'listening');
  const source=createServer((req,res)=>{observed.source++;req.resume();res.writeHead(Number(req.url.slice(1)),{Location:`http://127.0.0.1:${target.address().port}/sink`});res.end();});
  source.listen(0,'127.0.0.1');await once(source,'listening');
  t.after(async()=>{for(const server of [source,target]){server.closeAllConnections();await new Promise((resolve,reject)=>server.close(e=>e?reject(e):resolve()));}});
  return {observed,url:`http://127.0.0.1:${source.address().port}`};
}
test('real HTTP 302/307/308 login redirects never deliver password/body to a second target',async t=>{
  const {observed,url}=await redirectFixture(t);
  for(const code of [302,307,308])await assert.rejects(fetchWithoutRedirect(`${url}/${code}`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username:'fixture',password:'FictitiousPasswordOnly!1'}),redirect:'follow',signal:AbortSignal.timeout(3000)}),TypeError);
  assert.equal(observed.source,3);assert.equal(observed.target,0);
});
test('real HTTP 302/307/308 audit redirects never deliver service key or bearer to a second target',async t=>{
  const {observed,url}=await redirectFixture(t);
  for(const code of [302,307,308])for(const method of ['GET','POST'])await assert.rejects(fetchWithoutRedirect(`${url}/${code}`,{method,headers:{'X-Audit-Service-Key':'fictitious-key-only',Authorization:'Bearer fictitious-token-only'},...(method==='POST'?{body:'{}'}:{}),signal:AbortSignal.timeout(3000)}),TypeError);
  assert.equal(observed.source,6);assert.equal(observed.target,0);
});
test('HTTP suite has required safety scenarios and no service/algorithm imports or skip hooks',async()=>{
  const suite=await readFile(join(here,'suite.mjs'),'utf8');
  for(const needle of ['10000','length:20','ROLE_CYCLE','AUDIT_DUPLICATE_CONFLICT','BEFORE INSERT','DROP TRIGGER IF EXISTS','f.restart(0)','JWKS','two JVM','department move','nonroot department admin','upper bound','signature']) assert.ok(suite.includes(needle),needle);
  assert.ok((suite.match(/await scenario\(/g)??[]).length>=20);
  for(const match of suite.matchAll(/from ['"]([^'"]+)['"]/g)) assert.ok(match[1].startsWith('node:')||['./redis.mjs','./normalization.mjs','./http.mjs'].includes(match[1]),'HTTP scenarios only import Node builtins and test-only helpers');
  assert.ok(suite.includes('const normalizer=createNormalizer()'),'Each suite run owns a registry');
  assert.ok(suite.includes('fetchWithoutRedirect('));assert.ok(!/\bfetch\s*\(/.test(suite));
  assert.ok(suite.includes('EXT private service projection')&&suite.includes("'/integration/authorities'")&&suite.includes("'/identity/users/'"),'Keep both main-agent private provider endpoint scenarios');
  assert.ok(suite.includes('const permission = existing.items.find'),'Keep exact seed permission reuse');
  assert.ok(!/\b(?:skip|todo)\s*\(/.test(suite));
});
test('startup and cleanup cannot flush Redis, stop shared daemons or kill arbitrary process IDs',async()=>{
  const code=await readFile(join(here,'fixture.mjs'),'utf8'),redis=await readFile(join(here,'redis.mjs'),'utf8');
  assert.ok(code.includes('ownedDatabase = false'));assert.ok(code.includes('ownedDatabase=true'));assert.ok(code.includes('children.get(i)'));assert.ok(!/\btaskkill\b|\bpkill\b|\bkillall\b|process\.kill|['"](?:FLUSHALL|FLUSHDB|SHUTDOWN)['"]/i.test(code+redis));
  assert.ok(redis.includes("['SCAN',cursor,'MATCH',prefix+'*'"));assert.ok(redis.includes('startsWith(prefix)'));
});
test('environment error exits 2 with JSON fail rows, never masquerades as a business RED',async()=>{
  const dir=await mkdtemp(join(tmpdir(),'rbac1-static-report-'));
  try {const run=spawnSync(process.execPath,[join(here,'run.mjs'),'--module=both',`--report=${dir}`],{env:{...process.env,JAVA_HOME:''},encoding:'utf8'});assert.equal(run.status,2);const report=JSON.parse(await readFile(join(dir,'report.json'),'utf8'));assert.equal(report.exitCode,2);assert.equal(report.rows.length,3);assert.ok(report.rows.every(r=>r.status==='fail'&&r.classification==='ENVIRONMENT_BLOCKED'));assert.ok(!JSON.stringify(report).includes('accessToken'));} finally {await rm(dir,{recursive:true,force:true});}
});
