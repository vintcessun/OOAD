"""Exercise the deployed teacher web edge with live identity and course databases."""
import argparse
import json
from pathlib import Path
import ssl
import subprocess
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import HTTPSHandler, Request, build_opener


def run(args):
    context = ssl._create_unverified_context() if args.local or args.internal_web else ssl.create_default_context(cafile=args.ca)
    http = build_opener(HTTPSHandler(context=context))
    accounts = json.loads(Path(args.accounts).read_text(encoding="utf-8"))

    def call(path, status=200, *, payload=None, token=None):
        headers = {}
        if token:
            headers["Authorization"] = "Bearer " + token
        data = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(payload).encode()
        if args.internal_web:
            config = ["url = " + json.dumps(args.origin + path)]
            config.extend("header = " + json.dumps(name + ": " + value) for name, value in headers.items())
            if data is not None:
                config.append("data = " + json.dumps(data.decode()))
            raw = subprocess.check_output(["docker", "exec", "-i", args.internal_web, "curl",
                                           "--insecure", "--silent", "--show-error", "--max-time", "20",
                                           "--write-out", "\n%{http_code} %{content_type}", "--config", "-"],
                                          input="\n".join(config).encode())
            body, response_meta = raw.rsplit(b"\n", 1)
            actual, media = response_meta.decode().split(" ", 1)
            actual = int(actual)
            media = media.split(";", 1)[0]
        else:
            req = Request(args.origin + path, data, headers)
            try:
                res = http.open(req, timeout=20)
            except HTTPError as error:
                res = error
            with res:
                body = res.read()
                media = res.headers.get_content_type()
                actual = res.code
        if actual != status:
            code = json.loads(body).get("code") if media == "application/json" else media
            raise AssertionError("{} {} expected {} received {} {}"
                                 .format("GET" if data is None else "POST", path.split("?")[0], status, actual, code))
        return json.loads(body) if media == "application/json" and body else body

    assert call("/healthz") == b"ok\n"
    assert call("/version") == b"course-project-frontend\n"
    call("/actuator/prometheus", 404)
    call("/integration/authorities", 403)
    call("/api/terms", 401)
    call("/auth/api-permissions/CV", 401)
    for role, account in accounts.items():
        login_role = "student" if role == "student" else "teacher"
        response = call("/api/login", payload=dict(role=login_role, account=account["username"],
                                                   password=account["password"]))
        token = response["accessToken"]
        me = call("/auth/me", token=token)
        assert login_role in me["roles"]
        assert "CV" not in me["permissions"] and "TEC" not in me["permissions"]
        assert "CV" not in response["user"]["permissions"]
        assert call("/auth/api-permissions/CV", token=token) == dict(operationId="CV", allowed=role == "administrator")
        assert call("/auth/api-permissions/listAdminCourses", token=token)["allowed"] == (role == "administrator")
        call("/auth/api-permissions/UnknownTeacherAuthority", 404, token=token)
        if role != "administrator":
            assert call("/auth/permissions/MTE", token=token) == {"allowed": False}
            call("/auth/departments", 403, token=token)
            call("/api/teachers", 403, token=token,
                 payload=dict(userId="course-align-cloud-t2", title="联调教师", dept="联调教师组"))
            course_id = account["course"]
            list_endpoint = "/api/teacher/courses" if role == "teacher" else "/api/me/courses"
            page = call(list_endpoint, token=token)
            assert any(int(item.get("id", item.get("courseId", -1))) == course_id for item in page["items"])
            assert call("/api/courses/" + str(course_id), token=token)
            if role == "student":
                call("/api/courses/copy", 403, token=token, payload=dict(sourceCourseId=course_id))
        else:
            assert {"admin", "teacher"} <= set(me["roles"])
            for code in ("MCO", "MTR", "MTE", "MLG", "MTEC"):
                assert call("/auth/permissions/" + code, token=token)["allowed"]
            for endpoint in ("/api/admin/courses", "/api/terms", "/api/teachers", "/api/audit-logs"):
                call(endpoint, token=token)
            root, = [d for d in call("/auth/departments", token=token)["items"] if d["code"] == "HQ"]
            company, = call("/auth/departments?" + urlencode(dict(parentId=root["id"], keyword="课程联调组织")),
                            token=token)["items"]
            department, = call("/auth/departments?" + urlencode(dict(parentId=company["id"])),
                               token=token)["items"]
            members = call("/auth/departments/{}/users?keyword=CLOUD-T2".format(department["id"]), token=token)
            candidate, = members["items"]
            subject = candidate["userId"]
            assert subject == "course-align-cloud-t2"
            assert call("/auth/users/" + subject, token=token)["no"] == "CLOUD-T2"
            payload = dict(userId=subject, title="联调教师", dept="联调教师组")
            if args.create:
                call("/api/teachers", 201, token=token, payload=payload)
            if not args.candidate_pending:
                call("/api/teachers", 409, token=token, payload=payload)
            teachers = call("/api/teachers?keyword=CLOUD-T2", token=token)["items"]
            assert any(row["userId"] == subject for row in teachers) == (not args.candidate_pending)
        print("{} flow passed".format(role), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("origin")
    parser.add_argument("accounts")
    parser.add_argument("--ca")
    parser.add_argument("--local", action="store_true")
    parser.add_argument("--internal-web")
    parser.add_argument("--create", action="store_true")
    parser.add_argument("--candidate-pending", action="store_true")
    options = parser.parse_args()
    if ((options.local and options.origin != "https://127.0.0.1:19443")
            or (options.internal_web and options.origin != "https://127.0.0.1")
            or (not options.local and not options.internal_web and not options.ca)):
        parser.error("Use the CA for endpoints outside the local rehearsal")
    run(options)
