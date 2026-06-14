# 052 metadata pending gather parity 分析

状态：已完成

更新时间：2026-06-07

## 背景

`028-05-C` 已完成 metadata provider fan-out routing，`028-05-E` 已补 Douban/RT provider
worker 的 dev-safe listener 和 terminal failure result。但当前 V3 result collector 仍存在一个
V1 scatter/gather 语义缺口：多 provider 被 fan-out 后，任意一条 `ENRICHMENT_RESULT` 到达
都会立即 finalize raw video 状态并发布 raw search sync。

V1 行为是按 `enrich:pending:{videoId}` 汇聚 provider result：每个 provider 完成后从 pending
set 移除，只有 pending set 归零时才 finalize、clear pending key 并发布搜索同步。

## 当前 V3 缺口

- `MetadataPendingRegistry` 只有 `register()` 和 `clear()`，没有完成某 provider 并返回 remaining
  count 的 API。
- `MetadataResultCollectorService.process()` 直接 `finalizeVideoStatus()` 和 `publishIndex()`，
  多 provider 场景会过早进入 `SUCCESS`、`FAILED` 或 `PERMANENT_FAILURE`。
- result 指向 missing raw video 时只返回，不清理 pending key。
- 当前 dev K8S provider env 默认关闭，本轮不做真实 provider live 调用。

## 本轮方案

最小 dev-safe pending gather 切片：

```text
MetadataPendingRegistry
  -> complete(videoId, providerType)
  -> SREM enrich:pending:{videoId} provider
  -> 返回 remaining count；pending key 不存在时返回 null

MetadataResultCollectorService
  -> missing raw video 时 clear pending key
  -> 成功 result 先应用 metadata 并保存
  -> remaining > 0 时不 finalize、不 publish raw search sync
  -> remaining == 0 或没有 pending key 时 finalize、clear pending key、publish raw search sync
```

## 非目标

- 不实现真实 Douban/RT provider。
- 不修改 TMDB 公网 credential/gating。
- 不实现 cover image download、person/genre/area/language 深层字段合并。
- 不新增 Pod、Service、HTTPRoute 或中间件。
- 不在本轮执行 K8S rollout。

## 风险

- 多 provider result 并发到达时，pending set 操作必须尽量以 Redis 原子 set remove/size 为准。
- pending key 缺失可能代表旧消息或单 provider 直接 result，本轮按无 pending key 立即 finalize，
  保持兼容。
- 首个 provider result 已应用 metadata 但不 finalize，若后续 provider 长时间不返回，需要依赖
  后续 watchdog/TTL 任务处理。

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

后续 live smoke 单独执行：

```text
publish ENRICH_METADATA fixture
verify pending set register/complete/clear
verify q.fetch.metadata.* and q.enrich.metadata.result cleanup
verify raw search sync only after final provider
```

## 完成结果

2026-06-07 已完成本轮 pending gather 最小切片：

```text
MARK 1/4 MetadataPendingRegistry removes finished provider and reports remaining count
MARK 2/4 first provider result with remaining pending does not finalize or publish raw search sync
MARK 3/4 final provider result settles raw_videos status, clears pending key and publishes raw search sync
MARK 4/4 missing raw video result clears pending key and does not publish raw search sync
```

实现摘要：

- `MetadataPendingRegistry.complete(videoId, providerType)` 使用 Redis set remove/size 记录 provider
  完成并返回剩余 pending count。
- `MetadataResultCollectorService` 在 pending 未归零时只保存已到达的 metadata，不 finalize
  `enrichment_status`，也不发布 raw search sync。
- pending 归零时清理 `enrich:pending:{videoId}`，再 finalize 并发布 raw search sync。
- missing raw video result 会清理 pending key，避免 Redis 残留。

验证：

```text
.\gradlew.bat :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

后续仍需独立切片：

- metadata live queue smoke，临时启用 provider fan-out 并验证 RabbitMQ/Valkey/ES 清理。
- 真实 Douban/RT provider、公网失败分类、credential gating。
- metadata result 深层字段合并，例如 cover image、people、genre、area、language。
