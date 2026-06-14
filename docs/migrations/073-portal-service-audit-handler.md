# 073 portal-service handler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 扩展 service handler audit 铺开范围，将 `ircs-portal-service` 接入
`ServiceRequestAuditFilter`，让 portal metadata/home/explore/detail 相关请求在
`request_audit_logs` 中带上 `request_source=ircs-portal-service`。本切片复用 shared
query parameter 脱敏，避免 token、password、secret、api_key 等 query 参数写入审计表。

## 代码范围

```text
services/ircs-portal-service/src/main/java/com/prodigalgal/ircs/portal/PortalServiceAuditConfiguration.java
services/ircs-portal-service/src/test/java/com/prodigalgal/ircs/portal/PortalServiceAuditFilterTest.java
services/ircs-portal-service/build.gradle
scripts/smoke/portal-service-audit-02812-smoke.ps1
docs/reports/073-02812-portal-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险控制

- 只写请求元数据，不记录 request body 或 response body。
- 复用 shared `RequestAuditSanitizer`，对敏感 query 参数做值脱敏。
- 本地测试显式带入 Authorization、Cookie 和 query token，并断言 audit 表不包含这些值。
- live smoke 只访问 metadata endpoint，不触发写入、缓存刷新、queue publish 或 fixture。
- live smoke 使用唯一 `X-Trace-Id`，结束后删除本轮 audit fixture。

## 迁移结果

```text
MARK 1/4 portal-service handler request writes request_source
MARK 2/4 portal-service audit redacts query token
MARK 3/4 portal-service audit excludes Authorization and Cookie
MARK 4/4 portal-service audit filter skips actuator noise
```

live smoke：

```text
MARK 1/4 portal-service pod is Ready
MARK 2/4 portal-service metadata request returns success
MARK 3/4 portal-service live audit row redacts query token
MARK 4/4 portal-service live smoke cleanup leaves no DB residue
```

验证证据：

```text
.\gradlew.bat :services:ircs-portal-service:test --rerun-tasks
BUILD SUCCESSFUL in 9s

ircs-portal-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-portal-service@sha256:6d10a946fce61b6930806ee4958c8e51ccee7243c55af90f71a6521b99bc4dc2

trace: 02812-portal-audit-20260608155128
residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
