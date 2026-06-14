# 028-16-C3-S9 BFF shared transport method/body/streaming adoption task spec

状态：完成实现，统一测试后置

父任务：

- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`
- `tasks/backlog/028-16-f6-c3-outbound-adoption-golden-p1p2-backlog.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

## 背景

C3-S6/S7/S8 已完成 shared outbound circuit breaker、content internal caller、task-service
POST JSON caller 和 identity multipart avatar storage caller。admin/portal BFF 之前已完成
`BFF_PROXY` target base URL validation，但仍直接持有 JDK `HttpClient`，method/body/response
streaming 没有纳入 shared outbound transport 与 circuit caller key 口径。

## 目标

- 梳理 admin/portal BFF 当前下游转发点，并让主要 BFF proxy path 通过 shared outbound
  streaming adapter 发起请求。
- 保留 method、body、request header、response status/header 与 response body streaming 语义。
- 让 BFF caller key、request timeout 和 circuit breaker 参数支持 ENV/K8S 覆盖。
- 回写 C3 父任务、F6 backlog、089/093 总账和统一测试后置清单。

## 非目标

- 不修改 notification、normalization、catalog/C1、metadata provider 或 storage R2。
- 不做 live 门户 smoke、真实门户访问或全量 build。
- 不把 storage cover download 改成 byte-array transport；BFF 本轮使用 streaming adapter。

## 影响范围

```text
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/outbound/**
shared/ircs-common/src/test/java/com/prodigalgal/ircs/common/outbound/**
services/ircs-admin-bff/src/main/java/com/prodigalgal/ircs/adminbff/**
services/ircs-admin-bff/src/test/java/com/prodigalgal/ircs/adminbff/**
services/ircs-admin-bff/src/main/resources/application.yaml
services/ircs-portal-bff/src/main/java/com/prodigalgal/ircs/portalbff/**
services/ircs-portal-bff/src/test/java/com/prodigalgal/ircs/portalbff/**
services/ircs-portal-bff/src/main/resources/application.yaml
docs/reports/089-v1-golden-behavior-inventory*.md
docs/reports/093-v1-v3-parity-progress-ledger.md
tasks/**
```

## 验收标准

```text
C3-S9 MARK 1/7 BFF transport dispersal inventory completed
C3-S9 MARK 2/7 shared outbound supports BFF_PROXY arbitrary method/body with streaming response
C3-S9 MARK 3/7 admin-bff uses shared BFF_PROXY streaming adapter without losing route/query/header/status behavior
C3-S9 MARK 4/7 portal-bff uses shared BFF_PROXY streaming adapter without losing route/query/header/status behavior
C3-S9 MARK 5/7 caller key timeout circuit config support ENV/K8S override
C3-S9 MARK 6/7 focused no-real-network tests pass or are recorded as deferred
C3-S9 MARK 7/7 docs/tasks/089/093 ledgers updated with unified testing deferred
```

## 测试方式

优先运行 focused no-real-network tests：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-admin-bff:test :services:ircs-portal-bff:test --rerun-tasks
```

如耗时或环境阻塞，按用户当前策略记录到统一测试批次，不做 live smoke。

## 2026-06-10 实现结果

- `shared/ircs-common` 新增 streaming transport contract：
  `OutboundStreamingHttpClient`、`OutboundStreamingTransport`、
  `OutboundStreamingHttpResponse` 和 `JdkOutboundStreamingTransport`。
- `JdkOutboundTransport` 与新的 streaming transport 均支持任意 HTTP method + byte body；
  BFF 的 `GET`/`HEAD`/`OPTIONS` 仍无 body，`POST`/`PUT`/`PATCH`/`DELETE` 等继续转发
  request body。
- admin-bff 与 portal-bff `BffProxyClient` 已从直接持有 JDK `HttpClient` 改为 shared
  `OutboundStreamingHttpClient` + `BFF_PROXY` policy。
- response 继续以 `InputStream` 写回 servlet response，保留 SSE/download/debug-stream 等
  streaming 语义；本轮不把 BFF response 改成 byte-array，也不改变 storage cover download
  的 strict streaming 边界。
- request hop-by-hop headers 仍过滤；response status/header/body 继续透传，response
  hop-by-hop headers 仍过滤。
- invalid `targetBaseUrl` 仍在发送前映射为 `502 BAD_GATEWAY` /
  `Invalid BFF target base URL`；shared outbound open circuit / URL shape 等错误映射为
  `502 BAD_GATEWAY` / `BFF proxy upstream unavailable`。
- admin/portal BFF 增加 ENV/K8S 覆盖项：
  `APP_BFF_PROXY_CONNECT_TIMEOUT`、`APP_BFF_PROXY_REQUEST_TIMEOUT`、
  `APP_BFF_PROXY_CIRCUIT_KEY`、`APP_BFF_PROXY_CIRCUIT_ENABLED`、
  `APP_BFF_PROXY_CIRCUIT_FAILURE_THRESHOLD`、`APP_BFF_PROXY_CIRCUIT_OPEN_DURATION`、
  `APP_BFF_PROXY_CIRCUIT_HALF_OPEN_MAX_CALLS`；未设置 caller-specific circuit ENV 时回落到
  `IRCS_OUTBOUND_CIRCUIT_*` global ENV。

## 验证

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-admin-bff:test :services:ircs-portal-bff:test --rerun-tasks
BUILD SUCCESSFUL in 19s
```

说明：首次沙箱内运行因 `.gradle` wrapper lock/cache 写入用户目录被拒绝；已按权限规则用同一
focused test 命令在沙箱外重跑通过。配置补充 global fallback 后再次重跑仍通过。未执行 live
门户 smoke、真实门户访问或全量 build。

## 当前 MARK

```text
C3-S9 MARK 1/7 BFF transport dispersal inventory completed: done
C3-S9 MARK 2/7 shared outbound supports BFF_PROXY arbitrary method/body with streaming response: done
C3-S9 MARK 3/7 admin-bff uses shared BFF_PROXY streaming adapter without losing route/query/header/status behavior: done
C3-S9 MARK 4/7 portal-bff uses shared BFF_PROXY streaming adapter without losing route/query/header/status behavior: done
C3-S9 MARK 5/7 caller key timeout circuit config support ENV/K8S override: done
C3-S9 MARK 6/7 focused no-real-network tests pass or are recorded as deferred: done
C3-S9 MARK 7/7 docs/tasks/089/093 ledgers updated with unified testing deferred: done
```

## 统一测试后置

- `UTB-02816-P1P2-02` 统一覆盖 shared common、admin-bff、portal-bff、identity、storage
  outbound focused tests 和 dev-safe BFF smoke。
- 本轮未做 portal/admin 真实页面访问、SSE/download/debug-stream live path、open circuit
  K8S 参数矩阵或全量 build。
