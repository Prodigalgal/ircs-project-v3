# 028-05-G metadata pending gather parity 子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/052-metadata-pending-gather-parity-analysis.md`
- `docs/migrations/049-metadata-provider-fanout-parity.md`
- `docs/migrations/051-metadata-provider-worker-enrichment-parity.md`
- V1 `MetadataResultCollector`
- V3 `services/ircs-metadata-worker`

## 背景

V3 metadata worker 已经能 fan-out provider task，也能消费 Douban/TMDB/RT result。但 result
collector 当前没有等待 pending set 归零，导致多 provider 场景下第一条 result 就可能提前
finalize raw video 状态并发布 raw search sync。

## 目标

- 为 `MetadataPendingRegistry` 增加 provider complete/remaining 语义。
- result collector 在 pending provider 未归零时只保存 metadata，不 finalize、不发布 raw search sync。
- 最后一条 provider result 到达时 finalize raw video 状态、清理 pending key、发布 raw search sync。
- missing raw video result 清理 pending key，避免 Redis 残留。

## 非目标

- 不实现真实 Douban/RT provider。
- 不修改 K8S dev env。
- 不做 live queue smoke。
- 不实现 cover image、person、genre、area、language 字段深层合并。
- 不修改 storage-service 或 image download 逻辑。

## 输入

- `EnrichmentResultMsg.videoId`
- `EnrichmentResultMsg.providerType`
- Redis `enrich:pending:{videoId}`
- `raw_videos` enrichment 字段

## 输出

- `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataPendingRegistry.java`
- `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/result/MetadataResultCollectorService.java`
- `services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/**`
- 本报告、迁移记录和任务状态回写。

## 影响范围

包含：

```text
services/ircs-metadata-worker/**
docs/reports/052-metadata-pending-gather-parity-analysis.md
tasks/done/028-05-g-metadata-pending-gather-parity.md
```

不包含：

```text
services/ircs-storage-service/**
deploy/**
portal/admin UI
```

## 依赖

- `MetadataDispatchService` 已注册 pending set。
- provider worker 已发布 terminal `ENRICHMENT_RESULT`。
- `RawSearchSyncPublisher` 已可发布 raw search sync。

## 风险

- pending key 不存在时需要兼容单 result 旧路径，不能让 result 永久不结算。
- 首条 result 保存 metadata 但不 finalize 后，后续 provider 丢失仍需后续 watchdog/TTL 任务处理。
- Redis set remove/size 行为需要通过单元测试覆盖。

## 验收标准

```text
MARK 1/4 MetadataPendingRegistry removes finished provider and reports remaining count
MARK 2/4 first provider result with remaining pending does not finalize or publish raw search sync
MARK 3/4 final provider result settles raw_videos status, clears pending key and publishes raw search sync
MARK 4/4 missing raw video result clears pending key and does not publish raw search sync
```

## 测试边界

本地测试：

```text
.\gradlew.bat :services:ircs-metadata-worker:test --rerun-tasks
```

K8S smoke 后续单独执行：

```text
publish ENRICH_METADATA fixture
verify pending set register/complete/clear
verify metadata queues and DLQs cleanup
verify raw search sync only after final provider
```

## 实现结果

2026-06-07 已完成：

- `MetadataPendingRegistry.complete(videoId, providerType)` 删除已完成 provider 并返回 remaining。
- result collector 在 `remaining > 0` 时只保存 metadata，不 finalize、不发布 raw search sync。
- result collector 在 `remaining == 0` 时清理 pending key、finalize raw video 状态并发布 raw
  search sync。
- missing raw video result 会清理 pending key。

完成标记：

```text
MARK 1/4 MetadataPendingRegistry removes finished provider and reports remaining count
MARK 2/4 first provider result with remaining pending does not finalize or publish raw search sync
MARK 3/4 final provider result settles raw_videos status, clears pending key and publishes raw search sync
MARK 4/4 missing raw video result clears pending key and does not publish raw search sync
```

## 验证结果

```text
.\gradlew.bat :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

## 遗留问题

- 本轮未执行 K8S rollout 或 live queue smoke。
- 真实 Douban/RT provider、credential gating、公网失败分类和深层字段合并继续后置。
