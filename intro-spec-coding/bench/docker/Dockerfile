# One image for both stages:
#   agent stage     — fresh workspace copy, no host config, no hidden tests
#   evaluator stage — frozen workspace + hidden tests mounted read-only
# Built from debian:stable-slim + the official Node tarball (via npmmirror) because pulling
# node images from Docker Hub was too slow. The Claude Code tarballs are fetched on the host
# first (docker/fetch-vendor.sh) because the native binaries timed out inside the build.
# On the machine used for the experiment the image was actually produced by build-image.sh +
# install-agents.sh (run + commit), which performs the same steps.
FROM debian:stable-slim

ARG NODE_VERSION=24.8.0
ARG NODE_MIRROR=https://registry.npmmirror.com/-/binary/node
ARG NPM_REGISTRY=https://registry.npmmirror.com
ARG APT_MIRROR=mirrors.huaweicloud.com

RUN sed -i "s|deb.debian.org|${APT_MIRROR}|g" /etc/apt/sources.list.d/debian.sources \
    && apt-get update && apt-get install -y --no-install-recommends git ca-certificates curl xz-utils ripgrep procps \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL "${NODE_MIRROR}/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" | tar -xJ -C /usr/local --strip-components=1 \
    && npm config set --global registry ${NPM_REGISTRY} \
    && npm config set --global fetch-retries 5 \
    && npm config set --global fetch-timeout 600000 \
    && useradd -m -s /bin/bash node

RUN npm install -g @fission-ai/openspec@1.14.1 && npm cache clean --force

COPY docker/vendor/ /vendor/
COPY docker/install-agents.sh /tmp/install-agents.sh
RUN npm install -g /vendor/anthropic-ai-claude-code-linux-x64-2.1.295.tgz /vendor/anthropic-ai-claude-code-2.1.295.tgz --force \
    && bash /tmp/install-agents.sh \
    && (cd /usr/local/lib/node_modules/opencode-ai && node postinstall.mjs) \
    && rm -rf /vendor /tmp/install-agents.sh && npm cache clean --force && claude --version

# Workspace dependencies are installed once; each run links them in.
COPY base/package.json /opt/deps/package.json
RUN cd /opt/deps && npm install --no-audit --no-fund && chown -R node:node /opt/deps

USER node
ENV DISABLE_AUTOUPDATER=1 \
    DISABLE_TELEMETRY=1 \
    DISABLE_ERROR_REPORTING=1 \
    CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1 \
    NODE_NO_WARNINGS=1
RUN git config --global user.email bench@local && git config --global user.name bench \
    && git config --global init.defaultBranch main
WORKDIR /work
