# 094 Source Field Auto Mapping Hardening

## Summary

The admin data-source wizard previously used a fragile keyword lookup for JSONPath auto mapping. It could confuse generic `name/id` fields, especially `type_name` versus `vod_name`, and the backend did not consume frontend/default-seed `categoryName` as canonical source type metadata.

This slice upgrades source-field inference and closes the frontend/backend mapping gap.

## Changes

- Admin frontend now uses scored auto-mapping rules for common VOD/CMS payloads.
- Exact source-site aliases such as `vod_name`, `vod_id`, `type_name`, `type_id`, `vod_class`, `vod_play_from`, `vod_play_url` are preferred over generic `name/id/url`.
- Negative scoring prevents common wrong matches:
  - `type_name` as title
  - `type_id` as source video id
  - pagination totals as episode totals
  - cover URLs as playlist URLs
- Detail preview now displays more mapped fields before save, including source id, language, remarks, score, published date and category id/name.
- `ScraperMappingService` now reads `categoryName/category_name` as aliases for canonical `rawTypeName`.
- Direct scrape raw metadata now also accepts `typeId/categoryId/typeName/categoryName` aliases.
- Manual mapping remains available after heuristic initialization, but admin edits are constrained to select-only JSONPath dropdowns rather than free-text input.
- Existing saved/manual JSONPath values are preserved as current options in edit mode, so a data source is not accidentally cleared before a fresh sample response is fetched.

## Verification

`node --input-type=module` auto-mapping probe in `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-frontend`

Result:

```json
{"listTitle":"$.list[0].vod_name","detailTitle":"$.list[0].vod_name","categoryName":"$.list[0].type_name","typeId":"$.list[0].type_id"}
```

Manual mapping preservation probe:

```json
{"manualListTitle":"$.custom.title","manualDetailTitle":"$.custom.detailTitle","autoCategoryName":"$.list[0].type_name"}
```

`npm run build` in `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-frontend`

Result: `vite build` successful

`.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --tests com.prodigalgal.ircs.scraper.ScraperMappingServiceTest --tests com.prodigalgal.ircs.scraper.ManualScraperServiceTaskExecutionTest`

Result: passed

## Remaining Boundary

This is stronger than the previous keyword-only heuristic, but it is not yet proven against the full live source-site matrix. The unified source-site test pass still needs real sample responses from seeded V1 sources and a K8S scrape smoke that follows mapped category/type metadata into DB, normalization and aggregation.
