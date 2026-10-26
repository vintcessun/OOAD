"""从当前仓库源码生成 E 的离线答辩速查网页。

运行：python human-review/build-e-guide.py
只读取源码并覆写 human-review/E-code-explained.html。
"""

from __future__ import annotations

import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = Path(__file__).with_name("E-code-explained.html")
S = "rbac-structured/src/main/java/com/xmu/rbac/structured/"
T = "rbac-structured/src/test/java/com/xmu/rbac/structured/integration/"
M = "rbac-contract/src/main/resources/db/migration/"
W = "rbac-web/src/"

# 分类、标题、老师可能的问法、30 秒讲法、追问提示、相对仓库路径、起止行。
# 行号以仓库提交 827ca45 为基线；重新生成时从当前文件抽取，便于同步源码。
CARDS = [
    ("先看这里", "这次验收做到哪个阶段？", "第一次检查 RBAC0 结构化 RBAC1 RBAC2", "当前第一次检查要求前端、API、数据库设计，以及用结构化方式实现 RBAC0 后端。RBAC1 的角色继承、RBAC2 的约束和后续面向对象实现不属于这次交付范围。", "不要把后续阶段的设计骨架说成这次已经实现的功能。", "README.md", 8, 54),
    ("先看这里", "为什么叫结构化设计？三层怎么分？", "Controller Service Mapper 结构化 JPA", "这一阶段按功能过程分解：Controller 接 HTTP 参数与统一响应，Service 写校验、业务流程和事务，Mapper 负责 MySQL/Redis 的数据访问；依赖只能从上往下。这里的 Mapper 是 JPA 接口名称，不代表使用 MyBatis。", "老师问设计方法时，可先说功能分解和数据流，再落到这三层代码。", "docs/02-architecture.md", 79, 108),
    ("先看这里", "真实数据和演示样本有什么区别？", "111 部门 10000 员工 18 系统 测试虚构数据", "README 记录了真实数据导入核对规模：111 个部门、1 万名员工、18 个系统、115 个模块、359 个资源。但仓库里的 e2e 三份工作簿是虚构测试样本，不能拿它们的计数冒充真实数据结果。", "如果老师要求现场展示真实导入证据，应打开数据库/验收记录核对；本网页只是源码导航。", "README.md", 22, 40),
    ("先看这里", "RBAC0 到底是什么？", "RBAC0 用户 角色 权限 关系", "这里把权限分给角色，再把角色指派给用户。一次鉴权会结合用户状态、角色、权限和作用部门判断。部门负责人字段只是组织资料，不会自动让人获得管理员权限。", "可以画出 User → UserRole → Role → RolePermission → Permission；作用范围由 UserRole 的部门字段补充。", M+"V1__rbac0_baseline.sql", 162, 210),
    ("先看这里", "E 负责哪四个接口？", "E01 E02 E03 E04 API 公司 部门 导入", "E01 查可见公司，E02 查管理范围内的部门树，E03 导入部门与员工，E04 导入功能权限清单。入口都在 CatalogController，真正的读取与写入分别交给 OrgService 和 ImportService。", "现场先说接口，再沿 Controller → Service → Mapper/表走；具体权限写在 OpenAPI 契约。", S+"controller/CatalogController.java", 128, 158),
    ("先看这里", "一次请求是怎么到业务代码的？", "调用链 前端 API 拦截器 Controller Service Mapper", "页面调用 orgApi，路由进入权限拦截器验证登录和契约声明，再由 CatalogController 调用 OrgService 或 ImportService，最终通过 Mapper 操作数据库。", "不要说前端按钮能保证安全；真正的服务端准入在 PermissionInterceptor，查询结果还要由 Service 按管理范围过滤。", S+"controller/PermissionInterceptor.java", 55, 103),
    ("接口与范围", "四个接口要求什么权限？", "OpenAPI x-required-permission org:view org:import permission:create", "公司列表和部门树需要 rbac:org:view；组织导入需要 rbac:org:import；功能权限清单导入需要 rbac:permission:create。这些声明来自 OpenAPI，拦截器执行入口权限检查。", "有接口权限只表示可执行这类操作；范围内有哪些部门还要看 scopeOf。", "rbac-contract/src/main/resources/openapi/rbac-api.yaml", 921, 998),
    ("接口与范围", "E01 为什么只返回一些公司？", "companies 公司列表 rootDepartmentId 范围", "OrgService 先取操作者的 org:view 授权范围，再遍历公司及其根部门；只有已有根部门且授权范围与公司组织树有交集的公司才返回。超级管理员按全范围处理。", "没有根部门的公司不会显示；它是查询过滤，不会修改数据。", S+"service/OrgService.java", 38, 53),
    ("接口与范围", "E02 为什么祖先可见却不能管理？", "tree 祖先 节点 兄弟分支 作用域", "为了显示路径，部门树会保留通往可管理节点的祖先；但祖先的其他兄弟分支不会因此进入管理范围。真正的管理动作仍要单独检查目标部门。", "可见性和可操作性不同；测试里的道路分公司示例能快速演示。", S+"service/OrgService.java", 55, 85),
    ("接口与范围", "scopeOf 怎么取管理范围？", "scopeOf Scope paths SUPER_ADMIN", "它读取用户的授权集合；超级管理员得到全范围，普通用户筛出指定权限的作用部门路径。OrgService 用这些路径判断公司与节点是否可见。", "同一个权限可能有多个作用部门，返回的是路径集合。", S+"service/AuthorizationService.java", 221, 244),
    ("组织导入", "E03 上传了什么？", "multipart departments employees companyCode companyName", "接口接收公司编码、公司名称和两份 xlsx。先读取部门表与员工表，再创建或复用公司、建部门树、定位员工归属，最后关联部门负责人并返回导入报告。", "参数和字段名要能在 Controller 与前端 orgApi 中对上。", S+"controller/CatalogController.java", 139, 150),
    ("组织导入", "组织导入总体顺序是什么？", "importOrganization 事务 部门 员工 负责人", "ImportService.importOrganization 是一个事务：查找或创建公司、读取两表、先导部门、再导员工、最后关联负责人。有效数据会进入数据库，逐行无法处理的数据进入 anomalies。", "方法末尾明确写了不会自动分配角色；审计会记录导入结果。", S+"service/ImportService.java", 139, 169),
    ("组织导入", "部门树如何按上级关系建立？", "importDepartments pending done 上级 缺失 成环", "把部门行先放进 pending，多轮处理：上级已建好的部门才能创建孩子。重复名、多个根、找不到上级或成环都进入异常清单。部门 path 保存从根到自己的编号路径。", "这里的名称在一份表内要求唯一；已存在部门会复用并更新 managerName。", S+"service/ImportService.java", 172, 236),
    ("组织导入", "同名财务部为何不会串公司？", "locate 分公司 部门 公司归属", "员工不能只按部门名匹配。locate 先确定分公司节点，再在其下找部门；分公司直属人员归分公司本节点，避免把不同公司或不同分公司的同名财务部混起来。", "测试 t19 验证同名部门的跨公司授权隔离；导入测试验证员工部门归属。", S+"service/ImportService.java", 239, 319),
    ("组织导入", "重复导入员工怎么处理？", "importEmployees username 已存在 skip 密码", "导入前取已有用户名；工作簿内重名报异常，数据库里已存在的用户名会跳过。新用户才写入公司、部门、工号、岗位、电话和预编码的初始口令，并标记首次登录须改密。", "跳过意味着已有用户的部门、岗位等不会按新表自动更新；是否需要同步必须单独定规则。", S+"service/ImportService.java", 257, 303),
    ("组织导入", "初始密码为什么只编码一次？", "BCrypt initialPasswordHash mustChangePwd", "UserManagementService 启动时把配置的初始口令做一次 BCrypt 编码，ImportService 导入时复用该哈希，避免每名员工都重复计算。新账号会设 mustChangePwd=true。", "这是导入性能选择；不要现场说出生产环境明文口令。", S+"service/UserManagementService.java", 62, 81),
    ("组织导入", "负责人如何关联？会自动授权吗？", "linkManagers managerName managerUserId 重名", "先在负责人姓名对应的员工里查部门子树；子树内恰好一人就关联，子树内没人时再考虑全公司唯一匹配。重名或找不到会记异常。managerUserId 只表示组织信息，不产生角色指派。", "现实现只索引本轮新建员工；重复导入改成已有员工时存在 HR-003 待复现问题。", S+"service/ImportService.java", 321, 356),
    ("组织导入", "异常报告的行号从哪来？", "Anomaly row readSheet 标题行 空行 HR-002", "报告用 Anomaly(sheet,row,reason,value) 表示异常。但组织导入的 readSheet 跳过表头和空行后只保留单元格值，下游按列表下标加 2 算行号；标题行或空行会使行号偏移。", "这是静态发现 HR-002，尚未在真实运行中复现；不要表述为已经验证的线上事故。", S+"service/ImportService.java", 681, 729),
    ("权限导入", "E04 上传清单后做什么？", "importPermissions 系统 模块 资源 权限 角色模板", "读取首个工作表，定位表头；系统与模块列允许合并单元格延续上一行。每个功能点建立五类操作的权限定义，再按角色列单元格给模板角色同步实际授权。", "权限定义存在，不等于所有角色都获得这些权限。", S+"service/ImportService.java", 368, 465),
    ("权限导入", "“全部”“无”“查看/编辑本人”如何解析？", "parseCell 全部 无 本人 空白 view", "全部映射五类操作，无映射空授权；查看/编辑本人会把相关操作标为 selfOnly。空白和无法识别的词会产生异常，不直接当成“无”撤权。", "selfOnly 需要业务系统继续按记录归属过滤，权限中心只传出限制标记。", S+"service/ImportService.java", 563, 603),
    ("权限导入", "清单异常时会不会误撤权？", "untouched unresolvedRows revokeMissing 异常", "单元格解析失败时将该资源放进 untouched，保留原授权。若功能点本身找不到系统或模块，unresolvedRows 会阻止本次删除同步，但仍允许新增和更新。", "要区分“单元格异常”和“功能点定位失败”两种保护分支。", S+"service/ImportService.java", 467, 518),
    ("权限导入", "删除清单中的权限如何回收？", "revokeMissing wanted 业务权限 rbac", "先收集这次清单要求的 wanted 授权，之后只在业务系统的模板角色授权中删除不再出现的项。rbac 自身的管理权限不由这份清单回收，每条回收都有审计记录。", "界面手工加给模板角色的业务授权，下次导入也可能被清单同步回收。", S+"service/ImportService.java", 538, 559),
    ("权限导入", "授权变更为何下一次请求生效？", "bumpGrantVersion 缓存 事务", "导入同步完成后调用 bumpGrantVersion；版本号存在 MySQL 的 sys_grant_version，并与授权数据的事务一起提交或回滚。新请求读到新版本后，不会继续用旧版本缓存。", "这是代码设计目标；本机没有运行 MySQL/Redis 集成测试，实际生效时间仍需实测。", S+"service/AuthorizationService.java", 199, 214),
    ("权限导入", "资源编码怎样保持稳定？", "resource occurrence 同名 换序 HR-004", "已有资源按模块、名称和同名第几次出现来认领；新资源用该模块已用的最大序号加一。不同名功能点插删行通常不会使后面资源错认，但同名不同义的行换序有风险。", "HR-004 是待组内确认源清单允许同名的规则与待复现问题。", S+"service/ImportService.java", 648, 674),
    ("权限导入", "未知角色列会怎样？", "roleTemplate ROLE_COL 默认角色", "roleTemplate 对已知的五类中文角色列映射固定编码；未知列目前回退为 ROLE_COL 加序号并创建角色。若老师问“五类是否被强制限定”，答案是当前代码未严格拒绝额外角色列。", "这可能是范围收敛问题，需与小组确认是否把未知表头列为异常；不要说已经修改。", S+"service/ImportService.java", 607, 615),
    ("数据库", "组织数据在哪几张表？", "sys_company sys_department sys_user path 外键", "sys_company 是公司，sys_department 保存公司、父部门、层级和物化路径；sys_user 保存员工所属公司和部门。部门 manager_user_id 关联用户，但和授权用的 sys_user_role 是两件事。", "根部门的 parent_id 为空；同级唯一键涉及 NULL 时，数据库层不一定保证每家公司只有一个根，导入逻辑另有约束。", M+"V1__rbac0_baseline.sql", 23, 89),
    ("数据库", "资源、操作、权限的关系？", "Resource Action Permission resource_action", "资源是功能点，操作是查看/新增/编辑等动作，权限是资源×操作的组合。sys_permission 同时存资源 ID、操作 ID、权限码，并用复合唯一键避免同一组合重复。", "角色通过 sys_role_permission 关联权限；用户再通过 sys_user_role 获得角色。", M+"V1__rbac0_baseline.sql", 96, 153),
    ("数据库", "角色权限与用户角色有什么区别？", "sys_role_permission sys_user_role self_only scope_dept_id", "sys_role_permission 说某角色能做什么，含 self_only；sys_user_role 说某用户持有什么角色，以及这个角色在哪个部门范围内生效。两者不能混为一张表。", "清单导入主要改模板角色的 role_permission，不给任何用户自动插入 user_role。", M+"V1__rbac0_baseline.sql", 179, 210),
    ("数据库", "V1、V2、V3、V4 分别改了什么？", "Flyway 迁移 V1 V2 V3 V4 expires_at", "V1 建 RBAC0 基线表和初始化数据；V2 新增授权版本表；V3 把普通员工自增 ID 起点调到 10000，低号段留给内部账号；V4 先停用已到期且仍启用的账号，再删除 expires_at。", "这些是按顺序执行的迁移；V1 出现旧字段不代表当前库仍有该字段。", M+"V4__drop_user_expiry.sql", 1, 15),
    ("数据库", "为什么 StarUML 和 V4 字段不一致？", "rbac.mdj ER expires_at 模型 E-04", "现有 Mermaid ER 与 V4 删除字段一致，但可编辑的 StarUML rbac.mdj 仍有 expires_at。结构校验只检查 JSON 引用等合法性，不能证明它与当前 DDL 字段一致。", "这是 E-04 文档/模型同步问题，答辩时应以迁移后的当前数据库为准，并说明模型待更新。", "diagrams/README.md", 1, 80),
    ("前端", "页面怎样调用四个 E API？", "orgApi companies tree importOrganization importPermissions FormData", "前端 orgApi 对应两个 GET 和两个 POST。组织导入用 FormData 放 departments、employees 两份文件，公司编码和名称放查询参数；权限导入用 file 字段上传。", "现场从 OrgView 的调用跳到 orgApi，再到 Controller。", W+"api/index.ts", 90, 104),
    ("前端", "导入按钮与结果在哪个页面？", "OrgView ImportReportView 权限导入", "组织管理页面 OrgView 同时放组织与权限清单两个导入弹窗。ImportReportView 展示 counts、notes、anomalies；权限目录页 PermissionView 并不包含导入按钮。", "导入成功后组织树会刷新；权限导入只更新报告和成功提示。", W+"views/OrgView.vue", 44, 103),
    ("前端", "为什么旧报告会误导用户？", "E-05 orgReport permReport 旧结果", "打开或关闭弹窗只切换可见状态，没有清空 orgReport/permReport。第二次选择新文件时旧报告还在；新请求失败时也会继续显示，可能误认成新文件的结果。", "E-05 是代码静态发现，前端手工复现与修复还没做。", W+"views/OrgView.vue", 113, 164),
    ("前端", "换公司为何清空已选部门？", "DeptTreeSelect switchCompany model null", "部门选择组件在切换公司时先把已选部门设为 null，再加载新公司的部门树，避免把旧公司的部门编号提交上去。", "祖先节点可能仅用于展示，服务端仍要对最终目标做范围检查。", W+"components/DeptTreeSelect.vue", 32, 58),
    ("测试与问题", "现有测试覆盖哪些导入场景？", "ExcelImportIntegrationTest t21 重导 错文件", "后端集成测试覆盖组织归属与重复导入数量、权限清单归一化和异常、删行回收与 selfOnly、错误文件与无权限。测试通过与否本轮未运行。", "缺标题/空行号、已有员工改负责人、同名功能点换序反例。", T+"ExcelImportIntegrationTest.java", 33, 109),
    ("测试与问题", "部门树的范围怎么验证？", "organizationTreeIsLimitedToScope 404 祖先", "ManagementApiTest 用 root 和道路分公司的管理员对比：root 可见完整公司 A 树，普通管理员只见到祖先和道路分支；不存在的公司返回 404。", "这是测试断言内容，不要说本机已跑过。", T+"ManagementApiTest.java", 627, 648),
    ("测试与问题", "为什么端到端导入用例可能误报失败？", "E-06 orgDialog el-alert 提示位置", "测试在 orgDialog 内寻找“导入不分配任何角色”，但该提示位于 OrgView 弹窗外的页面卡片。导入本身可能成功，测试仍会因为找错范围失败。", "这是静态定位的 E-06，等待测试环境运行确认。", "rbac-web/e2e/console.spec.ts", 75, 98),
]


def code_excerpt(path: str, first: int, last: int) -> str:
    lines = (ROOT / path).read_text(encoding="utf-8").splitlines()
    if not (1 <= first <= last <= len(lines)):
        raise ValueError(f"源码范围过期：{path}:{first}-{last}（总行数 {len(lines)}）")
    return "\n".join(f"{n:4} │ {lines[n - 1]}".rstrip() for n in range(first, last + 1))


def e(value: str) -> str:
    return html.escape(str(value), quote=True)


def render_card(index: int, row: tuple[str, ...]) -> str:
    category, title, aliases, answer, followup, path, first, last = row
    local_url = "../" + path
    github_url = f"https://github.com/vintcessun/OOAD/blob/827ca45/{path}#L{first}-L{last}"
    search = " ".join(row[:6]).lower()
    return f"""
    <article class="card" id="q{index}" data-cat="{e(category)}" data-search="{e(search)}">
      <div class="card-top"><span class="pill">{e(category)}</span><span class="number">#{index:02}</span></div>
      <h2>{e(title)}</h2>
      <p class="answer">{e(answer)}</p>
      <div class="followup"><strong>老师追问时补充</strong><br>{e(followup)}</div>
      <div class="source"><span>源码：</span><a href="{e(local_url)}" target="_blank">{e(path)}</a><span class="line">第 {first}–{last} 行</span>
        <button class="copy" data-copy="{e(path)}:{first}">复制位置</button>
        <a class="github" href="{e(github_url)}" target="_blank" rel="noopener">GitHub 定位 ↗</a>
      </div>
      <details><summary>查看这一段原代码 <span>{first}–{last} 行</span></summary><pre><code>{e(code_excerpt(path, first, last))}</code></pre></details>
    </article>"""


HTML = """<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>E · 组织导入与数据库模型｜源码答辩速查</title>
<style>
:root{font-family:Inter,"Microsoft YaHei",system-ui,sans-serif;color:#192b40;background:#f4f7fb}
*{box-sizing:border-box}html{scroll-behavior:smooth}body{margin:0}
.layout{display:grid;grid-template-columns:282px minmax(0,1fr);min-height:100vh}
aside{position:sticky;top:0;height:100vh;overflow:auto;background:#eaf0f7;border-right:1px solid #d8e2ee;padding:24px 18px}
.brand{font-weight:800;font-size:21px;line-height:1.35;color:#173f6b}.sub{color:#61758c;font-size:12px;margin:8px 0 24px}
.search{width:100%;padding:12px 13px;border:1px solid #c4d3e5;border-radius:8px;background:#fff;font:inherit;outline:none}
.search:focus{border-color:#3383c9;box-shadow:0 0 0 3px #3383c92b}
.hint{font-size:12px;color:#71839b;margin:8px 0 14px}.cats{display:flex;gap:6px;flex-wrap:wrap;margin-bottom:18px}
.cat{border:1px solid #cad7e6;background:#fff;color:#315473;border-radius:999px;padding:6px 9px;cursor:pointer;font-size:12px}
.cat.active{background:#215c99;color:#fff;border-color:#215c99}.count{font-size:12px;color:#61758c;margin-bottom:8px}
.nav a{display:block;color:#315473;text-decoration:none;font-size:12px;line-height:1.45;padding:6px 8px;border-radius:6px}
.nav a:hover{background:#dce8f5}.nav .group{font-weight:800;color:#173f6b;margin:12px 0 3px;font-size:12px}
.tip{background:#fff;border:1px solid #d5e0eb;border-radius:9px;padding:11px;font-size:12px;line-height:1.6;color:#587087;margin-top:20px}
main{max-width:1160px;width:100%;margin:0 auto;padding:30px 32px 90px}
.hero{background:linear-gradient(120deg,#174b82,#2f75ad);border-radius:14px;padding:30px;color:#fff;box-shadow:0 8px 30px #174b821a}
.hero h1{margin:0 0 10px;font-size:30px}.hero p{margin:5px 0;line-height:1.7;color:#e9f2fc}
.meta{font-size:12px;opacity:.9}.notice{border-left:4px solid #dfaa48;background:#fff8e8;color:#6b5123;border-radius:4px;margin:20px 0;padding:13px 16px;line-height:1.7;font-size:14px}
.flow{background:#fff;border:1px solid #dae4ef;border-radius:10px;margin:20px 0;padding:18px 20px}
.flow h2{margin:0 0 10px;font-size:17px}.flow p{margin:6px 0;line-height:1.65}
.section{font-size:20px;color:#174b82;margin:35px 0 14px;border-bottom:1px solid #ccd9e8;padding-bottom:9px}
.card{scroll-margin-top:16px;background:#fff;border:1px solid #d9e3ef;border-radius:12px;margin:0 0 16px;padding:22px 26px;box-shadow:0 3px 14px #23466c0a}
.card-top{display:flex;justify-content:space-between}.pill{background:#e8f2ff;color:#23578b;border-radius:99px;padding:4px 9px;font-size:11px;font-weight:700}.number{color:#a5b4c5;font-size:12px}
.card h2{font-size:19px;margin:12px 0}.answer{font-size:15px;line-height:1.85;margin:0 0 16px}
.followup{background:#edf7f4;border-left:3px solid #18806c;color:#31574f;padding:11px 13px;line-height:1.75;font-size:13px;border-radius:3px}
.source{display:flex;align-items:center;gap:8px;flex-wrap:wrap;font-size:12px;color:#6a7f95;margin:17px 0 12px;overflow-wrap:anywhere}
a{color:#2065a2}.source a{font-weight:650}.source .line{white-space:nowrap}
.copy{background:#f4f8fc;border:1px solid #cad9e9;border-radius:5px;color:#315473;padding:4px 7px;cursor:pointer}.copy:hover{background:#e5f0fc}
details{border:1px solid #d8e4f1;border-radius:8px;overflow:hidden}summary{background:#edf3fa;color:#29527a;padding:11px 14px;cursor:pointer;font-size:13px;font-weight:650}summary span{float:right;font-weight:400}
pre{background:#152234;color:#dfecf9;overflow:auto;margin:0;padding:17px;font-family:"Cascadia Code",Consolas,monospace;font-size:12px;line-height:1.6}
.empty{padding:40px;text-align:center;background:#fff;border-radius:10px;color:#647a90}
.footer{color:#7b8da0;font-size:12px;line-height:1.7;margin-top:30px}
@media(max-width:760px){.layout{display:block}aside{position:relative;height:auto}.nav{max-height:180px;overflow:auto}main{padding:18px 14px}.hero{padding:22px}.hero h1{font-size:24px}.card{padding:18px}.source{display:block}.source>*{margin-right:6px}}
@media print{aside,.copy,.github{display:none!important}.layout{display:block}main{max-width:none;padding:0}.card{break-inside:avoid;box-shadow:none}details{display:none}.hero{color:#000;background:#fff;border:1px solid #999}}
</style>
</head>
<body>
<div class="layout">
<aside>
  <div class="brand">E · 源码答辩速查</div>
  <div class="sub">组织导入 · 数据库模型 · 权限清单</div>
  <input id="search" class="search" type="search" placeholder="问法、方法名、文件名…" autocomplete="off" aria-label="搜索讲解">
  <div class="hint">按 / 聚焦搜索，Esc 清空。离线可用。</div>
  <div id="cats" class="cats"></div><div id="count" class="count"></div>
  <nav id="nav" class="nav" aria-label="讲解目录"></nav>
  <div class="tip"><strong>答辩时怎么用</strong><br>先用搜索定位问题，读“30 秒讲法”，被追问再展开代码。要在 IDEA 定位：复制路径，打开文件后用 Ctrl+G 输入行号。</div>
</aside>
<main>
  <header class="hero"><h1>E · 组织导入与数据库模型</h1><p>按老师可能的提问找源码、讲功能、解释边界。每条均嵌入当前仓库的原代码，可直接离线查看。</p><p class="meta">基线：827ca45 · 仅作答辩导航；静态审查不等于测试通过</p></header>
  <div class="notice">先区分事实与待确认：网页里标 HR-002/003/004、E-04/05/06 的问题来自代码静态审查，尚未在隔离环境完成运行复现。老师问“有没有测过”，应如实说“已核对代码路径，集成测试待运行”。</div>
  <section class="flow"><h2>记住这条主线</h2><p><strong>请求：</strong>OrgView → orgApi → PermissionInterceptor → CatalogController → OrgService / ImportService → Mapper → MySQL。</p><p><strong>授权：</strong>用户 → 角色指派（含部门作用范围）→ 角色 → 权限；导入组织数据不自动指派角色。</p><p><strong>库表：</strong>公司、部门、用户是一组；系统、模块、资源、操作、权限是另一组；角色权限与用户角色把两组接到鉴权流程。</p></section>
  <div id="content">__CARDS__</div><div id="empty" class="empty" hidden>没有匹配项。试试“部门”“权限”“V4”“异常”“导入”。</div>
  <p class="footer">生成方式：python human-review/build-e-guide.py。网页内代码片段是生成时的源码快照；若小组继续修改源码，请重新生成并核对行号。详细问题与状态见 E.md 和 problems/E/E-review-20261026.md。</p>
</main></div>
<script>
const cards=[...document.querySelectorAll('.card')], search=document.querySelector('#search'), cats=document.querySelector('#cats'), nav=document.querySelector('#nav'), count=document.querySelector('#count'), empty=document.querySelector('#empty');
const groups=[...new Set(cards.map(c=>c.dataset.cat))]; let active='全部';
function render(){
 const q=search.value.trim().toLocaleLowerCase('zh-CN');
 const visible=cards.filter(c=>(active==='全部'||c.dataset.cat===active)&&(!q||c.dataset.search.includes(q)));
 cards.forEach(c=>c.hidden=!visible.includes(c));
 document.querySelectorAll('.section').forEach(s=>s.hidden=!visible.some(c=>c.dataset.cat===s.dataset.cat));
 count.textContent='显示 '+visible.length+' / '+cards.length+' 条讲解'; empty.hidden=visible.length>0;
 cats.innerHTML=['全部',...groups].map(g=>'<button class="cat '+(g===active?'active':'')+'" data-cat="'+g+'">'+g+'</button>').join('');
 nav.innerHTML=groups.map(g=>{const subset=visible.filter(c=>c.dataset.cat===g);return subset.length?'<div class="group">'+g+'</div>'+subset.map(c=>'<a href="#'+c.id+'">'+c.querySelector('h2').textContent+'</a>').join(''):''}).join('');
}
search.addEventListener('input',render); cats.addEventListener('click',e=>{if(e.target.matches('.cat')){active=e.target.dataset.cat;render()}});
document.addEventListener('keydown',e=>{if(e.key==='/'&&document.activeElement!==search){e.preventDefault();search.focus()}if(e.key==='Escape'&&document.activeElement===search){search.value='';render();search.blur()}});
document.addEventListener('click',async e=>{const b=e.target.closest('.copy');if(!b)return;try{await navigator.clipboard.writeText(b.dataset.copy);b.textContent='已复制';setTimeout(()=>b.textContent='复制位置',1200)}catch{b.textContent='请手动复制路径'}});
render();
</script>
</body></html>"""


def main() -> None:
    last = None
    parts = []
    for i, row in enumerate(CARDS, 1):
        if row[0] != last:
            last = row[0]
            parts.append(f'<h2 class="section" data-cat="{e(last)}">{e(last)}</h2>')
        parts.append(render_card(i, row))
    OUT.write_text(HTML.replace("__CARDS__", "\n".join(parts)), encoding="utf-8")
    print(f"已生成 {OUT}，{len(CARDS)} 条讲解")


if __name__ == "__main__":
    main()
