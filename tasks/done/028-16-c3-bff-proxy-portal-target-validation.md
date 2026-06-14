# 028-16-C3-BFF-PROXY portal BFF target validation task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`
- `tasks/done/028-16-c3-bff-proxy-admin-target-validation.md`

## 背景

C3 已完成 shared outbound `PUBLIC_FETCH`、`INTERNAL_SERVICE` 与 admin-bff
`BFF_PROXY` target validation 第一刀。portal-bff 仍直接使用 JDK `HttpClient` 按 route
table 代理到 identity、interaction、portal、search 和 storage，已有 route whitelist、
hop-by-hop header 过滤、redirect never 和 audit，但缺少对固定 `targetBaseUrl` 的
shared `BFF_PROXY` policy 校验证据。

本切片只处理 `services/ircs-portal-bff`，不触碰 admin-bff。

## 目标

- 将 portal-bff `BffProxyClient` 接入 shared `BFF_PROXY` target base URL 校验。
- 只校验 route table 中配置的固定 `targetBaseUrl`，不把用户 path/query 当任意 URL。
- 保留现有 route whitelist、request/response hop-by-hop header 过滤、redirect never、
  多 method/body 转发和 audit。
- 增加 no-real-network focused tests，至少覆盖 query/path 原样、hop-by-hop header、
  invalid target fail-before-send、route whitelist 中两个。

## 非目标

- 不修改 admin-bff。
- 不迁移 BFF transport 到 shared `JdkOutboundTransport`，因为 BFF 仍需要多 HTTP method、
  request body 和 streaming response 转发。
- 不修改 content config、normalization、R2、SMTP。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/reports/089` 或 `docs/migrations/089`。
- 不实现 circuit breaker。

## 输入

```text
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/outbound/OutboundUrlPolicy.java
services/ircs-portal-bff/src/main/java/com/prodigalgal/ircs/portalbff/BffProxyClient.java
services/ircs-portal-bff/src/main/java/com/prodigalgal/ircs/portalbff/BffRoute*.java
services/ircs-portal-bff/src/test/java/com/prodigalgal/ircs/portalbff/*Test.java
```

## 输出

```text
portal BffProxyClient shared BFF_PROXY target base URL validation
focused no-real-network BffProxyClient tests
this C3-BFF-PROXY portal task spec with implementation and verification notes
```

## 影响范围

- `services/ircs-portal-bff` proxy caller。
- `shared/ircs-common` outbound policy 作为校验依赖。
- portal-bff 局部 tests。

不影响 admin-bff、storage strict download、internal service caller、R2、SMTP、
normalization pipeline 或 content config。

## 依赖

- `shared:ircs-common`
- portal-bff Spring Web / servlet test support
- JDK `HttpClient`

## 风险

- 如果把用户 path/query 一起当 arbitrary URL 校验，可能改变合法业务 query 的透传语义。
- 如果 target base URL 校验过严，可能误伤 cluster DNS 或 localhost dev URL；`BFF_PROXY`
  应只校验 `http/https`、host 必填和禁止 userinfo。
- 如果替换 transport 过大，可能破坏非 GET method、body 转发或 response header 透传。

## 验收标准

```text
C3-BFF-PROXY-PORTAL MARK 1/5 only portal-bff is changed
C3-BFF-PROXY-PORTAL MARK 2/5 BffProxyClient validates configured targetBaseUrl with shared BFF_PROXY policy
C3-BFF-PROXY-PORTAL MARK 3/5 user request path/query is forwarded, not treated as arbitrary target URL
C3-BFF-PROXY-PORTAL MARK 4/5 focused tests cover no-real-network route/header/invalid-target behavior
C3-BFF-PROXY-PORTAL MARK 5/5 shared and portal-bff tests pass without real network
```

## 测试边界

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-portal-bff:test --rerun-tasks
```

测试必须使用 fake `HttpClient` / mock servlet request-response，不访问真实 upstream service，
不做公网调用。

## 实现结果

- 本切片只修改 `services/ircs-portal-bff`，未修改 `services/ircs-admin-bff`。
- `BffProxyClient` 新增 shared `OutboundUrlPolicy` 依赖，生产构造器使用
  `DefaultOutboundAddressResolver` 创建 policy，测试构造器可注入 fake `HttpClient`。
- `BffProxyClient#targetUri` 在拼接用户 path/query 前，只对 route table 中固定
  `targetBaseUrl` 调用 `validateBffProxyTarget()`。
- invalid base URL、非法 scheme、缺失 host 或 userinfo 会在发送 upstream 请求前失败，并映射为
  `502 BAD_GATEWAY` / `Invalid BFF target base URL`。
- 保留原 JDK `HttpClient` transport、`HttpClient.Redirect.NEVER`、多 method / body 转发、
  request/response hop-by-hop header 过滤和 audit finally 记录。
- request hop-by-hop header 过滤补齐 `Expect`，与 admin-bff 已完成切片保持一致。

## 验证结果

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-portal-bff:test --rerun-tasks
BUILD SUCCESSFUL in 16s
```

focused tests：

- `BffProxyClientTest#forwardsWhitelistedRoutePathQueryAndFiltersHopByHopHeaders` 覆盖 route
  whitelist、最长前缀路由优先、用户 path/query 原样转发、request hop-by-hop header 过滤、
  response hop-by-hop header 过滤，以及 302 response 不被 BFF client 跟随。
- `BffProxyClientTest#rejectsUnknownRouteBeforeSendingRequest` 覆盖未知 route 在发送前返回
  `404 NOT_FOUND`。
- `BffProxyClientTest#rejectsInvalidTargetBaseUrlBeforeSendingRequest` 覆盖含 userinfo 的
  `targetBaseUrl` 在发送前被 shared `BFF_PROXY` policy 阻断。
- `OutboundHttpClientTest#bffProxyPolicyValidatesConfiguredTargetBaseUrlShape` 继续覆盖 shared
  `BFF_PROXY` policy 基础 URL shape 校验。

## MARK

```text
C3-BFF-PROXY-PORTAL MARK 1/5 only portal-bff is changed: done
C3-BFF-PROXY-PORTAL MARK 2/5 BffProxyClient validates configured targetBaseUrl with shared BFF_PROXY policy: done
C3-BFF-PROXY-PORTAL MARK 3/5 user request path/query is forwarded, not treated as arbitrary target URL: done
C3-BFF-PROXY-PORTAL MARK 4/5 focused tests cover no-real-network route/header/invalid-target behavior: done
C3-BFF-PROXY-PORTAL MARK 5/5 shared and portal-bff tests pass without real network: done
```

## 遗留问题

- BFF proxy 仍未接入 circuit breaker；建议后续统一设计 caller/host key 和 fake failure 计数。
- shared `JdkOutboundTransport` 当前只支持 GET；如后续要让 BFF transport 也复用 shared client，
  需要先扩展 method/body/streaming response 能力，并保持 hop-by-hop header 过滤。
- C3 线仍有 storage `IMAGE_DOWNLOAD_STRICT` adapter、notification MAIL credential lease、
  task/content/identity internal caller 等剩余风险，需由后续独立切片承接。

## 当前状态

已完成，待主控/文档 worker 汇总到 C3 父任务和 089 全局报告。
