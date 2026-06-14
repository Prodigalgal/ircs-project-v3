# 006 metadata-worker result extraction

Status: metadata result collector slice deployed and smoke-tested in `ircs-dev`

## Target service

`services/ircs-metadata-worker`

## V1 source files

```text
modules/scraper/metadata/collector/MetadataResultCollector.java
modules/scraper/model/dto/EnrichmentResultMsg.java
modules/scraper/model/dto/EnrichedMetadataDTO.java
modules/scraper/metadata/MetadataEnrichmentService.java
modules/scraper/metadata/worker/MetadataProviderWorker.java
infrastructure/queue/QueueTopic.java
```

## V3 files created

```text
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/metadata/ProviderType.java
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/metadata/EnrichedMetadataDTO.java
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/metadata/EnrichmentResultMsg.java
services/ircs-metadata-worker/build.gradle
services/ircs-metadata-worker/src/main/resources/application.yaml
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/result/MetadataResultConsumer.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/result/MetadataResultMessageParser.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/result/MetadataResultCollectorService.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/result/MetadataResultProcessingResult.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/result/RawVideoMetadataRecord.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/result/RawVideoMetadataRepository.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/result/RawSearchSyncPublisher.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/result/MetadataResultMessageParserTest.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/result/MetadataResultCollectorServiceTest.java
deploy/k8s/dev/metadata-worker-dev.yaml
```

## Boundary decision

This slice moves the metadata result settlement worker out of the V1 monolith:

```text
RabbitMQ q.enrich.metadata.result
  -> ircs-metadata-worker
  -> shared PostgreSQL raw_videos metadata update
  -> publish raw search sync
  -> ircs-search-service updates Elasticsearch
```

The service now owns:

- `EnrichmentResultMsg` JSON parsing
- basic raw video metadata updates for provider IDs, description, score, year,
  and alias title
- V1-compatible locked-field checks for description, score, and year
- enrichment status settlement for single result messages
- raw search sync publication after database update
- invalid message rejection without requeue, so poison messages go to DLQ

The service does not yet own:

- `q.enrich.metadata` dispatcher/scatter
- `q.fetch.metadata.douban`
- `q.fetch.metadata.tmdb`
- `q.fetch.metadata.rt`
- Redis pending-set coordination across multiple providers
- actor/director/genre/language/area association resolution
- cover image creation from provider poster URLs

The dev deployment intentionally keeps this first metadata slice light:

```text
APP_METADATA_LISTENER_ENABLED=true
dependencies: RabbitMQ + JDBC only
no Redis, no Spring AI, no external provider clients
```

## Queues

```text
Exchange: x.process
Routing:  metadata.result
Queue:    q.enrich.metadata.result
Owner:    ircs-metadata-worker
Payload:  EnrichmentResultMsg JSON

Exchange: x.search
Routing:  search.sync.raw
Queue:    q.search.sync.raw
Owner:    ircs-search-service
Payload:  legacy UUID for INDEX
```

## Dev dependencies

PostgreSQL is namespace-local in dev:

```text
service:
  postgres-svc.ircs-dev.svc.cluster.local:5432
database:
  ircs
table:
  raw_videos
```

RabbitMQ is namespace-local in dev:

```text
service:
  rabbitmq-svc.ircs-dev.svc.cluster.local:5672
exchange:
  x.process
queue:
  q.enrich.metadata.result
```

Do not point metadata-worker at `postgres-svc` or `rabbitmq-svc` in
`ircs-system`.

## Verification

Local tests:

```text
.\gradlew.bat :services:ircs-metadata-worker:test
  PASS
```

Root build:

```text
.\gradlew.bat build
  PASS, BUILD SUCCESSFUL in 21s
```

Image build:

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath :services:ircs-metadata-worker -ImageName ircs-metadata-worker -Tag dev -Architecture arm64 -Os linux
  PASS, pushed harbor.mnnu.eu.org/ircs/ircs-metadata-worker:dev for linux/arm64
```

Deployment:

```text
kubectl apply -f deploy\k8s\dev\metadata-worker-dev.yaml
kubectl rollout status deployment/ircs-metadata-worker -n ircs-dev --timeout=240s
  PASS, deployment/ircs-metadata-worker Ready 1/1
```

Startup checks:

```text
ircs-metadata-worker:
  Ready 1/1
  Spring Boot started on port 8080
  RabbitMQ connected to rabbitmq-svc.ircs-dev.svc.cluster.local:5672

rabbitmqctl list_queues name messages consumers:
  q.enrich.metadata.result      0 messages, 1 consumer
  q.enrich.metadata.result.dlq  0 messages, 0 consumers after cleanup
  q.enrich.metadata             0 messages, 0 consumers
  q.fetch.metadata.douban       0 messages, 0 consumers
  q.fetch.metadata.tmdb         0 messages, 0 consumers
  q.fetch.metadata.rt           0 messages, 0 consumers
```

End-to-end smoke:

```text
Inserted temporary raw_videos row:
  id=4263b56b-5fb6-42a6-a530-3c6a43d18e6d
  title=IRCS Metadata Worker Smoke
  enrichment_status=PENDING
  aggregation_status=BOUND

Published EnrichmentResultMsg JSON to x.process/metadata.result:
  providerType=TMDB
  tmdbId=tmdb-4263b56b
  imdbId=tt2943679
  description=IRCS_METADATA_WORKER_SMOKE_DESCRIPTION
  year=2026
  score=8.6
  originalTitle=IRCS_METADATA_WORKER_ORIGINAL

PostgreSQL after metadata-worker:
  tmdb_id=tmdb-4263b56b
  imdb_id=tt2943679
  description=IRCS_METADATA_WORKER_SMOKE_DESCRIPTION
  year=2026
  score=8.6
  alias_title=IRCS_METADATA_WORKER_ORIGINAL
  enrichment_status=SUCCESS
  enrichment_retry_count=0
  aggregation_status=PENDING

Elasticsearch after metadata-worker -> search-service chain:
  GET /ircs_raw_video/_doc/4263b56b-5fb6-42a6-a530-3c6a43d18e6d
  found=true
  externalIds includes tmdb-4263b56b and tt2943679
```

Cleanup:

```text
Published structured DELETE to x.search/search.sync.raw.
Deleted temporary raw_videos row.

PostgreSQL:
  count(raw_videos where id=4263b56b-5fb6-42a6-a530-3c6a43d18e6d) -> 0

Elasticsearch:
  GET /ircs_raw_video/_doc/4263b56b-5fb6-42a6-a530-3c6a43d18e6d
  found=false
```

Poison message behavior:

```text
Invalid payload:
  codex-bad-metadata-result-smoke

Result:
  rejected with AmqpRejectAndDontRequeueException
  moved to q.enrich.metadata.result.dlq

After cleanup:
  q.enrich.metadata.result      0 messages, 1 consumer
  q.enrich.metadata.result.dlq  0 messages, 0 consumers
```

Final dev state:

```text
deployments:
  ircs-metadata-worker Ready 1/1
  ircs-search-service Ready 1/1

resource usage:
  pods:            9 / 20
  services:        10 / 20
  requests.cpu:    825m / 4
  requests.memory: 2176Mi / 8Gi
  limits.cpu:      5 / 8
  limits.memory:   5888Mi / 16Gi

No public HTTPRoute in ircs-dev.
```

## Remaining work

- Add Redis/Valkey dev middleware before porting provider pending-set
  coordination.
- Move `q.enrich.metadata` dispatcher/scatter.
- Move provider workers for Douban, TMDB, and Rotten Tomatoes with rate limits
  and retry classification.
- Add actor/director/genre/language/area association resolution.
- Add cover image creation for metadata poster URLs.
