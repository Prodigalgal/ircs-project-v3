# 089 V1 golden behavior inventory - module notes

来源：`089-v1-golden-behavior-inventory.md` 拆分。

## 第一批行为模块说明

### `infrastructure/dataInit`

V1 初始化不是普通 seed SQL，而是有顺序、有 readiness gate、有 Rabbit listener 启动时机的
运行时编排。V3 目前要补：

```text
SystemConfigKey 默认值和敏感值生成
language/area/category/genre/resolver/data source/task 默认数据
credential 候选和 cache warmup
Rabbit/worker 消费不早于初始化完成
```

`028-16-C1` 只读审计已经把 V1 initializer 映射到 V3 owner：

| V1 initializer | V3 owner | 当前状态 | seed source 决策 | 风险 |
| --- | --- | --- | --- | --- |
| `SystemConfigInitializer` | `ircs-config-service` | partial done：已有 `SystemConfigInitializerRunner`、`SystemConfigDefaults`、`JdbcConfigRepository.upsertDefault()` 和 runner test；`028-16-C2-S4` 已完成 V1 133 个 `SystemConfigKey` full catalog diff，`security.admin.password` / `security.jwt.secret` 继续随机生成 | runtime owner seed，默认 seed 始终低于 ENV/K8S/启动参数与 DB | P0：更多消费者 env-first/cache event、live Rabbit smoke 和 cache TTL/evict 矩阵未闭合 |
| `CredentialInitializer` | `ircs-credential-service` | done first slice：`028-16-C1-S6` 已新增 runtime env/file initializer，复刻 V1 `OPENAI/TMDB/MAIL/R2` env 来源、`/etc/ircs/init/credentials.json`/classpath fallback、默认字段、占位符跳过和 fingerprint 幂等；`028-16-C1-S7` 已补 admin/public summary payload 脱敏，internal lease 保留真实 secret | runtime owner seed，secret 不进 Liquibase | 空库自动导入与 summary 回显 secret 风险已缓解；真实 provider/MAIL/R2 live smoke 仍待后续 |
| `HanLPInitializer` | `ircs-normalization-worker` | done first slice：`028-16-A4` 已补 `HanLpPrewarmRunner`，启动时预热 HanLP segment、简繁转换和 CharTable；失败只记录日志，不阻断 worker | runtime local warmup | 首批消费预热延迟风险已缓解；仍需后续 live log/smoke 证明 ARM64 dev 镜像加载稳定 |
| `CategoryInitializer` | `ircs-catalog-service` | partial done：`028-16-C1-S1` 已补 V1 8 类 category 与 slug，catalog-service test 通过 | runtime owner seed | 空库标准分类缺失风险已缓解；仍需 live smoke 或 empty dev DB 复验 |
| `AreaInitializer` | `ircs-catalog-service` | done first closure：`028-16-C1-S2c` 已复用 V1 `presets/countries.json`，catalog-service owner seed 读取 251 个 country/area，保留 V1 region mapping；资源缺失时才回退 P0 子集 | runtime owner seed，默认 seed 仍低于已有 DB/用户编辑值 | 全量国家/地区已补；仍需后续 empty dev DB/live smoke 复验 |
| `LanguageInitializer` | `ircs-catalog-service` | done first closure：`028-16-C1-S2c` 已复用 V1 `presets/languages.json`，catalog-service owner seed 读取 187 个 language，并继续补 V1 常见 raw alias；资源缺失时才回退 P0 子集 | runtime owner seed，默认 seed 仍低于已有 DB/用户编辑值 | 全量 language preset 已补；raw language cache evict 由 owner seed/read-through 口径记录，仍需 live 复验 |
| `StandardGenreInitializer` | `ircs-catalog-service` | partial done：`028-16-C1-S1` 已按 V1 `STANDARD_GENRES` 全量写入，并补同名 raw genre alias | runtime owner seed | 空库 raw genre exact mapping 缺默认目标风险已缓解；仍需 live smoke |
| `DataSourceInitializer` | `ircs-catalog-service` + `ircs-scraper-service` | partial done：`028-16-C1-S2` 已 seed V1 25 个 data source 配置、默认 list/detail params、`default_mapping.json`，并复刻 mapping patch；`028-16-C1-S2b` 已补显式远程 `class[]` 分类同步 service、V1 category inference 和 raw_category upsert；`028-16-C1-S4` 已补 task-service 按 data source 派生默认 collection task；`028-16-C1-S8` 已补 create/update/patch 后 afterCommit best-effort 触发远程分类同步 | runtime owner seed；远程分类同步拆可控 service；task-service 派生默认任务 | 空库无 data source、分类同步核心逻辑、admin 写操作触发和默认 task 派生风险已缓解；真实资源站公网 smoke 仍未执行 |
| `ResolverInitializer` | `ircs-content-service` auxiliary | done first slice：`028-16-C1-S5` 已新增默认 resolver preset seeder，复刻 V1 四个解析源、`默认线路`、`isActive=true` 和按名称跳过不覆盖语义 | runtime owner seed | 空库默认解析源缺口已缓解；真实播放器 live smoke、resolver cache TTL/evict 仍待后续 |
| `TaskInitializer` | `ircs-task-service` | done first slice：`028-16-C1-S4` 已新增默认 collection task seeder，按 V1 对每个 data source 派生“自动采集/全量采集”，字段值和 name 幂等规则由 task-service 测试锁定；cron scheduler 默认 `APP_TASK_SCHEDULER_ENABLED:false`；`028-16-C1-S9` 已让 `TaskWatchdog` 默认 `APP_TASK_WATCHDOG_ENABLED:false`，并用 context test 固定 disabled default / enabled opt-in | runtime owner seed | 空库默认 task 缺口已缓解；全局 readiness 自动放行、live cron smoke 和显式 watchdog smoke 仍待后续 |
| `CacheInitializer` | config/catalog/content/task/worker local cache | done first closure：C1-S2c 记录 warmup/TTL/evict 口径；catalog/data source/resolver/task/credential 通过 owner initializer 启动期 seed/warmup，SystemConfig 类 cache 走 Rabbit event evict + short TTL read-through；content-service 已由 C2-S21 覆盖，normalization-worker 本轮补默认 `30s` TTL focused test | runtime local warmup + event evict + TTL read-through | 全服务 live matrix 与 resolver/真实资源站 smoke 仍后置统一测试 |

V3 listener/readiness 第一轮判断：

- normalization、metadata、search、ingestion、storage、content、interaction 等服务已有
  `spring.rabbitmq.listener.simple.auto-startup: ${APP_*_LISTENER_ENABLED:false}`，可作为部署侧
  手动 gate。
- 未见等价 V1 的 `SystemReadinessOrchestrator`、`SystemStartupReadinessAspect` 或
  `RabbitListenerEndpointRegistry.start()` 自动放行链条。
- notification-worker 例外缺口已由 `028-16-C1-S3` 补齐：`MailConsumer` 通过
  `APP_NOTIFICATION_LISTENER_ENABLED:false` 默认关闭，保留后续 K8S 显式开启。
- `TaskWatchdog` 已由 `028-16-C1-S9` 补 `APP_TASK_WATCHDOG_ENABLED:false`
  默认关闭 guard；C1 full readiness / auto-start gate first closure 已继续补齐
  normalization retry watchdog、storage R2 watchdog 和 aggregation dev scheduler 的默认关闭
  与 health/smoke 可观察证据。

### `modules/datasource`、`modules/metadata`、`modules/ingestion`

这里承接清洗、标准映射、LLM cleaning、normalization pipeline 和 aggregation pipeline，
是 P0 最高风险区。`028-08` 已经补了 relation side effect 和 live smoke，但明确未接回
HanLP/OpenCC/LLM/fuzzy matching，因此不能标记为完全迁移。

### `modules/storage`、`modules/scraper`

V3 已完成 R2、avatar、下载与部分安全验证，但 V1 的 Tika MIME、charset detection、
streaming parse、provider/list/trend worker 细节还需要逐项验证。这里允许做更安全的
improved replacement，但必须拿 fixture 判定表和 smoke 证据说话。

#### P0-10 统一出站 HTTP caller inventory

2026-06-08 针对 `028-16-C3` 做了只读源码盘点；本节不代表服务代码已改造，只记录
V1 contract、V3 caller 和第一刀建议。

V1 统一出站 HTTP 行为：

| 能力 | V1 contract |
| --- | --- |
| 统一入口 | `HttpService.get/getStream/executeStream` 经 `AppHttpClientFactoryImpl` 创建 Apache HttpClient5/RestClient。 |
| SSRF | `SsrfValidator` 请求前解析 host，阻断 localhost、loopback、link-local、site-local、multicast、metadata host/IP。 |
| 连接池/DNS | 全局 pool max total 500、per route 100，DNS 解析后按 IPv4/IPv6 偏好和 shuffle 排序。 |
| redirect | 普通 client 开启 redirect；file downloader 和多处 V3 replacement 更偏向 redirect never/blocked。 |
| UA/headers | 默认浏览器 UA，支持 random UA、自定义 headers、`Accept-Encoding: gzip, deflate`。 |
| gzip | HttpClient5 compression + stream gzip magic bytes 包装。 |
| retry/circuit breaker | Resilience4j 按 host 建 circuit breaker；429、5xx、IO/timeout 可重试，4xx/SecurityException 不重试。 |
| proxy | `HttpOptions` 支持 custom proxy host/port，DTO 保存 proxy username/password。 |
| file download | SSRF、10MB、media/image content type、3xx blocked、浏览器式 header。 |

V3 outbound caller inventory：

| 优先级 | V3 caller | 用途 | 当前边界 | 缺口 |
| --- | --- | --- | --- | --- |
| P0 | `ircs-scraper-service/ListScraperClient` | datasource list/detail HTTP fetch | 已接 shared `PUBLIC_FETCH`，保留 timeout、custom header、custom proxy/auth、显式 UA，并补回 V1 `enableRandomUa` 受控 UA 池；fake test 覆盖私网阻断和 no-real-network 语义 | circuit breaker、Tika charset/streaming parse 仍未接回；未做真实 provider smoke。 |
| P0 | `ircs-metadata-worker/RestClientTmdbHttpClient` | TMDB public API | 已接 shared `PUBLIC_FETCH`，保留 2xx body、2xx empty/404 empty、429 retryable、5xx retryable、其他状态 terminal 分类；fake test 覆盖私网阻断、retry、gzip 和 302 不跟随 | circuit breaker、真实公网 smoke 仍待后续；`TmdbCredentialRepository` 仍需单独走 `INTERNAL_SERVICE`。 |
| P0 | `ircs-magnet-service/YtsBzMagnetProviderSearchRunner` | YTS_BZ real provider | real provider 默认关闭、allowlist gate、timeout、redirect never；默认 adapter 已接 shared `PUBLIC_FETCH` | proxy、circuit breaker、真实公网 smoke 仍待后续。 |
| P0 | `ircs-catalog-service/CatalogFetchSampleClient` | 任意 sample URL GET | 10s timeout、redirect never、2xx body；已接 shared `PUBLIC_FETCH`，fake test 覆盖 SSRF 阻断和 no-real-network 语义 | proxy、circuit breaker 仍待后续。 |
| P1 | `ircs-storage-service/CoverImageDownloadService` | cover image download | http/https、host、无 userinfo、DNS 私网阻断、10MB、image MIME、redirect blocked | 比 V1 文件下载更严格；缺 shared contract、HTTP retry/circuit/gzip 明确测试。 |
| P1 | `ircs-task-service/ScraperTaskExecutionClient` | 内部调用 scraper task execution | 固定内部 base URL，异常转失败 | 应走 `INTERNAL_SERVICE` policy，不应套 public SSRF；缺统一 timeout/retry/target 校验。 |
| P1 | `ircs-content-service/InternalContentClients` | 内部调用 scraper refetch | 已接 shared `INTERNAL_SERVICE`；每次调用读取 runtime/DB/fallback base URL；POST 固定 refetch path；非 2xx、IO 和 open circuit 均映射为 502 | 已有 circuit breaker first slice；未做 K8S live smoke。 |
| P1 | `ircs-identity-service/RestClientAvatarStorageClient` | 内部上传 avatar 到 storage | 固定 storage base URL，multipart | 同上。 |
| P1 | `ircs-notification-worker/CredentialServiceMailCredentialRepository` | 内部租 MAIL credential | 固定 credential-service base URL，可带内部 token | 同上。 |
| P1 | `ircs-metadata-worker/TmdbCredentialRepository` | 内部租 TMDB credential | 固定 credential-service base URL，可带内部 token | 同上。 |
| P1 | `ircs-admin-bff/BffProxyClient` | admin route table proxy | route table 白名单、hop-by-hop header 过滤、redirect never、audit | 应走 `BFF_PROXY` policy，只校验 target base URL，不把用户 path/query 当任意 URL。 |
| P1 | `ircs-portal-bff/BffProxyClient` | portal route table proxy | route table 白名单、hop-by-hop header 过滤、redirect never、audit | 同上。 |

Shared contract 建议分为四类 policy：

| Policy | 适用范围 | 合约 |
| --- | --- | --- |
| `PUBLIC_FETCH` | scraper、catalog sample、metadata public provider、magnet provider | http/https、host 必填、禁止 userinfo、DNS fail closed、阻断私网/metadata/reserved 地址、redirect 默认 never、UA/proxy/timeout/retry 可配置。 |
| `IMAGE_DOWNLOAD_STRICT` | storage cover image | 保留 V3 更严格 replacement：redirect blocked、10MB、image MIME、私网阻断；不抢先改 live smoke 语义。 |
| `INTERNAL_SERVICE` | task/content/identity/notification/metadata credential | 允许 cluster DNS 和内部 base URL，但启动/测试时校验 scheme、host、timeout、内部 token/header 边界。 |
| `BFF_PROXY` | admin/portal BFF | 保留 route table 白名单、hop-by-hop header 过滤和 audit；只校验配置 target base URL。 |

第一实现切片建议：

```text
C3-S1: shared outbound contract fake-first
Caller: ircs-catalog-service/CatalogFetchSampleClient
Policy: PUBLIC_FETCH
验证：fake DNS + fake transport 覆盖 SSRF blocked hosts/IP ranges、redirect never、
     stable/default UA、429/5xx/timeout retry 语义、gzip body、circuit breaker key。
非目标：真实公网 provider、storage download、BFF proxy、HTTPRoute。
```

该切片不碰公网，业务副作用最小，完成后可把同一 contract 推到 scraper、metadata 和 magnet。

### `modules/search`

V3 已完成 raw/unified producer live smoke 和 old unified dirty lifecycle 第一刀，但 V1 的
outbox relay、reconciliation scheduler、stats processor 和查询工厂语义仍需逐项归属。

### `modules/system`、`modules/task`

V3 已有 ops runner registry、audit、task runner/watchdog 审计，但 V1 的 SystemConfig 默认值、
key pool、traffic policy、DLQ、maintenance runner、task cleanup、graceful shutdown 和
task state cache 仍要继续分配到具体任务。

