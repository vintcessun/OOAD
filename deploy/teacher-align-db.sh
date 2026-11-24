#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

[[ $(hostname) == mysql ]]
release=/root/rbac-course-align-24a4d7d
cd "$release"
db=$(docker ps -q --filter label=com.docker.swarm.service.name=rbac1-cloud_mysql)
[[ -n "$db" && "$db" != *$'\n'* ]]
[[ $(docker inspect "$db" --format '{{.State.Health.Status}}') == healthy ]]

sql() {
    docker exec -i "$db" sh -ec 'MYSQL_PWD="$(cat /run/secrets/mysql-root-password)" exec mysql --default-character-set=utf8mb4 -uroot -NBr'
}

case "$1" in
    backup)
        [[ ! -e before-switchover.sql.gz ]]
        docker exec "$db" sh -ec '
            export MYSQL_PWD="$(cat /run/secrets/mysql-root-password)"
            exec mysqldump -uroot --databases rbac_center rbac_course --single-transaction \
                --triggers --no-tablespaces --set-gtid-purged=OFF --column-statistics=0
        ' | gzip > before-switchover.sql.gz.tmp
        gzip -t before-switchover.sql.gz.tmp
        mv before-switchover.sql.gz.tmp before-switchover.sql.gz
        sha256sum before-switchover.sql.gz > before-switchover.sha256
        echo DATA_BACKUP_COMPLETE
        ;;
    import)
        sha256sum --status -c before-switchover.sha256
        sha256sum --status -c seed.sha256
        if ! sql < seed.sql > import.sql.log 2>&1; then
            echo DATA_IMPORT_FAILED >&2
            exit 1
        fi
        if ! sql < seed.sql > reimport.sql.log 2>&1; then
            echo DATA_REIMPORT_FAILED >&2
            exit 1
        fi
        grep -E '^SEED_(APPLIED|ALREADY_PRESENT)' import.sql.log reimport.sql.log
        grep -q '^SEED_ALREADY_PRESENT' reimport.sql.log
        bash "$0" verify
        ;;
    verify)
        sql <<'SQL'
SELECT 'rbac_migration',version,success FROM rbac_center.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
SELECT 'course_migration',version,success FROM rbac_course.flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;
SELECT 'roster_departments',COUNT(*) FROM rbac_center.sys_department WHERE code LIKE 'MUNI24A-%';
SELECT 'roster_accounts',COUNT(*) FROM rbac_center.sys_user WHERE subject LIKE 'muni-24a4-%' AND phone IS NULL;
SELECT 'imported_teachers',COUNT(*) FROM rbac_course.teacher WHERE user_id LIKE 'muni-24a4-%';
SELECT 'imported_students',COUNT(*) FROM rbac_course.student WHERE user_id LIKE 'muni-24a4-%';
SELECT 'terms',COUNT(*) FROM rbac_course.term WHERE id BETWEEN 1000001 AND 1999999;
SELECT 'courses',COUNT(*) FROM rbac_course.course WHERE id BETWEEN 1000001 AND 1999999;
SELECT 'classes',COUNT(*) FROM rbac_course.teaching_class WHERE id BETWEEN 1000001 AND 1999999;
SELECT 'enrollment',COUNT(*) FROM rbac_course.enrollment WHERE course_id BETWEEN 1000001 AND 1999999;
SELECT 'original_smoke',COUNT(*) FROM rbac_course.course WHERE id=619001;
SELECT 'missing_sample_refs',COUNT(*) FROM rbac_course.enrollment e
LEFT JOIN rbac_course.teaching_class c ON c.id=e.class_id
LEFT JOIN rbac_course.student s ON s.id=e.student_id
LEFT JOIN rbac_course.course x ON x.id=e.course_id
WHERE e.course_id BETWEEN 1000001 AND 1999999 AND (c.id IS NULL OR s.id IS NULL OR x.id IS NULL);
SQL
        ;;
    restore)
        sha256sum --status -c before-switchover.sha256
        gzip -dc before-switchover.sql.gz | sql
        echo DATA_BACKUP_RESTORED
        ;;
    *)
        echo 'Usage: teacher-align-db.sh backup|import|verify|restore' >&2
        exit 2
        ;;
esac
