# 028-20-04 V1 mainTitle title semantics and display/search compatibility

Status: done

## Goal

Restore V1 title semantics in V3: persisted/API `title` must mean `mainTitle`, while subtitle, alias title, and display composition remain available for portal/admin/search compatibility.

## Scope

- Compare V1/V3 title semantics for raw video normalization, aggregation, search indexing, and portal/admin payloads.
- Switch normalization write-back so `title` stores the extracted main title, not the combined display title.
- Preserve atomic API payload fields: `title`, `aliasTitle`, `subtitle`, `season`.
- Expand search variants so users can still find combined title/subtitle/season forms.
- Keep portal/admin capable of combined display without changing backend atomic payload semantics.
- Add focused tests and production package build for affected services.

## Non-goals

- No historical data rewrite.
- No frontend visual redesign.
- No physical schema split for display title.

## Acceptance

- V1-style examples such as `The Last Kingdom: Seven Kings Must Die Season 2` store `title=The Last Kingdom`, `season=2`, and `subtitle=Seven Kings Must Die`.
- Chinese title/subtitle examples keep `title` as main title and move explicit season/subtitle into `season` / `subtitle`.
- Search variants include main title, alias, subtitle, main+subtitle, main+season, and V1 punctuation-compatible forms.
- Focused tests pass for normalization/search/aggregation/content/BFF if touched.
- Task index records verification, exceptions, and remaining live-smoke boundary.

## Implementation

- `RawVideoTextNormalizer` now writes `title` from extracted V1-style `mainTitle` instead of the full cleaned display title.
- `season` and `subtitle` remain atomic fields; subtitle no longer repeats the explicit season token.
- Alias cleanup now removes redundant `title + season` and `title + subtitle` aliases so `aliasTitle` remains a true alias field.
- `Part II` / `Vol.2` are protected from the trailing-number season rule to avoid misclassifying movie sequel or volume names.
- `SearchTextHelper` now keeps up to 64 title variants and includes punctuation/season/subtitle combinations for search/display compatibility.
- API payloads remain atomic: no `displayTitle` field was added, and portal/admin can compose display text from `title`, `season`, `subtitle`, and `aliasTitle`.

## Verification

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests com.prodigalgal.ircs.normalization.RawVideoTextNormalizerTest :services:ircs-search-service:test --tests com.prodigalgal.ircs.search.support.SearchTextHelperTest
```

Result: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test :services:ircs-search-service:test :services:ircs-aggregation-worker:test :services:ircs-content-service:test :services:ircs-portal-bff:test :services:ircs-admin-bff:test
```

Result: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:bootJar :services:ircs-search-service:bootJar :services:ircs-aggregation-worker:bootJar :services:ircs-content-service:bootJar :services:ircs-portal-bff:bootJar :services:ircs-admin-bff:bootJar
```

Result: `BUILD SUCCESSFUL`.

## Remaining Boundary

- Existing DB rows are not rewritten in this slice.
- Dev K8S live reindex/search smoke is deferred to the unified production-like smoke batch.
