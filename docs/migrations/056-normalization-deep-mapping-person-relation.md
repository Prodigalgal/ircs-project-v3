# 056 normalization deep mapping person relation

状态：已完成

日期：2026-06-07

关联：

- `docs/reports/056-normalization-deep-mapping-person-relation-02808.md`
- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- `docs/migrations/052-normalization-raw-relation-parity.md`
- `tasks/done/028-08-normalization-deep-mapping-parity.md`

## 背景

`ircs-normalization-worker` 已能清洗基础字段并写 raw genre/language/area relation。
本次迁移补齐 V1 normalization 中 person relation 的最小可测副作用，让 raw video 在
search projection 和 admin 查询中可获得 actor/director 关系。

## 行为复现

本次完成：

```text
raw_metadata.actorNames/actors/actor
  -> actors
  -> video_actors

raw_metadata.directorNames/directors/director
  -> directors
  -> video_directors
```

完成标记：

```text
MARK 1/4 normalization extracts actor/director relation values from raw_metadata
MARK 2/4 normalization upserts actors/directors by name
MARK 3/4 normalization replaces video_actors/video_directors join rows
MARK 4/4 normalization preserves READY status and raw search sync after person relation writes
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

- locked fields 中包含 `actors` 或 `directors` 时，不提取对应 person relation。
- 本次未提取到非空 actor/director 值时，不删除已有 join rows，避免误清人工修正。
- person 字典使用 `name` 唯一约束 upsert，不新增公共 HTTPRoute，不修改 K8S 清单。
- 本轮不实现 NER fallback、raw category、standard mapping、watchdog 或 FAILED backoff scheduler。

## 验证

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

## 后续

- raw category / `data_source_category_id` mapping。
- standard genre/language/area/category mapping。
- normalization FAILED backoff scheduler、startup recovery 和 watchdog。
- `q.normalize.video` live smoke、queue/DLQ cleanup、ES residue cleanup。
