# 016-01 scraper-service manual scrape MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

上游来源：

- `docs/reports/016-scraper-ingestion-boundary-analysis.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`

关联任务：

- `tasks/done/016-02-ingestion-worker-video-ingest-mvp.md`

## 背景

V1 manual scrape 能力位于 `modules/scraper`，由 `ManualScraperController` 暴露 `/api/v1/scraper/manual/init` 和 `/api/v1/scraper/manual/stream/{sessionId}`。实际流程会读取 `data_sources`，执行 list/detail HTTP 抓取，用 `fieldMapping` 映射为 `ScrapedVideo`，再通过 `PersistenceStage` 调 `RawVideoIngestionService.upsertVideo` 投递 `q.ingest.video`。

V3 已有 `services/ircs-scraper-service` 骨架和 shared `ServiceRoute.SCRAPER`，但尚未接入 manual scrape 运行边界。

## 目标

- 在 `ircs-scraper-service` 接入 V1 兼容 manual scrape API：
  - `POST /api/v1/scraper/manual/init`
  - `GET /api/v1/scraper/manual/stream/{sessionId}`
- 支持 `ManualScrapeConfigDTO` 的关键词、页码、UA、proxy、headers 和 delay 输入。
- 读取共享 PostgreSQL `data_sources` 作为 MVP 迁移桥。
- 沿用 V1 list/detail URL 构建、list ID 抓取、detail HTTP fetch、JSON mapping 和 playlist parse。
- 将抓取结果 assemble 为 ingestion payload 并投递 `QueueTopic.INGEST_VIDEO`。
- SSE 输出 `LOG`、`CARD`、`DONE`、`ERROR`；MVP 中 card 状态必须表示“投递结果”，不得表示 DB 已完成 upsert。
- 保持 ClusterIP only 的后续实现边界，不新增公共 HTTPRoute。

## 非目标

- 不消费 `q.ingest.video`。
- 不写 `raw_videos`、`playlists`、`episodes`、`source_domains`。
- 不迁移 collection task 创建、启动、暂停、恢复、停止和 task log 统计。
- 不修改 `task-service` 或 `identity-service`。
- 不接入 trend sync、background search、metadata provider、metadata result collector。
- 不接入公网 HTTPRoute 或 edge cutover。

## 输入

- V1 `ManualScraperController`
- V1 `ManualScraperService` / `ManualScraperServiceImpl`
- V1 `ListScraperService` / `ListScraperServiceImpl`
- V1 `MappingService` / `MappingServiceImpl`
- V1 `ScraperPipelineExecutor`
- V1 `HttpFetchStage`、`MappingStage`、`PersistenceStage`
- V1 DTO/VO：
  - `ManualScrapeConfigDTO`
  - `ScrapeEvent`
  - `ScrapedVideoCardDTO`
  - `ScrapeListResult`
  - `ScrapedItemSummary`
  - `ScrapedVideo`
  - `ScrapedPlaylist`
  - `ScrapedEpisode`
- V1 `DataSource` / `DataSourceRepository`
- V3 shared `QueueTopic.INGEST_VIDEO`

## 输出

- 已在 `services/ircs-scraper-service` 形成 manual scrape API、SSE、scrape pipeline 和 ingest queue publisher。
- 明确 session 状态存储策略：MVP 可单副本进程内状态，推荐后续切 Valkey。
- 与 `tasks/done/016-02-ingestion-worker-video-ingest-mvp.md` 对齐 ingestion payload contract。

## 影响范围

```text
services/ircs-scraper-service
shared/ircs-contracts
shared/ircs-messaging
docs/migrations
docs/k8s
tasks
```

本轮实际写入范围：

```text
services/ircs-scraper-service
deploy/k8s/dev/scraper-service-dev.yaml
docs/migrations/019-scraper-service-manual-scrape-extraction.md
docs/k8s
tasks
```

## 依赖

- `ircs-ingestion-worker` 后续需要能消费 `q.ingest.video`。
- RabbitMQ exchange/queue 已由 shared `QueueTopic` 定义。
- 共享 PostgreSQL `data_sources` 表可读。
- HTTP client、traffic policy、JSONPath/ObjectMapper、playlist parser 能从 V1 迁移或以等价实现替代。
- Admin 鉴权路由后续需与 identity-service 或 edge auth 方案对齐；MVP smoke 可使用内部访问或本地测试绕开真实登录。

## 风险

- manual scrape session 若不用 Valkey，多副本和重启场景会丢 session。
- V1 pipeline 的 persistence 阶段实际只确认投递 queue，不确认 DB 落库；SSE 需要调整状态语义。
- 外部数据源 field mapping 差异大，迁移时需要保留 V1 容错和编码探测。
- 未接 task-service 时无法复用 collection task lifecycle、进度统计和 task log。
- proxy password、headers 等输入可能包含敏感信息，日志和 SSE 输出必须避免泄露。

## 验收标准

- task spec 明确 scraper-service 只负责抓取、映射和投递，不直接写内容表。
- 后续实现完成后：
  - `POST /api/v1/scraper/manual/init` 返回 `sessionId`。
  - `GET /api/v1/scraper/manual/stream/{sessionId}` 返回 SSE，并输出 `LOG`、`CARD`、`DONE` 或 `ERROR`。
  - 给定一个 dev 数据源和关键词时，服务能投递 `q.ingest.video`。
  - `q.ingest.video` payload 与 ingestion-worker task spec 对齐。
  - 无新增公共 HTTPRoute。

## 测试边界与验证结果

已完成验证：

```text
.\gradlew.bat :services:ircs-scraper-service:test
kubectl apply --dry-run=server -f deploy\k8s\dev\scraper-service-dev.yaml
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-scraper-service' -ImageName ircs-scraper-service -Tag dev -Architecture arm64 -Os linux
kubectl rollout status deployment/ircs-scraper-service -n ircs-dev --timeout=240s
ClusterIP manual scrape init API smoke
ClusterIP manual scrape SSE smoke
RabbitMQ q.ingest.video -> ingestion-worker -> raw_videos/playlists/episodes smoke
```

结果：通过。临时 `data_sources`、`raw_videos`、`playlists`、`episodes` 和
`source_domains` 已清理；RabbitMQ 队列和 DLQ 最终均为 0 消息。

## 关联文档

- `docs/reports/016-scraper-ingestion-boundary-analysis.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/migrations/019-scraper-service-manual-scrape-extraction.md`

## 当前状态

已完成。`ircs-scraper-service` 已在 `ircs-dev` Ready 1/1，保留
`/api/v1/scraper/manual/init` 和 `/api/v1/scraper/manual/stream/{sessionId}`，
只负责抓取、映射、SSE 和 `q.ingest.video` 投递，不直接写内容表。
