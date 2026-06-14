# 052 normalization raw relation parity

状态：已完成

日期：2026-06-07

关联：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- `tasks/done/028-05-f-normalization-raw-relation-parity.md`
- `docs/migrations/015-normalization-worker-normalize-extraction.md`

## 背景

`ircs-normalization-worker` 的 MVP 已经能消费 `q.normalize.video`、清洗基础字段、推进
`normalization_status=READY` 并发布 raw search sync，但还没有复现 V1 normalization 中
raw genre、raw language、raw area 的关系表副作用。

这些关系被 admin raw video 查询、详情展示、aggregation candidate 和 search projection 使用。
接口壳存在时，如果关系表不写入，功能仍会在筛选、聚合和搜索字段上出现隐性缺失。

## 行为复现

本次迁移完成：

```text
raw_metadata.genreNames/genres/genre -> raw_genres + video_raw_genres
raw_metadata.language/lang          -> raw_languages + video_raw_languages
raw_metadata.area/region            -> raw_areas + video_raw_areas
```

完成标记：

```text
MARK 1/4 normalization extracts genre/language/area relation values from raw_metadata
MARK 2/4 normalization upserts raw_genres/raw_languages/raw_areas
MARK 3/4 normalization replaces video_raw_genres/video_raw_languages/video_raw_areas
MARK 4/4 normalization preserves READY status and raw search sync after relation writes
```

## 实现范围

修改文件：

```text
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoPatch.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizer.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationRepository.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizerTest.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationServiceTest.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationRepositoryTest.java
```

## 关键约束

- locked fields 中包含 `genres`、`language` 或 `area` 时，不提取对应 raw relation。
- 本次未提取到非空 relation 值时，不删除已有 join relations，避免误清人工修正。
- raw relation upsert 只写 `source_value`，不填标准映射列。
- 本轮不修改 K8S 清单，不新增 Pod、Service、HTTPRoute 或中间件。

## 验证

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

## 后续

- `028-05-G`：metadata pending gather 和 live queue smoke。
- `028-05-H`：image download true download/failure parity。
- `028-05-I`：mail notification disabled/failure parity。
- normalization 后续增强：actor/director relation、raw category、standard mapping、live queue smoke、
  watchdog 和 FAILED backoff scheduler。
