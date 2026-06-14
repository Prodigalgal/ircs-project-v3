# 028-05-I mail notification disabled/failure parity 子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/054-mail-notification-disabled-failure-parity-analysis.md`
- `docs/migrations/001-notification-worker-extraction.md`
- V1 `MailConsumer`
- V1 `MailServiceImpl`
- V3 `services/ircs-notification-worker`

## 背景

`SEND_MAIL` 是当前 `QueueTopic` 审计表中的剩余高优先级项之一。V3 worker 已经有 consumer
和 SMTP service，但缺少 disabled/failure path 的测试证据，也缺少 consumer 层失败日志后重新
抛出的明确行为。

## 目标

- disabled mail path 不调用 `JavaMailSender`，正常返回并 ack。
- 模板渲染失败包装并抛出稳定异常，交给 RabbitMQ retry/DLQ。
- SMTP 发送失败包装并抛出稳定异常，交给 RabbitMQ retry/DLQ。
- `MailConsumer` 调用 mail service，失败时记录日志并重新抛出。

## 非目标

- 不接真实 SMTP。
- 不修改 identity-service。
- 不新增邮件发送历史表。
- 不做 K8S rollout 或 live smoke。
- 不实现生产级 credential pool。

## 输入

- `MailMessageDTO`
- `app.notification.mail.enabled`
- `app.notification.mail.from`
- Thymeleaf template engine
- `JavaMailSender`

## 输出

- `services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/**`
- `services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/**`
- 本报告、迁移记录和任务状态回写。

## 影响范围

包含：

```text
services/ircs-notification-worker/**
docs/reports/054-mail-notification-disabled-failure-parity-analysis.md
tasks/done/028-05-i-mail-notification-disabled-failure-parity.md
```

不包含：

```text
services/ircs-storage-service/**
services/ircs-metadata-worker/**
deploy/**
portal/admin UI
```

## 依赖

- `QueueTopic.SEND_MAIL` 已由 notification-worker owner。
- `MailMessageDTO` 已在 shared contracts 中。
- Spring mail 和 Thymeleaf 已在 notification-worker 依赖中。

## 风险

- 测试不能触发真实 SMTP。
- failure path 必须重新抛出，不允许吞异常。
- disabled path 返回成功会 ack，这是 dev-safe 行为，需要在文档中明确。

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

K8S smoke 后续单独执行：

```text
publish SEND_MAIL fixture
verify q.notification.mail / DLQ behavior
```

## 实现结果

2026-06-07 已完成：

- `MailConsumer` 调用 `NotificationMailService`，失败时记录日志并重新抛出。
- disabled path 不调用 `JavaMailSender`，正常返回。
- template rendering failure 包装为稳定异常并传播。
- SMTP send failure 包装为稳定异常并传播。

完成标记：

```text
MARK 1/4 SEND_MAIL consumer calls mail service for queue message
MARK 2/4 mail disabled path skips JavaMailSender and returns without exception
MARK 3/4 template rendering failure is wrapped and propagated for retry/DLQ
MARK 4/4 SMTP send failure is wrapped and propagated for retry/DLQ
```

## 验证结果

```text
.\gradlew.bat :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL in 8s
```

主线程集成验证：

```text
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-storage-service:test :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL in 18s
```

## 遗留问题

- 本轮未执行 K8S rollout 或 live queue smoke。
- 生产级 SMTP credential pool、发送历史记录和真实 SMTP smoke 继续后置。
