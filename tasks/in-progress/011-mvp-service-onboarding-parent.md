# 011 MVP 服务整体接入父任务

状态：进行中

上游来源：

- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`

## 背景

V3 目标拆分包含 18 个服务。当前开发环境已隔离在 `ircs-dev` namespace，并已部署低资源 PostgreSQL、RabbitMQ、Elasticsearch、Valkey 与 Telepresence traffic-manager。用户要求加快 MVP 服务整体接入。

## 目标

- 将目标服务按低风险切片逐个接入到 `ircs-dev`。
- 每个服务独立完成代码、manifest、镜像、rollout 和 smoke。
- 保持无公共 HTTPRoute，避免与 `ircs-system` 生产流量冲突。
- 控制资源 request，优先使用已有中间件。

## 非目标

- 不在本父任务内做生产切换。
- 不在本父任务内做物理数据库拆分。
- 不创建额外中间件实例，除非后续单独确认。

## 当前进度

已部署并 Ready：18/18。

已完成迁移验收：18/18。

已部署但验收未闭环：0/18。

未部署：0/18。

已完成子任务：

```text
tasks/done/010-02-credential-internal-token-hardening.md
tasks/done/011-01-catalog-service-readonly-mvp.md
tasks/done/012-01-config-service-readonly-mvp.md
tasks/done/012-02-dev-resource-compression-for-mvp-onboarding.md
tasks/done/013-01-task-service-readonly-mvp.md
tasks/done/014-01-documentation-organization.md
tasks/done/015-01-identity-service-auth-profile-mvp.md
tasks/done/016-01-scraper-service-manual-scrape-mvp.md
tasks/done/016-02-ingestion-worker-video-ingest-mvp.md
tasks/done/017-01-portal-read-composition-mvp.md
tasks/done/017-02-interaction-feedback-history-mvp.md
tasks/done/017-03-magnet-provider-cache-mvp.md
tasks/done/018-01-normalization-worker-normalize-mvp.md
tasks/done/019-01-aggregation-worker-scheduler-mvp.md
tasks/done/020-01-dev-java-limit-compression-for-next-rollouts.md
tasks/done/021-01-documentation-state-sync-after-identity-rollout.md
tasks/done/022-01-dev-worker-service-pruning-for-service-quota.md
tasks/done/023-01-ops-service-maintenance-dashboard-mvp.md
tasks/done/024-01-dev-pod-quota-adjustment-for-final-rollouts.md
```

进行中子任务：

```text
暂无服务子任务
```

Pod quota 已通过 `024-01` 从 `20` 小幅调整到 `24`。portal/admin 前端入口和
正式 BFF 拆分完成后，当前 `pods=28/30`、`services=24/26`，仍保留 2 个缓冲 Pod
和 2 个缓冲 Service。

待切分服务子任务：

```text
暂无。18 个目标业务服务均已完成 MVP rollout。
```

待办 backlog 子任务：

```text
tasks/backlog/014-02-legacy-doc-language-normalization.md
```

进行中服务子任务：

```text
暂无
```

## 最新回写 2026-06-03

`ircs-normalization-worker` 已完成 `q.normalize.video` MVP 接入，并在
`ircs-dev` Ready 1/1。该 worker 消费 namespace-local RabbitMQ，更新
namespace-local PostgreSQL `raw_videos`，成功后发布 `q.search.sync.raw` 由
`ircs-search-service` 写入 Elasticsearch。Smoke 清理后 PostgreSQL、ES 和相关
队列无临时残留。

magnet-service 接入后当时 quota 使用：

```text
pods:            16 / 20
services:        17 / 20
requests.cpu:    625m / 4
requests.memory: 3Gi / 8Gi
limits.cpu:      7750m / 8
limits.memory:   9Gi / 16Gi
```

后续继续接入服务前，需要关注 `limits.cpu` 剩余只有 `250m`。

## 最新回写 2026-06-04

`ircs-aggregation-worker` 已完成 scheduler MVP 接入，并在 `ircs-dev`
Ready 1/1。该 worker 不消费 RabbitMQ aggregation queue，而是按 V1 真实形态
扫描 namespace-local PostgreSQL `raw_videos`，将满足条件的临时 raw video 从
`aggregation_status=PENDING` 推进到 `BOUND`，创建 `unified_videos` 和
`raw_video_unified_video` 绑定，并发布 `q.search.sync.raw` 与
`q.search.sync.unified` 由 `ircs-search-service` 写入 ES。Smoke 清理后
PostgreSQL、Elasticsearch 和 RabbitMQ 队列无临时残留。

当时 quota 使用：

```text
pods:            17 / 20
services:        18 / 20
requests.cpu:    650m / 4
requests.memory: 3200Mi / 8Gi
limits.cpu:      8 / 8
limits.memory:   9728Mi / 16Gi
```

后续继续接入服务前，必须先处理 `limits.cpu=8/8` 的硬限制。

并行 subagent 已返回 `identity-service` 和 `magnet-service` 的本地代码草案。当时两者
均未推 ARM64 镜像、未部署、未完成集群 smoke；随后 `magnet-service` 已完成主
agent 复核、镜像推送、rollout 和 smoke。`identity-service` 也已完成 ARM64 镜像推送
和 rollout；随后已补齐 `PUT /api/portal/profile`、reset-password、BANNED 登录拒绝和
残留清理 smoke，并移动到 `tasks/done/`。

全仓验证：

```text
.\gradlew.bat build
```

结果：BUILD SUCCESSFUL。

## 最新回写 2026-06-04 资源压缩

为继续部署 identity/magnet，已将 dev Java 服务 CPU limit 压缩到 `250m`。当时已部署的
11 个服务完成 `kubectl apply` 和 rollout，全部 Ready 1/1；identity/magnet 先完成
manifest 更新和 server dry-run，随后已分别进入 rollout 阶段。

当时 quota 使用：

```text
pods:            17 / 20
services:        18 / 20
requests.cpu:    650m / 4
requests.memory: 3200Mi / 8Gi
limits.cpu:      5250m / 8
limits.memory:   9728Mi / 16Gi
```

`ircs-dev` 仍无 HTTPRoute，RabbitMQ 队列和 DLQ 均为 0 消息。后续风险转移到
`services` quota：identity 和 magnet 都部署后会达到 `20/20`。

## 最新回写 2026-06-04 magnet-service

`ircs-magnet-service` 已完成 provider/cache 只读 MVP 接入，并在 `ircs-dev`
Ready 1/1。该服务无 HTTPRoute，只暴露 ClusterIP，读取 `magnet_providers` 和
`magnet_links` 中 `APPROVED` links，不触发外部 provider 搜索，不写
`magnet_search_jobs` 或 `magnet_provider_runs`。

Smoke 清理后临时 `magnet_links`、`magnet_providers`、`unified_videos` 均为 0，
RabbitMQ 队列和 DLQ 均为 0 消息。

当时 quota 使用：

```text
pods:            18 / 20
services:        19 / 20
requests.cpu:    675m / 4
requests.memory: 3328Mi / 8Gi
limits.cpu:      5500m / 8
limits.memory:   10Gi / 16Gi
```

magnet-service 接入后当时最紧限制为 `services=19/20`；随后部署 identity-service 后已达到
`20/20`。

## 最新回写 2026-06-04 identity-service

`ircs-identity-service` 已完成 ARM64 镜像推送和 `ircs-dev` rollout，当前
Deployment Ready 1/1，Service 为 ClusterIP，未创建 HTTPRoute。主要
auth/profile smoke 已覆盖 PoW、register、login、profile get、profile update、
check-in、password change、forgot-password mail disabled、reset-password temp
Valkey token、relogin、BANNED 登录拒绝和临时数据清理。

identity-service 已计入“已部署并 Ready”和“已完成迁移验收”。邮件验证开启后的
`activate`/`resend-code` 单独后置到 email verification slice。

当时 quota 使用：

```text
pods:            19 / 20
services:        20 / 20
requests.cpu:    700m / 4
requests.memory: 3456Mi / 8Gi
limits.cpu:      5750m / 8
limits.memory:   10752Mi / 16Gi
```

identity-service 接入后当时最紧限制为 `services=20/20`。随后已通过 worker-only
Service 裁剪释放配额。

## 最新回写 2026-06-04 worker-only Service 裁剪

为释放剩余服务接入空间，已删除 5 个不需要入站 HTTP 的 worker-only Service，并从
对应 dev manifest 中移除 Service 文档：

```text
ircs-notification-worker
ircs-metadata-worker
ircs-ingestion-worker
ircs-normalization-worker
ircs-aggregation-worker
```

对应 Deployment 仍全部 Ready 1/1，RabbitMQ 队列和 DLQ 消息数均为 0，`ircs-dev`
仍无 HTTPRoute。

当时 quota 使用：

```text
pods:            19 / 20
services:        15 / 20
requests.cpu:    700m / 4
requests.memory: 3456Mi / 8Gi
limits.cpu:      5750m / 8
limits.memory:   10752Mi / 16Gi
```

当时剩余 Service quota 为 5，足够继续接入 scraper、portal、interaction、ops 四个
剩余 MVP 服务，并保留 1 个缓冲位；scraper 随后已完成接入，最新 quota 见下一节。

## 最新回写 2026-06-04 scraper-service

`ircs-scraper-service` 已完成 manual scrape MVP 接入，并在 `ircs-dev` Ready 1/1。
该服务无 HTTPRoute，只暴露 ClusterIP，保留
`POST /api/v1/scraper/manual/init` 和
`GET /api/v1/scraper/manual/stream/{sessionId}`，读取 `data_sources`，并将抓取或
direct smoke item 映射为 `IngestionItem` 发布到 `q.ingest.video`。

ClusterIP smoke 已验证 SSE `LOG -> CARD(PUBLISHED) -> DONE`，并通过
`ircs-ingestion-worker` 写入临时 `raw_videos`、1 个 playlist 和 1 个 episode。
临时 PostgreSQL 数据已清理，RabbitMQ 队列和 DLQ 最终均为 0。

当前 quota 使用：

```text
pods:            20 / 20
services:        16 / 20
requests.cpu:    725m / 4
requests.memory: 3584Mi / 8Gi
limits.cpu:      6 / 8
limits.memory:   11Gi / 16Gi
```

后续部署 portal、interaction、ops 前，需要先执行 Pod quota 前置调整；不需要提升
CPU 或 memory hard limit。

## 最新回写 2026-06-04 dev Pod quota 前置调整

已通过 `deploy/k8s/dev/resource-quota-dev.yaml` 将 `ircs-dev-quota` 的 Pod hard limit
从 `20` 小幅调整到 `24`，其余 hard limit 保持不变。server dry-run 与 live apply
均通过。该调整只放宽可创建 Pod 数量，不会自动增加 CPU 或 memory request。

调整后继续保持 Java 服务低资源 profile：

```text
request.cpu:    25m
request.memory: 128Mi
limit.cpu:      250m
limit.memory:   512Mi
```

## 最新回写 2026-06-04 portal-service

`ircs-portal-service` 已完成 read composition MVP 接入，并在 `ircs-dev` Ready 1/1。
该服务无 HTTPRoute，只暴露 ClusterIP，读取 catalog/content/search/magnet 相关迁移桥
数据并聚合 portal 首页和元数据读模型。

ClusterIP smoke 已验证：

```text
GET /api/portal/metadata -> categories, genres, areas, languages, years
GET /api/portal/home     -> spotlight, trending, sections
```

ARM64 镜像：

```text
harbor.mnnu.eu.org/ircs/ircs-portal-service:dev
sha256:a2ca28ce3f71ad223257640c2c9396bb591c7ab022407717dc94ca60aa52a7cb
```

## 最新回写 2026-06-04 interaction-service

`ircs-interaction-service` 已完成 feedback/history MVP 接入，并在 `ircs-dev` Ready 1/1。
该服务无 HTTPRoute，只暴露 ClusterIP，覆盖 public feedback wall、member watch history
和 favorites read API。`q.interaction.watch_progress` 消费者在 MVP 中后置。

ClusterIP smoke 已验证：

```text
GET /api/portal/feedback/wall -> public message, no memberEmail leak
GET /api/portal/interaction/history with ROLE_MEMBER JWT   -> 200
GET /api/portal/interaction/favorites with ROLE_MEMBER JWT -> 200
```

临时 member/message 已清理，RabbitMQ 队列和 DLQ 均为 0。

ARM64 镜像：

```text
harbor.mnnu.eu.org/ircs/ircs-interaction-service:dev
sha256:540c97ad07c292e866a0f30523ff5b20c5f6bf5a6ae702db353c85f8fd019bd4
```

portal/interaction 接入后当前 quota 使用：

```text
pods:            22 / 24
services:        18 / 20
requests.cpu:    770m / 4
requests.memory: 3840Mi / 8Gi
limits.cpu:      6450m / 8
limits.memory:   12160Mi / 16Gi
```

portal/interaction 接入后剩余未接入服务为 `ircs-ops-service`；随后 ops-service 已完成
接入，见下一节。

## 最新回写 2026-06-04 ops-service

`ircs-ops-service` 已完成 maintenance/dashboard MVP 接入，并在 `ircs-dev` Ready 1/1。
该服务无 HTTPRoute，只暴露 ClusterIP，保留 dashboard 只读聚合接口、dashboard refresh
兼容入口，以及 maintenance active/expired stream 兼容入口。所有 destructive
maintenance init、DLQ retry/discard、traffic reset 和 request audit 采集均后置。

ClusterIP smoke 已验证：

```text
GET  /api/v1/dashboard/statistics        -> 200, rawCountDb 等 V1 兼容字段
GET  /api/v1/dashboard/analysis?days=14  -> 200, weeklyTrend/idCoverage 等字段
GET  /api/v1/dashboard/metrics           -> 200, totalQueueBacklog=0, redisMetric present
GET  /api/v1/dashboard/source-quality    -> 200
POST /api/v1/dashboard/refresh?days=14   -> 204
GET  /api/v1/ops/maintenance/active      -> 204
GET  /api/v1/ops/maintenance/stream/{id} -> ERROR + DONE
```

为降低 dev PostgreSQL 连接压力，ops-service Hikari pool 已限制为
`maximum-pool-size=2`、`minimum-idle=0`。Smoke 后 `raw_videos`、`unified_videos`、
`collection_tasks`、`search_sync_tasks` 计数仍为 0，RabbitMQ 所有队列和 DLQ 消息数均为
0，`ircs-dev` 仍无 HTTPRoute。

ARM64 镜像：

```text
harbor.mnnu.eu.org/ircs/ircs-ops-service:dev
sha256:c7f873757ed678fbe909de020eed4762b25e852d96defa5aa28e8d27f769589f
```

ops-service 接入后当时 quota 使用：

```text
pods:            23 / 24
services:        19 / 20
requests.cpu:    795m / 4
requests.memory: 3968Mi / 8Gi
limits.cpu:      6700m / 8
limits.memory:   12672Mi / 16Gi
```

至此 `settings.gradle` 中 18 个目标业务服务均已完成 MVP 接入和 `ircs-dev` rollout。

## 最新回写 2026-06-06 ops-service observability read API

post-MVP `025-07` 已在同一个 `ircs-ops-service` Deployment 上完成 request audit、
DLQ 和 traffic status 查询面迁移，不新增 Pod、Service 或 HTTPRoute。当前
`ircs-ops-service:dev` 镜像 digest 已更新为：

```text
harbor.mnnu.eu.org/ircs/ircs-ops-service:dev
sha256:c78353aee97fb3013b129bea99d94c1cc6558cbfaf7c4b7555b1ef6cc26aa7ac
```

ClusterIP smoke 已完成接口级复现：

```text
MARK 1/4 GET /api/v1/ops/request-audit -> HTTP 200
MARK 2/4 GET /api/v1/ops/request-audit/summary -> HTTP 200
MARK 3/4 GET /api/v1/ops/dlq -> HTTP 200
MARK 4/4 GET /api/v1/ops/traffic/status -> HTTP 200
```

portal/admin 前端入口和正式 BFF 拆分完成后，当前 quota 为 `pods=28/30`、
`services=24/26`。

## 影响范围

- `services/*`
- `deploy/k8s/dev/*`
- `docs/architecture/*`
- `docs/migrations/*`
- `docs/k8s/*`
- `tasks/*`

## 测试边界

每个子任务至少记录：

- Gradle test 或 build。
- `kubectl apply --dry-run=server`。
- ARM64 Jib image push。
- `kubectl rollout status`。
- 集群内 API/queue smoke。
- 临时数据清理结果。

## 风险

- dev namespace 的 Pod、Service、CPU、memory quota 有硬限制。
- 部分服务仍依赖共享 PostgreSQL 表，需要在切片内明确只读、写入或迁移桥边界。
- worker 切片必须避免重复消费生产队列，只连接 `ircs-dev` RabbitMQ。

## 验收标准

- 父任务完成时，18 个目标服务均至少完成 MVP 接入或有明确延期决策。
- 所有完成服务在 `ircs-dev` Ready。
- 所有未完成项留在 `tasks/backlog/` 或 `tasks/in-progress/`，不得只留在总结中。
