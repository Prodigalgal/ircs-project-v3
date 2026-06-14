# 028-16-B7 raw-source pipeline cover image task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`

上游来源：

- `tasks/in-progress/028-16-b6-raw-source-pipeline-scalar-fields.md`
- V1 `CoverImagePipelineHandler`
- V1 `CoverImage`
- V1 `VideoFieldConstants.COVER_IMAGE_URL`
- V3 `JdbcAggregationRepository`

## 背景

B5/B6 已补 raw 绑定 unified 后的关系重建和标量字段聚合，但 V1
`AggregationPipeline` 还有 `order 60 CoverImagePipelineHandler`。该 handler 会遍历同一
unified 的全部 raw sources，按托管优先和文件大小阈值选择 unified cover。当前 V3
aggregation-worker 未基于全部 raw sources 重算 `unified_videos.cover_image_id`，portal/admin/search
看到的封面可能与 V1 聚合结果偏离。

## 目标

- 在 raw 绑定 unified 后，基于该 unified 的全部 raw sources 重建 cover image 第一刀。
- 复刻 V1 `CoverImagePipelineHandler.shouldReplace()`：
  - 当前 unified 没有封面时，采用第一个有封面的 raw source。
  - `storage_type != EXTERNAL` 视为 managed；managed source 优先覆盖 external current。
  - current 已 managed 时，external source 不覆盖 current。
  - 同为 managed 或同为 external 时，仅当 source `file_size` 大于 current `file_size * 1.1`
    才替换，避免频繁抖动。
  - `file_size` 为 null 时按 0 计算。
- 明确 V1 handler 本身未检查 `locked_fields`；本切片按 V1 当前实现，不因
  `coverImageUrl` lock 阻止 pipeline cover 优选。
- 保持 B2-B6 duplicate merge、scalar/relation rebuild 和 search side effect 不回退。

## 非目标

- 不在本切片调用 storage-service 或执行真实 R2 外呼。
- 不实现 V1 `coverImageService.promoteToR2(sourceImage.getId())` 的跨服务 side effect；
  该项后续拆为 storage command / event 切片。
- 不实现完整 JGraphT raw/raw cluster。
- 不实现 pipeline handler order/failure isolation 的框架化 runtime。
- 不执行 K8S live smoke。

## 输入

```text
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationService.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
```

## 输出

- repository cover pipeline primitive。
- service 调用点：raw bind 后执行 scalar、relation、cover rebuild，再 mark bound。
- H2-compatible repository fixture tests。
- service order test，保证 bind -> scalar -> relation -> cover -> markBound。
- docs/tasks/migration 回写。

## 影响范围

```text
services/ircs-aggregation-worker/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md
tasks/index.md
docs/index.md
```

## 风险

- V1 依赖 `context.sources()` 顺序；V3 用 raw `created_at asc, id asc` 固定顺序，属于
  dev-safe 稳定化。
- 不能把本切片误写成“选最大文件”，否则会丢掉 10% 阈值和顺序防抖语义。
- V1 handler 未检查 `coverImageUrl` lock；若 V3 擅自加锁保护会偏离当前 V1 行为。
- R2 promote 是真实跨服务副作用；本切片先不外呼，避免在 aggregation-worker 内引入
  storage-service 耦合。

## 验收标准

```text
B7 MARK 1/5 cover pipeline chooses first raw cover when unified cover is empty
B7 MARK 2/5 managed LOCAL/R2 cover replaces EXTERNAL cover even when smaller
B7 MARK 3/5 same managedness cover replaces only when file_size is greater than current * 1.1
B7 MARK 4/5 coverImageUrl locked_fields does not block V1 CoverImagePipelineHandler parity
B7 MARK 5/5 aggregation-worker/search-service tests pass and docs record remaining R2 promote/JGraphT gaps
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀。`AggregationService.bindRawVideo()` 现在在
`rebuildUnifiedPipelineScalars()` 与 `rebuildUnifiedPipelineRelations()` 后执行：

```text
rebuildUnifiedPipelineCoverImage(unifiedVideoId)
```

实现结果：

- `JdbcAggregationRepository.rebuildUnifiedPipelineCoverImage()` 先读取当前 unified cover，
  再按 raw `created_at asc, id asc` 稳定顺序遍历同一 unified 下所有 raw cover。
- `shouldReplaceCover()` 复刻 V1 `CoverImagePipelineHandler.shouldReplace()`：
  - 当前 cover 为空时，采用第一个 raw cover。
  - `LOCAL` / `R2` 等 managed cover 会覆盖 `EXTERNAL` current。
  - current managed 时，不被 external raw cover 降级。
  - 同为 managed 或同为 external 时，仅当 source `file_size` 大于 current `file_size * 1.1`
    才替换。
  - `file_size` 为 null 时按 0 计算。
- `coverImageUrl` 出现在 `locked_fields` 时仍不阻止 cover pipeline，贴合 V1 handler
  当前实际行为。
- 本切片不调用 storage-service，不执行真实 R2 upload，也不修改 `cover_images.next_retry_time`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

MARK：

```text
B7 MARK 1/5 cover pipeline chooses first raw cover when unified cover is empty: done 2026-06-09
B7 MARK 2/5 managed LOCAL/R2 cover replaces EXTERNAL cover even when smaller: done 2026-06-09
B7 MARK 3/5 same managedness cover replaces only when file_size is greater than current * 1.1: done 2026-06-09
B7 MARK 4/5 coverImageUrl locked_fields does not block V1 CoverImagePipelineHandler parity: done 2026-06-09
B7 MARK 5/5 aggregation-worker/search-service tests pass and docs record remaining R2 promote/JGraphT gaps: done 2026-06-09; R2 promote later moved to B8
```

仍未完成：

- V1 `coverImageService.promoteToR2(sourceImage.getId())` 的跨服务副作用已由后续
  `028-16-B8` 第一刀补到 storage command / due upload scheduling；本 B7 切片仍不直接执行
  storage-service 真实 R2 upload。
- `AggregationPipeline` handler order/failure isolation 的框架化 runtime。
- 完整 JGraphT raw/raw cluster、graph scoring 细节和 K8S live smoke。
