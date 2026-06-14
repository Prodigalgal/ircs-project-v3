# 072 028-12 search-service handler audit 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 背景

`028-12` 已完成 config-service、catalog-service、content-service、credential-service、
identity-service、interaction-service、magnet-service、storage-service 的 service handler audit，
并完成 notification worker/job audit、ops 查询和 live smoke。下一步选择
`ircs-search-service`，因为它承载 portal search suggest/recommendations API，也是
normalization、metadata result、aggregation 等内部链路最终刷新 Elasticsearch 的下游服务。

`028-09` 已通过多组 live smoke 验证 search producer 到 search-service consumer 的索引刷新副作用。
补齐 search-service handler audit 后，portal/admin 可以同时追踪 BFF 入口请求和 search-service
handler 请求，后续排查搜索响应、ES 失败降级和推荐列表差异会更直接。

## 目标

- 为 `ircs-search-service` 接入 `ServiceRequestAuditFilter`。
- 让 portal search handler 请求写入 `request_audit_logs.request_source=ircs-search-service`。
- 本地测试证明 Authorization、Cookie 和 query token 不进入 audit 表。
- 使用只读 `suggest` endpoint 完成 live smoke，不触发 search sync consumer、ES 写入或 queue publish。

## 非目标

- 不修改 search-service suggest/recommendations 业务语义。
- 不修改 search sync consumer、legacy UUID parser、Elasticsearch document mapping 或 index 写入。
- 不在 audit 中记录 Authorization、Cookie、JWT、query token、ES credential 或 response body。
- 不新增 controller、BFF route、Service、Pod、PVC、中间件或 HTTPRoute。
- 不构造 DB/ES/queue fixture。

## 验收标准

```text
MARK 1/4 search-service handler request writes request_source
MARK 2/4 search-service audit redacts query token
MARK 3/4 search-service audit excludes Authorization and Cookie
MARK 4/4 search-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 search-service pod is Ready
MARK 2/4 search-service suggest request returns success
MARK 3/4 search-service live audit row redacts query token
MARK 4/4 search-service live smoke cleanup leaves no DB residue
```

## 测试边界

```text
.\gradlew.bat :services:ircs-search-service:test --rerun-tasks
.\scripts\smoke\search-service-audit-02812-smoke.ps1
```

live smoke 只能调用 `/api/portal/search/suggest` 只读端点，并在 query string 中放入用于审计脱敏验证的
`token` 参数；不执行 recommendations fixture、search sync queue 投递或 ES 文档写入。

## 影响范围

```text
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/SearchServiceAuditConfiguration.java
services/ircs-search-service/src/test/java/com/prodigalgal/ircs/search/SearchServiceAuditFilterTest.java
services/ircs-search-service/build.gradle
scripts/smoke/search-service-audit-02812-smoke.ps1
docs/reports/072-02812-search-service-audit-handler.md
docs/migrations/072-search-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 当前状态

已完成。

## 实现结果

- 新增 `SearchServiceAuditConfiguration`，为 `ircs-search-service` 注册
  `ServiceRequestAuditFilter`。
- `services/ircs-search-service/build.gradle` 增加 H2 测试依赖，用于本地 audit table 验证。
- 新增 `SearchServiceAuditFilterTest`，覆盖 search suggest handler audit 写入、query
  token/password 脱敏、Authorization / Cookie / response content 排除，以及 actuator 噪音排除。
- 新增 `scripts/smoke/search-service-audit-02812-smoke.ps1`，通过只读 suggest endpoint
  完成 live audit 与 query token 脱敏验证。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL in 11s

SearchServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
search-service total tests=15, failures=0, errors=0, skipped=0
```

ARM64 image 与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-search-service' -ImageName ircs-search-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-search-service
kubectl -n ircs-dev rollout status deployment/ircs-search-service --timeout=240s

deployment/ircs-search-service successfully rolled out
pod: ircs-search-service-5477cf5d99-8wnhm Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-search-service@sha256:228bad735e35e0ac36d2303ac0bce67fe438a3598bbd4d460fd59f0ded5faefe
```

live smoke：

```text
.\scripts\smoke\search-service-audit-02812-smoke.ps1
MARK 1/4 search-service pod is Ready
MARK 2/4 search-service suggest request returns success
MARK 3/4 search-service live audit row redacts query token
MARK 4/4 search-service live smoke cleanup leaves no DB residue

trace: 02812-search-audit-20260608154116
```

后置检查：

```text
request_audit_logs search-service 02812 residue: 0
local search-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

MARK：

```text
MARK 1/4 search-service handler request writes request_source
MARK 2/4 search-service audit redacts query token
MARK 3/4 search-service audit excludes Authorization and Cookie
MARK 4/4 search-service audit filter skips actuator noise
MARK 1/4 search-service pod is Ready
MARK 2/4 search-service suggest request returns success
MARK 3/4 search-service live audit row redacts query token
MARK 4/4 search-service live smoke cleanup leaves no DB residue
```
