# 016 scraper-service 与 ingestion-worker MVP 边界分析

状态：已完成边界分析；ingestion-worker 与 scraper-service 均已完成 MVP 接入

分析日期：2026-06-03

上游来源：

- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

关联任务：

- `tasks/done/016-01-scraper-service-manual-scrape-mvp.md`
- `tasks/done/016-02-ingestion-worker-video-ingest-mvp.md`

## 背景

当前 V3 目标是将 `ircs-project-v1` 单体完整拆分为 18 个微服务或 worker。`ircs-ingestion-worker` 已完成 `q.ingest.video`、`q.process.playlist_sync` 和 `q.normalize.video` publish 的 MVP 接入；`ircs-scraper-service` 随后也完成 manual scrape API、抓取/映射和投递边界的 MVP 接入。V1 中 manual scrape、list/detail 抓取、JSON mapping、入库投递、批量写库和 playlist 同步分布在 `modules/scraper`、`modules/ingestion`、`modules/content`、`modules/datasource` 和 queue 基础设施中。

本报告保留 MVP 拆分边界。`ingestion-worker` 的实现、部署和 smoke 结果见 `docs/migrations/014-ingestion-worker-video-ingest-extraction.md`。

## V1 证据清单

### Controller

| V1 文件 | 路由 | 结论 |
| --- | --- | --- |
| `ircs-backend/src/main/java/com/prodigalgal/ircs/modules/scraper/controller/ManualScraperController.java` | `/api/v1/scraper/manual` | 只包含 `POST /init` 和 `GET /stream/{sessionId}` SSE，要求 `ADMIN` 权限。 |

`modules/ingestion` 未发现 HTTP Controller；ingestion 主要是 RabbitMQ worker 边界。

### Service 与核心流程

| 模块 | V1 文件 | 职责 |
| --- | --- | --- |
| scraper | `ManualScraperService` / `ManualScraperServiceImpl` | 创建 manual scrape session，读取 `data_sources`，按数据源和页码抓取，输出 SSE 事件。 |
| scraper | `ListScraperService` / `ListScraperServiceImpl` | 根据 `DataSource` 的 `baseUrl`、`listPath`、`listParams` 和 `fieldMapping.list_mapping` 抓取列表 ID。 |
| scraper | `MappingService` / `MappingServiceImpl` | 根据 `fieldMapping.detail_mapping` 把详情 JSON 映射为 `ScrapedVideo`，生成 `sourceHash`。 |
| scraper | `ScraperPipelineExecutor` + `HttpFetchStage` + `MappingStage` + `PersistenceStage` | 顺序执行 detail HTTP、mapping、persistence。 |
| ingestion | `RawVideoIngestionService` / `RawVideoIngestionServiceImpl` | 将 `ScrapedVideo` assemble 为 `IngestionItem`，投递 ingest 队列；消费后批量 upsert 和触发 playlist 同步。 |
| ingestion | `BatchVideoPersister` | 消费 `q.ingest.video`，调用 `saveBatch`。 |
| ingestion | `PlaylistSyncConsumer` | 消费 `q.process.playlist_sync`，同步 playlists/episodes。 |
| ingestion | `RawVideoNormalizationServiceImpl` | V1 通过本地 `RawVideosPersistedEvent` 把入库成功 ID 继续投递到 `q.normalize.video`。该桥接在拆分后不能依赖本地 Spring event 自动存在。 |
| search | `SearchEventListener` + `SearchOutboxRelay` | V1 入库事件还会写 `search_sync_tasks` 并由 relay 投递 `q.search.sync.raw`。V3 已有 search-service 消费 `q.search.sync.raw`，但 ingestion MVP 需决定是否直接补索引消息或后置。 |

### DTO / VO / 消息载荷

| 类型 | V1 文件 | 用途 |
| --- | --- | --- |
| `ManualScrapeConfigDTO` | `modules/scraper/model/dto/ManualScrapeConfigDTO.java` | manual scrape 输入：`keyword`、页码范围、UA、proxy、headers、delay。 |
| `ScrapeEvent` | `modules/scraper/model/dto/ScrapeEvent.java` | SSE 事件：`LOG`、`CARD`、`DONE`、`ERROR`。 |
| `ScrapedVideoCardDTO` | `modules/scraper/model/dto/ScrapedVideoCardDTO.java` | SSE card 输出：source、title、cover、sourceVid、status、error、year、category、detailUrl。 |
| `ScrapeListResult` / `ScrapedItemSummary` | `modules/scraper/model/dto/*` | 列表抓取结果和单条 ID 摘要。 |
| `ScrapedVideo` / `ScrapedPlaylist` / `ScrapedEpisode` | `modules/ingestion/model/vo/*` | scraper 到 ingestion 的领域中间态。 |
| `IngestionItem` | `modules/ingestion/model/vo/IngestionItem.java` | ingest 队列 payload：`IngestionVideoDTO video` + `forceIngest`。 |
| `IngestionVideoDTO` / `IngestionPlaylistDTO` / `IngestionEpisodeDTO` | `modules/ingestion/model/dto/*` | 入库队列和 playlist 同步所需结构。 |
| `PlaylistSyncMessage` | `modules/ingestion/model/dto/PlaylistSyncMessage.java` | playlist 同步队列 payload：`videoId`、`sourceHash`、`dataHash`、`playlists`。 |

### Entity / Repository / 表

| V1 Entity / Repository | 表 | MVP 读写边界 |
| --- | --- | --- |
| `DataSource` / `DataSourceRepository` | `data_sources` | scraper-service 读取；MVP 不写。 |
| `RawVideo` / `RawVideoRepository` / `RawVideoIngestionRepositoryImpl` | `raw_videos` | ingestion-worker 写入和 upsert；scraper-service 不直接写。 |
| `Playlist` / `Episode` | `playlists`、`episodes` | ingestion-worker 的 playlist sync 写入、更新、删除。 |
| `SourceDomain` / `SourceDomainRepository` | `source_domains` | ingestion assemble 阶段会抽取播放地址域名并创建或关联域名；MVP 需纳入 ingestion-worker 写边界或后置为风险。 |
| `SearchSyncTask` / `SearchSyncTaskRepository` | `search_sync_tasks` | V1 search outbox 表；本轮 scraper/ingestion MVP 不建议直接拥有。 |

`raw_videos` 写入关键字段包括 `source_vid`、`source_hash`、`data_hash`、`title`、`alias_title`、`description`、`year`、`remarks`、`score`、`published_at`、`total_episodes`、`duration`、外部 ID、`raw_metadata`、`data_source_id`、`enrichment_status`、`normalization_status`。V1 ingest upsert 会将 `normalization_status` 置为 `PENDING`，并让 `enrichment_status` 保持 `PENDING`。

## 队列与消息流

### V1 队列名

| QueueTopic | Exchange | Routing key | Queue | 当前 V3 ownership |
| --- | --- | --- | --- | --- |
| `INGEST_VIDEO` | `x.ingest` | `video.upsert` | `q.ingest.video` | `ircs-ingestion-worker` |
| `PLAYLIST_SYNC` | `x.process` | `video.playlist_sync` | `q.process.playlist_sync` | `ircs-ingestion-worker` |
| `NORMALIZE_VIDEO` | `x.process` | `video.normalize` | `q.normalize.video` | `ircs-normalization-worker` |
| `ENRICH_METADATA` | `x.process` | `video.enrich` | `q.enrich.metadata` | `ircs-metadata-worker` |
| `SEARCH_SYNC_RAW` | `x.search` | `search.sync.raw` | `q.search.sync.raw` | `ircs-search-service` |

### V1 manual scrape 到 ingest 流

```text
POST /api/v1/scraper/manual/init
  -> ManualScraperService.initSession
  -> cache manual scrape config

GET /api/v1/scraper/manual/stream/{sessionId}
  -> ManualScraperService.stream
  -> data_sources.findAll
  -> ListScraperService.fetchIdsFromPage
  -> HttpFetchStage detail GET
  -> MappingStage map JSON to ScrapedVideo
  -> PersistenceStage
  -> RawVideoIngestionService.upsertVideo
  -> publishBlocking x.ingest/video.upsert
  -> q.ingest.video
```

### V1 ingest consumer 流

```text
q.ingest.video
  -> BatchVideoPersister
  -> RawVideoIngestionService.saveBatch
  -> RawVideoIngestionRepository.batchUpsert(raw_videos)
  -> resolve persisted raw video IDs
  -> publish x.process/video.playlist_sync
  -> publish RawVideosPersistedEvent

q.process.playlist_sync
  -> PlaylistSyncConsumer
  -> RawVideoIngestionService.syncPlaylists
  -> batchSyncPlaylists(playlists, episodes)
```

拆分后需要显式恢复 V1 的本地事件副作用：

- `RawVideosPersistedEvent -> q.normalize.video`：否则新入库 raw video 会停在 `normalization_status=PENDING`。
- `RawVideosPersistedEvent -> search sync`：MVP 可以先接受 raw 搜索更新后置，但 smoke 应记录该差异；若要求端到端可见，ingestion-worker 应直接投递 `q.search.sync.raw` 的 `INDEX` 消息或写入被 search-service 接管的 outbox。

## 拆分边界

### scraper-service MVP 边界

`ircs-scraper-service` 应拥有前端 manual scrape API 与抓取/映射流程，但不直接写内容表。

包含：

- 保留 V1 兼容路由 `/api/v1/scraper/manual/init`。
- 保留 V1 兼容 SSE 路由 `/api/v1/scraper/manual/stream/{sessionId}`。
- 读取 `data_sources`，使用 V1 `fieldMapping` 解析 list/detail。
- 执行 HTTP 抓取、限流、JSON mapping、playlist parse。
- 生成 `IngestionItem` 并投递 `QueueTopic.INGEST_VIDEO`。
- SSE 输出 `LOG`、`CARD`、`DONE`、`ERROR`，card 状态在 MVP 中以“已投递/投递失败”为准，不能假装已完成 DB upsert。

不包含：

- 不消费 `q.ingest.video`。
- 不写 `raw_videos`、`playlists`、`episodes`、`source_domains`。
- 不接入 collection task 调度、暂停、恢复、task log 统计。
- 不迁移 trend sync、metadata enrichment provider、background search。
- 不新增 HTTPRoute，不碰 K8S。

### ingestion-worker MVP 边界

`ircs-ingestion-worker` 应拥有 ingest 队列消费、raw video upsert、playlist sync 和必要的下游触发。

包含：

- 消费 `q.ingest.video`，解析 `IngestionItem`。
- 批量查询 `raw_videos.source_hash` 和 `data_hash`，支持 hash unchanged skip。
- upsert `raw_videos`，保持 V1 `normalization_status=PENDING`、`enrichment_status=PENDING`、`aggregation_status=PENDING` 语义。
- 消费或投递并处理 `q.process.playlist_sync`，同步 `playlists` 和 `episodes`。
- 处理 `source_domains` 的 extract-or-create，或在实现时明确保留 V1 assembler 行为。
- 入库成功后投递 `q.normalize.video`，让 normalization-worker 后续接管。
- 记录失败重试边界，避免单条坏数据拖垮整个 batch。

不包含：

- 不暴露 HTTP API。
- 不执行 list/detail HTTP 抓取。
- 不做 normalization pipeline、metadata enrichment、aggregation。
- 不消费 `q.normalize.video`、`q.enrich.metadata`、`q.search.sync.raw`。
- 不接管 `search_sync_tasks` 表。
- 不做物理数据库拆分。

## 数据表边界

```text
scraper-service
  READ:
    data_sources
    system config / traffic config if实现沿用 V1 延迟策略
  WRITE:
    none for content tables
  STATE:
    manual scrape session cache, preferably Valkey or bounded in-memory for MVP

ingestion-worker
  READ:
    raw_videos(source_hash, data_hash, id)
    data_sources for FK/reference
    source_domains for domain reuse
  WRITE:
    raw_videos
    playlists
    episodes
    source_domains
  PUBLISH:
    q.process.playlist_sync
    q.normalize.video
    optional q.search.sync.raw
```

## 主要风险

- V1 `ManualScraperServiceImpl` 通过 `CacheService` 保存 session，V3 MVP 若使用进程内 Map，Pod 重启或多副本会丢失 session；建议 MVP 单副本并记录风险，后续切 Valkey。
- V1 `PersistenceStage` 返回 `IngestionStatus.INSERTED`，但实际只是成功投递 `q.ingest.video`，并未确认 DB insert/update。V3 SSE 文案需要避免误导。
- V1 `RawVideosPersistedEvent` 的后续链路是单体本地事件，拆分后必须显式改为 RabbitMQ 消息或独立 outbox。
- `source_domains` 目前属于 content 边界，但 V1 ingestion assembler 会创建该表记录；MVP 可以作为共享 PostgreSQL 迁移桥临时写入，后续应收敛到 content-service/internal API。
- `raw_videos` 的 `locked_fields`、status 字段和 hash 幂等语义必须保留；否则 metadata-worker、search-service 和后续 normalization/aggregation 会出现漂移。
- manual scrape 会对所有数据源循环抓取，缺少源站限流或 traffic key 会造成外部站点压力。
- 与主 agent 正在推进的 task-service 不应耦合；本轮不迁移 collection task scheduler。

## 验收建议

### scraper-service 文档验收

- task spec 明确 manual scrape API、SSE、读取表、投递队列、非目标。
- 明确 card 状态语义从“入库完成”降级为“投递完成/失败”或后续通过独立查询确认。
- 明确不接入 task-service 和 collection task lifecycle。

### ingestion-worker 文档验收

- task spec 明确 `q.ingest.video` 和 `q.process.playlist_sync` consumer。
- 明确写表：`raw_videos`、`playlists`、`episodes`、`source_domains`。
- 明确保留 `source_hash`/`data_hash` 幂等、status 初始化和 playlist stale data 检查。
- 明确 downstream publish：至少 `q.normalize.video`，搜索同步作为 MVP 可选项或后续子任务。

## 推荐接入顺序

1. 先接入 `ircs-ingestion-worker` 的 `q.ingest.video` consumer，用手工 RabbitMQ payload smoke 验证 `raw_videos` upsert 和 playlist sync。
2. 再接入 `ircs-scraper-service` manual scrape API，让它只负责抓取、映射和投递 `q.ingest.video`。
3. 在 ingestion smoke 中补充 `q.normalize.video` 下游消息验证，确认不会因拆分丢失 V1 本地事件副作用。
4. 最后再评估 raw search sync 是否在 ingestion MVP 中直接投递 `q.search.sync.raw`，或拆为 search outbox 后续任务。

## 本轮结论

- `scraper-service` MVP 是 HTTP/SSE + 外部抓取 + mapping + `q.ingest.video` producer，并已在 `ircs-dev` Ready 1/1。
- `ingestion-worker` MVP 是 `q.ingest.video`/`q.process.playlist_sync` consumer + raw video/playlist/source domain 写入 + normalization downstream publisher。
- 两者之间不应共享直接数据库写职责；`scraper-service` 不直接写内容表，`ingestion-worker` 不做外部 HTTP 抓取。
- `ingestion-worker` 与 `scraper-service` 均已完成 MVP 实现和 `ircs-dev` 部署；后续重点转向真实外部 data source smoke、Valkey session、auth 和 collection task lifecycle。
