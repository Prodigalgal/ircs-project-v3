import importlib.util
from io import BytesIO
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch
from zipfile import ZipFile

SPEC = importlib.util.spec_from_file_location('verified', Path(__file__).with_name('promote-verified.py'))
verified = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verified)


class PromotionGuardTests(unittest.TestCase):
    revision = 'a' * 40
    modules = ['ircs-aggregation-worker', 'ircs-content-safety-service', 'ircs-credential-service']

    def jobs(self):
        return [{'name': name, 'status': 'completed', 'conclusion': 'success'}
                for name in verified.required_jobs(['ircs-api-gateway'])]

    def plan(self, images=None):
        return {'schema_version': 1, 'head': self.revision,
                'java_images': self.modules if images is None else images, 'removed_java_images': [],
                'java_test_modules': ['services:ircs-content-safety-service'], 'java_tests': True,
                'frontends': [], 'frontend_tests': [], 'python_changed': False, 'python_tests': False}

    def partial_jobs(self, images=None):
        names = {'prepare', 'manifests', 'java-tests', 'Native service runtime / amd64', 'Native service runtime / arm64'}
        names.update(f'Native {image} / {arch}' for image in (self.modules if images is None else images)
                     for arch in verified.ARCHITECTURES)
        return ([{'name': name, 'status': 'completed', 'conclusion': 'success'} for name in names]
                + [{'name': name, 'status': 'completed', 'conclusion': 'skipped'}
                   for name in ('python-tests', 'frontend-tests', 'python-images', 'frontend-images', verified.PUBLISH_JOB)])

    def candidate_run(self, branch='main', **overrides):
        run = {'head_sha': self.revision, 'head_branch': branch, 'status': 'completed',
               'head_repository': {'full_name': verified.CANONICAL_REPOSITORY}, 'event': 'workflow_dispatch',
               'path': '.github/workflows/native-build.yml'}
        return {**run, **overrides}

    def archive(self, plan=None, filename='affected-plan.json'):
        buffer = BytesIO()
        with ZipFile(buffer, 'w') as package:
            package.writestr(filename, json.dumps(self.plan() if plan is None else plan))
        return buffer.getvalue()

    def test_publication_failure_does_not_invalidate_successful_application_gates(self):
        jobs = self.jobs() + [{'name': verified.PUBLISH_JOB, 'status': 'completed', 'conclusion': 'failure'}]
        verified.validate_jobs(jobs, ['ircs-api-gateway'])

    def test_runtime_failure_cannot_be_bypassed(self):
        jobs = self.jobs()
        next(j for j in jobs if j['name'] == 'Native service runtime / arm64')['conclusion'] = 'failure'
        with self.assertRaises(ValueError):
            verified.validate_jobs(jobs, ['ircs-api-gateway'])

    def test_missing_architecture_cannot_be_bypassed(self):
        with self.assertRaises(ValueError):
            verified.validate_jobs([j for j in self.jobs() if j['name'] != 'Native ircs-api-gateway / arm64'], ['ircs-api-gateway'])

    def test_unexpected_failed_gate_cannot_be_ignored(self):
        with self.assertRaises(ValueError):
            verified.validate_jobs(self.jobs() + [{'name': 'unknown-check', 'conclusion': 'failure'}], ['ircs-api-gateway'])

    def test_only_ci_and_docs_are_allowed_after_candidate(self):
        for path in ('.github/workflows/native-build.yml', '.github/scripts/affected-plan.py',
                     'backend/scripts/ci/native-consumer-smoke.py', 'backend/docs/reports/evidence.md'):
            self.assertTrue(verified.ci_only_path(path))
        for path in ('VERSION', 'backend/build.gradle', 'backend/services/ircs-content-service/build.gradle',
                     'frontends/admin/package.json', '.github/scripts/gateway-identity-v1.patch'):
            self.assertFalse(verified.ci_only_path(path))

    def test_three_module_partial_candidate_allows_expected_skips_and_publishes_only_actual_images(self):
        java, selected = verified.validate_jobs(self.partial_jobs(), self.modules, self.plan())
        self.assertEqual(java, sorted(self.modules))
        self.assertEqual(selected, sorted(self.modules))

    def test_forced_additional_images_are_allowed_only_when_fully_verified(self):
        plan = self.plan([self.modules[0]])
        self.assertEqual(verified.validate_jobs(self.partial_jobs(), self.modules, plan)[1], sorted(self.modules))
        jobs = self.partial_jobs()
        next(j for j in jobs if j['name'] == f'Native {self.modules[2]} / arm64')['conclusion'] = 'skipped'
        with self.assertRaises(ValueError):
            verified.validate_jobs(jobs, self.modules, plan)

    def test_partial_missing_architecture_or_failed_test_runtime_or_manifest_is_rejected(self):
        for name in (f'Native {self.modules[0]} / arm64', 'java-tests', 'Native service runtime / arm64', 'manifests', 'prepare'):
            for conclusion in ('failure', 'skipped', 'cancelled'):
                with self.subTest(name=name, conclusion=conclusion):
                    jobs = self.partial_jobs()
                    next(j for j in jobs if j['name'] == name)['conclusion'] = conclusion
                    with self.assertRaises(ValueError):
                        verified.validate_jobs(jobs, self.modules, self.plan())
        missing = [j for j in self.partial_jobs() if j['name'] != f'Native {self.modules[0]} / arm64']
        with self.assertRaises(ValueError):
            verified.validate_jobs(missing, self.modules, self.plan())

    def test_test_only_empty_matrix_is_never_publishable(self):
        jobs = [{'name': name, 'status': 'completed', 'conclusion': 'success'} for name in ('prepare', 'java-tests')]
        with self.assertRaisesRegex(ValueError, 'no verified images'):
            verified.validate_jobs(jobs, self.modules, self.plan([]))

    def test_expected_plan_scope_cannot_be_silently_omitted(self):
        with self.assertRaisesRegex(ValueError, 'misses affected-plan scope'):
            verified.validate_jobs(self.partial_jobs(self.modules[:2]), self.modules, self.plan())
        for change in ({'frontends': ['admin']}, {'python_changed': True}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                verified.validate_jobs(self.partial_jobs(), self.modules, {**self.plan(), **change})

    def test_plan_test_scope_is_required_even_when_that_component_has_no_image(self):
        for change in ({'frontend_tests': ['admin']}, {'python_tests': True}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                verified.validate_jobs(self.partial_jobs(), self.modules, {**self.plan(), **change})

    def test_actual_frontend_and_python_matrices_require_both_architectures_and_tests(self):
        for image_names, test_name, image in ((['Frontend admin / amd64', 'Frontend admin / arm64'], 'Frontend admin checks', 'ircs-admin-frontend'),
                                               (['Python image / amd64', 'Python image / arm64'], 'python-tests', verified.PYTHON_IMAGE)):
            jobs = [j for j in self.partial_jobs() if j['name'] != test_name]
            jobs += [{'name': name, 'status': 'completed', 'conclusion': 'success'} for name in [*image_names, test_name]]
            with self.subTest(image=image):
                self.assertIn(image, verified.validate_jobs(jobs, self.modules, self.plan())[1])
                with self.assertRaises(ValueError):
                    verified.validate_jobs([j for j in jobs if j['name'] != image_names[1]], self.modules, self.plan())
                next(j for j in jobs if j['name'] == test_name)['conclusion'] = 'skipped'
                with self.assertRaises(ValueError):
                    verified.validate_jobs(jobs, self.modules, self.plan())

    def test_other_failed_or_incomplete_jobs_still_reject_partial_candidate(self):
        for status, conclusion in (('completed', 'failure'), ('completed', 'cancelled'), ('in_progress', None)):
            jobs = self.partial_jobs() + [{'name': 'unexpected-gate', 'status': status, 'conclusion': conclusion}]
            with self.subTest(status=status, conclusion=conclusion), self.assertRaises(ValueError):
                verified.validate_jobs(jobs, self.modules, self.plan())

    def test_legacy_without_artifact_retains_full_candidate_requirements(self):
        with self.assertRaises(ValueError):
            verified.validate_jobs(self.partial_jobs(), self.modules)
        self.assertEqual(len(verified.validate_jobs(self.jobs(), ['ircs-api-gateway'])[1]), 4)

    def test_only_canonical_main_or_exact_reserved_tag_run_is_trusted(self):
        for branch in ('main', 'v3.3.2'):
            verified.validate_run(self.candidate_run(branch), self.revision, '3.3.2')
        for run in (self.candidate_run('v3.3.1'), self.candidate_run('upgrade/candidate'),
                    self.candidate_run('v3.3.2', event='push'), self.candidate_run(event='pull_request'),
                    self.candidate_run(head_repository={'full_name': 'untrusted/fork'}),
                    self.candidate_run(head_sha='b' * 40), self.candidate_run(status='in_progress'),
                    self.candidate_run(path='.github/workflows/other.yml')):
            with self.subTest(run=run), self.assertRaises(ValueError):
                verified.validate_run(run, self.revision, '3.3.2')

    def test_artifact_is_read_in_memory_and_binds_exact_candidate_sha(self):
        self.assertEqual(verified.parse_plan_archive(self.archive(), self.revision), self.plan())
        for archive in (self.archive({**self.plan(), 'head': 'b' * 40}),
                        self.archive({**self.plan(), 'schema_version': 2}),
                        self.archive(filename='../affected-plan.json')):
            with self.subTest(), self.assertRaises(ValueError):
                verified.parse_plan_archive(archive, self.revision)

    def test_artifact_loader_is_scoped_to_same_run_and_does_not_fallback_on_expiry(self):
        artifact = {'name': 'affected-plan', 'id': 123, 'expired': False,
                    'workflow_run': {'id': 456, 'head_sha': self.revision}}
        with patch.object(verified, 'api', return_value={'artifacts': [artifact]}) as api, \
                patch.object(verified.subprocess, 'check_output', return_value=self.archive()) as download:
            self.assertEqual(verified.load_plan(verified.CANONICAL_REPOSITORY, 456, self.revision), self.plan())
            self.assertIn('/actions/runs/456/artifacts?', api.call_args.args[0])
            self.assertEqual(download.call_args.args[0][-1], f'repos/{verified.CANONICAL_REPOSITORY}/actions/artifacts/123/zip')
        for artifacts in ([{**artifact, 'expired': True}], [artifact, artifact],
                          [{**artifact, 'workflow_run': {'id': 999, 'head_sha': self.revision}}]):
            with patch.object(verified, 'api', return_value={'artifacts': artifacts}), self.assertRaises(ValueError):
                verified.load_plan(verified.CANONICAL_REPOSITORY, 456, self.revision)
        with patch.object(verified, 'api', return_value={'artifacts': []}):
            self.assertIsNone(verified.load_plan(verified.CANONICAL_REPOSITORY, 456, self.revision))

    def test_stale_guard_requires_ancestry_and_rejects_new_business_inputs(self):
        with patch.object(verified.subprocess, 'run') as ancestry, \
                patch.object(verified, 'git', return_value='.github/scripts/promote-verified.py\ndocs/recovery.md'):
            verified.validate_freshness(self.revision, 'b' * 40)
            ancestry.assert_called_once_with(['git', 'merge-base', '--is-ancestor', self.revision, 'b' * 40], check=True)
        with patch.object(verified.subprocess, 'run'), patch.object(verified, 'git', return_value='backend/services/ircs-content-service/src/main/java/Business.java'):
            with self.assertRaisesRegex(ValueError, 'Newer application inputs'):
                verified.validate_freshness(self.revision, 'b' * 40)
        with patch.object(verified.subprocess, 'run', side_effect=subprocess.CalledProcessError(1, 'git')):
            with self.assertRaises(subprocess.CalledProcessError):
                verified.validate_freshness(self.revision, 'b' * 40)


if __name__ == '__main__':
    unittest.main()
