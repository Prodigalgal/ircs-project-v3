# 058 normalization raw category data source category

状态：已完成

日期：2026-06-07

关联：

- `docs/reports/057-normalization-raw-category-data-source-category-02808.md`
- `docs/reports/056-normalization-deep-mapping-person-relation-02808.md`
- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- `tasks/done/028-08-normalization-deep-mapping-parity.md`

## 背景

V1 normalization 会把来源分类从 `raw_metadata` 解析为 `raw_category`，并把 raw video 绑定到
`data_source_category_id`。V3 normalization worker 当前尚未执行这一副作用，导致 raw video
admin 查询、dashboard 分类统计和后续 aggregation 仍可能缺少来源分类。

## 行为复现

本次迁移复现：

```text
raw_metadata.rawTypeId/rawTypeName/dataSourceId
  -> raw_category(data_source_id, source_code, source_name)
  -> raw_videos.data_source_category_id
```

完成标记：

```text
MARK 1/4 normalization extracts raw category fields from raw_metadata
MARK 2/4 normalization falls back to raw_videos.data_source_id when metadata dataSourceId is absent
MARK 3/4 normalization upserts raw_category by data_source_id/source_code without clearing category_id
MARK 4/4 normalization updates raw_videos.data_source_category_id and preserves READY/search sync
```

## 实现范围

```text
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoRecord.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoPatch.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizer.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationRepository.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizerTest.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationServiceTest.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationRepositoryTest.java
```

## 关键约束

- locked fields 中包含 `category` 时，不提取 raw category，也不更新 `data_source_category_id`。
- `rawTypeId` 为空时跳过，不用 `rawTypeName` 生成 source code。
- `raw_category` upsert 仅更新 `source_name` 和 `updated_at`，不覆盖已有 `category_id`。
- 本轮不实现 standard mapping、watchdog、FAILED backoff scheduler 或 live smoke。

## 验证

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 8s
```

## 后续

- standard genre/language/area/category mapping。
- normalization FAILED backoff scheduler、startup recovery 和 watchdog。
- `q.normalize.video` live smoke、queue/DLQ cleanup、ES residue cleanup。
