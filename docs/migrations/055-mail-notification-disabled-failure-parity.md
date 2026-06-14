# 055 mail notification disabled/failure parity

状态：已完成

日期：2026-06-07

关联：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/054-mail-notification-disabled-failure-parity-analysis.md`
- `tasks/done/028-05-i-mail-notification-disabled-failure-parity.md`
- `docs/migrations/001-notification-worker-extraction.md`

## 背景

`SEND_MAIL` 已在 V3 抽离为 `ircs-notification-worker`，但 disabled 和 failure path 缺少测试证据。
本迁移补齐 dev-safe 行为语义：disabled 时 ack/no-op，模板或 SMTP 失败时重新抛出交给
RabbitMQ retry/DLQ。

## 行为复现

```text
q.notification.mail
  -> MailConsumer
  -> NotificationMailService.send
  -> disabled: skip JavaMailSender and return
  -> template failure: RuntimeException("Template rendering failed")
  -> smtp failure: RuntimeException("SMTP delivery failed")
  -> consumer logs failure and rethrows
```

完成标记：

```text
MARK 1/4 SEND_MAIL consumer calls mail service for queue message
MARK 2/4 mail disabled path skips JavaMailSender and returns without exception
MARK 3/4 template rendering failure is wrapped and propagated for retry/DLQ
MARK 4/4 SMTP send failure is wrapped and propagated for retry/DLQ
```

## 实现范围

修改文件：

```text
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/MailConsumer.java
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/SmtpNotificationMailService.java
services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/MailConsumerTest.java
services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/SmtpNotificationMailServiceTest.java
```

## 关键约束

- disabled path 是 dev-safe ack/no-op，不触发真实 SMTP。
- failure path 必须重新抛出，不能吞异常。
- 本轮不修改 K8S 清单，不新增 Pod、Service、HTTPRoute 或中间件。

## 验证

```text
.\gradlew.bat :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL in 8s
```

## 后续

- live queue smoke 需要单独验证 disabled fixture ack 和 failure fixture retry/DLQ。
- 生产级 SMTP credential pool 和发送历史记录继续后置。
