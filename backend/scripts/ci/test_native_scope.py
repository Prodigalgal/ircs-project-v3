"""CI-only matrix and runtime fixture scope regressions."""
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import uuid


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

    def test_magnet_only_builds_its_two_native_architectures(self):
        result = matrix.outputs_for({'java_images': ['ircs-magnet-service'],
                                     'java_test_modules': ['services:ircs-magnet-service']},
                                    SETTINGS + "include 'services:ircs-magnet-service'\n")
        self.assertEqual(['ircs-magnet-service'], result['images'])
        self.assertEqual(['ircs-magnet-service'], result['selected_images'])
        self.assertEqual(2, len(result['matrix']['include']))

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

    def test_metadata_and_normalization_do_not_require_search_infrastructure(self):
        self.smoke.selected_services = {'ircs-metadata-worker', 'ircs-normalization-worker'}
        self.assertFalse(self.smoke.requires_search_fixture())

    def test_search_keeps_its_real_elasticsearch_fixture_when_selected_alone_or_together(self):
        for selected in ({'ircs-search-service'}, {'ircs-search-service', 'ircs-metadata-worker'}):
            self.smoke.selected_services = selected
            self.assertTrue(self.smoke.requires_search_fixture())

    def test_baseline_ops_and_identity_forwarding_fixtures_do_not_add_a_search_dependency(self):
        self.smoke.selected_services = {'ircs-api-gateway', 'ircs-ops-service', 'ircs-identity-service'}
        self.assertFalse(self.smoke.requires_search_fixture())

    def test_magnet_only_runs_its_functional_probe_without_other_application_fixtures(self):
        self.smoke.selected_services = {'ircs-magnet-service'}
        starts, probes = [], []
        self.smoke.start = lambda service, values, **kwargs: starts.append((service, values))
        self.smoke.stop = lambda *args: []
        self.smoke.magnet_search_round_trip = lambda: probes.append(True)

        self.smoke.main()

        self.assertEqual(['ircs-magnet-service'], [service for service, _ in starts])
        self.assertEqual([True], probes)
        self.assertFalse(self.smoke.requires_search_fixture())
        values = starts[0][1]
        self.assertEqual('native-smoke', values['SPRING_PROFILES_ACTIVE'])
        self.assertEqual('true', values['APP_MAGNET_FIXTURE_ENABLED'])
        for key in ('APP_MAGNET_AUTO_SEARCH_ENABLED', 'APP_MAGNET_RECOVERY_ENABLED', 'APP_MAGNET_REAL_PROVIDER_ENABLED'):
            self.assertEqual('false', values[key])
        self.assertEqual('3600000', values['APP_MAGNET_AUTO_SEARCH_INITIAL_DELAY_MS'])
        self.assertIn('ApplicationName=native-magnet-smoke', values['SPRING_DATASOURCE_URL'])

    def test_signed_identity_binds_post_method(self):
        with tempfile.TemporaryDirectory() as directory:
            key_file = Path(directory) / 'signing-key.der'
            with patch.dict(os.environ, {'CI_GATEWAY_SIGNING_KEY_FILE': str(key_file)}), \
                    patch.object(self.smoke.subprocess, 'check_output', return_value=b'probe-signature'):
                self.smoke.signed_identity_headers('/api/v1/magnets/search/unified/probe', method='POST')
            canonical = key_file.with_name('message.bin').read_text().splitlines()
        self.assertEqual('IRCS-GATEWAY-IDENTITY-V1', canonical[0])
        self.assertEqual('POST', self.smoke.base64.urlsafe_b64decode(canonical[1] + '===').decode())

    def test_authenticated_request_signs_the_actual_post_method(self):
        self.smoke.current_service = 'ircs-magnet-service'
        path = '/api/v1/magnets/search/unified/probe'
        with patch.object(self.smoke, 'signed_identity_headers', return_value={'X-IRCS-Gateway-Signature': 'probe'}) as sign, \
                patch.object(self.smoke, 'urlopen', return_value=io.BytesIO(b'{"id":"probe"}')) as http:
            self.assertEqual({'id': 'probe'}, self.smoke.request(path, {}, authenticated=True))
        sign.assert_called_once_with(path, method='POST')
        request = http.call_args.args[0]
        self.assertEqual('POST', request.get_method())
        self.assertEqual(b'{}', request.data)

    def magnet_runtime(self, durable_status='SUCCESS', candidate_status='CANDIDATE', queue_error=''):
        fixture_ids = [uuid.UUID(int=value) for value in (1, 2, 3)]
        unified_id, provider_id, job_id = map(str, fixture_ids)
        provider_code = 'native_fixture_' + provider_id.replace('-', '')
        task_id = 'magnet.search:' + job_id
        prefix = 'ircs:work:v1:magnet.search'
        state, lanes, sql_calls, api_calls = {}, {}, [], []
        released = False

        def respond(*args):
            nonlocal released
            if args[1] == 'ircs-native-postgres':
                statement = args[-1]
                sql_calls.append(statement)
                if "'connections'" in statement:
                    return json.dumps({'connections': 2, 'transactions': 0})
                if statement.startswith('select json_build_object'):
                    queries = {'IMDB': 'tt1234567', 'TITLE_YEAR': 'IRCS dev-safe fixture S01 2024'}
                    runs = [{'external_id_type': query, 'external_id_value': value, 'provider_code': provider_code,
                             'status': 'SUCCESS', 'candidate_count': 1,
                             'accepted_count': 1, 'request_url': 'fixture://native-smoke/' + query}
                            for query, value in queries.items()]
                    links = [{'status': candidate_status, 'provider_code': provider_code,
                              'provider_evidence': {'mode': 'fixture', 'identityDisposition': 'CANDIDATE'},
                              'info_hash': letter * 40, 'magnet_uri': 'magnet:?xt=urn:btih:' + letter.lower() * 40}
                             for letter in ('A', 'B')]
                    return json.dumps({'job': {'status': durable_status, 'finished_at': '2026-11-16T00:00:00Z',
                                              'provider_codes': [provider_code], 'total_candidates': 2, 'accepted_count': 2},
                                       'jobCount': 1, 'runs': runs, 'links': links})
                return ''
            command = list(args[3:])
            if command[0] == '--json':
                self.assertEqual('HMGET', command[1])
                return json.dumps([state.get(field) for field in command[3:]])
            if command[0] == 'HSET':
                state.update(zip(command[2::2], command[3::2]))
            elif command[0] == 'ZADD':
                lanes[command[1]] = command[2]
                if command[1].endswith(':pending'):
                    released = True
            elif command[0] == 'ZREM':
                lanes.pop(command[1], None)
            elif command[0] == 'ZSCORE':
                return lanes.get(command[1], '')
            elif command[0] == 'ZCARD':
                return '1' if command[1] in lanes else '0'
            elif command[0] == 'HGET':
                if command[2] == 'lastError':
                    return queue_error
                if released:
                    state.update({'status': 'SUCCESS', 'attempt': '1', 'ownerId': ''})
                    lanes.clear()
                return state.get(command[2], '')
            return ''

        def api(path, data=None, authenticated=False):
            api_calls.append((path, data, authenticated))
            return {'id': job_id} if data is not None else {'status': 'UP'}

        return fixture_ids, respond, api, state, sql_calls, api_calls, prefix, task_id

    def test_magnet_probe_exercises_durable_worker_completion_and_protects_existing_claim(self):
        fixture_ids, runtime, api, state, sql_calls, api_calls, prefix, task_id = self.magnet_runtime()
        with patch.object(uuid, 'uuid4', side_effect=fixture_ids), \
                patch.object(self.smoke, 'docker', side_effect=runtime) as docker, \
                patch.object(self.smoke, 'request', side_effect=api):
            self.smoke.magnet_search_round_trip()

        self.assertEqual(2, len([call for call in api_calls if call[1] == {} and call[2]]))
        seed = sql_calls[0]
        self.assertIn('UPDATE magnet_providers SET enabled=false', seed)
        self.assertIn("'2024',1,'series','tt1234567','SYNCED'", seed)
        self.assertIn("'FIXTURE'", seed)
        self.assertIn('["IMDB","TITLE_YEAR"]', seed)
        self.assertIn('pg_stat_activity', sql_calls[-2])
        self.assertIn('DELETE FROM unified_videos', sql_calls[-1])
        self.assertTrue(any(call.args[-2:] == ('DEL', prefix + ':task:' + task_id) for call in docker.call_args_list))
        self.assertEqual('SUCCESS', state['status'])

    def test_magnet_probe_rejects_duplicate_publish_that_resets_claim_and_cleans_up(self):
        fixture_ids, runtime, api, state, sql_calls, *_ = self.magnet_runtime()

        def resetting_api(*args, **kwargs):
            response = api(*args, **kwargs)
            state['ownerId'] = ''
            return response

        with patch.object(uuid, 'uuid4', side_effect=fixture_ids), \
                patch.object(self.smoke, 'docker', side_effect=runtime), \
                patch.object(self.smoke, 'request', side_effect=resetting_api):
            with self.assertRaisesRegex(AssertionError, 'reset the protected queue claim'):
                self.smoke.magnet_search_round_trip()
        self.assertIn('DELETE FROM unified_videos', sql_calls[-1])

    def test_magnet_probe_rejects_queue_success_without_durable_job_success(self):
        fixture_ids, runtime, api, *_ = self.magnet_runtime(durable_status='FAILED')
        with patch.object(uuid, 'uuid4', side_effect=fixture_ids), \
                patch.object(self.smoke, 'docker', side_effect=runtime), \
                patch.object(self.smoke, 'request', side_effect=api):
            with self.assertRaisesRegex(AssertionError, 'job must persist SUCCESS'):
                self.smoke.magnet_search_round_trip()

    def test_magnet_probe_rejects_unverified_fixture_auto_approval(self):
        fixture_ids, runtime, api, *_ = self.magnet_runtime(candidate_status='APPROVED')
        with patch.object(uuid, 'uuid4', side_effect=fixture_ids), \
                patch.object(self.smoke, 'docker', side_effect=runtime), \
                patch.object(self.smoke, 'request', side_effect=api):
            with self.assertRaisesRegex(AssertionError, 'must remain valid CANDIDATE'):
                self.smoke.magnet_search_round_trip()

    def test_magnet_probe_reports_first_worker_failure_without_waiting_for_retries(self):
        fixture_ids, runtime, api, *_ = self.magnet_runtime(queue_error='Native JDBC parameter failure')
        with patch.object(uuid, 'uuid4', side_effect=fixture_ids), \
                patch.object(self.smoke, 'docker', side_effect=runtime), \
                patch.object(self.smoke, 'request', side_effect=api), patch.object(self.smoke.time, 'sleep') as sleep:
            with self.assertRaisesRegex(AssertionError, 'Native magnet task failed: Native JDBC parameter failure'):
                self.smoke.magnet_search_round_trip()
        sleep.assert_not_called()

    def test_magnet_failure_never_prints_startup_token_arguments(self):
        self.smoke.selected_services = {'ircs-magnet-service'}
        output = io.StringIO()
        self.smoke.start = lambda *args, **kwargs: (_ for _ in ()).throw(
            RuntimeError('docker startup arguments token=' + self.smoke.password))
        self.smoke.stop = lambda *args: []
        with patch('sys.stdout', output):
            with self.assertRaises(AssertionError) as error:
                self.smoke.main()
        self.assertNotIn(self.smoke.password, output.getvalue())
        self.assertNotIn(self.smoke.password, str(error.exception))

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

    def test_normalization_only_runs_actual_text_consumption_probe(self):
        self.smoke.selected_services = {'ircs-normalization-worker'}
        starts, probes = [], []
        self.smoke.start = lambda service, values, **kwargs: starts.append(service)
        self.smoke.stop = lambda *args: []
        self.smoke.normalization_round_trip = lambda: probes.append(True)
        self.smoke.main()
        self.assertEqual(['ircs-normalization-worker'], starts)
        self.assertEqual([True], probes)

    def normalization_docker(self, title='国王的演讲'):
        def respond(*args):
            if 'HGET' in args:
                return 'SUCCESS' if args[-1] == 'status' else ''
            if 'ZCARD' in args:
                return '1'
            if args[-1].startswith('select json_build_object'):
                return json.dumps({'status': 'READY', 'title': title, 'description': '音乐与繁体国语'})
            return ''
        return respond

    def test_normalization_probe_requires_durable_conversion_and_both_handoffs(self):
        with patch.object(self.smoke, 'docker', side_effect=self.normalization_docker()) as runtime:
            self.smoke.normalization_round_trip()
        args = [call.args for call in runtime.call_args_list]
        self.assertTrue(any('HGET' in command for command in args))
        for queue in ('pipeline.enrich-metadata', 'aggregation.raw-video'):
            self.assertTrue(any(command[-1] == 'ircs:work:v1:' + queue + ':pending' for command in args))

    def test_normalization_probe_rejects_an_unconverted_native_title(self):
        with patch.object(self.smoke, 'docker', side_effect=self.normalization_docker('國王的演講')):
            with self.assertRaisesRegex(AssertionError, 'title conversion'):
                self.smoke.normalization_round_trip()

    def test_normalization_probe_reports_the_first_durable_failure_without_waiting_for_all_retries(self):
        fallback = self.normalization_docker()

        def respond(*args):
            if 'HGET' in args and args[-1] == 'lastError':
                return 'BadSqlGrammarException: could not determine data type of parameter'
            return fallback(*args)

        with patch.object(self.smoke, 'docker', side_effect=respond), patch.object(self.smoke.time, 'sleep') as sleep:
            with self.assertRaisesRegex(AssertionError, 'Native normalization task failed: BadSqlGrammarException'):
                self.smoke.normalization_round_trip()
        sleep.assert_not_called()

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
