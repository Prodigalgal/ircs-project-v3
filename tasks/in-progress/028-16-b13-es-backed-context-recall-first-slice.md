# 028-16-B13 ES-backed context recall first slice task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- V1 `GraphAggregationServiceImpl.loadContextAccelerated()`
- V1 `SearchQueryService.findCandidateUnifiedVideoIds()`
- V1 `PortalSearchQueryFactory.buildCandidateQuery()`
- V3 `028-16-B10` raw/unified context graph
- V3 `028-16-B11` category bucket graph
- V3 `028-16-B12` graph scoring metadata penalty

## 背景

B10 已把 context unified node 接入 raw/unified mixed graph，但 title/year context recall
仍是 aggregation-worker 本地 PostgreSQL similarity fallback。V1 的真实边界不是 SQL
title fallback，而是：

```text
raw 有有效 doubanId/tmdbId -> PostgreSQL 精确查 UnifiedVideo.id
raw 没有有效 doubanId/tmdbId 且有 title -> SearchQueryService 走 ES candidate query
ES 只返回 UnifiedVideo.id，最终仍回 PostgreSQL 装载 AggregationCandidate
```

因此 B13 第一刀要把 V3 的 context title recall 改成 search-service owned ES recall，同时
不重写 B10-B12 已完成的 mixed graph、category bucket、metadata scoring、pipeline 和
search sync。

## 目标

- search-service 新增内部只读 recall endpoint，返回 V1 `findCandidateUnifiedVideoIds()`
  等价的 UUID 列表。
- ES candidate query 对齐 V1：title sanitize 128、`multi_match best_fields`、`AND`、
  `minimum_should_match 90%`、字段权重和 year `+-2` range、limit 20。
- aggregation-worker 对无有效 douban/tmdb 的 raw 先调用 search-service recall，再按 ID
  回 PostgreSQL 装载 `UnifiedVideoAggregationCandidate`。
- 保持有有效 douban/tmdb 的 raw 只走 DB external-id context recall，不再走 title fallback。
- 保持 search-service/ES 不可用时 aggregation 不失败；默认业务结果降级为空 context，只有
  显式关闭 ES recall 时才使用旧 SQL title fallback 作为开发兜底。

## 非目标

- 不让 aggregation-worker 直接连接 ES。
- 不把 ES score、完整 document 或分页语义暴露给 aggregation。
- 不新增公网 HTTPRoute。
- 不实现完整 internal token policy；本切片只沿用集群内部路径，安全策略后续独立拆分。
- 不执行 K8S live smoke；本切片只做本地 service tests。

## 输入

```text
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/PortalSearchQueryService.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/document/UnifiedVideoSearchDocument.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/RawVideoGraphClusterer.java
```

## 输出

- search-service internal recall controller。
- `PortalSearchQueryService.findCandidateUnifiedVideoIds(title, year)`。
- aggregation-worker `AggregationContextSearchClient` + RestClient 实现。
- `JdbcAggregationRepository.findContextUnifiedCandidates()` 接入 ES-first UUID recall，并回表装载。
- search-service 和 aggregation-worker 单元测试固定 V1 边界。
- 文档、迁移记录和任务索引回写。

## 影响范围

```text
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/**
services/ircs-search-service/src/test/java/com/prodigalgal/ircs/search/**
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/**
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/**
services/ircs-aggregation-worker/src/main/resources/application.yaml
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 依赖

- search-service 拥有 ES starter、index document 和 search sync 消费者。
- aggregation-worker 保持 JDBC + internal HTTP，不新增 ES dependency。
- B10-B12 已提供 mixed graph、category bucket、metadata scoring 和回表后 context candidate
  参与聚类的能力。

## 风险

- ES 最终一致性可能导致刚写入 DB 的 unified 尚未召回；本切片默认按 V1 语义 fail-open
  为无 context，不阻塞聚合。
- 若把 ES 空结果再退到 SQL title fallback，会比 V1 更激进地合并；本切片用测试阻断。
- 内部 endpoint 暂无专用 token，后续 internal service policy 需要单独补。
- K8S live smoke 还未执行，真实 ES index 与 queue residue 仍需后续验证。

## 验收标准

```text
B13 MARK 1/6 search-service exposes internal UUID-only context recall API
B13 MARK 2/6 search candidate query matches V1 multi_match/year/limit semantics
B13 MARK 3/6 aggregation-worker calls ES recall only for raw without valid douban/tmdb
B13 MARK 4/6 ES recall IDs are loaded back from PostgreSQL before graph clustering
B13 MARK 5/6 ES empty/error does not trigger SQL title fallback unless recall is explicitly disabled
B13 MARK 6/6 search-service and aggregation-worker tests pass and docs record remaining runtime/live gaps
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-search-service:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-search-service:test :services:ircs-aggregation-worker:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀。B13 在 search-service 暴露内部只读 UUID recall API，并让
aggregation-worker 的 context title recall 改为 search-service owned ES recall：

- `PortalSearchQueryService.findCandidateUnifiedVideoIds(title, year)` 已复刻 V1
  `PortalSearchQueryFactory.buildCandidateQuery()` 的 candidate query：title sanitize 128、
  `multi_match best_fields`、`AND`、`minimum_should_match 90%`、字段权重、
  year `+-2` range 和 limit 20。
- `InternalSearchRecallController` 新增
  `GET /internal/v1/search/unified-context-candidates`，只返回 `List<UUID>`，不暴露 ES score、
  document 或分页模型。
- aggregation-worker 新增 `AggregationContextSearchClient` 与
  `RestAggregationContextSearchClient`，默认指向 `http://ircs-search-service:8080`，可通过
  `APP_AGGREGATION_CONTEXT_SEARCH_ENABLED=false` 显式关闭。
- `JdbcAggregationRepository.findContextUnifiedCandidates()` 保持 V1 边界：有有效
  douban/tmdb 的 raw 仍只走 DB external recall；无有效 douban/tmdb 时才调用 ES recall。
  ES 返回的 UUID 会回 PostgreSQL 装载 `UnifiedVideoAggregationCandidate` 并补 metadata 后
  才进入 graph。
- ES 返回空或 search-service/ES 异常时，默认按 V1 fail-open 为无 context，不触发旧 SQL
  title fallback；只有 client 显式 `notAttempted()`，例如关闭 ES recall，才使用旧 SQL
  fallback 作为开发兜底。

验收结果：

```text
B13 MARK 1/6 search-service exposes internal UUID-only context recall API: done 2026-06-09
B13 MARK 2/6 search candidate query matches V1 multi_match/year/limit semantics: done 2026-06-09
B13 MARK 3/6 aggregation-worker calls ES recall only for raw without valid douban/tmdb: done 2026-06-09
B13 MARK 4/6 ES recall IDs are loaded back from PostgreSQL before graph clustering: done 2026-06-09
B13 MARK 5/6 ES empty/error does not trigger SQL title fallback unless recall is explicitly disabled: done 2026-06-09
B13 MARK 6/6 search-service and aggregation-worker tests pass and docs record remaining runtime/live gaps: done 2026-06-09
```

验证：

```powershell
.\gradlew.bat --no-daemon :services:ircs-search-service:test :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

测试证据：

```text
InternalSearchRecallControllerTest: tests=1, failures=0, errors=0
PortalSearchQueryServiceTest: tests=6, failures=0, errors=0
search-service test suites: tests=19, failures=0, errors=0
JdbcAggregationRepositoryTest: tests=22, failures=0, errors=0
RestAggregationContextSearchClientTest: tests=3, failures=0, errors=0
aggregation-worker test suites: tests=65, failures=0, errors=0
```

仍未完成：

- B13 尚未执行 K8S live smoke；真实 ES index、search sync 最终一致性和 queue residue 仍需
  dev 环境验证。
- pipeline handler order/failure isolation runtime 仍由后续 B14 或同级切片承接。
