# 074 task-service handler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 扩展 service handler audit 铺开范围，将 `ircs-task-service` 接入
`ServiceRequestAuditFilter`，让 collection task 列表、详情和生命周期相关 handler 请求在
`request_audit_logs` 中带上 `request_source=ircs-task-service`。本切片只验证只读
`GET /api/v1/collection-tasks`，避免触发任务生命周期和 scraper 执行。

## 代码范围

```text
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/TaskServiceAuditConfiguration.java
services/ircs-task-service/src/test/java/com/prodigalgal/ircs/task/TaskServiceAuditFilterTest.java
services/ircs-task-service/build.gradle
scripts/smoke/task-service-audit-02812-smoke.ps1
docs/reports/074-02812-task-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险控制

- 只写请求元数据，不记录 request body 或 response body。
- 复用 shared `RequestAuditSanitizer`，对敏感 query 参数做值脱敏。
- 本地测试显式带入 Authorization、Cookie 和 query token，并断言 audit 表不包含这些值。
- live smoke 只访问 task list endpoint，不触发创建、更新、删除、生命周期命令、scheduler 或 scraper。
- live smoke 使用唯一 `X-Trace-Id`，结束后删除本轮 audit fixture。

## 迁移结果

```text
MARK 1/4 task-service handler request writes request_source
MARK 2/4 task-service audit redacts query token
MARK 3/4 task-service audit excludes Authorization Cookie body and response content
MARK 4/4 task-service audit filter skips actuator noise
```

live smoke：

```text
MARK 1/4 task-service pod is Ready
MARK 2/4 task-service collection task list request returns success
MARK 3/4 task-service live audit row redacts query token
MARK 4/4 task-service live smoke cleanup leaves no DB residue
```

验证证据：

```text
.\gradlew.bat :services:ircs-task-service:test --rerun-tasks
BUILD SUCCESSFUL in 8s

ircs-task-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-task-service@sha256:e5d8f0d50a921e6d0f5ade027c089b877669d177977596a815aac5f4701eefb0

trace: 02812-task-audit-20260608160734
residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
