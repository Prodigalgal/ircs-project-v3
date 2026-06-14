# 028-10-N notification maintenance runner safety gate

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

## 目标

- 对 notification-worker 中 maintenance/cleanup runner 补齐默认关闭、显式 enable、dry-run、batch limit、rate limit、audit log、idempotency/cleanup 和失败语义。
- 确保清理类 runner 未显式开启真实执行时不会删除数据、不会发送 SMTP、不会投递真实 mail queue。
- 增加 focused tests，覆盖默认关闭、dry-run 不修改、limit 生效、audit 记录、真实执行只影响 fixture。
- 可行时执行 dev smoke：临时 fixture、dry-run、one-shot execute、ops/audit 查询、fixture 清理。

## 范围

包含：

```text
services/ircs-notification-worker/** maintenance/cleanup/runner/gate
deploy/k8s/dev/notification-worker-dev.yaml
scripts/smoke/notification*maintenance*
docs/**
tasks/**
```

不包含：

```text
metadata provider
config/content C2
storage
portal/admin/BFF
normalization/aggregation
真实 SMTP
真实 mail queue 投递
```

## 高风险 runner 策略

- 默认关闭：常驻 Deployment 不自动执行清理 runner。
- 显式 enable：只有 `APP_MAIL_SEND_HISTORY_CLEANUP_ENABLED=true` 才会启动 runner。
- dry-run 默认：runner 即使开启，也默认 `APP_MAIL_SEND_HISTORY_CLEANUP_DRY_RUN=true`，只统计候选记录，不删除。
- 真实执行双开关：必须同时设置 `APP_MAIL_SEND_HISTORY_CLEANUP_DRY_RUN=false` 和 `APP_MAIL_SEND_HISTORY_CLEANUP_EXECUTE_ENABLED=true`。
- batch limit：单批和批次数受配置限制，service 层继续 cap batch size，避免大表长事务。
- rate limit：批次之间可配置 `APP_MAIL_SEND_HISTORY_CLEANUP_RATE_LIMIT_DELAY_MS`，默认 0，生产/Job 可显式限速。
- audit log：每次 enabled 后的 dry-run、真实执行、拒绝或失败都写入 `worker_job_audit_events`，不记录 secret、recipient 或 message body。
- idempotency/cleanup：清理条件只基于 `notification_mail_send_history.created_at < cutoff` 和可选状态白名单；重复执行只继续处理剩余候选行。
- 失败语义：配置非法、DB 缺失、真实执行 gate 缺失均返回失败结果；one-shot 可用 exit code 1 让 Job fail-fast。

## 验收标准

```text
MARK 1/6 028-10-N docs/task spec created before implementation
MARK 2/6 cleanup runner default-off and real execute requires explicit double gate
MARK 3/6 dry-run does not modify fixture rows and limit still bounds candidate work
MARK 4/6 worker job audit records dry-run/execute/refusal without secrets
MARK 5/6 focused notification-worker tests pass
MARK 6/6 dev smoke records dry-run + execute + ops/audit evidence or precise blocker
```

## 测试方式

必跑：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
```

可跑：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-maintenance-02810n-smoke.ps1 -ConfirmExecute -StartOpsPortForward -FailOnUnavailable
```

不跑：

```text
真实 SMTP
真实 mail queue 投递
生产数据清理
HTTPRoute 修改
```

## 验收结果

```text
MARK 1/6 PASS 028-10-N docs/task spec created before implementation
MARK 2/6 PASS cleanup runner default-off and real execute requires explicit double gate
MARK 3/6 PASS dry-run does not modify fixture rows and limit still bounds candidate work
MARK 4/6 PASS worker job audit records dry-run/execute/refusal without secrets
MARK 5/6 PASS focused notification-worker tests pass
MARK 6/6 PASS dev smoke records dry-run + execute + ops/audit evidence
```

关键证据：

```text
imageDigest=sha256:4cff0405ff615adfd29fe9847518208a9320b0f3522afa0021ceaeef55ada6cc
dryRun candidateRows=1 deletedRows=0
execute deletedRows=1
oldAfterExecute=0
freshAfterExecute=1
dryRunAudit=1
executeAudit=1
opsWorkerJobAudit.available=true
finalSmokeResidue=0
sent=false
mailQueuePublished=false
passwordPrinted=false
recipientPrinted=false
```

报告：

- `docs/reports/102-02810n-notification-maintenance-runner-safety-gate.md`
