# 081 MAIL credential adapter and fake sink 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-10` 增加 notification-worker 的 MAIL credential adapter 与 fake/sink 发送模式。
本迁移只证明 credential-service MAIL pool 能被发送路径消费，并用 fake sink 验证模板/发送语义；
不执行真实 SMTP。

## 范围

```text
services/ircs-notification-worker/**
services/ircs-credential-service/**   # 条件允许
docs/reports/081-02810-mail-credential-adapter-fake-sink.md
tasks/done/028-10-b-mail-credential-adapter-fake-sink.md
```

## 安全门禁

```text
APP_MAIL_ENABLED=false
APP_MAIL_DELIVERY_MODE=fake
APP_MAIL_CREDENTIAL_PROVIDER=credential-service
```

默认 dev 不真实发送。fake/sink 只记录本地测试对象，不连接 SMTP。

## 期望结果

```text
MARK 1/5 notification-worker can lease MAIL credentials without printing secrets
MARK 2/5 disabled mail path still skips send and acks/no-ops
MARK 3/5 fake sink records intended mail without SMTP network
MARK 4/5 missing credential and fake failure keep retry/DLQ error semantics
MARK 5/5 notification-worker and credential-service tests pass without SMTP
```

## 实施前迁移方案

- notification-worker 新增 MAIL credential adapter，复用 credential-service 内部 provider lease contract，不新增 credential-service HTTP API。
- notification-worker 新增 fake/sink delivery mode；该模式只把已渲染邮件交给内存 sink，单元测试断言 intended mail，不触发 SMTP 网络。
- SMTP 模式在发送前租用 MAIL credential 并临时注入 `username/password` 到 `JavaMailSenderImpl`；fake/sink 模式同样执行 adapter contract 校验，除非 `APP_MAIL_ENABLED=false`。
- `APP_MAIL_ENABLED=false` 继续直接返回，作为 dev disabled ack/no-op 语义，不租 credential、不发送、不失败。
- 本迁移不触碰部署清单、不 rollout、不 push image。

## 实际迁移结果

- 已迁移 notification-worker mail path 到可配置 delivery mode：`smtp` 使用 SMTP delivery，`fake` 与 `sink` 使用 fake sink。
- 已迁移 notification-worker credential path 到可选 credential-service adapter：`APP_MAIL_CREDENTIAL_PROVIDER=credential-service` 时通过内部 lease endpoint 获取 MAIL credential，并过滤 `username/password`。
- 已保留 `APP_MAIL_ENABLED=false` disabled no-op，disabled 分支不租 credential、不发送。
- 未修改 credential-service 源码，未新增 endpoint；使用现有内部通用 lease endpoint 与既有 MAIL 双 key service guard。

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
