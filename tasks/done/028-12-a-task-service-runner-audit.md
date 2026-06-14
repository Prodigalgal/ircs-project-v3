# 028-12-A task-service runner audit task spec

状态：已完成

父任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/085-02812-task-service-runner-audit.md`
- `docs/migrations/085-task-service-runner-audit.md`

## 背景

task-service 的 HTTP handler audit 已完成，但 collection task runner、cron scheduler、startup
recovery 和 watchdog 还没有 worker/job audit evidence。V1 的任务运行链路需要在 V3 中可观察。

## 目标

- 为 task-service 配置 `WorkerJobAuditWriter`。
- runner 成功、失败、skip/refused 写入 audit。
- watchdog 和 startup recovery 写入 batch audit evidence。
- 本地测试覆盖，不执行真实 scraper。

## 非目标

- 不改 scraper-service。
- 不真实抓取。
- 不 rollout。
- 不新增 schema。

## 输入

```text
CollectionTaskRunnerService
TaskWatchdog
TaskStartupRecovery
WorkerJobAuditWriter
worker_job_audit_events contract
```

## 输出

```text
task-service worker audit implementation
task-service tests
docs/reports/085-02812-task-service-runner-audit.md
docs/migrations/085-task-service-runner-audit.md
```

## 影响范围

```text
services/ircs-task-service/**
docs/reports/085-02812-task-service-runner-audit.md
docs/migrations/085-task-service-runner-audit.md
tasks/done/028-12-a-task-service-runner-audit.md
```

## 验收标准

```text
MARK 1/5 task-service config exposes WorkerJobAuditWriter with service source
MARK 2/5 collection task runner success/failure writes worker job audit evidence
MARK 3/5 runner skipped/refused path writes stable audit evidence without scraper call
MARK 4/5 watchdog/startup recovery writes batch audit evidence
MARK 5/5 task-service tests pass without live scraper execution
```

## 测试边界

```powershell
.\gradlew.bat :services:ircs-task-service:test --rerun-tasks
```

## 实现结果

- `services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/TaskServiceAuditConfiguration.java`
  新增 `WorkerJobAuditWriter` bean，默认 `jobSource=ircs-task-service`。
- `CollectionTaskRunnerService` 接入 worker/job audit：
  `job_type=collection-task-runner`、`job_name=collection-task.runner`、
  `correlation_id=<taskId>`。
- runner success 写入 `succeeded`，scraper 返回失败或 runner 异常写入 `failed`。
- runner plan missing、disabled、非 QUEUED、停止后暂停写入 `skipped`。
- runner `markRunning` 竞争失败写入 `refused`，且不调用 scraper。
- runner audit error 只写稳定低敏原因或异常类名，不写 secret、body、token、headers、
  proxy credential 或 scraper exception message。
- `TaskWatchdog` fail stale active tasks 在实际 fail 数量大于 0 时写入 batch audit evidence。
- `TaskStartupRecovery` recovery 在实际 recovered 数量大于 0 时写入 batch audit evidence。
- 新增/更新 task-service 本地单元测试覆盖配置、runner success/failure/skipped/refused、
  watchdog batch 和 startup recovery batch。

## 测试结果

```powershell
.\gradlew.bat :services:ircs-task-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 11s`，`10 actionable tasks: 10 executed`。

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

## 交付边界

- 未 rollout、未 push image、未执行真实 scraper/live task。
- 本任务已由主线程集成并移动到 `tasks/done/`。
