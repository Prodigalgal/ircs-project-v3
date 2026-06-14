# 028-16-A1 canonical rawMetadata contract task spec

状态：已完成（A1 第一刀，保留在 in-progress 原路径供父任务追踪）

父任务：

- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `ScrapedVideoAssembler`
- V3 `ScraperMappingService`

## 背景

V1 在 scraper/ingestion 入口主动组装 canonical `rawMetadata`，字段包含
`actorNames`、`directorNames`、`genreNames`、`rawTypeId`、`rawTypeName`、
`coverImageUrl`、`dataSourceId` 等。V3 当前 `ScraperMappingService.mapDetail` 更接近把
detail JSON 原样传入 `rawMetadata`，这会导致 `028-08` 的 normalization fixture 通过，但真实
scraper 数据未必具备 normalizer 读取的 canonical key。

## 目标

- 在 scraper-service detail/direct item 映射阶段生成 canonical `rawMetadata`。
- 保留原始 detail JSON，避免丢失 provider 原始响应。
- 使 ingestion -> normalization 可以稳定读取 genre/language/area/person/category/cover key。
- 增加 fixture test 证明真实映射后能产生 V3 normalizer 需要的 key。

## 非目标

- 不修改 normalization-worker 关系写入逻辑。
- 不新增真实外部 provider 网络调用。
- 不新增 HTTPRoute。

## 输入

```text
services/ircs-scraper-service/src/main/java/com/prodigalgal/ircs/scraper/ScraperMappingService.java
services/ircs-scraper-service/src/test/**
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizer.java
```

## 输出

```text
scraper-service canonical rawMetadata implementation
scraper-service unit/fixture tests
task/doc MARK update
```

## 影响范围

```text
services/ircs-scraper-service/**
```

## 依赖

- V3 `IngestionVideoDTO.rawMetadata` 仍使用 JSON 字符串。
- 后续 `028-16-A2/A3` 会消费 canonical key。

## 风险

- 覆盖原始 detail JSON 会影响排障，因此必须把原始 payload 放在独立字段。
- mapping 配置字段命名不统一，需要兼容 snake/camel/key alias。

## 验收标准

```text
MARK 1/4 mapDetail creates canonical rawMetadata with dataSourceId/sourceVid/title/base fields: done 2026-06-08
MARK 2/4 mapDetail preserves original provider JSON under sourcePayload or equivalent key: done 2026-06-08
MARK 3/4 directItem merges user-provided rawMetadata without dropping canonical fields: done 2026-06-08
MARK 4/4 scraper-service tests prove actor/director/genre/rawType/cover keys are emitted: done 2026-06-08
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --rerun-tasks
```

## 实现结果

- `ScraperMappingService.mapDetail` 现在在 detail 映射后生成 canonical `rawMetadata`，顶层包含 `dataSourceId`、`sourceVid`、`title`、`aliasTitle`、`description`、`coverImageUrl`、`year`、`area`、`language`、`rawTypeId`、`rawTypeName`、`genreNames`、`actorNames`、`directorNames` 等可由 mapping 推导的 key。
- 原始 provider detail JSON 保存在 `rawMetadata.sourcePayload`，用于排障和后续行为对照。
- `directItem` 现在会把用户提供的 `rawMetadata` 合并进 canonical JSON；用户自定义字段保留，item 基础字段会补齐 canonical key，rawType/genre/person 字段可从用户 rawMetadata 推导。
- mapping 读取补充了常用 camel/snake alias，例如 `sourceVid/source_vid`、`coverImageUrl/cover_image_url/cover`、`rawTypeId/raw_type_id/typeId/type_id`。

## 验证结果

```text
.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --rerun-tasks
BUILD SUCCESSFUL in 19s
```

## 遗留风险

- 本轮只做 scraper-service fixture，不做真实外部 provider 网络调用。
- `sourcePayload` 对 detail JSON 做完整保留，后续如果 provider payload 特别大，仍需由 `028-16-D` streaming/charset 切片评估体积和内存风险。
- direct item 请求契约没有新增独立 rawType/genre/person 字段；这些字段继续从用户传入的 `rawMetadata` 中读取。
