# 028-20-18 normalization retry watchdog claim-first

## 目标

将 `normalization-worker` retry watchdog 从外层全局 mutex 下沉到 `raw_videos` 行级 claim，允许多副本并发恢复不同 due failed / stale active normalization 批次。

## 已完成

- `NormalizationRetryWatchdog` 移除外层 `normalization:retry-watchdog` Redis mutex，直接执行 claim-first batch。
- `RawVideoNormalizationRepository` 新增：
  - `claimDueFailedNormalizationRecoveryIds(now, limit)`
  - `claimStaleActiveNormalizationRecoveryIds(staleBefore, limit)`
- 两个 claim 入口均使用 `FOR UPDATE SKIP LOCKED`，先把候选行更新为 `PENDING` 并清空 `next_normalization_retry_time`，再 `returning rv.id` 给 watchdog republish。
- 后续补强：Rabbit publish 失败时，已成功发布的 ID 不动；当前失败 ID 和剩余未发布 ID 会恢复为 `FAILED` 并写入新的 `next_normalization_retry_time`，避免 claim-first 后悬挂 `PENDING`。
- 删除已无生产调用者的 `NormalizationDistributedLockRunner`。
- 删除 `app.normalization.cluster-lock.*` 死配置，避免后续误以为 normalization 仍依赖服务级粗锁。
- 保留旧 `find*` / `markRecoveryQueued` 入口用于兼容现有测试与后续清理窗口。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*NormalizationRetryWatchdogTest" --tests "*RawVideoNormalizationRepositoryTest" --tests "*LlmCleaningRunnerTest" :services:ircs-normalization-worker:bootJar
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*NormalizationRetryWatchdogTest" --tests "*RawVideoNormalizationRepositoryTest" --tests "*LlmCleaningServiceTest" --tests "*LlmCleaningRepositoryTest" :services:ircs-normalization-worker:bootJar
```

结果：`BUILD SUCCESSFUL`。

静态回扫：

```powershell
rg -n "normalization:retry-watchdog|NormalizationDistributedLockRunner|APP_NORMALIZATION_CLUSTER_LOCK|normalization\.cluster-lock|callExclusive\(" services\ircs-normalization-worker\src -g "*.java" -g "*.yaml" -g "*.yml"
```

结果：无生产残留。

## 遗留

- 尚未执行 K8S 多副本 contention live smoke；需要后续用两个 normalization-worker 副本验证同一批 due/stale raw video 不会重复 claim。
- 旧 `find*` / `markRecoveryQueued` 兼容入口仍可在后续清理窗口删除；本切片先避免扩大行为面。
