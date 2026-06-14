# 059 028-12 worker/job audit notification worker first slice

状态：已完成

日期：2026-06-07

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

上游来源：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/reports/058-02812-catalog-service-audit-first-slice.md`
- `docs/migrations/058-catalog-service-audit-handler.md`

## 背景

`028-11` 和 `028-12` catalog 第一刀已经完成 HTTP handler audit 的最小铺开。
worker/job 执行事件仍没有真实写入，且不能复用 `request_audit_logs`，否则会把 queue
consumer、scheduler 或 runner 行为混入 HTTP request audit 查询面。

本轮选择 `ircs-notification-worker` 的 `MailConsumer`，原因是它是低风险 queue consumer，
当前 dev mail 默认关闭，业务路径简单，且成功/失败边界清晰。

## 目标

- 新增专用 `worker_job_audit_events` 表。
- 在 shared common 中新增轻量 `WorkerJobAuditWriter`。
- 在 `MailConsumer` 成功和失败路径写入 queue consumer 事件。
- 本地 H2 测试验证成功/失败事件真实落库。

## 非目标

- 不新增 HTTPRoute，不 rollout 到 `ircs-dev`。
- 不修改 ops-service 查询接口或前端页面。
- 不记录 request body、Authorization、Cookie、credential、token、邮件正文、邮件收件人或 SMTP 配置。
- 不修改 metadata worker、normalization worker、storage-service、search producer、K8S 快照或索引文件。

## 事件口径

专用字段：

```text
job_source      worker/service 名称，例如 ircs-notification-worker
job_type        queue_consumer / scheduler / maintenance_runner / provider_task
job_name        具体 consumer 或 runner 名称，例如 notification.mail
correlation_id  外部 trace/message/session id；没有安全来源时为空
status          started / succeeded / failed / skipped
duration_ms     执行耗时
error_class     失败分类，例如 java.lang.RuntimeException
error_message   截断后的异常摘要
```

本轮 `MailMessageDTO` 没有 message id，且字段包含收件人、正文和模板变量；因此
`correlation_id` 不从消息内容派生，避免泄露。

## 方案

```text
MailConsumer.onMessage(message)
  -> startedAt
  -> mailService.send(message)
  -> WorkerJobAuditWriter.record(succeeded)
  -> catch Exception
       -> WorkerJobAuditWriter.record(failed)
       -> rethrow for RabbitMQ retry
```

writer 使用 JDBC URL 直连，保持和 BFF audit writer 类似的轻量形态。缺少 datasource、
关闭开关或写入失败时 no-op / warning，不影响 worker 业务路径。

## 范围

```text
shared/ircs-common/**
services/ircs-notification-worker/**
platform/ircs-migrator/src/main/resources/db/changelog/**
docs/reports/059-02812-worker-job-audit-notification-worker-first-slice.md
docs/migrations/059-worker-job-audit-events.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险

- 审计失败不能改变 RabbitMQ retry 语义。
- notification worker 当前未注入 `DB_URL`；缺省必须 no-op，后续 live smoke 再补 env。
- worker/job audit 后续铺开可能带来写入量增长，需要继续按 worker 分批接入。

## 验收标准

```text
MARK 1/3 worker_job_audit_events schema is dedicated and non-HTTP
MARK 2/3 notification worker mail consumer writes succeeded event
MARK 3/3 notification worker mail consumer writes failed event and preserves retry exception
```

## 测试边界

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-notification-worker:test --rerun-tasks
```

本轮不执行 live smoke，不触碰 HTTPRoute、ResourceQuota、K8S 快照或索引文件。

## 实现结果

- 新增 `worker_job_audit_events` 专用表 changelog，并在 migrator master changelog 末尾 include。
- shared common 新增 `WorkerJobAuditEvent` 和 `WorkerJobAuditWriter`。
- writer 仅写入 job 元数据；datasource 缺省、关闭或写入失败时 no-op / warning，不影响业务路径。
- writer 对异常摘要做敏感词防护，命中 `authorization`、`cookie`、`credential`、`token`、
  `password`、`secret` 或 `request body` 时写入 `[redacted-sensitive-error]`。
- `ircs-notification-worker` 新增 `NotificationWorkerAuditConfiguration`，默认读取
  `APP_WORKER_AUDIT_*` / `SPRING_DATASOURCE_*` / `DB_*`，缺少 datasource 时 writer 不写入。
- `MailConsumer` 在 `mailService.send` 成功后写入 `succeeded`，失败后写入 `failed`
  并继续抛出原异常给 RabbitMQ retry。
- notification worker 增加 PostgreSQL runtime dependency 和 H2 test dependency。

## 验证结果

本地测试：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL in 12s
```

测试证据：

```text
WorkerJobAuditWriterTest tests=3, failures=0, errors=0
MailConsumerTest tests=4, failures=0, errors=0
SmtpNotificationMailServiceTest tests=3, failures=0, errors=0
BffRequestAuditWriterTest tests=2, failures=0, errors=0
```

migrator 资源处理：

```text
.\gradlew.bat :platform:ircs-migrator:processResources --rerun-tasks
BUILD SUCCESSFUL in 1s
```

MARK：

```text
MARK 1/3 worker_job_audit_events schema is dedicated and non-HTTP
MARK 2/3 notification worker mail consumer writes succeeded event
MARK 3/3 notification worker mail consumer writes failed event and preserves retry exception
```

SKIPPED：

```text
SKIPPED live smoke: notification worker 当前 dev 清单未注入 DB_URL，本轮不伪造 live 写入。
SKIPPED HTTPRoute: 本轮未新增公共路由。
SKIPPED ResourceQuota/K8S 快照: 未 rollout，不触碰集群资源。
SKIPPED ops-service query: 本轮只落首个 worker/job audit 写入，不新增查询 API。
```

## 剩余项

- 后续由主线程或新子任务决定是否给 `ircs-notification-worker` 注入 audit datasource 并做
  ClusterIP / queue fixture live smoke。
- ops-service 查询 `worker_job_audit_events` 的只读接口和关联视图仍未实现。
- 其他 worker、scheduler 和 maintenance runner 仍需按风险分批接入。
