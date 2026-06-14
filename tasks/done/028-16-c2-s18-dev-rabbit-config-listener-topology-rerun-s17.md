# 028-16-C2-S18 dev Rabbit config listener topology + rerun C2-S17 task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

上游来源：

- `tasks/in-progress/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/done/028-16-c2-s15-live-matrix-smoke-harness.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

## 背景

C2-S17 真实 live smoke 在执行临时 DB 写入前被 Rabbit 拓扑预检阻塞：
Rabbit management API 查询 `q.content.config_changed` 返回 404，`rabbitmqctl list_queues`
也没有任何 `config_changed` 队列。content-service 代码侧已经有
`SystemConfigChangedListener`、`ContentConfigEventTopology` 和
`APP_CONTENT_CONFIG_LISTENER_ENABLED` 独立开关；本轮需要核对 dev deployment、运行中 pod、
Rabbit 连接、拓扑 bean 条件和 rollout 状态，最小修复 dev 环境或 content-service，使
content-service 的 config listener 队列真实声明，再重跑 C2-S17 live smoke。

## 目标

- 找出 `q.content.config_changed` 未声明的真实原因。
- 最小修复 dev deployment 或 content-service config listener/topology，不新增 HTTPRoute。
- 保持 dev 低资源边界，只允许按需 rollout content-service。
- 运行 K8S server dry-run。
- 重跑 C2-S17 live smoke，使用非敏感 key `app.content.internal.scraper-base-url`。
- 确认临时值已恢复，记录 Rabbit 队列、publish/consume/evict 证据或新阻塞。

## 非目标

- 不触碰 Douban provider。
- 不触碰 R2。
- 不触碰 notification migrator、SMTP 或 MAIL credential。
- 不触碰 normalization。
- 不新增 HTTPRoute。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/reports/089` 或 `docs/migrations/089`。

## 输入

- content-service queue：`q.content.config_changed`
- exchange / routing key：`x.domain.events` / `config.system.changed`
- live smoke key：`app.content.internal.scraper-base-url`
- live smoke script：`scripts/smoke/config-event-content-live-02816c2-s17-smoke.ps1`
- dev manifest：`deploy/k8s/dev/content-service-dev.yaml`

## 输出

- 如需修改：`deploy/k8s/dev/content-service-dev.yaml`
- 如需修改：`services/ircs-content-service/**` 中 config listener/topology 相关代码或测试
- 如需修改：`scripts/smoke/*config*`
- 本 task spec 的定位、变更、验证、rollout、cleanup 和恢复记录。

## 影响范围

包含：

- content-service config listener/topology。
- content-service dev deployment 的 Rabbit 和 listener env。
- C2-S17 content config event live smoke。

不包含：

- provider、R2、SMTP、normalization、notification migrator。
- 全局 docs/tasks index 与 089 汇总。
- 其他服务的 config listener live matrix。

## 依赖

- `kubectl` 可访问 `ircs-dev` namespace。
- `ircs-content-service`、`ircs-config-service`、RabbitMQ、PostgreSQL dev 实例可用。
- 如执行 rollout，仅限 `deployment/ircs-content-service`。
- smoke 脚本必须回收本次 port-forward，并恢复临时配置值。

## 风险

- live cluster 当前 deployment 可能与本地 manifest 或镜像不同步，需以运行中 pod 证据为准。
- `APP_CONTENT_SCRAPER_BASE_URL` 运行时覆盖会使 DB fallback effective read 难以直接观测；
  smoke 需要记录该边界。
- Rabbit queue stats 是瞬时数据，publish/consume/ack 证据需要结合 queue detail 与服务日志。
- 如果 content-service 镜像仍缺少最新 topology 代码，单纯重启无法声明队列，需要重新构建镜像；
  本轮优先定位并只做必要动作。

## 验收标准

- 记录 `q.content.config_changed` 未声明原因。
- content-service tests 通过，前提是修改了 content-service 代码或测试。
- `kubectl apply --dry-run=server -f deploy/k8s/dev/content-service-dev.yaml` 通过。
- Rabbit 中存在 `q.content.config_changed` 且有 content-service consumer，或记录新阻塞。
- C2-S17 live smoke 重跑完成，记录 publish/consume/evict 成功或新阻塞。
- 临时 `app.content.internal.scraper-base-url` 值已恢复，无本次 port-forward、DB 临时值或其他残留。

## 测试边界

代码测试：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
```

K8S dry-run：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\content-service-dev.yaml
```

Rabbit 队列证据：

```text
kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers
```

真实 smoke：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-content-live-02816c2-s17-smoke.ps1 -ConfirmLiveRabbit
```

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md`

## 关联任务

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/in-progress/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/done/028-16-c2-s15-live-matrix-smoke-harness.md`

## 当前状态

2026-06-09：任务创建。下一步先核对 content-service live deployment/pod env、镜像、日志与
Rabbit 队列，再决定是否只需 rollout 或需要修改 manifest/code。

2026-06-09：已完成 C2-S18。content-service config listener 队列缺失的根因已定位并修复到
dev live 环境；C2-S17 已重跑，原 Rabbit queue 404 阻塞解除，但 live publish/consume/evict
出现新的上游阻塞。

根因定位：

- live `ircs-content-service` deployment 实际没有 `APP_CONTENT_CONFIG_LISTENER_ENABLED=true`，
  `last-applied-configuration` 仍是旧 manifest。
- live content-service 旧 pod 为 `ircs-content-service-58d98958c8-pbr6v`，创建于
  `2026-06-08T03:32:14Z`，镜像 digest 为
  `sha256:5557e950adbce042a9d51a15fe551ae9cefe86227be50b9e52ef976a68ade42a`。
- 旧镜像内未找到 `ContentConfigEventTopology` / `SystemConfigChangedListener`，因此即使 Rabbit
  连接成功，也不会声明 `q.content.config_changed`。
- Rabbit connection 本身可用，旧 content-service 日志已有：
  `Created new connection: ... amqp://admin@10.101.99.87:5672/`。

修复动作：

- 未修改 content-service 代码；本地代码和 manifest 已具备所需 topology/listener/env。
- 运行 content-service tests 后，推送最新 `harbor.mnnu.eu.org/ircs/ircs-content-service:dev`
  arm64/linux 镜像。
- 应用 `deploy/k8s/dev/content-service-dev.yaml`，只 rollout
  `deployment/ircs-content-service`，未新增 HTTPRoute，未修改其他服务 deployment。

验证结果：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\content-service-dev.yaml
deployment.apps/ircs-content-service configured (server dry run)
service/ircs-content-service unchanged (server dry run)

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\docker\push-service-image-jib.ps1 -Service ircs-content-service -Tag dev -Architecture arm64 -Os linux
exitCode=0

kubectl apply -f deploy\k8s\dev\content-service-dev.yaml
deployment.apps/ircs-content-service configured
service/ircs-content-service unchanged

kubectl -n ircs-dev rollout status deployment/ircs-content-service --timeout=180s
deployment "ircs-content-service" successfully rolled out
```

rollout 后证据：

```text
pod=ircs-content-service-5c7d485f95-bw4sm
created=2026-06-09T09:34:19Z
imageID=harbor.mnnu.eu.org/ircs/ircs-content-service@sha256:fe13a48d60dadf9a89fbf7130151a0e6116470f27627877c4a20ed4bbb1b3886
restartCount=0
ready=true

APP_CONTENT_CONFIG_LISTENER_ENABLED=true
APP_CONTENT_RESOLVER_PRESET_SEED_ENABLED=true
APP_CONTENT_SCRAPER_BASE_URL=http://ircs-scraper-service.ircs-dev.svc.cluster.local:8080

unzip -l /app/classpath/ircs-content-service-0.1.0-SNAPSHOT-plain.jar
com/prodigalgal/ircs/content/ContentConfigEventTopology.class
com/prodigalgal/ircs/content/SystemConfigChangedListener.class
application.yaml

rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers
q.content.config_changed      0 0 0 1
q.content.config_changed.dlq  0 0 0 0
```

C2-S17 重跑结果：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-content-live-02816c2-s17-smoke.ps1 -ConfirmLiveRabbit
MARK 1/7 kubectl can access target namespace
MARK 2/7 config-service and content-service deployments are ready
MARK 3/7 Rabbit content config queue is present with a consumer
MARK 4/7 original config-service value captured before write
MARK 5/7 config-service wrote temporary non-sensitive value
status: FAILED
reason: LIVE_SMOKE_ERROR_RESTORE_FAILED
error: RabbitMQ queue event not observed for temporary-update; last={"name":"q.content.config_changed","consumers":1,"messages":0,"messagesReady":0,"messagesUnacknowledged":0,"publish":0,"deliver":0,"deliverGet":0,"ack":0}
restoration.fallback.status: SQL_FALLBACK_RESTORED
restoration.fallback.output: DELETE 0
```

新阻塞：

- `q.content.config_changed` 已存在且有 1 个 consumer，但临时 config update 后 queue stats 未观察到
  publish / deliver / ack。
- live `ircs-config-service` deployment 当前没有 `SPRING_RABBITMQ_HOST`、
  `SPRING_RABBITMQ_USERNAME`、`SPRING_RABBITMQ_PASSWORD` env；日志没有 Rabbit connection 或
  `SystemConfigChange` publish 线索。
- live config-service 镜像内未找到 `SystemConfigChangePublisher.class`，只看到
  `application.yaml`；本地 `deploy/k8s/dev/config-service-dev.yaml` 已包含 Rabbit env，但未应用到
  live deployment。
- 本轮授权 rollout 只限 content-service，因此未修改或 rollout config-service。后续应由独立
  config-service publisher deployment 同步切片处理。

恢复与残留核查：

```text
select 'target_rows=' || count(*) from system_configs
where config_key = 'app.content.internal.scraper-base-url';
target_rows=0

select 'temp_rows=' || count(*) from system_configs
where config_value like 'http://content-config-live-smoke.invalid/%';
temp_rows=0

Get-CimInstance Win32_Process ... kubectl.exe port-forward ... 19087/19088/19089
无输出，未发现本次 port-forward 残留。
```

完成边界：

- C2-S18 完成：content-service dev Rabbit config listener topology 已真实声明队列，Rabbit
  management API 404 原阻塞解除。
- C2-S17 未完成：真实 publish/consume/evict 仍阻塞于 config-service live publisher 未部署且未连
  Rabbit；临时值已恢复，无 DB 或 port-forward 残留。
