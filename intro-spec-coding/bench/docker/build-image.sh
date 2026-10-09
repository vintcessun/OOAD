#!/usr/bin/env bash
# Builds spec-bench:1 with `docker run` + `docker commit` instead of `docker build`.
# Same result as docker/Dockerfile. Used because on this machine (Docker Desktop on Windows)
# the BuildKit context upload kept timing out and downloads inside containers were very slow,
# so every download happens on the host first (docker/fetch-vendor.sh) and the container
# installs offline from docker/vendor/.
set -euo pipefail
export MSYS_NO_PATHCONV=1
here="$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)"
docker rm -f spec-bench-build >/dev/null 2>&1 || true
docker run --name spec-bench-build -e http_proxy= -e https_proxy= -e HTTP_PROXY= -e HTTPS_PROXY= -v "$here/vendor:/vendor:ro" debian:stable-slim bash -euxc '
    sed -i "s|deb.debian.org|mirrors.huaweicloud.com|g" /etc/apt/sources.list.d/debian.sources
    apt-get update && apt-get install -y --no-install-recommends git ca-certificates ripgrep procps xz-utils
    rm -rf /var/lib/apt/lists/*
    tar -xJf /vendor/node-v24.8.0-linux-x64.tar.xz -C /usr/local --strip-components=1
    npm install -g --offline /vendor/anthropic-ai-claude-code-linux-x64-2.1.295.tgz /vendor/anthropic-ai-claude-code-2.1.295.tgz --force
    mkdir -p /usr/local/lib/node_modules/@fission-ai
    cp -r /vendor/global/node_modules/@fission-ai/openspec /usr/local/lib/node_modules/@fission-ai/
    ln -sf /usr/local/lib/node_modules/@fission-ai/openspec/bin/openspec.js /usr/local/bin/openspec
    useradd -m -s /bin/bash node
    mkdir -p /opt/deps /work && cp -r /vendor/deps/. /opt/deps/
    chown -R node:node /opt/deps /work
    su node -c "git config --global user.email bench@local && git config --global user.name bench && git config --global init.defaultBranch main"
    claude --version && openspec --version
  '
docker commit \
  -c 'USER node' -c 'WORKDIR /work' \
  -c 'ENV DISABLE_AUTOUPDATER=1 DISABLE_TELEMETRY=1 DISABLE_ERROR_REPORTING=1 CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1 NODE_NO_WARNINGS=1' \
  spec-bench-build spec-bench:1
docker rm spec-bench-build >/dev/null
# Then add Codex and OpenCode (see install-agents.sh), the same way:
docker run --name spec-bench-agents -u root -e http_proxy= -e https_proxy= -v "$here/vendor:/vendor:ro" -v "$here/install-agents.sh:/install-agents.sh:ro" spec-bench:1 \
  bash -c "bash /install-agents.sh && cd /usr/local/lib/node_modules/opencode-ai && node postinstall.mjs"
docker commit -c "USER node" spec-bench-agents spec-bench:1
docker rm spec-bench-agents >/dev/null
docker run --rm spec-bench:1 bash -lc "claude --version; codex --version; opencode --version; openspec --version"
