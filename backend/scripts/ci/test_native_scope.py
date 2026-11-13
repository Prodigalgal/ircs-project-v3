"""CI-only matrix and runtime fixture scope regressions."""
import importlib.util
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


matrix = load('native_matrix', 'native-matrix.py')
SETTINGS = "include 'services:ircs-api-gateway'\ninclude 'services:ircs-ops-service'\ninclude 'platform:ircs-migrator'\n"


class NativeMatrixTest(unittest.TestCase):
    def test_empty_plan_means_empty_application_matrices(self):
        result = matrix.outputs_for({}, SETTINGS)
        self.assertFalse(result['has_selected'])
        self.assertEqual({'include': []}, result['matrix'])
        self.assertEqual([], result['selected_images'])

    def test_one_service_produces_only_two_native_architecture_entries(self):
        result = matrix.outputs_for({'java_images': ['ircs-api-gateway'], 'java_test_modules': ['services:ircs-api-gateway']}, SETTINGS)
        self.assertEqual(['ircs-api-gateway'], result['images'])
        self.assertEqual(2, len(result['matrix']['include']))
        self.assertFalse(result['python_changed'])
        self.assertEqual([], result['frontends'])

    def test_frontend_and_python_do_not_populate_java_matrix(self):
        result = matrix.outputs_for({'frontends': ['admin'], 'python_changed': True}, SETTINGS)
        self.assertEqual([], result['images'])
        self.assertEqual(['ircs-admin-frontend', 'ircs-adult-classifier-service'], sorted(result['selected_images']))
        self.assertEqual(2, len(result['frontend_matrix']['include']))

    def test_force_scope_adds_without_losing_unpromoted_application_changes(self):
        result = matrix.outputs_for({'java_images': ['ircs-api-gateway']}, SETTINGS, scope='ircs-ops-service')
        self.assertEqual(['ircs-api-gateway', 'ircs-ops-service'], result['images'])

    def test_baseline_maps_keep_old_component_versions(self):
        baseline = {'images': {'ircs-migrator': {'image': 'registry/migrator:old', 'version': '3.2.2'}}}
        result = matrix.outputs_for({}, SETTINGS, baseline)
        self.assertEqual({'ircs-migrator': '3.2.2'}, result['module_versions'])
        self.assertEqual({'ircs-migrator': 'registry/migrator:old'}, result['image_refs'])


class NativeRuntimeScopeTest(unittest.TestCase):
    def setUp(self):
        env = {'REGISTRY': 'registry', 'SOURCE_REVISION': 'a' * 40, 'RELEASE_TAG': 'new', 'APP_VERSION': '3.2.3',
               'NATIVE_ARCH': 'arm64', 'CI_PASSWORD': 'disposable-fixture', 'JAVA_IMAGES': '["ircs-api-gateway"]',
               'IMAGE_REFS_JSON': '{"ircs-ops-service":"registry/ops:old"}',
               'MODULE_VERSIONS_JSON': '{"ircs-ops-service":"3.2.2"}'}
        with patch.dict(os.environ, env):
            self.smoke = load('native_smoke', 'native-consumer-smoke.py')

    def test_selected_uses_new_arch_image_and_version(self):
        self.assertEqual('registry/ircs-api-gateway:new-arm64', self.smoke.image('ircs-api-gateway'))
        self.assertEqual('3.2.3', self.smoke.version_for('ircs-api-gateway'))

    def test_unchanged_fixture_uses_baseline_manifest_and_version(self):
        self.assertEqual('registry/ops:old', self.smoke.image('ircs-ops-service'))
        self.assertEqual('3.2.2', self.smoke.version_for('ircs-ops-service'))

    def test_missing_baseline_never_invents_candidate_image(self):
        with self.assertRaises(AssertionError):
            self.smoke.image('ircs-identity-service')

    def test_gateway_only_does_not_start_unselected_listener_cases(self):
        starts = []
        self.smoke.start = lambda service, values, **kwargs: starts.append(service)
        self.smoke.stop = lambda *args: []
        self.smoke.gateway_config_cache_probe = lambda: None
        self.smoke.gateway_forwarding_probe = lambda backend: None
        self.smoke.main()
        self.assertEqual(['ircs-api-gateway', 'ircs-ops-service', 'ircs-identity-service'], starts)
        self.assertNotIn('ircs-ingestion-worker', starts)
        self.assertNotIn('ircs-content-service', starts)


if __name__ == '__main__':
    unittest.main()
