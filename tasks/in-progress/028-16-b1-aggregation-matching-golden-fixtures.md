# 028-16-B1 aggregation matching golden fixtures task spec

状态：第一刀已完成；暂留 in-progress，等待主线程统一归档或拆分后续 B2/B3

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `GraphClusteringEngine`
- V1 `DefaultWeightedMatchingStrategy`
- V3 `AggregationService`

## 背景

V1 aggregation 使用 JGraphT 连通分量、JaroWinkler 相似度、season/version/external ID 阻断和
leader 选举。V3 当前 aggregation-worker 主要按 external ID 命中或新建 unified，缺少无外部 ID
候选召回、相似标题聚合、冲突阻断和 duplicate unified merge 保护。

## 目标

- 在 aggregation-worker 增加第一批 V1 matching golden fixture。
- 明确哪些 fixture 先实现、哪些进入后续图聚类/merge task。
- 实现最小可验证的 matching strategy，优先覆盖无 external ID 同片聚合和冲突阻断。

## 非目标

- 本切片不一次性完整迁移 JGraphT cluster merge victim。
- 不修改 search-service。
- 不做 K8S live smoke，先以 repository/service test 证明。

## 输入

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/**
services/ircs-aggregation-worker/src/test/**
build.gradle
```

## 输出

```text
aggregation matching fixture tests
minimal matching strategy implementation or explicit failing inventory
task/doc MARK update
```

## 影响范围

```text
services/ircs-aggregation-worker/**
build.gradle only if service-scoped commons-text/JGraphT dependency is required
```

## 依赖

- V1 commons-text/JGraphT 语义需要按服务最小化接入。
- 后续 duplicate unified merge 和 relation union 可作为 `028-16-B2/B3`。

## 风险

- fuzzy matching 过宽会误合不同季/不同版本，必须先做阻断测试。
- DB fixture 不清理会污染本地或 dev 环境，本切片先本地 test。

## 验收标准

```text
MARK 1/5 title/year candidate recall works when external IDs are absent
MARK 2/5 external ID conflict blocks merge
MARK 3/5 season/version-sensitive fixtures do not merge incorrectly
MARK 4/5 similar title fixture binds to existing unified instead of creating duplicate
MARK 5/5 aggregation-worker tests pass and document remaining graph/merge gaps
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
```

## 实现结果

本轮第一刀已在 `services/ircs-aggregation-worker` 内完成最小 V1 matching strategy：

- 新增 `AggregationMatchingStrategy`，使用 service-scoped `commons-text` JaroWinkler 进行标题交叉相似度判断。
- 新增 `UnifiedVideoAggregationCandidate` 作为 repository candidate projection。
- `JdbcAggregationRepository.findMatchingUnifiedVideo` 从单纯 external ID 命中扩展为 external ID candidate + title/year candidate recall，再由策略统一阻断/评分。
- `RawVideoAggregationRecord` 补充 `subtitle` 与 `season` 字段，用于 season/version-sensitive matching。
- `AggregationService` 保持原有绑定流程：命中候选时复用 unified，未命中时创建 unified；未引入 HTTPRoute，未修改其他服务。

## 当前 MARK

```text
MARK 1/5 title/year candidate recall works when external IDs are absent: passed
  - JdbcAggregationRepositoryTest.recallsTitleYearCandidatesWhenExternalIdsAreAbsent
  - AggregationMatchingStrategyTest.matchesTitleYearCandidateWhenExternalIdsAreAbsent
MARK 2/5 external ID conflict blocks merge: passed
  - AggregationMatchingStrategyTest.externalIdConflictBlocksOtherwiseSimilarCandidate
MARK 3/5 season/version-sensitive fixtures do not merge incorrectly: passed
  - AggregationMatchingStrategyTest.seasonSensitiveCandidateDoesNotMergeIncorrectly
  - AggregationMatchingStrategyTest.versionSensitiveCandidateDoesNotMergeIncorrectly
MARK 4/5 similar title fixture binds to existing unified instead of creating duplicate: passed
  - AggregationMatchingStrategyTest.similarTitleBindsToExistingUnifiedCandidate
  - AggregationServiceTest.reusesExistingUnifiedVideoWhenSimilarTitleMatchesWithoutExternalIds
MARK 5/5 aggregation-worker tests pass and document remaining graph/merge gaps: passed
  - .\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
```

## 验证记录

```text
2026-06-08:
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

## 后续 gap

- duplicate unified merge victim、victim delete、binding migration 和 search DELETE side effect 仍未实现，保留给 `028-16-B2/B3`。
- 本轮没有接入 JGraphT cluster，也没有实现 raw/raw 多节点 cluster leader 选举。
- 本轮没有实现 category/genre/area/language/person/cover image relation union，仍归属父任务 `028-16-B`。
- 本轮仅做本地 service/repository/strategy tests，不做 K8S live smoke。
