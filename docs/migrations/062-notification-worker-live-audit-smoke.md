# 062 notification worker/job audit live smoke 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 完成 notification worker/job audit live queue smoke：

- `MailConsumer` 读取 Rabbit `message_id` 并写入 `worker_job_audit_events.correlation_id`。
- `ircs-notification-worker` dev manifest 注入 worker audit datasource。
- 新增可清理 smoke 脚本，通过 Rabbit Management API 投递 `MailMessageDTO` fixture，
  再通过 PostgreSQL 和 `ops-service` 查询验证 audit 事件。

## 代码范围

```text
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/MailConsumer.java
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/NotificationWorkerRabbitConfiguration.java
services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/MailConsumerTest.java
services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/NotificationWorkerRabbitConfigurationTest.java
deploy/k8s/dev/notification-worker-dev.yaml
scripts/smoke/notification-worker-audit-02812-smoke.ps1
docs/reports/062-02812-notification-worker-live-audit-smoke.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险控制

- `message_id` 由 smoke 生成，不包含邮箱、正文、token 或 credential。
- 真实业务若没有 Rabbit `message_id`，`correlation_id` 继续为空，保持旧行为。
- smoke 只在 mail queue 和 DLQ baseline 均为 0 时投递 fixture。
- dev 邮件仍保持 `APP_MAIL_ENABLED=false`，不触发真实 SMTP。
- Spring AMQP Java deserialization 只 allowed-list notification contract 包，不启用
  trust-all。

## 迁移结果

```text
MARK 1/5 notification worker has q.notification.mail consumer
MARK 2/5 live smoke publishes MailMessageDTO fixture with Rabbit message_id
MARK 3/5 notification worker writes succeeded worker_job_audit_events with correlation_id
MARK 4/5 ops worker-job audit query exposes notification worker event
MARK 5/5 live smoke cleanup leaves no DB residue and mail queues empty
```

验证证据：

```text
.\gradlew.bat :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL in 8s

ircs-notification-worker imageID:
  harbor.mnnu.eu.org/ircs/ircs-notification-worker@sha256:742fe58a97b29e57eeae201da03ec63e331d4759284582d6d2351654bb7df6d4

trace: 02812-notification-audit-20260608101907
status: PASSED
residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
