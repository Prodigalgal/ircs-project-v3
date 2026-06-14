# 028-16-F6 Source Field Auto Mapping Hardening

## Goal

Harden admin data-source auto mapping so common VOD/CMS source-site payloads map to the fields consumed by V3 scraper, normalization, and aggregation without silently losing source category/type metadata.

## Scope

- Improve admin data-source wizard heuristic mapping for list/detail payloads.
- Keep existing explicit manual mapping behavior; auto mapping only fills blank fields.
- Keep admin manual mapping edits select-only from parsed JSONPath options, not free-text input.
- Ensure frontend `categoryName` mapping is consumed by `ScraperMappingService` as canonical `rawTypeName`.
- Expand preview output so admins can inspect key mapped fields before saving.

## Non-goals

- No live source-site request matrix in this slice.
- No LLM-based mapping inference.
- No change to persisted `field_mapping` schema shape.

## Implemented

- Replaced the single-pass keyword lookup with scored field rules in admin frontend `autoMapping.js`.
- Added CMS/VOD-aware aliases for `vod_*`, snake/camel keys, pagination, playlist, external IDs, category/type and raw metadata fields.
- Added anti-match penalties so `type_name` is no longer selected as video `title`, and `type_id` is no longer selected as source video id.
- Updated preview generation to show `source_vid`, language, remarks, score, publishedAt, category id/name and existing relationship fields.
- Updated scraper mapping to accept `categoryName/category_name` as aliases for canonical `rawTypeName`.
- Updated direct raw metadata import to accept `typeId/categoryId/typeName/categoryName` aliases.
- Confirmed and hardened `JsonPathSelector` as a select-only dropdown:
  - no free-text JSONPath entry
  - parsed JSONPath options are de-duplicated
  - existing saved/manual value remains visible as `(当前配置)` in edit mode when sample paths have not been loaded or no longer contain it

## Validation

- `node --input-type=module` auto-mapping probe in `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-frontend`
  - Result: `listTitle=$.list[0].vod_name`, `detailTitle=$.list[0].vod_name`, `categoryName=$.list[0].type_name`, `typeId=$.list[0].type_id`
- `node --input-type=module` manual-preservation probe in `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-frontend`
  - Result: `manualListTitle=$.custom.title`, `manualDetailTitle=$.custom.detailTitle`, `autoCategoryName=$.list[0].type_name`
- `npm run build` in `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-frontend`
  - Result: `vite build` successful
- `.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --tests com.prodigalgal.ircs.scraper.ScraperMappingServiceTest --tests com.prodigalgal.ircs.scraper.ManualScraperServiceTaskExecutionTest`
  - Result: passed

## Deferred

- Real V1 source-site sample matrix across all seeded sources.
- Browser smoke of the full admin wizard against deployed V3 catalog-service.
- Live scrape smoke proving category/type metadata reaches DB, normalization and aggregation in K8S.
