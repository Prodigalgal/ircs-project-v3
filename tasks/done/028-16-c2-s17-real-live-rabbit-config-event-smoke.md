# 028-16-C2-S17 real live Rabbit config event smoke task spec

状态：完成

父任务：

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

上游来源：

- `tasks/in-progress/028-16-c2-s16-live-rabbit-config-event-safe-smoke.md`
- `tasks/done/028-16-c2-s15-live-matrix-smoke-harness.md`

## 背景

C2-S15 已经用 no-write harness 固定 config event payload 安全形态，并用 content-service
代表切片证明 `SystemConfigChangedEvent` 可以按 key evict 后读取 DB fallback 新值。C2-S16
只完成了 live-safe dry-run，没有执行真实 DB 写入或 Rabbit publish/consume。当前用户已允许真实
测试和真实外网测试，因此本切片需要在 dev live 环境内使用非敏感 key、临时值和恢复原值流程，
完成 content-service 代表 key 的真实 config update / publish / consume / evict smoke。

## 目标

- 使用 content-service 非敏感 key `app.content.internal.scraper-base-url`。
- 记录原值，不打印 secret，不触碰 R2/provider/SMTP 或其他敏感配置。
- 通过 config-service 完成真实临时配置写入，并取得 write/publish evidence。
- 证明 Rabbit `config.system.changed` 事件被路由到 content-service consumer 队列并被消费。
- 取得 content-service cache evict/read evidence。
- 最后恢复原值并记录 restoration evidence，确保不留下临时配置。
- 如果 Rabbit、port-forward、K8S 或 API 不可用，记录精确错误和已恢复状态。

## 非目标

- 不修改 service 业务代码。
- 不修改 `deploy/**` 常驻 manifest。
- 不触碰 R2/provider/SMTP。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/reports/089`、`docs/migrations/089`。
- 不做全服务 config event live matrix；本轮只覆盖 content-service 代表切片。

## 输入

- key：`app.content.internal.scraper-base-url`
- 临时值：由 smoke 脚本生成的 `http://content-config-live-smoke.invalid/<runId>`
- event contract：`x.domain.events` / `config.system.changed`
- content-service consumer queue：`q.content.config_changed`
- 上游 S15/S16 的 payload safety、dry-run 和 content cache evict 单测证据。

## 输出

- 如需扩展：`scripts/smoke/*config*`
- 本 task spec 的真实命令输出摘要、cleanup/restoration 证据、失败点和残留状态。

## 影响范围

包含：

- `scripts/smoke/*config*`
- `tasks/in-progress/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`

不包含：

- `services/**`
- `deploy/**`
- R2/provider/SMTP 相关配置和凭据。
- 全局 docs/tasks index。

## 依赖

- `kubectl` 可访问 `ircs-dev` namespace。
- config-service 与 content-service dev pod/service 可用。
- PostgreSQL 与 RabbitMQ dev 实例可用。
- 如脚本使用 port-forward，必须在 finally 中回收本次启动的本地进程。

## 风险

- content-service 如果存在运行时环境变量 override，DB fallback 的读值可能不会成为实际 effective
  value；脚本需要检测并记录该状态。
- 没有 content-service 只读 smoke endpoint 时，cache evict/read evidence 可能只能通过日志、队列消费
  状态、DB restore 后再次触发事件等间接证据组合证明。
- Rabbit message routing/consumption 的瞬时性较强，脚本需要采集 publish 前后 queue
  `messages/messages_ready/messages_unacknowledged/consumers` 与服务日志摘要。
- 任何失败路径都必须优先 restore 原值，避免留下临时配置。

## 验收标准

- 真实命令输出摘要包含：原值摘要、临时值摘要、config-service write/publish evidence、
  Rabbit queue consume evidence、content-service cache evict/read evidence、恢复原值 evidence。
- 不打印敏感值，不触碰 secret，不留下临时配置。
- 如果 live 环境不可用，task spec 记录精确错误、失败阶段和恢复状态。
- 只修改允许范围内文件。

## 测试边界

脚本语法：

```text
powershell -NoProfile -Command "$tokens=$null;$errors=$null;[System.Management.Automation.Language.Parser]::ParseFile('<script>',[ref]$tokens,[ref]$errors) | Out-Null; if ($errors.Count) { $errors | Format-List; exit 1 }; 'PARSER_OK'"
```

真实 smoke：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File <script> -ConfirmLiveRabbit
```

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md`

## 关联任务

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/in-progress/028-16-c2-s16-live-rabbit-config-event-safe-smoke.md`
- `tasks/done/028-16-c2-s15-live-matrix-smoke-harness.md`

## 当前状态

2026-06-09：任务创建，准备读取现有 smoke/API 入口后执行最小 live 验证。

2026-06-09：真实 live smoke 未完成，已收缩停止；没有临时配置残留。

实现结果：

- 新增 `scripts/smoke/config-event-content-live-02816c2-s17-smoke.ps1`。
- 脚本默认 dry-run；真实模式需要 `-ConfirmLiveRabbit`。
- 脚本预检 config-service/content-service rollout、content runtime override、Rabbit queue、
  原始 DB 值，且只有在 `q.content.config_changed` 存在并有 consumer 后才执行临时写入。
- 脚本修正了 dry-run、HTTP helper cleanup 和 K8S API transient timeout retry。

真实执行结果：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-content-live-02816c2-s17-smoke.ps1 -ConfirmLiveRabbit
MARK 1/7 kubectl can access target namespace
MARK 2/7 config-service and content-service deployments are ready
FAILED LIVE_SMOKE_ERROR: RabbitMQ queue detail HTTP 404 body={"error":"Object Not Found","reason":"Not Found"}
restoration: NOT_NEEDED / TEMP_WRITE_NOT_APPLIED
```

阻塞点：

- Rabbit management API 查询 `q.content.config_changed` 返回 404。
- `kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers`
  只列出业务队列，未列出任何 `config_changed` 队列。
- content-service live deployment 存在 `APP_CONTENT_SCRAPER_BASE_URL` 运行时覆盖；即使 Rabbit
  拓扑可用，也会阻断 DB fallback effective read 的直接观测。
- 一次中间尝试在读取 content deployment env 时遇到
  `Unable to connect to the server: net/http: TLS handshake timeout`，发生在临时写入前。
- 另一次早期脚本问题为 `Cannot bind argument to parameter 'LiteralPath' because it is null.`，
  已修复，且发生在临时写入前。

恢复与残留核查：

```text
Get-CimInstance Win32_Process ... kubectl.exe port-forward ... 19087/19088/19089
无输出，未发现本次 port-forward 残留。

select config_key || '|' || coalesce(config_value,'<NULL>') ...
from system_configs
where config_key = 'app.content.internal.scraper-base-url'
   or config_value like 'http://content-config-live-smoke.invalid/%';
无输出，未发现原 key DB 行或 C2-S17 临时值残留。

rabbitmqctl list_queues ... | Select-String "config_changed|q.content.config_changed"
仅表头，无 config_changed 队列。
```

当前状态：

- config-service temporary write：未执行。
- Rabbit config event publish/consume：未执行。
- content-service cache evict/read：未完成，阻塞于 Rabbit queue 缺失与 runtime override。
- 原值恢复：无需恢复；已确认未留下临时值。
- 残留：未发现本次 port-forward、DB 临时值或 Rabbit config 队列残留。

2026-06-09：C2-S19 上线 config-service publisher 后已重跑本 smoke。Rabbit publish /
deliver / ack 真实链路已通过；本任务仍保留进行中，是因为 content-service live deployment 存在
`APP_CONTENT_SCRAPER_BASE_URL` 运行时覆盖，DB fallback read probe 不能直接观察临时 DB 值。

上线修复摘要：

- config-service 新镜像 digest：
  `sha256:de6cc3f758ca62ff42bea22b422b2c570e488ac341da7380effdc9ce93472f05`
- live pod：`ircs-config-service-658d7dd578-xs6tb`
- live env：`SPRING_RABBITMQ_USERNAME=admin`
- live class：`SystemConfigChangePublisher.class` present

真实 smoke 结果：

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

Rabbit 证据：

```text
temporary-update:
before publish=0 deliver=0 deliverGet=0 ack=0 messages=0 consumers=1
after  publish=1 deliver=1 deliverGet=1 ack=1 messages=0 consumers=1
publishDelta=1
deliverDelta=2
ackDelta=1

restore-original:
before publish=1 deliver=1 deliverGet=1 ack=1 messages=0 consumers=1
after  publish=2 deliver=2 deliverGet=2 ack=2 messages=0 consumers=1
publishDelta=1
deliverDelta=2
ackDelta=1
deleteStatus=204
verifiedOriginalState=true
```

清理与残留：

```text
target_rows=0
temp_rows=0
q.content.config_changed      0 0 0 1
q.content.config_changed.dlq  0 0 0 0
kubectl port-forward 19087/19088/19089 无残留
```

剩余阻塞：

- `contentRuntimeOverride.present=true`，`APP_CONTENT_SCRAPER_BASE_URL` 有值；如需把本任务完全归档，
  需要独立安全方案临时移除 override 或提供只读诊断端点来观察 DB fallback read。

2026-06-09：C2-S20 已补可恢复 content DB fallback probe，本任务完成并归档。

最终 smoke：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-content-live-02816c2-s17-smoke.ps1 -ConfirmLiveRabbit -TemporarilyRemoveContentRuntimeOverride -ProbeContentRead -CreateTemporaryRawVideoFixture
MARK 1/7 kubectl can access target namespace
MARK 2/7 config-service and content-service deployments are ready
MARK 3/7 Rabbit content config queue is present with a consumer
MARK 4/7 original config-service value captured before write
MARK 5/7 config-service wrote temporary non-sensitive value
MARK 6/7 Rabbit publish and content consumer ack observed for temporary update
MARK 7/7 content-service read probe observed temporary DB fallback value
status=SUCCESS
reason=LIVE_CONFIG_EVENT_SMOKE_COMPLETED
```

优先级原则：

- 仍固定为运行时覆盖源 -> `system_configs` DB -> service fallback/default。
- 本轮没有让 DB 覆盖 runtime override；只在显式 live probe 开关下临时移除
  `APP_CONTENT_SCRAPER_BASE_URL`，probe 后恢复原值。

runtime override 原值与恢复：

```text
original APP_CONTENT_SCRAPER_BASE_URL=http://ircs-scraper-service.ircs-dev.svc.cluster.local:8080
patch status=TEMPORARILY_REMOVED
after patch present=false
restore status=RESTORED
after restore APP_CONTENT_SCRAPER_BASE_URL=http://ircs-scraper-service.ircs-dev.svc.cluster.local:8080
```

content read 证据：

```text
temporaryValue=http://content-config-live-smoke.invalid/20260609114151
temporaryWrite.effectiveSource=DB
rawVideoFixture.id=102ba5b3-850c-42c4-b8e2-f36821de85f9
httpStatus=502
temporaryValueObserved=true
bodyPreview contains temporary DB base URL
```

清理与残留：

```text
target_rows=0
temp_rows=0
raw_fixture_rows=0
q.content.config_changed      0 0 0 1
q.content.config_changed.dlq  0 0 0 0
kubectl port-forward 19087/19088/19089 无残留
```
