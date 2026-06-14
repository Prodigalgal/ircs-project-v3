# 023-01 ops-service maintenance/dashboard MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

上游来源：

- `docs/reports/023-ops-service-boundary-analysis.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- V1 `DashboardController`
- V1 `MaintenanceController`
- V1 `DashboardServiceImpl`
- V1 `MaintenanceServiceImpl`

关联任务：

- `tasks/done/016-01-scraper-service-manual-scrape-mvp.md`
- `tasks/done/017-01-portal-read-composition-mvp.md`
- `tasks/done/017-02-interaction-feedback-history-mvp.md`

## 背景

`ircs-ops-service` 曾是最后一个尚未接入 `ircs-dev` 的目标业务服务。V1 ops/admin 运行态能力集中在
`modules/system`，其中 dashboard 暴露 `/api/v1/dashboard/**`，maintenance 暴露
`/api/v1/ops/maintenance/**`。V1 dashboard 是多基础设施只读聚合，读取 PostgreSQL、
Elasticsearch、RabbitMQ、Redis、R2 和 JVM/disk 指标；V1 maintenance 同时包含
active session/SSE 观测和多个破坏性全量写命令。

为了降低首个 ops-service 接入风险，本子任务只固定 maintenance/dashboard MVP 边界：
先接 dashboard 读面和 maintenance session 兼容面，不接任何全量重置、重建、DLQ retry
或 traffic reset。

## 目标

- 为 `ircs-ops-service` 接入 ClusterIP only 的 maintenance/dashboard MVP。
- 保留 V1 dashboard 只读接口：
  - `GET /api/v1/dashboard/statistics`
  - `GET /api/v1/dashboard/analysis?days=14`
  - `GET /api/v1/dashboard/metrics`
  - `GET /api/v1/dashboard/source-quality`
- 保留低风险 cache refresh 入口：
  - `POST /api/v1/dashboard/refresh?days=14`
- 保留 maintenance session 查询与 SSE 兼容入口：
  - `GET /api/v1/ops/maintenance/active`
  - `GET /api/v1/ops/maintenance/stream/{sessionId}`
- dashboard smoke 必须只读，不新增或修改业务数据。
- 后续实施时完成本地测试、server dry-run、ARM64 镜像、K8S rollout 和 ClusterIP smoke。

## 非目标

- 不在本轮接入高风险写命令或公网路由。
- 不新增公共 HTTPRoute。
- 不接入以下 maintenance init 命令：
  - `/api/v1/ops/maintenance/sanitize/init`
  - `/api/v1/ops/maintenance/aggregation/reset-init`
  - `/api/v1/ops/maintenance/unified/recalculate-init`
  - `/api/v1/ops/maintenance/area/clean-init`
  - `/api/v1/ops/maintenance/language/clean-init`
  - `/api/v1/ops/maintenance/llm/**`
  - `/api/v1/ops/maintenance/search/reindex-raw/init`
  - `/api/v1/ops/maintenance/search/reindex-unified/init`
  - `/api/v1/ops/maintenance/trend/sync`
- 不重置 `raw_videos`、`unified_videos`、`raw_video_unified_video` 或 `search_sync_tasks`。
- 不重建 Elasticsearch index。
- 不投递 `q.notification.mail`。
- 不启动 DLQ consumer，不消费 `<queue>.dlq`。
- 不接入 `/api/v1/ops/dlq/**` retry/discard。
- 不接入 `/api/v1/ops/traffic/reset`。
- 不决定 V3 request audit filter 的最终采集架构。
- 不拆分独立数据库。

## 输入

- `services/ircs-ops-service`
- V1 `DashboardController`
- V1 `DashboardService` / `DashboardServiceImpl`
- V1 `MaintenanceController`
- V1 `MaintenanceService` / `MaintenanceServiceImpl`
- V1 `SystemMetricsService` / `SystemMetricsServiceImpl`
- V1 DTO/VO：
  - `DashboardStatsDTO`
  - `DashboardAnalysisDTO`
  - `ChartDataPoint`
  - `EfficiencyStatsDTO`
  - `SystemMetricsVO`
  - `QueueMetricVO`
  - `RedisMetricVO`
  - `SourceQualityVO`
  - `MaintenanceSession`
  - `MaintenanceEvent`
- V1 frontend API：
  - `ircs-frontend/src/features/dashboard/dashboardApi.js`
  - `ircs-frontend/src/features/ops/maintenance/maintenanceApi.js`
- shared queue metadata：
  - `QueueTopic`
  - `<queue>.dlq` naming
- 只读数据和基础设施：
  - `raw_videos`
  - `unified_videos`
  - `collection_tasks`
  - Elasticsearch raw/unified indices
  - RabbitMQ queue properties
  - Valkey/Redis INFO
  - R2/storage health

## 输出

- `ircs-ops-service` maintenance/dashboard MVP 实现。
- V1 dashboard 兼容响应字段。
- maintenance active/session stream 兼容响应。
- dev Deployment/Service。
- 迁移文档、K8S 快照和任务状态回写。
- `docs/migrations/022-ops-service-maintenance-dashboard-extraction.md`

## 影响范围

```text
services/ircs-ops-service
deploy/k8s/dev/ops-service-dev.yaml
docs/migrations
docs/k8s
deploy/k8s/README.md
tasks
```

## 依赖

- 当前 V3 shared PostgreSQL。
- 当前 V3 namespace-local Elasticsearch。
- 当前 V3 namespace-local RabbitMQ。
- 当前 V3 namespace-local Valkey。
- 已接入的 search-service 可作为 dashboard analysis/source-quality 的数据来源或兼容参考。
- 已接入的 storage-service 可作为 R2/storage health 的目标参考。
- admin 鉴权后续需与 identity-service 或 edge auth 方案对齐；MVP smoke 可先使用集群内访问验证业务响应。

## 风险

- dashboard 聚合面较宽，若一次性复制 V1 全部基础设施探测，可能使 smoke 过重。
- RabbitMQ queue metrics 必须读取 `ircs-dev` RabbitMQ，避免误连其他 namespace 或生产环境。
- `GET /api/v1/dashboard/analysis` 和 `source-quality` 依赖 ES 数据，ES 缺数据时需要兼容空响应或明确失败。
- maintenance session 如沿用进程内状态，多副本、重启或 rollout 后会丢 session。
- 若误接 maintenance init 命令，可能批量修改内容表、删除聚合数据或重建 ES index。
- `POST /api/v1/dashboard/refresh` 只能清 dashboard cache，不能写业务数据。
- V1 admin 前端已有 maintenance 按钮，但本 MVP 不承诺这些写命令可用；前端联调时需接受按钮后置或禁用策略。

## 验收标准

文档阶段已完成：

- task spec 明确 dashboard 读面和 maintenance session 兼容面。
- task spec 明确所有 destructive maintenance 命令为非目标。
- task spec 明确 DLQ、traffic、request audit 在本 MVP 中不接入。
- 进入实现阶段前已移动到 `tasks/in-progress/`。

实现阶段：

- `GET /api/v1/dashboard/statistics` 返回 DB/ES 统计字段，缺数据时数值可为 0。
- `GET /api/v1/dashboard/analysis?days=14` 返回 stats、trend、distribution 类字段，缺数据时数组为空而不是 `null`。
- `GET /api/v1/dashboard/metrics` 返回 queueGroups、totalQueueBacklog、redisMetric 和基础系统指标。
- `GET /api/v1/dashboard/source-quality` 返回数组，ES 不可用时有明确降级或错误语义。
- `POST /api/v1/dashboard/refresh?days=14` 不修改业务表。
- `GET /api/v1/ops/maintenance/active` 在无任务时返回 `204 No Content`。
- `GET /api/v1/ops/maintenance/stream/{sessionId}` 对不存在 session 返回过期/不存在事件并结束。
- 服务在 `ircs-dev` Ready 1/1。
- 未新增公网 HTTPRoute。
- smoke 前后业务表、RabbitMQ 队列和 DLQ 消息数无异常新增。

## 测试边界

实现阶段至少验证：

```text
.\gradlew.bat :services:ircs-ops-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\ops-service-dev.yaml
kubectl rollout status deployment/ircs-ops-service -n ircs-dev --timeout=240s
集群内 GET /api/v1/dashboard/statistics smoke
集群内 GET /api/v1/dashboard/analysis?days=14 smoke
集群内 GET /api/v1/dashboard/metrics smoke
集群内 GET /api/v1/dashboard/source-quality smoke
集群内 POST /api/v1/dashboard/refresh?days=14 smoke
集群内 GET /api/v1/ops/maintenance/active smoke
集群内 GET /api/v1/ops/maintenance/stream/{sessionId} 过期 session smoke
确认 smoke 前后业务表、RabbitMQ 队列和 DLQ 无异常新增
```

不适用测试：

- 本轮不测试 maintenance init 命令。
- 本轮不测试 DLQ retry/discard。
- 本轮不测试 traffic reset。
- 本轮不测试 request audit 多服务采集。

## 关联文档

- `docs/reports/023-ops-service-boundary-analysis.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`

## 关联任务

- 父任务：`tasks/in-progress/011-mvp-service-onboarding-parent.md`
- 同批服务：`tasks/done/016-01-scraper-service-manual-scrape-mvp.md`
- 同批已完成服务：`tasks/done/017-01-portal-read-composition-mvp.md`
- 同批已完成服务：`tasks/done/017-02-interaction-feedback-history-mvp.md`

## 当前状态

已完成。代码实现、dev manifest、ARM64 镜像、`ircs-dev` rollout、
ClusterIP smoke、DB/queue/HTTPRoute 后置检查和文档回写均已完成。

## 实现结果

- `services/ircs-ops-service` 已实现 dashboard statistics/analysis/metrics/source-quality。
- `POST /api/v1/dashboard/refresh` 仅重置 metrics throughput window，不修改业务表。
- `GET /api/v1/ops/maintenance/active` 在无任务时返回 `204 No Content`。
- `GET /api/v1/ops/maintenance/stream/{sessionId}` 对不存在 session 返回 `ERROR` 和 `DONE` SSE。
- `deploy/k8s/dev/ops-service-dev.yaml` 已创建 Deployment/Service，ClusterIP only。
- Hikari pool 已压缩为 `maximum-pool-size=2`、`minimum-idle=0`，降低 dev PostgreSQL 连接压力。

## 验证结果 2026-06-04

```text
.\gradlew.bat :services:ircs-ops-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\ops-service-dev.yaml
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-ops-service' -ImageName ircs-ops-service -Tag dev -Architecture arm64 -Os linux
kubectl apply -f deploy\k8s\dev\ops-service-dev.yaml
kubectl rollout status deployment/ircs-ops-service -n ircs-dev --timeout=240s
```

结果：全部通过。

ClusterIP smoke：

```text
GET  /api/v1/dashboard/statistics        -> 200
GET  /api/v1/dashboard/analysis?days=14  -> 200
GET  /api/v1/dashboard/metrics           -> 200, totalQueueBacklog=0, redisMetric present
GET  /api/v1/dashboard/source-quality    -> 200
POST /api/v1/dashboard/refresh?days=14   -> 204
GET  /api/v1/ops/maintenance/active      -> 204
GET  /api/v1/ops/maintenance/stream/{id} -> ERROR + DONE
```

部署结果：

```text
image:  harbor.mnnu.eu.org/ircs/ircs-ops-service:dev
digest: sha256:c7f873757ed678fbe909de020eed4762b25e852d96defa5aa28e8d27f769589f
svc:    ircs-ops-service, ClusterIP 10.102.243.27
ready:  deployment/ircs-ops-service 1/1
quota:  pods=23/24, services=19/20
```

清理/只读验证：

```text
raw_videos, unified_videos, collection_tasks, search_sync_tasks: 0,0,0,0
RabbitMQ queues and DLQs: all messages=0
HTTPRoute in ircs-dev: No resources found
```

## 修复记录

- 修复 `/api/v1/dashboard/analysis` 中 `Instant` SQL 参数类型推断失败，改为
  `java.sql.Timestamp`。
- 修复 `raw_category` schema drift，使用 `source_name/source_code` 替代旧 `name` 字段。
- analysis 可选分布查询增加空数组降级，避免非核心分布失败导致整个 dashboard 500。
