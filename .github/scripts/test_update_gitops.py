"""CI regression for partial GitOps promotion; no network or Git writes."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('update_gitops', Path(__file__).with_name('update-gitops.py'))
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)
SHA = 'a' * 40
TAG = '20261113-v3.2.3-production-test.1-' + SHA[:12]
OLD = '20261113-v3.2.2-production-test.1-' + 'b' * 12


class UpdateGitOpsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.core = self.root / 'ircs-prod/core'
        (self.core / 'manifests').mkdir(parents=True)
        self.names = ['ircs-api-gateway', 'ircs-migrator', 'ircs-admin-frontend']
        self.kustomization = 'resources:\n  - manifests/migrator-job.yaml\n  - manifests/api.yaml\nimages:\n'
        self.kustomization += ''.join('- name: docker.io/speedproxy/' + name + '\n  newTag: ' + OLD + '\n' for name in self.names)
        (self.core / 'kustomization.yaml').write_text(self.kustomization)
        (self.core / 'manifests/migrator-job.yaml').write_text('spec:\n  backoffLimit: 0\n  ttlSecondsAfterFinished: 90\n')
        self.ledger = {'schema_version': 1, 'release_version': '3.2.2', 'source_revision': 'b' * 40, 'release_tag': OLD,
                       'images': {name: {'image': 'docker.io/speedproxy/' + name + ':' + OLD,
                                        'source_revision': 'b' * 40, 'version': '3.2.2', 'verified_run_id': 'old-run'} for name in self.names}}
        (self.core / 'release-state.json').write_text(json.dumps(self.ledger))
        self.env = {'SOURCE_REVISION': SHA, 'RELEASE_TAG': TAG, 'APP_VERSION': '3.2.3', 'SELECTED_IMAGES': '["ircs-api-gateway"]',
                    'GITHUB_RUN_ID': 'new-run'}

    def promote(self):
        return module.promote(self.root, self.env, ensure_security=False)

    def read_ledger(self):
        return json.loads((self.core / 'release-state.json').read_text())

    def test_selected_only_preserves_other_images_and_component_versions(self):
        hook = (self.core / 'manifests/migrator-job.yaml').read_bytes()
        result = self.promote()
        self.assertEqual(['ircs-api-gateway'], result['changed_images'])
        ledger = self.read_ledger()
        self.assertEqual(self.ledger['images']['ircs-admin-frontend'], ledger['images']['ircs-admin-frontend'])
        self.assertEqual(self.ledger['images']['ircs-migrator'], ledger['images']['ircs-migrator'])
        self.assertEqual('3.2.3', ledger['images']['ircs-api-gateway']['version'])
        self.assertEqual(SHA, ledger['source_revision'])
        source = (self.core / 'kustomization.yaml').read_text()
        self.assertNotIn('- manifests/migrator-job.yaml', source)
        self.assertEqual(hook, (self.core / 'manifests/migrator-job.yaml').read_bytes())
        self.assertEqual(1, source.count(TAG))

    def test_selected_migrator_adds_hook_with_existing_yaml_indentation(self):
        path = self.core / 'kustomization.yaml'
        path.write_text(self.kustomization.replace('  - manifests/migrator-job.yaml\n', ''))
        self.env['SELECTED_IMAGES'] = '["ircs-migrator"]'
        self.promote()
        self.assertIn('resources:\n  - manifests/migrator-job.yaml\n  - manifests/api.yaml', path.read_text())
        hook = (self.core / 'manifests/migrator-job.yaml').read_text()
        self.assertIn('activeDeadlineSeconds: 600', hook)
        self.assertIn('ttlSecondsAfterFinished: 600', hook)

    def test_rerun_preserves_original_verification_run_id_and_bytes(self):
        self.promote()
        before = {path: path.read_bytes() for path in self.core.rglob('*') if path.is_file()}
        self.env['GITHUB_RUN_ID'] = 'different-run'
        self.assertEqual([], self.promote()['changed_images'])
        self.assertTrue(all(path.read_bytes() == content for path, content in before.items()))

    def test_empty_selection_is_a_complete_noop(self):
        self.env['SELECTED_IMAGES'] = '[]'
        self.assertEqual([], self.promote()['changed_images'])
        self.assertEqual(self.kustomization, (self.core / 'kustomization.yaml').read_text())

    def test_partial_release_requires_seeded_unchanged_provenance(self):
        (self.core / 'release-state.json').unlink()
        with self.assertRaises(ValueError):
            self.promote()

    def test_stale_component_ledger_fails_before_changing_manifests(self):
        self.ledger['images']['ircs-migrator']['image'] += '-wrong'
        (self.core / 'release-state.json').write_text(json.dumps(self.ledger))
        with self.assertRaises(ValueError):
            self.promote()
        self.assertEqual(self.kustomization, (self.core / 'kustomization.yaml').read_text())

    def test_legacy_selection_still_includes_java_and_three_other_images(self):
        env = {'JAVA_IMAGES': '["ircs-api-gateway"]'}
        self.assertEqual({'ircs-api-gateway'} | module.EXTRA_IMAGES, module.selected_images(env))

    def test_unknown_selected_image_and_invalid_revision_fail(self):
        self.env['SELECTED_IMAGES'] = '["ircs-not-mapped"]'
        with self.assertRaises(ValueError):
            self.promote()
        self.env['SOURCE_REVISION'] = 'short-sha'
        with self.assertRaises(ValueError):
            self.promote()


if __name__ == '__main__':
    unittest.main()
