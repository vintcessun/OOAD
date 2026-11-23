// Test-only reference registry. One instance belongs to ONE runSuite, never to a module/global run.
// References are not declarations: an unknown FK/subject stays unresolved (and retains its value).
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const SET_FIELDS = new Set(['permissions','directRoles','effectiveRoles','roles','sources','edges','mappings','permissionIds','roleIds','authorities']);
const REF_FIELDS = {userId:'user',actorId:'user',assignedBy:'user',departmentId:'department',roleId:'role',seniorRoleId:'role',juniorRoleId:'role',permissionId:'permission',uiResourceId:'uiResource',apiOperationId:'apiOperation',systemId:'system'};
const SUBJECT_LOOKUP = /^\/(?:identity|auth)\/users\/[^/]+$/;
const DEPARTMENT_MEMBERS = /^\/auth\/departments\/[^/]+\/users$/;
const UUID_FIELDS = new Set(['requestId','request_id','auditUUID','auditUuid']);
function generatedField(key, value, type) {
  if(key==='traceId')return true;
  if(key==='timestamp')return 'code' in value && 'message' in value;
  if(['at','occurredAt','occurred_at'].includes(key))return type==='audit';
  if(key==='assignedAt')return type==='assignment';
  if(key==='createdAt')return ['user','audit'].includes(type);
  return false;
}
const ordered = value => JSON.stringify(value);
const sortSet = list => list.sort((a,b)=>ordered(a).localeCompare(ordered(b)));

function routeContext(path) {
  const url = new URL(path,'http://fixture.invalid');
  const segments = url.pathname.split('/').filter(Boolean);
  const systemIndex=segments.indexOf('systems');
  const systemCode=systemIndex>=0 && segments[systemIndex+1] && !/^\d+$/.test(segments[systemIndex+1]) ? decodeURIComponent(segments[systemIndex+1]) : url.searchParams.get('systemCode');
  let type;
  if (/\/audit-logs$/.test(url.pathname)) type='audit';
  else if (/\/role-assignments(?:\/[^/]+)?$/.test(url.pathname)) type='assignment';
  else if (/\/assignable-roles$/.test(url.pathname)) type='role';
  else if (/\/effective-permissions$|\/users\/[^/]+\/systems\/[^/]+\/permissions$|\/role-hierarchy$/.test(url.pathname)) type=undefined;
  else if (/\/permissions(?:\/\d+)?(?:\/status)?$/.test(url.pathname)) type='permission';
  else if (/\/ui-resources(?:\/[^/]+)?$/.test(url.pathname)) type='uiResource';
  else if (/\/api-operations(?:\/[^/]+)?$/.test(url.pathname)) type='apiOperation';
  else if (/\/departments(?:\/[^/]+)?$/.test(url.pathname)) type='department';
  else if (/\/roles(?:\/\d+)?(?:\/status)?$/.test(url.pathname)) type='role';
  else if (/\/users(?:\/[^/]+)?(?:\/status|\/department)?$/.test(url.pathname)) type='user';
  else if (/\/systems(?:\/[^/]+)?(?:\/status)?$/.test(url.pathname)) type='system';
  return {type,systemCode,subjectUserId:SUBJECT_LOOKUP.test(url.pathname)||DEPARTMENT_MEMBERS.test(url.pathname)};
}
function entityType(value, hint) {
  if (!value || typeof value!=='object' || Array.isArray(value)) return hint;
  if ('actor' in value && 'target' in value && 'type' in value) return 'audit';
  if ('username' in value) return 'user';
  if ('userId' in value && 'roleId' in value) return 'assignment';
  if ('operationId' in value && ('method' in value || 'pathTemplate' in value)) return 'apiOperation';
  if (['MENU','BUTTON'].includes(value.type)) return 'uiResource';
  if (['UI','API'].includes(value.kind) && 'code' in value) return 'permission';
  if ('builtin' in value && 'code' in value) return 'role';
  if ('code' in value && ('parentId' in value || 'path' in value)) return 'department';
  return hint;
}
function childContext(key, context, type) {
  const types={user:'user',actor:'user',directRoles:'role',effectiveRoles:'role',roles:'role',permissions:'permission',children:type,mappings:'mapping',edges:'edge',items:type};
  return {...context,type:types[key]??(key==='data'?context.type:undefined)};
}

export function createNormalizer() {
  const references=new Map(), aliasOwners=new Map(), subjects=new Map(), subjectOwners=new Map(), uuidAliases=new Map(), times=new Map();
  const mapFor=type=>{if(!references.has(type))references.set(type,new Map());return references.get(type);};
  const rawKey=value=>String(value);
  function register(type,id,businessKey) {
    if(id===null||id===undefined) return;
    if(businessKey===null||businessKey===undefined) throw new Error('Entity declarations require a stable business key');
    const map=mapFor(type),key=rawKey(id),baseAlias=`@${type}:${JSON.stringify(businessKey)}`;
    // Never rebind an already observed ID to a different entity (also detects ID collisions).
    if(!map.has(key)) {
      let alias=baseAlias;
      // Revoking then regranting the same user/role creates a NEW assignment, not the old PK.
      if(type==='assignment')for(let occurrence=2;aliasOwners.has(alias)&&aliasOwners.get(alias)!==key;occurrence++)alias=`${baseAlias}#${occurrence}`;
      if(aliasOwners.has(alias)&&aliasOwners.get(alias)!==key)throw new Error('Distinct entity IDs declared with the same business key');
      map.set(key,alias);aliasOwners.set(alias,key);
    }
    return map.get(key);
  }
  function registerSubject(subject,username) {
    if(subject===null||subject===undefined)return;
    const raw=String(subject),alias=`@subject:${JSON.stringify(username)}`;
    // Subject is immutable during a run. A second raw subject for one username must NOT
    // acquire the same alias: leave it unresolved so changed identity remains distinguishable.
    if(!subjects.has(raw)&&(!subjectOwners.has(alias)||subjectOwners.get(alias)===raw)){subjects.set(raw,alias);subjectOwners.set(alias,raw);}
  }
  function reference(type,value) {
    if(value===null||value===undefined) return value;
    return mapFor(type).get(rawKey(value))??{$unresolvedReference:type,value};
  }
  function subjectReference(value) {
    if(value===null||value===undefined)return value;
    return subjects.get(String(value))??{$unresolvedReference:'subject',value};
  }
  function uuid(value) {
    if(typeof value!=='string'||!UUID.test(value))return value;
    const key=value.toLowerCase();if(!uuidAliases.has(key))uuidAliases.set(key,`@requestUUID:${uuidAliases.size+1}`);return uuidAliases.get(key);
  }
  // Only explicitly registered generated instants are normalized in request time filters.
  // Keep their signed offset, so from=at and from=at+1ms can never compare equal.
  function registerTime(label,instant) {const ms=Date.parse(instant);if(!Number.isFinite(ms))throw new Error('Invalid generated time');times.set(label,ms);}
  function requestTime(value) {
    const ms=Date.parse(value);if(!Number.isFinite(ms))return value;
    for(const [label,origin] of times) if(Math.abs(ms-origin)<=1)return `@time:${label}:${ms-origin}ms`;
    return value;
  }
  function stableKey(type,value,context) {
    const system=value.systemCode??context.systemCode;
    switch(type) {
      case 'user': return value.username;
      case 'system': case 'department': return value.code;
      case 'role': case 'permission': case 'uiResource':
        return value.code!==undefined && system!==undefined && system!==null ? [system,...(type==='permission'?[value.kind]:[]),value.code] : undefined;
      case 'apiOperation': return value.operationId!==undefined && system ? [system,value.operationId] : undefined;
      case 'assignment': return value.userId!==undefined&&value.roleId!==undefined ? [reference('user',value.userId),reference('role',value.roleId)] : undefined;
      case 'audit': return context.auditRequestId ? uuid(context.auditRequestId) : undefined;
      default: return undefined;
    }
  }
  function discover(value,context={}) {
    if(!value||typeof value!=='object')return;
    if(Array.isArray(value)){for(const item of value)discover(item,context);return;}
    const type=entityType(value,context.type),local={...context,systemCode:value.systemCode??context.systemCode};
    if(value.id!==undefined&&type) {const key=stableKey(type,value,local);if(key!==undefined)register(type,value.id,key);}
    if(type==='user'&&value.username!==undefined&&value.subject!==undefined)registerSubject(value.subject,value.username);
    for(const [key,item] of Object.entries(value)) {
      if(UUID_FIELDS.has(key))uuid(item);
      // Business before/after JSON is opaque: do not register identifiers, strip times or sort it.
      if(key==='before'||key==='after')continue;
      discover(item,childContext(key,local,type));
    }
  }
  function plain(value) {
    if(Array.isArray(value))return value.map(plain);
    if(value&&typeof value==='object')return Object.fromEntries(Object.keys(value).sort().map(k=>[k,plain(value[k])]));
    return value;
  }
  function walk(value,context={},field='') {
    if(Array.isArray(value)) {
      const result=value.map(item=>walk(item,context));
      return SET_FIELDS.has(field)?sortSet(result):result;
    }
    if(!value||typeof value!=='object')return value;
    const type=entityType(value,context.type),local={...context,systemCode:value.systemCode??context.systemCode};
    const result={};
    for(const key of Object.keys(value).sort()) {
      const item=value[key];
      if(generatedField(key,value,type))continue;
      if(key==='before'||key==='after'){result[key]=plain(item);continue;}
      if(UUID_FIELDS.has(key)){result[key]=uuid(item);continue;}
      if(key==='id'&&type){result[key]=reference(type,item);continue;}
      if(key==='subject'||key==='actorSubject'){result[key]=subjectReference(item);continue;}
      if(REF_FIELDS[key]){result[key]=key==='userId'&&local.subjectUserId?subjectReference(item):reference(REF_FIELDS[key],item);continue;}
      if(key==='parentId'){result[key]=reference(type==='uiResource'?'uiResource':'department',item);continue;}
      if(key==='roleIds'||key==='permissionIds'){const refType=key==='roleIds'?'role':'permission';result[key]=Array.isArray(item)?sortSet(item.map(id=>reference(refType,id))):item;continue;}
      if(key==='path'&&type==='department'&&typeof item==='string') {
        result[key]=item.split('/').map(part=>/^\d+$/.test(part)?reference('department',part):part);continue;
      }
      if(key==='path'&&Array.isArray(item)) {
        // Inheritance paths are ordered sequences, never sets. Role codes stay literal.
        result[key]=item.map(part=>(typeof part==='number'||/^\d+$/.test(part))?reference('role',part):plain(part));continue;
      }
      result[key]=walk(item,childContext(key,local,type),key);
    }
    return result;
  }
  function normalize(value,context={}) {discover(value,context);return walk(value,context);}
  function observeRequest(method,path,body) {
    const context=routeContext(path);
    // UUID numbering happens at dispatch, NOT at nondeterministic concurrent response completion.
    if(body&&typeof body==='object')for(const key of UUID_FIELDS)if(key in body)uuid(body[key]);
    return {method,path,body,context};
  }
  function observeResponse(request,value) {
    const context={...request.context};
    if(request.path==='/auth/login'&&request.body?.username&&value?.accessToken) {
      try {const claims=JSON.parse(Buffer.from(value.accessToken.split('.')[1],'base64url'));registerSubject(claims.sub,request.body.username);}catch { /* JWT format is separately asserted by the suite. */ }
    }
    if(context.type==='audit'&&request.method==='POST')context.auditRequestId=request.body?.requestId;
    discover(value,context);
    return walk(value,context);
  }
  function normalizePath(path) {
    const url=new URL(path,'http://fixture.invalid'),parts=url.pathname.split('/');
    const subjectLookup=SUBJECT_LOOKUP.test(url.pathname);
    const preceding={users:'user',departments:'department',roles:'role',permissions:'permission',inherits:'role',uiResources:'uiResource','ui-resources':'uiResource','api-operations':'apiOperation','role-assignments':'assignment'};
    for(let i=1;i<parts.length;i++) {
      const prev=parts[i-1],part=decodeURIComponent(parts[i]);
      if(prev==='users'&&subjectLookup){parts[i]=encodeURIComponent(ordered(subjectReference(part)));continue;}
      if((preceding[prev]||prev==='systems')&&/^\d+$/.test(part))parts[i]=encodeURIComponent(ordered(reference(preceding[prev]??'system',part)));
    }
    const query=[];
    for(const [key,value] of url.searchParams) {
      const type=key==='parentId'&&url.pathname==='/auth/departments'?'department':REF_FIELDS[key];
      query.push([key,type?ordered(reference(type,value)):UUID_FIELDS.has(key)?uuid(value):['from','to'].includes(key)?requestTime(value):value]);
    }
    query.sort(([ak,av],[bk,bv])=>ak.localeCompare(bk)||av.localeCompare(bv));
    return parts.join('/')+(query.length?'?'+new URLSearchParams(query):'');
  }
  return {register,registerSubject,registerTime,normalize,observeRequest,observeResponse,normalizePath};
}

// Stateless convenience retains unknown IDs instead of erasing them. Use createNormalizer for runs.
export const normalize=value=>createNormalizer().normalize(value);
