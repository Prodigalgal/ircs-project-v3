# 028-10-N notification maintenance runner safety gate

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

来源：

- `docs/requirements/028-10-n-notification-maintenance-runner-safety-gate.md`
- `tasks/done/028-10-j-notification-mail-send-history-retention-cleanup.md`
- `tasks/done/028-10-l-notification-smtp-v1-live-smoke.md`

## 目标

- 在 notification-worker cleanup runner 外层补最小高风险 runner gate。
- 默认关闭；开启后默认 dry-run；真实删除需要显式双开关。
- 记录 worker job audit，支持 ops-service 只读查询。
- 保留 028-10-J 的 retention cutoff、状态白名单和 batch limit 行为。
- 更新 dev manifest 和 smoke 脚本，确保 dev/K8S 不会误触发 SMTP、mail queue 或真实业务副作用。

## 非目标

- 不修改 metadata provider、config/content C2、storage、portal/admin/BFF、normalization/aggregation。
- 不新增公网路由，不新增 ops 写入口。
- 不发送 SMTP，不投递真实 mail queue。
- 不做全量生产数据清理。

## 影响范围

```text
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/**
services/ircs-notification-worker/src/main/resources/application.yaml
services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/**
deploy/k8s/dev/notification-worker-dev.yaml
scripts/smoke/notification-mail-send-history-maintenance-02810n-smoke.ps1
docs/requirements/028-10-n-notification-maintenance-runner-safety-gate.md
tasks/done/028-10-n-notification-maintenance-runner-safety-gate.md
tasks/in-progress/028-10-production-governor-credential-pool-parity.md
tasks/index.md
```

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

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
```

可选 dev smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-maintenance-02810n-smoke.ps1 -ConfirmExecute -StartOpsPortForward -FailOnUnavailable
```

## 进度

2026-06-09：已完成。

## 实现结果

- `MailSendHistoryCleanupRunner`：
  - cleanup 默认仍由 `APP_MAIL_SEND_HISTORY_CLEANUP_ENABLED=false` 关闭。
  - enabled 后默认 `APP_MAIL_SEND_HISTORY_CLEANUP_DRY_RUN=true`，只统计候选行。
  - 真实删除必须 `APP_MAIL_SEND_HISTORY_CLEANUP_DRY_RUN=false` 且
    `APP_MAIL_SEND_HISTORY_CLEANUP_EXECUTE_ENABLED=true`。
  - 缺少 execute gate 时拒绝执行，并写 failed worker-job audit。
  - dry-run/execute 成功或失败均写 `worker_job_audit_events`。
- `JdbcMailSendHistoryCleanupService`：
  - dry-run 使用 bounded candidate count，不执行 delete。
  - 保留 retention/status/batch/max-batches，batch size service cap 仍为 `5000`。
  - 新增 per-batch rate-limit delay，最大 cap `60000ms`。
- dev manifest：
  - 显式 `APP_MAIL_SEND_HISTORY_CLEANUP_ENABLED=false`。
  - 显式 `APP_MAIL_SEND_HISTORY_CLEANUP_DRY_RUN=true`。
  - 显式 `APP_MAIL_SEND_HISTORY_CLEANUP_EXECUTE_ENABLED=false`。
- smoke helper：
  - `scripts/smoke/notification-mail-send-history-maintenance-02810n-smoke.ps1`
  - 默认 dry-run；`-ConfirmExecute` 才执行 fixture 删除。

## 验证结果

通过：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL in 19s
```

通过：

```powershell
$errors=$null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path .\scripts\smoke\notification-mail-send-history-maintenance-02810n-smoke.ps1), [ref]$null, [ref]$errors)
```

结果：`PowerShell parse OK`

通过：

```powershell
kubectl apply --dry-run=server -f deploy\k8s\dev\notification-worker-dev.yaml
```

结果：

```text
configmap/ircs-dev-app-config unchanged (server dry run)
deployment.apps/ircs-notification-worker configured (server dry run)
```

镜像与 rollout：

```text
imageDigest=sha256:4cff0405ff615adfd29fe9847518208a9320b0f3522afa0021ceaeef55ada6cc
deployment "ircs-notification-worker" successfully rolled out
```

dev smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-maintenance-02810n-smoke.ps1 -ConfirmExecute -StartOpsPortForward -FailOnUnavailable
```

结果：

```text
status=PASS
reason=MAINTENANCE_RUNNER_DRY_RUN_AND_EXECUTE_OK
dryRun candidateRows=1 deletedRows=0
execute deletedRows=1
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

清理证据：

```text
kubectl -n ircs-dev get jobs,pods -l codex-task=028-10-N
No resources found in ircs-dev namespace.

notification_mail_send_history where correlation_id like 'codex-02810n-%' count=0
APP_MAIL_SEND_HISTORY_CLEANUP_ENABLED=false
APP_MAIL_SEND_HISTORY_CLEANUP_DRY_RUN=true
APP_MAIL_SEND_HISTORY_CLEANUP_EXECUTE_ENABLED=false
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
