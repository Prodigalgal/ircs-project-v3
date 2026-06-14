# 028-05-F normalization raw relation parity 子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- `docs/migrations/015-normalization-worker-normalize-extraction.md`
- V1 `AreaLanguageHandler`
- V1 `CategoryGenreHandler`
- V3 `services/ircs-normalization-worker`

## 背景

当前 `ircs-normalization-worker` 已完成基础字段 MVP，但没有写 raw genre/language/area
关系。V1 normalization 会把这些 raw metadata 关系写入独立表和 join 表，供 admin raw
列表筛选、详情展示、aggregation candidate 和 search projection 使用。

## 目标

- 从 `raw_metadata.genreNames` 提取 raw genre 集合。
- 从 `raw_metadata.language/lang` 提取 raw language 集合。
- 从 `raw_metadata.area/region` 提取 raw area 集合。
- 在 normalization 成功时 upsert raw relation 表，并替换当前 raw video 的 join relations。
- 保持基础字段、locked fields、`READY` 状态和 raw search sync 既有行为。

## 非目标

- 不实现 actor/director relation。
- 不实现 raw category / data source category。
- 不实现 standard genre/language/area 映射。
- 不做 K8S rollout 或 smoke。
- 不修改 content-service、catalog-service 或 search-service。

## 输入

- `raw_videos.raw_metadata`
- `raw_videos.locked_fields`
- `raw_genres`、`raw_languages`、`raw_areas`
- `video_raw_genres`、`video_raw_languages`、`video_raw_areas`

## 输出

- `services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoPatch.java`
- `services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizer.java`
- `services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationRepository.java`
- `services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/**`
- 本报告、迁移记录和任务状态回写。

## 影响范围

包含：

```text
services/ircs-normalization-worker/**
docs/reports/051-normalization-raw-relation-parity-analysis.md
tasks/done/028-05-f-normalization-raw-relation-parity.md
```

不包含：

```text
deploy/**
services/ircs-content-service/**
services/ircs-catalog-service/**
services/ircs-search-service/**
portal/admin UI
```

## 依赖

- V3 schema 已有 raw relation 表和 join 表。
- `RawVideoTextNormalizer` 已解析基础字段和 locked fields。
- `RawVideoNormalizationRepository.markReady` 已在一个事务内更新基础字段。

## 风险

- raw metadata 字段可能为字符串、数组或混合格式；解析需要容错。
- relation 替换必须只在字段未 locked 且输入非空时执行，避免清空人工修正。
- 本轮不做 standard mapping，因此 aggregation 仍需后续增强。

## 验收标准

- `RawVideoPatch` 携带 raw genre/language/area 集合。
- `RawVideoTextNormalizer` 可从字符串或数组 metadata 提取去重 relation 值。
- `RawVideoNormalizationRepository.markReady` upsert raw relation 表并替换 join 表。
- 既有基础字段测试继续通过。
- `.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks` 通过。

## 测试边界

本地测试：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
```

K8S smoke 后续单独执行：

```text
publish NORMALIZE_VIDEO fixture
verify raw relation DB rows
verify search sync and queues cleanup
```

## 实现结果

2026-06-07 已完成：

- `RawVideoPatch` 携带 raw genre/language/area 集合。
- `RawVideoTextNormalizer` 可从字符串、数组和常见分隔符提取去重 relation 值。
- `RawVideoNormalizationRepository.markReady` 在基础字段 READY 更新后 upsert
  `raw_genres`、`raw_languages`、`raw_areas`，并替换
  `video_raw_genres`、`video_raw_languages`、`video_raw_areas`。
- locked 或空 relation 输入不会清空已有 join relations。

完成标记：

```text
MARK 1/4 normalization extracts genre/language/area relation values from raw_metadata
MARK 2/4 normalization upserts raw_genres/raw_languages/raw_areas
MARK 3/4 normalization replaces video_raw_genres/video_raw_languages/video_raw_areas
MARK 4/4 normalization preserves READY status and raw search sync after relation writes
```

## 验证结果

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

## 遗留问题

- actor/director relation、raw category、standard mapping 和 live queue smoke 继续后置。
- 本轮未触发 K8S rollout，避免在本地代码切片中占用 dev 配额。
