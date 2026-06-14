# 073 028-12 portal-service handler audit 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 背景

`028-12` 已完成 config-service、catalog-service、content-service、credential-service、
identity-service、interaction-service、magnet-service、storage-service、search-service 的
service handler audit，并完成 notification worker/job audit、ops 查询和 live smoke。下一步选择
`ircs-portal-service`，因为它承载 portal metadata、home、explore 和 movie detail 读聚合接口，是
后续通过门户持续验证 V1 到 V3 行为迁移的第一层业务读面。

`025-02` 已完成 portal-service explore/detail API 路由迁移，`027` 已完成 portal/admin 前端拆分部署。
补齐 portal-service handler audit 后，可以同时追踪 portal-bff 入口请求和 portal-service 内部 handler
请求，便于定位门户读聚合、缓存头、参数归一化和下游 content/search/storage 数据差异。

## 目标

- 为 `ircs-portal-service` 接入 `ServiceRequestAuditFilter`。
- 让 portal handler 请求写入 `request_audit_logs.request_source=ircs-portal-service`。
- 本地测试证明 Authorization、Cookie 和 query token 不进入 audit 表。
- 使用只读 metadata endpoint 完成 live smoke，不触发写入、缓存刷新、queue publish 或 fixture。

## 非目标

- 不修改 portal-service metadata、home、explore、detail 业务语义。
- 不修改 portal-bff route、前端静态门户、缓存策略或参数归一化行为。
- 不在 audit 中记录 Authorization、Cookie、JWT、query token 或 response body。
- 不新增 controller、BFF route、Service、Pod、PVC、中间件或 HTTPRoute。
- 不构造 DB/ES/queue fixture。

## 验收标准

```text
MARK 1/4 portal-service handler request writes request_source
MARK 2/4 portal-service audit redacts query token
MARK 3/4 portal-service audit excludes Authorization and Cookie
MARK 4/4 portal-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 portal-service pod is Ready
MARK 2/4 portal-service metadata request returns success
MARK 3/4 portal-service live audit row redacts query token
MARK 4/4 portal-service live smoke cleanup leaves no DB residue
```

## 测试边界

```text
.\gradlew.bat :services:ircs-portal-service:test --rerun-tasks
.\scripts\smoke\portal-service-audit-02812-smoke.ps1
```

live smoke 只能调用 `/api/portal/metadata` 只读端点，并在 query string 中放入用于审计脱敏验证的
`token` 参数；不执行 home/explore/detail fixture 或任何写入。

## 影响范围

```text
services/ircs-portal-service/src/main/java/com/prodigalgal/ircs/portal/PortalServiceAuditConfiguration.java
services/ircs-portal-service/src/test/java/com/prodigalgal/ircs/portal/PortalServiceAuditFilterTest.java
services/ircs-portal-service/build.gradle
scripts/smoke/portal-service-audit-02812-smoke.ps1
docs/reports/073-02812-portal-service-audit-handler.md
docs/migrations/073-portal-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 当前状态

已完成。

## 实现结果

- 新增 `PortalServiceAuditConfiguration`，为 `ircs-portal-service` 注册
  `ServiceRequestAuditFilter`。
- `services/ircs-portal-service/build.gradle` 增加 H2 测试依赖，用于本地 audit table 验证。
- 新增 `PortalServiceAuditFilterTest`，覆盖 metadata handler audit 写入、query
  token/password 脱敏、Authorization / Cookie / response content 排除，以及 actuator 噪音排除。
- 新增 `scripts/smoke/portal-service-audit-02812-smoke.ps1`，通过只读 metadata endpoint
  完成 live audit 与 query token 脱敏验证。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-portal-service:test --rerun-tasks
BUILD SUCCESSFUL in 9s

PortalServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
portal-service total tests=16, failures=0, errors=0, skipped=0
```

ARM64 image 与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-portal-service' -ImageName ircs-portal-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-portal-service
kubectl -n ircs-dev rollout status deployment/ircs-portal-service --timeout=240s

deployment/ircs-portal-service successfully rolled out
pod: ircs-portal-service-84978475cc-smxwg Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-portal-service@sha256:6d10a946fce61b6930806ee4958c8e51ccee7243c55af90f71a6521b99bc4dc2
```

live smoke：

```text
.\scripts\smoke\portal-service-audit-02812-smoke.ps1
MARK 1/4 portal-service pod is Ready
MARK 2/4 portal-service metadata request returns success
MARK 3/4 portal-service live audit row redacts query token
MARK 4/4 portal-service live smoke cleanup leaves no DB residue

trace: 02812-portal-audit-20260608155128
```

后置检查：

```text
request_audit_logs portal-service 02812 residue: 0
local portal-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

MARK：

```text
MARK 1/4 portal-service handler request writes request_source
MARK 2/4 portal-service audit redacts query token
MARK 3/4 portal-service audit excludes Authorization and Cookie
MARK 4/4 portal-service audit filter skips actuator noise
MARK 1/4 portal-service pod is Ready
MARK 2/4 portal-service metadata request returns success
MARK 3/4 portal-service live audit row redacts query token
MARK 4/4 portal-service live smoke cleanup leaves no DB residue
```
