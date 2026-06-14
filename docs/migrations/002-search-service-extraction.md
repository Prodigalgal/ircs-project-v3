# 002 search-service extraction

Status: real JDBC + Elasticsearch processor deployed and smoke-tested in `ircs-dev`

## Target service

`services/ircs-search-service`

## V1 source files

```text
modules/search/consumer/SearchSyncConsumer.java
modules/search/model/dto/SearchSyncMessage.java
modules/search/model/enums/SearchEntityType.java
modules/search/model/enums/SyncOperation.java
modules/search/service/SearchSyncService.java
modules/search/service/impl/SearchSyncServiceImpl.java
modules/search/scheduler/SearchOutboxRelay.java
modules/search/listener/SearchEventListener.java
```

## V3 files created

```text
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/search/SearchEntityType.java
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/search/SearchSyncMessage.java
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/search/SyncOperation.java
services/ircs-search-service/build.gradle
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/document/RawVideoSearchDocument.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/document/UnifiedVideoSearchDocument.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/index/SearchIndexService.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/repository/SearchDocumentJdbcRepository.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/support/CoverImageUrlResolver.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/support/SearchTextHelper.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/sync/JdbcSearchSyncProcessor.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/sync/SearchSyncConsumer.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/sync/SearchSyncMessageParser.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/sync/SearchSyncProcessor.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/sync/UnsupportedSearchSyncProcessor.java
services/ircs-search-service/src/main/resources/application.yaml
services/ircs-search-service/src/main/resources/elasticsearch/es-settings.json
services/ircs-search-service/src/test/java/com/prodigalgal/ircs/search/support/SearchTextHelperTest.java
services/ircs-search-service/src/test/java/com/prodigalgal/ircs/search/sync/JdbcSearchSyncProcessorTest.java
services/ircs-search-service/src/test/java/com/prodigalgal/ircs/search/sync/SearchSyncMessageParserTest.java
deploy/k8s/dev/search-service-dev.yaml
```

## Boundary decision

This slice moves search sync ownership out of the V1 monolith:

```text
RabbitMQ q.search.sync.raw/q.search.sync.unified
  -> ircs-search-service
  -> shared PostgreSQL read model during transition
  -> namespace-local Elasticsearch indices
```

The service now owns:

- search sync message parsing and queue-specific legacy UUID compatibility
- raw/unified video read-model queries through JDBC
- raw/unified Elasticsearch document mapping
- index creation using V1-compatible IK/Pinyin settings
- INDEX and DELETE processing
- invalid message rejection without requeue, so poison messages go to DLQ

The service does not yet own the producer side. V1 outbox/event emission can
still write search messages while V3 consumes them. The shared database remains
a temporary migration bridge until content/catalog ownership is split.

The dev deployment enables queue consumption:

```text
APP_SEARCH_LISTENER_ENABLED=true
```

## Queues

```text
Exchange: x.search
Routing:  search.sync.raw
Queue:    q.search.sync.raw
Owner:    ircs-search-service
Payload:  SearchSyncMessage or legacy UUID

Exchange: x.search
Routing:  search.sync.unified
Queue:    q.search.sync.unified
Owner:    ircs-search-service
Payload:  SearchSyncMessage or legacy UUID
```

## Compatibility

The V3 parser supports both V1 formats:

```text
Structured JSON:
  {"entityId":"...","entityType":"RAW_VIDEO","operation":"INDEX"}

Legacy UUID:
  "69e3d71f-fc47-48dc-8f48-5115e2ed5454"
```

Legacy UUID messages default to `INDEX` with the queue-specific entity type.
Invalid payloads are rejected with `AmqpRejectAndDontRequeueException`.

## Dev dependencies

PostgreSQL is namespace-local in dev:

```text
deploy/k8s/dev/postgres-dev.yaml
image: postgres:18-alpine
service:
  postgres-svc.ircs-dev.svc.cluster.local:5432
database:
  ircs
```

The V1 baseline changelog is applied through the dedicated migrator Job:

```text
deploy/k8s/dev/migrator-job-dev.yaml
databasechangelog rows: 122
public tables:          45
search task table:      search_sync_tasks
```

`ircs-search-service` injects datasource settings from dev ConfigMap/Secret:

```text
SPRING_DATASOURCE_URL      <- DB_URL
SPRING_DATASOURCE_USERNAME <- postgres
SPRING_DATASOURCE_PASSWORD <- DB_PASSWORD
```

Elasticsearch is namespace-local in dev:

```text
deploy/k8s/dev/elasticsearch-dev.yaml
image: docker.elastic.co/elasticsearch/elasticsearch:9.1.4
plugins:
  elasticsearch-analysis-ik-9.1.4
  elasticsearch-analysis-pinyin-9.1.4
service:
  elasticsearch-svc.ircs-dev.svc.cluster.local:9200
```

The dev search-service manifest wires Spring Elasticsearch environment
variables to the namespace-local service:

```text
SPRING_ELASTICSEARCH_URIS     <- ELASTICSEARCH_URIS
SPRING_ELASTICSEARCH_USERNAME <- elastic
SPRING_ELASTICSEARCH_PASSWORD <- ELASTICSEARCH_PASSWORD
```

Do not point search-service at `postgres-svc`, `rabbitmq-svc`, or
`elasticsearch-svc` in `ircs-system`.

## Verification

Local build:

```text
.\gradlew.bat :services:ircs-search-service:test
  PASS

.\gradlew.bat :services:ircs-search-service:build
  PASS
```

Image build:

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath :services:ircs-search-service -ImageName ircs-search-service -Tag dev -Architecture arm64 -Os linux
  PASS, pushed harbor.mnnu.eu.org/ircs/ircs-search-service:dev for linux/arm64
```

Deployment:

```text
kubectl rollout restart deployment/ircs-search-service -n ircs-dev
kubectl rollout status deployment/ircs-search-service -n ircs-dev --timeout=240s
  PASS, deployment/ircs-search-service Ready 1/1
```

Startup checks:

```text
SearchIndexService startup:
  created or verified ircs_raw_video
  created or verified ircs_unified_video

rabbitmqctl list_queues name messages consumers:
  q.search.sync.raw      0 messages, 1 consumer
  q.search.sync.unified  0 messages, 1 consumer
  q.notification.mail    0 messages, 1 consumer
```

Smoke INDEX:

```text
Inserted dev raw_videos row:
  id=765b00c1-50bb-4846-a955-e3ba960b21a9
  title=IRCS Dev Smoke Movie

Published legacy UUID to x.search/search.sync.raw:
  Message published and routed successfully

GET /ircs_raw_video/_doc/765b00c1-50bb-4846-a955-e3ba960b21a9:
  found=true
  title=IRCS Dev Smoke Movie
  normalizedTitle=ircs dev smoke movie
  sourceVid=dev-smoke-765b00c1
```

Smoke DELETE:

```text
Published structured JSON DELETE to x.search/search.sync.raw:
  {"entityId":"765b00c1-50bb-4846-a955-e3ba960b21a9","entityType":"RAW_VIDEO","operation":"DELETE"}

GET /ircs_raw_video/_doc/765b00c1-50bb-4846-a955-e3ba960b21a9:
  found=false
```

Poison message behavior:

```text
Invalid CLI-escaped JSON payload:
  rejected with AmqpRejectAndDontRequeueException
  moved to q.search.sync.raw.dlq

After cleanup:
  q.search.sync.raw      0 messages, 1 consumer
  q.search.sync.raw.dlq  0 messages, 0 consumers
```

Final dev state:

```text
deployments:
  ircs-search-service Ready 1/1

indices:
  ircs_raw_video      green/open, docs.count=0, replicas=0
  ircs_unified_video  green/open, docs.count=0, replicas=0

No public HTTPRoute in ircs-dev.
```

## Remaining work

- Add a repeatable smoke script for INDEX/DELETE so this can run in CI/dev
  without manual RabbitMQ CLI quoting.
- Port the search outbox relay or event producer from V1 after the next content
  ownership slice is chosen.
- Add integration tests with a real PostgreSQL + Elasticsearch container stack
  once test resource cost is acceptable.
- Split read-model ownership away from the shared database after content,
  catalog, and storage services are extracted.
