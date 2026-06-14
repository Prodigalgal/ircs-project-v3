# 092 worker/scheduler/runner audit expansion 迁移记录

状态：implementation first closure，overall partial

日期：2026-06-10

## 迁移内容

为 `028-12-C` 增加更多 worker/scheduler/runner 的 `worker_job_audit_events` 写入。

## 范围

```text
services/ircs-metadata-worker/**
services/ircs-normalization-worker/**
services/ircs-storage-service/**
docs/reports/106-02812c-worker-scheduler-runner-audit-expansion.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
tasks/backlog/028-16-f7-worker-scheduler-audit-golden-p1p2-backlog.md
docs/reports/093-v1-v3-parity-progress-ledger.md
```

## 期望结果

```text
MARK 1/6 inventory and coverage decision recorded
MARK 2/6 metadata provider queue writes succeeded/failed/skipped audit evidence
MARK 3/6 normalization raw queue writes succeeded/failed audit evidence
MARK 4/6 normalization LLM runner writes dry-run/skipped/succeeded/failed audit evidence
MARK 5/6 storage avatar/download/R2 promote/watchdog writes audit evidence
MARK 6/6 focused tests are passed or explicitly deferred to unified testing
```

## 迁移边界

- 复用既有 `worker_job_audit_events` schema 和 `WorkerJobAuditWriter`。
- 不新增 schema、不新增 ops API、不修改 K8S/deploy。
- 不执行 live smoke、不触发真实 provider/R2/mail。
- audit 写入 best-effort，失败不得改变原 worker/runner 主路径。

## 实际变更

- metadata-worker：新增 `MetadataWorkerAuditConfiguration`，`MetadataProviderWorker` 写入 provider
  queue `succeeded/failed/skipped` audit evidence，`application.yaml` 新增 `app.worker.audit.*`。
- normalization-worker：新增 `NormalizationWorkerAuditConfiguration`，`NormalizeVideoConsumer`
  写入 raw queue audit evidence，`LlmCleaningRunner` 写入 LLM cleaning scheduler audit evidence，
  `application.yaml` 新增 `app.worker.audit.*`。
- storage-service：`StorageServiceAuditConfiguration` 暴露 `WorkerJobAuditWriter`，
  `AvatarSyncConsumer`、`ImageDownloadConsumer`、`CoverImageR2PromoteConsumer` 和
  `StorageR2Watchdog` 写入 queue/scheduler audit evidence，`application.yaml` 新增
  `app.worker.audit.*`。

## 验证记录

```powershell
.\gradlew.bat --no-daemon :services:ircs-metadata-worker:test --tests "*MetadataProviderWorkerTest" --tests "*MetadataWorkerAuditConfigurationTest" :services:ircs-normalization-worker:test --tests "*NormalizeVideoConsumerTest" --tests "*LlmCleaningRunnerTest" --tests "*NormalizationWorkerAuditConfigurationTest" :services:ircs-storage-service:test --tests "*AvatarSyncConsumerTest" --tests "*ImageDownloadConsumerTest" --tests "*CoverImageR2PromoteConsumerTest" --tests "*StorageR2WatchdogTest" --tests "*StorageWorkerAuditConfigurationTest"
```

结果：`BUILD SUCCESSFUL in 8s`。

```text
metadata-worker tests=7, failures=0, errors=0
normalization-worker tests=5, failures=0, errors=0
storage-service tests=10, failures=0, errors=0
```

## MARK 完成情况

```text
MARK 1/6 PASS inventory and coverage decision recorded
MARK 2/6 PASS metadata provider queue writes succeeded/failed/skipped audit evidence
MARK 3/6 PASS normalization raw queue writes succeeded/failed audit evidence
MARK 4/6 PASS normalization LLM runner writes dry-run/skipped/succeeded/failed audit evidence
MARK 5/6 PASS storage avatar/download/R2 promote/watchdog writes audit evidence
MARK 6/6 PASS focused tests are passed or explicitly deferred to unified testing
```

## 后置项

- `UTB-02816-P1P2-03` 执行 ops worker-job-audit query smoke 与 fixture cleanup。
- live queue/DLQ residue、provider/R2/mail no-unintended-side-effect、ResourceQuota/HTTPRoute
  residue 均未在本轮执行。
