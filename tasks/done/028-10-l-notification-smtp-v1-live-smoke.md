# 028-10-L notification SMTP V1 credential one-shot live smoke

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

## Compact Task Spec

目标：

- 在 notification-worker Ready 恢复后，使用 V1 真实 SMTP 凭据执行最多 1 封 one-shot live smoke。
- 通过 `notification_mail_send_history` 和 ops endpoint 验证审计记录。
- 发送后恢复 runtime 开关并清理临时 helper 资源。

范围：

```text
scripts/smoke/notification-mail-smtp-02810f-preflight.ps1
docs/**
tasks/**
```

非目标：

- 不硬编码、不打印 SMTP password/token。
- 不向真实业务用户或批量列表发送邮件。
- 不修改 metadata provider、config/content C2、storage、portal/admin/BFF。
- 不清理真实发送产生的业务审计记录。

## 验证结果

通过：

```powershell
$mailFrom = kubectl -n ircs-dev get configmap ircs-dev-app-config -o jsonpath='{.data.MAIL_FROM}'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -TaskId '028-10-L' -SliceName 'notification-smtp-v1-live-smoke-retry' -CredentialBaseUrl 'http://127.0.0.1:18095' -CredentialPort 18095 -OpsBaseUrl 'http://127.0.0.1:18135' -OpsPort 18135 -RabbitBaseUrl 'http://127.0.0.1:19080' -RabbitPort 19080 -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward -StartRabbitPortForward -AllowSend -TestRecipient $mailFrom
```

结果：

```text
status=PASS
reason=SMTP_V1_ONE_SHOT_SMOKE_OK
trace=02810l-smtp-smoke-20260609222338
sent=true
publishResult.routed=true
mailboxReceiptConfirmedByUser=true
passwordPrinted=false
tokenPrinted=false
```

send history：

```text
correlation_id=02810l-smtp-smoke-20260609222338
recipientMasked=HuaWai System <hu***@gmail.com>
delivery_mode=smtp
status=sent
credential_id=f0a0a267-f124-40c0-82e1-1ad323370cc7
failure_code=null
failure_message=null
created_at=2026-06-09T14:25:28.383121+00:00
ops.totalElements=1
ops.status=SENT
ops.provider=SMTP
recipientPrinted=false
```

恢复与清理：

```text
deployment ready=1/1
APP_MAIL_ENABLED=configMap:ircs-dev-app-config/APP_MAIL_ENABLED
APP_MAIL_SEND_HISTORY_ENABLED=absent
APP_NOTIFICATION_LISTENER_ENABLED=absent
APP_MAIL_CREDENTIAL_PROVIDER=absent
APP_MAIL_CREDENTIAL_SERVICE_BASE_URL=absent
APP_CREDENTIAL_INTERNAL_TOKEN=absent
pod=ircs-notification-worker-78cd4697cd-mkfbv phase=Running ready=True restarts=0
no 028-10-L job/pod residue
no smoke port-forward process residue
auditRecordRetained=true
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
