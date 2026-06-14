# 057 normalization raw category data source category 02808 分析

状态：已完成

更新时间：2026-06-07

## 背景

`028-08` 已完成 actor/director person relation parity。剩余 deep mapping 中，
raw category / `data_source_category_id` 是最小可测切片：V1 在 normalization 阶段从
`raw_metadata.rawTypeId/rawTypeName/dataSourceId` 解析来源分类，
写入 `raw_category` 并把 raw video 绑定到对应 `data_source_category_id`。

## 上游来源

- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- `docs/reports/056-normalization-deep-mapping-person-relation-02808.md`
- `docs/migrations/056-normalization-deep-mapping-person-relation.md`
- `tasks/done/028-08-normalization-deep-mapping-parity.md`

## 当前证据

- V1 `CategoryGenreHandler` 在 `CATEGORY` 未 locked、`dataSourceId` 非空且 `rawTypeId`
  非空时调用 `StandardCategoryService.resolveRawCategory(...)`，再设置
  `video.dataSourceCategory`。
- V3 `raw_category` 当前 schema 使用 `data_source_id`、`source_code`、`source_name`、
  `category_id`，并通过 `(data_source_id, source_code)` 唯一约束去重。
- V3 `raw_videos` 已保留 `data_source_id` 和 `data_source_category_id`，但
  normalization worker 当前只更新基础字段、raw genre/language/area relation 和
  actor/director relation。
- V1 `dataSourceName` 只参与日志和可读上下文；V3 本轮不新增日志或冗余落库字段。

## 本轮目标

- 从 `raw_metadata.rawTypeId/rawTypeName/dataSourceId` 提取 raw category
  输入。
- 在 `dataSourceId` 缺失时回退使用 `raw_videos.data_source_id`。
- normalization 成功时 upsert `raw_category`，按 `(data_source_id, source_code)` 保持幂等。
- normalization 成功时更新 `raw_videos.data_source_category_id`。
- 保持 `normalization_status=READY` 和 `q.search.sync.raw` 发布行为不变。

## 非目标

- 不修改 provider、storage、audit、ops service。
- 不实现 standard genre/language/area/category mapping。
- 不实现 FAILED backoff scheduler、startup recovery、watchdog 或 live smoke。
- 不推断标准分类；本轮只保留既有 `category_id`，或在新 raw category 中置空。

## 影响范围

```text
services/ircs-normalization-worker/**
docs/reports/057-normalization-raw-category-data-source-category-02808.md
docs/migrations/058-normalization-raw-category-data-source-category.md
tasks/done/028-08-normalization-deep-mapping-parity.md
```

## 风险

- `raw_metadata` 可能只有 `rawTypeName` 而没有 `rawTypeId`；本轮按 V1 行为要求
  `rawTypeId` 非空，不猜测 source code。
- `dataSourceId` 可能非法；本轮应回退到 `raw_videos.data_source_id`，两者都缺失则跳过。
- 已有 raw category 的 `category_id` 可能来自人工映射；upsert 不应覆盖该字段。

## 验收标准

```text
MARK 1/4 normalization extracts raw category fields from raw_metadata
MARK 2/4 normalization falls back to raw_videos.data_source_id when metadata dataSourceId is absent
MARK 3/4 normalization upserts raw_category by data_source_id/source_code without clearing category_id
MARK 4/4 normalization updates raw_videos.data_source_category_id and preserves READY/search sync
```

## 测试边界

本地测试：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
```

本轮不做 `q.normalize.video` live smoke；queue/DLQ 和 ES residue 清理仍留给后续子任务。

## 完成结果

2026-06-07 已完成本轮 raw category / `data_source_category_id` parity：

```text
MARK 1/4 normalization extracts raw category fields from raw_metadata
MARK 2/4 normalization falls back to raw_videos.data_source_id when metadata dataSourceId is absent
MARK 3/4 normalization upserts raw_category by data_source_id/source_code without clearing category_id
MARK 4/4 normalization updates raw_videos.data_source_category_id and preserves READY/search sync
```

实现摘要：

- `RawVideoRecord` 读取 `raw_videos.data_source_id`，用于 metadata `dataSourceId` 缺失时 fallback。
- `RawVideoPatch` 新增 raw category 绑定字段：`rawCategoryDataSourceId`、
  `rawCategorySourceCode`、`rawCategorySourceName`。
- `RawVideoTextNormalizer` 从 `rawTypeId/rawTypeName/dataSourceId` 提取 raw category 输入；
  locked `category`、`dataSourceCategory` 或 `dataSourceCategoryId` 时跳过。
- `RawVideoNormalizationRepository.markReady` 在基础字段 READY 更新后 upsert
  `raw_category`，并更新 `raw_videos.data_source_category_id`。
- `raw_category` upsert 不写也不覆盖 `category_id`，保留已有人工或后续 standard mapping。

验证：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 8s
```

迁移记录：

- `docs/migrations/058-normalization-raw-category-data-source-category.md`

后续仍需独立切片：

- standard genre/language/area/category mapping。
- normalization FAILED backoff scheduler、startup recovery 和 watchdog。
- `q.normalize.video` live smoke、queue/DLQ cleanup、ES residue cleanup。
