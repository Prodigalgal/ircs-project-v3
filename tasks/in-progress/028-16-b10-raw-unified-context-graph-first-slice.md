# 028-16-B10 raw/unified context graph first slice task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- V1 `GraphAggregationServiceImpl.loadContextAccelerated()`
- V1 `GraphClusteringEngine.cluster(sourceCandidates, contextCandidates)`
- V3 `028-16-B9` same-batch raw/raw cluster first slice

## 背景

B9 已恢复同一批 claimed raw 之间的 JGraphT raw/raw cluster，但 V1 图聚类并不只看 raw。
V1 `GraphAggregationServiceImpl` 会先加载本批 source raw candidates，再通过 external IDs
和 title/year search 召回 context unified candidates，把 raw 和 unified 一起放入
`GraphClusteringEngine`。图聚类会跳过纯 unified component，但 raw/unified component 会复用
已有 unified；同一 cluster 内多个 unified 会形成 root/victim merge。

V3 当前 `RawVideoGraphClusterer` 仍是 raw-only。`AggregationService.findClusterMatchPlan()`
会在 cluster 写入阶段逐 raw 调 `findMatchPlan()` 找 unified，但这不是 V1 的“unified 作为
graph node 参与 edge/connected component/star peeling”的语义。

## 目标

- 在 B9 graph 基础上新增 context unified node，恢复 V1 raw/unified mixed graph 第一刀。
- repository 提供 `findContextUnifiedCandidates(List<RawVideoAggregationRecord>)`，按 V1
  语义先用 douban/tmdb external ID 召回；只有 raw 缺少有效 douban/tmdb 时才退到
  title/year recall，并做稳定去重，不改变 HTTP/API 或其他服务。
- graph clusterer 接收 raw sources 与 unified context candidates；raw/raw、raw/unified 都可以
  建边，unified/unified 不建边；纯 unified component 不输出 cluster。
- cluster 中如含 unified，写入阶段优先复用 unified context，并将其余 unified 作为 victims
  走现有 B2-B4 merge 链路。
- 保持 B5-B9 的 pipeline/search/R2 side effect 不回退。

## 非目标

- 不实现 V1 `SearchQueryService.findCandidateUnifiedVideoIds()` 的真实 ES context recall；
  本切片复用当前 repository external ID + title/year SQL recall。
- 不补 actor/director Jaccard、episode penalty、area penalty 等完整 graph scoring。
- 不在 B10 本切片实现 category bucket；后续 `028-16-B11` 已补 category bucket 第一刀。
- 不做 K8S live smoke。

## 输入

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationService.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/RawVideoGraphClusterer.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationMatchingStrategy.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
```

## 输出

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationGraphCandidate.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/RawVideoAggregationCluster.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/RawVideoGraphClusterer.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/RawVideoGraphClustererTest.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/AggregationServiceTest.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepositoryTest.java
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

- context unified 进入 graph 可能改变 root 选择，必须用测试固定“unified 优先”以及 victim merge。
- B10 本切片没有 category bucket 和 metadata scoring；后续 B11 已补 category bucket 第一刀，
  但 metadata scoring 仍未完成，不能把 P0-07/P0-08 整体标记完成。
- 当前 recall 仍是 SQL title/year，不是 V1 的 search service context recall；后续仍需补 ES/live smoke。

## 验收标准

```text
B10 MARK 1/6 repository recalls context unified candidates for a raw batch by V1 douban/tmdb or title/year fallback
B10 MARK 2/6 graph clusterer accepts raw and unified nodes and skips pure unified components
B10 MARK 3/6 raw/unified edge uses threshold 0.85 and star consistency 0.90 like B9
B10 MARK 4/6 multi-raw cluster with one context unified reuses that unified without creating a new one
B10 MARK 5/6 cluster with multiple context unified IDs emits root/victim merge through existing B2-B4 path
B10 MARK 6/6 local aggregation-worker/search-service tests pass and docs record remaining scoring/runtime/live gaps
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀。B10 在 B9 raw/raw graph 基础上加入 context unified node：

- `JdbcAggregationRepository.findContextUnifiedCandidates()` 已按 V1 `loadContextAccelerated()`
  语义收窄：context external recall 只使用 douban/tmdb；raw 缺少有效 douban/tmdb 时才走
  title/year SQL recall；imdb/rottenTomatoes 仍保留在后续 matching conflict/match 判断中。
- `RawVideoGraphClusterer.cluster(rawVideos, contextUnifiedVideos)` 已支持 raw + unified mixed
  graph；raw/raw 与 raw/unified 可建边，unified/unified 不建边，纯 unified component 不输出。
- `RawVideoAggregationCluster` 已携带 `contextUnifiedVideoIds`，写入阶段可优先复用 context
  unified；多个 context unified 会走既有 B2-B4 root/victim merge 链路。
- `AggregationService` 多 raw 分支会先召回 context unified，再把 raw/context 一起传入 graph
  cluster；B5-B8 pipeline/search/R2 promote side effect 保持原顺序。
- `JdbcAggregationRepositoryTest`、`RawVideoGraphClustererTest`、`AggregationServiceTest`
  已覆盖 context recall、pure unified skip、conflict bridge 阻断、复用单个 context unified
  与多个 context unified victim merge。

验收结果：

```text
B10 MARK 1/6 repository recalls context unified candidates for a raw batch by V1 douban/tmdb or title/year fallback: done 2026-06-09
B10 MARK 2/6 graph clusterer accepts raw and unified nodes and skips pure unified components: done 2026-06-09
B10 MARK 3/6 raw/unified edge uses threshold 0.85 and star consistency 0.90 like B9: done 2026-06-09
B10 MARK 4/6 multi-raw cluster with one context unified reuses that unified without creating a new one: done 2026-06-09
B10 MARK 5/6 cluster with multiple context unified IDs emits root/victim merge through existing B2-B4 path: done 2026-06-09
B10 MARK 6/6 local aggregation-worker/search-service tests pass and docs record remaining scoring/runtime/live gaps: done 2026-06-09
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
RawVideoGraphClustererTest: tests=6, failures=0, errors=0
AggregationServiceTest: tests=9, failures=0, errors=0
JdbcAggregationRepositoryTest: tests=14, failures=0, errors=0
ConfigServiceTest: tests=18, failures=0, errors=0
```

仍未完成：

- 真实 V1 `SearchQueryService.findCandidateUnifiedVideoIds()` / ES context recall 尚未接入。
- category bucket 后续已由 B11 补第一刀；actor/director Jaccard、episode penalty、
  area penalty 等 metadata scoring/penalty 后续已由 B12 补第一刀。
- pipeline handler order/failure isolation runtime 与 K8S live smoke 仍需后续切片。
