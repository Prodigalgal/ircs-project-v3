# 028-16-C1-S3 notification listener start gate task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `RabbitMqConfig`
- V1 `DataInitializationOrchestrator`
- V1 `SystemReadinessOrchestrator`

## 背景

V1 所有 Rabbit listener container 默认 `autoStartup=false`，初始化链完成并
`markAsReady()` 后才由 `RabbitListenerEndpointRegistry.start()` 激活消费者。V3 多数
worker 已通过 `APP_*_LISTENER_ENABLED:false` 默认关闭 listener，但
`ircs-notification-worker` 当前 `MailConsumer` 没有独立 `autoStartup` gate，存在启动后
早于初始化/凭据导入消费邮件任务的风险。

## 目标

- 给 notification-worker mail queue consumer 增加独立 listener start gate。
- 默认保持 listener disabled，与 V1 初始化后放行语义一致。
- 保留 K8S/dev 通过 env 显式开启的能力。
- 增加测试固定 `@RabbitListener.autoStartup` 表达式，避免后续回退。

## 非目标

- 本任务不实现跨服务全局 readiness orchestrator。
- 本任务不导入 MAIL credential，也不执行真实 SMTP。
- 本任务不改 K8S manifest、不新增 Pod/Service/HTTPRoute。

## 输入

```text
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/MailConsumer.java
services/ircs-notification-worker/src/main/resources/application.yaml
services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/MailConsumerTest.java
```

## 输出

- `MailConsumer` 使用 `${app.notification.listener.enabled:false}` 控制自动启动。
- `application.yaml` 暴露 `APP_NOTIFICATION_LISTENER_ENABLED:false`。
- notification-worker 单元测试覆盖 listener gate。
- C1、089 和索引回写。

## 影响范围

```text
services/ircs-notification-worker/**
tasks/in-progress/028-16-c1-initialization-readiness-gate.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 风险

- 若 dev K8S 需要 notification-worker 消费邮件，必须显式设置
  `APP_NOTIFICATION_LISTENER_ENABLED=true`。
- 该 gate 只能避免自动启动，不等价于完整 V1 初始化 orchestrator；后续仍需继续做
  owner readiness marker 和自动放行策略。

## 验收标准

```text
C1-S3 MARK 1/4 notification-worker mail listener has explicit autoStartup gate
C1-S3 MARK 2/4 application.yaml exposes APP_NOTIFICATION_LISTENER_ENABLED default false
C1-S3 MARK 3/4 notification-worker tests pass without real Rabbit/SMTP
C1-S3 MARK 4/4 C1/089/docs/tasks indexes updated
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
```

## 当前状态

2026-06-09：任务创建，准备实现。

2026-06-09：实现完成。

```text
C1-S3 MARK 1/4 notification-worker mail listener has explicit autoStartup gate: done
C1-S3 MARK 2/4 application.yaml exposes APP_NOTIFICATION_LISTENER_ENABLED default false: done
C1-S3 MARK 3/4 notification-worker tests pass without real Rabbit/SMTP: done
C1-S3 MARK 4/4 C1/089/docs/tasks indexes updated: done
```

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

实现结果：

- `MailConsumer` 的 `@RabbitListener` 增加
  `autoStartup = "${app.notification.listener.enabled:false}"`。
- `application.yaml` 增加 `app.notification.listener.enabled`，并映射到
  `APP_NOTIFICATION_LISTENER_ENABLED:false`。
- `MailConsumerTest` 增加反射 guard，固定 listener 默认关闭表达式。

dev rollout 证据：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-notification-worker' -ImageName ircs-notification-worker -Tag dev -Architecture arm64 -Os linux
Pushed harbor.mnnu.eu.org/ircs/ircs-notification-worker:dev for linux/arm64

kubectl -n ircs-dev set env deployment/ircs-notification-worker APP_NOTIFICATION_LISTENER_ENABLED=true
kubectl -n ircs-dev rollout status deployment/ircs-notification-worker --timeout=180s
deployment "ircs-notification-worker" successfully rolled out

ircs-notification-worker ready=1/1 APP_NOTIFICATION_LISTENER_ENABLED=true
q.notification.mail messages=0 consumers=1
q.notification.mail.dlq messages=0 consumers=0
ircs-dev quota pods=28/30|services=24/26|requests.cpu=880m/4|limits.cpu=7650m/8
ircs-dev HTTPRoute: none
```

说明：源码默认值仍是 `false`，符合 V1 初始化后放行语义；dev 环境为保持既有邮件队列
smoke 能力，已显式设置 `APP_NOTIFICATION_LISTENER_ENABLED=true`。
