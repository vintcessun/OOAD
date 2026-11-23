#!/usr/bin/env bash
set -Eeuo pipefail
cleanup() {
  result=$?
  for name in "${test_mysql:-}" "${test_redis:-}"; do
    [[ -z "$name" ]] || docker stop "$name" >/dev/null 2>&1 || true
  done
  if [[ -n ${ROOT:-} ]]; then
    rm -f -- "$ROOT/source/rbac-web/.cloud.Dockerfile" "$ROOT/source/.cloud-course.Dockerfile"
  fi
  echo "BUILD_EXIT=$result"
}
trap cleanup EXIT
umask 077
export JAVA_HOME=/root/jdk-21.0.2
export PATH="$JAVA_HOME/bin:$PATH"
export MAVEN_OPTS="-Xms96m -Xmx600m -XX:ActiveProcessorCount=2"
export NODE_OPTIONS=--max-old-space-size=512
ROOT=/root/rbac1-cloud-3a05cf1
cd "$ROOT"
if [ ! -d source/.git ]; then git init -q source; fi
cd source
if ! git cat-file -e '3a05cf1^{commit}' 2>/dev/null; then
 if [ -f "$ROOT/source.bundle" ]; then
  git fetch "$ROOT/source.bundle" refs/remotes/origin/main
 else
  timeout 90 git fetch --depth=1 https://github.com/vintcessun/OOAD.git refs/heads/main
 fi
fi
git checkout --detach 3a05cf1
git rev-parse HEAD
ddd_jar=rbac-ddd/target/rbac-ddd.jar
course_jar=.cache/course-source/original/backend/target/course-project-backend-0.0.1-SNAPSHOT.jar
if [[ ${1:-} == --resume-web ]]; then
  sha256sum -c "$ROOT/java-artifacts.sha256"
else
bash tools/install-clonefactory.sh
test_mysql=
test_redis=
if ss -lntH | awk '{print $4}' | grep -Eq ':(13306|16379)$'; then
 echo 'Dedicated test ports are already in use'; exit 1
fi
export TEST_DB_PASSWORD="$(openssl rand -hex 32)"
export JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=25.0 -XX:ActiveProcessorCount=2"
export MYSQL_ROOT_PASSWORD="$TEST_DB_PASSWORD"
docker run -d --rm --name rbac1-cloud-build-mysql -p 127.0.0.1:13306:3306 --memory 448m \
 -e MYSQL_ROOT_PASSWORD -e MYSQL_ROOT_HOST=% -e MYSQL_DATABASE=rbac1_ddd_test mysql:8.0 \
 --innodb-buffer-pool-size=48M --max-connections=30 --performance-schema=OFF >/dev/null
test_mysql=rbac1-cloud-build-mysql
unset MYSQL_ROOT_PASSWORD
docker run -d --rm --name rbac1-cloud-build-redis -p 127.0.0.1:16379:6379 --memory 96m redis:7-alpine >/dev/null
test_redis=rbac1-cloud-build-redis
for retry in $(seq 1 90); do
 if docker exec "$test_mysql" sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -Nse "SELECT 1" rbac1_ddd_test' >/dev/null 2>&1; then break; fi
 sleep 2
done
docker exec "$test_mysql" sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -h127.0.0.1 -uroot -Nse "SELECT 1" rbac1_ddd_test'
docker exec "$test_redis" redis-cli ping
bash mvnw -B -ntp -s deploy/maven-settings-huawei.xml -pl rbac-ddd -am clean package
docker stop "$test_mysql" "$test_redis" >/dev/null
test_mysql=
test_redis=
unset TEST_DB_PASSWORD JAVA_TOOL_OPTIONS
bash tools/prepare-course.sh --original
bash mvnw -B -ntp -s course-integration/maven-settings.xml -gs course-integration/maven-settings.xml -f .cache/course-source/original/backend/pom.xml clean package -DskipTests
sha256sum "$ddd_jar" "$course_jar" > "$ROOT/java-artifacts.sha256"
fi
REG=swr.cn-north-4.myhuaweicloud.com/egret-hill
TAG=rbac1-cloud-3a05cf1
export DOCKER_BUILDKIT=0
# Only registry hosts are normalized in the image build. npm ci still verifies each locked integrity hash.
python3 - <<'PY'
from pathlib import Path

install = r'''ARG CLOUD_NPM_REGISTRY=https://mirrors.huaweicloud.com/repository/npm
RUN sed -E -i "s#https://registry\.(npmjs\.org|npmmirror\.com)#${CLOUD_NPM_REGISTRY}#g" package-lock.json \
    && npm ci --no-audit --no-fund --registry="${CLOUD_NPM_REGISTRY}" --maxsockets=8
'''
for template, output, start in (
    ('rbac-web/Dockerfile', 'rbac-web/.cloud.Dockerfile', 'ARG NPM_REGISTRY='),
    ('deploy/full-course-web.Dockerfile', '.cloud-course.Dockerfile', 'RUN npm ci '),
):
    text = Path(template).read_text()
    begin = text.index(start)
    end = text.index('\nCOPY ', begin)
    Path(output).write_text(text[:begin] + install.rstrip() + text[end:])
PY
docker build --build-arg MODULE=rbac-ddd -t "$REG/rbac-ddd:$TAG" .
docker build -f rbac-web/.cloud.Dockerfile --target build -t "rbac1-cloud-rbac-web-check:$TAG" rbac-web
docker run --rm "rbac1-cloud-rbac-web-check:$TAG" npm run typecheck
docker run --rm "rbac1-cloud-rbac-web-check:$TAG" npm test -- --run
docker build -f rbac-web/.cloud.Dockerfile -t "$REG/rbac-web:$TAG" rbac-web
docker build -f deploy/full-course-backend.Dockerfile -t "$REG/course-backend:$TAG" .cache/course-source/original/backend
docker build -f .cloud-course.Dockerfile -t "$REG/course-web:$TAG" .
for image in rbac-ddd rbac-web course-backend course-web; do docker push "$REG/$image:$TAG"; done
for image in rbac-ddd rbac-web course-backend course-web; do docker image inspect "$REG/$image:$TAG" --format '{{range .RepoDigests}}{{println .}}{{end}}' | grep -F "$REG/$image@"; done
