"""Exercise Native listener switches and ingestion against disposable infrastructure."""
import base64
import hashlib
import hmac
import json
import os
import re
import subprocess
from pathlib import Path
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

registry = os.environ['REGISTRY']
revision = os.environ['SOURCE_REVISION']
release_tag = os.environ['RELEASE_TAG']
app_version = os.environ['APP_VERSION']
arch = os.environ['NATIVE_ARCH']
password = os.environ['CI_PASSWORD']
authorization = 'Basic ' + base64.b64encode(f'ircs:{password}'.encode()).decode()


def token_part(value):
    return base64.urlsafe_b64encode(json.dumps(value).encode()).decode().rstrip('=')


claims = {'sub': 'native-smoke', 'role': 'ROLE_ADMIN', 'permissions': ['*'], 'scopes': ['admin:*', 'data:*'],
          'contentVisibility': ['PUBLIC', 'MEMBER', 'ADMIN', 'DRAFT', 'HIDDEN'],
          'dataCategories': ['*'], 'dataGenres': ['*'], 'dataTags': ['*'],
          'iat': int(time.time()), 'exp': int(time.time()) + 3600}
signed_part = token_part({'alg': 'HS384', 'typ': 'JWT'}) + '.' + token_part(claims)
signature = base64.urlsafe_b64encode(hmac.new(password.encode(), signed_part.encode(), hashlib.sha384).digest()).decode().rstrip('=')
admin_authorization = 'Bearer ' + signed_part + '.' + signature
current_service = None
current_port = 18080


def signed_identity_headers(path, issued_at=None):
    headers = {
        'X-Authenticated-User': 'api-token:native-smoke',
        'X-IRCS-Auth-Subject': 'api-token:native-smoke',
        'X-IRCS-Auth-Role': 'ROLE_ADMIN', 'X-IRCS-Auth-Permissions': '*',
        'X-IRCS-Auth-Scopes': 'admin:*,data:*', 'X-IRCS-Data-Categories': '*',
        'X-IRCS-Data-Genres': '*', 'X-IRCS-Data-Tags': '*',
        'X-IRCS-Content-Visibility': 'PUBLIC,MEMBER,ADMIN,DRAFT,HIDDEN',
        'X-IRCS-Client-IP': '127.0.0.1',
        'X-IRCS-Gateway-Timestamp': str(int(time.time()) if issued_at is None else issued_at),
    }
    values = ['GET', path, *headers.values()]
    canonical = 'IRCS-GATEWAY-IDENTITY-V1\n' + ''.join(
        base64.urlsafe_b64encode(value.encode()).decode().rstrip('=') + '\n' for value in values)
    key_file = Path(os.environ['CI_GATEWAY_SIGNING_KEY_FILE'])
    message_file = key_file.with_name('message.bin')
    message_file.write_bytes(canonical.encode())
    signed = subprocess.check_output(['openssl', 'pkeyutl', '-sign', '-rawin', '-keyform', 'DER',
                                      '-inkey', str(key_file), '-in', str(message_file)])
    headers['X-IRCS-Gateway-Signature'] = base64.urlsafe_b64encode(signed).decode().rstrip('=')
    return headers


def gateway_envelope_probe():
    path = '/api/v1/native-gateway-boundary-probe'

    def status(headers, target=path):
        try:
            with urlopen(Request('http://127.0.0.1:18080' + target, headers=headers), timeout=5) as response:
                return response.status
        except HTTPError as error:
            return error.code

    assert status({}) == 401, 'Unsigned direct API request must be denied'
    assert status({'Authorization': admin_authorization, 'X-IRCS-Auth-Subject': 'admin',
                   'X-IRCS-Auth-Role': 'ROLE_ADMIN'}) == 401, 'Bearer/raw headers must not bypass gateway boundary'
    headers = signed_identity_headers(path)
    assert status(headers) == 404, 'Valid Ed25519 envelope must reach application routing'
    assert status(headers, path + '?tampered=1') == 401, 'Signature must bind the exact query'
    assert status(signed_identity_headers(path, int(time.time()) - 60)) == 401, 'Expired envelope must fail'


def docker(*args):
    return subprocess.check_output(['docker', *args], text=True).strip()


def image(service):
    return f'{registry}/{service}:{release_tag}-{arch}'


def request(path, data=None, broker=False, authenticated=False):
    url = ('http://127.0.0.1:15672' if broker else f'http://127.0.0.1:{current_port}') + path
    headers = {'Content-Type': 'application/json'}
    if broker:
        headers['Authorization'] = authorization
    elif authenticated:
        headers['Authorization'] = admin_authorization
        if current_service != 'ircs-api-gateway':
            assert data is None, 'Direct signed smoke helper currently covers GET only'
            headers.update(signed_identity_headers(path))
            headers.update({'X-IRCS-SERVICE-ID': 'native-smoke', 'X-IRCS-SERVICE-TOKEN': password,
                            'X-IRCS-SERVICE-SCOPES': 'ops:read,ops:run'})
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
    try:
        result = request('/api/queues/%2F/' + name, broker=True)
        if 'consumers' not in result or 'messages' not in result:
            raise ValueError(f'Broker statistics for {name} are not initialized yet')
        return result
    except HTTPError as error:
        if error.code == 404:
            return {'consumers': 0, 'messages': 0}
        raise


def start(service, values, container_name='ircs-native-consumer', listen_port=18080):
    global current_service, current_port
    current_service = service
    current_port = listen_port
    common = {
        'SPRING_DATASOURCE_URL': 'jdbc:postgresql://ircs-native-postgres:5432/ircs',
        'SPRING_DATASOURCE_USERNAME': 'ircs', 'SPRING_DATASOURCE_PASSWORD': password,
        'SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE': '2', 'SPRING_DATASOURCE_HIKARI_MINIMUMIDLE': '0',
        'SPRING_DATASOURCE_HIKARI_CONNECTIONTIMEOUT': '10000', 'SPRING_DATASOURCE_HIKARI_IDLETIMEOUT': '30000',
        'SPRING_RABBITMQ_HOST': 'ircs-native-broker', 'SPRING_RABBITMQ_USERNAME': 'ircs',
        'SPRING_RABBITMQ_PASSWORD': password, 'SPRING_DATA_REDIS_HOST': 'ircs-native-key-value',
        'APP_IDENTITY_JWT_SECRET': password,
        'IRCS_GATEWAY_IDENTITY_VERIFICATION_KEY': os.environ['CI_GATEWAY_VERIFICATION_KEY'],
        'APP_OPS_SERVICE_TOKEN': password,
        'MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED': 'false', 'MANAGEMENT_TRACING_EXPORT_ENABLED': 'false',
        'MANAGEMENT_OPENTELEMETRY_TRACING_SAMPLER': 'always-off',
        'SPRING_ELASTICSEARCH_URIS': 'http://ircs-native-search:9200',
        'APP_CREDENTIAL_SERVICE_TOKEN': password, 'APP_CREDENTIAL_INITIALIZER_ENABLED': 'false',
        'APP_TASK_SCHEDULER_ENABLED': 'false', 'APP_TASK_MEDIA_REQUEST_ENABLED': 'false',
        'APP_TASK_DEFAULT_SEED_ENABLED': 'false', 'APP_MAGNET_AUTO_SEARCH_ENABLED': 'false',
        'APP_SCRAPER_TREND_SYNC_ENABLED': 'false', 'APP_SCRAPER_TREND_DISCOVERY_ENABLED': 'false',
        'APP_STORAGE_R2_WORK_QUEUE_WORKER_ENABLED': 'false',
        'OPS_ALERT_NOTIFICATION_MAIL_ENABLED': 'false', 'APP_OPS_MAINTENANCE_NOTIFICATION_MAIL_ENABLED': 'false',
        'APP_TASK_TREND_DISCOVERY_ENABLED': 'false', 'APP_MAIL_ENABLED': 'false',
        'APP_CONTENT_RESOLVER_PRESET_SEED_ENABLED': 'false',
    }
    common.update(values)
    if service == 'ircs-api-gateway':
        common['IRCS_GATEWAY_IDENTITY_SIGNING_KEY'] = os.environ['CI_GATEWAY_SIGNING_KEY']
    args = ['run', '-d', '--name', container_name, '--network', 'ircs-native-consumers',
            '-p', f'127.0.0.1:{listen_port}:8080', '--memory', '1g']
    for key, value in common.items():
        args += ['-e', f'{key}={value}']
    docker(*args, image(service), '-Xmx384m')

    def ready():
        state = json.loads(docker('inspect', '--format', '{{json .State}}', container_name))
        if not state['Running']:
            raise RuntimeError(f'{service} exited before readiness: exit code {state["ExitCode"]}')
        return request('/actuator/health/readiness')['status'] == 'UP'

    wait_for(f'{service} health', ready)
    assert request('/actuator/info')['build']['version'] == app_version, service
    with urlopen(f'http://127.0.0.1:{listen_port}/actuator/prometheus', timeout=5) as response:
        metrics = response.read().decode()
    maxima = re.findall(r'^(?:hikaricp_connections_max|jdbc_connections_max)(?:\{[^\n]*\})? ([\d.]+)', metrics, re.M)
    assert maxima or service == 'ircs-content-safety-service', (service, 'Missing datasource pool metrics')
    assert all(float(value) == 2 for value in maxima), (service, maxima)
    if service != 'ircs-api-gateway':
        gateway_envelope_probe()


def stop(container_name='ircs-native-consumer'):
    subprocess.run(['docker', 'stop', '--time', '10', container_name], check=False,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    result = subprocess.run(['docker', 'logs', container_name], check=False, text=True, capture_output=True)
    logs = result.stdout + result.stderr
    if result.returncode == 0:
        print('\n'.join(logs.splitlines()[-60:]), flush=True)
    subprocess.run(['docker', 'rm', '-f', container_name], check=False, stdout=subprocess.DEVNULL)
    return [failure for failure in ('UnsupportedFeatureError', 'NumberFormatException', 'Unknown redis exception',
                                    'Redis command timed out', 'Invalid runtime queue claim response')
            if failure in logs]


def gateway_forwarding_probe(backend):
    global current_service, current_port
    try:
        start('ircs-api-gateway', {
            'APP_GATEWAY_OPS_BASE_URL': 'http://ircs-native-consumer:8080',
            'APP_GATEWAY_IDENTITY_BASE_URL': 'http://ircs-native-consumer:8080',
            'APP_GATEWAY_OPS_SERVICE_TOKEN': password,
        }, container_name='ircs-native-gateway', listen_port=18081)
        if backend == 'ircs-ops-service':
            path = '/api/v1/ops/runtime-dlq?sampleLimit=0'
            assert isinstance(request(path, authenticated=True), list)
            try:
                request(path)
            except HTTPError as error:
                assert error.code == 401
            else:
                raise AssertionError('Gateway must reject unauthenticated ops access')
            try:
                request(path + '&token=' + admin_authorization.removeprefix('Bearer '))
            except HTTPError as error:
                assert error.code == 401
            else:
                raise AssertionError('Query credentials must not authenticate an API request')
        else:
            for path in ('/api/v1/auth/pow', '/api/portal/auth/pow'):
                challenge = request(path)
                assert challenge.get('id') and challenge.get('scope'), challenge
        print(f'Native Gateway -> {backend}: signed upstream request and authorization verified.', flush=True)
    finally:
        failures = stop('ircs-native-gateway')
        current_service, current_port = backend, 18080
        assert not failures, failures


def runtime_queue_buffer_probe():
    task_type = 'normalization.llm-cleaning'
    prefix = 'ircs:work:v1:' + task_type
    task_id = 'native-buffer-probe'
    task_key = prefix + ':task:' + task_id
    now = str(int(time.time() * 1000))
    docker('exec', 'ircs-native-key-value', 'valkey-cli', 'HSET', task_key,
           'taskType', task_type, 'taskId', task_id, 'submissionId', 'native-smoke',
           'aggregateId', 'native-smoke', 'version', 'native-smoke', 'status', 'PERMANENT_FAILURE',
           'attempt', '1', 'createdAt', now, 'updatedAt', now)
    subprocess.run(['docker', 'exec', '-i', 'ircs-native-key-value', 'valkey-cli', '-x', 'HSET', task_key, 'payload'],
                   input='n' * (2 * 1024 * 1024), text=True, check=True, stdout=subprocess.DEVNULL)
    docker('exec', 'ircs-native-key-value', 'valkey-cli', 'ZADD', prefix + ':dlq', now, task_id)
    pending_ids = ['native-probe-pending-' + str(index) for index in range(3)]
    inflight_ids = ['native-probe-inflight-' + str(index) for index in range(2)]
    for item in pending_ids:
        docker('exec', 'ircs-native-key-value', 'valkey-cli', 'ZADD', prefix + ':pending', now, item)
    for item in inflight_ids:
        docker('exec', 'ircs-native-key-value', 'valkey-cli', 'ZADD', prefix + ':inflight', str(int(now) + 3600000), item)
    try:
        for _ in range(10):
            queues = request('/api/v1/ops/runtime-dlq?sampleLimit=1', authenticated=True)
            result = next(queue for queue in queues if queue['taskType'] == task_type)
            assert (result['pending'], result['inflight'], result['dlq']) == (3, 2, 1), result
            assert len(result['samples']) == 1 and result['samples'][0]['taskId'] == task_id, result
            time.sleep(2)
        print('Native 384 MiB heap: repeated 2 MiB queue reads preserved exact counts and buffer cleanup.', flush=True)
    finally:
        docker('exec', 'ircs-native-key-value', 'valkey-cli', 'ZREM', prefix + ':pending', *pending_ids)
        docker('exec', 'ircs-native-key-value', 'valkey-cli', 'ZREM', prefix + ':inflight', *inflight_ids)
        docker('exec', 'ircs-native-key-value', 'valkey-cli', 'ZREM', prefix + ':dlq', task_id)
        docker('exec', 'ircs-native-key-value', 'valkey-cli', 'DEL', task_key)


def gateway_config_cache_probe():
    cache_key = 'ircs:system-config:v1:security.jwt.iat-floor'
    docker('exec', 'ircs-native-postgres', 'psql', '-U', 'ircs', '-d', 'ircs', '-v', 'ON_ERROR_STOP=1', '-c',
           "INSERT INTO system_configs(id,created_at,updated_at,version,config_key,config_value) "
           "VALUES(gen_random_uuid(),now(),now(),1,'security.jwt.iat-floor','0') "
           "ON CONFLICT(config_key) DO UPDATE SET config_value='0'")

    def status():
        try:
            request('/api/v1/ops/runtime-dlq?sampleLimit=0', authenticated=True)
            return 200
        except HTTPError as error:
            return error.code

    def database_loads():
        with urlopen('http://127.0.0.1:18080/actuator/prometheus', timeout=5) as response:
            lines = response.read().decode().splitlines()
        return sum(float(line.rsplit(' ', 1)[1]) for line in lines
                   if line.startswith('ircs_system_config_cache_requests_total{')
                   and 'category="security"' in line and 'level="L3"' in line and 'outcome="load"' in line)

    before = database_loads()
    docker('exec', 'ircs-native-key-value', 'valkey-cli', 'SET', cache_key, str(int(time.time()) + 60), 'PX', '30000')
    assert status() == 401, 'A shared revocation must reject the old token'
    docker('exec', 'ircs-native-key-value', 'valkey-cli', 'SET', cache_key, '0', 'PX', '30000')
    # The configured upstream is intentionally unavailable. Reaching its 502
    # response proves authorization passed after observing the updated L2 value.
    assert status() == 502, 'A newer shared value must be observed without waiting for L1 expiry'
    assert database_loads() == before, 'L2 hits must not load database configuration'
    docker('exec', 'ircs-native-key-value', 'valkey-cli', 'DEL', cache_key)
    for _ in range(5):
        assert status() == 502
    assert database_loads() == before + 1, 'One cold miss must fill L2 for subsequent authorizations'
    remaining_ttl = int(docker('exec', 'ircs-native-key-value', 'valkey-cli', 'PTTL', cache_key))
    assert 0 < remaining_ttl <= 30000, remaining_ttl
    print('Native JWT cache: shared revocation observed, hot authorizations used no L3 reads, one cold miss loaded once.', flush=True)


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
                wait_for(f'{service} consumers disabled', lambda: all(queue(q)['consumers'] == 0 for q in queues))
            print(f'{service}: runtime {flag}={enabled} verified', flush=True)
            assert not stop(), f'{service}: Native transport errors'
            wait_for('consumer shutdown', lambda: all(queue(q)['consumers'] == 0 for q in queues))
    checked = {case[0] for case in cases}
    failures = []
    for service in json.loads(os.environ['JAVA_IMAGES']):
        if service in checked or service == 'ircs-migrator':
            continue
        values = {}
        if service == 'ircs-storage-service':
            values = {'APP_STORAGE_R2_ENABLED': 'true', 'APP_STORAGE_R2_ACCOUNT_ID': 'native-smoke',
                      'APP_STORAGE_R2_ACCESS_KEY': password, 'APP_STORAGE_R2_SECRET_KEY': password,
                      'APP_STORAGE_R2_ENDPOINT': 'http://127.0.0.1:9'}
        if service == 'ircs-ops-service':
            values['APP_OPS_RUNTIME_DLQ_REPLAY_WORKER_ENABLED'] = 'false'
        if service == 'ircs-api-gateway':
            values['APP_GATEWAY_OPS_BASE_URL'] = 'http://127.0.0.1:9'
        try:
            start(service, values)
            if service == 'ircs-ops-service':
                runtime_queue_buffer_probe()
                gateway_forwarding_probe(service)
            if service == 'ircs-identity-service':
                gateway_forwarding_probe(service)
            if service == 'ircs-api-gateway':
                gateway_config_cache_probe()
            print(f'{service}: Native application startup and pool budget verified', flush=True)
        except Exception as error:
            failures.append(f'{service}: {error}')
            print(f'Native startup failed: {service}: {error}', flush=True)
        finally:
            failures.extend(f'{service}: {error}' for error in stop())
    assert not failures, failures
finally:
    stop()
