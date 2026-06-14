# 028-20-03 UUID timeBasedEpoch for new writes

Status: done

## Goal

Switch V3 production new business IDs back to the V1 `timeBasedEpoch` UUID strategy. Existing data is not migrated or rewritten.

## Scope

- Make `IrcsUuidGenerators.defaultGenerator()` return `TIME_BASED_EPOCH`.
- Add `IrcsUuidGenerators.nextId()` as the default new business ID entrypoint.
- Replace production `UUID.randomUUID()` business primary-key writes with `IrcsUuidGenerators.nextId()`.
- Keep true random UUID use for security tokens, multipart boundaries, temp file names, and generated secrets.

## Non-goals

- No historical data migration.
- No SQL backfill rewrite for old rows.
- No replacement of test fixture UUIDs.

## Acceptance

- Production source `UUID.randomUUID()` remains only for non-business-random use.
- Affected services compile and focused tests pass.
- Task index records exact deferred cases and verification commands.

## Implementation

- `IrcsUuidGenerators.defaultGenerator()` now resolves to `TIME_BASED_EPOCH`.
- `IrcsUuidGenerators.nextId()` is the default entrypoint for new business IDs.
- Production business primary-key writes in credential, scraper, task, content, aggregation, catalog, notification, ingestion, magnet, identity, normalization, interaction, storage, and ops services now call `IrcsUuidGenerators.nextId()`.
- Existing rows and migration/backfill SQL remain untouched, per decision.

## Production Exceptions

The remaining production `UUID.randomUUID()` calls are intentionally random/non-business-ID use:

- `SystemConfigDefaults`: generated fallback secret seed.
- `RestClientAvatarStorageClient`: multipart boundary.
- `PoWService`: proof-of-work challenge ID.
- `MemberAuthService`: activation/reset token.
- `LocalObjectStorage`: temporary file suffix.

## Verification

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-credential-service:test :services:ircs-scraper-service:test :services:ircs-task-service:test :services:ircs-content-service:test :services:ircs-aggregation-worker:test :services:ircs-catalog-service:test :services:ircs-notification-worker:test :services:ircs-ingestion-worker:test :services:ircs-magnet-service:test :services:ircs-identity-service:test :services:ircs-normalization-worker:test :services:ircs-interaction-service:test :services:ircs-storage-service:test :services:ircs-ops-service:test
```

Result: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat --no-daemon :services:ircs-credential-service:bootJar :services:ircs-scraper-service:bootJar :services:ircs-task-service:bootJar :services:ircs-content-service:bootJar :services:ircs-aggregation-worker:bootJar :services:ircs-catalog-service:bootJar :services:ircs-notification-worker:bootJar :services:ircs-ingestion-worker:bootJar :services:ircs-magnet-service:bootJar :services:ircs-identity-service:bootJar :services:ircs-normalization-worker:bootJar :services:ircs-interaction-service:bootJar :services:ircs-storage-service:bootJar :services:ircs-ops-service:bootJar
```

Result: `BUILD SUCCESSFUL`.

```powershell
rg -n "UUID\.randomUUID\(\)" .\services .\shared -g "*.java" -g "!**/src/test/**"
```

Result: only the five non-business exceptions listed above remain.
