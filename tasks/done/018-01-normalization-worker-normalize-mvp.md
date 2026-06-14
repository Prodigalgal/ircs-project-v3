# 018-01 normalization-worker normalize queue MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

上游来源：

- `docs/reports/018-normalization-worker-boundary-analysis.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `docs/migrations/014-ingestion-worker-video-ingest-extraction.md`

迁移记录：

- `docs/migrations/015-normalization-worker-normalize-extraction.md`

## 背景

`ircs-ingestion-worker` 已在入库成功后发布 `q.normalize.video`，但
`ircs-dev` 原本没有 normalization consumer。V1 normalization 会消费 raw
video UUID，读取 `raw_metadata`，清洗基础字段，将状态推进到 `READY`，并触发
raw search sync。

## 目标

- 在 `ircs-normalization-worker` 接入 `q.normalize.video` consumer。
- 兼容 legacy UUID 字符串 payload。
- 读取 `raw_videos` 并执行轻量字段 normalization。
- 保留 `locked_fields` 保护语义。
- 成功后更新 `normalization_status=READY`。
- 失败后更新 `normalization_status=FAILED` 和 retry 信息。
- 成功后发布 `q.search.sync.raw`。
- 部署到 `ircs-dev`，保持 ClusterIP only 和低资源 profile。

## 非目标

- 不暴露 HTTP API。
- 不迁移完整 V1 normalization handler 体系。
- 不写 cover image、raw area、raw language、raw genre、category/person 关系表。
- 不实现 startup recovery、watchdog、FAILED backoff scheduler。
- 不消费 `q.enrich.metadata` 或 aggregation 相关队列。
- 不新增 HTTPRoute。

## 输入

- V1 `RawVideoNormalizationServiceImpl`
- V1 `NormalizationPipeline` / `NormalizationHandler`
- V1 `NormalizationContext`
- V1 title/base/year handlers
- V1 `NormalizationScheduler`
- V1 `RawVideoMaintenanceRepository`
- V3 `QueueTopic.NORMALIZE_VIDEO`
- V3 `QueueTopic.SEARCH_SYNC_RAW`
- V3 `raw_videos` live schema

## 输出

- `ircs-normalization-worker` queue consumer。
- raw video JDBC normalization service。
- raw search sync publisher。
- dev Deployment/Service manifest。
- 迁移文档、K8S 快照和任务状态回写。

## 影响范围

```text
services/ircs-normalization-worker
deploy/k8s/dev/normalization-worker-dev.yaml
docs/migrations
docs/k8s
tasks
```

## 实现结果

已实现 `q.normalize.video` consumer、legacy UUID payload parser、JDBC
normalization service、raw search sync publisher 和 dev manifest。

基础字段 normalization 已覆盖 title、alias、description、year、area、
language、remarks、score、episodes、duration 和外部 ID。`locked_fields` 支持
JSON array 和 object，smoke 中验证了 `title` 锁定后不被覆盖。

为降低 dev 镜像体积，本轮未引入 HanLP/OpenCC。完整简繁转换和更深层
normalization handler 作为后续增强任务处理。

## 风险

- 轻量 normalization 不能完全替代 V1 handler 体系，需要后续补齐。
- scheduler 未迁移时，FAILED backoff 只能记录，不能自动重试。
- 完整 category/genre/person/area/language 关系表写入仍待后续切片。

## 验收标准

- `q.normalize.video` 出现 1 个 consumer。
- 投递临时 raw video UUID 后，状态变为 `READY`。
- title/year/base fields 能从 `raw_metadata` 清洗更新。
- `locked_fields` 锁定字段不被覆盖。
- 成功后发布 raw search sync，ES 出现临时 raw video 文档。
- 临时 DB 行和 ES 文档清理为 0。
- `ircs-normalization-worker` 在 `ircs-dev` Ready 1/1 且无 HTTPRoute。

## 验证结果

```text
.\gradlew.bat :services:ircs-normalization-worker:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f .\deploy\k8s\dev\normalization-worker-dev.yaml
deployment.apps/ircs-normalization-worker created (server dry run)
service/ircs-normalization-worker created (server dry run)

.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-normalization-worker' -ImageName ircs-normalization-worker -Tag dev -Architecture arm64 -Os linux
Pushed harbor.mnnu.eu.org/ircs/ircs-normalization-worker:dev for linux/arm64

kubectl rollout status deployment/ircs-normalization-worker -n ircs-dev --timeout=240s
deployment "ircs-normalization-worker" successfully rolled out
```

Smoke 结果：

```text
temporary raw video id: b2d67282-9a0d-4a63-86d8-0cd0f0e25a10
q.normalize.video consumers: 1
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
PostgreSQL cleanup rows: 0
Elasticsearch cleanup found: false
```

## 测试边界

已覆盖：

- 单服务单元测试。
- 全仓库 Gradle build。
- K8S server dry-run。
- ARM64 Jib image push。
- K8S rollout。
- RabbitMQ consume/publish smoke。
- PostgreSQL field/status smoke。
- Elasticsearch raw document smoke。
- 临时 DB/ES/queue cleanup。

未覆盖：

- 完整 V1 handler pipeline。
- FAILED recovery scheduler/watchdog。
- category/genre/person 关系表写入。

## 当前状态

已完成。当前 `ircs-dev` 业务服务接入数为 11/18。
