# 058 normalization standard mapping 02808 分析

状态：已完成

更新时间：2026-06-07

关联任务：

- `tasks/done/028-08-normalization-deep-mapping-parity.md`

上游来源：

- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- `docs/reports/056-normalization-deep-mapping-person-relation-02808.md`
- `docs/reports/057-normalization-raw-category-data-source-category-02808.md`

## 背景

`028-08` 已完成 person relation 和 raw category 两刀，但 V1 的 raw 字典解析还会在可命中时绑定
标准字典。V3 目前仅写入 raw 值和 join rows，`standard_*_id` 与 `category_id` 仍可能为空，
导致后续 aggregation、portal/admin 筛选和 dashboard 分类统计不够接近 V1。

## 本轮切片

本轮只做保守的本地精确匹配：

```text
raw_genres.source_value      -> standard_genre.name
raw_languages.source_value   -> standard_languages.name/code/english_name/native_name
raw_areas.source_value       -> standard_areas.name/code
raw_category.source_name     -> standard_category.name
raw_category.source_code     -> standard_category.slug
```

已有人工映射优先，upsert 时只在当前标准外键为空时填充命中结果。

## 非目标

- 不引入 LLM cleaning、HanLP、缓存推断或策略映射。
- 不做 fuzzy matching。
- 不实现 FAILED backoff scheduler、startup recovery、watchdog 或 live queue smoke。
- 不修改 catalog/content/search/aggregation 服务代码。
- 不执行 K8S rollout。

## MARK 设计

```text
MARK 1/4 normalization maps raw_genres to existing standard_genre by exact name without overriding manual mapping
MARK 2/4 normalization maps raw_languages to existing standard_languages by exact name/code/native/english name without overriding manual mapping
MARK 3/4 normalization maps raw_areas to existing standard_areas by exact name/code without overriding manual mapping
MARK 4/4 normalization maps raw_category to existing standard_category by source_name/name or source_code/slug without overriding manual mapping
```

## 测试边界

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
```

本轮只要求本地 repository 单元测试，不做 live queue smoke。

## 完成结果

2026-06-07 已完成 standard mapping fill parity：

```text
MARK 1/4 normalization maps raw_genres to existing standard_genre by exact name without overriding manual mapping
MARK 2/4 normalization maps raw_languages to existing standard_languages by exact name/code/native/english name without overriding manual mapping
MARK 3/4 normalization maps raw_areas to existing standard_areas by exact name/code without overriding manual mapping
MARK 4/4 normalization maps raw_category to existing standard_category by source_name/name or source_code/slug without overriding manual mapping
```

实现摘要：

- `raw_genres` upsert 新增 `standard_genre_id` 精确命中填充，并通过
  `coalesce(raw_genres.standard_genre_id, excluded.standard_genre_id)` 保留人工映射。
- `raw_languages` upsert 新增 `standard_language_id` 精确命中填充，匹配
  `name/code/english_name/native_name`，并保留人工映射。
- `raw_areas` upsert 新增 `standard_area_id` 精确命中填充，匹配 `name/code`，并保留人工映射。
- `raw_category` upsert 新增 `category_id` 精确命中填充，匹配 `standard_category.name/slug`，
  并保留人工映射。

验证：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

后续仍需独立切片：

- normalization FAILED backoff scheduler、startup recovery 和 watchdog。
- `q.normalize.video` live smoke、queue/DLQ cleanup、ES residue cleanup。
