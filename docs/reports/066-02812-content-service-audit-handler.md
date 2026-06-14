# 066 028-12 content-service handler audit 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 背景

`028-12` 已完成 `config-service`、`catalog-service` 的 HTTP handler audit 第一批接入，
并完成 notification worker/job audit、ops 查询 API 和 live queue smoke。当前仍需继续把
service handler audit 铺到承载 portal/admin 核心验证路径的业务服务。

`ircs-content-service` 承载 raw/unified video、people、playlist、resolver 和 source-domain
等 admin/content 接口。HTTP 路由已经迁移，但如果缺少 service handler audit，portal/admin
后续只能证明 BFF 入口被访问，不能证明请求实际落到 content-service handler 并产生可查询
证据。

## 目标

- 为 `ircs-content-service` 接入 `ServiceRequestAuditFilter`。
- 记录 `request_audit_logs.request_source=ircs-content-service`。
- 保持不采集请求体、Authorization、Cookie、credential 或 token。
- 本地测试覆盖正常 content handler 请求写入和 actuator 噪音排除。

## 非目标

- 不修改 content-service 业务语义。
- 不新增 controller、BFF route、Service、Pod 或 HTTPRoute。
- 不接入 credential-service 敏感 handler audit。
- 不接入新的 worker/job audit。
- live smoke 只走 port-forward 到既有 ClusterIP Service，不新增公共入口。

## 验收标准

```text
MARK 1/2 content-service handler request writes request_source
MARK 2/2 content-service audit filter skips actuator noise
```

## 测试边界

```text
.\gradlew.bat :services:ircs-content-service:test --rerun-tasks
```

本轮测试只验证 handler audit 写入和过滤行为，不构造 content 数据写入 fixture，不触发
RabbitMQ、search sync、normalization 或 provider 链路。

## 影响范围

```text
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/ContentServiceAuditConfiguration.java
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/people/ContentServiceAuditFilterTest.java
services/ircs-content-service/build.gradle
scripts/smoke/content-service-audit-02812-smoke.ps1
docs/reports/066-02812-content-service-audit-handler.md
docs/migrations/066-content-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 实现结果

- 新增 `ContentServiceAuditConfiguration`，为 `ircs-content-service` 注册
  `ServiceRequestAuditFilter`。
- 新增 `ContentServiceAuditFilterTest`，使用 MockMvc + H2 PostgreSQL mode 验证
  handler audit 写入和 actuator 噪音排除。
- `services/ircs-content-service/build.gradle` 增加 H2 test dependency。
- 新增 `scripts/smoke/content-service-audit-02812-smoke.ps1`，通过唯一 `X-Trace-Id`
  调用 live content-service 只读端点，并清理本轮 audit fixture。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL in 15s

ContentServiceAuditFilterTest tests=2, failures=0, errors=0
content-service total tests=29, failures=0, errors=0, skipped=0
```

ARM64 image 与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-content-service' -ImageName ircs-content-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-content-service
kubectl -n ircs-dev rollout status deployment/ircs-content-service --timeout=240s

deployment/ircs-content-service successfully rolled out
pod: ircs-content-service-58d98958c8-pbr6v Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-content-service@sha256:5557e950adbce042a9d51a15fe551ae9cefe86227be50b9e52ef976a68ade42a
```

live smoke：

```text
.\scripts\smoke\content-service-audit-02812-smoke.ps1
MARK 1/4 content-service pod is Ready
MARK 2/4 content-service live handler request returns success
MARK 3/4 content-service live audit row is queryable by trace_id
MARK 4/4 content-service live smoke cleanup leaves no DB residue

trace: 02812-content-audit-20260608113559
```

后置检查：

```text
request_audit_logs content-service 02812 residue: 0
local content-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

MARK：

```text
MARK 1/2 content-service handler request writes request_source
MARK 2/2 content-service audit filter skips actuator noise
MARK 1/4 content-service pod is Ready
MARK 2/4 content-service live handler request returns success
MARK 3/4 content-service live audit row is queryable by trace_id
MARK 4/4 content-service live smoke cleanup leaves no DB residue
```
