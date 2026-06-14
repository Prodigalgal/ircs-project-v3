# 028-16-B11 category bucket graph first slice task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- V1 `GraphClusteringEngine`
- V1 `AggregationCandidate`
- V3 `RawVideoGraphClusterer`
- V3 `JdbcAggregationRepository`

## 背景

`028-16-B9` 已接回 same-batch raw/raw JGraphT cluster，`028-16-B10` 已接回 raw/unified
context mixed graph 第一刀。但 V1 `GraphClusteringEngine.buildGraphOptimized()` 在年份桶前
还有第一层 category bucket：`categoryName.trim()` 相同才进入同一比较桶；空分类落到
`UNKNOWN_CATEGORY`，不同 category 不比较。

当前 V3 candidate record 未携带 `categoryName`，graph clusterer 只做 year bucket guard，
因此仍可能把同名同年但不同标准分类的 raw/unified 建边。

## 目标

- 给 V3 raw aggregation candidate 补 V1 等价 `categoryName`：`raw_videos.data_source_category_id`
  -> `raw_category.category_id` -> `standard_category.name`。
- 给 V3 unified aggregation candidate 补 V1 等价 `categoryName`：`unified_videos.category_id`
  -> `standard_category.name`。
- 在 `RawVideoGraphClusterer` 建边前加入 V1 category bucket guard。
- 固定空/blank category 的 `UNKNOWN_CATEGORY` 语义：未知分类只和未知分类比较，不和具体分类比较。

## 非目标

- 不实现 actor/director Jaccard、episode/area penalty 或完整 graph scoring。
- 不改 ES-backed context recall，本轮继续使用 B10 已完成的 DB recall。
- 不改 category 投票/relations rebuild，B5 已覆盖该 pipeline 输出。
- 不执行 K8S live smoke。

## 输入

```text
D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/ingestion/aggregation/engine/impl/GraphClusteringEngine.java
D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/ingestion/aggregation/model/AggregationCandidate.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/RawVideoGraphClusterer.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
```

## 输出

- `RawVideoAggregationRecord` / `UnifiedVideoAggregationCandidate` 增加 category name 字段。
- `JdbcAggregationRepository` raw/unified select 补 category join。
- `AggregationGraphCandidate` 暴露 category name。
- `RawVideoGraphClusterer` 按 V1 category bucket 阻断跨分类 edge。
- 单元测试覆盖 raw/raw、raw/unified 和 UNKNOWN_CATEGORY 边界。

## 影响范围

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/**
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 风险

- category 来源如果取 `sourceCategoryName` 而不是标准分类名，会把同一作品按资源站来源分类误隔离。
- 未知 category 如果和具体 category 比较，会回退到 B9/B10 的误合并风险。
- mock/fixture 构造器参数增加，容易漏改测试；必须用本地 test 覆盖。

## 验收标准

```text
B11 MARK 1/5 raw candidates expose V1 standard categoryName from raw_category -> standard_category
B11 MARK 2/5 unified candidates expose V1 standard categoryName from unified_videos -> standard_category
B11 MARK 3/5 raw/raw graph edges do not cross concrete category buckets
B11 MARK 4/5 raw/unified context graph edges do not cross category buckets, UNKNOWN only compares with UNKNOWN
B11 MARK 5/5 aggregation-worker/search-service tests pass and docs record remaining scoring/live gaps
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀。B11 在 B9/B10 graph 基础上补回 V1
`GraphClusteringEngine.buildGraphOptimized()` 的第一层 category bucket：

- `RawVideoAggregationRecord` 已携带 V1 等价标准分类名，来源为
  `raw_videos.data_source_category_id` -> `raw_category.category_id` ->
  `standard_category.name`。
- `UnifiedVideoAggregationCandidate` 已携带 V1 等价标准分类名，来源为
  `unified_videos.category_id` -> `standard_category.name`。
- `AggregationGraphCandidate` 已暴露 category name，`RawVideoGraphClusterer` 建边前按
  `categoryName.trim()` 分桶；空/blank 分类归入 `UNKNOWN_CATEGORY`。
- raw/raw 与 raw/unified graph edge 都不会跨具体 category bucket；`UNKNOWN_CATEGORY`
  只和 `UNKNOWN_CATEGORY` 比较，不和具体分类比较。
- repository 级 H2 测试已覆盖 raw/unified candidate category join，clusterer 测试已覆盖
  raw/raw、raw/unified 和 unknown category 边界。

验收结果：

```text
B11 MARK 1/5 raw candidates expose V1 standard categoryName from raw_category -> standard_category: done 2026-06-09
B11 MARK 2/5 unified candidates expose V1 standard categoryName from unified_videos -> standard_category: done 2026-06-09
B11 MARK 3/5 raw/raw graph edges do not cross concrete category buckets: done 2026-06-09
B11 MARK 4/5 raw/unified context graph edges do not cross category buckets, UNKNOWN only compares with UNKNOWN: done 2026-06-09
B11 MARK 5/5 aggregation-worker/search-service tests pass and docs record remaining scoring/live gaps: done 2026-06-09
```

验证：

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

测试证据：

```text
AggregationMatchingStrategyTest: tests=6, failures=0, errors=0
AggregationServiceTest: tests=9, failures=0, errors=0
JdbcAggregationRepositoryTest: tests=16, failures=0, errors=0
RawVideoGraphClustererTest: tests=9, failures=0, errors=0
search-service test suites: tests=15, failures=0, errors=0
```

仍未完成：

- 真实 V1 `SearchQueryService.findCandidateUnifiedVideoIds()` / ES context recall 尚未接入。
- actor/director Jaccard、episode penalty、area penalty 等 metadata scoring/penalty 后续已由
  B12 补第一刀。
- pipeline handler order/failure isolation runtime 与 K8S live smoke 仍需后续切片。
