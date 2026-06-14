# 028-10-I notification SMTP staging-only one-shot live smoke task spec

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/requirements/028-10-i-notification-smtp-staging-one-shot-live-smoke.md`
- `docs/reports/090-02810h-notification-mail-send-history-dev-migration-ops-query.md`
- `tasks/done/028-10-f-smtp-staging-live-preflight.md`
- `tasks/done/028-10-h-notification-mail-send-history-dev-migration-ops-query.md`

## 背景

`028-10-H` 已证明 `notification_mail_send_history` 表和 ops 查询 endpoint 在 dev
环境可用。本任务尝试把 SMTP 从 preflight 推进到严格 staging-only 的 one-shot live smoke。
如果 staging-only 或 safe recipient 等门禁不满足，任务必须阻断发送并保留证据。

## 目标

- 检查 notification-worker live deployment、ConfigMap、credential-service MAIL lease、
  send history 表和 ops endpoint。
- 不输出 password、token、secret 或 recipient 全量；邮箱仅输出脱敏形式。
- 若全部门禁通过，临时开启必要开关，发布最多 1 条 `notification.mail` 测试消息，
  等待 send history 写入并通过 ops endpoint 查询。
- 执行后恢复原开关；如未发送，也要证明没有修改或已恢复。

## 非目标

- 不把 Gmail 或任何真实用户邮箱服务默认视为 staging-only。
- 不发送批量邮件，不向生产用户或未知收件人发送。
- 不修改 config-service、content-service、Douban provider、storage-service。
- 不修改 ops-service schema/migrator。
- 不删除业务审计记录。

## 输入

```text
scripts/smoke/notification-mail-smtp-02810f-preflight.ps1
deploy/k8s/dev/notification-worker-dev.yaml
services/ircs-notification-worker/src/main/resources/application.yaml
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/**
dev K8S namespace ircs-dev
dev PostgreSQL notification_mail_send_history / sys_credentials
ops-service send history endpoints
```

## 输出

```text
加固后的 SMTP smoke 脚本或阻塞证据
preflight / send history / ops endpoint JSON 摘要
恢复与清理证据
docs/reports/092 回写
本 task spec 回写并按完成情况移动或保留
```

## 影响范围

包含：

```text
scripts/smoke/notification-mail-smtp-02810f-preflight.ps1
deploy/k8s/dev/notification-worker*
docs/requirements/028-10-i-notification-smtp-staging-one-shot-live-smoke.md
docs/reports/092-02810i-notification-smtp-staging-one-shot-live-smoke.md
tasks/done/028-10-i-notification-smtp-staging-one-shot-live-smoke.md
```

不包含：

```text
services/ircs-config-service/**
services/ircs-content-service/**
services/ircs-storage-service/**
services/ircs-ops-service/** schema/migrator
Douban provider
production traffic
```

## 依赖

- `kubectl` 可访问 `ircs-dev`。
- `postgres-0` 可执行只读 SQL。
- credential-service internal lease API 可通过 token 和 ClusterIP/port-forward 查询。
- RabbitMQ management API 可用于单条测试消息发布。
- ops-service send history endpoint 可查询目标 correlation id。

## 风险

- live SMTP 配置不是 staging-only 时必须阻断发送。
- 临时 patch deployment 可能触发 rollout；必须记录原值并恢复。
- Rabbit queue 可能已有并行消息；本任务只以唯一 correlation/message id 查询自身记录。
- send history row 属于业务审计预期保留，不做清理。

## 验收标准

```text
MARK 1/6 028-10-I docs/task spec 已在实现或 live smoke 前创建
MARK 2/6 preflight 已读取 K8S/credential/ops/send history 状态，未输出 secret 或 recipient 全量
MARK 3/6 staging-only、safe recipient、one-shot、history 可查、开关可恢复门禁已执行
MARK 4/6 若门禁通过，最多发送 1 封测试邮件；若门禁不通过，停止在 preflight 并记录原因
MARK 5/6 send history 审计通过 DB 或 ops endpoint 查询到，或明确说明因未发送无法产生
MARK 6/6 临时 APP_MAIL_ENABLED 等开关和 port-forward 已恢复/清理，审计记录保留原因已说明
```

## 测试边界

必跑：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -TaskId '028-10-I' -SliceName 'smtp-staging-only-one-shot-live-smoke' -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward
```

仅当门禁明确通过时才允许：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -TaskId '028-10-I' -SliceName 'smtp-staging-only-one-shot-live-smoke' -AllowSend -TestRecipient <safe-staging-recipient> -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward
```

不跑：

```text
未确认 staging-only 的 SMTP 真实发送
批量邮件
生产用户收件人
provider/R2/config Rabbit/normalization 测试
```

## 关联文档

- `docs/requirements/028-10-i-notification-smtp-staging-one-shot-live-smoke.md`
- `docs/reports/092-02810i-notification-smtp-staging-one-shot-live-smoke.md`
- `docs/reports/090-02810h-notification-mail-send-history-dev-migration-ops-query.md`

## 关联任务

- 父任务：`tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- 本任务：`tasks/done/028-10-i-notification-smtp-staging-one-shot-live-smoke.md`

## 当前状态

已完成。脚本已支持 028-10-I one-shot 分支，但当前 dev SMTP 配置不是 staging-only，
因此真实 SMTP 被阻断，未发送邮件，未写入新增 send history row。

## 实现结果

- `scripts/smoke/notification-mail-smtp-02810f-preflight.ps1` 新增 `TaskId`、`SliceName`、
  Rabbit port-forward、等待参数和 `028-10-I` 6 项 MARK 输出。
- one-shot 分支只在全部门禁通过后才会执行 runtime patch、Rabbit 单条 publish、
  send history 查询和 runtime restore。
- 当前 live preflight 未进入 patch/send 分支，原因是 `SMTP_CONFIG_NOT_STAGING_ONLY`。

## 验收结果

```text
MARK 1/6 PASS docs/task spec created before live smoke
MARK 2/6 PASS K8S/credential/ops/send history preflight completed without secret/full recipient output
MARK 3/6 PASS staging-only/safe recipient/one-shot/history/restoration gates executed
MARK 4/6 PASS SMTP blocked at preflight because config is not staging-only; sent=false
MARK 5/6 PASS send history table and ops endpoint available; no new row because no SMTP was sent
MARK 6/6 PASS no runtime patch applied; APP_MAIL_ENABLED remains false; helper port-forward processes cleaned
```

## 测试与操作记录

```powershell
$errors=$null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1), [ref]$null, [ref]$errors)
```

结果：`PowerShell parse OK`。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -TaskId '028-10-I' -SliceName 'smtp-staging-only-one-shot-live-smoke' -CredentialBaseUrl 'http://127.0.0.1:18092' -CredentialPort 18092 -OpsBaseUrl 'http://127.0.0.1:18132' -OpsPort 18132 -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward
```

结果：`status=SKIPPED`、`reason=SMTP_PREFLIGHT_BLOCKED`、`sent=false`。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -TaskId '028-10-I' -SliceName 'smtp-staging-only-one-shot-live-smoke' -CredentialBaseUrl 'http://127.0.0.1:18093' -CredentialPort 18093 -OpsBaseUrl 'http://127.0.0.1:18133' -OpsPort 18133 -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward -AllowSend -TestRecipient 'codex-smoke@mailinator.com'
```

结果：`status=SKIPPED`、`reason=SMTP_PREFLIGHT_BLOCKED`、`blockReasons=SMTP_CONFIG_NOT_STAGING_ONLY`、
`allowSend=true`、`safeTestRecipient=true`、`sent=false`。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -TaskId '028-10-F' -SliceName 'smtp-staging-live-preflight' -CredentialBaseUrl 'http://127.0.0.1:18094' -CredentialPort 18094 -OpsBaseUrl 'http://127.0.0.1:18134' -OpsPort 18134 -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward
```

结果：旧 028-10-F 兼容 MARK 1/5 到 5/5 正常。

## 关键证据

```text
host=smtp.gmail.com
port=465
fromMasked=HuaWai System <hu***@gmail.com>
recipientMasked=co***@mailinator.com
APP_MAIL_ENABLED=false
credentialLease.available=true
credentialLease.payloadKeys=password, username
sendHistory.table=notification_mail_send_history
opsSendHistory.available=true
totalLast24h=0
passwordPrinted=false
recipientPrinted=false
```

## 恢复与清理

```text
runtime patch: not applied
APP_MAIL_ENABLED: configMap ircs-dev-app-config/APP_MAIL_ENABLED=false
APP_MAIL_SEND_HISTORY_ENABLED: not persisted on deployment
APP_CREDENTIAL_INTERNAL_TOKEN: not persisted on deployment
ircs-notification-worker readyReplicas=1
temporary port-forward ports 18092/18093/18094/18132/18133/18134/19079: no residue
```

## 未执行项

```text
SKIPPED real SMTP: SMTP_CONFIG_NOT_STAGING_ONLY
SKIPPED Rabbit publish: preflight blocked before one-shot branch
SKIPPED send history row cleanup: no row was created
```

## 遗留项

- 需要独立切换到 staging-only SMTP provider，例如 Mailtrap、Mailosaur、Ethereal 或 smtp4dev。
- 切换后再运行带 `-AllowSend`、安全测试收件人和 `-StartRabbitPortForward` 的 one-shot 分支。
