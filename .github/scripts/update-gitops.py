"""Promote a fully verified V3 image set without changing runtime/data settings."""
import json
import os
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
revision = os.environ['SOURCE_REVISION']
if not re.fullmatch('[0-9a-f]{40}', revision):
    raise SystemExit('Expected a full source commit SHA')
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
    return match.group('prefix') + 'sha-' + revision

pattern = r'(?m)^(?P<prefix>[ \t]*- name: docker\.io/speedproxy/(?P<image>[a-z0-9-]+)\r?\n[ \t]+newTag: )[^\r\n]+'
result = re.sub(pattern, promote, source)
if updated != images:
    raise SystemExit(f'Incomplete GitOps image mapping: {sorted(images - updated)}')
path.write_text(result, encoding='utf-8', newline='\n')
print(f'Promoted {len(updated)} images to sha-{revision}')
