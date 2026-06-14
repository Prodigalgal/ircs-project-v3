# 092 028-10-I notification SMTP staging-only one-shot live smoke 报告

状态：已完成

日期：2026-06-09

## 背景

本报告承接 `028-10-H` 的 send history dev DB 与 ops endpoint 闭环，记录本轮
staging-only one-shot SMTP live smoke 的门禁检查、执行结果、恢复证据和遗留项。

## 执行计划

- 先补齐本地需求、报告和子任务 spec。
- 复用并加固 `scripts/smoke/notification-mail-smtp-02810f-preflight.ps1`，让它支持
  028-10-I 的 one-shot 门禁、最多 1 封、send history 查询和恢复证据。
- 读取 live dev 配置和 credential 证据，只输出脱敏摘要。
- 仅在全部门禁通过时执行真实 SMTP；否则停止在 preflight。

## 实现记录

- 加固 `scripts/smoke/notification-mail-smtp-02810f-preflight.ps1`：
  - 新增 `TaskId`、`SliceName` 参数，兼容旧 `028-10-F` 默认输出。
  - 新增 `028-10-I` 的 6 项 MARK 输出。
  - 新增 Rabbit port-forward 参数和 one-shot 分支；只有全部 staging-only 门禁通过时才会
    patch runtime、发布最多 1 条 `notification.mail` 测试消息并查询 send history。
  - 新增 runtime patch/restore 辅助逻辑；未来真正 staging 环境执行时会把
    `APP_MAIL_ENABLED`、`APP_MAIL_SEND_HISTORY_ENABLED`、`APP_NOTIFICATION_LISTENER_ENABLED`
    等临时开启，并恢复原 env 形态。
  - 保持 password/token/recipient 不全量输出；邮箱只记录脱敏摘要。

## 验证记录

PowerShell 语法解析结果：`PowerShell parse OK`。

无发送 preflight：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -TaskId '028-10-I' -SliceName 'smtp-staging-only-one-shot-live-smoke' -CredentialBaseUrl 'http://127.0.0.1:18092' -CredentialPort 18092 -OpsBaseUrl 'http://127.0.0.1:18132' -OpsPort 18132 -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward
```

结果摘要：

```text
status=SKIPPED
reason=SMTP_PREFLIGHT_BLOCKED
sent=false
blockReasons=ALLOW_SEND_NOT_SET, SMTP_CONFIG_NOT_STAGING_ONLY, SAFE_TEST_RECIPIENT_NOT_CONFIRMED
sendHistoryTablePresent=true
opsSendHistory.available=true
mailCredentialAvailable=true
passwordPrinted=false
recipientPrinted=false
APP_MAIL_ENABLED=false
```

带 `AllowSend` 与安全测试收件人形态的 preflight：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -TaskId '028-10-I' -SliceName 'smtp-staging-only-one-shot-live-smoke' -CredentialBaseUrl 'http://127.0.0.1:18093' -CredentialPort 18093 -OpsBaseUrl 'http://127.0.0.1:18133' -OpsPort 18133 -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward -AllowSend -TestRecipient 'codex-smoke@mailinator.com'
```

结果摘要：

```text
MARK 1/6 K8S notification-worker mail host/from/enabled gate checked without printing secrets
MARK 2/6 MAIL credential evidence checked without password output
MARK 3/6 unsafe or unclear recipient/config blocks SMTP and emits evidence
MARK 4/6 one-shot SMTP smoke not executed because preflight gate blocked it
MARK 5/6 send history row not expected because SMTP was not sent
MARK 6/6 no runtime patch applied and dev APP_MAIL_ENABLED remains false
status=SKIPPED
reason=SMTP_PREFLIGHT_BLOCKED
sent=false
blockReasons=SMTP_CONFIG_NOT_STAGING_ONLY
allowSend=true
safeTestRecipient=true
recipientMasked=co***@mailinator.com
host=smtp.gmail.com
fromMasked=HuaWai System <hu***@gmail.com>
sendHistoryTablePresent=true
opsSendHistory.available=true
totalLast24h=0
passwordPrinted=false
recipientPrinted=false
```

旧 `028-10-F` 兼容 preflight：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -TaskId '028-10-F' -SliceName 'smtp-staging-live-preflight' -CredentialBaseUrl 'http://127.0.0.1:18094' -CredentialPort 18094 -OpsBaseUrl 'http://127.0.0.1:18134' -OpsPort 18134 -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward
```

结果：`MARK 1/5` 到 `MARK 5/5` 输出正常，`status=SKIPPED`，
`reason=SMTP_PREFLIGHT_BLOCKED`。

## 恢复与清理

```text
APP_MAIL_ENABLED=valueFrom configMap ircs-dev-app-config/APP_MAIL_ENABLED
ircs-dev-app-config APP_MAIL_ENABLED=false
ircs-notification-worker readyReplicas=1
APP_MAIL_SEND_HISTORY_ENABLED 未被持久写入 deployment
APP_CREDENTIAL_INTERNAL_TOKEN 未被持久写入 deployment
本轮唯一使用的临时 port-forward 端口 18092/18093/18094/18132/18133/18134/19079 无残留进程
notification_mail_send_history recent24hCount=0
```

## 结论

028-10-I 已完成安全门禁与脚本闭环，但真实 SMTP 未发送。当前 dev 配置使用 Gmail
真实 SMTP host/from，不能视为 staging-only；即使用户允许真实测试并显式传入安全测试收件人，
脚本也正确阻断在 `SMTP_CONFIG_NOT_STAGING_ONLY`。

send history 审计写入未发生，原因是没有发送。表和 ops endpoint 均可查，
`totalLast24h=0` 是本轮没有污染业务审计表的证据。后续若要真实发送，需要先切换到
Mailtrap/Mailosaur/Ethereal/smtp4dev 或等价 staging-only provider。
