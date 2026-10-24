#!/usr/bin/env bash
# Server-only tests/build/publication. Usage: build.sh [build|push|push-only]
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/env-build.sh
mode="${1:-build}"
case "$mode" in build|push|push-only) ;; *) echo "Usage: build.sh [build|push|push-only]" >&2; exit 2 ;; esac
vars=(REGISTRY TAG BASE_IMAGE NODE_IMAGE NGINX_IMAGE MYSQL_SOURCE_IMAGE REDIS_SOURCE_IMAGE MYSQL_IMAGE REDIS_IMAGE)
declare -A overrides=()
for name in "${vars[@]}"; do overrides[$name]="${!name-}"; done
if [ -f .env ]; then set -a; source ./.env; set +a; fi
for name in "${vars[@]}"; do
  if [ -n "${overrides[$name]}" ]; then printf -v "$name" '%s' "${overrides[$name]}"; fi
done
REGISTRY="${REGISTRY:-}"
BASE_IMAGE="${BASE_IMAGE:-eclipse-temurin:21-jre}"
NODE_IMAGE="${NODE_IMAGE:-node:20-alpine}"
NGINX_IMAGE="${NGINX_IMAGE:-nginx:1.27-alpine}"
MYSQL_SOURCE_IMAGE="${MYSQL_SOURCE_IMAGE:-mysql:8.0}"
REDIS_SOURCE_IMAGE="${REDIS_SOURCE_IMAGE:-redis:7-alpine}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
if [ -n "$REGISTRY" ] && [[ "$REGISTRY" != */ ]]; then echo "REGISTRY must end with /" >&2; exit 1; fi
if [ "$mode" != build ]; then
  [ -n "$REGISTRY" ] || { echo "push requires REGISTRY" >&2; exit 1; }
  for image in "$MYSQL_IMAGE" "$REDIS_IMAGE"; do
    [[ "$image" == "$REGISTRY"* ]] || { echo "Runtime image must use REGISTRY: $image" >&2; exit 1; }
  done
fi
if [ "$mode" = push-only ]; then
  [ -f target/deploy-release.env ] || { echo "No completed build record" >&2; exit 1; }
  saved_registry="$(source target/deploy-release.env; printf '%s' "$REGISTRY")"
  [ "$saved_registry" = "$REGISTRY" ] || { echo "Registry differs from build record" >&2; exit 1; }
  saved_tag="$(source target/deploy-release.env; printf '%s' "$TAG")"
  [ -z "${TAG:-}" ] || [ "$TAG" = "$saved_tag" ] || { echo "TAG differs from build record" >&2; exit 1; }
  TAG="$saved_tag"
else
  for image in "$BASE_IMAGE" "$NODE_IMAGE" "$NGINX_IMAGE" "$MYSQL_SOURCE_IMAGE" "$REDIS_SOURCE_IMAGE"; do
    docker image inspect "$image" >/dev/null || { echo "Import missing base: $image" >&2; exit 1; }
  done
  if [ -z "${TAG:-}" ]; then
    TAG="$(git rev-parse --short HEAD)"
    if [ -n "$(git status --porcelain -- pom.xml .mvn mvnw Dockerfile docker-compose.yml rbac-contract rbac-structured rbac-web deploy)" ]; then
      TAG="$TAG-release-$(date -u +%Y%m%d%H%M%S)-$$"
    fi
  fi
fi
[[ "$TAG" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$ ]] || { echo "Invalid TAG" >&2; exit 1; }
if [ "$mode" != push-only ]; then
  export MAVEN_OPTS="${MAVEN_OPTS:--Xmx256m}"
  export JAVA_TOOL_OPTIONS="-Xmx512m"
  # Keep production credentials out of fixed test-profile fixtures.
  env -u RBAC_INITIAL_PASSWORD -u RBAC_JWT_SECRET -u DB_PASSWORD -u MYSQL_ROOT_PASSWORD \
    ./mvnw -B -s deploy/maven-settings-huawei.xml -pl rbac-structured -am -DreuseForks=false -DforkCount=1 clean verify
  if [ "${STOP_TEST_DEPS_AFTER_VERIFY:-0}" = 1 ]; then docker stop rbac-test-mysql rbac-test-redis; fi
  unset JAVA_TOOL_OPTIONS
  export DOCKER_BUILDKIT=0
  docker build --pull=false --build-arg BASE_IMAGE="$BASE_IMAGE" --build-arg MODULE=rbac-structured \
    -t "${REGISTRY}rbac-structured:$TAG" .
  docker build --pull=false --memory=1100m --target build --build-arg NODE_IMAGE="$NODE_IMAGE" --build-arg NGINX_IMAGE="$NGINX_IMAGE" \
    -t "rbac-web-test-stage:$TAG" rbac-web
  mkdir -p target/frontend-coverage
  artifact_container="$(docker create "rbac-web-test-stage:$TAG")"
  trap 'docker rm "$artifact_container" >/dev/null 2>&1 || true' EXIT
  docker cp "$artifact_container:/web/coverage/." target/frontend-coverage/
  docker rm "$artifact_container" >/dev/null
  trap - EXIT
  docker build --pull=false --memory=1100m --build-arg NODE_IMAGE="$NODE_IMAGE" --build-arg NGINX_IMAGE="$NGINX_IMAGE" \
    -t "${REGISTRY}rbac-web:$TAG" rbac-web
  printf 'TAG=%q\nREGISTRY=%q\nPUSHED=false\n' "$TAG" "$REGISTRY" > target/deploy-release.env
  docker image inspect "${REGISTRY}rbac-structured:$TAG" "${REGISTRY}rbac-web:$TAG" > target/built-images.json
fi
if [ "$mode" != build ]; then
  # Verify push-only still refers to the exact two successfully built images.
  python3 - "$REGISTRY" "$TAG" <<'PY'
import json,subprocess,sys
saved={x['Id'] for x in json.load(open('target/built-images.json'))}
refs=[sys.argv[1]+n+':'+sys.argv[2] for n in ('rbac-structured','rbac-web')]
actual={x['Id'] for x in json.loads(subprocess.check_output(['docker','image','inspect']+refs).decode())}
assert actual==saved, 'Local image IDs differ from completed build'
PY
  docker tag "$MYSQL_SOURCE_IMAGE" "$MYSQL_IMAGE"
  docker tag "$REDIS_SOURCE_IMAGE" "$REDIS_IMAGE"
  images=("$MYSQL_IMAGE" "$REDIS_IMAGE" "${REGISTRY}rbac-structured:$TAG" "${REGISTRY}rbac-web:$TAG")
  for image in "${images[@]}"; do docker push "$image"; done
  docker image inspect "${images[@]}" > target/published-images.json
  python3 - <<'PY'
import json
items=json.load(open('target/published-images.json'))
assert len(items)==4 and all(x.get('RepoDigests') for x in items), 'Missing published digests'
PY
  printf 'TAG=%q\nREGISTRY=%q\nPUSHED=true\n' "$TAG" "$REGISTRY" > target/deploy-release.env
  echo "Published four runtime images for $TAG"
fi
echo "Release: target/deploy-release.env; backend: rbac-structured/target/site/jacoco/; frontend: target/frontend-coverage/"
