# 028-20-21 DB claim release semantics

## 目标

补强 claim-first 迁移后的失败释放语义，确保 DB claim 在 timeout、provider failure、Rabbit publish failure 等路径下不会长期悬挂。

## 已完成

- 复核当前 claim-first 链路：
  - search outbox：`PROCESSING` row lease 已有 `locked_until` 过期恢复，失败 `markFailed` 清空 `locked_by/locked_until`。
  - storage avatar：`avatar_sync_claimed_until` TTL 兜底，publish 失败显式 `releaseAvatarSyncClaim`。
  - storage cover：claim 为 `UPLOADING`，sync 失败会 `markFailed` 并写 `next_retry_time`。
  - aggregation：`claimPending()` 在事务内执行，失败时 rollback；业务失败显式 `markPending`。
- LLM cleaning provider timeout/error：
  - 原先依赖 `llm_cleaning_claimed_until` TTL。
  - 现在 catch `ProviderTimeout` / `ProviderError` / invalid mode 后显式 `releaseClaims`。
  - release 失败只记录 warning，仍由 TTL 兜底。
- normalization retry watchdog Rabbit publish failure：
  - 已成功 publish 的 raw 不回滚。
  - 当前失败 raw 和剩余未发布 raw 调用 `releaseRecoveryPublishFailures(...)`。
  - 状态恢复为 `FAILED` 并写入新的 `next_normalization_retry_time`，不增加 normalization retry count。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*NormalizationRetryWatchdogTest" --tests "*RawVideoNormalizationRepositoryTest" --tests "*LlmCleaningServiceTest" --tests "*LlmCleaningRepositoryTest" :services:ircs-normalization-worker:bootJar
```

结果：`BUILD SUCCESSFUL`。

## 遗留

- LLM cleaning 目前失败后立即释放 claim；如果 live provider smoke 发现手动/定时触发热循环，应新增独立 retry/backoff 字段，而不是用 claim TTL 充当 backoff。
- normalization retry watchdog 仍需 K8S Rabbit failure / recovery live smoke，确认 publish 失败后 DB 状态和下一轮 retry 可见。
