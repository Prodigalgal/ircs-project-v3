# 019 aggregation-worker MVP 边界分析

状态：已完成边界分析，已完成 MVP 接入

分析日期：2026-06-04

上游来源：

- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `tasks/in-progress/011-mvp-service-onboarding-parent.md`
- `docs/migrations/015-normalization-worker-normalize-extraction.md`

关联任务：

- `tasks/done/019-01-aggregation-worker-scheduler-mvp.md`

## 背景

`ircs-normalization-worker` 已接管 `q.normalize.video`，可以将 raw video 推进到
`normalization_status=READY` 并发布 raw search sync。V1 的 aggregation 位于
`modules/ingestion/aggregation`，不是 RabbitMQ 队列触发，而是由
`AggregationScheduler` 周期扫描满足条件的 raw video：

```text
aggregation_status = PENDING
normalization_status = READY
enrichment_status != PENDING
```

扫描后 V1 会将任务置为 `PROCESSING`，执行候选匹配、聚类、创建或合并
`UnifiedVideo`，绑定 `raw_video_unified_video`，将 raw video 标记为 `BOUND`，
并触发 raw/unified search sync。

## V1 证据清单

| V1 文件 | 关键职责 |
| --- | --- |
| `AggregationScheduler` | 定时扫描 PENDING aggregation task，使用 `FOR UPDATE SKIP LOCKED` 抢占任务，救援卡住的 PROCESSING。 |
| `GraphAggregationServiceImpl` | 批量读取候选，加载上下文 unified video，调用聚类引擎，创建/合并 unified video，绑定 raw video，发布 search sync。 |
| `GraphClusteringEngine` | 使用图聚类把 raw/unified candidate 聚成 cluster。 |
| `DefaultWeightedMatchingStrategy` | 按外部 ID、标题、年份、元数据相似度计算匹配分。 |
| `AggregationPipeline` | 将 raw video 字段汇总写入 unified video。 |
| `BasicAttributeHandler` | 投票写 title/year/score/publishedAt。 |
| `ExternalIdHandler` | 写 douban/tmdb/imdb/rottenTomatoes ID。 |
| `MetadataHandler` / `CategoryHandler` / `CoverImagePipelineHandler` | 汇总描述、集数、分类、封面等复杂字段。 |
| `RawVideoMaintenanceRepository` | `findPendingAggregationTasksSkipLocked`、`updateAggregationStatus`、stuck task 查询。 |
| `UnifiedVideoRepository` | unified 查询、保存、search sync 所需读取。 |

## MVP 边界

本轮 `ircs-aggregation-worker` 包含：

- 使用 scheduler 周期扫描 PENDING aggregation task。
- 只处理满足 V1 前置条件的 raw video：
  - `normalization_status=READY`
  - `enrichment_status` 不为 `PENDING`
  - `aggregation_status=PENDING`
- 使用 `FOR UPDATE SKIP LOCKED` 抢占少量任务并置为 `PROCESSING`。
- 单条/小批量创建或复用 unified video：
  - 优先按 `douban_id`、`tmdb_id`、`imdb_id`、`rotten_tomatoes_id` 命中现有
    `unified_videos`。
  - 未命中时新建 `unified_videos`。
- 从 raw video 汇总基础字段到 unified video：
  - title、alias title、description、year、score、total episodes、duration、
    remarks、外部 ID。
- 写 `raw_video_unified_video` 绑定关系。
- 将 raw video `aggregation_status` 更新为 `BOUND`。
- 发布 `q.search.sync.unified`，让 `ircs-search-service` 写入
  `ircs_unified_video`。
- 发布 `q.search.sync.raw`，让 raw search 文档刷新 unified 关联。
- dev Deployment/Service，ClusterIP only，无 HTTPRoute。

本轮不包含：

- 不迁移完整图聚类和 CQEngine/JGraphT 依赖。
- 不执行 unified merge victim 删除和交互迁移。
- 不写 unified category/genre/area/language/person 关系表。
- 不处理 cover image 归并。
- 不做 HTTP API。
- 不做生产切换或物理数据库拆分。

## 数据边界

读取：

```text
raw_videos
raw_video_unified_video
unified_videos
```

写入：

```text
raw_videos.aggregation_status
raw_videos.updated_at
raw_video_unified_video
unified_videos
```

发布：

```text
q.search.sync.unified
q.search.sync.raw
```

## 风险

- MVP 不迁移完整聚类算法，不能替代 V1 的多源复杂合并能力。
- 如果按外部 ID 命中错误，会错误绑定 unified video；本轮 smoke 使用唯一临时
  ID 控制风险。
- scheduler 扫描 dev 数据时必须小批量、低资源，避免误触大量历史数据。
- `limits.cpu` 当前只剩 `250m`，接入前需要下调其他 dev 服务 limit 或调整本服务
  limit，否则会被 ResourceQuota 拒绝。
- 发布 unified search sync 后，smoke 必须清理 ES 文档、绑定表、unified row 和
  raw row。

## 验收建议

- `ircs-aggregation-worker` 在 `ircs-dev` Ready 1/1。
- 不新增 HTTPRoute。
- scheduler enabled 后能将临时 raw video 从 `PENDING` 推进到 `BOUND`。
- 绑定表存在 `raw_video_id -> unified_video_id`。
- `unified_videos` 出现基础字段汇总结果。
- `q.search.sync.unified` 被消费，ES `ircs_unified_video` 出现临时文档。
- `q.search.sync.raw` 被消费，raw ES 文档刷新。
- 临时 DB 行、绑定关系、ES 文档、相关队列和 DLQ 清理为 0。

## 本轮结论

`aggregation-worker` 是 normalization 后继续打通内容主链的下一刀。本轮不新增
RabbitMQ aggregation queue，而是按 V1 真实形态迁移 scheduler 扫描边界；实现
`raw_videos READY -> unified_videos/raw_video_unified_video -> q.search.sync.*`
的最小可验证闭环。

## 实现与验证回写

实现结果见：

```text
docs/migrations/016-aggregation-worker-scheduler-extraction.md
tasks/done/019-01-aggregation-worker-scheduler-mvp.md
```

已完成：

- `ircs-aggregation-worker` scheduler、JDBC repository 和 search sync publisher。
- `deploy/k8s/dev/aggregation-worker-dev.yaml`。
- ARM64 Jib 镜像推送。
- `ircs-dev` rollout，Deployment Ready 1/1。
- 临时 raw/unified 聚合 smoke。
- PostgreSQL、Elasticsearch 和 RabbitMQ 临时数据清理。

当前 MVP 接入数已从 11/18 提升到 12/18。
