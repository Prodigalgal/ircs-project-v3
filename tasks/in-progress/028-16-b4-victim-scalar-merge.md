# 028-16-B4 victim scalar merge task spec

状态：第一刀已完成；暂留 in-progress，等待 raw-source AggregationPipeline 和 live smoke 闭环

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`

上游来源：

- `tasks/in-progress/028-16-b2-duplicate-unified-merge-victim-delete.md`
- `tasks/in-progress/028-16-b3-unified-relation-union-victim-merge.md`
- V1 `UnifiedVideo.merge(victim)`
- V3 `JdbcAggregationRepository.mergeDuplicateUnifiedVideos`

## 背景

`028-16-B2` 已补 duplicate victim 删除、raw binding/interaction 迁移和 search DELETE；
`028-16-B3` 已补 victim relation union。但 V1 `UnifiedVideo.merge(victim)` 还会在 root 缺值时
从 victim 补标量字段，并让 `lastTrendAt` 取更新值。当前 V3 删除 victim 前未补这些字段，
仍会丢失 victim 独有 metadata。

## 目标

- 删除 victim 前，把 victim 的标量 metadata merge 到 root。
- 按 V1 语义只在 root 缺值时补：`douban_id`、`tmdb_id`、`imdb_id`、
  `rotten_tomatoes_id`、`description`、`score`、`published_at`、`alias_title`、
  `total_episodes`、`duration`、`remarks`、`season`、`subtitle`。
- `last_trend_at` 取 root/victim 中更新的非空值。
- merge 后将 root `metadata_status` 标记为 `DIRTY`，因为 V1 `merge(victim)` 会将 metadata
  status 置为 dirty；后续 `AggregationService.upsertUnifiedFields()` 仍可在本轮 raw 绑定完成后
  标记为 `SYNCED`。
- 保持 B2/B3 的 raw binding、favorite/history、relation union、victim delete 和 search sync
  行为不回退。

## 非目标

- 不合并 `category_id`、`cover_image_id`、`locked_fields`；V1 `merge(victim)` 未显式处理这些字段。
- 不实现 raw-source `AggregationPipeline` 的投票、动态字段和 cover 优选。
- 不接完整 JGraphT raw/raw cluster。
- 不执行 K8S live smoke。

## 输入

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepositoryTest.java
platform/ircs-migrator/src/main/resources/db/changelog/2026/01/06-01-changelog.sql
platform/ircs-migrator/src/main/resources/db/changelog/2026/01/26-01-add-trend-time.sql
platform/ircs-migrator/src/main/resources/db/changelog/2026/02/add-unified-video-details.sql
platform/ircs-migrator/src/main/resources/db/changelog/2026/02/add-unified-video-season-subtitle.sql
platform/ircs-migrator/src/main/resources/db/changelog/2026/05/rename-subtitle.sql
```

## 输出

- repository victim scalar merge primitive。
- H2-compatible fixture test covering null/empty root fields and non-empty root preservation。
- B2/B3 SQL order regression 更新。
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

- 标量 merge 必须发生在删除 victim 前。
- 不能覆盖 root 已有非空值，否则会偏离 V1 merge 语义。
- `last_trend_at` 类型在 H2/PostgreSQL 兼容测试里需要显式覆盖。
- root `metadata_status` 在 `mergeDuplicateUnifiedVideos()` 内置为 `DIRTY`，但同一轮
  `upsertUnifiedFields()` 后会回到 `SYNCED`；测试需要按 repository primitive 解释。

## 验收标准

```text
B4 MARK 1/5 victim scalar fields fill only root null/empty fields
B4 MARK 2/5 root existing scalar fields are preserved
B4 MARK 3/5 last_trend_at keeps the newest timestamp and metadata_status becomes DIRTY during merge
B4 MARK 4/5 B2/B3 binding/interaction/relation/delete behavior remains covered
B4 MARK 5/5 aggregation-worker/search-service tests pass and docs record remaining raw-source pipeline gaps
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀代码实现和本地测试。

实现结果：

```text
B4 MARK 1/5 done 2026-06-09: victim scalar fields 只填充 root null/empty fields。
B4 MARK 2/5 done 2026-06-09: root existing scalar fields 保留，不被 victim 覆盖。
B4 MARK 3/5 done 2026-06-09: last_trend_at 保留 root/victim 中更新值，merge primitive 内 root metadata_status 置为 DIRTY。
B4 MARK 4/5 done 2026-06-09: B2/B3 binding/interaction/relation/delete 行为仍由测试覆盖。
B4 MARK 5/5 done 2026-06-09: aggregation-worker/search-service tests passed，raw-source pipeline gaps 已保留。
```

主要代码输出：

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepositoryTest.java
```

验证：

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

后续不在本切片内完成的边界：

```text
B5: raw sources -> unified 的完整 AggregationPipeline，包括 locked_fields、category 投票、
    dynamic fields、cover image 择优覆盖和 R2 promote。
B6: 完整 JGraphT raw/raw cluster、更多 graph scoring 细节和 K8S live smoke。
```
