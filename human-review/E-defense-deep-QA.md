# E 组员答辩追问：数据库 ER、API 与源码

适用范围：第一次检查的 **RBAC0 结构化实现**，重点为 E 负责的组织导入和数据库模型。下述“现有代码”按仓库本地版本核对；测试结论另看测试报告，不能把静态阅读说成已运行验证。答题时可按“**需求 → 设计 → 代码位置 → 结果/边界**”组织语言。

现场在 IDEA 中可用 `Ctrl+Shift+N` 按文件名打开，再用 `Ctrl+G` 输入行号：

| 老师问到 | 快速定位 |
| --- | --- |
| 四个 E 接口入口 | `CatalogController.java:128–158` |
| 公司列表、可见部门树 | `OrgService.java:39–85` |
| 组织导入总流程 | `ImportService.java:140–169` |
| 建部门树 | `ImportService.java:173–236` |
| 员工归属与复用 | `ImportService.java:248–319` |
| 负责人关联 | `ImportService.java:325–356` |
| 权限清单导入 | `ImportService.java:369–535` |
| 删除同步、解析单元格 | `ImportService.java:543–603` |
| 工作簿读取与表头 | `ImportService.java:681–729` |
| 管理范围与授权版本 | `AuthorizationService.java:209–244` |
| 当前数据库迁移 | `V1__rbac0_baseline.sql` → `V2__grant_version.sql` → `V3__reserve_internal_user_ids.sql` → `V4__drop_user_expiry.sql` |

## 一、先看 ER 图：为什么有这些实体

### 1. 老师：ER 图一共有多少个实体？为什么不是只画用户、角色、权限三张表？

**建议回答：** 当前实际迁移共建 14 张表：V1 有 13 张，V2 增加 `sys_grant_version`。用户、角色、权限只是 RBAC0 的抽象概念，落地时还要保存多对多指派、资源与操作、组织关系、菜单和审计。可分成四组记：组织与人（`sys_company`、`sys_department`、`sys_user`）；资源与权限（`sys_software_system`、`sys_function_module`、`sys_resource`、`sys_action`、`sys_permission`）；授权关系（`sys_role`、`sys_role_permission`、`sys_user_role`）；菜单、审计和版本（`sys_menu`、`sys_audit_log`、`sys_grant_version`）。菜单独立成表，因为页面导航的层级、路由与显示顺序不等同于权限定义；是否看得到叶子菜单再关联相应资源的 `view` 权限。这样既能回答“用户能做什么”，也能回答“对哪个部门的数据做”。

**定位：** [数据库说明 §2](../docs/03-database.md)、[V1 建表](../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql)、[V2 版本表](../rbac-contract/src/main/resources/db/migration/V2__grant_version.sql)。

### 2. 老师：为什么要单独建公司和部门？把公司当成根部门不行吗？

**建议回答：** 公司是多公司隔离和导入时识别企业的主体，保存唯一的 `company_code`；部门是每家公司内部的组织树，有 `company_id`、`parent_id` 和 `path`。把二者拆开，就能明确“这棵部门树属于哪家公司”，也允许不同公司都出现名为“财务部”的部门。公司列表 API 返回可见公司的编号和根部门编号，部门树 API 再根据公司编号查树。若只靠部门名称判断归属，同名部门很容易把员工或授权范围放错公司。

**追问：** V1 的 `(company_id,parent_id,dept_name)` 唯一键约束同级重名；根节点 `parent_id` 为空时，数据库唯一键本身不能可靠保证“每公司恰好一个根”，导入方法另做一次根节点检查。因此不要说这个约束完全由数据库保证。

**定位：** [V1 公司与部门表](../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql)、[OrgService.companies](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java)、[ImportService.importDepartments](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)。

### 3. 老师：`parent_id`、`path`、`level` 为什么同时存在？

**建议回答：** `parent_id` 表示直接上级，便于构造树；`path` 例如 `/3/15/27/`，表示整条祖先链，判断某部门是否在授权部门的子树中可以做路径前缀比较；`level` 用于层级展示和排序。它们有部分信息冗余，但用途不同：查询完整子树时，`path` 避免每次递归；页面建树时，`parent_id` 更直接。创建新部门时要先保存拿到 ID，再补写完整路径。

**追问：** 若以后实现“移动部门”，必须同步更新该部门和所有后代的 `path`、`level`；当前导入逻辑主要创建或复用部门，不能宣称已实现完整迁移子树。

**定位：** [V1 部门表](../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql)、[ImportService.importDepartments](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)、[DepartmentMapper.findSubtree](../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java)。

### 4. 老师：部门有层级，这是不是已经实现 RBAC1 的角色继承？

**建议回答：** 不是。部门树表达组织结构与授权适用范围；RBAC1 的角色继承是“高级角色自动拥有低级角色的权限”，本阶段没有角色继承表或继承计算。这里的角色只通过 `sys_role_permission` 拥有哪些权限，用户经 `sys_user_role` 在某个部门及其子树内持有角色。不能把“部门的父子关系”说成“角色的父子关系”。

**定位：** [数据库设计原则](../docs/03-database.md)、[V1 角色、角色权限与用户角色](../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql)。

### 5. 老师：一个员工只能属于一个部门，为什么还能在多个部门有权限？

**建议回答：** `sys_user.department_id` 表示员工的人事归属，只有一个；`sys_user_role.scope_dept_id` 表示一次角色指派在哪个部门及其下级生效，同一用户可有多条指派。因此“人在哪儿工作”和“在哪些部门能执行操作”分开建模。比如员工甲属于总公司财务部，也可以另获道路分公司上的某角色；授权范围不应靠更改其人事部门来表达。

**定位：** [V1 用户表与用户角色指派](../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql)、[数据库说明 §3.4](../docs/03-database.md)。

### 6. 老师：资源、操作、权限为什么要分三类？为什么不直接把“查看考勤”写成字符串？

**建议回答：** 资源 `sys_resource` 是功能点，例如“考勤管理”；操作 `sys_action` 是可复用的动词，例如 `view`、`edit`；权限 `sys_permission` 是“资源 × 操作”的组合，例如 `考勤管理:view`。这样同一功能点能有多个操作，角色也能只获其中一部分。权限码方便接口传输与查询，但数据库仍通过资源 ID、操作 ID 和唯一键保证这个组合有明确来源，避免只凭字符串、无法关联业务资源。

**定位：** [V1 系统、模块、资源、操作、权限表](../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql)、[数据库说明 §3.3](../docs/03-database.md)。

### 7. 老师：`sys_role_permission` 和 `sys_user_role` 为什么不是一张表？

**建议回答：** 前者回答“这个角色具有什么权限”，键是角色与权限，并记录 `self_only`；后者回答“哪个用户在哪个部门范围持有这个角色”，键是用户、角色和适用部门。若合并，每给一个用户指派角色都要复制一整套角色权限，修改角色模板也难以统一生效。拆分后，导入权限清单可以更新模板的角色权限，而用户的角色指派依旧由管理员人工完成。

**定位：** [V1 两张关联表](../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql)、[ImportService.importPermissions](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)。

### 8. 老师：为什么部门负责人既有 `manager_name` 又有 `manager_user_id`？负责人是否自动获得权限？

**建议回答：** 原始部门表给的是姓名，不保证在员工表中恰好唯一，所以先保留 `manager_name` 原始登记；若能唯一对应某个员工，再写 `manager_user_id` 便于关联展示。负责人只是组织资料，代码没有因关联负责人而插入 `sys_user_role`，所以不会自动产生管理权限。真正授权还要走人工角色指派。

**追问：** 当前 `linkManagers` 只从本轮新建员工中找负责人；重复导入改成已有员工时可能无法更新，已记为 HR-003 待复现问题。不要把这个边界隐瞒成“完全支持增量同步”。

**定位：** [V1 部门表](../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql)、[ImportService.linkManagers](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)、[E 审查问题](problems/E/E-review-20261026.md)。

### 9. 老师：为什么审计日志和授权版本表没有与所有业务表画外键？

**建议回答：** 审计记录要在用户或角色后来变化、甚至删除后仍能说明“当时谁做了什么”，所以保存操作者与目标的 ID、名称快照，不依赖业务对象继续存在。授权版本表是一个独立的技术性单行表，记录授权数据的当前版本，供缓存失效使用，不代表某个用户或角色的业务关系。二者目的分别是历史追溯和一致性控制。

**定位：** [数据库说明 ER 图注释](../docs/03-database.md)、[V2 授权版本表](../rbac-contract/src/main/resources/db/migration/V2__grant_version.sql)。

### 10. 老师：V1 有 `expires_at`，你们 ER 图里为什么有的有、有的没有？

**建议回答：** V1 是历史建表迁移，原设计中 `expires_at` 表示账号到期时间；V4 按老师要求先停用已到期而仍启用的账号，再删除这个字段。当前实际表结构应看 V1→V4 顺序执行后的结果，不是只看 V1 文件。现存可编辑 StarUML 源文件仍有旧字段，而新 Mermaid ER 已按 V4 更新，这是已记录的模型同步问题，不能说数据库还保留旧字段。

**定位：** [V4 删列迁移](../rbac-contract/src/main/resources/db/migration/V4__drop_user_expiry.sql)、[E-04 记录](problems/E/E-review-20261026.md)。

## 二、再看 API：为什么设计这些接口

### 11. 老师：E 的四个 API 分别服务什么场景？为什么拆成四个？

**建议回答：** `GET /companies` 让管理界面先选可见公司，返回 `CompanyView`；`GET /departments/tree?companyId=...` 再查该公司的可见部门树，返回 `DeptNode`；`POST /imports/organization` 接收部门和员工两份表，建立组织数据，返回 `ImportReport`；`POST /imports/permissions` 接收功能权限清单，建立资源、权限和角色模板授权，也返回 `ImportReport`。查询与导入的读写职责不同；组织表与权限清单的数据结构、校验、权限要求和副作用也不同，因此分开。对应权限分别为 `rbac:org:view`、`rbac:org:view`、`rbac:org:import`、`rbac:permission:create`。若老师问“对应资源”，先确认是指 API 返回的业务对象，还是 RBAC 权限模型里的资源：后者的 `rbac:org`、`rbac:permission` 是受保护的管理功能资源，不是公司记录或 Excel 文件本身。

**定位：** [OpenAPI 四接口](../rbac-contract/src/main/resources/openapi/rbac-api.yaml)、[CatalogController 入口](../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java)。

### 12. 老师：为什么公司列表和部门树不合成一个接口？

**建议回答：** 公司列表只返回选择器需要的少量摘要，如公司编号、编码、名称和根部门编号；部门树可能有很多层节点，且要按操作者的管理范围过滤。先列可选公司，再按 `companyId` 查对应树，可避免页面一开始加载所有公司的完整树，也使“公司不存在返回 404”和“公司存在但没有可见部门返回空数组”可以分开表达。更重要的是公司是否出现、部门节点是否可见都由服务端判定，不能只靠前端隐藏选项。

**定位：** [OrgService.companies/tree](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java)、[CatalogDtos.CompanyView/DeptNode](../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/CatalogDtos.java)。

### 13. 老师：组织导入 API 为什么一次上传两张表？请求和响应具体是什么？

**建议回答：** 员工行的部门归属依赖部门树，所以一个组织导入请求先处理 `departments`，再处理 `employees`，最后关联部门负责人。请求是 `multipart/form-data`：query 有 `companyCode`、`companyName`，文件字段名是 `departments`、`employees`。成功响应含 `counts`（创建、跳过等数量）、`notes`（处理说明）和 `anomalies`（表、行、原因、值），便于管理员审核，不能只显示“导入成功”。

**定位：** [CatalogController.importOrganization](../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java)、[ImportReport](../rbac-structured/src/main/java/com/xmu/rbac/structured/dto/ImportReport.java)、[前端 orgApi](../rbac-web/src/api/index.ts)。

### 14. 老师：为什么功能权限清单导入不是给员工直接分配角色？

**建议回答：** 清单描述的是“某类角色模板对某功能点应有哪些操作”，所以它修改 `sys_role_permission`；是否让员工持有某角色属于组织授权决策，要由管理员另行写入 `sys_user_role`。若按岗位或负责人自动分配，原始 Excel 的错误、同名或过时岗位会直接变成越权。代码末尾的组织导入说明也明确“不分配任何角色”。

**定位：** [ImportService.importOrganization](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)、[V1 两张授权表](../rbac-contract/src/main/resources/db/migration/V1__rbac0_baseline.sql)。

### 15. 老师：已有接口权限的人，是不是就能看到所有公司的组织树？

**建议回答：** 不是。拦截器先检查“能否调用 `org:view` 这类接口”；`OrgService` 再用 `AuthorizationService.scopeOf` 取得该用户持有这项权限的部门路径，只返回范围内节点及用于显示路径的祖先。祖先可见不代表有权管理祖先的其他分支。要讲清“接口操作权限”和“目标数据范围”是两道不同的检查。

**定位：** [OrgService.tree](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/OrgService.java)、[AuthorizationService.scopeOf](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java)、[组织树测试](../rbac-structured/src/test/java/com/xmu/rbac/structured/integration/ManagementApiTest.java)。

### 16. 老师：权限清单导入 API 为什么要返回异常清单，而不是遇到一处错就全部失败？

**建议回答：** 原清单可能同时有大量有效行和少量无法识别的单元格。现实现对能够定位且合法的数据继续处理，把异常放进 `anomalies` 供人工修正；空白权限格不等于明确的“无”，为避免误撤权，会保留该资源在对应模板角色上的旧授权。若功能点连系统或模块都无法定位，本次会暂停“删除同步”，以免在不知道源清单全貌时大量误回收。严重的文件错误或必填参数缺失仍会抛异常，不能概括成“任何错误都只跳过一行”。

**定位：** [ImportService.parseCell/importPermissions/revokeMissing](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)。

## 三、老师打开源码：怎样逐层讲

### 17. 老师：后端分几层？各层谁负责、为什么这样分？

**建议回答：** 这次是结构化实现，按功能流程拆成 Controller、Service、Mapper 三层。Controller 绑定 HTTP 参数并返回统一格式；Service 做业务校验、组织定位、事务和授权规则；Mapper 是 Spring Data JPA 接口，负责读写 MySQL，Redis 访问也放数据访问层。依赖从上向下，Controller 不直接操作 Mapper，Mapper 不做权限决策。这个项目的“Mapper”是层名，不意味着使用 MyBatis。具体到写库，Service 构造 `Company`、`Department`、`User` 等实体后调用 Mapper 的 `save`/`saveAll`；这些 Mapper 继承 `JpaRepository`，Spring Data JPA 提供基本持久化实现。复杂查询才用方法名、JPQL 或原生 SQL 明确写出。

**定位：** [架构说明 §2.2–2.3](../docs/02-architecture.md)、[CatalogController](../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java)、[DepartmentMapper](../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/DepartmentMapper.java)。

### 18. 老师：从网页点“导入组织与员工”后，调用链具体怎么走？要说到数据库。

**建议回答：** `OrgView.vue` 收集公司编码、名称和两个文件；`orgApi.importOrganization` 组装 `FormData` 发 POST；`PermissionInterceptor` 按契约检查身份及 `rbac:org:import`；`CatalogController.importOrganization` 取参数与文件流，调用 `ImportService.importOrganization`；Service 查找或新建公司、读工作簿、建部门树、定位员工、关联负责人；`CompanyMapper`、`DepartmentMapper`、`UserMapper` 把实体写到 `sys_company`、`sys_department`、`sys_user`。返回 `ImportReport` 后前端展示计数与异常并刷新公司及部门树。顺着这条链说，比只报类名更有说服力。

**定位：** [OrgView.vue](../rbac-web/src/views/OrgView.vue)、[前端 orgApi](../rbac-web/src/api/index.ts)、[Controller](../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/CatalogController.java)、[ImportService](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)。

### 19. 老师：`importDepartments` 为什么有 `pending`、`done` 和多轮循环？

**建议回答：** Excel 的行顺序不保证父部门一定排在孩子前。方法把未处理部门放 `pending`，每轮只处理上级已经在 `done` 的行，直到没有待处理行或一轮没有进展。若最后仍有待处理行，通常是上级缺失或形成环，列入异常而不猜一个上级。创建部门时先保存获取自增 ID，再补写 `path`。这是按依赖拓扑逐步建树，不是简单按 Excel 行号插库。

**定位：** [ImportService.importDepartments](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)。

### 20. 老师：`locate` 函数做什么？为什么不直接按部门名称查？

**建议回答：** `locate(root, child, branch, deptName)` 用员工表的“分公司 + 部门”两列定位部门。若分公司是总公司，就在根节点的直接下级找；若“部门”本身就是分公司，该员工归分公司直属；否则在指定分公司的下级找组合名或原部门名。这样“财务部”不会因为重名而落到总公司或另一个分公司的财务部。无法唯一定位时记异常，不随便取第一条。

**定位：** [ImportService.locate](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)。

### 21. 老师：`readSheet`、`parseCell`、`revokeMissing` 各自负责什么？

**建议回答：** `readSheet` 读取组织工作簿首个工作表，寻找表头、把数据行转换为文本并跳过全空行；`parseCell` 把权限清单单元格中的“查看/编辑本人”“全部”“无”等转换为操作集合和 `selfOnly`，空白或未知词返回异常；`revokeMissing` 比较原有业务授权与本次清单要求的 `wanted`，但跳过 `untouched` 保护的异常资源，然后回收不再要求的授权。讲函数时先说输入、输出，再说异常和数据库副作用。

**追问：** 组织 `readSheet` 丢掉物理行号，下游 `i+2` 可能报错行，已记为 HR-002 待复现。

**定位：** [ImportService.java](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)、[E 审查问题](problems/E/E-review-20261026.md)。

### 22. 老师：重新导入权限清单时，为什么有 `wanted`、`untouched`、`revokeMissing`？

**建议回答：** `wanted` 是新清单明确要求的权限 ID 与“只限本人”标记；`untouched` 是该角色列解析异常、不应动其旧授权的资源 ID；`revokeMissing` 执行最后的差集回收：旧授权既不在 `wanted`、所属资源也不在 `untouched`，才删除。举例：原来能查看考勤，新表写“查看”继续保留；写“无”表示明确回收；留空报异常、暂时保留。若整行找不到系统/模块，本次跳过所有删除同步。这里删除的是角色模板的**业务授权**，不碰权限中心自身的 `rbac:*` 管理权限。

**定位：** [ImportService.importPermissions/revokeMissing](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)、[RolePermissionMapper.findBusinessGrantsOfRole](../rbac-structured/src/main/java/com/xmu/rbac/structured/mapper/RolePermissionMapper.java)。

### 23. 老师：为什么权限更新后还要调用 `bumpGrantVersion`？Redis 缓存不能直接删吗？

**建议回答：** 授权数据改在 MySQL，旧授权可能已缓存在 Redis。如果只逐个删缓存键，数据库提交与删键之间、以及并发回填时容易留下短暂不一致。项目把全局授权版本号放在 MySQL 的单行表里，修改授权时在同一事务中递增；新请求先读版本号，用新版缓存键，查不到就从数据库加载。因此版本变化随事务一起提交或回滚。这里是代码设计解释；缓存下一次请求效果是否完全符合要求，仍应以集成实测为证。

**定位：** [V2 迁移](../rbac-contract/src/main/resources/db/migration/V2__grant_version.sql)、[AuthorizationService.bumpGrantVersion](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuthorizationService.java)。

### 24. 老师：`@Transactional` 是什么意思？导入有异常时到底回滚还是继续？

**建议回答：** `ImportService.importOrganization` 和 `importPermissions` 的业务写入各在一个事务中。代码**主动收集并跳过**的逐行/逐格异常不会抛出事务异常，所以有效数据可继续写入并最终提交；无法读取文件、必填参数缺失或数据库异常等被抛出的错误会使业务事务回滚。这里要区分“报告里有 `anomalies`”和“Java 方法抛异常”：前者不等于整笔失败。审计的 `AuditService.record` 用独立事务，审计记录与业务回滚的关系需单独核对，不能简单说所有表绝对一起回滚。

**定位：** [ImportService 两个导入入口](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ImportService.java)、[AuditService.record](../rbac-structured/src/main/java/com/xmu/rbac/structured/service/AuditService.java)。

### 25. 老师：现有实现有哪些明确边界？你作为 Debug 负责人发现了什么？

**建议回答：** 我们在代码静态审查中记录了三类 E 风险：组织导入跳过标题/空白行后异常报告的原始行号可能偏移（HR-002）；重复导入时负责人查找只看本轮新建员工，可能使新负责人姓名与旧用户 ID 不一致（HR-003）；同模块同名但不同义的功能点换序，依赖出现次序的资源认领可能错配授权（HR-004）。另外 V4 已删 `expires_at`，但可编辑 StarUML 旧模型未同步（E-04）；组织导入页面复开弹窗可能保留旧报告（E-05）；端到端测试有弹窗外文案的断言范围问题（E-06）。这些是**有代码路径证据的待复现或文档问题**，不能声称已全部实测、修复。

**定位：** [E 问题证据](problems/E/E-review-20261026.md)、[E 阅读状态](E.md)。

## 四、上台前只背这一条完整例子

老师若随机让你介绍一段 E 代码，可以选 **“部门与员工 Excel 导入”**：需求是把真实部门树和一万名员工导入权限中心，但同名部门、行顺序、异常数据不能导致误归属或自动越权；设计为两份文件同一入口，先建立部门再定位员工，最后关联负责人，报告逐行异常；代码从 `OrgView`、`orgApi`、`CatalogController` 进入 `ImportService.importOrganization`，经三个 Mapper 写入公司、部门、用户表；验证看导入报告、组织树和员工归属。最后主动补充：导入**不会自动分配角色**，负责人也**不会自动有管理权限**；HR-002/003 等边界仍需隔离环境复现。这样就覆盖老师问的“为什么设计、有什么实体、API 为什么存在、代码分几层、一个函数做什么、怎样连到数据库”。
