# 028-16-B aggregation graph and pipeline parity task spec

状态：进行中

父任务：

- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/088-v1-v3-deep-parity-gap-audit.md`
- `docs/migrations/088-v1-v3-deep-parity-gap-audit.md`
- `tasks/done/019-01-aggregation-worker-scheduler-mvp.md`
- `tasks/done/028-09-search-producer-live-smoke-parity.md`

## 背景

V3 aggregation-worker 已能扫描 PENDING raw、绑定或创建 unified、发布 raw/unified search sync，
但 `019` 文档已明确不包含完整图聚类、CQEngine/JGraphT、merge victim、category/genre/area/
language/person 关系表和 cover image 归并。V1 聚合实际由 `GraphAggregationServiceImpl`、
`GraphClusteringEngine`、`DefaultWeightedMatchingStrategy` 与 `AggregationPipeline` 共同完成。

## 目标

- 接回 V1 fuzzy matching：season/version blocking、external ID conflict、JaroWinkler 标题交叉匹配、actor/director Jaccard、episode/area penalty。
- 接回图聚类 cluster 语义，至少覆盖 raw/raw 与 raw/unified 多节点场景。
- 接回 aggregation pipeline 的复杂字段汇总：category、genre、area、language、actor、director、cover image、dynamic metadata。
- 接回 unified merge victim 删除和关系迁移策略。
- 保持 `028-09` 已完成 search sync、dirty lifecycle 和 recalculate 行为不回退。

## 非目标

- 不做生产级全量历史重聚合。
- 不在没有 fixture 的情况下启用大 batch。
- 不新增 HTTPRoute。

## 输入

V1：

```text
GraphAggregationServiceImpl
GraphClusteringEngine
DefaultWeightedMatchingStrategy
AggregationCandidate
AggregationCluster
AggregationPipeline
BasicAttributeHandler
ExternalIdHandler
MetadataHandler
CategoryHandler
CoverImagePipelineHandler
DynamicFieldHandler
```

V3：

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationService.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/video/**
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/**
```

## 输出

- aggregation-worker service-scoped dependencies for commons-text/JGraphT if needed。
- Matching strategy、candidate loader、cluster engine 和 pipeline classes。
- 本地 strategy/repository/service tests。
- 最小 live smoke：多 raw 无外部 ID fuzzy merge、raw+existing unified merge、victim cleanup。
- 报告、迁移记录和任务状态回写。

## 影响范围

```text
services/ircs-aggregation-worker/**
services/ircs-content-service/**
services/ircs-search-service/**
docs/reports/088-v1-v3-deep-parity-gap-audit.md
tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md
```

## 风险

- 聚合策略可能错误合并不同作品，必须先以小 fixture 和 strict thresholds 验证。
- merge victim 牵涉 unified 关系、interaction、search doc，必须分阶段启用。
- JGraphT/commons-text 增加镜像体积，但比全局接入可控。

## 验收标准

```text
MARK 1/9 aggregation-worker has service-scoped matching dependencies
MARK 2/9 matching strategy blocks season/version/external-id conflicts like V1
MARK 3/9 matching strategy merges title/alias/subtitle fuzzy candidates with metadata score
MARK 4/9 graph clustering groups raw/raw and raw/unified candidates above threshold
MARK 5/9 aggregation pipeline writes category/genre/area/language/person relations
MARK 6/9 aggregation pipeline carries cover image and dynamic metadata where V1 does
MARK 7/9 merge victim path deletes duplicate unified and preserves bindings/search refresh
MARK 8/9 recalculate keeps old unified dirty lifecycle compatible with 028-09
MARK 9/9 live smoke leaves DB/ES/queue residue clean and no HTTPRoute added
```

## 测试边界

```powershell
.\gradlew.bat :services:ircs-aggregation-worker:test :services:ircs-content-service:test :services:ircs-search-service:test --rerun-tasks
.\scripts\smoke\search-producer-aggregation-02809-smoke.ps1
.\scripts\smoke\search-producer-old-unified-dirty-02809-smoke.ps1
```

## 当前状态

已合并审计 agent B 的聚合/匹配/绑定/dirty lifecycle 细节，并拆出首个实现切片：

```text
tasks/in-progress/028-16-b1-aggregation-matching-golden-fixtures.md
```

该切片先覆盖无 external ID 候选召回、external ID 冲突阻断、season/version 阻断和相似标题
绑定，后续再拆 duplicate unified merge、relation union 和 dirty lifecycle。

当前子切片进度：

```text
028-16-B1 aggregation matching fixtures: done first slice, MARK 5/5, local aggregation-worker test passed
028-16-B2 duplicate unified merge victim delete: done first slice, MARK 6/6, local aggregation-worker/search-service tests passed
028-16-B3 unified relation union victim merge: done first slice, MARK 5/5, local aggregation-worker/search-service tests passed
028-16-B4 victim scalar merge: done first slice, MARK 5/5, local aggregation-worker/search-service tests passed
028-16-B5 raw-source pipeline category/relation rebuild: done first slice, MARK 6/6, local aggregation-worker/search-service tests passed
028-16-B6 raw-source pipeline scalar fields: done first slice, MARK 7/7, local aggregation-worker/search-service tests passed
028-16-B7 raw-source pipeline cover image: done first slice, MARK 5/5, local aggregation-worker/search-service tests passed
028-16-B8 cover R2 promote side effect: done first slice, MARK 5/5, local messaging/aggregation/storage tests passed
028-16-B9 JGraphT raw/raw cluster first slice: done first slice, MARK 6/6, local aggregation-worker/search-service tests passed
028-16-B10 raw/unified context graph first slice: done first slice, MARK 6/6, local aggregation-worker/search-service tests passed
028-16-B11 category bucket graph first slice: done first slice, MARK 5/5, local aggregation-worker/search-service tests passed
028-16-B12 graph scoring metadata penalty first slice: done first slice, MARK 6/6, local aggregation-worker/search-service tests passed
028-16-B13 ES-backed context recall first slice: done first slice, MARK 6/6, local aggregation-worker/search-service tests passed
028-16-B14 pipeline runtime order/failure isolation first slice: done first slice, MARK 6/6, local aggregation-worker/search-service tests passed
028-16-B15 aggregation pipeline runtime live smoke: done, MARK 8/8, dev K8S live smoke passed
```

遗留 gap：

```text
真实 R2 upload live smoke、multi context victim merge production-like smoke、search outbox/stats 深化
```

2026-06-09：B15 已完成 dev K8S aggregation pipeline runtime live smoke。为保持 V1
DynamicField `remarks` 语义，已新增 `raw_videos.aggregation_status_updated_at`，让
aggregation scheduler/状态心跳不再改写 raw 业务 `updated_at`。B15 smoke 通过
MARK 8/8：multi-raw scheduler bind、pipeline scalar/category、metadata relation union、
cover promote gated、raw/unified ES refresh、queue/DLQ cleanup、HTTPRoute/ResourceQuota
后置检查均通过。当前 B 线的 K8S runtime smoke gap 已关闭，真实 R2 upload 和更完整
production-like merge/reconciliation 仍保留后续独立任务。

2026-06-10：`028-16-F2/B15` 已补 aggregation local closure。search sync producer 统一发布结构化
raw/unified INDEX 与 unified DELETE；search-service 对结构化 payload 做字段完整性校验，缺字段时
直接 no-requeue reject，legacy UUID payload 继续兼容。pipeline runtime 任一 stage failure 后，
aggregation-service 不再 mark raw `BOUND` 或发布 root raw/unified INDEX / R2 promote，改为 raw
`PENDING` + unified `DIRTY`；若 duplicate victim 已在 DB 删除，则仍发布 victim unified DELETE。
`recalculateUnified()` 只在所有 raw 成功后才 `SYNCED` + unified INDEX。focused 测试通过：

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --tests "*Aggregation*"
.\gradlew.bat --no-daemon :services:ircs-search-service:test --tests "*SearchSync*"
```

本轮未执行 live smoke/K8S rollout/真实 R2 upload；multi context victim merge production-like smoke、
R2 object cleanup、队列/DLQ/ES residue 和持久 search outbox/stats processor 仍进
`UTB-02816-P1P2-01` 或后续独立任务。

## 2026-06-09 B2 duplicate unified merge / victim delete 边界

已根据只读盘点新增：

```text
tasks/in-progress/028-16-b2-duplicate-unified-merge-victim-delete.md
```

下一刀只承接 V1 cluster 中已有多个 unified 时的 root/victim 收敛闭环：root 选择、victim
raw binding 迁移、favorite/history 冲突清理、victim unified delete 和 search DELETE
side effect。relation union、完整 JGraphT raw/raw cluster、cover/category/genre/area/language/
person 聚合继续留给 B3/B4，避免扩大 blast radius。

只读盘点发现 V1 `GraphAggregationServiceImpl` 中 interaction migration 调用疑似把
`victim -> victim` 作为参数；V3 后续实现按目标语义固定为 `victim -> root`，并用测试覆盖。

## 2026-06-09 B2 第一刀实现结果

已完成 root/victim 收敛、victim raw binding 迁移、favorite/history 冲突清理、victim
unified scoped relation 删除和 search DELETE side effect。实现保持 root unified INDEX、raw
INDEX，并额外为 victim unified 发布结构化 `UNIFIED_VIDEO DELETE`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

仍未标记完成的 V1 parity：raw-source AggregationPipeline、完整 JGraphT raw/raw cluster、
更多 graph scoring 细节和 K8S live smoke。

## 2026-06-09 B3 unified relation union / victim merge 第一刀

已新增并完成：

```text
tasks/in-progress/028-16-b3-unified-relation-union-victim-merge.md
```

B3 第一刀在 `mergeDuplicateUnifiedVideos(root, victims)` 删除 victim 前，将 victim unified
侧的 `actors/directors/genres/standard_languages/standard_areas` 通过 `insert ... select ...
on conflict do nothing` union 到 root。这样 B2 删除 victim 时不再丢掉 V1 `UnifiedVideo.merge(victim)`
里的 relation `addAll` 语义。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

仍未标记完成的 V1 parity：raw sources -> unified 的完整 `AggregationPipeline`，包括
`locked_fields`、category 投票、dynamic fields、cover image 择优覆盖/R2 promote；完整
JGraphT raw/raw cluster 和 K8S live smoke 也继续保留。

## 2026-06-09 B5 raw-source pipeline category / relation rebuild 第一刀

已新增并完成：

```text
tasks/in-progress/028-16-b5-raw-source-pipeline-category-relation.md
```

B5 第一刀在 raw 绑定 unified 后，基于该 unified 下全部 raw sources 重建 V1
`CategoryHandler` 与 `MetadataHandler` 的核心关系输出：category 投票写入
`unified_videos.category_id`，并从 raw `video_actors/video_directors/video_raw_genres/
video_raw_languages/video_raw_areas` 重建 unified `actors/directors/genres/languages/areas`。
对应 `locked_fields` 命中时保持 V1 语义：不清空、不覆盖。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

仍未标记完成的 V1 parity：`BasicAttributeHandler`、`ExternalIdHandler`、`DynamicFieldHandler`、
`CoverImagePipelineHandler`、handler order/failure isolation、完整 JGraphT raw/raw cluster 和
K8S live smoke。

## 2026-06-09 B6 raw-source pipeline scalar fields 第一刀

已新增并完成：

```text
tasks/in-progress/028-16-b6-raw-source-pipeline-scalar-fields.md
```

B6 第一刀在 raw 绑定 unified 后，基于同一 unified 下全部 raw sources 重建 V1
`BasicAttributeHandler`、`ExternalIdHandler` 和 `DynamicFieldHandler` 的标量输出：
title/year 投票、score 最大值、published_at 首个非空、external IDs 填空且不受锁保护、
alias/description/subtitle 最长、remarks 最新、total_episodes/duration/season 首个非空。
对应 V1 `locked_fields` 命中时，Basic/Dynamic 字段不覆盖。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

后续 B7-B14 已分别承接 cover、R2 promote、JGraphT/context/scoring/ES recall 和
pipeline runtime 第一刀；当前仍未标记完成的 V1 parity：K8S live smoke。

## 2026-06-09 B7 raw-source pipeline cover image 第一刀

已新增并完成：

```text
tasks/in-progress/028-16-b7-raw-source-pipeline-cover-image.md
```

B7 第一刀在 raw 绑定 unified 后，基于当前 unified cover 和同一 unified 下全部 raw sources
重建 V1 `CoverImagePipelineHandler` 的核心选择规则：当前空则采用第一个 raw cover，managed
`LOCAL`/`R2` 优先于 `EXTERNAL`，同类 managedness 只有 `file_size > current * 1.1` 才替换，
`file_size` null 按 0 处理，并且 `coverImageUrl` locked field 不阻止该 handler。

本切片未调用 storage-service，也未执行真实 R2 upload；V1 `promoteToR2()` 的跨服务副作用
后续已由 B8 拆到 storage command / due upload scheduling。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

后续 B8 已承接 `coverImageService.promoteToR2(sourceImage.getId())` side effect；仍未标记完成的
V1 parity 为 pipeline handler order/failure isolation runtime、完整 JGraphT raw/raw cluster
和 K8S live smoke。

## 2026-06-09 B8 cover R2 promote side effect 第一刀

已新增并完成：

```text
tasks/in-progress/028-16-b8-cover-r2-promote-side-effect.md
```

B8 第一刀新增 `QueueTopic.PROMOTE_COVER_R2`，由 aggregation-worker 在 B7 cover pipeline
选中 `LOCAL` + `LOCAL_STORED` cover 时发布 UUID command；storage-service 独立消费该
command，在 R2 active 且 cover 仍符合 `LOCAL` + `LOCAL_STORED` 且 `next_retry_time is null`
时设置 `next_retry_time = now()` 与 `last_error = null`。该语义贴合 V1
`CoverImageServiceImpl.promoteToR2()`：只唤醒/安排上传，不在 aggregation-worker 内执行
真实 R2 upload。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-aggregation-worker:test :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL
```

后续 B9-B14 已承接 JGraphT/context/scoring/ES recall 和 pipeline runtime 第一刀；当前仍未
标记完成的 V1 parity：K8S live smoke。

## 2026-06-09 B9 JGraphT raw/raw cluster 第一刀

已新增：

```text
tasks/in-progress/028-16-b9-jgrapht-raw-cluster-first-slice.md
```

B9 第一刀承接 V1 `GraphClusteringEngine` 的 raw/raw cluster 核心行为：同一批 claimed raw
先通过 JGraphT 建图、连通分量和 star peeling 成簇，再复用 B1-B8 已验证的 unified
创建/复用、duplicate merge、pipeline rebuild、search sync 和 cover R2 promote side effect。
本切片不覆盖完整 context unified 混合图、完整 actor/director metadata scoring、handler
runtime 和 K8S live smoke；后续 B10-B14 已分别承接 context graph、category bucket、
metadata scoring、ES-backed context recall 和 pipeline runtime 第一刀。

实现结果：

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/RawVideoGraphClusterer.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/RawVideoAggregationCluster.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/RawVideoGraphClustererTest.java
```

B9 使用 service-scoped `org.jgrapht:jgrapht-core:1.5.2`，raw/raw graph 边阈值 `0.85`，
star peeling 一致性阈值 `0.90`；有效年份差 `<= 1` 可比较，未知年份只和未知年份比较。
`aggregateBatch()` 对 multi-raw cluster 创建或复用一个 unified，将 cluster 内 raw 全部 bind
到该 unified，并只在所有 bind 后运行一次 pipeline rebuild 与 cover R2 promote side effect。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

后续 B10-B14 已分别承接 raw/unified 混合图、category bucket、metadata scoring、
ES-backed context recall 和 pipeline runtime 第一刀；当前仍未标记完成的 V1 parity：
K8S live smoke。

## 2026-06-09 B10 raw/unified context graph 第一刀

已新增：

```text
tasks/in-progress/028-16-b10-raw-unified-context-graph-first-slice.md
```

B10 第一刀承接 V1 `GraphAggregationServiceImpl.loadContextAccelerated()` 与
`GraphClusteringEngine.cluster(sourceCandidates, contextCandidates)` 的 mixed graph 语义：
在 B9 raw/raw graph 基础上，将 repository 召回的 context unified candidates 作为 graph node
参与 raw/unified edge、connected component 和 star peeling。纯 unified component 不输出
cluster；含 unified 的 raw cluster 优先复用 unified，并通过现有 B2-B4 merge 处理多个 unified
victims。

B10 已完成第一刀：`findContextUnifiedCandidates()` 按 V1 只用 douban/tmdb 做 context
external recall，缺少有效 douban/tmdb 时才退到 title/year recall；graph clusterer 已支持
raw/unified mixed graph，unified/unified 不建边，纯 unified component 跳过；service 层可复用
单个 context unified 或 merge 多个 context unified victims。普通 matching 阶段仍保留
imdb/rottenTomatoes 的 match/conflict 判断。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

后续 B11/B12/B13/B14 已分别承接 category bucket、metadata scoring/penalty、ES-backed
context recall 与 pipeline runtime 第一刀；当前仍未标记完成的 V1 parity：K8S live smoke。

## 2026-06-09 B11 category bucket graph 第一刀

已新增并完成：

```text
tasks/in-progress/028-16-b11-category-bucket-graph-first-slice.md
```

B11 第一刀在 B9/B10 graph 基础上补回 V1 `GraphClusteringEngine.buildGraphOptimized()`
的第一层 category bucket：raw candidate 从 `raw_category -> standard_category` 暴露标准
分类名，unified candidate 从 `unified_videos.category_id -> standard_category` 暴露标准
分类名；graph 建边前按 `categoryName.trim()` 分桶，空/blank 分类归入
`UNKNOWN_CATEGORY`。raw/raw 和 raw/unified edge 均不会跨具体分类，`UNKNOWN_CATEGORY`
只和 `UNKNOWN_CATEGORY` 比较。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

后续 B12/B13/B14 已承接 actor/director Jaccard、episode/area penalty、ES-backed context
recall 与 pipeline runtime 第一刀；当前仍未标记完成的 V1 parity：K8S live smoke。

## 2026-06-09 B12 graph scoring metadata penalty 第一刀

已新增并完成：

```text
tasks/in-progress/028-16-b12-graph-scoring-metadata-penalty-first-slice.md
```

B12 第一刀在 B9-B11 graph 基础上补回 V1 `DefaultWeightedMatchingStrategy` 的 metadata
scoring 与 penalty：candidate 主体查询后按 V1 `populateMetadata()` 语义补
actor/director/standard area names；无 metadata 时保持 `titleScore * 0.95`，双方都有
metadata 时使用 `titleScore * 0.70 + actor/director Jaccard * 0.30`，episode 不兼容扣
`0.15`，双方 area 都存在且不 contains 匹配扣 `0.10`。同标题但 actor/director 不重叠、
episode 不兼容或 raw/unified context metadata 不一致时，不再误建 graph edge。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

后续 B13 已承接真实 ES-backed context recall 第一刀，B14 已承接 pipeline handler
order/failure isolation runtime 第一刀；仍未标记完成的 V1 parity：K8S live smoke。

## 2026-06-09 B13 ES-backed context recall 第一刀

已新增并完成：

```text
tasks/in-progress/028-16-b13-es-backed-context-recall-first-slice.md
```

B13 第一刀在 B10-B12 graph 基础上补回 V1 `SearchQueryService.findCandidateUnifiedVideoIds()`
的 ES-backed context recall。search-service 新增内部只读
`GET /internal/v1/search/unified-context-candidates`，只返回 UUID 列表；query 复刻 V1
candidate 语义：title sanitize 128、`multi_match best_fields`、`AND`、
`minimum_should_match 90%`、`title/normalizedTitle^5`、`alias/variants^3`、
`externalIds^8`、year `+-2` range 和 limit 20。

aggregation-worker 新增 `AggregationContextSearchClient`，默认通过 internal HTTP 调用
search-service。`findContextUnifiedCandidates()` 保持 V1 边界：有有效 douban/tmdb 的 raw
只走 DB external recall；无有效 douban/tmdb 的 raw 才走 ES title/year recall。ES 返回的
UUID 会回 PostgreSQL 装载 `UnifiedVideoAggregationCandidate` 并补 metadata 后再进入 graph；
ES 空结果或异常不会触发 SQL title fallback，只有显式关闭 ES recall 时才使用旧 SQL fallback
作为开发兜底。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-search-service:test :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

仍未标记完成的 V1 parity：K8S live smoke。

## 2026-06-09 B14 pipeline runtime order/failure isolation 第一刀

已新增并完成：

```text
tasks/in-progress/028-16-b14-pipeline-runtime-order-failure-isolation.md
```

B14 第一刀把 B5-B8 已完成的 SQL rebuild 与 cover R2 promote 侧效应收束为显式
`AggregationPipelineRuntime`。runtime 按 V1 `AggregationHandler.getOrder()` 顺序执行：
`BasicAttributeHandler(10)`、`ExternalIdHandler(20)`、`MetadataHandler(30)`、
`DynamicFieldHandler(40)`、`CategoryHandler(50)`、`CoverImagePipelineHandler(60)`。

`JdbcAggregationRepository` 已拆出 V1 handler 粒度 rebuild 方法，同时保留旧的
`rebuildUnifiedPipelineScalars()` 与 `rebuildUnifiedPipelineRelations()` 作为兼容包装。
runtime 对每个 stage 独立捕获异常并继续后续 stage，记录 `AggregationPipelineStageFailure`；
cover stage 成功时才返回待发布的 R2 promote cover IDs。`AggregationService` 的 single raw
与 multi-raw cluster bind 流程均已切换到 runtime，再由 service 发布
`PROMOTE_COVER_R2` storage command。

2026-06-09 继续补强 B14：每个 stage 已通过 `TransactionTemplate` +
`PROPAGATION_NESTED` 运行在独立 JDBC nested savepoint 中，避免 PostgreSQL 某个 stage
SQL 失败后外层事务进入 failed transaction block。该补丁不使用 `REQUIRES_NEW`，符合 dev
环境低连接池资源约束。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-search-service:test :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

测试证据：

```text
AggregationPipelineRuntimeTest: tests=3, failures=0, errors=0
aggregation-worker test suites: tests=68, failures=0, errors=0
search-service test suites: tests=19, failures=0, errors=0
```

仍未标记完成的 V1 parity：K8S live smoke。

## 2026-06-09 B4 victim scalar merge 第一刀

已新增并完成：

```text
tasks/in-progress/028-16-b4-victim-scalar-merge.md
```

B4 第一刀在删除 victim 前按 V1 `UnifiedVideo.merge(victim)` 语义补齐 root 缺失的 victim
标量字段：external IDs、description、score、published_at、alias_title、total_episodes、
duration、remarks、season、subtitle；root 已有非空值不被覆盖，`last_trend_at` 取更新值。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

仍未标记完成的 V1 parity：raw sources -> unified 的完整 `AggregationPipeline`，包括
`locked_fields`、category 投票、dynamic fields、cover image 择优覆盖/R2 promote；完整
JGraphT raw/raw cluster 和 K8S live smoke 也继续保留。
