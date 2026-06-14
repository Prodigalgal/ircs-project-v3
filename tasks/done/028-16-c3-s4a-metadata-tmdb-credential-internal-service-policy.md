# 028-16-C3-S4a metadata TMDB credential INTERNAL_SERVICE policy task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`

## 背景

C3 已完成 shared `PUBLIC_FETCH` contract 和 catalog、magnet、scraper、metadata TMDB public
API 第一批接入。当前仍有多处内部 service caller 直接使用 `RestClient` 或 JDK
`HttpClient`，其中 `metadata-worker/TmdbCredentialRepository` 负责从固定
credential-service base URL 租用 TMDB credential，属于内部服务调用，不应套用公网
`PUBLIC_FETCH` SSRF 策略。

## 目标

- 在 shared outbound contract 中补出 `INTERNAL_SERVICE` policy 类型，明确区分
  `PUBLIC_FETCH`、`INTERNAL_SERVICE` 和 `BFF_PROXY`。
- 将 `metadata-worker/TmdbCredentialRepository` 迁到 shared `INTERNAL_SERVICE` policy。
- 保留固定 credential-service path、`requiredPayloadKey`、`limit`、内部 token header、
  空凭据过滤和失败转 `CREDENTIAL_SERVICE_UNAVAILABLE` retryable 语义。
- 使用 fake resolver/fake transport 测试，不做真实公网或真实 credential-service 调用。

## 非目标

- 不迁移 notification-worker MAIL credential lease。
- 不迁移 task/content/identity 内部 caller。
- 不迁移 admin-bff 或 portal-bff proxy。
- 不实现 circuit breaker。
- 不修改全局索引、全局 089 report 或 migration 文档。

## 输入

```text
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/outbound/**
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbCredentialRepository.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/credential/MetadataCredentialServiceProperties.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbCredentialRepositoryTest.java
```

## 输出

```text
shared INTERNAL_SERVICE policy factory and URL validation
TmdbCredentialRepository shared outbound adapter
focused no-real-network tests for base URL validation, internal header, failure mapping
this task spec with implementation and verification notes
```

## 影响范围

- `shared/ircs-common` outbound contract。
- `ircs-metadata-worker` TMDB credential lease caller。

不影响 storage strict download、BFF route proxy、task-service readiness gate、content-service
config consumer。

## 依赖

- `shared:ircs-common`
- `shared:ircs-contracts`
- metadata-worker Jackson / Spring Boot service dependencies

## 风险

- `INTERNAL_SERVICE` 如果误用 `PUBLIC_FETCH` 会阻断 cluster DNS、localhost dev URL。
- 如果把 user path/query 拼进 target base URL 校验，可能重新引入任意 URL 风险。
- 迁移后必须保持 `X-IRCS-INTERNAL-TOKEN` header 和 credential secret payload 映射不变。

## 验收标准

```text
C3-S4a MARK 1/5 shared outbound policy can distinguish PUBLIC_FETCH, INTERNAL_SERVICE, BFF_PROXY
C3-S4a MARK 2/5 INTERNAL_SERVICE validates scheme/host/userinfo without DNS SSRF blocking
C3-S4a MARK 3/5 TmdbCredentialRepository uses shared INTERNAL_SERVICE and preserves token/header/path/query
C3-S4a MARK 4/5 credential-service failure still maps to CREDENTIAL_SERVICE_UNAVAILABLE retryable
C3-S4a MARK 5/5 shared and metadata-worker tests pass without real network
```

## 测试边界

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-metadata-worker:test --rerun-tasks
```

fake resolver/fake transport 覆盖 URL validation、header、JSON body、5xx/IO failure 映射；
不访问公网、不访问真实 credential-service。

## 实现结果

- `shared:ircs-common` 新增 `OutboundHttpPolicyType`，并在 `OutboundHttpPolicy` 中显式区分
  `PUBLIC_FETCH`、`INTERNAL_SERVICE` 和 `BFF_PROXY`。
- `OutboundHttpClient` 按 policy type 分派 URL validation：`PUBLIC_FETCH` 继续使用 DNS
  fail-closed SSRF 校验；`INTERNAL_SERVICE` 和 `BFF_PROXY` 只校验 `http/https`、host
  必填和禁止 userinfo，不阻断 cluster DNS 或 localhost dev base URL。
- `MetadataCredentialServiceProperties` 新增 `requestTimeout`，配置项为
  `app.metadata.credential-service.request-timeout` / `APP_METADATA_CREDENTIAL_SERVICE_REQUEST_TIMEOUT`，
  默认 `10s`。
- `TmdbCredentialRepository` 已从 Spring `RestClient` 迁到 shared `OutboundHttpClient` +
  `OutboundHttpPolicy.internalService()`，固定调用
  `/internal/credentials/providers/TMDB/leases?requiredPayloadKey=api_key&limit=...`。
- 保留 `X-IRCS-INTERNAL-TOKEN`、空 lease 返回空列表、空 `api_key` 过滤和失败转
  `CREDENTIAL_SERVICE_UNAVAILABLE` retryable 语义。

## 验证结果

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL in 18s
```

focused tests：

- `OutboundHttpClientTest` 覆盖 `INTERNAL_SERVICE` 允许 `credential-service` cluster DNS 和
  `localhost`，并拒绝非法 scheme / userinfo；覆盖 `BFF_PROXY` target base URL shape 校验。
- `TmdbCredentialRepositoryTest` 覆盖固定 path/query、internal token header、request timeout、
  localhost dev base URL、userinfo fail-before-transport、5xx/IO 失败映射。

## MARK

```text
C3-S4a MARK 1/5 shared outbound policy can distinguish PUBLIC_FETCH, INTERNAL_SERVICE, BFF_PROXY: done
C3-S4a MARK 2/5 INTERNAL_SERVICE validates scheme/host/userinfo without DNS SSRF blocking: done
C3-S4a MARK 3/5 TmdbCredentialRepository uses shared INTERNAL_SERVICE and preserves token/header/path/query: done
C3-S4a MARK 4/5 credential-service failure still maps to CREDENTIAL_SERVICE_UNAVAILABLE retryable: done
C3-S4a MARK 5/5 shared and metadata-worker tests pass without real network: done
```

## 遗留问题

- notification-worker MAIL credential lease 仍未接 `INTERNAL_SERVICE`。
- task-service scraper execution caller、content-service internal scraper caller 和 identity-service
  avatar storage client 仍未接 `INTERNAL_SERVICE`。
- admin-bff / portal-bff 仍未接 `BFF_PROXY` target base URL validation。
- circuit breaker 仍未落代码。
- storage `IMAGE_DOWNLOAD_STRICT` shared adapter 仍未接入。

## 当前状态

已完成，待主控/文档 worker 汇总到 C3 父任务和 089 全局报告。
