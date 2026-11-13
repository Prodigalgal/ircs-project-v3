#!/usr/bin/env bash
set -euo pipefail
# Resolve only required fixture images. Baseline references are immutable
# multiarch manifests; selected applications use this candidate's arch images.
MIGRATOR_IMAGE="$(python3 - <<'PY'
import json, os
selected = set(json.loads(os.environ['JAVA_IMAGES']))
assert selected, 'A runtime smoke requires selected Java images'
refs = json.loads(os.environ.get('IMAGE_REFS_JSON', '{}'))
versions = json.loads(os.environ.get('MODULE_VERSIONS_JSON', '{}'))
required = {'ircs-migrator'}
if 'ircs-api-gateway' in selected:
    required.update({'ircs-ops-service', 'ircs-identity-service'})
if selected & {'ircs-ops-service', 'ircs-identity-service'}:
    required.add('ircs-api-gateway')
for fixture in required - selected:
    assert refs.get(fixture) and versions.get(fixture), 'Missing immutable baseline fixture: ' + fixture
image = (os.environ['REGISTRY'] + '/ircs-migrator:' + os.environ['RELEASE_TAG'] + '-' + os.environ['NATIVE_ARCH']
         if 'ircs-migrator' in selected else refs['ircs-migrator'])
print(image)
PY
)"
if [[ "$JAVA_IMAGES" == '["ircs-migrator"]' ]]; then
  bash backend/scripts/ci/native-smoke.sh ircs-migrator "$MIGRATOR_IMAGE"
  exit 0
fi
CI_PASSWORD="$(openssl rand -hex 24)"
export CI_PASSWORD
printf '::add-mask::%s\n' "$CI_PASSWORD"
key_dir="$(mktemp -d)"

cleanup() {
  if [[ "$?" != 0 ]]; then
    docker logs --tail 80 ircs-native-broker 2>&1 || true
    docker logs --tail 80 ircs-native-migrator 2>&1 || true
  fi
  docker rm -f ircs-native-consumer ircs-native-gateway ircs-native-migrator ircs-native-postgres ircs-native-broker ircs-native-key-value ircs-native-search >/dev/null 2>&1 || true
  docker network rm ircs-native-consumers >/dev/null 2>&1 || true
  rm -rf "$key_dir"
}
trap cleanup EXIT
openssl genpkey -algorithm ED25519 -outform DER -out "$key_dir/private.der"
chmod 600 "$key_dir/private.der"
openssl pkey -inform DER -in "$key_dir/private.der" -pubout -outform DER -out "$key_dir/public.der"
CI_GATEWAY_SIGNING_KEY_FILE="$key_dir/private.der"
CI_GATEWAY_SIGNING_KEY="$(base64 -w0 "$key_dir/private.der")"
CI_GATEWAY_VERIFICATION_KEY="$(base64 -w0 "$key_dir/public.der")"
export CI_GATEWAY_SIGNING_KEY_FILE CI_GATEWAY_SIGNING_KEY CI_GATEWAY_VERIFICATION_KEY
printf '::add-mask::%s\n' "$CI_GATEWAY_SIGNING_KEY"
docker network create ircs-native-consumers >/dev/null
docker run -d --network ircs-native-consumers --name ircs-native-postgres \
  -e POSTGRES_USER=ircs -e POSTGRES_PASSWORD="$CI_PASSWORD" -e POSTGRES_DB=ircs postgres:18 >/dev/null
docker run -d --network ircs-native-consumers --name ircs-native-key-value valkey/valkey:8 >/dev/null
docker run -d --network ircs-native-consumers --name ircs-native-broker -p 127.0.0.1:15672:15672 \
  -e RABBITMQ_DEFAULT_USER=ircs -e RABBITMQ_DEFAULT_PASS="$CI_PASSWORD" rabbitmq:4.1.8-management >/dev/null
plugin_dir="${RUNNER_TEMP}/ircs-native-search-plugins"
mkdir -p "$plugin_dir/ik" "$plugin_dir/pinyin"
for plugin in ik pinyin; do
  curl --fail --silent --show-error --retry 3 \
    "https://release.infinilabs.com/analysis-${plugin}/stable/elasticsearch-analysis-${plugin}-9.1.4.zip" \
    -o "${RUNNER_TEMP}/ircs-${plugin}.zip"
  unzip -q "${RUNNER_TEMP}/ircs-${plugin}.zip" -d "$plugin_dir/$plugin"
done
chmod -R a+rX "$plugin_dir"
docker run -d --network ircs-native-consumers --name ircs-native-search -p 127.0.0.1:19200:9200 \
  -e discovery.type=single-node -e xpack.security.enabled=false -e node.store.allow_mmap=false \
  -e 'ES_JAVA_OPTS=-Xms512m -Xmx512m' -v "$plugin_dir:/usr/share/elasticsearch/plugins:ro" \
  docker.elastic.co/elasticsearch/elasticsearch:9.1.4 >/dev/null
for attempt in $(seq 1 180); do
  if docker exec ircs-native-postgres pg_isready -U ircs -d ircs >/dev/null 2>&1 \
    && curl --fail --silent --user "ircs:${CI_PASSWORD}" http://127.0.0.1:15672/api/overview >/dev/null \
    && curl --fail --silent 'http://127.0.0.1:19200/_cluster/health?wait_for_status=yellow&timeout=1s' >/dev/null; then break; fi
  sleep 2
done
docker run --name ircs-native-migrator --network ircs-native-consumers \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://ircs-native-postgres:5432/ircs \
  -e SPRING_DATASOURCE_USERNAME=ircs -e SPRING_DATASOURCE_PASSWORD="$CI_PASSWORD" \
  "$MIGRATOR_IMAGE" -Xmx256m
python3 backend/scripts/ci/native-consumer-smoke.py
