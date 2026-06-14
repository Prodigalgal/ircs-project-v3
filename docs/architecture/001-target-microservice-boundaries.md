# IRCS V3 target microservice boundaries

This is the one-step target topology. The migration can still be executed in
batches, but the boundary map below is the final ownership model.

## Runtime architecture

```text
                             +-----------------------------+
                             | Browser / Admin UI / Portal |
                             +--------------+--------------+
                                            |
                                            v
                             +-----------------------------+
                             | K8S Ingress / Edge Nginx    |
                             | keeps /api/v1, /api/portal, |
                             | and /media stable           |
                             +--+-----+-----+-----+-----+--+
                                |     |     |     |     |
              +-----------------+     |     |     |     +-----------------+
              |                       |     |     |                       |
              v                       v     v     v                       v
  +-----------------------+ +----------------+ +----------------+ +----------------+
  | identity-service      | | portal-service | | content-service| | storage-service|
  | auth, JWT, members    | | public read API| | videos, media  | | cover, media   |
  +-----------+-----------+ +-------+--------+ +-------+--------+ +-------+--------+
              |                     |                  |                  |
              |                     v                  |                  |
              |           +----------------+            |                  |
              |           | search-service |<-----------+                  |
              |           | ES query/sync   |                               |
              |           +-------+--------+                               |
              |                   |                                        |
              v                   v                                        v
  +-----------------------+ +----------------+ +----------------+ +----------------+
  | interaction-service   | | catalog-service| | magnet-service | | scraper-service|
  | history, feedback     | | genre/lang/etc | | magnet lookup  | | manual scrape  |
  +-----------+-----------+ +-------+--------+ +-------+--------+ +-------+--------+
              |                     |                  |                  |
              +---------------------+------------------+------------------+
                                    |
                                    v
                           +-------------------+
                           | RabbitMQ          |
                           | queue ownership   |
                           +--+--+--+--+--+----+
                              |  |  |  |  |
          +-------------------+  |  |  |  +--------------------+
          |                      |  |  |                       |
          v                      v  v  v                       v
 +------------------+ +----------------+ +----------------+ +-------------------+
 | ingestion-worker | | norm-worker    | | metadata-worker| | notification-work |
 | q.ingest.video   | | q.normalize    | | enrich/fetch   | | q.notification    |
 +--------+---------+ +-------+--------+ +-------+--------+ +---------+---------+
          |                   |                  |                    |
          v                   v                  v                    v
 +------------------+ +----------------+ +----------------+ +-------------------+
 | aggregation-work | | task-service   | | credential-svc | | ops-service       |
 | cluster + bind   | | collection job | | API keys/pools | | DLQ, audit, maint |
 +------------------+ +----------------+ +----------------+ +-------------------+

 Shared infrastructure:
 +------------+  +---------+  +----------+  +----------------+  +-----------+
 | PostgreSQL |  | Valkey  |  | RabbitMQ |  | Elasticsearch  |  | R2/PVC    |
 +------------+  +---------+  +----------+  +----------------+  +-----------+
```

## Service ownership matrix

| Service | Primary V1 source | Owns HTTP routes | Owns queues / jobs | Owns data first |
| --- | --- | --- | --- | --- |
| `ircs-identity-service` | `modules/system` auth + `modules/portal` member auth/profile | `/api/v1/auth/**`, `/api/v1/members/**`, `/api/portal/auth/**`, `/api/portal/profile/**` | avatar sync publish only | `member`, admin auth state, token/session state |
| `ircs-content-service` | `modules/content` | `/api/v1/raw-videos/**`, `/api/v1/unified-videos/**`, `/api/v1/playlists/**`, actor/director/source-domain/resolver routes | publishes normalize/enrich/search/storage messages | raw/unified video, playlist, episode, actor, director, source-domain |
| `ircs-catalog-service` | `modules/metadata` + `modules/datasource` | standard/raw area/category/genre/language, data-source routes; read-only MVP deployed with `/api/v1/catalog/*` and V1 aliases | none initially | catalog, datasource, normalization dictionaries |
| `ircs-task-service` | `modules/task` | `/api/v1/collection-tasks/**`; read-only list/detail MVP deployed | collection watchdog/scheduler deferred | collection task, task logs |
| `ircs-portal-service` | `modules/portal` content read composition | `/api/portal/**` except auth/profile/search/interaction; read composition MVP deployed as ClusterIP only | cache warmers deferred | portal read model cache |
| `ircs-scraper-service` | `modules/scraper` manual/trend/list scraping | `/api/v1/scraper/manual/**`; manual scrape MVP deployed as ClusterIP only | publishes `q.ingest.video`; metadata/trend fan-out deferred | scrape session state |
| `ircs-magnet-service` | `modules/magnet` | `/api/v1/magnets/**`, `/api/v1/magnet-providers/**`; provider/cache read-only MVP deployed as ClusterIP only | none initially; external search deferred | magnet providers/cache |
| `ircs-search-service` | `modules/search` | `/api/portal/search/**` | `q.search.sync.raw`, `q.search.sync.unified`, search relay/reconciliation | search sync task, ES indices |
| `ircs-storage-service` | `modules/storage` + content image unlink physical side | `/api/v1/cover-images/**`, `/media/**` | image download, avatar sync, image delete | cover image, object metadata |
| `ircs-interaction-service` | `modules/interaction` | `/api/v1/messages/**`, `/api/portal/interaction/**`, `/api/portal/feedback/**`; feedback/history read MVP deployed as ClusterIP only | `q.interaction.watch_progress` deferred | favorites, watch history, messages |
| `ircs-config-service` | `modules/system` config | `/api/v1/configs/**`, `/api/v1/common/**`; read-only config MVP deployed with sensitive value redaction | config change events | system config |
| `ircs-credential-service` | `modules/system` credentials/key pool | `/api/v1/credentials/**` | credential refresh jobs | credential, key pool, provider limits |
| `ircs-ops-service` | `modules/system` ops/maintenance/dashboard | `/api/v1/ops/**`, `/api/v1/dashboard/**`, debug metadata; maintenance/dashboard MVP deployed as ClusterIP only | DLQ maintenance deferred; metrics snapshots read-only | audit, failed message, maintenance session |
| `ircs-ingestion-worker` | `modules/ingestion` ingest | none; MVP deployed, worker Service pruned in dev | `q.ingest.video`, `q.process.playlist_sync`; publishes `q.normalize.video` | raw ingestion state, playlist sync migration bridge |
| `ircs-normalization-worker` | `modules/ingestion` normalization | none; MVP deployed, worker Service pruned in dev | `q.normalize.video`; publishes `q.search.sync.raw`; scheduler deferred | normalization status and lightweight raw field normalization |
| `ircs-aggregation-worker` | `modules/ingestion` aggregation | none; scheduler MVP deployed, worker Service pruned in dev | scans `raw_videos`; publishes `q.search.sync.raw` and `q.search.sync.unified` | aggregation status, `raw_video_unified_video`, basic unified fields |
| `ircs-metadata-worker` | `modules/scraper/metadata` | none | `q.enrich.metadata`, `q.fetch.metadata.*`, result gather | enrichment result state |
| `ircs-notification-worker` | `modules/system` mail | none | `q.notification.mail` | notification audit |

## Queue ownership

```text
Exchange x.ingest
  q.ingest.video                 -> ingestion-worker

Exchange x.process
  q.normalize.video              -> normalization-worker
  q.enrich.metadata              -> metadata-worker
  q.enrich.metadata.result       -> metadata-worker
  q.process.playlist_sync        -> ingestion-worker
  q.fetch.metadata.douban        -> metadata-worker
  q.fetch.metadata.tmdb          -> metadata-worker
  q.fetch.metadata.rt            -> metadata-worker

Exchange x.storage
  q.storage.image                -> storage-service
  q.storage.avatar_sync          -> storage-service
  q.storage.image_unlink         -> content-service
  q.storage.image_delete         -> storage-service

Exchange x.notification
  q.notification.mail            -> notification-worker

Exchange x.interaction
  q.interaction.watch_progress   -> interaction-service

Exchange x.search
  q.search.sync.raw              -> search-service
  q.search.sync.unified          -> search-service
```

## Dependency direction

```text
Controllers -> application services -> repositories
Workers     -> application services -> repositories

Allowed sync calls:
  portal-service      -> content-service, search-service, magnet-service
  ops-service         -> domain services only through internal APIs/events
  scraper-service     -> ingestion-worker only through ingestion messages
  content-service     -> catalog-service for dictionary lookups

Forbidden:
  worker -> frontend-facing controller
  worker -> worker sync calls
  content-service -> portal-service
  direct service-to-service table mutation
```

## Database policy

Phase 0 uses the same PostgreSQL instance with explicit table ownership. Each
service gets an application role and only writes its owned tables. Cross-domain
reads move toward internal APIs or read-model tables before physical DB splits.

Physical DB split is postponed until:

- HTTP and queue ownership are stable.
- Each service has isolated migrations.
- Cross-service writes have been replaced by events or internal APIs.
