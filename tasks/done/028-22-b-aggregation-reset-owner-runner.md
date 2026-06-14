# 028-22-B aggregation reset owner runner

状态：完成

父任务：

- `tasks/in-progress/028-22-production-candidate-closeout-parent.md`

报告：

- `docs/reports/119-02822b-aggregation-reset-owner-runner.md`

## 目标

将 V1 `aggregation/reset-init` 从 ops-service 占位拒绝项升级为按 owner-service
边界执行的 destructive runner，保持 V1 顺序：aggregation prepare -> search hard reset ->
aggregation raw pending。

## 改动

- aggregation-worker 增加 reset prepare / mark raw pending internal API。
- search-service 增加 unified index hard reset internal API，并清理对应 search sync task。
- ops-service 增加多 owner 编排 runner，继续透传 correlationId 并写 worker-job audit。
- shared contracts 增加 aggregation reset step 与 search index maintenance response。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --tests "*AggregationServiceTest" --tests "*InternalAggregationControllerTest" --tests "*JdbcAggregationRepositoryTest" :services:ircs-search-service:test --tests "*SearchIndexServiceTest" --tests "*SearchIndexMaintenanceInternalControllerTest" :services:ircs-ops-service:test --tests "*MaintenanceRunnerServiceTest" --tests "*AggregationMaintenanceClientTest" --tests "*SearchServiceMaintenanceSyncClientTest" --tests "*MaintenanceReindexCommandTest"
```

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test :services:ircs-ops-service:test
```

结果：`BUILD SUCCESSFUL`。

## 遗留

- 未做 live rollout / live destructive smoke。
- `sanitize`、`trend-sync` 后续已分别由 `028-22-C/D` 完成；真实 live smoke 后置统一测试窗口。
