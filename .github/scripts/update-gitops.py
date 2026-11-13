"""Promote only selected verified images and record each component's provenance."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys

EXTRA_IMAGES = {'ircs-adult-classifier-service', 'ircs-admin-frontend', 'ircs-huawai-frontend'}


def selected_images(environment):
    value = environment.get('SELECTED_IMAGES')
    images = json.loads(value) if value is not None else list(set(json.loads(environment['JAVA_IMAGES'])) | EXTRA_IMAGES)
    if not isinstance(images, list) or any(not isinstance(item, str) or not re.fullmatch('ircs-[a-z0-9-]+', item) for item in images):
        raise ValueError('SELECTED_IMAGES must be a JSON array of image names')
    return set(images)


def security_migration_present(root):
    core = root / 'ircs-prod/core'
    required = {
        '01-app-config.yaml': ('GATEWAY_IDENTITY_PUBLIC_KEY:',),
        '05-application-network-policy.yaml': ('name: ircs-application-ingress', 'name: ircs-api-gateway-ingress'),
        'kustomization.yaml': ('05-application-network-policy.yaml', 'IRCS_GATEWAY_IDENTITY_VERIFICATION_KEY'),
        'manifests/api-gateway.yaml': ('IRCS_GATEWAY_IDENTITY_SIGNING_KEY', 'GATEWAY_IDENTITY_PRIVATE_KEY'),
    }
    return all((core / path).is_file() and all(marker in (core / path).read_text(encoding='utf-8') for marker in markers)
               for path, markers in required.items())


def ensure_security_migration(root):
    # Already migrated configuration can evolve without replaying a stale patch.
    if security_migration_present(root):
        return
    patch = Path(__file__).resolve().with_name('gateway-identity-v1.patch')
    command = ['git', '-C', str(root.resolve()), 'apply', '--unidiff-zero']
    subprocess.run([*command, '--check', str(patch)], check=True, capture_output=True)
    subprocess.run([*command, str(patch)], check=True, capture_output=True)
    if not security_migration_present(root):
        raise ValueError('Gateway trust-boundary migration could not be verified')


def write_if_changed(path, text):
    if not path.exists() or path.read_text(encoding='utf-8') != text:
        path.write_text(text, encoding='utf-8', newline='\n')


def promote(root, environment, ensure_security=True):
    revision, release_tag, version = (environment[key] for key in ('SOURCE_REVISION', 'RELEASE_TAG', 'APP_VERSION'))
    registry = environment.get('REGISTRY', 'docker.io/speedproxy').rstrip('/')
    if not re.fullmatch('[0-9a-f]{40}', revision) or not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise ValueError('Expected a full source SHA and formal business version')
    if not re.fullmatch(r'\d{8}-v' + re.escape(version) + r'-production-test\.1-' + revision[:12], release_tag):
        raise ValueError('Expected the reserved date/version/purpose/source image tag')
    images = selected_images(environment)
    if not images:
        return {'selected': [], 'changed_images': []}
    core = root / 'ircs-prod/core'
    ledger_path = core / 'release-state.json'
    ledger = json.loads(ledger_path.read_text(encoding='utf-8')) if ledger_path.exists() else {'schema_version': 1, 'images': {}}
    if ledger.get('schema_version') != 1 or not isinstance(ledger.get('images'), dict):
        raise ValueError('Unsupported release-state ledger')
    path = core / 'kustomization.yaml'
    source = path.read_text(encoding='utf-8')
    pattern = (r'(?m)^(?P<prefix>[ \t]*- name: ' + re.escape(registry)
               + r'/(?P<image>[a-z0-9-]+)\r?\n[ \t]+newTag: )(?P<tag>[^\r\n]+)')
    current = {match['image']: match['tag'].strip().strip('"\x27') for match in re.finditer(pattern, source)}
    if images - current.keys():
        raise ValueError('Incomplete GitOps image mapping: ' + ', '.join(sorted(images - current.keys())))
    if set(current) - images - ledger['images'].keys():
        raise ValueError('Seed release-state.json before a partial application promotion')
    for image in set(current) - images:
        if ledger['images'][image].get('image') != registry + '/' + image + ':' + current[image]:
            raise ValueError('Unchanged GitOps image differs from its component ledger: ' + image)
    if ensure_security:
        ensure_security_migration(root)
    source = path.read_text(encoding='utf-8')
    changed = {image for image in images if current[image] != release_tag}
    result = re.sub(pattern, lambda match: match['prefix'] + release_tag if match['image'] in images else match[0], source)
    hook_reference = re.compile(r'(?m)^[ \t]*-[ \t]+(?:\./)?(?:manifests/)?migrator-job\.yaml[ \t]*\r?\n')
    if 'ircs-migrator' in images:
        if not hook_reference.search(result):
            result, count = re.subn(r'(?m)^(resources:[ \t]*\r?\n)([ \t]*)(?=-\s)',
                                    lambda match: match[1] + match[2] + '- manifests/migrator-job.yaml\n' + match[2],
                                    result, count=1)
            if count != 1:
                raise ValueError('Migrator resource list could not be located')
        hook_path = core / 'manifests/migrator-job.yaml'
        hook = hook_path.read_text(encoding='utf-8')
        hook = re.sub(r'(?m)^  ttlSecondsAfterFinished: \d+$', '  ttlSecondsAfterFinished: 600', hook)
        if re.search(r'(?m)^  activeDeadlineSeconds:', hook):
            hook = re.sub(r'(?m)^  activeDeadlineSeconds: \d+$', '  activeDeadlineSeconds: 600', hook)
        else:
            hook = hook.replace('  backoffLimit: 0\n', '  backoffLimit: 0\n  activeDeadlineSeconds: 600\n', 1)
        if '  activeDeadlineSeconds: 600\n' not in hook or '  ttlSecondsAfterFinished: 600\n' not in hook:
            raise ValueError('Migrator hook deadline/cleanup policy could not be verified')
        write_if_changed(hook_path, hook)
    else:
        result = hook_reference.sub('', result)
    for image in images:
        existing = ledger['images'].get(image, {})
        if (image not in changed and existing.get('image') == registry + '/' + image + ':' + release_tag
                and existing.get('source_revision') == revision and existing.get('version') == version):
            continue
        entry = {'image': registry + '/' + image + ':' + release_tag, 'source_revision': revision, 'version': version}
        run_id = environment.get('VERIFIED_RUN_ID') or environment.get('GITHUB_RUN_ID')
        if run_id:
            entry['verified_run_id'] = str(run_id)
        ledger['images'][image] = entry
    ledger.update({'schema_version': 1, 'release_version': version, 'source_revision': revision, 'release_tag': release_tag})
    write_if_changed(path, result)
    write_if_changed(ledger_path, json.dumps(ledger, ensure_ascii=False, indent=2, sort_keys=True) + '\n')
    return {'selected': sorted(images), 'changed_images': sorted(changed)}


def main():
    try:
        print(json.dumps(promote(Path(sys.argv[1]), os.environ), sort_keys=True))
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        raise SystemExit('GitOps promotion failed: ' + type(error).__name__) from None


if __name__ == '__main__':
    main()
