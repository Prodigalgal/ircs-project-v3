# 071 storage-service handler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 扩展 service handler audit 铺开范围，将 `ircs-storage-service` 接入
`ServiceRequestAuditFilter`，让 media、cover image、avatar storage 相关请求在
`request_audit_logs` 中带上 `request_source=ircs-storage-service`。本切片复用 shared
query parameter 脱敏，避免 token、password、secret、api_key 等 query 参数写入审计表。

## 代码范围

```text
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/StorageServiceAuditConfiguration.java
services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/StorageServiceAuditFilterTest.java
services/ircs-storage-service/build.gradle
scripts/smoke/storage-service-audit-02812-smoke.ps1
docs/reports/071-02812-storage-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险控制

- 只写请求元数据，不记录 request body 或 response body。
- 复用 shared `RequestAuditSanitizer`，对敏感 query 参数做值脱敏。
- 本地测试显式带入 request body、Authorization、Cookie 和 query token，并断言 audit 表不包含这些值。
- live smoke 只访问 cover image list endpoint，不触发 upload、download、delete、retry 或 R2 sync。
- live smoke 使用唯一 `X-Trace-Id`，结束后删除本轮 audit fixture。

## 迁移结果

```text
MARK 1/4 storage-service handler request writes request_source
MARK 2/4 storage-service audit redacts query token
MARK 3/4 storage-service audit excludes body Authorization and Cookie
MARK 4/4 storage-service audit filter skips actuator noise
```

live smoke：

```text
MARK 1/4 storage-service pod is Ready
MARK 2/4 storage-service cover image list request returns success
MARK 3/4 storage-service live audit row redacts query token
MARK 4/4 storage-service live smoke cleanup leaves no DB residue
```

验证证据：

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL in 10s

ircs-storage-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:89408df252a2fb53ad6a939fae66427f78bbd8808791fa4afd941e1a6c487ff2

trace: 02812-storage-audit-20260608152804
residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
