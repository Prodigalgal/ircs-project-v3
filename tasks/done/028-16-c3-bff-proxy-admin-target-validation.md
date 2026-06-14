# 028-16-C3-BFF-PROXY admin BFF target validation task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`

## 背景

C3 已完成 shared outbound `PUBLIC_FETCH` 与 `INTERNAL_SERVICE` 多个切片，shared policy
中也已经存在 `BFF_PROXY` 类型。admin-bff / portal-bff 仍直接用 JDK `HttpClient` 按
route table 代理请求，已有 route whitelist、hop-by-hop header 过滤、redirect never 和
audit，但缺少对固定 `targetBaseUrl` 的 shared policy 校验证据。

本切片选择 `services/ircs-admin-bff`，不同时修改 portal-bff。

## 目标

- 将 admin-bff `BffProxyClient` 接入 shared `BFF_PROXY` target base URL 校验。
- 只校验 route table 中配置的固定 `targetBaseUrl`，不把用户 path/query 当任意 URL 重新解释。
- 保留现有 route table whitelist、hop-by-hop header 过滤、redirect never 和 audit 行为。
- 增加 focused no-real-network tests，覆盖 hop-by-hop header 过滤、route whitelist 和 invalid
  target base URL fail-before-send。

## 非目标

- 不修改 portal-bff。
- 不迁移 BFF transport 到 shared `JdkOutboundTransport`，因为当前 shared transport 只覆盖 GET，
  而 BFF 需要保留多 HTTP method 和 request body 转发。
- 不修改 content config、normalization、R2、SMTP。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/reports/089` 或 `docs/migrations/089`。
- 不实现 circuit breaker。

## 输入

```text
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/outbound/OutboundUrlPolicy.java
services/ircs-admin-bff/src/main/java/com/prodigalgal/ircs/adminbff/BffProxyClient.java
services/ircs-admin-bff/src/main/java/com/prodigalgal/ircs/adminbff/BffRoute*.java
services/ircs-admin-bff/src/test/java/com/prodigalgal/ircs/adminbff/*Test.java
```

## 输出

```text
admin BffProxyClient shared BFF_PROXY target base URL validation
focused no-real-network BffProxyClient tests
this C3-BFF-PROXY task spec with implementation and verification notes
```

## 影响范围

- `services/ircs-admin-bff` proxy caller。
- `shared/ircs-common` outbound policy 作为校验依赖。
- admin-bff 局部 tests。

不影响 portal-bff、storage strict download、internal service caller、R2、SMTP 和
normalization pipeline。

## 依赖

- `shared:ircs-common`
- admin-bff Spring Web / servlet test support
- JDK `HttpClient`

## 风险

- 如果把用户 path/query 一起当作 arbitrary URL 校验，会误把 BFF 代理重新变成公网 URL
  caller，也可能改变合法业务 query 的语义。
- 如果 target base URL 校验过严，可能误伤 cluster DNS 或 localhost dev URL；`BFF_PROXY`
  应只校验 `http/https`、host 必填和禁止 userinfo。
- 如果替换 transport 过大，可能破坏非 GET method、body 转发或 response header 透传。

## 验收标准

```text
C3-BFF-PROXY MARK 1/5 only admin-bff is changed
C3-BFF-PROXY MARK 2/5 BffProxyClient validates configured targetBaseUrl with shared BFF_PROXY policy
C3-BFF-PROXY MARK 3/5 user request path/query is forwarded, not treated as arbitrary target URL
C3-BFF-PROXY MARK 4/5 focused tests cover hop-by-hop header filtering plus route whitelist or invalid target base URL
C3-BFF-PROXY MARK 5/5 shared and admin-bff tests pass without real network
```

## 测试边界

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-admin-bff:test --rerun-tasks
```

测试必须使用 fake `HttpClient` / mock servlet request-response，不访问真实 upstream service，
不做公网调用。

## 实现结果

- 本切片只修改 `services/ircs-admin-bff`，未修改 `services/ircs-portal-bff`。
- `BffProxyClient` 新增 shared `OutboundUrlPolicy` 依赖，生产构造器使用
  `DefaultOutboundAddressResolver` 创建 policy，测试构造器可注入 fake `HttpClient`。
- `BffProxyClient#targetUri` 在拼接用户 path/query 前，只对 route table 中固定
  `targetBaseUrl` 调用 `validateBffProxyTarget()`。
- invalid base URL、非法 scheme、缺失 host 或 userinfo 会在发送 upstream 请求前失败，并映射为
  `502 BAD_GATEWAY` / `Invalid BFF target base URL`。
- 保留原 JDK `HttpClient` transport、`HttpClient.Redirect.NEVER`、多 method / body 转发、
  request/response hop-by-hop header 过滤和 audit finally 记录。

## 验证结果

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-admin-bff:test --rerun-tasks
BUILD SUCCESSFUL in 16s
```

focused tests：

- `BffProxyClientTest#forwardsWhitelistedRouteQueryAndFiltersHopByHopHeaders` 覆盖 route
  whitelist、用户 query 原样转发、request hop-by-hop header 过滤、response hop-by-hop header
  过滤，以及 302 response 不被 BFF client 跟随。
- `BffProxyClientTest#rejectsUnknownRouteBeforeSendingRequest` 覆盖未知 route 在发送前返回
  `404 NOT_FOUND`。
- `BffProxyClientTest#rejectsInvalidTargetBaseUrlBeforeSendingRequest` 覆盖含 userinfo 的
  `targetBaseUrl` 在发送前被 shared `BFF_PROXY` policy 阻断。
- `OutboundHttpClientTest#bffProxyPolicyValidatesConfiguredTargetBaseUrlShape` 继续覆盖 shared
  `BFF_PROXY` policy 基础 URL shape 校验。

## MARK

```text
C3-BFF-PROXY MARK 1/5 only admin-bff is changed: done
C3-BFF-PROXY MARK 2/5 BffProxyClient validates configured targetBaseUrl with shared BFF_PROXY policy: done
C3-BFF-PROXY MARK 3/5 user request path/query is forwarded, not treated as arbitrary target URL: done
C3-BFF-PROXY MARK 4/5 focused tests cover hop-by-hop header filtering plus route whitelist or invalid target base URL: done
C3-BFF-PROXY MARK 5/5 shared and admin-bff tests pass without real network: done
```

## 遗留问题

- `services/ircs-portal-bff` 仍未接入 shared `BFF_PROXY` target base URL validation。
- BFF proxy 仍未接入 circuit breaker；建议后续统一设计 caller/host key 和 fake failure 计数。
- shared `JdkOutboundTransport` 当前只支持 GET；如后续要让 BFF transport 也复用 shared client，
  需要先扩展 method/body/streaming response 能力，并保持 hop-by-hop header 过滤。

## 当前状态

已完成，待主控/文档 worker 汇总到 C3 父任务和 089 全局报告。
