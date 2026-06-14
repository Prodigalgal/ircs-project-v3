# 028-20-19 aggregation cluster-lock dead config cleanup

## 目标

确认 `aggregation-worker` 聚合行为不再存在服务级粗锁，并清理已经无生产调用者的 scheduler cluster-lock 配置，明确聚合按行级 claim / 批次并发执行。

## 已完成

- 复核 `AggregationScheduler`：仅保留单 Pod 内 `AtomicBoolean` 防重入，不做跨副本分布式 mutex。
- 复核 `AggregationService.aggregateBatch`：通过 `repository.claimPending(batchSize)` 领取批次。
- 复核 `JdbcAggregationRepository.claimPending`：`raw_videos` 使用 `FOR UPDATE SKIP LOCKED` 领取 `PENDING` 行并标记 `PROCESSING`。
- 复核 `JdbcAggregationRepository.resetStuckProcessing`：stuck recovery 同样使用 `FOR UPDATE SKIP LOCKED`。
- 删除 `services/ircs-aggregation-worker/src/main/resources/application.yaml` 中已无生产调用者的 `app.aggregation.scheduler.cluster-lock.*` 配置。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --tests "*AggregationSchedulerTest" --tests "*JdbcAggregationRepositoryTest" :services:ircs-aggregation-worker:bootJar
```

结果：`BUILD SUCCESSFUL`。

静态回扫：

```powershell
rg -n "cluster-lock|APP_AGGREGATION_SCHEDULER_CLUSTER_LOCK|DistributedLock|callWithLock|callExclusive|aggregation:batch" services\ircs-aggregation-worker\src -g "*.java" -g "*.yaml" -g "*.yml"
```

结果：无残留。

## 遗留

- 尚未做 K8S 多副本 contention live smoke；需要后续用两个 `aggregation-worker` 副本验证同一批 `raw_videos` 不会重复 claim。
- 当前并发粒度是 raw row / batch；后续如果发现同一候选 cluster 被不同批次同时归并到不同 `unified_videos`，需要补 per-match-key 或 per-unified 级协调，而不是恢复全局粗锁。
