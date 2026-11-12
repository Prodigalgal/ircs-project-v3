from __future__ import annotations

import ast
import json
import re
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
VERSION_PATTERN = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")


def fail(message: str) -> None:
    print(f"版本契约失败：{message}", file=sys.stderr)
    raise SystemExit(1)


def read_json(path: Path) -> dict[str, object]:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        fail(f"无法读取 {path.relative_to(ROOT)}：{error}")


def main() -> None:
    version_path = ROOT / "VERSION"
    try:
        version = version_path.read_text(encoding="utf-8").strip()
    except OSError as error:
        fail(f"无法读取 VERSION：{error}")
    if not VERSION_PATTERN.fullmatch(version):
        fail(f"VERSION 必须是正式 x.y.z，当前为 {version!r}")

    for frontend in ("admin", "huawai"):
        directory = ROOT / "frontends" / frontend
        package = read_json(directory / "package.json")
        lock = read_json(directory / "package-lock.json")
        if package.get("version") != version:
            fail(f"frontends/{frontend}/package.json version 与 VERSION 不一致")
        if lock.get("version") != version:
            fail(f"frontends/{frontend}/package-lock.json version 与 VERSION 不一致")
        packages = lock.get("packages")
        root_package = packages.get("") if isinstance(packages, dict) else None
        if not isinstance(root_package, dict) or root_package.get("version") != version:
            fail(f"frontends/{frontend}/package-lock.json packages[''].version 与 VERSION 不一致")
        dockerfile = (directory / "Dockerfile").read_text(encoding="utf-8")
        if f"ARG APP_VERSION={version}" not in dockerfile or "version.json" not in dockerfile:
            fail(f"frontends/{frontend}/Dockerfile 未提供匹配的 APP_VERSION /version.json")

    classifier = ROOT / "backend/services/ircs-adult-classifier-service/app/main.py"
    tree = ast.parse(classifier.read_text(encoding="utf-8"), filename=str(classifier))
    default_versions: list[str] = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
            continue
        if isinstance(node.func.value, ast.Name) and node.func.value.id == "os" and node.func.attr == "getenv":
            if len(node.args) >= 2 and isinstance(node.args[0], ast.Constant) and node.args[0].value == "APP_VERSION":
                if isinstance(node.args[1], ast.Constant) and isinstance(node.args[1].value, str):
                    default_versions.append(node.args[1].value)
    if default_versions != [version]:
        fail(f"FastAPI APP_VERSION 默认值应唯一且等于 VERSION，当前为 {default_versions}")
    dockerfile = (ROOT / "backend/services/ircs-adult-classifier-service/Dockerfile").read_text(encoding="utf-8")
    if f"ARG APP_VERSION={version}" not in dockerfile or "ENV APP_VERSION=${APP_VERSION}" not in dockerfile:
        fail("classifier Dockerfile 未将匹配的 APP_VERSION 注入运行环境")

    gradle = (ROOT / "backend/build.gradle").read_text(encoding="utf-8")
    if "gradleProperty('appVersion')" not in gradle or "../VERSION" not in gradle or "buildInfo()" not in gradle:
        fail("backend/build.gradle 缺少 appVersion 优先、根 VERSION 回退或 Spring Boot build info")
    native = (ROOT / "backend/scripts/docker/Dockerfile.native").read_text(encoding="utf-8")
    if f"ARG APP_VERSION={version}" not in native or "-PappVersion=\"${APP_VERSION}\"" not in native:
        fail("Native Dockerfile 未把 APP_VERSION 传给 Gradle appVersion")

    print(f"版本契约通过：{version}（VERSION、Backend、Admin、Huawai、Python classifier、Native build metadata）")


if __name__ == "__main__":
    main()
