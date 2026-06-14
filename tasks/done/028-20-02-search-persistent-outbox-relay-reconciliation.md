# 028-20-02 Search persistent outbox relay/reconciliation

Status: done - implementation closure, live K8S/Rabbit/ES smoke deferred to unified test window.

## Goal

Restore V1 `search_sync_tasks` behavior in V3 as a production-oriented persistent outbox:

- business services enqueue search sync tasks transactionally
- search-service owns claim, relay, retry, dead-letter, and reconciliation execution
- Rabbit search queues remain the execution transport consumed by search-service
- ops endpoints expose current outbox health without cache

## Scope

- Add shared transactional outbox writer for producers.
- Add search-service internal enqueue API for manual/external reconciliation.
- Add production relay with lease locking, retry backoff, dead-letter, audit.
- Add DB reconciliation runner that discovers DB records missing from ES and enqueues them.
- Move current direct search Rabbit publishers to persistent outbox.
- Add Liquibase migration and focused tests.

## Non-goals

- Strict physical database split.
- mTLS/service identity scopes beyond current internal token placeholder.
- Full ES stale-document purge; V1 reconciliation covers DB-to-ES missing index repair first.

## Acceptance

- Search sync producers no longer publish directly to `q.search.sync.raw`/`q.search.sync.unified`.
- Relay publishes only from `search_sync_tasks`.
- Success deletes grouped tasks as V1 did.
- Failure retries with exponential backoff and moves to `DEAD_LETTER` after max retries.
- Processing lease prevents duplicate claims across search-service replicas.
- Tests cover enqueue, claim, relay success/failure, ops stats, internal enqueue, reconciliation, and migrated producers.

## Implementation

- Added shared `SearchSyncOutboxWriter` in `shared:ircs-common` for transactional outbox writes.
- Added shared internal request/response DTOs in `shared:ircs-contracts`.
- Migrated search sync producers in:
  - `ircs-content-service`
  - `ircs-aggregation-worker`
  - `ircs-normalization-worker`
  - `ircs-metadata-worker`
  - `ircs-ops-service`
- Added search-service:
  - internal enqueue API `/internal/v1/search/sync-tasks`
  - Postgres lease-based claim with H2 fallback for tests
  - relay scheduler with persistent Rabbit messages, audit, retry, dead-letter
  - reconciliation runner with dry-run default and execute gate
- Added Liquibase migration `2026/07/extend-search-outbox-runtime.sql`.

## Evidence

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-search-service:test :services:ircs-content-service:test :services:ircs-aggregation-worker:test :services:ircs-normalization-worker:test :services:ircs-metadata-worker:test :services:ircs-ops-service:test
```

Result: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat --no-daemon :platform:ircs-migrator:bootJar :services:ircs-search-service:bootJar :services:ircs-content-service:bootJar :services:ircs-aggregation-worker:bootJar :services:ircs-normalization-worker:bootJar :services:ircs-metadata-worker:bootJar :services:ircs-ops-service:bootJar
```

Result: `BUILD SUCCESSFUL`.

Production-code scan:

```powershell
rg -n "SEARCH_SYNC_RAW|SEARCH_SYNC_UNIFIED|SEARCH_Q_RAW|SEARCH_Q_UNIFIED|search.sync.raw|search.sync.unified|new SearchSyncMessage|SearchSyncMessage" services shared -g "*.java"
```

Result: direct search Rabbit publishing remains only in search-service relay/consumer and queue metadata/descriptors; migrated business producers use `SearchSyncOutboxWriter`.

## Runtime Gates

- Relay: `APP_SEARCH_OUTBOX_RELAY_ENABLED=false` by default.
- Queue listener: `APP_SEARCH_LISTENER_ENABLED=false` by default.
- Reconciliation: `APP_SEARCH_RECONCILIATION_ENABLED=false` and `APP_SEARCH_RECONCILIATION_DRY_RUN=true` by default.
- Internal token: `APP_SEARCH_INTERNAL_REQUIRE_TOKEN=false` by default; production should set `APP_SEARCH_INTERNAL_TOKEN` or `APP_INTERNAL_SERVICE_TOKEN` and require token.

## Deferred Unified Test

- Apply migrator in `ircs-dev`.
- Enable relay/listener in search-service.
- Produce one raw/unified change through content/aggregation path and verify `search_sync_tasks` drains.
- Run reconciliation with dry-run first, then execute-gated single bounded pass.
- Verify ops stats endpoint and worker audit rows.
