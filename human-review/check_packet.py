"""Validate the review assignments without changing source, notes or databases.

Usage from repository root: python -B docs/human-review/check_packet.py
Python 3.10+; standard library only. This is not a business test runner.
"""
from __future__ import annotations

from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import sys

PACKET = Path(__file__).resolve().parent
ROOT = PACKET.parents[1]
JAVA = "rbac-structured/src/main/java/com/xmu/rbac/structured"
CONTRACT = "rbac-contract/src/main/resources/openapi/rbac-api.yaml"
OWNERS = set("ABCDEF")


def sources() -> set[str]:
    result = set()
    for name in (
        "rbac-structured/src", "rbac-contract/src", "rbac-web/src", "rbac-web/tests",
        "rbac-web/e2e", "deploy", ".github/workflows", ".mvn", "diagrams/src", "diagrams/tools",
    ):
        for path in (ROOT / name).rglob("*"):
            if path.is_file() and not any(x in {"__pycache__", "node_modules", "target"} for x in path.parts):
                result.add(path.relative_to(ROOT).as_posix())
    for name in ("rbac-structured", "rbac-contract", "rbac-web"):
        result.update(p.relative_to(ROOT).as_posix() for p in (ROOT / name).iterdir() if p.is_file())
    for name in (
        "pom.xml", "Dockerfile", "docker-compose.yml", "mvnw", "mvnw.cmd", ".gitignore",
        ".gitattributes", ".dockerignore", "diagrams/rbac.mdj", "diagrams/README.md",
    ):
        if (ROOT / name).is_file():
            result.add(name)
    return result


def digest(path: Path) -> str:
    data = path.read_bytes()
    if path.suffix not in {".xlsx", ".jar", ".png"}:
        data = data.replace(b"\r\n", b"\n")
    return hashlib.sha256(data).hexdigest()


def contract() -> dict[str, dict]:
    operations = {}
    current_path = None
    current = None
    for number, line in enumerate((ROOT / CONTRACT).read_text(encoding="utf-8").splitlines(), 1):
        match = re.fullmatch(r"  (/[^:]+):", line)
        if match:
            current_path, current = match[1], None
        match = re.fullmatch(r"    (get|post|patch|put|delete|head|options|trace):", line)
        if match:
            key = match[1].upper() + " " + current_path
            if key in operations:
                raise ValueError("Duplicate API: " + key)
            current = {"method": match[1].upper(), "path": current_path, "line": number}
            operations[key] = current
        if current is not None:
            match = re.match(r"      (summary|operationId|x-required-permission|security):\s*(.*)", line)
            if match:
                current[match[1]] = match[2].strip().strip("\"'")
    for key, op in operations.items():
        op["permission"] = "anonymous" if op.get("security") == "[]" else op.get("x-required-permission")
        if not op["permission"]:
            raise ValueError("Missing permission declaration: " + key)
    return operations


def controllers() -> dict[str, dict]:
    operations = {}
    for path in (ROOT / JAVA / "controller").glob("*.java"):
        text = path.read_text(encoding="utf-8")
        fields = {var: cls for cls, var in re.findall(r"private final (\w+) (\w+);", text)}
        mappings = list(re.finditer(r'@(Get|Post|Patch|Put|Delete)Mapping\("([^"\n]+)"\)', text))
        if len(re.findall(r"@(?:Request|Get|Post|Patch|Put|Delete)Mapping\b", text)) != len(mappings):
            raise ValueError("Unsupported mapping syntax; inspect manually: " + str(path))
        for i, mapping in enumerate(mappings):
            chunk = text[mapping.end():mappings[i + 1].start() if i + 1 < len(mappings) else len(text)]
            signature = re.search(r"public\s+[\w<>, ?.\[\]]+\s+(\w+)\s*\(", chunk)
            if signature is None:
                raise ValueError("Controller method could not be parsed: " + str(path))
            calls = []
            for var, method in re.findall(r"\b(\w+)\.(\w+)\(", chunk):
                if fields.get(var, "").endswith("Service"):
                    call = {"class": fields[var], "method": method}
                    if call not in calls:
                        calls.append(call)
            key = mapping[1].upper() + " " + mapping[2]
            if key in operations:
                raise ValueError("Duplicate controller mapping: " + key)
            operations[key] = {"file": path.relative_to(ROOT).as_posix(), "method": signature[1],
                               "line": text[:mapping.start()].count("\n") + 1, "services": calls}
    return operations


def main() -> int:
    data = json.loads((PACKET / "inventory.json").read_text(encoding="utf-8"))
    errors = []
    text_cache = {}

    def read(path: Path) -> str:
        path = path.resolve()
        if path not in text_cache:
            text_cache[path] = path.read_text(encoding="utf-8")
        return text_cache[path]

    rows = data["files"]
    files = {row["path"]: row for row in rows}
    if len(files) != len(rows):
        errors.append("Duplicate file assignment")
    actual = sources()
    errors.extend("Unassigned file: " + p for p in sorted(actual - files.keys()))
    errors.extend("Missing/out-of-scope file: " + p for p in sorted(files.keys() - actual))
    for name, row in files.items():
        if row["owner"] not in OWNERS or not row.get("focus"):
            errors.append("Invalid owner/scope: " + name)
        if not set(row.get("related", [])).issubset(OWNERS - {row["owner"]}):
            errors.append("Invalid related reviewers: " + name)
        if (ROOT / name).is_file() and digest(ROOT / name) != row["sha256"]:
            errors.append("Source changed: " + name)
        if row["owner"] in OWNERS:
            guide = PACKET / (row["owner"] + ".md")
            token = "](../../" + name + ")"
            matching = [] if not guide.is_file() else [line for line in read(guide).splitlines()
                                                       if line.startswith("| ") and token in line]
            if len(matching) != 1:
                errors.append("Missing/duplicate guide row: " + name)
    schema, routes = contract(), controllers()
    apis = {op["key"]: op for op in data["apis"]}
    if len(apis) != len(data["apis"]):
        errors.append("Duplicate API assignment")
    for key in sorted(set(schema) | set(routes) | set(apis)):
        if key not in schema or key not in routes or key not in apis:
            errors.append("API contract/controller/inventory mismatch: " + key)
            continue
        op = apis[key]
        if op["owner"] not in OWNERS or op["permission"] != schema[key]["permission"] or op["controller"] != routes[key]:
            errors.append("API binding changed: " + key)
        if op["owner"] in OWNERS:
            card = PACKET / "api-cards" / (op["owner"] + ".md")
            if not card.is_file() or read(card).count("<!-- api: " + key + " -->") != 1:
                errors.append("Missing/duplicate API card: " + key)
        for call in op["controller"]["services"]:
            path = ROOT / JAVA / "service" / (call["class"] + ".java")
            pattern = r"^    (?![ \t])(?!(?:return|throw|if|for|while|switch|catch|new)\b)(?:public |private |protected |static )*[\w<>, ?.\[\]]+\s+" + re.escape(call["method"]) + r"\s*\("
            if not path.is_file() or not re.search(pattern, read(path), re.M):
                errors.append("Service entry missing: " + key)
    if Counter(op["owner"] for op in apis.values()) != Counter({"A": 4, "B": 2, "C": 13, "D": 14, "E": 4, "F": 8}):
        errors.append("API ownership counts differ from plan")
    tests = {(row["file"], row["method"]) for row in data["tests"]}
    actual_tests = set()
    for path in (ROOT / "rbac-structured/src/test/java").rglob("*Test.java"):
        for method in re.findall(r"@Test\s+(?:@\w+(?:\([^\n]*\))?\s+)*void\s+(\w+)\s*\(", read(path)):
            actual_tests.add((path.relative_to(ROOT).as_posix(), method))
    if tests != actual_tests or len(tests) != len(data["tests"]):
        errors.append("Backend test-method assignment mismatch")
    for row in data["tests"]:
        if row["owner"] not in OWNERS:
            errors.append("Invalid test reviewer: " + row["method"])
    for doc in PACKET.rglob("*.md"):
        text = read(doc)
        for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", text):
            if "://" in target or target.startswith("#"):
                continue
            parts = target.split("#", 1)
            path = (doc.parent / parts[0]).resolve()
            if not path.exists():
                errors.append("Broken link: " + str(doc.relative_to(PACKET)) + " " + target)
            elif len(parts) == 2 and re.fullmatch(r"L\d+", parts[1]):
                if int(parts[1][1:]) > len(read(path).splitlines()):
                    errors.append("Invalid source line: " + target)
            elif len(parts) == 2 and path.suffix == ".md" and not re.search(r'id="' + re.escape(parts[1]) + r'"', read(path)):
                errors.append("Missing explicit card anchor: " + target)
    if errors:
        print("FAIL\n" + "\n".join("- " + x for x in errors))
        return 1
    print(f"PASS: {len(files)} files, {len(apis)} APIs, {len(tests)} backend test methods assigned; source bindings and links match")
    print("Human reading, reproductions and signatures are not certified by this check.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
