# 028-12-B aggregation scheduler audit task spec

状态：已完成

父任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/086-02812-aggregation-scheduler-audit.md`
- `docs/migrations/086-aggregation-scheduler-audit.md`

## 背景

aggregation-worker 是 scheduler 型 worker，当前已有业务 live smoke，但缺少
`worker_job_audit_events` 可观察性。需要把 scheduler run 成功、空批 reset 和异常路径纳入
audit。

## 目标

- 为 aggregation-worker 配置 `WorkerJobAuditWriter`。
- scheduler processed batch 写 succeeded audit。
- empty batch reset stuck path 写 succeeded audit。
- exception path 写 failed audit 并保持 scheduler loop 不向外抛。

## 非目标

- 不改 aggregation 业务算法。
- 不 rollout。
- 不新增 schema。
- 不修改其他服务。

## 输入

```text
AggregationScheduler
AggregationService
WorkerJobAuditWriter
worker_job_audit_events contract
```

## 输出

```text
aggregation scheduler audit implementation
aggregation-worker tests
docs/reports/086-02812-aggregation-scheduler-audit.md
docs/migrations/086-aggregation-scheduler-audit.md
```

## 影响范围

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

## 实现结果

- 已在 `ircs-aggregation-worker` 增加 `AggregationWorkerAuditConfiguration`，
  配置 `WorkerJobAuditWriter`，默认 `jobSource=ircs-aggregation-worker`。
- 已在 `AggregationScheduler.runBatch()` 写入 worker/job audit evidence：
  - processed batch：`jobType=scheduler`、`jobName=aggregation.batch`、
    `status=succeeded`。
  - empty batch reset stuck：`jobType=scheduler`、
    `jobName=aggregation.reset-stuck`、`status=succeeded`。
  - exception path：`status=failed` 并记录异常对象；scheduler loop 继续吞异常，
    不向外抛。
- 已新增本地单元测试覆盖配置和 scheduler 三条路径，不触发 live scheduler execution。

## 测试结果

```powershell
.\gradlew.bat :services:ircs-aggregation-worker:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 16s`。

```text
AggregationSchedulerTest tests=3, skipped=0, failures=0, errors=0
AggregationWorkerAuditConfigurationTest tests=1, skipped=0, failures=0, errors=0
AggregationServiceTest tests=3, skipped=0, failures=0, errors=0
RawVideoAggregationRecordTest tests=2, skipped=0, failures=0, errors=0
```

## MARK

```text
MARK 1/5 PASS aggregation-worker config exposes WorkerJobAuditWriter with service source
MARK 2/5 PASS scheduler processed batch writes succeeded audit evidence
MARK 3/5 PASS empty batch reset-stuck path writes succeeded audit evidence
MARK 4/5 PASS scheduler exception path writes failed audit evidence without aborting scheduler loop
MARK 5/5 PASS aggregation-worker tests pass without live scheduler execution
```

## 边界确认

- 本任务已由主线程集成并移动到 `tasks/done/`。
- 未 rollout、未 push image、未执行 live aggregation fixture。
