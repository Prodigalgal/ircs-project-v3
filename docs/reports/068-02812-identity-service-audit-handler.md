# 068 028-12 identity-service handler audit 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 背景

`028-12` 已完成 config-service、catalog-service、content-service、credential-service 的
service handler audit，以及 notification worker/job audit 的写入、ops 查询和 live smoke。
下一步需要覆盖 `ircs-identity-service`，因为它承载 admin auth、portal member auth、
profile、avatar 和 member admin，是 portal/admin 持续验证 V1 功能无损迁移的入口服务。

identity-service 的敏感边界比普通 service 更高：login/change-password/register/reset-password
等接口都有 password/token request body，profile/admin 请求会带 `Authorization`，并且
`JwtAuthenticationFilter` 支持从 query parameter `token` 解析 JWT。若直接记录原始
`query_string`，可能把 token 写入 `request_audit_logs`。

## 目标

- 为 `ircs-identity-service` 接入 `ServiceRequestAuditFilter`。
- 在 shared `ServiceRequestAuditFilter` 中脱敏敏感 query 参数，至少覆盖 `token`、
  `password`、`secret`、`api_key`、`authorization`、`cookie`。
- 本地测试证明 identity request body、Authorization、Cookie 和 query token 不进入 audit 表。
- 使用安全 public PoW endpoint 完成 live smoke，不登录、不改密码、不写真实 member 数据。

## 非目标

- 不修改 identity-service auth/profile/member 业务语义。
- 不在 audit 中记录 request body、response body、Authorization、Cookie、JWT、password、
  reset token 或 avatar 文件内容。
- 不新增 controller、BFF route、Service、Pod、PVC、中间件或 HTTPRoute。
- 不构造真实登录、注册、密码重置或头像上传 fixture。

## 验收标准

```text
MARK 1/4 shared audit redacts sensitive query parameters
MARK 2/4 identity-service handler request writes request_source
MARK 3/4 identity-service audit excludes body Authorization Cookie and query token
MARK 4/4 identity-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 identity-service pod is Ready
MARK 2/4 identity-service PoW request returns success
MARK 3/4 identity-service live audit row redacts query token
MARK 4/4 identity-service live smoke cleanup leaves no DB residue
```

## 测试边界

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-identity-service:test --rerun-tasks
.\scripts\smoke\identity-service-audit-02812-smoke.ps1
```

live smoke 只能调用 `/api/v1/auth/pow`，不执行 login/change-password/register/reset-password
或 avatar upload。

## 影响范围

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
docs/migrations/068-identity-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 实现结果

- 新增 `RequestAuditSanitizer`，统一对 service/BFF request audit 的 query string
  敏感参数值脱敏，覆盖 `token`、`password`、`secret`、`api_key`、
  `authorization` 和 `cookie`。
- `ServiceRequestAuditFilter` 与 `BffRequestAuditWriter` 均改为写入脱敏后的
  `query_string`。
- 新增 `IdentityServiceAuditConfiguration`，为 `ircs-identity-service` 注册
  `ServiceRequestAuditFilter`。
- 新增 `IdentityServiceAuditFilterTest`，覆盖 identity login handler audit 写入、
  request body / Authorization / Cookie / query token / response token 排除，以及
  actuator 噪音排除。
- 新增 `scripts/smoke/identity-service-audit-02812-smoke.ps1`，通过唯一 trace 调用
  public PoW endpoint，验证 live `request_audit_logs` 写入和 query token 类参数脱敏。

说明：live smoke 最初使用 `token=query-secret-token` 时触发
`JwtAuthenticationFilter` 的 query JWT 解析并返回 401，且未进入 request audit。最终
smoke 改用 `access_token=query-secret-token`，仍覆盖 key 包含 `token` 的脱敏逻辑；
精确 `token` 参数由本地单元测试覆盖。

## 验证结果

本地测试：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-identity-service:test --rerun-tasks
BUILD SUCCESSFUL in 12s

BffRequestAuditWriterTest tests=3, failures=0, errors=0
RequestAuditSanitizerTest tests=2, failures=0, errors=0
WorkerJobAuditWriterTest tests=3, failures=0, errors=0
IdentityServiceAuditFilterTest tests=2, failures=0, errors=0
identity-service total tests=25, failures=0, errors=0, skipped=0
```

ARM64 image 与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-identity-service' -ImageName ircs-identity-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-identity-service
kubectl -n ircs-dev rollout status deployment/ircs-identity-service --timeout=240s

deployment/ircs-identity-service successfully rolled out
pod: ircs-identity-service-7f8c65d969-f9fbt Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-identity-service@sha256:09d4574336c51d54c7716e8d6697fad8a063e0f770c0caa74f1afa49ef9481c7
```

live smoke：

```text
.\scripts\smoke\identity-service-audit-02812-smoke.ps1
MARK 1/4 identity-service pod is Ready
MARK 2/4 identity-service PoW request returns success
MARK 3/4 identity-service live audit row redacts query token
MARK 4/4 identity-service live smoke cleanup leaves no DB residue

trace: 02812-identity-audit-20260608144010
```

后置检查：

```text
request_audit_logs identity-service 02812 residue: 0
local identity-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

MARK：

```text
MARK 1/4 shared audit redacts sensitive query parameters
MARK 2/4 identity-service handler request writes request_source
MARK 3/4 identity-service audit excludes body Authorization Cookie and query token
MARK 4/4 identity-service audit filter skips actuator noise
MARK 1/4 identity-service pod is Ready
MARK 2/4 identity-service PoW request returns success
MARK 3/4 identity-service live audit row redacts query token
MARK 4/4 identity-service live smoke cleanup leaves no DB residue
```
