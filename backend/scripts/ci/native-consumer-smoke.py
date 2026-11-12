"""Exercise Native listener switches and ingestion against disposable infrastructure."""
import base64
import json
import os
import subprocess
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

registry = os.environ['REGISTRY']
revision = os.environ['SOURCE_REVISION']
arch = os.environ['NATIVE_ARCH']
password = os.environ['CI_PASSWORD']
authorization = 'Basic ' + base64.b64encode(f'ircs:{password}'.encode()).decode()


def docker(*args):
    return subprocess.check_output(['docker', *args], text=True).strip()


def image(service):
    return f'{registry}/{service}:sha-{revision}-{arch}'


def request(path, data=None, broker=False):
    url = ('http://127.0.0.1:15672' if broker else 'http://127.0.0.1:18080') + path
    headers = {'Content-Type': 'application/json'}
    if broker:
        headers['Authorization'] = authorization
    payload = json.dumps(data, ensure_ascii=False).encode() if data is not None else None
    with urlopen(Request(url, data=payload, headers=headers), timeout=5) as response:
        return json.load(response)


def wait_for(description, condition):
    for _ in range(90):
        try:
            if condition():
                return
        except (HTTPError, URLError, TimeoutError, ConnectionError, ValueError):
            pass
        time.sleep(2)
    raise AssertionError(f'Timed out waiting for {description}')


def queue(name):
    return request('/api/queues/%2F/' + name, broker=True)


def start(service, values):
    common = {
        'SPRING_DATASOURCE_URL': 'jdbc:postgresql://ircs-native-postgres:5432/ircs',
        'SPRING_DATASOURCE_USERNAME': 'ircs', 'SPRING_DATASOURCE_PASSWORD': password,
        'SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE': '2', 'SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE': '0',
        'SPRING_RABBITMQ_HOST': 'ircs-native-broker', 'SPRING_RABBITMQ_USERNAME': 'ircs',
        'SPRING_RABBITMQ_PASSWORD': password, 'SPRING_DATA_REDIS_HOST': 'ircs-native-key-value',
        'APP_IDENTITY_JWT_SECRET': password,
        'MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED': 'false', 'MANAGEMENT_TRACING_EXPORT_ENABLED': 'false',
        'APP_TASK_SCHEDULER_ENABLED': 'false', 'APP_TASK_MEDIA_REQUEST_ENABLED': 'false',
        'APP_TASK_TREND_DISCOVERY_ENABLED': 'false', 'APP_MAIL_ENABLED': 'false',
        'APP_CONTENT_RESOLVER_PRESET_SEED_ENABLED': 'false',
    }
    common.update(values)
    args = ['run', '-d', '--name', 'ircs-native-consumer', '--network', 'ircs-native-consumers',
            '-p', '127.0.0.1:18080:8080', '--memory', '1g']
    for key, value in common.items():
        args += ['-e', f'{key}={value}']
    docker(*args, image(service), '-Xmx384m')
    wait_for(f'{service} health', lambda: request('/actuator/health/readiness')['status'] == 'UP')


def stop():
    subprocess.run(['docker', 'logs', '--tail', '60', 'ircs-native-consumer'], check=False)
    subprocess.run(['docker', 'rm', '-f', 'ircs-native-consumer'], check=False, stdout=subprocess.DEVNULL)


def ingestion_round_trip():
    item = {'video': {'sourceVid': 'native-smoke', 'sourceHash': 'native-smoke-source',
                      'dataHash': 'native-smoke-version', 'title': 'Native 消费验证', 'playlists': []},
            'forceIngest': False}

    def publish():
        result = request('/api/exchanges/%2F/amq.default/publish', {
            'routing_key': 'q.ingest.video', 'properties': {'content_type': 'application/json', 'delivery_mode': 2},
            'payload': json.dumps(item, ensure_ascii=False), 'payload_encoding': 'string'}, broker=True)
        assert result['routed'] is True

    def persisted():
        count = docker('exec', 'ircs-native-postgres', 'psql', '-U', 'ircs', '-d', 'ircs', '-Atc',
                       "select count(*) from raw_videos where source_hash='native-smoke-source'")
        return count == '1'

    def handed_off():
        pending = docker('exec', 'ircs-native-key-value', 'valkey-cli', 'zcard',
                         'ircs:work:v1:pipeline.normalize-video:pending')
        return pending == '1' and queue('q.ingest.video')['messages'] == 0 and queue('q.process.playlist_sync')['messages'] == 0

    publish()
    wait_for('Native PostgreSQL persistence', persisted)
    wait_for('confirmed playlist consumption and normalization handoff', handed_off)
    wait_for('first ingestion acknowledgement', lambda: queue('q.ingest.video').get('message_stats', {}).get('ack', 0) > 0)
    acknowledgements = queue('q.ingest.video')['message_stats']['ack']
    publish()
    wait_for('duplicate ingestion acknowledgement', lambda: queue('q.ingest.video').get('message_stats', {}).get('ack', 0) > acknowledgements)
    wait_for('duplicate message acknowledgement', handed_off)
    assert persisted()
    assert queue('q.ingest.video.dlq')['messages'] == 0
    assert queue('q.process.playlist_sync.dlq')['messages'] == 0
    print('Native ingestion persisted one video, consumed playlist sync and deduplicated normalization work.', flush=True)


cases = [
    ('ircs-ingestion-worker', 'APP_INGESTION_LISTENER_ENABLED', ['q.ingest.video', 'q.process.playlist_sync']),
    ('ircs-content-service', 'APP_CONTENT_LISTENER_ENABLED', ['q.storage.image_unlink']),
    ('ircs-interaction-service', 'APP_INTERACTION_LISTENER_ENABLED', ['q.interaction.watch_progress']),
]
try:
    for service, flag, queues in cases:
        for enabled in ('false', 'true'):
            start(service, {flag: enabled})
            if enabled == 'true':
                wait_for(f'{service} consumers enabled', lambda: all(queue(q)['consumers'] > 0 for q in queues))
                if service == 'ircs-ingestion-worker':
                    ingestion_round_trip()
            else:
                assert all(queue(q)['consumers'] == 0 for q in queues)
            print(f'{service}: runtime {flag}={enabled} verified', flush=True)
            stop()
            wait_for('consumer shutdown', lambda: all(queue(q)['consumers'] == 0 for q in queues))
finally:
    stop()
