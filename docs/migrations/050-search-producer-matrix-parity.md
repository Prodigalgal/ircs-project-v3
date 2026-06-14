# 050 search producer matrix parity

状态：已完成

更新时间：2026-06-07

## 来源

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `tasks/done/028-05-d-search-producer-matrix-parity.md`

## 迁移目标

建立 `SEARCH_SYNC_RAW` / `SEARCH_SYNC_UNIFIED` producer matrix，并修复一个真实副作用缺口：
`CMD_IMAGE_UNLINK` 清空 raw/unified `cover_image_id` 后，Elasticsearch 搜索文档仍保留旧封面。

## 实现结果

- 建立 producer matrix，覆盖 content、metadata result、normalization、aggregation、ops runner。
- `CoverImageReferenceRepository` 增加按 image ID 查询受影响 raw/unified ID。
- `CoverImageUnlinkService` 在事务提交后发布 raw/unified `DELETE -> INDEX` search refresh。
- 保留 `EVENT_IMAGE_UNLINKED`，继续触发 storage 物理删除链路。
- 新增 `scripts/smoke/content-image-unlink-search-sync-02805d-smoke.ps1`。

## 验证

```text
.\gradlew.bat :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
```

K8S smoke：

```text
MARK 1/5 initial raw/unified search documents include coverImageUrl
MARK 2/5 CMD_IMAGE_UNLINK clears raw/unified cover_image_id in PostgreSQL
MARK 3/5 content image unlink publishes raw/unified search refresh and clears ES coverImageUrl
MARK 4/5 storage/search queues and DLQs are empty after unlink chain
MARK 5/5 smoke cleanup leaves no DB/ES fixture residue
```

## 剩余边界

- aggregation、normalization、metadata result 仍使用 legacy UUID payload；search-service consumer
  当前兼容该格式。
- old unified dirty lifecycle 仍需在后续端到端场景中验证刷新时机。
