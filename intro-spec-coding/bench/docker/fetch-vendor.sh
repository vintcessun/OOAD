#!/usr/bin/env bash
# Downloads everything the image needs on the host (docker/vendor/ is gitignored);
# build-image.sh then installs offline inside the container.
set -euo pipefail
cd "$(dirname "$0")"
REG=https://registry.npmmirror.com
mkdir -p vendor && cd vendor
curl -fsSLO https://registry.npmmirror.com/-/binary/node/v24.8.0/node-v24.8.0-linux-x64.tar.xz
npm pack @anthropic-ai/claude-code@2.1.295 @anthropic-ai/claude-code-linux-x64@2.1.295 --registry $REG
rm -rf global deps && mkdir -p global deps
npm install -g --prefix "$(pwd -W 2>/dev/null || pwd)/global" --os=linux --cpu=x64 @fission-ai/openspec@1.14.1 --registry $REG
cp ../../base/package.json deps/
(cd deps && npm install --os=linux --cpu=x64 --libc=glibc --no-audit --no-fund --registry $REG)
npm pack @openai/codex@0.162.0 @openai/codex@0.162.0-linux-x64 opencode-ai@1.18.35 opencode-linux-x64@1.18.35 --registry $REG
# Codex model catalog for DeepSeek: the JSON embedded in DeepSeek's official setup script
curl -fsSL https://cdn.deepseek.com/api-docs/codex-deepseek-setup.sh \
  | awk "/<<'CODEX_MODELS_JSON'/{f=1;next}/^CODEX_MODELS_JSON/{f=0}f" > codex-models.json
