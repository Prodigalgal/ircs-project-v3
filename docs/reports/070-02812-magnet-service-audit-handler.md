# 070 028-12 magnet-service handler audit 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 背景

`028-12` 已完成 config-service、catalog-service、content-service、credential-service、
identity-service、interaction-service handler audit，并补齐 notification worker/job audit 的
写入、ops 查询和 live smoke。下一步继续覆盖 `ircs-magnet-service`，因为它承载
magnet provider 配置、统一影片磁力链接查询和 provider search 触发，是 V1 到 V3 行为迁移中
已知 guarded/MVP 功能之一。

`028-03` 已经完成 magnet provider search dev-safe 行为第一刀，但 service handler 层仍缺少
独立 request audit 证据。补齐后，admin/ops 可以同时看到 BFF 入口请求和 magnet-service
handler 请求，便于持续验证 provider/search 行为。

## 目标

- 为 `ircs-magnet-service` 接入 `ServiceRequestAuditFilter`。
- 让 magnet handler 请求写入 `request_audit_logs.request_source=ircs-magnet-service`。
- 本地测试证明 request body、Authorization、Cookie 和 query token 不进入 audit 表。
- 使用只读 provider list endpoint 完成 live smoke，不触发 provider search 或写入 magnet 数据。

## 非目标

- 不修改 magnet-service provider/search/cache 业务语义。
- 不在 audit 中记录 request body、response body、Authorization、Cookie、JWT、provider payload
  或 magnet URI。
- 不新增 controller、BFF route、Service、Pod、PVC、中间件或 HTTPRoute。
- 不构造真实 provider write、provider search、magnet link 或 external provider fixture。
- 不接入新的 queue consumer、scheduler 或 worker/job audit 事件。

## 验收标准

```text
MARK 1/4 magnet-service handler request writes request_source
MARK 2/4 magnet-service audit redacts query token
MARK 3/4 magnet-service audit excludes body Authorization and Cookie
MARK 4/4 magnet-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 magnet-service pod is Ready
MARK 2/4 magnet-service provider list request returns success
MARK 3/4 magnet-service live audit row redacts query token
MARK 4/4 magnet-service live smoke cleanup leaves no DB residue
```

## 测试边界

```text
.\gradlew.bat :services:ircs-magnet-service:test --rerun-tasks
.\scripts\smoke\magnet-service-audit-02812-smoke.ps1
```

live smoke 只能调用 `/api/v1/magnet-providers` 只读端点，并在 query string 中放入用于审计
脱敏验证的 `token` 参数；不执行 provider create/update、`/magnets/search/unified/{id}` 或
magnet link 写入。

## 影响范围

```text
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetServiceAuditConfiguration.java
services/ircs-magnet-service/src/test/java/com/prodigalgal/ircs/magnet/MagnetServiceAuditFilterTest.java
services/ircs-magnet-service/build.gradle
scripts/smoke/magnet-service-audit-02812-smoke.ps1
docs/reports/070-02812-magnet-service-audit-handler.md
docs/migrations/070-magnet-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 实现结果

- `services/ircs-magnet-service/build.gradle` 增加 `shared:ircs-common` 依赖。
- 新增 `MagnetServiceAuditConfiguration`，为 `ircs-magnet-service` 注册
  `ServiceRequestAuditFilter`。
- 新增 `MagnetServiceAuditFilterTest`，覆盖 provider write handler audit 写入、query
  token/password 脱敏、request body / Authorization / Cookie / response content 排除，以及
  actuator 噪音排除。
- 新增 `scripts/smoke/magnet-service-audit-02812-smoke.ps1`，通过只读 provider list endpoint
  完成 live audit 与 query token 脱敏验证。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-magnet-service:test --rerun-tasks
BUILD SUCCESSFUL in 9s

MagnetServiceAuditFilterTest tests=2, failures=0, errors=0
magnet-service total tests=17, failures=0, errors=0, skipped=0
```

ARM64 image 与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-magnet-service' -ImageName ircs-magnet-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-magnet-service
kubectl -n ircs-dev rollout status deployment/ircs-magnet-service --timeout=240s

deployment/ircs-magnet-service successfully rolled out
pod: ircs-magnet-service-569d445dd9-pmc7f Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-magnet-service@sha256:8adacd816cd0e71008924b554a9c444ace1878e6d1ed2197ff6edc2ec5ff1ad1
```

live smoke：

```text
.\scripts\smoke\magnet-service-audit-02812-smoke.ps1
MARK 1/4 magnet-service pod is Ready
MARK 2/4 magnet-service provider list request returns success
MARK 3/4 magnet-service live audit row redacts query token
MARK 4/4 magnet-service live smoke cleanup leaves no DB residue

trace: 02812-magnet-audit-20260608150827
```

后置检查：

```text
request_audit_logs magnet-service 02812 residue: 0
local magnet-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

MARK：

```text
MARK 1/4 magnet-service handler request writes request_source
MARK 2/4 magnet-service audit redacts query token
MARK 3/4 magnet-service audit excludes body Authorization and Cookie
MARK 4/4 magnet-service audit filter skips actuator noise
MARK 1/4 magnet-service pod is Ready
MARK 2/4 magnet-service provider list request returns success
MARK 3/4 magnet-service live audit row redacts query token
MARK 4/4 magnet-service live smoke cleanup leaves no DB residue
```
