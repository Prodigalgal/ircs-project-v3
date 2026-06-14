# 025 V1 路由覆盖闭环父任务

状态：进行中

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

## 背景

18 个目标业务服务已完成 MVP rollout，但完整 V1 到 V3 微服务迁移还没有闭环。当前需要
以 V1 Controller 路由和服务行为为基线，逐批关闭 V3 缺口。

## 目标

- 维护 V1/V3 路由覆盖差距。
- 将缺口拆成可独立实现和验证的子任务。
- 优先关闭低风险、前端可见、只读或轻写的 API。
- 对高风险批量写、DLQ retry、traffic reset、destructive maintenance 设置单独保护边界。

## 非目标

- 不替代 `011` 的 MVP rollout 父任务。
- 不在本父任务内做生产切换。
- 不在本父任务内做物理 DB 拆分。
- 不把路由存在等同于行为完全等价。

## 当前基线

```text
V1 routes: 203
V3 routes: 226
missing:   0
```

说明：`V3 routes=224` 为完成 `025-18` 到 `025-20` 后的 closeout 口径，包含 23 条
源码复核 candidate route。`025-20` 属于 guarded dev contract，HTTP 接口和 BFF
smoke 已闭环，但生产级全量 maintenance runner、外部 provider 搜索和物理数据拆分仍为
后续行为深度迁移，不再计入 HTTP Controller 缺口数。当前 `226` 比 closeout 口径多
2 条 post-025 additive ops 查询端点：`GET /api/v1/ops/worker-job-audit` 和
`GET /api/v1/ops/worker-job-audit/summary`，不影响 missing=0。

## 子任务

进行中：

```text
暂无
```

已完成：

```text
tasks/done/025-01-search-service-portal-query-api-mvp.md
tasks/done/025-02-portal-service-explore-detail-mvp.md
tasks/done/025-03-interaction-service-portal-write-api-mvp.md
tasks/done/025-04-task-service-lifecycle-api-mvp.md
tasks/done/025-05-content-service-raw-unified-admin-api-lossless.md
tasks/done/025-06-storage-service-cover-image-admin-api-lossless.md
tasks/done/025-07-ops-service-observability-read-api-mvp.md
tasks/done/025-08-interaction-service-admin-message-api-mvp.md
tasks/done/025-09-task-service-runner-scheduler-mvp.md
tasks/done/025-10-identity-service-admin-auth-api-mvp.md
tasks/done/025-11-identity-service-member-admin-api-mvp.md
tasks/done/025-12-content-service-people-admin-api-mvp.md
tasks/done/025-13-catalog-service-admin-read-contract-mvp.md
tasks/done/025-14-config-service-common-timezones-api-mvp.md
tasks/done/025-15-content-service-aux-admin-api-mvp.md
tasks/done/025-16-catalog-service-admin-write-contract-mvp.md
tasks/done/025-17-config-credential-admin-write-contract-mvp.md
tasks/done/025-18-magnet-service-admin-write-search-contract-mvp.md
tasks/done/025-19-identity-service-portal-avatar-upload-contract-mvp.md
tasks/done/025-20-ops-service-guarded-operations-debug-stream-contract-mvp.md
```

Backlog 候选：

```text
HTTP Controller + BFF smoke 口径暂为空。
后续转向行为深度缺口：生产级 maintenance runner、外部 provider search、队列消费者和物理数据拆分。
```

## 最新完成 2026-06-07 remaining route closeout

`025-18`、`025-19`、`025-20` 已完成三组剩余 route candidate 迁移与复现：

```text
025-18 magnet-service admin provider/search contract: MARK 3/3
025-19 identity-service portal avatar upload contract: MARK 1/1
025-20 ops-service guarded operations/debug/stream contract: MARK 19/19
```

本轮同时修复 `ircs-portal-bff` 和 `ircs-admin-bff` 的 multipart 代理行为，关闭 BFF
自身 multipart 预解析，保证头像上传等 multipart 请求由后端服务解析。

当前 `025` HTTP Controller + BFF smoke 口径：

```text
V1 routes: 203
V3 routes: 226
missing:   0
```

说明：当前值包含 2026-06-08 `028-12` 新增的 2 条 worker/job audit ops 查询端点；
`025` closeout 时刻的 V3 route 数为 224。

## 最新完成 2026-06-06

`025-09` 已完成 task runner/scheduler runtime contract 迁移：

```text
start/resume -> QUEUED -> runner -> scraper internal execution -> q.ingest.video -> ingestion -> COMPLETED
```

本切片不新增 public Controller route，因此 public route audit 仍保持：

```text
V1 routes: 203
V3 routes: 108
missing:   106
```

复现标记：

```text
task runner/scheduler runtime contract: 1/1
```

## 最新完成 2026-06-06 admin auth

`025-10` 已完成 identity-service admin auth API 迁移：

```text
GET  /api/v1/auth/pow
POST /api/v1/auth/login
POST /api/v1/auth/change-password
```

复现标记：

```text
MARK 1/3 GET  /api/v1/auth/pow
MARK 2/3 POST /api/v1/auth/login
MARK 3/3 POST /api/v1/auth/change-password
```

按 `025` route audit 估算，当前 public route 覆盖更新为：

```text
V1 routes: 203
V3 routes: 111
missing:   103
```

## 最新完成 2026-06-06 member admin

`025-11` 已完成 identity-service member admin API 迁移：

```text
GET    /api/v1/members
GET    /api/v1/members/{id}/favorites
GET    /api/v1/members/{id}/history
PUT    /api/v1/members/{id}
PUT    /api/v1/members/{id}/status
DELETE /api/v1/members/{id}
```

复现标记：

```text
MARK 1/6 GET    /api/v1/members
MARK 2/6 GET    /api/v1/members/{id}/favorites
MARK 3/6 GET    /api/v1/members/{id}/history
MARK 4/6 PUT    /api/v1/members/{id}
MARK 5/6 PUT    /api/v1/members/{id}/status
MARK 6/6 DELETE /api/v1/members/{id}
```

按 `025` route audit 估算，当前 public route 覆盖更新为：

```text
V1 routes: 203
V3 routes: 117
missing:   97
```

## 最新完成 2026-06-06 people/catalog/timezones

`025-12` 已完成 content-service actors/directors admin API 迁移：

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

`025-13` 已完成 catalog-service admin read contract 迁移：

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

`025-14` 已完成 config-service common timezones API 迁移：

```text
GET /api/v1/common/timezones
```

复现标记：

```text
content-service people admin API:        MARK 14/14
catalog-service admin read contract:     MARK 21/21
config-service common timezones API:     MARK 1/1
```

本轮通过 `admin-bff` live smoke 后，按 `025` route audit 估算，当前 public route 覆盖更新为：

```text
V1 routes: 203
V3 routes: 153
missing:   61
```

## 最新完成 2026-06-07 content auxiliary admin

`025-15` 已完成 content-service playlists/source-domains/resolvers admin API 迁移：

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

复现标记：

```text
content-service auxiliary admin API: MARK 14/14
```

本轮通过 `admin-bff` live smoke 后，按 `025` route audit 估算，当前 public route 覆盖更新为：

```text
V1 routes: 203
V3 routes: 167
missing:   47
```

## 最新完成 2026-06-07 catalog/config/credential write contract

`025-16` 已完成 catalog-service admin write contract 迁移：

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

`025-17` 已完成 config/credential admin write contract 迁移：

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

复现标记：

```text
catalog-service admin write contract:     MARK 25/25
config/credential admin write contract:   MARK 9/9
```

本轮通过 `admin-bff` live smoke 后，按 `025` route audit 估算，当前 public route 覆盖更新为：

```text
V1 routes: 203
V3 routes: 201
missing:   13
```

## 验收标准

- 每个子任务有明确输入、输出、影响范围、风险和测试边界。
- 每个子任务完成后至少更新对应任务和迁移/报告文档。
- 涉及 K8S 的子任务必须继续验证 ARM64 镜像、rollout、ClusterIP smoke、无 HTTPRoute。
