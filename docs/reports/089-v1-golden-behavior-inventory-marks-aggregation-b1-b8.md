# 089 V1 golden behavior inventory - aggregation B1 MARK detail

来源：`089-v1-golden-behavior-inventory-marks.md` 拆分。

### 028-16-B1 aggregation matching MARK

`028-16-B1` 已完成 aggregation-worker 第一批 V1 matching golden fixtures，范围限制在
`services/ircs-aggregation-worker` 与 service-scoped `commons-text` 依赖。验证命令：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09 已完成 `028-16-B2 duplicate unified merge victim delete` 第一刀：root/victim
收敛、raw binding 迁移、favorite/history 冲突清理、victim scoped relation 删除、victim
unified delete 与 search DELETE side effect 已有本地测试固定。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09 已完成 `028-16-B3 unified relation union victim merge` 第一刀：victim
`actors/directors/genres/standard_languages/standard_areas` 在 victim delete 前 union 到 root，
并用 `on conflict do nothing` 固定去重。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

当时仍需后续切片承接 `BasicAttributeHandler`、`ExternalIdHandler`、`DynamicFieldHandler`、
`CoverImagePipelineHandler`、完整 JGraphT raw/raw cluster、metadata scoring 细节与 K8S
live smoke；后续 B6/B7 已分别推进 scalar 与 cover 选择规则，B9-B12 已推进 graph 与
metadata scoring 第一刀，当前未完成项仍按下文记录。

2026-06-09 已完成 `028-16-B4 victim scalar merge` 第一刀：duplicate victim 删除前，root
会在自身字段为空时从 victim 补 external IDs、description、score、published_at、alias_title、
total_episodes、duration、remarks、season、subtitle，并保留更新的 `last_trend_at`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09 已完成 `028-16-B5 raw-source pipeline category/relation rebuild` 第一刀：raw 绑定
unified 后，基于该 unified 全部 raw sources 做 category 投票，并重建 actors/directors/
genres/languages/areas relation；`locked_fields` 命中时保持 V1 语义，不清空、不覆盖。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09 已完成 `028-16-B6 raw-source pipeline scalar fields` 第一刀：raw 绑定 unified
后，基于该 unified 全部 raw sources 重建 `BasicAttributeHandler`、`ExternalIdHandler` 与
`DynamicFieldHandler` 的标量输出，包括 title/year 投票、score 最大值、published_at 首个非空、
external IDs 填空且不受锁保护、alias/description/subtitle 最长、remarks 最新以及
total_episodes/duration/season 首个非空。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09 已完成 `028-16-B7 raw-source pipeline cover image` 第一刀：raw 绑定 unified
后，基于当前 unified cover 与该 unified 全部 raw sources 重建 V1 `CoverImagePipelineHandler`
的核心选择规则：空 cover 采用第一个 raw cover，managed `LOCAL`/`R2` 优先于 `EXTERNAL`，
同类 managedness 只有 `file_size > current * 1.1` 才替换，`file_size` null 按 0 处理；
`coverImageUrl` locked field 不阻止该 handler。B7 本切片不调用 storage-service，不执行真实
R2 upload；V1 `promoteToR2()` side effect 后续已由 B8 拆到 storage command / due upload
scheduling。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

当时仍需拆分 B8/B9 承接 cover R2 promote side effect、handler order/failure isolation、
完整 JGraphT raw/raw cluster、metadata scoring 细节与 K8S live smoke；截至 B8-B14 第一刀，
cover R2 promote side effect、JGraphT raw/raw cluster、raw/unified context graph、category bucket
、metadata scoring/penalty 和 ES-backed context recall 均已推进，仍未标记完成的是
K8S live smoke。

2026-06-09 已完成 `028-16-B8 cover R2 promote side effect` 第一刀：新增
`PROMOTE_COVER_R2` storage command，aggregation-worker 在 cover pipeline 选中 `LOCAL` +
`LOCAL_STORED` cover 时发布 command；storage-service 消费后按 V1 `promoteToR2()` 语义
只安排 due upload，不直接执行真实 R2 upload。R2 inactive、非 eligible cover 或已存在
`next_retry_time` 时不修改 DB。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-aggregation-worker:test :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL
```

