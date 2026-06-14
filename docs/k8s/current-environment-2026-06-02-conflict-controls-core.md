# K8S current environment conflict controls - 2026-06-02

来源：`current-environment-2026-06-02-base.md` 拆分。

## Conflict controls

Confirmed safe:

- Telepresence traffic-manager is installed in `ircs-dev`.
- Helm values restrict Telepresence managed namespaces to `ircs-dev`.
- Local Telepresence status maps only `[ircs-dev]`.
- No external HTTPRoute exists in `ircs-dev`.
- `ircs-portal-frontend`, `ircs-admin-frontend`, `ircs-frontend-gateway`,
  `ircs-portal-bff`, and `ircs-admin-bff` are deployed as separate Pods and
  expose only ClusterIP Services in `ircs-dev`.
- `ircs-frontend-gateway` routes portal API traffic to `ircs-portal-bff` and
  admin API traffic to `ircs-admin-bff`; it does not attach to
  `production-gateway`.
- `deploy/k8s/dev/notification-worker-dev.yaml` is applied and Ready 1/1.
- `deploy/k8s/dev/search-service-dev.yaml` is applied and Ready 1/1.
- `deploy/k8s/dev/postgres-dev.yaml` is applied and Ready 1/1.
- `deploy/k8s/dev/rabbitmq-dev.yaml` is applied and Ready 1/1.
- `deploy/k8s/dev/elasticsearch-dev.yaml` is applied and Ready 1/1.
- `deploy/k8s/dev/valkey-dev.yaml` is applied and Ready 1/1; `valkey-cli ping`
  returns `PONG`.
- `deploy/k8s/dev/migrator-job-dev.yaml` completed successfully once in
  `ircs-dev` using image `harbor.mnnu.eu.org/ircs/ircs-migrator:dev`.
- The notification worker dev config points RabbitMQ to
  `rabbitmq-svc.ircs-dev.svc.cluster.local`, not `ircs-system`.
- The shared dev ConfigMap `DB_URL` points PostgreSQL to
  `postgres-svc.ircs-dev.svc.cluster.local:5432`, not `ircs-system`.
- `ircs-search-service` injects datasource credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to
  `postgres-svc.ircs-dev.svc.cluster.local:5432`.
- The search-service dev config points Elasticsearch to
  `elasticsearch-svc.ircs-dev.svc.cluster.local:9200`, not `ircs-system`.
- `ircs-storage-service` injects datasource credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to
  `postgres-svc.ircs-dev.svc.cluster.local:5432`.
- The storage-service dev config points RabbitMQ to
  `rabbitmq-svc.ircs-dev.svc.cluster.local`, not `ircs-system`.
- The storage-service dev config keeps R2 disabled and uses `emptyDir` at
  `/app/storage`, so this test slice does not claim another PVC.
- `ircs-storage-service` now owns `/api/v1/cover-images/**` admin API in
  addition to image delete/download workers. It exposes only ClusterIP and has
  no public HTTPRoute.
- `ircs-content-service` injects datasource credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to
  `postgres-svc.ircs-dev.svc.cluster.local:5432`.
- The content-service dev config points RabbitMQ to
  `rabbitmq-svc.ircs-dev.svc.cluster.local`, not `ircs-system`.
- `ircs-metadata-worker` injects datasource credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to
  `postgres-svc.ircs-dev.svc.cluster.local:5432`.
- The metadata-worker dev config points RabbitMQ to
  `rabbitmq-svc.ircs-dev.svc.cluster.local`, not `ircs-system`.
- The metadata-worker dev config points Valkey to
  `valkey-svc.ircs-dev.svc.cluster.local`, not `ircs-system`.
- The metadata-worker dev config points the credential-service client to
  `http://ircs-credential-service.ircs-dev.svc.cluster.local:8080`; TMDB
  credential lookup no longer reads `sys_credentials` directly from
  metadata-worker.
- The metadata-worker has no ClusterIP Service after worker-only Service
  pruning; it remains a RabbitMQ worker Deployment with Pod-level probes.
- `ircs-credential-service` injects datasource credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to
  `postgres-svc.ircs-dev.svc.cluster.local:5432`.
- The credential-service dev config exposes only a ClusterIP service and has no
  public HTTPRoute.
- `ircs-credential-service` requires `X-IRCS-INTERNAL-TOKEN` for
  `/internal/credentials/**`; `ircs-metadata-worker` sends the same token from
  `ircs-dev-secrets`.
- `ircs-catalog-service` injects datasource credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to
  `postgres-svc.ircs-dev.svc.cluster.local:5432`.
- The catalog-service dev config exposes only a ClusterIP service and has no
  public HTTPRoute.
- `ircs-config-service` injects datasource credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to
  `postgres-svc.ircs-dev.svc.cluster.local:5432`.
- The config-service dev config exposes only a ClusterIP service and has no
  public HTTPRoute. Sensitive config values are redacted in read responses.
- `ircs-task-service` injects datasource credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to
  `postgres-svc.ircs-dev.svc.cluster.local:5432`.
- The task-service dev config exposes only a ClusterIP service and has no
  public HTTPRoute. It reads `collection_tasks` and `data_sources` only; task
  commands, logs, scheduler, watchdog, and queue publishing are deferred.
- `ircs-ingestion-worker` injects datasource and RabbitMQ credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to namespace-local
  PostgreSQL and RabbitMQ in `ircs-dev`, not `ircs-system`.
- The ingestion-worker dev config had no public HTTPRoute before worker-only
  Service pruning. It now has no ClusterIP Service and consumes
  `q.ingest.video` and
  `q.process.playlist_sync`, writes `raw_videos`, `playlists`, `episodes`, and
  `source_domains` as a migration bridge, and publishes `q.normalize.video`.
- `ircs-normalization-worker` injects datasource and RabbitMQ credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to namespace-local
  PostgreSQL and RabbitMQ in `ircs-dev`, not `ircs-system`.
- The normalization-worker dev config had no public HTTPRoute and now has no
  ClusterIP Service after worker-only Service pruning. It consumes
  `q.normalize.video`, writes lightweight
  normalized fields and `normalization_status` on `raw_videos`, and publishes
  `q.search.sync.raw`.
- `ircs-aggregation-worker` injects datasource and RabbitMQ credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to namespace-local
  PostgreSQL and RabbitMQ in `ircs-dev`, not `ircs-system`.
- The aggregation-worker dev config had no public HTTPRoute and now has no
  ClusterIP Service after worker-only Service pruning. It does not consume a
  RabbitMQ aggregation queue; it scans
  `raw_videos`, writes `unified_videos` and `raw_video_unified_video` as a
  migration bridge, updates `aggregation_status`, and publishes
  `q.search.sync.raw` plus `q.search.sync.unified`.
- `ircs-magnet-service` injects datasource credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to namespace-local
  PostgreSQL in `ircs-dev`, not `ircs-system`.
- The magnet-service dev config exposes only a ClusterIP service and has no
  public HTTPRoute. It reads `magnet_providers` and `APPROVED`
  `magnet_links`; external provider search and job/run writes are deferred.
- `ircs-identity-service` injects datasource, Valkey, RabbitMQ and JWT/mail
  settings from `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to
  namespace-local PostgreSQL, Valkey and RabbitMQ in `ircs-dev`, not
  `ircs-system`.
- The identity-service dev config exposes only a ClusterIP service and has no
  public HTTPRoute. It owns member auth/profile MVP routes in this slice.
- `ircs-scraper-service` injects datasource and RabbitMQ credentials from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to namespace-local
  PostgreSQL and RabbitMQ in `ircs-dev`, not `ircs-system`.
- The scraper-service dev config exposes only a ClusterIP service and has no
  public HTTPRoute. It owns manual scrape init/SSE routes in this slice and
  publishes `q.ingest.video`; it does not write content tables directly.
- `ircs-portal-service` injects datasource, Valkey and service-client settings
  from `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to namespace-local
  dependencies and ClusterIP services in `ircs-dev`, not `ircs-system`.
- The portal-service dev config exposes only a ClusterIP service and has no
  public HTTPRoute. It owns portal home and metadata read composition in this
  slice.
- `ircs-interaction-service` injects datasource, RabbitMQ and JWT settings from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to namespace-local
  PostgreSQL and RabbitMQ in `ircs-dev`, not `ircs-system`.
- The interaction-service dev config exposes only a ClusterIP service and has
  no public HTTPRoute. It owns feedback wall, watch history and favorites read
  APIs in this slice; watch-progress queue consumption is deferred.
- `ircs-ops-service` injects datasource, RabbitMQ and Valkey settings from
  `ircs-dev-app-config` and `ircs-dev-secrets`, pointing to namespace-local
  PostgreSQL, RabbitMQ and Valkey in `ircs-dev`, not `ircs-system`.
- The ops-service dev config exposes only a ClusterIP service and has no public
  HTTPRoute. It owns dashboard read endpoints, maintenance session
  compatibility, request audit read API, DLQ read API, and traffic status read
  API in this slice; destructive maintenance, DLQ retry/discard and traffic reset
  are deferred in this historical slice. As of `028-01`, portal/admin BFF
  request audit collection is complete; per-service handler-level audit remains
  deferred.
- Metadata provider fan-out toggles are disabled in dev:
  `APP_METADATA_DOUBAN_ENABLED=false`,
  `APP_METADATA_TMDB_ENABLED=false`,
  `APP_METADATA_RT_ENABLED=false`.
- The TMDB provider worker listener is enabled in dev with
  `APP_METADATA_TMDB_WORKER_ENABLED=true`; this consumes direct
  `metadata.fetch.tmdb` tasks but does not make the dispatcher fan out to TMDB.
- PostgreSQL now has V1-imported enabled `TMDB` rows in `sys_credentials`; dev
  keeps `APP_METADATA_TMDB_ENABLED=false` by default to avoid automatic fan-out,
  while direct live queue smoke can temporarily enable and then restore it.
- PostgreSQL accepts connections for database `ircs` as user `postgres`.
- The V1 changelog baseline has been applied in dev:
  `databasechangelog=122`, public tables `45`, extensions `pg_trgm` and
  `plpgsql`.
- The search sync task table name is `search_sync_tasks`.
- `q.notification.mail` has 1 consumer in the `ircs-dev` RabbitMQ broker.
- `q.search.sync.raw` and `q.search.sync.unified` each have 1 consumer from
  `ircs-search-service`.
- `q.storage.image_delete` has 1 consumer from `ircs-storage-service`.
- `q.storage.image_unlink` has 1 consumer from `ircs-content-service`.
- `q.storage.image` has 1 consumer from `ircs-storage-service`.
- `q.storage.avatar_sync` still has 0 consumers in this slice.
- `q.enrich.metadata.result` has 1 consumer from `ircs-metadata-worker`.
- `q.enrich.metadata` has 1 consumer from `ircs-metadata-worker`.
- `q.fetch.metadata.tmdb` has 1 consumer from `ircs-metadata-worker`.
- `q.ingest.video` has 1 consumer from `ircs-ingestion-worker`.
- `q.process.playlist_sync` has 1 consumer from `ircs-ingestion-worker`.
- `q.normalize.video` has 1 consumer from `ircs-normalization-worker`.
- `q.interaction.watch_progress` has 1 consumer from `ircs-interaction-service`
  after `028-05-A`; messages and DLQ are 0 after smoke.
- `q.fetch.metadata.douban` and `q.fetch.metadata.rt` still have 0 consumers in
  this slice, and no messages accumulated there.
- Elasticsearch cluster health is green and includes `analysis-ik` and
  `analysis-pinyin` version `9.1.4`.
- Search indices exist in dev with zero replicas for the single-node profile:
  `ircs_raw_video green/open docs.count=0 rep=0` and
  `ircs_unified_video green/open docs.count=0 rep=0`.
- Search smoke passed:
  legacy UUID INDEX created `ircs_raw_video` document
  `765b00c1-50bb-4846-a955-e3ba960b21a9`; structured JSON DELETE removed it;
  invalid payload rejection moved to DLQ and was cleaned up.
- Storage delete smoke passed:
  legacy UUID payload deleted temporary `cover_images` metadata
  `91e45085-8c83-4781-b709-d7586d249e6d`; invalid payload rejection moved to
  `q.storage.image_delete.dlq` and was cleaned up.
- Content unlink smoke passed:
  legacy UUID payload to `x.storage/image.cmd.unlink` removed temporary
  raw/unified cover image references for
  `307b726f-d59f-4eb1-bf29-9647bbba29c0`, then storage-service consumed
  `image.event.unlinked` and deleted the temporary `cover_images` metadata;
  invalid payload rejection moved to `q.storage.image_unlink.dlq` and was
  cleaned up.
- Metadata result smoke passed:
  EnrichmentResultMsg JSON to `x.process/metadata.result` updated temporary
  `raw_videos` metadata for `4263b56b-5fb6-42a6-a530-3c6a43d18e6d`, then
  published raw search sync and `ircs-search-service` indexed the document in
  `ircs_raw_video`; structured DELETE removed the ES document and the temporary
  DB row was cleaned up; invalid payload rejection moved to
  `q.enrich.metadata.result.dlq` and was cleaned up.
- Metadata dispatcher smoke passed:
  legacy UUID to `x.process/video.enrich` updated temporary `raw_videos`
  metadata for `f0dbb653-da2f-450d-bb0b-a573f49bb5b1` to
  `enrichment_status=SUCCESS`, published raw search sync, and
  `ircs-search-service` indexed the document in `ircs_raw_video`; structured
  DELETE removed the ES document and the temporary DB row was cleaned up;
  invalid payload rejection moved to `q.enrich.metadata.dlq` and was cleaned up.
- Metadata dispatcher smoke found and fixed a schema drift bug where the new
  repository used the old `data_source_categories`/`categories` names instead
  of current `raw_category`/`standard_category` tables.
- TMDB provider worker historical no-credential smoke passed:
  MetadataSearchContext JSON to `x.process/metadata.fetch.tmdb` was consumed by
  `ircs-metadata-worker`; when dev had 0 enabled TMDB credentials, the worker
  published retryable `POOL_EXHAUSTED`, and cleanup left DB/ES/queues empty.
  As of the V1 credential import slice, the same path has also passed live
  success with a real V1-imported TMDB credential.
- Credential-service smoke passed:
  `/actuator/health/readiness` returned `{"status":"UP"}`; a temporary
  `sys_credentials` row with fake TMDB payload keys was returned from
  `/api/v1/credentials?provider=TMDB&enabled=true&limit=5` with `payloadKeys`
  and `fingerprintSuffix` only, and without the fake payload value; the
  temporary row was deleted and the same TMDB query returned `[]`.
- Credential-service smoke found and fixed a SQL parameter typing bug where
  optional filters used `(:enabled is null or enabled = :enabled)`, which
  PostgreSQL could not type-infer for nullable prepared parameters.
- Credential-service provider lease smoke passed:
  a temporary `sys_credentials` row for `TMDB` was visible through
  `/internal/credentials/providers/TMDB/leases` with scalar `secretPayload`
  values, then deleted; after cleanup the TMDB lease pool returned `[]`.
- Metadata-worker credential-service client smoke passed:
  the worker consumed `MetadataSearchContext` for
  `1f463d41-83c4-4723-984c-2ec1fc084bdb`, reached `POOL_EXHAUSTED` through the
  credential-service lease path, updated `raw_videos` to
  `enrichment_status=FAILED` and `enrichment_retry_count=1`, indexed the failed
  document in `ircs_raw_video`, then structured DELETE and DB cleanup left both
  PostgreSQL and Elasticsearch empty for that temporary ID.
- Credential internal token smoke passed:
  no token returned 401, wrong token returned 401, and the correct token from
  `ircs-dev-secrets` returned the current empty TMDB lease pool `[]`.
- Catalog-service smoke passed:
  temporary standard/raw category, genre, area, language, and data-source rows
  were visible through `/api/v1/catalog/*` and V1 aliases; cleanup left
  `remaining_temp_rows=0`.
- Config-service smoke passed:
  temporary normal and sensitive `system_configs` rows were visible through
  `/api/v1/configs` and `/api/v1/configs/{key}`; the sensitive row returned
  `value=null` and `sensitive=true`, and the fake secret value was not present
  in responses; cleanup left `remaining_temp_configs=0`.
- Task-service smoke passed:
  temporary `data_sources` and `collection_tasks` rows were visible through
  `/api/v1/collection-tasks` and `/api/v1/collection-tasks/{id}`; list filters
  `name`, `status`, `enabled`, and `dataSourceId` returned the temporary task;
  the detail response returned `proxyPassword=null` and did not leak the fake
  proxy password; cleanup left `remaining_temp_tasks=0` and
  `remaining_temp_sources=0`.
- Ingestion-worker smoke passed:
  RabbitMQ management API published `IngestionItem` to `x.ingest/video.upsert`;
  `ircs-ingestion-worker` wrote temporary `raw_videos` with
  `normalization_status=PENDING`, `enrichment_status=PENDING`, and
  `aggregation_status=PENDING`, synchronized 1 playlist and 2 episodes,
  extracted `https://video.example.invalid` into `source_domains`, and
  published 1 message to `q.normalize.video`. Re-publishing the same
  `source_hash/data_hash` with `forceIngest=false` left `q.normalize.video=0`
  and did not duplicate rows. A stale playlist sync message with an old
  `dataHash` was consumed without replacing the current playlist/episodes.
  Cleanup left `remaining_temp_raw_videos=0`,
  `remaining_temp_data_sources=0`, `remaining_temp_source_domains=0`, and
  `q.ingest.video`, `q.process.playlist_sync`, `q.normalize.video`, and their
  DLQs at 0 messages.
- Normalization-worker smoke passed:
  legacy UUID payload to `x.process/video.normalize` updated temporary
  `raw_videos` metadata for `b2d67282-9a0d-4a63-86d8-0cd0f0e25a10` to
  `normalization_status=READY`, preserved locked title
  `Locked Manual Title`, normalized alias/year/base fields, published raw search
  sync, and `ircs-search-service` indexed the document in `ircs_raw_video`;
  structured DELETE removed the ES document and the temporary DB row was cleaned
  up; `q.normalize.video`, `q.search.sync.raw`, and their DLQs were all at 0
  messages after cleanup.
- Aggregation-worker smoke passed:
  scheduler scanned temporary `raw_videos` row
  `c3c7fb4e-9ac1-46fc-9b8f-11e6a8f3f101` with
  `normalization_status=READY`, `enrichment_status=SUCCESS`, and
  `aggregation_status=PENDING`; it created unified video
  `0501b04b-75a6-4d05-ace7-83c82c3b4a66`, wrote
  `raw_video_unified_video`, advanced the raw row to `aggregation_status=BOUND`,
  and published `q.search.sync.raw` plus `q.search.sync.unified`.
  `ircs-search-service` indexed both `ircs_raw_video` and `ircs_unified_video`.
  Structured DELETE and DB cleanup left PostgreSQL temporary rows at 0,
  Elasticsearch temporary documents at `found=false`, and all RabbitMQ queues
  and DLQs at 0 messages.
- Magnet-service smoke passed:
  temporary provider `CODEX_SMOKE`, temporary unified video
  `6f7a5f0b-99dc-4fa5-8d3b-d5d0430f0c01`, one `APPROVED` magnet link, and one
  `REJECTED` magnet link were inserted for smoke. `GET /api/v1/magnet-providers`
  returned the provider, `GET /api/v1/magnet-providers/{id}` returned the
  provider detail, and `GET /api/v1/magnets/unified/{id}` returned only the
  `APPROVED` link. `magnet_search_jobs` and `magnet_provider_runs` had 0 rows
  for the temporary smoke IDs. Cleanup left temporary `magnet_links`,
  `magnet_providers`, and `unified_videos` rows at 0; RabbitMQ queues and DLQs
  remained at 0 messages.
