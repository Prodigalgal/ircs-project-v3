# 059 worker/job audit events

状态：已完成

日期：2026-06-07

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

关联报告：

- `docs/reports/059-02812-worker-job-audit-notification-worker-first-slice.md`

## 背景

HTTP request audit 已由 `request_audit_logs` 承载。worker/job 执行事件需要独立表，
避免 queue consumer、scheduler、maintenance runner 或 provider task 污染 HTTP request
语义。

## 变更计划

- 新增 Liquibase SQL changelog：`2026/07/add-worker-job-audit-events.sql`。
- 在 master changelog 末尾 include 新文件，不修改历史 migration。
- 新表使用 `worker_job_audit_events`，只保存执行元数据。

## Schema 草案

```text
id uuid primary key
created_at timestamp not null
updated_at timestamp not null
version bigint not null default 0
job_source varchar(128) not null
job_type varchar(64) not null
job_name varchar(128) not null
correlation_id varchar(128)
status varchar(32) not null
duration_ms bigint not null default 0
error_class varchar(256)
error_message text
```

索引：

```text
idx_worker_job_audit_created(created_at desc)
idx_worker_job_audit_source_status(job_source, status)
idx_worker_job_audit_job_name(job_name)
idx_worker_job_audit_correlation(correlation_id)
```

## 安全边界

不记录 credential、token、request body、Cookie、Authorization、邮件正文、邮件收件人或
SMTP 配置。`MailMessageDTO` 当前没有安全 message id，因此本轮 notification worker
事件允许 `correlation_id` 为空。

## 验证计划

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-notification-worker:test --rerun-tasks
```

本轮只做本地 H2 写入测试，不执行 live schema smoke。

## 实现结果

- 新增 `platform/ircs-migrator/src/main/resources/db/changelog/2026/07/add-worker-job-audit-events.sql`。
- 更新 `platform/ircs-migrator/src/main/resources/db/changelog/db.changelog-master.yaml`，
  在末尾 include 新 SQL 文件。
- 未修改历史 migration。

## 验证结果

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL in 12s

.\gradlew.bat :platform:ircs-migrator:processResources --rerun-tasks
BUILD SUCCESSFUL in 1s
```

MARK：

```text
MARK 1/3 worker_job_audit_events schema is dedicated and non-HTTP
```

SKIPPED：

```text
SKIPPED live schema smoke: 本轮未 rollout，也未连接 dev PostgreSQL。
```
