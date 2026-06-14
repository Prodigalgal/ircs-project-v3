# 056 normalization deep mapping person relation 02808 分析

状态：已完成

更新时间：2026-06-07

## 背景

`028-05-F` 已完成 `raw_metadata` 中 genre、language、area 三类 raw relation 写入，
但 V1 normalization 的深层映射仍缺 actor/director、raw category、standard mapping、
FAILED backoff scheduler、startup recovery、watchdog 和 live queue smoke。

本次只推进 `028-08` 的第一刀，聚焦最小可测的 person relation parity：

```text
raw_metadata.actorNames/directorNames
  -> actors/directors
  -> video_actors/video_directors
  -> raw search sync 保持发布
```

## 上游来源

- `docs/reports/018-normalization-worker-boundary-analysis.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- `docs/migrations/052-normalization-raw-relation-parity.md`
- `tasks/done/028-08-normalization-deep-mapping-parity.md`

## 当前证据

- `RawVideoTextNormalizer` 已有 relation value 抽取工具，当前只用于 genre/language/area。
- `RawVideoNormalizationRepository.markReady` 已在基础字段更新后替换 raw relation join rows。
- schema 中 `actors.name` 和 `directors.name` 有唯一约束，`video_actors` 与
  `video_directors` 使用 `(person_id, video_id)` 复合主键，适合做 upsert + replace。
- `ircs-search-service` raw search projection 已读取 `video_actors` 与 `video_directors`。

## 目标

- 从 `raw_metadata.actorNames/actors/actor` 提取 actor 名称集合。
- 从 `raw_metadata.directorNames/directors/director` 提取 director 名称集合。
- normalization 成功时 upsert `actors`、`directors`。
- normalization 成功时替换当前 raw video 的 `video_actors`、`video_directors`。
- 保持 `normalization_status=READY` 和 `q.search.sync.raw` 发布行为不变。

## 非目标

- 不实现 V1 `PersonHandler` 的 NER fallback。
- 不写 raw category / `data_source_category_id`。
- 不写 standard genre/language/area/category mapping。
- 不实现 FAILED backoff scheduler、startup recovery 或 watchdog。
- 不新增公共 HTTPRoute，不修改 K8S 清单。

## 影响范围

```text
services/ircs-normalization-worker/**
docs/reports/056-normalization-deep-mapping-person-relation-02808.md
docs/migrations/*normalization*deep*mapping*.md
tasks/done/028-08-normalization-deep-mapping-parity.md
```

## 风险

- actor/director 原始字段可能有多种别名，本轮只覆盖显式字段，不猜测简介 NER。
- 未提取到非空 person 值时，不删除已有 join rows，避免误清人工修正。
- person 字典 upsert 会写共享 PostgreSQL 表，后续物理拆库时仍需 ownership 拆分。

## 验收标准

```text
MARK 1/4 normalization extracts actor/director relation values from raw_metadata
MARK 2/4 normalization upserts actors/directors by name
MARK 3/4 normalization replaces video_actors/video_directors join rows
MARK 4/4 normalization preserves READY status and raw search sync after person relation writes
```

## 测试边界

本地测试：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
```

本轮不做 live queue smoke；`q.normalize.video` live smoke、DLQ cleanup、ES residue cleanup 保留给后续子任务。

## 完成结果

2026-06-07 已完成本轮 person relation parity：

```text
MARK 1/4 normalization extracts actor/director relation values from raw_metadata
MARK 2/4 normalization upserts actors/directors by name
MARK 3/4 normalization replaces video_actors/video_directors join rows
MARK 4/4 normalization preserves READY status and raw search sync after person relation writes
```

实现摘要：

- `RawVideoPatch` 新增 `actorValues`、`directorValues`。
- `RawVideoTextNormalizer` 从 `actorNames/actors/actor`、`directorNames/directors/director`
  提取字符串或数组 relation 值，并复用分隔符拆分、去重、去空白规则。
- `RawVideoNormalizationRepository.markReady` 在基础字段和 raw relation 更新后 upsert
  `actors/directors`，并替换 `video_actors/video_directors`。
- locked 或空 person relation 输入不触发 join 表删除，避免覆盖人工修正。

验证：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

迁移记录：

- `docs/migrations/056-normalization-deep-mapping-person-relation.md`

后续仍需独立切片：

- raw category / `data_source_category_id` mapping。
- standard genre/language/area/category mapping。
- normalization FAILED backoff scheduler、startup recovery 和 watchdog。
- `q.normalize.video` live smoke、queue/DLQ cleanup、ES residue cleanup。
