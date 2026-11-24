# Docker Swarm 课程代理部署复现

本指南用于复现仓库现有 Compose 的课程代理路径。这里的命令部署六项服务，课程请求经 DDD 鉴权后转发。当前云端设计采用[七服务拓扑和课程业务直达课程后端的路线](deployment.adoc)。既有云端版本的上线结果见[云端验收记录](course-swarm-alignment.md)。

## 1. 准备构建和部署环境

- 在 Swarm manager 执行 `docker node ls`，确认参与调度的节点为 `Ready` / `Active`。为 MySQL 和 Redis 选择数据节点，其余应用交给 Swarm 调度。
- 准备 JDK 21、Node.js 22、Git、Docker、OpenSSL 和所有节点可访问的镜像仓库。在 manager 登录仓库，部署时使用 `--with-registry-auth`。
- 准备两个前端入口及对应的 TLS 证书。外部网络只需向授权来源开放权限前端和课程前端的 HTTPS 端口。

构建节点拉取基础镜像受限时，可以在能访问镜像仓库的机器按目标架构执行 `docker pull --platform` 和 `docker save`，核对归档 SHA-256 后在构建节点执行 `docker load`。目标架构以部署节点为准。

## 2. 构建并发布镜像

在仓库根目录运行。将示例仓库地址和版本改为本次发布值，镜像前缀保留末尾的 `/`。

```bash
export REGISTRY='registry.example.org/team/'
export TAG="$(git rev-parse --short HEAD)"
export MODULE=rbac-ddd

bash tools/install-clonefactory.sh
bash mvnw -B -ntp -pl rbac-ddd -am package
(cd rbac-web && npm ci && npm run typecheck && npm test)

bash tools/prepare-course.sh --original
COURSE_BACKEND=.cache/course-source/original/backend
bash mvnw -B -ntp -s course-integration/maven-settings.xml \
  -gs course-integration/maven-settings.xml -f "$COURSE_BACKEND/pom.xml" package

docker build --build-arg MODULE=rbac-ddd -t "${REGISTRY}rbac-ddd:$TAG" .
docker build -t "${REGISTRY}rbac-web:$TAG" rbac-web
docker build -f deploy/full-course-backend.Dockerfile -t "${REGISTRY}course-backend:$TAG" "$COURSE_BACKEND"
docker build -f deploy/full-course-web.Dockerfile -t "${REGISTRY}course-web:$TAG" .
for image in rbac-ddd rbac-web course-backend course-web; do
  docker push "${REGISTRY}$image:$TAG"
done
```

DDD 集成测试需要[专用 MySQL 和 Redis 测试实例](test-plan.adoc)。教师后端以 [`UPSTREAM.lock`](../course-integration/UPSTREAM.lock) 的提交为准，使用默认运行模式。预期四个镜像构建成功，镜像仓库返回各自的 digest。记录应用镜像和 MySQL、Redis 镜像的 digest，在部署副本中把 `image` 固定为 `仓库/镜像@sha256:…`。

## 3. 配置并发布 Swarm 栈

1. 把 [`docker-compose.yml`](../docker-compose.yml) 复制到仓库根目录下的部署副本，在副本中固定六个镜像的 digest。若已有专用 overlay 网络，把 `networks.private` 的定义改为 `external: true` 和 `name: <现有网络名>`。用 `docker network inspect` 核对网络属于当前 Swarm。
2. 给数据节点设置专用标签，例如 `docker node update --label-add rbac.data=true <数据节点>`。将 MySQL 原有的 manager 约束替换为 `node.labels.rbac.data == true`，给 Redis 添加相同约束。四项应用服务可使用 `node.role == worker` 约束及资源预留，由 Swarm 调度。
3. 在 manager 的受控目录准备数据库口令、JWT 密钥与服务密钥，定好 `RUNTIME_DIR`，按清单的文件名供 Swarm secrets 读取。可用 `bash deploy/full-secrets.sh` 生成初始文件，再将 `tls.crt` 和 `tls.key` 换成匹配实际访问域名或 IP SAN 的服务证书和私钥。数据库使用新卷时，初始化脚本创建 `rbac_center` 和 `rbac_course` 两个独立库。复用已有卷前核对 Flyway 历史、应用账号及备份。
4. 设定 `JWT_ISSUER` 为权限中心实际 HTTPS 入口。保留 Swarm 服务名 `rbac-backend` 和 `course-backend`，让 `IDENTITY_BASE_URL` 与 `COURSE_UPSTREAM` 分别指向这两个服务的内部 HTTP 地址。课程边缘将 `/login` 与 `/api/login` 转给课程后端，其余课程业务 API 转给 DDD。MySQL、Redis 和两个后端无需发布宿主机端口。
5. 以 manager 运行以下命令。示例栈名可按环境修改。

```bash
docker stack deploy --with-registry-auth --resolve-image never \
  -c docker-compose.swarm.yml rbac-cloud
docker stack services rbac-cloud
docker stack ps --no-trunc rbac-cloud
```

预期六项服务均为 `1/1`，MySQL 与 Redis 位于数据节点。核对任务使用的镜像 digest、两库 Flyway 迁移及 Redis 的 `PONG`。复用外部网络时，检查部署前后网络 ID 一致。替换旧栈时，先确认旧写入已停止，并在新栈验收后按约定处理旧数据。

## 4. 验证 HTTPS 与浏览器信任

推荐使用受客户端信任的证书。临时联调可使用专用 CA 签发带正确 SAN 的服务证书，并对测试进程提供公有 CA。仓库附有[既有联调环境的公有 CA](certs/rbac-cloud-ca.crt)。它适用于由该 CA 签发的服务证书，新环境应使用自己的证书。公有 CA 的证书指纹如下，可用 `openssl x509 -in <文件> -noout -sha256 -fingerprint` 核对。

```text
799A76D7D32AD5B361051E332412904C56FEF82210E0FC2DE4D96CD0FCF6D450
```

```bash
curl --cacert /path/to/ca.crt --fail https://rbac.example.org/api/v1/actuator/health
curl --cacert /path/to/ca.crt --fail https://course.example.org/course/
```

需要用 Windows 浏览器联调时，可在确认指纹后把公有 CA 临时加入当前用户的证书库。联调完成后按证书指纹移除。

```powershell
$ca = (Resolve-Path '.\docs\certs\rbac-cloud-ca.crt').Path
$cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($ca)
certutil.exe -user -addstore Root $ca
# 联调结束
certutil.exe -user -delstore Root $cert.Thumbprint
```

## 5. 联调验收

- 权限中心首页和课程中心 `/course/` 返回 `200`，JS 和 CSS 正常加载，权限中心的健康接口返回 `UP`。
- 在权限中心建立合成教师、学生账号和相应角色。在课程库绑定两人的稳定 `subject` 并添加最小课程数据。两种身份均能登录并读取获授权的课程。
- 匿名请求课程 API 返回 `401`，学生尝试教师专属操作返回 `403`。两个公开入口的 `/integration/` 和 `/identity/` 私有接口均被拒绝。
