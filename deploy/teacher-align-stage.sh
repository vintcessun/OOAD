#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
cd /root/rbac-course-align-24a4d7d
release=$PWD
trap 'echo STAGE_EXIT=$?' EXIT

test_mysql=rbac-align-mysql
net=rbac-align-test
sql() {
    docker exec -i "$test_mysql" sh -ec 'MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")" exec mysql -uroot -NBr --default-character-set=utf8mb4'
}

wait_url() {
    local url=$1
    for attempt in $(seq 1 90); do
        if curl -kfSs --max-time 3 "$url" >/dev/null 2>&1; then return; fi
        sleep 2
    done
    printf 'Service unavailable at %s\n' "$url" >&2
    return 1
}

for name in ddd backend web redis; do
    if docker container inspect "rbac-align-$name" >/dev/null 2>&1; then
        docker rm -f "rbac-align-$name" >/dev/null
    fi
done
install -d -m 700 test/identity test/course test/tls
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out test/identity/jwt-private.pem 2>/dev/null
openssl pkey -in test/identity/jwt-private.pem -pubout -out test/identity/jwt-public.pem
openssl rand -hex 32 > test/identity/rbac.audit-service-key
cp test/identity/rbac.audit-service-key test/course/app.identity.authorities-service-key
cp test/mysql-password test/identity/spring.datasource.password
cp test/mysql-password test/course/spring.datasource.password
openssl rand -hex 32 > test/identity/RBAC_BOOTSTRAP_PASSWORD
openssl req -nodes -x509 -newkey rsa:2048 -days 3 -subj /CN=localhost \
  -keyout test/tls/tls.key -out test/tls/tls.crt 2>/dev/null

sql <<'SQL'
CREATE DATABASE IF NOT EXISTS rbac_center CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS rbac_course CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SQL
docker run -d --name rbac-align-redis --network "$net" --network-alias redis --memory 64m \
    redis:7-alpine redis-server --maxmemory 32mb >/dev/null

docker run -d --name rbac-align-ddd --network "$net" --network-alias rbac-backend \
    -p 127.0.0.1:19881:8081 --memory 500m \
    -v "$release/test/identity:/run/secrets:ro" \
    -e JAVA_OPTS='-Xms64m -Xmx224m -XX:ActiveProcessorCount=2' \
    -e SERVER_PORT=8081 -e COURSE_UPSTREAM=http://course-backend:8080 \
    -e SPRING_CONFIG_IMPORT=configtree:/run/secrets/ \
    -e 'SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/rbac_center?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false' \
    -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATA_REDIS_HOST=redis -e SPRING_DATA_REDIS_PORT=6379 \
    -e RBAC_JWT_PRIVATE_KEY_FILE=/run/secrets/jwt-private.pem -e RBAC_JWT_PUBLIC_KEY_FILE=/run/secrets/jwt-public.pem \
    rbac-ddd:course-align >/dev/null
wait_url http://127.0.0.1:19881/api/v1/actuator/health

docker run -d --name rbac-align-backend --network "$net" --network-alias course-backend \
    -p 127.0.0.1:19882:8081 --memory 560m \
    -v "$release/test/course:/run/secrets:ro" \
    -e JAVA_OPTS='-Xms64m -Xmx192m -XX:ActiveProcessorCount=2' \
    -e SPRING_PROFILES_ACTIVE=swarm -e APP_BACKEND_STRATEGY=ddd \
    -e IDENTITY_BASE_URL=http://rbac-backend:8081 -e SPRING_CONFIG_IMPORT=configtree:/run/secrets/ \
    -e 'SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/rbac_course?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false' \
    -e SPRING_DATASOURCE_USERNAME=root \
    -e LOGGING_LEVEL_CN_EDU_XMU_PROJECTMGT_API=INFO -e LOGGING_LEVEL_CN_EDU_XMU_PROJECTMGT_SERVICE=INFO \
    -e LOGGING_LEVEL_CN_EDU_XMU_PROJECTMGT_INFRASTRUCTURE=INFO course-backend:course-align >/dev/null
wait_url http://127.0.0.1:19882/actuator/health
curl -fsS http://127.0.0.1:19882/actuator/prometheus | awk 'END { if (NR < 10) exit 1; print "Metrics lines", NR }'

sql < seed/seed.sql > test/import.log 2>&1
sql < seed/seed.sql > test/reimport.log 2>&1
grep -E '^SEED_(APPLIED|ALREADY_PRESENT)' test/import.log test/reimport.log
grep -q '^SEED_ALREADY_PRESENT' test/reimport.log

docker run -d --name rbac-align-web --network "$net" -p 127.0.0.1:19443:443 \
    --memory 64m -v "$release/test/tls:/run/secrets:ro" course-web:course-align >/dev/null
wait_url https://127.0.0.1:19443/healthz
curl -kfSs https://127.0.0.1:19443/course/ >/dev/null
