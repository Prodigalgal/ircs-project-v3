# 028-20-12B worker job audit instance source

状态：完成

日期：2026-06-11

## 背景

运维增强要求 worker/outbox/runner 的运行态必须能追踪具体副本。`WorkerJobAuditWriter`
过去默认使用 `spring.application.name` 作为 `job_source`，多副本时所有审计行相同，无法区分是哪一个 Pod/进程处理任务。

## 范围

- `WorkerJobAuditWriter` 默认使用 `WorkerInstanceIds.resolve(...)` 生成 `service@host#pid`。
- 新增可选 `workerInstanceId` 构造参数，支持后续显式注入 Pod 名称或稳定 worker instance id。
- 保留 service prefix，兼容 ops 现有按服务名过滤 worker job audit 的查询方式。
- 更新 shared、notification-worker、aggregation-worker、task-service 的代表性测试。

## 非目标

- 不修改 `worker_job_audit_events` 表结构。
- 不在本切片逐服务新增 ENV 配置项；K8S 默认 `HOSTNAME` 已可提供 Pod 粒度。
- 不在本切片补 Rabbit consumer / maintenance runner 的 Micrometer 指标。

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*WorkerJobAuditWriterTest" --tests "*WorkerInstanceIdsTest" :services:ircs-notification-worker:test --tests "*MailConsumerTest" --tests "*MailSendHistoryCleanupRunnerTest" :services:ircs-aggregation-worker:test --tests "*AggregationWorkerAuditConfigurationTest" :services:ircs-task-service:test --tests "*TaskServiceAuditConfigurationTest" :services:ircs-ops-service:test --tests "*WorkerJobAuditRepositoryTest"
.\gradlew.bat --no-daemon :shared:ircs-common:jar :services:ircs-notification-worker:bootJar :services:ircs-aggregation-worker:bootJar :services:ircs-task-service:bootJar :services:ircs-ops-service:bootJar :services:ircs-search-service:bootJar
```

结果：通过。

## 遗留

- 后续在 K8S live smoke 中确认 worker audit `job_source` 实际展示 Pod 名或显式 worker id。
- 继续为 Rabbit consumer 与 maintenance runner 增加任务延迟、run duration、成功/失败计数等 Micrometer 指标。
