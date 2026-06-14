# 109 028-20 distributed lock manager multi-service rollout

## 结论

本轮把分布式锁从 `ops-service` 第一刀扩展为全服务可复用治理层，并按业务行为选择锁类型：

后续修正：`docs/reports/110-02820-fine-grained-lock-audit.md` 已复核本报告中的部分 singleton 判断；`normalization-worker` LLM cleaning、retry watchdog、`search-service` outbox relay、`aggregation-worker` batch scheduler、`storage-service` R2 watchdog 已从外层 mutex 下沉到 DB row claim/lease，允许多副本处理不同批次。`aggregation-worker` 额外补了 `AGGREGATION_MATCH` match-key 细粒度 mutex，用于避免相似 raw 并发创建重复 unified，不恢复全局 scheduler lock。

观测增强：`028-20-22` 已在 shared distributed lock 层补统一 Micrometer 与 Actuator health。所有使用 `DistributedLockManager` 的服务会记录 mutex / time-slice / token-bucket 的 acquire/reserve/renew/release 结果与耗时；health component 通过 Redis PING 暴露 lock backend UP/DOWN，Redis bean 不可用时明确 DOWN。metrics 不包含具体 lock key、owner 或实体 ID，避免高基数标签污染。

- `MUTEX_LEASE`：singleton runner、启动 seed、watchdog、recovery、cleanup、reconciliation、outbox relay 等跨副本只能由一个实例执行的入口。
- `TIME_SLICE_RESERVATION`：源站、provider 抓取前的时间片预占用限流。
- `TOKEN_BUCKET`：凭据/API 配额型调用限流。

Redis/Valkey 是分布式协调层；DB 幂等、唯一约束、row lease、消息 ack 和业务状态机仍是最终兜底。

## 覆盖范围

已接入或确认覆盖：

- shared common：`DistributedLockManager`、`DistributedLockService`、`RedisDistributedLockManager`、`DistributedLockProfiles`、Redis mutex lease、时间片预占用、令牌桶、统一 lock metrics 和 `distributedLock` health component。
- ops-service：maintenance scheduler cluster lease，已在上一切片完成。
- metadata-worker：TMDB credential/API token bucket，已在前序 provider parity 中完成。
- scraper-service：`ListScraperClient` 对每个 `DataSource` 出站抓取前执行时间片预占用。
- magnet-service：YTS_BZ provider 抓取前执行 provider 级时间片预占用。
- task-service：default collection task seed、startup recovery、watchdog 统一互斥。
- search-service：初始接入 outbox relay、reconciliation runner 入口互斥；后续 outbox relay 已下沉到 DB row lease，reconciliation 仍保留互斥。
- aggregation-worker：初始接入 scheduler batch 入口互斥；后续 batch scheduler 已下沉到 DB row claim。
- normalization-worker：初始接入 LLM cleaning runner、retry watchdog 入口互斥；后续两者均已下沉到 DB row claim/lease。
- config-service：SystemConfig default initializer 入口互斥。
- catalog-service：catalog default seed 入口互斥。
- content-service：default resolver preset seed 入口互斥。
- credential-service：runtime credential initializer 入口互斥。
- storage-service：初始接入 R2 watchdog 入口互斥；后续已下沉到 avatar/cover claim-first。
- notification-worker：mail send history cleanup runner 入口互斥。

## 非机械加锁边界

- RabbitMQ `@RabbitListener` 不做全局 Redis mutex。RabbitMQ 已提供 competing consumer 分发；全局锁会把队列吞吐压成单实例。这里继续依赖 ack、幂等写、唯一约束和状态机。
- `CollectionTaskRunnerService` 不做 per-task 全局 Redis 锁；任务领取已经依赖 DB 原子状态迁移，Redis 只包 scheduler/watchdog/startup/seed 入口。
- HanLP prewarm 不加锁；它是本地进程模型预热，没有共享写副作用。
- Search outbox 仍保留 DB `locked_by/locked_until` row lease，Redis 只作为 batch loop 的跨副本入口护栏。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --tests "*ScraperTrafficLimiterTest" --tests "*ListScraperClientTest" --tests "*ManualScraperServiceTaskExecutionTest" :services:ircs-scraper-service:bootJar
.\gradlew.bat --no-daemon :services:ircs-task-service:test --tests "*TaskDistributedLockRunnerTest" --tests "*DefaultCollectionTaskSeederTest" --tests "*TaskWatchdogTest" --tests "*TaskStartupRecoveryTest" :services:ircs-task-service:bootJar
.\gradlew.bat --no-daemon :services:ircs-search-service:test --tests "*SearchOutboxRelayTest" --tests "*SearchReconciliationRunnerTest" --tests "*SearchOutboxRelayHealthIndicatorTest" :services:ircs-search-service:bootJar
.\gradlew.bat --no-daemon :services:ircs-magnet-service:test --tests "*MagnetProviderTrafficLimiterTest" --tests "*YtsBzMagnetProviderSearchRunnerTest" --tests "*GatedMagnetProviderSearchRunnerTest" --tests "*MagnetQueryServiceTest" :services:ircs-magnet-service:bootJar
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --tests "*AggregationSchedulerTest" :services:ircs-aggregation-worker:bootJar
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*NormalizationRetryWatchdogTest" --tests "*LlmCleaningRunnerTest" :services:ircs-normalization-worker:bootJar
.\gradlew.bat --no-daemon :services:ircs-config-service:test --tests "*SystemConfigInitializerRunnerTest" :services:ircs-config-service:bootJar
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --tests "*CatalogDefaultInitializerRunnerTest" :services:ircs-catalog-service:bootJar
.\gradlew.bat --no-daemon :services:ircs-credential-service:test --tests "*RuntimeCredentialInitializerTest" :services:ircs-credential-service:bootJar
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --tests "*StorageR2WatchdogTest" :services:ircs-storage-service:bootJar
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --tests "*MailSendHistoryCleanupRunnerTest" --tests "*JdbcMailSendHistoryCleanupServiceTest" :services:ircs-notification-worker:bootJar
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*DistributedLock*Test" --tests "*RedisDistributedLockManager*Test" --tests "*RedisClusterLeaseServiceTest"
.\gradlew.bat --no-daemon :services:ircs-scraper-service:bootJar :services:ircs-magnet-service:bootJar :services:ircs-ops-service:bootJar :services:ircs-aggregation-worker:bootJar :services:ircs-search-service:bootJar :services:ircs-normalization-worker:bootJar
```

结果均为 `BUILD SUCCESSFUL`。

## 遗留边界

- 本轮未执行 K8S 多副本 live lock smoke；统一测试窗口需验证 Redis key、跨副本 skipped 行为、`/actuator/health` 的 `distributedLock` component、Prometheus `ircs.distributed.lock.*` 指标和 audit 可见性。
- 当前 lock TTL 默认 10 分钟，多数入口是短 batch/seed/watchdog；若后续真实 runner 超过 TTL，需要补 renewal 或按 runner 独立拉长 TTL。
- scraper 现在以 `fixedDelayMs` 作为抓取前时间片 gap，同时原有抓取后 delay 仍保留，手动抓取可能偏保守；生产前可按源站策略调小。
- Redis lock fail closed 是刻意选择：分布式系统里高风险冲突入口宁可 skipped/failed，也不在协调层不可用时并发写共享资源。
