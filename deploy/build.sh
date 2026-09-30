#!/usr/bin/env bash
# 编译服务器上执行：测试 → 打 jar → 打镜像 →（可选）推到镜像仓库。
#
# 用法（在仓库根目录）：
#   bash deploy/build.sh                      # 只在本机打镜像（单机部署用）
#   REGISTRY=192.168.0.10:5000/ bash deploy/build.sh push   # 推到私有仓库（Swarm 部署用）
set -euo pipefail
cd "$(dirname "$0")/.."

TAG="${TAG:-$(git rev-parse --short HEAD)}"
REGISTRY="${REGISTRY:-}"

echo "==> 1/3 编译 + 全部测试 + Jacoco 门槛（任何一步失败都不会出镜像）"
./mvnw -B -s deploy/maven-settings-huawei.xml clean verify

echo "==> 2/3 打镜像，标签 $TAG 与 latest"
for m in rbac-structured rbac-mock-biz; do
  docker build --build-arg MODULE="$m" -t "${REGISTRY}${m}:${TAG}" -t "${REGISTRY}${m}:latest" .
done

if [ "${1:-}" = "push" ]; then
  echo "==> 3/3 推送到 ${REGISTRY}"
  for m in rbac-structured rbac-mock-biz; do
    docker push "${REGISTRY}${m}:${TAG}"
    docker push "${REGISTRY}${m}:latest"
  done
else
  echo "==> 3/3 跳过推送（单机部署直接 docker compose up -d）"
fi

echo "Jacoco 报告：rbac-structured/target/site/jacoco/index.html"
