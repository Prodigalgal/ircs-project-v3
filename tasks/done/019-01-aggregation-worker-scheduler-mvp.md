# 019-01 aggregation-worker scheduler MVP 子任务

状态：已完成

完成日期：2026-06-04

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

上游来源：

- `docs/reports/019-aggregation-worker-boundary-analysis.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `docs/migrations/015-normalization-worker-normalize-extraction.md`
- `docs/migrations/016-aggregation-worker-scheduler-extraction.md`

## 背景

V1 aggregation 由 `AggregationScheduler` 扫描满足条件的 raw video，再由
`GraphAggregationServiceImpl` 进行候选匹配、创建/合并 unified video、绑定 raw
video 并发布 search sync。当前 V3 已完成 ingestion 和 normalization；本任务负责
把 raw video 从 `READY` 继续推进到 unified 绑定。

## 目标

- 在 `ircs-aggregation-worker` 接入低资源 scheduler。
- 扫描 `aggregation_status=PENDING`、`normalization_status=READY`、
  `enrichment_status != PENDING` 的 raw video。
- 小批量 claim 为 `PROCESSING`。
- 创建或复用 `unified_videos`。
- 写 `raw_video_unified_video` 绑定。
- 更新 raw video `aggregation_status=BOUND`。
- 发布 `q.search.sync.unified` 和 `q.search.sync.raw`。
- 部署到 `ircs-dev`，保持 ClusterIP only 和低资源 profile。

## 非目标

- 不迁移完整 V1 图聚类、CQEngine 或 JGraphT。
- 不做 unified merge victim 删除。
- 不迁移 interaction migration。
- 不写 category/genre/area/language/person 关系表。
- 不归并 cover image。
- 不暴露 HTTP API。
- 不新增 HTTPRoute。

## 输入

- V1 `AggregationScheduler`
- V1 `GraphAggregationServiceImpl`
- V1 `AggregationPipeline`
- V1 `BasicAttributeHandler`
- V1 `ExternalIdHandler`
- V1 `RawVideoMaintenanceRepository`
- V1 `UnifiedVideoRepository`
- V3 `QueueTopic.SEARCH_SYNC_RAW`
- V3 `QueueTopic.SEARCH_SYNC_UNIFIED`
- V3 live schema：`raw_videos`、`unified_videos`、
  `raw_video_unified_video`

## 输出

- `ircs-aggregation-worker` scheduler。
- raw/unified JDBC aggregation service。
- raw/unified search sync publisher。
- dev Deployment/Service manifest。
- 迁移文档、K8S 快照和任务状态回写。

## 影响范围

```text
services/ircs-aggregation-worker
deploy/k8s/dev/aggregation-worker-dev.yaml
docs/migrations/016-aggregation-worker-scheduler-extraction.md
docs/k8s/current-environment-2026-06-02.md
tasks/done/019-01-aggregation-worker-scheduler-mvp.md
```

## 风险

- MVP 简化匹配策略，不能完全替代 V1 图聚类。
- `ircs-dev` `limits.cpu` 接入后已达到 `8/8`，后续服务需要继续压低 limit。
- scheduler 若配置过大可能扫描历史数据；当前 dev 使用 batch size 1。
- search smoke 必须清理 ES 文档和临时 DB 行。

## 验收标准

- `ircs-aggregation-worker` 在 `ircs-dev` Ready 1/1。
- `ircs-dev` 无 HTTPRoute。
- 临时 raw video 被 scheduler 从 `PENDING` 推进到 `BOUND`。
- `raw_video_unified_video` 出现临时绑定。
- `unified_videos` 出现临时 unified row。
- `q.search.sync.unified` 和 `q.search.sync.raw` 被消费。
- ES `ircs_unified_video` 和 `ircs_raw_video` 出现临时文档。
- 临时 DB 行、绑定关系、ES 文档和相关队列/DLQ 清理为 0。

## 测试边界

```text
.\gradlew.bat :services:ircs-aggregation-worker:test
.\gradlew.bat build
kubectl apply --dry-run=server -f .\deploy\k8s\dev\aggregation-worker-dev.yaml
ARM64 Jib image push
kubectl rollout status deployment/ircs-aggregation-worker -n ircs-dev --timeout=240s
PostgreSQL raw_videos/unified_videos/raw_video_unified_video smoke
RabbitMQ q.search.sync.raw and q.search.sync.unified consume smoke
Elasticsearch ircs_raw_video and ircs_unified_video document smoke
临时数据清理检查
```

## 实现结果

已完成 scheduler 扫描、JDBC 聚合绑定、raw/unified search sync publish、低资源
dev manifest、ARM64 镜像推送和 `ircs-dev` rollout。

部署结果：

```text
deployment/ircs-aggregation-worker Ready 1/1
service/ircs-aggregation-worker ClusterIP 10.100.97.59
HTTPRoute: none
image digest: sha256:6b397d462693101553323c826bc1e2c14d1a9a0a84e9b95206741cfd076fcf6d
```

Smoke 结果：

```text
raw_videos.aggregation_status: BOUND
raw_video_unified_video binding: created
unified_videos row: created and populated
q.search.sync.raw messages after cleanup: 0
q.search.sync.raw.dlq messages after cleanup: 0
q.search.sync.unified messages after cleanup: 0
q.search.sync.unified.dlq messages after cleanup: 0
temporary PostgreSQL rows after cleanup: 0
temporary Elasticsearch documents after cleanup: found=false
```

## 当前状态

已完成。当前 `ircs-dev` 业务服务接入数为 12/18。
