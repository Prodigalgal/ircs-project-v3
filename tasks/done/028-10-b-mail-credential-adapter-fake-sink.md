# 028-10-B MAIL credential adapter and fake sink task spec

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/081-02810-mail-credential-adapter-fake-sink.md`
- `docs/migrations/081-mail-credential-adapter-fake-sink.md`

## 背景

MAIL credential 已从 V1 导入到 V3 dev credential pool，credential-service 也已有 MAIL
pool guard。notification-worker 仍未把发送路径接入 credential-service，也没有 fake/sink
模式来验证真实发送前的行为。

## 目标

- notification-worker 支持从 credential-service 租用 MAIL credential。
- 增加 fake/sink delivery mode，不连接 SMTP。
- 保留 disabled mail no-op。
- 测试覆盖缺 credential、fake success、fake failure 和 retry/DLQ 语义。

## 非目标

- 不执行真实 SMTP。
- 不写 send history 表。
- 不修改 K8S manifest、不 rollout。
- 不改 ops-service 或 magnet-service。

## 输入

```text
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/**
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/**
MAIL sys_credentials payload: username/password
```

## 输出

```text
MAIL credential adapter
fake/sink mail delivery mode
notification-worker and credential-service tests
docs/reports/081-02810-mail-credential-adapter-fake-sink.md
docs/migrations/081-mail-credential-adapter-fake-sink.md
```

## 影响范围

```text
services/ircs-notification-worker/**
services/ircs-credential-service/**
docs/reports/081-02810-mail-credential-adapter-fake-sink.md
docs/migrations/081-mail-credential-adapter-fake-sink.md
tasks/done/028-10-b-mail-credential-adapter-fake-sink.md
```

## 验收标准

```text
MARK 1/5 notification-worker can lease MAIL credentials without printing secrets
MARK 2/5 disabled mail path still skips send and acks/no-ops
MARK 3/5 fake sink records intended mail without SMTP network
MARK 4/5 missing credential and fake failure keep retry/DLQ error semantics
MARK 5/5 notification-worker and credential-service tests pass without SMTP
```

## 测试边界

```powershell
.\gradlew.bat :services:ircs-notification-worker:test :services:ircs-credential-service:test --rerun-tasks
```

## 子任务状态

| 子任务 | 边界 | 输出 | 验证 | 状态 |
| --- | --- | --- | --- | --- |
| A. MAIL credential adapter | `services/ircs-notification-worker/src/main/java/.../mail` 内部 adapter 与属性；不新增 credential-service API | 可测试的 MAIL lease contract，本地过滤 username/password | adapter 单元测试 + credential-service 现有 MAIL lease guard | 已完成 |
| B. fake/sink delivery mode | notification-worker mail delivery path | `APP_MAIL_DELIVERY_MODE=fake|sink` 不连接 SMTP | fake success/failure 单元测试 | 已完成 |
| C. disabled no-op 保持 | `APP_MAIL_ENABLED=false` 分支 | disabled 不租 credential、不发送、不抛错 | disabled 单元测试 | 已完成 |
| D. retry/DLQ 语义 | `MailConsumer` failure rethrow | 缺 credential 与 fake failure 继续向 Rabbit listener 抛出异常 | consumer 单元测试 | 已完成 |

## 本轮实现约束

- 允许写入 notification-worker；credential-service 仅当双 key contract 缺失才补。
- 已确认 credential-service `CredentialService#leaseMailCredentials` 已有 username/password 双 key guard，且内部通用 lease endpoint 可被 adapter 调用；本轮默认不修改 credential-service 源码。
- 不执行真实 SMTP、不投递 live RabbitMQ、不 rollout、不 push image。

## 实现结果

- notification-worker 新增 `NotificationMailProperties`，支持 `APP_MAIL_DELIVERY_MODE=smtp|fake|sink`、`APP_MAIL_CREDENTIAL_PROVIDER=static|credential-service`、`APP_MAIL_FAKE_FAILURE_ENABLED` 与 credential-service lease 配置。
- notification-worker 新增 `CredentialServiceMailCredentialRepository`，调用 `GET /internal/credentials/providers/MAIL/leases?requiredPayloadKey=username&limit=N`，携带 `X-IRCS-INTERNAL-TOKEN`，并在 worker 本地过滤 `username/password` 双 key。
- notification-worker 新增 fake/sink delivery 与内存 `FakeMailSink`，只记录 intended mail，不创建 MIME、不调用 SMTP 网络。
- `SmtpNotificationMailService` 保留 `APP_MAIL_ENABLED=false` 先返回语义；disabled path 不渲染模板、不租 credential、不触发 fake/smtp delivery。
- `MailConsumer` 原有失败重新抛出行为未改变；缺 credential 与 fake failure 单测固定为继续交给 RabbitMQ retry/DLQ 策略处理。
- credential-service 源码未修改；本轮使用其既有 MAIL 双 key guard 与内部通用 lease endpoint。

## 验收结果

```text
MARK 1/5 PASS notification-worker can lease MAIL credentials without printing secrets
MARK 2/5 PASS disabled mail path still skips send and acks/no-ops
MARK 3/5 PASS fake sink records intended mail without SMTP network
MARK 4/5 PASS missing credential and fake failure keep retry/DLQ error semantics
MARK 5/5 PASS notification-worker and credential-service tests pass without SMTP
```

## 测试结果

```powershell
.\gradlew.bat :services:ircs-notification-worker:test :services:ircs-credential-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 25s`，16 个 Gradle task executed。

本轮未连接真实 SMTP、未投递 live RabbitMQ、未 rollout、未 push image。
