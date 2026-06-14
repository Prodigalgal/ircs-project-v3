# 069 028-12 interaction-service handler audit 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 背景

`028-12` 已完成 config-service、catalog-service、content-service、credential-service、
identity-service handler audit，并补齐 notification worker/job audit 的写入、ops 查询和
live smoke。下一步继续把 service handler audit 铺到 portal/admin 的交互链路。

`ircs-interaction-service` 承载 portal 收藏、历史、反馈墙、我的留言，以及 admin 留言管理。
这些接口已经在 `025` 路由覆盖中复现，但如果缺少 service handler audit，portal/admin 只能
证明 BFF 入口被访问，不能证明请求落到 interaction-service handler 并产生可查询证据。
该服务也支持 query parameter `token` 作为 member JWT，因此本轮必须继续验证敏感 query
参数脱敏。

## 目标

- 为 `ircs-interaction-service` 接入 `ServiceRequestAuditFilter`。
- 让 interaction handler 请求写入 `request_audit_logs.request_source=ircs-interaction-service`。
- 本地测试证明 `Authorization`、Cookie、request body 和 query token 不进入 audit 表。
- 使用 public feedback wall endpoint 完成 live smoke，不登录、不写 member/message 数据。

## 非目标

- 不修改 interaction-service 收藏、历史、反馈、留言管理或 watch-progress 业务语义。
- 不在 audit 中记录 request body、response body、Authorization、Cookie、JWT 或反馈正文。
- 不新增 controller、BFF route、Service、Pod、PVC、中间件或 HTTPRoute。
- 不构造真实 member 登录、收藏、历史、留言或 admin reply fixture。
- 不接入新的 queue consumer 或 worker/job audit 事件。

## 验收标准

```text
MARK 1/4 interaction-service handler request writes request_source
MARK 2/4 interaction-service audit redacts query token
MARK 3/4 interaction-service audit excludes body Authorization and Cookie
MARK 4/4 interaction-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 interaction-service pod is Ready
MARK 2/4 interaction-service feedback wall request returns success
MARK 3/4 interaction-service live audit row redacts query token
MARK 4/4 interaction-service live smoke cleanup leaves no DB residue
```

## 测试边界

```text
.\gradlew.bat :services:ircs-interaction-service:test --rerun-tasks
.\scripts\smoke\interaction-service-audit-02812-smoke.ps1
```

live smoke 只调用 `/api/portal/feedback/wall`，并在 query string 中放入用于审计脱敏验证的
`token` 参数；该参数不会进入业务认证流程。

## 影响范围

```text
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/InteractionServiceAuditConfiguration.java
services/ircs-interaction-service/src/test/java/com/prodigalgal/ircs/interaction/InteractionServiceAuditFilterTest.java
services/ircs-interaction-service/build.gradle
scripts/smoke/interaction-service-audit-02812-smoke.ps1
docs/reports/069-02812-interaction-service-audit-handler.md
docs/migrations/069-interaction-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 实现结果

- `services/ircs-interaction-service/build.gradle` 增加 `shared:ircs-common` 依赖和 H2 test
  dependency。
- 新增 `InteractionServiceAuditConfiguration`，为 `ircs-interaction-service` 注册
  `ServiceRequestAuditFilter`。
- 新增 `InteractionServiceAuditFilterTest`，覆盖 feedback handler audit 写入、query
  token/password 脱敏、request body / Authorization / Cookie / response content 排除，以及
  actuator 噪音排除。
- 新增 `scripts/smoke/interaction-service-audit-02812-smoke.ps1`，通过 public feedback wall
  endpoint 完成 live audit 与 query token 脱敏验证。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL in 15s

InteractionServiceAuditFilterTest tests=2, failures=0, errors=0
interaction-service total tests=51, failures=0, errors=0, skipped=0
```

ARM64 image 与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-interaction-service' -ImageName ircs-interaction-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-interaction-service
kubectl -n ircs-dev rollout status deployment/ircs-interaction-service --timeout=240s

deployment/ircs-interaction-service successfully rolled out
pod: ircs-interaction-service-5ff44bdf48-g2ggz Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:2cd5b1086233d618ac0354be4a1c0f982a5c75793670acfcd14ca451c0492cb0
```

live smoke：

```text
.\scripts\smoke\interaction-service-audit-02812-smoke.ps1
MARK 1/4 interaction-service pod is Ready
MARK 2/4 interaction-service feedback wall request returns success
MARK 3/4 interaction-service live audit row redacts query token
MARK 4/4 interaction-service live smoke cleanup leaves no DB residue

trace: 02812-interaction-audit-20260608145509
```

后置检查：

```text
request_audit_logs interaction-service 02812 residue: 0
local interaction-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

MARK：

```text
MARK 1/4 interaction-service handler request writes request_source
MARK 2/4 interaction-service audit redacts query token
MARK 3/4 interaction-service audit excludes body Authorization and Cookie
MARK 4/4 interaction-service audit filter skips actuator noise
MARK 1/4 interaction-service pod is Ready
MARK 2/4 interaction-service feedback wall request returns success
MARK 3/4 interaction-service live audit row redacts query token
MARK 4/4 interaction-service live smoke cleanup leaves no DB residue
```
