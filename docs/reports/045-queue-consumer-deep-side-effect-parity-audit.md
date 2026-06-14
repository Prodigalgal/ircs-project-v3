# 045 queue consumer deep side-effect parity 审计

状态：已完成

更新时间：2026-06-07

## 背景

`025` 已关闭 HTTP Controller route missing，`028` 开始补行为级 parity。队列消费者是
V1 到 V3 行为差异最容易隐藏的部分：HTTP 接口返回成功不代表异步副作用已经被消费、
落库、索引或清理。当前需要把所有 `QueueTopic` 的 owner、消费者实现、已验证副作用和
剩余缺口统一列出，作为后续 task spec 的依据。

## 当前队列 ownership

| QueueTopic | Queue | Owner | 当前证据 | parity 状态 |
| --- | --- | --- | --- | --- |
| `INGEST_VIDEO` | `q.ingest.video` | `ircs-ingestion-worker` | `RawVideoIngestConsumer` 已存在，MVP smoke 已记录 | 部分完成，需复核 V1 深层字段 |
| `NORMALIZE_VIDEO` | `q.normalize.video` | `ircs-normalization-worker` | `028-05-F` 已完成 raw genre/language/area relation 写入，保留 raw search sync | 部分完成，actor/director、raw category、standard mapping 和 live smoke 后续单拆 |
| `ENRICH_METADATA` | `q.enrich.metadata` | `ircs-metadata-worker` | `028-05-C` 已完成 fan-out routing 单测，`028-05-G` 已完成 pending gather 最小切片 | 部分完成，dev live smoke 仍需后续 |
| `ENRICHMENT_RESULT` | `q.enrich.metadata.result` | `ircs-metadata-worker` | `028-05-E` 已补 terminal failure result 测试，`028-05-G` 已补 pending gather 收敛 | 部分完成，需复核图片/关联字段副作用和 live smoke |
| `PLAYLIST_SYNC` | `q.process.playlist_sync` | `ircs-ingestion-worker` | `PlaylistSyncConsumer` 已存在 | 部分完成，需复核 V1 playlist/episode 边界 |
| `FETCH_METADATA_DOUBAN` | `q.fetch.metadata.douban` | `ircs-metadata-worker` | `028-05-E` 已补 dev-safe listener，缺 provider 时发布 terminal result | 部分完成，真实 Douban provider 未实现 |
| `FETCH_METADATA_TMDB` | `q.fetch.metadata.tmdb` | `ircs-metadata-worker` | `MetadataProviderWorker` 已消费 TMDB | 部分完成，dev credentials/provider gating 需 smoke |
| `FETCH_METADATA_RT` | `q.fetch.metadata.rt` | `ircs-metadata-worker` | `028-05-E` 已补 dev-safe listener，缺 provider 时发布 terminal result | 部分完成，真实 RT provider 未实现 |
| `DOWNLOAD_IMAGE` | `q.storage.image` | `ircs-storage-service` | `028-05-H` 已完成本地 true download、安全校验、失败 retry/DEAD 测试和 live smoke cleanup | 已完成当前 dev-safe download 链路 |
| `SYNC_AVATAR` | `q.storage.avatar_sync` | `ircs-storage-service` | `028-05-B` 已完成 consumer、identity producer、R2 disabled smoke | 部分完成，真实 R2 live 和 watchdog 后续单拆 |
| `CMD_IMAGE_UNLINK` | `q.storage.image_unlink` | `ircs-content-service` | `028-05-D` 已完成 image unlink -> raw/unified search refresh live smoke | 已完成当前 cover unlink 链路 |
| `EVENT_IMAGE_UNLINKED` | `q.storage.image_delete` | `ircs-storage-service` | `ImageDeletionConsumer` 已存在 | 部分完成，已做物理删除 smoke |
| `SEND_MAIL` | `q.notification.mail` | `ircs-notification-worker` | `028-05-I` 已完成 disabled/failure path 单元测试 | 已完成当前 dev-safe disabled/failure 切片 |
| `WATCH_PROGRESS` | `q.interaction.watch_progress` | `ircs-interaction-service` | 028-05-A 已完成 consumer、DB upsert、latest wins smoke | 已完成第一条 interaction queue parity |
| `SEARCH_SYNC_RAW` | `q.search.sync.raw` | `ircs-search-service` | `028-05-D` 已完成 producer matrix，并 live smoke content image unlink raw refresh | 部分完成，normalization/metadata/aggregation 仍需按场景 smoke |
| `SEARCH_SYNC_UNIFIED` | `q.search.sync.unified` | `ircs-search-service` | `028-05-D` 已完成 producer matrix，并 live smoke content image unlink unified refresh | 部分完成，old unified dirty lifecycle 仍需复核 |

## 高优先级缺口

1. `q.interaction.watch_progress`
   - 已完成：`028-05-A interaction watch-progress consumer parity`。
   - 证据：`MARK 4/4`，RabbitMQ fixture 可写 `member_watch_histories`，旧消息晚到不会
     覆盖新进度，队列和 DLQ 清理为 0。

2. `q.storage.avatar_sync`
   - 已完成：`028-05-B storage avatar async sync consumer parity`。
   - 证据：storage consumer、identity producer、R2 disabled ack/no-op smoke 和 R2 active
     unit path 已闭环。
   - 剩余：真实 R2 live 和 `AvatarSyncScheduler` watchdog 后续单拆。

3. `q.fetch.metadata.douban` / `q.fetch.metadata.rt`
   - 已完成：`028-05-C metadata provider fan-out parity` 和
     `028-05-E metadata provider worker and enrichment result parity`、
     `028-05-G metadata pending gather parity`。
   - 证据：fan-out routing 覆盖三 provider；Douban/RT listener 能消费并在 provider 缺失时
     发布 terminal `ENRICHMENT_RESULT` failure；pending 未归零不 finalize、不发布 raw search sync，
     pending 归零才清 key、结算并发布 raw search sync。
   - 剩余：真实 Douban/RT provider、公网失败分类和 live ack/DLQ smoke 后续单拆。

4. `q.normalize.video` 深层关系写入
   - 已完成：`028-05-F normalization raw relation parity`。
   - 证据：raw genre/language/area 提取、raw relation upsert、join relation 替换和 raw search
     sync 保持已通过本地测试，记录 `MARK 4/4`。
   - 剩余：actor/director、raw category、standard mapping、live queue smoke、watchdog/backoff
     继续后续单拆。

5. `q.search.sync.raw/unified` producer 全覆盖
   - 已完成：`028-05-D raw/unified search producer matrix parity`。
   - 证据：producer matrix 覆盖 content、metadata、normalization、aggregation、ops runner；
     content image unlink 已通过 RabbitMQ/Elasticsearch live smoke 证明封面清空。
   - 剩余：normalization、metadata result、aggregation 等 producer 继续随各自业务切片做
     live smoke。

## 与并行子任务的边界

- `028-02` 已完成 avatar media/storage 本地可服务链路，远端 sync consumer 后续单拆。
- `028-03` 负责 magnet provider search，不在本审计里实现。
- `028-04` 已完成 ops maintenance runner 的 unified search sync producer，不在本审计里实现。
- `028-05-A/B/C/D/E` 已逐项补齐一批高优先级 queue parity；剩余项继续按新 task spec 拆分。

## 下一步建议

`028-05-A` 到 `028-05-I` 已全部完成当前 dev-safe 第一阶段。后续优先拆：

```text
028-06 real provider live parity
028-07 R2 live and watchdog parity
028-08 normalization deep mapping parity
028-09 search producer live smoke parity
028-10 production governor and credential pool parity
028-12 service audit rollout and worker job audit
```

验收应至少包含：

```text
V1 fixture 样本
DB 关系/字段副作用
RabbitMQ queue/DLQ cleanup
Search index refresh
ClusterIP/RabbitMQ smoke
```

## 后续切片边界补充

`028-05-G metadata pending gather parity` 已完成：

```text
MARK 1/4 MetadataPendingRegistry removes finished provider and reports remaining count
MARK 2/4 first provider result with remaining pending does not finalize or publish raw search sync
MARK 3/4 final provider result settles raw_videos status, clears pending key and publishes raw search sync
MARK 4/4 missing raw video result clears pending key and does not publish raw search sync
```

`028-05-H image download true download/failure parity` 最小边界：

```text
MARK 1/5 DOWNLOAD_IMAGE consumer consumes eligible EXTERNAL/UNPROCESSED fixture
MARK 2/5 true download stores local cover and finalizes LOCAL_STORED metadata
MARK 3/5 failed download records FAILED retry_count next_retry_time last_error
MARK 4/5 unsafe download is blocked without storing object, max retries reaches DEAD
MARK 5/5 live smoke cleanup leaves no DB/object residue, q.storage.image and DLQ empty
```

`028-05-I mail notification disabled/failure parity` 已完成：

```text
MARK 1/4 SEND_MAIL consumer calls mail service for queue message
MARK 2/4 mail disabled path skips JavaMailSender and returns without exception
MARK 3/4 template rendering failure is wrapped and propagated for retry/DLQ
MARK 4/4 SMTP send failure is wrapped and propagated for retry/DLQ
```

这些切片不包含真实 Douban/RT provider、TMDB 公网质量、cover image R2 pending upload、
normalization producer/watchdog、生产级 traffic governor、真实 SMTP credential pool 或全服务
service/worker audit 铺开；这些已拆入 `028-06` 到 `028-12` 的后续任务。
