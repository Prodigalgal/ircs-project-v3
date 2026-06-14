# 028-10-K notification-worker Harbor push / retention smoke unblock

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/done/028-10-j-notification-mail-send-history-retention-cleanup.md`

## 背景

`028-10-J` 已实现 send history retention cleanup，并通过本地 notification-worker tests。
dev DB smoke 被 `harbor.mnnu.eu.org/ircs/ircs-notification-worker unauthorized` 阻塞，
因此尚未 rollout 新 image，也未执行 cleanup Job。

## 目标

- 诊断 Harbor unauthorized：区分本地 Docker auth、Jib credential、仓库权限、脚本参数和目标 repo。
- 优先使用现有安全凭据/脚本路径修复 push；无法修复时给出可验证替代路径。
- 完成 `028-10-J` dev DB cleanup smoke。
- 回写 docs/tasks 总账；通过时将 `028-10-J` MARK 完成，阻塞时记录无残留证据。

## 范围

可改：

```text
deploy/k8s/dev/notification-worker*
scripts/docker/push-service-image-jib.ps1
scripts/smoke/notification-mail-send-history-retention-02810j-smoke.ps1
docs/**
tasks/**
```

不改：

```text
metadata provider
config/content C2
storage
portal/admin/BFF
```

## 验收标准

```text
MARK 1/5 Harbor unauthorized root cause or verified alternative path recorded
MARK 2/5 notification-worker dev cleanup image is available to K8S without printing secrets
MARK 3/5 cleanup smoke inserts old/fresh fixture and runs one-shot cleanup
MARK 4/5 old fixture deleted, fresh fixture retained, then all smoke fixture removed
MARK 5/5 ops summary queryable and SMTP remains unsent
```

## 测试方式

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-retention-02810j-smoke.ps1 -StartOpsPortForward -FailOnUnavailable
```

## 当前记录

- task spec 已在实现/诊断前创建。
- 本轮不得输出 secret；Harbor/K8S secret 只允许做存在性、类型和 registry key 形态检查。
- 2026-06-09 retry worker 启动：先诊断 Harbor push unauthorized，再优先复用现有安全凭据/脚本完成
  `028-10-J` retention dev DB smoke；本轮仍不得发送 SMTP。
- 用户补充后续 SMTP one-shot 口径：允许 Gmail 等标准 SMTP 协议邮箱，但必须使用专用凭据、
  单一测试收件人 allowlist、防批量误发、发送后 send history 可审计，且 secret 不输出。

## Harbor 诊断

- `ircs-system/harbor-secret` 与 `ircs-dev/harbor-secret` 均存在，secret 类型为
  `kubernetes.io/dockerconfigjson`，registry key 形态匹配 Harbor 主机；本任务不输出 secret。
- 现有 `push-service-image-jib.ps1` 参数与目标 repo 正确，Jib 日志显示使用临时
  `DOCKER_CONFIG` 中的 Docker config 对 Harbor push 鉴权。
- 本轮 unauthorized 未复现，`ircs-notification-worker:dev` ARM64 push 成功；阻塞解除。上一轮
  unauthorized 归因收敛为当时凭据/会话或临时读取状态，不是 manifest repo 名称或脚本参数错误。

## 验证结果

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
opsSendHistory.totalLast24h=1
sent=false
passwordPrinted=false
recipientPrinted=false
```

通过：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL in 20s
```

清理验证：

```text
DB residue: select count(*) from notification_mail_send_history where correlation_id like 'codex-02810j-%' => 0
K8S residue: codex-task=028-10-J Job/Pod => 0 after deleting older failed residue notification-mail-history-cleanup-02810j-b31a56c17b89
SMTP: not sent; cleanup Job had APP_MAIL_ENABLED=false and APP_NOTIFICATION_LISTENER_ENABLED=false
```

## MARK

```text
MARK 1/5 PASS Harbor unauthorized root cause or verified alternative path recorded
MARK 2/5 PASS notification-worker dev cleanup image is available to K8S without printing secrets
MARK 3/5 PASS cleanup smoke inserts old/fresh fixture and runs one-shot cleanup
MARK 4/5 PASS old fixture deleted, fresh fixture retained, then all smoke fixture removed
MARK 5/5 PASS ops summary queryable and SMTP remains unsent
```
