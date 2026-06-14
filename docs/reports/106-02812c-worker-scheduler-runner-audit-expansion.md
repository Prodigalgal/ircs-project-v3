# 106 028-12-C worker/scheduler/runner audit expansion 报告

状态：live smoke closure，overall partial

日期：2026-06-10

## 背景

`028-12` 已完成 HTTP service handler audit、notification worker/job audit、task-service
runner/watchdog/startup recovery audit 与 aggregation-worker scheduler audit。剩余缺口是更多
worker/scheduler/runner 的 V1 审计采集语义：开始/结束/失败、correlation、job kind、duration、
error redaction，以及 dry-run/execute/skip 语义。

当前策略是先推进功能完整，随后补最小 live queue / ops query smoke；耗时完整测试仍纳入统一测试批次。

## 目标

- 梳理当前缺审计的 worker/scheduler/runner，并记录覆盖决策。
- 优先给 metadata-worker provider queue、normalization-worker queue/LLM runner、
  storage-service media queue/watchdog 补 `worker_job_audit_events` 写入。
- 复用既有 `WorkerJobAuditWriter`，不新增表结构或平行审计体系。
- 错误信息不输出 provider token/password/recipient/request body 等敏感字段。
- 更新 028-12 父任务、F7 backlog、093 总账和统一测试计划。

## 非目标

- 不修改 BFF transport。
- 不修改 C1 catalog/normalization cache。
- 不修改 storage R2 core behavior。
- 不触发 live queue smoke、真实 provider、真实 R2 或完整测试。
- 不改 notification runner 已完成路径。

## Inventory 与覆盖决策

| 模块 | runner/worker | 决策 | 说明 |
| --- | --- | --- | --- |
| metadata-worker | `MetadataProviderWorker` TMDB/Douban/RT queue | 本轮实现 | provider queue 是外部 provider 关键链路，按 provider type + videoId 记录 succeeded/failed/skipped。 |
| normalization-worker | `NormalizeVideoConsumer` | 本轮实现 | raw video normalization queue 是主处理链路，按 rawVideoId 记录 succeeded/failed。 |
| normalization-worker | `LlmCleaningRunner` | 本轮实现 | LLM cleaning 具备 dry-run/disabled/credential-missing 等 skip 语义，按 kind 记录 succeeded/skipped/failed。 |
| normalization-worker | `NormalizationRetryWatchdog` | 本轮 deferred | 已有 C1 gate 与 retry watchdog 测试，下一批统一覆盖 recovery count 与 skipped。 |
| storage-service | `AvatarSyncConsumer` | 本轮实现 | avatar sync queue 是 identity/storage 关键链路，按 memberId 记录 succeeded/failed。 |
| storage-service | `ImageDownloadConsumer` | 本轮实现 | cover download queue 是媒体落地关键链路，按 imageId 记录 succeeded/failed。 |
| storage-service | `CoverImageR2PromoteConsumer` | 本轮实现 | R2 promote queue 记录 scheduled=succeeded、not scheduled=skipped。 |
| storage-service | `StorageR2Watchdog` | 本轮实现 | R2 watchdog 记录 active run=succeeded、inactive=skipped、top-level exception=failed。 |
| scraper-service | `ManualScraperService` / task execution HTTP | deferred | 手动触发已由 scraper-service handler audit 覆盖；task-service runner 已记录 collection-task runner audit，本轮避免重复计数。 |
| ops-service | maintenance/reindex runner | deferred | 028-10-C/028-10-N 已覆盖 runner registry 与 notification maintenance gate；其他高风险 runner 后续单独拆。 |

## Audit event 字段

统一写入 `worker_job_audit_events`：

```text
job_source: spring.application.name，例如 ircs-metadata-worker
job_type: queue-consumer 或 scheduler
job_name: metadata.provider.tmdb / normalization.raw-video / normalization.llm-cleaning.genre /
          storage.avatar-sync / storage.cover-download / storage.cover-r2-promote / storage.r2-watchdog
correlation_id: videoId/rawVideoId/memberId/imageId/kind/skip mode
status: succeeded / failed / skipped
duration_ms: 从入口开始到业务处理结束
error_class: Throwable class，成功时 null
error_message: 复用 WorkerJobAuditWriter redaction，额外用稳定 audit exception reason，不记录 token/password/recipient
```

## 验收标准

```text
MARK 1/6 worker/scheduler/runner inventory completed
MARK 2/6 audit coverage decision recorded per runner
MARK 3/6 metadata provider worker audit hooks implemented
MARK 4/6 normalization queue/LLM runner audit hooks implemented
MARK 5/6 storage media queue/watchdog audit hooks implemented
MARK 6/6 focused lightweight tests compile/pass or unified-test deferred reason recorded
```

## 统一测试后置

纳入 `UTB-02816-P1P2-03`：

```text
focused tests:
  .\gradlew.bat --no-daemon :services:ircs-metadata-worker:test --tests "*MetadataProviderWorkerTest" --tests "*MetadataWorkerAuditConfigurationTest"
  .\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*NormalizeVideoConsumerTest" --tests "*LlmCleaningRunnerTest" --tests "*NormalizationWorkerAuditConfigurationTest"
  .\gradlew.bat --no-daemon :services:ircs-storage-service:test --tests "*AvatarSyncConsumerTest" --tests "*ImageDownloadConsumerTest" --tests "*CoverImageR2PromoteConsumerTest" --tests "*StorageR2WatchdogTest" --tests "*StorageWorkerAuditConfigurationTest"

后置 smoke:
  ops worker-job-audit query fixture cleanup
  queue/DLQ residue check
  provider/R2/mail no-unintended-side-effect check
  ResourceQuota/HTTPRoute residue check
```

## 实现结果

- `ircs-metadata-worker` 新增 `MetadataWorkerAuditConfiguration` 和 `app.worker.audit.*` 配置入口。
- `MetadataProviderWorker` 对 TMDB/Douban/RT provider queue 记录 `succeeded/failed/skipped`。
- `ircs-normalization-worker` 新增 `NormalizationWorkerAuditConfiguration` 和 `app.worker.audit.*` 配置入口。
- `NormalizeVideoConsumer` 对 raw video normalization queue 记录 `succeeded/failed`，correlation 为 rawVideoId。
- `LlmCleaningRunner` 对每个 `LlmCleaningKind` 记录 `succeeded/skipped/failed`，默认 `dry-run`
  等固定 reason 进入 `skipped`。
- `ircs-storage-service` 复用 `StorageServiceAuditConfiguration` 暴露 `WorkerJobAuditWriter`，并新增
  `app.worker.audit.*` 配置入口。
- `AvatarSyncConsumer`、`ImageDownloadConsumer`、`CoverImageR2PromoteConsumer` 和
  `StorageR2Watchdog` 记录 storage media queue/watchdog audit。

## 敏感字段边界

- audit correlation 仅使用 UUID、provider type、LLM kind/reason 或固定 execute/skip reason。
- error message 不直接拼接 provider token、password、recipient、request body 或 credential payload。
- 写库层继续复用 `WorkerJobAuditWriter#errorMessage` 的 token/password/secret/Bearer/email redaction。
- audit 写入为 best-effort；`auditWriter.record` 异常只写 warn，不改变 worker/runner 主路径。

## 验证结果

执行 focused tests，跳过 live queue smoke 和完整测试：

```powershell
.\gradlew.bat --no-daemon :services:ircs-metadata-worker:test --tests "*MetadataProviderWorkerTest" --tests "*MetadataWorkerAuditConfigurationTest" :services:ircs-normalization-worker:test --tests "*NormalizeVideoConsumerTest" --tests "*LlmCleaningRunnerTest" --tests "*NormalizationWorkerAuditConfigurationTest" :services:ircs-storage-service:test --tests "*AvatarSyncConsumerTest" --tests "*ImageDownloadConsumerTest" --tests "*CoverImageR2PromoteConsumerTest" --tests "*StorageR2WatchdogTest" --tests "*StorageWorkerAuditConfigurationTest"
```

结果：`BUILD SUCCESSFUL in 8s`。第二次组合命令为 up-to-date；此前 storage focused rerun 实际执行并
`BUILD SUCCESSFUL in 14s`。

JUnit XML 结果：

```text
metadata-worker: tests=7, skipped=0, failures=0, errors=0
normalization-worker: tests=5, skipped=0, failures=0, errors=0
storage-service: tests=10, skipped=0, failures=0, errors=0
```

## MARK

```text
MARK 1/6 PASS worker/scheduler/runner inventory completed
MARK 2/6 PASS audit coverage decision recorded per runner
MARK 3/6 PASS metadata provider worker audit hooks implemented
MARK 4/6 PASS normalization queue/LLM runner audit hooks implemented
MARK 5/6 PASS storage media queue/watchdog audit hooks implemented
MARK 6/6 PASS focused lightweight tests compile/pass or unified-test deferred reason recorded
```

## 2026-06-12 live smoke closure

本轮补齐 metadata / normalization / storage worker audit live smoke，并在执行前完成运行中镜像
freshness 对账与旧镜像滚动修复。

```powershell
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test :services:ircs-storage-service:test --tests *LlmCredentialResolverTest --tests *LlmCleaningServiceTest --tests *LlmCleaningInternalControllerTest --tests *RawVideoNormalizationServiceTest --tests *RawVideoTextNormalizerTest --tests *SystemConfigRepositoryTest --tests *StorageR2WatchdogTest --tests *CoverImageDownloadServiceTest --rerun-tasks
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\worker-audit-metadata-normalization-storage-02812c-smoke.ps1 -FailOnSkip
```

结果：

```text
container freshness: CURRENT_TAG_DIGEST=22, EXTERNAL_OR_INFRA=6, STALE_TAG_DIGEST=0
image freshness artifacts:
  build/tmp/ircs-dev-container-image-freshness-20260612111028.json
  build/tmp/ircs-dev-container-image-freshness-20260612111028.csv
worker audit smoke: MARK 6/6 PASSED
trace: 02812c-worker-audit-20260612111112
queue/DLQ residue: 0
ops query: metadata=true, normalization=true, storage=true
```

本轮顺手修复：

- `ircs-normalization-worker` 最新镜像启动缺口：`LlmCredentialResolver` 改为 Lombok 单构造器，
  并补 `NormalizationOutboundConfiguration`。
- `ircs-normalization-worker` 补 `NormalizationSearchSyncConfiguration`，提供
  `SearchSyncOutboxWriter`。
- `ircs-storage-service` 去除 watchdog/download service 的生产多构造器歧义。
- `src/main` 范围 `@Autowired` 已清零；全服务源码当前态的重打包/滚动仍需先继续收敛历史
  多构造器组件。

总体状态仍为 partial：metadata/normalization/storage live worker audit 已闭环；完整
provider/R2/mail no-unintended-side-effect 矩阵、ResourceQuota/HTTPRoute 后置检查以及全服务
源码当前态重打包滚动仍后置。
