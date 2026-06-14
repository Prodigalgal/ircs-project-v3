# 054 mail notification disabled/failure parity 分析

状态：已完成

更新时间：2026-06-07

## 背景

`ircs-notification-worker` 是 V3 第一批抽出的 worker，已经消费 `q.notification.mail` 并通过
Spring `JavaMailSender` 发送邮件。当前 HTTP/BFF route coverage 已归零，行为级 parity 需要确认
mail queue 的 disabled 和 failure 语义与 V1 一致：disabled 时应跳过并 ack，模板或 SMTP 失败时
应抛出异常交给 RabbitMQ retry/DLQ，而不是静默吞掉。

## V1 行为摘要

V1 `MailConsumer`：

- 消费 `q.notification.mail`。
- 如果配置 `MAIL_ENABLED=false`，`MailServiceImpl` 记录 skip 并返回，不抛异常。
- 模板渲染失败时包装为 `RuntimeException("Template rendering failed")`。
- SMTP transport 或 key pool 失败时抛出 RuntimeException。
- consumer 捕获失败日志后重新抛出，让 RabbitMQ 重试。

## V3 当前状态

已具备：

- `MailConsumer` 监听 `q.notification.mail`。
- `SmtpNotificationMailService` 支持 `app.notification.mail.enabled`。
- disabled 时返回不发送。
- SMTP send 异常包装为 `RuntimeException("SMTP delivery failed")`。

缺口：

- 没有 notification-worker 测试目录，disabled/failure 行为没有可重复验证。
- `MailConsumer` 当前不记录 failure retry 语义日志。
- 模板渲染异常没有包装为稳定错误语义。

## 本轮方案

最小 dev-safe mail parity：

```text
SmtpNotificationMailService
  -> disabled: skip and return
  -> template rendering failure: throw RuntimeException("Template rendering failed")
  -> smtp send failure: throw RuntimeException("SMTP delivery failed")

MailConsumer
  -> success/disabled: ack
  -> exception: log and rethrow for RabbitMQ retry/DLQ
```

## 非目标

- 不接真实 SMTP。
- 不修改 identity-service forgot-password 行为。
- 不做 K8S rollout 或 live queue smoke。
- 不新增 DB 表记录邮件发送历史。
- 不实现生产级 credential pool。

## 风险

- dev 环境默认 mail disabled，应避免测试触发真实 SMTP。
- failure path 必须重新抛出，不能因为日志补强导致 RabbitMQ 误 ack。
- 模板渲染异常包装后应保留 cause，便于后续排障。

## 验收标准

```text
MARK 1/4 SEND_MAIL consumer calls mail service for queue message
MARK 2/4 mail disabled path skips JavaMailSender and returns without exception
MARK 3/4 template rendering failure is wrapped and propagated for retry/DLQ
MARK 4/4 SMTP send failure is wrapped and propagated for retry/DLQ
```

## 测试边界

本地测试：

```text
.\gradlew.bat :services:ircs-notification-worker:test --rerun-tasks
```

后续 live smoke 单独执行：

```text
publish SEND_MAIL fixture with APP_MAIL_ENABLED=false
verify q.notification.mail ack and DLQ empty
publish failing fixture in isolated dev window
verify retry/DLQ behavior
```

## 完成结果

2026-06-07 已完成 mail disabled/failure 最小切片：

```text
MARK 1/4 SEND_MAIL consumer calls mail service for queue message
MARK 2/4 mail disabled path skips JavaMailSender and returns without exception
MARK 3/4 template rendering failure is wrapped and propagated for retry/DLQ
MARK 4/4 SMTP send failure is wrapped and propagated for retry/DLQ
```

实现摘要：

- `MailConsumer` 在 `NotificationMailService.send()` 抛出异常时记录 retry 语义日志并重新抛出。
- `SmtpNotificationMailService` 保持 disabled path skip/no-op，不调用 `JavaMailSender`。
- `SmtpNotificationMailService` 将 Thymeleaf 模板异常包装为 `RuntimeException("Template rendering failed")`。
- SMTP send 异常继续包装为 `RuntimeException("SMTP delivery failed")`。

验证：

```text
.\gradlew.bat :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL in 8s
```

后续仍需独立切片：

- dev live queue smoke，确认 disabled fixture ack、失败 fixture retry/DLQ。
- 生产级 SMTP credential pool 和发送历史记录。
