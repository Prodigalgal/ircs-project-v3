# 051 normalization raw relation parity 分析

状态：已完成

更新时间：2026-06-07

## 背景

`018-01` 已完成 `ircs-normalization-worker` MVP：消费 `q.normalize.video`，清洗基础字段，
将 `raw_videos.normalization_status` 推进到 `READY`，并发布 raw search sync。但当时明确
未迁移 V1 的深层关系副作用：

- `video_raw_genres`
- `video_raw_languages`
- `video_raw_areas`
- `video_actors`
- `video_directors`
- `raw_category` / `data_source_category_id`

这会导致接口虽然可用，但 admin/portal 的 raw video 查询、筛选、聚合候选和后续 aggregation
缺少 V1 已验证的关系数据。

## V1 行为摘要

V1 normalization handler：

- `AreaLanguageHandler`
  - 从 `raw_metadata.area` 解析地区。
  - 通过 `AreaNormalizationService.resolveRawAreas` 写 `raw_areas` 并替换
    `video_raw_areas`。
  - 从 `raw_metadata.language` 解析语言；语言为空时可按地区推断。
  - 通过 `RawLanguageService.resolveRawLanguages` 写 `raw_languages` 并替换
    `video_raw_languages`。
- `CategoryGenreHandler`
  - 从 `raw_metadata.rawTypeId/rawTypeName/dataSourceId/dataSourceName` 解析来源分类。
  - 从 `raw_metadata.genreNames` 解析题材集合。
  - 通过 `RawGenreService.resolveRawGenres` 写 `raw_genres` 并替换 `video_raw_genres`。
- `PersonHandler`
  - 从 `raw_metadata.actorNames/directorNames` 解析演员、导演，必要时从简介做 NER fallback。
  - 写 `actors/directors` 与 `video_actors/video_directors`。

## V3 当前状态

已完成：

- 基础字段：title、alias、description、year、area、raw_language_str、remarks、score、
  total episodes、duration、外部 ID。
- `locked_fields` 保护。
- `normalization_status=READY` 和 raw search sync。

本轮前缺口：

- 未写 `raw_genres` / `video_raw_genres`。
- 未写 `raw_languages` / `video_raw_languages`。
- 未写 `raw_areas` / `video_raw_areas`。
- 未写 `actors/directors` 关系。
- 未写 `raw_category` 或 `data_source_category_id`。
- 未做 startup recovery、watchdog 和 FAILED backoff scheduler。

## 本轮方案

最小 dev-safe raw relation 切片：

```text
RawVideoTextNormalizer
  -> 从 raw_metadata 提取 genreNames、language/lang、area/region
  -> 归一化为去重、去空白的 relation value 集合

RawVideoNormalizationRepository.markReady
  -> 更新 raw_videos 基础字段
  -> 对非 locked 且非空集合：
       upsert raw_genres / raw_languages / raw_areas
       replace video_raw_genres / video_raw_languages / video_raw_areas
  -> normalization_status = READY
  -> publish SEARCH_SYNC_RAW INDEX
```

本轮不实现标准映射：

- `standard_genre_id`
- `standard_language_id`
- `standard_area_id`

原因是 V1 的标准映射依赖缓存和知识库服务；本轮先恢复 raw relation 可见性和筛选副作用，
标准映射后续进入独立 task。

## 非目标

- 不实现 actor/director NER 和 person relation。
- 不实现 raw category / data source category 解析。
- 不实现 standard genre/language/area 映射。
- 不实现 scheduler/watchdog/backoff runner。
- 不做 K8S rollout。
- 不新增 Pod、Service、HTTPRoute 或中间件。

## 风险

- raw relation upsert 会写共享 PostgreSQL 桥接表；后续物理拆库时需再拆 ownership。
- 如果 raw metadata 字段格式与预期不一致，本轮只应跳过未知字段，不应让 normalization 失败。
- 标准映射为空时，aggregation 中依赖 standard relation 的逻辑仍不完全等价，需要后续切片。

## 验收标准

```text
MARK 1/4 normalization extracts genre/language/area relation values from raw_metadata
MARK 2/4 normalization upserts raw_genres/raw_languages/raw_areas
MARK 3/4 normalization replaces video_raw_genres/video_raw_languages/video_raw_areas
MARK 4/4 normalization preserves READY status and raw search sync after relation writes
```

## 测试边界

本地测试：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
```

本轮不做 K8S rollout。后续 live smoke 可单独验证：

```text
publish NORMALIZE_VIDEO fixture with genreNames/language/area
verify raw relation tables and join tables
verify q.normalize.video / q.search.sync.raw / DLQ cleanup
verify ES raw document refresh
```

## 完成结果

2026-06-07 已完成本轮最小 raw relation parity：

```text
MARK 1/4 normalization extracts genre/language/area relation values from raw_metadata
MARK 2/4 normalization upserts raw_genres/raw_languages/raw_areas
MARK 3/4 normalization replaces video_raw_genres/video_raw_languages/video_raw_areas
MARK 4/4 normalization preserves READY status and raw search sync after relation writes
```

实现摘要：

- `RawVideoPatch` 新增 `rawGenreValues`、`rawLanguageValues`、`rawAreaValues`。
- `RawVideoTextNormalizer` 从 `genreNames/genres/genre`、`language/lang`、
  `area/region` 提取字符串或数组 relation 值，并按逗号、顿号、斜杠等分隔符去重。
- `RawVideoNormalizationRepository.markReady` 在基础字段更新后 upsert raw relation 表，
  并替换当前 raw video 的三类 join relations。
- locked 或空 relation 输入不触发 join 表删除，避免覆盖人工修正。

验证：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

后续仍需独立切片：

- actor/director relation 和 NER fallback。
- raw category / data source category 映射。
- standard genre/language/area 映射。
- normalization live queue smoke、watchdog 和 FAILED backoff scheduler。
