#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

[[ $(hostname) == package ]]
cd /root/rbac-course-align-24a4d7d
base=rbac1-cloud_
network=rbac_default
network_id=qk8xucu9ycj4d70puvp25wnmi
registry=swr.cn-north-4.myhuaweicloud.com/egret-hill
monitor_image=swr.cn-north-4.myhuaweicloud.com/oomall-javaee/prom/prometheus@sha256:6573196e5dbe834779ac4d34cb053113cc502a69e9e20ba3be96d37f33e81545
tag=teacher-24a4d7d-align
ddd="${base}rbac-backend"
rbac_web="${base}rbac-web"
backend="${base}course-backend"
web="${base}course-web"
prom="${base}course-prometheus"
config="${base}course-nginx-24a4d7d-align"
prom_config="${base}course-prometheus-24a4d7d-align"
audit_key=${base}audit-service-key-37e372e29be5

assert_network() {
    [[ $(docker network inspect "$network" --format '{{.Id}}') == "$network_id" ]]
}

image_of() {
    docker service inspect "$1" --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
}

ref() {
    local image
    read -r image < "$1.image"
    [[ "$image" == "$registry/$1@sha256:"* && ${#image} -eq $((${#registry}+${#1}+73)) ]] || return 1
    printf '%s' "$image"
}

await_replica() {
    local name=$1
    local replicas=$2
    for _ in $(seq 1 75); do
        state=$(docker service ls --filter "name=$name" --format '{{.Name}} {{.Replicas}}' |
            awk -v name="$name" '$1==name {print $2}')
        if [[ "$state" != "$replicas/$replicas" ]]; then
            sleep 2
            continue
        fi
        [[ "$replicas" == 0 ]] || return 0
        local tasks
        tasks=$(docker service ps --format '{{.CurrentState}}' "$name")
        if ! grep -Eq '^(Pending|Assigned|Accepted|Preparing|Ready|Starting|Running)' <<< "$tasks"; then
            return 0
        fi
        sleep 2
    done
    echo "Replica convergence failed for $name" >&2
    return 1
}

case "$1" in
    publish)
        grep -qx ALIGN_BUILD_EXIT=0 build.log
        grep -qx STAGE_EXIT=0 stage.log
        test -f browser-stage-passed
        test -f stage-http-passed
        for name in rbac-ddd course-backend course-web; do
            docker tag "$name:course-align" "$registry/$name:$tag"
            docker push "$registry/$name:$tag"
            docker image inspect "$registry/$name:$tag" --format '{{range .RepoDigests}}{{println .}}{{end}}' |
                awk -v prefix="$registry/$name@sha256:" 'index($0,prefix)==1' > "$name.image"
            test "$(wc -l < "$name.image")" -eq 1
            ref "$name"
            echo
        done
        for name in rbac-ddd course-backend course-web; do
            check="rbac-align-pull-$name"
            docker service create --detach=true --mode global --restart-condition none --name "$check" \
                --with-registry-auth --no-resolve-image --entrypoint /bin/true \
                --constraint 'node.role==worker' --constraint 'node.hostname!=mysql' "$(ref "$name")" >/dev/null
            for _ in $(seq 1 40); do
                completed=$(docker service ps --format '{{.Node}} {{.CurrentState}}' "$check" |
                    awk '$2 == "Complete" {print $1}' | sort -u | wc -l)
                [[ "$completed" == 4 ]] && break
                sleep 2
            done
            docker service ps --format '{{.Node}} {{.CurrentState}}' "$check"
            docker service rm "$check" >/dev/null
            [[ "$completed" == 4 ]]
        done
        docker config create "$config" overlay/deploy/full-course-nginx.conf >/dev/null
        docker config create "$prom_config" overlay/deploy/course-prometheus.yml >/dev/null
        touch swr-preflight-passed
        ;;
    freeze)
        test -f swr-preflight-passed
        assert_network
        test ! -e before-services.json
        docker service inspect "$ddd" "$backend" "$web" "$rbac_web" "${base}mysql" "${base}redis" > before-services.json
        for svc in "$ddd" "$backend" "$web" "$rbac_web"; do
            image_of "$svc" > "$svc.before-image"
            await_replica "$svc" 1
        done
        docker service scale --detach=true "$web=0" "$rbac_web=0"
        await_replica "$web" 0
        await_replica "$rbac_web" 0
        docker service scale --detach=true "$backend=0" "$ddd=0"
        await_replica "$backend" 0
        await_replica "$ddd" 0
        touch maintenance-open
        ;;
    activate-backends)
        test -f maintenance-open
        assert_network
        docker service update --detach=true --with-registry-auth --no-resolve-image \
            --image "$(ref rbac-ddd)" --replicas 1 "$ddd"
        docker service update --detach=true --with-registry-auth --no-resolve-image \
            --image "$(ref course-backend)" --replicas 1 \
            --env-add SPRING_PROFILES_ACTIVE=swarm --env-add APP_BACKEND_STRATEGY=ddd \
            --env-add LOGGING_LEVEL_CN_EDU_XMU_PROJECTMGT_API=INFO \
            --env-add LOGGING_LEVEL_CN_EDU_XMU_PROJECTMGT_SERVICE=INFO \
            --env-add LOGGING_LEVEL_CN_EDU_XMU_PROJECTMGT_INFRASTRUCTURE=INFO \
            --secret-add "source=$audit_key,target=app.identity.authorities-service-key" \
            --health-cmd 'curl -fsS http://127.0.0.1:8081/actuator/health || exit 1' \
            --health-start-period 60s --health-interval 30s --health-timeout 5s --health-retries 5 \
            --log-driver json-file --log-opt max-size=20m --log-opt max-file=5 \
            --update-order stop-first --update-monitor 150s --update-failure-action rollback "$backend"
        await_replica "$ddd" 1
        await_replica "$backend" 1
        [[ $(image_of "$ddd") == "$(ref rbac-ddd)" && $(image_of "$backend") == "$(ref course-backend)" ]]
        ;;
    internal-web)
        [[ $(image_of "$backend") == "$(ref course-backend)" ]]
        docker service create --detach=true --name rbac-align-internal-web \
            --constraint 'node.hostname==package' --network "$network" --with-registry-auth --no-resolve-image \
            --secret "source=${base}tls-v2.crt,target=tls.crt" --secret "source=${base}tls-v2.key,target=tls.key" \
            --config "source=$config,target=/etc/nginx/templates/default.conf.template" "$(ref course-web)"
        await_replica rbac-align-internal-web 1
        id=$(docker ps -q --filter label=com.docker.swarm.service.name=rbac-align-internal-web)
        docker exec "$id" curl --retry 8 --retry-connrefused --retry-delay 2 -fkSs https://127.0.0.1/healthz
        docker exec "$id" curl --retry 8 --retry-connrefused --retry-delay 2 -fSs http://course-backend:8081/actuator/health |
            python3 -c 'import json,sys; assert json.load(sys.stdin)["status"]=="UP"; print("Course Actuator UP")'
        docker exec "$id" curl --retry 8 --retry-connrefused --retry-delay 2 -fSs http://rbac-backend:8081/api/v1/actuator/health |
            python3 -c 'import json,sys; assert json.load(sys.stdin)["status"]=="UP"; print("DDD Actuator UP")'
        touch internal-probes-passed
        ;;
    open)
        test -f internal-probes-passed
        test -f production-data-passed
        assert_network
        old_config=$(docker service inspect "$web" --format '{{range .Spec.TaskTemplate.ContainerSpec.Configs}}{{println .ConfigName}}{{end}}')
        [[ "$old_config" == "${base}course-nginx-37e372e29be5" ]]
        docker service update --detach=true --with-registry-auth --no-resolve-image \
            --image "$(ref course-web)" --replicas 1 \
            --config-rm "$old_config" --config-add "source=$config,target=/etc/nginx/templates/default.conf.template" \
            --health-cmd 'test "$(curl -kfsS https://127.0.0.1/healthz)" = ok' \
            --health-start-period 20s --health-interval 30s --health-timeout 5s --health-retries 3 \
            --log-driver json-file --log-opt max-size=20m --log-opt max-file=5 \
            --update-order start-first --update-monitor 120s --update-failure-action rollback "$web"
        docker service scale --detach=true "$rbac_web=1"
        await_replica "$web" 1
        await_replica "$rbac_web" 1
        [[ $(image_of "$web") == "$(ref course-web)" ]]
        docker service rm rbac-align-internal-web >/dev/null
        touch public-open
        ;;
    monitor)
        docker service create --detach=true --name "$prom" --label com.docker.stack.namespace=rbac1-cloud \
            --constraint 'node.hostname==monitor' --network "$network" --with-registry-auth --no-resolve-image \
            --config "source=$prom_config,target=/etc/prometheus/prometheus.yml" \
            --mount "type=volume,source=${base}course-prometheus-data,target=/prometheus" \
            --limit-memory 512m --reserve-memory 128m --log-driver json-file --log-opt max-size=20m --log-opt max-file=5 \
            --update-order stop-first --update-failure-action rollback "$monitor_image" \
            --config.file=/etc/prometheus/prometheus.yml --storage.tsdb.path=/prometheus --storage.tsdb.retention.time=15d
        await_replica "$prom" 1
        ;;
    rollback-images)
        test -f before-services.json
        for svc in "$ddd" "$backend" "$web"; do
            if [[ "$(image_of "$svc")" != "$(cat "$svc.before-image")" ]]; then
                docker service rollback --detach=true "$svc"
            fi
        done
        docker service scale --detach=true "$ddd=1" "$backend=1" "$web=1" "$rbac_web=1"
        ;;
    *)
        echo 'Usage: teacher-align-release.sh publish|freeze|activate-backends|internal-web|open|monitor|rollback-images' >&2
        exit 2
        ;;
esac
