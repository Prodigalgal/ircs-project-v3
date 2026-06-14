# 028-16-B6 raw-source pipeline scalar fields task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`

上游来源：

- `tasks/in-progress/028-16-b5-raw-source-pipeline-category-relation.md`
- V1 `BasicAttributeHandler`
- V1 `ExternalIdHandler`
- V1 `DynamicFieldHandler`
- V1 `VideoFieldConstants`
- V3 `JdbcAggregationRepository`

## 背景

B5 已补 raw 绑定 unified 后的 category 投票与 actors/directors/genres/languages/areas
关系重建。但 V1 `AggregationPipeline` 在 `MetadataHandler` 之外还会按 handler 顺序聚合
一批标量字段：

```text
order 10 BasicAttributeHandler
order 20 ExternalIdHandler
order 40 DynamicFieldHandler
```

V3 目前 `upsertUnifiedFields()` 只基于当前 raw 做 coalesce，无法复现 V1 “同一 unified
全部 raw sources 共同投票/择优”的字段规则，portal/admin/search 看到的 unified 标量可能
仍与 V1 偏离。

## 目标

- 在 raw 绑定 unified 后，基于该 unified 的全部 raw sources 重建第一批 scalar pipeline 字段。
- 复刻 V1 `BasicAttributeHandler`：
  - `title`：未锁时按 raw title 投票，票数最高写入 unified。
  - `year`：未锁时按 raw year 投票，票数最高写入 unified。
  - `score`：未锁时取 raw score 最大值。
  - `publishedAt`：未锁时取第一个非空 raw published_at；V3 采用稳定 raw ordering 作为 first。
- 复刻 V1 `ExternalIdHandler`：
  - `doubanId`、`tmdbId`、`imdbId`、`rottenTomatoesId` 不受 locked_fields 保护。
  - 仅当 unified 当前值无效时，从 raw sources 依次补第一个有效值。
- 复刻 V1 `DynamicFieldHandler`：
  - `aliasTitle`、`description`、`subtitle`：未锁时取最长。
  - `remarks`：未锁时取 `updated_at` 最新的非空 raw remarks。
  - `totalEpisodes`、`duration`：未锁时取第一个非空。
  - `season`：未锁时取第一个非空。
- 保持 B2-B5 duplicate merge、relation union/rebuild 和 search side effect 不回退。

## 非目标

- 不在本切片实现 `CoverImagePipelineHandler` 的托管优先、fileSize 10% 阈值和 R2 promote。
- 不实现完整 JGraphT raw/raw cluster。
- 不实现 pipeline handler order/failure isolation 的框架化 runtime；本轮只落地等价 SQL primitive。
- 不执行 K8S live smoke。

## 输入

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationService.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
```

## 输出

- repository raw-source scalar pipeline primitive。
- service 调用点：raw bind 后执行 scalar rebuild，再执行 relation rebuild。
- H2-compatible repository fixture tests。
- service order test，保证 bind -> scalar rebuild -> relation rebuild -> markBound。
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

- V1 部分 `findFirst()` 依赖 source list 顺序；V3 SQL 必须用稳定 order 复现，否则重复运行可能漂移。
- title/year/category 在 V1 HashMap 平票时无稳定 tie-break；V3 需要稳定化，同时在文档里标明这是 dev-safe 改进。
- External ID 不走 locked_fields；如果误加锁保护会偏离 V1。
- `upsertUnifiedFields()` 与 B6 scalar rebuild 都会改同一字段，必须保持最终结果由 B6 raw-source 规则收敛。

## 验收标准

```text
B6 MARK 1/7 title/year vote and score max update unlocked unified fields
B6 MARK 2/7 published_at/total_episodes/duration/season choose first non-empty raw source with stable ordering
B6 MARK 3/7 alias_title/description/subtitle choose longest raw value
B6 MARK 4/7 remarks chooses latest updated raw value
B6 MARK 5/7 external IDs fill only invalid unified values and ignore locked_fields
B6 MARK 6/7 locked scalar fields preserve existing unified values
B6 MARK 7/7 aggregation-worker/search-service tests pass and docs record remaining cover/JGraphT gaps
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀。`AggregationService.bindRawVideo()` 现在在
`bindRawToUnified()` 后按顺序执行：

```text
rebuildUnifiedPipelineScalars(unifiedVideoId)
rebuildUnifiedPipelineRelations(unifiedVideoId)
markBound(rawVideoId)
```

实现结果：

- `BasicAttributeHandler`：
  - 未锁 `title` / `year` 时，基于同一 unified 下全部 raw sources 投票写回。
  - 未锁 `score` 时取 raw score 最大值。
  - 未锁 `publishedAt` 时取稳定 raw ordering 下第一个非空 `published_at`。
- `ExternalIdHandler`：
  - `douban_id`、`tmdb_id`、`imdb_id`、`rotten_tomatoes_id` 不受 `locked_fields` 保护。
  - 仅当 unified 当前值无效时，从 raw sources 按稳定顺序填第一个有效值。
  - external ID 有效性已贴回 V1：非空且不等于 `"0"`。
- `DynamicFieldHandler`：
  - 未锁 `aliasTitle`、`description`、`subtitle` 时取最长非空 raw 值。
  - 未锁 `remarks` 时取 `updated_at` 最新的非空 raw remarks。
  - 未锁 `totalEpisodes`、`duration`、`season` 时取稳定 raw ordering 下第一个非空值。
- `locked_fields` 支持 V1 camelCase key：`publishedAt`、`aliasTitle`、`totalEpisodes` 等。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

MARK：

```text
B6 MARK 1/7 title/year vote and score max update unlocked unified fields: done 2026-06-09
B6 MARK 2/7 published_at/total_episodes/duration/season choose first non-empty raw source with stable ordering: done 2026-06-09
B6 MARK 3/7 alias_title/description/subtitle choose longest raw value: done 2026-06-09
B6 MARK 4/7 remarks chooses latest updated raw value: done 2026-06-09
B6 MARK 5/7 external IDs fill only invalid unified values and ignore locked_fields: done 2026-06-09
B6 MARK 6/7 locked scalar fields preserve existing unified values: done 2026-06-09
B6 MARK 7/7 aggregation-worker/search-service tests pass and docs record remaining cover/JGraphT gaps: done 2026-06-09
```

仍未完成：

- V1 `CoverImagePipelineHandler` 的托管优先、fileSize 10% 阈值、R2 promote。
- `AggregationPipeline` handler order/failure isolation 的框架化 runtime。
- 完整 JGraphT raw/raw cluster、graph scoring 细节和 K8S live smoke。
