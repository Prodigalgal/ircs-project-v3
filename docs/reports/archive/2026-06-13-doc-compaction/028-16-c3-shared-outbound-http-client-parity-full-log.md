# 028-16-C3 shared outbound HTTP client parity task spec

状态：进行中；C3-S8/S9 已完成实现与 focused tests，live/full/组合测试统一后置

父任务：

- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-d-tika-file-parsing-security-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `AppHttpClientFactoryImpl`
- V1 `HttpServiceImpl`
- V1 `SsrfValidator`
- V3 storage/scraper/metadata/magnet/task current outbound clients

## 背景

V1 有统一出站 HTTP 层，包含 HttpClient5 池化、DNS/SSRF、retry、circuit breaker、gzip、
随机 User-Agent 和 proxy 等能力。V3 当前 storage download 有较完整的安全边界，但 scraper、
metadata、magnet、task 等服务各自使用 RestClient/JDK HttpClient，缺少统一合约。

## 目标

- 建立所有 V3 outbound HTTP caller 清单。
- 抽取 shared SSRF/redirect/User-Agent/gzip/retry/proxy/circuit breaker 合约。
- 优先让 scraper/metadata/magnet 复用或对齐同一组安全测试。
- 保持低资源，不引入重量级网关或 sidecar。

## 非目标

- 不把所有服务立刻改成同一个客户端实现。
- 不执行真实公网 provider 调用。
- 不改变 storage 已通过的 R2/avatar/download live smoke 语义。

## 影响范围

```text
shared/ircs-common/** or a new shared outbound module
services/ircs-scraper-service/**
services/ircs-metadata-worker/**
services/ircs-magnet-service/**
services/ircs-storage-service/** only for contract reuse if needed
docs/tasks
```

## 验收标准

```text
MARK 1/6 V3 outbound HTTP caller inventory completed
MARK 2/6 SSRF private/loopback/metadata-IP blocking contract test exists
MARK 3/6 redirect/gzip/User-Agent/proxy/retry behavior documented or tested
MARK 4/6 first two caller services use shared contract or explicit adapter
MARK 5/6 service tests pass without real network
MARK 6/6 docs/tasks indexes and 089 inventory updated
```

## 测试边界

按接入服务运行局部 tests，优先 fake HTTP server，不做真实公网调用。

## 2026-06-08 C3 caller inventory 与 contract 设计

本轮只做只读源码盘点和文档设计，不修改服务代码，不新增 `HTTPRoute`，不回退其他 worker
改动。盘点来源：

```text
V1:
  AppHttpClientFactoryImpl
  HttpServiceImpl
  SecureFileDownloaderImpl
  SsrfValidator
  UserAgentUtils
  HttpOptions

V3:
  services/*/src/main/java 中直接使用 RestClient.Builder、RestClient.builder()、
  java.net.http.HttpClient.newBuilder() 的 caller
```

### V1 golden contract 摘要

| 能力 | V1 行为 |
| --- | --- |
| 统一入口 | `HttpService.get/getStream/executeStream` 统一经 `AppHttpClientFactory` 创建底层 client。 |
| SSRF | `SsrfValidator.validateUrl` 在请求前校验 host，阻断 localhost、loopback、link-local、site-local、multicast、metadata host/IP 等解析结果。 |
| DNS/连接池 | `AppHttpClientFactoryImpl` 使用 HttpClient5 pool，默认 max total 500、per route 100，DNS 解析后打散 IPv4/IPv6 顺序。 |
| timeout | 默认 10s；transient/file download 多为 15s；`HttpOptions.timeoutMs` 可覆盖。 |
| redirect | 普通 client 开启 redirect；文件下载 handler 遇 3xx 返回 empty/blocked；transient client 不允许 circular redirect。 |
| User-Agent | 未显式配置时使用浏览器 UA；`enableRandomUa` 时从 `UserAgentUtils` 的 7 个 UA 中随机选择。 |
| headers | 默认 `Accept`、`Accept-Language`、`Connection`、`Accept-Encoding: gzip, deflate`，并允许 `headersJson` 覆盖/追加。 |
| gzip | HttpClient5 content compression 打开；stream 路径还按 gzip magic bytes 包装 `GZIPInputStream`。 |
| retry | `HttpOptions.maxRetries` 驱动 Resilience4j retry；默认 3；429、5xx、IO/timeout 类错误可重试，4xx/SecurityException 不重试。 |
| circuit breaker | 按 host 从 `CircuitBreakerRegistry` 取 circuit breaker，包住实际请求。 |
| proxy | `HttpOptions.useCustomProxy/proxyHost/proxyPort` 支持 HTTP proxy；V1 DTO 也保存 proxy username/password。 |
| observability | Micrometer observation/interceptor 标记 host/url 和 HttpClient5 execution。 |
| file download | `SecureFileDownloaderImpl` 先做 SSRF，10MB 上限，阻断 3xx，限制 image/media content type，使用浏览器式 header。 |

### V3 outbound caller inventory

| 优先级 | Caller | 用途 | 当前安全边界 | SSRF | redirect | UA | retry | proxy | gzip | circuit breaker |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P0 | `ircs-scraper-service/ListScraperClient` | 按 data source base/list/detail URL 抓取 list/detail JSON | 已接 shared `PUBLIC_FETCH`，保留 custom header、custom proxy/auth、显式 UA 与 random UA | shared fail-closed | NEVER | 显式值 / V1 受控 random UA / 默认浏览器 UA | shared 429/5xx/IO retry | 有，policy 显式透传 | shared gzip 解压 | 缺失 |
| P0 | `ircs-metadata-worker/RestClientTmdbHttpClient` | 调 TMDB public API | 已接 shared `PUBLIC_FETCH`，保留 2xx/404/429/5xx/4xx 分类 | shared fail-closed | NEVER | 默认浏览器 UA | shared 429/5xx/IO retry + 原业务分类 | 缺失 | shared gzip 解压 | 缺失 |
| P0 | `ircs-magnet-service/YtsBzMagnetProviderSearchRunner` | 调 YTS_BZ real provider | real provider 默认关闭，allowlist gate，timeout，redirect never | 缺失 | NEVER | 缺失 | 缺失 | 缺失 | 未显式 | 缺失 |
| P0 | `ircs-catalog-service/CatalogFetchSampleClient` | 管理端/目录侧 sample URL GET | 10s timeout、redirect never、2xx 返回 body | 缺失 | NEVER | 缺失 | 缺失 | 缺失 | 未显式 | 缺失 |
| P1 | `ircs-storage-service/CoverImageDownloadService` | 下载 cover image 到本地/R2 | http/https、host、无 userinfo、DNS 私网阻断、10MB、image MIME、redirect blocked | 已有且比 V1 更严格 | NEVER + 3xx blocked | `IRCS-Storage-Service/0.1` | repository job retry，不是 HTTP retry | 缺失 | 未显式 | 缺失 |
| P1 | `ircs-task-service/ScraperTaskExecutionClient` | 内部 POST scraper task execution | 固定内部 base URL 配置，异常转 task failure | 内部 URL 未统一校验 | RestClient 默认边界未固化 | payload 传递 scraper UA，不是 outbound UA | 缺失 | 不适用 | 未显式 | 缺失 |
| P1 | `ircs-content-service/InternalContentClients` | 内部 POST scraper refetch | 已接 shared `INTERNAL_SERVICE`，每次调用读取 runtime/DB/fallback base URL，非 2xx/IO/open circuit 转 502 | 内部 URL shape 校验 | NEVER | `IRCS-Internal-Service/0.1` | shared 5xx/IO retry | 不适用 | shared gzip 解压 | shared circuit first slice |
| P1 | `ircs-identity-service/RestClientAvatarStorageClient` | 内部 multipart 上传 avatar 到 storage | 固定 storage base URL，multipart content type | 内部 URL 未统一校验 | RestClient 默认边界未固化 | 缺失 | 缺失 | 不适用 | 未显式 | 缺失 |
| P1 | `ircs-notification-worker/CredentialServiceMailCredentialRepository` | 内部租用 MAIL credential | 固定 credential-service base URL，可加内部 token | 内部 URL 未统一校验 | RestClient 默认边界未固化 | 缺失 | 缺失 | 不适用 | 未显式 | 缺失 |
| P1 | `ircs-metadata-worker/TmdbCredentialRepository` | 内部租用 TMDB credential | 固定 credential-service base URL，可加内部 token | 内部 URL 未统一校验 | RestClient 默认边界未固化 | 缺失 | 仅异常分类，非 client contract | 不适用 | 未显式 | 缺失 |
| P1 | `ircs-admin-bff/BffProxyClient` | Admin BFF 代理到 identity/content/catalog/task/ops/storage/config/credential/magnet/scraper/interaction | route table 白名单、hop-by-hop header 过滤、redirect never、audit | target config 未统一校验 | NEVER | 转发原 header | 缺失 | 不适用 | pass-through | 缺失 |
| P1 | `ircs-portal-bff/BffProxyClient` | Portal BFF 代理到 identity/interaction/portal/search/storage | route table 白名单、hop-by-hop header 过滤、redirect never、audit | target config 未统一校验 | NEVER | 转发原 header | 缺失 | 不适用 | pass-through | 缺失 |

排除项：R2/S3 SDK、RabbitMQ、Elasticsearch、SMTP 属于各自框架 outbound，不是本轮
RestClient/JDK HttpClient shared HTTP contract 的第一批接入对象；可在后续网络治理任务中另列。

### Shared contract 设计状态

建议在 `shared/ircs-common` 或独立 shared outbound module 中设计轻量合约，先不要引入
sidecar 或 gateway。合约只约束调用方在发起 HTTP 前后的行为，具体 transport 可先支持
fake transport，再接 JDK HttpClient/RestClient adapter。

```text
OutboundHttpClient
OutboundHttpRequest
OutboundHttpResponse
OutboundHttpPolicy
OutboundUrlPolicy
OutboundAddressResolver
OutboundTransport
```

建议 policy 分层：

| Policy | 适用 caller | 默认行为 |
| --- | --- | --- |
| `PUBLIC_FETCH` | scraper、catalog sample、metadata provider、magnet provider | 只允许 http/https、host 必填、禁止 userinfo、阻断 private/loopback/link-local/metadata/multicast/reserved 地址；redirect 默认不跟随；可配置 UA/proxy/timeout/retry。 |
| `IMAGE_DOWNLOAD_STRICT` | storage cover image | 继承 storage 现有更严格语义：redirect blocked、10MB、image MIME、私网阻断；首刀不改变 storage live smoke。 |
| `INTERNAL_SERVICE` | task/content/identity/notification/metadata credential | 允许 cluster DNS/配置 base URL，但要求 startup/test 可校验 scheme、host、timeout、内部 token/header 边界。 |
| `BFF_PROXY` | admin/portal BFF | 保留 route table 白名单、hop-by-hop header 过滤和 audit；只对 target base URL 做配置校验，避免把用户 path/query 误当任意 URL。 |

最低 contract 测试应覆盖：

```text
1. SSRF: localhost、127.0.0.1、0.0.0.0、::1、169.254.169.254、
   metadata.google.internal、10/8、172.16/12、192.168/16、100.64/10、
   198.18/15、fc00::/7、fe80::/10、multicast 均被阻断。
2. redirect: public fetch 默认 NEVER；如后续允许 redirect，必须每跳重新执行 URL/DNS 校验。
3. UA: 未配置时有稳定默认 UA；启用 random UA 时只从受控集合选择；service UA 可显式覆盖。
4. retry: 429、5xx、IO timeout 可重试；4xx、SecurityException 不重试。
5. proxy: 仅显式开启且 host/port 合法时生效；proxy credential 不写入日志。
6. gzip: 支持 content-encoding gzip 或 gzip magic bytes 的 fake response 验证。
7. circuit breaker: key 默认为 normalized host 或 caller id + host；fake transport 可断言 failure 计数。
```

### First implementation slice 建议

第一刀建议选 `CatalogFetchSampleClient` + shared fake transport contract，不碰公网，不改
`HTTPRoute`，不触碰 storage live smoke 语义：

```text
C3-S1 shared outbound contract test
  输入：example.invalid/public JSON、fake DNS、fake transport、fake redirect、fake gzip body
  输出：PUBLIC_FETCH policy 的 URL validation、redirect never、UA、retry/gzip/circuit 行为测试
  接入 caller：CatalogFetchSampleClient adapter
  不包含：真实公网 provider、storage download、BFF proxy、K8S HTTPRoute
```

选择原因：

- `CatalogFetchSampleClient` 是最小任意 URL GET caller，业务副作用最小。
- 可通过 fake transport 完成 2xx/3xx/429/5xx/gzip/timeout 场景，不需要外网和本地公网服务。
- 通过后可把同一 contract 迁到 scraper、metadata public provider、magnet provider。
- storage 当前边界更严格，适合第二阶段做 `IMAGE_DOWNLOAD_STRICT` adapter，不适合作为第一刀。

## 当前 C3 MARK

```text
MARK 1/6 V3 outbound HTTP caller inventory completed: done 2026-06-08
MARK 2/6 SSRF private/loopback/metadata-IP blocking contract test exists: done first slice
MARK 3/6 redirect/gzip/User-Agent/proxy/retry behavior documented or tested: partial, redirect/UA/retry/gzip/proxy tested; circuit breaker pending
MARK 4/6 first caller services use shared contract or explicit adapter: done first slices, catalog + magnet + scraper + metadata TMDB
MARK 5/6 service tests pass without real network: done first slices
MARK 6/6 docs/tasks indexes and 089 inventory updated: done first slices
```

## 遗留风险

- V1 普通 `HttpService` 允许 redirect，但 V3 storage/BFF/catalog/magnet 多处已经默认不跟随；
  shared contract 应明确“PUBLIC_FETCH 默认不跟随，只有显式策略才允许有限 redirect 并逐跳重校验”。
- V1 `SsrfValidator` 对 `UnknownHostException` 只记录 warn，shared contract 建议对任意 public fetch
  选择 fail closed，避免 fake DNS 和真实 DNS 行为不一致。
- scraper 当前自定义 header/proxy 来自 task/data source 配置，接入 shared contract 时必须保留
  headers/proxy 能力，并避免把 proxy password 写入 audit/log。
- 内部 RestClient caller 不应直接套 public SSRF policy，否则会误伤 cluster DNS；需要单独
  `INTERNAL_SERVICE` policy。

## 2026-06-08 C3-S1 shared outbound contract 第一刀

本轮进入实现阶段，范围收窄为 fake-first shared contract + `CatalogFetchSampleClient` 接入。
该接口是当前 V3 最小任意 URL GET caller，业务副作用低，适合先补 SSRF/redirect/UA/retry/gzip
合同，不访问公网，也不改变 storage 已通过的 image/R2 smoke。

本轮范围：

- 在 `shared/ircs-common` 新增轻量 outbound HTTP contract，包含 request/response/policy、
  transport、address resolver、URL safety validation 和 JDK adapter。
- `PUBLIC_FETCH` 默认只允许 `http/https`、host 必填、禁止 userinfo、DNS fail closed、
  阻断 loopback/private/link-local/metadata/reserved/multicast 地址，redirect 默认不跟随。
- fake transport 单测覆盖 SSRF、默认 UA、gzip 解压、429/5xx/IO retry 和 3xx 不跟随。
- `CatalogFetchSampleClient` 改用 shared outbound client；保持 2xx 返回 body、非 2xx 返回
  `null`、10s timeout 和不做真实公网测试。

非目标：

- 本轮不接 scraper/metadata/magnet 真实公网 provider。
- 本轮不改 storage download 的更严格 MIME/大小/redirect 语义。
- 本轮不把 BFF/内部 service caller 套入 public fetch policy。

验收补充：

```text
MARK 2/6 SSRF private/loopback/metadata-IP blocking contract test exists: done first slice
MARK 3/6 redirect/gzip/User-Agent/proxy/retry behavior documented or tested: partial, redirect/UA/retry/gzip tested; proxy/circuit breaker pending
MARK 4/6 first two caller services use shared contract or explicit adapter: done first slice, catalog + magnet
```

实现结果：

- `shared/ircs-common` 新增 `com.prodigalgal.ircs.common.outbound` 轻量合约：
  `OutboundHttpClient`、`OutboundHttpRequest`、`OutboundHttpResponse`、`OutboundHttpPolicy`、
  `OutboundTransport`、`OutboundUrlPolicy`、DNS resolver 和 JDK adapter。
- `OutboundUrlPolicy` 对 public fetch 执行 fail-closed SSRF 校验：阻断 `localhost`、
  metadata host、loopback、any-local、link-local、site-local、multicast、`10/8`、`172.16/12`、
  `192.168/16`、`100.64/10`、`198.18/15`、`fc00::/7`、`fe80::/10` 等。
- `OutboundHttpClient` 统一补默认 `Accept`、`Accept-Language`、`Accept-Encoding`、
  browser-style `User-Agent`，默认不跟随 redirect，支持 429/5xx/timeout/IO retry 和
  `Content-Encoding: gzip` / gzip magic bytes 解压。
- `CatalogFetchSampleClient` 已接入 shared outbound client，保留 2xx body、非 2xx `null`、
  10s timeout 和 no-real-network 测试。
- `YtsBzMagnetProviderSearchRunner` 的默认 YTS_BZ HTTP adapter 已接入 shared outbound
  client，保留既有 runner fake 测试 seam；新增测试证明 shared adapter 会在 private
  provider URL 触发前阻断。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-catalog-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-catalog-service:test :services:ircs-magnet-service:test --rerun-tasks
BUILD SUCCESSFUL
```

剩余：

- circuit breaker 仍未落代码；proxy credential 当前只透传到 policy/JDK authenticator，不进入
  payload、事件或日志，后续如增加 audit 必须保持脱敏。
- BFF proxy、内部 service caller 仍未接 shared policy；其中内部
  caller 需要 `INTERNAL_SERVICE`，不能误套 `PUBLIC_FETCH`。

## 2026-06-08 C3-S2 scraper ListScraperClient shared outbound 接入

本轮继续推进第二个 public fetch 高风险 caller：`ircs-scraper-service/ListScraperClient`。
该 caller 承接 datasource list/detail 抓取，V1 等价能力中包含自定义 header、User-Agent、
proxy/proxy auth、SSRF、retry、gzip/charset 等边界。V3 当前已经保留 header/proxy/UA，
但每个 caller 自建 JDK `HttpClient`，缺少 shared SSRF/retry/gzip contract。

本轮范围：

- 扩展 `shared/ircs-common` outbound policy，支持显式 proxy host/port/username/password，
  并保持 proxy credential 不进入响应、事件或日志。
- 保留 `ListScraperClient` 现有 URL 构造、`headers` JSON 覆盖、`userAgent` fallback、
  `useCustomProxy`/proxy auth 语义。
- `ListScraperClient` 改为经 shared `OutboundHttpClient` + `PUBLIC_FETCH` policy 发起请求，
  请求前执行 SSRF fail-closed 校验，transport 层默认 redirect never。
- 使用 fake resolver/fake transport 补 scraper-service no-real-network 单测，断言 custom
  header、User-Agent、proxy policy 透传和 private provider URL 在 transport 前被阻断。

非目标：

- 本轮不接 `RestClientTmdbHttpClient`、BFF proxy、storage strict adapter 或内部 service caller。
- 本轮不做真实公网 provider smoke。
- 本轮不实现完整 circuit breaker；先把 proxy/header/SSRF/retry/gzip 公共合同补到 scraper。

验收补充：

```text
C3-S2 MARK 1/4 shared outbound proxy policy exists and has fake-test coverage
C3-S2 MARK 2/4 ListScraperClient uses shared PUBLIC_FETCH without losing headers/proxy/UA behavior
C3-S2 MARK 3/4 scraper-service tests pass without real network
C3-S2 MARK 4/4 docs/tasks indexes and 089 inventory updated
```

实现结果：

- `shared/ircs-common` 新增 `OutboundProxy` 与 `OutboundUserAgents`，`OutboundHttpPolicy`
  支持 `withProxy()`、`withUserAgent()`、`withMaxRetries()`，空 UA 会回落到 V1 风格默认浏览器 UA。
- `JdkOutboundTransport` 改为按请求 policy 构建 proxy-aware client；显式 proxy host/port 合法
  时才生效，proxy username/password 只进入 JDK `Authenticator`，不进入响应、事件或日志。
- `OutboundHttpClient` 在发起 transport 前校验 proxy，并继续执行 public fetch SSRF fail-closed、
  redirect never、默认 headers、retry 和 gzip 解压。
- `ListScraperClient` 已改为 shared `OutboundHttpClient`；保留原 URL 构造、`headers` JSON
  覆盖、custom proxy/auth、显式 `userAgent`，并补回 V1 `enableRandomUa` 受控 UA 池语义。
- `JdkOutboundTransport` 跳过 JDK 禁止手工设置的 restricted header，避免 shared 默认
  `Connection` header 在真实 JDK adapter 中触发运行时异常。
- 新增 `ListScraperClientTest`，用 fake resolver/fake transport 断言 custom header、UA、
  proxy policy 透传、random UA 覆盖显式 UA，以及 private datasource URL 在 transport 前被阻断。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-scraper-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-scraper-service:test :services:ircs-catalog-service:test :services:ircs-magnet-service:test --rerun-tasks
BUILD SUCCESSFUL
```

当前 C3-S2 MARK：

```text
C3-S2 MARK 1/4 shared outbound proxy policy exists and has fake-test coverage: done
C3-S2 MARK 2/4 ListScraperClient uses shared PUBLIC_FETCH without losing headers/proxy/UA behavior: done
C3-S2 MARK 3/4 scraper-service tests pass without real network: done
C3-S2 MARK 4/4 docs/tasks indexes and 089 inventory updated: done
```

下一刀候选：

- C3-S3 只接 `metadata-worker/RestClientTmdbHttpClient` 的 TMDB public API caller，保留
  `TmdbHttpClient` 接口和现有 2xx/404/429/5xx/4xx 业务分类。
- `TmdbCredentialRepository` 是内部 credential-service caller，应后续单独走 `INTERNAL_SERVICE`
  policy，允许 cluster DNS / dev localhost 配置，不得误套 `PUBLIC_FETCH`。
- C3 仍需 circuit breaker、storage `IMAGE_DOWNLOAD_STRICT` adapter、BFF `BFF_PROXY` target
  校验和 internal service timeout/target/header 边界。

## 2026-06-09 C3-S3 metadata TMDB public outbound 接入

本轮接入 metadata-worker 的 TMDB public API caller：`RestClientTmdbHttpClient`。当前该类
直接使用 Spring `RestClient.Builder` 发绝对 URI，业务层已经有 2xx/404/429/5xx/4xx 分类，
但缺 shared SSRF、redirect、默认 UA、gzip、retry 和 timeout contract。

本轮范围：

- 将 `RestClientTmdbHttpClient` 改为 shared `OutboundHttpClient` adapter，使用
  `OutboundHttpPolicy.publicFetch()`。
- 保留 `TmdbHttpClient` 接口和现有业务状态分类：2xx body、2xx empty、404 empty、429
  retryable、5xx retryable、其他 4xx terminal。
- 为 TMDB public request 增加独立 timeout 配置 `app.metadata.tmdb.request-timeout`，默认
  10s；不复用 `maxWait`，因为 `maxWait` 当前用于 credential rate limit wait。
- 使用 fake resolver/fake transport 补 metadata-worker no-real-network 单测，覆盖 private
  TMDB base URL 阻断、状态码分类、默认 header、retry 和 gzip/redirect 基础语义。

非目标：

- 本轮不改 `TmdbCredentialRepository`；它是内部 credential-service caller，后续单独走
  `INTERNAL_SERVICE` policy，不能误套 `PUBLIC_FETCH`。
- 本轮不触碰 TMDB provider 匹配、credential pool、rate limiter、metadata result merge。
- 本轮不做真实 TMDB 公网 smoke。

验收补充：

```text
C3-S3 MARK 1/5 RestClientTmdbHttpClient uses shared PUBLIC_FETCH
C3-S3 MARK 2/5 TMDB status mapping remains compatible with existing worker semantics
C3-S3 MARK 3/5 private/metadata URL is blocked before transport by fake resolver
C3-S3 MARK 4/5 metadata-worker and shared common tests pass without real network
C3-S3 MARK 5/5 docs/tasks indexes and 089 inventory updated
```

实现结果：

- `RestClientTmdbHttpClient` 已从 Spring `RestClient.Builder` 直接 GET 改为 shared
  `OutboundHttpClient` adapter，生产构造器使用 `DefaultOutboundAddressResolver` +
  `JdkOutboundTransport`。
- `TmdbProviderProperties` 新增 `requestTimeout`，`application.yaml` 支持
  `APP_METADATA_TMDB_REQUEST_TIMEOUT` 注入；该 timeout 独立于 credential rate limit 的
  `maxWait`。
- 保留既有 `TmdbHttpClient#getJson(URI)` 接口和业务分类：2xx body 返回、2xx empty/404
  返回 empty、429 -> `RATE_LIMITED`、5xx -> `UPSTREAM_UNAVAILABLE`、其他状态码 ->
  `HTTP_xxx` terminal。
- `OutboundHttpException` / `IOException` 会在 TMDB adapter 内转为
  `TMDB_OUTBOUND_UNAVAILABLE` retryable；`InterruptedException` 会恢复 interrupt flag 后转为
  `TMDB_OUTBOUND_INTERRUPTED` retryable。
- 新增 `RestClientTmdbHttpClientTest`，用 fake resolver/fake transport 覆盖 success/empty/404、
  429/5xx/401 分类、private metadata URL transport 前阻断、timeout/5xx retry + gzip 解压、
  302 不跟随并按 terminal 状态处理。
- `TmdbCredentialRepository` 未接 `PUBLIC_FETCH`，继续保持内部 credential-service caller
  边界，待后续 `INTERNAL_SERVICE` policy 切片。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-metadata-worker:test :services:ircs-scraper-service:test :services:ircs-catalog-service:test :services:ircs-magnet-service:test --rerun-tasks
BUILD SUCCESSFUL
```

当前 C3-S3 MARK：

```text
C3-S3 MARK 1/5 RestClientTmdbHttpClient uses shared PUBLIC_FETCH: done
C3-S3 MARK 2/5 TMDB status mapping remains compatible with existing worker semantics: done
C3-S3 MARK 3/5 private/metadata URL is blocked before transport by fake resolver: done
C3-S3 MARK 4/5 metadata-worker and shared common tests pass without real network: done
C3-S3 MARK 5/5 docs/tasks indexes and 089 inventory updated: done
```

下一刀候选：

- C3-S4a 已完成 `INTERNAL_SERVICE` policy 与 `TmdbCredentialRepository` 接入；后续 C3-S4b/C3-S4c
  继续接 notification MAIL credential lease、task/content internal caller 和 identity avatar storage
  caller，校验固定 base URL、timeout、内部 token/header，并允许 cluster DNS。
- C3-S5 可拆 storage `IMAGE_DOWNLOAD_STRICT` adapter 或 BFF `BFF_PROXY` target base URL
  校验。
- circuit breaker 仍需独立切片；建议在更多 caller 接入 shared policy 后统一做 host/caller
  key 设计和 fake failure 计数测试。

## 2026-06-09 C3-S4 INTERNAL_SERVICE / BFF_PROXY 只读盘点

本轮只做后续切片边界确认，不修改代码。结论：

| Caller | 当前行为 | 应归类 | 最小测试重点 |
| --- | --- | --- | --- |
| `metadata-worker/TmdbCredentialRepository` | 固定 credential-service base URL，GET TMDB leases，可带 `X-IRCS-INTERNAL-TOKEN` | `INTERNAL_SERVICE` | URL、internal token、5xx retryable 映射、空凭据过滤 |
| `notification-worker/CredentialServiceMailCredentialRepository` | 固定 credential-service base URL，GET MAIL leases，可带内部 token | `INTERNAL_SERVICE` | URL、internal token、空凭据/失败异常 |
| `task-service/ScraperTaskExecutionClient` | 固定 scraper base URL，POST task execution；payload 内 UA/proxy/header 是 scraper 抓取配置 | `INTERNAL_SERVICE` | path/body 不变、失败仍转 task failure |
| `content-service/InternalContentClients` | 固定 scraper base URL，POST raw video refetch | `INTERNAL_SERVICE` | path variable、异常仍转 502 |
| `identity-service/RestClientAvatarStorageClient` | 固定 storage base URL，multipart POST avatar | `INTERNAL_SERVICE` | multipart 字段、filename/content type、失败不泄漏文件内容 |
| admin/portal `BffProxyClient` | route table 白名单 + JDK HttpClient，过滤 hop-by-hop header，redirect never，写 audit | `BFF_PROXY` | route 优先级、query 透传、target base URL fail fast、audit 成功/失败 |

边界：

- `INTERNAL_SERVICE` 不套 `PUBLIC_FETCH`；它应允许 cluster DNS / dev localhost，但校验
  scheme/host/userinfo、timeout、redirect never 和固定 header。
- `BFF_PROXY` 不把用户 path/query 当任意 URL 重新解释，只校验 route table 的
  `targetBaseUrl`，保留现有 header 过滤、audit 和 redirect never。

建议顺序：

```text
C3-S4a: shared INTERNAL_SERVICE policy + TmdbCredentialRepository
C3-S4b: Mail credential repository + ScraperTaskExecutionClient + InternalContentClients
C3-S4c: RestClientAvatarStorageClient multipart
C3-S5: admin/portal BFF_PROXY target base URL validation
```

## 2026-06-09 C3-S4a metadata TMDB credential INTERNAL_SERVICE policy

已新增并完成：

```text
tasks/done/028-16-c3-s4a-metadata-tmdb-credential-internal-service-policy.md
```

本切片把 shared outbound policy 从单一 public fetch 扩展为显式类型：
`PUBLIC_FETCH`、`INTERNAL_SERVICE`、`BFF_PROXY`。`PUBLIC_FETCH` 继续执行 DNS
fail-closed SSRF 校验；`INTERNAL_SERVICE` / `BFF_PROXY` 只校验 `http/https`、host 必填
和禁止 userinfo，允许 cluster DNS 与 localhost dev base URL。

metadata-worker `TmdbCredentialRepository` 已迁到 shared `INTERNAL_SERVICE`，保留固定
`/internal/credentials/providers/TMDB/leases` path/query、`requiredPayloadKey=api_key`、
`limit`、`X-IRCS-INTERNAL-TOKEN`、空凭据过滤和失败转
`CREDENTIAL_SERVICE_UNAVAILABLE` retryable 语义。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL

TmdbCredentialRepositoryTest: tests=4, failures=0
```

当前 C3-S4a MARK：

```text
C3-S4a MARK 1/5 shared outbound policy can distinguish PUBLIC_FETCH, INTERNAL_SERVICE, BFF_PROXY: done
C3-S4a MARK 2/5 INTERNAL_SERVICE validates scheme/host/userinfo without DNS SSRF blocking: done
C3-S4a MARK 3/5 TmdbCredentialRepository uses shared INTERNAL_SERVICE and preserves token/header/path/query: done
C3-S4a MARK 4/5 credential-service failure still maps to CREDENTIAL_SERVICE_UNAVAILABLE retryable: done
C3-S4a MARK 5/5 shared and metadata-worker tests pass without real network: done
```

下一刀候选修正：

- notification-worker MAIL credential lease 仍未接 `INTERNAL_SERVICE`。
- task-service scraper execution caller、content-service internal scraper caller 和 identity-service
  avatar storage client 仍未接 `INTERNAL_SERVICE`。
- admin-bff / portal-bff 仍未接 `BFF_PROXY` target base URL validation。
- circuit breaker 和 storage `IMAGE_DOWNLOAD_STRICT` shared adapter 仍需独立切片。

## 2026-06-09 C3-BFF-PROXY admin BFF target validation

已新增并完成：

```text
tasks/done/028-16-c3-bff-proxy-admin-target-validation.md
```

本切片只选择 `services/ircs-admin-bff`，未修改 `services/ircs-portal-bff`。admin
`BffProxyClient` 已在拼接用户 path/query 前，对 route table 中固定 `targetBaseUrl`
调用 shared `BFF_PROXY` policy 的 `validateBffProxyTarget()`；该校验只检查 `http/https`、
host 必填和禁止 userinfo，不把用户 path/query 当作任意 URL 重新解释。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-admin-bff:test --rerun-tasks
BUILD SUCCESSFUL
```

当前 C3-BFF-PROXY MARK：

```text
C3-BFF-PROXY MARK 1/5 only admin-bff is changed: done
C3-BFF-PROXY MARK 2/5 BffProxyClient validates configured targetBaseUrl with shared BFF_PROXY policy: done
C3-BFF-PROXY MARK 3/5 user request path/query is forwarded, not treated as arbitrary target URL: done
C3-BFF-PROXY MARK 4/5 focused tests cover hop-by-hop header filtering plus route whitelist or invalid target base URL: done
C3-BFF-PROXY MARK 5/5 shared and admin-bff tests pass without real network: done
```

剩余修正：

- portal-bff 仍未接 `BFF_PROXY` target base URL validation。
- BFF proxy circuit breaker 仍未落代码。
- 如后续要让 BFF transport 复用 shared client，需要先扩展 shared transport 的 method/body/streaming
  response 能力。

文档集成 worker 已把 admin-bff `BFF_PROXY` target validation 按已完成子切片纳入
`docs/index.md`、`tasks/index.md`、`docs/reports/089-v1-golden-behavior-inventory.md` 和
`docs/migrations/089-v1-golden-behavior-inventory.md`；当时 C3 主任务剩余风险口径收敛为
portal-bff、circuit breaker、storage strict adapter、notification MAIL credential lease、
task/content/identity internal caller，以及 shared transport method/body/streaming。

## 2026-06-09 C3-S6 shared outbound circuit breaker first slice + content internal caller

本轮作为 shared outbound circuit breaker first slice，不回退其他 worker 已完成的
catalog、magnet、scraper、metadata TMDB、metadata credential、notification MAIL credential
和 admin-bff 进展。范围收窄为 shared `OutboundHttpClient` 的 host/caller key circuit breaker，
并选择 `content-service/InternalContentClients` 作为一个 internal caller adoption。

目标：

- 在 `shared/ircs-common` 为 outbound client 增加 timeout/5xx/connection refused 等失败后的
  circuit open、half-open probe、成功恢复和快速失败错误语义。
- circuit key 默认按 policy type + host + port 归一化，允许 caller 显式覆盖 caller key；
  默认参数可被 ENV/K8S 注入覆盖。
- `InternalContentClients#refetchRawVideo` 改用 shared `INTERNAL_SERVICE` policy，保留动态
  `scraperBaseUrl()`、固定 path、异常转 `BAD_GATEWAY` 语义。

非目标：

- 不改 notification 028-10 runner、C1 readiness、storage R2、portal/admin/BFF。
- 不接 task runner JSON body caller，不做 shared streaming response。
- 不做真实外网或 K8S live smoke；本轮使用 fake transport / local fake server 等无副作用测试。

影响文件：

```text
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/outbound/**
shared/ircs-common/src/test/java/com/prodigalgal/ircs/common/outbound/**
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/video/InternalContentClients.java
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/video/InternalContentClientsTest.java
tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md
tasks/index.md
docs/reports/089-v1-golden-behavior-inventory.md
```

验收标准：

```text
C3-S6 MARK 1/6 shared outbound circuit breaker covers timeout, 5xx, IO/connection refused, open, half-open recovery
C3-S6 MARK 2/6 circuit breaker has explicit error semantics for open circuit and remains fake-testable
C3-S6 MARK 3/6 circuit breaker config supports ENV/K8S override and caller key override
C3-S6 MARK 4/6 InternalContentClients uses shared INTERNAL_SERVICE without losing dynamic base URL/path/error mapping
C3-S6 MARK 5/6 focused tests pass without real external network
C3-S6 MARK 6/6 docs/tasks ledger updated with remaining caller adoption list
```

实现结果：

- `shared/ircs-common` 新增 `OutboundCircuitBreakerConfig`、`OutboundCircuitBreaker` 和
  `OutboundCircuitOpenException`。`OutboundHttpClient` 现在按 logical execute 统计 circuit：
  retry 用尽后才把 timeout/IO/connection refused 记为失败；最终 429/5xx 响应也会记失败；
  open 状态在 transport 前快速抛 `OutboundCircuitOpenException`；open duration 到期后进入
  half-open，probe 成功即关闭，失败则重新 open。
- circuit key 默认是 `policyType:host[:port]`，caller 可用
  `OutboundHttpPolicy.withCircuitBreakerKey()` 覆盖为 `callerKey:host[:port]`。
- 默认 circuit 参数可由 ENV/K8S 或 system property 覆盖：
  `IRCS_OUTBOUND_CIRCUIT_ENABLED`、`IRCS_OUTBOUND_CIRCUIT_FAILURE_THRESHOLD`、
  `IRCS_OUTBOUND_CIRCUIT_OPEN_DURATION_MS`、
  `IRCS_OUTBOUND_CIRCUIT_HALF_OPEN_MAX_CALLS`。
- shared transport 新增 `POST` no-body/body 支持，保持 GET 入口兼容。
- `content-service/InternalContentClients` 已从直接 `RestClient` POST 改为 shared
  `INTERNAL_SERVICE` policy，保留每次调用读取 `configValues.scraperBaseUrl()`、固定
  `/internal/v1/scraper/raw-videos/{id}/refetch` path、2xx 成功、非 2xx/IO/open circuit
  转 `ContentApiException(BAD_GATEWAY, ...)` 的业务语义。
- shared circuit breaker 日志口径使用 stable key/state/reason，不记录 URL query、proxy secret
  或 payload；Micrometer/audit sink 后续可基于该 key/reason 扩展。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-catalog-service:test :services:ircs-magnet-service:test :services:ircs-scraper-service:test :services:ircs-metadata-worker:test :services:ircs-notification-worker:test :services:ircs-content-service:test
BUILD SUCCESSFUL
```

live smoke：

```text
未执行；本轮只做 fake transport 与本地 fake server，无真实外网/K8S 副作用。
```

当前 C3-S6 MARK：

```text
C3-S6 MARK 1/6 shared outbound circuit breaker covers timeout, 5xx, IO/connection refused, open, half-open recovery: done
C3-S6 MARK 2/6 circuit breaker has explicit error semantics for open circuit and remains fake-testable: done
C3-S6 MARK 3/6 circuit breaker config supports ENV/K8S override and caller key override: done
C3-S6 MARK 4/6 InternalContentClients uses shared INTERNAL_SERVICE without losing dynamic base URL/path/error mapping: done
C3-S6 MARK 5/6 focused tests pass without real external network: done
C3-S6 MARK 6/6 docs/tasks ledger updated with remaining caller adoption list: done
```

剩余 adoption 清单（C3-S6 当时口径，`task-service/ScraperTaskExecutionClient` 后续已由
C3-S7 完成）：

- `identity-service/RestClientAvatarStorageClient`：需要 multipart upload adapter，避免泄漏文件内容。
- `portal-bff` 与 `admin-bff`：target validation 已有切片记录，但 BFF transport 仍未复用 shared
  method/body/streaming response；如接入需先扩展 streaming。
- `storage-service/CoverImageDownloadService`：后续接 `IMAGE_DOWNLOAD_STRICT` shared adapter，保持
  10MB/MIME/redirect blocked 现有 live 语义。
- public provider caller 已接 shared client，但如需真实 circuit 参数细化，可逐 caller 增加
  caller key 和 service-level ENV 覆盖。

## 2026-06-09 C3-S7 task-service scraper execution POST JSON caller

本轮作为 shared outbound circuit breaker adoption 的 task-service POST JSON 切片。范围收窄为
`task-service/ScraperTaskExecutionClient`，它是固定 scraper-service internal endpoint 的
`POST /internal/v1/scraper/task-executions` caller；payload 内的 `userAgent`、proxy 和 headers
是 scraper 抓取配置，不是本 caller 自身 outbound header。

目标：

- `ScraperTaskExecutionClient` 改用 shared `OutboundHttpClient` + `INTERNAL_SERVICE` policy，
  保留固定 path、JSON body、2xx response mapping 和 V1/V3 task failure 语义。
- shared guard 覆盖 timeout、5xx/IO/connection refused、open circuit fast fail，且 4xx/5xx
  response mapping 不被 Spring `RestClient.retrieve()` 异常语义改变。
- 增加 no-real-network tests 覆盖 POST body、2xx response、4xx/5xx、timeout/connection
  refused、open circuit fast fail。

非目标：

- 不修改 notification、normalization A9、resource verifier manifests、storage、portal/admin/BFF。
- 不做真实公网 provider 调用；live smoke 只在已有 dev-safe task/dry-run 条件足够时执行。
- 不改变 scraper-service internal API 契约和 task repository/runner 调度逻辑。

影响文件：

```text
services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/ScraperTaskExecutionClient.java
services/ircs-task-service/src/test/java/com/prodigalgal/ircs/task/ScraperTaskExecutionClientTest.java
tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md
tasks/index.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/reports/093-v1-v3-parity-progress-ledger.md
```

验收标准：

```text
C3-S7 MARK 1/6 ScraperTaskExecutionClient uses shared INTERNAL_SERVICE POST JSON caller
C3-S7 MARK 2/6 POST body and 2xx response mapping preserve existing task execution contract
C3-S7 MARK 3/6 4xx/5xx response mapping remains explicit and failure-compatible
C3-S7 MARK 4/6 timeout/connection refused/open circuit fast fail are covered by tests
C3-S7 MARK 5/6 focused task-service and shared tests pass without real network
C3-S7 MARK 6/6 docs/tasks ledger updated with remaining adoption list and live smoke status
```

测试方式：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-task-service:test --rerun-tasks
```

实现结果：

- `ScraperTaskExecutionClient` 已从 Spring `RestClient.retrieve()` 直接 POST 改为 shared
  `OutboundHttpClient` + `INTERNAL_SERVICE` policy，生产路径使用
  `DefaultOutboundAddressResolver` + `JdkOutboundTransport`。
- 保留固定 `/internal/v1/scraper/task-executions` path、JSON body、task payload 字段、
  `ircsTaskRunner.directItems` / `httpHeaders` 拆分逻辑、2xx response DTO mapping 和
  非 2xx / IO / timeout / open circuit 统一转 `IllegalStateException` 的 task failure 外观。
- caller 使用 `task-scraper-execution` circuit breaker key；shared guard 继续提供 timeout、
  5xx retry/failure 计数、IO/connection refused failure 计数和 open circuit fast fail。
- 新增 `ScraperTaskExecutionClientTest`，fake transport 覆盖 POST body、2xx response、4xx、
  5xx retry后失败、timeout、connection refused、open circuit fast fail；`INTERNAL_SERVICE`
  测试断言不会执行 public DNS SSRF resolution。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-task-service:test --rerun-tasks
BUILD SUCCESSFUL
```

live smoke：

```text
未执行；本轮没有可确认的 dev-safe task/dry-run execution 输入，且不应临时创建会触发真实 scraper
抓取的任务。POST path、body、response mapping 和 outbound guard 已由 fake transport 覆盖。
```

当前 C3-S7 MARK：

```text
C3-S7 MARK 1/6 ScraperTaskExecutionClient uses shared INTERNAL_SERVICE POST JSON caller: done
C3-S7 MARK 2/6 POST body and 2xx response mapping preserve existing task execution contract: done
C3-S7 MARK 3/6 4xx/5xx response mapping remains explicit and failure-compatible: done
C3-S7 MARK 4/6 timeout/connection refused/open circuit fast fail are covered by tests: done
C3-S7 MARK 5/6 focused task-service and shared tests pass without real network: done
C3-S7 MARK 6/6 docs/tasks ledger updated with remaining adoption list and live smoke status: done
```

剩余 adoption 清单：

- `identity-service/RestClientAvatarStorageClient`：已由 C3-S8 接入 shared `INTERNAL_SERVICE`
  multipart POST，focused fake transport tests 通过；live/full suite 统一测试后置。
- `storage-service/CoverImageDownloadService`：已由并行 worker 完成 `IMAGE_DOWNLOAD_STRICT`
  shared adapter，保持 10MB/MIME/redirect blocked 现有语义；本轮未重复修改。
- `portal-bff` 与 `admin-bff`：target validation 已有切片记录，但 BFF transport 仍未复用 shared
  method/body/streaming response；如接入需先扩展 streaming。
- public provider/internal caller 已有多处接入 shared client；如需真实 circuit 参数细化，可逐 caller
  增加 caller key 和 service-level ENV 覆盖。

## 2026-06-10 C3-S8 identity avatar storage multipart INTERNAL_SERVICE caller

本轮按“先推进完整、统一测试后置”的策略，只做实现和轻量定向测试，不做 live smoke、不做组合长测试。
范围收窄为 `identity-service/RestClientAvatarStorageClient`，并兼容并行 worker 已完成的 storage
strict adapter 与 BFF target validation 记录。

目标：

- `RestClientAvatarStorageClient` 改用 shared `OutboundHttpClient` +
  `OutboundHttpPolicy.internalService()`。
- 保留固定 `/internal/storage/avatars`、multipart `file` 字段、filename/content type/binary body、
  response JSON -> `StoredAvatar` 映射。
- timeout、5xx、IO、invalid URL、open circuit 保持现有
  `ApiException(BAD_GATEWAY, "storage.sync.failed")` 外观。
- 增加 caller-specific request timeout 配置与 `identity-avatar-storage` circuit breaker key。

实现结果：

- identity avatar storage client 已从 Spring `RestClient` 直接 multipart 上传迁到 shared
  `INTERNAL_SERVICE` POST body caller。
- multipart body 由 identity caller 构造，shared transport 继续只承接 method/body/header，
  不改变 storage cover streaming 下载边界。
- 新增 `RestClientAvatarStorageClientTest`，fake transport 覆盖 multipart body、2xx/empty
  response、503、IO、invalid internal URL fail-before-send、open circuit fast fail。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-identity-service:test --tests "com.prodigalgal.ircs.identity.RestClientAvatarStorageClientTest" --tests "com.prodigalgal.ircs.identity.RestClientAvatarStorageClientContextTest" --rerun-tasks
BUILD SUCCESSFUL in 16s
```

live/full tests：

```text
未执行；按用户最新策略统一测试后置。
```

当前 C3-S8 MARK：

```text
C3-S8 MARK 1/6 RestClientAvatarStorageClient uses shared INTERNAL_SERVICE multipart POST: done
C3-S8 MARK 2/6 multipart field filename content type and body are preserved: done
C3-S8 MARK 3/6 2xx response mapping and empty response failure remain compatible: done
C3-S8 MARK 4/6 timeout/5xx/IO/open circuit map to storage.sync.failed BAD_GATEWAY: done
C3-S8 MARK 5/6 invalid internal base URL fails before transport send: done
C3-S8 MARK 6/6 docs/tasks ledger updated and live/full tests deferred to unified testing: done
```

剩余 adoption 清单：

- BFF full transport method/body/streaming response 复用仍未实现；admin/portal target validation 已完成。
- caller-specific circuit config 仍需 service-level ENV / caller key 统一矩阵。
- identity/storage/BFF/shared 组合测试与 live-safe avatar chain 留到统一测试批次。

## 2026-06-10 C3-S9 BFF shared transport method/body/streaming adoption

本轮按“功能推进完整、统一测试后置”策略，完成 admin/portal BFF proxy 的 shared outbound
streaming adoption，不做 live 门户 smoke、不做真实门户访问、不做全量 build。

目标：

- 将 BFF proxy 从直接 JDK `HttpClient` 改为 shared `BFF_PROXY` streaming transport。
- 保留 route table 白名单、target base URL fail-before-send、query 透传、hop-by-hop header
  过滤、response status/header/body 透传和 response streaming 语义。
- 支持 caller-specific request timeout、circuit key 和 circuit 参数由 ENV/K8S 覆盖。

实现结果：

- `shared/ircs-common` 新增 `OutboundStreamingHttpClient`、
  `OutboundStreamingTransport`、`OutboundStreamingHttpResponse`、
  `JdkOutboundStreamingTransport`。
- shared JDK byte-array transport 与 streaming transport 均支持任意 HTTP method + byte body；
  BFF `GET`/`HEAD`/`OPTIONS` 无 body，`POST`/`PUT`/`PATCH`/`DELETE` 等继续转发 body。
- admin-bff 与 portal-bff 均使用 shared `OutboundStreamingHttpClient` + `BFF_PROXY`
  policy；response body 仍以 `InputStream` 写回 servlet output，SSE/download/debug-stream
  等路径不被强制 byte-array 化。
- admin/portal BFF application config 新增：
  `APP_BFF_PROXY_CONNECT_TIMEOUT`、`APP_BFF_PROXY_REQUEST_TIMEOUT`、
  `APP_BFF_PROXY_CIRCUIT_KEY`、`APP_BFF_PROXY_CIRCUIT_ENABLED`、
  `APP_BFF_PROXY_CIRCUIT_FAILURE_THRESHOLD`、`APP_BFF_PROXY_CIRCUIT_OPEN_DURATION`、
  `APP_BFF_PROXY_CIRCUIT_HALF_OPEN_MAX_CALLS`；caller-specific circuit ENV 未设置时回落到
  `IRCS_OUTBOUND_CIRCUIT_*` global ENV。
- open circuit / shared URL shape 错误统一映射为 `502 BAD_GATEWAY` /
  `BFF proxy upstream unavailable`；invalid target base URL 仍保持
  `Invalid BFF target base URL`。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-admin-bff:test :services:ircs-portal-bff:test --rerun-tasks
BUILD SUCCESSFUL in 19s
```

首次沙箱内运行因用户目录 `.gradle` wrapper lock/cache 权限失败；同一 focused test 命令经批准在
沙箱外通过。补充 global circuit ENV fallback 后再次重跑仍通过。

当前 C3-S9 MARK：

```text
C3-S9 MARK 1/7 BFF transport dispersal inventory completed: done
C3-S9 MARK 2/7 shared outbound supports BFF_PROXY arbitrary method/body with streaming response: done
C3-S9 MARK 3/7 admin-bff uses shared BFF_PROXY streaming adapter without losing route/query/header/status behavior: done
C3-S9 MARK 4/7 portal-bff uses shared BFF_PROXY streaming adapter without losing route/query/header/status behavior: done
C3-S9 MARK 5/7 caller key timeout circuit config support ENV/K8S override: done
C3-S9 MARK 6/7 focused no-real-network tests pass or are recorded as deferred: done
C3-S9 MARK 7/7 docs/tasks/089/093 ledgers updated with unified testing deferred: done
```

剩余 adoption / testing 清单：

- C3 implementation adoption 进入 first closure；C3 父任务仍不关闭，等待统一测试批次。
- `UTB-02816-P1P2-02` 继续覆盖 identity/storage/BFF/shared outbound 组合回归、dev-safe
  BFF smoke、caller key/circuit ENV matrix 和 live-safe avatar chain。
- 本轮未执行 portal/admin 真实页面访问、SSE/download/debug-stream live path、K8S open circuit
  参数矩阵或全量 build。

## 2026-06-10 本轮收敛口径

C3 当前按“implementation adoption first closure”入账，不按生产闭环关闭：

- 已完成实现：C3-S8 identity-service `RestClientAvatarStorageClient` multipart
  `INTERNAL_SERVICE` adoption；C3-S9 admin/portal BFF shared `BFF_PROXY` streaming transport
  adoption。
- 已验证：C3-S8 focused identity tests passed；C3-S9 focused shared/admin-bff/portal-bff
  no-real-network tests passed。
- 后置债务：identity/storage/BFF/shared outbound 组合回归、dev-safe BFF smoke、caller-specific
  circuit ENV/K8S matrix、live-safe avatar chain、portal/admin 真实页面访问、SSE/download/debug-stream
  live path。

## 2026-06-10 C3-S10 caller-specific circuit ENV matrix implementation

本轮完成 caller-specific circuit config / ENV matrix 实现，不做 live smoke、不做全量测试。

实现结果：

- shared outbound 新增 `OutboundCircuitBreakerConfig.enabledFromEnvironment(String callerKey)`，
  按 `caller-specific ENV -> global ENV -> caller-specific JVM property -> global JVM property -> default`
  解析 circuit config，确保 ENV/K8S 高优先级。
- 新增 `OutboundHttpPolicy.withCallerCircuitBreakerKey(String)`；content `content-refetch-scraper`、
  task `task-scraper-execution`、identity `identity-avatar-storage` 已接入 caller-aware config。
- admin/portal BFF YAML 保留 `APP_BFF_PROXY_CIRCUIT_*` service-level 高优先级，并补
  `IRCS_OUTBOUND_CIRCUIT_ADMIN_BFF_PROXY_*` / `IRCS_OUTBOUND_CIRCUIT_PORTAL_BFF_PROXY_*`
  caller-specific fallback。
- adopted services 的 `application.yaml` 和 dev manifests 暴露 5 个 caller 默认矩阵，默认仍为
  enabled=true、failure-threshold=5、open-duration=30s、half-open-max-calls=1。

ENV matrix：

```text
content-refetch-scraper   -> IRCS_OUTBOUND_CIRCUIT_CONTENT_REFETCH_SCRAPER_*
task-scraper-execution    -> IRCS_OUTBOUND_CIRCUIT_TASK_SCRAPER_EXECUTION_*
identity-avatar-storage   -> IRCS_OUTBOUND_CIRCUIT_IDENTITY_AVATAR_STORAGE_*
admin-bff-proxy           -> IRCS_OUTBOUND_CIRCUIT_ADMIN_BFF_PROXY_*
portal-bff-proxy          -> IRCS_OUTBOUND_CIRCUIT_PORTAL_BFF_PROXY_*
global fallback           -> IRCS_OUTBOUND_CIRCUIT_*
suffixes                  -> ENABLED, FAILURE_THRESHOLD, OPEN_DURATION, OPEN_DURATION_MS, HALF_OPEN_MAX_CALLS
```

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "com.prodigalgal.ircs.common.outbound.OutboundHttpClientTest" --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-content-service:compileJava :services:ircs-task-service:compileJava :services:ircs-identity-service:compileJava :services:ircs-admin-bff:compileJava :services:ircs-portal-bff:compileJava
BUILD SUCCESSFUL
```

C3-S10 MARK 5/5 done implementation-first；C3 父任务仍保持进行中，dev K8S override/open-circuit
matrix、BFF smoke、identity/storage/BFF 组合回归继续后置到 `UTB-02816-P1P2-02`。
