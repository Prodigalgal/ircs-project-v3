# 066 content-service handler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 扩展 service handler audit 铺开范围，将 `ircs-content-service` 接入
`ServiceRequestAuditFilter`，让 admin/content 请求在 `request_audit_logs` 中带上
`request_source=ircs-content-service`。

## 代码范围

```text
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/ContentServiceAuditConfiguration.java
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/people/ContentServiceAuditFilterTest.java
services/ircs-content-service/build.gradle
scripts/smoke/content-service-audit-02812-smoke.ps1
docs/reports/066-02812-content-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险控制

- 复用 shared `ServiceRequestAuditFilter`，不新增 audit 表结构。
- 只写请求元数据，不记录 body、Authorization、Cookie、credential 或 token。
- actuator、static、assets 和 audit 查询噪音继续由 shared filter 排除。
- live smoke 只通过临时 port-forward 访问既有 ClusterIP Service，不新增 HTTPRoute 或常驻资源。
- smoke 使用唯一 `X-Trace-Id`，结束后删除本轮 audit fixture。

## 迁移结果

```text
MARK 1/2 content-service handler request writes request_source
MARK 2/2 content-service audit filter skips actuator noise
```

live smoke：

```text
MARK 1/4 content-service pod is Ready
MARK 2/4 content-service live handler request returns success
MARK 3/4 content-service live audit row is queryable by trace_id
MARK 4/4 content-service live smoke cleanup leaves no DB residue
```

验证证据：

```text
.\gradlew.bat :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL in 15s

ircs-content-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-content-service@sha256:5557e950adbce042a9d51a15fe551ae9cefe86227be50b9e52ef976a68ade42a

trace: 02812-content-audit-20260608113559
residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
