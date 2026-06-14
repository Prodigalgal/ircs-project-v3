# 014 ingestion-worker 视频入库队列抽取

状态：完成

更新时间：2026-06-03

## 背景

`ircs-ingestion-worker` 是 V3 目标 worker 之一，V1 边界位于
`ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/ingestion`。
V1 的核心入库链路是消费 `q.ingest.video`，按 `source_hash` 和
`data_hash` 执行 raw video 幂等写入，再触发 playlist sync 和
normalization。

本轮把该链路切到独立 worker，在 `ircs-dev` namespace 内使用共享
PostgreSQL 和 RabbitMQ 作为迁移桥。

## 本轮实现范围

已实现：

- 消费 `q.ingest.video`。
- 解析 `IngestionItem(IngestionVideoDTO video, boolean forceIngest)`。
- 按 `source_hash` / `data_hash` 保留幂等语义。
- 写入或更新 `raw_videos`，初始化：
  - `normalization_status=PENDING`
  - `enrichment_status=PENDING`
  - `aggregation_status=PENDING`
- 发布并消费 `q.process.playlist_sync`。
- 同步 `playlists`、`episodes`。
- 根据 episode URL 提取或复用 `source_domains`。
- stale playlist sync 检查：`PlaylistSyncMessage.dataHash` 必须匹配当前
  `raw_videos.data_hash`。
- 入库成功后发布 `q.normalize.video`。
- dev Deployment/Service，ClusterIP only。

未实现：

- scraper list/detail HTTP 抓取。
- manual scrape SSE。
- normalization pipeline，不消费 `q.normalize.video`。
- metadata enrichment。
- aggregation。
- raw search sync。
- content-service internal API 替代 `source_domains` 共享表写入。
- HTTPRoute。

## 代码范围

```text
services/ircs-ingestion-worker/build.gradle
services/ircs-ingestion-worker/src/main/resources/application.yaml
services/ircs-ingestion-worker/src/main/java/com/prodigalgal/ircs/ingestion/IrcsIngestionWorkerApplication.java
services/ircs-ingestion-worker/src/main/java/com/prodigalgal/ircs/ingestion/IngestionMessageParser.java
services/ircs-ingestion-worker/src/main/java/com/prodigalgal/ircs/ingestion/PlaylistSyncMessageParser.java
services/ircs-ingestion-worker/src/main/java/com/prodigalgal/ircs/ingestion/RawVideoIngestConsumer.java
services/ircs-ingestion-worker/src/main/java/com/prodigalgal/ircs/ingestion/PlaylistSyncConsumer.java
services/ircs-ingestion-worker/src/main/java/com/prodigalgal/ircs/ingestion/IngestionPublisher.java
services/ircs-ingestion-worker/src/main/java/com/prodigalgal/ircs/ingestion/RawVideoIngestionService.java
services/ircs-ingestion-worker/src/main/java/com/prodigalgal/ircs/ingestion/RawVideoState.java
services/ircs-ingestion-worker/src/main/java/com/prodigalgal/ircs/ingestion/RawVideoIngestionRepository.java
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/ingestion/IngestionItem.java
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/ingestion/IngestionVideoDTO.java
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/ingestion/IngestionPlaylistDTO.java
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/ingestion/IngestionEpisodeDTO.java
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/ingestion/PlaylistSyncMessage.java
services/ircs-ingestion-worker/src/test/java/com/prodigalgal/ircs/ingestion/IngestionMessageParserTest.java
services/ircs-ingestion-worker/src/test/java/com/prodigalgal/ircs/ingestion/RawVideoIngestionServiceTest.java
deploy/k8s/dev/ingestion-worker-dev.yaml
```

## 数据库边界

读取：

```text
raw_videos
data_sources
source_domains
```

写入：

```text
raw_videos
playlists
episodes
source_domains
```

当前 `source_domains` 写入是迁移桥。后续更稳妥的边界是通过
`content-service` internal API 或事件写入 content 归属表。

## K8S 部署

```text
namespace: ircs-dev
deployment: ircs-ingestion-worker
service: ircs-ingestion-worker
image: harbor.mnnu.eu.org/ircs/ircs-ingestion-worker:dev
image digest: sha256:c9293ffbd00a899f4c93f8dbf59bff5811aa43cc6fe213161c02d38ecea53797
arch: linux/arm64
service type: ClusterIP
HTTPRoute: none
resources: request 25m/128Mi, limit 500m/512Mi
strategy: Recreate
```

## 验证结果

本地验证：

```text
.\gradlew.bat :services:ircs-ingestion-worker:test -> BUILD SUCCESSFUL
.\gradlew.bat build -> BUILD SUCCESSFUL
kubectl apply --dry-run=server -f .\deploy\k8s\dev\ingestion-worker-dev.yaml -> server dry run passed
```

镜像与部署：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-ingestion-worker' -ImageName ircs-ingestion-worker -Tag dev -Architecture arm64 -Os linux
kubectl apply -f .\deploy\k8s\dev\ingestion-worker-dev.yaml
kubectl rollout status deployment/ircs-ingestion-worker -n ircs-dev --timeout=240s -> successfully rolled out
```

集群内 smoke：

```text
publish x.ingest/video.upsert -> routed=true
raw_videos by source_hash -> 1
normalization_status -> PENDING
enrichment_status -> PENDING
aggregation_status -> PENDING
playlists -> 1
episodes -> 2
source_domains -> https://video.example.invalid
q.normalize.video after first ingest -> 1
same source_hash/data_hash with forceIngest=false -> q.normalize.video remains 0
stale playlist sync with old dataHash -> playlist stays Main, episodes stays 2
cleanup -> remaining_temp_raw_videos=0, remaining_temp_data_sources=0, remaining_temp_source_domains=0
queues -> q.ingest.video=0, q.process.playlist_sync=0, q.normalize.video=0, DLQ=0
```

Smoke 使用的临时数据：

```text
data_sources.id: 2e4bb071-985e-43a6-9d92-f3ae34b45830
source_hash:     codex-ingest-source-hash-0001
data_hash:       codex-ingest-data-hash-0001
domain_value:    https://video.example.invalid
```

验证说明：

- 直接在 Windows 命令行使用新版 `rabbitmqadmin publish message -m` 传 JSON
  时，双引号会被命令参数处理破坏，曾产生 1 条无效 `q.ingest.video.dlq`。
- 该无效 DLQ 已清理；正式 smoke 使用 RabbitMQ management HTTP API 通过
  临时 `kubectl port-forward` 投递，验证链路通过。

## 当前资源结果

`ircs-dev` quota 在部署后为：

```text
pods:            15 / 20
services:        16 / 20
requests.cpu:    600m / 4
requests.memory: 2944Mi / 8Gi
limits.cpu:      7250m / 8
limits.memory:   8704Mi / 16Gi
```

## 遗留问题

- 本迁移完成时 `q.normalize.video` 尚无 normalization-worker 消费，本轮只验证
  publish；截至 `docs/migrations/015-normalization-worker-normalize-extraction.md`
  完成后，该队列已由 `ircs-normalization-worker` 消费。
- playlist sync 当前采用 full replace，未完整迁移 V1 更细粒度 diff。
- `source_domains` 仍由 ingestion-worker 直接写共享表，后续应收敛到
  content-service ownership。
- raw search sync 未在本轮触发，后续可在 normalization 或 content 切片中补齐。
