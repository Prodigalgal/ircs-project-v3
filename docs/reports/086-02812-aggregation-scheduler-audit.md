# 086 028-12 aggregation scheduler audit 报告

状态：已完成

日期：2026-06-08

## 背景

`ircs-aggregation-worker` 是 scheduler 型 worker，已完成 aggregation -> search sync live smoke，
但 scheduler batch 执行、空批次 reset stuck processing、异常路径尚未写入
`worker_job_audit_events`。`028-12` 需要把 worker/scheduler/runner audit 继续铺开。

## 目标

- 为 aggregation-worker 增加 `WorkerJobAuditWriter` 配置。
- 每次 scheduler run 记录 succeeded/failed audit evidence。
- 区分 aggregate processed、empty batch reset stuck processing 和 exception path。
- 不 rollout、不 push image、不新增 HTTPRoute。

## 非目标

- 不改变 aggregation matching 逻辑。
- 不新增 schema。
- 不执行 live aggregation fixture。
- 不修改 task-service、ops-service 或 notification-worker。

## 建议写入范围

```text
services/ircs-aggregation-worker/**
docs/reports/086-02812-aggregation-scheduler-audit.md
docs/migrations/086-aggregation-scheduler-audit.md
tasks/done/028-12-b-aggregation-scheduler-audit.md
```

## 验收标准

```text
MARK 1/5 aggregation-worker config exposes WorkerJobAuditWriter with service source
MARK 2/5 scheduler processed batch writes succeeded audit evidence
MARK 3/5 empty batch reset-stuck path writes succeeded audit evidence
MARK 4/5 scheduler exception path writes failed audit evidence without aborting scheduler loop
MARK 5/5 aggregation-worker tests pass without live scheduler execution
```

## 测试边界

```powershell
.\gradlew.bat :services:ircs-aggregation-worker:test --rerun-tasks
```

本轮不 rollout、不触发 live scheduler fixture。

## 实现结果

- 新增 `AggregationWorkerAuditConfiguration`，为 aggregation-worker 暴露
  `WorkerJobAuditWriter`，默认 `jobSource=ircs-aggregation-worker`，并支持
  `APP_WORKER_AUDIT_*` 覆盖 datasource、账号和 source。
- 更新 `AggregationScheduler.runBatch()`：
  - processed batch 成功路径写入 `jobType=scheduler`、
    `jobName=aggregation.batch`、`status=succeeded`。
  - empty batch 后的 `resetStuckProcessing` 成功路径写入 `jobType=scheduler`、
    `jobName=aggregation.reset-stuck`、`status=succeeded`。
  - exception path 写入 `status=failed`，保留原有日志记录和吞异常行为，不向外中断
    scheduler loop。
- 更新 `application.yaml`，补充 `app.worker.audit.*` 本地配置入口。
- 新增 `AggregationSchedulerTest` 和 `AggregationWorkerAuditConfigurationTest`，以 mock
  和 context runner 覆盖 5 个 MARK，不执行 live scheduler fixture。

## 测试结果

```powershell
.\gradlew.bat :services:ircs-aggregation-worker:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 16s`。

测试报告：

```text
AggregationSchedulerTest tests=3, skipped=0, failures=0, errors=0
AggregationWorkerAuditConfigurationTest tests=1, skipped=0, failures=0, errors=0
AggregationServiceTest tests=3, skipped=0, failures=0, errors=0
RawVideoAggregationRecordTest tests=2, skipped=0, failures=0, errors=0
```

## MARK 状态

```text
MARK 1/5 PASS aggregation-worker config exposes WorkerJobAuditWriter with service source
MARK 2/5 PASS scheduler processed batch writes succeeded audit evidence
MARK 3/5 PASS empty batch reset-stuck path writes succeeded audit evidence
MARK 4/5 PASS scheduler exception path writes failed audit evidence without aborting scheduler loop
MARK 5/5 PASS aggregation-worker tests pass without live scheduler execution
```

## 未执行项

- 未 rollout。
- 未 push image。
- 未执行 live aggregation fixture。
