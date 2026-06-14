# 053 metadata pending gather parity

状态：已完成

日期：2026-06-07

关联：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/052-metadata-pending-gather-parity-analysis.md`
- `tasks/done/028-05-g-metadata-pending-gather-parity.md`
- `docs/migrations/049-metadata-provider-fanout-parity.md`
- `docs/migrations/051-metadata-provider-worker-enrichment-parity.md`

## 背景

metadata fan-out 和 provider worker listener 已迁移后，V3 仍缺 V1 scatter/gather 的 pending
收敛语义。没有 pending gather 时，多 provider 场景下第一条 result 会过早 finalize raw video
状态并发布 raw search sync。

## 行为复现

本次迁移完成：

```text
ENRICHMENT_RESULT provider result
  -> MetadataPendingRegistry.complete(videoId, providerType)
  -> remaining > 0: save metadata only, no finalize, no raw search sync
  -> remaining == 0: clear pending key, finalize status, publish raw search sync
  -> missing raw video: clear pending key, no raw search sync
```

完成标记：

```text
MARK 1/4 MetadataPendingRegistry removes finished provider and reports remaining count
MARK 2/4 first provider result with remaining pending does not finalize or publish raw search sync
MARK 3/4 final provider result settles raw_videos status, clears pending key and publishes raw search sync
MARK 4/4 missing raw video result clears pending key and does not publish raw search sync
```

## 实现范围

修改文件：

```text
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataPendingRegistry.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/result/MetadataResultCollectorService.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/dispatch/MetadataPendingRegistryTest.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/result/MetadataResultCollectorServiceTest.java
```

## 关键约束

- pending key 不存在时按 untracked 处理，兼容旧消息和单 provider 直接 result。
- pending 未归零时只保存 metadata，不修改最终 enrichment status，不发布 raw search sync。
- pending 归零后才清 key、finalize 和发布 raw search sync。
- 本轮不修改 K8S 清单，不新增 Pod、Service、HTTPRoute 或中间件。

## 验证

```text
.\gradlew.bat :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

## 后续

- live queue smoke 需要单独启用 provider fan-out fixture，验证 RabbitMQ、Valkey、ES 清理。
- 真实 Douban/RT provider、公网失败分类、credential gating 继续后置。
- metadata result 的 cover image、people、genre、area、language 深层字段合并继续后置。
