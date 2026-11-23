# RBAC 统一身份与权限中心

厦门大学 OOAD 课程设计：**结构化 + 独立 DDD 两套 RBAC1 后端**，共用严格契约和同一 Vue3 权限中心。权限中心校验课程操作权限，再转发到老师原版课程后端。

> **此前版本的本地交付验收记录**：188项后端JUnit、双后端25+25及T21共51项真库验收、38项前端单测、PC/手机4例、教师原440项与真实课程12例。两轮七服务本地Swarm验证完成，最终DDD。完整范围、hash和未执行的外部事项见[最终验收](docs/final-acceptance.adoc)，不把本地通过冒充外部服务器或GitHub CI成功。

**重构优先于迁移和兼容。** 单公司、用户唯一部门；部门用于组织管理和角色准入，不表示课程业务数据范围。同系统角色继承 DAG；UI/API 独立授权及显式映射；同步审计。课程对象级权限仍由真实课程 Service 执行，不做 RBAC2/3、热力图或旧协议兼容层。

## 文档入口

| 文档 | 用途 |
|---|---|
| [需求与追踪](docs/requirements.adoc) / [完整输入](docs/sources/requirements-final.md) | 需求 ID、范围及验收定义 |
| [实现合同](docs/implementation-contract.adoc) / [数据库合同](docs/schema-contract.adoc) | 两实现共同遵守的行为与十五表新基线 |
| [课程集成合同](docs/course-integration-contract.adoc) | 教师身份/审计接口、真实 CC 与服务端边界 |
| [结构化设计](docs/architecture-structured.adoc) / [DDD 设计](docs/architecture-ddd.adoc) | 实际分层、事务与独立领域模型 |
| [最终设计与 UML](docs/final-design.adoc) / [答辩](docs/defense.adoc) | 六组对应类图/顺序图、图源与源码索引 |
| [六人口试审查分工](docs/oral-review/README.adoc) | 全员共同任务、A–F任务卡、交叉口试、迭代复现与只读Git索引 |
| [数据库](docs/database.adoc) / [API](docs/api.adoc) | 当前唯一契约、种子及安全边界 |
| [最终验收](docs/final-acceptance.adoc) / [测试证据](docs/test-plan.adoc) / [共享验收](docs/acceptance-plan.adoc) | 真实结果、双实例、故障注入及行为等价 |
| [桌面前端验收](docs/acceptance/frontend-redesign.adoc) / [历史浏览器截图](docs/acceptance/screenshots/README.adoc) | 双后端 Chromium、逐路由视觉复核和角色继承演示 |
| [课程接入](course-integration/README.md) / [CloneFactory](docs/clonefactory.adoc) | 固定源码、安装及本轮验收入口 |
| [部署](docs/deployment.adoc) / [部署证据](deploy/deployment-evidence.json) | 隔离 Swarm、TLS、秘密、真实镜像与验收限制 |
| [基线](docs/migration-baseline.adoc) / [迭代记录](docs/iteration-log.adoc) | 起始版本、保全备份及真实阶段结果 |

## 工程

```text
rbac-contract/      唯一 OpenAPI、V1–V5、课程 80 操作目录；不含共享业务实现
rbac-structured/    Controller → 无状态 Service → Spring Data/JPA/Redis Mapper
rbac-ddd/           独立纯JDK领域、应用编排、端口、JPA/Redis适配器及REST
rbac-course-proxy/  课程路由匹配、请求限额和固定上游转发
rbac-gateway/       旧独立网关，供已有测试和兼容运行
rbac-web/           同一 Vue3/TypeScript 权限中心，支持选择任一后端
course-integration/ 固定教师源码的补丁/覆盖层、真实课程前端及测试
                                   （不复制完整上游仓库或真实人员名单）
deploy/            同一服务名选择后端、六服务隔离部署和验证工具
docs/design/       当前可维护图源、SVG/PNG与源码/方法索引
tools/             合同生成、CloneFactory、共享真库验收和设计核验
```

权限中心均为 Java21 / Spring Boot3.3.5 / JPA / MySQL8 / Redis7。教师工程保留其固定版本和 MIT 许可，不伪装为我方重新实现的课程业务。
共享契约目前为 **68 个中心操作、80 个课程操作、15 张表**。两个私有服务接口仅供受信服务调用。

非RBAC导论实验已迁入独立私有仓库 [vintcessun/intro-spec-coding](https://github.com/vintcessun/intro-spec-coding)。原四条历史及1176个文件的初始 tree `29c24ee631060a8d208350ad65ac2b294b25ce95` 已与远端核验；后续提交 `381e498` 修正独立说明和凭据忽略规则。原目录退出主仓库，被忽略的私有资产保全在仓库外。

## 本地验证

要求 **显式 JDK21**、Node22+、Python3、Docker；wrapper提供 Maven3.9.9。绘图另需 Graphviz。

**下列命令使用专用 loopback 测试实例。Flyway 会将 RBAC1 空库初始化到 V7，并按顺序升级已应用本项目较早迁移的库。**
先检查13306/16379及容器名称没有被其它用途占用；下列命令不用于重建已有实例：

```bash
docker run -d --name rbac-phase2-test-mysql -p 127.0.0.1:13306:3306 \
  -e MYSQL_ROOT_PASSWORD=root mysql:8.0
docker run -d --name rbac-phase2-test-redis -p 127.0.0.1:16379:6379 redis:7-alpine
# 等健康就绪，并设置 JAVA_HOME 为 JDK21；测试密码仅限这两个专用实例。
bash tools/install-clonefactory.sh
# Python 合同校验依赖 PyYAML/openapi-spec-validator，详见工具文件头。
python tools/generate-rbac1-contract.py --check
python tools/generate-rbac1-contract-test.py
node --test tools/*.test.mjs

# 正常跑JUnit并打包；外部HTTP覆盖收集后才执行原覆盖率门槛。
./mvnw -B -s deploy/maven-settings-huawei.xml clean package
source deploy/full-jacoco-agent.sh -s deploy/maven-settings-huawei.xml
export ACCEPTANCE_DB_PASSWORD='<专用测试实例凭据>'
(cd tools/acceptance && npm ci && npm test && npm run acceptance)
./mvnw -B -s deploy/maven-settings-huawei.xml verify -DskipTests

cd rbac-web
npm ci
npm run typecheck
npm run test:coverage
npm run build
E2E_MODULE=rbac-structured npm run e2e
E2E_MODULE=rbac-ddd npm run e2e
```

shared HTTP 使用本次唯一新库、私有临时 jar 快照和自有 Redis namespace，不清空共享实例。覆盖率经官方 JaCoCo TCP dump 后与JUnit合并，**不降低门槛、不扩大排除**；中间不能clean。Windows需 Git Bash / `mvnw.cmd`，Playwright可通过 `PW_CHROMIUM` 指定本机 Chrome。

前端开发默认代理structured8081；`RBAC_API=http://127.0.0.1:8082 npm run dev` 可连接DDD。真实课程准备与复现见 `course-integration/`，不能把stub边界测试当作两中心真实联调。

## 部署与安全

`MODULE=rbac-structured|rbac-ddd` 选择一个中心后端。六服务包括中心前后端、课程前后端、MySQL 和 Redis。课程边缘将 `/api/*` 交给权限中心鉴权转发，课程登录直接交给课程后端。只在两边缘发布 TLS。

本机从 IDEA 运行时，在权限中心的本地配置中设 `course.upstream=http://127.0.0.1:8084`，课程前端代理把业务 `/api/*` 指到权限中心（DDD 示例端口 `8083`）。课程后端的 `IDENTITY_BASE_URL` 指向同一个权限中心。Docker 构建使用 `tools/prepare-course.sh --original --build` 生成未应用接入补丁的课程后端。

#### 服务器 DDD 与本机课程项目联调

服务器单独部署权限中心时，使用 [DDD 服务器 Docker Compose](deploy/ddd-server.compose.yml)，配置和启动步骤见 [服务器部署手册](docs/ddd-server.md)。

在多节点集群部署权限前后端与教师课程中心时，见 [Swarm 部署指南](docs/swarm-deployment.md)。

- 在课程前端的 IDEA npm 运行配置或 `.env.development.local` 设置 `VITE_RBAC_PROXY_TARGET` 为服务器 DDD 的 HTTPS 入口。默认本机课程登录仍走 `http://127.0.0.1:8084`，可用 `VITE_COURSE_PROXY_TARGET` 覆盖。老师前端如果是新克隆的，需先配置本地 Vite 代理读取这两个变量，将 `/api/login` 改写为 `/login` 发给本地课程后端，其他 `/api/*` 和 `/auth/*` 交给 DDD。OOAD 自带工作台直接使用 `/login`。
- 老师原版后端在本地运行，将 `IDENTITY_BASE_URL` 设为同一个服务器入口，并继续使用本地课程数据库。服务器权限中心数据库需创建与本地课程名单对应的学生和教师账号，分配课程角色并核对已有的 `subject` 绑定。
- 服务器 DDD 的 `COURSE_UPSTREAM` 设为 **DDD 进程或容器可访问的本地课程后端地址**，例如通过 VPN 提供的私网地址。服务器与本地电脑之间需要双向可达。服务器上的 `127.0.0.1:8084` 指向服务器自己。

运行密钥、数据库口令及首次bootstrap密码由被忽略的运行目录和Swarm secrets提供；没有固定公开root密码。bootstrap只用于未初始化root；应在首次使用前改密，改密会失效旧令牌。本地自签TLS不等于公网可信证书。

- [OpenAPI](rbac-contract/src/main/resources/openapi/rbac-api.yaml) 是唯一接口事实来源；外部 `/auth/*` 保留教师裸响应。
- `/integration/*`、`/identity/*` 仅用于可信服务，需 bearer + 服务密钥，公共边缘拒绝访问；服务密钥不能进入前端。
- 原始员工表只留在被忽略的 [data/raw](data/raw/README.md)，不提交、不打镜像。
- 旧版文档、审查运行目录和演示代码由Git历史保存，不恢复多公司或业务scope模型。
- 用户提供的37页PDF是一期RBAC0资料；未收到的新版评分原件/课堂PPT以及外部服务器条件不冒称已验收。
- 部署工具不自动删除旧服务或数据卷；本地Swarm证据不能冒充外部生产部署。
- [依赖安全核验](docs/dependency-security.adoc)：GitHub实际告警78→1个开发依赖medium，high/critical为0；npm仍计5个dev-only moderate，无兼容修复，明确保留而非假清零。
