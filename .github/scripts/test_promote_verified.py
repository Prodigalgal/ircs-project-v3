import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location('verified', Path(__file__).with_name('promote-verified.py'))
verified = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verified)


class PromotionGuardTests(unittest.TestCase):
    def jobs(self):
        return [{'name': name, 'status': 'completed', 'conclusion': 'success'}
                for name in verified.required_jobs(['ircs-api-gateway'])]

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


if __name__ == '__main__':
    unittest.main()
