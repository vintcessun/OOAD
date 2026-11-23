import assert from 'node:assert/strict';
const C='course-project-management';
export async function provisionAccounts(center,{bootstrap,serviceKey}) {
 let root;const observed=[];
 async function request(method,path,body,token=root,headers={}) {
  const r=await fetch(center+path,{redirect:'error',signal:AbortSignal.timeout(30000),method,headers:{...(token?{Authorization:'Bearer '+token}:{}),...(body===undefined?{}:{'Content-Type':'application/json'}),...headers},...(body===undefined?{}:{body:JSON.stringify(body)})});let value;try{value=await r.json()}catch{value=null}
  observed.push({method,path,status:r.status,code:value?.code??null});assert(r.ok,`${method} ${path}: HTTP ${r.status}, code ${value?.code??'none'}`);return value;
 }
 async function ok(method,path,body,token=root) {const v=await request(method,'/api/v1'+path,body,token);assert.equal(v.code,'OK');return v.data;}
 async function login(name,password,system){return (await request('POST','/auth/login',{username:name,password,systems:[system]},null)).accessToken;}
 async function fresh(name,password,system){const token=await login(name,password,system);const changed=password+'X';await ok('POST','/auth/password',{oldPassword:password,newPassword:changed},token);return login(name,changed,system);}
 root=await fresh('root',bootstrap,'rbac-center');
 const tree=await ok('GET','/departments/tree');assert(Array.isArray(tree)&&tree[0]?.id,'Real seeded department tree required');
 const dep=await ok('POST','/departments',{parentId:tree[0].id,code:'M6_REAL',name:'Synthetic real integration'});
 const codes=['course:access','TC','CR','AV','TEC','audit:read','audit:write','CC'];const permissions={};
 for(const code of codes) {const page=await ok('GET','/permissions?'+new URLSearchParams({systemCode:C,kind:'API',keyword:code,pageSize:'100'}));permissions[code]=page.items.find(x=>x.code===code&&x.kind==='API')??await ok('POST','/permissions',{systemCode:C,code,name:code,kind:'API'});}
 const definitions={ADMIN:['admin','TC','CR','AV','TEC','course:access','audit:read','audit:write'],ADMIN_NO_TEC:['admin','course:access'],LEADER:['teacher','TEC','course:access','audit:read','audit:write'],CC_TEACHER:['teacher','CC','course:access','audit:read','audit:write'],NO_CC_TEACHER:['teacher','course:access','audit:read','audit:write'],STUDENT_LEADER:['student','course:access','audit:write'],STUDENT_APPLICANT:['student','course:access']};
 const {roles:baseRoles}=await ok('GET', `/systems/${C}/role-hierarchy`);
 // Only the disposable test center is reconfigured to test minimal, disjoint grants.
 for (const base of baseRoles.filter(r=>['admin','teacher','student'].includes(r.code))) {
  await ok('PUT','/roles/'+base.id+'/permissions',{permissionIds:[]});
 }
 const roles={};for(const [who,[identity,...grants]]of Object.entries(definitions)) {const role=await ok('POST','/roles',{systemCode:C,code:'M6_'+who,name:'Synthetic '+who});roles[who]=role;const base=baseRoles.find(r=>r.code===identity);assert(base);await ok('POST',`/roles/${role.id}/inherits/${base.id}`);await ok('PUT',`/roles/${role.id}/permissions`,{permissionIds:grants.map(x=>permissions[x].id)});}
 await ok('PUT',`/departments/${dep.id}/assignable-roles`,{roleIds:Object.values(roles).map(x=>x.id)});
 const tokens={},subjects={},users={};
 const localAccounts={LEADER:'M6-T1',STUDENT_LEADER:'M6-S1'};
 const localNames={LEADER:'Synthetic Leader',STUDENT_LEADER:'Synthetic Student Leader'};
 const password='SyntheticOnly!'+bootstrap.slice(-16)+'a1';
 for(const who of Object.keys(definitions)) {const user=await ok('POST','/users',{username:localAccounts[who]??'m6_'+who.toLowerCase(),name:localNames[who]??'Synthetic '+who,no:localAccounts[who]??'M6-'+who,departmentId:dep.id,password});users[who]=user;subjects[who]=user.subject;assert(user.subject);await ok('POST',`/users/${user.id}/role-assignments`,{roleId:roles[who].id});tokens[who]=await fresh(user.username,password,C);}
 const authorityEvidence={};
 for(const who of ['LEADER','CC_TEACHER','NO_CC_TEACHER']) {const decision=await request('GET','/auth/api-permissions/copyCourse',undefined,tokens[who]);assert.equal(decision.allowed,true,'All three teacher sessions must pass coarse copy operation');const snapshot=await request('GET','/integration/authorities',undefined,tokens[who],{'X-Audit-Service-Key':serviceKey});assert(snapshot.authorities.includes('ROLE_teacher'));assert.equal(snapshot.authorities.includes('CC'),who==='CC_TEACHER');authorityEvidence[who]={operationAllowed:decision.allowed,teacher:true,CC:snapshot.authorities.includes('CC')};}
 return {tokens,subjects,users,authorityEvidence,http:observed,department:dep,
   wrongAudience:root, coursePassword:password+'X', localAccounts};
}
