# 016 aggregation-worker scheduler extraction

状态：已完成

完成日期：2026-06-04

上游来源：

- `docs/reports/019-aggregation-worker-boundary-analysis.md`
- `tasks/done/019-01-aggregation-worker-scheduler-mvp.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `docs/migrations/015-normalization-worker-normalize-extraction.md`

## 背景

`ircs-normalization-worker` 已经把 raw video 推进到
`normalization_status=READY`，但此前 `ircs-dev` 中没有 aggregation worker，
导致 raw video 无法继续创建或绑定 `unified_videos`。V1 aggregation 的真实形态
不是 RabbitMQ consumer，而是 `AggregationScheduler` 定时扫描满足条件的
`raw_videos`：

```text
aggregation_status = PENDING
normalization_status = READY
enrichment_status != PENDING
```

本轮按这个真实边界抽取 `ircs-aggregation-worker`，先完成 scheduler 扫描、
轻量匹配、统一视频创建/绑定和 search sync publish 的最小闭环。

## 实现范围

本轮已实现：

- `ircs-aggregation-worker` scheduler，默认在 dev 开启。
- 使用 `FOR UPDATE SKIP LOCKED` 小批量 claim `PENDING` raw video。
- 将已 claim 的 raw video 标记为 `PROCESSING`。
- 只处理满足以下条件的 raw video：
  - `normalization_status=READY`
  - `enrichment_status` 不为 `PENDING`
  - `aggregation_status=PENDING`
- 优先复用已有绑定关系。
- 未绑定时按外部 ID 复用已有 `unified_videos`：
  - `douban_id`
  - `tmdb_id`
  - `imdb_id`
  - `rotten_tomatoes_id`
- 未命中时新建 `unified_videos`。
- 从 raw video 汇总基础字段到 unified video：
  - title
  - alias title
  - description
  - year
  - area
  - raw language string
  - remarks
  - score
  - total episodes
  - duration
  - external IDs
- 写入 `raw_video_unified_video` 绑定。
- 将 raw video `aggregation_status` 推进到 `BOUND`。
- 发布 `q.search.sync.unified`，由 `ircs-search-service` 写入
  `ircs_unified_video`。
- 发布 `q.search.sync.raw`，让 raw 搜索文档刷新 unified 关联。
- 新增低资源 dev manifest：
  `deploy/k8s/dev/aggregation-worker-dev.yaml`。

## 非目标和延期范围

本轮未迁移：

- 完整 V1 图聚类算法。
- CQEngine 和 JGraphT 依赖。
- `DefaultWeightedMatchingStrategy` 的完整相似度评分。
- unified merge victim 删除。
- interaction migration。
- category、genre、area、language、person 关系表写入。
- cover image 归并。
- HTTP API。
- 生产切换或物理数据库拆分。

这些范围需要后续独立 task spec 承接，不能隐含在本 MVP 内。

## 文件变更

```text
services/ircs-aggregation-worker/build.gradle
services/ircs-aggregation-worker/src/main/resources/application.yaml
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/*
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/*
deploy/k8s/dev/aggregation-worker-dev.yaml
docs/reports/019-aggregation-worker-boundary-analysis.md
tasks/done/019-01-aggregation-worker-scheduler-mvp.md
```

## 队列和数据边界

消费：

```text
无 RabbitMQ aggregation queue
scheduler scans raw_videos
```

发布：

```text
x.search / search.sync.unified -> q.search.sync.unified
x.search / search.sync.raw     -> q.search.sync.raw
```

读取/写入：

```text
raw_videos
raw_video_unified_video
unified_videos
```

搜索副作用：

```text
ircs-search-service consumes q.search.sync.raw and q.search.sync.unified
Elasticsearch index: ircs_raw_video
Elasticsearch index: ircs_unified_video
```

## K8S 部署

镜像：

```text
harbor.mnnu.eu.org/ircs/ircs-aggregation-worker:dev
digest: sha256:6b397d462693101553323c826bc1e2c14d1a9a0a84e9b95206741cfd076fcf6d
platform: linux/arm64
```

资源：

```text
request: cpu 25m, memory 128Mi
limit:   cpu 250m, memory 512Mi
```

入口：

```text
ClusterIP only
No HTTPRoute
```

本服务 CPU limit 使用 `250m`，是因为 `ircs-dev` 在接入前只剩 `250m` CPU
limit 余量。接入后 namespace `limits.cpu` 已达到 `8/8`。

## 验证结果

本地验证：

```text
.\gradlew.bat :services:ircs-aggregation-worker:test
.\gradlew.bat build
kubectl apply --dry-run=server -f .\deploy\k8s\dev\aggregation-worker-dev.yaml
```

结果：

```text
aggregation-worker tests: BUILD SUCCESSFUL
full build: BUILD SUCCESSFUL
server dry-run: deployment/service created by server dry run
```

镜像与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-aggregation-worker' -ImageName ircs-aggregation-worker -Tag dev -Architecture arm64 -Os linux
kubectl apply -f .\deploy\k8s\dev\aggregation-worker-dev.yaml
kubectl rollout status deployment/ircs-aggregation-worker -n ircs-dev --timeout=240s
```

结果：

```text
Pushed harbor.mnnu.eu.org/ircs/ircs-aggregation-worker:dev for linux/arm64
deployment "ircs-aggregation-worker" successfully rolled out
imageID: harbor.mnnu.eu.org/ircs/ircs-aggregation-worker@sha256:6b397d462693101553323c826bc1e2c14d1a9a0a84e9b95206741cfd076fcf6d
```

Smoke：

```text
temporary raw video id: c3c7fb4e-9ac1-46fc-9b8f-11e6a8f3f101
temporary unified video id: 0501b04b-75a6-4d05-ace7-83c82c3b4a66
source_hash: codex-aggregation-smoke-20260604-0001
```

验证结果：

```text
raw_videos.aggregation_status: BOUND
raw_video_unified_video binding: created
unified_videos.title: Codex Aggregation Smoke
unified_videos.alias_title: Aggregation Alias
unified_videos.year: 2026
unified_videos.score: 8.9
unified_videos.douban_id: agg7654321
unified_videos.metadata_status: SYNCED
q.search.sync.unified consumers: 1
q.search.sync.raw consumers: 1
Elasticsearch ircs_unified_video found: true
Elasticsearch ircs_raw_video found: true
```

清理结果：

```text
PostgreSQL temporary raw video rows: 0
PostgreSQL temporary bindings: 0
PostgreSQL temporary unified video rows: 0
Elasticsearch temporary raw video document: found=false
Elasticsearch temporary unified video document: found=false
RabbitMQ all queues and DLQs: 0 messages
```

## 当前环境影响

`ircs-dev` 新增 1 个业务 Deployment 和 1 个 ClusterIP Service。当前业务服务
接入数从 11/18 提升到 12/18。

当前 quota 使用：

```text
pods:            17 / 20
services:        18 / 20
configmaps:      3 / 30
secrets:         4 / 30
requests.cpu:    650m / 4
requests.memory: 3200Mi / 8Gi
limits.cpu:      8 / 8
limits.memory:   9728Mi / 16Gi
```

`limits.cpu` 已无余量。后续接入 identity、scraper、portal、interaction、
magnet 或 ops 前，必须先下调既有 dev 服务 limit，或为下一服务使用更低 limit 并
同步确认 ResourceQuota。

## 后续建议

- 后续优先推进已经有 task spec 的服务：
  - `identity-service`
  - `scraper-service`
  - `portal-service`
  - `interaction-service`
  - `magnet-service`
- `ops-service` 仍需要先补边界分析和 task spec。
- aggregation 后续增强应拆为独立任务：
  - 完整图聚类和相似度评分。
  - 多源复杂合并。
  - relation table 聚合。
  - cover image 聚合。
  - stuck/FAILED 恢复策略强化。
