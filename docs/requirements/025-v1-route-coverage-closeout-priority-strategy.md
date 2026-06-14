# 025 V1 route coverage closeout - priority strategy

来源：`025-v1-route-coverage-closeout.md` 拆分。

## 优先级策略

第一批优先选择：

- 前端可能直接调用的 portal/search/interaction/content 读写缺口。
- 不需要新增中间件、不新增 HTTPRoute、不触发破坏性批量操作的缺口。
- 能在本地测试和 ClusterIP smoke 中独立证明的缺口。

第一刀已完成 `ircs-search-service` 的 portal search query API：

```text
GET /api/portal/search/suggest
GET /api/portal/search/recommendations/{videoId}
```

原因：

- V1 缺口只有 2 个路由，边界清晰。
- 当前 `ircs-search-service` 已拥有 ES 同步、索引 mapping 和 unified video document。
- 该切片只读 ES，不写 PostgreSQL、RabbitMQ 或业务表。
- 已通过 mock 单元测试、全仓 build、ARM64 rollout 和 ClusterIP 只读 smoke 独立验证。

第二刀已完成 `ircs-interaction-service` 的门户写接口：

```text
POST   /api/portal/interaction/favorites/{unifiedVideoId}
GET    /api/portal/interaction/favorites/{unifiedVideoId}/status
POST   /api/portal/interaction/history
DELETE /api/portal/interaction/history
DELETE /api/portal/interaction/history/{id}
POST   /api/portal/feedback
GET    /api/portal/feedback
DELETE /api/portal/feedback/{id}
```

原因：

- 复用已部署的 `ircs-interaction-service`，不新增 Pod、Service 或 HTTPRoute。
- 关闭前端用户态收藏、观看历史和反馈写入缺口。
- 已通过 command/controller 单元测试、全仓 build、ARM64 rollout 和 ClusterIP 写入
  smoke 独立验证。
- `interaction` 模块剩余缺口降为后台 `/api/v1/messages/**` 4 条路由。

第三刀已完成 `ircs-portal-service` 的 explore/detail 只读接口：

```text
GET /api/portal/explore
GET /api/portal/movies/{id}
```

原因：

- 复用已部署的 `ircs-portal-service`，不新增 Pod、Service 或 HTTPRoute。
- 关闭前端用户态探索页和详情页缺口。
- 已通过 Controller/Service 单元测试、全仓 build、ARM64 rollout 和 ClusterIP
  smoke 独立验证。
- `PortalContentController` 内容浏览缺口已关闭。

第四刀已完成 `ircs-task-service` 的生命周期 command/log/state 接口：

```text
POST   /api/v1/collection-tasks
PUT    /api/v1/collection-tasks/{id}
DELETE /api/v1/collection-tasks/{id}
POST   /api/v1/collection-tasks/{id}/start
POST   /api/v1/collection-tasks/{id}/resume
POST   /api/v1/collection-tasks/{id}/pause
POST   /api/v1/collection-tasks/{id}/stop
GET    /api/v1/collection-tasks/{id}/logs
```

原因：

- 复用已部署的 `ircs-task-service`，不新增 Pod、Service 或 HTTPRoute。
- 关闭 V1 `task` 模块 HTTP Controller route 缺口。
- 已通过 Controller 单元测试、全仓 build、ARM64 rollout 和 ClusterIP
  smoke 独立验证。
- 真实 `StreamTaskExecutor`/scheduler 执行契约拆入后续 `025-09`。

第五刀已完成 `ircs-content-service` 的 raw/unified admin API：

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

原因：

- V1 raw/unified 内容管理接口已经经过验证，V3 本轮按无损迁移口径承接 21 条接口。
- 复用已部署的 `ircs-content-service` 和 `ircs-scraper-service`，不新增 Pod、Service
  或 HTTPRoute。
- search sync、normalize、enrich、refetch、recalculate 均有真实承接路径。
- 已通过 content/scraper/aggregation 相关测试、全仓 build、ARM64 rollout 和
  ClusterIP smoke 独立验证。
- 21 条接口按接口粒度完成 `1/1` 复现标记，总计 `21/21`。

第六刀已完成 `ircs-storage-service` 的 cover image admin API：

```text
GET    /api/v1/cover-images
GET    /api/v1/cover-images/{id}
DELETE /api/v1/cover-images/{id}
POST   /api/v1/cover-images/upload
POST   /api/v1/cover-images/fetch
POST   /api/v1/cover-images/{id}/sync-r2
POST   /api/v1/cover-images/{id}/retry
```

原因：

- V1 cover image 管理接口已经经过验证，V3 本轮按无损迁移口径承接 7 条接口。
- 复用已部署的 `ircs-storage-service`，不新增 Pod、Service、PVC 或 HTTPRoute。
- 继续使用 dev `emptyDir` `/app/storage` 和共享 PostgreSQL 迁移桥。
- 每条接口只有在完整迁移且 live smoke 复现后才标记 `1/1`，已完成 `7/7`。

第七刀已完成 `ircs-ops-service` 的 observability read API：

```text
GET /api/v1/ops/request-audit
GET /api/v1/ops/request-audit/summary
GET /api/v1/ops/dlq
GET /api/v1/ops/traffic/status
```

原因：

- V1 request audit、DLQ 和 traffic status 查询面已经经过验证，V3 本轮按 read-first
  口径承接 4 条接口。
- 复用已部署的 `ircs-ops-service`，不新增 Pod、Service 或 HTTPRoute。
- 不迁移 DLQ retry/discard、traffic reset 等带副作用接口。
- 每条接口只有在完整迁移且 live smoke 复现后才标记 `1/1`，已完成 `4/4`。

第八刀已完成 `ircs-interaction-service` 的 admin message API：

```text
GET    /api/v1/messages
POST   /api/v1/messages/{id}/reply
PUT    /api/v1/messages/{id}/visibility
DELETE /api/v1/messages/{id}
```

原因：

- V1 后台留言管理接口已经经过验证，V3 本轮按 admin 门户契约承接 4 条接口。
- 复用已部署的 `ircs-interaction-service`，通过 `admin-bff` 接入，不新增 Pod、Service 或
  HTTPRoute。
- 返回 `memberEmail` 用于后台管理，同时保持门户公开墙不泄漏 email。
- 响应同时输出 `isPublic` 与 `public`，兼容 V1 DTO 与 admin 门户开关读取。
- 每条接口只有在完整迁移且 live smoke 复现后才标记 `1/1`，已完成 `4/4`。

第九刀已完成 `ircs-task-service` 的 runner/scheduler 运行时契约：

```text
POST /api/v1/collection-tasks/{id}/start
POST /api/v1/collection-tasks/{id}/resume
  -> QUEUED
  -> CollectionTaskRunnerService
  -> scraper-service internal task execution
  -> q.ingest.video
  -> ingestion-worker
  -> COMPLETED/FAILED
```

原因：

- 第四刀已迁移 task lifecycle/log/state HTTP 路由，但 start/resume 仍缺真实执行。
- 本轮复用已部署的 `ircs-task-service`、`ircs-scraper-service`、`ircs-ingestion-worker`
  和 `admin-bff`，不新增 Pod、Service 或 HTTPRoute。
- `task-service` 负责 runner、scheduler、状态、日志和启动自愈。
- `scraper-service` 负责抓取/投递，不把 V1 `StreamTaskExecutor` 的抓取细节复制回
  `task-service`。
- live smoke 已通过 `admin-bff` 验证 start -> runner -> scraper internal -> ingestion，
  结果为 `COMPLETED|1|1|0`。
- 本切片不新增 public route；按运行时契约粒度完成 `1/1` 复现标记。

第十刀已完成 `ircs-identity-service` 的 admin auth API：

```text
GET  /api/v1/auth/pow
POST /api/v1/auth/login
POST /api/v1/auth/change-password
```

原因：

- V1 admin 门户登录链路依赖这三条接口，且 admin-bff 已把 `/api/v1/auth/**`
  路由到 `ircs-identity-service`。
- 复用 `system_configs` 中的 `security.admin.username`、`security.admin.password`、
  `security.jwt.secret` 和 `security.jwt.iat-floor`。
- 复用已部署的 `ircs-identity-service` 和 `admin-bff`，不新增 Pod、Service 或 HTTPRoute。
- live smoke 已通过 `admin-bff` 验证 pow/login/change-password，并恢复临时 admin 配置。
- 每条接口只有在完整迁移且 live smoke 复现后才标记 `1/1`，已完成 `3/3`。

第十一刀已完成 `ircs-identity-service` 的 member admin API：

```text
GET    /api/v1/members
GET    /api/v1/members/{id}/favorites
GET    /api/v1/members/{id}/history
PUT    /api/v1/members/{id}
PUT    /api/v1/members/{id}/status
DELETE /api/v1/members/{id}
```

原因：

- V1 admin 会员管理页依赖这 6 条接口，且 `admin-bff` 已把 `/api/v1/members/**`
  路由到 `ircs-identity-service`。
- 复用已部署的 `ircs-identity-service` 和 `admin-bff`，不新增 Pod、Service 或 HTTPRoute。
- 会员状态变更和删除已同步 `MemberStatusCacheService`。
- 收藏/历史查询按当前共享 PostgreSQL 迁移桥复现 V1 字段形状，后续物理拆库时再转为
  interaction-service internal API。
- live smoke 已通过 `admin-bff` 验证 6 条接口，并恢复临时 admin 配置、清理临时会员和视频数据。
- 每条接口只有在完整迁移且 live smoke 复现后才标记 `1/1`，已完成 `6/6`。

第十二刀已完成 `ircs-content-service` 的 people dictionary admin API：

```text
POST   /api/v1/actors
GET    /api/v1/actors
GET    /api/v1/actors/all
GET    /api/v1/actors/{id}
PUT    /api/v1/actors/{id}
PATCH  /api/v1/actors/{id}/status
DELETE /api/v1/actors/{id}
POST   /api/v1/directors
GET    /api/v1/directors
GET    /api/v1/directors/all
GET    /api/v1/directors/{id}
PUT    /api/v1/directors/{id}
PATCH  /api/v1/directors/{id}/status
DELETE /api/v1/directors/{id}
```

原因：

- V1 actors/directors 后台人员字典接口已经经过验证，V3 本轮按 admin 门户契约承接 14 条接口。
- 复用已部署的 `ircs-content-service` 和 `admin-bff`，不新增 Pod、Service 或 HTTPRoute。
- live smoke 已通过 `admin-bff` 验证 create/page/all/detail/update/status/delete 全链路，并清理临时 actors/directors 数据。
- 每条接口只有在完整迁移且 live smoke 复现后才标记 `1/1`，已完成 `14/14`。

第十三刀已完成 `ircs-catalog-service` 的 admin read contract：

```text
GET /api/v1/data-sources
GET /api/v1/data-sources/{id}
GET /api/v1/standard-categories
GET /api/v1/standard-categories/all
GET /api/v1/standard-categories/page
GET /api/v1/standard-categories/{id}
GET /api/v1/standard-genres
GET /api/v1/standard-genres/page
GET /api/v1/standard-genres/{id}
GET /api/v1/standard-areas
GET /api/v1/standard-areas/page
GET /api/v1/standard-languages
GET /api/v1/raw-categories
GET /api/v1/raw-categories/{id}
GET /api/v1/raw-genres
GET /api/v1/raw-genres/{id}
GET /api/v1/raw-areas
GET /api/v1/raw-areas/{id}
GET /api/v1/raw-languages
GET /api/v1/raw-languages/{id}/trace
GET /api/v1/raw-languages/{id}
```

原因：

- V1 datasource、standard dictionary、raw dictionary 的后台只读契约已经经过验证，V3 本轮先承接 read contract。
- 复用已部署的 `ircs-catalog-service` 和 `admin-bff`，不新增 Pod、Service 或 HTTPRoute。
- 本切片不迁移 datasource 删除、fetch-sample 等高风险写接口。
- live smoke 已通过 `admin-bff` 验证 21 条接口，并清理临时 data source、raw video、raw language 数据。
- 每条接口只有在完整迁移且 live smoke 复现后才标记 `1/1`，已完成 `21/21`。

第十四刀已完成 `ircs-config-service` 的 common timezones API：

```text
GET /api/v1/common/timezones
```

原因：

- V1 admin 门户表单依赖 common timezones 选项，且 `admin-bff` 已把 `/api/v1/common/**`
  路由到 `ircs-config-service`。
- 复用已部署的 `ircs-config-service` 和 `admin-bff`，不新增 Pod、Service 或 HTTPRoute。
- 本接口无 DB 写入、无外部调用，适合作为轻量缺口闭环。
- live smoke 已通过 `admin-bff` 验证，完成 `1/1` 复现标记。

第十五刀已完成 `ircs-content-service` 的 playlists/source-domains/resolvers admin API：

```text
POST   /api/v1/playlists
PUT    /api/v1/playlists/{id}
GET    /api/v1/playlists
GET    /api/v1/playlists/{id}
DELETE /api/v1/playlists/{id}
GET    /api/v1/source-domains
GET    /api/v1/source-domains/{id}
PUT    /api/v1/source-domains/{id}
POST   /api/v1/resolvers
PUT    /api/v1/resolvers/{id}
GET    /api/v1/resolvers
GET    /api/v1/resolvers/active
GET    /api/v1/resolvers/{id}
DELETE /api/v1/resolvers/{id}
```

原因：

- V1 playlists/source-domains/resolvers 后台辅助管理接口已经经过验证，V3 本轮按 admin 门户契约承接 14 条接口。
- 复用已部署的 `ircs-content-service` 和 `admin-bff`，不新增 Pod、Service 或 HTTPRoute。
- playlists 绑定 `raw_videos`，source-domain 与 episode URL 重组沿用共享 PostgreSQL 迁移桥。
- live smoke 已通过 `admin-bff` 验证 14 条接口，并清理临时 playlists、resolvers、source_domains、raw_videos 和 data_sources 数据。
- 每条接口只有在完整迁移且 live smoke 复现后才标记 `1/1`，已完成 `14/14`。

第十六刀已完成 `ircs-catalog-service` 的 admin write contract：

```text
POST   /api/v1/data-sources
PUT    /api/v1/data-sources/{id}
PATCH  /api/v1/data-sources/{id}
DELETE /api/v1/data-sources/{id}
POST   /api/v1/data-sources/fetch-sample
POST   /api/v1/standard-categories
PUT    /api/v1/standard-categories/{id}
PATCH  /api/v1/standard-categories/{id}
DELETE /api/v1/standard-categories/{id}
POST   /api/v1/standard-genres
PUT    /api/v1/standard-genres/{id}
DELETE /api/v1/standard-genres/{id}
POST   /api/v1/standard-areas
POST   /api/v1/standard-languages
PUT    /api/v1/raw-categories/{id}
DELETE /api/v1/raw-categories/{id}
POST   /api/v1/raw-genres
PUT    /api/v1/raw-genres/{id}
DELETE /api/v1/raw-genres/{id}
POST   /api/v1/raw-areas
PUT    /api/v1/raw-areas/{id}
DELETE /api/v1/raw-areas/{id}
POST   /api/v1/raw-languages
PUT    /api/v1/raw-languages/{id}
DELETE /api/v1/raw-languages/{id}
```

原因：

- V1 datasource、standard dictionary、raw dictionary 写契约已经经过验证，V3 本轮按 admin 门户契约承接 25 条接口。
- 复用已部署的 `ircs-catalog-service` 和 `admin-bff`，不新增 Pod、Service 或 HTTPRoute。
- `fetch-sample` 使用 namespace 内部可控 URL 验证，不访问生产外部数据源。
- live smoke 已通过 `admin-bff` 验证 25 条接口，并清理临时 data_sources、standard_* 和 raw_* 数据。
- 每条接口只有在完整迁移且 live smoke 复现后才标记 `1/1`，已完成 `25/25`。

第十七刀已完成 `ircs-config-service` 和 `ircs-credential-service` 的 admin write contract：

```text
GET    /api/v1/credentials/templates?provider=TMDB
POST   /api/v1/credentials
PUT    /api/v1/credentials/{id}
DELETE /api/v1/credentials/{id}
POST   /api/v1/credentials/refresh
POST   /api/v1/configs
PUT    /api/v1/configs/{key}
DELETE /api/v1/configs/{key}
POST   /api/v1/configs/test/{type}
```

原因：

- V1 admin 设置页依赖 configs 写入、connection test、credential templates 和 credential 写入能力。
- 复用已部署的 `ircs-config-service`、`ircs-credential-service` 和 `admin-bff`，不新增 Pod、Service 或 HTTPRoute。
- connection test 使用 dry-run 响应，不访问 R2、SMTP、LLM 或公网代理。
- live smoke 已通过 `admin-bff` 验证 9 条接口，并清理临时 `system_configs` 和 `sys_credentials` 数据。
- 每条接口只有在完整迁移且 live smoke 复现后才标记 `1/1`，已完成 `9/9`。

本轮并行边界和完成结果见：

```text
docs/reports/039-parallel-subagent-route-slices-02515-02517.md
```

第十八刀已完成 `ircs-magnet-service` 的 admin provider/search contract：

```text
POST /api/v1/magnet-providers
PUT  /api/v1/magnet-providers/{id}
POST /api/v1/magnets/search/unified/{id}
```

第十九刀已完成 `ircs-identity-service` 的 portal avatar upload contract：

```text
POST /api/portal/profile/avatar
```

第二十刀已完成 `ircs-ops-service` 的 guarded operations/debug/stream contract：

```text
GET    /api/v1/dashboard/metrics/stream
GET    /api/v1/debug/metadata/search
POST   /api/v1/ops/traffic/reset
POST   /api/v1/ops/dlq/{id}/retry
POST   /api/v1/ops/dlq/batch-retry
DELETE /api/v1/ops/dlq/{id}
POST   /api/v1/ops/dlq/batch-discard
POST   /api/v1/ops/maintenance/sanitize/init
POST   /api/v1/ops/maintenance/aggregation/reset-init
POST   /api/v1/ops/maintenance/unified/recalculate-init
POST   /api/v1/ops/maintenance/area/clean-init
POST   /api/v1/ops/maintenance/language/clean-init
POST   /api/v1/ops/maintenance/llm/sanitize/init
POST   /api/v1/ops/maintenance/llm/area/init
POST   /api/v1/ops/maintenance/llm/genre/init
POST   /api/v1/ops/maintenance/llm/category/init
POST   /api/v1/ops/maintenance/search/reindex-raw/init
POST   /api/v1/ops/maintenance/search/reindex-unified/init
POST   /api/v1/ops/maintenance/trend/sync
```

原因：

- `admin-bff` live smoke 已完成 magnet `3/3` 和 ops `19/19` 复现标记。
- `portal-bff` live smoke 已完成 avatar `1/1` 复现标记。
- 本轮修复 `portal-bff`/`admin-bff` multipart 代理预解析问题，multipart 请求可经 BFF
  透传给后端服务。
- 本轮未新增 Pod、Service 或 HTTPRoute；只滚动已有 Deployment。
- `025-20` 维护类接口按 guarded dev contract 复现接口返回和 SSE 可观察性，不执行真实
  生产级全量任务。

当前 HTTP Controller + BFF smoke 口径剩余缺口为 0 条。后续不再按“未迁移 HTTP
接口数”推进，而应进入行为深度迁移：真实 maintenance runner、外部 provider search、
队列消费者补齐、avatar 持久化/sync 和物理数据 ownership 拆分。

