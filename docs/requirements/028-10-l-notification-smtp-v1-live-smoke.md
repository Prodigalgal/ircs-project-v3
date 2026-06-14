# 028-10-L notification SMTP V1 credential one-shot live smoke

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

## 背景

`028-10-I` 已完成 one-shot SMTP 安全门禁，但当时把 Gmail/标准 SMTP provider 视为非 staging-only，
因此没有真实发送。用户已授权本轮可以使用 V1 的真实 SMTP 凭据，并允许 Gmail 等标准 SMTP
协议邮箱执行最多 1 封真实测试邮件。

V1 配置来源只记录路径和 key，不记录 secret：

```text
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config\01-secrets.yaml
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config\02-configmap.yaml
V3 ircs-dev.sys_credentials provider=MAIL
```

## 目标

- 对照 V1 SMTP 配置来源与 V3 notification-worker runtime 配置，记录凭据来源和启用门禁。
- 临时启用 notification-worker SMTP、credential-service MAIL credential、send history 和 mail listener。
- 通过 Rabbit `notification.mail` 发布最多 1 条 one-shot 测试消息，最多发送 1 封测试邮件。
- 通过 `notification_mail_send_history` 和 ops endpoint 验证 provider/status/error/timestamp/recipient 展示证据。
- 发送后恢复 `APP_MAIL_ENABLED` 等 runtime env 原值，并清理 port-forward/helper 资源。

## 非目标

- 不硬编码、打印或写入 SMTP password/token。
- 不向真实业务用户或批量列表发送邮件。
- 不修改 metadata provider、config/content C2、storage、portal/admin/BFF。
- 不清理业务审计记录；本轮真实发送产生的 send history 记录按业务审计预期保留。

## 输入

```text
scripts/smoke/notification-mail-smtp-02810f-preflight.ps1
deploy/k8s/dev/notification-worker-dev.yaml
docs/k8s/v1-config-environment-map.md
tasks/done/028-14-v1-credential-import-for-live-parity.md
```

## 输出

```text
docs/reports/101-02810l-notification-smtp-v1-live-smoke.md
tasks/done/028-10-l-notification-smtp-v1-live-smoke.md
tasks/in-progress/028-10-production-governor-credential-pool-parity.md
tasks/index.md
```

## 影响范围

```text
scripts/smoke/notification-mail-smtp-02810f-preflight.ps1
docs/**
tasks/**
```

按需但优先不改：

```text
deploy/k8s/dev/notification-worker*
services/ircs-notification-worker/**
```

## 风险

- Gmail/标准 SMTP 可能接受发送但不代表最终收件箱投递成功；send history 的 `SENT` 语义仍是 delivery
  层接受发送意图。
- 使用真实凭据可能触发 provider 风控；本任务只允许 one-shot、单一收件人。
- ops endpoint 保留业务审计完整 recipient；报告和脚本输出必须脱敏或证明为安全测试收件人。

## 验收标准

```text
MARK 1/6 V1/V3 SMTP config and MAIL credential evidence checked without printing secrets
MARK 2/6 standard SMTP one-shot gates pass only with explicit AllowSend and one safe recipient
MARK 3/6 notification-worker runtime switches are temporarily enabled and restored
MARK 4/6 exactly one notification.mail test message is published
MARK 5/6 send history DB row and ops endpoint expose status/error/timestamp/recipient evidence safely
MARK 6/6 temporary port-forward/helper resources are cleaned; audit row retention is documented
```

## 测试方式

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -TaskId '028-10-L' -SliceName 'notification-smtp-v1-live-smoke' -CredentialBaseUrl 'http://127.0.0.1:18095' -CredentialPort 18095 -OpsBaseUrl 'http://127.0.0.1:18135' -OpsPort 18135 -RabbitBaseUrl 'http://127.0.0.1:19080' -RabbitPort 19080 -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward -StartRabbitPortForward -AllowSend -TestRecipient '<single safe test recipient>'
```

## 当前状态

已完成；本轮为 `028-10-L-retry`。主线程只读核查已确认 `ircs-notification-worker`
Deployment `READY 1/1`、Pod `Running`、`restarts=0`，最近日志只有正常 Started，且没有
`No default constructor`；`APP_MAIL_ENABLED` 已恢复为
`ircs-dev-app-config/APP_MAIL_ENABLED`，`028-10-L` Job/Pod 无残留。

执行约束：

- 使用 V1 配置/凭据来源读取真实 SMTP 配置，凭据来源只记录文件/key/credential row 证据，不记录
  password/token 全量。
- Gmail/标准 SMTP provider 在本轮授权范围内；仍必须显式 `AllowSend`、单一安全测试收件人、最多发送
  1 封。
- 发送前后保存并恢复 `APP_MAIL_ENABLED`、`APP_MAIL_SEND_HISTORY_ENABLED`、
  `APP_NOTIFICATION_LISTENER_ENABLED`、credential provider/internal token 等 runtime 状态。
- 通过 DB 与 ops endpoint 验证 `notification_mail_send_history`；真实发送产生的审计记录按业务审计
  保留，不作为污染清理。

完成结果：

```text
trace=02810l-smtp-smoke-20260609222338
sent=true
publishResult.routed=true
sendHistory.status=sent
ops.status=SENT
mailboxReceiptConfirmedByUser=true
runtimeRestored=true
cleanupVerified=true
MARK 6/6 PASS
```
