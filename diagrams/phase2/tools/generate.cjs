/* 二阶段总览：从当前 OAS / DDL / Controller 生成可离线阅读的资料。 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const cp = require('node:child_process');
const root = path.resolve(__dirname, '../../..');
const out = path.resolve(__dirname, '..');
let YAML;
try { YAML = require(process.env.YAML_MODULE || 'yaml'); }
catch { throw new Error('需要 YAML 解析器；设置 YAML_MODULE 为 yaml 包的路径，或使 require("yaml") 可用。'); }
for (const p of ['assets', 'data', 'src']) fs.mkdirSync(path.join(out, p), { recursive: true });
const read = p => fs.readFileSync(path.join(root, p), 'utf8');
const write = (p, v) => fs.writeFileSync(path.join(out, p), v, 'utf8');
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[c]));
const centerPath = 'rbac-contract/src/main/resources/openapi/rbac-api.yaml';
const coursePath = 'rbac-contract/src/main/resources/catalog/course-openapi.source.yaml';
const ddlPath = 'rbac-contract/src/main/resources/db/migration/V1__rbac1_schema.sql';
const roleMigration = 'rbac-contract/src/main/resources/db/migration/V7__drop_role_identity_marker.sql';
const center = YAML.parse(read(centerPath));
const course = YAML.parse(read(coursePath));
const catalog = JSON.parse(read('rbac-contract/src/main/resources/catalog/course-operations.json'));
const methods = new Set(['get', 'post', 'put', 'patch', 'delete', 'head', 'options']);
function group(p, op) {
  if (p.startsWith('/auth/audit-logs')) return '集中审计服务';
  if (p.startsWith('/auth/')) return '统一身份与鉴权';
  if (p.startsWith('/integration/') || p.startsWith('/identity/')) return '私有服务接口';
  if (p.includes('/actuator/') || p.includes('/.well-known/')) return '公钥与运行状态';
  if (p.includes('/role-assignments') || /users\/\{id\}\/systems/.test(p)) return '用户角色指派与有效权限';
  if (p.includes('/role-hierarchy') || p.includes('/inherits/') || p.includes('/roles')) return '角色、直接权限与继承';
  if (p.includes('/assignable-roles') || p.includes('/departments')) return '部门与角色准入';
  if (p.includes('/ui-api-mappings')) return 'UI 与 API 映射';
  if (p.includes('/ui-resource') || p.includes('/menus/me')) return '菜单与按钮资源';
  if (p.includes('/api-operations')) return 'API 操作登记';
  if (p.includes('/permissions')) return '权限定义';
  if (p.includes('/authorization/')) return '鉴权与解释';
  if (p.includes('/audit-logs')) return '管理审计查询';
  if (p.includes('/systems')) return '受管系统';
  if (p.includes('/users')) return '用户管理';
  return '本人资料、改密与首页';
}
const normalize = p => p.replace(/\{[^}]+\}/g, '{}');
function walk(p) { return fs.readdirSync(p, {withFileTypes:true}).flatMap(e => e.isDirectory() ? walk(path.join(p,e.name)) : [path.join(p,e.name)]); }
function routes(module) {
  const result = [];
  for (const f of walk(path.join(root,module,'src/main/java')).filter(f=>f.endsWith('.java'))) {
    const s = fs.readFileSync(f,'utf8');
    if (!s.includes('@RestController')) continue;
    const cls = s.indexOf('public class') >= 0 ? s.indexOf('public class') : s.indexOf('public final class');
    const prefix = s.slice(0,cls).match(/@RequestMapping\("([^"]+)"\)/)?.[1] || '';
    const re = /@(Get|Post|Put|Patch|Delete)Mapping\("([^"]+)"\)/g;
    let m;
    while ((m = re.exec(s))) result.push({method:m[1].toUpperCase(),path:prefix+m[2],file:path.relative(root,f).replaceAll('\\','/'),line:s.slice(0,m.index).split('\n').length});
  }
  return result;
}
const sroutes=routes('rbac-structured'), droutes=routes('rbac-ddd');
function operations(doc, source, isCenter) {
  const result=[];
  for (const [p,item] of Object.entries(doc.paths)) for (const [method,op] of Object.entries(item)) {
    if (!methods.has(method)) continue;
    const line = read(source).split('\n').findIndex(l=>l.trim() === `operationId: ${op.operationId}`)+1;
    const match = rs=>rs.filter(r=>r.method===method.toUpperCase() && normalize(r.path)===normalize(p));
    result.push({method:method.toUpperCase(),path:p,id:op.operationId,summary:op.summary||op.operationId,
      description:op.description||'',group:isCenter?group(p,op):(op.tags||['其他']).join(' / '),
      permission:op['x-course-permission']||op['x-required-permission']||(op.security?.length===0?'PUBLIC':''),role:op['x-required-role']||'',
      parameters:[...(item.parameters||[]),...(op.parameters||[])],requestBody:op.requestBody||null,
      responses:op.responses||{},security:op.security??doc.security??[],source,line,
      structured:isCenter?match(sroutes):[],ddd:isCenter?match(droutes):[],
      provider:op['x-provider']==='unified-identity'});
  }
  return result;
}
const centralOps=operations(center,centerPath,true), allCourseOps=operations(course,coursePath,false);
const courseOps=allCourseOps.filter(o=>!o.provider);
const providerCount=allCourseOps.length-courseOps.length;
const totalOperations=centralOps.length+courseOps.length;
const tables=[];
if (!read(roleMigration).includes('DROP COLUMN identity_role')) {
  throw new Error('角色表数据字典须与 V7 迁移一致');
}
const ddl=read(ddlPath);
const tableRegex=/CREATE TABLE (\w+) \(([\s\S]*?)\) ENGINE[^;]*;/g;
let tm;
const labels={sys_system:'受管系统',sys_department:'组织部门',sys_user:'统一用户',sys_role:'系统角色',sys_permission:'权限定义',sys_role_permission:'角色直接权限',sys_user_role:'用户直接角色指派',sys_department_allowed_role:'部门角色准入',sys_role_hierarchy:'角色继承边',sys_ui_resource:'菜单与按钮',sys_api_operation:'API 操作',sys_ui_api_mapping:'UI / API 映射',sys_token_revocation:'令牌撤销',sys_grant_version:'全局授权版本',sys_audit_log:'安全审计快照'};
while((tm=tableRegex.exec(ddl))) {
  const name=tm[1],body=tm[2];
  const lines=body.split('\n').map(l=>l.trim().replace(/,$/,''))
    .filter(line => name !== 'sys_role' || !/^(identity_role |CHECK\(identity_role)/.test(line));
  const fks=[...body.matchAll(/FOREIGN KEY\((\w+)\) REFERENCES (\w+)\((\w+)\)/g)].map(m=>({column:m[1],table:m[2],target:m[3]}));
  const columns=lines.filter(l=>/^\w+\s+(?:BIGINT|TINYINT|VARCHAR|CHAR|INT|BOOLEAN|TIMESTAMP|JSON)\b/.test(l)).map(l=>{
    const m=l.match(/^(\w+)\s+([A-Z]+(?:\([^)]*\))?)(.*)$/);
    return {name:m[1],type:m[2],nullable:!(/NOT NULL|PRIMARY KEY/.test(m[3])),pk:m[3].includes('PRIMARY KEY'),fk:fks.find(f=>f.column===m[1])||null,definition:l};
  });
  tables.push({name,label:labels[name],columns,fks,constraints:lines.filter(l=>/^(?:UNIQUE|CHECK|INDEX)/.test(l)),line:ddl.slice(0,tm.index).split('\n').length});
}
const fks=tables.flatMap(t=>t.fks.map(f=>({from:t.name,to:f.table,column:f.column,nullable:t.columns.find(c=>c.name===f.column).nullable})));
let commit='unavailable';try {commit=cp.execFileSync('git',['-c',`safe.directory=${root.replaceAll('\\','/')}`,'rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim();}catch{}
const sourceFiles=[centerPath,coursePath,ddlPath,roleMigration,'docker-compose.yml','rbac-web/nginx.conf','deploy/full-course-nginx.conf','course-integration/UPSTREAM.lock','rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java','rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java','rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/RoleHierarchy.java','rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java','rbac-gateway/src/main/java/com/xmu/rbac/gateway/GatewayController.java','course-integration/overlay/backend/src/main/java/cn/edu/xmu/projectmgt/integration/OperationGate.java'];
const provenance={generatedOn:'2026-11-19',commit,scope:'静态阅读与提取；不代表本轮执行了业务测试或部署',sources:sourceFiles.map(p=>({path:p,sha256:crypto.createHash('sha256').update(fs.readFileSync(path.join(root,p))).digest('hex')}))};
write('data/provenance.json',JSON.stringify(provenance,null,2));
write('data/openapi-operations.json',JSON.stringify({center:centralOps,course:courseOps,teacherProvider:allCourseOps.filter(o=>o.provider)},null,2));
write('data/schema.json',JSON.stringify({tables,fks},null,2));
const csv=v=>'"'+String(v??'').replaceAll('"','""')+'"';
write('data/openapi-operations.csv','\ufeff'+[['scope','group','method','path','operationId','permission','role','summary'],...centralOps.map(o=>['center',o.group,o.method,o.path,o.id,o.permission,o.role,o.summary]),...courseOps.map(o=>['course',o.group,o.method,o.path,o.id,o.permission,o.role,o.summary])].map(r=>r.map(csv).join(',')).join('\n'));

// 所有图均为本地 SVG，无远程图片、字体、脚本或账号依赖。
const C={ink:'#18322f',muted:'#62736e',teal:'#21796c',light:'#e9f3ee',paper:'#faf9f4',line:'#b8c7bf',orange:'#b66632',amber:'#fbf0df',blue:'#466b90'};
function svgStart(w,h,title,subtitle){return `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" role="img" aria-label="${esc(title)}"><defs><marker id="arrow" markerWidth="9" markerHeight="9" refX="8" refY="4" orient="auto"><path d="M0 0 L8 4 L0 8" fill="none" stroke="${C.teal}" stroke-width="1.5"/></marker><marker id="filled" markerWidth="9" markerHeight="9" refX="8" refY="4" orient="auto"><path d="M0 0 L8 4 L0 8Z" fill="${C.teal}"/></marker></defs><style>text{font-family:'Microsoft YaHei','PingFang SC',sans-serif;fill:${C.ink}} .mono{font-family:Consolas,'Courier New',monospace} .edge{fill:none;stroke:${C.teal};stroke-width:1.5} .label{paint-order:stroke;stroke:${C.paper};stroke-width:7;stroke-linejoin:round}</style><rect width="100%" height="100%" fill="${C.paper}"/><rect x="0" width="9" height="100%" fill="${C.teal}"/>${txt(44,49,title,28,'bold')}${txt(45,80,subtitle,15,'normal',C.muted)}`;}
function txt(x,y,s,size=17,weight='normal',color=C.ink,extra=''){return `<text x="${x}" y="${y}" font-size="${size}" font-weight="${weight}" style="fill:${color}" ${extra}>${esc(s)}</text>`;}
function lines(x,y,arr,size=16,step=25,color=C.muted){return arr.map((s,i)=>txt(x,y+i*step,s,size,'normal',color)).join('');}
function box(x,y,w,h,title,arr=[],fill='white',tag=''){
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="12" fill="${fill}" stroke="${C.line}"/>${tag?txt(x+18,y+25,tag,12,'bold',C.teal):''}${txt(x+18,y+(tag?53:32),title,21,'bold')}${lines(x+18,y+(tag?83:62),arr,16,25)}`;
}
function edge(points,label='',lx,ly,dashed=false){return `<polyline points="${points.map(p=>p.join(',')).join(' ')}" class="edge" ${dashed?'stroke-dasharray="7 5"':''} marker-end="url(#filled)"/>${label?txt(lx,ly,label,15,'normal',C.teal,'class="label"'):''}`;}
function actor(x,y,name,subtitle='') {return `<circle cx="${x}" cy="${y}" r="15" fill="white" stroke="${C.ink}" stroke-width="2"/><path d="M${x} ${y+15}v43m-30-28h60m-30 28l-25 33m25-33l25 33" stroke="${C.ink}" stroke-width="2" fill="none"/>${txt(x,y+116,name,17,'bold',C.ink,'text-anchor="middle"')}${subtitle?txt(x,y+140,subtitle,12,'normal',C.muted,'text-anchor="middle"'):''}`;}
function ellipse(x,y,rx,ry,label){return `<ellipse cx="${x}" cy="${y}" rx="${rx}" ry="${ry}" fill="white" stroke="${C.teal}" stroke-width="1.5"/>${txt(x,y+6,label,17,'normal',C.ink,'text-anchor="middle"')}`;}
function association(a,b,via=[]){return `<polyline points="${[a,...via,b].map(p=>p.join(',')).join(' ')}" fill="none" stroke="${C.line}" stroke-width="1.4"/>`;}
function usecases(){
  let s=svgStart(1500,1160,'01 / 权限中心总体用例图','UML 用例视角 · 矩形为系统边界，椭圆为用户目标，实线为参与关系；管理能力受实际权限限制');
  s+=`<rect x="260" y="125" width="975" height="925" rx="10" fill="${C.light}" stroke="${C.teal}"/>`+txt(288,160,'统一身份与权限中心（结构化 / DDD 二选一）',21,'bold');
  const us=[['session','登录、退出与修改密码',490,230],['me','查看本人资料与有效权限',990,230],['system','登记和启停受管系统',490,365],['directory','管理用户与部门',990,365],['roles','管理角色、权限与继承',490,500],['assign','配置准入、指派和回收角色',990,500],['resources','登记菜单、按钮和 API',490,635],['mapping','维护 UI / API 映射',990,635],['explain','检查权限并解释授权来源',490,770],['audit','查询管理操作审计',990,770],['external','查询身份及 UI / API 准入',490,915],['integration','写入 / 查询课程审计及私有服务接口',990,915]];
  for(const [k,label,x,y] of us){
    if(['session','me'].includes(k))s+= x<700 ? association([130,265],[x-205,y],[[215,y]]) : association([130,265],[x,y-39],[[200,185],[x,185]]);
    if(['system','directory','roles','assign','resources','mapping','explain','audit'].includes(k))s+= x<700 ? association([130,605],[x-205,y],[[225,y]]) : association([130,605],[x,y-39],[[235,y-60],[x,y-60]]);
    if(k==='audit')s+=association([1370,610],[x+205,y],[[1290,y]]);
    if(['external','integration'].includes(k))s+= x>700 ? association([1370,920],[x+205,y],[[1280,y]]) : association([1370,920],[x,y-39],[[1290,y-70],[x,y-70]]);
  }
  for(const [,label,x,y] of us)s+=ellipse(x,y,205,39,label);
  s+=actor(110,230,'已认证用户','登录入口允许匿名访问')+actor(110,570,'权限管理员','总部 / 部门授权范围')+actor(1390,575,'审计查看者','实际以权限码控制')+actor(1390,885,'课程后端 / 网关','受信任外部系统');
  s+=lines(280,1003,['前端菜单控制与服务端鉴权分别执行；健康检查、公钥发现属于技术接口，不单列业务用例。'],15,22);
  s+=lines(45,1090,['说明：管理员、审计查看者均使用统一身份；图中参与者表示职责，不是新增固定角色。','没有将“登录”机械地画成每个用例的 include；认证通常是管理用例的前置条件。'],16,25);
  write('assets/usecase-center.svg',s+'</svg>');
  let c=svgStart(1500,1050,'02 / 课程中心协作范围用例图','课程业务属于外部系统；本图说明接入背景，不将教师课程业务计入权限中心实现');
  c+=`<rect x="280" y="130" width="940" height="730" rx="10" fill="${C.light}" stroke="${C.teal}"/>`+txt(305,165,'教师课程项目管理系统',22,'bold');
  const cs=[['创建 / 审核 / 维护课程',530,250],['教学班、教师与学生名单',970,250],['创建、加入、管理小组',530,400],['里程碑与材料提交',970,400],['口试时段、预约与检查',530,550],['评分、成绩查询与导出',970,550],['使用统一身份与全局鉴权',530,730],['记录和查询关键操作审计',970,730]];
  const actors=[['学生 / 学生组长',110,350],['教师 / 课程组长',110,640],['课程管理员',1380,260],['统一身份与权限中心',1380,700]];
  const fromLeft=(source,i,bus)=>{const [,x,y]=cs[i];return x<750?association(source,[x-192,y],[[bus,y]]):association(source,[x,y-40],[[bus,y-65],[x,y-65]]);};
  const fromRight=(source,i,bus)=>{const [,x,y]=cs[i];return x>750?association(source,[x+192,y],[[bus,y]]):association(source,[x,y+40],[[bus,y+65],[x,y+65]]);};
  for(const i of [2,3,4,5])c+=fromLeft([135,385],i,235);
  for(const i of [0,1,2,3,4,5])c+=fromLeft([135,675],i,255);
  for(const i of [0,1,7])c+=fromRight([1355,295],i,1250);
  for(const i of [6,7])c+=fromRight([1355,735],i,1265);
  for(const [label,x,y] of cs)c+=ellipse(x,y,192,40,label);
  for(const [name,x,y] of actors)c+=actor(x,y,name);
  c+=lines(45,920,['图为高层用例分组，不表示每种参与者都能执行椭圆内全部子操作；具体操作受角色和对象关系约束。','例如：学生查询本人成绩，教师评分与导出；管理员审核课程，教师提交课程申请。','全局 API 许可由权限中心判断；“是否本班教师 / 本组成员”继续由课程 Service 判断。'],16,27);
  write('assets/usecase-course.svg',c+'</svg>');
  const pu=['@startuml','left to right direction','skinparam defaultFontName Microsoft YaHei','actor "已认证用户" as U','actor "权限管理员" as A','actor "审计查看者" as V','actor "课程后端 / 网关" as X','rectangle "统一身份与权限中心" {',...us.map(([k,l])=>`usecase "${l}" as ${k}`),'}','U -- session','U -- me',...['system','directory','roles','assign','resources','mapping','explain','audit'].map(k=>`A -- ${k}`),'V -- audit','X -- external','X -- integration','@enduml'];
  write('src/usecase-center.puml',pu.join('\n'));
  write('src/usecase-course.puml',['@startuml','left to right direction','skinparam defaultFontName Microsoft YaHei',...actors.map(([a],i)=>`actor "${a}" as A${i}`),'rectangle "教师课程项目管理系统" {',...cs.map(([l],i)=>`usecase "${l}" as C${i}`),'}',...[2,3,4,5].map(i=>`A0 -- C${i}`),...[0,1,2,3,4,5].map(i=>`A1 -- C${i}`),...[0,1,7].map(i=>`A2 -- C${i}`),'A3 -- C6','A3 -- C7','@enduml'].join('\n'));
}

// ER：表框布局固定；关系按实际 DDL 提取，网格 A* 寻路避开表框。
function er(){
  const names=[['sys_department','sys_system','sys_user'],['sys_department_allowed_role','sys_role','sys_user_role'],['sys_role_hierarchy','sys_permission','sys_role_permission'],['sys_ui_resource','sys_ui_api_mapping','sys_api_operation'],['sys_token_revocation','sys_grant_version','sys_audit_log']];
  const positions={};
  const keyExtras={sys_system:['code','status'],sys_department:['code','path'],sys_user:['subject','username','token_version'],sys_role:['code','status'],sys_permission:['code','kind','status'],sys_ui_resource:['type','code','route'],sys_api_operation:['operation_id','method','path_template','access_policy','required_role'],sys_token_revocation:['subject','expires_at'],sys_grant_version:['version'],sys_audit_log:['request_id','source_system_code','actor_id','actor_subject','event_type','trace_id','payload_hash']};
  names.forEach((row,r)=>row.forEach((name,c)=>{
    const t=tables.find(t=>t.name===name);const cols=t.columns.filter(k=>k.pk||k.fk||(keyExtras[name]||[]).includes(k.name));
    positions[name]={x:90+c*535,y:170+r*385,w:395,h:90+cols.length*28,cols};
  }));
  const W=1660,H=2240,step=10;
  const occupied=new Set();
  const key=(x,y)=>x+','+y;
  function blocked(x,y){if(x<20||x>W-20||y<125||y>H-125)return true;return Object.values(positions).some(b=>x>b.x-15&&x<b.x+b.w+15&&y>b.y-15&&y<b.y+b.h+15);}
  function route(a,b){
    const sx=Math.round(a[0]/step),sy=Math.round(a[1]/step),tx=Math.round(b[0]/step),ty=Math.round(b[1]/step);
    const start=key(sx,sy),end=key(tx,ty),prev=new Map(),dist=new Map([[start,0]]);
    let open=[{x:sx,y:sy,g:0,f:0}], seen=new Set(),iter=0;
    while(open.length&&iter++<70000){open.sort((a,b)=>b.f-a.f);const cur=open.pop(),ck=key(cur.x,cur.y);if(seen.has(ck))continue;seen.add(ck);if(ck===end){let pts=[],k=ck;while(k){pts.push(k.split(',').map(Number).map(n=>n*step));k=prev.get(k);}pts.reverse();pts.forEach(p=>occupied.add(key(...p)));return pts.filter((p,i)=>i===0||i===pts.length-1||!((p[0]===pts[i-1][0]&&p[0]===pts[i+1][0])||(p[1]===pts[i-1][1]&&p[1]===pts[i+1][1])));}
      for(const [dx,dy]of [[1,0],[-1,0],[0,1],[0,-1]]){const x=cur.x+dx,y=cur.y+dy,nk=key(x,y);if(nk!==end&&nk!==start&&blocked(x*step,y*step))continue;const g=cur.g+1+(occupied.has(key(x*step,y*step))?1.8:0);if(g<(dist.get(nk)??Infinity)){dist.set(nk,g);prev.set(nk,ck);open.push({x,y,g,f:g+Math.abs(x-tx)+Math.abs(y-ty)});}}
    }
    throw new Error('ER 寻路失败 '+a+' -> '+b);
  }
  let edges='',nodes='';
  const cols=[C.teal,C.blue,C.orange,'#76658a'];
  const parentPorts=new Map();
  for(let i=0;i<fks.length;i++){
    const f=fks[i],a=positions[f.from],b=positions[f.to];
    const row=a.cols.findIndex(c=>c.name===f.column);const ay=Math.round((a.y+82+row*28)/10)*10;
    let left = a.x>b.x; if(a.x===b.x)left=i%2===0;
    if(f.from===f.to)left=true;
    const sx=left?a.x:a.x+a.w;
    let tx=left?b.x+b.w:b.x;
    const pk=f.to+':'+(left?'right':'left');
    const port=parentPorts.get(pk)||0;parentPorts.set(pk,port+1);
    let ty=Math.round((b.y+78+port*24)/10)*10;
    let pts;
    if(f.from===f.to){const ex=a.x-45-(i%3)*13;tx=a.x;ty=a.y+35;pts=[[a.x,ay],[ex,ay],[ex,ty],[tx,ty]];}
    else {const start=[sx+(left?-25:25),ay],end=[tx+(left?25:-25),ty];pts=[[sx,ay],...route(start,end),[tx,ty]];}
    const color=cols[i%cols.length];
    const d=pts.map((p,j)=>(j?'L':'M')+p.join(' ')).join(' ');
    const crows=left?-1:1;
    // 从子表 FK 指向父表 PK。子端三叉=多；父端基数以 1 / 0..1 明示。
    edges+=`<g data-from="${f.from}" data-to="${f.to}" class="relation"><title>${esc(f.from+'.'+f.column+' → '+f.to+'.id; 子记录关联 '+(f.nullable?'0..1':'1')+' 个父记录；父记录可被 0..N 个子记录引用')}</title><path d="${d}" fill="none" stroke="${color}" stroke-width="1.5" opacity=".8"/><path d="M${sx} ${ay-7}L${sx+crows*14} ${ay}L${sx} ${ay+7}M${sx} ${ay}h${crows*14}" stroke="${color}" fill="none"/>${txt(tx+(f.from===f.to?-10:left?10:-10),ty-8,f.nullable?'0..1':'1',12,'bold',color,`text-anchor="${f.from===f.to?'end':left?'start':'end'}" class="label"`)}</g>`;
  }
  for(const t of tables){const b=positions[t.name];nodes+=`<g data-table="${t.name}" class="entity"><title>${esc(t.name+' / '+t.label)}</title><rect x="${b.x}" y="${b.y}" width="${b.w}" height="${b.h}" rx="8" fill="white" stroke="${C.line}"/><path d="M${b.x+8} ${b.y}h${b.w-16}q8 0 8 8v49h-${b.w}v-49q0-8 8-8" fill="${C.light}"/>${txt(b.x+15,b.y+24,t.name,17,'bold',C.ink,'class="mono"')}${txt(b.x+15,b.y+46,t.label,14,'normal',C.teal)}`;
    b.cols.forEach((c,i)=>{nodes+=txt(b.x+15,b.y+83+i*28,`${c.pk?'PK':c.fk?'FK':'  '}  ${c.name}${c.nullable?' ?':''}`,16,'normal',C.ink,'class="mono"');});nodes+='</g>';}
  let s=svgStart(W,H,'03 / 权限中心物理 ER 总览','15 张表 · 仅绘制 V1 SQL 中真实外键 · 线由子表 FK 指向父表 PK；关键字段视图，完整列见数据字典');
  s+=edges+nodes+lines(45,2140,[`共 ${fks.length} 条外键；子表端三叉表示父记录可对应 0..N 条子记录；父表端标明每条子记录关联 1 或 0..1 条父记录。`,'字段后的 ? 表示可空；关系线颜色仅用于辨线。令牌撤销、授权版本、审计没有物理外键。','审计保存操作者快照；source_system_code / actor_id 等字段不能据名称臆造外键。'],16,27);
  write('assets/er-overview.svg',s+'</svg>');
  let m=['erDiagram'];
  for(const t of tables){m.push(`  ${t.name} {`);for(const c of t.columns)m.push(`    ${c.type.replace(/[(),]/g,'_')} ${c.name}${c.pk?' PK':c.fk?' FK':''}`);m.push('  }');}
  for(const f of fks)m.push(`  ${f.to} ${f.nullable?'|o':'||'}--o{ ${f.from} : "${f.column}"`);
  write('src/er-physical.mmd',m.join('\n'));
}

function architectures(){
  let s=svgStart(1660,1270,'04 / 运行时架构总览','两个业务系统 · 七个部署服务 · 权限后端结构化 / DDD 二选一 · 箭头表示请求或依赖方向');
  s+=`<rect x="35" y="265" width="1590" height="885" rx="15" fill="${C.light}" stroke="${C.line}" stroke-dasharray="8 5"/>`+txt(53,295,'Docker 私有 overlay 网络；仅两个前端边缘发布 TLS 端口',15,'bold',C.teal);
  s+=edge([[235,210],[235,335]],'HTTPS :8443',250,260);
  s+=edge([[770,210],[770,335]],'HTTPS :9443',785,260);
  s+=edge([[235,485],[235,680]],'/api/v1/* · /auth/*',250,590);
  s+=edge([[975,405],[1150,405]],'/api/*',1000,390);
  s+=edge([[1355,485],[1355,680]],'去除 /api 前缀后转发',1370,590);
  s+=edge([[630,485],[630,570],[360,570],[360,680]],'/auth/* · JWKS',440,552);
  s+=edge([[1210,485],[1210,625],[455,625],[455,730]],'每次请求向中心查询 API 准入',700,610,true);
  s+=edge([[1150,805],[455,805]],'课程后端再次检查准入、加载真实能力、同步审计',605,788,true);
  s+=edge([[235,860],[235,1000],[510,1000]],'中心库',300,983);
  s+=edge([[1355,860],[1355,1128],[895,1128],[895,1040],[860,1040]],'课程库',1080,1113);
  s+=edge([[400,860],[400,925],[1030,925],[1030,965]],'版本化 L1 / Redis L2',605,909,true);
  s+=box(60,115,350,95,'权限管理用户',['管理员 / 审计查看者 / 普通用户']);
  s+=box(595,115,350,95,'课程用户',['学生 / 教师 / 课程管理员']);
  s+=box(60,335,395,150,'rbac-web',['Vue 3 / TypeScript + Nginx','静态页面、TLS、中心接口代理'],'white','01 · 权限中心前端');
  s+=box(580,335,395,150,'course-web',['课程集成前端 + Nginx','登录走中心，业务走网关'],'white','02 · 课程前端');
  s+=box(1150,335,395,150,'rbac-gateway',['可信 method/path → operationId','验证令牌 → 中心决定 → 固定上游'],'white','03 · 独立 Java 网关');
  s+=box(60,680,395,180,'rbac-backend',['MODULE=rbac-structured 或 rbac-ddd','身份 / RBAC1 / 资源 / 集中审计','单aud JWT；私有接口需服务密钥'],'white','04 · 当前选中的权限实现');
  s+=box(1150,680,395,180,'course-backend',['固定教师源码 + patches / overlay','课程 / 分组 / 里程碑 / 评分','原有对象级权限继续执行'],'white','05 · 教师真实后端');
  s+=box(510,965,350,135,'MySQL 8',['rbac_center：15 张中心表','rbac_course：课程业务库'],'white','06 · 两库 / 两账号');
  s+=box(930,965,350,135,'Redis 7',['权限缓存；数据库版本为权威','L1 位于各后端进程内'],'white','07 · 缓存基础设施');
  s+=lines(45,1170,['实线：浏览器 / 代理 / 业务 / 数据访问；虚线：中心安全查询与缓存依赖。响应沿请求链返回。','中心管理请求不经过课程网关；公开边缘拒绝 /integration/*、/identity/*，但可信课程后端可在内网调用。','课程事务与中心审计不共享数据库事务；同步 HTTP 不等于分布式原子提交。'],16,27);
  write('assets/architecture-runtime.svg',s+'</svg>');
  let a=svgStart(1660,1360,'05 / 工程模块与双后端依赖','构建视角 · 箭头从使用方指向被依赖方；domain 不依赖外层技术组件');
  a+=box(480,125,690,125,'rbac-contract',['唯一中心 OAS · 教师操作目录 · V1/V2 SQL · 错误词汇','共享协议和数据定义，不共享核心业务算法'],C.light,'SHARED CONTRACT');
  a+=edge([[390,330],[390,210],[480,210]]);
  a+=edge([[1240,330],[1240,210],[1170,210]]);
  a+=`<rect x="55" y="300" width="720" height="600" rx="12" fill="${C.light}" stroke="${C.line}"/><rect x="880" y="300" width="720" height="600" rx="12" fill="${C.amber}" stroke="${C.line}"/>`;
  a+=txt(80,338,'rbac-structured / 事务脚本式分层',24,'bold')+txt(905,338,'rbac-ddd / 领域职责与端口适配',24,'bold');
  a+=edge([[415,450],[415,495]])+edge([[415,595],[415,640]]);
  a+=box(95,375,640,75,'controller',['HTTP 接入 / DTO 校验 / 响应映射']);
  a+=box(95,495,640,100,'service',['管理流程、DAG 算法、授权上界、事务与审计']);
  a+=box(95,640,640,95,'mapper / Store',['Spring Data JPA / EntityManager / 数据锁']);
  a+=box(95,790,640,75,'辅助包：entity / dto / config',['分别提供数据对象、协议边界与框架装配']);
  a+=edge([[1240,450],[1240,495]])+edge([[1240,595],[1240,640]]);
  a+=edge([[1560,827],[1580,827],[1580,690],[1560,690]],'实现端口',1460,754,true);
  a+=box(920,375,640,75,'interfaces.rest',['HTTP Controller / DTO / VO']);
  a+=box(920,495,640,100,'application',['用例编排：锁、加载、调用领域、保存、审计']);
  a+=box(920,640,640,95,'domain + domain.port',['Role / User / RoleHierarchy / Policy / 仓储抽象']);
  a+=box(920,790,640,75,'infrastructure',['JPA Adapter / Entity / Redis / JWT / AOP']);
  a+=box(55,960,480,145,'rbac-web',['独立 npm 工程；同一套前端','通过配置接入任一中心后端','不属于 Maven reactor']);
  a+=box(590,960,480,145,'rbac-gateway',['第四个 Maven 模块；依赖契约','HTTP 调用中心与课程后端','不依赖两套中心业务实现']);
  a+=box(1125,960,480,145,'course-integration',['上游锁定 + 补丁 + 覆盖层 + 前端','tools/prepare-course.sh 准备源码','教师工程独立构建']);
  a+=box(55,1150,1550,115,'支撑层 / tools · deploy · .github · docs',['契约生成与校验、共享 HTTP 验收、制图、镜像与 Swarm、CI、需求追踪和历史证据','部署统一服务名 rbac-backend；两版同时验证时使用隔离数据库和缓存空间。'],C.light);
  a+=txt(55,1320,'同一外部行为 ≠ 同一内部实现；domain 的纯 JDK 约束由实际架构测试检查。',17,'normal',C.muted);
  write('assets/architecture-modules.svg',a+'</svg>');
  write('src/architecture-runtime.mmd',`flowchart TB
  rbUser["权限管理用户"] --> rbWeb["rbac-web / Nginx"]
  courseUser["课程用户"] --> courseWeb["course-web / Nginx"]
  subgraph network["Docker 私有网络"]
    rbWeb -->|"中心管理与身份接口"| center["rbac-backend：structured 或 ddd"]
    courseWeb -->|"auth 与 JWKS"| center
    courseWeb -->|"api"| gateway["rbac-gateway"]
    gateway -.->|"逐请求 API 准入"| center
    gateway --> courseBackend["course-backend：教师源码与接入补丁"]
    courseBackend -.->|"准入、能力、审计"| center
    center --> centerDb["MySQL / rbac_center"]
    courseBackend --> courseDb["MySQL / rbac_course"]
    center -.-> redis["Redis L2；L1 在后端进程内"]
  end
`);
  write('src/architecture-modules.mmd',`flowchart TB
  contract["rbac-contract：OAS / 目录 / DDL / 错误词汇"]
  subgraph structured["rbac-structured"]
    controllers["controller"] --> services["service：业务过程 / 事务脚本"]
    services --> mappers["mapper / Store：JPA 数据访问"]
    support["辅助包 entity / dto / config"]
  end
  subgraph ddd["rbac-ddd"]
    interfaces["interfaces.rest"] --> application["application：用例编排"]
    application --> domain["domain / domain.port：领域规则与端口"]
    infrastructure["infrastructure：JPA / Redis / JWT / AOP"] -.->|"实现与依赖"| domain
  end
  controllers --> contract
  interfaces --> contract
  gateway["rbac-gateway：HTTP 调用中心与课程"] --> contract
  frontend["rbac-web：独立 npm 工程"]
  integration["course-integration：锁定上游 / 补丁 / 覆盖层 / 前端"]
  supportTools["tools / deploy / CI / docs"]
`);
}

function resolve(doc,obj){return obj?.$ref?doc.components?.[obj.$ref.split('/')[2]]?.[obj.$ref.split('/')[3]]||obj:obj;}
function st(s){if(!s)return '—';if(s.$ref)return s.$ref.split('/').pop();if(s.type==='array')return `array<${st(s.items)}>`;return (s.type||'object')+(s.format?`/${s.format}`:'')+(s.enum?' ['+s.enum.join(' | ')+']':'');}
function bodySchema(body){return body?.content?.['application/json']?.schema||Object.values(body?.content||{})[0]?.schema;}
const functionality={
 '统一身份与鉴权':'统一登录、持久注销；查询当前系统角色和前端权限；分别判断 UI 权限与 API 操作资格。',
 '集中审计服务':'课程后端携带用户令牌与服务密钥写入 / 查询审计；requestId 幂等，操作者从可信身份取得。',
 '本人资料、改密与首页':'查看本人及有效权限、修改密码、查看用户/角色/系统基础计数。',
 '受管系统':'登记、重命名、启停系统；不同系统角色和资源隔离。无系统删除接口。',
 '部门与角色准入':'管理部门树，维护该部门允许指派的角色；替换准入时回收不合资格指派。',
 '用户管理':'分页检索、创建、编辑、启停、逻辑删除和独立部门调动；禁止通过普通 PATCH 偷改部门或状态。',
 '角色、直接权限与继承':'管理系统角色、启停与退役；配置直接权限；维护同系统多父 DAG；查询继承后的有效权限。',
 '用户角色指派与有效权限':'指派、撤销用户角色并查看结果；受部门准入、管理范围和操作者授权上界约束。',
 '权限定义':'按系统及 UI/API 种类检索、创建和启停权限；当前没有通用权限改名或删除端点。',
 '菜单与按钮资源':'维护 MENU/BUTTON 树、路由、顺序与权限码，返回本人可见菜单；父菜单不自动授予按钮权限。',
 'API 操作登记':'登记 operationId、HTTP 方法、路径、权限/角色条件及访问策略；中心受保护路由不能被弱化。',
 'UI 与 API 映射':'维护多对多覆盖关系并查询未映射项；映射用于追踪，不自动授予 API 权限。',
 '鉴权与解释':'查询某用户在某系统的 UI/API 许可，解释角色继承来源；调用资格不足是 403，正常拒绝可为 200 + allowed:false。',
 '管理审计查询':'查询中心和课程来源的审计事实；筛选、权限隔离和分页在业务/数据层完成。',
 '私有服务接口':'提供当前系统真实 API 能力与统一用户查询；仅可信后端可用，公开 Nginx 阻断。',
 '公钥与运行状态':'JWKS 用于签名验证；health/info 用于运行观察，不表示业务验收完成。'
};
const features=[...new Set(centralOps.map(o=>o.group))].map(name=>({name,count:centralOps.filter(o=>o.group===name).length,meaning:functionality[name]}));
const missing=centralOps.filter(o=>!o.structured.length||!o.ddd.length).map(o=>({id:o.id,path:o.path,method:o.method,structured:!!o.structured.length,ddd:!!o.ddd.length,reason:o.path.includes('/actuator/')?'Actuator 框架注册；另核对 application.yml 暴露配置':'待人工核对'}));
const modelNotes=[
 '核心授权链：User → UserRole → Role → RolePermission → Permission；继承边沿 senior → junior，senior 获得 junior 的有效权限。',
 'RoleHierarchy 允许多父与传递继承；同系统检查、自环和长环检查由持锁业务事务及领域对象完成，SQL CHECK 只覆盖部分条件。',
 '部门树负责组织管理与角色准入；准入表示“允许指派”，不表示用户自动获得角色。',
 '菜单按钮是树；角色继承是 DAG；两者不是同一个模型。',
 'Permission.kind 区分 UI 与 API；UI/API 映射只有追踪关系，不能隐式赋权。',
 'sys_token_revocation、sys_grant_version、sys_audit_log 没有外键；撤销按 subject/jti 查询，审计保存历史快照，授权版本是固定全局行。',
 '同一系统限制、父节点类型约束、DAG 无环、授权上界及最后有效超级管理员保护并非都由外键或 CHECK 实现。',
 '中心十五表不是课程库 ER。当前仓库通过固定上游准备课程数据库；不把课程表或课程数据复制进中心。',
 'V1/V2 仅用于新空开发/测试库，不是一期已有数据库的直接升级脚本。'
];
const architectureNotes=[
 '两个业务系统：统一身份与权限中心、课程项目管理系统。权限中心有两套替代实现；选择一个部署为 rbac-backend。',
 '根 Maven reactor 只有 contract、structured、ddd、gateway 四模块；rbac-web、课程前端使用 npm；准备后的教师后端单独构建。',
 '中心管理链：浏览器 → rbac-web/Nginx → rbac-backend → MySQL/Redis。中心自身也受权限控制。',
 '课程业务链：浏览器 → course-web/Nginx → rbac-gateway → course-backend → rbac_course。身份接口由 Nginx 转向权限中心。',
 '网关从真实 method/path 解析 operationId 后查询中心，不接受客户端声明操作身份，不允许任意上游地址。',
 '课程 OperationGate 再次校验中心准入并装载真实 authorities，继续执行教师 Service 对课程、班级、小组的对象级权限判断。',
 '单 aud 的身份 JWT 不携带角色权限；权限变更由权威数据和授权版本控制，不能等 JWT 过期才撤权。',
 '两后端各自实现 L1/L2 缓存；读取 MySQL 授权版本，TTL 只负责旧键回收；Redis 失败可回源，权威数据库失败不凭旧缓存放行。',
 '本地敏感变更、授权版本和 SUCCESS 审计同事务；拒绝记录独立事务。课程数据库与中心审计之间没有分布式原子事务。',
 'Nginx 公开边缘拒绝私有服务接口并清除服务密钥头；课程后端私下调用时仍须用户 Bearer 和服务密钥。'
];
function linkSource(p,label=p){return `[${label}](../../${p})`;}
const tableMd=(heads,rows)=>'| '+heads.join(' | ')+' |\n| '+heads.map(()=>'---').join(' | ')+' |\n'+rows.map(r=>'| '+r.map(v=>String(v??'').replaceAll('|','\\|').replaceAll('\n',' ')).join(' | ')+' |').join('\n');
const intro=`> 阅读基线：${provenance.generatedOn}；Git HEAD：\`${commit}\`。本文来自当前源码、固定教师 API 和数据库迁移的静态分析。本次未运行业务测试或部署，接口存在不等于本轮验收通过。\n`;
function apiMarkdown(){
  let md='# OpenAPI 功能解读与完整操作索引\n\n'+intro+'\n## 先理解文件与计数\n\n';
  md+=`- 中心：${linkSource(centerPath)}，${centralOps.length} 个操作、${Object.keys(center.paths).length} 个路径、${Object.keys(center.components.schemas).length} 个 schema。\n- 教师：${linkSource(coursePath)}，${allCourseOps.length} 个操作，其中 ${courseOps.length} 个课程业务操作与 ${providerCount} 个提供方操作。\n- 提供方操作计入中心目录。两个系统合计 ${totalOperations} 个操作标识，课程目录包含一个公开登录。\n- 中心目录包含 55 个 /api/v1 操作（含 health/info）、8 个统一身份鉴权操作、2 个审计操作、2 个私有服务接口和 1 个 JWKS。\n\n`;
  md+='## 功能分组\n\n'+tableMd(['能力','操作数','实现功能'],features.map(f=>[f.name,f.count,f.meaning]))+'\n\n';
  md+='## 怎样读一个操作\n\n`method + path` 是真实 HTTP 路由；`operationId` 是稳定操作标识，网关以它向中心查询资格；`parameters` 是路径/查询等参数，`requestBody` 是请求体，`responses` 描述状态码及返回模型，`components.schemas` 是可复用结构。\n\n`x-required-permission` 是本项目自定义元数据，不是 OpenAPI 自带鉴权算法。PUBLIC、@authenticated 和具体 API 权限码语义不同。`security: bearerAuth` 只声明 Bearer 方案，不能代替代码中的角色继承、管理范围和对象规则。\n\n';
  md+='## 协议与实现核对要点\n\n';
  md+='- `/api/v1` 普通成功响应使用 `{code:"OK", message, data, timestamp}`；Actuator health/info 是框架原生响应，不属于普通业务包装。\n- `/auth/*`、私有服务接口和 JWKS 使用各自裸响应；退出 204 无响应体。错误采用稳定字符串 `code` 与中文 `message`。\n- `GET /auth/me` 返回有效角色中的 student、teacher、admin 角色码和 UI 权限；管理端 `/api/v1/me` 给出用户与有效授权详情，二者不能混用。\n- UI 权限判断只返回 `{allowed}`；API 资格判断返回 `{operationId,allowed}`；普通无权可返回 200 + false，调用者身份不合法则是 401/403。\n- 审计 POST 提交后返回 201，同 requestId 同体重试返回原记录；同键异体冲突；GET 是裸分页。均要求服务密钥与相应审计权限。\n- `/integration/authorities` 和 `/identity/users/{userId}` 是内网可信服务接口；OpenAPI 写 Bearer 不等于只凭 Bearer 即可调用。\n- 列表通常 page 从 1 开始，pageSize 默认 20、最大 100；固定小集合可返回数组；看每个操作定义。\n- 权限中心 OAS 继承了教师描述中的“本系统不持久化密码/令牌状态”，那段“本系统”原指课程中心；当前中心实际使用 password_hash 与 sys_token_revocation。不能据复制描述否定代码。\n- 不提供公司 CRUD、业务数据范围、RBAC2/3、热力图；当前权限定义没有完整 CRUD，系统也没有 DELETE API。\n\n';
  for(const f of features){md+=`## ${f.name}（${f.count}）\n\n${f.meaning}\n\n`;md+=tableMd(['方法','路径','operationId','入口权限','摘要'],centralOps.filter(o=>o.group===f.name).map(o=>[o.method,'`'+o.path+'`','`'+o.id+'`',o.permission,o.summary]))+'\n\n';}
  md+='## 课程业务目录（80）\n\n课程接口由教师后端实现；权限中心登记其操作和全局许可。UI 权限码、AuthorityCodes 与对象关系分别承担不同职责。\n\n';
  for(const tag of [...new Set(courseOps.map(o=>o.group))]){const ops=courseOps.filter(o=>o.group===tag);md+=`### ${tag}（${ops.length}）\n\n`+tableMd(['方法','路径','operationId','摘要'],ops.map(o=>[o.method,'`'+o.path+'`',o.id,o.summary]))+'\n\n';}
  md+='## 请求模型、参数、状态码与源码\n\n离线页面的 API 页签可搜索并展开每个操作，查看参数、请求模型完整结构、响应模型与 Controller 来源。机器可读提取见 [JSON](data/openapi-operations.json)，可筛选表格见 [CSV](data/openapi-operations.csv)。\n\n';
  md+='## 路由核对\n\n按 HTTP 方法及规范化路径匹配两版 Controller，所有非框架端点均应找到对应声明；health/info 由 Actuator 自动注册。该检查不证明运行时授权或业务行为正确。\n\n'+tableMd(['操作','结构化显式匹配','DDD 显式匹配','解释'],missing.map(o=>[o.id,o.structured,o.ddd,o.reason]))+'\n';
  write('03-openapi.md',md);
}
function documentation(){
  write('01-usecases.md','# 总体用例图\n\n'+intro+'\n![权限中心总体用例](assets/usecase-center.svg)\n\n![课程协作范围](assets/usecase-course.svg)\n\n## 系统边界\n\n权限中心管理统一身份、受管系统、用户、部门、角色继承、UI/API 资源和集中审计。课程中心负责课程、班级、小组、里程碑、提交和成绩。外部课程后端/网关是权限中心的系统参与者。\n\n管理员和审计查看者是参与职责，不是新增的固定数据库角色；实际可执行操作由 API 权限、组织管理范围和授权上界决定。学生组长/任课教师/课程组长还受课程对象关系约束。\n\n图中用例为高层分组，不等于每个参与者都可以执行分组内所有动作。登录是受保护用例的常见前置条件，未机械地添加 include；健康检查不作为业务用例。\n\n## 来源与可编辑文件\n\n- '+linkSource(centerPath)+'\n- '+linkSource(coursePath)+'\n- '+linkSource('rbac-web/src/router/routes.ts')+'\n- [权限中心 PlantUML](src/usecase-center.puml)\n- [课程协作 PlantUML](src/usecase-course.puml)\n');
  let md='# ER 图与数据字典\n\n'+intro+'\n![物理 ER 总览](assets/er-overview.svg)\n\n## 读图说明\n\n图展示十五表与全部实际外键，仅列关键字段；下方字典保留所有列。子表端三叉表示父记录可被多条子记录引用；父表端 1/0..1 表示一条子记录的父记录基数。可空 FK 对应可选父关系。\n\n'+modelNotes.map(t=>'- '+t).join('\n')+'\n\n';
  for(const t of tables){md+=`## ${t.name} · ${t.label}\n\n`+tableMd(['字段','类型','可空','键/关系','完整定义'],t.columns.map(c=>[c.name,c.type,c.nullable?'是':'否',c.pk?'PK':c.fk?`FK → ${c.fk.table}.${c.fk.target}`:'—','`'+c.definition+'`']))+'\n\n'+t.constraints.map(c=>'- `'+c+'`').join('\n')+'\n\n';}
  md+='## 全部外键\n\n'+tableMd(['子表','FK','父表','每条子记录关联父记录'],fks.map(f=>[f.from,f.column,f.to,f.nullable?'0..1':'1']))+'\n\n来源：'+linkSource(ddlPath)+'。可编辑源：[Mermaid ER](src/er-physical.mmd)，提取数据：[schema.json](data/schema.json)。\n';write('02-data-model.md',md);
  const repo=[['rbac-contract/','协议、DDL、资源目录；不含共享授权算法'],['rbac-structured/','Controller → Service → Mapper；规则在服务过程'],['rbac-ddd/','接口 → 应用 → 领域/端口；基础设施实现端口'],['rbac-gateway/','独立鉴权转发入口，固定课程上游'],['rbac-web/','权限管理前端；菜单与按钮按 UI 权限展示'],['course-integration/','固定教师源码的 patches/overlay、接入前端、测试'],['tools/','契约校验、CloneFactory 安装、教师准备、共享验收、制图'],['deploy/','full-* 完整构建、Swarm、切换、密钥与验证工具'],['.github/workflows/','CI：真库、后端、前端、浏览器、课程与图核对'],['docs/','当前契约、双设计、验收记录、答辩和历史'],['diagrams/','一期 StarUML 保留；本目录 phase2 为新总览'],['data/raw/','原始资料；非自动加载、非当前导入接口依据'],['.tmp/','本地原始上下文；不参与构建运行'],['.codeartsdoer/、.idea/','本地工具/IDE 辅助；不是运行服务'],['pom.xml / .mvn / mvnw*','四模块聚合、依赖/插件管理与 Maven Wrapper'],['Dockerfile / docker-compose.yml','Java 运行镜像与完整七服务拓扑']];
  let ar='# 架构总览与目录地图\n\n'+intro+'\n![运行时架构](assets/architecture-runtime.svg)\n\n![模块与双后端](assets/architecture-modules.svg)\n\n## 请求与依赖\n\n'+architectureNotes.map(x=>'- '+x).join('\n')+'\n\n## 目录职责\n\n'+tableMd(['路径','职责'],repo)+'\n\n';
  ar+='## 相对 RBAC0 的变化\n\n'+tableMd(['一期','当前'],[['单套结构化与模拟业务模块','双后端、独立网关、真实课程接入'],['角色无系统归属、没有继承图','角色属于系统，DAG 继承计算有效权限'],['多公司/作用域/旧资源模型','单公司、唯一部门、准入规则、UI/API 独立'],['旧 V1–V4 数据库','新空库十五表 V1/V2；不承诺旧库直接升级'],['旧文档和静态图','契约、双设计、自动制图和可追溯验证'],['intro-spec-coding / human-review / mock','导论迁出、旧审查退出当前工程、真实课程替代 mock']])+'\n\n';
  ar+='## 注意文档年代与真实限制\n\n- 一期 `diagrams/phase1/rbac.mdj` 及 export 明确属于历史材料；不覆盖它们。\n- `docs/design/` 是现行详细设计主要入口；`docs/diagrams/` 包含早期迭代图。\n- `data/raw/README.md` 的导入操作属于一期，当前 OAS 没有对应导入 API。\n- `docs/iteration-log.adoc` 的 M2 骨架状态只描述当时，不代表现在 DDD 仍为空壳。\n- `deploy/build.sh` 发现 full-build.sh 后转入完整流程；不要仅按旧分支理解现行部署。\n- 课程前端是实际联调界面，不能将高层课程用例图解释为这套前端已覆盖教师全部业务 UI。\n- 两版缓存 TTL/技术不同；相同外部契约不要求内部实现一致。\n\n## 关键源码入口\n\n'+sourceFiles.map(p=>'- '+linkSource(p)).join('\n')+'\n';
  write('04-architecture.md',ar);apiMarkdown();
  write('README.md',`# RBAC1 二阶段项目总览\n\n${intro}\n建议先打开 [离线总览页](index.html)，支持图形缩放、接口筛选、模型展开和源码定位；不依赖网络。\n\n1. [总体用例图](01-usecases.md)：权限中心业务边界与课程协作。\n2. [ER 与完整数据字典](02-data-model.md)：十五表、全部外键、关键约束。\n3. [OpenAPI 解读](03-openapi.md)：${centralOps.length} 个中心操作、${courseOps.length} 个课程操作、协议与边界。\n4. [架构总览](04-architecture.md)：七服务、四 Maven 模块、双后端与目录职责。\n\n## 图与源文件\n\n图位于 assets/，提供 SVG 与导出的 PNG；src/ 提供可编辑 PlantUML / Mermaid；布局与网页由 tools/generate.cjs 可重复生成。图中的组件/关系来自当前源码和部署定义，不借用一期图。\n\n- [来源及 SHA-256](data/provenance.json)\n- [操作 JSON](data/openapi-operations.json) / [CSV](data/openapi-operations.csv)\n- [表和外键 JSON](data/schema.json)\n- [静态核对结果](data/verification.json)\n\n## 重生成\n\n在仓库根执行 \`node diagrams/phase2/tools/generate.cjs\`。需要 Node.js 与 \`yaml\` 包；可以通过环境变量 \`YAML_MODULE\` 指定已安装的包目录，避免修改项目依赖。浏览器 PNG 导出与页面验证使用 tools/render.cjs，需要 playwright-core 和本地 Chromium/Edge；通过 PLAYWRIGHT_MODULE、BROWSER_PATH 指定。\n\n本目录以实际 DDL、OAS、Controller、网关、接入层与部署配置为依据。详见 data/provenance.json。本次只执行资料提取、静态一致性与可视化验证，没有重新执行业务验收。\n`);
  return {repo};
}

function schemaHtml(doc,s,depth=0,seen=new Set()) {
  if(!s)return '<span class="muted">无 JSON 模型</span>';
  if(s.$ref){const n=s.$ref.split('/').pop();if(seen.has(n)||depth>5)return `<code>${esc(n)}</code>（递归引用）`;seen=new Set(seen);seen.add(n);s=resolve(doc,s);return `<div class="schema-name">${esc(n)}</div>`+schemaHtml(doc,s,depth+1,seen);}
  if(s.type==='array')return '<div>数组元素：</div>'+schemaHtml(doc,s.items,depth+1,seen);
  if(s.allOf||s.oneOf||s.anyOf){const k=s.allOf?'allOf':s.oneOf?'oneOf':'anyOf';return `<div>${k}</div>`+s[k].map(c=>schemaHtml(doc,c,depth+1,new Set(seen))).join('');}
  if(!s.properties)return `<code>${esc(st(s))}</code>${s.description?'<p>'+esc(s.description)+'</p>':''}`;
  return '<table class="schema"><thead><tr><th>字段</th><th>类型 / 必填</th><th>说明 / 约束</th></tr></thead><tbody>'+Object.entries(s.properties).map(([k,v])=>{
    const rules=['minLength','maxLength','minimum','maximum','minItems','maxItems','pattern','default','example'].filter(r=>v[r]!==undefined).map(r=>`${r}=${JSON.stringify(v[r])}`).join('；');
    const nested=(v.$ref||v.items?.$ref||v.properties||v.allOf||v.oneOf)&&depth<5?`<details><summary>展开模型</summary>${schemaHtml(doc,v,depth+1,new Set(seen))}</details>`:'';
    return `<tr><td><code>${esc(k)}</code></td><td>${esc(st(v))}${(s.required||[]).includes(k)?'<b class="required">必填</b>':''}${v.nullable?' / nullable':''}</td><td>${esc(v.description||'')}<small>${esc(rules)}</small>${nested}</td></tr>`;
  }).join('')+'</tbody></table>';
}
function opHtml(o,doc,scope){
  const params=o.parameters.map(p=>resolve(doc,p));
  const paramsHtml=params.length?`<table><thead><tr><th>位置 / 参数</th><th>类型</th><th>约束与说明</th></tr></thead><tbody>${params.map(p=>`<tr><td>${esc(p.in)} / <code>${esc(p.name)}</code>${p.required?'<b class="required">必填</b>':''}</td><td>${esc(st(p.schema))}</td><td>${esc(p.description||'')}<small>${esc(JSON.stringify(p.schema||{}))}</small></td></tr>`).join('')}</tbody></table>`:'<p class="muted">没有声明路径 / 查询参数。</p>';
  const responses=Object.entries(o.responses).map(([code,r])=>{r=resolve(doc,r);const sch=bodySchema(r);return `<details class="response"><summary><b>${esc(code)}</b> ${esc(r.description||'')} <code>${esc(st(sch))}</code></summary>${sch?schemaHtml(doc,sch):'无响应体'}</details>`;}).join('');
  const sources=scope==='center'?[...o.structured,...o.ddd]:[];
  const extra=o.path.startsWith('/auth/audit-logs')||o.group==='私有服务接口'?'<p class="callout">可信后端调用：还需服务密钥与业务权限；公开边缘不应暴露私有服务接口。</p>':'';
  return `<details class="endpoint" data-scope="${scope}" data-group="${esc(o.group)}" data-method="${o.method}" data-search="${esc([o.path,o.id,o.summary,o.group,o.permission,o.role].join(' ').toLowerCase())}"><summary><span class="method ${o.method}">${o.method}</span><code>${esc(o.path)}</code><span class="op-summary">${esc(o.summary)}</span></summary><div class="op-detail"><div class="op-meta"><code>${esc(o.id)}</code><span>${esc(o.group)}</span><span>入口权限 ${esc(o.permission||'见角色 / 操作元数据')}</span>${o.role?'<span>角色 '+esc(o.role)+'</span>':''}</div>${extra}<h4>用途与说明</h4><p class="preline">${esc(o.description||functionality[o.group]||o.summary)}</p><h4>参数</h4>${paramsHtml}<h4>请求体 ${o.requestBody?.required?'（必填）':''}</h4>${schemaHtml(doc,bodySchema(resolve(doc,o.requestBody)))}<h4>响应状态与模型</h4>${responses}<h4>实现与来源</h4><p><a href="../../${o.source}">${esc(o.source)} : ${o.line}</a></p>${sources.map(s=>`<p><a href="../../${s.file}">${esc(s.file)} : ${s.line}</a></p>`).join('')}${scope==='center'&&!sources.length?'<p>Actuator 由框架注册，见两版 application.yml。</p>':''}</div></details>`;
}
function html(repo){
 const figure=(file,title,note)=>`<figure><div class="figure-head"><div><h3>${title}</h3><p>${note}</p></div><a href="assets/${file}.svg" target="_blank">打开原图 ↗</a></div><div class="diagram-tools"><button type="button" data-zoom="-">−</button><span class="zoom-value">100%</span><button type="button" data-zoom="+">＋</button><button type="button" data-zoom="reset">适应宽度</button><a href="assets/${file}.png">PNG</a></div><div class="canvas"><img src="assets/${file}.svg" alt="${title}" loading="lazy"/></div></figure>`;
 const noteList=a=>'<ul class="notes">'+a.map(x=>'<li>'+esc(x)+'</li>').join('')+'</ul>';
 const card=(n,t,b)=>`<article class="feature"><span>${n}</span><h3>${t}</h3><p>${b}</p></article>`;
 const tableHtml='<div class="table-wrap"><table><thead><tr><th>目录</th><th>职责</th></tr></thead><tbody>'+repo.map(r=>`<tr><td><code>${esc(r[0])}</code></td><td>${esc(r[1])}</td></tr>`).join('')+'</tbody></table></div>';
 let h=`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>RBAC1 · 项目全景</title><link rel="stylesheet" href="assets/style.css"></head><body><aside><a class="brand" href="#overview"><span class="brand-mark">R1</span><span>OOAD / PHASE 02<small>项目阅读图册</small></span></a><nav aria-label="章节"><a href="#overview" class="active">00 <span>从这里开始</span></a><a href="#usecases">01 <span>参与者与用例</span></a><a href="#data">02 <span>数据与关系</span></a><a href="#api">03 <span>OpenAPI 功能</span></a><a href="#architecture">04 <span>体系架构</span></a><a href="#sources">05 <span>重构与依据</span></a></nav><div class="aside-bottom">2026.11.19<br>静态源码解读 · 离线可用<br><a href="README.md">Markdown 阅读入口 ↗</a></div></aside><main><header><span>厦门大学 OOAD / RBAC1 统一身份与权限中心</span><a href="README.md">文件索引 ↗</a></header><section id="overview" class="panel"><p class="eyebrow">A READING ATLAS / 00</p><h1>从功能到数据，<br>看清整个系统。</h1><p class="lead">一套统一权限中心，两种独立实现。<br>连接真实课程系统，沿着用例、接口、数据与部署建立整体认识。</p><div class="metrics"><div><strong>2</strong><span>业务系统</span></div><div><strong>4</strong><span>Maven 模块</span></div><div><strong>15</strong><span>中心数据表</span></div><div><strong>${centralOps.length}<span> + ${courseOps.length}</span></strong><span>中心操作 + 课程操作</span></div></div><div class="intro-grid">${card('01','先看边界','权限中心管理身份与全局授权；课程中心保留课程、教学班和小组的业务规则。')}${card('02','再看双设计','结构化以 Service 过程承载规则；DDD 将规则分配给领域对象，应用层组织用例。')}${card('03','最后看运行','七服务部署，一次选择一个权限后端；同一前端与同一外部契约不变。')}</div><p class="scope">本图册依据当前源码与固定教师版本。没有在本次阅读中重新运行后端验收或部署。计数来自文件解析；API 存在不等于运行行为已验证。</p></section><section id="usecases" class="panel" hidden><p class="eyebrow">ACTORS & INTENT / 01</p><h2>谁使用系统，<br>为了完成什么。</h2><p class="lead">参与者代表职责；一个高层用例可能对应多个接口。认证通常是前置条件，不把所有业务都画成“包含登录”。</p>${figure('usecase-center','权限中心总体用例','真实 UML 椭圆和系统边界；管理员能否执行仍由实际权限与组织范围约束。')}${figure('usecase-course','课程中心协作用例','课程业务属于教师工程；用于理解接入背景，不扩张权限中心职责。')}<div class="callout">学生组长、课程组长等身份与对象关系有关。图中“教师评分 / 学生成绩查询”被高层归组，不意味着学生能执行评分。</div><p><a href="01-usecases.md">完整说明 ↗</a> · <a href="src/usecase-center.puml">PlantUML 源码</a></p></section><section id="data" class="panel" hidden><p class="eyebrow">DATA & RELATIONS / 02</p><h2>十五张表，<br>三条核心关系。</h2><p class="lead">用户—角色—权限决定授权；部门准入约束指派；UI 与 API 建立显式资源映射。</p>${figure('er-overview','物理 ER 总览','结构以 V1–V7 迁移为准；图列关键字段，完整定义见下方字典。三张无外键表保持独立。')}${noteList(modelNotes)}<h3>完整数据字典</h3><p class="muted">PK / FK / 可空性均从 DDL 提取；CHECK 和联合唯一约束保留原文。</p>${tables.map(t=>`<details class="dictionary"><summary><code>${t.name}</code><span>${t.label}</span><small>${t.columns.length} 列 · ${t.fks.length} 外键</small></summary><div class="table-wrap"><table><thead><tr><th>字段</th><th>类型</th><th>可空</th><th>键 / 关系</th><th>DDL</th></tr></thead><tbody>${t.columns.map(c=>`<tr><td><code>${c.name}</code></td><td>${c.type}</td><td>${c.nullable?'是':'否'}</td><td>${c.pk?'PK':c.fk?'→ '+c.fk.table+'.'+c.fk.target:'—'}</td><td><code>${esc(c.definition)}</code></td></tr>`).join('')}</tbody></table></div><pre>${esc(t.constraints.join('\n'))}</pre></details>`).join('')}<p><a href="02-data-model.md">数据说明 ↗</a> · <a href="src/er-physical.mmd">Mermaid ER 源码</a></p></section><section id="api" class="panel" hidden><p class="eyebrow">CAPABILITIES & CONTRACTS / 03</p><h2>接口即功能地图，<br>也标出系统边界。</h2><p class="lead">中心 ${centralOps.length} 个操作。教师快照 ${allCourseOps.length} 个操作，其中 ${providerCount} 个由中心提供，${courseOps.length} 个由课程系统实现。展开接口可查看请求、响应及源码位置。</p><div class="callout"><b>计数口径：</b>中心含 55 个 /api/v1 操作（含 health/info）、8 个统一鉴权操作、2 个集中审计操作、2 个私有服务接口及 1 个 JWKS。课程目录包括公开登录。</div><div class="feature-grid">${features.map(f=>`<article class="api-feature"><b>${esc(f.name)} <em>${f.count}</em></b><p>${esc(f.meaning)}</p></article>`).join('')}</div><details class="reading-notes"><summary>读接口前：协议、权限和描述中的易混点</summary>${noteList(['method + path 是路由；operationId 是网关登记和准入查询的稳定操作标识。','security 声明 Bearer 方案，不自动实现细粒度授权；x-required-permission 是本项目元数据。','普通内部响应包装 code=OK；Actuator、外部身份及私有服务接口采用各自裸响应；204 无响应体。','UI 权限与 API 权限分离；/auth/me 的课程角色码与 /api/v1/me 的内部管理详情不同。','正常鉴权拒绝可为 HTTP 200 + allowed:false；调用者无资格调用该接口才是 401/403。','审计写入与私有接口还需服务密钥，不能仅按 OpenAPI Bearer 声明推断可访问性。','中心 OAS 中“不持久化密码/令牌状态”沿用了课程消费者视角；中心实际保存 password_hash 和 MySQL 撤销。','本次 Controller 路由静态匹配覆盖非 Actuator 中心端点；不代表重新运行全部业务测试。'])}</details><div class="filters"><input id="api-search" type="search" placeholder="搜索路径、功能、operationId、权限码" aria-label="搜索接口"><select id="api-scope" aria-label="选择系统"><option value="center">权限中心 · ${centralOps.length}</option><option value="course">课程业务 · ${courseOps.length}</option><option value="all">全部 · ${totalOperations}</option></select><select id="api-method" aria-label="HTTP 方法"><option value="all">全部方法</option>${['GET','POST','PUT','PATCH','DELETE'].map(m=>`<option>${m}</option>`).join('')}</select><select id="api-group" aria-label="功能分组"><option value="all">全部功能</option></select><span id="api-count" aria-live="polite"></span></div><div id="endpoints">${centralOps.map(o=>opHtml(o,center,'center')).join('')}${courseOps.map(o=>opHtml(o,course,'course')).join('')}</div><p><a href="03-openapi.md">完整 Markdown 清单 ↗</a> · <a href="data/openapi-operations.csv">CSV</a> · <a href="data/openapi-operations.json">JSON</a></p></section><section id="architecture" class="panel" hidden><p class="eyebrow">RUNTIME & DEPENDENCIES / 04</p><h2>运行时怎样协作，<br>代码里怎样分工。</h2><p class="lead">Nginx 分流，网关做全局准入，课程 Service 检查业务对象。两版中心独立实现相同的外部行为。</p>${figure('architecture-runtime','七服务运行时总览','区分浏览器、Nginx、Java 网关、业务后端和数据存储；箭头省略响应返回。')}${figure('architecture-modules','四个 Maven 模块与双后端','双实现不互相调用；基础设施依赖领域端口，domain 不反向依赖 Spring / JPA。')}${noteList(architectureNotes)}<h3>整个仓库的职责</h3>${tableHtml}<p><a href="04-architecture.md">架构说明 ↗</a> · <a href="src/architecture-runtime.mmd">Mermaid 架构源</a></p></section><section id="sources" class="panel" hidden><p class="eyebrow">EVOLUTION & EVIDENCE / 05</p><h2>一次模型和工程边界<br>共同发生的重构。</h2><p class="lead">本阶段不只增加角色继承，还替换模拟接入、收缩旧业务、建立共同契约，并独立实现 DDD 后端。</p><div class="evolution"><div><h3>RBAC0 / 一期</h3><p>单套结构化 + 模拟业务</p><p>多公司 / 旧作用域</p><p>角色没有系统归属与继承</p><p>旧数据库 V1–V4</p><p>分散文档与历史图</p></div><div><h3>RBAC1 / 当前</h3><p>双实现 + 网关 + 真实课程</p><p>单公司 / 部门准入</p><p>系统内角色 DAG / UI 与 API 分离</p><p>十五表空库 V1 / V2</p><p>契约、追踪、自动制图与共享验收</p></div></div><div class="callout">一期模型和导出图归档于 diagrams/phase1，保留原内容。当前详细设计主要位于 docs/design。本次图册在 diagrams/phase2，与一期材料并列。</div><h3>静态核对结果</h3><div class="verification"><p><b>${centralOps.length}</b> 中心操作；<b>${courseOps.length}</b> 课程业务；<b>${tables.length}</b> 数据表；<b>${fks.length}</b> 实际外键。</p><p>两个后端显式 Controller 路由匹配；Actuator 自动端点单列。SVG / PNG 渲染与网页交互由本次可视化检查记录补充。</p></div><h3>原始依据</h3><ul class="source-list">${sourceFiles.map(p=>`<li><a href="../../${p}">${esc(p)}</a></li>`).join('')}</ul><p class="muted">源码基线 <code>${commit}</code><br>文件内容 SHA-256 见 <a href="data/provenance.json">provenance.json</a>；检查结果见 <a href="data/verification.json">verification.json</a>。</p><p class="scope">该图册是当前工作区的静态解读。未将历史测试、已有证据或源码存在当成本轮新执行的业务成功。</p></section><footer>RBAC1 / 项目全景 · 二阶段独立阅读资料 · <a href="README.md">Markdown 入口</a></footer></main><script src="assets/app.js"></script></body></html>`;
 write('index.html',h);
 write('assets/style.css',`*{box-sizing:border-box} :root{--paper:#f7f6f0;--ink:#18322f;--muted:#697970;--teal:#21796c;--line:#dce3d9;--amber:#f7ecda}html{scroll-behavior:smooth}body{margin:0;background:var(--paper);color:var(--ink);font-family:'Microsoft YaHei','PingFang SC',sans-serif;font-size:15px;line-height:1.8}a{color:var(--teal);text-underline-offset:4px}button,input,select{font:inherit}button{cursor:pointer}button:focus-visible,a:focus-visible,summary:focus-visible{outline:3px solid #b66632;outline-offset:3px}aside{position:fixed;inset:0 auto 0 0;width:245px;border-right:1px solid var(--line);padding:35px 25px;display:flex;flex-direction:column;background:#eeefe7}.brand{display:flex;align-items:center;gap:12px;color:var(--ink);text-decoration:none;font-family:Consolas,monospace;font-size:12px;letter-spacing:1px}.brand small{display:block;font-family:'Microsoft YaHei',sans-serif;font-size:12px;letter-spacing:3px;color:var(--muted);margin-top:5px}.brand-mark{display:grid;place-items:center;background:var(--teal);color:white;width:45px;height:45px;font-size:23px;font-weight:bold}.aside-bottom{margin-top:auto;color:var(--muted);font-size:11px;line-height:2.2}nav{display:grid;gap:9px;margin-top:65px}nav a{display:flex;gap:18px;align-items:center;padding:12px;text-decoration:none;color:#76867c;font:12px Consolas,monospace;border-left:2px solid transparent}nav a span{font:14px 'Microsoft YaHei',sans-serif;color:var(--ink)}nav a.active{background:#e1e8dc;color:var(--teal);border-color:var(--teal)}main{margin-left:245px;padding:0 56px;max-width:1940px}header{padding:22px 0;display:flex;justify-content:space-between;border-bottom:1px solid var(--line);font-size:11px;color:var(--muted);letter-spacing:.8px}.panel{padding:54px 0 60px;animation:reveal .3s ease-out}.panel[hidden]{display:none}.eyebrow{font:12px Consolas,monospace;letter-spacing:2px;color:var(--teal);margin:0 0 24px}h1,h2{font-family:'STSong','SimSun',serif;font-weight:500;line-height:1.25;letter-spacing:-1px;margin:0 0 25px}h1{font-size:clamp(44px,5.2vw,78px)}h2{font-size:clamp(37px,4vw,59px)}h3{font-size:19px;margin:0 0 13px}h4{font-size:14px;margin:25px 0 10px}.lead{font-size:17px;line-height:1.9;color:var(--muted);max-width:850px;margin-bottom:32px}.metrics{display:flex;border-block:1px solid var(--line);margin:44px 0;gap:0}.metrics>div{padding:22px 38px 22px 0;margin-right:45px}.metrics strong{font:52px Georgia,serif;display:block;line-height:1.2}.metrics strong span{font-size:32px}.metrics>div>span{font-size:12px;color:var(--muted)}.intro-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:30px}.feature>span{font:13px Consolas,monospace;color:#b66632}.feature h3{margin-top:15px}.feature p,.scope{color:var(--muted);font-size:13px}.scope{border-top:1px solid var(--line);padding-top:22px;margin-top:34px}.feature-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:0 30px;margin:25px 0}.api-feature{border-top:1px solid var(--line);padding:18px 0}.api-feature p{font-size:13px;color:var(--muted);margin:7px 0}.api-feature em{font:12px Consolas,monospace;color:var(--teal);background:#e3eee5;padding:3px 8px;margin-left:7px}.callout{background:var(--amber);border-left:3px solid #b66632;padding:17px 20px;font-size:13px;margin:25px 0}.muted{color:var(--muted)}figure{margin:35px 0 45px;background:#fff;border:1px solid var(--line);min-width:0}.figure-head{display:flex;align-items:start;justify-content:space-between;padding:20px 22px;border-bottom:1px solid var(--line);gap:20px}.figure-head h3{margin:0;font-size:18px}.figure-head p{font-size:12px;color:var(--muted);margin:6px 0 0}.figure-head>a{font-size:12px;white-space:nowrap}.diagram-tools{display:flex;align-items:center;gap:9px;background:#f2f4ed;padding:8px 20px;font-size:12px}.diagram-tools button{border:1px solid #cfdbd1;background:white;color:var(--teal);min-width:30px;padding:3px 8px}.diagram-tools a{margin-left:auto}.zoom-value{font:11px Consolas,monospace;width:42px;text-align:center}.canvas{overflow:auto;max-height:920px;background:#faf9f4}.canvas img{display:block;width:100%;max-width:none;height:auto;transform-origin:top left}.notes{padding-left:22px;max-width:1100px}.notes li{padding:5px 0;font-size:14px}.dictionary,.reading-notes{border-bottom:1px solid var(--line);padding:14px 0}.dictionary summary{display:flex;gap:20px;align-items:center;cursor:pointer;flex-wrap:wrap}.dictionary summary:before{content:'＋';color:var(--teal)}.dictionary[open] summary:before{content:'−'}.dictionary small{margin-left:auto;color:var(--muted)}code{font:12px/1.7 Consolas,'Microsoft YaHei',monospace;overflow-wrap:anywhere}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#eef2ea;padding:15px;font:12px/1.8 Consolas,monospace}.table-wrap{overflow:auto}table{border-collapse:collapse;width:100%;font-size:12px;margin:15px 0}th,td{border-bottom:1px solid var(--line);padding:10px;text-align:left;vertical-align:top}th{font-size:11px;color:var(--muted);white-space:nowrap;background:#f1f3ed}td small{display:block;color:var(--muted);font-size:11px;word-break:break-word}.filters{position:sticky;top:0;z-index:2;display:flex;gap:9px;flex-wrap:wrap;padding:15px 0;background:var(--paper);border-bottom:1px solid var(--line)}input,select{border:1px solid #ccd7cd;background:white;padding:9px 11px;min-width:0;font-size:12px;color:var(--ink)}#api-search{flex:1;min-width:240px}#api-count{font-size:12px;color:var(--muted);align-self:center;min-width:55px}.endpoint{border-bottom:1px solid var(--line)}.endpoint>summary{display:flex;align-items:center;gap:13px;padding:15px 3px;cursor:pointer;list-style:none}.endpoint>summary:after{content:'＋';color:var(--teal);margin-left:auto}.endpoint[open]>summary:after{content:'−'}.endpoint>summary code{font-size:12px}.op-summary{font-size:12px;color:var(--muted);overflow-wrap:anywhere}.method{font:10px Consolas,monospace;font-weight:bold;color:var(--teal);background:#e2eee6;padding:4px 7px;min-width:51px;text-align:center;flex-shrink:0}.POST{background:#e8eafa;color:#525d91}.DELETE{background:#f7e6df;color:#a8593c}.PUT,.PATCH{background:#f6edd9;color:#90672c}.op-detail{background:#fff;padding:20px 25px 30px;border:1px solid var(--line);border-bottom:none}.op-meta{display:flex;flex-wrap:wrap;gap:12px;font-size:11px;color:var(--muted)}.op-detail p{font-size:13px}.preline{white-space:pre-line;overflow-wrap:anywhere}.schema-name{font:12px Consolas,monospace;color:var(--teal);margin:8px 0}.required{display:inline-block;font-size:10px;color:#a8593c;margin-left:7px}.schema details{margin:8px 0}.response{padding:8px 0;font-size:12px}.response summary{cursor:pointer}.evolution{display:grid;grid-template-columns:1fr 1fr;gap:25px;margin:35px 0}.evolution>div{padding:25px;background:#eaf0e5}.evolution>div:first-child{background:#edede7;color:var(--muted)}.evolution p{font-size:14px;margin:12px 0;border-bottom:1px solid #d6ddcf;padding-bottom:12px}.source-list{font-size:12px;word-break:break-word;padding-left:20px}.verification{border-block:1px solid var(--line);padding:10px 0;font-size:13px}.verification b{font:28px Georgia,serif;margin:0 5px}footer{border-top:1px solid var(--line);font-size:11px;color:var(--muted);padding:22px 0 35px}@keyframes reveal{from{opacity:0;transform:translateY(5px)}to{opacity:1;transform:none}}@media(max-width:1000px){aside{width:200px;padding:25px 18px}main{margin-left:200px;padding:0 30px}.metrics>div{margin-right:20px;padding-right:15px}.metrics strong{font-size:42px}.op-summary{display:none}}@media(max-width:720px){aside{position:static;width:auto;border-right:0;border-bottom:1px solid var(--line);padding:17px 20px}.brand{font-size:10px}.brand-mark{width:35px;height:35px;font-size:20px}nav{display:flex;overflow:auto;margin-top:18px;gap:5px}nav a{padding:7px;white-space:nowrap;gap:7px}nav a span{font-size:12px}.aside-bottom{display:none}main{margin-left:0;padding:0 20px}header{font-size:10px;gap:15px}.panel{padding:35px 0}.lead{font-size:15px}.metrics{flex-wrap:wrap;margin:25px 0}.metrics>div{width:50%;margin:0;padding:15px 0}.intro-grid{grid-template-columns:1fr;gap:10px}.feature-grid{grid-template-columns:1fr}.feature h3{margin:5px 0}.figure-head{padding:15px;gap:10px}.figure-head h3{font-size:15px}.figure-head>a{font-size:10px}.diagram-tools{padding:8px 12px}.filters{position:static}#api-search{width:100%;flex-basis:100%}.endpoint>summary{gap:8px}.endpoint>summary code{font-size:10px}.op-detail{padding:15px}.evolution{grid-template-columns:1fr;gap:10px}.dictionary summary{gap:8px}.dictionary small{font-size:10px}.canvas{max-height:650px}.callout{padding:13px;font-size:12px}}@media print{aside,header,.filters,.diagram-tools,footer{display:none}main{margin:0;padding:0}.panel[hidden]{display:block}.panel{break-before:page}.canvas{max-height:none;overflow:visible}figure{break-inside:avoid}h1,h2{font-size:35px}}`);
 write('assets/app.js',`(() => {
const panels=[...document.querySelectorAll('.panel')];
function navigate(){const id=location.hash.slice(1)||'overview';const target=panels.some(p=>p.id===id)?id:'overview';panels.forEach(p=>p.hidden=p.id!==target);document.querySelectorAll('nav a').forEach(a=>a.classList.toggle('active',a.hash==='#'+target));window.scrollTo(0,0)}
window.addEventListener('hashchange',navigate);navigate();
for(const figure of document.querySelectorAll('figure')){let zoom=1;const img=figure.querySelector('img'),label=figure.querySelector('.zoom-value');figure.querySelectorAll('[data-zoom]').forEach(b=>b.addEventListener('click',()=>{const v=b.dataset.zoom;zoom=v==='reset'?1:Math.min(3.5,Math.max(.5,zoom+(v==='+'?.25:-.25)));img.style.width=(zoom*100)+'%';label.textContent=Math.round(zoom*100)+'%'}));}
const endpoints=[...document.querySelectorAll('.endpoint')],search=document.querySelector('#api-search'),scope=document.querySelector('#api-scope'),method=document.querySelector('#api-method'),group=document.querySelector('#api-group'),count=document.querySelector('#api-count');
function groups(){const previous=group.value;group.replaceChildren(new Option('全部功能','all'));const values=[...new Set(endpoints.filter(e=>scope.value==='all'||e.dataset.scope===scope.value).map(e=>e.dataset.group))];values.forEach(v=>group.add(new Option(v,v)));if(values.includes(previous))group.value=previous;}
function filter(){let n=0;const q=search.value.trim().toLowerCase();for(const e of endpoints){const show=(scope.value==='all'||e.dataset.scope===scope.value)&&(method.value==='all'||e.dataset.method===method.value)&&(group.value==='all'||e.dataset.group===group.value)&&(!q||e.dataset.search.includes(q));e.hidden=!show;if(show)n++;}count.textContent=n+' 个操作';}
scope.addEventListener('change',()=>{groups();filter()});search.addEventListener('input',filter);method.addEventListener('change',filter);group.addEventListener('change',filter);groups();filter();
})();`);
}

usecases();er();architectures();const {repo}=documentation();html(repo);
const report={status:'static-generated',counts:{centerOperations:centralOps.length,courseOperations:courseOps.length,teacherProviderOperations:allCourseOps.filter(o=>o.provider).length,centerPaths:Object.keys(center.paths).length,schemas:Object.keys(center.components.schemas).length,tables:tables.length,foreignKeys:fks.length,columns:tables.reduce((n,t)=>n+t.columns.length,0)},controllerExceptions:missing,checks:[]};
function check(name,ok){report.checks.push({name,ok});if(!ok)throw new Error(name);}
check('中心68操作',centralOps.length===68);check('课程80操作及10提供方',courseOps.length===80&&providerCount===10);check('十五张表',tables.length===15);check('每个外键都对应真实表和列',fks.every(f=>tables.some(t=>t.name===f.to&&t.columns.some(c=>c.name==='id'))));check('全部非框架中心操作在两版Controller找到',missing.every(o=>o.path.includes('/actuator/')));check('中心operationId唯一',new Set(centralOps.map(o=>o.id)).size===centralOps.length);
check('中心普通内部53操作及两个Actuator端点',centralOps.filter(o=>o.path.startsWith('/api/v1/')).length===55);
check('两版Actuator路径与暴露配置匹配', ['rbac-structured','rbac-ddd'].every(m=>{const config=YAML.parse(read(m+'/src/main/resources/application.yml'));const web=config.management.endpoints.web;return web['base-path']==='/api/v1/actuator'&&web.exposure.include==='health,info';}));
check('跨系统 operationId 唯一',new Set([...centralOps,...courseOps].map(o=>o.id)).size===totalOperations);
write('data/verification.json',JSON.stringify(report,null,2));
console.log(JSON.stringify(report,null,2));
