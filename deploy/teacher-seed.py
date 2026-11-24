"""Create one transactional data-only release from the pinned teacher workbook and dump."""
import argparse
from collections import Counter
from decimal import Decimal
import hashlib
import io
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import xml.etree.ElementTree as ET
import zipfile

import bcrypt

REVISION = "24a4d7d303ca082a43369658b6d2067489590c37"
WORKBOOK_SHA = "66657800a5e2e252c9d1dedfee2dbd54b694e3a5912166c2bac79c8703b20516"
DUMP_SHA = "a0637b31cf26322cadab5ecae0a2c05dc0d541780f34b75d0d117834c672b069"
SOURCE = "teacher_seed_24a4d7d"
OFFSET = 1000000
PREFIX = "muni-24a4-"
TABLES = (
    "teacher", "student", "term", "course", "teaching_class",
    "course_grouping_rule", "class_grouping_rule", "course_leader_assignment",
    "class_teacher_assignment", "enrollment", "milestone", "project_group",
    "group_membership", "group_join_request", "file_object", "submission",
    "submission_file", "submission_review", "oral_slot", "oral_booking",
    "oral_assessment", "oral_individual_score",
)


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def source_files(repo):
    def git(*command):
        return subprocess.check_output(["git", "-C", repo, *command])

    files = git("ls-tree", "-rz", "--name-only", REVISION).decode().split("\0")
    workbook, = [name for name in files if name.endswith(".xlsx")]
    dump, = [name for name in files if name.startswith("deploy/") and name.endswith("-dump.sql")]
    result = [git("show", REVISION + ":" + name) for name in (workbook, dump)]
    require([hashlib.sha256(raw).hexdigest() for raw in result] == [WORKBOOK_SHA, DUMP_SHA],
            "Teacher inputs do not match the release manifest")
    return result


def roster_from(raw):
    ns = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
    with zipfile.ZipFile(io.BytesIO(raw)) as workbook:
        strings = ["".join(node.itertext()) for node in
                   ET.fromstring(workbook.read("xl/sharedStrings.xml")).findall("m:si", ns)]
        rows = ET.fromstring(workbook.read("xl/worksheets/sheet2.xml"))
    roster = {}
    for node in rows.findall(".//m:row", ns):
        row = {}
        for cell in node.findall("m:c", ns):
            value = cell.findtext("m:v", "", ns)
            if value:
                column = re.match(r"[A-Z]+", cell.get("r")).group()
                if column in ("A", "B", "C", "D"):
                    row[column] = strings[int(value)] if cell.get("t") == "s" else value
        if not row or row.get("B") == "用户名":
            continue
        require(all(row.get(key) for key in "ABCD") and row["B"] not in roster,
                "Missing roster fields or duplicate username")
        roster[row["B"]] = row
    require(len(roster) == 10000, "Roster must contain 10000 unique usernames")
    return roster


def sql(value):
    if value is None:
        return "NULL"
    if isinstance(value, (int, Decimal)):
        return str(int(value) if isinstance(value, bool) else value)
    return "CONVERT(X'{}' USING utf8mb4)".format(value.encode("utf-8").hex())


def ident(name):
    return chr(96) + name + chr(96)


def query(container, statement):
    command = ["docker", "exec", "-i", container, "sh", "-ec",
               'export MYSQL_PWD=$(cat "$MYSQL_ROOT_PASSWORD_FILE"); '
               'exec mysql --default-character-set=utf8mb4 -uroot -NBr']
    return subprocess.run(command, input=statement.encode(),
                          stdout=subprocess.PIPE, check=True).stdout.decode()


def read_dump(container, dump):
    require(not re.search(rb"^\s*(USE|CREATE DATABASE|DROP DATABASE)\b", dump, re.M | re.I),
            "Dump contains a database-level command")
    query(container, "CREATE DATABASE IF NOT EXISTS " + SOURCE
          + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\nUSE " + SOURCE
          + ";\n" + dump.decode("utf-8"))
    tables = {}
    for table in TABLES:
        fields = query(container, "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA="
                       + sql(SOURCE) + " AND TABLE_NAME=" + sql(table)
                       + " AND EXTRA NOT LIKE '%VIRTUAL GENERATED%'"
                       + " AND EXTRA NOT LIKE '%STORED GENERATED%' ORDER BY ORDINAL_POSITION;").splitlines()
        require(fields, "Missing columns for " + table)
        pairs = ", ".join(sql(name) + ", " + ident(name) for name in fields)
        rows = query(container, "SELECT JSON_OBJECT({}) FROM {}.{};".format(pairs, SOURCE, ident(table)))
        tables[table] = [json.loads(line, parse_float=Decimal) for line in rows.splitlines()]
    keys = query(container, "SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME "
                 "FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = "
                 + sql(SOURCE) + " AND REFERENCED_COLUMN_NAME='id';")
    foreign_keys = {(table, column): parent for table, column, parent in
                    (line.split("\t") for line in keys.splitlines())}
    return tables, foreign_keys


def insert(writer, table, rows):
    if not rows:
        return
    fields = list(rows[0])
    quoted = ", ".join(map(ident, fields))
    for start in range(0, len(rows), 200):
        chunk = rows[start:start + 200]
        values = ["SELECT " + ", ".join(sql(row[name]) + (
            " AS " + ident(name) if n == 0 else "") for name in fields)
                  for n, row in enumerate(chunk)]
        writer.write("INSERT INTO " + table + " (" + quoted + ")\nSELECT " + quoted
                     + " FROM (\n" + "\nUNION ALL\n".join(values)
                     + "\n) AS batch WHERE NOT @already_seeded;\n")


def build_directory(roster):
    pairs = sorted({(row["C"], row["D"]) for row in roster.values()})
    require(len(pairs) == 110, "Expected 110 company-department pairs")
    parents = {}
    departments = []
    for company, name in sorted(pairs, key=lambda pair: pair[0] != pair[1]):
        parent = 1 if company in (name, "总公司") else parents[company, company]
        department_id = OFFSET + len(departments) + 1
        parents[company, name] = department_id
        path = ("/1/" if parent == 1 else "/1/{}/".format(parent)) + str(department_id) + "/"
        departments.append(dict(id=department_id, parent_id=parent, code="MUNI24A-" + str(department_id),
                                name=name, path=path, status="ENABLED"))
    role_department = {parents[row["C"], row["D"]]: ("teacher" if row["C"] == "总公司" else "student")
                       for row in roster.values()}
    departments.extend([
        dict(id=OFFSET+111, parent_id=1, code="CLOUD-ALIGN", name="课程联调组织",
             path="/1/{}/".format(OFFSET+111), status="ENABLED"),
        dict(id=OFFSET+112, parent_id=OFFSET+111, code="CLOUD-ALIGN-TEACHERS", name="联调教师组",
             path="/1/{}/{}/".format(OFFSET+111, OFFSET+112), status="ENABLED"),
    ])
    roles = {}
    subjects = {}
    users = []

    def identity(username, name, department, role, subject):
        uid = OFFSET + len(users) + 1
        roles[uid] = role
        subjects[username] = subject
        users.append(dict(id=uid, username=username, name=name, no=username, department_id=department,
                          subject=subject, phone=None, status="ACTIVE", must_change_password=0))

    for username, row in sorted(roster.items()):
        department = parents[row["C"], row["D"]]
        identity(username, row["A"], department, role_department[department],
                 PREFIX + hashlib.sha256(username.encode()).hexdigest())
    require(Counter(roles.values()) == {"teacher": 190, "student": 9810}, "Unexpected role counts")
    for username, name in (("CLOUD-A1", "课程联调管理员"), ("CLOUD-T2", "课程联调待建教师"),
                           ("CLOUD-SEED-T0", "课程样例教师")):
        identity(username, name, OFFSET+112, "teacher", "course-align-" + username.lower())
    return departments, role_department, users, roles, subjects


def bind_roster(roster, tables, subjects):
    old_subjects = {}
    numbers = {}
    for table, field in (("teacher", "employee_no"), ("student", "student_no")):
        numbers[table] = {}
        extras = [row for row in tables[table] if row[field] not in roster]
        require(len(extras) == 1, "Unexpected non-roster identities in " + table)
        if table == "teacher":
            extras[0][field] = "CLOUD-SEED-T0"
            extras[0]["name"] = "课程样例教师"
        else:
            tables[table].remove(extras[0])
        for row in tables[table]:
            name = row[field]
            require(name in subjects and (name not in roster or
                    (roster[name]["C"] == "总公司") == (table == "teacher")), "Roster role mismatch")
            numbers[table][row["id"]] = name
            old = row["user_id"]
            require(old and old not in old_subjects, "Duplicate or missing original course subject")
            old_subjects[old] = subjects[name]
            row["user_id"] = subjects[name]
        require(len(numbers[table]) == (191 if table == "teacher" else 9810), "Roster join mismatch")
    require(set(numbers["teacher"].values()) | set(numbers["student"].values())
            == set(roster) | {"CLOUD-SEED-T0"}, "Not all roster usernames are bound")
    return old_subjects, numbers


def make_credentials(users, tables, roster, numbers):
    visible = {row["id"] for row in tables["course"] if row["status"] == 3}
    teachers = ((r["course_id"], numbers["teacher"][r["teacher_id"]])
                for r in tables["course_leader_assignment"]
                if r["course_id"] in visible and numbers["teacher"][r["teacher_id"]] in roster)
    students = ((r["course_id"], numbers["student"][r["student_id"]])
                for r in tables["enrollment"] if r["course_id"] in visible)
    teacher, student = min(teachers), min(students)
    selected = {"CLOUD-A1": ("administrator", None), teacher[1]: ("teacher", teacher[0]+OFFSET),
                student[1]: ("student", student[0]+OFFSET)}
    credentials = {}
    for user in users:
        password = secrets.token_urlsafe(24)
        username = user["username"]
        if username in selected:
            kind, course_id = selected[username]
            credentials[kind] = dict(username=username, password=password, course=course_id)
        user["password_hash"] = bcrypt.hashpw(password.encode(), bcrypt.gensalt(rounds=10)).decode()
    print("Independent login hashes generated", flush=True)
    return credentials


def remap(tables, foreign_keys, subjects):
    valid_ids = {table: {row["id"] for row in rows if "id" in row} for table, rows in tables.items()}
    course_links = []
    for table, rows in tables.items():
        for row in rows:
            if "id" in row:
                row["id"] += OFFSET
            for field, value in row.items():
                parent = foreign_keys.get((table, field))
                if parent and value is not None:
                    require(value in valid_ids[parent], "Unresolved seed foreign key: {}.{}".format(table, field))
                    row[field] = value + OFFSET
                elif field in ("creator", "modifier"):
                    row[field] = subjects.get(value, value)
            if table == "course" and row["source_course_id"] is not None:
                course_links.append((row["id"], row["source_course_id"]))
                row["source_course_id"] = None
    return course_links


def write_assignments(writer, role_department, roles):
    for role in ("teacher", "student", "admin"):
        admitted = [uid for uid, assigned in role_department.items() if assigned == role]
        if role in ("teacher", "admin"):
            admitted.append(OFFSET+112)
        users = [uid for uid, assigned in roles.items() if assigned == role]
        if role == "admin":
            users = [OFFSET+10001]
        writer.write("""INSERT INTO rbac_center.sys_department_allowed_role(department_id, role_id)
SELECT d.id, r.id FROM rbac_center.sys_department d JOIN rbac_center.sys_role r
JOIN rbac_center.sys_system s ON s.id=r.system_id
WHERE NOT @already_seeded AND s.code='course-project-management'
AND r.code={role} AND d.id IN ({ids});
""".format(role=sql(role), ids=",".join(map(str, admitted))))
        writer.write("""INSERT INTO rbac_center.sys_user_role(user_id, role_id, assigned_by)
SELECT u.id, r.id, 1 FROM rbac_center.sys_user u JOIN rbac_center.sys_role r
JOIN rbac_center.sys_system s ON s.id=r.system_id
WHERE NOT @already_seeded AND s.code='course-project-management'
AND r.code={role} AND u.id IN ({ids});
""".format(role=sql(role), ids=",".join(map(str, users))))


def make_seed(out, roster, tables, foreign_keys):
    departments, role_department, users, roles, subjects = build_directory(roster)
    old_subjects, numbers = bind_roster(roster, tables, subjects)
    row_counts = {table: len(rows) for table, rows in tables.items()}
    require(tuple(row_counts[t] for t in ("term", "course", "teaching_class", "enrollment"))
            == (3, 9, 22, 344), "Teacher sample totals do not match")
    credentials = make_credentials(users, tables, roster, numbers)
    course_links = remap(tables, foreign_keys, old_subjects)
    admin = tables["teacher"][0].copy()
    admin.update(id=OFFSET*2, user_id=subjects["CLOUD-A1"], employee_no="CLOUD-A1",
                 name="课程联调管理员", title="联调教师", department="联调教师组",
                 creator="course-align", modifier="course-align")

    with (out / "seed.sql.tmp").open("w", encoding="utf-8") as writer:
        writer.write("""SET time_zone = '+00:00';
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
START TRANSACTION;
SELECT version INTO @version_before FROM rbac_center.sys_grant_version WHERE id=1 FOR UPDATE;
SET @already_seeded = EXISTS (SELECT 1 FROM rbac_center.sys_user WHERE subject='course-align-cloud-a1');
SET @has_current_term = EXISTS (SELECT 1 FROM rbac_course.term WHERE is_current=TRUE);
""")
        insert(writer, "rbac_center.sys_department", departments)
        insert(writer, "rbac_center.sys_user", users)
        write_assignments(writer, role_department, roles)
        for table, rows in tables.items():
            if table == "term":
                source_current, = [row["id"] for row in rows if row["is_current"]]
                for row in rows:
                    row["is_current"] = 0
            insert(writer, "rbac_course." + ident(table), rows)
            if table == "teacher":
                insert(writer, "rbac_course.teacher", [admin])
            if table == "term":
                writer.write("UPDATE rbac_course.term SET is_current=TRUE WHERE id={} "
                             "AND NOT @has_current_term AND NOT @already_seeded;\n".format(source_current))
            if table == "course":
                for course_id, source_id in course_links:
                    writer.write("UPDATE rbac_course.course SET source_course_id={} WHERE id={} "
                                 "AND NOT @already_seeded;\n".format(source_id, course_id))
        writer.write("""CREATE TEMPORARY TABLE rbac_center.seed_assertion (ok TINYINT PRIMARY KEY);
INSERT INTO rbac_center.seed_assertion VALUES (0);
INSERT INTO rbac_center.seed_assertion
SELECT (
  (SELECT COUNT(*) FROM rbac_center.sys_department WHERE code LIKE 'MUNI24A-%')=110
  AND (SELECT COUNT(*) FROM rbac_center.sys_user WHERE subject LIKE 'muni-24a4-%' AND phone IS NULL)=10000
  AND (SELECT COUNT(*) FROM rbac_center.sys_user u JOIN rbac_course.teacher t
       ON t.user_id=u.subject AND t.employee_no=u.username JOIN rbac_center.sys_user_role ur
       ON ur.user_id=u.id JOIN rbac_center.sys_role r ON r.id=ur.role_id
       JOIN rbac_center.sys_system s ON s.id=r.system_id
       WHERE u.subject LIKE 'muni-24a4-%' AND r.code='teacher' AND s.code='course-project-management')=190
  AND (SELECT COUNT(*) FROM rbac_center.sys_user u JOIN rbac_course.student st
       ON st.user_id=u.subject AND st.student_no=u.username JOIN rbac_center.sys_user_role ur
       ON ur.user_id=u.id JOIN rbac_center.sys_role r ON r.id=ur.role_id
       JOIN rbac_center.sys_system s ON s.id=r.system_id
       WHERE u.subject LIKE 'muni-24a4-%' AND r.code='student' AND s.code='course-project-management')=9810
  AND (SELECT COUNT(*) FROM rbac_course.course WHERE id IN ({}))=9
);
UPDATE rbac_center.sys_grant_version SET version = version+1 WHERE id=1 AND NOT @already_seeded;
COMMIT;
SELECT IF(@already_seeded,'SEED_ALREADY_PRESENT','SEED_APPLIED'), 110,190,9810,3,9,22,344;
""".format(",".join(str(row["id"]) for row in tables["course"])))
    (out / "seed.sql.tmp").rename(out / "seed.sql")
    (out / "acceptance-accounts.json").write_text(json.dumps(credentials, ensure_ascii=False), encoding="utf-8")
    (out / "source-totals.json").write_text(json.dumps(row_counts, sort_keys=True), encoding="utf-8")


if __name__ == "__main__":
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--teacher", required=True)
    parser.add_argument("--mysql", required=True)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    args.out.mkdir(mode=0o700, parents=True, exist_ok=False)
    xlsx, source_dump = source_files(args.teacher)
    parsed, constraints = read_dump(args.mysql, source_dump)
    make_seed(args.out, roster_from(xlsx), parsed, constraints)
