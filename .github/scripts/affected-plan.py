#!/usr/bin/env python3
"""Plan affected application builds from Git and the Gradle project graph.

Pure stdlib; never builds, pushes, deploys, or contacts a registry. Consumers
must retain previously verified immutable artifacts for omitted applications.
Version metadata alone is not an application change. --files-json accepts a
JSON array (or a file containing it) of paths or name-status records; renames
use {"status":"R100","old_path":"old","path":"new"}.
"""

from __future__ import annotations

import argparse
import ast
from collections.abc import Mapping, Sequence
from copy import deepcopy
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[2]
PYTHON_ROOT = "backend/services/ircs-adult-classifier-service/"
FRONTENDS = ("admin", "huawai")
SEMVER = r"\d+\.\d+\.\d+"
PROJECT_REFERENCE = re.compile(r"\bproject\s*\(\s*['\"]:([^'\"]+)['\"]\s*\)")


def strip_gradle_comments(text: str) -> str:
    """Leave string literals intact, including URL // and quoted braces."""
    return re.sub(r"('(?:\\.|[^'\\])*'|\"(?:\\.|[^\"\\])*\")|//[^\n]*|/\*[\s\S]*?\*/",
                  lambda match: match[1] if match[1] else " ", text)


def braced_body(text: str, opening: int) -> str:
    depth, quote, escaped = 1, None, False
    for index in range(opening + 1, len(text)):
        char = text[index]
        if quote:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
        elif char in "'\"":
            quote = char
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[opening + 1:index]
    raise ValueError("Unbalanced Gradle configuration block")


def gradle_graph(tree: Mapping[str, str]) -> dict[str, set[str]]:
    settings = strip_gradle_comments(tree.get("backend/settings.gradle", ""))
    modules = set()
    for include in re.finditer(r"^\s*include\s*\(?([^\n]+)", settings, re.M):
        modules.update(re.findall(r"['\"]:?(shared:[\w-]+|services:[\w-]+|platform:[\w-]+)['\"]", include[1]))
    if not modules:
        raise ValueError("No Gradle modules were found in backend/settings.gradle")
    graph = {module: set(PROJECT_REFERENCE.findall(strip_gradle_comments(
        tree.get("backend/" + module.replace(":", "/") + "/build.gradle", "")))) for module in modules}
    root = strip_gradle_comments(tree.get("backend/build.gradle", ""))
    for block in re.finditer(r"\b(allprojects|subprojects|configure\s*\([^{}\n]*\))\s*\{", root):
        body = braced_body(root, block.end() - 1)
        # Only dependency declarations count; project(...) used as a configure
        # target must not become an artificial dependency on the migrator.
        dependencies = set()
        for declaration in re.finditer(r"\bdependencies\s*\{", body):
            dependencies.update(PROJECT_REFERENCE.findall(braced_body(body, declaration.end() - 1)))
        targets = set(modules)
        if block[1].startswith("configure"):
            targets = set(PROJECT_REFERENCE.findall(block[1]))
            if re.search(r"\bserviceProjects\b", block[1]):
                targets.update(module for module in modules if module.startswith("services:"))
            if dependencies and not targets:
                raise ValueError("Unsupported Gradle configure target with project dependencies")
        for module in targets & modules:
            graph[module].update(dependencies - {module})
    unknown = {dependency for dependencies in graph.values() for dependency in dependencies if dependency not in modules}
    if unknown:
        raise ValueError("Unregistered Gradle project dependencies: " + ", ".join(sorted(unknown)))
    return graph


def deployment_modules(graph: Mapping[str, set[str]]) -> set[str]:
    return {module for module in graph if module.startswith("services:") or module == "platform:ircs-migrator"}


def consumers(changed: set[str], graph: Mapping[str, set[str]]) -> set[str]:
    affected = set(changed)
    while True:
        expanded = affected | {module for module, dependencies in graph.items() if dependencies & affected}
        if expanded == affected:
            return affected
        affected = expanded


def normalize_path(path: str) -> str:
    path = path.replace("\\", "/")
    while path.startswith("./"):
        path = path[2:]
    if not path or path.startswith("/") or ".." in PurePosixPath(path).parts or ":" in path.split("/")[0]:
        raise ValueError("Change paths must stay relative to the repository")
    return str(PurePosixPath(path))


def change_paths(changes: Sequence[str | Mapping[str, str]]) -> list[str]:
    paths = set()
    for change in changes:
        if isinstance(change, str):
            paths.add(normalize_path(change))
        elif isinstance(change, Mapping):
            path = change.get("path") or change.get("new_path")
            if not path:
                raise ValueError("Change record has no path")
            paths.add(normalize_path(path))
            if change.get("old_path"):
                paths.add(normalize_path(change["old_path"]))
        else:
            raise ValueError("Changes must be paths or name-status objects")
    return sorted(paths)


class AppVersionNormalizer(ast.NodeTransformer):
    def visit_Call(self, node):
        self.generic_visit(node)
        if isinstance(node.func, ast.Name) and node.func.id == "FastAPI":
            for keyword in node.keywords:
                if keyword.arg != "version":
                    continue
                value = keyword.value
                if isinstance(value, ast.Constant) and isinstance(value.value, str) and re.fullmatch(SEMVER, value.value):
                    keyword.value = ast.Constant(value="<APP_VERSION>")
                elif (isinstance(value, ast.Call) and isinstance(value.func, ast.Attribute)
                      and isinstance(value.func.value, ast.Name) and value.func.value.id == "os"
                      and value.func.attr == "getenv" and len(value.args) == 2
                      and isinstance(value.args[0], ast.Constant) and value.args[0].value == "APP_VERSION"
                      and isinstance(value.args[1], ast.Constant) and isinstance(value.args[1].value, str)
                      and re.fullmatch(SEMVER, value.args[1].value)):
                    value.args[1] = ast.Constant(value="<APP_VERSION>")
        return node


def normalized_metadata(path: str, text: str):
    if path.endswith(("/package.json", "/package-lock.json")) and path.startswith("frontends/"):
        document = deepcopy(json.loads(text))
        document.pop("version", None)
        if path.endswith("/package-lock.json") and isinstance(document.get("packages"), dict):
            root_package = document["packages"].get("")
            if isinstance(root_package, dict):
                root_package.pop("version", None)
        # Dependency versions, integrity, resolved URLs, scripts and engines
        # remain significant, including every node_modules/* lockfile entry.
        return document
    if path == PYTHON_ROOT + "app/main.py":
        return ast.dump(AppVersionNormalizer().visit(ast.parse(text)), include_attributes=False)
    if PurePosixPath(path).name.startswith("Dockerfile") or path.endswith(".Dockerfile"):
        text = re.sub(r"(?m)^(\s*ARG\s+APP_VERSION\s*=\s*)['\"]?" + SEMVER + r"['\"]?(\s*)$",
                      r"\1<APP_VERSION>\2", text)
    if path.endswith((".sql", ".gradle", ".py", ".yaml", ".yml")) or "Dockerfile" in path:
        text = re.sub(r"(?im)^(\s*(?:#|//|--)[^\n]*(?:所属[^\n]*版本|业务版本|business version|release version)[^\n]*)$",
                      lambda match: re.sub(SEMVER, "<RELEASE_VERSION>", match[0]), text)
    return text


def metadata_only(path: str, before: Mapping[str, str], after: Mapping[str, str]) -> bool:
    if path == "VERSION":
        return True
    old, new = before.get(path), after.get(path)
    if old is None or new is None or old == new:
        return False
    try:
        return normalized_metadata(path, old) == normalized_metadata(path, new)
    except (ValueError, SyntaxError, TypeError, AttributeError):
        return False  # Broken metadata is significant; CI must still validate it.


def is_document(path: str) -> bool:
    name = PurePosixPath(path).name.lower()
    return (path.endswith((".md", ".rst")) or path.startswith(("docs/", "tasks/", "backend/docs/", "archive/"))
            or name in ("agents.md", "readme", "license", "license.txt", "notice", "changelog"))


def is_test(path: str) -> bool:
    return ("/src/test/" in path or "/tests/" in path or "/__tests__/" in path
            or re.search(r"(?:^|/)(?:test_[^/]+|[^/]+[.](?:test|spec)[.][^/]+)$", path) is not None)


def plan_changes(changes: Sequence[str | Mapping[str, str]], before: Mapping[str, str],
                 after: Mapping[str, str], *, initial: bool = False) -> dict:
    """Pure planner API; before/after contain settings, build files and changed contents."""
    head_graph = gradle_graph(after)
    old_graph = gradle_graph(before) if before.get("backend/settings.gradle") else {}
    graph = {module: head_graph.get(module, set()) | old_graph.get(module, set())
             for module in set(head_graph) | set(old_graph)}
    deployable = deployment_modules(head_graph)
    java_code, java_direct, java_test = set(), set(), set()
    frontend_build, frontend_test = set(), set()
    python_build = python_test = deployment_config = False
    reasons, ignored = [], []
    paths = change_paths(changes)

    def reason(path, scope, explanation):
        reasons.append({"path": path, "scope": scope, "reason": explanation})

    def all_java(path, explanation):
        java_code.update(graph)
        java_direct.update(graph)
        reason(path, "java", explanation)

    def all_apps(path, explanation):
        nonlocal python_build, python_test
        all_java(path, explanation)
        frontend_build.update(FRONTENDS)
        frontend_test.update(FRONTENDS)
        python_build = python_test = True
        reason(path, "all-applications", explanation)

    if initial:
        all_apps("<initial>", "No base commit; initial explicit full application plan")
    else:
        for path in paths:
            if is_document(path):
                reason(path, "ci", "Non-compiled documentation or instructions")
                continue
            if metadata_only(path, before, after):
                ignored.append(path)
                reason(path, "metadata", "Only root release metadata changed; retain existing immutable artifacts")
                continue
            if path.startswith((".github/", ".gitea/")) or "/.github/" in path or "/.gitea/" in path:
                reason(path, "ci", "CI implementation or workflow; validate CI without rebuilding applications")
                continue
            if path.startswith(PYTHON_ROOT):
                python_test = True
                if not is_test(path) and not path.endswith("/requirements-test.txt"):
                    python_build = True
                reason(path, "python-tests" if is_test(path) or path.endswith("/requirements-test.txt") else "python",
                       "Classifier-local change; Java and frontend artifacts are reusable")
                continue
            frontend = next((name for name in FRONTENDS if path.startswith("frontends/" + name + "/")), None)
            if frontend:
                frontend_test.add(frontend)
                if not is_test(path):
                    frontend_build.add(frontend)
                reason(path, "frontend:" + frontend, "Frontend-local test change" if is_test(path) else "Frontend-local build input")
                continue
            module = next((name for name in graph if path.startswith("backend/" + name.replace(":", "/") + "/")), None)
            if module:
                java_direct.add(module)
                java_test.add(module)
                if not is_test(path):
                    java_code.add(module)
                reason(path, "java:" + module, "Module-local test input" if is_test(path) else "Module build/runtime input; follow transitive consumers")
                continue
            if (path in ("backend/build.gradle", "backend/settings.gradle", "backend/gradle.properties",
                         "backend/gradlew", "backend/gradlew.bat", "backend/.dockerignore")
                    or path.startswith(("backend/gradle/", "backend/buildSrc/", "backend/src/", "backend/resources/"))
                    or path.startswith("backend/scripts/docker/")):
                all_java(path, "Shared Java toolchain, settings, native image recipe or shared resource")
                continue
            if path.startswith("backend/scripts/testing/"):
                java_test.update(graph)
                reason(path, "java-tests", "Shared regression fixture; no runtime artifact changed")
                continue
            if path.startswith(("backend/scripts/", "scripts/", "tools/")):
                reason(path, "ci", "Operational/verification helper; no application compilation input")
                continue
            if path == "backend/build-image-manifest.txt":
                reason(path, "metadata", "Recorded image/source provenance is release metadata, not a build input")
                continue
            if path.startswith(("backend/deploy/", "deploy/", "k8s/")):
                deployment_config = True
                reason(path, "deployment-config", "Deployment configuration can reuse existing immutable application images")
                continue
            if path.startswith("frontends/"):
                frontend_build.update(FRONTENDS)
                frontend_test.update(FRONTENDS)
                reason(path, "frontends", "Unknown/shared frontend build input; conservatively include both frontends")
                continue
            if path.startswith("backend/"):
                all_java(path, "Unmapped backend business/build input; conservatively include Java deployment consumers")
                continue
            if path in (".gitignore", ".gitattributes", ".editorconfig"):
                reason(path, "ci", "Repository tooling configuration")
                continue
            all_apps(path, "Unknown repository business/build path; conservative application scope requires review")

    affected = consumers(java_code, graph)
    java_test.update(affected)
    # Removed modules cannot be built. Expose removal separately so deployment
    # automation can review it instead of inventing an image for a deleted path.
    removed = deployment_modules(old_graph) - deployable
    images = sorted(module.split(":")[-1] for module in affected & deployable)
    test_modules = sorted(java_test & set(head_graph))
    any_build = bool(images or frontend_build or python_build)
    requires_promoted_baseline = bool(
        any_build or test_modules or frontend_test or python_test or deployment_config)
    return {
        "schema_version": 1, "initial": initial,
        "java_images": images, "java_changed_modules": sorted(java_direct),
        "java_test_modules": test_modules, "java_tests": bool(test_modules),
        "removed_java_images": sorted(module.split(":")[-1] for module in removed),
        "frontends": sorted(frontend_build), "frontend_tests": sorted(frontend_test),
        "python_changed": python_build, "python_tests": python_test,
        "runtime_smoke": any_build or deployment_config,
        "config_only": deployment_config and not any_build,
        "requires_promoted_baseline": requires_promoted_baseline,
        "ci_validation": bool(paths) or initial,
        "changed_paths": paths, "ignored_metadata": sorted(ignored), "reasons": reasons,
    }


def git(root: Path, *args: str, check=True) -> subprocess.CompletedProcess:
    result = subprocess.run(["git", "-C", str(root), *args], capture_output=True, check=False)
    if check and result.returncode:
        raise ValueError("Git read failed: " + " ".join(args[:2]))
    return result


def commit(root: Path, revision: str) -> str:
    return git(root, "rev-parse", "--verify", "--end-of-options", revision + "^{commit}").stdout.decode().strip()


def diff_changes(root: Path, base: str, head: str) -> list[dict[str, str]]:
    fields = git(root, "diff", "--name-status", "-z", "--find-renames", base, head, "--").stdout.decode("utf-8").split("\0")
    result, index = [], 0
    while index < len(fields) and fields[index]:
        status, path = fields[index:index + 2]
        index += 2
        record = {"status": status, "path": path}
        if status.startswith(("R", "C")):
            record["old_path"], record["path"] = path, fields[index]
            index += 1
        result.append(record)
    return result


def read_tree(root: Path, revision: str | None, changed_paths: Sequence[str]) -> dict[str, str]:
    if not revision:
        return {}
    listing = git(root, "ls-tree", "-r", "--name-only", "-z", revision).stdout.decode("utf-8").split("\0")
    wanted = set(changed_paths) | {path for path in listing if path.startswith("backend/") and path.endswith("build.gradle")}
    wanted.add("backend/settings.gradle")
    available = set(listing)
    return {path: git(root, "show", revision + ":" + path).stdout.decode("utf-8", errors="replace")
            for path in sorted(wanted & available)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", help="base commit; all-zero SHA or a root commit without a parent selects initial full plan")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--repo", type=Path, default=ROOT)
    parser.add_argument("--files-json", help="JSON array or JSON file overriding changed paths; contents still come from base/head")
    parser.add_argument("--output", type=Path, help="optional plan JSON file; JSON is always printed to stdout")
    args = parser.parse_args()
    try:
        root = args.repo.resolve()
        head = commit(root, args.head)
        initial = bool(args.base and re.fullmatch("0+", args.base))
        if initial:
            base = None
        elif args.base:
            base = commit(root, args.base)
        else:
            parent = git(root, "rev-parse", "--verify", head + "^", check=False)
            base = parent.stdout.decode().strip() if parent.returncode == 0 else None
            initial = base is None
        if args.files_json:
            raw = args.files_json.strip()
            changes = json.loads(raw if raw.startswith("[") else Path(raw).read_text(encoding="utf-8"))
            if not isinstance(changes, list):
                raise ValueError("--files-json must contain an array")
        else:
            changes = diff_changes(root, base, head) if base else []
        paths = change_paths(changes)
        result = plan_changes(changes, read_tree(root, base, paths), read_tree(root, head, paths), initial=initial)
        result.update({"base": base, "head": head})
        encoded = json.dumps(result, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
        if args.output:
            args.output.write_text(encoded, encoding="utf-8")
        print(encoded, end="")
        return 0
    except (ValueError, OSError) as error:
        print("Affected planner failed: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
