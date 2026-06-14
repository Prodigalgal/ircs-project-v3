# 028-22-A unified recalculate owner runner

状态：已完成

父任务：

- `tasks/in-progress/028-22-production-candidate-closeout-parent.md`

报告：

- `docs/reports/118-02822a-unified-recalculate-owner-runner.md`

## 结果

- `unified-recalculate` 已从 dev refusal 升级为 HIGH risk、devAllowed、supportsDryRun 的 capped owner runner。
- ops-service 只调用 aggregation-worker owner API，不直接写 aggregation 表。
- aggregation-worker 负责 dirty unified 选择、`FOR UPDATE SKIP LOCKED` 和复用现有 recalculate 副作用。
- internal API 已接入 service identity / scopes。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --tests "*AggregationServiceTest" --tests "*InternalAggregationControllerTest" :services:ircs-ops-service:test --tests "*MaintenanceRunnerServiceTest" --tests "*AggregationMaintenanceClientTest" --tests "*OpsConfigValuesTest"
```

```text
BUILD SUCCESSFUL
```

## 后置

- K8S rollout / live smoke 后置到统一测试批次。
- `aggregation-reset`、`sanitize`、`trend-sync` 后续已分别由 `028-22-B/C/D` 完成；
  真实 live smoke 后置统一测试窗口。
