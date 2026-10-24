#!/usr/bin/env bash
# Manager commands: label DATA APP [APP...], check, up, ps, logs [service], down.
# Preparation uses label/check only; up/down are explicit future deployment operations.
set -euo pipefail
cd "$(dirname "$0")/.."
STACK="${STACK:-rbac}"

load_env() {
  [ -f .env ] || { echo "Missing .env; copy deploy/env.example and configure it" >&2; exit 1; }
  local override_tag="${TAG-}"
  set -a; source ./.env; set +a
  TAG="${override_tag:-${TAG-}}"
}
check_config() {
  local name value
  for name in MYSQL_ROOT_PASSWORD DB_PASSWORD; do
    value="${!name:-}"
    case "$value" in ""|*change-me*|root|rbac|123456|demouser) echo "Set a non-default $name" >&2; exit 1 ;; esac
  done
  [ "$MYSQL_ROOT_PASSWORD" != "$DB_PASSWORD" ] || { echo "Database passwords must differ" >&2; exit 1; }
  local secret="${RBAC_JWT_SECRET:-}"
  case "$secret" in *change-me*|dev-only-*) echo "Replace JWT secret" >&2; exit 1 ;; esac
  [ "${#secret}" -ge 32 ] || { echo "JWT secret requires at least 32 bytes" >&2; exit 1; }
  local initial="${RBAC_INITIAL_PASSWORD:-}"
  if [ "${#initial}" -lt 8 ] || ! [[ "$initial" =~ [A-Z] && "$initial" =~ [a-z] && "$initial" =~ [0-9] && "$initial" =~ [^a-zA-Z0-9] ]]; then
    echo "Initial employee password must include upper/lower/digit/special and be at least 8 characters" >&2; exit 1
  fi
  [[ "${REGISTRY:-}" == */ ]] || { echo "REGISTRY must be nonempty and end with /" >&2; exit 1; }
  for name in MYSQL_IMAGE REDIS_IMAGE; do
    [[ "${!name:-}" == "$REGISTRY"* ]] || { echo "$name must use REGISTRY" >&2; exit 1; }
  done
  [ "${APP_REPLICAS:-1}" = 1 ] || { echo "This deployment budget supports APP_REPLICAS=1" >&2; exit 1; }
}
check_nodes() {
  [ "$(docker info --format '{{.Swarm.ControlAvailable}}')" = true ] || { echo "Run on the Swarm manager" >&2; exit 1; }
  local role nodes node state ready
  for role in data app; do
    nodes="$(docker node ls -q --filter "node.label=rbac.role=$role")"
    ready=0
    for node in $nodes; do
      state="$(docker node inspect "$node" --format '{{.Status.State}} {{.Spec.Availability}}')"
      [ "$state" != "ready active" ] || ready=$((ready+1))
    done
    if [ "$role" = data ]; then
      [ "$ready" -eq 1 ] || { echo "Require exactly one Ready/Active data node" >&2; exit 1; }
    else
      [ "$ready" -ge 2 ] || { echo "Require at least two Ready/Active application nodes" >&2; exit 1; }
    fi
  done
}
case "${1:-}" in
  label)
    data="${2:?Usage: stack.sh label DATA APP [APP...]}"
    shift 2
    [ "$#" -ge 1 ] || { echo "Specify application node(s)" >&2; exit 2; }
    for app in "$@"; do [ "$app" != "$data" ] || { echo "Data and app nodes must differ" >&2; exit 2; }; done
    docker node update --label-add rbac.role=data "$data"
    for app in "$@"; do docker node update --label-add rbac.role=app "$app"; done
    ;;
  check)
    load_env; check_config; check_nodes
    echo "Configuration and node preflight passed: four services; no deployment executed."
    ;;
  up)
    load_env; check_config; check_nodes
    if [ -z "${TAG:-}" ]; then
      [ -f target/deploy-release.env ] || { echo "No build record; run build.sh push first" >&2; exit 1; }
      release_registry="$(source target/deploy-release.env; printf '%s' "$REGISTRY")"
      [ "$release_registry" = "$REGISTRY" ] || { echo "Registry differs from build record" >&2; exit 1; }
      pushed="$(source target/deploy-release.env; printf '%s' "${PUSHED:-false}")"
      [ "$pushed" = true ] || { echo "Build record is not marked pushed; run build.sh push" >&2; exit 1; }
      TAG="$(source target/deploy-release.env; printf '%s' "$TAG")"
    fi
    [[ "$TAG" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$ ]] || { echo "Invalid image TAG" >&2; exit 1; }
    export TAG
    docker stack deploy -c docker-compose.yml --with-registry-auth "$STACK"
    echo "Submitted four services. Check ps and HTTP health; command return does not confirm readiness."
    ;;
  ps)
    docker stack services "$STACK"
    docker stack ps "$STACK" --no-trunc
    ;;
  logs) docker service logs --tail 100 -f "${STACK}_${2:-rbac-structured}" ;;
  down)
    docker stack rm "$STACK"
    echo "Stack removal is asynchronous; persistent files/volumes are retained."
    ;;
  init) echo "Existing four-node Swarm must be reused; init is disabled." >&2; exit 2 ;;
  *) echo "Usage: stack.sh {label DATA APP [APP...]|check|up|ps|logs [service]|down}" >&2; exit 2 ;;
esac
