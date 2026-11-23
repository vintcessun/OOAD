"""Source-checked RBAC1 package diagrams based on the course project's SVG template."""
import re
from html import escape
from pathlib import Path
from string import Template


TEMPLATE = Path(__file__).with_name('render-rbac1-design.package-template.svg')
BACKGROUND = '#f5f5f7'
INK = '#1d1d1f'
SECONDARY = '#6e6e73'
DEPENDENCY = ('#6c6c70', 1.5, '7 5', 'openArrow')
REALIZATION = ('#5856d6', 2.2, '5 4', 'triangle')

# Layer rectangles and tabs follow the reference's own grid and palette.
STRUCTURED_LAYERS = (
    (30, 120, 430, 720, 'API', 'HTTP 入口、请求与响应对象', '#007aff'),
    (500, 120, 520, 780, 'SERVICE.STRUCTURED', '事务脚本与业务校验', '#34c759'),
    (1060, 120, 460, 780, 'PERSISTENCE', '数据访问与实体映射', '#af52de'),
    (1570, 120, 520, 900, 'CONFIGURATION', 'Web、课程代理与日志装配', '#5856d6'),
)
STRUCTURED_PACKAGES = {
    'controller': (0, (70, 260, 350, 191), ('AuthController', 'ManagementController', 'PermissionInterceptor',
                                         'IntegrationIdentityController', 'GlobalExceptionHandler')),
    'dto': (0, (70, 542, 350, 164), ('Inputs', 'Actor', 'IntegrationViews', 'DisplayName')),
    'service': (1, (540, 260, 440, 239), ('ManagementService', 'AuthorizationService', 'AuthenticationService',
                                        'AuditService', 'ResourceService', 'TokenService', 'IntegrationIdentityService')),
    'mapper': (2, (1100, 260, 380, 187), ('Store', 'RoleMapper', 'GrantVersionMapper', 'DepartmentMapper', 'UserMapper')),
    'entity': (2, (1100, 574, 380, 207), ('Role', 'User', 'GrantVersion', 'AuditLog', 'ApiOperation', 'UiResource')),
    'config': (3, (1610, 260, 440, 187), ('WebConfig', 'CourseProxyConfiguration', 'CompatiblePasswordEncoder',
                                          'TraceFilter', 'LayerLoggingAspect')),
}
# Every edge carries its own label position and route to keep the SVG editable.
STRUCTURED_EDGES = (
    ('controller', 'service', '调用管理与认证服务', (480, 313), ((420, 327), (540, 327))),
    ('controller', 'dto', '接收请求并组装响应', (298, 493), ((198, 451), (198, 542))),
    ('service', 'dto', '读取 Actor 和输入对象', (490, 600), ((540, 463), (478, 463), (478, 579), (420, 579))),
    ('service', 'mapper', '使用 Store / Mapper', (1040, 313), ((980, 327), (1100, 327))),
    ('service', 'entity', '读取或修改实体状态', (1040, 652), ((980, 431), (1035, 431), (1035, 629), (1100, 629))),
    ('mapper', 'entity', '通过 JPA 读写实体', (1394, 518), ((1230, 447), (1230, 574))),
    ('dto', 'entity', '将请求输入映射到实体', (726, 813), ((244, 706), (244, 832), (1290, 832), (1290, 781))),
    ('config', 'controller', '注册 PermissionInterceptor', (376, 214), ((1700, 260), (1700, 222), (194, 222), (194, 260))),
    ('config', 'service', '装配 Auth / Token 服务', (1330, 238), ((1660, 260), (1660, 245), (656, 245), (656, 260))),
)

DDD_LAYERS = (
    (30, 120, 420, 760, 'API', 'HTTP 入口、DTO 与 VO', '#007aff'),
    (490, 120, 500, 820, 'DOMAIN.PORT', '仓储、身份、缓存与操作契约端口', '#5856d6'),
    (1030, 120, 460, 490, 'APPLICATION', '用例编排、事务与授权协调', '#ff9500'),
    (1030, 680, 460, 545, 'DOMAIN', '领域对象和授权策略', '#ff9500'),
    (1550, 120, 760, 1220, 'INFRASTRUCTURE', 'infrastructure.* · JPA、身份、缓存与配置适配', '#af52de'),
)
DDD_PACKAGES = {
    'interfaces.rest': (0, (70, 255, 340, 215), ('RoleController', 'DirectoryController', 'IdentityController',
                                               'AuthorizationController', 'ResourceController', 'RegisteredApiInterceptor')),
    'interfaces.rest.dto': (0, (70, 572, 340, 185), ('CreateRoleRequest', 'UserUpdateRequest', 'RoleView', 'UiResourceView')),
    'domain.port': (1, (530, 255, 420, 400), ('AuthorizationRepository', 'DirectoryRepository', 'AuditRepository',
                                            'ResourceRepository', 'TokenProvider', 'CredentialEncoder', 'EffectiveCache', 'OperationContract')),
    'application': (2, (1070, 255, 380, 255), ('RoleApplicationService', 'DirectoryApplicationService',
                                             'AuthorizationApplicationService', 'IdentityApplicationService',
                                             'ResourceApplicationService', 'AuditApplicationService', 'GrantIntegrityService')),
    'domain': (3, (1070, 835, 380, 244), ('Role', 'RoleHierarchy', 'User', 'DepartmentEligibility',
                                        'AuthorizationPolicy', 'LastAdministratorPolicy', 'AuditFact')),
    'infrastructure.persistence': (4, (1590, 250, 318, 215), ('JpaAuthorizationRepositoryAdapter',
                                                           'JpaDirectoryRepositoryAdapter', 'JpaAuditRepositoryAdapter',
                                                           'JpaResourceRepositoryAdapter', 'RoleState', 'UiState')),
    'infrastructure.persistence.entity': (4, (1955, 250, 315, 172), ('RoleEntity', 'UserEntity', 'GrantVersionEntity', 'AuditEntity')),
    'infrastructure.persistence.repository': (4, (1955, 478, 315, 148), ('RoleJpaRepository', 'UserJpaRepository', 'GrantVersionJpaRepository')),
    'infrastructure.identity': (4, (1590, 510, 318, 145), ('RsaTokenProvider', 'BcryptCredentialEncoder', 'BootstrapInitializer')),
    'infrastructure.cache': (4, (1590, 702, 318, 106), ('VersionedEffectiveCache',)),
    'infrastructure.config': (4, (1590, 860, 318, 176), ('OpenApiOperationContract', 'HttpSecurityConfiguration',
                                                       'CourseProxyConfiguration', 'RouteContractVerifier')),
    'infrastructure.health': (4, (1955, 704, 315, 108), ('ImplementationHealthIndicator',)),
    'infrastructure.logging': (4, (1955, 862, 315, 108), ('LayerLoggingAspect',)),
}
DDD_EDGES = (
    ('interfaces.rest', 'application', '调用应用服务', (869, 245), ((196, 255), (196, 229), (1195, 229), (1195, 255))),
    ('interfaces.rest', 'interfaces.rest.dto', '映射请求与响应', (320, 522), ((201, 470), (201, 572))),
    ('interfaces.rest.dto', 'application', '映射命令与应用结果', (376, 203), ((70, 653), (46, 653), (46, 212), (1212, 212), (1212, 255))),
    ('interfaces.rest', 'domain.port', '使用身份与目录端口', (470, 317), ((410, 331), (530, 331))),
    ('application', 'domain.port', '调用授权与仓储端口', (1010, 366), ((1070, 380), (950, 380))),
    ('application', 'domain', '编排聚合、调用授权规则', (1415, 765), ((1300, 510), (1300, 869))),
    ('domain', 'domain.port', '使用仓储契约中的类型', (1138, 644), ((1070, 910), (1048, 910), (1048, 452), (950, 452))),
    ('domain.port', 'domain', '接收或返回领域对象', (914, 688), ((950, 478), (1041, 478), (1041, 959), (1070, 959))),
    ('infrastructure.persistence', 'infrastructure.persistence.entity', '映射', (1931, 331), ((1908, 348), (1955, 348))),
    ('infrastructure.persistence', 'infrastructure.persistence.repository', '调用 JPA Repository', (1885, 490),
     ((1850, 465), (1850, 503), (1932, 503), (1932, 544), (1955, 544))),
    ('infrastructure.persistence.repository', 'infrastructure.persistence.entity', '持久化实体', (2156, 453), ((2077, 478), (2077, 422))),
    ('infrastructure.persistence', 'domain', '恢复领域对象', (1546, 347), ((1590, 361), (1507, 361), (1507, 934), (1450, 934))),
    ('infrastructure.persistence', 'domain.port', '实现 AuthorizationRepository / DirectoryRepository\nAuditRepository / ResourceRepository',
     (1270, 1098), ((1590, 405), (1520, 405), (1520, 1130), (1028, 1130), (1028, 518), (950, 518)),
     (('JpaAuthorizationRepositoryAdapter', 'AuthorizationRepository'), ('JpaDirectoryRepositoryAdapter', 'DirectoryRepository'),
      ('JpaAuditRepositoryAdapter', 'AuditRepository'), ('JpaResourceRepositoryAdapter', 'ResourceRepository'))),
    ('infrastructure.identity', 'domain.port', '实现 TokenProvider / CredentialEncoder',
     (1270, 1154), ((1590, 599), (1529, 599), (1529, 1168), (1015, 1168), (1015, 555), (950, 555)),
     (('RsaTokenProvider', 'TokenProvider'), ('BcryptCredentialEncoder', 'CredentialEncoder'))),
    ('infrastructure.cache', 'domain.port', '实现 EffectiveCache',
     (1270, 1192), ((1590, 766), (1538, 766), (1538, 1206), (1002, 1206), (1002, 592), (950, 592)),
     (('VersionedEffectiveCache', 'EffectiveCache'),)),
    ('infrastructure.config', 'domain.port', '实现 OperationContract',
     (1270, 1230), ((1590, 935), (1547, 935), (1547, 1244), (989, 1244), (989, 629), (950, 629)),
     (('OpenApiOperationContract', 'OperationContract'),)),
)


def layer_box(x, y, w, h, title, subtitle, color):
    return f'''<g>
    <rect x="{x}" y="{y}" width="{w}" height="{h}" rx="24" fill="#ffffff" fill-opacity="0.42" stroke="#dedee3" stroke-width="1.2"/>
    <rect x="{x+20}" y="{y+18}" width="5" height="42" rx="2.5" fill="{color}"/>
    <text x="{x+38}" y="{y+34}" font-size="11.5" font-weight="760" fill="{SECONDARY}">«layer»</text>
    <text x="{x+38}" y="{y+58}" font-size="20" font-weight="800" fill="{INK}">{escape(title)}</text>
    <text x="{x+38}" y="{y+80}" font-size="12.5" fill="{SECONDARY}">{escape(subtitle)}</text>
  </g>'''


def package_box(package, bounds, members, color):
    x, y, w, h = bounds
    name = package.removeprefix('infrastructure.')
    rows = '\n    '.join(
        f'<text x="{x+22}" y="{y+74+i*24}" font-size="14" fill="{INK}">{escape(member)}</text>'
        for i, member in enumerate(members)
    )
    return f'''<g data-package="{escape(package, quote=True)}">
    <path d="M{x},{y+18} Q{x},{y} {x+18},{y} H{x+150} V{y+34} H{x+w} V{y+h} H{x} Z" fill="#ffffff" stroke="#c7c7cc" stroke-width="1.4"/>
    <rect x="{x}" y="{y+34}" width="5" height="{h-34}" rx="2.5" fill="{color}"/>
    <text x="{x+16}" y="{y+24}" font-size="12" font-weight="750" fill="{SECONDARY}">«package»</text>
    <text x="{x+22}" y="{y+56}" font-size="19" font-weight="780" fill="{INK}">{escape(name)}</text>
    {rows}
  </g>'''


def relation(points, style):
    stroke, width, dash, marker = style
    path = ' '.join(f'{x},{y}' for x, y in points)
    return (f'<polyline points="{path}" fill="none" stroke="{stroke}" stroke-width="{width}" '
            f'stroke-linejoin="round" stroke-linecap="round" stroke-dasharray="{dash}" marker-end="url(#{marker})"/>')


def edge_box(source, target, caption, label, points, *realizations):
    x, y = label
    style = REALIZATION if realizations else DEPENDENCY
    spans = ''.join(f'<tspan x="{x}" dy="{15 if i else 0}">{escape(line)}</tspan>'
                    for i, line in enumerate(caption.splitlines()))
    return f'''<g data-source="{source}" data-target="{target}">
    {relation(points, style)}
    <text x="{x}" y="{y}" text-anchor="middle" font-size="12" font-weight="650" fill="{SECONDARY}" paint-order="stroke" stroke="{BACKGROUND}" stroke-width="5">{spans}</text>
  </g>'''


def legend(x, y, dependency_note):
    return f'''<g>
    <rect x="{x}" y="{y}" width="680" height="88" rx="14" fill="#ffffff" stroke="#d1d1d6"/>
    <text x="{x+20}" y="{y+25}" font-size="13" font-weight="760" fill="{INK}">图例</text>
    {relation(((x+20, y+52), (x+120, y+52)), DEPENDENCY)}
    <text x="{x+136}" y="{y+56}" font-size="12" fill="{SECONDARY}">依赖：灰色虚线 + 空心箭头</text>
    {relation(((x+360, y+52), (x+460, y+52)), REALIZATION)}
    <text x="{x+476}" y="{y+56}" font-size="12" fill="{SECONDARY}">实现：蓝色虚线 + 空心三角</text>
    <text x="{x+20}" y="{y+77}" font-size="12" fill="{SECONDARY}">{escape(dependency_note)}</text>
  </g>'''


def scope_note(x, y, message):
    return f'''<g>
    <rect x="{x}" y="{y}" width="1040" height="58" rx="12" fill="#ffffff" stroke="#d1d1d6"/>
    <text x="{x+18}" y="{y+25}" font-size="13" font-weight="720" fill="{INK}">当前视图</text>
    <text x="{x+18}" y="{y+45}" font-size="12.5" fill="{SECONDARY}">{escape(message)}</text>
  </g>'''


def validate_relations(mode, packages, edges, index):
    prefix = f'com.xmu.rbac.{mode}.'
    sources = {name.removeprefix(prefix): entry['source'] for name, entry in index.items() if name.startswith(prefix)}
    references = {}
    for name, source in sources.items():
        body = re.sub(r'(?m)^\s*package\s+[^;]+;', '', source)
        references[name] = set(re.findall(r'\b' + re.escape(prefix) + r'([\w.]+\*?)', body))
    for package, (_, _, members) in packages.items():
        for member in members:
            if f'{package}.{member}' not in sources:
                raise ValueError(f'Missing package diagram type: {package}.{member}')
    for source, target, _, _, _, *realizations in edges:
        if realizations:
            for adapter, port in realizations[0]:
                key = f'{source}.{adapter}'
                declaration = sources[key].split(f'class {adapter}', 1)[-1].split('{', 1)[0]
                if (not re.search(r'\bimplements\s+[^{}]*\b' + re.escape(port) + r'\b', declaration)
                        or not references[key].intersection({f'{target}.{port}', f'{target}.*'})):
                    raise ValueError(f'Missing package diagram realization: {adapter} implements {port}')
        else:
            target_types = {name for name in sources if name.rsplit('.', 1)[0] == target}
            target_pattern = r'(?:' + '|'.join(re.escape(name) for name in sorted(target_types)) + r')(?:\..+)?'
            if not any(ref == target + '.*' or re.fullmatch(target_pattern, ref)
                       for name, refs in references.items() if name.rsplit('.', 1)[0] == source for ref in refs):
                raise ValueError(f'Missing package diagram dependency: {source} -> {target}')


def package_views(index):
    """Yield standalone SVG for both backend implementations after validating Java evidence."""
    template = Template(TEMPLATE.read_text(encoding='utf-8'))
    for mode, variant, width, height, layers, packages, edges, note in (
        ('structured', 'Structured', 2140, 1200, STRUCTURED_LAYERS, STRUCTURED_PACKAGES, STRUCTURED_EDGES,
         '范围：com.xmu.rbac.structured 主要子包。事务脚本直接使用 Store 和实体。Spring 框架接口及共享 Maven 模块在此视图范围外。'),
        ('ddd', 'DDD', 2340, 1480, DDD_LAYERS, DDD_PACKAGES, DDD_EDGES,
         '范围：com.xmu.rbac.ddd 主要子包。INFRASTRUCTURE 内的包名以 infrastructure 为前缀。四组蓝线对应 Java implements 声明。'),
    ):
        validate_relations(mode, packages, edges, index)
        body = [layer_box(*layer) for layer in layers]
        body.extend(package_box(package, bounds, members, layers[layer][-1])
                    for package, (layer, bounds, members) in packages.items())
        body.extend(edge_box(*edge) for edge in edges)
        legend_x, note_x = (70, 810) if mode == 'structured' else (1160, 70)
        body.extend((scope_note(note_x, height-120, note),
                     legend(legend_x, height-120, '箭头指向被依赖或被实现的包。连线采用正交折线。')))
        yield mode + '-packages', template.substitute(
            width=width, height=height, title=f'RBAC1 权限中心 · {variant} UML Package Diagram',
            subtitle='子包级包图：灰色虚线表示依赖，蓝色虚线空心三角表示实现。',
            body='\n  '.join(body),
        )
