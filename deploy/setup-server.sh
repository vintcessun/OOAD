#!/usr/bin/env bash
# 华为云服务器初始化：git、docker，编译服务器另装 JDK 21.0.2 与 Maven 3.9.9。
# 全部走华为云镜像。系统自带源里的 JDK 版本太老，所以 JDK 与 Maven 直接下载压缩包装到 /opt。
#
# 用法（root 执行）：
#   bash deploy/setup-server.sh build     # 编译服务器：git + docker + JDK + Maven
#   bash deploy/setup-server.sh deploy    # 部署服务器：git + docker
#
# ⚠ 国庆期间老师会发配置视频和脚本，届时以老师的为准；本脚本只是先把版本定下来。
set -euo pipefail

ROLE="${1:-deploy}"
JDK_VERSION=21.0.2
MAVEN_VERSION=3.9.9
MIRROR=https://mirrors.huaweicloud.com

install_pkg() {
  if command -v dnf >/dev/null 2>&1; then dnf install -y "$@"
  elif command -v yum >/dev/null 2>&1; then yum install -y "$@"
  else apt-get update && apt-get install -y "$@"
  fi
}

echo "==> git / curl / tar"
install_pkg git curl tar

echo "==> docker"
if ! command -v docker >/dev/null 2>&1; then
  if command -v apt-get >/dev/null 2>&1; then
    install_pkg docker.io docker-compose-v2 || install_pkg docker.io
  else
    # CentOS / EulerOS：用华为云的 docker-ce 源
    curl -fsSL "$MIRROR/docker-ce/linux/centos/docker-ce.repo" -o /etc/yum.repos.d/docker-ce.repo
    sed -i "s#https://download.docker.com#$MIRROR/docker-ce#g" /etc/yum.repos.d/docker-ce.repo
    install_pkg docker-ce docker-ce-cli containerd.io docker-compose-plugin
  fi
fi
systemctl enable --now docker
echo "   镜像加速：华为云控制台 → 容器镜像服务 SWR → 镜像资源 → 镜像中心 → 镜像加速器，"
echo "   把给出的地址写进 /etc/docker/daemon.json 的 registry-mirrors 后 systemctl restart docker"

if [ "$ROLE" = "build" ]; then
  echo "==> JDK $JDK_VERSION"
  if [ ! -d "/opt/jdk-$JDK_VERSION" ]; then
    curl -fsSL "$MIRROR/openjdk/$JDK_VERSION/openjdk-${JDK_VERSION}_linux-x64_bin.tar.gz" | tar -xz -C /opt
  fi
  echo "==> Maven $MAVEN_VERSION"
  if [ ! -d "/opt/apache-maven-$MAVEN_VERSION" ]; then
    curl -fsSL "$MIRROR/apache/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz" | tar -xz -C /opt
  fi
  cat > /etc/profile.d/rbac-build.sh <<EOF
export JAVA_HOME=/opt/jdk-$JDK_VERSION
export MAVEN_HOME=/opt/apache-maven-$MAVEN_VERSION
export PATH=\$JAVA_HOME/bin:\$MAVEN_HOME/bin:\$PATH
EOF
  # shellcheck disable=SC1091
  . /etc/profile.d/rbac-build.sh
  java -version
  mvn -v
fi

echo "==> 完成（$ROLE）。重新登录一次 shell 让 PATH 生效。"
