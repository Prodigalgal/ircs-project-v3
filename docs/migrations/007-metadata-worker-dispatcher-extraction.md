# 007 metadata-worker dispatcher extraction

Status: metadata dispatcher slice deployed and smoke-tested in `ircs-dev`

## Target service

`services/ircs-metadata-worker`

## V1 source files

```text
modules/scraper/metadata/MetadataEnrichmentService.java
modules/scraper/metadata/dispatcher/MetadataTaskDispatcher.java
modules/scraper/metadata/worker/MetadataProviderWorker.java
infrastructure/queue/QueueTopic.java
```

## V3 files created

```text
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/metadata/MetadataSearchContext.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataDispatchConsumer.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataDispatchService.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataDispatchResult.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataPendingRegistry.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataProviderRoutingProperties.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataProviderTaskPublisher.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataUuidMessageParser.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/RawVideoEnrichmentRecord.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/RawVideoEnrichmentRepository.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/dispatch/MetadataDispatchServiceTest.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/dispatch/MetadataUuidMessageParserTest.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/dispatch/RawVideoEnrichmentRepositoryTest.java
deploy/k8s/dev/valkey-dev.yaml
```

## Boundary decision

This slice moves the metadata dispatcher/scatter queue out of the V1 monolith:

```text
RabbitMQ q.enrich.metadata
  -> ircs-metadata-worker
  -> shared PostgreSQL raw_videos lookup/update
  -> optional Valkey pending-provider set
  -> provider fetch queues when provider toggles are enabled
  -> raw search sync for immediate terminal outcomes
```

Current dev mode keeps provider workers disabled, so the deployed slice owns the
dispatcher boundary without starting external provider traffic:

```text
APP_METADATA_DISPATCHER_ENABLED=true
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_TMDB_ENABLED=false
APP_METADATA_RT_ENABLED=false
```

## Runtime diagram

```text
                         ircs-dev namespace

                  +-------------------------+
                  | RabbitMQ x.process      |
                  | video.enrich routing    |
                  +-----------+-------------+
                              |
                              v
                  +-------------------------+
                  | q.enrich.metadata       |
                  | consumer: metadata      |
                  +-----------+-------------+
                              |
                              v
        +----------------------------------------------+
        | ircs-metadata-worker                         |
        | - parse raw/quoted UUID                      |
        | - read raw_videos                            |
        | - decide provider fan-out                    |
        | - write Valkey pending set when fan-out runs |
        +----------+----------------------+------------+
                   |                      |
       immediate   |                      | provider toggles enabled later
       terminal    v                      v
          +------------------+    +-----------------------------+
          | x.search         |    | x.process provider routing  |
          | search.sync.raw  |    | metadata.fetch.*            |
          +--------+---------+    +--------------+--------------+
                   |                           deferred
                   v
          +------------------+
          | search-service   |
          | Elasticsearch    |
          +------------------+
```

## Dev middleware

Valkey is added only as a low-profile development dependency:

```text
service:      valkey-svc.ircs-dev.svc.cluster.local:6379
image:        valkey/valkey:8-alpine
persistence:  none, no PVC
maxmemory:    96mb
policy:       allkeys-lru
request:      50m/128Mi
limit:        250m/256Mi
```

## Verification

Local tests:

```text
.\gradlew.bat :services:ircs-metadata-worker:test
  PASS

.\gradlew.bat build
  PASS, BUILD SUCCESSFUL in 26s
```

Image build:

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-metadata-worker' -ImageName ircs-metadata-worker -Tag dev -Architecture arm64 -Os linux
  PASS, pushed harbor.mnnu.eu.org/ircs/ircs-metadata-worker:dev for linux/arm64
```

Deployment:

```text
kubectl apply -f deploy\k8s\dev\valkey-dev.yaml
kubectl rollout status deployment/valkey -n ircs-dev --timeout=180s
kubectl exec -n ircs-dev deployment/valkey -- valkey-cli ping
  PASS, PONG

kubectl apply -f deploy\k8s\dev\metadata-worker-dev.yaml
kubectl rollout restart deployment/ircs-metadata-worker -n ircs-dev
kubectl rollout status deployment/ircs-metadata-worker -n ircs-dev --timeout=240s
  PASS, deployment/ircs-metadata-worker Ready 1/1
```

Queue ownership:

```text
q.enrich.metadata          0 messages, 1 consumer
q.enrich.metadata.result   0 messages, 1 consumer
q.fetch.metadata.douban    0 messages, 0 consumers
q.fetch.metadata.tmdb      0 messages, 0 consumers
q.fetch.metadata.rt        0 messages, 0 consumers
```

End-to-end smoke:

```text
Inserted temporary raw_videos row:
  id=f0dbb653-da2f-450d-bb0b-a573f49bb5b1
  title=IRCS Metadata Dispatcher Smoke
  tmdb_id=tmdb-f0dbb653
  enrichment_status=PENDING
  aggregation_status=BOUND

Published legacy UUID to x.process/video.enrich.

PostgreSQL after metadata dispatcher:
  enrichment_status=SUCCESS
  enrichment_retry_count=0
  aggregation_status=BOUND

Elasticsearch after metadata-worker -> search-service chain:
  GET /ircs_raw_video/_doc/f0dbb653-da2f-450d-bb0b-a573f49bb5b1
  found=true
  externalIds includes tmdb-f0dbb653
```

Cleanup:

```text
Published structured DELETE to x.search/search.sync.raw.
Deleted temporary raw_videos row.

PostgreSQL:
  count(raw_videos where id=f0dbb653-da2f-450d-bb0b-a573f49bb5b1) -> 0

Elasticsearch:
  GET /ircs_raw_video/_doc/f0dbb653-da2f-450d-bb0b-a573f49bb5b1
  found=false
```

Poison message behavior:

```text
Invalid payload:
  codex-bad-metadata-dispatch-smoke

Result:
  rejected with AmqpRejectAndDontRequeueException
  moved to q.enrich.metadata.dlq

After cleanup:
  q.enrich.metadata      0 messages, 1 consumer
  q.enrich.metadata.dlq  0 messages, 0 consumers
```

## Issue found during smoke

The first smoke found a real schema drift bug:

```text
ERROR: relation "data_source_categories" does not exist
```

The dispatcher repository now uses the current V3 schema:

```text
raw_videos
  -> raw_category
  -> standard_category
```

`RawVideoEnrichmentRepositoryTest` guards against accidentally returning to the
old V1 table names.

## Remaining work

- Enable provider dispatch one provider at a time, starting with TMDB.
- Add provider workers for `q.fetch.metadata.tmdb`, `q.fetch.metadata.douban`,
  and `q.fetch.metadata.rt` with strict rate limits.
- Teach the result collector to clear Valkey pending sets when all provider
  results arrive.
- Add cover image creation from provider poster URLs after the storage download
  queue is owned.
