# 098 028-10-K notification-worker Harbor push / retention smoke

日期：2026-06-09

状态：完成

## 结论

`028-10-K` retry 复用现有安全凭据和 `push-service-image-jib.ps1` 成功 push
`ircs-notification-worker:dev` ARM64 镜像，并完成 `028-10-J` send history retention
dev DB cleanup smoke。未发送 SMTP，未投递 Rabbit mail queue，未输出 secret。

上一轮 Harbor unauthorized 在本轮未复现。已验证：

- `ircs-system/harbor-secret` 与 `ircs-dev/harbor-secret` 存在且类型正确。
- Docker config registry key 形态匹配 Harbor 主机。
- Jib 使用临时 `DOCKER_CONFIG` 中的 Docker config 完成 Harbor push。
- 目标 repo 与脚本参数一致。

因此本轮根因收敛为上一轮当时凭据/会话或临时读取状态问题，不是目标 repo 名称、脚本参数或
K8S pull secret 结构问题。

## 镜像

```text
image: harbor.mnnu.eu.org/ircs/ircs-notification-worker:dev
architecture: linux/arm64
digest: sha256:79462463933647277e6069c61d0081e75ed241ccdff94f264d311d6ee164027b
```

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker -Tag dev -Architecture arm64 -Os linux
```

结果：

```text
BUILD SUCCESSFUL
Pushed harbor.mnnu.eu.org/ircs/ircs-notification-worker:dev for linux/arm64
```

## Dev DB Smoke

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-retention-02810j-smoke.ps1 -StartOpsPortForward -FailOnUnavailable
```

结果：

```text
status=PASS
reason=SEND_HISTORY_RETENTION_CLEANUP_OK
job=notification-mail-history-cleanup-02810j-814314ff5c9c
deletedRows=1
batches=1
oldRemainingAfterCleanup=0
freshRetainedAfterCleanup=true
finalSmokeResidue=0
opsSendHistory.available=true
opsSendHistory.queried=true
opsSendHistory.totalLast24h=1
```

Job 日志显示：

```text
Notification mail send history cleanup result: success=true, deletedRows=1, batches=1, reason=completed
```

## 安全与清理

```text
SMTP sent=false
passwordPrinted=false
recipientPrinted=false
APP_MAIL_ENABLED=false in cleanup Job
APP_NOTIFICATION_LISTENER_ENABLED=false in cleanup Job
DB residue: codex-02810j-* count=0
K8S residue: codex-task=028-10-J Job/Pod=0
```

本轮额外删除了上一轮遗留的失败 Job：

```text
notification-mail-history-cleanup-02810j-b31a56c17b89
```

## 后续 SMTP One-shot 口径

后续 SMTP one-shot 不再把 Gmail 等标准 SMTP 协议邮箱视为不合格；允许标准 SMTP provider。
但必须同时满足：

- 使用专用凭据。
- 单一测试收件人 allowlist。
- 防批量误发。
- 发送后 send history 可审计。
- secret 不输出。

本轮仍未发送 SMTP。

## MARK

```text
MARK 1/5 PASS Harbor unauthorized root cause or verified alternative path recorded
MARK 2/5 PASS notification-worker dev cleanup image is available to K8S without printing secrets
MARK 3/5 PASS cleanup smoke inserts old/fresh fixture and runs one-shot cleanup
MARK 4/5 PASS old fixture deleted, fresh fixture retained, then all smoke fixture removed
MARK 5/5 PASS ops summary queryable and SMTP remains unsent
```
