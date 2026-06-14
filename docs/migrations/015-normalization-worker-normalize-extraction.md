# 015 normalization-worker normalize queue extraction

状态：已完成

完成日期：2026-06-03

上游来源：

- `docs/reports/018-normalization-worker-boundary-analysis.md`
- `tasks/done/018-01-normalization-worker-normalize-mvp.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `docs/migrations/014-ingestion-worker-video-ingest-extraction.md`

## 背景

`ircs-ingestion-worker` 已在视频入库成功后发布 `q.normalize.video`，但此前
`ircs-dev` 没有 consumer，导致新入库 raw video 停留在
`normalization_status=PENDING`。V1 的 normalization 逻辑位于
`modules/ingestion`，核心职责是消费 raw video UUID、读取 `raw_metadata`、
执行字段清洗、推进状态到 `READY`，并发布 raw search sync。

本轮将该队列边界抽取到 `ircs-normalization-worker`，先完成可独立验证的 MVP
字段清洗和状态链路。

## 实现范围

本轮已实现：

- `ircs-normalization-worker` 消费 `q.normalize.video`。
- 兼容 legacy UUID 字符串 payload 和 JSON 字符串 UUID payload。
- 读取 `raw_videos` 当前记录和 `raw_metadata`。
- `normalization_status=READY` 时幂等跳过。
- `raw_metadata` 缺失或无效时记录失败状态、retry count 和下一次 retry 时间。
- 轻量清洗基础字段：
  - `title`
  - `alias_title`
  - `description`
  - `year`
  - `area`
  - `raw_language_str`
  - `remarks`
  - `score`
  - `total_episodes`
  - `duration`
  - `douban_id`
  - `tmdb_id`
  - `imdb_id`
  - `rotten_tomatoes_id`
- 保留 `locked_fields` 保护语义，锁定字段不覆盖。
- 成功后更新 `normalization_status=READY`。
- 成功后发布 `q.search.sync.raw`，由 `ircs-search-service` 写入
  `ircs_raw_video`。
- 新增低资源 dev manifest：
  `deploy/k8s/dev/normalization-worker-dev.yaml`。

## 非目标和延期范围

本轮未迁移：

- 完整 V1 normalization handler pipeline。
- HanLP/OpenCC 简繁转换和更深层标题 handler。
- cover image 下载或 `cover_images` 写入。
- raw area/language/genre/category/person 关系表写入。
- startup recovery、watchdog、FAILED backoff scheduler。
- aggregation 队列或 enrichment 队列消费。
- 物理数据库拆分。

其中 HanLP/OpenCC 依赖曾导致 dev 镜像依赖层过大、Harbor push 中途断连。本轮为
减少 ARM64 dev 镜像体积，先采用轻量标准化；完整文本 handler 后续单独切片补齐。

## 文件变更

```text
services/ircs-normalization-worker/build.gradle
services/ircs-normalization-worker/src/main/resources/application.yaml
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/*
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/*
deploy/k8s/dev/normalization-worker-dev.yaml
docs/reports/018-normalization-worker-boundary-analysis.md
tasks/done/018-01-normalization-worker-normalize-mvp.md
```

## 队列和数据边界

消费：

```text
x.process / video.normalize -> q.normalize.video
```

发布：

```text
x.search / search.sync.raw -> q.search.sync.raw
```

读取/写入：

```text
raw_videos
```

搜索副作用：

```text
ircs-search-service consumes q.search.sync.raw
Elasticsearch index: ircs_raw_video
```

## K8S 部署

镜像：

```text
harbor.mnnu.eu.org/ircs/ircs-normalization-worker:dev
digest: sha256:229f8f9bd93bae31b99d6d61adefd12daa918e0755d3ea86c3a5e496fb4c6314
platform: linux/arm64
```

资源：

```text
request: cpu 25m, memory 128Mi
limit:   cpu 500m, memory 512Mi
```

入口：

```text
ClusterIP only
No HTTPRoute
```

## 验证结果

本地验证：

```text
.\gradlew.bat :services:ircs-normalization-worker:test
.\gradlew.bat build
kubectl apply --dry-run=server -f .\deploy\k8s\dev\normalization-worker-dev.yaml
```

结果：

```text
normalization-worker tests: BUILD SUCCESSFUL
full build: BUILD SUCCESSFUL
server dry-run: deployment/service created by server dry run
```

镜像与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-normalization-worker' -ImageName ircs-normalization-worker -Tag dev -Architecture arm64 -Os linux
kubectl apply -f .\deploy\k8s\dev\normalization-worker-dev.yaml
kubectl rollout status deployment/ircs-normalization-worker -n ircs-dev --timeout=240s
```

结果：

```text
Pushed harbor.mnnu.eu.org/ircs/ircs-normalization-worker:dev for linux/arm64
deployment "ircs-normalization-worker" successfully rolled out
imageID: harbor.mnnu.eu.org/ircs/ircs-normalization-worker@sha256:229f8f9bd93bae31b99d6d61adefd12daa918e0755d3ea86c3a5e496fb4c6314
```

Smoke：

```text
temporary raw video id: b2d67282-9a0d-4a63-86d8-0cd0f0e25a10
published payload: legacy UUID string
exchange/routing: x.process / video.normalize
```

验证结果：

```text
q.normalize.video consumers: 1
q.search.sync.raw consumers: 1
raw_videos.normalization_status: READY
raw_videos.title: Locked Manual Title
raw_videos.alias_title: Alt, Other
raw_videos.year: 2026
raw_videos.raw_language_str: zh-CN
raw_videos.score: 8.8
Elasticsearch ircs_raw_video found: true
Elasticsearch normalizationStatus: READY
q.normalize.video messages: 0
q.normalize.video.dlq messages: 0
q.search.sync.raw messages: 0
q.search.sync.raw.dlq messages: 0
```

清理结果：

```text
PostgreSQL temporary raw video rows: 0
Elasticsearch temporary raw video document: found=false
RabbitMQ related queues and DLQs: 0 messages
```

## 当前环境影响

`ircs-dev` 新增 1 个业务 Deployment 和 1 个 ClusterIP Service。当前业务服务
接入数从 10/18 提升到 11/18。

当前 quota 使用：

```text
pods:            16 / 20
services:        17 / 20
configmaps:      3 / 30
secrets:         4 / 30
requests.cpu:    625m / 4
requests.memory: 3Gi / 8Gi
limits.cpu:      7750m / 8
limits.memory:   9Gi / 16Gi
```

`limits.cpu` 只剩 `250m`，后续接入服务前需要继续压低已有服务 limit 或调整
dev quota/profile，否则会比 request 更早卡住。

## 后续建议

- 下一刀优先在 `aggregation-worker` 和 `ops-service` 之间选择；两者目前还没有
  对应清晰子任务。
- `identity-service`、`scraper-service`、`portal-service`、`interaction-service`
  和 `magnet-service` 已有 backlog task，可用 subagent 并行推进，但每个切片
  仍需独立 smoke。
- 单独补 `normalization-worker` 后续增强任务：
  - 完整 V1 handler pipeline。
  - 简繁转换和标题深度清洗。
  - category/genre/person/area/language 关系表写入。
  - FAILED recovery scheduler 和 watchdog。
