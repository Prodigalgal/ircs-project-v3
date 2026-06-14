# 028-16-C2-S11 notification mail config parity

状态：已完成

日期：2026-06-09

## 背景

`028-16-C2` 正在把 V1 `SystemConfigService` 的配置默认值、运行时覆盖优先级、缓存失效和
跨服务消费者逐项迁入 V3。前序 C2-S5 到 C2-S10 已覆盖 search、interaction、portal、
storage、normalization 和 aggregation 的一批配置消费者。

本轮审计发现 notification-worker 仍有明确偏差：

- V1 `MailServiceImpl` 发送前读取 `SystemConfigKey.MAIL_ENABLED`，为 false 时直接跳过。
- V1 `MailCredentialHandler` 创建 `JavaMailSender` 时从 `SystemConfigService` 读取
  `app.mail.host`、`app.mail.port`、`app.mail.protocol`、`app.mail.properties.auth`、
  `app.mail.properties.starttls`、`app.mail.properties.ssl`、`app.mail.timeout` 和
  `app.mail.debug`；username/password 只来自 MAIL credential。
- V1 `SystemConfigKey.MAIL_FROM` 默认值为 `HuaWai System <huawai.system@gmail.com>`。
- V3 notification-worker 当前通过 `application.yaml` / `@ConfigurationProperties`
  启动期绑定 `app.notification.mail.*` 和 Spring Mail properties，不会在
  `system_configs` DB 更新或 `SystemConfigChangedEvent` 后刷新。

## 目标

把 notification-worker 的 mail enable/from/SMTP connect options 迁到 V1 风格的
`Runtime injected -> system_configs DB -> V1 default seed` 读取链路，并接入 config change
event evict。

## 非目标

- 不执行真实 SMTP live smoke，不发送真实邮件。
- 不改变 credential-service MAIL lease payload 语义；username/password 仍由 credential lease
  或静态 Spring Mail credential 提供。
- 不实现 V1 `MAIL_RATE_LIMIT_MIN_MS/MAX_MS` 等发送间隔治理。
- 不改变 Rabbit `SEND_MAIL` listener 默认关闭策略。
- 不迁 admin/config connectivity test 的 MAIL 语义；本轮只覆盖 notification-worker 消费者。

## 上游来源

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- V1:
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/common/enums/SystemConfigKey.java`
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/system/service/impl/MailServiceImpl.java`
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/system/service/handler/MailCredentialHandler.java`
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/system/support/MailSenderFactory.java`

## 输入

- V1 SystemConfig keys:
  - `app.mail.enabled`
  - `app.mail.from`
  - `app.mail.host`
  - `app.mail.port`
  - `app.mail.protocol`
  - `app.mail.properties.auth`
  - `app.mail.properties.starttls`
  - `app.mail.properties.ssl`
  - `app.mail.timeout`
  - `app.mail.debug`
- V3 aliases:
  - `app.notification.mail.enabled`
  - `app.notification.mail.from`
  - Spring Mail / existing env aliases，例如 `APP_MAIL_HOST`、`SPRING_MAIL_HOST`。

## 输出

- notification-worker service-local config repository/cache。
- notification-worker runtime injection source detector。
- notification-worker mail config value resolver。
- `q.notification.config_changed` event listener/topology。
- `SmtpNotificationMailService` 每次发送前使用 env/DB/default 判断 enabled/from/delivery mode。
- `SmtpMailDelivery` 在 SMTP 发送时使用 env/DB/default 构建 V1 风格 JavaMailSender options。
- 更新 notification-worker application.yaml 和 dev K8S manifest。
- 单元测试、K8S server dry-run 和 docs/tasks 回写。

## 影响范围

- `services/ircs-notification-worker/**`
- `deploy/k8s/dev/notification-worker-dev.yaml`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/index.md`
- `docs/index.md`

## 依赖

- `shared:ircs-contracts` 中的 `SystemConfigChangedEvent`。
- Rabbit topology 常量 `shared:ircs-contracts` / `shared:ircs-messaging`。
- notification-worker 需要 JDBC 读取 `system_configs`。
- notification-worker 已使用 Spring Mail，可用 `JavaMailSenderImpl` 动态构建 SMTP sender。

## 风险

- V1 默认 `app.mail.enabled=true`，但 dev 环境必须继续通过 runtime env `APP_MAIL_ENABLED=false`
  覆盖，避免测试环境误发真实邮件。
- 动态 sender 不能泄露 credential password；测试和日志只能断言非敏感配置。
- 本轮只证明本地 event listener/topology 合约，不证明 live Rabbit broker 路径。

## 验收标准

- MARK 1/8 V1 MAIL SystemConfig facts documented。
- MARK 2/8 notification-worker has dedicated config cache/event listener。
- MARK 3/8 mail enabled/from reads runtime/DB/default with V1 defaults。
- MARK 4/8 SMTP connect options read runtime/DB/default with V1 defaults。
- MARK 5/8 runtime env overrides DB and V1 defaults for dev safety。
- MARK 6/8 config event evicts changed MAIL key cache。
- MARK 7/8 notification-worker tests and K8S dry-run pass without real SMTP/Rabbit。
- MARK 8/8 docs/tasks indexes and 089 inventory updated。

## 测试边界

- 本轮运行：
  - `.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks`
  - `kubectl apply --dry-run=server -f deploy\k8s\dev\notification-worker-dev.yaml`
- 不运行真实 Rabbit live smoke。
- 不执行真实 SMTP 发送。

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`

## 关联任务

- 父任务：`tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- 上游父任务：`tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`

## 当前状态

已完成。

## 实现结果

- notification-worker 新增 service-local `RuntimeInjectedConfig`、`SystemConfigRepository`
  和 `NotificationMailConfigValues`，MAIL 配置读取顺序固定为运行时注入 ->
  `system_configs` DB -> V1 default seed。
- `SmtpNotificationMailService` 每次发送前动态读取 `app.mail.enabled` /
  `app.mail.from`，并兼容 `app.notification.mail.*` 与 `APP_MAIL_*` alias。
- `SmtpMailDelivery` 改为每次 SMTP 发送时根据当前配置和 credential lease 构建
  `JavaMailSender`，覆盖 host、port、protocol、auth、STARTTLS、SSL、timeout 和 debug。
- notification-worker 新增 `q.notification.config_changed`、DLQ 绑定和
  `APP_NOTIFICATION_CONFIG_LISTENER_ENABLED` 独立开关，用于按 key evict 本地
  `system_configs` cache。
- dev K8S manifest 注入 notification-worker DB 连接和 config listener 开关，同时保留
  `APP_MAIL_ENABLED=false`，确保 V1 默认 `true` 不会导致开发环境误发真实邮件。

## 验证结果

```text
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\notification-worker-dev.yaml
configmap/ircs-dev-app-config unchanged (server dry run)
deployment.apps/ircs-notification-worker configured (server dry run)
```

## MARK

- MARK 1/8 V1 MAIL SystemConfig facts documented：done 2026-06-09。
- MARK 2/8 notification-worker has dedicated config cache/event listener：done 2026-06-09。
- MARK 3/8 mail enabled/from reads runtime/DB/default with V1 defaults：done 2026-06-09。
- MARK 4/8 SMTP connect options read runtime/DB/default with V1 defaults：done 2026-06-09。
- MARK 5/8 runtime env overrides DB and V1 defaults for dev safety：done 2026-06-09。
- MARK 6/8 config event evicts changed MAIL key cache：done 2026-06-09。
- MARK 7/8 notification-worker tests and K8S dry-run pass without real SMTP/Rabbit：done 2026-06-09。
- MARK 8/8 docs/tasks indexes and 089 inventory updated：done 2026-06-09。

## 遗留问题

- 本轮未执行真实 SMTP live smoke，也未发送真实邮件。
- V1 `MAIL_RATE_LIMIT_MIN_MS/MAX_MS` 等发送间隔治理仍留给后续 production governor /
  mail behavior parity 切片。
- config change event 只通过本地 listener/topology 测试和 K8S dry-run 验证，live Rabbit
  广播链路仍留给 C2 后续 smoke。
