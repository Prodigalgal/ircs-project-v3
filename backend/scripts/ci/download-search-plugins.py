"""Fetch the production Elasticsearch 9.1.4 plugin archives for Native runtime tests."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys


VERSION = '9.1.4'
PACKAGES = {
    'ik': (4619008, '0bf5c3e809212dc4646e34c9e81169007c4bc400526675d37a4fd7d6b285e2db'),
    'pinyin': (6019065, 'ca0a46b86265c5f29932a1df1714299139efd5320d71087a6d7689651d478c9f'),
}


def matches(archive, size, checksum):
    if not archive.is_file() or archive.stat().st_size != size:
        return False
    with archive.open('rb') as contents:
        return hashlib.file_digest(contents, 'sha256').hexdigest() == checksum


def fetch(name, archive, mirror):
    size, checksum = PACKAGES[name]
    if matches(archive, size, checksum):
        return
    sources = [
        f'https://get.infini.cloud/elasticsearch/analysis-{name}/{VERSION}',
        f'https://release.infinilabs.com/analysis-{name}/stable/elasticsearch-analysis-{name}-{VERSION}.zip',
    ]
    if mirror:
        sources.insert(0, f'{mirror}/{archive.name}')
    partial = archive.with_suffix('.partial')
    try:
        for source in sources:
            result = subprocess.run([
                'curl', '--fail', '--silent', '--show-error', '--location', '--proto', '=https',
                '--proto-redir', '=https', '--connect-timeout', '10', '--max-time', '90',
                '--retry', '1', '--retry-delay', '2', '--retry-max-time', '120', '--retry-all-errors',
                '--max-filesize', str(size), '--output', str(partial), source,
            ], check=False)
            if result.returncode == 0 and matches(partial, size, checksum):
                partial.replace(archive)
                return
            partial.unlink(missing_ok=True)
    finally:
        partial.unlink(missing_ok=True)
    raise SystemExit(f'Could not verify the Elasticsearch {VERSION} {name} plugin archive')


def main():
    directory = Path(sys.argv[1])
    mirror = os.environ.get('IRCS_CI_SEARCH_PLUGIN_MIRROR', '')
    if mirror and not re.fullmatch(r'https://img\.mnnu\.eu\.org/ircs-ci-fixtures/[a-z0-9][a-z0-9.-]{0,100}', mirror):
        raise SystemExit('Expected a project-owned CI fixture mirror URL')
    directory.mkdir(parents=True, exist_ok=True)
    for name in PACKAGES:
        fetch(name, directory / f'{name}.zip', mirror)
        print(f'Verified Elasticsearch {VERSION} {name} plugin archive', flush=True)


if __name__ == '__main__':
    main()
