# 085 028-12 task-service runner audit 报告

状态：已完成

日期：2026-06-08

## 背景

`028-12` 已完成 HTTP service handler audit 全覆盖和 notification worker/job audit live queue
第一刀。`ircs-task-service` 现在已有 `CollectionTaskRunnerService`、cron scheduler、startup
recovery 和 watchdog，但 runner/scheduler 层还没有 `worker_job_audit_events` 证据。

该切片优先补 task-service runner audit：任务被执行、跳过、失败、watchdog recovery 都应形成
可查询的 worker/job audit evidence，便于 admin 通过 ops-service 持续观察任务运行行为。

## 目标

- 为 task-service 增加 `WorkerJobAuditWriter` 配置。
- 在 `CollectionTaskRunnerService` 成功、失败、skip/refused 路径写入 audit。
- 在 `TaskWatchdog` fail stale active tasks 和 `TaskStartupRecovery` recovery 路径写入 audit。
- 不执行真实 scraper 抓取、不 rollout、不新增 HTTPRoute。

## 非目标

- 不修改 scraper-service。
- 不执行 live collection task。
- 不改变 task lifecycle HTTP contract。
- 不新增数据库 schema，复用 `worker_job_audit_events`。

## 建议写入范围

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

本轮不 rollout、不 push image、不触发真实 scraper。

## 实施方案

- `TaskServiceAuditConfiguration` 参考 ops-service 的配置风格暴露
  `WorkerJobAuditWriter`，默认 `jobSource=ircs-task-service`，datasource/username/password
  优先读取 `app.worker.audit.*`，再回落到 Spring datasource/env。
- `CollectionTaskRunnerService` 为单任务 runner 写入固定口径 audit：
  `job_type=collection-task-runner`、`job_name=collection-task.runner`、
  `correlation_id=<taskId>`。
- runner success 写入 `status=succeeded`，scraper 返回失败或异常写入 `status=failed`。
- runner execution plan missing、disabled、非 QUEUED、停止后暂停写入 `status=skipped`；
  `markRunning` 竞争失败写入 `status=refused`，且不调用 scraper。
- runner audit error 只使用稳定原因或异常类名，不把 scraper exception message、headers、
  token、body、proxy credential 等运行输入写入 worker audit error。
- `TaskWatchdog` 和 `TaskStartupRecovery` 对实际更新数量大于 0 的 batch 写入 audit：
  `job_type=task-watchdog|task-startup-recovery`，`correlation_id=batch:*`。

## 实施结果

- task-service 已配置 `WorkerJobAuditWriter`，默认服务来源为 `ircs-task-service`。
- collection task runner 的成功、失败、skipped、refused 路径已接入
  `worker_job_audit_events` compatible evidence。
- watchdog stale active task fail 和 startup interrupted task recovery 已接入 batch audit evidence。
- 新增本地单元测试覆盖配置、runner success/failure/skipped/refused、watchdog batch 和
  startup recovery batch。
- 本轮未修改 scraper-service、shared 模块、K8S、deploy manifest、父任务、索引文件或其他服务。
- 本轮未执行 rollout、未 push image、未执行真实 scraper/live task。

## 验收结果

```text
MARK 1/5 task-service config exposes WorkerJobAuditWriter with service source - PASS
MARK 2/5 collection task runner success/failure writes worker job audit evidence - PASS
MARK 3/5 runner skipped/refused path writes stable audit evidence without scraper call - PASS
MARK 4/5 watchdog/startup recovery writes batch audit evidence - PASS
MARK 5/5 task-service tests pass without live scraper execution - PASS
```

## 测试结果

```powershell
.\gradlew.bat :services:ircs-task-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 11s`，`10 actionable tasks: 10 executed`。

测试结果 XML：

```text
CollectionTaskControllerTest tests=8, failures=0, errors=0, skipped=0
CollectionTaskCronSchedulerTest tests=1, failures=0, errors=0, skipped=0
CollectionTaskRunnerServiceTest tests=4, failures=0, errors=0, skipped=0
TaskServiceAuditConfigurationTest tests=1, failures=0, errors=0, skipped=0
TaskServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
TaskStartupRecoveryTest tests=2, failures=0, errors=0, skipped=0
TaskWatchdogTest tests=2, failures=0, errors=0, skipped=0
```

## 遗留边界

- 本切片不 rollout，不做 live PostgreSQL 写入 smoke。
- `worker_job_audit_events` 查询面仍复用既有 ops-service，不在 task-service 内新增查询 API。
