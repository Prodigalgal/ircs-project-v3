# 049 search producer matrix parity 分析报告

状态：已完成（producer matrix、content image unlink 修复、ARM64 rollout 与 ES/RabbitMQ smoke 已闭环）

更新时间：2026-06-07

## 背景

`028-05` 队列消费者深层副作用审计已经确认 `SEARCH_SYNC_RAW` 和
`SEARCH_SYNC_UNIFIED` 的 consumer 存在，但 producer 覆盖仍需要独立矩阵。仅确认
search-service 能消费消息不足以证明行为 parity；还需要逐项确认 content、metadata
result、normalization、aggregation、ops runner 等写操作是否在更新 raw/unified
搜索读模型相关数据后发布正确的 search sync 消息。

## 上游来源

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/044-ops-maintenance-runner-parity-analysis.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`
- `shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/queue/QueueTopic.java`

## 审计范围

包含：

```text
services/ircs-content-service/**
services/ircs-normalization-worker/**
services/ircs-metadata-worker/**
services/ircs-aggregation-worker/**
services/ircs-ops-service/**
shared/ircs-contracts/**
```

不包含：

```text
services/ircs-search-service/** consumer 修改
deploy/**
scripts/**
docs/index.md
tasks/index.md
docs/k8s/current-environment-2026-06-02.md
K8S rollout
```

## 当前契约

| QueueTopic | Queue | Routing key | EntityType | 预期 operation |
| --- | --- | --- | --- | --- |
| `SEARCH_SYNC_RAW` | `q.search.sync.raw` | `search.sync.raw` | `RAW_VIDEO` | `INDEX` / `DELETE` |
| `SEARCH_SYNC_UNIFIED` | `q.search.sync.unified` | `search.sync.unified` | `UNIFIED_VIDEO` | `INDEX` / `DELETE` |

`SearchSyncMessage` 当前结构为：

```text
entityId
entityType
operation
```

## Producer Matrix

| Producer 来源 | 写操作 / 触发点 | RAW 发布 | UNIFIED 发布 | 当前证据 | 结论 |
| --- | --- | --- | --- | --- | --- |
| content raw admin | raw create | `INDEX` | 不涉及 | `RawVideoAdminService.create()` 调用 `publishRawSearch(id, INDEX)` | 已覆盖 |
| content raw admin | raw update | `INDEX` | 新绑定 unified 时 `INDEX` | `RawVideoAdminService.update()`；critical change 会解绑 raw 并 mark old unified dirty | 已覆盖，old unified 依赖后续 aggregation/recalculate 刷新 |
| content raw admin | raw batch delete | `DELETE` | 原绑定 unified `INDEX` | `RawVideoAdminService.batchDelete()` 先取绑定、删除 raw、发布 raw delete 和 unified index | 已覆盖 |
| content raw admin | raw batch sync-search | `INDEX` | 不涉及 | `RawVideoAdminService.batchSyncSearch()` | 已覆盖 |
| content raw admin | re-normalize / re-enrich | 不直接发布 search；转发 `NORMALIZE_VIDEO` / `ENRICH_METADATA` | 不涉及 | `RawVideoAdminService.reNormalize()`、`reEnrich()` | 已覆盖，后续 worker 负责 RAW `INDEX` |
| content unified admin | unified create | 绑定 raw `INDEX` | `INDEX` | `UnifiedVideoAdminService.create()` | 已覆盖 |
| content unified admin | unified update | bind/unbind raw `INDEX` | `INDEX` | `UnifiedVideoAdminService.update()` | 已覆盖 |
| content unified admin | unified batch delete | 被解绑 raw `INDEX` | `DELETE` | `UnifiedVideoAdminService.batchDelete()` | 已覆盖 |
| content unified admin | unified batch sync-search | 不涉及 | `INDEX` | `UnifiedVideoAdminService.batchSyncSearch()` | 已覆盖 |
| content unified admin | unified recalculate metadata | 来源 raw `INDEX` | `INDEX` | `UnifiedVideoAdminService.recalculateMetadata()` | 已覆盖 |
| content image unlink | `CMD_IMAGE_UNLINK` 清空 raw/unified `cover_image_id` | 已补 `DELETE -> INDEX` refresh | 已补 `DELETE -> INDEX` refresh | `CoverImageUnlinkService`、`CoverImageReferenceRepository` | 本切片修复并 smoke |
| metadata dispatch | 无 provider 可用时立即完成 status 写入 | `INDEX` | 不涉及 | `MetadataDispatchService.finalizeImmediate()` 后 `RawSearchSyncPublisher.publishIndex()` | 已覆盖 |
| metadata result | enrichment result 落库、更新 raw metadata/status | `INDEX` | 不涉及 | `MetadataResultCollectorService.process()` 保存后发布 raw index；missing video 不发布 | 已覆盖 |
| normalization | normalize raw video 成功写入 normalized fields | `INDEX` | 不涉及 | `RawVideoNormalizationService.normalize()` 成功 `markReady()` 后发布 raw index；失败/缺失不发布 | 已覆盖 |
| aggregation | scheduler batch 绑定 raw 到 unified、upsert unified fields | legacy UUID `INDEX` | legacy UUID `INDEX` | `AggregationService.aggregateBatch()` 对 `BOUND` 结果发布 raw/unified index | 已覆盖 |
| aggregation | internal unified recalculate | 每个 raw legacy UUID `INDEX` | legacy UUID `INDEX` | `AggregationService.recalculateUnified()` | 已覆盖 |
| aggregation | ineligible / missing raw | 不发布 | 不发布 | `AggregationServiceTest.skipsIneligibleRawVideoAndDoesNotPublishSearchSync()` | 已覆盖 |
| ops runner | dev-safe unified reindex runner | 不涉及 | 结构化 `INDEX` | `MaintenanceReindexCommand.enqueueUnifiedIndex()`；`028-04` 已完成测试和 smoke | 已覆盖 |

## 本切片修复

发现的低风险缺口：

```text
q.storage.image_unlink
  -> CoverImageUnlinkService
  -> raw_videos.cover_image_id = null
  -> unified_videos.cover_image_id = null
  -> 旧行为只发布 image.event.unlinked
```

search-service 的 raw/unified 搜索文档会从 `cover_images` 解析 `coverImageUrl`、
`coverStorageType`、`coverStatus`。因此清空 `cover_image_id` 后如果不发布 search sync，
Elasticsearch 中已存在的 raw/unified 文档可能继续保留旧封面。

本轮最小改动：

```text
CoverImageReferenceRepository.findRawVideoIds(imageId)
CoverImageReferenceRepository.findUnifiedVideoIds(imageId)
CoverImageUnlinkService.unlink(imageId)
  -> unlink 前读取受影响 raw/unified ID
  -> unlink DB reference
  -> transaction commit 后对 raw IDs 发布 SEARCH_SYNC_RAW DELETE 再 INDEX
  -> transaction commit 后对 unified IDs 发布 SEARCH_SYNC_UNIFIED DELETE 再 INDEX
  -> 保留 image.event.unlinked 发布
```

最终选择 `DELETE -> INDEX` refresh 的原因：Elasticsearch 更新链路对 null 字段保留旧值存在
风险；先删除旧文档再按数据库当前状态重建，能证明 `coverImageUrl` 被清空。

## 验证结果

测试命令：

```text
.\gradlew.bat :services:ircs-content-service:test --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL
```

新增/更新覆盖：

- `CoverImageReferenceRepositoryTest` 验证按 image ID 查 raw/unified 受影响 ID。
- `CoverImageUnlinkServiceTest` 验证 unlink 后发布 raw/unified search `DELETE -> INDEX`、
  `image.event.unlinked`，并验证事务激活时在 `afterCommit` 后才发布 search refresh。
- 既有 content raw/unified admin producer 测试继续通过。

K8S smoke：

```text
.\scripts\smoke\content-image-unlink-search-sync-02805d-smoke.ps1

MARK 1/5 initial raw/unified search documents include coverImageUrl
MARK 2/5 CMD_IMAGE_UNLINK clears raw/unified cover_image_id in PostgreSQL
MARK 3/5 content image unlink publishes raw/unified search refresh and clears ES coverImageUrl
MARK 4/5 storage/search queues and DLQs are empty after unlink chain
MARK 5/5 smoke cleanup leaves no DB/ES fixture residue
```

完成标记：

```text
MARK 1/2 search producer matrix built and content image unlink cover search sync gap fixed
MARK 2/2 content image unlink search refresh clears ES coverImageUrl in live smoke
```

## 剩余风险

- aggregation、normalization、metadata result 目前仍使用 legacy UUID payload 发布 raw
  `INDEX`，依赖 search-service legacy parser。consumer 已支持该格式，本切片不改
  search-service。
- `RawVideoAdminService.update()` 对 old unified 只 mark dirty，不直接发布 old unified
  `INDEX`；这与当前 aggregation/recalculate 设计相关，建议后续在 unified dirty
  lifecycle 中用 live smoke 再确认是否需要更强刷新。
- 已执行 content-service ARM64 rollout 和 RabbitMQ/Elasticsearch live smoke；其他 producer
  仍需在各自行为切片中继续做端到端 smoke。

## 验收标准

- Producer Matrix 覆盖 content、metadata result、normalization、aggregation、ops runner。
- 每个 producer 明确 RAW/UNIFIED 是否发布、消息 operation、证据文件和缺口状态。
- 已修复一个清晰低风险缺口：content image unlink 清空 cover reference 后补发 raw/unified
  search refresh。
- 未修改 search-service consumer，未新增 HTTPRoute、Pod、Service 或中间件。
