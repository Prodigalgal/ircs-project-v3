"""Derive one immutable candidate tag from its reserved business version and commit."""
from datetime import datetime
from pathlib import Path
import os
import subprocess
from zoneinfo import ZoneInfo

root = Path(__file__).resolve().parents[2]
version = (root / 'VERSION').read_text(encoding='utf-8').strip()
revision = os.environ['GITHUB_SHA']
reserved = subprocess.check_output(['git', 'rev-list', '-n', '1', 'v' + version], cwd=root, text=True).strip()
if reserved != revision:
    raise SystemExit('This business version is reserved for a different source commit; allocate a new version')
committed = subprocess.check_output(['git', 'show', '-s', '--format=%cI', revision], cwd=root, text=True).strip()
date = datetime.fromisoformat(committed).astimezone(ZoneInfo('Asia/Shanghai')).strftime('%Y%m%d')
tag = f'{date}-v{version}-production-test.1-{revision[:12]}'
with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
    output.write(f'version={version}\nrelease_tag={tag}\n')
print(f'Reserved V3 {version}: {tag}')
