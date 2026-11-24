"""Maintained source-flow projections; loaded in renderer globals, not a business dependency.
Ordered callsite checks are in the renderer; notes identify omitted internals and transaction callbacks.
"""
def end_use_case(q):
    q.close('svc','ctl'); q.finish()

def local_completion(q, d, audit_args, repository='repo'):
    if d:
        q.call('svc','integrity','requireAdministrator')
        q.call('svc',repository,'advanceVersion')
    else:
        q.raw('note over svc: finish: Store.flush → preserveAdministrator → g.version++')
    q.call('svc','audit','local' if d else 'success',audit_args)

def affected_grants(q):
    q.open('svc','svc','requireAffectedGrants','actor, graph, changedRole, permissions')
    q.call('svc','graph','affectedBy','changedRole.id()')
    q.raw('loop changed role and every affected senior')
    q.call('svc','auth','requireGraphGrant','actor, graph, affectedId')
    q.call('svc','auth','upperBound','actor, systemCode, proposedClosure')
    q.raw('end\nnote over auth: All checks precede saveRole/saveEdges; actor facts still precede mutation.\nSpecial-capability traversal includes disabled nodes.')
    q.close('svc','svc')

def build_sequences():
    for mode in ['ddd','structured']:
        d=mode=='ddd'
        # S01: exact graph policy and persistence order; exceptions abort interaction.
        p={'ctl':'RoleController' if d else 'ManagementController',
           'svc':'RoleApplicationService' if d else 'ManagementService',
           'auth':'AuthorizationApplicationService' if d else 'AuthorizationService',
           'repo':'AuthorizationRepository' if d else 'Store',
           'graph':'RoleHierarchy' if d else 'AuthorizationService',
           'audit':'AuditApplicationService' if d else 'AuditService'}
        if d:p['integrity']='GrantIntegrityService'
        else:p.pop('graph',None)
        q=Sequence('S01-'+mode+'-sequence',mode,p,'ctl','link' if d else handler(mode,'inherit'),
                   'bearer, seniorId, juniorId' if d else 'actor, seniorId, juniorId')
        q.open('ctl','svc','link' if d else 'inherit','principal, senior, junior, true' if d else 'actor, senior, junior, true')
        q.raw('group local write transaction: lock before grant reads')
        q.call('svc','repo' if d else 'auth','lockGrants' if d else 'lock')
        q.call('svc','auth','require' if d else 'center','principal, rbac:role:edit, true' if d else 'actor, rbac:role:edit')
        if d:
            q.call('svc','repo','role','senior');q.call('svc','repo','role','junior')
            q.call('svc','auth','requireRoleManagement','actor, role');q.call('svc','auth','requireRoleManagement','actor, other')
            q.raw('note over auth: Role.requireManageable checks SUPER_ADMIN capability.\nCross-system rejected before graph mutation.')
            q.call('svc','repo','hierarchy','role.systemCode()')
            q.raw('alt self / cycle / duplicate')
            q.open('svc','graph','link','senior, junior');q.raw('graph --> obj: DomainException; abort / rollback');q.close('graph','svc')
            q.raw('else valid edge');q.call('svc','graph','link','senior, junior')
            affected_grants(q)
            q.raw('note over auth: requireGrantable traverses even disabled nodes:\nno pre-planted path to SUPER_ADMIN; permission-set inclusion alone is insufficient.')
            q.call('svc','repo','saveEdges','systemCode, graph.edges()');q.raw('end')
        else:
            q.call('svc','auth','root','actor');q.call('svc','auth','captureUpper','actor')
            q.call('svc','auth','protectRole','actor, seniorRole');q.call('svc','auth','protectRole','actor, juniorRole')
            q.call('svc','repo','one','RoleHierarchy.class, predicate, senior, junior')
            q.raw('note over svc: reachable(junior, senior): DFS queries outgoing edges.\nSelf / cross-system / duplicate / cycle throws BizException and aborts interaction.')
            q.raw('alt legal new edge');q.call('svc','repo','save','edge');q.call('svc','svc','changedUpper','actor, seniorRole')
            q.raw('note over auth: upper compares captured pre-mutation actor snapshot.\nSpecial-capability traversal includes disabled nodes;\na latent SUPER_ADMIN path is rejected and rolled back.\nelse invalid graph\nsvc --> obj: BizException; rollback\nend')
        local_completion(q,d,'actor, system, type, target, SUCCESS, null' if d else 'actor, operation, target')
        q.raw('end\nnote over svc: Transaction proxy commits only normal return.\nAny exception aborts: no edge/version/SUCCESS fact persists.')
        end_use_case(q)
        # S02: user rules precede DirectoryRepository.assign; eligibility owns role admission.
        p={'ctl':'DirectoryController' if d else 'ManagementController',
           'svc':'DirectoryApplicationService' if d else 'ManagementService',
           'auth':'AuthorizationApplicationService' if d else 'AuthorizationService',
           'repo':'AuthorizationRepository' if d else 'Store',
           'user':'User' if d else 'Store','audit':'AuditApplicationService' if d else 'AuditService'}
        if d:p.update({'directory':'DirectoryRepository','organization':'OrganizationPolicy','eligibility':'DepartmentEligibility','integrity':'GrantIntegrityService'})
        else:p.pop('user',None)
        q=Sequence('S02-'+mode+'-sequence',mode,p,'ctl','assign' if d else handler(mode,'assign'),'bearer, userId, input' if d else 'actor, userId, input')
        q.open('ctl','svc','assign','principal, userId, roleId' if d else 'actor, userId, roleId')
        q.raw('group local write transaction')
        q.call('svc','repo' if d else 'auth','lockGrants' if d else 'lock')
        q.call('svc','auth','require' if d else 'center','principal, rbac:assignment:edit, false' if d else 'actor, rbac:assignment:edit')
        if d:
            q.call('svc','directory','user','userId, true');q.raw('note over svc: managed(actor, department) checks OrganizationPolicy scope.')
            q.raw('alt department is disabled');q.open('svc','organization','requireAccepting','department')
            q.raw('organization --> obj: DEPARTMENT_DISABLED; abort');q.close('organization','svc')
            q.raw('else department accepts assignments');q.call('svc','organization','requireAccepting','department')
            q.raw('end');q.call('svc','repo','role','roleId');q.call('svc','repo','systemEnabled','systemCode')
            q.raw('note over svc: Disabled target system throws ROLE_DISABLED.')
            q.open('svc','svc','requireRoleWithin','actor, role');q.call('svc','auth','upperBound','actor, systemCode, proposed')
            q.close('svc','svc')
            q.call('svc','repo','eligibility','departmentId')
            q.raw('alt user inactive / department mismatch')
            q.open('svc','user','assign','role, eligibility');q.raw('user --> obj: DomainException; abort / rollback');q.close('user','svc')
            q.raw('else user admits assignment');q.open('svc','user','assign','role, eligibility')
            q.call('user','eligibility','requireEligible','role');q.close('user','svc')
            q.raw('note over eligibility: Missing eligibility or disabled role throws here.\nUser then rejects duplicate direct assignment; all exceptions\nabort before DirectoryRepository.assign.')
            q.call('svc','directory','assign','userId, roleId, actor.id()');q.raw('end')
        else:
            q.call('svc','auth','captureUpper','actor');q.call('svc','auth','scope','actor, user.departmentId')
            q.raw('alt department is disabled')
            q.open('svc','svc','requireAccepting','department')
            q.raw('svc --> obj: DEPARTMENT_DISABLED; abort');q.close('svc','svc')
            q.raw('else department accepts assignments');q.call('svc','svc','requireAccepting','department')
            q.raw('end\nnote over svc: ACTIVE user and enabled role/system required.')
            q.call('svc','repo','one','DepartmentAllowedRole.class, predicate, departmentId, roleId')
            q.raw('alt admitted');q.call('svc','auth','upper','actor, systemCode, proposed');q.call('svc','repo','save','ur')
            q.raw('else disabled / forbidden\nsvc --> obj: BizException; abort / rollback\nend')
        local_completion(q,d,'actor, rbac-center, ROLE_ASSIGNED, subject, SUCCESS, null' if d else 'actor, ROLE_ASSIGNED, userId')
        q.raw('end\nnote over svc: Assignment, administrator-integrity check, version and SUCCESS audit\nshare the transaction; exceptions cannot commit a partial success.')
        end_use_case(q)
        # S03: distinct cache algorithms, not a generic duplicated model.
        if d:
            p={'ctl':'DirectoryController','rest':'RestSupport','svc':'DirectoryApplicationService',
               'auth':'AuthorizationApplicationService','model':'AuthorizationPolicy','graph':'RoleHierarchy',
               'reads':'AuthorizationReadTransaction','repo':'AuthorizationRepository','cache':'EffectiveCache'}
            q=Sequence('S03-ddd-sequence',mode,p,'ctl','effective','bearer, id, system',reply_arrow='-->>')
            q.lines.insert(1,'!pragma teoz true')
            q.lines[q.lines.index('skinparam maxMessageSize 100')]='skinparam maxMessageSize 160'
            q.lines[q.lines.index('participant "rest: RestSupport" as rest')]='participant "«MetaClass»\\n: RestSupport" as rest'
            q.open('ctl','svc','effective','principal, id, system')
            q.call('svc','svc','actor','principal, rbac:user:view, false')
            q.open('svc','repo','systemEnabled','system')
            q.raw('break unknown system')
            q.reply('repo','obj','DomainException 404 SYSTEM_NOT_FOUND')
            q.raw('end')
            q.close('repo','svc','system status')
            q.call('svc','svc','managedUser','actor, id')
            q.raw('note over svc: Resolve the system before loading the target user and checking department scope.')
            q.open('svc','auth','effective','userId, system')
            q.raw('alt no outer read-only transaction (S03 HTTP takes this path)')
            q.open('auth','auth','stableEffective','userId, system')
            q.raw('loop until the version is stable or three attempts are used')
            q.call('auth','repo','version',reply='initial version')
            q.call('auth','repo','systemEnabled','system','system status')
            q.raw('break target system disabled')
            q.reply('auth','svc','DomainException 401 SYSTEM_DISABLED')
            q.raw('note over svc: Catch this target-side error and use empty Effective.')
            q.raw('end')
            q.call('auth','repo','identity','userId','target identity')
            q.raw('break target user missing')
            q.reply('auth','obj','DomainException 404 USER_NOT_FOUND')
            q.raw('end')
            q.raw('alt target user inactive')
            q.raw('note over auth: Use empty Effective.')
            q.raw('else target user active\nopt no active transaction')
            q.call('auth','cache','get','system, userId, version','cached Effective or null')
            q.raw('end\nopt no usable cached result')
            q.call('auth','repo','assignments','userId, system','direct role IDs')
            q.call('auth','repo','hierarchy','system','role graph')
            q.call('auth','repo','permissions','system','permission catalog')
            q.open('auth','model','calculate','system, directIds, graph, permissions')
            q.open('model','graph','paths','safeDirect')
            q.raw('loop each direct role')
            q.call('graph','graph','visit','id, new ArrayList<>(), result')
            q.raw('note right of graph: Skip disabled roles and preserve distinct paths.')
            q.raw('end')
            q.close('graph','model','role paths')
            q.close('model','auth','calculated Effective')
            q.raw('end\nend')
            q.call('auth','repo','version',reply='version before cache write')
            q.raw('opt version unchanged since the initial read\nopt target user active and no active transaction')
            q.call('auth','cache','put','system, userId, version, result')
            q.raw('note right of cache: Cache the candidate for this version.')
            q.raw('end')
            q.call('auth','repo','version',reply='final version')
            q.raw('end')
            q.raw('note over auth,repo: A version change before or after writing starts the next attempt.\nAn unchanged final version ends the loop with the candidate result.')
            q.raw('end')
            q.raw('break three attempts exhausted')
            q.reply('auth','obj','DomainException 503 AUTHORIZATION_UNSTABLE')
            q.raw('end')
            q.close('auth','auth','stable Effective')
            q.raw('else read-only outer transaction (other callers)')
            q.open('auth','reads','fresh','query callback')
            q.open('reads','auth','stableEffective','userId, system')
            q.raw('note over auth,reads: Same retry algorithm in REQUIRES_NEW / READ_COMMITTED / readOnly.\nClear the JPA context at the start of each attempt and skip all cache access.')
            q.close('auth','reads','stable Effective')
            q.close('reads','auth','stable Effective')
            q.raw('end')
            q.raw('opt stable Effective returned')
            q.reply('auth','svc','stable Effective')
            q.raw('end')
            q.close('auth','svc')
            q.raw('opt stable result or caught SYSTEM_DISABLED')
            q.close('svc','ctl','Effective (empty for disabled system)')
            q.call('ctl','rest','effective','effective','effective permissions')
            q.call('ctl','rest','ok','effective permissions','response body')
            q.reply('ctl','obj','HTTP 200 with effective permissions')
            q.raw('end')
            q.finish()
        else:
            structured_effective_sequence()
        # S04: the same in-process course filter uses the active backend's authorization callback.
        p={'filter':'CourseProxyFilter','route':'RouteRegistry','tokens':'TokenProvider' if d else 'TokenService',
           'auth':'AuthorizationApplicationService' if d else 'AuthorizationService','body':'BoundedBody','proxy':'ProxyClient'}
        q=Sequence('S04-'+mode+'-sequence',mode,p,'filter','doFilter','request, response, chain')
        q.raw('note over obj,filter: Course web Nginx forwards protected /api/ routes to rbac-backend.\nThe filter is registered when course.upstream is configured.\nCenter /api/v1 routes continue through the normal filter chain.')
        q.call('filter','route','resolve','request.method, path without /api','operationId')
        q.raw('alt route and Bearer header accepted\ngroup configured Authorize callback in rbac-backend')
        q.call('filter','tokens','verify','token' if d else 'bearer')
        q.raw('alt verified token with course audience')
        if d:q.call('filter','auth','validate','principal','actor')
        q.call('filter','auth','api','actorId, systemCode, operationId','allowed')
        if d:q.raw('ref over filter,auth: S04-ddd-policy-sequence shows the version bracket')
        else:q.raw('note over auth: Outside a transaction, api checks the authorization version before and after the decision.')
        q.raw('else invalid token or audience\nfilter --> obj: 401 / 403\nend\nend\nalt authorization accepted');q.call('filter','body','read','request input, max request bytes, 413','bytes')
        q.raw('alt body read succeeds');q.call('filter','proxy','forward','request, coursePath, bytes, traceId, false','course response')
        q.raw('note over proxy: HTTP to course-backend; relay its response.\nCourse object-level 403 is preserved.\nfilter --> obj: course response\nelse body error\nfilter --> obj: 400 / 413\nend\nelse denied or unavailable\nfilter --> obj: 403 / 503\nend\nelse invalid route or Bearer header\nfilter --> obj: 400 / 404 / 401\nend');q.finish()
        build_external_audit_sequence(mode)
        build_role_audit_sequence(mode)
    build_policy_detail()
    build_login_detail()
    build_simulation_detail()

def audit_sequence(name, mode, participants, entry, args):
    q = Sequence(f'{name}-{mode}-sequence', mode, participants, 'ctl', entry, args,
                 reply_arrow='-->>')
    q.lines.insert(1, '!pragma teoz true')
    q.lines[q.lines.index('skinparam ParticipantPadding 24')] = 'skinparam ParticipantPadding 124'
    q.lines[q.lines.index('skinparam maxMessageSize 100')] = 'skinparam maxMessageSize 200'
    return q


def audit_replay(q, ddd):
    target = 'fact' if ddd else 'svc'
    q.open('svc', target, 'requireReplay' if ddd else 'same',
           'proposed' if ddd else 'existing, payload')
    q.raw('break Conflict')
    q.reply(target, 'obj', '400 AUDIT_DUPLICATE_CONFLICT')
    q.raw('||16||\nend')
    q.close(target, 'svc')
    q.raw('note right of svc: Keep the original audit record and ID.')


def build_external_audit_sequence(mode):
    ddd = mode == 'ddd'
    participants = {
        'ctl': 'IdentityController' if ddd else 'AuthController',
        'svc': 'AuditApplicationService' if ddd else 'AuditService',
        'repo': 'AuditRepository' if ddd else 'Store',
    }
    if ddd:
        participants['fact'] = 'AuditFact'
    q = audit_sequence('S05', mode, participants, 'audit' if ddd else 'write',
                       'bearer, key, input' if ddd else 'actor, key, input')
    if ddd:
        q.raw('note right of ctl: Resolve principal from Bearer. The caller does not set the actor.')
        q.open('ctl', 'ctl', 'auditActor', 'principal, key, audit:write')
        q.raw('note right of ctl: Check the service key and audit:write to obtain the actor.')
        q.close('ctl', 'ctl')
        q.raw('note right of ctl: Read the effective course role codes for the audit snapshot.')
        q.open('ctl', 'svc', 'external',
               'actor, system, roles, requestId, type, objectType, objectId, target, before, after')
    else:
        q.raw('note right of ctl: PermissionInterceptor has already resolved actor from the user token.')
        q.open('ctl', 'svc', 'external', 'actor, input, key')
        q.call('svc', 'svc', 'trusted', 'key')
        q.raw('note right of svc: Check whether the actor has the API permission audit:write.')
    q.raw('note right of svc: Validate payload fields and hash canonical JSON.\n'
          'The digest binds the source system and authenticated actor to the content.')
    if ddd:
        q.raw('note right of svc: Build the proposed AuditFact with millisecond precision.')
    lookup = 'find' if ddd else 'one'
    arguments = 'requestId' if ddd else 'AuditLog.class, requestId predicate'
    q.raw('group First audit transaction (REQUIRES_NEW)')
    q.call('svc', 'repo', lookup, arguments, 'existing audit or null')
    q.raw('alt Known ID')
    audit_replay(q, ddd)
    q.raw('else New requestId')
    if not ddd:
        q.raw('note right of svc: Build the AuditLog and truncate occurredAt to milliseconds.')
    q.call('svc', 'repo', 'append' if ddd else 'save', 'fact' if ddd else 'log')
    if not ddd:
        q.call('svc', 'repo', 'flush')
    q.raw('||16||\nend\nend')
    q.raw('opt Retry')
    q.raw('note right of svc: Catch DataIntegrityViolationException after the first transaction rolls back.\nConcurrent insertion of the same requestId is one cause.')
    q.raw('group Read again (separate REQUIRES_NEW transaction)')
    q.call('svc', 'repo', lookup, arguments, 'existing audit or null')
    q.raw('alt Record found')
    audit_replay(q, ddd)
    q.raw('else Record absent')
    q.raw('break Missing')
    q.reply('svc', 'obj', 'Rethrow the original database exception')
    q.raw('||16||\nend\nend\nend\nend')
    q.raw('note over svc,ctl: Complete the audit transaction before replying.\nAfter a timeout, retry with the original requestId.')
    q.close('svc', 'ctl', 'audit record')
    q.reply('ctl', 'obj', '201 audit result')
    q.finish()


def build_role_audit_sequence(mode):
    ddd = mode == 'ddd'
    participants = {
        'ctl': 'RoleController' if ddd else 'ManagementController',
        'svc': 'RoleApplicationService' if ddd else 'ManagementService',
        'auth': 'AuthorizationApplicationService' if ddd else 'AuthorizationService',
        'repo': 'AuthorizationRepository' if ddd else 'Store',
        'audit': 'AuditApplicationService' if ddd else 'AuditService',
    }
    if ddd:
        participants.update(role='Role', graph='RoleHierarchy',
                            integrity='GrantIntegrityService', auditRepo='AuditRepository')
    q = audit_sequence('S06', mode, participants, 'status' if ddd else handler(mode, 'roleStatus'),
                       'bearer, id, input' if ddd else 'actor, id, input')
    q.open('ctl', 'svc', 'status' if ddd else 'roleStatus',
           'principal, id, enabled' if ddd else 'actor, id, status')
    q.raw('group Same transaction: role status, grant version and success audit')
    if ddd:
        q.call('svc', 'repo', 'lockGrants')
        q.call('svc', 'auth', 'require', 'principal, rbac:role:status, true')
        q.call('svc', 'repo', 'role', 'id')
        q.call('svc', 'auth', 'requireRoleManagement', 'actor, role')
        q.raw('||12||\nopt Enable')
        q.call('svc', 'auth', 'requireRoleGrantSystem', 'actor, role.systemCode()')
        q.raw('||16||\nend')
        q.call('svc', 'role', 'changeStatus', 'enabled')
        q.raw('||12||\nopt Enable')
        q.open('svc', 'svc', 'requireAffectedGrants', 'actor, proposed graph, role, permissions')
        q.call('svc', 'graph', 'affectedBy', 'role.id()')
        q.raw('||12||\nloop Affected')
        q.call('svc', 'auth', 'requireGraphGrant', 'actor, proposed graph, affectedId')
        q.call('svc', 'auth', 'upperBound', 'actor, systemCode, proposed permissions')
        q.raw('||16||\nend')
        q.close('svc', 'svc')
        q.raw('||16||\nend')
        q.call('svc', 'repo', 'saveRole', 'role')
        q.call('svc', 'integrity', 'requireAdministrator')
        q.raw('note over svc,repo: Check that an effective SUPER_ADMIN remains.\nA failure rolls back the transaction before the version or audit is written.')
        q.call('svc', 'repo', 'advanceVersion')
    else:
        q.call('svc', 'auth', 'lock')
        q.raw('note over svc,auth: begin checks rbac:role:status and root department,\nthen captures the grant limit for this actor before mutation.')
        q.call('svc', 'auth', 'require', 'Role.class, id', 'role')
        q.call('svc', 'auth', 'protectRole', 'actor, role')
        q.raw('||12||\nopt Enable')
        q.call('svc', 'auth', 'requireRoleGrantSystem', 'actor, roleSystem')
        q.raw('||16||\nend')
        q.raw('note over svc: Set the managed Role.status. If enabled, check the proposed grants.')
        q.call('svc', 'repo', 'flush')
        q.call('svc', 'auth', 'preserveAdministrator')
        q.raw('note over svc: finish increments the grant version after the integrity check.')
    q.open('svc', 'audit', 'local' if ddd else 'success',
           'actor, system, ROLE_STATUS_CHANGED, target, SUCCESS, null' if ddd
           else 'actor, ROLE_STATUS, target')
    q.raw('note over audit: Capture the current roles for this actor and build the success record.')
    audit_repo = 'auditRepo' if ddd else 'repo'
    q.open('audit', audit_repo, 'append' if ddd else 'save', 'fact' if ddd else 'log')
    q.raw('break Failure')
    q.reply(audit_repo, 'obj', 'Persistence error, transaction rolls back')
    q.raw('||16||\nend')
    q.close(audit_repo, 'audit')
    q.close('audit', 'svc')
    q.raw('||16||\nend')
    q.raw('note over ctl,svc: Spring commits after the service method finishes, before returning to the controller.')
    q.raw('alt Committed')
    q.reply('svc', 'ctl', 'role result')
    q.reply('ctl', 'obj', '200 role status updated')
    q.raw('else Commit throws')
    q.reply('svc', 'obj', 'Commit error, success response withheld')
    q.raw('||16||\nend')
    q.raw('note over audit: Denied audit events use a separate transaction.')
    end_use_case(q)


def build_simulation_detail():
    p={'ctl':'AuthorizationController','rest':'RestSupport','directory':'DirectoryApplicationService',
       'auth':'AuthorizationApplicationService','repo':'AuthorizationRepository','restType':'RestSupport'}
    q=Sequence('SIM-ddd-sequence','ddd',p,'ctl','check','bearer, input',reply_arrow='-->>')
    method(key('AuthorizationController','ddd'),'explain')
    q.lines[-2]+=' or explain(bearer, input)'
    q.lines.insert(1,'!pragma teoz true')
    q.lines[q.lines.index('participant "restType: RestSupport" as restType')]='participant "«MetaClass»\\n: RestSupport" as restType'
    q.raw('note over ctl: /authorization/check passes explain=false.\n/authorization/explain passes explain=true.')
    q.open('ctl','ctl','check','bearer, input, explain')
    q.call('ctl','rest','principal','bearer','principal')
    q.call('ctl','auth','require','principal, rbac:authz:invoke, false','actor')
    q.raw('break identifier does not match UI / API kind')
    q.reply('ctl','obj','400 INVALID_AUTHORIZATION_REQUEST')
    q.raw('end')
    q.raw('note over ctl: Use the requested userId, or actor.id() if omitted.')
    q.raw('opt target differs from actor')
    q.call('ctl','directory','user','principal, targetUserId','target within department scope')
    q.raw('end')
    q.open('ctl','auth','simulate','targetUserId, system, kind, identifier')
    q.open('auth','auth','stableSimulation','targetUserId, system, kind, identifier')
    q.raw('loop until terminal result, at most 3 attempts')
    q.call('auth','repo','version',reply='initial version')
    q.raw('group Target status and decision')
    q.open('auth','auth','unavailableReason','targetUserId, system')
    q.call('auth','repo','systemEnabled','system','enabled')
    q.call('auth','repo','identity','targetUserId','target identity')
    q.raw('note over auth,repo: An unknown system or missing user throws a 404 DomainException.\nThe attempt skips the remaining status and decision reads.')
    q.close('auth','auth','unavailable reason or null')
    q.raw('alt target available')
    q.call('auth','auth','stableEffective','targetUserId, system','effective permissions')
    q.raw('alt UI\nnote over auth: Decide with Effective.has(identifier, UI).\nelse API')
    q.call('auth','auth','stableApi','targetUserId, system, identifier','allowed')
    q.raw('end\nelse target user or system disabled')
    q.raw('note over auth: Use empty effective permissions and allowed=false.')
    q.raw('end\nend')
    q.raw('alt attempt completed')
    q.call('auth','repo','version',reply='current version')
    q.raw('note over auth,repo: If the version matches, finish with SimulationResult.\nOtherwise repeat the whole attempt.')
    q.raw('else DomainException caught during lookup or decision')
    q.raw('opt status below 500')
    q.call('auth','repo','version',reply='current version')
    q.raw('end')
    q.raw('break status at least 500 or version unchanged')
    q.reply('auth','obj','DomainException with original status and code')
    q.raw('end')
    q.raw('note over auth: A changed version on a client error retries the whole attempt.')
    q.raw('end\nend')
    q.raw('break three attempts exhausted')
    q.reply('auth','obj','503 AUTHORIZATION_UNSTABLE')
    q.raw('end')
    q.close('auth','auth','SimulationResult')
    q.close('auth','ctl','SimulationResult')
    q.raw('note over ctl: Return allowed, reason and traceId.\nReason is USER_DISABLED / SYSTEM_DISABLED, ALLOW or DENY.')
    q.raw('opt explain=true')
    q.call('ctl','restType','effective','decision.effective()','effective permissions')
    q.raw('note over ctl: Include the explanation trace and effective permissions.')
    q.raw('end')
    q.call('ctl','restType','ok','result','response envelope')
    q.close('ctl','ctl','response')
    q.reply('ctl','obj','HTTP 200 with simulation response')
    q.finish()

def structured_effective_sequence():
    mode='structured'
    participants={'ctl':'ManagementController','svc':'ManagementService',
                  'auth':'AuthorizationService','model':'Store'}
    q=Sequence('S03-structured-sequence',mode,participants,'ctl',handler(mode,'effectiveUser'),
               'actor, user, system',reply_arrow='-->>')
    q.lines.insert(1,'!pragma teoz true')
    q.open('ctl','svc','effectiveUser','actor, user, system')
    q.raw('note over svc: Read-only transaction for this HTTP request')
    q.call('svc','auth','center','actor, rbac:user:view')
    q.open('svc','auth','system','system')
    q.raw('break unknown system')
    q.reply('auth','obj','404 SYSTEM_NOT_FOUND')
    q.raw('end')
    q.close('auth','svc','targetSystem')
    q.call('svc','auth','require','User.class, user','targetUser')
    q.call('svc','auth','scope','actor, target department')
    q.raw('||15||')
    q.raw('alt target user active and target system enabled')
    q.open('svc','auth','snapshot','userId, system')
    q.call('auth','auth','activeSystem','userId, system','enabled system')
    q.call('auth','auth','direct','userId, systemId','direct role IDs')
    q.open('auth','auth','calculate','directIds, systemId')
    q.open('auth','auth','calculateConfiguredGrants','directIds, systemId')
    q.call('auth','model','where','Role.class, system predicate','roles')
    q.raw('note over auth,model: Also load inheritance edges, enabled permissions and role grants.')
    q.raw('loop each valid direct role')
    q.call('auth','auth','walk','root, roles, edges, grants, path, effective, permissions, sources')
    q.raw('note right of auth: Collect grants and follow enabled junior roles.')
    q.raw('end')
    q.raw('note over auth,model: Skip disabled roles, deduplicate permission IDs and preserve distinct source paths.')
    q.close('auth','auth','Snapshot')
    q.close('auth','auth','Snapshot')
    q.raw('note over auth: The transaction bypasses cache reads and writes.')
    q.close('auth','svc','Snapshot')
    q.raw('else target user or system disabled')
    q.raw('note over svc: Use an empty Snapshot.')
    q.raw('end')
    q.call('svc','auth','effective','snapshot, system','directRoles, effectiveRoles, permissions, sources')
    q.close('svc','ctl','effective permissions')
    q.close('ctl','obj','HTTP 200 with effective permissions')
    q.raw('deactivate obj\n== Separate invocation: snapshot() outside a transaction ==')
    q.open('obj','auth','snapshot','userId, system')
    q.raw('activate obj')
    q.raw('loop until stable or at most 3 attempts')
    q.call('auth','auth','version',reply='initial version')
    q.call('auth','auth','activeSystem','userId, system','enabled system')
    q.raw('note over auth,model: Look up the versioned key in L1, then Redis on miss.\nCalculate from the database when neither cache yields a usable snapshot.')
    q.raw('opt no usable cached snapshot')
    q.call('auth','auth','direct','userId, systemId','direct role IDs')
    q.call('auth','auth','calculate','directIds, systemId','Snapshot')
    q.raw('end')
    q.call('auth','auth','version',reply='pre-write version')
    q.raw('opt unchanged version before writing')
    q.raw('note over auth: Write L1 and attempt to write Redis.')
    q.call('auth','auth','version',reply='post-write version')
    q.raw('end')
    q.raw('note over auth,model: A version change before or after writing retries the attempt.\nAn unchanged version after writing ends the loop.')
    q.raw('end')
    q.raw('alt stable version')
    q.reply('auth','obj','Snapshot')
    q.raw('else three attempts exhausted')
    q.reply('auth','obj','BizException(503, AUTHORIZATION_UNAVAILABLE)')
    q.raw('end')
    q.close('auth','obj')
    q.finish(close_entry=False)

def build_policy_detail():
    participants = {
        'auth': 'AuthorizationApplicationService',
        'reads': 'AuthorizationReadTransaction',
        'repo': 'AuthorizationRepository',
        'policy': 'AuthorizationPolicy',
    }
    q = Sequence('S04-ddd-policy-sequence', 'ddd', participants, 'auth', 'api',
                 'userId, system, operationId', reply_arrow='-->>')
    q.lines.insert(1, '!pragma teoz true')
    # Leave room for fragment guards before the caller's execution bar.
    q.lines[q.lines.index('skinparam ParticipantPadding 24')] = 'skinparam ParticipantPadding 124'
    q.lines[q.lines.index('skinparam maxMessageSize 100')] = 'skinparam maxMessageSize 275'
    q.raw('alt outer read-only')
    q.open('auth', 'reads', 'fresh', 'query callback')
    q.raw('note right of reads: REQUIRES_NEW, READ_COMMITTED, readOnly.\nClear the JPA context at the start of each attempt.')
    q.open('reads', 'auth', 'stableApi', 'userId, system, operationId')
    q.raw('ref over auth,policy: Run the stableApi retry loop shown below')
    q.close('auth', 'reads', 'allowed')
    q.close('reads', 'auth', 'allowed')
    q.raw('||12||\nelse direct call')
    q.open('auth', 'auth', 'stableApi', 'userId, system, operationId')
    q.raw('loop up to 3 tries')
    q.call('auth', 'repo', 'version', reply='initial version')
    q.call('auth', 'repo', 'operation', 'system, operationId', 'registered operation or null')
    q.raw('break missing')
    q.reply('auth', 'obj', 'DomainException 404 API_OPERATION_NOT_FOUND')
    q.raw('||12||\nend')
    q.call('auth', 'repo', 'identity', 'userId', 'target identity')
    q.raw('note right of auth: allowed starts false.')
    q.raw('opt target active')
    q.call('auth', 'auth', 'effective', 'userId, system', 'effective permissions')
    q.call('auth', 'repo', 'identity', 'userId', 'current target identity')
    q.raw('opt still active')
    q.call('auth', 'policy', 'allows', 'operation, effective', 'allowed')
    q.raw('||12||\nend\nend')
    q.call('auth', 'repo', 'version', reply='final version')
    q.raw('note right of auth: Stop when the version matches.\nRetry when the version changes.')
    q.raw('||12||\nend')
    q.raw('break unstable')
    q.reply('auth', 'obj', 'DomainException 503 AUTHORIZATION_UNSTABLE')
    q.raw('||12||\nend')
    q.close('auth', 'auth')
    q.raw('||12||\nend')
    q.reply('auth', 'obj', 'allowed')
    q.finish()

def build_login_detail():
    p={'identity':'IdentityApplicationService','attempts':'LoginAttemptApplicationService','repo':'AuthorizationRepository','policy':'LoginAttemptPolicy','encoder':'CredentialEncoder','tokens':'TokenProvider','audit':'AuditApplicationService'}
    q=Sequence('LOGIN-ddd-sequence','ddd',p,'identity','login','username, password, system')
    q.open('identity','attempts','attempt','username, password, system');q.raw('group attempt @Transactional: locked persistent login state')
    q.call('attempts','repo','lockLogin','username');q.raw('alt no account\nnote over attempts: return null; no token\nelse account exists')
    q.call('attempts','policy','locked','lockedUntil, now');q.raw('opt ACTIVE and not locked');q.call('attempts','encoder','matches','password, storedHash');q.raw('end')
    q.call('attempts','policy','evaluate','failedCount, lockedUntil, active, matches, now')
    q.call('attempts','repo','saveLoginState','user.id(), result.failedCount(), result.lockedUntil()')
    q.raw('alt rejected');q.call('attempts','audit','local','user, system, LOGIN_DENIED, target, DENIED, INVALID_CREDENTIALS')
    q.raw('note over policy: fifth wrong credential locks for 900s;\nlocked attempt preserves count/until; expiry restarts count;\naccepted credentials reset count to zero.\nelse accepted')
    q.call('attempts','repo','systemEnabled','system');q.call('attempts','tokens','issue','subject, system, tokenVersion')
    q.call('attempts','audit','local','user, system, LOGIN, target, SUCCESS, null')
    q.raw('end\nend\nend\nnote over attempts,identity: attempt transaction COMMIT before returning null/token.\nDisabled system or persistence exception still rolls this transaction back.')
    q.close('attempts','identity');q.raw('alt null result\nidentity --> obj: DomainException 401 INVALID_CREDENTIALS\nelse token\nnote over identity: return token; HTTP layer owns response\nend');q.finish()
