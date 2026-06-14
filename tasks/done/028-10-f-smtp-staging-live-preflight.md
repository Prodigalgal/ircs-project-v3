# 028-10-F SMTP staging live preflight / optional one-shot smoke task spec

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `tasks/done/028-10-e-notification-send-history-schema-ops-query.md`
- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`

## 背景

`028-10-E` 已完成 notification send history schema contract 与 ops-service 只读查询。
当前允许真实测试，但 SMTP smoke 存在误发邮件、外部 provider 风控和投递语义误读风险。
本切片必须先检查 dev/K8S/V1 credential 与 notification-worker 常驻 mail gate，只有确认
staging-only、安全测试收件人、低频一封和配置清晰时，才允许执行 one-shot SMTP smoke。

## 目标

- 检查 dev K8S 配置、credential-service MAIL credential 和 V1/dev MAIL credential 证据。
- 确认 `host`、`from`、`to/test recipient`，不打印 `password`、token 或 secret。
- 保持常驻 dev `APP_MAIL_ENABLED=false`，若临时开启必须恢复。
- 若收件人或 staging-only 门禁不明确，只输出 blocked preflight evidence，不发送 SMTP。
- 若门禁全部通过，只执行一封 one-shot SMTP smoke，并记录 send history row、status、
  provider response 或 exception；不宣称真实投递到 inbox。

## 非目标

- 不修改 provider/R2/config Rabbit/normalization。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/reports/089`、`docs/migrations/089`。
- 不删除 send history 审计记录。
- 不扩大 mail listener、Rabbit queue 或生产 traffic。
- 不把 `SENT` 解释为 inbox 已收到。

## 输入

```text
deploy/k8s/dev/notification-worker-dev.yaml
tasks/done/028-10-e-notification-send-history-schema-ops-query.md
tasks/in-progress/028-10-production-governor-credential-pool-parity.md
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/**
services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/**
dev K8S namespace ircs-dev
dev PostgreSQL credential / notification_mail_send_history tables
```

## 输出

```text
scripts/smoke/notification-mail-smtp-02810f-preflight.ps1
preflight 或 one-shot smoke JSON 摘要
send history / ops query evidence
本 task spec 的实现结果、验证情况和剩余风险回写
```

## 影响范围

包含：

```text
scripts/smoke/*mail*
tasks/in-progress/028-10-f-smtp-staging-live-preflight.md
tasks/done/028-10-f-smtp-staging-live-preflight.md
```

不包含：

```text
docs/index.md
tasks/index.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
provider/R2/config Rabbit/normalization
deploy/k8s/dev/*.yaml 修改
production runner
```

## 依赖

- `kubectl` 能访问 `ircs-dev`。
- `postgres-0` 可执行只读 SQL。
- 如执行 one-shot smoke，必须显式提供安全测试收件人，并通过 staging-only gate。
- send history 表若尚未落库，本切片只能阻断并记录 schema missing evidence。

## 风险

- SMTP 可能向真实邮箱发送邮件，必须默认阻断，除非收件人通过安全测试地址校验。
- K8S `MAIL_FROM` 当前可能是真实账号，不能仅凭 from 判断 staging-only。
- credential 输出若包含 secret 会泄露密码，脚本必须只输出是否存在和脱敏摘要。
- `notification_mail_send_history` 如果未落 dev DB，会导致无法记录真实 smoke 审计。

## 验收标准

```text
MARK 1/5 028-10-F task spec created before smoke implementation
MARK 2/5 preflight checks K8S APP_MAIL_ENABLED, MAIL host/from and credential availability without password output
MARK 3/5 unsafe or unclear recipient/config blocks SMTP and emits evidence
MARK 4/5 safe path can perform at most one one-shot smoke with send history / ops query evidence
MARK 5/5 dev APP_MAIL_ENABLED remains or is restored to false
```

## 测试边界

必跑：

```powershell
.\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1
```

仅当门禁明确通过时才跑：

```powershell
.\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -AllowSend -TestRecipient <safe-test-address>
```

不跑：

```text
未确认收件人的 SMTP
高频 SMTP
生产收件人
provider/R2/config Rabbit/normalization 测试
```

## 关联文档

- `tasks/done/028-10-e-notification-send-history-schema-ops-query.md`
- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`

## 关联任务

- 父任务：`tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- 本任务：`tasks/in-progress/028-10-f-smtp-staging-live-preflight.md`

## 当前状态

已完成。本切片执行到 live preflight 并因门禁不满足而阻断真实 SMTP；未发送邮件，未修改
K8S manifest，未删除审计记录。

## 实现结果

- 新增 `scripts/smoke/notification-mail-smtp-02810f-preflight.ps1`。
- 脚本默认只做 preflight，不发送 SMTP、不写 K8S 配置。
- 脚本检查并输出：
  - notification-worker `APP_MAIL_ENABLED`、SMTP host/port/from、delivery mode、credential provider、
    send history enablement。
  - `sys_credentials` 中 `MAIL` credential 摘要。
  - credential-service internal lease API 中 `MAIL` credential 摘要。
  - `notification_mail_send_history` 表存在性。
  - ops-service send history summary/query 可用性。
- 输出中脱敏邮箱，只报告 `passwordPresent/passwordPrinted=false`，不打印 password/token/secret。

## 验收结果

```text
MARK 1/5 PASS 028-10-F task spec created before smoke implementation
MARK 2/5 PASS preflight checks K8S APP_MAIL_ENABLED, MAIL host/from and credential availability without password output
MARK 3/5 PASS unsafe or unclear recipient/config blocks SMTP and emits evidence
MARK 4/5 BLOCKED one-shot SMTP smoke was not executed because staging-only/send-history gates failed
MARK 5/5 PASS dev APP_MAIL_ENABLED remained false
```

## 测试结果

通过默认 preflight：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1
```

结果摘要：

```text
status=SKIPPED
reason=SMTP_PREFLIGHT_BLOCKED
sent=false
APP_MAIL_ENABLED=false
host=smtp.gmail.com
fromMasked=HuaWai System <hu***@gmail.com>
credentialSql.available=true
credentialLease.available=false
sendHistory.table=null
blockReasons=ALLOW_SEND_NOT_SET, SMTP_CONFIG_NOT_STAGING_ONLY, SAFE_TEST_RECIPIENT_NOT_CONFIRMED,
             MAIL_CREDENTIAL_UNAVAILABLE, SEND_HISTORY_TABLE_MISSING, SEND_HISTORY_NOT_ENABLED_IN_DEPLOYMENT
```

通过 credential-service internal lease preflight：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -UseKubernetesSecretToken -StartCredentialPortForward
```

结果摘要：

```text
status=SKIPPED
reason=SMTP_PREFLIGHT_BLOCKED
sent=false
credentialLease.available=true
credentialLease.provider=MAIL
credentialLease.name=System Notification Sender
credentialLease.usernameMasked=hu***@gmail.com
credentialLease.payloadKeys=password, username
passwordPrinted=false
blockReasons=ALLOW_SEND_NOT_SET, SMTP_CONFIG_NOT_STAGING_ONLY, SAFE_TEST_RECIPIENT_NOT_CONFIRMED,
             SEND_HISTORY_TABLE_MISSING, SEND_HISTORY_NOT_ENABLED_IN_DEPLOYMENT
```

通过显式 `-AllowSend` + 安全测试收件人形态 + ops query preflight：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -AllowSend -TestRecipient codex-smoke@mailinator.com -UseKubernetesSecretToken -StartCredentialPortForward -StartOpsPortForward
```

结果摘要：

```text
status=SKIPPED
reason=SMTP_PREFLIGHT_BLOCKED
sent=false
recipientMasked=co***@mailinator.com
safeTestRecipient=true
allowSend=true
mailCredentialAvailable=true
standingDevAppMailEnabledFalse=true
stagingOnlyConfig=false
sendHistoryTablePresent=false
sendHistoryEnabledInDeployment=false
opsSendHistory.queried=true
opsSendHistory.available=false
opsSendHistory.error=404 not found
blockReasons=SMTP_CONFIG_NOT_STAGING_ONLY, SEND_HISTORY_TABLE_MISSING, SEND_HISTORY_NOT_ENABLED_IN_DEPLOYMENT
```

## 未执行项

```text
SKIPPED real SMTP: 当前 dev 配置不是 staging-only，且 send history 表未落库、deployment 未开启 send history
SKIPPED inbox verification: 未发送邮件，且即使 SENT 也不宣称真实 inbox 投递
SKIPPED audit cleanup: 未产生 send history 审计记录；本切片原则上不删除 send history
SKIPPED K8S config patch: 未临时开启 APP_MAIL_ENABLED，因此无需恢复 patch
```

## 恢复状态

```text
APP_MAIL_ENABLED=false
sent=false
passwordPrinted=false
recipientPrinted=false
port-forward helper processes stopped by script finally block
```

## 剩余前置条件

- dev/staging 需要明确隔离 SMTP host，例如 Mailtrap/Mailosaur/Ethereal/smtp4dev 或等价 staging-only
  provider，不能直接把 Gmail 真实账号视为 staging-only。
- `notification_mail_send_history` 需要先落 dev DB，且 notification-worker deployment 需要显式开启
  `APP_MAIL_SEND_HISTORY_ENABLED=true`。
- notification-worker 如使用 credential-service MAIL lease，需要通过 secretRef 配置
  `APP_MAIL_CREDENTIAL_INTERNAL_TOKEN` 或等价内部 token，不应在输出或 manifest 中明文记录。
- one-shot smoke 只能在安全测试收件人、低频一封、staging-only provider、send history 可查询全部满足后执行。
