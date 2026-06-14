# 118 028-22-A unified recalculate owner runner

状态：实现完成，live rollout / smoke 后置到统一测试批次

日期：2026-06-12

## 背景

`unified-recalculate` 长期归在 destructive / high-write runner 剩余项里。上一轮已经补齐
`search-reindex-raw` 的 capped owner runner，本轮继续把 `unified-recalculate` 从 ops-service
直接执行模型中拆出，交给 aggregation-worker owner API 处理候选选择和数据副作用。

## 改动

- 新增共享契约 `AggregationMaintenanceRunResponse`。
- aggregation-worker 新增 internal access policy：
  - `app.aggregation.internal-access.require-token`
  - `app.aggregation.internal-access.token`
  - `app.aggregation.internal-access.required-scope`
- aggregation-worker internal API：
  - `POST /internal/v1/aggregation/unified-videos/recalculate-dirty?limit=N`
  - 按 `metadata_status='DIRTY'` capped 选择 unified video。
  - 使用 `FOR UPDATE SKIP LOCKED` 避免多副本重复认领。
  - 复用现有 `AggregationService.recalculateUnified()` 数据副作用。
- ops-service 新增 aggregation owner client：
  - `INTERNAL_SERVICE` outbound policy。
  - service identity headers。
  - `X-Correlation-Id` 透传。
- `MaintenanceRunnerService` 将 `unified-recalculate` 注册为 HIGH risk、devAllowed、supportsDryRun
  的 capped owner runner，并继续写 `worker_job_audit_events`。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --tests "*AggregationServiceTest" --tests "*InternalAggregationControllerTest" :services:ircs-ops-service:test --tests "*MaintenanceRunnerServiceTest" --tests "*AggregationMaintenanceClientTest" --tests "*OpsConfigValuesTest"
```

结果：

```text
BUILD SUCCESSFUL
```

## 边界

- 本轮不执行 K8S rollout，不触碰 live DB。
- 本轮只补 `unified-recalculate`；后续 `aggregation-reset`、`sanitize`、`trend-sync` 已分别由
  `028-22-B/C/D` 完成，真实 live smoke 后置统一测试窗口。
- `aggregation-reset` 仍不能复用本 runner 直接打开，必须独立定义 reset 范围、数据副作用和 gate。

## MARK

```text
MARK A1 PASS aggregation-worker exposes authenticated capped dirty unified recalculate API
MARK A2 PASS ops-service calls aggregation owner with INTERNAL_SERVICE policy and correlationId
MARK A3 PASS MaintenanceRunnerService marks unified-recalculate as devAllowed, supportsDryRun and audited
MARK A4 PASS focused aggregation-worker / ops-service tests pass
```
