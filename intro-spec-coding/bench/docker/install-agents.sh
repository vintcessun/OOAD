#!/usr/bin/env bash
# Runs as root inside the image build container; installs Codex and OpenCode offline from /vendor.
# The linux-x64 binaries are published as separate packages (Codex via an npm alias), so they are
# unpacked by hand next to the launcher packages, where the launchers look for them.
set -euxo pipefail
npm install -g --offline /vendor/openai-codex-0.162.0.tgz --force
mkdir -p /usr/local/lib/node_modules/@openai/codex/node_modules/@openai/codex-linux-x64
tar -xzf /vendor/openai-codex-0.162.0-linux-x64.tgz --strip-components=1 \
  -C /usr/local/lib/node_modules/@openai/codex/node_modules/@openai/codex-linux-x64

npm install -g --offline /vendor/opencode-ai-1.18.35.tgz --force --ignore-scripts
mkdir -p /usr/local/lib/node_modules/opencode-linux-x64
tar -xzf /vendor/opencode-linux-x64-1.18.35.tgz --strip-components=1 -C /usr/local/lib/node_modules/opencode-linux-x64
chmod +x /usr/local/lib/node_modules/opencode-linux-x64/bin/* || true

# Codex model catalog for DeepSeek, extracted from DeepSeek's official setup script
# (cdn.deepseek.com/api-docs/codex-deepseek-setup.sh). The provider and key are written at run time.
mkdir -p /opt/codex && cp /vendor/codex-models.json /opt/codex/models.json

codex --version
opencode --version
