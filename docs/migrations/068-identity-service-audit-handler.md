# 068 identity-service handler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` 扩展 service handler audit 铺开范围，将 `ircs-identity-service` 接入
`ServiceRequestAuditFilter`，并在 shared audit filter 中增加敏感 query parameter
脱敏，避免 JWT、reset token、password 或 secret 通过 `query_string` 写入
`request_audit_logs`。

## 代码范围

```text
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/audit/ServiceRequestAuditFilter.java
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/audit/BffRequestAuditWriter.java
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/audit/RequestAuditSanitizer.java
shared/ircs-common/src/test/java/com/prodigalgal/ircs/common/audit/BffRequestAuditWriterTest.java
shared/ircs-common/src/test/java/com/prodigalgal/ircs/common/audit/RequestAuditSanitizerTest.java
services/ircs-identity-service/src/main/java/com/prodigalgal/ircs/identity/IdentityServiceAuditConfiguration.java
services/ircs-identity-service/src/test/java/com/prodigalgal/ircs/identity/IdentityServiceAuditFilterTest.java
services/ircs-identity-service/build.gradle
scripts/smoke/identity-service-audit-02812-smoke.ps1
docs/reports/068-02812-identity-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 风险控制

- 只写请求元数据，不记录 request body 或 response body。
- 对 query string 中的敏感 key 做值脱敏，保留非敏感 query 参数用于排障。
- 本地测试显式带入 password body、Authorization、Cookie 和 query token，并断言 audit 表不包含这些值。
- live smoke 只访问 public PoW endpoint，不执行真实登录、注册、改密或头像上传。
- live smoke 使用唯一 `X-Trace-Id`，结束后删除本轮 audit fixture。
- live smoke 避开被 `JwtAuthenticationFilter` 消费的精确 `token` 参数，使用
  `access_token` 覆盖 token 类 query key 脱敏；精确 `token` 参数由本地单测覆盖。

## 迁移结果

```text
MARK 1/4 shared audit redacts sensitive query parameters
MARK 2/4 identity-service handler request writes request_source
MARK 3/4 identity-service audit excludes body Authorization Cookie and query token
MARK 4/4 identity-service audit filter skips actuator noise
```

live smoke：

```text
MARK 1/4 identity-service pod is Ready
MARK 2/4 identity-service PoW request returns success
MARK 3/4 identity-service live audit row redacts query token
MARK 4/4 identity-service live smoke cleanup leaves no DB residue
```

验证证据：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-identity-service:test --rerun-tasks
BUILD SUCCESSFUL in 12s

ircs-identity-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-identity-service@sha256:09d4574336c51d54c7716e8d6697fad8a063e0f770c0caa74f1afa49ef9481c7

trace: 02812-identity-audit-20260608144010
residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
