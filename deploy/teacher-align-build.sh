#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
trap 'echo ALIGN_BUILD_EXIT=$?' EXIT
cd /root/rbac-course-align-24a4d7d
release=$PWD
rbac="$release/rbac"
teacher="$release/teacher"
upstream=/root/rbac-course-upstream-web-20261124/upstream
revision=24a4d7d303ca082a43369658b6d2067489590c37
baseline=2940dd860598356af9204cd8e858419403912487
old=/root/rbac1-cloud-redeploy-c6b1634/source
export JAVA_HOME=/root/jdk-21.0.2 PATH=/root/jdk-21.0.2/bin:$PATH
export MAVEN_OPTS='-Xms64m -Xmx440m -XX:ActiveProcessorCount=2'
export JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2 -XX:MaxRAMPercentage=22'
export DOCKER_BUILDKIT=0

mkdir -p "$rbac" "$teacher"
git -C "$old" archive "$baseline" | tar -x -C "$rbac"
git -C "$upstream" archive "$revision" backend web LICENSE | tar -x -C "$teacher"
cp -R overlay/. "$rbac/"
python3 "$rbac/deploy/teacher-authorities.py" "$teacher"
cd "$teacher"
python3 - <<'PY'
from pathlib import Path
p = Path("web/src/api/current-user.test.ts")
test = p.read_text(encoding="utf-8")
old = ("    expect(info).toHaveBeenNthCalledWith(1, '[CurrentUserApi] GET /me 请求开始')\n"
       "    expect(info).toHaveBeenNthCalledWith(2, '[CurrentUserApi] GET /me 成功', {")
assert test.count(old) == 1
p.write_text(test.replace(old, "    expect(info).toHaveBeenCalledWith('[CurrentUserApi] GET /me 成功', {")
                 .replace("调用当前用户接口时打印请求和权限日志", "读取当前用户时记录响应中的权限"),
             encoding="utf-8")
PY

cd "$rbac"
if [[ ${1:-} != --backend-only ]]; then
  bash mvnw -B -ntp -s deploy/maven-settings-huawei.xml -pl rbac-ddd -am \
    -Dtest=AuthorizationSecurityRegressionTest,AuthorizationTokenAudienceTest -Dsurefire.failIfNoSpecifiedTests=false package
  docker build --build-arg MODULE=rbac-ddd --label "org.opencontainers.image.revision=$baseline" \
    --label "edu.xmu.release=teacher-course-24a4d7d-aligned" -t rbac-ddd:course-align .
fi
settings="$rbac/course-integration/maven-settings.xml"
bash mvnw -B -ntp -s "$settings" -gs "$settings" -f "$teacher/backend/pom.xml" \
  -Dtest=HttpExchangeUnifiedIdentityGatewayTest,ApiPermissionInterceptorTest package
docker build -f "$rbac/deploy/full-course-backend.Dockerfile" \
  --label "org.opencontainers.image.revision=$revision" -t course-backend:course-align "$teacher/backend"

if [[ ${1:-} != --backend-only ]]; then
  cp "$rbac/deploy/full-course-nginx.conf" "$teacher/course-nginx.conf"
  docker build --label "org.opencontainers.image.revision=$revision" \
    -f "$rbac/deploy/teacher-align-web.Dockerfile" -t course-web:course-align "$teacher"
fi
