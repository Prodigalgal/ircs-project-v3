# 085 task-service runner audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 增加 task-service runner/scheduler/recovery 的 worker/job audit evidence。

## 范围

```text
services/ircs-task-service/**
docs/reports/085-02812-task-service-runner-audit.md
tasks/done/028-12-a-task-service-runner-audit.md
```

## 期望结果

```text
MARK 1/5 task-service config exposes WorkerJobAuditWriter with service source
MARK 2/5 collection task runner success/failure writes worker job audit evidence
MARK 3/5 runner skipped/refused path writes stable audit evidence without scraper call
MARK 4/5 watchdog/startup recovery writes batch audit evidence
MARK 5/5 task-service tests pass without live scraper execution
```

## 实施结果

- `TaskServiceAuditConfiguration` 新增 `WorkerJobAuditWriter` bean，配置风格与
  notification-worker/ops-service 对齐，默认 `jobSource=ircs-task-service`。
- `CollectionTaskRunnerService` 写入 runner audit evidence：
  `job_type=collection-task-runner`、`job_name=collection-task.runner`、
  `correlation_id=<taskId>`、`status=succeeded|failed|skipped|refused`。
- runner skipped/refused 路径在调用 scraper 前返回，并写入稳定低敏原因。
- `TaskWatchdog` 对 stale active task fail batch 写入
  `task-watchdog.fail-stale-active-tasks` audit evidence。
- `TaskStartupRecovery` 对 interrupted task recovery batch 写入
  `task-startup-recovery.recover-interrupted-tasks` audit evidence。
- 不新增 schema，继续复用既有 `worker_job_audit_events` 表结构和 ops 查询面。

## 验证结果

```powershell
.\gradlew.bat :services:ircs-task-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 11s`。

```text
CollectionTaskControllerTest tests=8, failures=0, errors=0, skipped=0
CollectionTaskCronSchedulerTest tests=1, failures=0, errors=0, skipped=0
CollectionTaskRunnerServiceTest tests=4, failures=0, errors=0, skipped=0
TaskServiceAuditConfigurationTest tests=1, failures=0, errors=0, skipped=0
TaskServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
TaskStartupRecoveryTest tests=2, failures=0, errors=0, skipped=0
TaskWatchdogTest tests=2, failures=0, errors=0, skipped=0
```

## MARK

```text
MARK 1/5 task-service config exposes WorkerJobAuditWriter with service source - PASS
MARK 2/5 collection task runner success/failure writes worker job audit evidence - PASS
MARK 3/5 runner skipped/refused path writes stable audit evidence without scraper call - PASS
MARK 4/5 watchdog/startup recovery writes batch audit evidence - PASS
MARK 5/5 task-service tests pass without live scraper execution - PASS
```

## 未执行项

- 未 rollout。
- 未 push image。
- 未执行真实 scraper 或 live collection task。
