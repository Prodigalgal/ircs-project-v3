# 067 028-12 credential-service handler audit 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 背景

`028-12` 已完成 `config-service`、`catalog-service`、`content-service` 的 HTTP handler
audit 铺开，以及 notification worker/job audit 的写入、ops 查询和 live smoke。下一步需要
覆盖 `ircs-credential-service`，使 portal/admin 在验证 credential 管理行为时能同时看到服务
handler 层请求证据。

credential-service 与其他业务服务不同：public write API 的 secret 在 JSON body，
internal lease API 的 token 在 `X-IRCS-INTERNAL-TOKEN` header，lease response 中可能包含
provider secret payload。因此本切片必须把敏感边界作为验收重点，而不是只证明 audit 能写入。

## 目标

- 为 `ircs-credential-service` 接入 `ServiceRequestAuditFilter`。
- 记录 `request_audit_logs.request_source=ircs-credential-service`。
- 本地测试证明 request body、Authorization、Cookie 和 internal token 不进入 audit 表。
- 使用安全只读模板 endpoint 完成 live smoke，不读取真实 credential secret。

## 非目标

- 不修改 credential-service 业务语义、credential sanitizer 或 lease response。
- 不在 audit 中记录 request body、response body、Authorization、Cookie、internal token 或 secret payload。
- 不新增 controller、BFF route、Service、Pod 或 HTTPRoute。
- 不构造或写入真实 credential fixture。
- 不调用 internal lease endpoint 做 live smoke。

## 验收标准

```text
MARK 1/3 credential-service handler request writes request_source
MARK 2/3 credential-service audit excludes body Authorization Cookie and internal token
MARK 3/3 credential-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 credential-service pod is Ready
MARK 2/4 credential-service safe template request returns success
MARK 3/4 credential-service live audit row is queryable by trace_id
MARK 4/4 credential-service live smoke cleanup leaves no DB residue
```

## 测试边界

```text
.\gradlew.bat :services:ircs-credential-service:test --rerun-tasks
.\scripts\smoke\credential-service-audit-02812-smoke.ps1
```

本轮 local test 可以模拟 credential write body，但 live smoke 只能打
`/api/v1/credentials/templates?provider=TMDB`，避免读取或写入真实 credential secret。

## 影响范围

```text
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialServiceAuditConfiguration.java
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/CredentialServiceAuditFilterTest.java
services/ircs-credential-service/build.gradle
scripts/smoke/credential-service-audit-02812-smoke.ps1
docs/reports/067-02812-credential-service-audit-handler.md
docs/migrations/067-credential-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 实现结果

- 新增 `CredentialServiceAuditConfiguration`，为 `ircs-credential-service` 注册
  `ServiceRequestAuditFilter`。
- 新增 `CredentialServiceAuditFilterTest`，覆盖 public credential write 请求写入、
  internal lease unauthorized 请求写入、敏感字段排除和 actuator 噪音排除。
- `services/ircs-credential-service/build.gradle` 增加 H2 test dependency。
- 新增 `scripts/smoke/credential-service-audit-02812-smoke.ps1`，通过安全模板 endpoint
  完成 live audit 验证，不读取或写入真实 credential。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL in 13s

CredentialServiceAuditFilterTest tests=3, failures=0, errors=0
credential-service total tests=29, failures=0, errors=0, skipped=0
```

ARM64 image 与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-credential-service' -ImageName ircs-credential-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-credential-service
kubectl -n ircs-dev rollout status deployment/ircs-credential-service --timeout=240s

deployment/ircs-credential-service successfully rolled out
pod: ircs-credential-service-84449db48c-s57wq Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-credential-service@sha256:634f635076842a4a0b4518ef1ea5ca0beaee8980e80544f15c5eebb3ff750e96
```

live smoke：

```text
.\scripts\smoke\credential-service-audit-02812-smoke.ps1
MARK 1/4 credential-service pod is Ready
MARK 2/4 credential-service safe template request returns success
MARK 3/4 credential-service live audit row is queryable by trace_id
MARK 4/4 credential-service live smoke cleanup leaves no DB residue

trace: 02812-credential-audit-20260608115419
```

后置检查：

```text
request_audit_logs credential-service 02812 residue: 0
local credential-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

MARK：

```text
MARK 1/3 credential-service handler request writes request_source
MARK 2/3 credential-service audit excludes body Authorization Cookie and internal token
MARK 3/3 credential-service audit filter skips actuator noise
MARK 1/4 credential-service pod is Ready
MARK 2/4 credential-service safe template request returns success
MARK 3/4 credential-service live audit row is queryable by trace_id
MARK 4/4 credential-service live smoke cleanup leaves no DB residue
```
