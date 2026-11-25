"""Validate an already-built candidate before a scope-preserving publish-only recovery."""
import argparse
from datetime import datetime, timedelta, timezone
import json
from io import BytesIO
import os
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile

PUBLISH_JOB = 'Promote verified V3 images to GitOps'
CANONICAL_REPOSITORY = 'Prodigalgal/ircs-project-v3'
PYTHON_IMAGE = 'ircs-adult-classifier-service'
FRONTEND_IMAGES = {'admin': 'ircs-admin-frontend', 'huawai': 'ircs-huawai-frontend'}
ARCHITECTURES = ('amd64', 'arm64')


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


def validate_run(run, revision, version):
    if (run.get('head_sha') != revision or run.get('head_branch') not in ('main', 'v' + version)
            or run.get('head_repository', {}).get('full_name') != CANONICAL_REPOSITORY
            or run.get('event') not in ('push', 'workflow_dispatch')
            or (run.get('head_branch') != 'main' and run.get('event') != 'workflow_dispatch')
            or run.get('status') != 'completed' or run.get('path') != '.github/workflows/native-build.yml'):
        raise ValueError('Workflow run is not a completed canonical main or reserved-version tag candidate')


def candidate_version(revision):
    version = git('show', revision + ':VERSION')
    if (not re.fullmatch(r'\d+\.\d+\.\d+', version)
            or git('rev-parse', '--verify', 'refs/tags/v' + version + '^{commit}') != revision):
        raise ValueError('Candidate does not own its reserved business version tag')
    return version


def parse_plan_archive(archive, revision):
    # Do not extract an Actions archive onto the checkout or execute its contents.
    if len(archive) > 4 * 1024 * 1024:
        raise ValueError('Affected-plan archive is too large')
    with ZipFile(BytesIO(archive)) as package:
        entries = package.infolist()
        if (len(entries) != 1 or entries[0].filename != 'affected-plan.json'
                or entries[0].file_size > 2 * 1024 * 1024):
            raise ValueError('Expected one bounded affected-plan.json in the same-run artifact')
        plan = json.loads(package.read(entries[0]))
    if not isinstance(plan, dict) or plan.get('schema_version') != 1 or plan.get('head') != revision:
        raise ValueError('Affected-plan schema or exact source revision does not match the candidate')
    return plan


def load_plan(repository, run_id, revision):
    artifacts = []
    for page in range(1, 20):
        result = api(f'repos/{repository}/actions/runs/{run_id}/artifacts?per_page=100&page={page}')
        artifacts.extend(item for item in result['artifacts'] if item.get('name') == 'affected-plan')
        if len(result['artifacts']) < 100:
            break
    else:
        raise ValueError('Artifact listing exceeds the reviewed pagination boundary')
    if not artifacts:
        return None  # Legacy candidates must pass the original full 22+3 boundary.
    if len(artifacts) != 1 or artifacts[0].get('expired') is not False:
        raise ValueError('Affected-plan artifact is ambiguous or expired')
    artifact = artifacts[0]
    provenance = artifact.get('workflow_run', {})
    if (provenance.get('id', run_id) != run_id or provenance.get('head_sha', revision) != revision
            or not isinstance(artifact.get('id'), int) or artifact['id'] <= 0):
        raise ValueError('Affected-plan artifact provenance does not match the candidate run')
    archive = subprocess.check_output(['gh', 'api', f'repos/{repository}/actions/artifacts/{artifact["id"]}/zip'])
    return parse_plan_archive(archive, revision)


def plan_scope(plan, allowed_java):
    for key in ('java_images', 'removed_java_images', 'java_test_modules', 'frontends', 'frontend_tests'):
        values = plan.get(key)
        if not isinstance(values, list) or any(not isinstance(value, str) for value in values) or len(set(values)) != len(values):
            raise ValueError(f'Invalid affected-plan list: {key}')
    for key in ('java_tests', 'python_changed', 'python_tests'):
        if type(plan.get(key)) is not bool:
            raise ValueError(f'Invalid affected-plan flag: {key}')
    if (plan['removed_java_images'] or not set(plan['java_images']) <= set(allowed_java)
            or not set(plan['frontends']) <= FRONTEND_IMAGES.keys()
            or not set(plan['frontend_tests']) <= FRONTEND_IMAGES.keys()):
        raise ValueError('Affected-plan contains unsupported or removed application scope')
    selected = set(plan['java_images']) | {FRONTEND_IMAGES[name] for name in plan['frontends']}
    if plan['python_changed']:
        selected.add(PYTHON_IMAGE)
    tests = {f'Frontend {name} checks' for name in plan['frontend_tests']}
    if plan['java_tests'] or plan['java_test_modules']:
        tests.add('java-tests')
    if plan['python_tests']:
        tests.add('python-tests')
    return selected, tests


def validate_jobs(jobs, images, plan=None):
    by_name = {job['name']: job for job in jobs}
    if len(by_name) != len(jobs):
        raise ValueError('Duplicate candidate job names are ambiguous')
    native, frontends, python = set(), set(), False
    for name in by_name:
        match = re.fullmatch(r'Native (ircs-[a-z0-9-]+) / (amd64|arm64)', name)
        if match:
            if match[1] not in images:
                raise ValueError('Candidate built an unreviewed Java image: ' + match[1])
            native.add(match[1])
        match = re.fullmatch(r'Frontend (admin|huawai) / (amd64|arm64)', name)
        if match:
            frontends.add(match[1])
        python = python or bool(re.fullmatch(r'Python image / (amd64|arm64)', name))
    selected = native | {FRONTEND_IMAGES[name] for name in frontends}
    if python:
        selected.add(PYTHON_IMAGE)
    if not selected:
        raise ValueError('A test-only run has no verified images to publish')
    if plan is None:
        needed = required_jobs(images)
        expected = set(images) | {PYTHON_IMAGE, *FRONTEND_IMAGES.values()}
    else:
        expected, tests = plan_scope(plan, images)
        needed = {'prepare', 'manifests'} | tests
        if native:
            needed.add('java-tests')
            needed.update(f'Native service runtime / {arch}' for arch in ARCHITECTURES)
        if python:
            needed.add('python-tests')
            needed.update(f'Python image / {arch}' for arch in ARCHITECTURES)
        for frontend in frontends:
            needed.add(f'Frontend {frontend} checks')
            needed.update(f'Frontend {frontend} / {arch}' for arch in ARCHITECTURES)
        needed.update(f'Native {image} / {arch}' for image in native for arch in ARCHITECTURES)
    if not expected <= selected:
        raise ValueError('Candidate image matrix misses affected-plan scope: ' + ', '.join(sorted(expected - selected)))
    missing = needed - by_name.keys()
    failed = [name for name in sorted(needed & by_name.keys())
              if by_name[name].get('status') != 'completed' or by_name[name].get('conclusion') != 'success']
    unexpected = [job['name'] for job in jobs if job['name'] != PUBLISH_JOB and job['name'] not in needed
                  and (job.get('status') != 'completed' or job.get('conclusion') not in ('success', 'skipped'))]
    if missing or failed or unexpected:
        raise ValueError(f'Candidate gates incomplete: missing={sorted(missing)}, failed={failed}, unexpected={unexpected}')
    return sorted(native), sorted(selected)


def validate_freshness(revision, latest):
    # A matching tree is insufficient: a detached or force-pushed branch is not a trusted ancestor.
    subprocess.run(['git', 'merge-base', '--is-ancestor', revision, latest], check=True)
    business_changes = [path for path in git('diff', '--name-only', revision, latest).splitlines() if not ci_only_path(path)]
    if business_changes:
        raise ValueError('Newer application inputs exist; refuse stale promotion: ' + ', '.join(business_changes))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-id', required=True, type=int)
    parser.add_argument('--source-revision', required=True)
    args = parser.parse_args()
    if not re.fullmatch('[0-9a-f]{40}', args.source_revision) or args.run_id <= 0:
        raise SystemExit('Expected an exact candidate SHA and a positive workflow run ID')
    repository = os.environ['GITHUB_REPOSITORY']
    if repository != CANONICAL_REPOSITORY:
        raise SystemExit('Publish-only recovery is restricted to the canonical source repository')
    run = api(f'repos/{repository}/actions/runs/{args.run_id}')
    version = candidate_version(args.source_revision)
    validate_run(run, args.source_revision, version)
    settings = git('show', args.source_revision + ':backend/settings.gradle')
    java_images = sorted({module.split(':')[-1] for module in re.findall(
        r"^include '(services:[a-z0-9-]+|platform:ircs-migrator)'$", settings, re.M)})
    if len(java_images) != 22:
        raise SystemExit('Recovery expects the reviewed 22-module Java deployment boundary')
    jobs = []
    for page in range(1, 20):
        result = api(f'repos/{repository}/actions/runs/{args.run_id}/jobs?filter=latest&per_page=100&page={page}')
        jobs.extend(result['jobs'])
        if len(result['jobs']) < 100:
            break
    else:
        raise ValueError('Job listing exceeds the reviewed pagination boundary')
    plan = load_plan(repository, args.run_id, args.source_revision)
    java_images, selected = validate_jobs(jobs, java_images, plan)
    latest = api(f'repos/{repository}/commits/main')['sha']
    subprocess.run(['git', 'fetch', 'origin', 'main'], check=True)
    validate_freshness(args.source_revision, latest)
    committed = datetime.fromisoformat(git('show', '-s', '--format=%cI', args.source_revision))
    date = committed.astimezone(timezone(timedelta(hours=8))).strftime('%Y%m%d')
    tag = f'{date}-v{version}-production-test.1-{args.source_revision[:12]}'
    with Path(os.environ['GITHUB_OUTPUT']).open('a', encoding='utf-8') as output:
        for key, value in {'version': version, 'release_tag': tag, 'source_revision': args.source_revision,
                           'java_images': json.dumps(java_images, separators=(',', ':')),
                           'selected_images': json.dumps(selected, separators=(',', ':'))}.items():
            output.write(f'{key}={value}\n')
    print(f'Validated same-run {"incremental" if plan is not None else "legacy full"} candidate gates; '
          f'publishing {len(selected)} existing images only.')


if __name__ == '__main__':
    main()
