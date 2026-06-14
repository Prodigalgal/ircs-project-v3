# 011 MVP 服务整体接入加速需求

状态：进行中

更新时间：2026-06-04

## 背景

用户要求在已经隔离的 `ircs-dev` namespace 中加快 V1 到 V3 的 MVP 服务整体接入，同时避免与现有生产流量、配置和资源配额冲突。当前 VPS/K8S 节点为 ARM64，本机为 x86，因此服务镜像必须按 `linux/arm64` 推送到 dev 使用的 Harbor tag。

## 目标

- 按 `settings.gradle` 中的 18 个目标服务核算接入状态。
- 优先接入可独立验证、资源占用低、不会引入公网流量入口的服务切片。
- 每个服务切片至少具备本地测试、server dry-run、ARM64 镜像推送、K8S rollout 和集群内 smoke。
- `ircs-dev` 继续保持无公共 HTTPRoute，避免流量冲突。

## 当前接入状态

已接入并在 `ircs-dev` Ready 的业务服务为 18/18：

```text
ircs-notification-worker
ircs-search-service
ircs-storage-service
ircs-content-service
ircs-metadata-worker
ircs-credential-service
ircs-catalog-service
ircs-config-service
ircs-task-service
ircs-ingestion-worker
ircs-normalization-worker
ircs-aggregation-worker
ircs-magnet-service
ircs-identity-service
ircs-scraper-service
ircs-portal-service
ircs-interaction-service
ircs-ops-service
```

尚未部署接入的 MVP 服务为 0/18：

```text
暂无
```

## 范围

本需求包含：

- 接入矩阵维护。
- 轻量 dev manifest。
- 业务服务 ARM64 镜像推送。
- K8S rollout 和集群内 smoke。
- 迁移文档、K8S 快照和任务状态回写。

本需求不包含：

- 生产 namespace 切换。
- 公网 HTTPRoute 暴露。
- 独立数据库拆分。
- 增加高资源中间件副本。
- 引入真实外部 provider 流量，除非后续单独确认。

## 风险

- `ircs-dev` ResourceQuota 仍然有限，需要保持每个 Java 服务 `25m/128Mi` request。
- 当前 PostgreSQL/RabbitMQ/Elasticsearch/Valkey 为 dev 共享中间件，服务边界先通过代码和内部 API 固化，物理 DB 拆分后置。
- `production-gateway` 允许跨 namespace HTTPRoute 时，任何 dev HTTPRoute 都可能造成公网流量风险，因此当前阶段继续禁止 dev HTTPRoute。

## 验收标准

- 每接入一个服务后，业务 Deployment Ready 1/1。
- 新增服务仅 ClusterIP，不新增 HTTPRoute。
- 资源配额增量可解释且不超过 `ircs-dev` hard limit。
- Smoke 使用临时数据时必须清理，清理后 DB/ES/queue 不留测试残留。
- 文档和任务文件同步记录实现结果、验证结果、遗留问题和下一步。

## 最新接入结果 2026-06-03

`ircs-normalization-worker` 已完成 `q.normalize.video` MVP 接入，在
`ircs-dev` Ready 1/1。该服务无 HTTPRoute，使用 `25m/128Mi` request 和
`500m/512Mi` limit。Smoke 已验证 raw video 从 `PENDING` 推进到 `READY`，
并发布 `q.search.sync.raw` 触发 ES 索引；临时 PostgreSQL、Elasticsearch 和
RabbitMQ 数据均已清理。

## 最新接入结果 2026-06-04

`ircs-aggregation-worker` 已完成 scheduler MVP 接入，在 `ircs-dev`
Ready 1/1。该服务无 HTTPRoute，使用 `25m/128Mi` request 和
`250m/512Mi` limit。Smoke 已验证临时 raw video 从 `PENDING` 推进到
`BOUND`，创建 unified video 和 raw/unified 绑定，并发布
`q.search.sync.raw`、`q.search.sync.unified` 触发 ES 索引；临时 PostgreSQL、
Elasticsearch 和 RabbitMQ 数据均已清理。

当前 `ircs-dev` CPU limit 已达到 `8/8`，后续继续接入服务前必须先压低已有
服务 limit 或调整下一服务的资源 profile。

## 当前进行中草案 2026-06-04

并行 subagent 已完成 `ircs-identity-service` 和 `ircs-magnet-service` 的本地代码与
dev manifest 草案，且主 agent 已复跑各自本地测试和 server dry-run，均通过。但
`ircs-magnet-service` 已在随后完成 ARM64 镜像、`ircs-dev` rollout 和集群内
smoke，因此已计入 Ready 服务数。`ircs-identity-service` 随后也完成 ARM64 镜像推送和
`ircs-dev` rollout，但 `PUT /api/portal/profile` smoke 尚未闭环，继续保留在进行中。

## 最新接入结果 2026-06-04 magnet-service

`ircs-magnet-service` 已完成 provider/cache 只读 MVP 接入，在 `ircs-dev`
Ready 1/1。该服务无 HTTPRoute，使用 `25m/128Mi` request 和 `250m/512Mi`
limit。Smoke 已验证 provider list/detail、unified approved magnet links 查询、
REJECTED link 过滤、job/run 不写入，以及临时 PostgreSQL 数据清理。

## 最新接入结果 2026-06-04 identity-service

`ircs-identity-service` 已部署到 `ircs-dev` 并 Ready 1/1，未新增 HTTPRoute，使用
`25m/128Mi` request 和 `250m/512Mi` limit。auth/profile MVP smoke 已覆盖
PoW、register、login、profile get、profile update、check-in、password change、
forgot-password mail disabled、reset-password temp Valkey token、relogin、
BANNED 登录拒绝和临时数据清理。

随后已通过 worker-only Service 裁剪将 `services` quota 从 `20/20` 降到 `15/20`。
继续部署 scraper、portal、interaction 或 ops 时，仍必须保持无公网 HTTPRoute，并使用
低资源 profile。

## 最新接入结果 2026-06-04 scraper-service

`ircs-scraper-service` 已完成 manual scrape MVP 接入，在 `ircs-dev` Ready 1/1，
未新增 HTTPRoute，使用 `25m/128Mi` request 和 `250m/512Mi` limit。Smoke 已验证
`POST /api/v1/scraper/manual/init`、`GET /api/v1/scraper/manual/stream/{sessionId}`、
SSE `CARD(PUBLISHED)`、`q.ingest.video` 投递、ingestion-worker 落库、playlist/episode
同步和临时数据清理。

scraper 接入后当时 `ircs-dev` Pod quota 已满，Service quota 使用 16/20。继续部署
portal、interaction 或 ops 前，需要先处理 Pod quota；CPU、memory 和 Service quota
仍有余量。该 Pod quota 阻塞已在 `024-01` 中处理。

## 最新接入结果 2026-06-04 portal/interaction

已通过 `024-01` 将 dev Pod hard limit 从 `20` 小幅调整到 `24`，CPU、memory 和
Service hard limit 保持不变。随后 `ircs-portal-service` 与
`ircs-interaction-service` 均完成 ARM64 镜像推送、`ircs-dev` rollout 和 ClusterIP
smoke。

`ircs-portal-service` 已验证：

```text
GET /api/portal/metadata -> categories, genres, areas, languages, years
GET /api/portal/home     -> spotlight, trending, sections
```

`ircs-interaction-service` 已验证：

```text
GET /api/portal/feedback/wall -> public message, no memberEmail leak
GET /api/portal/interaction/history with ROLE_MEMBER JWT   -> 200
GET /api/portal/interaction/favorites with ROLE_MEMBER JWT -> 200
```

临时 member/message 已清理，RabbitMQ 队列和 DLQ 均为 0。portal/interaction 接入后当时
剩余未接入服务为 `ircs-ops-service`。

## 最新接入结果 2026-06-04 ops-service

`ircs-ops-service` 已完成 maintenance/dashboard MVP 接入，在 `ircs-dev` Ready 1/1，
未新增 HTTPRoute，使用 `25m/128Mi` request 和 `250m/512Mi` limit。该服务保留
dashboard 只读聚合接口、dashboard refresh 兼容入口、maintenance active 查询和过期
session SSE 兼容响应，不接入任何 destructive maintenance init、DLQ retry/discard、
traffic reset 或 request audit 采集。

ClusterIP smoke 已验证：

```text
GET  /api/v1/dashboard/statistics        -> 200
GET  /api/v1/dashboard/analysis?days=14  -> 200
GET  /api/v1/dashboard/metrics           -> 200, totalQueueBacklog=0
GET  /api/v1/dashboard/source-quality    -> 200
POST /api/v1/dashboard/refresh?days=14   -> 204
GET  /api/v1/ops/maintenance/active      -> 204
GET  /api/v1/ops/maintenance/stream/{id} -> ERROR + DONE
```

Smoke 后业务表计数未变化，RabbitMQ 队列和 DLQ 均为 0，`ircs-dev` 仍无 HTTPRoute。
portal/admin 前端入口和正式 BFF 拆分完成后，当前 `ircs-dev` quota 为
`pods=28/30`、`services=24/26`。至此 18 个目标业务服务均已完成 MVP 接入。
