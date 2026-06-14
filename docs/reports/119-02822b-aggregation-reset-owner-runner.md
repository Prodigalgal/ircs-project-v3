# 119 028-22-B aggregation reset owner runner

状态：实现完成，live rollout / smoke 后置到统一测试批次

日期：2026-06-13

## V1 行为

V1 `POST /api/v1/ops/maintenance/aggregation/reset-init` 的真实顺序：

1. `raw_video_unified_video` 全量解绑。
2. 删除 `UNIFIED_VIDEO` search sync task。
3. 删除全部 `unified_videos`。
4. hard reset `UNIFIED_VIDEO` ES index。
5. 将全部 `raw_videos.aggregation_status` 重置为 `PENDING`。

## V3 实现

- shared contracts：
  - `AggregationResetStepResponse`
  - `SearchIndexMaintenanceResponse`
- aggregation-worker：
  - `POST /internal/v1/aggregation/reset/prepare`
  - `POST /internal/v1/aggregation/reset/mark-raw-pending`
  - `prepare` 清理 unified 相关关系、interaction/magnet 引用、raw/unified 绑定和 unified 主表。
  - `mark-raw-pending` 只更新 `aggregation_status` 与 `aggregation_status_updated_at`，不污染 raw 业务 `updated_at`。
  - internal API 继续走 service identity / scopes。
- search-service：
  - `POST /internal/v1/search/index-maintenance/UNIFIED_VIDEO/hard-reset`
  - 删除对应 `search_sync_tasks`。
  - 删除并重建 ES index，evict portal public read model cache。
- ops-service：
  - `MaintenanceRunnerService` 将 `aggregation-reset` 注册为 HIGH risk / devAllowed / supportsDryRun runner。
  - 执行顺序固定为 aggregation prepare -> search hard reset -> aggregation raw pending。
  - 继续记录 `worker_job_audit_events`，并透传 `correlationId`。

## 验证

聚焦测试：

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --tests "*AggregationServiceTest" --tests "*InternalAggregationControllerTest" --tests "*JdbcAggregationRepositoryTest" :services:ircs-search-service:test --tests "*SearchIndexServiceTest" --tests "*SearchIndexMaintenanceInternalControllerTest" :services:ircs-ops-service:test --tests "*MaintenanceRunnerServiceTest" --tests "*AggregationMaintenanceClientTest" --tests "*SearchServiceMaintenanceSyncClientTest" --tests "*MaintenanceReindexCommandTest"
```

受影响服务完整测试：

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test :services:ircs-ops-service:test
```

结果：

```text
BUILD SUCCESSFUL
```

## 边界

- 本轮未执行 K8S rollout，未触碰 live DB / ES。
- `aggregation-reset` 是无界 destructive runner；live 执行必须放到统一测试窗口，先确认 namespace、数据备份、资源和回滚路径。
- V3 已按 owner 编排补齐 V1 主链路顺序；跨服务 side effect 已在 aggregation/search owner 中实现，后续仍需 live 数据验证是否存在未纳入的新增引用表。

## MARK

```text
MARK B1 PASS V1 reset order documented
MARK B2 PASS aggregation-worker reset prepare and raw pending owner APIs implemented
MARK B3 PASS search-service hard reset owner API implemented
MARK B4 PASS ops-service aggregation-reset runner executes owner steps in V1 order with audit
MARK B5 PASS focused and affected service tests pass
```
