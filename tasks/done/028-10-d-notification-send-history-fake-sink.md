# 028-10-D notification send history fake/sink persistence task spec

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/done/028-10-b-mail-credential-adapter-fake-sink.md`
- `tasks/done/028-10-c-ops-runner-registry-dev-gate.md`
- `docs/reports/059-02810-production-governor-credential-pool-first-slice.md`
- `docs/reports/089-v1-golden-behavior-inventory.md` 的 MAIL / production governor 风险

## 背景

`028-10-B` 已让 notification-worker 支持 MAIL credential adapter 与 fake/sink delivery mode，
dev 可以验证邮件发送意图而不连接 SMTP。`028-10-C` 已完成 ops high-risk runner dev gate。
当前剩余风险中，真实 SMTP 仍禁止在 dev 执行，send history 也尚未形成持久化证据。

本切片选择 notification-worker send history persistence：在 disabled、fake/sink success 和 fake
failure 路径记录稳定历史状态，作为真实 SMTP 前的本地可验证证据。实现只覆盖 notification-worker
内部，不执行 SMTP、不投递 live RabbitMQ、不打开高风险 runner。

## 目标

- 增加 notification mail send history 写入接口与 JDBC 实现。
- disabled path 记录 `SKIPPED`，fake/sink success 记录 `SENT`，fake failure 记录 `FAILED`。
- send history 不记录 credential password，不输出 secret。
- 保持 dev 默认 `APP_MAIL_ENABLED=false` 时不真实 SMTP。
- 增加 focused tests 覆盖 disabled、fake/sink success 和 fake failure 的 history/audit 语义。

## 非目标

- 不执行真实 SMTP。
- 不新增 Liquibase migration 或生产 DDL；本轮只固定代码 contract 与 H2 fixture。
- 不修改 K8S manifest，除非测试证明 dev 默认需要显式 guard。
- 不修改 docs/index.md、tasks/index.md、`docs/reports/089-v1-golden-behavior-inventory.md`。
- 不修改 storage R2、normalization-worker、content-service C2、shared outbound C3 metadata credential。
- 不实现或打开 ops high-risk runner。

## 输入

```text
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/**
services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/**
```

## 输出

```text
notification mail send history writer
notification-worker focused unit tests
本 task spec 的实现结果与验证回写
```

## 影响范围

包含：

```text
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/**
services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/**
tasks/in-progress/028-10-d-notification-send-history-fake-sink.md
```

不包含：

```text
services/ircs-ops-service/**
services/ircs-credential-service/**
deploy/k8s/**
docs/index.md
tasks/index.md
docs/reports/089-v1-golden-behavior-inventory.md
```

## 依赖

- notification-worker 现有 `SmtpNotificationMailService`、`FakeMailDelivery`、`MailConsumer`
  测试可本地运行。
- 本地 Gradle/JDK 能运行 `:services:ircs-notification-worker:test`。
- send history 表结构在测试中用 H2 fixture 固定，后续生产 migration 另拆任务。

## 风险

- 若 send history 写入失败直接影响 mail send，可能改变 Rabbit retry/DLQ 行为。
- fake/sink success 只能证明发送意图，不代表 SMTP 真实投递。
- disabled path 记录 `SKIPPED` 不能被上层误读为邮件投递成功。
- 后续真实 SMTP migration 需要明确表结构 owner 与 retention 策略。

## 验收标准

```text
MARK 1/5 send history task spec created before code changes
MARK 2/5 disabled mail records SKIPPED without credential lease or delivery
MARK 3/5 fake/sink success records SENT without SMTP network
MARK 4/5 fake failure records FAILED and still propagates retry/DLQ exception
MARK 5/5 notification-worker tests pass without real SMTP
```

## 测试边界

必跑：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
```

按实际改动无需跑：

```text
:services:ircs-ops-service:test
:services:ircs-credential-service:test
kubectl apply --dry-run=server
```

不跑：

```text
真实 SMTP
live RabbitMQ
high-risk maintenance runner
K8S rollout
```

## 实现结果

- 已选择 notification-worker send history persistence/fake sink evidence 作为小切片。
- 新增 `MailSendHistoryWriter`、`JdbcMailSendHistoryWriter`、`MailSendHistoryEvent`
  和 `MailSendHistoryStatus`。
- 新增 `notification_mail_send_history` 写入 contract：
  - `correlation_id`
  - `recipient`
  - `subject`
  - `template_code`
  - `delivery_mode`
  - `status`
  - `credential_id`
  - `failure_code`
  - `failure_message`
- `SmtpNotificationMailService` 已记录：
  - disabled path -> `SKIPPED`
  - fake/sink success -> `SENT`
  - fake failure / delivery failure / credential lease failure -> `FAILED`
- `MailConsumer` 将 Rabbit `message_id` 传给 mail service，使 send history 与
  `worker_job_audit_events.correlation_id` 使用同一线索。
- send history writer 为 best-effort：
  - 默认 `APP_MAIL_SEND_HISTORY_ENABLED=false`。
  - 表未迁移、配置缺失或写入失败时只记录 warn，不反向改变邮件发送和 Rabbit retry/DLQ 语义。
  - 只记录 `credential_id`，不记录 username/password。
  - failure message 会脱敏 email、Bearer 和敏感关键词。

## 验收结果

```text
MARK 1/5 PASS send history task spec created before code changes
MARK 2/5 PASS disabled mail records SKIPPED without credential lease or delivery
MARK 3/5 PASS fake/sink success records SENT without SMTP network
MARK 4/5 PASS fake failure records FAILED and still propagates retry/DLQ exception
MARK 5/5 PASS notification-worker tests pass without real SMTP
```

## 测试结果

通过：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test :services:ircs-ops-service:test :services:ircs-credential-service:test --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL in 30s
20 actionable tasks: 20 executed
```

测试 XML 汇总：

```text
ircs-notification-worker: tests=36, failures=0, errors=0, skipped=0
ircs-ops-service: tests=53, failures=0, errors=0, skipped=0
ircs-credential-service: tests=36, failures=0, errors=0, skipped=0
```

额外先跑并通过：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-ops-service:test :services:ircs-credential-service:test --rerun-tasks
```

## 未执行项

```text
SKIPPED real SMTP: 本切片禁止真实 SMTP，只验证 fake/sink/send history persistence
SKIPPED live RabbitMQ: 本切片只做本地 unit/H2 fixture，不投递 live queue
SKIPPED K8S dry-run: 未修改 deploy/k8s manifest
SKIPPED high-risk runner: 未修改或打开 ops high-risk maintenance runner
SKIPPED production DDL/Liquibase: 本切片只固定代码 contract，生产 migration/retention 后续独立拆分
```

## 剩余生产风险

- `notification_mail_send_history` 仍需生产 schema migration、ops 查询面和 retention 策略。
- fake/sink `SENT` 只代表本地发送意图已被 fake sink 接收，不代表真实 SMTP 投递成功。
- 真实 SMTP smoke 仍需 staging-only 隔离账号、低频限额和人工确认。
- high-risk runner 仍保持 dev gate/refusal，真实实现 gate 与 production governor 还需后续独立切片。

## 当前状态

已完成并移动到 `tasks/done/028-10-d-notification-send-history-fake-sink.md`。
