# 028-16-F7 worker/scheduler audit golden P1/P2 backlog

状态：partial，028-12-C implementation first closure

父任务：

- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 背景

028-12 已完成 HTTP service handler audit、notification worker/job audit、task-service runner/watchdog
startup recovery audit 与 aggregation scheduler audit。093 仍把“更多 worker/scheduler/runner audit”
列为 Top 剩余项，避免内部异步链路只有局部可观察性。

## 目标

- 盘点 normalization、metadata、storage、scraper、content、search、ops、task 等 worker/scheduler/runner。
- 为每类异步执行定义 worker_job_audit_events 或等价审计字段：source、jobType、status、correlation、
  retry、duration、error redaction。
- 明确哪些 runner 只需 request audit，哪些必须 worker/job audit。
- 准备统一测试批次的 ops query 与 fixture cleanup 边界。

## 非目标

- 不把所有低价值定时任务无差别接入审计。
- 不改变业务执行时序。
- 不执行高风险 runner。

## 影响范围

```text
services/ircs-normalization-worker/**
services/ircs-metadata-worker/**
services/ircs-storage-service/**
services/ircs-scraper-service/**
services/ircs-content-service/**
services/ircs-search-service/**
services/ircs-ops-service/**
services/ircs-task-service/**
docs/reports/093-v1-v3-parity-progress-ledger.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 依赖

- worker_job_audit_events schema、writer 和 ops query 已完成。
- 028-10-N maintenance runner gate 已完成，可作为高风险 runner 模板。
- 统一测试窗口允许查 ops endpoint 和清理 audit fixture。

## 风险

- 审计写入失败不应破坏业务主路径，需明确 best-effort 或 fail-closed 边界。
- 错误消息可能包含 token、URL、邮件地址或 provider credential，必须脱敏。
- 过细审计会增加 DB 写入与查询噪音。

## 验收标准

```text
MARK 1/6 worker/scheduler/runner inventory completed
MARK 2/6 audit coverage decision recorded per runner
MARK 3/6 high-value missing audit hooks implemented
MARK 4/6 sensitive field redaction verified
MARK 5/6 focused service tests pass
MARK 6/6 ops query smoke passes with audit fixture cleanup
```

## 统一测试边界

纳入统一测试批次 `UTB-02816-P1P2-03`。统一执行 focused tests、ops worker-job-audit query smoke、
fixture cleanup、mail/provider/R2 no-unintended-side-effect 检查和后置资源检查。

## 028-12-C partial 入账

2026-06-10：028-12-C worker/scheduler/runner audit expansion 已完成 implementation first closure，
先实现、后统一测试。

本轮 coverage decision：

```text
metadata-worker MetadataProviderWorker: implement worker_job_audit_events
normalization-worker NormalizeVideoConsumer: implement worker_job_audit_events
normalization-worker LlmCleaningRunner: implement worker_job_audit_events, including dry-run/skipped
storage-service AvatarSyncConsumer: implement worker_job_audit_events
storage-service ImageDownloadConsumer: implement worker_job_audit_events
storage-service CoverImageR2PromoteConsumer: implement worker_job_audit_events, skipped when not scheduled
storage-service StorageR2Watchdog: implement worker_job_audit_events, skipped when R2 inactive
scraper-service ManualScraperService: deferred, covered by handler audit and task-service runner audit for this slice
ops-service high-risk runners: deferred to later safety-gated runner slices
```

MARK status：

```text
MARK 1/6 worker/scheduler/runner inventory completed: PASS for 028-12-C scope
MARK 2/6 audit coverage decision recorded per runner: PASS for 028-12-C scope
MARK 3/6 high-value missing audit hooks implemented: PASS metadata/normalization/storage
MARK 4/6 sensitive field redaction verified: PASS by stable metadata-only audit fields and writer redaction
MARK 5/6 focused service tests pass: PASS metadata=7, normalization=5, storage=10
MARK 6/6 ops query smoke passes with audit fixture cleanup: deferred to UTB-02816-P1P2-03
```

## 2026-06-11 UTB-02816-P1P2-03 partial execution

`UTB-02816-P1P2-03` 已执行一次，结果为 partial。通过项可入账，但 notification worker audit
因队列环境不安全被阻断，不能关闭本任务。

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-03 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-03-20260611.json
```

通过项：

```text
task service audit smoke: PASSED
search worker audit focused regression: PASSED
scraper worker audit focused regression: PASSED
search service audit smoke: PASSED
scraper service audit smoke: PASSED dev-safe dry-run
resource rollback verifier: PASSED
```

阻塞项：

```text
notification worker audit smoke: FAILED, exitCode=2
child reason: MAIL_QUEUE_NOT_EMPTY
Rabbit snapshot:
  q.notification.mail messages=1 consumers=0
  q.notification.mail.dlq messages=0 consumers=0
```

本轮未 purge `q.notification.mail`，避免误删真实待发邮件。后续需要先识别该消息来源或明确
清理策略，再重跑 notification worker audit。

附带修复：`production-like-smoke-orchestrator-02819.ps1` 已修复 native command 非 0 exit code
被误标 `PASSED` 的问题；此后 `exitCode=2` 会正确进入 `FAILED`。

## 2026-06-11 UTB-02816-P1P2-03 closure update

已完成 notification 阻塞收敛并重跑 `UTB-02816-P1P2-03`：

```text
notification worker audit smoke: PASSED
task service audit smoke: PASSED
search worker audit focused regression: PASSED
scraper worker audit focused regression: PASSED
search service audit smoke: PASSED
scraper service audit smoke: PASSED dev-safe dry-run
resource rollback verifier: PASSED
```

相关修复：

- `scripts/smoke/notification-worker-audit-with-listener-02812-smoke.ps1` 负责 dev smoke 临时打开 listener gate 并恢复。
- `scripts/smoke/notification-worker-audit-02812-smoke.ps1` 等待 Rabbit ack 归零后再判断 cleanup residue。
- `platform/ircs-migrator` 重新推送 ARM64 dev image 并运行正式 Job，补齐 `audit_class` live schema，恢复 ops query。

仍未由本批次关闭的 worker/scheduler live 债务：

- metadata/normalization/storage worker 的 live queue + ops query fixture cleanup 仍需独立补测。
- scraper service audit 本批次仍是 dev-safe dry-run，未触发真实 scraper work。
