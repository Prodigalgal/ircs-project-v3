# 028-16-B5 raw-source pipeline category relation task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`

上游来源：

- `tasks/in-progress/028-16-b3-unified-relation-union-victim-merge.md`
- `tasks/in-progress/028-16-b4-victim-scalar-merge.md`
- V1 `AggregationPipeline`
- V1 `MetadataHandler`
- V1 `CategoryHandler`
- V3 `RawVideoNormalizationRepository`
- V3 portal/search unified relation readers

## 背景

B2-B4 已把 duplicate unified merge 的 root/victim 收敛、victim 删除、search DELETE、victim
relation union 和 victim scalar merge 补齐。但 V1 `AggregationPipeline` 在每次聚合时会基于
同一 unified 下的 raw sources 重建 category、genre、area、language、actor、director 等字段。
V3 当前 `upsertUnifiedFields()` 只补标量字段，不会把 raw relation 聚合到 unified relation，
导致 portal/admin/search 的分类、地区、语言、类型和人员筛选仍可能缺数据。

## 目标

- 在 raw 绑定到 unified 后，基于该 unified 的全部 raw sources 重建第一批 aggregation
  pipeline 关系。
- category：按 `raw_videos.data_source_category_id -> raw_category.category_id` 投票，选择票数最高
  的 standard category 写入 `unified_videos.category_id`。
- relations：从 raw sources 的 `video_actors`、`video_directors`、`video_raw_genres`、
  `video_raw_languages`、`video_raw_areas` 重建 unified 的 `actors/directors/genres/languages/areas`。
- 尊重 unified `locked_fields`：`category`、`actors`、`directors`、`genres`、`area`、`language`
  被锁时不自动覆盖对应字段或关系。
- 保持 B2-B4 duplicate merge 行为不回退。

## 非目标

- 不在本切片实现 V1 dynamic fields：alias/description/subtitle 最长、remarks 最新、episodes/duration/season 首个。
- 不实现 cover image managed/file-size 优选和 R2 promote。
- 不接完整 JGraphT raw/raw cluster。
- 不执行 K8S live smoke。

## 输入

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationService.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/**
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationRepository.java
platform/ircs-migrator/src/main/resources/db/changelog/2026/01/06-01-changelog.sql
platform/ircs-migrator/src/main/resources/db/changelog/2026/01/24-01-refactor-category.sql
platform/ircs-migrator/src/main/resources/db/changelog/2026/01/24-02-rename-genres-to-standard-genre.sql
platform/ircs-migrator/src/main/resources/db/changelog/2026/01/27-01-add-area-tables.sql
```

## 输出

- repository raw-source category vote + relation rebuild primitive。
- service 调用点：raw bind 完成后重建 unified pipeline 第一批关系。
- H2-compatible repository fixture tests。
- service order test，保证 bind -> rebuild -> markBound/search publish。
- docs/tasks/migration 回写。

## 影响范围

```text
services/ircs-aggregation-worker/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md
tasks/index.md
docs/index.md
```

## 风险

- `locked_fields` 字段名必须与 V1/V3 admin 使用一致，否则会错误覆盖人工锁定字段。
- relation rebuild 是清空再写，必须只在未锁字段上执行。
- category 投票需要稳定 tie-breaker，避免重复运行结果不一致。
- 本切片不能误标 dynamic fields、cover 和完整 graph cluster 完成。

## 验收标准

```text
B5 MARK 1/6 category vote writes unified_videos.category_id when category is unlocked
B5 MARK 2/6 locked category preserves existing unified_videos.category_id
B5 MARK 3/6 raw actors/directors rebuild unified relation when unlocked
B5 MARK 4/6 raw genres/languages/areas rebuild unified standard relation when unlocked and ignore unmapped raw values
B5 MARK 5/6 locked relation fields preserve existing unified relations
B5 MARK 6/6 aggregation-worker/search-service tests pass and docs record remaining dynamic/cover/JGraphT gaps
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀。`AggregationService.bindRawVideo()` 现在在
`bindRawToUnified()` 之后、`markBound()` 之前调用
`JdbcAggregationRepository.rebuildUnifiedPipelineRelations(unifiedVideoId)`，确保本次新绑定的
raw source 立即参与 category 投票和 relation rebuild。

实现结果：

- category：未锁定 `category` 时，按同一 unified 下全部 raw source 的
  `raw_videos.data_source_category_id -> raw_category.category_id` 计票，票数最高写入
  `unified_videos.category_id`；平票使用 UUID 字符串排序稳定化，避免重复运行结果漂移。
- actors/directors：未锁定 `actors`、`directors` 时，先清空对应 unified relation，再从
  `video_actors`、`video_directors` 按 raw bindings 去重重建。
- genres/languages/areas：未锁定 `genres`、`language`、`area` 时，先清空对应 unified
  relation，再从 `video_raw_genres -> raw_genres.standard_genre_id`、
  `video_raw_languages -> raw_languages.standard_language_id`、
  `video_raw_areas -> raw_areas.standard_area_id` 去重重建；未映射到 standard 的 raw value
  不写入 unified relation。
- `locked_fields` 同时兼容 V1 单数字段 `area/language` 和 V3 可能出现的复数字段
  `areas/languages`；锁定字段不清空、不覆盖。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

MARK：

```text
B5 MARK 1/6 category vote writes unified_videos.category_id when category is unlocked: done 2026-06-09
B5 MARK 2/6 locked category preserves existing unified_videos.category_id: done 2026-06-09
B5 MARK 3/6 raw actors/directors rebuild unified relation when unlocked: done 2026-06-09
B5 MARK 4/6 raw genres/languages/areas rebuild unified standard relation when unlocked and ignore unmapped raw values: done 2026-06-09
B5 MARK 5/6 locked relation fields preserve existing unified relations: done 2026-06-09
B5 MARK 6/6 aggregation-worker/search-service tests pass and docs record remaining dynamic/cover/JGraphT gaps: done 2026-06-09
```

仍未完成：

- V1 `BasicAttributeHandler` 的 title/year 投票、score 最大值、publishedAt 首个非空。
- V1 `ExternalIdHandler` 的 raw-source external ID merge 细节。
- V1 `DynamicFieldHandler` 的 alias/description/subtitle 最长、remarks 最新、episodes/duration/season 首个非空。
- V1 `CoverImagePipelineHandler` 的托管优先、fileSize 10% 阈值、R2 promote。
- `AggregationPipeline` handler order 和单 handler 异常继续执行语义。
- 完整 JGraphT raw/raw cluster、graph scoring 细节和 K8S live smoke。
