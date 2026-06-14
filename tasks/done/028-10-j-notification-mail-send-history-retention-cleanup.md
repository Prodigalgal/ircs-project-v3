# 028-10-J notification mail send history retention / cleanup

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `tasks/done/028-10-h-notification-mail-send-history-dev-migration-ops-query.md`
- `tasks/done/028-10-i-notification-smtp-staging-one-shot-live-smoke.md`
- `docs/requirements/028-10-j-notification-mail-send-history-retention-cleanup.md`

## 目标

- 固化 send history retention 策略：保留天数、状态筛选、批量大小、runner/手动触发、默认关闭原则。
- 实现或确认 notification-worker cleanup service/runner，低资源、可 one-shot、不会误删非目标记录。
- 增加 focused tests。
- 可行时执行 dev DB smoke：插入临时旧/新记录，运行 cleanup，验证旧临时记录删除、新临时记录保留、ops summary 正常。

## 范围

包含：

```text
services/ircs-notification-worker/** retention/cleanup 相关
scripts/smoke/notification-mail-send-history*
docs/**
tasks/**
```

不包含：

```text
metadata provider
config/content C2
storage
portal/admin/BFF
真实 SMTP 发送
```

## Retention 策略

- 默认保留天数：`180` 天。
- 默认状态筛选：不按状态筛选；`SENT`、`FAILED`、`SKIPPED` 超过 cutoff 后同等适用。需要缩小范围时使用状态白名单。
- 批量大小：默认 `500`，最大 `5000`；默认最多 `20` 批，避免低资源 dev runner 长时间占用 DB。
- 触发方式：notification-worker one-shot runner；默认关闭，手动/K8S Job 显式开启。
- 默认原则：任何环境默认不执行清理；生产和 staging-like 清理必须保留 summary/audit 证据。
- 误删防线：只删除 `notification_mail_send_history` 中 `created_at < cutoff` 且状态匹配的记录；不碰其他表，不输出 secret，不发送 SMTP。

## 影响文件

```text
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/**
services/ircs-notification-worker/src/main/resources/application.yaml
services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/**
scripts/smoke/notification-mail-send-history-retention-02810j-smoke.ps1
docs/requirements/028-10-j-notification-mail-send-history-retention-cleanup.md
tasks/done/028-10-j-notification-mail-send-history-retention-cleanup.md
tasks/in-progress/028-10-production-governor-credential-pool-parity.md
```

## 验收标准

```text
MARK 1/5 028-10-J docs/task spec created before implementation
MARK 2/5 cleanup service/runner is default-off and bounded by retention/status/batch limits
MARK 3/5 focused notification-worker tests pass
MARK 4/5 dev DB smoke deletes only temporary old rows and keeps temporary fresh rows
MARK 5/5 ops summary remains queryable and cleanup evidence is recorded
```

## 测试方式

必跑：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
```

可跑：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-retention-02810j-smoke.ps1 -StartOpsPortForward
```

不跑：

```text
真实 SMTP
Rabbit mail queue 投递
生产数据破坏性清理
```

## 当前状态

实现完成；`028-10-K` retry 已解除 Harbor push 阻塞并完成 dev DB cleanup smoke。
SMTP provider one-shot 真发仍是另一个独立门禁项。

## 实现结果

- 新增 `JdbcMailSendHistoryCleanupService`：
  - 默认 retention 设计为 `180` 天。
  - 只按 `created_at < cutoff` 删除。
  - 支持状态白名单；为空时不按状态筛选。
  - `batch-size` 默认 `500`，服务内最大 `5000`。
  - `max-batches` 默认 `20`，避免低资源环境长时间占用 DB。
  - `retention-days < 1`、`batch-size < 1`、`max-batches < 1` 直接拒绝。
- 新增 `MailSendHistoryCleanupRunner`：
  - `APP_MAIL_SEND_HISTORY_CLEANUP_ENABLED=false` 默认关闭。
  - `APP_MAIL_SEND_HISTORY_CLEANUP_EXIT_ON_COMPLETE=true` 时用于 one-shot Job，执行后退出 JVM。
  - 不接 Rabbit，不发送 SMTP，不输出 secret。
- 新增 smoke 脚本：
  - `scripts/smoke/notification-mail-send-history-retention-02810j-smoke.ps1`
  - 使用临时状态 `cleanup_smoke`，只清理本轮旧 fixture，验证 fresh fixture 在 cleanup 后仍保留，再最终删除临时 residue。

## 验证结果

通过：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL in 18s
```

通过：

```powershell
$errors=$null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path .\scripts\smoke\notification-mail-send-history-retention-02810j-smoke.ps1), [ref]$null, [ref]$errors)
```

结果：

```text
PowerShell parse OK
```

通过：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker -Tag dev -Architecture arm64 -Os linux
```

结果：

```text
Pushed harbor.mnnu.eu.org/ircs/ircs-notification-worker:dev for linux/arm64
imageDigest=sha256:79462463933647277e6069c61d0081e75ed241ccdff94f264d311d6ee164027b
```

通过：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-retention-02810j-smoke.ps1 -StartOpsPortForward -FailOnUnavailable
```

结果：

```text
status=PASS
reason=SEND_HISTORY_RETENTION_CLEANUP_OK
job=notification-mail-history-cleanup-02810j-814314ff5c9c
deletedRows=1
oldRemainingAfterCleanup=0
freshRetainedAfterCleanup=true
finalSmokeResidue=0
opsSendHistory.available=true
opsSendHistory.queried=true
sent=false
passwordPrinted=false
recipientPrinted=false
```

## MARK

```text
MARK 1/5 PASS 028-10-J docs/task spec created before implementation
MARK 2/5 PASS cleanup service/runner is default-off and bounded by retention/status/batch limits
MARK 3/5 PASS focused notification-worker tests pass
MARK 4/5 PASS dev DB smoke deletes only temporary old rows and keeps temporary fresh rows
MARK 5/5 PASS ops summary remains queryable and cleanup evidence is recorded
```

## 清理证据

- dev DB smoke 最终删除 `codex-02810j-*` 临时记录；复查 residue count 为 `0`。
- 本轮未发送 SMTP，未投递 Rabbit mail queue；cleanup Job 显式设置 `APP_MAIL_ENABLED=false`、
  `APP_NOTIFICATION_LISTENER_ENABLED=false`。
- 本轮 smoke Job 自动删除；另清理了早前残留的失败 Job
  `notification-mail-history-cleanup-02810j-b31a56c17b89`，复查 `codex-task=028-10-J` Job/Pod 无残留。
