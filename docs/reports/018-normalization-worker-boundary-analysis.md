# 018 normalization-worker MVP 边界分析

状态：已完成

分析日期：2026-06-03

上游来源：

- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `tasks/in-progress/011-mvp-service-onboarding-parent.md`
- `docs/migrations/014-ingestion-worker-video-ingest-extraction.md`

关联任务：

- `tasks/done/018-01-normalization-worker-normalize-mvp.md`

迁移记录：

- `docs/migrations/015-normalization-worker-normalize-extraction.md`

## 背景

`ircs-ingestion-worker` 已完成视频入库 MVP，并在成功写入 `raw_videos` 后向
`q.normalize.video` 发布 raw video UUID。当前 `q.normalize.video` 在
`ircs-dev` 中仍无 consumer，导致新入库视频停在
`normalization_status=PENDING`，后续 enrichment、aggregation 和 raw search
可见性链路不能自然推进。

V1 normalization 位于 `modules/ingestion`，核心实现是
`RawVideoNormalizationServiceImpl` 消费 `q.normalize.video`，读取
`raw_videos.raw_metadata`，执行 normalization pipeline，成功后将
`normalization_status` 置为 `READY`，并触发 raw search sync。

## V1 证据清单

| V1 文件 | 关键职责 |
| --- | --- |
| `RawVideoNormalizationServiceImpl` | 消费 `q.normalize.video`，逐个 raw video 原子清洗，成功后 `READY` 并触发 search sync，失败后按 retry/backoff 更新状态。 |
| `NormalizationPipeline` / `NormalizationHandler` | 按 handler order 执行清洗步骤，handler 失败时记录日志并尽力继续。 |
| `NormalizationContext` | 持有 `RawVideo`、`rawMetadata` 和 handler state，并支持 `locked_fields` 判断。 |
| `BaseAttributesHandler` | 从 raw metadata 写 alias、description、episodes、duration、remarks、score、外部 ID、data source。 |
| `YearNormalizationHandler` | 校验并规范年份。 |
| title handlers | 清洗 title、alias title、season、subtitle。 |
| `CoverImageHandler` | 通过 storage/content 能力处理 cover image。 |
| `AreaLanguageHandler` / `CategoryGenreHandler` / `PersonHandler` | 写 raw area/language/genre/category/person 关系。 |
| `NormalizationScheduler` | 启动恢复 PROCESSING，watchdog 重置卡住任务，队列空闲时补 PENDING，按 backoff 重试 FAILED。 |
| `RawVideoMaintenanceRepository` | 查询/更新 normalization 状态和重试时间。 |

## MVP 边界

本轮 `ircs-normalization-worker` 包含：

- 消费 `q.normalize.video`，兼容 legacy UUID 字符串 payload。
- 读取 `raw_videos` 当前状态和 `raw_metadata`。
- 幂等处理：`normalization_status=READY` 时跳过。
- `raw_metadata` 为空或无效 JSON 时标记 `FAILED` 并递增
  `normalization_retry_count`。
- 对基础字段执行轻量 normalization：
  - title 清洗。
  - alias title 清洗。
  - year 合法性校验。
  - description、remarks、totalEpisodes、duration、score、外部 ID 更新。
  - 尊重 `locked_fields`，被锁字段不覆盖。
- 成功后将 `normalization_status` 更新为 `READY`。
- 成功后发布 `q.search.sync.raw`，让现有 `ircs-search-service` 更新 ES。
- dev Deployment/Service，ClusterIP only，无 HTTPRoute。

本轮不包含：

- 不迁移完整 V1 handler 体系。
- 不写 `cover_images`，不触发 storage download。
- 不写 raw area/language/genre/category/person 关系表。
- 不实现 startup recovery、watchdog、FAILED backoff scheduler。
- 不消费 enrichment/aggregation 队列。
- 不做物理数据库拆分。

## 数据表边界

读取：

```text
raw_videos
```

写入：

```text
raw_videos.normalization_status
raw_videos.normalization_retry_count
raw_videos.next_normalization_retry_time
raw_videos.title
raw_videos.alias_title
raw_videos.description
raw_videos.year
raw_videos.remarks
raw_videos.score
raw_videos.total_episodes
raw_videos.duration
raw_videos.douban_id
raw_videos.tmdb_id
raw_videos.imdb_id
raw_videos.rotten_tomatoes_id
raw_videos.raw_language_str
raw_videos.updated_at
```

发布：

```text
q.search.sync.raw
```

## 风险

- V1 pipeline 完整度较高，本轮只抽 MVP 字段清洗和状态链，复杂字典关系需要后续切片补齐。
- 本轮为降低 dev 镜像体积，不引入 HanLP/OpenCC，完整简繁转换和更深层标题 handler 后续单独补齐。
- `locked_fields` 是 raw video 的人工修正保护，MVP 必须保留。
- `q.search.sync.raw` 发布后会由 search-service 读取当前 DB 并写 ES，smoke 需要验证 ES 文档出现并清理。
- 没有 scheduler 时，异常 FAILED 重试需要后续任务接管；本轮只验证单条失败状态。
- 如果 `ingestion-worker` 和 `normalization-worker` 同时跑在 dev，需要确保都只连接 `ircs-dev` RabbitMQ/PostgreSQL。

## 验收建议

- `q.normalize.video` 有 1 个 consumer。
- 投递 raw video UUID 后，`raw_videos.normalization_status` 从 `PENDING` 变为
  `READY`。
- 被 `locked_fields` 锁住的字段不被 raw metadata 覆盖。
- 成功 normalization 后 `q.search.sync.raw` 被消费，ES `ircs_raw_video`
  出现对应文档。
- 无效 payload 或空 raw metadata 不污染队列，失败状态可解释。
- smoke 临时 DB 行和 ES 文档清理为 0。

## 本轮结论

`normalization-worker` 是 ingestion 后的下一条关键链路。本轮先打通
`q.normalize.video -> raw_videos READY -> q.search.sync.raw -> ES`，把 V1
本地事件副作用显式拆成跨服务队列消息；完整 handler 和 scheduler 后续再拆。

## 验证结果

本轮已完成实现和部署：

```text
ircs-normalization-worker Ready 1/1
image digest sha256:229f8f9bd93bae31b99d6d61adefd12daa918e0755d3ea86c3a5e496fb4c6314
q.normalize.video consumers: 1
q.search.sync.raw consumers: 1
No HTTPRoute in ircs-dev
```

Smoke 使用临时 raw video
`b2d67282-9a0d-4a63-86d8-0cd0f0e25a10`，验证
`normalization_status=READY`、`locked_fields` 不覆盖 title、
`q.search.sync.raw` 被 search-service 消费并写入 `ircs_raw_video`。清理后
PostgreSQL 临时行 0、Elasticsearch 文档 `found=false`、相关队列和 DLQ 均为
0。
