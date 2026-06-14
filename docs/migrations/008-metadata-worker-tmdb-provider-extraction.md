# 008 metadata-worker TMDB provider extraction

Status: TMDB provider-worker slice deployed and smoke-tested in `ircs-dev`

## Target service

`services/ircs-metadata-worker`

## V1 source files

```text
modules/scraper/metadata/worker/MetadataProviderWorker.java
modules/scraper/metadata/provider/TmdbMetadataProvider.java
modules/scraper/metadata/credential/ProviderCredentialPool.java
infrastructure/queue/QueueTopic.java
```

## V3 files created

```text
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/metadata/EnrichedMetadataDTO.java
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/metadata/EnrichmentResultMsg.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/MetadataProvider.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/MetadataProviderRetryableException.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/MetadataProviderTerminalException.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/MetadataProviderResultPublisher.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/MetadataProviderWorker.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/MetadataSearchContextMessageParser.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/tmdb/RestClientTmdbHttpClient.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbCredential.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbCredentialRepository.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbHttpClient.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbMetadataProvider.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbProviderProperties.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbRateLimiter.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/credential/MetadataCredentialServiceProperties.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/provider/MetadataProviderWorkerTest.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/provider/MetadataSearchContextMessageParserTest.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbCredentialRepositoryTest.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbMetadataProviderTest.java
```

## Boundary decision

This slice moves the first external metadata provider worker out of the V1
monolith. The dev deployment enables the TMDB provider queue consumer, but keeps
dispatcher fan-out disabled so normal `video.enrich` traffic does not
automatically call external providers yet:

```text
APP_METADATA_DISPATCHER_ENABLED=true
APP_METADATA_TMDB_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_RT_ENABLED=false
```

The worker owns only the TMDB fetch queue:

```text
owned:     q.fetch.metadata.tmdb
publishes: metadata.result to q.enrich.metadata.result
deferred:  q.fetch.metadata.douban, q.fetch.metadata.rt
```

TMDB credentials are now leased from `ircs-credential-service` through its
internal provider lease API. Credential-service still reads `sys_credentials`
rows where `provider='TMDB'`, `enabled=true`, and `payload` contains `api_key`,
but metadata-worker no longer reads that table directly. Credential rate
limiting is coordinated through Valkey using a per-credential key shaped as:

```text
cred:<credential-id>
```

## Runtime diagram

```text
                         ircs-dev namespace

                  +-------------------------+
                  | RabbitMQ x.process      |
                  | metadata.fetch.tmdb     |
                  +-----------+-------------+
                              |
                              v
                  +-------------------------+
                  | q.fetch.metadata.tmdb   |
                  | consumer: metadata      |
                  +-----------+-------------+
                              |
                              v
        +----------------------------------------------+
        | ircs-metadata-worker                         |
        | - parse MetadataSearchContext                |
        | - lease enabled TMDB credential              |
        |   from credential-service                    |
        | - check Valkey per-credential rate window    |
        | - call TMDB search/detail when credentials   |
        |   exist                                      |
        | - publish EnrichmentResultMsg                |
        +-----------+-------------------+--------------+
                    |                   |
                    | lease credentials |
                    |                   v
                    |      +-------------------------+
                    |      | credential-service      |
                    |      | sys_credentials owner   |
                    |      +-------------------------+
                    |
                    | publish result
                    v
                  +-------------------------+
                  | q.enrich.metadata.result|
                  | consumer: metadata      |
                  +-----------+-------------+
                              |
                              v
        +----------------------------------------------+
        | result collector                             |
        | - update raw_videos                          |
        | - publish search.sync.raw                    |
        +----------------------+-----------------------+
                               |
                               v
                  +-------------------------+
                  | search-service          |
                  | Elasticsearch           |
                  +-------------------------+
```

## Dev no-credential behavior

The current `ircs-dev` database has no enabled TMDB credentials:

```text
select provider, count(*) as enabled_count
from sys_credentials
where provider = 'TMDB' and enabled
group by provider;

provider | enabled_count
---------+--------------
(0 rows)
```

That is intentional for this slice. The smoke validates queue ownership and
result handling without calling the public TMDB API. With an empty credential
pool, the worker publishes a retryable failure:

```text
provider=TMDB
success=false
retryable=true
errorCode=POOL_EXHAUSTED
```

## Verification

Local tests:

```text
.\gradlew.bat :services:ircs-metadata-worker:test
  PASS

.\gradlew.bat build
  PASS, BUILD SUCCESSFUL in 1m16s
```

Image build:

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-metadata-worker' -ImageName ircs-metadata-worker -Tag dev -Architecture arm64 -Os linux
  PASS, pushed harbor.mnnu.eu.org/ircs/ircs-metadata-worker:dev for linux/arm64
```

Deployment:

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\metadata-worker-dev.yaml
  PASS

kubectl apply -f deploy\k8s\dev\metadata-worker-dev.yaml
kubectl rollout restart deployment/ircs-metadata-worker -n ircs-dev
kubectl rollout status deployment/ircs-metadata-worker -n ircs-dev --timeout=240s
  PASS, deployment/ircs-metadata-worker Ready 1/1
```

Queue ownership after rollout:

```text
q.enrich.metadata          0 messages, 1 consumer
q.enrich.metadata.result   0 messages, 1 consumer
q.fetch.metadata.tmdb      0 messages, 1 consumer
q.fetch.metadata.douban    0 messages, 0 consumers
q.fetch.metadata.rt        0 messages, 0 consumers
```

End-to-end smoke:

```text
Inserted temporary raw_videos row:
  id=0b3b8f17-f8d2-4979-8659-0a7c29a3e6fa
  title=IRCS TMDB Worker Smoke
  enrichment_status=PENDING
  enrichment_retry_count=0
  aggregation_status=BOUND

Published MetadataSearchContext JSON to x.process/metadata.fetch.tmdb.

metadata-worker log:
  Retryable metadata provider failure. provider=TMDB,
  videoId=0b3b8f17-f8d2-4979-8659-0a7c29a3e6fa,
  code=POOL_EXHAUSTED

PostgreSQL after result collector:
  enrichment_status=FAILED
  enrichment_retry_count=1
  aggregation_status=BOUND
  tmdb_id is empty

Elasticsearch after metadata-worker -> search-service chain:
  GET /ircs_raw_video/_doc/0b3b8f17-f8d2-4979-8659-0a7c29a3e6fa
  found=true
  enrichmentStatus=FAILED
  externalIds=[]
```

Cleanup:

```text
Published structured DELETE to x.search/search.sync.raw.
Deleted temporary raw_videos row.

PostgreSQL:
  count(raw_videos where id=0b3b8f17-f8d2-4979-8659-0a7c29a3e6fa) -> 0

Elasticsearch:
  GET /ircs_raw_video/_doc/0b3b8f17-f8d2-4979-8659-0a7c29a3e6fa
  found=false
```

Poison message behavior:

```text
Invalid payload:
  codex-bad-tmdb-provider-smoke

Result:
  rejected with AmqpRejectAndDontRequeueException
  moved to q.fetch.metadata.tmdb.dlq

After cleanup:
  q.fetch.metadata.tmdb      0 messages, 1 consumer
  q.fetch.metadata.tmdb.dlq  0 messages, 0 consumers
```

Resource profile after rollout:

```text
pods:            10 / 20
services:        11 / 20
requests.cpu:    875m / 4
requests.memory: 2304Mi / 8Gi
limits.cpu:      5250m / 8
limits.memory:   6Gi / 16Gi
HTTPRoute:       none in ircs-dev
```

## Remaining work

- Add a real dev TMDB credential only when we are ready for controlled external
  provider traffic.
- Enable dispatcher fan-out to TMDB after a credential exists and rate-limit
  smoke passes.
- Add Douban and Rotten Tomatoes provider workers as separate slices.
- Teach the result collector to clear Valkey pending sets when all provider
  results arrive.
- Add cover image creation from provider poster URLs after the storage download
  queue is owned.
