# 028-20-20 aggregation match-key fine-grained lock

## 目标

在不恢复 `aggregation-worker` 服务级粗锁的前提下，补齐多副本按行/批次并发聚合时的内容身份冲突保护，避免两个副本同时为相似 raw 创建重复 `unified_videos`。

## 已完成

- `shared:ircs-common` 新增 `DistributedLockBusinessType.AGGREGATION_MATCH`，profile prefix 为 `lock:aggregation:match:`。
- 新增 `AggregationMatchKeys`：
  - external id：`douban` / `tmdb` / `imdb` / `rt`
  - title fallback：复用 V1/V3 聚合匹配语义中的 title noise、season、version、year 归一化思想
  - cluster：合并所有 raw member keys 和 context unified ids
- 新增 `AggregationMatchKeyLock`：
  - 按 match keys 排序获取多个 Redis mutex，避免锁顺序不稳定。
  - 锁持有到 Spring transaction `afterCompletion`，确保 DB commit/rollback 后再释放。
  - 未启用时可本地 noop；启用时 Redis 不可用按 fail-closed 抛错。
- `AggregationService`：
  - `bindRawVideo` 在 find/create/bind 前获取 raw match-key lock。
  - `bindRawCluster` 在 cluster bind/create 前获取 cluster match-key lock。
  - 拿不到锁时将 raw member 标回 `PENDING`，让下一轮 retry，不并发创建第二个 unified。
- 保持 `claimPending()` / `resetStuckProcessing()` 的 `FOR UPDATE SKIP LOCKED` 行级并发；没有恢复 `aggregation:batch` 全局粗锁。

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*DistributedLock*Test" :services:ircs-aggregation-worker:test --tests "*AggregationServiceTest" --tests "*AggregationMatchKeysTest" --tests "*AggregationMatchKeyLockTest" --tests "*AggregationSchedulerTest" --tests "*JdbcAggregationRepositoryTest" :services:ircs-aggregation-worker:bootJar
```

结果：`BUILD SUCCESSFUL`。

静态回扫：

```powershell
rg -n "AGGREGATION_MATCH|match-lock|AggregationMatchKey|aggregation:batch|cluster-lock|APP_AGGREGATION_SCHEDULER_CLUSTER_LOCK|callWithLock|DistributedLock" shared\ircs-common\src\main\java services\ircs-aggregation-worker\src -g "*.java" -g "*.yaml" -g "*.yml"
```

结果：只剩 `AGGREGATION_MATCH` / `match-lock` / `AggregationMatchKey*` 细粒度锁相关命中，无 `aggregation:batch` 或 scheduler cluster-lock 残留。

## 遗留

- 尚未执行 K8S 两副本 contention live smoke；需要构造同 external id / 同 title fingerprint raw 数据，确认一个副本处理，另一个副本回退 `PENDING`，最终只保留一个 unified。
- match-key 是防并发创建重复 unified 的协调层，不替代后续的重复 unified reconciliation / cleanup。
