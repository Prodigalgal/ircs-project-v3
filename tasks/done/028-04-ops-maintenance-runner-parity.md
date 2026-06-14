# 028-04 ops maintenance runner parity 子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/044-ops-maintenance-runner-parity-analysis.md`
- `docs/reports/023-ops-service-boundary-analysis.md`
- `docs/migrations/042-ops-service-guarded-operations-debug-stream-extraction.md`
- V1 `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\system`

## 背景

`025-20` 已让 `ircs-ops-service` 覆盖 V1 maintenance init/trend sync 的 HTTP
contract、in-memory session 和 SSE 事件形态，但当前行为仍停留在 dev guarded
日志，不执行真实 runner。`028` 行为 parity 主线要求至少选择一个低风险维护入口，
把它推进到可验证的副作用，而不是只证明路由和 SSE 可访问。

V1 maintenance 写命令中，aggregation reset、sanitize、LLM clean、search hard reset
和完整 trend sync 都可能触发批量修改、外部 provider 或 Elasticsearch index lifecycle。
本子任务只接一个低风险最小切片：`POST /api/v1/ops/maintenance/search/reindex-unified/init`
创建 session 后，SSE runner 从 `unified_videos` 小批量读取 ID，并投递
`q.search.sync.unified` 的 `INDEX` 消息。它不 hard reset index，不删除业务数据，
不访问公网 provider。

## 目标

- 保留现有 maintenance HTTP route 和 session/SSE contract。
- 为 `search-reindex-unified` 增加 dev-safe runner。
- runner 仅按小限额投递 unified search index 消息，形成可验证副作用。
- 在 SSE 中输出 runner 已执行、投递数量和完成事件。
- 增加本地单元测试证明消息投递和 guarded fallback 行为。

## 非目标

- 不实现 RawVideo 全量 reindex runner。
- 不调用 V1 `IndexLifecycleService.hardResetIndex` 等 destructive/production 级逻辑。
- 不执行 aggregation reset、sanitize、LLM clean、area/language clean。
- 不触发完整 trend sync，不访问 TMDB、豆瓣或其他外部 provider。
- 不新增公网 HTTPRoute、Pod、Service、PVC 或中间件。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/k8s/current-environment-2026-06-02.md`。

## 输入

- V3 `services/ircs-ops-service` 当前 `MaintenanceController`、`MaintenanceSessionService`
  和 guarded SSE 实现。
- V3 `shared:ircs-contracts` 的 `QueueTopic.SEARCH_SYNC_UNIFIED`、
  `SearchSyncMessage`、`SearchEntityType.UNIFIED_VIDEO`、`SyncOperation.INDEX`。
- V3 PostgreSQL 迁移中已存在的 `unified_videos.id`、`unified_videos.updated_at`。
- V3 search-service 当前支持结构化或 legacy UUID 的 search sync message parser。

## 输出

- `docs/reports/044-ops-maintenance-runner-parity-analysis.md`
- `tasks/in-progress/028-04-ops-maintenance-runner-parity.md`
- `services/ircs-ops-service` 中的 dev-safe unified reindex runner 实现。
- 对应 ops-service 本地测试。

## 影响范围

包含：

- `services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops`
- `services/ircs-ops-service/src/test/java/com/prodigalgal/ircs/ops`
- `services/ircs-ops-service/src/main/resources/application.yaml`

不包含：

- `deploy/k8s/**`
- `services/ircs-search-service/**`
- `services/ircs-content-service/**`
- `services/ircs-admin-bff/**`
- `services/ircs-portal-bff/**`
- `docs/index.md`
- `tasks/index.md`
- `docs/k8s/current-environment-2026-06-02.md`

## 依赖

- `ircs-ops-service` 可连接 PostgreSQL 和 RabbitMQ。
- `unified_videos` 表存在且包含 `id`、`updated_at` 字段。
- `q.search.sync.unified`、`x.search` 和 routing key `search.sync.unified` 仍由共享
  `QueueTopic` 统一定义。
- search-service 能解析结构化 `SearchSyncMessage` 或 legacy UUID 消息。

## 风险

- 即便不 hard reset index，投递 search sync 消息仍可能被 search-service 消费并写入
  Elasticsearch；必须使用小限额。
- ops-service 直接读取 `unified_videos` 属于迁移桥阶段行为，不应扩展为长期跨域写模型。
- 如果 dev 数据库没有 `unified_videos` 数据，runner 应产生 `0` 条投递并正常结束。
- 当前 session 仍是进程内状态，服务重启会丢失 session。

## 验收标准

- `POST /api/v1/ops/maintenance/search/reindex-unified/init` 仍返回 `sessionId`。
- `GET /api/v1/ops/maintenance/stream/{sessionId}` 对 `search-reindex-unified` 执行真实
  dev-safe runner，而不是只输出 guarded 日志。
- runner 最多投递配置限额内的 unified search `INDEX` 消息。
- 其他 maintenance task 仍保持 guarded fallback，不执行真实副作用。
- 本地 Gradle 测试通过。

## 测试边界

本轮必须运行：

```text
.\gradlew.bat :services:ircs-ops-service:test
```

可选后续由主 agent 执行：

```text
kubectl rollout status deployment/ircs-ops-service -n ircs-dev --timeout=240s
ClusterIP smoke:
  POST /api/v1/ops/maintenance/search/reindex-unified/init
  GET  /api/v1/ops/maintenance/stream/{sessionId}
RabbitMQ/search sync side-effect check
kubectl -n ircs-dev get httproute
ResourceQuota 后置检查
```

## 关联文档

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/044-ops-maintenance-runner-parity-analysis.md`
- `docs/reports/023-ops-service-boundary-analysis.md`
- `docs/migrations/042-ops-service-guarded-operations-debug-stream-extraction.md`

## 关联任务

- 父任务：`tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- 前置完成：`tasks/done/025-20-ops-service-guarded-operations-debug-stream-contract-mvp.md`
- 前置完成：`tasks/done/028-01-bff-request-audit-collection-parity.md`

## 当前状态

2026-06-07：

- 已完成 V1/V3 证据核对。
- 已选择 `search-reindex-unified` 作为第一条真实 runner。
- 已实现 dev-safe 限额读取、结构化 search sync 消息投递、SSE 输出和测试。

## 实施结果

本轮新增 `search-reindex-unified` dev-safe runner：

```text
POST /api/v1/ops/maintenance/search/reindex-unified/init
  -> 返回 sessionId

GET /api/v1/ops/maintenance/stream/{sessionId}
  -> taskName = search-reindex-unified 时执行真实 runner
  -> 从 unified_videos 按 updated_at desc 读取最多 OPS_MAINTENANCE_REINDEX_DEV_LIMIT 条 ID
  -> 向 QueueTopic.SEARCH_SYNC_UNIFIED 投递 SearchSyncMessage(INDEX)
  -> SSE 输出 LOG / PROGRESS 或 WARN / DONE
```

保留边界：

- `sanitize`、`aggregation-reset`、`search-reindex-raw`、`trend-sync` 等未接入 runner 的
  task 仍只走 guarded fallback。
- 不 hard reset Elasticsearch index。
- 不修改业务表。
- 不新增 K8S 资源或公网 HTTPRoute。

完成标记：

```text
MARK 1/1 search-reindex-unified SSE runner publishes capped unified search INDEX messages
```

## 测试结果

已运行：

```text
.\gradlew.bat :services:ircs-ops-service:test
BUILD SUCCESSFUL
```

覆盖点：

- `MaintenanceReindexCommandTest`
  - 有候选 ID 时投递结构化 `SearchSyncMessage`。
  - 无候选 ID 时不投递 RabbitMQ 消息。
  - `dev-limit=0` 时不查询数据库、不投递消息。
- `MaintenanceSessionServiceTest`
  - `search-reindex-unified` SSE 会调用真实 runner。
  - 未支持 task 继续 guarded，不调用真实 runner。

## 剩余项

- RawVideo reindex dev-safe runner 未接入。
- 完整 search index hard reset/bulk mode 未接入，仍需单独 task spec。
- trend sync 完整 provider 拉取和最小 `last_trend_at` 更新均未接入。
- session 仍为进程内状态，重启和多副本恢复后续再设计。

## 主线程集成验收

2026-06-07：

```text
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
BUILD SUCCESSFUL

ircs-ops-service@sha256:77736bf364124fe8e582f71ce72cee7edf065c2812b16bbcfe1101963046253d

deployment/ircs-ops-service    Ready 1/1
deployment/ircs-search-service Ready 1/1
deployment/ircs-admin-bff      Ready 1/1
```

K8S smoke：

```text
.\scripts\smoke\behavior-02802-02804-parity-smoke.ps1
MARK 6/8 ops search-reindex-unified runner publishes dev-safe progress
MARK 7/8 search-service consumes reindex message and indexes unified video
MARK 8/8 smoke cleanup leaves no temporary DB/ES residue
```

后置检查：

```text
kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
```
