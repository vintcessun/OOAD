#!/usr/bin/env bash
set -euo pipefail
umask 077

root=/root/rbac1-cloud-3a05cf1
registry=swr.cn-north-4.myhuaweicloud.com/egret-hill
tag=rbac1-cloud-3a05cf1
export CLOUD_RUNTIME_DIR="$root/runtime"
export CLOUD_SOURCE_DIR="$root/source"
source_dir="$CLOUD_SOURCE_DIR"
manifest="$source_dir/deploy/cloud-stack.yml"

[[ $(hostname) == package ]]
[[ $(docker info --format '{{.Swarm.ControlAvailable}}') == true ]]
[[ $(docker network inspect rbac_default --format '{{.Driver}}/{{.Scope}}') == overlay/swarm ]]
network_id=$(docker network inspect rbac_default --format '{{.Id}}')
[[ $(git -C "$source_dir" rev-parse HEAD) == 3a05cf199e29fc227372043be5b6dd0f27e105ed ]]
grep -qx 'BUILD_EXIT=0' "$root/build.log"

digest() {
  local image=$1 reference
  reference=$(docker image inspect "$registry/$image:$tag" --format '{{range .RepoDigests}}{{println .}}{{end}}' |
    awk -v prefix="$registry/$image@sha256:" 'index($0,prefix)==1')
  if [[ ! $reference =~ @sha256:[0-9a-f]{64}$ || $reference == *$'\n'* ]]; then
    printf 'Expected one published digest for %s\n' "$image" >&2
    return 1
  fi
  printf '%s\n' "$reference"
}

CLOUD_DDD_IMAGE=$(digest rbac-ddd)
CLOUD_COURSE_BACKEND_IMAGE=$(digest course-backend)
CLOUD_RBAC_WEB_IMAGE=$(digest rbac-web)
CLOUD_COURSE_WEB_IMAGE=$(digest course-web)
export CLOUD_DDD_IMAGE CLOUD_COURSE_BACKEND_IMAGE CLOUD_RBAC_WEB_IMAGE CLOUD_COURSE_WEB_IMAGE
export CLOUD_MYSQL_IMAGE="$registry/ooad-mysql@sha256:24c32e2688db4ccd7f18660805a3531394559feb979eba3691c2529350fbf597"
export CLOUD_REDIS_IMAGE="$registry/ooad-redis@sha256:dcbb2bdc63df1a1682c3e2599450b60c7474019884428f207e51e951b212e7b5"

for image in "$CLOUD_DDD_IMAGE" "$CLOUD_COURSE_BACKEND_IMAGE" "$CLOUD_RBAC_WEB_IMAGE" \
  "$CLOUD_COURSE_WEB_IMAGE" "$CLOUD_MYSQL_IMAGE" "$CLOUD_REDIS_IMAGE"; do
  docker pull "$image"
done

bash "$source_dir/deploy/cloud-secrets.sh"
old_services=(rbac_mysql rbac_redis rbac_rbac-structured rbac_rbac-web)
existing=()
for service in "${old_services[@]}"; do
  if docker service inspect "$service" >/dev/null 2>&1; then
    [[ $(docker service inspect "$service" --format '{{.Spec.Mode.Replicated.Replicas}}') == 0 ]]
    [[ -z $(docker service ps "$service" --filter desired-state=running -q) ]]
    existing+=("$service")
  fi
done
if (( ${#existing[@]} )); then docker service rm "${existing[@]}"; fi
[[ $(docker network inspect rbac_default --format '{{.Id}}') == "$network_id" ]]

cd "$source_dir"
docker stack deploy -c "$manifest" --with-registry-auth --resolve-image never rbac1-cloud
[[ $(docker network inspect rbac_default --format '{{.Id}}') == "$network_id" ]]
docker stack services rbac1-cloud
