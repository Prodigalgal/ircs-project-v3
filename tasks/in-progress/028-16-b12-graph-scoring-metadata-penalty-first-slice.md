# 028-16-B12 graph scoring metadata penalty first slice task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- V1 `DefaultWeightedMatchingStrategy`
- V1 `GraphAggregationServiceImpl.populateMetadata()`
- V1 `RawVideoQueryRepositoryImpl.findAggregatedCandidatesByIds()`
- V1 `UnifiedVideoMaintenanceRepository.findCandidatesByIds()`
- V3 `AggregationMatchingStrategy`
- V3 `JdbcAggregationRepository`

## 背景

B9 已恢复 same-batch raw/raw JGraphT cluster，B10 已恢复 raw/unified context mixed graph，
B11 已补 V1 category bucket。但 V3 `AggregationMatchingStrategy` 目前在无 external ID 时
基本只使用 title score：`titleScore * 0.95`，缺少 V1 `DefaultWeightedMatchingStrategy`
中的 metadata Jaccard 与 penalty 规则。

V1 scoring 核心规则如下：

```text
season/version/external-id block 或 external-id perfect match 优先
titleScore < 0.80 -> 0
metadata 非空：final = titleScore * 0.70 + actor/director Jaccard * 0.30
metadata 任一侧为空：final = titleScore * 0.95
episode count incompatible: final -= 0.15
area both present and not contains-each-other: final -= 0.10
clamp to 0..1
```

V1 candidate metadata 由 `GraphAggregationServiceImpl.populateMetadata()` 在 projection 后填充：
raw 从 `video_actors`、`video_directors`、`video_raw_areas -> standard_areas` 读取；unified
从 `unified_video_actors`、`unified_video_directors`、`unified_video_standard_areas` 读取。
Jaccard 只使用 actor/director 名称，area 只用于 penalty。

## 目标

- 给 V3 raw aggregation candidate 补 actor/director name set 与标准 area name set。
- 给 V3 unified aggregation candidate 补 actor/director name set 与标准 area name set。
- 在 `AggregationMatchingStrategy` 中按 V1 公式接回 actor/director Jaccard、episode penalty
  和 area penalty。
- 保持 B1-B11 的 season/version/external-id/year/category bucket 行为不回退。
- 用 fake/unit 和 H2 repository tests 固定 scoring 与 metadata projection。

## 非目标

- 不接真实 ES-backed context recall。
- 不补 genre/language scoring；V1 `calculateMetadataJaccard()` 实际只用 actor/director。
- 不改 raw-source pipeline 输出、不改 cover/R2 side effect。
- 不执行 K8S live smoke。

## 输入

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationMatchingStrategy.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/RawVideoAggregationRecord.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/UnifiedVideoAggregationCandidate.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/**
```

## 输出

- `RawVideoAggregationRecord` / `UnifiedVideoAggregationCandidate` 增加 actor/director/area name sets。
- `JdbcAggregationRepository` candidate projection 聚合 raw/unified metadata names。
- `AggregationMatchingStrategy` 按 V1 scoring 公式计算 metadata score 与 penalty。
- 单元测试覆盖 metadata boost、metadata mismatch 降分、episode penalty、area penalty。
- repository H2 测试覆盖 raw/unified candidate metadata names 来源。

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

- candidate record 参数继续增加，测试 fixture 容易漏改；必须集中 helper 并跑 aggregation-worker 全量测试。
- metadata Jaccard 会降低“标题很像但演员/导演完全不同”的分数，可能改变 cluster 输出；第一刀用 V1 公式并用边界测试固定。
- V1 area penalty 依赖 `populateMetadata()` 后的 joined area string；V3 必须使用标准 area names，而不是 raw source value。

## 验收标准

```text
B12 MARK 1/6 raw candidates expose actor/director/standard area names like V1 populateMetadata
B12 MARK 2/6 unified candidates expose actor/director/standard area names like V1 populateMetadata
B12 MARK 3/6 matching strategy uses V1 title 0.70 + metadata Jaccard 0.30 formula when metadata exists
B12 MARK 4/6 matching strategy keeps V1 title * 0.95 fallback when either side metadata is empty
B12 MARK 5/6 episode and area penalties can drop otherwise mergeable candidates below threshold
B12 MARK 6/6 aggregation-worker/search-service tests pass and docs record remaining ES recall/runtime/live gaps
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀。B12 在 B9-B11 graph 基础上补回 V1
`DefaultWeightedMatchingStrategy` 的 metadata scoring 与 penalty：

- `RawVideoAggregationRecord` 和 `UnifiedVideoAggregationCandidate` 已携带
  `actorNames`、`directorNames`、`areaNames`，并对 null/blank 做空集合规整。
- `JdbcAggregationRepository` 按 V1 `populateMetadata()` 语义在 candidate 主查询后补 metadata：
  raw 从 `video_actors -> actors`、`video_directors -> directors`、
  `video_raw_areas -> raw_areas -> standard_areas` 读取；unified 从
  `unified_video_actors -> actors`、`unified_video_directors -> directors`、
  `unified_video_standard_areas -> standard_areas` 读取。
- `AggregationMatchingStrategy` 在无 external ID perfect match 时按 V1 公式计算：
  metadata 为空走 `titleScore * 0.95`；metadata 双方都有走
  `titleScore * 0.70 + actor/director Jaccard * 0.30`；episode 不兼容扣 `0.15`；
  双方 area 都存在且不 contains 匹配扣 `0.10`；最终 clamp 到 `0..1`。
- `RawVideoGraphClustererTest` 已覆盖同标题但 actor/director 不重叠不误聚合、
  raw/unified context bridge 受 metadata score 约束、episode penalty 阻断 raw edge。

验收结果：

```text
B12 MARK 1/6 raw candidates expose actor/director/standard area names like V1 populateMetadata: done 2026-06-09
B12 MARK 2/6 unified candidates expose actor/director/standard area names like V1 populateMetadata: done 2026-06-09
B12 MARK 3/6 matching strategy uses V1 title 0.70 + metadata Jaccard 0.30 formula when metadata exists: done 2026-06-09
B12 MARK 4/6 matching strategy keeps V1 title * 0.95 fallback when either side metadata is empty: done 2026-06-09
B12 MARK 5/6 episode and area penalties can drop otherwise mergeable candidates below threshold: done 2026-06-09
B12 MARK 6/6 aggregation-worker/search-service tests pass and docs record remaining ES recall/runtime/live gaps: done 2026-06-09
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
AggregationMatchingStrategyTest: tests=11, failures=0, errors=0
RawVideoGraphClustererTest: tests=12, failures=0, errors=0
JdbcAggregationRepositoryTest: tests=18, failures=0, errors=0
search-service test suites: tests=15, failures=0, errors=0
```

仍未完成：

- 真实 V1 `SearchQueryService.findCandidateUnifiedVideoIds()` / ES context recall 尚未接入。
- pipeline handler order/failure isolation runtime 与 K8S live smoke 仍需后续切片。
