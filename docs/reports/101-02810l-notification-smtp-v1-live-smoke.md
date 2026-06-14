# 101 028-10-L notification SMTP V1 credential one-shot live smoke

## 结论

`028-10-L-retry` 已通过。notification-worker 在 Ready 恢复后，使用 V1 导入到 V3
dev credential pool 的 MAIL 凭据完成 1 次真实 SMTP one-shot smoke。

本轮只发送 1 封测试邮件；收件人为 V1 `MAIL_FROM` 自投递测试目标，报告只记录脱敏值。
`notification_mail_send_history` 与 ops endpoint 均查到同一条审计记录。该审计记录按业务审计预期
保留，不作为污染清理。

用户随后人工确认测试邮箱已收到该邮件；该确认只作为收件箱层面的补充证据，不记录完整邮箱地址。

## 凭据与门禁

凭据来源只记录路径/key/row 证据，不记录 secret：

```text
V1 config source: D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config\01-secrets.yaml
V1 config source: D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config\02-configmap.yaml
V3 credential source: ircs-dev.sys_credentials provider=MAIL
MAIL credential row: id=f0a0a267-f124-40c0-82e1-1ad323370cc7, name=System Notification Sender, enabled=true, priority=100
payload keys: username,password
passwordPrinted=false
tokenPrinted=false
```

门禁结果：

```text
AllowSend=true
standardSmtpProviderAllowed=true
stagingOnlyConfig=false
smtpProviderGate=true
v1MailFromTestTarget=true
safeTestRecipient=true
lowFrequencyOneShot=true
sendHistoryTablePresent=true
deliveryModeSmtp=true
standingDevAppMailEnabledFalse=true
```

## 执行命令

实际命令使用 `MAIL_FROM` 从 `ircs-dev-app-config` 读取单一自投递目标；命令记录中不写真实收件人：

```powershell
$mailFrom = kubectl -n ircs-dev get configmap ircs-dev-app-config -o jsonpath='{.data.MAIL_FROM}'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -TaskId '028-10-L' -SliceName 'notification-smtp-v1-live-smoke-retry' -CredentialBaseUrl 'http://127.0.0.1:18095' -CredentialPort 18095 -OpsBaseUrl 'http://127.0.0.1:18135' -OpsPort 18135 -RabbitBaseUrl 'http://127.0.0.1:19080' -RabbitPort 19080 -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward -StartRabbitPortForward -AllowSend -TestRecipient $mailFrom
```

结果：

```text
MARK 1/6 K8S notification-worker mail host/from/enabled gate checked without printing secrets
MARK 2/6 MAIL credential evidence checked without password output
MARK 3/6 one-shot SMTP gates passed before runtime patch
MARK 4/6 one-shot SMTP smoke message published at most once
MARK 5/6 send history row written and ops endpoint queried
MARK 6/6 temporary runtime switches restored and helper resources ready for cleanup
status=PASS
reason=SMTP_V1_ONE_SHOT_SMOKE_OK
trace=02810l-smtp-smoke-20260609222338
sent=true
publishResult.routed=true
mailboxReceiptConfirmedByUser=true
```

## Send History 证据

DB row：

```text
correlation_id=02810l-smtp-smoke-20260609222338
recipient_masked=HuaWai System <hu***@gmail.com>
delivery_mode=smtp
status=sent
credential_id=f0a0a267-f124-40c0-82e1-1ad323370cc7
failure_code=null
failure_message=null
created_at=2026-06-09T14:25:28.383121+00:00
updated_at=2026-06-09T14:25:28.383121+00:00
recipientPrinted=false
```

Ops endpoint：

```text
GET /api/v1/ops/notification-mail-send-history?size=5&correlationId=02810l-smtp-smoke-20260609222338
opsAvailable=true
totalElements=1
status=SENT
provider=SMTP
error=
timestamp=2026-06-09T14:25:28.383121Z
correlationId=02810l-smtp-smoke-20260609222338
recipientMasked=HuaWai System <hu***@gmail.com>
recipientPrinted=false
```

## 恢复与清理

runtime restore：

```text
deployment ready=1/1
APP_MAIL_ENABLED=configMap:ircs-dev-app-config/APP_MAIL_ENABLED
APP_MAIL_SEND_HISTORY_ENABLED=absent
APP_NOTIFICATION_LISTENER_ENABLED=absent
APP_MAIL_CREDENTIAL_PROVIDER=absent
APP_MAIL_CREDENTIAL_SERVICE_BASE_URL=absent
APP_CREDENTIAL_INTERNAL_TOKEN=absent
pod=ircs-notification-worker-78cd4697cd-mkfbv phase=Running ready=True restarts=0
```

cleanup：

```text
no 028-10-L job/pod residue
no smoke port-forward process residue
auditRecordRetained=true
auditRecordRetainedReason=notification_mail_send_history is business audit history
```

## MARK

```text
MARK 1/6 PASS V1/V3 SMTP config and MAIL credential evidence checked without printing secrets
MARK 2/6 PASS standard SMTP one-shot gates pass only with explicit AllowSend and one safe recipient
MARK 3/6 PASS notification-worker runtime switches are temporarily enabled and restored
MARK 4/6 PASS exactly one notification.mail test message is published
MARK 5/6 PASS send history DB row and ops endpoint expose status/error/timestamp/recipient evidence safely
MARK 6/6 PASS temporary port-forward/helper resources are cleaned; audit row retention is documented
```
