"""Derive affected CI matrices and immutable baseline maps without building applications."""
import argparse
import json
import os
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[3]
FRONTENDS = {'admin': 'ircs-admin-frontend', 'huawai': 'ircs-huawai-frontend'}
PYTHON = 'ircs-adult-classifier-service'
ARCHITECTURES = [('amd64', 'ubuntu-24.04', 'x86-64'), ('arm64', 'ubuntu-24.04-arm', 'compatibility')]


def outputs_for(plan, settings, baseline=None, scope='auto'):
    modules = re.findall(r"^include '(services:[a-z0-9-]+|platform:ircs-migrator)'$", settings, re.M)
    by_image = {module.split(':')[-1]: module for module in modules}
    all_images = set(by_image) | set(FRONTENDS.values()) | {PYTHON}
    selected = set(plan.get('java_images', [])) | {FRONTENDS[name] for name in plan.get('frontends', [])}
    if plan.get('python_changed'):
        selected.add(PYTHON)
    forced = set()
    if scope not in ('', 'auto'):
        forced = all_images if scope == 'all' else {item.strip().removeprefix(':').split(':')[-1] for item in scope.split(',') if item.strip()}
        forced = {FRONTENDS.get(item, item) for item in forced}
        if forced - all_images:
            raise ValueError('Unknown forced image scope: ' + ', '.join(sorted(forced - all_images)))
        # Force adds to pending changes, so promotion cannot silently skip a
        # previously failed application's changes while advancing its baseline.
        selected |= forced
    java_images = sorted(selected & by_image.keys())
    frontend_images = sorted(name for name, image in FRONTENDS.items() if image in selected)
    test_modules = sorted(set(plan.get('java_test_modules', [])) | {by_image[name] for name in forced & by_image.keys()})
    frontend_tests = sorted(set(plan.get('frontend_tests', [])) | set(frontend_images))
    matrix = {'include': [{'module': by_image[image], 'directory': by_image[image].replace(':', '/'), 'image': image,
                           'arch': arch, 'runner': runner, 'machine': machine}
                          for image in java_images for arch, runner, machine in ARCHITECTURES]}
    frontend_matrix = {'include': [{'frontend': name, 'image': FRONTENDS[name], 'arch': arch, 'runner': runner}
                                  for name in frontend_images for arch, runner, _ in ARCHITECTURES]}
    entries = (baseline or {}).get('images', {})
    return {'matrix': matrix, 'images': java_images, 'registry': 'docker.io/speedproxy',
            'selected_images': sorted(selected), 'has_selected': bool(selected),
            'java_tests': bool(test_modules), 'java_test_modules': test_modules,
            'python_changed': PYTHON in selected, 'python_tests': bool(plan.get('python_tests', False) or PYTHON in selected),
            'frontends': frontend_images, 'frontend_tests': frontend_tests,
            'frontend_matrix': frontend_matrix, 'frontend_test_matrix': {'frontend': frontend_tests},
            'image_refs': {name: entry['image'] for name, entry in entries.items()},
            'module_versions': {name: entry['version'] for name, entry in entries.items()},
            'runtime_smoke': bool(java_images), 'removed_java_images': plan.get('removed_java_images', [])}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path)
    parser.add_argument('--baseline', type=Path)
    parser.add_argument('--scope', default=os.environ.get('BUILD_SCOPE', 'auto'))
    args = parser.parse_args()
    settings = (ROOT / 'backend/settings.gradle').read_text(encoding='utf-8')
    if args.plan:
        plan = json.loads(args.plan.read_text(encoding='utf-8'))
    else:
        modules = re.findall(r"^include '(services:[a-z0-9-]+|platform:ircs-migrator)'$", settings, re.M)
        requested = args.scope
        java = [module.split(':')[-1] for module in modules]
        if requested not in ('all', 'auto', ''):
            chosen = {item.strip().split(':')[-1] for item in requested.split(',')}
            if chosen - set(java):
                raise ValueError('Unknown historical Java image scope')
            java = [name for name in java if name in chosen]
        plan = {'java_images': java, 'java_test_modules': [module for module in modules if module.split(':')[-1] in java]}
        args.scope = 'auto'
    baseline = json.loads(args.baseline.read_text(encoding='utf-8')) if args.baseline and args.baseline.exists() else None
    outputs = outputs_for(plan, settings, baseline, args.scope)
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a', encoding='utf-8') as output:
            for name, value in outputs.items():
                output.write(name + '=' + (value if isinstance(value, str) else json.dumps(value, separators=(',', ':'))) + '\n')
    print(json.dumps(outputs, sort_keys=True))


if __name__ == '__main__':
    main()
