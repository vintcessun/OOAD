# 架构总览与目录地图

> 阅读基线：2026-11-19；Git HEAD：`57deb4a1cf91da197f3f30687839afca8bf7b815`。本文来自当前源码、固定教师 API 和数据库迁移的静态分析。本次未运行业务测试或部署，接口存在不等于本轮验收通过。

![运行时架构](assets/architecture-runtime.svg)

![模块与双后端](assets/architecture-modules.svg)

## 请求与依赖

- 两个业务系统：统一身份与权限中心、课程项目管理系统。权限中心有两套替代实现；选择一个部署为 rbac-backend。
- 根 Maven reactor 只有 contract、structured、ddd、gateway 四模块；rbac-web、课程前端使用 npm；准备后的教师后端单独构建。
- 中心管理链：浏览器 → rbac-web/Nginx → rbac-backend → MySQL/Redis。中心自身也受权限控制。
- 课程业务链：浏览器 → course-web/Nginx → rbac-gateway → course-backend → rbac_course。身份接口由 Nginx 转向权限中心。
- 网关从真实 method/path 解析 operationId 后查询中心，不接受客户端声明操作身份，不允许任意上游地址。
- 课程 OperationGate 再次校验中心准入并装载真实 authorities，继续执行教师 Service 对课程、班级、小组的对象级权限判断。
- 单 aud 的身份 JWT 不携带角色权限；权限变更由权威数据和授权版本控制，不能等 JWT 过期才撤权。
- 两后端各自实现 L1/L2 缓存；读取 MySQL 授权版本，TTL 只负责旧键回收；Redis 失败可回源，权威数据库失败不凭旧缓存放行。
- 本地敏感变更、授权版本和 SUCCESS 审计同事务；拒绝记录独立事务。课程数据库与中心审计之间没有分布式原子事务。
- Nginx 公开边缘拒绝私有服务接口并清除服务密钥头；课程后端私下调用时仍须用户 Bearer 和服务密钥。

## 目录职责

| 路径 | 职责 |
| --- | --- |
| rbac-contract/ | 协议、DDL、资源目录；不含共享授权算法 |
| rbac-structured/ | Controller → Service → Mapper；规则在服务过程 |
| rbac-ddd/ | 接口 → 应用 → 领域/端口；基础设施实现端口 |
| rbac-gateway/ | 独立鉴权转发入口，固定课程上游 |
| rbac-web/ | 权限管理前端；菜单与按钮按 UI 权限展示 |
| course-integration/ | 固定教师源码的 patches/overlay、接入前端、测试 |
| tools/ | 契约校验、CloneFactory 安装、教师准备、共享验收、制图 |
| deploy/ | full-* 完整构建、Swarm、切换、密钥与验证工具 |
| .github/workflows/ | CI：真库、后端、前端、浏览器、课程与图核对 |
| docs/ | 当前契约、双设计、验收记录、答辩和历史 |
| diagrams/ | 一期 StarUML 保留；本目录 phase2 为新总览 |
| data/raw/ | 原始资料；非自动加载、非当前导入接口依据 |
| .tmp/ | 本地原始上下文；不参与构建运行 |
| .codeartsdoer/、.idea/ | 本地工具/IDE 辅助；不是运行服务 |
| pom.xml / .mvn / mvnw* | 四模块聚合、依赖/插件管理与 Maven Wrapper |
| Dockerfile / docker-compose.yml | Java 运行镜像与完整七服务拓扑 |

## 相对 RBAC0 的变化

| 一期 | 当前 |
| --- | --- |
| 单套结构化与模拟业务模块 | 双后端、独立网关、真实课程接入 |
| 角色无系统归属、没有继承图 | 角色属于系统，DAG 继承计算有效权限 |
| 多公司/作用域/旧资源模型 | 单公司、唯一部门、准入规则、UI/API 独立 |
| 旧 V1–V4 数据库 | 新空库十五表 V1/V2；不承诺旧库直接升级 |
| 旧文档和静态图 | 契约、双设计、自动制图和可追溯验证 |
| intro-spec-coding / human-review / mock | 导论迁出、旧审查退出当前工程、真实课程替代 mock |

## 注意文档年代与真实限制

- 一期 `diagrams/phase1/rbac.mdj` 及 export 明确属于历史材料；不覆盖它们。
- `docs/design/` 是现行详细设计主要入口；`docs/diagrams/` 包含早期迭代图。
- `data/raw/README.md` 的导入操作属于一期，当前 OAS 没有对应导入 API。
- `docs/iteration-log.adoc` 的 M2 骨架状态只描述当时，不代表现在 DDD 仍为空壳。
- `deploy/build.sh` 发现 full-build.sh 后转入完整流程；不要仅按旧分支理解现行部署。
- 课程前端是实际联调界面，不能将高层课程用例图解释为这套前端已覆盖教师全部业务 UI。
- 两版缓存 TTL/技术不同；相同外部契约不要求内部实现一致。

## 关键源码入口

- [rbac-contract/src/main/resources/openapi/rbac-api.yaml](../../rbac-contract/src/main/resources/openapi/rbac-api.yaml)
- [rbac-contract/src/main/resources/catalog/course-openapi.source.yaml](../../rbac-contract/src/main/resources/catalog/course-openapi.source.yaml)
- [rbac-contract/src/main/resources/db/migration/V1__rbac1_schema.sql](../../rbac-contract/src/main/resources/db/migration/V1__rbac1_schema.sql)
- [rbac-contract/src/main/resources/db/migration/V7__drop_role_identity_marker.sql](../../rbac-contract/src/main/resources/db/migration/V7__drop_role_identity_marker.sql)
- [docker-compose.yml](../../docker-compose.yml)
- [rbac-web/nginx.conf](../../rbac-web/nginx.conf)
- [deploy/full-course-nginx.conf](../../deploy/full-course-nginx.conf)
- [course-integration/UPSTREAM.lock](../../course-integration/UPSTREAM.lock)
- [rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/controller/AuthController.java)
- [rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java](../../rbac-structured/src/main/java/com/xmu/rbac/structured/service/ManagementService.java)
- [rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/RoleHierarchy.java](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/RoleHierarchy.java)
- [rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java](../../rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/AuthorizationPolicy.java)
- [rbac-gateway/src/main/java/com/xmu/rbac/gateway/GatewayController.java](../../rbac-gateway/src/main/java/com/xmu/rbac/gateway/GatewayController.java)
- [course-integration/overlay/backend/src/main/java/cn/edu/xmu/projectmgt/integration/OperationGate.java](../../course-integration/overlay/backend/src/main/java/cn/edu/xmu/projectmgt/integration/OperationGate.java)
