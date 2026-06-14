# 110 028-20 fine-grained lock audit

## 结论

本轮复核了生产代码中的 Redis mutex、DB row claim、时间片预占用和令牌桶使用点。旧的 `028-20-15` rollout 把部分 batch/relay runner 先按 singleton runner 接入了 mutex；这对冲突安全有帮助，但对已经具备 DB 行级认领的吞吐链路过于保守。

已完成第一批拆分：

- `normalization-worker` LLM cleaning 不再用全局 `normalization:llm-cleaning` mutex 串行 HTTP/runner；新增 raw dictionary/category 行级 `llm_cleaning_claimed_by`、`llm_cleaning_claimed_until` lease，`claimCandidates` 使用 `FOR UPDATE SKIP LOCKED`，允许不同 kind、同 kind 不同候选批次并发。
- `normalization-worker` retry watchdog 不再用全局 `normalization:retry-watchdog` mutex；`raw_videos` due failed/stale active recovery 改为 repository claim-first 更新并 `returning rv.id`，允许多副本认领不同恢复批次。
- DB claim 释放语义已补一轮：LLM cleaning 在 provider timeout/error 时显式释放本批 claim；normalization retry watchdog 在 Rabbit publish 失败时只回滚未发布的 recovery claim 为 `FAILED + next_normalization_retry_time`，避免数据悬挂在 `PENDING`。
- `search-service` outbox relay 不再用外层 `search:outbox-relay` mutex；继续依赖 `search_sync_tasks.locked_by/locked_until` row lease claim，不同副本可 claim 不同 due tasks。
- `aggregation-worker` scheduler 不再用外层 `aggregation:batch` mutex；继续依赖 `claimPending()` 的 `FOR UPDATE SKIP LOCKED`，并把 stuck reset 也补成 `FOR UPDATE SKIP LOCKED`。后续已删除 `app.aggregation.scheduler.cluster-lock.*` 死配置，避免误判为仍有服务级粗锁。
- `aggregation-worker` 额外补了 match-key 细粒度 Redis mutex：锁粒度是 external id / title fingerprint / context unified id，不锁 scheduler 或整个 batch；拿不到同一 match-key 时把 raw 标回 `PENDING` 下一轮重试，避免多副本同时创建重复 unified。锁注册 transaction synchronization，释放推迟到 DB 事务完成之后。
- `storage-service` R2 watchdog 不再用外层 `storage:r2-watchdog` mutex；封面使用 `cover_images.status='UPLOADING'` claim，头像在 `members` 增加 `avatar_sync_claimed_by`、`avatar_sync_claimed_until` lease，允许多副本处理不同 avatar/cover 批次。

## 排查分类

| 分类 | 服务/入口 | 当前判断 | 处理结果 |
| --- | --- | --- | --- |
| 已拆粗锁 | normalization LLM cleaning runner/internal API | 全局锁会阻断不同 kind 和不同候选批次 | 已改为 DB claim lease |
| claim 释放 | normalization LLM cleaning provider failure | provider timeout/error 后不能长期占有 raw dictionary/category claim | timeout/error 显式 `releaseClaims`；如需防热循环，后续加独立 retry backoff |
| claim 释放 | normalization retry watchdog publish failure | claim-first 后 Rabbit publish 失败会导致 raw 悬挂 `PENDING` | 未发布 ID 恢复 `FAILED` 并写 `next_normalization_retry_time` |
| 已拆粗锁 | search outbox relay | 已有 DB row lease，外层 mutex 限制多副本吞吐 | 已移除外层 mutex |
| 已拆粗锁 | aggregation batch scheduler | 已有 raw_videos `PROCESSING` claim，外层 mutex 限制多副本吞吐 | 已移除外层 mutex，stuck reset 加 skip locked，清理 scheduler cluster-lock 死配置 |
| 细粒度互斥 | aggregation match-key bind/create | 行级 claim 只能避免同一 raw 重复领取，不能阻止相似 raw 在不同副本同时创建 unified | 新增 `AGGREGATION_MATCH` profile，按 external id/title fingerprint/context unified id 加细粒度 mutex |
| 已拆粗锁 | storage R2 watchdog | avatar/cover 已补 claim-first，外层 mutex 会限制多副本恢复吞吐 | 已移除外层 mutex；cover claim 为 `UPLOADING`，avatar claim 使用 TTL |
| 已拆粗锁 | normalization retry watchdog | `find ids -> markRecoveryQueued` 曾有二次确认且外层 mutex 限制多副本吞吐 | 已改为 due/stale claim-first，移除 normalization cluster-lock runner |
| 合理 singleton | config/catalog/content/credential initializer/seed | 初始化默认值、seed、runtime credential import，目标就是单实例兜底 | 保留 mutex |
| 合理 singleton | task startup recovery/default seed/watchdog | 全表维护和兜底恢复，不是正常吞吐链路 | 保留 mutex |
| 合理 singleton | notification mail send history cleanup | 维护清理任务，当前不追求多副本删除吞吐 | 保留 mutex |
| 合理 singleton | ops maintenance scheduler | ops 只是全局编排器，实际并发应在 owner service 内控制 | 保留 cluster lease |
| 合理 singleton | search reconciliation | 全量扫描/补 outbox，重复跑收益低且可能重复 enqueue | 保留 mutex |
| 已是细粒度 | scraper data source traffic limiter | 按 dataSourceId 时间片预占用 | 保持 |
| 已是细粒度 | magnet provider traffic limiter | 按 provider/id 时间片预占用 | 保持 |
| 已是细粒度 | metadata TMDB rate limiter | 按 credential token bucket | 保持 |

## 变更证据

新增/调整：

- `platform/ircs-migrator/src/main/resources/db/changelog/2026/07/add-llm-cleaning-claim-lease.sql`
- `services/ircs-normalization-worker/.../LlmCleaningRepository.java`
- `services/ircs-normalization-worker/.../LlmCleaningService.java`
- `services/ircs-normalization-worker/.../LlmCleaningInternalController.java`
- `services/ircs-normalization-worker/.../LlmCleaningRunner.java`
- `services/ircs-normalization-worker/.../RawVideoNormalizationRepository.java`
- `services/ircs-normalization-worker/.../NormalizationRetryWatchdog.java`
- `services/ircs-search-service/.../SearchOutboxRelay.java`
- `services/ircs-aggregation-worker/.../AggregationScheduler.java`
- `services/ircs-aggregation-worker/.../JdbcAggregationRepository.java`
- `shared/ircs-common/.../DistributedLockBusinessType.java`
- `shared/ircs-common/.../DistributedLockProfiles.java`
- `services/ircs-aggregation-worker/.../AggregationMatchKeyLock.java`
- `services/ircs-aggregation-worker/.../AggregationMatchKeys.java`
- `services/ircs-aggregation-worker/.../AggregationService.java`
- `services/ircs-aggregation-worker/src/main/resources/application.yaml`
- `platform/ircs-migrator/src/main/resources/db/changelog/2026/07/add-avatar-sync-claim-lease.sql`
- `services/ircs-storage-service/.../StorageR2Watchdog.java`
- `services/ircs-storage-service/.../AvatarSyncMemberRepository.java`
- `services/ircs-storage-service/.../CoverImageAdminRepository.java`
- `services/ircs-storage-service/.../CoverImageR2SyncService.java`
- `services/ircs-normalization-worker/src/main/resources/application.yaml`
- removed `services/ircs-normalization-worker/.../NormalizationDistributedLockRunner.java`

验证命令：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*LlmCleaningServiceTest" --tests "*LlmCleaningRepositoryTest" --tests "*LlmCleaningInternalControllerTest" --tests "*LlmCleaningRunnerTest"
.\gradlew.bat --no-daemon :services:ircs-search-service:test --tests "*SearchOutboxRelayTest" :services:ircs-aggregation-worker:test --tests "*AggregationSchedulerTest" --tests "*JdbcAggregationRepositoryTest" :services:ircs-normalization-worker:test --tests "*LlmCleaning*Test" :services:ircs-search-service:bootJar :services:ircs-aggregation-worker:bootJar :services:ircs-normalization-worker:bootJar :platform:ircs-migrator:processResources
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --tests "*StorageR2WatchdogTest" --tests "*AvatarSyncMemberRepositoryTest" --tests "*CoverImageAdminRepositoryTest" --tests "*CoverImageR2SyncServiceTest" :services:ircs-storage-service:bootJar :platform:ircs-migrator:processResources
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*NormalizationRetryWatchdogTest" --tests "*RawVideoNormalizationRepositoryTest" --tests "*LlmCleaningRunnerTest" :services:ircs-normalization-worker:bootJar
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --tests "*AggregationSchedulerTest" --tests "*JdbcAggregationRepositoryTest" :services:ircs-aggregation-worker:bootJar
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*DistributedLock*Test" :services:ircs-aggregation-worker:test --tests "*AggregationServiceTest" --tests "*AggregationMatchKeysTest" --tests "*AggregationMatchKeyLockTest" --tests "*AggregationSchedulerTest" --tests "*JdbcAggregationRepositoryTest" :services:ircs-aggregation-worker:bootJar
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*NormalizationRetryWatchdogTest" --tests "*RawVideoNormalizationRepositoryTest" --tests "*LlmCleaningServiceTest" --tests "*LlmCleaningRepositoryTest" :services:ircs-normalization-worker:bootJar
```

结果均为 `BUILD SUCCESSFUL`。

## 遗留风险

- 尚未执行 K8S 多副本 contention live smoke；需要在统一测试窗口验证多副本同时 claim 时 Redis/DB/metrics/audit 可见。
- LLM cleaning provider timeout/error 已显式释放 claim；如果后续真实 provider smoke 发现热循环，需要加独立 retry/backoff 字段，不能再用“不释放 claim”代替 backoff。
- storage R2 watchdog 已改 DB claim-first，但尚未跑真实 R2 多副本 contention smoke。
- normalization retry watchdog 已改 DB claim-first，但尚未跑真实多副本 contention smoke。
- aggregation match-key lock 已补代码和事务释放测试，但尚未跑 K8S 两副本同 title/external id contention live smoke；仍需验证 Redis key、raw 回退 `PENDING`、最终 unified 去重结果和 audit/metrics 可见性。
