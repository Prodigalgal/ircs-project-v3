# 086 aggregation scheduler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 增加 aggregation-worker scheduler 的 worker/job audit evidence。

## 范围

```text
services/ircs-aggregation-worker/**
docs/reports/086-02812-aggregation-scheduler-audit.md
tasks/done/028-12-b-aggregation-scheduler-audit.md
```

## 期望结果

```text
MARK 1/5 aggregation-worker config exposes WorkerJobAuditWriter with service source
MARK 2/5 scheduler processed batch writes succeeded audit evidence
MARK 3/5 empty batch reset-stuck path writes succeeded audit evidence
MARK 4/5 scheduler exception path writes failed audit evidence without aborting scheduler loop
MARK 5/5 aggregation-worker tests pass without live scheduler execution
```

## 实际变更

- `services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationWorkerAuditConfiguration.java`
  新增 worker/job audit writer 配置，默认使用
  `app.worker.audit.source=${spring.application.name:ircs-aggregation-worker}`。
- `services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationScheduler.java`
  在 processed batch、empty batch reset-stuck 和 exception path 记录
  `WorkerJobAuditEvent`。
- `services/ircs-aggregation-worker/src/main/resources/application.yaml`
  新增 `app.worker.audit.*` 配置入口。
- `services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/AggregationSchedulerTest.java`
  覆盖 scheduler 三条 audit evidence 路径和异常不外抛。
- `services/ircs-aggregation-worker/src/test/java/com/prodigalgal/ircs/aggregation/AggregationWorkerAuditConfigurationTest.java`
  覆盖 writer bean 与 service source。

## 验证记录

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

## MARK 完成情况

```text
MARK 1/5 PASS aggregation-worker config exposes WorkerJobAuditWriter with service source
MARK 2/5 PASS scheduler processed batch writes succeeded audit evidence
MARK 3/5 PASS empty batch reset-stuck path writes succeeded audit evidence
MARK 4/5 PASS scheduler exception path writes failed audit evidence without aborting scheduler loop
MARK 5/5 PASS aggregation-worker tests pass without live scheduler execution
```

## 迁移边界

- 未新增 schema，复用既有 `worker_job_audit_events`。
- 未修改 K8S、deploy、其他服务或 shared 模块。
- 未 rollout、未 push image、未执行 live aggregation fixture。
