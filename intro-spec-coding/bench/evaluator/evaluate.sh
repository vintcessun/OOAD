#!/usr/bin/env bash
# Runs inside the evaluator container.
#   /frozen   the workspace exactly as the agent left it (read-only)
#   /bench    base public tests + hidden tests (read-only)
#   /out      results
# Never touches /frozen: everything runs on a private copy.
set -u
git config --global --add safe.directory '*'
mkdir -p /tmp/eval && cd /tmp/eval
tar -C /frozen --exclude=node_modules --exclude=.git -cf - . | tar -xf -
ln -s /opt/deps/node_modules node_modules

# Diff against the base tag, including new untracked files and anything the agent committed.
mkdir -p /tmp/repo && tar -C /frozen --exclude=node_modules -cf - . | tar -C /tmp/repo -xf -
git -C /tmp/repo add -A >/dev/null 2>&1
git -C /tmp/repo diff --cached --numstat base > /out/numstat.txt 2>/dev/null
git -C /tmp/repo diff --cached base > /out/diff.patch 2>/dev/null

# 1. Build = type check with the project's own tsconfig.
npx tsc --noEmit -p tsconfig.json > /out/tsc.txt 2>&1
echo $? > /out/tsc.exit

# 2. The agent's own tests under tests/ ("test quality"). Runs before the public/hidden test
#    folders exist, because vitest's path filter is a substring match ("tests" would match them).
npx vitest run tests --reporter=json --outputFile=/out/agent-tests.json > /out/agent-tests.log 2>&1

# 3. Public tests: the ORIGINAL public test file, not whatever the agent left in tests/.
mkdir -p public-tests && cp /bench/base/tests/orders.test.ts public-tests/
# 4. Hidden tests.
mkdir -p hidden-tests && cp /bench/evaluator/hidden-tests/*.ts hidden-tests/
# Keep vitest from also picking up the agent's own config tweaks.
cp /bench/base/vitest.config.ts vitest.config.ts

npx vitest run public-tests --reporter=json --outputFile=/out/public.json > /out/public.log 2>&1
npx vitest run hidden-tests --reporter=json --outputFile=/out/hidden.json > /out/hidden.log 2>&1

# 5. Was anything the agent must not touch modified?
{
  cmp -s /frozen/TASK.md /task.md && echo "task_md_intact=1" || echo "task_md_intact=0"
  cmp -s /frozen/tests/orders.test.ts /bench/base/tests/orders.test.ts && echo "public_tests_intact=1" || echo "public_tests_intact=0"
  cmp -s /frozen/src/db.ts /bench/base/src/db.ts && echo "schema_intact=1" || echo "schema_intact=0"
  cmp -s /frozen/package.json /bench/base/package.json && echo "package_json_intact=1" || echo "package_json_intact=0"
} > /out/integrity.txt
exit 0
