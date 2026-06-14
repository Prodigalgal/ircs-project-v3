# 028-10-K notification-worker Harbor push / retention smoke unblock

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/done/028-10-j-notification-mail-send-history-retention-cleanup.md`

## 目标

- 诊断 `harbor.mnnu.eu.org/ircs/ircs-notification-worker` push unauthorized 的直接原因。
- 优先使用现有安全凭据和脚本路径修复或绕过 push 阻塞，不硬编码 secret。
- 完成 `028-10-J` dev DB cleanup smoke：旧临时记录删除、新临时记录保留、ops summary 可查。
- 明确 SMTP 未发送、fixture/job 清理和最终 MARK 状态。

## 范围

包含：

```text
deploy/k8s/dev/notification-worker*
scripts/docker/push-service-image-jib.ps1
scripts/smoke/notification-mail-send-history-retention-02810j-smoke.ps1
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
secret 明文落盘或输出
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

优先：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-retention-02810j-smoke.ps1 -StartOpsPortForward -FailOnUnavailable
```

按需降级：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
kubectl -n ircs-dev get secret harbor-secret -o jsonpath="{.type}"
kubectl -n ircs-dev get deployment ircs-notification-worker -o jsonpath="{.spec.template.spec.containers[0].image}"
```

不跑：

```text
真实 SMTP
Rabbit mail queue 投递
生产数据破坏性清理
```

## 2026-06-09 retry 补充口径

- 本轮只做 Harbor push/替代路径诊断与 retention cleanup smoke，不发送 SMTP。
- 后续 SMTP one-shot 不再把 Gmail 等标准 SMTP 协议邮箱视为不合格；允许使用标准 SMTP
  provider，但必须满足专用凭据、单一测试收件人 allowlist、防批量误发、send history 可审计、
  secret 不输出等门禁。

## 2026-06-09 retry 验收记录

Harbor 诊断：

- `ircs-system/harbor-secret` 与 `ircs-dev/harbor-secret` 均存在，类型为
  `kubernetes.io/dockerconfigjson`，registry key 为 Harbor 主机级别；未在文档输出 secret。
- 现有脚本路径可用，Jib 从临时 `DOCKER_CONFIG` 读取凭据并成功 push；上一轮
  unauthorized 未复现，根因收敛为当时凭据/会话或临时读取状态问题，不是目标 repo 或脚本参数错误。

镜像与 cleanup smoke：

```text
push command: powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker -Tag dev -Architecture arm64 -Os linux
image: harbor.mnnu.eu.org/ircs/ircs-notification-worker:dev
digest: sha256:79462463933647277e6069c61d0081e75ed241ccdff94f264d311d6ee164027b
smoke command: powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-retention-02810j-smoke.ps1 -StartOpsPortForward -FailOnUnavailable
smoke status: PASS SEND_HISTORY_RETENTION_CLEANUP_OK
cleanup Job: notification-mail-history-cleanup-02810j-814314ff5c9c
cleanup result: deletedRows=1, batches=1
oldRemainingAfterCleanup=0
freshRetainedAfterCleanup=true
finalSmokeResidue=0
ops summary queried=true, available=true, totalLast24h=1
SMTP sent=false, passwordPrinted=false, recipientPrinted=false
post-cleanup DB residue for codex-02810j-*=0
codex-task=028-10-J Job/Pod residue=0 after deleting older failed residue
```

最终 MARK：

```text
MARK 1/5 PASS Harbor unauthorized root cause or verified alternative path recorded
MARK 2/5 PASS notification-worker dev cleanup image is available to K8S without printing secrets
MARK 3/5 PASS cleanup smoke inserts old/fresh fixture and runs one-shot cleanup
MARK 4/5 PASS old fixture deleted, fresh fixture retained, then all smoke fixture removed
MARK 5/5 PASS ops summary queryable and SMTP remains unsent
```
