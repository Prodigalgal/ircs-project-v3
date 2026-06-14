# 027 content-service raw/unified admin API 抽取记录

状态：已完成

更新时间：2026-06-06

## 范围

本次在已部署的 `ircs-content-service` 上无损迁移 V1 `RawVideoController` 和
`UnifiedVideoController` 的 21 条后台内容管理接口：

```text
GET    /api/v1/raw-videos
GET    /api/v1/raw-videos/{id}
POST   /api/v1/raw-videos
PUT    /api/v1/raw-videos/{id}
DELETE /api/v1/raw-videos/{id}
POST   /api/v1/raw-videos/batch/delete
POST   /api/v1/raw-videos/batch/sync-search
POST   /api/v1/raw-videos/{id}/renormalize
POST   /api/v1/raw-videos/{id}/reenrich
POST   /api/v1/raw-videos/{id}/refetch
POST   /api/v1/raw-videos/batch/renormalize
POST   /api/v1/raw-videos/batch/reenrich
POST   /api/v1/raw-videos/batch/refetch

GET    /api/v1/unified-videos
GET    /api/v1/unified-videos/{id}
POST   /api/v1/unified-videos
PUT    /api/v1/unified-videos/{id}
DELETE /api/v1/unified-videos/{id}
POST   /api/v1/unified-videos/batch/delete
POST   /api/v1/unified-videos/batch/sync-search
POST   /api/v1/unified-videos/{id}/recalculate
```

完成后按 `025` 路由覆盖基线估算：

```text
V3 service Controller routes: 72 -> 93
Direct missing routes:         142 -> 121
Raw/Unified admin API gap:     closed
```

## 实现

- 在 `ircs-content-service` 新增 raw/unified admin controller、DTO、service 和 JDBC
  repository。
- raw/unified list/detail/create/update/delete/batch delete 走 content-service JDBC。
- raw/unified search sync 发布 `SEARCH_SYNC_RAW` / `SEARCH_SYNC_UNIFIED` 结构化消息。
- raw renormalize 更新 `normalization_status=PENDING` 并发布 `NORMALIZE_VIDEO`。
- raw reenrich 发布 `ENRICH_METADATA`。
- raw refetch 由 content-service 调用 scraper-service internal refetch contract。
- unified recalculate 当前在 content-service 内基于已绑定 raw source 执行重算，并发布
  raw/unified search sync；aggregation-worker internal recalculation code 作为后续
  owner contract 保留，不作为当前 dev runtime 路径。
- dev datasource 服务增加 Hikari 小池配置，避免 18 个服务同时接入后把 PostgreSQL
  连接数打满：

```text
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0
SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT=30000
```

## 明确非目标

- 不新增 K8S Service。
- 不新增公共 HTTPRoute。
- 不迁移 `.214` 开发资源。
- 不做物理数据库拆分。
- 不重写后台 UI。
- 不把无法承接的副作用伪装成成功；本切片所有目标副作用均有真实承接路径。

## 验证

本地：

```text
.\gradlew.bat :services:ircs-content-service:test :services:ircs-scraper-service:test :services:ircs-aggregation-worker:test -> BUILD SUCCESSFUL
.\gradlew.bat build -> BUILD SUCCESSFUL
kubectl apply --dry-run=client -f deploy/k8s/dev/*.yaml -> all dev yaml dry-run ok
```

镜像与 rollout：

```text
content-service image:  harbor.mnnu.eu.org/ircs/ircs-content-service:dev
content-service digest: sha256:e70bc1e9ca03b80f103775a2eb50ac8978af2af9b1fadb5f79e3cf06f839fb9a
kubectl -n ircs-dev rollout status deploy/ircs-content-service -> successfully rolled out
```

ClusterIP smoke：

```text
MARK 21/21 raw/unified admin API -> passed
raw create/list/detail/update/delete/batch/sync/renormalize/reenrich/refetch -> passed
unified create/list/detail/update/delete/batch/sync/recalculate -> passed
pre-cleanup smoke anchors: 1|1|1|1|1
post-cleanup smoke anchors: 0|0|0|0|0
PostgreSQL pg_stat_activity after Hikari compression: 1 active / 7 idle / 8 internal-null
ResourceQuota: pods=23/24, services=19/20
ircs-dev HTTPRoute: none
```

## 遗留

- V1 raw/unified admin API 的 21 条 HTTP 接口已完成迁移与接口级复现标记。
- 真实生产切流、物理数据库拆分和后台 UI 联调不属于本切片。
- 后续继续按 `025` 父任务关闭 storage、ops、interaction admin message 和 task-runner
  等剩余 V1 缺口。
