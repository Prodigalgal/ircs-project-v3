"""Build the native deployment matrix from the Gradle module boundary."""
import json
import os
import re
from pathlib import Path

root = Path(__file__).resolve().parents[2]
modules = re.findall(r"^include '(services:[a-z0-9-]+|platform:ircs-migrator)'$", (root / "settings.gradle").read_text(), re.M)
priority = ["ircs-content-safety-service", "ircs-ingestion-worker", "ircs-scraper-service", "ircs-migrator"]
modules.sort(key=lambda module: (priority.index(module.split(":")[-1]) if module.split(":")[-1] in priority else len(priority), module))
scope = os.environ.get("BUILD_SCOPE", "all").strip() or "all"
if scope != "all":
    selected = {name.strip().removeprefix(":").split(":")[-1] for name in scope.split(",")}
    unknown = selected - {module.split(":")[-1] for module in modules}
    if unknown:
        raise SystemExit(f"Unknown native modules: {sorted(unknown)}")
    modules = [module for module in modules if module.split(":")[-1] in selected]
if not modules:
    raise SystemExit("At least one Java deployment module is required")
matrix = {"include": [
    {"module": module, "directory": module.replace(":", "/"), "image": module.split(":")[-1],
     "arch": arch, "runner": runner, "machine": machine}
    for module in modules
    for arch, runner, machine in [("amd64", "ubuntu-24.04", "x86-64"), ("arm64", "ubuntu-24.04-arm", "compatibility")]
]}
outputs = {
    "matrix": json.dumps(matrix, separators=(",", ":")),
    "images": json.dumps([module.split(":")[-1] for module in modules], separators=(",", ":")),
    "registry": "ghcr.io/" + os.environ["GITHUB_REPOSITORY"].lower(),
}
with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
    for key, value in outputs.items():
        output.write(f"{key}={value}\n")
print(f"Selected {len(modules)} Java deployment modules across two architectures")
