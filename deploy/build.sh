#!/usr/bin/env bash
# 编译服务器上执行：测试 → 打 jar → 打镜像 →（可选）推到华为云 SWR。对应实验二第三部分。
#
# 用法（在仓库根目录）：
#   bash deploy/build.sh          # 只在本机打镜像（单机部署用）
#   bash deploy/build.sh push     # 推到 .env 里 REGISTRY 指定的 SWR 组织（集群部署用）
#
# 推送前先登录 SWR：控制台 → 容器镜像服务 SWR → 总览 →「登录指令」，复制整条 docker login 执行。
# 临时登录指令有效期 24 小时，过期后 push 和 stack.sh 的 --with-registry-auth 都会报 unauthorized。
set -euo pipefail
cd "$(dirname "$0")/.."

# .env 里的 REGISTRY / TAG / BASE_IMAGE；命令行上临时给的环境变量优先
if [ -f .env ]; then
  _registry="${REGISTRY-}"; _tag="${TAG-}"; _base="${BASE_IMAGE-}"
  set -a; . ./.env; set +a
  REGISTRY="${_registry:-${REGISTRY-}}"; TAG="${_tag:-${TAG-}}"; BASE_IMAGE="${_base:-${BASE_IMAGE-}}"
fi
TAG="${TAG:-$(git rev-parse --short HEAD)}"
REGISTRY="${REGISTRY:-}"
BASE_IMAGE="${BASE_IMAGE:-eclipse-temurin:21-jre}"

if [ "${1:-}" = "push" ] && [ -z "$REGISTRY" ]; then
  echo "push 需要 REGISTRY，例如 REGISTRY=swr.cn-north-4.myhuaweicloud.com/<组织名>/（写进 .env）" >&2
  exit 1
fi

echo "==> 1/3 编译 + 全部测试 + Jacoco 门槛（任何一步失败都不会出镜像）"
./mvnw -B -s deploy/maven-settings-huawei.xml clean verify

echo "==> 2/3 打镜像，标签 $TAG 与 latest，基础镜像 $BASE_IMAGE"
for m in rbac-structured rbac-mock-biz; do
  docker build --build-arg BASE_IMAGE="$BASE_IMAGE" --build-arg MODULE="$m" \
    -t "${REGISTRY}${m}:${TAG}" -t "${REGISTRY}${m}:latest" .
done

if [ "${1:-}" = "push" ]; then
  echo "==> 3/3 推送到 ${REGISTRY}"
  for m in rbac-structured rbac-mock-biz; do
    docker push "${REGISTRY}${m}:${TAG}"
    docker push "${REGISTRY}${m}:latest"
  done
  echo "部署这个版本：TAG=$TAG bash deploy/stack.sh up"
else
  echo "==> 3/3 跳过推送（单机部署直接 docker compose up -d）"
fi

echo "Jacoco 报告：rbac-structured/target/site/jacoco/index.html"
