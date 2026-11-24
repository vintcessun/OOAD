"""Render the persisted release from the prior live manifest and published digest files."""
from copy import deepcopy
from pathlib import Path
import sys

import yaml


previous_file, release_dir, output = (Path(arg) for arg in sys.argv[1:4])
previous = yaml.safe_load(previous_file.read_text(encoding="utf-8"))
template = yaml.safe_load((release_dir / "overlay/deploy/cloud-stack.yml").read_text(encoding="utf-8"))
services = previous["services"]
original = template["services"]
for service, image_file in (("rbac-backend", "rbac-ddd.image"),
                            ("course-backend", "course-backend.image"),
                            ("course-web", "course-web.image")):
    services[service]["image"] = (release_dir / image_file).read_text().strip()

prefix = "rbac1-cloud_"
previous["configs"]["course-nginx"]["name"] = prefix + "course-nginx-24a4d7d-align"
previous["configs"]["course-prometheus"] = dict(external=True, name=prefix+"course-prometheus-24a4d7d-align")
previous["volumes"]["course-prometheus-data"] = dict(external=True, name=prefix+"course-prometheus-data")
for service in ("course-backend", "course-web"):
    for field in ("healthcheck", "logging"):
        services[service][field] = deepcopy(original[service][field])
    services[service]["deploy"]["update_config"] = deepcopy(original[service]["deploy"]["update_config"])

backend = services["course-backend"]
for name in ("SPRING_PROFILES_ACTIVE", "APP_BACKEND_STRATEGY", "LOGGING_LEVEL_CN_EDU_XMU_PROJECTMGT_API",
             "LOGGING_LEVEL_CN_EDU_XMU_PROJECTMGT_SERVICE", "LOGGING_LEVEL_CN_EDU_XMU_PROJECTMGT_INFRASTRUCTURE"):
    backend["environment"][name] = original["course-backend"]["environment"][name]
backend["secrets"].append(dict(source="audit-service-key", target="app.identity.authorities-service-key"))
services["course-prometheus"] = deepcopy(original["course-prometheus"])
output.write_text(yaml.safe_dump(previous, default_flow_style=False, allow_unicode=True), encoding="utf-8")
