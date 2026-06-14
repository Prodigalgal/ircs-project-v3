# 028-16-B14 aggregation pipeline runtime order/failure isolation task spec

状态：完成第一刀，含 PostgreSQL savepoint 失败隔离

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- V1 `AggregationPipeline`
- V1 `AggregationHandler`
- V1 `BasicAttributeHandler`
- V1 `ExternalIdHandler`
- V1 `MetadataHandler`
- V1 `DynamicFieldHandler`
- V1 `CategoryHandler`
- V1 `CoverImagePipelineHandler`
- V3 `028-16-B5` raw-source relation rebuild
- V3 `028-16-B6` raw-source scalar rebuild
- V3 `028-16-B7` cover image choose rules
- V3 `028-16-B8` cover R2 promote side effect

## 背景

B5-B8 已分别补回 V1 `AggregationPipeline` 的关系、标量、封面择优和 R2 promote
侧效应，但 V3 `AggregationService` 仍直接按三段 repository 方法调用：

```text
rebuildUnifiedPipelineScalars()
rebuildUnifiedPipelineRelations()
rebuildUnifiedPipelineCoverImage()
```

这与 V1 的真实 runtime 契约仍有差异：

```text
BasicAttributeHandler(10)
ExternalIdHandler(20)
MetadataHandler(30)
DynamicFieldHandler(40)
CategoryHandler(50)
CoverImagePipelineHandler(60)
```

V1 `AggregationPipeline.execute()` 会按 `AggregationHandler.getOrder()` 排序执行，并且单个
handler 抛错只记录日志，不阻断后续 handler。本切片要把 V3 已有 SQL rebuild 能力收束成
同等可见、可测的 pipeline runtime，避免后续继续靠 service 中的隐式调用顺序维持 parity。

## 目标

- 新增 aggregation-worker 本地 `AggregationPipelineRuntime`，按 V1 handler 顺序执行。
- 将 `JdbcAggregationRepository` 的 pipeline rebuild 拆成 V1 handler 粒度方法：
  `BasicAttribute`、`ExternalId`、`Metadata`、`DynamicField`、`Category`、`CoverImage`。
- 保留旧 grouped 方法作为兼容包装，避免既有 repository tests 与后续调用点突然失效。
- runtime 对每个 stage 做失败隔离：记录失败 stage，继续执行后续 stage；JDBC/PostgreSQL
  场景必须使用 stage 粒度 savepoint，不能只在 Java 层 catch 异常。
- cover stage 成功时继续返回待 R2 promote 的 cover image IDs，由 service 发布
  `publishCoverR2Promote()`。
- `AggregationService` 仅通过 runtime 执行 pipeline，不再直接拼 repository rebuild 顺序。

## 非目标

- 不修改 B9-B13 已完成的 graph cluster、metadata scoring 和 ES context recall。
- 不重新设计 SQL 聚合规则；本切片只调整 runtime 顺序与失败隔离。
- 不新增公网 HTTPRoute。
- 不执行 K8S live smoke；真实 dev 集群验证保留给后续单独切片。
- 不实现 pipeline execution 持久化审计；本切片只返回本地 execution result 并记录日志。
- 不引入 `REQUIRES_NEW`；dev Hikari pool 较小，本切片使用同一个物理事务内的 nested savepoint。

## 输入

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationService.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/AggregationServiceTest.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepositoryTest.java
```

## 输出

- `AggregationPipelineRuntime`、stage enum 与 execution result。
- V1 handler 粒度的 repository rebuild 方法。
- `AggregationService` 切换为 runtime 调用。
- runtime 单元测试固定顺序、失败隔离和 cover promote 透传。
- service tests 更新为验证 runtime 侧效应发布，不再验证 repository 内部顺序。
- 文档、迁移记录和任务状态回写。

## 影响范围

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/**
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md
tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 依赖

- B5-B8 已存在 SQL rebuild 与 cover promote publisher。
- aggregation-worker 当前使用 Spring transaction 包裹 batch/one/cluster 绑定流程。
- repository grouped methods 的现有测试仍作为规则回归保护。

## 风险

- 拆分 grouped SQL 方法时若遗漏锁字段，将造成用户手工维护字段被自动覆盖。
- 失败隔离会让部分 stage 失败后仍标记 raw 为 BOUND；这是 V1 行为，但需要用 execution
  result 和日志保留定位线索。PostgreSQL 中若只 catch `DataAccessException` 而不 rollback
  到 savepoint，外层事务会进入 failed transaction block，后续 stage 与 `markBound()` 仍会失败。
- 若 cover stage 失败，不能发布错误的 R2 promote；runtime 必须只透传成功 stage 返回的
  promote IDs。
- K8S live smoke 未执行前，真实 DB/queue/ES residue 仍不能标记闭环。

## 验收标准

```text
B14 MARK 1/6 repository exposes V1 handler-grained pipeline rebuild methods
B14 MARK 2/6 pipeline runtime executes Basic->ExternalId->Metadata->Dynamic->Category->Cover order
B14 MARK 3/6 runtime catches one stage failure and continues later stages using JDBC nested savepoints
B14 MARK 4/6 cover stage promote IDs are returned only when cover stage succeeds
B14 MARK 5/6 AggregationService uses runtime for single raw and cluster binding side effects
B14 MARK 6/6 aggregation-worker tests pass and docs record K8S live smoke as remaining gap
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-search-service:test :services:ircs-aggregation-worker:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀。B14 将 V3 aggregation-worker 的 pipeline rebuild 从
`AggregationService` 中分散的 grouped repository 调用，收束为 `AggregationPipelineRuntime`：

- `JdbcAggregationRepository` 已拆出 V1 handler 粒度方法：
  `rebuildUnifiedPipelineBasicAttributes()`、`rebuildUnifiedPipelineExternalIds()`、
  `rebuildUnifiedPipelineMetadataRelations()`、`rebuildUnifiedPipelineDynamicFields()`、
  `rebuildUnifiedPipelineCategory()` 和 `rebuildUnifiedPipelineCoverImage()`。
- 旧的 `rebuildUnifiedPipelineScalars()` 与 `rebuildUnifiedPipelineRelations()` 保留为兼容包装，
  继续支撑既有 repository 测试和后续调用点。
- `AggregationPipelineRuntime` 按 V1 顺序执行：
  `BasicAttribute(10) -> ExternalId(20) -> Metadata(30) -> DynamicField(40) ->
  Category(50) -> CoverImage(60)`。
- runtime 对每个 stage 独立 `try/catch`，单 stage 失败会记录
  `AggregationPipelineStageFailure` 并继续后续 stage，复刻 V1
  `AggregationPipeline.execute()` 的失败隔离边界。
- cover stage 成功时返回 `coverR2PromoteImageIds`，`AggregationService` 继续发布
  `PROMOTE_COVER_R2` storage command；cover stage 失败时返回空 promote list。
- `AggregationService` 的 single raw 和 multi-raw cluster bind 流程均已切换为 runtime 调用。

验收结果：

```text
B14 MARK 1/6 repository exposes V1 handler-grained pipeline rebuild methods: done 2026-06-09
B14 MARK 2/6 pipeline runtime executes Basic->ExternalId->Metadata->Dynamic->Category->Cover order: done 2026-06-09
B14 MARK 3/6 runtime catches one stage failure and continues later stages using JDBC nested savepoints: done 2026-06-09
B14 MARK 4/6 cover stage promote IDs are returned only when cover stage succeeds: done 2026-06-09
B14 MARK 5/6 AggregationService uses runtime for single raw and cluster binding side effects: done 2026-06-09
B14 MARK 6/6 aggregation-worker tests pass and docs record K8S live smoke as remaining gap: done 2026-06-09
```

验证：

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-search-service:test :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

测试证据：

```text
AggregationPipelineRuntimeTest: tests=3, failures=0, errors=0
AggregationServiceTest: tests=9, failures=0, errors=0
aggregation-worker test suites: tests=68, failures=0, errors=0
search-service test suites: tests=19, failures=0, errors=0
```

仍未完成：

- B14 尚未执行 K8S live smoke；真实 dev DB/ES/queue residue、cover promote command 和
  clustered raw pipeline execution 仍需后续环境验证。

2026-06-09：复核发现 B14 第一刀的“失败隔离”只在 Java 层 `try/catch`，不足以覆盖真实
PostgreSQL 事务语义。`AggregationService.aggregateBatch()` 外层是 `@Transactional`，
如果某个 stage 的 JDBC SQL 抛出 `DataAccessException`，同一个物理事务可能进入 failed
transaction block；后续 stage 即使被 Java 调用，也会因事务未 rollback 到 savepoint 而继续失败。

本轮补充范围：

- `AggregationPipelineRuntime` 注入 `PlatformTransactionManager`。
- 每个 stage 使用 `TransactionTemplate` + `PROPAGATION_NESTED` 执行，失败时由 Spring 回滚到
  savepoint，再由 runtime 在 callback 外 catch 并记录 stage failure。
- 单元测试补充 stage transaction 调用次数，固定“每个 handler 都进入 stage transaction”的契约。

验收补充：

```text
B14 MARK 3/6 PostgreSQL/JDBC stage failures rollback to nested savepoint before continuing: done 2026-06-09
```

实现结果：

- `AggregationPipelineRuntime` 已注入 `PlatformTransactionManager`，通过
  `TransactionTemplate` + `PROPAGATION_NESTED` 为每个 stage 建立独立 savepoint。
- stage callback 内不吞异常；异常先交给 Spring 回滚到 savepoint，再由 runtime 在 callback
  外记录 `AggregationPipelineStageFailure` 并继续后续 stage。
- 没有使用 `REQUIRES_NEW`，不会在 dev 环境 Hikari 小连接池里额外占连接。
- `AggregationPipelineRuntimeTest` 已补 `RecordingTransactionOperations`，断言 6 个 V1
  handler stage 均通过 stage transaction boundary。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-search-service:test :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

AggregationPipelineRuntimeTest: tests=3, failures=0, errors=0
AggregationServiceTest: tests=9, failures=0, errors=0
```
