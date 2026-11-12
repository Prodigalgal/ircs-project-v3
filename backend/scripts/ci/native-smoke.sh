#!/usr/bin/env bash
set -euo pipefail

service="$1"
image="$2"
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
APP_VERSION="${APP_VERSION:-$(tr -d '[:space:]' < "$root_dir/VERSION")}"
export APP_VERSION

if [[ ! "$APP_VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "APP_VERSION must be a formal x.y.z version" >&2
  exit 1
fi

docker pull "$image" >/dev/null
image_version="$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.version" }}' "$image")"
if [[ "$image_version" != "$APP_VERSION" ]]; then
  echo "Image $image has OCI version '$image_version', expected APP_VERSION '$APP_VERSION'" >&2
  exit 1
fi

case "$service" in
  ircs-content-safety-service|ircs-migrator) ;;
  *) exit 0 ;;
esac

tmp_dir="$(mktemp -d)"
cleanup() {
  docker logs ircs-native-smoke 2>&1 || true
  docker rm -f ircs-native-smoke ircs-native-smoke-repeat ircs-native-dependency >/dev/null 2>&1 || true
  docker network rm ircs-native-smoke >/dev/null 2>&1 || true
  rm -rf "$tmp_dir"
}
trap cleanup EXIT
docker network create ircs-native-smoke >/dev/null

if [[ "$service" == "ircs-migrator" ]]; then
  docker run -d --network ircs-native-smoke --name ircs-native-dependency \
    -e POSTGRES_USER=ircs -e POSTGRES_PASSWORD=ci-disposable-password -e POSTGRES_DB=ircs \
    postgres:18 >/dev/null
  for attempt in $(seq 1 60); do
    if docker exec ircs-native-dependency pg_isready -U ircs -d ircs >/dev/null 2>&1; then break; fi
    sleep 2
  done
  docker run --name ircs-native-smoke --network ircs-native-smoke \
    -e APP_VERSION="$APP_VERSION" \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://ircs-native-dependency:5432/ircs \
    -e SPRING_DATASOURCE_USERNAME=ircs -e SPRING_DATASOURCE_PASSWORD=ci-disposable-password \
    "$image"
  migrations=$(docker exec ircs-native-dependency psql -U ircs -d ircs -Atc 'select count(*) from databasechangelog')
  test "$migrations" -gt 0
  first_tag_count=$(docker exec ircs-native-dependency psql -U ircs -d ircs -Atc "select count(*) from databasechangelog where tag = '$APP_VERSION'")
  test "$first_tag_count" -eq 1
  docker run --rm --name ircs-native-smoke-repeat --network ircs-native-smoke \
    -e APP_VERSION="$APP_VERSION" \
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://ircs-native-dependency:5432/ircs \
    -e SPRING_DATASOURCE_USERNAME=ircs -e SPRING_DATASOURCE_PASSWORD=ci-disposable-password \
    "$image"
  repeated_migrations=$(docker exec ircs-native-dependency psql -U ircs -d ircs -Atc 'select count(*) from databasechangelog')
  repeated_tag_count=$(docker exec ircs-native-dependency psql -U ircs -d ircs -Atc "select count(*) from databasechangelog where tag = '$APP_VERSION'")
  test "$repeated_migrations" -eq "$migrations"
  test "$repeated_tag_count" -eq 1
  echo "Native migrator applied $migrations changesets and recorded one idempotent $APP_VERSION tag in disposable PostgreSQL"
else
  docker run -d --network ircs-native-smoke --name ircs-native-dependency valkey/valkey:8 >/dev/null
  docker run -d --name ircs-native-smoke --network ircs-native-smoke -p 127.0.0.1:18080:8080 \
    -e SPRING_DATA_REDIS_HOST=ircs-native-dependency \
    -e APP_CONTENT_SAFETY_INTERNAL_ACCESS_TOKEN=ci-disposable-internal-token \
    -e MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false \
    "$image" >/dev/null
  ready=false
  for attempt in $(seq 1 90); do
    if curl --fail --silent http://127.0.0.1:18080/actuator/health > "$tmp_dir/native-health.json"; then ready=true; break; fi
    sleep 2
  done
  test "$ready" = true
  curl --fail --silent --show-error http://127.0.0.1:18080/actuator/info > "$tmp_dir/native-info.json"
  test "$(curl --silent --output /dev/null --write-out '%{http_code}' http://127.0.0.1:18080/api/v1/native-unsigned-probe)" = 503
  test "$(curl --silent --output /dev/null --write-out '%{http_code}' -H 'Content-Type: application/json' \
    --data '{"items":[]}' http://127.0.0.1:18080/internal/v1/content-safety/adult-assessments:batch)" = 401
  curl --fail --silent --show-error -H 'Content-Type: application/json' \
    -H 'X-IRCS-SERVICE-ID: native-smoke' -H 'X-IRCS-SERVICE-TOKEN: ci-disposable-internal-token' \
    -H 'X-IRCS-SERVICE-SCOPES: content-safety:assess' \
    --data '{"items":[{"id":"00000000-0000-0000-0000-000000000001","title":"普通电影","subtitle":"成人色情影片"}]}' \
    http://127.0.0.1:18080/internal/v1/content-safety/adult-assessments:batch > "$tmp_dir/native-assessment.json"
  export NATIVE_SMOKE_TMP_DIR="$tmp_dir"
  python3 - <<'PY'
import json
import os
from pathlib import Path
directory = Path(os.environ["NATIVE_SMOKE_TMP_DIR"])
assert json.loads((directory / "native-health.json").read_text())["status"] == "UP"
item = json.loads((directory / "native-assessment.json").read_text())["items"][0]
build_version = json.loads((directory / "native-info.json").read_text())["build"]["version"]
assert item["level"] == "ADULT"
assert any(signal["source"].startswith("sensitive-word:") for signal in item["signals"])
assert build_version == os.environ["APP_VERSION"]
print("Native HTTP, Jackson binding and sensitive-word dictionary smoke passed")
PY
fi
