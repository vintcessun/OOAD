# 教师课程中心云端对齐

当前云端部署设计采用七服务拓扑。权限前端使用 `8443`，课程前端使用 `9443/course/`。`course-web` 将课程 `/api/*`（含登录）去掉 `/api` 前缀后直接转给 `course-backend:8080`。课程后端再通过内网 HTTP 向 DDD 权限中心查询身份、API 权限和审计。课程页面查询菜单权限及部门时，由浏览器访问权限中心的公开入口。`course-prometheus` 在私有网络 `rbac_default` 内抓取 `course-backend:8081/actuator/prometheus`。参见[部署拓扑与直连验收清单](deployment.adoc)。

## 构建

以权限中心提交 `2940dd860598356af9204cd8e858419403912487` 和教师提交 `24a4d7d303ca082a43369658b6d2067489590c37` 建立独立构建目录。用 Java 21、Maven Wrapper 和 Node 22 构建并验收。

`deploy/teacher-authorities.py` 应用于教师原版检出，保留教师的权限码注解。后端用 Swarm secret 和当前用户 Bearer 调用 DDD 的 `/integration/authorities` 和审计接口。公开当前用户响应使用 UI 权限。前端以 `/course/` 为基址并关闭 Mock。V8 迁移安装管理菜单和按钮权限，并关联课程操作。

打包后使用固定 SWR digest 发布 DDD、课程后端和课程前端。打包节点运行隔离数据验收，应用 worker 验证镜像拉取。保留服务快照及包含当前 digest 的发布清单。

## 初始化数据

从同一教师提交提取员工表和课程 SQL。员工表 SHA-256 为 `66657800a5e2e252c9d1dedfee2dbd54b694e3a5912166c2bac79c8703b20516`。课程 SQL SHA-256 为 `a0637b31cf26322cadab5ecae0a2c05dc0d541780f34b75d0d117834c672b069`。

`deploy/teacher-seed.py` 在独立 MySQL 中加载原始 SQL，输出限定范围、跨库事务导入文件。对任一冲突使用 MySQL 批处理模式立即停止。复用总部节点，按名单创建 110 个组织节点，绑定 190 名教师和 9,810 名学生。映射业务主键、选课关系及跨库 subject。生成文件含账号信息及哈希，保存在受限发布目录。交付密码仅保留验收用账号。

维护窗口先关闭入口并备份权限库和课程库，再启动候选后端完成 Flyway。成功的初次导入输出 `SEED_APPLIED`，重复执行输出 `SEED_ALREADY_PRESENT`。校验和、外键或行数不符时终止。先在内网验证新服务，再开放公网入口并复测原课程和演示账号。

## 巡检与回滚

- 携带联调 CA 查询 `/healthz` 和 `/version`，确认响应为纯文本。课程后端 `/actuator/health` 只在内网验证。
- 查询内网 Prometheus 的目标和 `up{job="course-project-backend"}`，确认连续两次抓取成功。Prometheus 固定在监控节点，日志使用容器日志轮转。
- 用课程管理员打开课程、学期、教师和日志菜单。按部门选人新增联调教师，并确认重复请求返回 `409`。用普通教师与学生验证课程列表、详情和越权拒绝。
- 开放入口前回滚时，使用维护窗口内的备份恢复两库并切回原服务配置。开放后先回切应用 digest 和探针配置，并保留新增业务数据。

## 2026-11-24 上线记录

以下为 2026 年 11 月 24 日发布版本的验收结果。

部署代码为 `44a4d75ef284ccc4606c869b761201dcc0a39cbe`。镜像使用次表所列 digest，三个新镜像均在四个应用 worker 拉取成功。`rbac_default` 网络 ID 为 `qk8xucu9ycj4d70puvp25wnmi`。

| 服务 | 节点 | Digest |
| --- | --- | --- |
| DDD | node1 | `sha256:82b812f2f20db70019014b1f516e1dbd59c269b6f50d6898455d3623437d2f91` |
| 权限前端 | log | `sha256:62c1d50ec998900bb9fcdf70d3dcf87a5b2c079c6ebd9a7833db897ea3146ef3` |
| 课程后端 | log | `sha256:e7351f1b98beee95a0059cb8fa0e3dca48fda5e3fd9c06d1b7492ab4846beb57` |
| 课程前端 | project | `sha256:c7f3507233ed17dc98f4b5141344d23be7e2c0a51383a241f7fe5ab6c7ab1bae` |
| MySQL | mysql | `sha256:24c32e2688db4ccd7f18660805a3531394559feb979eba3691c2529350fbf597` |
| Redis | mysql | `sha256:dcbb2bdc63df1a1682c3e2599450b60c7474019884428f207e51e951b212e7b5` |
| 课程 Prometheus | monitor | `sha256:6573196e5dbe834779ac4d34cb053113cc502a69e9e20ba3be96d37f33e81545` |

生产两库为 V8 和 V13。导入并核对 110 个组织节点、10,000 个账号、3 个学期、9 门课程、22 个教学班和 344 条选课，复跑未新增。选课外键无孤立引用，原课程 `619001` 已保留。MySQL 继续挂载 `rbac1-cloud_mysql-data-37e372e29be5`，Redis 返回 `PONG`。

生产切换前保存了两库逻辑备份、既有 stack 和服务配置。生产课程列表首次请求触及 DDD 的 2 秒转发超时，预热后的内网全量验收通过，随后恢复 HTTPS 入口。管理探针为 `UP`，Prometheus 连续两次抓取 `up=1`，七服务均为 `1/1`。

在权限中心 `https://49.4.114.16:8443/` 和课程中心 `https://49.4.114.16:9443/course/` 验收。隔离环境覆盖管理员四个菜单、逐级选人新增教师、重复提交和实时撤权，并演练旧 DDD 镜像使用 V8 数据库启动。

公网用联调 CA 验证了两个站点、静态资源和 DDD 健康接口。课程管理员通过网页新增了 `CLOUD-T2`，稳定 subject 绑定正确，重复提交返回 `409`。导入的教师和学生均完成网页登录、列表和课程详情验证。原教师及学生切换至 `Cloud Smoke Term` 后均在网页看到课程 `619001` 并可读取详情。匿名课程请求返回 `401`，越权请求返回 `403`，课程管理端点在公网返回 `404`。
