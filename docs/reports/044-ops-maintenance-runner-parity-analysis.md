# 044 ops maintenance runner parity 分析报告

状态：已完成（dev-safe runner、ARM64 rollout 与 ClusterIP smoke 已闭环）

更新时间：2026-06-07

## 背景

`028` 行为级 parity 主线已经确认 HTTP Controller/BFF route missing 为 `0`，
`028-01` 也已完成 BFF request audit collection。ops maintenance 的当前缺口是：
V3 `ircs-ops-service` 已复现 V1 maintenance route、session 和 SSE 形态，但多条
runner 仍只输出 dev guarded 日志，没有真实可验证副作用。

本报告为 `028-04` 固定分析结论和最小实现边界，确保进入代码前具备本地需求、范围、
风险和验收记录。

## 上游来源

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/023-ops-service-boundary-analysis.md`
- `docs/migrations/042-ops-service-guarded-operations-debug-stream-extraction.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- V1 `MaintenanceController`
- V1 `MaintenanceServiceImpl`
- V1 `TrendSyncServiceImpl`
- V3 `services/ircs-ops-service`

## 当前 V3 状态

V3 `MaintenanceController` 已覆盖以下入口：

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
POST /api/v1/ops/maintenance/trend/sync
GET  /api/v1/ops/maintenance/stream/{sessionId}
```

当前 `MaintenanceSessionService` 行为：

- `initSession(taskName)` 只创建进程内 session。
- `stream(sessionId)` 对存在 session 输出两条 guarded LOG 和 DONE。
- 不执行 reindex、trend sync、sanitize、aggregation reset 或其他真实 runner。

## V1 行为证据

V1 `MaintenanceController`：

- `search/reindex-raw/init` 调用 `maintenanceService.initRawReindexSession()`。
- `search/reindex-unified/init` 调用 `maintenanceService.initUnifiedReindexSession()`。
- `trend/sync` 调用 `trendSyncService.syncTrends()`。

V1 `MaintenanceServiceImpl.initUnifiedReindexSession()` 会：

```text
IndexLifecycleService.hardResetIndex(UNIFIED_VIDEO)
IndexLifecycleService.startBulkMode(UNIFIED_VIDEO)
分页读取 unified_videos ID
SearchSyncService.batchSyncUnifiedVideos(batchIds)
SSE 输出 progress
IndexLifecycleService.endBulkMode(UNIFIED_VIDEO)
```

V1 `TrendSyncServiceImpl.syncTrends()` 会：

```text
调用 TMDB/豆瓣等 TrendListProvider
根据 doubanId/tmdbId/title 匹配 unified_videos
更新 last_trend_at
为新条目创建 UnifiedVideo
投递 ENRICH_METADATA
触发 backgroundSearch
```

## 方案比较

| 候选 runner | 真实副作用 | 风险 | 本轮结论 |
| --- | --- | --- | --- |
| `search-reindex-raw` hard reset 复刻 | 重置 raw ES index 并全量同步 | 高，触碰 ES lifecycle 和大批量数据 | 后置 |
| `search-reindex-unified` hard reset 复刻 | 重置 unified ES index 并全量同步 | 高，触碰 ES lifecycle 和大批量数据 | 后置 |
| `search-reindex-unified` dev-safe message | 小批量投递 `q.search.sync.unified` INDEX 消息 | 低，最多触发少量 search index upsert | 本轮选择 |
| `trend-sync` 完整 V1 复刻 | 外呼 provider、更新/创建内容、投递消息 | 高，公网外呼和数据写入面过宽 | 后置 |
| `trend-sync` 最小 last_trend_at 更新 | 小批量更新 `unified_videos.last_trend_at` | 中，直接改内容读模型排序 | 后置备选 |

## 本轮决策

本轮实现 `search-reindex-unified` 的 dev-safe runner：

```text
POST /api/v1/ops/maintenance/search/reindex-unified/init
  -> 创建 session

GET /api/v1/ops/maintenance/stream/{sessionId}
  -> 若 taskName = search-reindex-unified:
       查询 unified_videos 最新 N 个 ID
       向 x.search / search.sync.unified 投递结构化 SearchSyncMessage(INDEX)
       SSE 输出投递数量和 DONE
     其他 taskName:
       继续 guarded fallback
```

默认限额使用小值，允许通过环境变量调小或调大：

```text
OPS_MAINTENANCE_REINDEX_DEV_LIMIT=5
```

## 安全边界

- 不 hard reset Elasticsearch index。
- 不调用 V1 `IndexLifecycleService`。
- 不删除或更新 `raw_videos`、`unified_videos`、`raw_video_unified_video`。
- 不触发外部 trend provider。
- 不新增公网 HTTPRoute。
- 不新增 Pod、Service、PVC 或中间件。
- 保留现有 guarded fallback，避免其他 maintenance init 被误执行。

## 验收标准

- `search-reindex-unified` SSE 不再只有 guarded 日志，而是执行限额消息投递。
- 单元测试能验证：
  - 有候选 ID 时向 `QueueTopic.SEARCH_SYNC_UNIFIED` 投递结构化 `SearchSyncMessage`。
  - 无候选 ID 时不投递消息但正常返回 `0`。
  - 未接 runner 的 maintenance task 仍走 guarded fallback。
- `.\gradlew.bat :services:ircs-ops-service:test` 通过。

## 遗留风险

- 当前副作用是 search sync 消息投递；若 search-service 正在消费，会进一步写入
  Elasticsearch。这是预期的低风险 upsert，但仍需由 dev limit 控制。
- 进程内 session 仍不支持多副本和重启恢复。
- RawVideo reindex、完整 trend sync、sanitize、aggregation reset、LLM clean 仍未完成行为
  parity，需要后续 task spec 单独推进。

## 当前结论

本轮最小实现已完成，并已由主线程完成索引页、K8S 环境快照、ARM64 image rollout
和 ClusterIP smoke 回写。

## 实施结果

新增实现：

```text
MaintenanceReindexCommand
MaintenanceRunnerService
MaintenanceRunResult
MaintenanceSessionService runner dispatch
MaintenanceEvent.warn / MaintenanceEvent.progress
app.ops.maintenance.reindex.dev-limit
```

运行路径：

```text
search-reindex-unified init
  -> in-memory session
  -> stream(sessionId)
  -> MaintenanceRunnerService.supports("search-reindex-unified")
  -> MaintenanceReindexCommand.enqueueUnifiedIndex()
  -> select unified_videos.id order by updated_at desc limit :limit
  -> publish SearchSyncMessage(entityType=UNIFIED_VIDEO, operation=INDEX)
```

默认限额：

```text
OPS_MAINTENANCE_REINDEX_DEV_LIMIT=5
```

硬性保护：

- 配置值被限制在 `0..100`。
- `0` 时不查询数据库、不投递消息。
- 无候选 `unified_videos` 时只输出 WARN + DONE。
- 未接入 runner 的 maintenance task 继续 guarded fallback。

## 验收结果

完成标记：

```text
MARK 1/1 search-reindex-unified SSE runner publishes capped unified search INDEX messages
```

测试命令：

```text
.\gradlew.bat :services:ircs-ops-service:test
BUILD SUCCESSFUL
```

测试覆盖：

- 有候选 ID 时向 `x.search / search.sync.unified` 投递结构化
  `SearchSyncMessage(UNIFIED_VIDEO, INDEX)`。
- 无候选 ID 时不投递 RabbitMQ 消息。
- `OPS_MAINTENANCE_REINDEX_DEV_LIMIT=0` 等价配置下不查库、不投递。
- `search-reindex-unified` session stream 会执行 runner。
- `sanitize` 等未支持 task 不执行真实 runner。

## 后续建议

- `admin-bff` ClusterIP smoke 已完成，SSE 文本、RabbitMQ 投递和 search-service ES
  upsert 已验证。
- RawVideo reindex、完整 ES hard reset/bulk mode、trend sync provider 和
  `last_trend_at` 更新继续拆独立 task spec。
- 若后续接入多副本 ops-service，应先把 maintenance session 从进程内 map 迁移到 Valkey
  或持久化表。

## 主线程集成验收

2026-06-07 已完成 ARM64 镜像推送、K8S rollout 和 ClusterIP smoke：

```text
ircs-ops-service@sha256:77736bf364124fe8e582f71ce72cee7edf065c2812b16bbcfe1101963046253d

deployment/ircs-ops-service    Ready 1/1
deployment/ircs-search-service Ready 1/1
deployment/ircs-admin-bff      Ready 1/1
```

Smoke：

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
