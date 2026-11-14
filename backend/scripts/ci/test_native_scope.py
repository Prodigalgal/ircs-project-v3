"""CI-only matrix and runtime fixture scope regressions."""
import importlib.util
import io
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
    def test_incremental_promotion_survives_skipped_unrelated_matrices_and_keeps_success_gates(self):
        workflow = (Path(__file__).resolve().parents[3] / '.github/workflows/native-build.yml').read_text()
        job = workflow.split('\n  gitops:\n', 1)[1].split('\n    runs-on:', 1)[0]
        condition = next(line.strip()[4:] for line in job.splitlines() if line.strip().startswith('if: '))
        self.assertTrue(condition.startswith('always() && !cancelled() &&'))
        for gate in ("github.ref == 'refs/heads/main'", "needs.prepare.result == 'success'",
                     "needs.prepare.outputs.has_selected == 'true'", "needs.manifests.result == 'success'"):
            self.assertIn(gate, condition)
        self.assertNotIn('||', condition)

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

    def test_aggregation_only_runs_its_http_probe_with_disposable_authentication(self):
        self.smoke.selected_services = {'ircs-aggregation-worker'}
        starts = []
        probes = []
        self.smoke.start = lambda service, values, **kwargs: starts.append((service, values))
        self.smoke.stop = lambda *args: []
        self.smoke.aggregation_evaluation_probe = lambda: probes.append(True)

        self.smoke.main()

        self.assertEqual(['ircs-aggregation-worker'], [service for service, _ in starts])
        self.assertEqual([True], probes)
        values = starts[0][1]
        self.assertEqual('true', values['APP_AGGREGATION_INTERNAL_ACCESS_REQUIRE_TOKEN'])
        self.assertEqual(self.smoke.password, values['APP_AGGREGATION_INTERNAL_ACCESS_TOKEN'])
        self.assertEqual('aggregation:maintenance', values['APP_AGGREGATION_INTERNAL_ACCESS_REQUIRED_SCOPE'])
        self.assertEqual('3600000', values['APP_AGGREGATION_WORK_QUEUE_WORKER_INITIAL_DELAY_MS'])
        self.assertNotIn('APP_AGGREGATION_WORK_QUEUE_WORKER_ENABLED', values)
        self.assertNotIn('APP_AGGREGATION_AUTO_UNIFIED_MERGE_ENABLED', values)

    def aggregation_http(self, requests):
        def respond(request, timeout):
            payload = json.loads(request.data)
            requests.append((request, payload))
            self.assertEqual('POST', request.method)
            self.assertEqual('/internal/v1/aggregation/evaluate', request.selector)
            headers = {key.lower(): value for key, value in request.header_items()}
            if len(requests) == 1:
                self.assertNotIn('x-ircs-service-token', headers)
                raise self.smoke.HTTPError(request.full_url, 401, 'Unauthorized', {}, io.BytesIO())
            self.assertEqual(self.smoke.password, headers['x-ircs-service-token'])
            self.assertEqual('aggregation:maintenance', headers['x-ircs-service-scopes'])
            if not payload['rawVideoIds']:
                raise self.smoke.HTTPError(request.full_url, 400, 'Bad Request', {}, io.BytesIO())
            response = io.BytesIO(json.dumps({'scope': 'SNAPSHOT_ONLY', 'autoUnifiedMergeEnabled': False,
                                             'decisions': [{'rawVideoId': payload['rawVideoIds'][0],
                                                            'decision': 'MISSING'}]}).encode())
            response.status = 200
            return response
        return respond

    def test_aggregation_http_probe_authenticates_and_compares_nonzero_read_only_counts(self):
        requests = []
        with patch.object(self.smoke, 'docker', return_value='7|11|9') as database, \
                patch.object(self.smoke, 'urlopen', side_effect=self.aggregation_http(requests)):
            self.smoke.aggregation_evaluation_probe()

        self.assertEqual(4, len(requests))
        single = requests[1][1]['rawVideoIds']
        self.assertEqual(single * 2, requests[2][1]['rawVideoIds'])
        self.assertEqual([], requests[3][1]['rawVideoIds'])
        self.assertEqual(2, database.call_count)
        for invocation in database.call_args_list:
            sql = invocation.args[-1]
            self.assertTrue(sql.lower().startswith('select '))
            self.assertIn('count(*) from unified_videos', sql)
            self.assertIn('count(*) from raw_videos', sql)
            self.assertIn('count(*) from raw_video_unified_video', sql)

    def test_aggregation_http_probe_rejects_any_core_row_count_change(self):
        requests = []
        with patch.object(self.smoke, 'docker', side_effect=['7|11|9', '7|12|9']), \
                patch.object(self.smoke, 'urlopen', side_effect=self.aggregation_http(requests)):
            with self.assertRaisesRegex(AssertionError, 'preserve raw, unified and binding counts'):
                self.smoke.aggregation_evaluation_probe()

    def test_aggregation_failure_never_prints_startup_token_arguments(self):
        self.smoke.selected_services = {'ircs-aggregation-worker'}
        output = io.StringIO()
        self.smoke.start = lambda *args, **kwargs: (_ for _ in ()).throw(
            RuntimeError('docker startup arguments token=' + self.smoke.password))
        self.smoke.stop = lambda *args: []
        with patch('sys.stdout', output):
            with self.assertRaises(AssertionError) as error:
                self.smoke.main()
        self.assertNotIn(self.smoke.password, output.getvalue())
        self.assertNotIn(self.smoke.password, str(error.exception))


if __name__ == '__main__':
    unittest.main()
