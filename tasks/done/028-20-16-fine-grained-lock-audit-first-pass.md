# 028-20-16 fine-grained lock audit first pass

## 目标

排查 distributed lock rollout 中是否存在可以按业务对象、批次或行级 claim 拆分的全局粗锁，并先修复不需要新业务决策的吞吐型粗锁。

## 已完成

- LLM cleaning 从全局 mutex 改为 DB row claim lease：
  - 新增 raw dictionary/category claim 字段迁移。
  - `claimCandidates` 使用 `FOR UPDATE SKIP LOCKED`。
  - HTTP internal API 和 runner 不再使用 `normalization:llm-cleaning` 全局锁。
- Search outbox relay 移除 `search:outbox-relay` 外层 mutex，继续依赖 `search_sync_tasks` row lease。
- Aggregation scheduler 移除 `aggregation:batch` 外层 mutex，`resetStuckProcessing` 补 `FOR UPDATE SKIP LOCKED`。
- 完成全服务锁使用点分类，报告见 `docs/reports/110-02820-fine-grained-lock-audit.md`。
- 后续 `028-20-17/18/19/20/21` 已继续完成 storage R2 watchdog claim-first、normalization retry watchdog claim-first、aggregation dead config cleanup、aggregation match-key 细粒度锁和 DB claim 释放语义补强。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*LlmCleaningServiceTest" --tests "*LlmCleaningRepositoryTest" --tests "*LlmCleaningInternalControllerTest" --tests "*LlmCleaningRunnerTest"
.\gradlew.bat --no-daemon :services:ircs-search-service:test --tests "*SearchOutboxRelayTest" :services:ircs-aggregation-worker:test --tests "*AggregationSchedulerTest" --tests "*JdbcAggregationRepositoryTest" :services:ircs-normalization-worker:test --tests "*LlmCleaning*Test" :services:ircs-search-service:bootJar :services:ircs-aggregation-worker:bootJar :services:ircs-normalization-worker:bootJar :platform:ircs-migrator:processResources
```

结果均为 `BUILD SUCCESSFUL`。

## 后续

- K8S 多副本 contention smoke：统一测试窗口验证 search/aggregation/LLM cleaning/storage 多副本 claim 不重复处理。
