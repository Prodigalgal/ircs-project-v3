# 025 V1/V3 route coverage audit - slice ledger

来源：`025-v1-v3-route-coverage-audit.md` 拆分。

### 当前进行中

```text
暂无。HTTP Controller + BFF smoke 口径剩余缺口暂为 0。
后续应转向行为深度迁移：生产级 maintenance runner、外部 provider search、
队列消费者和物理数据 ownership。
```

### 025-20 ops-service guarded operations/debug/stream contract

状态：已完成。迁移记录见
`docs/migrations/042-ops-service-guarded-operations-debug-stream-extraction.md`。

目标覆盖：

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

结果：

- 19 条 candidate route 均完成 `admin-bff` live smoke，标记 `19/19`。
- maintenance 类接口按 guarded dev contract 创建 session/SSE，不执行真实生产级全量任务。
- 临时 `failed_messages` 和 `traffic:limit:*` smoke 数据已清理。

### 025-19 identity-service portal avatar upload contract

状态：已完成。迁移记录见
`docs/migrations/041-identity-service-portal-avatar-upload-extraction.md`。

目标覆盖：

```text
POST /api/portal/profile/avatar
```

结果：

- 通过 `portal-bff` 注册、登录、multipart 上传并更新 `members.avatar_url`，标记 `1/1`。
- 修复 `portal-bff` 和 `admin-bff` multipart 预解析导致的 raw body 丢失问题。

### 025-18 magnet-service admin provider/search contract

状态：已完成。迁移记录见
`docs/migrations/040-magnet-service-admin-write-search-contract-extraction.md`。

目标覆盖：

```text
POST /api/v1/magnet-providers
PUT  /api/v1/magnet-providers/{id}
POST /api/v1/magnets/search/unified/{id}
```

结果：

- 3 条接口均完成 `admin-bff` live smoke，标记 `3/3`。
- dev search 创建 `magnet_search_jobs` 并标记 `SKIPPED`，不访问外部磁力资源站。

### 025-17 config/credential admin write contract

状态：已完成。迁移记录见
`docs/migrations/039-config-credential-admin-write-contract-extraction.md`。

目标覆盖：

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

结果：

- V3 service Controller route 数从 192 增至 201。
- 按 025 基线估算，直接缺口从 22 降至 13。
- config-service configs 写入和 dry-run connection test 缺口已关闭。
- credential-service templates/create/update/delete/refresh 缺口已关闭。
- 9 条接口均完成 `1/1` 复现标记，总计 `9/9`。

### 025-16 catalog-service admin write contract

状态：已完成。迁移记录见
`docs/migrations/038-catalog-service-admin-write-contract-extraction.md`。

目标覆盖：

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

结果：

- V3 service Controller route 数从 167 增至 192。
- 按 025 基线估算，直接缺口从 47 降至 22。
- catalog-service data-source、standard dictionary、raw dictionary 写契约缺口已关闭。
- `fetch-sample` live smoke 使用 namespace 内部可控 URL，不访问生产外部数据源。
- 25 条接口均完成 `1/1` 复现标记，总计 `25/25`。

### 025-15 content-service auxiliary admin API

状态：已完成。迁移记录见
`docs/migrations/037-content-service-aux-admin-api-extraction.md`。

目标覆盖：

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

结果：

- V3 service Controller route 数从 153 增至 167。
- 按 025 基线估算，直接缺口从 61 降至 47。
- content-service playlists/source-domains/resolvers 后台辅助管理缺口已关闭。
- 14 条接口均完成 `1/1` 复现标记，总计 `14/14`。

### 025-14 config-service common timezones API

状态：已完成。迁移记录见
`docs/migrations/036-config-service-common-timezones-api-extraction.md`。

目标覆盖：

```text
GET /api/v1/common/timezones
```

结果：

- V3 service Controller route 数从 152 增至 153。
- 按 025 基线估算，直接缺口从 62 降至 61。
- admin 门户常用时区选项缺口已关闭。
- 1 条接口完成 `1/1` 复现标记，总计 `1/1`。

### 025-13 catalog-service admin read contract

状态：已完成。迁移记录见
`docs/migrations/035-catalog-service-admin-read-contract-extraction.md`。

目标覆盖：

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

结果：

- V3 service Controller route 数从 131 增至 152。
- 按 025 基线估算，直接缺口从 83 降至 62。
- datasource、standard dictionary、raw dictionary 的 admin read contract 缺口已关闭。
- 21 条接口均完成 `1/1` 复现标记，总计 `21/21`。
- 修正了 trace 查询 SQL 中 V3 dev schema 的 `raw_videos` 表引用，并补充 repository SQL guard 测试。

### 025-12 content-service people dictionary admin API

状态：已完成。迁移记录见
`docs/migrations/034-content-service-people-admin-api-extraction.md`。

目标覆盖：

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

结果：

- V3 service Controller route 数从 117 增至 131。
- 按 025 基线估算，直接缺口从 97 降至 83。
- actors/directors 后台人员字典管理缺口已关闭。
- 14 条接口均完成 `1/1` 复现标记，总计 `14/14`。

### 025-11 identity-service member admin API

状态：已完成。迁移记录见
`docs/migrations/033-identity-service-member-admin-api-extraction.md`。

目标覆盖：

```text
GET    /api/v1/members
GET    /api/v1/members/{id}/favorites
GET    /api/v1/members/{id}/history
PUT    /api/v1/members/{id}
PUT    /api/v1/members/{id}/status
DELETE /api/v1/members/{id}
```

结果：

- V3 service Controller route 数从 111 增至 117。
- 按 025 基线估算，直接缺口从 103 降至 97。
- admin 门户会员管理链路缺口已关闭。
- 6 条接口均完成 `1/1` 复现标记，总计 `6/6`。

### 025-10 identity-service admin auth API

状态：已完成。迁移记录见
`docs/migrations/032-identity-service-admin-auth-api-extraction.md`。

目标覆盖：

```text
GET  /api/v1/auth/pow
POST /api/v1/auth/login
POST /api/v1/auth/change-password
```

结果：

- V3 service Controller route 数从 108 增至 111。
- 按 025 基线估算，直接缺口从 106 降至 103。
- admin 门户登录链路缺口已关闭。
- 3 条接口均完成 `1/1` 复现标记，总计 `3/3`。

### 025-09 task-service runner/scheduler runtime contract

状态：已完成。迁移记录见
`docs/migrations/031-task-service-runner-scheduler-extraction.md`。

目标覆盖：

```text
start/resume -> QUEUED -> runner -> scraper internal execution -> q.ingest.video -> ingestion -> COMPLETED
```

结果：

- 本切片不新增 public Controller route。
- 按 runtime contract 粒度完成 `1/1` 复现标记。
- route audit 计数保持 `V3=108`、`missing=106`，后续 public API 缺口继续拆分。

### 025-08 interaction-service admin message API

状态：已完成。迁移记录见
`docs/migrations/030-interaction-service-admin-message-api-extraction.md`。

目标覆盖：

```text
GET    /api/v1/messages
POST   /api/v1/messages/{id}/reply
PUT    /api/v1/messages/{id}/visibility
DELETE /api/v1/messages/{id}
```

结果：

- V3 service Controller route 数从 104 增至 108。
- 按 025 基线估算，直接缺口从 110 降至 106。
- `interaction` 后台留言管理缺口已关闭。
- 4 条接口均完成 `1/1` 复现标记，总计 `4/4`。

### 025-07 ops-service observability read API

状态：已完成。迁移记录见
`docs/migrations/029-ops-service-observability-read-api-extraction.md`。

目标覆盖：

```text
GET /api/v1/ops/request-audit
GET /api/v1/ops/request-audit/summary
GET /api/v1/ops/dlq
GET /api/v1/ops/traffic/status
```

结果：

- V3 service Controller route 数从 100 增至 104。
- 按 025 基线估算，直接缺口从 114 降至 110。
- request audit、DLQ、traffic status 查询面已迁移到 `ircs-ops-service`。
- 4 条接口均完成接口级复现标记，总计 `4/4`。
- DLQ retry/discard 与 traffic reset 写接口仍按高风险边界留待后续子任务。

### 025-06 storage-service cover image admin API

状态：已完成。迁移记录见
`docs/migrations/028-storage-service-cover-image-admin-api-extraction.md`。

目标覆盖：

```text
GET    /api/v1/cover-images
GET    /api/v1/cover-images/{id}
DELETE /api/v1/cover-images/{id}
POST   /api/v1/cover-images/upload
POST   /api/v1/cover-images/fetch
POST   /api/v1/cover-images/{id}/sync-r2
POST   /api/v1/cover-images/{id}/retry
```

结果：

- V3 service Controller route 数从 93 增至 100。
- 按 025 基线估算，直接缺口从 121 降至 114。
- `storage` 模块 7 条 HTTP Controller 缺口已关闭。
- 7 条接口均完成 `1/1` 复现标记，总计 `7/7`。

### 025-05 content-service raw/unified admin API

状态：已完成。迁移记录见
`docs/migrations/027-content-service-raw-unified-admin-api-extraction.md`。

新增覆盖：

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

结果：

- V3 service Controller route 数从 72 增至 93。
- 按 025 基线估算，直接缺口从 142 降至 121。
- raw/unified admin API 缺口已关闭，21 条接口均完成 `1/1` 复现标记。

### 025-02 portal-service movie detail/explore

状态：已完成。迁移记录见
`docs/migrations/025-portal-service-explore-detail-extraction.md`。

新增覆盖：

```text
GET /api/portal/explore
GET /api/portal/movies/{id}
```

结果：

- V3 service Controller route 数从 62 增至 64。
- 按 025 基线估算，直接缺口从 152 降至 150。
- `PortalContentController` 内容浏览缺口已关闭。

### 025-04 task-service lifecycle API

状态：已完成。迁移记录见
`docs/migrations/026-task-service-lifecycle-api-extraction.md`。

目标覆盖：

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

边界：

- 本切片关闭 HTTP command/log/state route。
- `start/resume` 暂按 V1 状态机进入 `QUEUED`，不复制单体内
  `StreamTaskExecutor`。
- 真实 task-runner/scheduler 执行契约拆入后续子任务。

结果：

- V3 service Controller route 数从 64 增至 72。
- 按 025 基线估算，直接缺口从 150 降至 142。
- `task` 模块 HTTP Controller 缺口已关闭；真实执行 runner 另列后续任务。

### 025-03 interaction-service write/history/favorite completion

状态：已完成。迁移记录见
`docs/migrations/024-interaction-service-portal-write-api-extraction.md`。

新增覆盖：

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

结果：

- V3 service Controller route 数从 54 增至 62。
- 按 025 基线估算，直接缺口从 160 降至 152。
- `interaction` 模块剩余缺口降至 4，均为后台 `/api/v1/messages/**`。

