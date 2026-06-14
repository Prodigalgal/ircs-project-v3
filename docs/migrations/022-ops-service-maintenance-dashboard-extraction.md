# 022 ops-service maintenance/dashboard MVP 抽离记录

状态：已完成

更新时间：2026-06-04

## 背景

`ircs-ops-service` 是 V3 MVP 服务接入的最后一个业务服务。V1 中 dashboard 与
maintenance 位于 `modules/system`，其中 dashboard 是只读聚合面，maintenance 同时包含
active session/SSE 和多个破坏性全量写命令。

本次只接入低风险 MVP：dashboard 读接口、dashboard cache refresh 兼容入口，以及
maintenance active/session stream 兼容面。所有重置、重建、DLQ retry/discard、traffic
reset 和 request audit 采集都后置。

## 目标边界

```text
GET  /api/v1/dashboard/statistics
GET  /api/v1/dashboard/analysis?days=14
GET  /api/v1/dashboard/metrics
GET  /api/v1/dashboard/source-quality
POST /api/v1/dashboard/refresh?days=14

GET  /api/v1/ops/maintenance/active
GET  /api/v1/ops/maintenance/stream/{sessionId}
```

## 非目标

- 不新增 HTTPRoute。
- 不接入 maintenance init 写命令。
- 不重置内容、聚合、搜索或队列表。
- 不重建 Elasticsearch index。
- 不消费或重投递 DLQ。
- 不实现 traffic reset。
- 不决定 request audit 的多服务采集架构。

## 实施计划

- 使用 `JdbcTemplate` 读取 PostgreSQL 统计与分布数据。
- 使用 `RabbitAdmin` 读取 namespace-local RabbitMQ queue/DLQ message 和 consumer count。
- 使用 Valkey/Redis INFO 读取轻量运行指标；失败时返回 `null` 或 0，而不是影响 dashboard。
- 使用进程内空 maintenance session registry 保留 V1 active/stream shape。
- 使用 ClusterIP-only dev manifest，资源保持低 profile。

## 验证计划

```text
.\gradlew.bat :services:ircs-ops-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\ops-service-dev.yaml
ARM64 Jib push
kubectl apply -f deploy\k8s\dev\ops-service-dev.yaml
kubectl rollout status deployment/ircs-ops-service -n ircs-dev --timeout=240s
ClusterIP smoke for dashboard and maintenance read APIs
RabbitMQ queue/DLQ messages remain 0 after smoke
No HTTPRoute in ircs-dev
```

## 实现结果

- 新增 `services/ircs-ops-service` maintenance/dashboard MVP 运行时实现。
- 新增 `deploy/k8s/dev/ops-service-dev.yaml`，Deployment/Service 均为 `ircs-dev`
  ClusterIP-only 配置，无 HTTPRoute。
- dashboard 统计读取 PostgreSQL 迁移桥数据，ES 计数字段在 MVP 中以 0 降级。
- analysis 分布查询按当前 PostgreSQL schema 聚合，单个可选分布查询失败时降级为空数组。
- metrics 读取 namespace-local RabbitMQ queue/DLQ message 和 consumer count，并读取 Valkey INFO。
- maintenance active 在无任务时返回 `204 No Content`；过期 session stream 返回 `ERROR` 和 `DONE`。
- 为降低 dev PostgreSQL 连接压力，Hikari pool 设置为 `maximum-pool-size=2`、`minimum-idle=0`。

## 镜像与部署

```text
image:  harbor.mnnu.eu.org/ircs/ircs-ops-service:dev
digest: sha256:c7f873757ed678fbe909de020eed4762b25e852d96defa5aa28e8d27f769589f
svc:    ircs-ops-service, ClusterIP 10.102.243.27
ready:  deployment/ircs-ops-service 1/1
```

## 验证结果

```text
.\gradlew.bat :services:ircs-ops-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\ops-service-dev.yaml
ARM64 Jib push
kubectl apply -f deploy\k8s\dev\ops-service-dev.yaml
kubectl rollout status deployment/ircs-ops-service -n ircs-dev --timeout=240s
```

ClusterIP smoke：

```text
GET  /api/v1/dashboard/statistics        -> 200, rawCountDb 字段存在
GET  /api/v1/dashboard/analysis?days=14  -> 200, weeklyTrend/idCoverage 字段存在
GET  /api/v1/dashboard/metrics           -> 200, totalQueueBacklog=0, redisMetric present
GET  /api/v1/dashboard/source-quality    -> 200
POST /api/v1/dashboard/refresh?days=14   -> 204
GET  /api/v1/ops/maintenance/active      -> 204
GET  /api/v1/ops/maintenance/stream/{id} -> ERROR + DONE
```

Smoke 后检查：

```text
raw_videos, unified_videos, collection_tasks, search_sync_tasks: 0,0,0,0
RabbitMQ queues and DLQs: all messages=0
HTTPRoute in ircs-dev: No resources found
quota: pods=23/24, services=19/20, requests.cpu=795m/4, limits.cpu=6700m/8
```

## 修复记录

- 第一次 `/api/v1/dashboard/analysis` smoke 返回 500，原因是 PostgreSQL JDBC 无法推断
  `Instant` 参数类型；已改为 `java.sql.Timestamp`。
- 第二次 `/api/v1/dashboard/analysis` smoke 返回 500，原因是当前 `raw_category`
  schema 使用 `source_name/source_code`，不是旧 `name` 字段；已改为当前 schema。
- psql 预检曾遇到 `too many clients`，因此 ops-service 显式压缩 Hikari pool，避免最后一个
  服务继续扩大 dev PostgreSQL 连接压力。

## 后续增强

- request audit 多服务采集架构。
- DLQ read/manage、retry/discard 独立安全切片。
- traffic monitor 只读和 reset 分离切片。
- destructive maintenance 通过内部 API、受控 Job 或事件机制单独接入。
