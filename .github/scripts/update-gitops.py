"""Promote a fully verified V3 image set without changing runtime/data settings."""
import json
import os
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
revision = os.environ['SOURCE_REVISION']
release_tag = os.environ['RELEASE_TAG']
version = os.environ['APP_VERSION']
if not re.fullmatch('[0-9a-f]{40}', revision):
    raise SystemExit('Expected a full source commit SHA')
if not re.fullmatch(r'\d{8}-v' + re.escape(version) + r'-production-test\.1-' + revision[:12], release_tag):
    raise SystemExit('Expected the reserved date/version/purpose/source image tag')
images = set(json.loads(os.environ['JAVA_IMAGES'])) | {
    'ircs-adult-classifier-service', 'ircs-admin-frontend', 'ircs-huawai-frontend'
}
path = root / 'ircs-prod/core/kustomization.yaml'
source = path.read_text(encoding='utf-8')
updated = set()

def promote(match):
    image = match.group('image')
    if image not in images:
        return match.group(0)
    updated.add(image)
    return match.group('prefix') + release_tag

pattern = r'(?m)^(?P<prefix>[ \t]*- name: docker\.io/speedproxy/(?P<image>[a-z0-9-]+)\r?\n[ \t]+newTag: )[^\r\n]+'
result = re.sub(pattern, promote, source)
if updated != images:
    raise SystemExit(f'Incomplete GitOps image mapping: {sorted(images - updated)}')
path.write_text(result, encoding='utf-8', newline='\n')
hook_path = root / 'ircs-prod/core/manifests/migrator-job.yaml'
hook = hook_path.read_text(encoding='utf-8')
hook = re.sub(r'(?m)^  ttlSecondsAfterFinished: \d+$', '  ttlSecondsAfterFinished: 600', hook)
if re.search(r'(?m)^  activeDeadlineSeconds:', hook):
    hook = re.sub(r'(?m)^  activeDeadlineSeconds: \d+$', '  activeDeadlineSeconds: 600', hook)
else:
    hook = hook.replace('  backoffLimit: 0\n', '  backoffLimit: 0\n  activeDeadlineSeconds: 600\n', 1)
if '  activeDeadlineSeconds: 600\n' not in hook or '  ttlSecondsAfterFinished: 600\n' not in hook:
    raise SystemExit('Migrator hook deadline/cleanup policy could not be verified')
hook_path.write_text(hook, encoding='utf-8', newline='\n')
print(f'Promoted {len(updated)} images to {release_tag}')
