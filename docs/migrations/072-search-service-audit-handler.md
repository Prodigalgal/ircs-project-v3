# 072 search-service handler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 扩展 service handler audit 铺开范围，将 `ircs-search-service` 接入
`ServiceRequestAuditFilter`，让 portal search suggest/recommendations 相关请求在
`request_audit_logs` 中带上 `request_source=ircs-search-service`。本切片复用 shared
query parameter 脱敏，避免 token、password、secret、api_key 等 query 参数写入审计表。

## 代码范围

```text
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/SearchServiceAuditConfiguration.java
services/ircs-search-service/src/test/java/com/prodigalgal/ircs/search/SearchServiceAuditFilterTest.java
services/ircs-search-service/build.gradle
scripts/smoke/search-service-audit-02812-smoke.ps1
docs/reports/072-02812-search-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险控制

- 只写请求元数据，不记录 request body 或 response body。
- 复用 shared `RequestAuditSanitizer`，对敏感 query 参数做值脱敏。
- 本地测试显式带入 Authorization、Cookie 和 query token，并断言 audit 表不包含这些值。
- live smoke 只访问 suggest endpoint，不触发 search sync consumer 或 ES 写入。
- live smoke 使用唯一 `X-Trace-Id`，结束后删除本轮 audit fixture。

## 迁移结果

```text
MARK 1/4 search-service handler request writes request_source
MARK 2/4 search-service audit redacts query token
MARK 3/4 search-service audit excludes Authorization and Cookie
MARK 4/4 search-service audit filter skips actuator noise
```

live smoke：

```text
MARK 1/4 search-service pod is Ready
MARK 2/4 search-service suggest request returns success
MARK 3/4 search-service live audit row redacts query token
MARK 4/4 search-service live smoke cleanup leaves no DB residue
```

验证证据：

```text
.\gradlew.bat :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL in 11s

ircs-search-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-search-service@sha256:228bad735e35e0ac36d2303ac0bce67fe438a3598bbd4d460fd59f0ded5faefe

trace: 02812-search-audit-20260608154116
residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
