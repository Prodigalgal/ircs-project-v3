# 059 normalization standard mapping

状态：已完成

更新时间：2026-06-07

关联任务：

- `tasks/done/028-08-normalization-deep-mapping-parity.md`

关联报告：

- `docs/reports/058-normalization-standard-mapping-02808.md`

## 迁移目标

在 V3 normalization worker 中补齐保守的标准字典回填行为，使 raw relation 和 raw category
在可精确命中已有标准字典时自动绑定标准外键，同时保留人工映射优先级。

## 行为变化

- `raw_genres` upsert 时，如果 `standard_genre_id` 为空且 `source_value` 精确命中
  `standard_genre.name`，填充标准题材。
- `raw_languages` upsert 时，如果 `standard_language_id` 为空且 `source_value` 精确命中
  `standard_languages.name/code/english_name/native_name`，填充标准语言。
- `raw_areas` upsert 时，如果 `standard_area_id` 为空且 `source_value` 精确命中
  `standard_areas.name/code`，填充标准地区。
- `raw_category` upsert 时，如果 `category_id` 为空且 `source_name` 精确命中
  `standard_category.name` 或 `source_code` 精确命中 `standard_category.slug`，填充标准分类。

## 兼容性

- 不新增数据库结构。
- 不覆盖已有人工映射。
- 不新增 Kubernetes 资源或 HTTPRoute。
- 不修改 catalog/content/search/aggregation 服务代码。

## 验证边界

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
```

## 实施结果

已完成 repository 级标准映射回填：

```text
MARK 1/4 normalization maps raw_genres to existing standard_genre by exact name without overriding manual mapping
MARK 2/4 normalization maps raw_languages to existing standard_languages by exact name/code/native/english name without overriding manual mapping
MARK 3/4 normalization maps raw_areas to existing standard_areas by exact name/code without overriding manual mapping
MARK 4/4 normalization maps raw_category to existing standard_category by source_name/name or source_code/slug without overriding manual mapping
```

验证：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```
