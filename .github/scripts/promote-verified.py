"""Validate an already-built full candidate before a publish-only recovery."""
import argparse
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import re
import subprocess

PUBLISH_JOB = 'Promote verified V3 images to GitOps'


def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()


def api(path):
    return json.loads(subprocess.check_output(['gh', 'api', path], text=True))


def ci_only_path(path):
    return (path.startswith(('.github/workflows/', 'backend/scripts/ci/', 'backend/docs/', 'docs/'))
            or (path.startswith('.github/scripts/') and path.endswith('.py'))
            or path in ('AGENTS.md', '.github/scripts/image-exists.sh'))


def required_jobs(java_images):
    names = {'prepare', 'java-tests', 'python-tests', 'Frontend admin checks', 'Frontend huawai checks', 'manifests'}
    for arch in ('amd64', 'arm64'):
        names.update({f'Python image / {arch}', f'Frontend admin / {arch}', f'Frontend huawai / {arch}',
                      f'Native service runtime / {arch}'})
        names.update(f'Native {image} / {arch}' for image in java_images)
    return names


def validate_jobs(jobs, images):
    by_name = {job['name']: job for job in jobs}
    needed = required_jobs(images)
    missing = needed - by_name.keys()
    failed = [name for name in sorted(needed & by_name.keys())
              if by_name[name].get('status') != 'completed' or by_name[name].get('conclusion') != 'success']
    unexpected = [job['name'] for job in jobs if job['name'] != PUBLISH_JOB and job['name'] not in needed
                  and job.get('conclusion') != 'success']
    if missing or failed or unexpected:
        raise ValueError(f'Candidate gates incomplete: missing={sorted(missing)}, failed={failed}, unexpected={unexpected}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True, type=int)
    parser.add_argument('--source-revision', required=True)
    args = parser.parse_args()
    if not re.fullmatch('[0-9a-f]{40}', args.source_revision) or args.run_id <= 0:
        raise SystemExit('Expected an exact candidate SHA and a positive workflow run ID')
    repository = os.environ['GITHUB_REPOSITORY']
    if repository != 'Prodigalgal/ircs-project-v3':
        raise SystemExit('Publish-only recovery is restricted to the canonical source repository')
    run = api(f'repos/{repository}/actions/runs/{args.run_id}')
    if (run['head_sha'] != args.source_revision or run['head_branch'] != 'main'
            or run['status'] != 'completed' or run['path'] != '.github/workflows/native-build.yml'):
        raise SystemExit('Workflow run is not the completed canonical main candidate')
    version = git('show', args.source_revision + ':VERSION')
    if not re.fullmatch(r'\d+\.\d+\.\d+', version) or git('rev-list', '-n', '1', 'v' + version) != args.source_revision:
        raise SystemExit('Candidate does not own its reserved business version')
    settings = git('show', args.source_revision + ':backend/settings.gradle')
    java_images = sorted({module.split(':')[-1] for module in re.findall(
        r"^include '(services:[a-z0-9-]+|platform:ircs-migrator)'$", settings, re.M)})
    if len(java_images) != 22:
        raise SystemExit('Full-candidate recovery expects the reviewed 22-module deployment boundary')
    jobs = []
    for page in range(1, 20):
        result = api(f'repos/{repository}/actions/runs/{args.run_id}/jobs?filter=latest&per_page=100&page={page}')
        jobs.extend(result['jobs'])
        if len(result['jobs']) < 100:
            break
    validate_jobs(jobs, java_images)
    latest = api(f'repos/{repository}/commits/main')['sha']
    subprocess.run(['git', 'fetch', 'origin', 'main'], check=True)
    changed = git('diff', '--name-only', args.source_revision, latest).splitlines()
    business_changes = [path for path in changed if not ci_only_path(path)]
    if business_changes:
        raise SystemExit('Newer application inputs exist; refuse stale promotion: ' + ', '.join(business_changes))
    committed = datetime.fromisoformat(git('show', '-s', '--format=%cI', args.source_revision))
    date = committed.astimezone(timezone(timedelta(hours=8))).strftime('%Y%m%d')
    tag = f'{date}-v{version}-production-test.1-{args.source_revision[:12]}'
    selected = [*java_images, 'ircs-adult-classifier-service', 'ircs-admin-frontend', 'ircs-huawai-frontend']
    with Path(os.environ['GITHUB_OUTPUT']).open('a', encoding='utf-8') as output:
        for key, value in {'version': version, 'release_tag': tag, 'source_revision': args.source_revision,
                           'java_images': json.dumps(java_images, separators=(',', ':')),
                           'selected_images': json.dumps(selected, separators=(',', ':'))}.items():
            output.write(f'{key}={value}\n')
    print(f'Validated {len(required_jobs(java_images))} successful candidate gates; publishing {len(selected)} existing images only.')


if __name__ == '__main__':
    main()
