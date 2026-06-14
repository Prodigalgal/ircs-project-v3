# 028-16-B2 duplicate unified merge victim delete task spec

状态：第一刀已完成；暂留 in-progress，等待 B3/B4 relation union、JGraphT raw/raw cluster 和 live smoke 闭环

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`

上游来源：

- `docs/reports/088-v1-v3-deep-parity-gap-audit.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `GraphAggregationServiceImpl`
- V1 `UnifiedVideo.merge(victim)`
- V1 `InteractionRepositoryCustomImpl`
- V1 `SearchSyncServiceImpl`
- V3 `AggregationService`
- V3 `JdbcAggregationRepository`
- V3 `SearchSyncMessage`

## 背景

`028-16-B1` 已让 aggregation-worker 在无 external ID、标题相近、season/version 阻断和
external ID 冲突场景下减少新建重复 unified 的风险。但 V1 在 cluster 已经含多个
`UNIFIED_VIDEO` 时，会选择 root、合并/删除 victim，并触发 victim unified 的 search
DELETE side effect。V3 当前 matching 结果只返回一个 best UUID，无法收敛历史重复 unified。

只读盘点确认：V1 `GraphAggregationServiceImpl` 实际有 `migrateInteractions(victim, victim)`
疑似参数错误。V3 本切片按目标语义实现 `victim -> root`，并用测试固定，不照抄该错误。

## 目标

- 在 aggregation-worker 中识别同一 raw 命中的多个 duplicate unified candidates。
- 建立 root/victim merge plan，先选择稳定 root，再将 victims 合并/删除。
- 迁移 victim raw binding 到 root，并去重。
- 迁移 `member_favorites`、`member_watch_histories` 中 victim -> root；已有 root 冲突时删除
  victim 冲突行。
- 删除 victim unified 及其本切片可控依赖关系，但不把 raw 退回 `PENDING`。
- aggregation publisher 支持结构化 `SearchSyncMessage`，至少可发布 victim unified DELETE。
- 保留 root unified INDEX、raw INDEX，并补 victim DELETE 发布顺序测试。

## 非目标

- 不在本切片接完整 JGraphT raw/raw cluster。
- 不做 category/genre/area/language/person/cover image 全量 relation union；拆给 B3。
- 不改 content-service 人工 delete unified 语义，避免把 raw 误退回 `PENDING`。
- 不执行 K8S live smoke；先以本地 repository/service/publisher 测试闭环。

## 输入

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationService.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationSearchSyncPublisher.java
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/search/SearchSyncMessage.java
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/sync/JdbcSearchSyncProcessor.java
```

## 输出

- duplicate unified merge plan/result 模型或等价内部结构。
- repository merge primitives：binding migration、interaction migration、victim relation cleanup、
  victim unified delete。
- publisher 结构化 DELETE 发送能力。
- aggregation-worker 本地 tests。
- 本任务与 089 总账更新。

## 影响范围

```text
services/ircs-aggregation-worker/**
shared/ircs-contracts/** if SearchSyncMessage helpers are needed
services/ircs-search-service/src/test/** only for existing DELETE regression if needed
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md
```

## 风险

- victim 删除牵涉多个表，必须用事务和本地 H2/Postgres 兼容测试固定。
- 交互迁移若未处理唯一约束冲突，会造成 favorite/history duplicate key。
- 搜索 DELETE 若仍发 legacy UUID 字符串，search-service 无法表达 DELETE。
- root/victim 选择如果不稳定，会导致重复运行结果不一致。

## 验收标准

```text
B2 MARK 1/6 matching result can expose root candidate plus duplicate victim candidates
B2 MARK 2/6 victim raw bindings migrate to root with conflict-safe dedupe
B2 MARK 3/6 member favorites/watch histories migrate victim -> root with conflict cleanup
B2 MARK 4/6 victim unified rows and scoped relations are deleted without reverting raw to PENDING
B2 MARK 5/6 aggregation publisher emits structured UNIFIED_VIDEO DELETE for victims
B2 MARK 6/6 aggregation-worker tests pass and document B3 relation-union leftovers
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成只读盘点并固定实现边界。本切片已完成第一刀代码实现和本地测试。

实现结果：

```text
B2 MARK 1/6 done 2026-06-09: `AggregationMatchPlan` 可暴露 root candidate 与 duplicate victim candidates。
B2 MARK 2/6 done 2026-06-09: `JdbcAggregationRepository.mergeDuplicateUnifiedVideos()` 迁移 victim raw bindings 到 root，并使用 conflict-safe dedupe。
B2 MARK 3/6 done 2026-06-09: `member_favorites`、`member_watch_histories` 支持 victim -> root 迁移；已有 root 行时先删除 victim 冲突行。
B2 MARK 4/6 done 2026-06-09: victim unified scoped relation 与 victim row 被删除，raw aggregation_status 不退回 `PENDING`。
B2 MARK 5/6 done 2026-06-09: `AggregationSearchSyncPublisher.publishUnifiedDelete(UUID)` 发送结构化 `UNIFIED_VIDEO DELETE`。
B2 MARK 6/6 done 2026-06-09: aggregation-worker/search-service 本地测试通过，B3 relation union leftovers 已记录。
```

主要代码输出：

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationMatchPlan.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationMatchingStrategy.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationService.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationSearchSyncPublisher.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/AggregationServiceTest.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepositoryTest.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/AggregationSearchSyncPublisherTest.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/AggregationMatchingStrategyTest.java
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
B3: category/genre/area/language/person/cover image 等 relation union。
B4: 完整 JGraphT raw/raw cluster、更多 V1 graph scoring 细节和 live smoke。
K8S: 本切片只跑本地单元/fixture 测试，尚未执行 dev namespace live smoke。
```
