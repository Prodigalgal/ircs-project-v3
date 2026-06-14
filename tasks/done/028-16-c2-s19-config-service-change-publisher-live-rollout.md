# 028-16-C2-S19 config-service change publisher live rollout task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

上游来源：

- `docs/requirements/028-16-c2-s19-config-service-change-publisher-live-rollout.md`
- `tasks/in-progress/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/done/028-16-c2-s18-dev-rabbit-config-listener-topology-rerun-s17.md`
- `tasks/done/028-16-c2-s15-live-matrix-smoke-harness.md`

## 背景

C2-S18 已解除 content-service 监听端阻塞，Rabbit 中已有
`q.content.config_changed` 且 `consumers=1`。C2-S17 重跑仍未完成，因为 live
config-service deployment 缺 `SPRING_RABBITMQ_*` env，live 镜像缺
`SystemConfigChangePublisher.class`，临时 SystemConfig update 后没有 Rabbit publish /
deliver / ack。

## 目标

- 确认或补齐 config-service 的 `SystemConfigChangePublisher` 已在 create/update/delete 后
  best-effort 发布 `SystemConfigChangedEvent`。
- 锁定默认 seed 不覆盖 ENV/K8S 注入的优先级边界。
- 将 `deploy/k8s/dev/config-service-dev.yaml` 中 Rabbit env 与低资源约束应用到 dev live。
- 构建并推送 `linux/arm64` config-service dev 镜像，rollout `ircs-config-service`。
- 重跑 C2-S17 content 代表 live smoke，并清理临时 DB key、port-forward 和任何临时状态。

## 非目标

- 不修改 content-service listener 代码。
- 不修改 storage-service、notification、Douban provider、portal/admin/BFF。
- 不新增 HTTPRoute、LoadBalancer、NodePort 或长期临时 Secret。
- 不做全服务 config event matrix。

## 输入

- live namespace：`ircs-dev`
- deployment：`ircs-config-service`
- Rabbit exchange/routing key：`x.domain.events` / `config.system.changed`
- 代表 consumer queue：`q.content.config_changed`
- smoke key：`app.content.internal.scraper-base-url`
- smoke 脚本：`scripts/smoke/config-event-content-live-02816c2-s17-smoke.ps1`

## 输出

- 如需代码修复：`services/ircs-config-service/**`
- 如需 manifest 修复：`deploy/k8s/dev/config-service-dev.yaml`
- 如需 smoke 修复：`scripts/smoke/*config*`、`scripts/smoke/*rabbit*`
- 本 task spec 的实施、命令、digest、K8S、Rabbit、DB cleanup 和遗留记录
- 必要时新增 `docs/reports/` 验证报告

## 影响范围

包含：

- config-service SystemConfig 写入后的 event publisher。
- config-service dev deployment Rabbit env、资源限制与 rollout。
- C2-S17 content 代表 live Rabbit smoke。

不包含：

- content-service listener 实现。
- 其他服务的 config listener、provider、R2、SMTP。
- frontend、BFF、edge routing。

## 依赖

- `kubectl` 可访问 `ircs-dev`。
- dev RabbitMQ、PostgreSQL、config-service、content-service 可用。
- Harbor pull secret 可用于 Jib 推送。
- smoke 脚本 finally 能回收本轮 port-forward，并恢复或删除临时配置。

## 风险

- K8S API 或 Rabbit management API transient timeout。
- 镜像推送成功但 rollout 拉取 digest 不一致，需要采集 live `imageID`。
- Rabbit stats 瞬时清零，需要采集前后 delta 与服务日志。
- 如果 smoke 在 restore 阶段失败，需要使用 SQL fallback 并复查 DB 无临时值。

## 验收标准

- `.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-config-service:test --rerun-tasks`
  通过，或记录精确失败。
- `kubectl apply --dry-run=server -f deploy\k8s\dev\config-service-dev.yaml` 通过。
- `deployment/ircs-config-service` rollout 成功，新 pod 有 `SPRING_RABBITMQ_*` env。
- 新 pod jar 内存在 `SystemConfigChangePublisher.class`。
- C2-S17 smoke 观察到 Rabbit publish / deliver / ack，或记录新阻塞。
- DB 中无 `http://content-config-live-smoke.invalid/%` 临时值残留。
- 本轮没有 port-forward、临时 Secret 或额外 K8S 暴露残留。

## 测试边界

本地测试：

```text
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-config-service:test --rerun-tasks
```

K8S dry-run：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\config-service-dev.yaml
```

镜像推送：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\docker\push-service-image-jib.ps1 -Service ircs-config-service -Tag dev -Architecture arm64 -Os linux
```

rollout：

```text
kubectl apply -f deploy\k8s\dev\config-service-dev.yaml
kubectl -n ircs-dev rollout status deployment/ircs-config-service --timeout=180s
```

真实 smoke：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-content-live-02816c2-s17-smoke.ps1 -ConfirmLiveRabbit
```

## 关联文档

- `docs/requirements/028-16-c2-s19-config-service-change-publisher-live-rollout.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`

## 关联任务

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/in-progress/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/done/028-16-c2-s18-dev-rabbit-config-listener-topology-rerun-s17.md`

## 实施结果

2026-06-09：任务创建。下一步先跑本地测试和 K8S dry-run，再构建推送 config-service 镜像并
rollout 到 dev live。

2026-06-09：C2-S19 已完成。config-service publisher 代码无需新增实现，本地源码已经包含
`SystemConfigChangePublisher` 并在 create/update/delete 后通过 transaction `afterCommit`
best-effort 发布 `SystemConfigChangedEvent`。本轮实际修复点是 dev manifest 的 Rabbit 用户名：
`rabbitmq-dev.yaml` 中默认用户为 `admin`，content-service 等 live consumer 也使用 `admin`，
而 config-service manifest 使用了错误的 `ircs`，导致 publisher 上线后首次 smoke 仍出现
`ACCESS_REFUSED`。

变更：

- `deploy/k8s/dev/config-service-dev.yaml`：`SPRING_RABBITMQ_USERNAME=admin`。
- `docs/requirements/028-16-c2-s19-config-service-change-publisher-live-rollout.md`
- `docs/reports/090-02816c2s19-config-service-publisher-live-rollout.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/in-progress/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\config-service-dev.yaml
deployment.apps/ircs-config-service configured (server dry run)
service/ircs-config-service unchanged (server dry run)

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\docker\push-service-image-jib.ps1 -Service ircs-config-service -Tag dev -Architecture arm64 -Os linux
BUILD SUCCESSFUL
Pushed harbor.mnnu.eu.org/ircs/ircs-config-service:dev for linux/arm64

kubectl apply -f deploy\k8s\dev\config-service-dev.yaml
deployment.apps/ircs-config-service configured
service/ircs-config-service unchanged

kubectl -n ircs-dev rollout status deployment/ircs-config-service --timeout=180s
deployment "ircs-config-service" successfully rolled out
```

镜像与 live pod：

```text
imageDigest=sha256:de6cc3f758ca62ff42bea22b422b2c570e488ac341da7380effdc9ce93472f05
pod=ircs-config-service-658d7dd578-xs6tb
imageID=harbor.mnnu.eu.org/ircs/ircs-config-service@sha256:de6cc3f758ca62ff42bea22b422b2c570e488ac341da7380effdc9ce93472f05
ready=true
restartCount=0
SPRING_RABBITMQ_USERNAME=admin
SystemConfigChangePublisher.class present
```

第一次 smoke 失败证据：

```text
AmqpAuthenticationException: ACCESS_REFUSED - Login was refused using authentication mechanism PLAIN.
SPRING_RABBITMQ_USERNAME=ircs
```

修复后 C2-S17 重跑：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-content-live-02816c2-s17-smoke.ps1 -ConfirmLiveRabbit
MARK 1/7 kubectl can access target namespace
MARK 2/7 config-service and content-service deployments are ready
MARK 3/7 Rabbit content config queue is present with a consumer
MARK 4/7 original config-service value captured before write
MARK 5/7 config-service wrote temporary non-sensitive value
MARK 6/7 Rabbit publish and content consumer ack observed for temporary update
MARK 7/7 content-service read probe was not fully observable in current live environment
status=PARTIAL
reason=CONTENT_READ_BLOCKED_BY_RUNTIME_OVERRIDE
```

Rabbit evidence：

```text
temporary-update publishDelta=1 deliverDelta=2 ackDelta=1 messages=0 consumers=1
restore-original publishDelta=1 deliverDelta=2 ackDelta=1 messages=0 consumers=1
q.content.config_changed      0 0 0 1
q.content.config_changed.dlq  0 0 0 0
```

清理证据：

```text
target_rows=0
temp_rows=0
未发现 kubectl port-forward 19087/19088/19089 残留。
```

完成边界：

- C2-S19 MARK 完成：config-service publisher 已上线，dev Rabbit env 正确，C2-S17 真实 Rabbit
  publish/deliver/ack 已通过。
- C2-S17 任务本身不整体归档：content-service live deployment 有
  `APP_CONTENT_SCRAPER_BASE_URL` runtime override，DB fallback read probe 仍不可直接观察。
