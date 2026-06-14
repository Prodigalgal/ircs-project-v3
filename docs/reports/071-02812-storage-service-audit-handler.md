# 071 028-12 storage-service handler audit 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 背景

`028-12` 已完成 config-service、catalog-service、content-service、credential-service、
identity-service、interaction-service、magnet-service 的 service handler audit，并完成
notification worker/job audit、ops 查询和 live smoke。下一步选择 `ircs-storage-service`，
因为它承载 `/media/**`、avatar upload、cover image admin API、image download consumer 和
R2 watchdog，是 V1 到 V3 媒体存储行为 parity 的核心服务。

`028-02`、`028-05-B`、`028-05-H`、`028-07` 已经分别推进 avatar media sync、avatar sync
consumer、cover image download、R2 watchdog。补齐 storage-service handler audit 后，portal/admin
持续验证媒体链路时可以同时看到 BFF 入口和 storage handler 请求，方便定位媒体 URL、R2、local object
storage 和 cover image admin 行为差异。

## 目标

- 为 `ircs-storage-service` 接入 `ServiceRequestAuditFilter`。
- 让 storage handler 请求写入 `request_audit_logs.request_source=ircs-storage-service`。
- 本地测试证明 request body、Authorization、Cookie 和 query token 不进入 audit 表。
- 使用只读 cover image list endpoint 完成 live smoke，不触发 upload、download、delete 或 R2 sync。

## 非目标

- 不修改 storage-service avatar、cover image、local object storage 或 R2 业务语义。
- 不在 audit 中记录 request body、response body、Authorization、Cookie、JWT、file content、R2
  credential 或 object key secret。
- 不新增 controller、BFF route、Service、Pod、PVC、中间件或 HTTPRoute。
- 不构造真实 upload/download/delete/R2 fixture。
- 不接入新的 queue consumer、scheduler 或 worker/job audit 事件。

## 验收标准

```text
MARK 1/4 storage-service handler request writes request_source
MARK 2/4 storage-service audit redacts query token
MARK 3/4 storage-service audit excludes body Authorization and Cookie
MARK 4/4 storage-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 storage-service pod is Ready
MARK 2/4 storage-service cover image list request returns success
MARK 3/4 storage-service live audit row redacts query token
MARK 4/4 storage-service live smoke cleanup leaves no DB residue
```

## 测试边界

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
.\scripts\smoke\storage-service-audit-02812-smoke.ps1
```

live smoke 只能调用 `/api/v1/cover-images` 只读端点，并在 query string 中放入用于审计脱敏验证的
`token` 参数；不执行 upload、fetch、delete、retry、sync-r2、avatar upload 或 queue publish。

## 影响范围

```text
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/StorageServiceAuditConfiguration.java
services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/StorageServiceAuditFilterTest.java
services/ircs-storage-service/build.gradle
scripts/smoke/storage-service-audit-02812-smoke.ps1
docs/reports/071-02812-storage-service-audit-handler.md
docs/migrations/071-storage-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 当前状态

已完成。

## 实现结果

- 新增 `StorageServiceAuditConfiguration`，为 `ircs-storage-service` 注册
  `ServiceRequestAuditFilter`。
- `services/ircs-storage-service/build.gradle` 增加 H2 测试依赖，用于本地 audit table 验证。
- 新增 `StorageServiceAuditFilterTest`，覆盖 cover image fetch handler audit 写入、query
  token/password 脱敏、request body / Authorization / Cookie / response content 排除，以及
  actuator 噪音排除。
- 新增 `scripts/smoke/storage-service-audit-02812-smoke.ps1`，通过只读 cover image list endpoint
  完成 live audit 与 query token 脱敏验证。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL in 10s

StorageServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
storage-service total tests=52, failures=0, errors=0, skipped=0
```

ARM64 image 与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-storage-service' -ImageName ircs-storage-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-storage-service
kubectl -n ircs-dev rollout status deployment/ircs-storage-service --timeout=240s

deployment/ircs-storage-service successfully rolled out
pod: ircs-storage-service-6c8788f87-jhg57 Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:89408df252a2fb53ad6a939fae66427f78bbd8808791fa4afd941e1a6c487ff2
```

live smoke：

```text
.\scripts\smoke\storage-service-audit-02812-smoke.ps1
MARK 1/4 storage-service pod is Ready
MARK 2/4 storage-service cover image list request returns success
MARK 3/4 storage-service live audit row redacts query token
MARK 4/4 storage-service live smoke cleanup leaves no DB residue

trace: 02812-storage-audit-20260608152804
```

后置检查：

```text
request_audit_logs storage-service 02812 residue: 0
local storage-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

MARK：

```text
MARK 1/4 storage-service handler request writes request_source
MARK 2/4 storage-service audit redacts query token
MARK 3/4 storage-service audit excludes body Authorization and Cookie
MARK 4/4 storage-service audit filter skips actuator noise
MARK 1/4 storage-service pod is Ready
MARK 2/4 storage-service cover image list request returns success
MARK 3/4 storage-service live audit row redacts query token
MARK 4/4 storage-service live smoke cleanup leaves no DB residue
```
