#!/usr/bin/env python3
"""Reproduce RBAC1 shared artifacts. Python 3 + PyYAML; --check never writes.
Source snapshot pinned to the teacher repository commit; no database/network access.
Migration verification is deliberately a separate, explicit docker/mysql invocation.
"""
import argparse, copy, csv, hashlib, io, json, re
from pathlib import Path
import yaml
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'rbac-contract/src/main/resources'
CAT = RES / 'catalog'
COMMIT = next(line.removeprefix('commit=') for line in
              (ROOT / 'course-integration/UPSTREAM.lock').read_text(encoding='utf8').splitlines()
              if line.startswith('commit='))
SOURCE = 'course-project-mgt-reference/api/openapi.yaml'
SOURCE_SHA256 = '8a309b5a475b9e24b2146f4ae112b8d0a9f291f48e4556b121758ada440b2d84'
METHODS = {'get','post','put','patch','delete','head','options'}
OUTPUTS = {}
DIRECTORY = {
    '/auth/departments': ('listIdentityDepartments', 'IdentityDepartment'),
    '/auth/departments/{departmentId}/users': ('listIdentityDepartmentUsers', 'IdentityDepartmentUser'),
    '/auth/users/{userId}': ('lookupIdentityUser', None),
}
DEPARTMENT_STATUS_PATH = '/api/v1/departments/{id}/status'
OPERATION_GROUPS = {
    '统一身份鉴权': {
        'login': '登录',
        'logout': '退出登录',
        'getIdentityCurrentUser': '查询当前用户的身份与前端权限',
        'listIdentityDepartments': '查询统一身份部门列表',
        'listIdentityDepartmentUsers': '查询统一身份部门成员',
        'lookupIdentityUser': '按统一身份标识查询用户',
        'checkIdentityPermission': '检查当前用户的 UI 权限',
        'getApiAuthority': '检查当前用户的 API 调用资格',
        'getMe': '查询本人资料与有效权限',
        'changePassword': '修改当前用户密码',
        'getJwks': '获取 JWT 验签公钥',
    },
    '系统管理': {
        'listSystems': '查询受管系统列表',
        'createSystem': '注册受管系统',
        'updateSystem': '修改系统名称',
        'setSystemStatus': '启用或停用系统',
    },
    '部门与角色准入': {
        'getDepartmentTree': '查询部门树',
        'createDepartment': '新增子部门',
        'updateDepartment': '修改部门名称',
        'setDepartmentStatus': '启用或停用部门',
        'deleteDepartment': '删除空部门',
        'getDepartmentAssignableRoles': '查询部门可指派角色',
        'replaceDepartmentAssignableRoles': '替换部门可指派角色',
    },
    '用户管理': {
        'listUsers': '查询用户列表',
        'createUser': '新增用户',
        'getUser': '查询用户详情',
        'updateUser': '修改用户资料',
        'setUserStatus': '修改用户状态',
        'deleteUser': '逻辑删除用户',
        'changeUserDepartment': '调整用户所属部门',
        'listUserRoleAssignments': '查询用户角色指派',
        'createUserRoleAssignment': '为用户分配角色',
        'deleteUserRoleAssignment': '撤销用户角色指派',
        'getUserEffectivePermissions': '查询用户有效权限',
    },
    '角色与继承': {
        'listRoles': '查询角色列表',
        'createRole': '新增角色',
        'getRole': '查询角色详情',
        'updateRole': '修改角色资料',
        'setRoleStatus': '启用或停用角色',
        'deleteRole': '删除角色',
        'getRolePermissions': '查询角色直接权限',
        'replaceRolePermissions': '替换角色直接权限',
        'getRoleEffectivePermissions': '查询角色有效权限',
        'getRoleHierarchy': '查询系统角色继承关系',
        'addRoleInheritance': '添加角色继承关系',
        'deleteRoleInheritance': '删除角色继承关系',
    },
    '权限管理': {
        'listPermissions': '查询权限列表',
        'createPermission': '新增权限',
        'setPermissionStatus': '启用或停用权限',
    },
    '菜单与按钮': {
        'getUiResources': '查询菜单与按钮配置',
        'createUiResource': '新增菜单或按钮',
        'updateUiResource': '修改菜单或按钮',
        'deleteUiResource': '删除菜单或按钮',
        'getMyMenus': '查询当前用户可见菜单',
    },
    'API 登记与映射': {
        'listApiOperations': '查询已登记的 API',
        'createApiOperation': '登记 API 操作',
        'updateApiOperation': '修改 API 操作配置',
        'getUiApiMappings': '查询 UI 与 API 映射及覆盖情况',
        'replaceUiApiMappings': '替换 UI 与 API 映射',
    },
    '鉴权与解释': {
        'checkAuthorization': '检查用户授权',
        'explainAuthorization': '解释用户授权结果',
    },
    '统一身份审计': {
        'queryIdentityAuditLogs': '查询课程审计日志',
        'writeIdentityAuditLog': '写入课程审计日志',
    },
    '管理审计': {
        'queryAuditLogs': '查询管理端审计日志',
    },
    '可信服务接口': {
        'getIntegrationAuthorities': '查询当前用户的服务端授权',
        'lookupUnifiedIdentityUser': '查询用户身份资料',
    },
    '仪表盘与运行状态': {
        'getDashboard': '查询仪表盘基础计数',
        'getHealth': '查询服务健康状态',
        'getInfo': '查询服务信息',
    },
}
def emit(path, text): OUTPUTS[RES / path] = text

def q(v):
    if v is None: return 'NULL'
    if isinstance(v, bool): return '1' if v else '0'
    if isinstance(v, int): return str(v)
    return "'" + str(v).replace("'", "''") + "'"
def insert(table, **values):
    return 'INSERT INTO '+table+' ('+', '.join(values)+') VALUES ('+', '.join(map(q,values.values()))+');\n'

def ddl():
    tables = {
'sys_system': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','code VARCHAR(64) NOT NULL UNIQUE','name VARCHAR(128) NOT NULL','status VARCHAR(16) NOT NULL', 'version BIGINT NOT NULL DEFAULT 0', "CHECK(status IN ('ENABLED','DISABLED'))"],
'sys_department': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','parent_id BIGINT NULL','code VARCHAR(64) NOT NULL UNIQUE','name VARCHAR(128) NOT NULL','path VARCHAR(1024) NOT NULL','status VARCHAR(16) NOT NULL','version BIGINT NOT NULL DEFAULT 0','FOREIGN KEY(parent_id) REFERENCES sys_department(id)',"CHECK(status IN ('ENABLED','DISABLED'))"],
'sys_user': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','subject VARCHAR(128) NOT NULL UNIQUE','username VARCHAR(128) NOT NULL UNIQUE','password_hash VARCHAR(100) NOT NULL','name VARCHAR(128) NOT NULL','no VARCHAR(64) NULL','phone VARCHAR(32) NULL','department_id BIGINT NOT NULL','status VARCHAR(16) NOT NULL','token_version BIGINT NOT NULL DEFAULT 0','must_change_password BOOLEAN NOT NULL DEFAULT true','failed_login_count INT NOT NULL DEFAULT 0','locked_until TIMESTAMP(3) NULL','version BIGINT NOT NULL DEFAULT 0','created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','FOREIGN KEY(department_id) REFERENCES sys_department(id)',"CHECK(status IN ('ACTIVE','DISABLED','DELETED'))",'CHECK(token_version>=0 AND failed_login_count>=0 AND version>=0)','INDEX ix_user_department_status(department_id,status,id)','INDEX ix_user_name(name)'],
'sys_role': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','system_id BIGINT NOT NULL','code VARCHAR(64) NOT NULL','name VARCHAR(128) NOT NULL','description VARCHAR(500) NULL','status VARCHAR(16) NOT NULL','builtin BOOLEAN NOT NULL DEFAULT false','identity_role VARCHAR(16) NULL','version BIGINT NOT NULL DEFAULT 0','FOREIGN KEY(system_id) REFERENCES sys_system(id)','UNIQUE(system_id,code)',"CHECK(status IN ('ENABLED','DISABLED'))", "CHECK(identity_role IS NULL OR identity_role IN ('student','teacher','admin'))"],
'sys_permission': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','system_id BIGINT NOT NULL','code VARCHAR(128) NOT NULL','name VARCHAR(128) NOT NULL','kind VARCHAR(8) NOT NULL','status VARCHAR(16) NOT NULL','version BIGINT NOT NULL DEFAULT 0','FOREIGN KEY(system_id) REFERENCES sys_system(id)','UNIQUE(system_id,kind,code)',"CHECK(kind IN ('UI','API'))", "CHECK(status IN ('ENABLED','DISABLED'))"],
'sys_role_permission': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','role_id BIGINT NOT NULL','permission_id BIGINT NOT NULL','FOREIGN KEY(role_id) REFERENCES sys_role(id)','FOREIGN KEY(permission_id) REFERENCES sys_permission(id)','UNIQUE(role_id,permission_id)'],
'sys_user_role': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','user_id BIGINT NOT NULL','role_id BIGINT NOT NULL','assigned_by BIGINT NULL','assigned_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','FOREIGN KEY(user_id) REFERENCES sys_user(id)','FOREIGN KEY(role_id) REFERENCES sys_role(id)','FOREIGN KEY(assigned_by) REFERENCES sys_user(id)','UNIQUE(user_id,role_id)'],
'sys_department_allowed_role': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','department_id BIGINT NOT NULL','role_id BIGINT NOT NULL','FOREIGN KEY(department_id) REFERENCES sys_department(id)','FOREIGN KEY(role_id) REFERENCES sys_role(id)','UNIQUE(department_id,role_id)'],
'sys_role_hierarchy': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','senior_role_id BIGINT NOT NULL','junior_role_id BIGINT NOT NULL','FOREIGN KEY(senior_role_id) REFERENCES sys_role(id)','FOREIGN KEY(junior_role_id) REFERENCES sys_role(id)','UNIQUE(senior_role_id,junior_role_id)','CHECK(senior_role_id<>junior_role_id)'],
'sys_ui_resource': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','system_id BIGINT NOT NULL','parent_id BIGINT NULL','type VARCHAR(16) NOT NULL','code VARCHAR(128) NOT NULL','name VARCHAR(128) NOT NULL','route VARCHAR(255) NULL','component VARCHAR(64) NULL','sort_order INT NOT NULL DEFAULT 0','status VARCHAR(16) NOT NULL','permission_id BIGINT NULL','version BIGINT NOT NULL DEFAULT 0','FOREIGN KEY(system_id) REFERENCES sys_system(id)','FOREIGN KEY(parent_id) REFERENCES sys_ui_resource(id)','FOREIGN KEY(permission_id) REFERENCES sys_permission(id)','UNIQUE(system_id,code)',"CHECK(type IN ('MENU','BUTTON'))", "CHECK(status IN ('ENABLED','DISABLED'))",'INDEX ix_ui_tree(system_id,parent_id,sort_order,id)'],
'sys_api_operation': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','system_id BIGINT NOT NULL','operation_id VARCHAR(128) NOT NULL','method VARCHAR(16) NOT NULL','path_template VARCHAR(500) NOT NULL','permission_id BIGINT NULL','required_role VARCHAR(64) NULL','access_policy VARCHAR(32) NOT NULL','enabled BOOLEAN NOT NULL DEFAULT true','version BIGINT NOT NULL DEFAULT 0','FOREIGN KEY(system_id) REFERENCES sys_system(id)','FOREIGN KEY(permission_id) REFERENCES sys_permission(id)','UNIQUE(system_id,operation_id)','UNIQUE(system_id,method,path_template)',"CHECK(access_policy IN ('PUBLIC','AUTHENTICATED','PROTECTED'))", "CHECK(method IN ('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS'))", "CHECK(access_policy<>'PROTECTED' OR permission_id IS NOT NULL OR required_role IS NOT NULL)"],
'sys_ui_api_mapping': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','ui_resource_id BIGINT NOT NULL','api_operation_id BIGINT NOT NULL','FOREIGN KEY(ui_resource_id) REFERENCES sys_ui_resource(id)','FOREIGN KEY(api_operation_id) REFERENCES sys_api_operation(id)','UNIQUE(ui_resource_id,api_operation_id)'],
'sys_token_revocation': ['jti VARCHAR(128) PRIMARY KEY','subject VARCHAR(128) NOT NULL','expires_at TIMESTAMP(3) NOT NULL','created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)','INDEX ix_revocation_expiry(expires_at)','INDEX ix_revocation_subject(subject)'],
'sys_grant_version': ['id TINYINT PRIMARY KEY','version BIGINT NOT NULL DEFAULT 0','CHECK(id=1)','CHECK(version>=0)'],
'sys_audit_log': ['id BIGINT AUTO_INCREMENT PRIMARY KEY','request_id CHAR(36) NOT NULL UNIQUE','source_system_code VARCHAR(64) NOT NULL','origin VARCHAR(16) NOT NULL','actor_id BIGINT NULL','actor_subject VARCHAR(128) NULL','actor_name VARCHAR(128) NULL','actor_roles_json JSON NOT NULL','event_type VARCHAR(64) NOT NULL','object_type VARCHAR(64) NULL','object_id VARCHAR(128) NULL','target VARCHAR(500) NOT NULL','before_json JSON NULL','after_json JSON NULL','result VARCHAR(16) NOT NULL','reason VARCHAR(255) NULL','trace_id VARCHAR(64) NOT NULL','payload_hash VARCHAR(64) NOT NULL','occurred_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)',"CHECK(origin IN ('COURSE','INTERNAL'))", "CHECK(result IN ('SUCCESS','DENIED','FAILURE'))",'INDEX ix_audit_system_time(source_system_code,occurred_at,id)','INDEX ix_audit_system_actor(source_system_code,actor_id,occurred_at,id)','INDEX ix_audit_system_object(source_system_code,object_type,object_id,occurred_at,id)']}
    text = '-- RBAC1 EMPTY DEVELOPMENT DATABASE ONLY. Never baseline/migrate an existing production database.\nSET time_zone = \'+00:00\';\n'
    for name, cols in tables.items():
        text += '\nCREATE TABLE '+name+' (\n  '+',\n  '.join(cols)+'\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci'+(' AUTO_INCREMENT=10000' if name=='sys_user' else '')+';\n'
    emit('db/migration/V1__rbac1_schema.sql',text)
    return tables

def ref(n): return {'$ref':'#/components/schemas/'+n}
def arr(s): return {'type':'array','items':ref(s) if isinstance(s,str) else s}
def obj(p, required=None): return {'type':'object','additionalProperties':False,'properties':p,'required':list(p) if required is None else required}
def string(maximum=None, **kw): return dict(type='string', **({'maxLength':maximum} if maximum else {}), **kw)
def enum(*values): return {'type':'string','enum':list(values)}
ID={'type':'integer','format':'int64','minimum':1}; BOOL={'type':'boolean'}; DATE=string(format='date-time'); NULL={'nullable':True}; STATUS=enum('ENABLED','DISABLED'); USERSTATUS=enum('ACTIVE','DISABLED','DELETED')
def nullable(s): return dict(s,nullable=True)
def page(item): return obj({'page':{'type':'integer','minimum':1},'pageSize':{'type':'integer','minimum':1,'maximum':100},'total':{'type':'integer','format':'int64','minimum':0},'items':arr(item)})
def response(s, description='成功'): return {'description':description,'content':{'application/json':{'schema':s}}}
def oas():
    teacher=yaml.safe_load((CAT/'course-openapi.source.yaml').read_text(encoding='utf8'))
    schemas=copy.deepcopy(teacher['components']['schemas'])
    # Retain only the transitive schemas of the identity and audit provider operations.
    external={p:copy.deepcopy(v) for p,v in teacher['paths'].items() if p.startswith('/auth/')}
    needed=set()
    def visit(x):
        if isinstance(x,dict):
            if '$ref' in x and x['$ref'].startswith('#/components/schemas/'):
                n=x['$ref'].split('/')[-1]
                if n not in needed: needed.add(n);visit(schemas[n])
            for v in x.values():visit(v)
        elif isinstance(x,list):
            for v in x:visit(v)
    visit(external)
    schemas={n:v for n,v in schemas.items() if n in needed}
    schemas['Error']=obj({'code':string(minLength=1),'message':string(minLength=1),'details':{}},['code','message'])
    login_properties = schemas['LoginInput']['properties']
    login_properties['username'].pop('example', None)
    login_properties['username']['description'] = 'RBAC1 用户账号'
    login_properties['password']['description'] = '由权限中心验证，数据库仅保存密码哈希。'
    login_properties['systems'].update(maxItems=1, description='仅传一个已启用的系统编码，作为 JWT 的唯一 aud。')
    for field in [login_properties['username'], login_properties['password'], login_properties['systems']['items']]:
        field['pattern'] = r'\S'
    schemas['IdentityCurrentUserVO']['required']=['subject','roles','permissions','name','no']
    schemas['AuditVO']=obj({'id':ID,'at':DATE,'actor':obj({'id':nullable(ID),'name':string(),'roles':arr(string()),'deleted':BOOL}),'type':string(),'target':string(500),'before':nullable({'type':'object','additionalProperties':True}),'after':nullable({'type':'object','additionalProperties':True}),'objectType':nullable(string()),'objectId':nullable(string())},['id','at','actor','type','target','before','after'])
    # The pinned teacher file is OpenAPI 3.1; the center publishes 3.0 nullable schemas.
    parent = schemas['IdentityDepartment']['properties']['parentId']
    schemas['IdentityDepartment']['properties']['parentId'] = nullable(dict(ID, description=parent['description']))
    schemas['IdentityDepartment']['required'].append('parentId')
    schemas['LoginVO']['properties']['tokenType']=enum('Bearer')
    schemas['IdentityAuditLog']=copy.deepcopy(schemas['AuditVO'])
    schemas['IdentityAuditLog']['properties']['type']=enum('score_changed','group_changed','milestone_changed','roster_changed')
    schemas['IdentityAuditLog']['properties']['actor']['properties']['roles']=arr(ref('Role'))
    schemas['IdentityAuditLog']['properties']['actor']['properties']['id']=copy.deepcopy(ID)
    # Page is explicitly concrete; avoid allOf + additionalProperties ambiguity.
    schemas['PageIdentityAuditLog']=page('IdentityAuditLog')
    def strict(x):
        if isinstance(x,dict):
            if x.get('type')=='object' and 'properties' in x:x['additionalProperties']=False
            for v in x.values(): strict(v)
        elif isinstance(x,list):
            for v in x:strict(v)
    strict(schemas)
    schemas.update({
    'SystemVO':obj({'id':ID,'code':string(64),'name':string(128),'status':STATUS}),
    'DepartmentVO':obj({'id':ID,'parentId':nullable(ID),'code':string(64),'name':string(128),'path':string(1024),'status':STATUS,'children':arr('DepartmentVO')}),
    'UserVO':obj({'id':ID,'subject':string(128),'username':string(128),'name':string(128),'no':nullable(string(64)),'phone':nullable(string(32)),'departmentId':ID,'departmentName':string(128),'status':USERSTATUS,'version':{'type':'integer','format':'int64','minimum':0}}),
    'RoleVO':obj({'id':ID,'systemCode':string(64),'code':string(64),'name':string(128),'description':nullable(string(500)),'status':STATUS,'builtin':BOOL,'identityRole':nullable(enum('student','teacher','admin')),'version':{'type':'integer','format':'int64','minimum':0}}),
    'PermissionVO':obj({'id':ID,'systemCode':string(64),'code':string(128),'name':string(128),'kind':enum('UI','API'),'status':STATUS}),
    'AssignmentVO':obj({'id':ID,'userId':ID,'roleId':ID,'systemCode':string(64),'roleCode':string(64),'roleName':string(128),'assignedAt':DATE,'assignedBy':nullable(ID)}),
    'EffectiveVO':obj({'directRoles':arr('RoleVO'),'effectiveRoles':arr('RoleVO'),'permissions':arr('PermissionVO'),'sources':arr(obj({'permissionCode':string(128),'permissionKind':enum('UI','API'),'roleCode':string(64),'inherited':BOOL,'path':arr(string())}))}),
    'UiResourceVO':obj({'id':ID,'systemCode':string(64),'parentId':nullable(ID),'type':enum('MENU','BUTTON'),'code':string(128),'name':string(128),'route':nullable(string(255)),'component':nullable(string(64)),'sortOrder':{'type':'integer'},'status':STATUS,'permissionCode':nullable(string(128)),'children':arr('UiResourceVO')}),
    'ApiOperationVO':obj({'id':ID,'systemCode':string(64),'operationId':string(128),'method':enum('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS'),'pathTemplate':string(500),'permissionCode':nullable(string(128)),'requiredRole':nullable(string(64)),'accessPolicy':enum('PUBLIC','AUTHENTICATED','PROTECTED'),'enabled':BOOL}),
    'MappingVO':obj({'uiResourceId':ID,'apiOperationId':ID}),
    'HierarchyVO':obj({'roles':arr('RoleVO'),'edges':arr(obj({'seniorRoleId':ID,'juniorRoleId':ID}))})})
    paths=external
    identity_descriptions = {
        'login': '权限中心验证用户名与密码，为 `systems` 中指定的单一系统签发 JWT。'
                 '可用系统包括管理中心 `rbac-center` 和课程系统 `course-project-management`。'
                 'JWT 携带身份和令牌状态所需的声明，角色与权限需通过对应接口查询。',
        'logout': '权限中心持久化当前 JWT 的撤销记录，成功后该令牌失效。',
        'getIdentityCurrentUser': '根据 JWT 的 `aud` 返回当前用户、有效身份角色与 UI 权限。'
                                  '无学号或工号时 `no` 返回空字符串。',
        'checkIdentityPermission': '根据 JWT 的 `aud` 查询当前用户是否拥有指定 UI 权限。未知或无权时返回 `allowed: false`。',
        'getApiAuthority': '根据 JWT 的 `aud` 和 `operationId` 查询当前用户调用目标 API 的全局资格。'
                           '已登记但无权或操作已停用时返回 `allowed: false`。'
                           '查询 `rbac-center` 的 `getDashboard` 时同时检查仪表盘所需的三项 API 权限。'
                           '课程对象级权限由课程业务接口继续校验。',
        'queryIdentityAuditLogs': '从权限中心的持久化审计记录查询当前 `aud` 的课程审计日志，按操作者、对象或时间筛选。'
                                  '调用需用户令牌、服务密钥与 `audit:read` API 权限。课程业务范围由课程服务校验。',
        'writeIdentityAuditLog': '权限中心持久化课程审计日志。调用需用户令牌、服务密钥与 `audit:write` API 权限。'
                                 '`objectType` 须与 `objectId` 前缀一致。'
                                 '同一 `requestId`、来源系统、用户及规范化内容的重试返回原记录。'
                                 '同一 `requestId` 对应不同内容或身份时返回 `400 AUDIT_DUPLICATE_CONFLICT`。',
    }
    for p,v in external.items():
        for m,o in v.items():
            if m not in METHODS:continue
            o.pop('servers',None)
            o['x-required-permission']='PUBLIC' if p=='/auth/login' else ('audit:write' if m=='post' and p=='/auth/audit-logs' else 'audit:read' if p=='/auth/audit-logs' else '@authenticated')
            if o['operationId'] in identity_descriptions:
                o['description'] = identity_descriptions[o['operationId']]
            responses = o['responses']
            responses['503'] = {'$ref': '#/components/responses/Unavailable'}
            if p == '/auth/login':
                responses['400'] = {'$ref': '#/components/responses/BadRequest'}
                responses['401'] = response(ref('Error'), '凭据无效、账号不可用或目标系统不可用')
                responses['401']['content']['application/json']['example'] = {
                    'code': 'INVALID_CREDENTIALS', 'message': '身份认证失败'}
            if p == '/auth/permissions/{permission}':
                v['parameters'][0]['schema'] = string(128, minLength=1)
            if p=='/auth/api-permissions/{operationId}' and m=='get':
                responses.setdefault('400', {'$ref': '#/components/responses/BadRequest'})
            if p in DIRECTORY:
                o.update({'x-required-audience': 'course-project-management',
                          'x-required-role': 'admin', 'x-course-permission': 'TEC'})
                _, item = DIRECTORY[p]
                if item:
                    output = 'Page' + item
                    schemas[output] = page(item)
                    responses['200'] = response(ref(output), responses['200']['description'])
                    responses['404'] = response(ref('Error'), '指定部门不存在或已停用（`NOT_FOUND`）')
                else:
                    v['parameters'][0]['schema'] = string(128, minLength=1)
                    o['description'] = '按稳定的 `subject` 查询活跃用户的姓名与学号或工号。无学号或工号时 `no` 返回空字符串。'
                    responses['404'] = response(ref('Error'), '用户不存在或未处于活跃状态（`IDENTITY_USER_NOT_FOUND`）')
                responses.setdefault('400', {'$ref': '#/components/responses/BadRequest'})
                responses.setdefault('401', {'$ref': '#/components/responses/Unauthorized'})
                responses['403'] = response(ref('Error'), '需要课程系统令牌、`admin` 身份角色及 `TEC` API 权限（`PERMISSION_DENIED`）')
            if p=='/auth/audit-logs':
                o.setdefault('parameters',[]).append({'name':'X-Audit-Service-Key','in':'header','required':True,'schema':string(minLength=1),'description':'可信课程后端服务密钥；不得提供给浏览器。'})
                if m=='get':responses['200']=response(ref('PageIdentityAuditLog'))
                responses['403'] = response(ref('Error'), '服务密钥无效（`AUDIT_SERVICE_REQUIRED`）或权限不足（`PERMISSION_DENIED`）')
    version={'type':'integer','format':'int64','minimum':0}
    schemas.update({
    'PasswordInput':obj({'oldPassword':string(minLength=1,pattern=r'\S',format='password',writeOnly=True),'newPassword':string(256,minLength=8,pattern=r'\S',format='password',writeOnly=True)}),
    'SystemCreateInput':obj({'code':string(64,minLength=1),'name':string(128,minLength=1)}),
    'NameInput':obj({'name':string(128,minLength=1)}),
    'StatusInput':obj({'status':STATUS}), 'UserStatusInput':obj({'status':USERSTATUS}),
    'DepartmentCreateInput':obj({'parentId':nullable(ID),'code':string(64,minLength=1),'name':string(128,minLength=1)},['code','name']),
    'RoleIdsInput':obj({'roleIds':arr(ID)}), 'PermissionIdsInput':obj({'permissionIds':{**arr(ID),'maxItems':1000}}),
    'UserCreateInput':obj({'username':string(128,minLength=1),'name':string(128,minLength=1),'no':nullable(string(64)),'phone':nullable(string(32)),'departmentId':ID,'password':string(256,minLength=8,pattern=r'\S',format='password',writeOnly=True)},['username','name','departmentId','password']),
    'UserUpdateInput':obj({'name':string(128,minLength=1),'no':nullable(string(64)),'phone':nullable(string(32)),'version':version},[]),
    'DepartmentChangeInput':obj({'departmentId':ID,'version':version},['departmentId']),
    'RoleCreateInput':obj({'systemCode':string(64,minLength=1),'code':string(64,minLength=1),'name':string(128,minLength=1),'description':nullable(string(500)),'identityRole':nullable(enum('student','teacher','admin'))},['systemCode','code','name']),
    'RoleUpdateInput':obj({'name':string(128,minLength=1,pattern=r'\S'),'description':nullable(string(500)),'version':version},[]),
    'AssignmentInput':obj({'roleId':ID}),
    'PermissionCreateInput':obj({'systemCode':string(64,minLength=1),'code':string(128,minLength=1),'name':string(128,minLength=1),'kind':enum('UI','API')}),
    'UiCreateInput':obj({'parentId':nullable(ID),'type':enum('MENU','BUTTON'),'code':string(128,minLength=1),'name':string(128,minLength=1),'route':nullable(string(255)),'component':nullable(string(64)),'sortOrder':{'type':'integer','default':0},'permissionCode':string(128,minLength=1),'status':STATUS},['type','code','name','permissionCode']),
    'ApiCreateInput':obj({'operationId':string(128,minLength=1),'method':enum('GET','POST','PUT','PATCH','DELETE','HEAD','OPTIONS'),'pathTemplate':string(500,minLength=1,pattern='^/'),'permissionCode':nullable(string(128)),'requiredRole':nullable(string(64)),'accessPolicy':enum('PUBLIC','AUTHENTICATED','PROTECTED'),'enabled':BOOL},['operationId','method','pathTemplate','accessPolicy','enabled']),
    'MappingsInput':obj({'mappings':arr('MappingVO')}),
    'AuthorizationInput':obj({'systemCode':string(64,minLength=1),'userId':ID,'kind':enum('UI','API'),'permission':string(128,minLength=1),'operationId':string(128,minLength=1)},['systemCode','kind']),
    'DecisionVO':obj({'allowed':BOOL,'reason':string(),'traceId':string(64)}),
    'ExplanationVO':obj({'allowed':BOOL,'reason':string(),'traceId':string(64),'trace':arr(string()),'effective':ref('EffectiveVO')}),
    'MappingCoverageVO':obj({'mappings':arr('MappingVO'),'unmappedUi':arr('UiResourceVO'),'unmappedApi':arr('ApiOperationVO')}),
    'MeVO':obj({'user':ref('UserVO'),'effective':ref('EffectiveVO')}),
    'DashboardVO':obj({n:{'type':'integer','format':'int64','minimum':0} for n in ['users','roles','systems']})})
    schemas['AuthorizationInput']['oneOf']=[{'properties':{'kind':enum('UI')},'required':['permission'],'not':{'required':['operationId']}},{'properties':{'kind':enum('API')},'required':['operationId'],'not':{'required':['permission']}}]
    for n in ['Ui','Api']:
        schemas[n+'UpdateInput']=copy.deepcopy(schemas[n+'CreateInput']);schemas[n+'UpdateInput']['required']=[]
    table=(ROOT/'docs/implementation-contract.adoc').read_text(encoding='utf8')
    rows=re.findall(r'^\|(GET|POST|PATCH|PUT|DELETE) (/[^ |]+) \|([^\n]+) \|([^\n]+)$',table,re.M)
    assert len(rows)==53,len(rows)
    # Explicit operationIds are stable and not derived from translated labels.
    definitions=[
    ('getMe',None,'MeVO'),('changePassword','PasswordInput',None),('listSystems',None,arr('SystemVO')),('createSystem','SystemCreateInput','SystemVO'),('updateSystem','NameInput','SystemVO'),('setSystemStatus','StatusInput','SystemVO'),
    ('getDepartmentTree',None,arr('DepartmentVO')),('createDepartment','DepartmentCreateInput','DepartmentVO'),('updateDepartment','NameInput','DepartmentVO'),('setDepartmentStatus','StatusInput','DepartmentVO'),('deleteDepartment',None,None),('getDepartmentAssignableRoles',None,arr('RoleVO')),('replaceDepartmentAssignableRoles','RoleIdsInput',None),
    ('listUsers',None,'PageUserVO'),('createUser','UserCreateInput','UserVO'),('getUser',None,'UserVO'),('updateUser','UserUpdateInput','UserVO'),('setUserStatus','UserStatusInput','UserVO'),('deleteUser',None,None),('changeUserDepartment','DepartmentChangeInput','UserVO'),
    ('listRoles',None,'PageRoleVO'),('createRole','RoleCreateInput','RoleVO'),('getRole',None,'RoleVO'),('updateRole','RoleUpdateInput','RoleVO'),('setRoleStatus','StatusInput','RoleVO'),('deleteRole',None,None),('getRolePermissions',None,arr('PermissionVO')),('replaceRolePermissions','PermissionIdsInput',None),('getRoleEffectivePermissions',None,'EffectiveVO'),('getRoleHierarchy',None,'HierarchyVO'),('addRoleInheritance',None,None),('deleteRoleInheritance',None,None),
    ('listUserRoleAssignments',None,arr('AssignmentVO')),('createUserRoleAssignment','AssignmentInput','AssignmentVO'),('deleteUserRoleAssignment',None,None),('getUserEffectivePermissions',None,'EffectiveVO'),('listPermissions',None,'PagePermissionVO'),('createPermission','PermissionCreateInput','PermissionVO'),('setPermissionStatus','StatusInput','PermissionVO'),('getUiResources',None,arr('UiResourceVO')),('createUiResource','UiCreateInput','UiResourceVO'),('updateUiResource','UiUpdateInput','UiResourceVO'),('deleteUiResource',None,None),('getMyMenus',None,arr('UiResourceVO')),('listApiOperations',None,'PageApiOperationVO'),('createApiOperation','ApiCreateInput','ApiOperationVO'),('updateApiOperation','ApiUpdateInput','ApiOperationVO'),('getUiApiMappings',None,'MappingCoverageVO'),('replaceUiApiMappings','MappingsInput',None),('checkAuthorization','AuthorizationInput','DecisionVO'),('explainAuthorization','AuthorizationInput','ExplanationVO'),('queryAuditLogs',None,'PageAuditVO'),('getDashboard',None,'DashboardVO')]
    assert len(rows)==len(definitions),(len(rows),len(definitions))
    management_descriptions = {
        'getMe': '返回当前登录用户的基本资料，以及在 JWT 的 `aud` 对应系统中的直接角色、有效角色、权限和权限来源。',
        'changePassword': '修改成功后返回 HTTP 200 和 `data: null`，此前签发给该用户的令牌失效。',
        'listSystems': '列出权限中心已登记的系统，返回各系统的编码、名称和启停状态。',
        'createSystem': '以唯一的系统编码注册受管系统。新系统默认启用，随后可在该系统下配置角色、权限和资源。',
        'updateSystem': '按系统 ID 修改显示名称，并返回更新后的系统资料。系统编码用于 JWT `aud` 和系统级配置定位。',
        'setSystemStatus': '启用或停用指定系统。停用期间拒绝该系统的运行时授权。重新启用时校验即将恢复的授权。',
        'getDepartmentTree': '返回以操作者所属部门为起点的组织树，包含该部门及其下级部门。',
        'createDepartment': '在管理范围内的父部门下新建子部门。父部门通过 `parentId` 指定，系统已有根部门。',
        'updateDepartment': '修改管理范围内指定部门的名称，并返回更新后的部门资料。',
        'setDepartmentStatus': '启用或停用管理范围内指定部门。仅非根且无子部门的节点可停用。停用保留成员和既有角色指派，并关闭新接纳。',
        'deleteDepartment': '删除管理范围内的空部门并清理其角色准入配置。指定部门为根部门或仍关联人员、子部门时返回 HTTP 409。',
        'getDepartmentAssignableRoles': '查询指定部门已配置的角色准入清单，可用 `systemCode` 筛选角色所属系统。',
        'replaceDepartmentAssignableRoles': '用 `roleIds` 整体替换指定部门的角色准入清单，并在同一事务内撤销该部门直属用户已不满足准入条件的角色指派。',
        'listUsers': '在操作者所属部门及其子部门范围内分页查询用户，支持按关键字、部门 ID 和用户状态筛选。',
        'createUser': '在管理范围内的指定部门创建用户。服务端生成稳定的 `subject`，以哈希保存初始密码，并返回用户资料。',
        'getUser': '按用户 ID 查询管理范围内的用户资料，包含部门、状态及用于并发更新的版本。',
        'updateUser': '更新管理范围内用户的姓名、学号或工号、手机号。仅提交要修改的字段，可提供 `version` 检查并发更新。',
        'setUserStatus': '将用户状态设为 `ACTIVE`、`DISABLED` 或 `DELETED`。停用和逻辑删除会使已有令牌失效，逻辑删除还会清空角色指派。',
        'deleteUser': '逻辑删除管理范围内的指定用户，撤销其角色指派，并使此前签发的令牌失效。',
        'changeUserDepartment': '跨部门调动会在更新部门的同一事务中清空该用户的角色指派。提交当前部门时保留原指派。',
        'listRoles': '按必填的 `systemCode` 查询角色目录，支持按关键字和角色状态筛选并分页返回。',
        'createRole': '在指定系统下创建默认启用的角色。可配置 `identityRole`，供统一身份接口投影为学生、教师或管理员身份。',
        'getRole': '按角色 ID 查询角色的所属系统、编码、显示信息、启停状态和版本。',
        'updateRole': '更新角色名称或描述。省略的字段保留原值，`description` 显式传 `null` 可清空描述。支持通过 `version` 检查并发更新。',
        'setRoleStatus': '启用或停用指定角色。停用的角色在运行时失效并中断经过该角色的继承路径，重新启用时校验受影响角色的授权。',
        'deleteRole': '删除角色并清理关联的用户指派、部门准入、直接权限和继承边。服务端保护最后一个有效超级管理员。',
        'getRolePermissions': '列出直接绑定到该角色的权限配置。需要查看继承后的权限及其来源时，调用角色有效权限接口。',
        'replaceRolePermissions': '用 `permissionIds` 整体替换角色的直接权限。权限必须与角色属于同一系统，空数组清空直接权限。替换时校验受影响角色的授权。',
        'getRoleEffectivePermissions': '以指定角色为起点计算已配置的有效角色、权限和来源。即使所属系统已停用，仍可查看由启用角色和权限组成的配置。',
        'getRoleHierarchy': '返回指定系统的角色和有向继承边。每条边从 `seniorRoleId` 指向其继承的 `juniorRoleId`。',
        'addRoleInheritance': '添加同一系统内从 `seniorId` 指向 `juniorId` 的继承边。重复边或会形成环的边返回 HTTP 409。',
        'deleteRoleInheritance': '删除这两个角色之间指定方向的直接继承边。删除后按剩余路径计算继承权限，找不到该边时返回 HTTP 404。',
        'listUserRoleAssignments': '查看管理范围内指定用户的直接角色指派。可用 `systemCode` 筛选，返回指派 ID、所属系统和指派时间等信息。',
        'createUserRoleAssignment': '向管理范围内的用户直接指派角色。角色须列入该用户当前部门的准入清单，并满足用户、角色及所属系统的启用条件。',
        'deleteUserRoleAssignment': '撤销路径中指定用户的某条直接角色指派。服务端核对 `assignmentId` 是否属于该用户。',
        'getUserEffectivePermissions': '查询管理范围内指定用户在目标系统的直接角色、有效角色、去重权限和授权来源。用于查看该用户在该系统中的运行时授权。',
        'listPermissions': '分页查询已定义的权限，可按所属系统、`UI` 或 `API` 种类及关键字筛选。',
        'createPermission': '在指定系统创建权限定义。权限编码在同一系统和种类内唯一，创建后可将该权限显式授予角色。',
        'setPermissionStatus': '启用或停用指定权限定义。停用后该权限退出运行时有效权限集合，角色中的绑定可供后续恢复使用。',
        'getUiResources': '查询指定系统的菜单和按钮配置树，包含资源状态和关联的 UI 权限码，供管理端编辑资源。',
        'createUiResource': '为指定系统新建菜单或按钮，并绑定该系统中已登记的 UI 权限。按钮需要挂在菜单下。',
        'updateUiResource': '修改菜单或按钮的配置，例如显示名称、父菜单、路由、关联权限或状态。父菜单和关联权限须属于资源所在系统。',
        'deleteUiResource': '删除指定的菜单或按钮。资源仍包含子节点或被 UI 与 API 映射引用时返回 HTTP 409。',
        'getMyMenus': '返回当前用户在 JWT 的 `aud` 对应系统中可见的菜单和按钮树。树按用户的有效 UI 权限裁剪，并保留展示所需的父菜单。',
        'listApiOperations': '分页查询指定系统已登记的 API 操作。可按 `operationId` 或路径模板搜索，结果包含访问策略和启用状态。',
        'createApiOperation': '登记指定系统的 API 方法、路径模板和访问策略。`PROTECTED` 操作至少需要 API 权限或身份角色条件，固定中心操作须与共享 OpenAPI 契约一致。',
        'updateApiOperation': '修改已登记操作的配置，包括访问策略、权限条件或启用状态。固定中心操作的配置须与共享 OpenAPI 契约一致并保持启用。',
        'getUiApiMappings': '返回指定系统的 UI 与 API 显式映射及覆盖情况，包括尚未建立映射的 UI 资源和 API 操作。',
        'replaceUiApiMappings': '用 `mappings` 整体替换指定系统的映射。每条映射的 UI 资源和 API 操作须属于该系统。映射供追踪和覆盖检查使用。',
        'checkAuthorization': '判断指定用户在目标系统中的授权资格，省略 `userId` 时检查调用者本人。UI 检查使用 `permission`，API 检查使用 `operationId`。结果包含决定、原因和追踪 ID。',
        'explainAuthorization': '按与授权检查相同的输入和管理范围计算决定，并返回判定步骤及目标用户的有效角色、权限和来源，供排查授权结果。',
        'queryAuditLogs': '分页查询权限中心记录的管理和课程审计事件，可按来源系统、操作者、目标和时间范围筛选。',
        'getDashboard': ('使用 `rbac-center` 令牌，调用者须同时具备 `rbac:user:view`、`rbac:role:view` 和 '
                         '`rbac:system:view` 三项 API 权限。`x-required-permission` 是路由登记的入口权限，'
                         '`x-required-permissions-all` 列出服务端同时校验的权限。'),
    }
    for name in ['UserVO','RoleVO','PermissionVO','ApiOperationVO','AuditVO']:schemas['Page'+name]=page(name)
    for (method,path,_,permission),(oid,inp,out) in zip(rows,definitions):
        out=ref(out) if isinstance(out,str) else out
        wrapper=obj({'code':enum('OK'),'message':string(),'data':out or {'type':'object','nullable':True,'enum':[None]},'timestamp':DATE})
        op = {
            'operationId': oid,
            'description': management_descriptions[oid],
            'x-required-permission': permission.strip(),
            'x-required-audience': 'rbac-center' if path not in ['/me', '/menus/me', '/auth/password'] else '@current',
            'responses': {'200': response(wrapper)},
        }
        if inp:op['requestBody']={'required':True,'content':{'application/json':{'schema':ref(inp)}}}
        op['parameters']=[]
        for arg in re.findall(r'\{([^}]+)\}',path):op['parameters'].append({'name':arg,'in':'path','required':True,'schema':string(64,minLength=1) if arg=='code' else ID})
        query={
        'listUsers':{'keyword':string(),'departmentId':ID,'status':USERSTATUS},'listRoles':{'systemCode':string(64,minLength=1),'keyword':string(),'status':STATUS},'listPermissions':{'systemCode':string(64),'kind':enum('UI','API'),'keyword':string()},'listApiOperations':{'keyword':string()},'getDepartmentAssignableRoles':{'systemCode':string(64)},'listUserRoleAssignments':{'systemCode':string(64)},'queryAuditLogs':{'systemCode':string(64),'actor':string(),'actorId':ID,'target':string(500),'from':DATE,'to':DATE}}
        for n,s in query.get(oid,{}).items():op['parameters'].append({'name':n,'in':'query','required':oid=='listRoles' and n=='systemCode','schema':s})
        if isinstance(out,dict) and out.get('$ref','').split('/')[-1].startswith('Page'):
            op['parameters'] += [{'$ref':'#/components/parameters/Page'},{'$ref':'#/components/parameters/PageSize'}]
        for status in ['400','401','403','404','409','503']:op['responses'][status]={'$ref':'#/components/responses/'+{'400':'BadRequest','401':'Unauthorized','403':'Forbidden','404':'NotFound','409':'Conflict','503':'Unavailable'}[status]}
        paths.setdefault('/api/v1'+path,{})[method.lower()]=op
    dashboard = paths['/api/v1/dashboard']['get']
    dashboard['x-required-permissions-all'] = ['rbac:user:view', 'rbac:role:view', 'rbac:system:view']
    # Private service-to-service projections remain separate from the teacher provider operations.
    schemas['IntegrationAuthoritiesVO']=obj({'authorities':arr(string())})
    schemas['IdentityUserLookupVO']=obj({'userId':string(128),'name':string(128),'no':string(64)})
    for path,oid,out in [('/integration/authorities','getIntegrationAuthorities','IntegrationAuthoritiesVO'),('/identity/users/{userId}','lookupUnifiedIdentityUser','IdentityUserLookupVO')]:
        parameters=[{'name':'X-Audit-Service-Key','in':'header','required':True,'schema':string(minLength=1),'description':'Only trusted course backend; never exposed in frontend configuration.'}]
        if '{userId}' in path:parameters.append({'name':'userId','in':'path','required':True,'schema':string(128,minLength=1)})
        paths[path]={'get':{'operationId':oid,'x-required-permission':'@authenticated','x-required-audience':'@current','x-trusted-service':True,'description':'Service key plus validated current user; lookup additionally requires admin identity and TEC API capability. No target subject/system overrides for authority projection.','parameters':parameters,'responses':{'200':response(ref(out)),**{status:{'$ref':'#/components/responses/'+name} for status,name in [('400','BadRequest'),('401','Unauthorized'),('403','Forbidden'),('404','NotFound'),('503','Unavailable')]}}}}
    public_operations = [
        ('/.well-known/jwks.json', 'getJwks',
         obj({'keys': arr(obj({'kty': enum('RSA'), 'use': enum('sig'), 'kid': string(),
                               'alg': enum('RS256'), 'n': string(), 'e': string()}))}),
         '返回验证 RS256 JWT 所用的公钥集合。验证方根据令牌头中的 `kid` 选取公钥。'),
        ('/api/v1/actuator/health', 'getHealth',
         {'type': 'object', 'required': ['status'], 'properties': {'status': string()}, 'additionalProperties': True},
         '返回服务的总体健康状态 `status`，供运行监测使用。组件和详情由各后端的 Actuator 配置决定。'),
        ('/api/v1/actuator/info', 'getInfo',
         {'type': 'object', 'additionalProperties': True},
         '返回 Actuator 已配置的应用信息。没有配置可发布的信息时返回空对象。'),
    ]
    for path, oid, out, description in public_operations:
        paths[path] = {'get': {'operationId': oid, 'description': description,
                               'x-required-permission': 'PUBLIC', 'security': [], 'responses': {'200': response(out)}}}
    presentation = {oid: (group, summary) for group, titles in OPERATION_GROUPS.items()
                    for oid, summary in titles.items()}
    for methods in paths.values():
        for method, operation in methods.items():
            if method not in METHODS:
                continue
            oid = operation['operationId']
            group, summary = presentation[oid]
            display = {'operationId': oid, 'summary': summary, 'tags': [group], 'x-apifox-folder': group}
            methods[method] = {**display, **{key: value for key, value in operation.items() if key not in display}}
    components={'schemas':schemas,'parameters':copy.deepcopy(teacher['components']['parameters']),'responses':copy.deepcopy(teacher['components']['responses']),'securitySchemes':{'bearerAuth':{'type':'http','scheme':'bearer','bearerFormat':'JWT'}}}
    for status,name in [('400','BadRequest'),('401','Unauthorized'),('403','Forbidden'),('404','NotFound'),('409','Conflict'),('503','Unavailable')]:components['responses'][name]=response(ref('Error'),status+' 业务错误')
    # Remove unused teacher parameters/responses to avoid unrelated business schemas.
    used=set()
    def refs(x):
        if isinstance(x,dict):
            if '$ref' in x:used.add(x['$ref'])
            for v in x.values():refs(v)
        elif isinstance(x,list):
            for v in x:refs(v)
    refs(paths);refs(schemas)
    components['parameters']={n:v for n,v in components['parameters'].items() if '#/components/parameters/'+n in used}
    components['responses']={n:v for n,v in components['responses'].items() if '#/components/responses/'+n in used}
    components['parameters']['Page']['schema']={'type':'integer','minimum':1,'default':1}
    components['parameters']['PageSize']['schema']={'type':'integer','minimum':1,'maximum':100,'default':20}
    document={'openapi':'3.0.3','info':{'title':'RBAC1 统一授权中心','version':'2.0.0','description':'结构化与 DDD 后端的共享契约。业务管理接口返回 code=OK 包装，外部身份、审计与服务投影采用裸响应。错误响应包含稳定的字符串 code。'},'servers':[{'url':'http://localhost:8081','description':'结构化后端默认本地地址'},{'url':'http://localhost:8082','description':'DDD 后端默认本地地址'}],'security':[{'bearerAuth':[]}],'tags':[{'name': group} for group in OPERATION_GROUPS],'paths':paths,'components':components}
    def clean(x):
        if isinstance(x,dict):
            if x.get('required') == []: del x['required']
            for v in x.values(): clean(v)
        elif isinstance(x,list):
            for v in x: clean(v)
    clean(document)
    emit('openapi/rbac-api.yaml',yaml.safe_dump(document,allow_unicode=True,sort_keys=False))
    return document

def course_catalog():
    raw=(CAT/'course-openapi.source.yaml').read_bytes(); doc=yaml.safe_load(raw)
    assert hashlib.sha256(raw).hexdigest()==SOURCE_SHA256, 'Pinned teacher source snapshot changed'
    rows=[]; excluded=[]
    for path,value in doc['paths'].items():
        for method,op in value.items():
            if method not in METHODS:continue
            item={'operationId':op['operationId'],'method':method.upper(),'path':path,'x-required-permission':op.get('x-required-permission'),'x-required-role':op.get('x-required-role')}
            if op.get('x-provider')=='unified-identity' or path.startswith('/auth/'):excluded.append(item);continue
            public = (method, path, op['operationId'], op.get('security')) == ('post', '/login', 'courseLogin', [])
            item.update(permissionCode=None if public else op.get('x-required-permission') or 'course:access',
                        requiredRole=op.get('x-required-role'), accessPolicy='PUBLIC' if public else 'PROTECTED')
            rows.append(item)
    assert len(excluded)==10 and len(rows)==80
    assert [r['operationId'] for r in rows if r['accessPolicy']=='PUBLIC'] == ['courseLogin']
    assert next(r for r in rows if r['operationId']=='copyCourse')['requiredRole']=='teacher'
    manifest={'source':SOURCE,'commit':COMMIT,'sha256':hashlib.sha256(raw).hexdigest(),'snapshot':'course-openapi.source.yaml','excludedProviderOperations':excluded,'operations':rows}
    emit('catalog/course-operations.json',json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    routes = ROOT / 'course-integration/overlay/backend/src/main/resources/integration-routes.tsv'
    OUTPUTS[routes] = ''.join(row['method'] + ' ' + re.sub(r'\{[^}]+}', '{}', row['path'])
                              + '\t' + row['operationId'] + '\n' for row in rows)
    return rows, manifest

# Course UI grouping is an explicit, reviewable operation-id mapping, not authorization inference.
GROUPS={
'overview':['getCurrentUser','listStudentCourses','listTeacherCourses'],
'courses':['listAdminCourses','getCourse','createCourse','copyCourse','submitCourseReview','resetCourseToApplication','reviewCourse','updateCourse','deleteCourse','updateCourseDescription','setCourseLeaders'],
'classes':['listClasses','createClasses','updateClass','deleteClass','setClassTeachers'],
'roster':['listClassStudents','addStudent','searchStudentCandidates','removeStudent','importRoster'],
'groups':['getCourseGroupRule','updateCourseGroupRule','getClassGroupRule','setClassGroupRule','deleteClassGroupRule','listCourseGroups','createGroupAsStudent','listClassGroups','createGroupAsTeacher','getGroup','updateGroup','dissolveGroup','listGroupJoinRequests','createGroupJoinRequest','decideGroupJoinRequest','cancelGroupJoinRequest','removeGroupMemberAsLeader','transferGroupLeader','leaveGroup'],
'milestones':['listMilestones','createMilestone','updateMilestone','deleteMilestone'],
'submissions':['listSubmissions','getGroupSubmission','submitMaterials','getMyMilestoneSubmission','submitMyMaterials','getSubmissionById','reviewSubmission'],
'oral':['listOralSlots','createOralSlot','deleteOralSlot','bookOralSlot','cancelBooking','listOralStatus','createGroupOralRecord','createStudentOralRecord','updateOralRecord','summarizeOralMemo'],
'grades':['getClassGrades','exportClassGrades','getMyGrades'],
'files':['downloadFile'], 'audit':['listAuditLogs'],
'administration':['listTerms','createTerm','updateTerm','deleteTerm','lookupIdentityUser','listTeachers','createTeacher','updateTeacher','deleteTeacher','replaceCurrentTerm','clearCurrentTerm']}

def seed(document, course, manifest):
    text='-- Generated RBAC1 empty-development seed. root is unusable until controlled bootstrap.\nSET time_zone = \'+00:00\';\n'
    def add(table,**v):
        nonlocal text;text+=insert(table,**v)
    for i,code,name in [(1,'rbac-center','统一授权中心'),(2,'course-project-management','课程项目管理')]:add('sys_system',id=i,code=code,name=name,status='ENABLED',version=0)
    for i,parent,code,name,path in [(1,None,'HQ','总部','/1/'),(2,1,'Finance','Finance','/1/2/'),(3,1,'Engineering','Engineering','/1/3/')]:add('sys_department',id=i,parent_id=parent,code=code,name=name,path=path,status='ENABLED',version=0)
    add('sys_user',id=1,subject='rbac-u1',username='root',password_hash='UNINITIALIZED',name='系统管理员',department_id=1,status='ACTIVE',token_version=0,must_change_password=True,failed_login_count=0,version=0)
    roles={}
    for rid,system,code,identity in [(1,1,'SUPER_ADMIN',None),(2,1,'RBAC_ADMIN',None),(3,1,'AUDITOR',None),(4,2,'teacher','teacher'),(5,2,'student','student'),(6,2,'admin','admin')]:
        roles[code]=rid;add('sys_role',id=rid,system_id=system,code=code,name=code,status='ENABLED',builtin=True,identity_role=identity,version=0)
    for department in [1,2,3]:
        for code,rid in roles.items():
            if code=='SUPER_ADMIN' and department!=1:continue
            add('sys_department_allowed_role',department_id=department,role_id=rid)
    add('sys_user_role',user_id=1,role_id=1,assigned_by=None)
    add('sys_grant_version',id=1,version=0)
    permissions={};grants=set();mapping=set();uis={};apis={};coverage=[]
    def permission(system,kind,code):
        key=(system,kind,code)
        if key not in permissions:
            permissions[key]=len(permissions)+1
            add('sys_permission',id=permissions[key],system_id=system,code=code,name=code,kind=kind,status='ENABLED',version=0)
        return permissions[key]
    def grant(role,pid):grants.add((roles[role],pid))
    def ui(system,code,route,component,parent=None,kind='MENU'):
        uid=len(uis)+1;pid=permission(system,'UI',code);uis[(system,code)]=(uid,pid)
        add('sys_ui_resource',id=uid,system_id=system,parent_id=parent,type=kind,code=code,name=code,route=route,component=component,sort_order=uid,status='ENABLED',permission_id=pid,version=0)
        return uid,pid
    center=[]
    for path,methods in document['paths'].items():
        for method,op in methods.items():
            if method not in METHODS:continue
            code=op['x-required-permission'];policy='PUBLIC' if code=='PUBLIC' else 'AUTHENTICATED' if code=='@authenticated' else 'PROTECTED'
            # Keep applied V2 stable while coverage reflects the capabilities after migrations.
            seed_code={'setRoleStatus':'rbac:role:edit', 'deleteDepartment':'rbac:department:edit'}.get(op['operationId'], code)
            pid=None if policy!='PROTECTED' else permission(1,'API',seed_code)
            aid=len(apis)+1;apis[(1,op['operationId'])]=aid
            add('sys_api_operation',id=aid,system_id=1,operation_id=op['operationId'],method=method.upper(),path_template=path,permission_id=pid,required_role=None,access_policy=policy,enabled=True,version=0)
            center.append((aid,op['operationId'],code,method,path))
            if pid:
                grant('SUPER_ADMIN',pid)
                if code!='rbac:system:edit':grant('RBAC_ADMIN',pid)
                if code.endswith(':view') or code=='audit:read':grant('AUDITOR',pid)
    pages=[('dashboard','Dashboard', ['getDashboard']),('systems','Systems',['listSystems','createSystem','updateSystem','setSystemStatus']),('departments','Departments',['getDepartmentTree','createDepartment','updateDepartment','deleteDepartment','getDepartmentAssignableRoles','replaceDepartmentAssignableRoles']),('users','Users',['listUsers','createUser','getUser','updateUser','setUserStatus','deleteUser','changeUserDepartment','listUserRoleAssignments','createUserRoleAssignment','deleteUserRoleAssignment','getUserEffectivePermissions']),('roles','Roles',['listRoles','createRole','getRole','updateRole','setRoleStatus','deleteRole','getRolePermissions','replaceRolePermissions','getRoleEffectivePermissions']),('hierarchy','Hierarchy',['getRoleHierarchy','addRoleInheritance','deleteRoleInheritance']),('permissions','Permissions',['listPermissions','createPermission','setPermissionStatus']),('ui-resources','UiResources',['getUiResources','createUiResource','updateUiResource','deleteUiResource','getMyMenus']),('api-operations','ApiOperations',['listApiOperations','createApiOperation','updateApiOperation']),('mappings','Mappings',['getUiApiMappings','replaceUiApiMappings']),('authorization','Authorization',['checkAuthorization','explainAuthorization']),('audit','Audit',['queryAuditLogs'])]
    assigned=set()
    for slug,component,ids in pages:
        parent,pid=ui(1,'rbac:ui:'+slug+':view','/'+slug,component)
        for role in ['SUPER_ADMIN','RBAC_ADMIN']:
            grant(role,pid)
        if slug not in ['authorization']:grant('AUDITOR',pid)
        for aid,oid,code,method,path in center:
            if oid not in ids:continue
            assigned.add(oid)
            target=parent
            if method!='get':
                target,pid=ui(1,'rbac:ui:'+oid,None,None,parent,'BUTTON');grant('SUPER_ADMIN',pid)
                if code!='rbac:system:edit':grant('RBAC_ADMIN',pid)
            mapping.add((target,aid));coverage.append([1,oid,method.upper(),path,code,'', '中心管理',target,aid])
    # Authenticated self-service controls are deliberately separate from the 12 management pages.
    for aid,oid,code,method,path in center:
        if oid in {'getIntegrationAuthorities','lookupUnifiedIdentityUser'}:
            coverage.append([1,oid,method.upper(),path,code,'','可信服务专用（不向浏览器开放）','',aid])
        elif oid not in assigned and code!='PUBLIC':
            target,pid=ui(1,'rbac:ui:'+oid,None,None,None,'BUTTON')
            for role in ['SUPER_ADMIN','RBAC_ADMIN','AUDITOR']:grant(role,pid)
            mapping.add((target,aid));coverage.append([1,oid,method.upper(),path,code,'','身份/本人',target,aid])
    assert set(sum(GROUPS.values(),[]))=={r['operationId'] for r in course}
    for row in course:
        pid=permission(2,'API',row['permissionCode']);aid=len(apis)+1;apis[(2,row['operationId'])]=aid
        add('sys_api_operation',id=aid,system_id=2,operation_id=row['operationId'],method=row['method'],path_template=row['path'],permission_id=pid,required_role=row['requiredRole'],access_policy='PROTECTED',enabled=True,version=0)
    # All API grants are enumerated below, never inferred at runtime from role names/UI mappings.
    for code in sorted({r['permissionCode'] for r in course}|{'course:access','audit:read','audit:write'}):
        pid=permission(2,'API',code);grant('admin',pid)
        if code in ['course:access','audit:read','audit:write','CRS']:grant('teacher',pid)
        if code in ['course:access','audit:write']:grant('student',pid)
    # Audit identity provider endpoints are center-hosted; course business snapshot excludes them.
    student_groups={'overview','courses','groups','milestones','submissions','oral','grades','files'}
    teacher_groups=student_groups|{'classes','roster','audit'}
    byid={r['operationId']:r for r in course}
    student_ops={'getCurrentUser','listStudentCourses','getCourse','listCourseGroups','createGroupAsStudent','getGroup','listGroupJoinRequests','createGroupJoinRequest','decideGroupJoinRequest','cancelGroupJoinRequest','removeGroupMemberAsLeader','transferGroupLeader','leaveGroup','listMilestones','getGroupSubmission','submitMaterials','getMyMilestoneSubmission','submitMyMaterials','getSubmissionById','listOralSlots','bookOralSlot','cancelBooking','getMyGrades','downloadFile'}
    teacher_excluded={'listStudentCourses','listAdminCourses','createGroupAsStudent','createGroupJoinRequest','getGroupSubmission','submitMaterials','getMyMilestoneSubmission','submitMyMaterials','bookOralSlot','cancelBooking','getMyGrades'}
    for role,groups in [('admin',set(GROUPS)),('teacher',teacher_groups),('student',student_groups)]:
        for group in GROUPS:
            if group not in groups:continue
            uid,pid=ui(2,'course:ui:'+role+':'+group,'/'+role+'/'+group,'Course'+group.title());grant(role,pid)
            for oid in GROUPS[group]:
                if role=='student' and oid not in student_ops: continue
                if role=='teacher' and (oid in teacher_excluded or (byid[oid]['requiredRole']=='admin' and oid!='resetCourseToApplication')): continue
                row=byid[oid];target=uid
                if row['method']!='GET':
                    target,pid=ui(2,'course:ui:'+role+':'+oid,None,None,uid,'BUTTON');grant(role,pid)
                aid=apis[(2,oid)];mapping.add((target,aid));coverage.append([2,oid,row['method'],row['path'],row['permissionCode'],row['requiredRole'] or '',group,target,aid])
    for rid,pid in sorted(grants):add('sys_role_permission',role_id=rid,permission_id=pid)
    for uid,aid in sorted(mapping):add('sys_ui_api_mapping',ui_resource_id=uid,api_operation_id=aid)
    text+='ALTER TABLE sys_user AUTO_INCREMENT=10000;\n'
    emit('db/migration/V2__rbac1_seed.sql',text)
    status_row = [1, 'setDepartmentStatus', 'PUT', DEPARTMENT_STATUS_PATH, 'rbac:department:status', '', '中心管理', '', '']
    coverage.insert(next(i for i, row in enumerate(coverage) if row[1] == 'deleteDepartment'), status_row)
    buf=io.StringIO(newline='');writer=csv.writer(buf,lineterminator='\n');writer.writerow(['systemId','operationId','method','path','permissionCode','requiredRole','category','uiResourceId','apiOperationId']);writer.writerows(row for row in coverage if row[1] != 'lookupIdentityUser')
    writer.writerows([1, oid, 'GET', path, 'TEC', 'admin', '统一身份鉴权', '', '']
                     for path, (oid, _) in DIRECTORY.items())
    writer.writerow([2, 'courseLogin', 'POST', '/login', 'PUBLIC', '', '课程登录', '', ''])
    emit('catalog/ui-api-coverage.csv',buf.getvalue())
    sample_ids=['listTerms','createTerm','copyCourse','importRoster','createGroupJoinRequest','submitMaterials','createMilestone','bookOralSlot','getMyGrades','downloadFile']
    samples=[dict(byid[oid],category=next(g for g,ids in GROUPS.items() if oid in ids)) for oid in sample_ids]
    emit('catalog/coverage-samples.json',json.dumps({'source':SOURCE,'commit':COMMIT,'sha256':manifest['sha256'],'samples':samples},ensure_ascii=False,indent=2)+'\n')
    return {'tables':15,'centerOperations':len(center),'courseOperations':len(course),'centerPages':len(pages),'permissions':len(permissions)+1,'uiResources':len(uis),'mappings':len(mapping),'rolePermissionGrants':len(grants)+2}

def split_role_status_permission(document):
    code=q(document['paths']['/api/v1/roles/{id}/status']['put']['x-required-permission'])
    emit('db/migration/V3__role_status_permission.sql',f"""-- Separate role status from the role edit capability without changing V1/V2.
START TRANSACTION;

UPDATE sys_grant_version SET version=version+1 WHERE id=1;

INSERT INTO sys_permission (system_id, code, name, kind, status)
SELECT id, {code}, '启停角色', 'API', 'ENABLED'
FROM sys_system WHERE code='rbac-center';

UPDATE sys_api_operation a
JOIN sys_system s ON s.id=a.system_id AND s.code='rbac-center'
JOIN sys_permission p ON p.system_id=s.id AND p.kind='API' AND p.code={code}
SET a.permission_id=p.id, a.version=a.version+1
WHERE a.operation_id='setRoleStatus';

-- Keep the existing built-in administrators authorized when they had the old capability.
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, new_permission.id
FROM sys_role r
JOIN sys_system s ON s.id=r.system_id AND s.code='rbac-center'
JOIN sys_role_permission rp ON rp.role_id=r.id
JOIN sys_permission old_permission ON old_permission.id=rp.permission_id
    AND old_permission.system_id=s.id AND old_permission.kind='API'
    AND old_permission.code='rbac:role:edit' AND old_permission.status='ENABLED'
JOIN sys_permission new_permission ON new_permission.system_id=s.id
    AND new_permission.kind='API' AND new_permission.code={code}
WHERE r.builtin=TRUE AND r.code IN ('SUPER_ADMIN','RBAC_ADMIN');

COMMIT;
""")

def update_course_identity():
    sql = '''-- Register the new identity provider queries and public course login.
START TRANSACTION;

DELETE m FROM sys_ui_api_mapping m
JOIN sys_api_operation a ON a.id=m.api_operation_id
JOIN sys_system s ON s.id=a.system_id AND s.code='course-project-management'
WHERE a.operation_id='lookupIdentityUser' AND a.path_template='/identity/users/{userId}';

DELETE a FROM sys_api_operation a
JOIN sys_system s ON s.id=a.system_id AND s.code='course-project-management'
WHERE a.operation_id='lookupIdentityUser' AND a.path_template='/identity/users/{userId}';

'''
    for path, (oid, _) in DIRECTORY.items():
        sql += f'''INSERT INTO sys_api_operation (system_id, operation_id, method, path_template, required_role, access_policy)
SELECT id, {q(oid)}, 'GET', {q(path)}, 'admin', 'AUTHENTICATED' FROM sys_system WHERE code='rbac-center';

'''
    sql += '''INSERT INTO sys_api_operation (system_id, operation_id, method, path_template, access_policy)
SELECT id, 'courseLogin', 'POST', '/login', 'PUBLIC' FROM sys_system WHERE code='course-project-management';

UPDATE sys_grant_version SET version=version+1 WHERE id=1;

COMMIT;
'''
    emit('db/migration/V4__course_identity_directory.sql', sql)

def validate(document):
    ids=set();count=0
    def walk(x):
        if isinstance(x,dict):
            if '$ref' in x:
                r=x['$ref'];assert r.startswith('#/'),r
                node=document
                for key in r[2:].split('/'):node=node[key.replace('~1','/').replace('~0','~')]
            for value in x.values():walk(value)
        elif isinstance(x,list):
            for v in x:walk(v)
    walk(document)
    for path,methods in document['paths'].items():
        for method,op in methods.items():
            if method not in METHODS:continue
            assert op['operationId'] not in ids,op['operationId'];ids.add(op['operationId']);count+=1
            assert 'x-required-permission' in op
            assert path.startswith('/api/v1/') or path.startswith('/auth/') or path in {'/.well-known/jwks.json','/integration/authorities','/identity/users/{userId}'}
            params=methods.get('parameters',[])+op.get('parameters',[])
            assert set(re.findall(r'\{([^}]+)\}',path))=={p['name'] for p in params if p.get('in')=='path'}
    assert document['components']['schemas']['RoleVO']['properties']['identityRole']['enum']==['student','teacher','admin']
    return count

def split_department_permissions(document):
    status = q(document['paths'][DEPARTMENT_STATUS_PATH]['put']['x-required-permission'])
    delete = q(document['paths']['/api/v1/departments/{id}']['delete']['x-required-permission'])
    emit('db/migration/V5__department_lifecycle_permissions.sql', f"""-- Register department lifecycle capabilities without changing applied migrations.
START TRANSACTION;

INSERT INTO sys_permission (system_id, code, name, kind, status)
SELECT id, {status}, '启停部门', 'API', 'ENABLED' FROM sys_system WHERE code='rbac-center'
UNION ALL
SELECT id, {delete}, '删除部门', 'API', 'ENABLED' FROM sys_system WHERE code='rbac-center'
UNION ALL
SELECT id, 'rbac:ui:setDepartmentStatus', '启停部门', 'UI', 'ENABLED' FROM sys_system WHERE code='rbac-center';

UPDATE sys_api_operation a
JOIN sys_system s ON s.id=a.system_id AND s.code='rbac-center'
JOIN sys_permission p ON p.system_id=s.id AND p.kind='API' AND p.code={delete}
SET a.permission_id=p.id, a.version=a.version+1
WHERE a.operation_id='deleteDepartment';

INSERT INTO sys_api_operation (system_id, operation_id, method, path_template, permission_id, access_policy)
SELECT s.id, 'setDepartmentStatus', 'PUT', {q(DEPARTMENT_STATUS_PATH)}, p.id, 'PROTECTED'
FROM sys_system s JOIN sys_permission p ON p.system_id=s.id AND p.kind='API' AND p.code={status}
WHERE s.code='rbac-center';

INSERT INTO sys_ui_resource (system_id, parent_id, type, code, name, sort_order, status, permission_id)
SELECT s.id, menu.id, 'BUTTON', p.code, '启停部门', menu.sort_order, 'ENABLED', p.id
FROM sys_system s
JOIN sys_ui_resource menu ON menu.system_id=s.id AND menu.code='rbac:ui:departments:view'
JOIN sys_permission p ON p.system_id=s.id AND p.kind='UI' AND p.code='rbac:ui:setDepartmentStatus'
WHERE s.code='rbac-center';

INSERT INTO sys_ui_api_mapping (ui_resource_id, api_operation_id)
SELECT ui.id, a.id FROM sys_system s
JOIN sys_ui_resource ui ON ui.system_id=s.id AND ui.code='rbac:ui:setDepartmentStatus'
JOIN sys_api_operation a ON a.system_id=s.id AND a.operation_id='setDepartmentStatus'
WHERE s.code='rbac-center';

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_system s
JOIN sys_role r ON r.system_id=s.id AND r.builtin=TRUE AND r.code IN ('SUPER_ADMIN','RBAC_ADMIN')
JOIN sys_permission p ON p.system_id=s.id AND (
    (p.kind='API' AND p.code IN ({status},{delete}))
    OR (p.kind='UI' AND p.code='rbac:ui:setDepartmentStatus'))
WHERE s.code='rbac-center';

UPDATE sys_grant_version SET version=version+1 WHERE id=1;

COMMIT;
""")

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    ddl()
    document = oas()
    course, manifest = course_catalog()
    # V2 is an applied migration. Reconstruct the historical inputs while generating current coverage.
    legacy_document = dict(document, paths={p: methods for p, methods in document['paths'].items()
                                           if p not in DIRECTORY and p != DEPARTMENT_STATUS_PATH})
    legacy_course = [r for r in course if r['operationId'] != 'courseLogin']
    teacher_index = next(i for i, row in enumerate(legacy_course) if row['operationId'] == 'listTeachers')
    legacy_course.insert(teacher_index, {'operationId': 'lookupIdentityUser', 'method': 'GET',
                                         'path': '/identity/users/{userId}',
                                         'permissionCode': 'TEC', 'requiredRole': 'admin'})
    counts = seed(legacy_document, legacy_course, manifest)
    counts['centerOperations'] = counts['validatedOasOperations'] = validate(document)
    # V4 removes the retired course mapping; V5 adds the department status mapping.
    counts['permissions'] += 3
    counts['uiResources'] += 1
    counts['rolePermissionGrants'] += 6
    split_role_status_permission(document)
    update_course_identity()
    split_department_permissions(document)
    old=list((RES/'db/migration').glob('V*.sql'))
    expected={p for p in OUTPUTS if p.suffix=='.sql'}
    if args.check:
        bad=[str(p.relative_to(ROOT)) for p,text in OUTPUTS.items() if not p.exists() or p.read_text(encoding='utf8')!=text]
        bad += [str(p.relative_to(ROOT)) for p in old if p not in expected]
        if bad:raise SystemExit('Generated artifacts differ: '+', '.join(bad))
    else:
        for p,text in OUTPUTS.items():p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text,encoding='utf8',newline='\n')
        for p in old:
            if p not in expected:p.unlink()
    print(json.dumps({'mode':'check' if args.check else 'generate','sourceSha256':manifest['sha256'],**counts},ensure_ascii=False))
if __name__=='__main__':main()
