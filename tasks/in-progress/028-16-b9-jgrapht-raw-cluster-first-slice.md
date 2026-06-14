# 028-16-B9 JGraphT raw/raw cluster first slice task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- V1 `GraphClusteringEngine`
- V1 `DefaultWeightedMatchingStrategy`
- V1 `GraphAggregationServiceImpl`

## 背景

B1-B8 已把 V3 aggregation-worker 的单 raw -> unified matching、duplicate unified merge、
raw-source pipeline relation/scalar/cover 和 cover R2 promote side effect 补到第一刀。
剩余 P0 聚合缺口中，`JGraphT raw/raw cluster` 仍未迁移：V1 会在同一批待处理 raw
source 与上下文 unified candidate 上构建加权图，按连通分量与 star peeling 输出 cluster。
V3 当前 `aggregateBatch()` 仍主要逐条 raw 处理，不能显式表达“同一批 raw 先成簇，再一次性
绑定到同一个 unified”的 V1 行为。

## 目标

- 在 aggregation-worker service scope 内接入 JGraphT。
- 新增 raw/raw graph cluster 第一刀：同一批 claimed raw 中，使用 V1 风格 edge threshold
  `0.85` 和 consistency threshold `0.90` 进行连通分量与 star peeling。
- raw/raw similarity 复用当前 B1 matching 规则的 season/version/external-id/title 阻断和评分；
  年份兼容性在 graph bucket 层按 V1 规则处理，避免引入另一套发散策略。
- `aggregateBatch()` 在批量 raw 数量大于 1 时先成簇；多 raw cluster 创建或复用一个
  unified，并复用 B1-B8 已验证的 merge、pipeline、search sync 与 cover R2 promote 副作用。
- 单 raw 路径保持现有行为，避免扩大 blast radius。

## 非目标

- 不在本切片实现完整 V1 context unified + raw 混合图。
- 不在本切片实现 actor/director Jaccard、area penalty、episode penalty 的完整 scoring。
- 不做 K8S live smoke。
- 不重写 B5-B8 pipeline SQL。

## 输入

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationService.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationMatchingStrategy.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
```

## 输出

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/RawVideoGraphClusterer.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/RawVideoAggregationCluster.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/RawVideoGraphClustererTest.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/AggregationServiceTest.java
```

## 影响范围

```text
services/ircs-aggregation-worker/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 风险

- raw/raw cluster 可能扩大误合并面，因此第一刀只在同一批 claimed raw 中启用，并复用
  既有 strict matching。
- cluster 内发布 search/R2 command 的顺序必须不回退 B1-B8；测试需要覆盖多 raw 只创建
  一个 unified、raw index 多次、unified index 一次。
- 本切片仍未覆盖 V1 context unified 混合图，不能把 P0-07/P0-08 整体标记完成。

## 验收标准

```text
B9 MARK 1/6 aggregation-worker has service-scoped JGraphT dependency
B9 MARK 2/6 raw/raw graph cluster uses edge 0.85 and consistency 0.90 with connected components
B9 MARK 3/6 raw/raw cluster blocks season/version/external-id conflicts through shared matching and year bucket guard
B9 MARK 4/6 aggregateBatch binds a multi-raw cluster to one unified and runs pipeline once after all binds
B9 MARK 5/6 search index and cover R2 promote side effects remain emitted for clustered raw members
B9 MARK 6/6 local aggregation-worker tests pass and docs record remaining context graph/runtime/live gaps
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀。aggregation-worker 仅在自身 service scope 接入
`org.jgrapht:jgrapht-core:1.5.2`，新增 `RawVideoGraphClusterer` 与
`RawVideoAggregationCluster`。`aggregateBatch()` 在同一批 claimed raw 多于 1 条时，先按
V1 风格的 raw/raw graph cluster 成簇；多 raw cluster 会创建或复用一个 unified，将 cluster
内 raw 全部绑定到该 unified，并在所有 bind 后只运行一次 B5-B8 已验证的 scalars、
relations、cover pipeline 与 cover R2 promote side effect。

实现结果：

- `RawVideoGraphClusterer` 使用 JGraphT `SimpleWeightedGraph` 与
  `ConnectivityInspector`，边阈值为 `0.85`，star peeling 一致性阈值为 `0.90`。
- raw/raw 年份桶贴近 V1：有效年份差 `<= 1` 可比较；未知年份只和未知年份比较。
- raw/raw similarity 复用 `AggregationMatchingStrategy` 的 season/version/external-id/title
  阻断和评分；年份兼容性由 graph bucket guard 处理；外部 ID 冲突、季数冲突、动画/真人冲突
  不会连边。
- `AggregationService.aggregateBatch()` 对单 raw 继续走原 `bindRawVideo()`；对 multi-raw
  cluster 复用现有 root/victim merge、pipeline rebuild、search index 和 cover R2 promote
  链路。
- 当前切片未实现 V1 context unified + raw 混合图、完整 actor/director metadata scoring、
  handler order/failure isolation runtime 和 K8S live smoke。

MARK：

```text
B9 MARK 1/6 aggregation-worker has service-scoped JGraphT dependency: done 2026-06-09
B9 MARK 2/6 raw/raw graph cluster uses edge 0.85 and consistency 0.90 with connected components: done 2026-06-09
B9 MARK 3/6 raw/raw cluster blocks season/version/external-id conflicts through shared matching and year bucket guard: done 2026-06-09
B9 MARK 4/6 aggregateBatch binds a multi-raw cluster to one unified and runs pipeline once after all binds: done 2026-06-09
B9 MARK 5/6 search index and cover R2 promote side effects remain emitted for clustered raw members: done 2026-06-09
B9 MARK 6/6 local aggregation-worker tests pass and docs record remaining context graph/runtime/live gaps: done 2026-06-09
```

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

仍未完成：

- V1 `sourceCandidates + contextCandidates` 的完整 raw/unified 混合图。
- actor/director Jaccard、episode penalty、area penalty 等完整 graph scoring。
- pipeline handler order/failure isolation runtime。
- K8S live smoke。
