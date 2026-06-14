# 028-10-J notification mail send history retention / cleanup

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

## 目标

- 明确 `notification_mail_send_history` retention 策略，避免审计表无限增长。
- 在 notification-worker 内实现低资源、默认关闭、可手动 one-shot 触发的 cleanup service/runner。
- 增加 focused tests，证明只删除 cutoff 之前且符合状态筛选的目标记录。
- 如 dev DB 可用，插入临时旧/新记录并运行 cleanup smoke，验证旧临时记录删除、新临时记录保留、ops summary 正常。

## 范围

包含：

```text
services/ircs-notification-worker/** retention/cleanup 相关
scripts/smoke/notification-mail-send-history*
docs/**
tasks/**
```

按需包含：

```text
services/ircs-ops-service/** retention 视图
platform/ircs-migrator/** 索引/字段
deploy/k8s/dev/notification-worker*
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
- 默认状态筛选：不按状态筛选，即 `SENT`、`FAILED`、`SKIPPED` 等审计状态在超过 cutoff 后同等适用；如需更窄清理，可通过状态白名单配置。
- 批量大小：默认 `500`，配置上限 `5000`，每次循环最多执行 `max-batches` 批，默认 `20`。
- 触发方式：notification-worker one-shot application runner，默认关闭；手动或 K8S Job 方式设置 cleanup enabled 后执行。
- 默认原则：所有环境默认不清理；生产或 staging-like 环境必须显式开启 runner，并保留 summary/audit 证据。
- 误删防线：只删除 `created_at < now - retention-days` 且状态匹配的 `notification_mail_send_history` 记录；runner 不碰其他表，不输出 secret，不发送 SMTP。

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

已由 `028-10-K` retry 解除 Harbor push 阻塞，并完成 dev DB cleanup smoke。
本任务未发送 SMTP，未投递 Rabbit mail queue。

## 验证记录

```text
MARK 1/5 PASS 028-10-J docs/task spec created before implementation
MARK 2/5 PASS cleanup service/runner is default-off and bounded by retention/status/batch limits
MARK 3/5 PASS focused notification-worker tests pass
MARK 4/5 PASS dev DB smoke deletes only temporary old rows and keeps temporary fresh rows
MARK 5/5 PASS ops summary remains queryable and cleanup evidence is recorded
```

关键证据：

```text
notification-worker image digest: sha256:79462463933647277e6069c61d0081e75ed241ccdff94f264d311d6ee164027b
cleanup Job: notification-mail-history-cleanup-02810j-814314ff5c9c completed
cleanup result: deletedRows=1, batches=1, reason=completed
oldRemainingAfterCleanup=0, freshRetainedAfterCleanup=true, finalSmokeResidue=0
ops summary: available=true, queried=true, totalLast24h=1
SMTP: sent=false, APP_MAIL_ENABLED=false in cleanup Job
cleanup: codex-task=028-10-J Job/Pod removed; codex-02810j-* DB residue count=0
```
