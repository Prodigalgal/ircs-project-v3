#!/usr/bin/env bash
set -euo pipefail

service="$1"
image="$2"
case "$service" in
  ircs-content-safety-service|ircs-migrator) ;;
  *) exit 0 ;;
esac

cleanup() {
  docker logs ircs-native-smoke 2>&1 || true
  docker rm -f ircs-native-smoke ircs-native-dependency >/dev/null 2>&1 || true
  docker network rm ircs-native-smoke >/dev/null 2>&1 || true
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
    -e SPRING_DATASOURCE_URL=jdbc:postgresql://ircs-native-dependency:5432/ircs \
    -e SPRING_DATASOURCE_USERNAME=ircs -e SPRING_DATASOURCE_PASSWORD=ci-disposable-password \
    "$image"
  migrations=$(docker exec ircs-native-dependency psql -U ircs -d ircs -Atc 'select count(*) from databasechangelog')
  test "$migrations" -gt 0
  echo "Native migrator applied $migrations changesets to disposable PostgreSQL"
else
  docker run -d --network ircs-native-smoke --name ircs-native-dependency valkey/valkey:8 >/dev/null
  docker run -d --name ircs-native-smoke --network ircs-native-smoke -p 127.0.0.1:18080:8080 \
    -e SPRING_DATA_REDIS_HOST=ircs-native-dependency \
    -e MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false \
    "$image" >/dev/null
  ready=false
  for attempt in $(seq 1 90); do
    if curl --fail --silent http://127.0.0.1:18080/actuator/health > native-health.json; then ready=true; break; fi
    sleep 2
  done
  test "$ready" = true
  curl --fail --silent --show-error -H 'Content-Type: application/json' \
    --data '{"items":[{"id":"00000000-0000-0000-0000-000000000001","title":"普通电影","subtitle":"成人色情影片"}]}' \
    http://127.0.0.1:18080/internal/v1/content-safety/adult-assessments:batch > native-assessment.json
  python3 - <<'PY'
import json
from pathlib import Path
assert json.loads(Path("native-health.json").read_text())["status"] == "UP"
item = json.loads(Path("native-assessment.json").read_text())["items"][0]
assert item["level"] == "ADULT"
assert any(signal["source"].startswith("sensitive-word:") for signal in item["signals"])
print("Native HTTP, Jackson binding and sensitive-word dictionary smoke passed")
PY
fi
