# 016-02 ingestion-worker video ingest MVP 子任务

状态：完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

上游来源：

- `docs/reports/016-scraper-ingestion-boundary-analysis.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`

关联文档：

- `docs/migrations/014-ingestion-worker-video-ingest-extraction.md`

关联任务：

- `tasks/done/016-01-scraper-service-manual-scrape-mvp.md`

## 背景

V1 ingestion 能力位于 `modules/ingestion`，核心队列入口是
`BatchVideoPersister` 消费 `q.ingest.video`，调用
`RawVideoIngestionService.saveBatch` 批量写入 `raw_videos`。写入成功后，
V1 会投递 `q.process.playlist_sync` 并发布本地
`RawVideosPersistedEvent`，由同一单体内的 normalization/search 监听器
继续触发下游。

V3 已有 `services/ircs-ingestion-worker` 骨架和 shared `QueueTopic`。本轮
把 ingest consumer、playlist sync 和 normalization publish 抽成独立 worker。

## 目标

- 在 `ircs-ingestion-worker` 接入 `q.ingest.video` consumer。
- 解析 ingestion payload：`IngestionItem(IngestionVideoDTO video, boolean forceIngest)`。
- 保留 V1 `source_hash` / `data_hash` 幂等语义。
- 写入或更新 `raw_videos`，保留状态初始化。
- 投递并处理 `q.process.playlist_sync`，同步 `playlists`、`episodes`。
- 保留 playlist stale data 检查。
- 保留 `source_domains` extract-or-create 行为作为迁移桥。
- 入库成功后显式投递 `q.normalize.video`。

## 非目标

- 不暴露 HTTP API。
- 不执行 scraper list/detail HTTP 请求。
- 不接入 manual scrape SSE。
- 不实现 normalization pipeline，不消费 `q.normalize.video`。
- 不实现 metadata enrichment。
- 不实现 aggregation。
- 不接管 `search_sync_tasks` 表。
- 不修改 `task-service` 或 `identity-service`。
- 不新增 HTTPRoute。

## 输入

- V1 `RawVideoIngestionService` / `RawVideoIngestionServiceImpl`
- V1 `BatchVideoPersister`
- V1 `PlaylistSyncConsumer`
- V1 `ScrapedVideoAssembler`
- V1 `RawVideo`、`Playlist`、`Episode`、`SourceDomain`
- V1 DTO/VO：`IngestionItem`、`IngestionVideoDTO`、`IngestionPlaylistDTO`、
  `IngestionEpisodeDTO`、`PlaylistSyncMessage`
- V3 shared `QueueTopic.INGEST_VIDEO`
- V3 shared `QueueTopic.PLAYLIST_SYNC`
- V3 shared `QueueTopic.NORMALIZE_VIDEO`

## 输出

- `ircs-ingestion-worker` ingest queue consumer。
- raw video JDBC upsert。
- playlist sync consumer。
- downstream normalization publisher。
- `deploy/k8s/dev/ingestion-worker-dev.yaml`。
- 迁移文档和 K8S 快照更新。

## 影响范围

```text
services/ircs-ingestion-worker
shared/ircs-contracts
shared/ircs-messaging
deploy/k8s/dev/ingestion-worker-dev.yaml
docs/migrations
docs/k8s
tasks
```

## 风险

- V1 依赖单体本地 `RawVideosPersistedEvent` 触发 normalization/search；
  拆分后必须显式发队列。
- `source_domains` 逻辑横跨 content 边界，MVP 直接写共享表是迁移桥。
- playlist sync 使用 full replace，后续要评估是否恢复 V1 diff 行为。
- 如果同时运行 V1 单体和 V3 ingestion-worker，必须确保只消费
  `ircs-dev` RabbitMQ，避免重复写库。

## 验收标准

- 手工投递 `IngestionItem` 到 `q.ingest.video` 后，`raw_videos` 出现对应
  `source_hash`。
- hash unchanged 且 `forceIngest=false` 时不重复写入，也不重复发布 normalize。
- playlist payload 能同步到 `playlists` 和 `episodes`。
- stale `PlaylistSyncMessage` 被丢弃，不覆盖新数据。
- 入库成功后能投递 `q.normalize.video`。
- 临时 smoke 数据可清理，清理后 DB 和 queue 不残留测试行。
- `ircs-ingestion-worker` 在 `ircs-dev` Ready 1/1 且无 HTTPRoute。

## 测试边界

```text
.\gradlew.bat :services:ircs-ingestion-worker:test
.\gradlew.bat build
kubectl apply --dry-run=server -f .\deploy\k8s\dev\ingestion-worker-dev.yaml
ARM64 Jib image push
kubectl rollout status deployment/ircs-ingestion-worker -n ircs-dev --timeout=240s
RabbitMQ q.ingest.video consume smoke
PostgreSQL raw_videos upsert smoke
RabbitMQ q.process.playlist_sync consume smoke
PostgreSQL playlists/episodes sync smoke
RabbitMQ q.normalize.video publish smoke
临时数据清理检查
```

## 实现结果

- 已新增 shared ingestion contracts。
- 已实现 `RawVideoIngestConsumer`、`PlaylistSyncConsumer`、
  `RawVideoIngestionService`、`RawVideoIngestionRepository`。
- 已实现 `source_hash` / `data_hash` 幂等跳过。
- 已实现 playlist stale data 防护。
- 已实现 `q.normalize.video` publisher。
- 已新增 `deploy/k8s/dev/ingestion-worker-dev.yaml`，Service 为 ClusterIP。
- 已推送 `linux/arm64` 镜像。

## 验证结果

```text
.\gradlew.bat :services:ircs-ingestion-worker:test -> BUILD SUCCESSFUL
.\gradlew.bat build -> BUILD SUCCESSFUL
kubectl apply --dry-run=server -f .\deploy\k8s\dev\ingestion-worker-dev.yaml -> server dry run passed
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-ingestion-worker' -ImageName ircs-ingestion-worker -Tag dev -Architecture arm64 -Os linux -> pushed
kubectl apply -f .\deploy\k8s\dev\ingestion-worker-dev.yaml -> created
kubectl rollout status deployment/ircs-ingestion-worker -n ircs-dev --timeout=240s -> successfully rolled out
```

集群内 smoke：

```text
publish x.ingest/video.upsert -> routed=true
raw_videos -> 1
playlists -> 1
episodes -> 2
source_domains -> 1
q.normalize.video -> 1
idempotent republish -> q.normalize.video remains 0, raw_count remains 1
stale playlist sync -> playlist_count remains 1, episode_count remains 2
cleanup -> remaining_temp_raw_videos=0, remaining_temp_data_sources=0, remaining_temp_source_domains=0
queues -> q.ingest.video=0, q.process.playlist_sync=0, q.normalize.video=0, DLQ=0
```

部署结果：

```text
image digest: sha256:c9293ffbd00a899f4c93f8dbf59bff5811aa43cc6fe213161c02d38ecea53797
ircs-ingestion-worker Ready 1/1
HTTPRoute: none
quota: pods 15/20, services 16/20, requests.cpu 600m/4, requests.memory 2944Mi/8Gi
```

## 遗留问题

- 本任务完成时 `q.normalize.video` 后续需要由 `ircs-normalization-worker` 接管；
  截至 `tasks/done/018-01-normalization-worker-normalize-mvp.md` 完成后，该队列已
  接入 normalization-worker。
- raw search sync 未在本轮接入。
- `source_domains` 直接写共享表后续需要收敛到 content ownership。
