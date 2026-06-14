# 028-20-12C worker job Micrometer baseline

状态：完成

日期：2026-06-11

## 背景

运维增强要求监控任务处理延迟、失败率等指标。V3 已经把多数 Rabbit consumer、watchdog、runner、scheduler
统一包到 `WorkerJobAuditWriter.record(...)`，因此本切片把 metrics 放到这条共同路径，避免每个服务重复埋点。

## 范围

- shared `WorkerJobMetrics` 记录 `ircs.worker.job.runs` counter。
- shared `WorkerJobMetrics` 记录 `ircs.worker.job.duration` timer。
- `WorkerJobAuditWriter.record(...)` 在 DB audit 禁用时仍记录 metrics。
- `WorkerJobAuditWriter.noop()` 保持真正 no-op。
- notification Rabbit consumer 与 ops maintenance runner 增加代表性指标测试。

## 非目标

- 不在代码里直接计算失败率 Gauge；失败率由 Prometheus/Grafana 基于 outcome counter 派生。
- 不给未接 `WorkerJobAuditWriter` 的轻量 config event listener 逐个补专用指标。
- 不执行 K8S live Prometheus smoke。

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*WorkerJobAuditWriterTest" --tests "*WorkerInstanceIdsTest" :services:ircs-notification-worker:test --tests "*MailConsumerTest" --tests "*MailSendHistoryCleanupRunnerTest" :services:ircs-ops-service:test --tests "*MaintenanceRunnerServiceTest" --tests "*MaintenanceSchedulerServiceTest" --tests "*WorkerJobAuditRepositoryTest"
.\gradlew.bat --no-daemon :shared:ircs-common:jar :services:ircs-notification-worker:bootJar :services:ircs-ops-service:bootJar :services:ircs-aggregation-worker:bootJar :services:ircs-task-service:bootJar :services:ircs-search-service:bootJar :services:ircs-storage-service:bootJar :services:ircs-metadata-worker:bootJar :services:ircs-normalization-worker:bootJar :services:ircs-scraper-service:bootJar
```

结果：通过。

## 遗留

- 统一 K8S smoke 窗口检查 `/actuator/prometheus` 中 `ircs_worker_job_runs` 与 `ircs_worker_job_duration` 实际可见。
- 如后续确认某些未接 audit 的 listener 需要 SLO 级观测，再按 job type 补专用 metrics。
