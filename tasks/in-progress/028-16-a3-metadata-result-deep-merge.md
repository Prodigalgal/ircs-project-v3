# 028-16-A3 metadata result deep merge task spec

状态：进行中（第一刀已完成）

父任务：

- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `MetadataResultCollector`
- V3 `MetadataResultCollectorService`

## 背景

V1 metadata result collector 会合并 area、language、person、genre 和 cover image 等富字段。
V3 当前 `MetadataResultCollectorService.applyMetadata` 主要处理外部 ID、description、score、
year、aliasTitle。结果是 provider enrichment 成功后，富元数据不一定进入 raw relation、
cover image 或 aggregation/search 后续链路。

## 目标

- 扩展 metadata result collector 对 `EnrichedMetadataDTO` 富字段的处理。
- 将 provider 返回的 area/language/genres/actors/directors/poster 写入后续 normalization 或
  raw metadata 可消费位置。
- 保持 pending registry、terminal status、raw search sync 和 aggregation pending 语义。

## 非目标

- 不执行真实 TMDB/Douban/RT 调用。
- 不在本切片实现 cover 图片下载，最多写入 URL 或触发既有后续任务。
- 不修改 aggregation-worker 匹配算法。

## 输入

```text
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/result/**
services/ircs-metadata-worker/src/test/**
shared/contracts/src/main/java/com/prodigalgal/ircs/contracts/metadata/**
```

## 输出

```text
metadata-worker deep merge implementation
metadata-worker tests
task/doc MARK update
```

## 影响范围

```text
services/ircs-metadata-worker/**
shared/contracts/** only if EnrichedMetadataDTO lacks required accessors
```

## 依赖

- 如果 DTO 已有字段，优先只改 metadata-worker。
- 如果 DTO 不足，需要最小化扩展 shared contract，并确认调用方兼容。

## 风险

- 直接写 raw relation 可能和 normalization-worker ownership 重叠；优先选择 raw metadata 或
  existing raw video fields，使 normalization 继续统一写关系。
- cover 下载可能引入 storage-service 副作用，本切片先不扩大。

## 验收标准

```text
MARK 1/5 done 2026-06-08 metadata result with genres/languages/areas/actors/directors is persisted into raw_metadata for normalization consumption
MARK 2/5 done 2026-06-08 poster/cover URL is preserved in raw_metadata without triggering unsafe download
MARK 3/5 done 2026-06-08 final provider completion still publishes raw search sync once
MARK 4/5 done 2026-06-08 metadata update marks aggregation_status PENDING
MARK 5/5 done 2026-06-08 metadata-worker tests cover pending and terminal paths
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-metadata-worker:test --rerun-tasks
```

## 第一刀实现结果

2026-06-08 已完成 metadata-worker 内的最小深层合并：

- `MetadataResultCollectorService` 将 `EnrichedMetadataDTO` 的 `area`、`language`、
  `genreNames`、`actorNames`、`directorNames`、`posterUrl`、`backdropUrl` 和
  `imageSource` 合并进 `raw_videos.raw_metadata`。
- 合并策略保留已有 raw metadata 字段；数组字段去重追加，`area` 和 `language` 以
  normalization 已支持的 top-level key 继续可消费。
- `posterUrl` 作为 provider URL 保留，同时补 `coverImageUrl`，本切片不下载图片、不写
  `cover_images`，避免引入 storage 副作用。
- pending registry 仍在剩余 provider 未完成时只保存 metadata，不发布 raw search sync；
  terminal path 仍发布一次 raw search sync。
- metadata 更新仍将 `aggregation_status` 置为 `PENDING`。

验证结果：

```text
.\gradlew.bat --no-daemon :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

遗留风险：

- 尚未做真实 TMDB/Douban/RT 队列 live smoke。
- 尚未把 provider poster URL 接入 cover image 下载或安全校验链路；该项仍应由后续
  storage/content 相关任务承接。
- 本切片不直接写 raw relation 表，关系表仍由 normalization-worker 统一消费
  `raw_metadata` 后生成。
