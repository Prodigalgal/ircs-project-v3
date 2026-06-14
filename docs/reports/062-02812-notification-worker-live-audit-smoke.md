# 062 028-12 notification worker/job audit live smoke 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 背景

`028-12` 已完成 `worker_job_audit_events` schema、notification worker 写入逻辑，以及
`ops-service` 查询 API 和 live query smoke。剩余缺口是：`ircs-notification-worker`
在 `ircs-dev` 中仍未注入 audit datasource，因此 `q.notification.mail` 真实队列消费还不能
证明会写入 worker/job audit。

## 目标

- 为 `ircs-notification-worker` dev Deployment 注入 `APP_WORKER_AUDIT_*` datasource。
- `MailConsumer` 优先使用 Rabbit `message_id` 作为 `correlation_id`，让 live smoke
  可以精确定位和清理自己的 audit 事件。
- 编写 `q.notification.mail` live queue smoke，验证 disabled-mail dev path 会 ack 消息、
  写入 `worker_job_audit_events`，并可由 ops 查询 API 读出。
- 保持 `ircs-dev` 无公共 HTTPRoute、无新增常驻 Service/Pod、无 fixture residue。

## 非目标

- 不启用真实 SMTP。
- 不修改 identity-service 业务发送逻辑。
- 不新增发送历史表。
- 不铺开其他 worker、scheduler 或 maintenance runner。
- 不新增 HTTPRoute。

## 验收标准

```text
MARK 1/5 notification worker has q.notification.mail consumer
MARK 2/5 live smoke publishes MailMessageDTO fixture with Rabbit message_id
MARK 3/5 notification worker writes succeeded worker_job_audit_events with correlation_id
MARK 4/5 ops worker-job audit query exposes notification worker event
MARK 5/5 live smoke cleanup leaves no DB residue and mail queues empty
```

## 测试边界

```text
.\gradlew.bat :services:ircs-notification-worker:test --rerun-tasks
.\scripts\smoke\notification-worker-audit-02812-smoke.ps1
```

live smoke 只投递一条 Java-serialized `MailMessageDTO` fixture，要求执行前
`q.notification.mail` 和 DLQ 均为空；若队列非空则 SKIPPED，避免误清真实消息。

## 实现结果

- `MailConsumer` 读取 Rabbit `message_id`，并写入
  `worker_job_audit_events.correlation_id`。
- `ircs-notification-worker` dev Deployment 已注入 `APP_WORKER_AUDIT_*` datasource，
  仍保持 `APP_MAIL_ENABLED=false`。
- 新增 notification-worker 专用 `SimpleMessageConverter` allowed-list，只允许
  `com.prodigalgal.ircs.contracts.notification.*` Java serialized payload，避免使用
  `SPRING_AMQP_DESERIALIZATION_TRUST_ALL`。
- `notification-worker-audit-02812-smoke.ps1` 已改为 UTF-8 no BOM 写入临时 Java
  serializer，避免 Windows PowerShell `Set-Content -Encoding UTF8` 产生 BOM 后被
  `javac` 拒绝。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL in 8s

MailConsumerTest tests=5, failures=0, errors=0, skipped=0
NotificationWorkerRabbitConfigurationTest tests=1, failures=0, errors=0, skipped=0
SmtpNotificationMailServiceTest tests=3, failures=0, errors=0, skipped=0
```

ARM64 rollout：

```text
deployment/ircs-notification-worker successfully rolled out
pod=ircs-notification-worker-5b795ff999-6ptv5
imageID=harbor.mnnu.eu.org/ircs/ircs-notification-worker@sha256:742fe58a97b29e57eeae201da03ec63e331d4759284582d6d2351654bb7df6d4
```

live smoke：

```text
.\scripts\smoke\notification-worker-audit-02812-smoke.ps1
MARK 1/5 notification worker has q.notification.mail consumer
MARK 2/5 live smoke publishes MailMessageDTO fixture with Rabbit message_id
MARK 3/5 notification worker writes succeeded worker_job_audit_events with correlation_id
MARK 4/5 ops worker-job audit query exposes notification worker event
MARK 5/5 live smoke cleanup leaves no DB residue and mail queues empty

trace: 02812-notification-audit-20260608101907
status: PASSED
residue: 0
q.notification.mail messages=0, consumers=1, ready=0, unacked=0
q.notification.mail.dlq messages=0, consumers=0, ready=0, unacked=0
```

后置检查：

```text
worker_job_audit_events 02812 notification residue: 0
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

## 暴露并修复的问题

- 首次 smoke 输出 `SKIPPED/JAVA_SERIALIZER_UNAVAILABLE`，原因是临时 Java 文件带
  UTF-8 BOM；已改为 no-BOM 写入。
- 第二次 smoke 输出 `FAILED/AUDIT_EVENT_NOT_WRITTEN`，日志显示
  `Attempt to deserialize unauthorized class MailMessageDTO`；已新增最小 allowed-list
  converter 并用单元测试覆盖。
