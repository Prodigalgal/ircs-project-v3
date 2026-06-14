# 028-16-B3 unified relation union victim merge task spec

状态：第一刀已完成；暂留 in-progress，等待 raw-source AggregationPipeline 和 live smoke 闭环

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`

上游来源：

- `docs/reports/088-v1-v3-deep-parity-gap-audit.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-b2-duplicate-unified-merge-victim-delete.md`
- V1 `AggregationPipeline`
- V1 `MetadataHandler`
- V1 `CategoryHandler`
- V1 `CoverImagePipelineHandler`
- V3 `JdbcAggregationRepository.mergeDuplicateUnifiedVideos`
- V3 portal/search unified relation readers

## 背景

`028-16-B2` 已完成 duplicate unified 的 root/victim 收敛、raw binding 迁移、favorite/history
冲突清理、victim 删除和 search DELETE side effect。但 B2 删除 victim relation 前没有把 victim
侧 `genre/area/language/person` 关系合并到 root，仍会丢失 V1 `MetadataHandler` 的 relation
union 语义。

V1 `MetadataHandler` 对 genres、actors、directors、areas、languages 使用 union 策略，并尊重
`locked_fields`。V1 `CategoryHandler` 使用 raw source category 投票，`CoverImagePipelineHandler`
使用 managed image 与 file size 择优覆盖。这些 raw-source pipeline 需要后续单独接入；本切片先补
duplicate unified merge 时最直接的数据丢失风险。

## 目标

- 在 `mergeDuplicateUnifiedVideos(root, victims)` 删除 victim 前，将 victim unified relation
  rows union 到 root。
- 覆盖 `unified_video_actors`、`unified_video_directors`、`unified_video_genres`、
  `unified_video_standard_languages`、`unified_video_standard_areas`。
- 对 root 已存在的相同 relation 使用 conflict-safe dedupe。
- 删除 victim 后，root relation 保留 root 原有值和 victim 独有值。
- 保持 B2 的 raw binding、favorite/history、victim DELETE 和 search sync 行为不回退。

## 非目标

- 不在本切片实现 raw sources -> unified 的完整 `AggregationPipeline`。
- 不实现 category 投票、cover image 择优覆盖或 R2 promote。
- 不实现 `locked_fields` 对 raw-source pipeline 的完整字段级保护；本切片只做 victim unified
  relation 保全，不覆盖 root 字段。
- 不接完整 JGraphT raw/raw cluster。
- 不执行 K8S live smoke。

## 输入

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepositoryTest.java
platform/ircs-migrator/src/main/resources/db/changelog/2026/01/06-01-changelog.sql
platform/ircs-migrator/src/main/resources/db/changelog/2026/01/27-01-add-area-tables.sql
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/repository/SearchDocumentJdbcRepository.java
services/ircs-portal-service/src/main/java/com/prodigalgal/ircs/portal/JdbcPortalRepository.java
```

## 输出

- repository victim relation union primitive。
- H2/PostgreSQL-compatible relation union fixture test。
- B2 mock SQL order regression 更新。
- 089 总账、迁移账本、任务索引回写。

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

- 关系表主键列顺序不同，必须逐表明确列名，不能用通用字符串拼错。
- 如果先删 victim 再 union，会丢数据；必须固定 SQL 顺序。
- H2 与 PostgreSQL `on conflict do nothing` 在本项目已有兼容用法，但仍需 fixture 覆盖。
- 本切片不是完整 pipeline，不能误标 P0-08 完成。

## 验收标准

```text
B3 MARK 1/5 victim actors/directors union into root before victim delete
B3 MARK 2/5 victim genres/languages/areas union into root before victim delete
B3 MARK 3/5 root duplicate relations dedupe with on conflict do nothing
B3 MARK 4/5 B2 raw binding/favorite/history/victim delete behavior remains covered
B3 MARK 5/5 aggregation-worker tests pass and docs record remaining raw-source pipeline gaps
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
B3 MARK 1/5 done 2026-06-09: victim actors/directors 在 victim delete 前 union 到 root。
B3 MARK 2/5 done 2026-06-09: victim genres/languages/areas 在 victim delete 前 union 到 root。
B3 MARK 3/5 done 2026-06-09: root duplicate relations 使用 `on conflict do nothing` 去重。
B3 MARK 4/5 done 2026-06-09: B2 raw binding/favorite/history/victim delete/search DELETE 行为仍由组合测试覆盖。
B3 MARK 5/5 done 2026-06-09: aggregation-worker/search-service tests passed，raw-source pipeline gaps 已保留。
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
B4: raw sources -> unified 的完整 AggregationPipeline，包括 locked_fields、category 投票、
    dynamic fields、cover image 择优覆盖和 R2 promote。
B5: 完整 JGraphT raw/raw cluster、更多 graph scoring 细节和 K8S live smoke。
```
