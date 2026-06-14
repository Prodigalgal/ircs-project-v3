# 081 028-10 MAIL credential adapter and fake sink 报告

状态：已完成

日期：2026-06-08

## 背景

`028-10-A` 已在 credential-service 中完成 MAIL pool guard，但 notification-worker
仍主要依赖静态 Spring Mail 配置。dev 环境 `APP_MAIL_ENABLED=false`，真实 SMTP 不应执行。
下一刀应先让 notification-worker 具备 credential-service MAIL lease adapter 与 fake/sink
发送模式，验证 credential 选择、MIME/模板路径和失败语义，而不投递公网邮件。

## 目标

- 在 notification-worker 中增加 MAIL credential adapter。
- 增加 fake/sink mail sender 模式，默认用于测试，不执行真实 SMTP。
- 保留 `APP_MAIL_ENABLED=false` disabled ack/no-op 语义。
- 本地测试验证 credential lease、缺 credential、fake send success、fake failure 与 retry/DLQ 语义。

## 非目标

- 不执行真实 SMTP。
- 不新增真实 send history 表。
- 不修改 K8S manifest、不 rollout、不新增 HTTPRoute。
- 不修改 ops runner registry 或 magnet-service。

## 建议写入范围

```text
services/ircs-notification-worker/**
services/ircs-credential-service/**   # 仅当通用 lease endpoint 不能表达 MAIL username/password 双 key 时允许
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

本轮不执行真实 SMTP、不投递 live mail queue、不 rollout。

## 本轮实施前方案

### 子任务拆分

- 子任务 A：在 notification-worker 中补充 MAIL credential adapter，调用 credential-service 内部 lease endpoint：`GET /internal/credentials/providers/MAIL/leases?requiredPayloadKey=username&limit=N`，使用 `X-IRCS-INTERNAL-TOKEN`，adapter 本地再校验 `username` 与 `password` 均存在。
- 子任务 B：将发送入口拆成“模板渲染 + delivery mode”，支持 `APP_MAIL_DELIVERY_MODE=smtp|fake|sink`；`fake` 与 `sink` 不创建 `JavaMailSender` MIME、不连接 SMTP。
- 子任务 C：保留 `APP_MAIL_ENABLED=false` 的 no-op/ack 语义，使 disabled path 不触发 credential lease、不触发 fake/smtp delivery。
- 子任务 D：补齐 notification-worker 单元测试，覆盖缺 credential、fake success、fake failure、consumer retry/DLQ 重新抛出语义；credential-service 现有 MAIL 双 key guard 测试作为 contract 侧回归。

### 风险与控制

- credential-service 通用 lease endpoint 只接收一个 `requiredPayloadKey`；notification-worker adapter 必须在本地二次过滤 `password`，避免使用半残 credential。
- fake/sink 模式只用于 contract 与模板路径验证，不记录 secret，不输出 password，不执行 SMTP。
- 本轮不修改 `docs/index.md`、`tasks/index.md`、父任务、K8S 快照、`deploy/k8s/**`、ops-service 或 magnet-service。

## 实现结果

- notification-worker 增加 MAIL credential adapter：调用 credential-service 内部 provider lease endpoint，provider 固定为 `MAIL`，`requiredPayloadKey=username`，并在 worker 侧二次过滤 `username/password`。
- notification-worker 增加 `APP_MAIL_DELIVERY_MODE=smtp|fake|sink`：`fake` 与 `sink` 共用 fake delivery，写入内存 `FakeMailSink`，不创建 MIME、不调用 SMTP 网络。
- notification-worker 增加 `APP_MAIL_CREDENTIAL_PROVIDER=credential-service`：启用后发送前租用 MAIL credential；`static` 保持原静态 Spring Mail 配置路径。
- `APP_MAIL_ENABLED=false` 仍在入口直接 no-op/ack，不触发模板渲染、credential lease、fake/smtp delivery。
- 缺 credential 与 fake failure 通过 consumer 单测验证继续重新抛出，保留 RabbitMQ retry/DLQ 语义。
- credential-service 源码未修改；其现有 MAIL username/password 双 key guard 被测试命令回归覆盖。

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
