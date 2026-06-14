# 102 028-10-N notification maintenance runner safety gate

## 结论

`028-10-N` 已通过。notification-worker 的 send-history cleanup runner 现在具备高风险
maintenance runner gate：常驻默认关闭，开启后默认 dry-run；真实删除必须同时设置
`APP_MAIL_SEND_HISTORY_CLEANUP_DRY_RUN=false` 与
`APP_MAIL_SEND_HISTORY_CLEANUP_EXECUTE_ENABLED=true`。

本轮 dev smoke 只操作 `codex-02810n-*` 临时 fixture；未发送 SMTP，未投递 mail queue，未输出
secret、recipient 或 message body。

## 策略

- 默认关闭：`APP_MAIL_SEND_HISTORY_CLEANUP_ENABLED=false`。
- dry-run 默认：`APP_MAIL_SEND_HISTORY_CLEANUP_DRY_RUN=true`，只统计候选行。
- 真实执行双开关：`DRY_RUN=false` 且 `EXECUTE_ENABLED=true` 才删除。
- limit：保留原有 retention/status/batch/max-batches，service 层 batch size cap 仍为 `5000`。
- rate limit：新增 `APP_MAIL_SEND_HISTORY_CLEANUP_RATE_LIMIT_DELAY_MS`，批次之间限速，最大 cap `60000ms`。
- audit：enabled 后的 dry-run、execute、refusal/failure 写入 `worker_job_audit_events`，由 ops-service 只读查询。
- 失败语义：真实执行缺少 execute gate 时拒绝并写 failed audit；one-shot 可通过 exit code 1 fail-fast。

## 改动摘要

- `MailSendHistoryCleanupRunner` 增加 dry-run、execute gate、rate-limit 参数和 worker job audit。
- `JdbcMailSendHistoryCleanupService` 增加 dry-run candidate count 与 per-batch rate-limit。
- `application.yaml` 与 dev manifest 显式保留安全默认值。
- 新增 runner focused tests 与 028-10-N dev smoke helper。

## 验证

本地测试：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL in 19s
```

PowerShell parse：

```text
PowerShell parse OK
```

K8S server dry-run：

```text
configmap/ircs-dev-app-config unchanged (server dry run)
deployment.apps/ircs-notification-worker configured (server dry run)
```

镜像与 rollout：

```text
image=harbor.mnnu.eu.org/ircs/ircs-notification-worker:dev
imageDigest=sha256:4cff0405ff615adfd29fe9847518208a9320b0f3522afa0021ceaeef55ada6cc
deployment "ircs-notification-worker" successfully rolled out
```

dev smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-maintenance-02810n-smoke.ps1 -ConfirmExecute -StartOpsPortForward -FailOnUnavailable
```

结果：

```text
MARK 1/6 send history/audit tables and notification-worker image located
MARK 2/6 temporary old and fresh send history rows inserted
MARK 3/6 dry-run one-shot completed without modifying fixture rows
MARK 4/6 dry-run worker job audit row recorded
MARK 5/6 explicit execute one-shot deletes only old fixture and records audit
MARK 6/6 ops worker-job-audit query available or explicitly skipped
status=PASS
reason=MAINTENANCE_RUNNER_DRY_RUN_AND_EXECUTE_OK
dryRun.candidateRows=1
dryRun.deletedRows=0
execute.deletedRows=1
oldAfterExecute=0
freshAfterExecute=1
dryRunAudit=1
executeAudit=1
opsWorkerJobAudit.available=true
opsWorkerJobAudit.pageElements=2
finalSmokeResidue=0
sent=false
mailQueuePublished=false
passwordPrinted=false
recipientPrinted=false
```

残留检查：

```text
kubectl -n ircs-dev get jobs,pods -l codex-task=028-10-N
No resources found in ircs-dev namespace.

select count(*) from notification_mail_send_history where correlation_id like 'codex-02810n-%';
0

APP_MAIL_ENABLED=configMap:ircs-dev-app-config/APP_MAIL_ENABLED
APP_MAIL_SEND_HISTORY_CLEANUP_ENABLED=false
APP_MAIL_SEND_HISTORY_CLEANUP_DRY_RUN=true
APP_MAIL_SEND_HISTORY_CLEANUP_EXECUTE_ENABLED=false
APP_MAIL_SEND_HISTORY_CLEANUP_RATE_LIMIT_DELAY_MS=0
```

## MARK

```text
MARK 1/6 PASS 028-10-N docs/task spec created before implementation
MARK 2/6 PASS cleanup runner default-off and real execute requires explicit double gate
MARK 3/6 PASS dry-run does not modify fixture rows and limit still bounds candidate work
MARK 4/6 PASS worker job audit records dry-run/execute/refusal without secrets
MARK 5/6 PASS focused notification-worker tests pass
MARK 6/6 PASS dev smoke records dry-run + execute + ops/audit evidence
```

## 遗留风险

- 本轮只覆盖 notification-worker send-history cleanup runner；ops-service full maintenance runner、其他 worker scheduler 的高风险 gate 仍属于各自后续切片。
- `worker_job_audit_events` 是审计证据，本轮 smoke 不删除审计行；只清理临时 send-history fixture 和 Job/Pod。
