"""CI-only unit regression for the pure affected planner; no application builds."""

from copy import deepcopy
import importlib.util
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("affected_plan", Path(__file__).with_name("affected-plan.py"))
planner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(planner)


def fixture():
    modules = ["shared:ircs-contracts", "shared:ircs-common", "shared:ircs-messaging", "shared:ircs-unused",
               "services:ircs-gateway", "services:ircs-catalog", "services:ircs-ingest", "platform:ircs-migrator"]
    result = {"backend/settings.gradle": "\n".join("include '" + module + "'" for module in modules),
              "backend/build.gradle": """
                  plugins { id 'example.plugin' version '1.0.0' }
                  configure(serviceProjects) {
                      dependencies { implementation project(':shared:ircs-common') }
                  }
                  configure(serviceProjects + project(':platform:ircs-migrator')) { nativeOptions {} }
              """}
    for module in modules:
        result["backend/" + module.replace(":", "/") + "/build.gradle"] = "dependencies {}"
    result["backend/shared/ircs-common/build.gradle"] = "dependencies { api project(':shared:ircs-contracts') }"
    result["backend/shared/ircs-messaging/build.gradle"] = "dependencies { api project(':shared:ircs-contracts') }"
    result["backend/services/ircs-ingest/build.gradle"] = "dependencies { implementation project(':shared:ircs-messaging') }"
    return result


class AffectedPlanTest(unittest.TestCase):
    def setUp(self):
        self.before = fixture()
        self.after = deepcopy(self.before)

    def plan(self, *paths):
        return planner.plan_changes(list(paths), self.before, self.after)

    def changed_content(self, path, before, after):
        self.before[path], self.after[path] = before, after
        return self.plan(path)

    def assert_no_builds(self, result):
        self.assertEqual([], result["java_images"])
        self.assertEqual([], result["frontends"])
        self.assertFalse(result["python_changed"])

    def test_service_source_builds_only_that_service(self):
        result = self.plan("backend/services/ircs-gateway/src/main/java/Gateway.java")
        self.assertEqual(["ircs-gateway"], result["java_images"])
        self.assertEqual(["services:ircs-gateway"], result["java_changed_modules"])
        self.assertEqual(["services:ircs-gateway"], result["java_test_modules"])
        self.assertEqual([], result["frontends"])
        self.assertFalse(result["python_changed"])

    def test_shared_dependency_follows_transitive_consumers_without_migrator(self):
        result = self.plan("backend/shared/ircs-contracts/src/main/java/Message.java")
        self.assertEqual(["ircs-catalog", "ircs-gateway", "ircs-ingest"], result["java_images"])
        self.assertIn("shared:ircs-messaging", result["java_test_modules"])
        self.assertNotIn("platform:ircs-migrator", result["java_test_modules"])

    def test_shared_messaging_affects_only_declared_service_consumers(self):
        result = self.plan("backend/shared/ircs-messaging/src/main/java/Rabbit.java")
        self.assertEqual(["ircs-ingest"], result["java_images"])

    def test_combined_root_configure_targets_apply_real_dependencies(self):
        self.after["backend/build.gradle"] += """
            configure(serviceProjects + project(':platform:ircs-migrator')) {
                dependencies { implementation project(':shared:ircs-unused') }
            }
        """
        result = self.plan("backend/shared/ircs-unused/src/main/java/NowUsed.java")
        self.assertEqual(["ircs-catalog", "ircs-gateway", "ircs-ingest", "ircs-migrator"], result["java_images"])

    def test_unused_shared_library_runs_its_tests_without_any_image(self):
        result = self.plan("backend/shared/ircs-unused/src/main/java/Unused.java")
        self.assert_no_builds(result)
        self.assertEqual(["shared:ircs-unused"], result["java_test_modules"])

    def test_java_test_only_change_has_no_application_build(self):
        result = self.plan("backend/services/ircs-gateway/src/test/java/GatewayTest.java")
        self.assert_no_builds(result)
        self.assertTrue(result["java_tests"])
        self.assertFalse(result["runtime_smoke"])

    def test_shared_test_only_does_not_rebuild_consumers(self):
        result = self.plan("backend/shared/ircs-common/src/test/java/CommonTest.java")
        self.assert_no_builds(result)
        self.assertEqual(["shared:ircs-common"], result["java_test_modules"])

    def test_admin_and_huawai_are_independent(self):
        for frontend in ("admin", "huawai"):
            with self.subTest(frontend=frontend):
                result = self.plan("frontends/" + frontend + "/src/page.tsx")
                self.assertEqual([frontend], result["frontends"])
                self.assertEqual([frontend], result["frontend_tests"])
                self.assertEqual([], result["java_images"])
                self.assertFalse(result["python_changed"])

    def test_frontend_tests_only_do_not_build_image(self):
        result = self.plan("frontends/admin/tests/videos.test.mjs")
        self.assert_no_builds(result)
        self.assertEqual(["admin"], result["frontend_tests"])

    def test_python_source_is_independent(self):
        result = self.plan(planner.PYTHON_ROOT + "app/classifier.py")
        self.assertTrue(result["python_changed"])
        self.assertTrue(result["python_tests"])
        self.assertEqual([], result["java_images"])
        self.assertEqual([], result["frontends"])

    def test_python_tests_and_test_dependencies_do_not_build_image(self):
        result = self.plan(planner.PYTHON_ROOT + "tests/test_http.py", planner.PYTHON_ROOT + "requirements-test.txt")
        self.assert_no_builds(result)
        self.assertTrue(result["python_tests"])

    def test_docs_and_workflows_only_validate_ci(self):
        result = self.plan("AGENTS.md", "backend/docs/reports/check.md", ".github/workflows/native-build.yml",
                           "frontends/admin/.github/workflows/build.yml", ".github/scripts/affected-plan.py")
        self.assert_no_builds(result)
        self.assertTrue(result["ci_validation"])
        self.assertFalse(result["java_tests"])
        self.assertFalse(result["runtime_smoke"])

    def test_package_root_version_only_is_ignored(self):
        result = self.changed_content("frontends/admin/package.json",
                                      '{"version":"3.2.1","dependencies":{"react":"19.3.1"}}',
                                      '{"version":"3.2.2","dependencies":{"react":"19.3.1"}}')
        self.assert_no_builds(result)
        self.assertEqual(["frontends/admin/package.json"], result["ignored_metadata"])

    def test_lockfile_only_root_versions_are_ignored(self):
        old = {"version": "3.2.1", "packages": {"": {"version": "3.2.1"}, "node_modules/react": {"version": "19.3.1"}}}
        new = deepcopy(old)
        new["version"] = new["packages"][""]["version"] = "3.2.2"
        result = self.changed_content("frontends/admin/package-lock.json", json.dumps(old), json.dumps(new))
        self.assert_no_builds(result)

    def test_dependency_versions_are_not_ignored_in_package_or_lock(self):
        for filename, old, new in (
            ("package.json", {"version": "3.2.1", "dependencies": {"react": "19.3.0"}},
             {"version": "3.2.2", "dependencies": {"react": "19.3.1"}}),
            ("package-lock.json", {"version": "3.2.1", "packages": {"node_modules/react": {"version": "19.3.0"}}},
             {"version": "3.2.2", "packages": {"node_modules/react": {"version": "19.3.1"}}}),
        ):
            with self.subTest(filename=filename):
                result = self.changed_content("frontends/admin/" + filename, json.dumps(old), json.dumps(new))
                self.assertEqual(["admin"], result["frontends"])

    def test_docker_arg_version_only_is_ignored_for_every_stack(self):
        for path in ("backend/scripts/docker/Dockerfile.native", "frontends/admin/Dockerfile",
                     "frontends/huawai/Dockerfile", planner.PYTHON_ROOT + "Dockerfile"):
            with self.subTest(path=path):
                result = self.changed_content(path, "FROM example:1\nARG APP_VERSION=3.2.1\n",
                                              "FROM example:1\nARG APP_VERSION=3.2.2\n")
                self.assert_no_builds(result)

    def test_docker_base_image_change_is_significant(self):
        result = self.changed_content("frontends/admin/Dockerfile", "FROM node:25\nARG APP_VERSION=3.2.1\n",
                                      "FROM node:26\nARG APP_VERSION=3.2.2\n")
        self.assertEqual(["admin"], result["frontends"])

    def test_fastapi_default_version_only_is_ignored(self):
        result = self.changed_content(planner.PYTHON_ROOT + "app/main.py",
                                      'app = FastAPI(version=os.getenv("APP_VERSION", "3.2.1"), title="Classifier")',
                                      'app = FastAPI(version=os.getenv("APP_VERSION", "3.2.2"), title="Classifier")')
        self.assert_no_builds(result)

    def test_fastapi_behavior_change_is_not_hidden_by_version(self):
        result = self.changed_content(planner.PYTHON_ROOT + "app/main.py",
                                      'app = FastAPI(version=os.getenv("APP_VERSION", "3.2.1"), title="Old")',
                                      'app = FastAPI(version=os.getenv("APP_VERSION", "3.2.2"), title="New")')
        self.assertTrue(result["python_changed"])

    def test_migration_release_comment_only_is_ignored_but_sql_is_not(self):
        path = "backend/platform/ircs-migrator/src/main/resources/db/changelog/001.sql"
        result = self.changed_content(path, "-- 所属业务版本 3.2.1\nCREATE INDEX a ON t(id)\n",
                                      "-- 所属业务版本 3.2.2\nCREATE INDEX a ON t(id)\n")
        self.assert_no_builds(result)
        self.after[path] = "-- 所属业务版本 3.2.2\nCREATE INDEX b ON t(name)\n"
        self.assertEqual(["ircs-migrator"], self.plan(path)["java_images"])

    def test_root_version_and_recorded_image_manifest_do_not_rebuild(self):
        result = self.plan("VERSION", "backend/build-image-manifest.txt")
        self.assert_no_builds(result)
        self.assertTrue(result["ci_validation"])

    def test_gradle_and_wrapper_native_recipe_changes_affect_all_java_only(self):
        for path in ("backend/build.gradle", "backend/gradle/libs.versions.toml", "backend/gradlew",
                     "backend/scripts/docker/Dockerfile.native", "backend/resources/shared.properties"):
            with self.subTest(path=path):
                result = self.plan(path)
                self.assertEqual(["ircs-catalog", "ircs-gateway", "ircs-ingest", "ircs-migrator"], result["java_images"])
                self.assertEqual([], result["frontends"])
                self.assertFalse(result["python_changed"])

    def test_real_module_dependency_change_rebuilds_that_service(self):
        path = "backend/services/ircs-gateway/build.gradle"
        self.after[path] = "dependencies { implementation 'example:dependency:2.0.0' }"
        self.assertEqual(["ircs-gateway"], self.plan(path)["java_images"])

    def test_deleted_source_rebuilds_its_owner(self):
        result = self.plan({"status": "D", "path": "backend/services/ircs-gateway/src/main/java/Removed.java"})
        self.assertEqual(["ircs-gateway"], result["java_images"])

    def test_rename_rebuilds_both_previous_and_new_owner(self):
        result = self.plan({"status": "R100", "old_path": "backend/services/ircs-gateway/src/main/java/Move.java",
                            "path": "backend/services/ircs-catalog/src/main/java/Move.java"})
        self.assertEqual(["ircs-catalog", "ircs-gateway"], result["java_images"])

    def test_renaming_business_code_to_docs_does_not_hide_removal(self):
        result = self.plan({"status": "R100", "old_path": "backend/services/ircs-gateway/src/main/java/Move.java",
                            "path": "docs/Move.md"})
        self.assertEqual(["ircs-gateway"], result["java_images"])

    def test_old_dependency_graph_is_preserved_for_removed_edges(self):
        self.after["backend/services/ircs-ingest/build.gradle"] = "dependencies {}"
        result = self.plan("backend/shared/ircs-messaging/src/main/java/Removed.java")
        self.assertEqual(["ircs-ingest"], result["java_images"])

    def test_removed_module_is_exposed_without_building_nonexistent_path(self):
        self.after["backend/settings.gradle"] = self.after["backend/settings.gradle"].replace("include 'services:ircs-catalog'", "")
        del self.after["backend/services/ircs-catalog/build.gradle"]
        result = self.plan("backend/settings.gradle", "backend/services/ircs-catalog/build.gradle")
        self.assertNotIn("ircs-catalog", result["java_images"])
        self.assertEqual(["ircs-catalog"], result["removed_java_images"])

    def test_initial_plan_is_explicitly_full(self):
        result = planner.plan_changes([], {}, self.after, initial=True)
        self.assertTrue(result["initial"])
        self.assertEqual(4, len(result["java_images"]))
        self.assertEqual(["admin", "huawai"], result["frontends"])
        self.assertTrue(result["python_changed"])

    def test_deployment_configuration_can_reuse_all_application_images(self):
        result = self.plan("backend/deploy/k8s/prod/gateway.yaml")
        self.assert_no_builds(result)
        self.assertTrue(result["config_only"])
        self.assertTrue(result["runtime_smoke"])

    def test_unknown_business_code_is_conservative_and_explained(self):
        result = self.plan("new-business/processor.rs")
        self.assertEqual(4, len(result["java_images"]))
        self.assertTrue(result["python_changed"])
        self.assertTrue(any(reason["scope"] == "all-applications" for reason in result["reasons"]))

    def test_gradle_comments_do_not_invent_dependencies(self):
        self.after["backend/services/ircs-gateway/build.gradle"] += "\n// implementation project(':shared:not-a-module')\n"
        result = self.plan("backend/shared/ircs-messaging/src/main/java/Rabbit.java")
        self.assertEqual(["ircs-ingest"], result["java_images"])

    def test_git_name_status_nul_parser_handles_deletions_and_renames(self):
        output = b"D\x00old.java\x00R100\x00first path.java\x00second path.java\x00"
        with patch.object(planner, "git", return_value=subprocess.CompletedProcess([], 0, stdout=output)):
            result = planner.diff_changes(Path("."), "base", "head")
        self.assertEqual([{"status": "D", "path": "old.java"},
                          {"status": "R100", "old_path": "first path.java", "path": "second path.java"}], result)

    def test_invalid_paths_and_missing_graph_fail_closed(self):
        with self.assertRaises(ValueError):
            self.plan("../outside.java")
        with self.assertRaises(ValueError):
            planner.plan_changes([], {}, {})


if __name__ == "__main__":
    unittest.main()
