# 028-10-E notification send history schema / ops query 第一刀 task spec

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `tasks/done/028-10-d-notification-send-history-fake-sink.md`
- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`

## 背景

`028-10-D` 已为 notification-worker 增加 `notification_mail_send_history`
best-effort writer，并在 H2 fixture 中固定了写入字段。该切片仍明确留下生产风险：
生产 schema、ops 查询面、retention 以及 staging-only SMTP smoke 尚未完成。

本切片只做第一刀：确认或补齐 `notification_mail_send_history` 的最小生产 schema 证据，并在
ops-service 增加只读查询与 summary。send history 继续只代表 notification-worker 的发送意图和
本地 delivery 结果，不代表真实 SMTP 最终投递成功。

## 目标

- 为 `notification_mail_send_history` 补最小生产 schema / Liquibase，或记录现有 schema 证据。
- ops-service 增加只读 send history 查询能力，支持按 `status`、`deliveryMode`、`templateCode`
  和时间范围查询。
- ops-service 增加 send history summary，清楚区分 `SKIPPED`、`SENT`、`FAILED`。
- 保持 `SENT` 语义为 fake/sink 或 delivery 层成功，不宣称真实 SMTP 投递。
- 不执行真实 SMTP，不打开 `APP_MAIL_ENABLED` 或 `APP_NOTIFICATION_MAIL_ENABLED`。

## 非目标

- 不执行真实 SMTP。
- 不投递 live RabbitMQ mail message。
- 不实现 retention 删除任务，只记录剩余风险。
- 不修改 storage strict、normalization、BFF、config live 或 R2 scripts。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/reports/089`、`docs/migrations/089`。
- 不修改 K8S manifest，不做 rollout。

## 输入

```text
tasks/done/028-10-d-notification-send-history-fake-sink.md
tasks/in-progress/028-10-production-governor-credential-pool-parity.md
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/**
services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/**
services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/**
services/ircs-ops-service/src/test/java/com/prodigalgal/ircs/ops/**
```

## 输出

```text
notification_mail_send_history schema 证据或 migration
ops-service send history read-only repository/query/controller
notification-worker / ops-service focused tests
本 task spec 的实现结果、验证结果和剩余风险回写
```

## 影响范围

包含：

```text
services/ircs-notification-worker/** schema/test
services/ircs-ops-service/** 查询 API/test
shared/ircs-contracts/** 必要 DTO
tasks/in-progress/028-10-e-notification-send-history-schema-ops-query.md
```

不包含：

```text
docs/index.md
tasks/index.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
storage strict
normalization
BFF
config live
R2 scripts
真实 SMTP
```

## 依赖

- `028-10-D` 已固定 send history writer 字段：
  `correlation_id`、`recipient`、`subject`、`template_code`、`delivery_mode`、`status`、
  `credential_id`、`failure_code`、`failure_message`。
- ops-service 现有 read-only audit 查询模式可复用 `NamedParameterJdbcTemplate`、`Page`
  和 `JdbcPageSorts`。
- 如果生产 Liquibase owner 在 `platform/ircs-migrator`，需要确认本切片写入范围是否允许修改
  platform changelog；若不允许，本切片至少通过 schema contract test 固定 DDL 证据。

## 风险

- `SENT` 容易被误读为真实 SMTP 投递完成，需要在 DTO 字段和 task spec 中明确语义。
- schema 如果只在测试 fixture 固定，仍不能替代生产迁移落库。
- 查询面如果缺少时间过滤，send history 未来可能变成大表扫描。
- retention 策略未实现前，生产数据增长仍需后续切片治理。

## 验收标准

```text
MARK 1/5 028-10-E task spec created before code changes
MARK 2/5 notification_mail_send_history schema evidence or migration is explicit
MARK 3/5 ops-service can query send history by status/type/time without mutation
MARK 4/5 ops-service summary exposes SKIPPED/SENT/FAILED counts with clear delivery semantics
MARK 5/5 notification-worker and ops-service tests pass without real SMTP
```

## 测试边界

必跑：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test :services:ircs-ops-service:test --rerun-tasks
```

必要时才跑：

```powershell
.\gradlew.bat --no-daemon :services:ircs-credential-service:test --rerun-tasks
```

不跑：

```text
真实 SMTP
live RabbitMQ
K8S rollout
retention 删除任务
high-risk maintenance runner
```

## 关联文档

- `tasks/done/028-10-d-notification-send-history-fake-sink.md`
- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`

## 关联任务

- 父任务：`tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- 本任务：`tasks/in-progress/028-10-e-notification-send-history-schema-ops-query.md`

## 当前状态

已完成，准备移动到 `tasks/done/028-10-e-notification-send-history-schema-ops-query.md`。

## 实现结果

- 新增 `notification_mail_send_history` schema contract：
  - `services/ircs-notification-worker/src/main/resources/db/schema/notification_mail_send_history.sql`
  - 字段覆盖 writer 已固定的 `correlation_id`、`recipient`、`subject`、`template_code`、
    `delivery_mode`、`status`、`credential_id`、`failure_code`、`failure_message`。
  - 索引覆盖 `created_at`、`status + delivery_mode + created_at`、`template_code + created_at`
    和 `correlation_id`。
- 修复 H2/schema 兼容：
  - 生产 schema 保留 PostgreSQL `TIMESTAMPTZ`。
  - notification-worker schema tests 在 H2 执行前将 `TIMESTAMPTZ` 转为 `TIMESTAMP`，
    并断言原始 DDL 仍包含 `TIMESTAMPTZ`。
- `JdbcMailSendHistoryWriterTest` 改为复用同一份 schema contract，避免 writer fixture
  与 schema 证据漂移。
- ops-service 新增只读查询 API：
  - `GET /api/v1/ops/notification-mail-send-history`
  - 支持 `status`、`deliveryMode`、`templateCode`、`type`、`correlationId`、`recipient`、
    `from`、`to` 与 pageable/sort。
  - `type` 作为 `templateCode` 的查询别名，便于第一刀按 status/type/time 验收。
- ops-service 新增 summary API：
  - `GET /api/v1/ops/notification-mail-send-history/summary`
  - 返回 24h 内 `SKIPPED`、`SENT`、`FAILED` 计数。
  - response 中明确：`SENT` 只表示 notification-worker delivery 层接受发送意图，不代表真实
    SMTP 最终投递成功。

## 验收结果

```text
MARK 1/5 PASS 028-10-E task spec created before code changes
MARK 2/5 PASS notification_mail_send_history schema evidence is explicit
MARK 3/5 PASS ops-service can query send history by status/type/time without mutation
MARK 4/5 PASS ops-service summary exposes SKIPPED/SENT/FAILED counts with clear delivery semantics
MARK 5/5 PASS notification-worker and ops-service tests pass without real SMTP
```

## 测试结果

通过：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test :services:ircs-ops-service:test --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL in 24s
services/ircs-notification-worker/build/test-results/test: tests=38, failures=0, errors=0, skipped=0
services/ircs-ops-service/build/test-results/test: tests=57, failures=0, errors=0, skipped=0
```

定向证据：

```text
JdbcMailSendHistoryWriterTest: tests=3, failures=0, errors=0, skipped=0
NotificationMailSendHistorySchemaTest: tests=1, failures=0, errors=0, skipped=0
NotificationMailSendHistoryRepositoryTest: tests=2, failures=0, errors=0, skipped=0
```

## 未执行项

```text
SKIPPED real SMTP: 本切片禁止真实 SMTP，未打开 APP_MAIL_ENABLED / APP_NOTIFICATION_MAIL_ENABLED
SKIPPED live RabbitMQ: 本切片只跑本地 unit/H2 fixture，不投递 live queue
SKIPPED K8S rollout: 未修改 deploy/k8s manifest
SKIPPED credential-service tests: 本切片未修改 credential-service，未触发必要条件
SKIPPED production migrator changelog: 本 worker 写入范围未授权 platform/ircs-migrator
SKIPPED retention delete job: retention 策略和删除执行后续独立切片处理
```

## 剩余生产风险

- `notification_mail_send_history` 已有 schema contract 证据，但仍需在后续授权切片纳入
  `platform/ircs-migrator` Liquibase master changelog 才能自动落生产库。
- retention 尚未实现，后续需要定义保留周期、归档/删除方式和 ops 可观察指标。
- `SENT` 仍只代表 fake/sink 或 delivery 层成功，不代表真实 SMTP 投递成功。
- staging-only SMTP smoke 仍需隔离账号、低频限额、人工确认和明确门禁。
