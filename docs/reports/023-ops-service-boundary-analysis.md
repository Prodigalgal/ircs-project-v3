# 023 ops-service maintenance/dashboard 边界分析报告

状态：完成（边界分析、task spec、MVP 实现、`ircs-dev` rollout 与 smoke 均已完成）

更新时间：2026-06-04

## 背景

当前主线目标是将 V1 单体 `D:\WorkSpace\Project\ircs\ircs-project-v1`
完整拆分迁移到当前 V3 微服务仓库。`ircs-ops-service` 完成接入后，当前
`ircs-dev` 已接入并 Ready 的目标业务服务为 18/18，尚未部署接入的服务为：

```text
暂无
```

Pod quota 已通过 `024-01` 小幅调整到 `24`；ops-service 接入后当前 quota 为
`pods=23/24`、`services=19/20`。本报告原始主体用于分析
`ircs-ops-service` 的 maintenance/dashboard 边界，并补充与 ops 目标 ownership
强相关的 audit、DLQ、traffic 证据；后续实施已按该边界完成 MVP，不新增公网
HTTPRoute，不接入破坏性 maintenance、DLQ retry/discard、traffic reset 或 request
audit 多服务采集。

## 上游来源

- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `tasks/in-progress/011-mvp-service-onboarding-parent.md`
- V1 后端源码：`D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend`
- V1 admin 前端源码：`D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-frontend`

## 结论

`ircs-ops-service` 是 admin control plane，不应在首个 MVP 中直接接管高风险全量写操作。
首个 maintenance/dashboard MVP 已按只读优先边界落地：

- 保留 V1 dashboard 的只读观测接口，覆盖统计、分析、实时指标和数据源质量。
- 保留 maintenance 的 active session 查询与 SSE 事件模型兼容边界。
- 暂不接入任何会批量修改内容、聚合、搜索索引或外部趋势同步的 maintenance init 命令。
- 暂不接入 DLQ retry/discard、traffic reset 等运维写命令。
- 暂不新增公共 HTTPRoute，仍保持 ClusterIP only。

核心原因：

- V1 dashboard 主要是 read aggregation，可通过共享 PostgreSQL、Elasticsearch、
  RabbitMQ、Valkey/R2 健康检查形成低风险 MVP。
- V1 maintenance init 命令会重置 `raw_videos` 状态、删除聚合绑定、删除
  `unified_videos`、重建 ES index，并触发后台调度，首个 ops-service 接入不宜直接迁移。
- V1 DLQ 和 request audit 已有清晰 ops ownership，但 DLQ retry/discard 会重新投递消息，
  request audit 在 V3 多服务形态下需要决定是 edge 记录、各服务本地记录，还是事件汇聚。

## V1 证据汇总

### Controller

```text
modules/system/controller/DashboardController.java
modules/system/controller/MaintenanceController.java
modules/system/controller/RequestAuditController.java
modules/system/controller/TrafficMonitorController.java
modules/system/controller/DlqController.java
```

V1 `DashboardController`：

```text
GET  /api/v1/dashboard/statistics
GET  /api/v1/dashboard/analysis?days=14
POST /api/v1/dashboard/refresh?days=14
GET  /api/v1/dashboard/metrics
GET  /api/v1/dashboard/metrics/stream
GET  /api/v1/dashboard/source-quality
```

V1 `MaintenanceController`：

```text
GET  /api/v1/ops/maintenance/active
POST /api/v1/ops/maintenance/sanitize/init
POST /api/v1/ops/maintenance/aggregation/reset-init
POST /api/v1/ops/maintenance/unified/recalculate-init
POST /api/v1/ops/maintenance/area/clean-init
POST /api/v1/ops/maintenance/language/clean-init
POST /api/v1/ops/maintenance/llm/sanitize/init
POST /api/v1/ops/maintenance/llm/area/init
POST /api/v1/ops/maintenance/llm/genre/init
POST /api/v1/ops/maintenance/llm/category/init
POST /api/v1/ops/maintenance/search/reindex-raw/init
POST /api/v1/ops/maintenance/search/reindex-unified/init
GET  /api/v1/ops/maintenance/stream/{sessionId}
POST /api/v1/ops/maintenance/trend/sync
```

V1 audit、traffic、DLQ 相关 ops Controller：

```text
GET    /api/v1/ops/request-audit
GET    /api/v1/ops/request-audit/summary
GET    /api/v1/ops/traffic/status
POST   /api/v1/ops/traffic/reset
GET    /api/v1/ops/dlq
POST   /api/v1/ops/dlq/{id}/retry
POST   /api/v1/ops/dlq/batch-retry
DELETE /api/v1/ops/dlq/{id}
POST   /api/v1/ops/dlq/batch-discard
```

所有上述后端入口均要求 `hasRole('ADMIN')`。V1 admin 前端 `apiSlice` 统一使用
`baseUrl: '/api/v1'`，因此前端短路径 `/ops/maintenance/**`、`/ops/dlq/**`、
`/dashboard/**` 对应后端完整路径 `/api/v1/**`。

### dashboard 依赖

V1 `DashboardServiceImpl`：

```text
RawVideoRepository.countEstimate()
UnifiedVideoRepository.countEstimate()
CollectionTaskRepository.count()
SearchQueryService.getDashboardStats()
SearchQueryService.getDashboardAnalysis(days)
SearchQueryService.getSourceQualityStats()
SystemMetricsService.getCurrentMetrics()
SystemMetricsService.getMetricsHistory()
```

V1 dashboard DTO / VO：

```text
DashboardStatsDTO
DashboardAnalysisDTO
ChartDataPoint
EfficiencyStatsDTO
SystemMetricsVO
QueueMetricVO
RedisMetricVO
SourceQualityVO
```

V1 `SystemMetricsServiceImpl` 会读取：

```text
RabbitAdmin queue properties
QueueTopic.values()
QueueTopic.dlqName
Micrometer rabbitmq.queue.* metrics
Valkey/Redis INFO
R2 ImageStorageService.isActive()
JVM Runtime memory
local disk free space
```

dashboard 相关前端入口：

```text
ircs-frontend/src/features/dashboard/dashboardApi.js
ircs-frontend/src/features/dashboard/Dashboard.jsx
ircs-frontend/src/features/dashboard/components/SystemMonitor.jsx
```

### maintenance 依赖

V1 `MaintenanceServiceImpl` 使用进程内 session 与 SSE：

```text
MaintenanceSession
MaintenanceEvent
MaintenanceService.SessionInfo
AtomicBoolean isMaintenanceRunning
AtomicReference currentSessionRef
SseEmitter
```

V1 maintenance 会直接执行或依赖以下高风险操作：

```text
raw_videos.normalization_status -> PENDING
raw_video_unified_video TRUNCATE
search_sync_tasks 删除 UNIFIED_VIDEO 任务
unified_videos deleteAllInBatch
unified_videos.metadata_status -> DIRTY
raw_videos.aggregation_status -> PENDING
IndexLifecycleService.hardResetIndex(RAW_VIDEO / UNIFIED_VIDEO)
IndexLifecycleService.startBulkMode / endBulkMode
SearchSyncService.batchSyncRawVideos / batchSyncUnifiedVideos
TrendSyncService.syncTrends
QueueTopic.SEND_MAIL completion notification
```

V1 `MaintenanceSuspensionAspect` 会在 maintenance 运行时拦截 `@Scheduled` 方法，避免定时任务与全量运维操作抢锁。V3 若迁移写命令，需要重新定义跨服务调度暂停策略，不能只复制单体 AOP。

### audit 证据

V1 `RequestAuditFilter` 只记录 `/api/v1/**` 请求，字段来自：

```text
username
method
path
queryString
statusCode
durationMs
clientIp
userAgent
traceId
errorMessage
```

V1 `request_audit_logs` 表来自：

```text
db/changelog/2026/07/add-request-audit-logs.sql
```

主要索引：

```text
idx_req_audit_created
idx_req_audit_user
idx_req_audit_method_path
idx_req_audit_status
idx_req_audit_ip
```

V1 前端 `ops/log-audit` 已重定向到 `ops/request-audit`；当前源码中没有单独
`LogAuditController`。

### DLQ 证据

V1 `QueueTopic` 统一定义 exchange、queue、routing key、display group 和 `dlqName`：

```text
x.ingest       q.ingest.video
x.process      q.normalize.video
x.process      q.enrich.metadata
x.process      q.enrich.metadata.result
x.process      q.process.playlist_sync
x.process      q.fetch.metadata.douban
x.process      q.fetch.metadata.tmdb
x.process      q.fetch.metadata.rt
x.storage      q.storage.image
x.storage      q.storage.avatar_sync
x.storage      q.storage.image_unlink
x.storage      q.storage.image_delete
x.notification q.notification.mail
x.interaction  q.interaction.watch_progress
x.search       q.search.sync.raw
x.search       q.search.sync.unified
x.dlx          <queue>.dlq
```

V1 `RabbitMqConfig` 为每个正常队列声明：

```text
x-dead-letter-exchange = x.dlx
x-dead-letter-routing-key = <queue>.dlq
```

并声明 `dlqListenerContainer` 监听所有 `<queue>.dlq`。`DlqConsumer` 将死信交给
`FailedMessageService.captureDlqMessage`，落库到 `failed_messages`。

V1 `failed_messages` 表来自：

```text
db/changelog/2026/01/21-01-add-failed-messages.sql
```

字段：

```text
id
created_at
updated_at
version
queue_name
routing_key
exchange
payload
exception_stack
retry_count
status
```

状态：

```text
PENDING
RETRIED
DISCARDED
```

### traffic 证据

V1 `TrafficMonitorServiceImpl` 读取 Redis key：

```text
traffic:limit:*
```

并把 key 分为 global limiters 和 credential limiters。credential label 依赖
`CredentialRepository` 查询 `credentials`。`POST /api/v1/ops/traffic/reset` 会删除指定
Redis slot，属于运维写命令。

## 数据边界

### ops-service 目标 owning 表

```text
request_audit_logs
failed_messages
```

说明：

- `request_audit_logs` 是 ops 可观测性数据，适合作为 ops-service 目标 owning 表。
- `failed_messages` 是 DLQ 人工处理数据，适合作为 ops-service 目标 owning 表。
- V1 没有持久化 `maintenance_sessions` 表；maintenance session 是进程内状态。

### dashboard MVP 读取依赖

```text
raw_videos
unified_videos
collection_tasks
search_sync_tasks
Elasticsearch raw/unified indices
RabbitMQ queue properties and Micrometer metrics
Valkey/Redis server info
R2/storage health
```

dashboard MVP 应保持只读，不写内容、任务、搜索或存储表。

### maintenance 写命令涉及的外部 ownership

```text
raw_videos                         content/ingestion ownership
raw_video_unified_video            aggregation ownership
unified_videos                     content/aggregation ownership
search_sync_tasks                  search ownership
Elasticsearch indices              search ownership
q.notification.mail                notification ownership
external trend sync                scraper/metadata ownership
```

这些写命令可以作为 ops-service 目标控制入口，但后续实现必须通过内部 API、事件、
受控 Job 或明确的 maintenance contract 执行，不能让 ops-service 直接跨域改表成为常态。

## 推荐 MVP 边界

### HTTP 边界

本次 `ircs-ops-service` maintenance/dashboard MVP 覆盖：

```text
GET  /api/v1/dashboard/statistics
GET  /api/v1/dashboard/analysis?days=14
GET  /api/v1/dashboard/metrics
GET  /api/v1/dashboard/source-quality
POST /api/v1/dashboard/refresh?days=14

GET  /api/v1/ops/maintenance/active
GET  /api/v1/ops/maintenance/stream/{sessionId}
```

其中：

- `POST /api/v1/dashboard/refresh` 仅允许做 dashboard cache eviction，不允许改业务数据。
- `GET /api/v1/ops/maintenance/active` 可在没有任务时返回 `204 No Content`。
- `GET /api/v1/ops/maintenance/stream/{sessionId}` 只保留 V1 SSE event shape；若 session 不存在，返回过期/不存在事件后结束。

### MVP 非目标

以下内容仍不属于本次已落地的 ops-service MVP：

- 不接入任何 maintenance init 写命令。
- 不重置 `raw_videos`、`unified_videos`、`raw_video_unified_video` 或 `search_sync_tasks`。
- 不重建 Elasticsearch index。
- 不触发 `TrendSyncService.syncTrends`。
- 不投递 `q.notification.mail`。
- 不启动 DLQ consumer，不消费任何 `<queue>.dlq`。
- 不接入 DLQ retry/discard。
- 不接入 traffic reset。
- 不做 request audit filter 的 V3 多服务落点决策。
- 不新增公网 HTTPRoute。
- 不做物理数据库拆分。

## 实施结果 2026-06-04

`ircs-ops-service` 已完成 MVP 实现、ARM64 镜像推送、`ircs-dev` rollout 和
ClusterIP smoke。

部署结果：

```text
image:  harbor.mnnu.eu.org/ircs/ircs-ops-service:dev
digest: sha256:c7f873757ed678fbe909de020eed4762b25e852d96defa5aa28e8d27f769589f
svc:    ircs-ops-service, ClusterIP 10.102.243.27
ready:  deployment/ircs-ops-service 1/1
quota:  pods=23/24, services=19/20
route:  no HTTPRoute in ircs-dev
```

ClusterIP smoke：

```text
GET  /api/v1/dashboard/statistics        -> 200
GET  /api/v1/dashboard/analysis?days=14  -> 200
GET  /api/v1/dashboard/metrics           -> 200
GET  /api/v1/dashboard/source-quality    -> 200
POST /api/v1/dashboard/refresh?days=14   -> 204
GET  /api/v1/ops/maintenance/active      -> 204
GET  /api/v1/ops/maintenance/stream/{id} -> ERROR + DONE
```

验证命令：

```text
.\gradlew.bat :services:ircs-ops-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\ops-service-dev.yaml
kubectl rollout status deployment/ircs-ops-service -n ircs-dev --timeout=240s
```

smoke 后置检查：

```text
raw_videos, unified_videos, collection_tasks, search_sync_tasks: 0,0,0,0
RabbitMQ queues and DLQs: all messages=0
HTTPRoute in ircs-dev: No resources found
```

实施过程中修复：

- `/api/v1/dashboard/analysis` 的 `Instant` SQL 参数类型推断失败，改为
  `java.sql.Timestamp`。
- `raw_category` schema drift，使用 `source_name/source_code` 替代旧 `name`
  字段。
- analysis 可选分布查询增加空数组降级，避免非核心分布失败导致整个 dashboard 500。
- Hikari pool 调整为 `maximum-pool-size=2`、`minimum-idle=0`，降低 dev
  PostgreSQL 连接压力。

## 风险

- dashboard 读面依赖多类基础设施，若直接复制 V1 `SystemMetricsServiceImpl`，可能把 R2、
  RabbitMQ、Redis、ES 和本地磁盘检查全部绑进 ops-service，导致 smoke 面过宽。
- V3 已拆分多个 worker 和服务，dashboard 的 queue depth / consumer count 需要确认只读取
  `ircs-dev` namespace-local RabbitMQ，不能误读生产环境。
- V1 maintenance session 是进程内状态；V3 多副本、重启或 rollout 会丢失 session。
- V1 `MaintenanceSuspensionAspect` 只适用于单体，V3 要暂停跨服务 scheduler 需要新的协作机制。
- maintenance 写命令具有破坏性；尤其 aggregation reset 会删除聚合视频并重置 ES，必须单独拆子任务并设置强保护。
- DLQ retry 会重新投递消息，可能触发重复写入或跨服务副作用；首个 MVP 只应读取 queue metrics，不处理死信。
- request audit 在 V3 中不能只由 ops-service 自己记录，否则看不到其他服务请求；需要后续明确 edge 或 per-service 采集策略。
- dashboard `source-quality` 和 `analysis` 依赖 search-service/ES；ES 缺数据时应有兼容降级或明确失败语义。

## 验收结果

文档阶段验收：

- 本报告列出 V1 ops/dashboard/maintenance/audit/DLQ/traffic 的 Controller、Service、
  DTO/VO、表、队列和前端入口证据。
- `tasks/done/023-01-ops-service-maintenance-dashboard-mvp.md` 明确后续 MVP 的目标、
  非目标、输入、输出、影响范围、依赖、风险、验收标准和测试边界。
- 本报告和对应 task spec 已回写实现结果、验证结果和后续增强边界。

实现阶段验收已完成：

- `ircs-ops-service` 在 `ircs-dev` Ready 1/1。
- 仅创建 ClusterIP Service，不新增 HTTPRoute。
- dashboard 读接口返回 V1 admin 前端可消费的字段 shape。
- dashboard smoke 不新增、不修改业务数据。
- maintenance active 在无任务时返回 `204 No Content`。
- maintenance stream 对不存在 session 返回明确过期/不存在事件并结束。
- RabbitMQ 队列和 DLQ 消息数在只读 smoke 前后不增加。
- 不触发任何全量 maintenance 写命令。

## 后续增强

- 单独拆 `ops-service request-audit` 子任务，决定 V3 多服务请求审计的采集位置。
- 单独拆 `ops-service DLQ read/manage` 子任务，先只读 `failed_messages`，再谨慎接 retry/discard。
- 单独拆 `ops-service traffic monitor` 子任务，先只读 traffic status，再接 reset 命令。
- 单独拆 destructive maintenance 子任务，通过内部 API、受控 Job 或事件机制接入全量重置与重建。
- 为 maintenance session 引入 Valkey 或持久化 session 表，支持重启恢复和多副本。
