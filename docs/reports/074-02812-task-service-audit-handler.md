# 074 028-12 task-service handler audit 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 背景

`028-12` 已完成 config-service、catalog-service、content-service、credential-service、
identity-service、interaction-service、magnet-service、storage-service、search-service 和
portal-service 的 service handler audit live smoke，并完成 notification worker/job audit、
ops 查询和 live queue smoke。剩余未铺开的 HTTP service handler audit 中，`ircs-task-service`
承载采集任务的列表、详情、生命周期命令、cron scheduler 和 scraper-service 执行桥接，是后续
admin 门户持续验证 V1 采集任务行为的重要入口。

本切片选择 `GET /api/v1/collection-tasks` 作为 live smoke 入口。该端点只读，不触发任务创建、
启动、暂停、停止、删除、scraper 调用或 queue publish，适合在当前 `ircs-dev` 低资源配置下补齐
task-service handler audit。

## 目标

- 为 `ircs-task-service` 接入 `ServiceRequestAuditFilter`。
- 让 collection task handler 请求写入
  `request_audit_logs.request_source=ircs-task-service`。
- 本地测试证明 Authorization、Cookie、query token/password 和 response content 不进入 audit 表。
- 使用只读 task list endpoint 完成 live smoke，不触发 task lifecycle 或 scraper 执行。

## 非目标

- 不修改 task-service 任务创建、更新、删除、start/resume/pause/stop 业务语义。
- 不修改 cron scheduler、runner、scraper-service HTTP 执行桥接或 task log 业务逻辑。
- 不在 audit 中记录 Authorization、Cookie、JWT、query token、password、request body 或 response body。
- 不新增 controller、BFF route、Service、Pod、PVC、中间件或 HTTPRoute。
- 不构造 task、log、DB/ES/queue fixture。

## 验收标准

```text
MARK 1/4 task-service handler request writes request_source
MARK 2/4 task-service audit redacts query token
MARK 3/4 task-service audit excludes Authorization Cookie body and response content
MARK 4/4 task-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 task-service pod is Ready
MARK 2/4 task-service collection task list request returns success
MARK 3/4 task-service live audit row redacts query token
MARK 4/4 task-service live smoke cleanup leaves no DB residue
```

## 测试边界

```text
.\gradlew.bat :services:ircs-task-service:test --rerun-tasks
.\scripts\smoke\task-service-audit-02812-smoke.ps1
```

live smoke 只能调用 `/api/v1/collection-tasks` 只读端点，并在 query string 中放入用于审计脱敏验证的
`token` 参数；不执行 create/update/delete/start/resume/pause/stop/log 写入或 scraper 执行。

## 影响范围

```text
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/TaskServiceAuditConfiguration.java
services/ircs-task-service/src/test/java/com/prodigalgal/ircs/task/TaskServiceAuditFilterTest.java
services/ircs-task-service/build.gradle
scripts/smoke/task-service-audit-02812-smoke.ps1
docs/reports/074-02812-task-service-audit-handler.md
docs/migrations/074-task-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 当前状态

已完成。

## 实现结果

- 新增 `TaskServiceAuditConfiguration`，为 `ircs-task-service` 注册
  `ServiceRequestAuditFilter`。
- `services/ircs-task-service/build.gradle` 增加 H2 测试依赖，用于本地 audit table 验证。
- 新增 `TaskServiceAuditFilterTest`，覆盖 collection task list handler audit 写入、query
  token/password 脱敏、Authorization / Cookie / response content 排除，以及 actuator 噪音排除。
- 新增 `scripts/smoke/task-service-audit-02812-smoke.ps1`，通过只读 collection task list
  endpoint 完成 live audit 与 query token 脱敏验证。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-task-service:test --rerun-tasks
BUILD SUCCESSFUL in 8s

TaskServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
task-service total tests=14, failures=0, errors=0, skipped=0
```

脚本语法：

```text
scripts/smoke/task-service-audit-02812-smoke.ps1
syntax ok
```

ARM64 image 与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-task-service' -ImageName ircs-task-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-task-service
kubectl -n ircs-dev rollout status deployment/ircs-task-service --timeout=240s

deployment/ircs-task-service successfully rolled out
pod: ircs-task-service-84f784487c-rmn2q Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-task-service@sha256:e5d8f0d50a921e6d0f5ade027c089b877669d177977596a815aac5f4701eefb0
```

live smoke：

```text
.\scripts\smoke\task-service-audit-02812-smoke.ps1
MARK 1/4 task-service pod is Ready
MARK 2/4 task-service collection task list request returns success
MARK 3/4 task-service live audit row redacts query token
MARK 4/4 task-service live smoke cleanup leaves no DB residue

trace: 02812-task-audit-20260608160734
```

后置检查：

```text
request_audit_logs task-service 02812 residue: 0
local task-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

MARK：

```text
MARK 1/4 task-service handler request writes request_source
MARK 2/4 task-service audit redacts query token
MARK 3/4 task-service audit excludes Authorization Cookie body and response content
MARK 4/4 task-service audit filter skips actuator noise
MARK 1/4 task-service pod is Ready
MARK 2/4 task-service collection task list request returns success
MARK 3/4 task-service live audit row redacts query token
MARK 4/4 task-service live smoke cleanup leaves no DB residue
```
