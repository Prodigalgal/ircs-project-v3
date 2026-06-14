# 028-16-C1 initialization readiness gate task spec

状态：进行中；C1-S2c 已完成实现，resolver/真实资源站 live smoke 与完整统一测试后置

父任务：

- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `DataInitializationOrchestrator`
- V1 `SystemConfigInitializer`、`LanguageInitializer`、`AreaInitializer`、`CategoryInitializer`、
  `StandardGenreInitializer`、`DataSourceInitializer`、`CredentialInitializer`、`TaskInitializer`、
  `CacheInitializer`、`HanLPInitializer`

## 背景

V1 通过 `DataInitializationOrchestrator` 按 `InitTask` 顺序执行初始化，并在完成后
`markAsReady()`，再启动 Rabbit listener。V3 当前主要依赖 Liquibase、分散 service 和脚本，
缺少等价运行时 readiness gate。

## 目标

- 建立 V1 initializer 到 V3 owner service 的映射。
- 明确哪些默认数据由 Liquibase seed，哪些需要运行时补齐。
- 设计并实现 worker/Rabbit 消费不早于初始化完成的低资源 gate。
- 提供空库 fixture 或 repository test，证明默认 config/dictionary/task/credential seed 可补齐。

## 非目标

- 不重建 V1 单体式全局 initializer。
- 不在所有服务里重复写 seed 逻辑。
- 不新增 K8S 中间件或 HTTPRoute。

## 影响范围

```text
services/ircs-config-service/**
services/ircs-catalog-service/**
services/ircs-credential-service/**
services/ircs-task-service/**
worker service startup/gate only where required
docs/tasks
```

## 验收标准

```text
MARK 1/6 V1 initializer-to-V3-owner matrix completed
MARK 2/6 SystemConfig/language/area/category/genre/resolver/data-source/task/credential seed source decided
MARK 3/6 runtime readiness gate design documented
MARK 4/6 empty-db seed tests cover highest-risk defaults
MARK 5/6 worker/Rabbit start gate has test or smoke evidence
MARK 6/6 docs/tasks indexes and 089 inventory updated
```

## 测试边界

按实际 owner service 运行局部 tests；不做真实 dev namespace reset。

## 2026-06-08 只读取证范围

本轮只读审计覆盖：

```text
V1:
  D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/infrastructure/dataInit/**
  D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/infrastructure/aop/SystemReadinessOrchestrator.java
  D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/infrastructure/aop/SystemStartupReadinessAspect.java
  D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/infrastructure/config/RabbitMqConfig.java

V3:
  platform/ircs-migrator/src/main/resources/db/changelog/**
  services/ircs-config-service/**
  services/ircs-catalog-service/**
  services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/auxiliary/**
  services/ircs-credential-service/**
  services/ircs-task-service/**
  services/ircs-*-worker/src/main/resources/application.yaml
  services/ircs-search-service/src/main/resources/application.yaml
  services/ircs-storage-service/src/main/resources/application.yaml
  services/ircs-content-service/src/main/resources/application.yaml
  services/ircs-interaction-service/src/main/resources/application.yaml
  shared/ircs-messaging/src/main/java/com/prodigalgal/ircs/messaging/RabbitTopologyConfiguration.java
```

## V1 初始化链条

V1 `DataInitializationOrchestrator` 是 `ApplicationRunner`，按 `InitTask.getOrder()` 排序执行。
每个 task 即使失败也会继续后续 task；链条结束后调用 `SystemReadinessOrchestrator.markAsReady()`，
再显式 `rabbitListenerEndpointRegistry.start()`，并启动 `dlqListenerContainer`。

V1 `RabbitMqConfig` 对 `batchContainerFactory`、`simpleContainerFactory`、`rateLimitedContainerFactory`
和 DLQ container 都设置 `setAutoStartup(false)`。因此 V1 的 Rabbit 消费入口由初始化编排主动放行，
不是 Spring Boot 默认启动。

V1 `SystemStartupReadinessAspect` 会拦截所有 `@Scheduled` 方法；`isSystemReady=false` 时直接跳过，
避免初始化未完成时 scheduler 抢跑。

## V1 initializer-to-V3 owner matrix

| V1 initializer / 对象 | V1 order | V1 行为摘要 | V3 owner | V3 当前状态 | seed source 决策 | 风险 | 后续实现建议 |
| --- | ---: | --- | --- | --- | --- | --- | --- |
| `SystemConfigInitializer` / SystemConfig | 0 | 遍历 `SystemConfigKey`，生成 `ADMIN_PASSWORD`、`JWT_SECRET`，读取 env 或默认值，更新 description。 | `services/ircs-config-service` | partial done：已有 `SystemConfigInitializerRunner` + `SystemConfigDefaults`，`JdbcConfigRepository.upsertDefault()` 在启动时补默认 key；C2-S4 已完成 V1 133 个 key full catalog diff；已有测试覆盖 runner 和 catalog。 | runtime owner seed：保留 config-service `ApplicationRunner`；不放入 Liquibase data seed。 | P0：跨服务 cache/event gate、更多消费者 env-first 和 live Rabbit smoke 未闭合。 | C2 继续补全 cache/event；C1 后续空库 test 要覆盖 sensitive defaults、core key description 更新和 readiness 放行。 |
| `CredentialInitializer` / Credential | 5 | 从 env 导入 OPENAI/TMDB/MAIL/R2，或读取 `/etc/ircs/init/credentials.json` / classpath preset，按 fingerprint 去重。 | `services/ircs-credential-service` | done first slice：`028-16-C1-S6` 已新增 credential-service runtime initializer，复刻 V1 env/file 来源、默认字段、占位符跳过和 fingerprint 幂等导入；`028-16-C1-S7` 已补 admin/public summary payload 脱敏，internal lease 仍保留真实 secret。 | runtime owner seed：credential-service idempotent runner；禁止把 secret 写入 Liquibase。 | 空库凭据自动导入与 summary 回显 secret 风险已缓解；真实 provider/MAIL/R2 live smoke 仍需后续。 | 后续补真实 provider/MAIL/R2 live smoke。 |
| `HanLPInitializer` / HanLP | 5 | `HanLP.segment()`、`convertToSimplifiedChinese()`、`CharTable.convert()` 预热 NLP 引擎。 | `services/ircs-normalization-worker` | done first slice：`028-16-A4` 已新增 `HanLpPrewarmRunner`，启动时预热 segment、简繁转换和 CharTable；失败只记录日志，不阻断 worker。 | runtime local warmup：normalization-worker 启动时预热；不进 Liquibase。 | 首批消费预热延迟风险已缓解；仍需后续 ARM64 dev live log/smoke 证明镜像加载稳定。 | A4 已完成本地 runner/test 和 K8S dry-run；后续 live smoke 并入 A 线或 normalization live 验证。 |
| `CategoryInitializer` / Category | 10 | 写入 8 个标准分类：电影、电视剧、综艺、动漫、短剧、纪录片、体育、其他。 | `services/ircs-catalog-service` | partial done：`028-16-C1-S1` 已新增 `CatalogDefaultInitializerRunner`，启动时幂等补 8 类和 slug，catalog-service test 通过。 | runtime owner seed：catalog-service dictionary initializer；当前 Liquibase 只算 schema。 | 已缓解空库标准分类缺失；仍需 live smoke 或 empty dev DB 复验。 | 后续可补 readiness marker；无需在 C1-S1 重复实现。 |
| `AreaInitializer` / Area | 15 | 读取 `presets/countries.json`，按 TMDB country code 写 `standard_areas`，并补 region。 | `services/ircs-catalog-service` | done first closure：`028-16-C1-S2c` 已复用 V1 countries.json，全量读取 251 个 country/area；资源缺失时才回退 P0 子集。 | runtime owner seed：catalog-service 从 preset JSON 导入；当前 Liquibase 只算 schema。 | 全量国家/地区已补；仍需 empty dev DB/live smoke 复验。 | 后续统一测试复验 seed 结果。 |
| `LanguageInitializer` / Language | 20 | 读取 `presets/languages.json` 写 `standard_languages`，再创建 raw alias 映射并 evict cache。 | `services/ircs-catalog-service` | done first closure：`028-16-C1-S2c` 已复用 V1 languages.json，全量读取 187 个 language，并保留 V1 常见 raw alias。 | runtime owner seed：catalog-service 从 preset JSON + alias list 导入；当前 Liquibase 只算 schema。 | 全量 language preset 已补；cache evict/read-through 策略已记录，仍需 live 复验。 | 后续统一测试复验 seed 和 alias 结果。 |
| `StandardGenreInitializer` / StandardGenre | 30 | 写入基础题材、短剧/网文高频词、特定元素、体育、形式、地区剧种等标准题材。 | `services/ircs-catalog-service` | partial done：`028-16-C1-S1` 已按 V1 `STANDARD_GENRES` 全量写入，并补同名 raw genre alias。 | runtime owner seed：catalog-service dictionary initializer；当前 Liquibase 只算 schema。 | 空库 raw genre exact mapping 缺默认目标风险已缓解；仍需 live smoke。 | 后续聚合/清洗任务继续验证具体题材归一行为。 |
| `DataSourceInitializer` / DataSource | 40 | 写入预设资源站，加载 `presets/default_mapping.json`，patch `detail_mapping`、`aliasTitle`、`totalEpisodes`、`duration`；新增后 V1 会尝试远程同步分类。 | `services/ircs-catalog-service` + `services/ircs-scraper-service` | partial done：`028-16-C1-S2` 已 seed 25 个 data source 配置和 default mapping；`028-16-C1-S2b` 已补显式远程 `class[]` 分类同步 service、V1 category inference 和 raw_category upsert 测试；`028-16-C1-S4` 已让 task-service 按 data source 派生默认 collection task；`028-16-C1-S8` 已让 data source create/update/patch 成功后 afterCommit best-effort 触发远程分类同步。 | runtime owner seed：catalog-service 导入预设资源站和 mapping；远程分类同步以显式可控 service 承接；task-service 派生默认任务。 | 空库无 data source、远程分类同步主逻辑、admin 写操作触发和默认 task 派生风险已缓解；真实资源站公网 smoke 仍未执行。 | 后续可补 ops 跨服务手动 sync runner 或 live provider smoke。 |
| `ResolverInitializer` / Resolver | 50 | 写入 4 个解析源及默认线路，`is_active=true`。 | `services/ircs-content-service` auxiliary | done first slice：`028-16-C1-S5` 已新增 content-service 默认 resolver preset seeder，按 V1 四个解析源和 `默认线路` 幂等补缺。 | runtime owner seed：content-service auxiliary initializer；当前 Liquibase 只算 schema。 | 空库默认解析源缺口已缓解；真实播放器 live smoke、resolver cache TTL/evict 仍需后续验证。 | 后续可并入 portal/admin 播放链路 smoke 或 C2 cache/evict 矩阵。 |
| `TaskInitializer` / Task | 60 | 对每个 data_source 创建“自动采集”和“全量采集”，包含 cron、Asia/Shanghai、随机 delay、timeout/retry、random UA。 | `services/ircs-task-service` | done first slice：`028-16-C1-S4` 已新增 task-service 默认 collection task seeder，按 V1 名称和字段值幂等派生两类任务；task cron scheduler 默认由 `APP_TASK_SCHEDULER_ENABLED:false` 关闭；`028-16-C1-S9` 已让 `TaskWatchdog` 由 `APP_TASK_WATCHDOG_ENABLED:false` 默认关闭并补 context guard 测试。 | runtime owner seed：task-service 在 data_sources 可见后派生任务；不放入 Liquibase。 | 空库默认任务缺失风险已缓解；cron 和 watchdog 自动执行仍需显式 gate 打开，避免 dev 默认对外抓取或扫描 stale active tasks。 | 后续 live smoke 可显式开启 scheduler/watchdog gate；全局 readiness orchestrator 仍需另拆。 |
| `CacheInitializer` / Cache | 100 | 清理并预热 SystemConfig、RateLimit、Language、Genre、Area、Resolver、Category、Standard* cache。 | 多服务 owner：config/catalog/content/task/worker 本地 cache | missing/needs evidence：V3 多为 service-local JDBC/Redis/内存访问，未见 V1 cache warmup/evict matrix。 | runtime local warmup：各 owner 在本服务 initializer 后预热本地 cache；不做全局单体 cache initializer。 | P1：启动初期 cache miss 可接受，但 config/dictionary 更新后的失效窗口和 TTL 未定义。 | 拆到 C2/cache TTL 子任务：先列 cache name/TTL/evict matrix，再决定是否需要 warmup。 |

## V3 listener/readiness 现状

- 已有缓解项：normalization、metadata、search、ingestion、storage、content、interaction 等服务在
  `application.yaml` 中把 `spring.rabbitmq.listener.simple.auto-startup` 绑定到 `APP_*_LISTENER_ENABLED:false`。
  这能通过部署环境变量阻止消费者默认启动。
- 缺口：未见 V3 等价的 `SystemReadinessOrchestrator`、`SystemStartupReadinessAspect` 或
  `RabbitListenerEndpointRegistry.start()` 自动放行链条；当前 env gate 更像人工/部署 gate。
- 已缓解例外风险：`028-16-C1-S3` 已让 notification-worker `MailConsumer` 使用
  `${app.notification.listener.enabled:false}`，并暴露
  `APP_NOTIFICATION_LISTENER_ENABLED:false`；`MailConsumerTest` 已固定该 listener gate。
- scheduler 风险：task-service `TaskWatchdog` 已由 `028-16-C1-S9`
  补 `APP_TASK_WATCHDOG_ENABLED:false` 默认关闭 guard；normalization retry watchdog、
  aggregation scheduler 和 storage R2 watchdog 等仍是各服务局部 `@Scheduled`，
  未见统一初始化 ready guard。

## runtime readiness gate 设计记录

C1 后续实现建议采用“owner-service 局部初始化 + worker/listener 延迟启动”，不新增 HTTPRoute，
也不恢复 V1 单体式全局 initializer。

```text
1. config-service:
   - 启动时 upsert SystemConfig defaults。
   - 完成后写入可读 readiness marker，例如 system_configs 中的 ircs.init.config-service.ready。

2. catalog-service / content-service / credential-service / task-service:
   - 各自只 seed 自己拥有的数据。
   - catalog 先补 dictionary/data_sources；content 补 resolver；credential 补 env/file credentials；
     task 在 data_sources 可见后派生默认 collection_tasks。
   - 每个 runner 必须幂等，重复启动只更新 description/mapping/缺失项，不覆盖用户编辑值。

3. worker 和 Rabbit listener:
   - listener 继续默认 auto-startup=false。
   - 本服务 `ApplicationRunner` 读取必需 readiness marker 和本地 initializer 结果。
   - 条件满足后调用 `RabbitListenerEndpointRegistry.start()`。
   - 条件不满足时保持 HTTP/actuator 可用但 listener 不启动；日志和 health detail 标明等待项。

4. scheduler:
   - 对 `@Scheduled` 或 `ApplicationReadyEvent` cron 注册点增加轻量 ready guard。
   - 已经延迟注册的 task-service cron scheduler 仍需在 refresh 前确认 task seed 完成。

5. 测试:
   - owner service 用 repository/service test 验证 empty-db idempotent seed。
   - worker service 用 slice test 验证未 ready 不 start listener，ready 后 start registry。
```

## 当前 MARK

```text
MARK 1/6 V1 initializer-to-V3-owner matrix completed: done 2026-06-08 read-only
MARK 2/6 SystemConfig/language/area/category/genre/resolver/data-source/task/credential seed source decided: done 2026-06-08 read-only
MARK 3/6 runtime readiness gate design documented: done 2026-06-08 read-only
MARK 4/6 empty-db seed tests cover highest-risk defaults: partial 2026-06-10; catalog category/genre/data_sources/default_mapping seed covered by catalog-service tests; catalog countries/languages now use full V1 JSON preset and focused tests cover 251 countries + 187 languages; content-service default resolver seed covered by C1-S5 local tests; credential-service runtime env/file seed covered by C1-S6 local tests; task-service default collection task seed covered by C1-S4 local tests
MARK 5/6 worker/Rabbit start gate has test or smoke evidence: partial 2026-06-10; notification-worker mail listener gate covered by C1-S3 local test; task cron scheduler gate covered by C1-S4 local test; task watchdog default-off gate covered by C1-S9 local context test; normalization SystemConfig missed-event TTL fallback covered by focused test
MARK 6/6 docs/tasks indexes and 089 inventory updated: done 2026-06-10 for current C1-S2c scope; global docs/tasks indexes integrated by documentation worker
```

## 2026-06-09 028-16-C1 full readiness orchestrator / auto start gate first closure

本轮目标是关闭 C1 剩余的 first closure，而不是一次性恢复 V1 单体式全局
`DataInitializationOrchestrator`。策略如下：

- ENV/K8S/启动参数等运行时注入仍是最高优先级；DB/default seed 只作为最低兜底。
- Rabbit listener、cron scheduler、watchdog、真实 provider/SMTP/R2 等危险自动入口默认关闭。
- 明确需要运行的 smoke 才通过 K8S env 显式开启，并保持 batch/资源低配。
- readiness/auto-start gate 必须可观察：health detail 或 smoke 输出需能看见 gate 名称、配置 key、
  effective value、source 和 blocked/enabled 状态。

影响范围：

```text
shared/ircs-common readiness/auto-start gate helper
services/ircs-normalization-worker watchdog default gate
services/ircs-storage-service R2 watchdog default gate
services/ircs-aggregation-worker scheduler dev gate
deploy/k8s/dev normalization/storage/aggregation low-resource env
scripts/smoke/readiness-auto-start-gate-02816c1.ps1
docs/tasks indexes and inventory
```

验收标准：

```text
MARK C1-F1 shared auto-start gate state exposes injected/default source
MARK C1-F2 normalization/storage dangerous watchdog beans default closed and opt-in only
MARK C1-F3 dev K8S manifests keep dangerous runner gates false unless a smoke enables them
MARK C1-F4 focused tests cover default-off, opt-in and observable source details
MARK C1-F5 readiness smoke/dry-run reports key gates and no side-effect env
```

## 本轮未实现项

- 本轮 C1-S9 修改了 task-service watchdog gate、配置、dev manifest 和本地任务/报告；未新增 HTTPRoute。
- 已新增 catalog-service empty-db seed tests；content-service default resolver seed 测试已完成；credential-service runtime env/file seed 测试已完成；task-service default collection task seed 和 watchdog default-off gate 测试已完成；更多 worker gate 测试未完成。
- 未实现全服务 worker/Rabbit 自动 start gate；已完成 notification-worker mail listener 默认关闭 guard、task cron scheduler 默认关闭 guard 和 task watchdog 默认关闭 guard。
- `docs/index.md` 和 `tasks/index.md` 已由文档集成 worker 纳入 C1-S9 全局 done summary。

## 2026-06-09 C1-S3 notification listener gate

已新增并完成：

```text
tasks/done/028-16-c1-s3-notification-listener-start-gate.md
```

本切片补齐 notification-worker 例外缺口：V1 所有 Rabbit listener 默认
`autoStartup=false`，而 V3 notification-worker 之前未配置 listener gate。现在
`MailConsumer` 使用 `${app.notification.listener.enabled:false}`，`application.yaml`
暴露 `APP_NOTIFICATION_LISTENER_ENABLED:false`，测试固定该表达式。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

## 2026-06-09 C1-S4 task-service default collection task seed

已新增并完成：

```text
tasks/done/028-16-c1-s4-task-service-default-collection-task-seed.md
```

本切片补齐 V1 `TaskInitializer` 的核心行为：task-service 会按每个 `data_sources`
幂等派生 `自动采集 - {name}` 与 `全量采集 - {name}`，默认字段固定为 V1 的
`BY_PAGE`、`Asia/Shanghai`、`startPage=1`、`endPage=0`、随机延迟 `3000..8000ms`、
`timeoutMs=15000`、`maxRetries=3` 和 `enableRandomUa=true`。同名任务存在时跳过，
不覆盖用户编辑字段。

同时新增 `APP_TASK_SCHEDULER_ENABLED:false`，让 dev 默认 seed 任务但不注册 cron，
避免初始化完成后自动对外抓取；需要 smoke 时再显式打开。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-task-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml
deployment.apps/ircs-task-service configured (server dry run)
service/ircs-task-service unchanged (server dry run)
```

## 2026-06-09 C1-S5 content-service resolver preset seed

已新增并完成：

```text
tasks/done/028-16-c1-s5-content-resolver-preset-seed.md
```

本切片补齐 V1 `ResolverInitializer` 的默认解析源初始化：content-service 启动时按名称
幂等补齐 `金蝉解析`、`789解析`、`火花解析`、`麒麟解析` 四个 resolver，每个 resolver
写入 `isActive=true`、V1 remark 和一条 `默认线路`。同名 resolver 存在时跳过，不覆盖
用户编辑值；`APP_CONTENT_RESOLVER_PRESET_SEED_ENABLED` 可显式关闭。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\content-service-dev.yaml
deployment.apps/ircs-content-service configured (server dry run)
service/ircs-content-service unchanged (server dry run)
```

## 2026-06-09 C1-S6 credential-service runtime initializer

已新增并完成：

```text
tasks/done/028-16-c1-s6-credential-runtime-initializer.md
```

本切片补齐 V1 `CredentialInitializer` 的第一刀：credential-service 启动时从 ENV 导入
`OPENAI`、`TMDB`、`MAIL`、`R2`，并从 `/etc/ircs/init/credentials.json` 或 classpath
`presets/credentials.json` 兜底导入文件凭据。导入复用 V3 `CredentialProviderCatalog`
的校验与 fingerprint，重复 fingerprint 时跳过，不覆盖用户编辑值；日志只记录 provider/name
和计数，不记录 payload 值。dev manifest 仅配置 initializer 开关和文件路径，不提交真实 secret。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\credential-service-dev.yaml
deployment.apps/ircs-credential-service configured (server dry run)
service/ircs-credential-service unchanged (server dry run)
```

## 2026-06-09 C1-S7 credential summary redaction

已新增并完成：

```text
tasks/done/028-16-c1-s7-credential-summary-redaction.md
```

本切片收口 C1-S6 引入真实凭据导入后的敏感响应风险：admin/public
`CredentialSummary.payload` 不再回显原始 secret，只按 key 返回
`present=true`、`redacted=true` metadata；`payloadKeys` 与 `fingerprintSuffix` 保持可用。
internal lease 仍走 `CredentialLeaseMapper`，可信内部服务可在 token 保护下获取真实 secret。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\credential-service-dev.yaml
deployment.apps/ircs-credential-service configured (server dry run)
service/ircs-credential-service unchanged (server dry run)
```

## 2026-06-09 C1-S8 data-source category sync trigger

已新增并完成：

```text
tasks/done/028-16-c1-s8-data-source-category-sync-trigger.md
```

本切片补齐 V1 `DataSourceServiceImpl` 的 admin 写操作副作用：catalog-service
`createDataSource()`、`updateDataSource()` 和 `patchDataSource()` 成功保存后，会在事务提交后
best-effort 触发 `CatalogRemoteCategorySyncService`，同步资源站 `class[]` 到 raw category。
同步失败只记录，不影响 data source 写入响应；patch 未找到数据源时不触发。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\catalog-service-dev.yaml
deployment.apps/ircs-catalog-service configured (server dry run)
service/ircs-catalog-service unchanged (server dry run)
```

## 2026-06-09 C1-S9 task-service watchdog start gate

已新增并完成：

```text
tasks/done/028-16-c1-s9-task-service-watchdog-start-gate.md
```

本切片补齐 task-service 仍会自动启动的局部 scheduler 缺口：`TaskWatchdog`
增加 `app.task.watchdog.enabled` 条件，`application.yaml` 和 dev manifest 均暴露
`APP_TASK_WATCHDOG_ENABLED:false`。默认缺失属性时不创建 watchdog bean；需要 stale active task
自动扫描时必须显式开启。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-task-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml
deployment.apps/ircs-task-service configured (server dry run)
service/ircs-task-service unchanged (server dry run)
```

## 2026-06-09 full readiness orchestrator / auto start gate first closure

已新增并完成：

```text
tasks/done/028-16-c1-full-readiness-auto-start-gate-first-closure.md
```

本切片关闭 C1 剩余 auto-start gate 的 first closure：`shared:ircs-common` 新增
`AutoStartGateInspector` / `AutoStartGateHealthIndicator`，统一暴露 gate 的
`enabled/source/sourceKey/blocked`；source 只把 ENV/K8S/启动参数/configtree 等运行时注入视为
`INJECTED`，DB/default seed 和 `application.yaml` 静态默认继续作为最低兜底。

normalization retry watchdog 与 storage R2 watchdog 现在默认不创建 bean，必须显式设置
`APP_NORMALIZATION_WATCHDOG_ENABLED=true` 或 `APP_STORAGE_R2_WATCHDOG_ENABLED=true`
才启动；dev K8S 还把 `APP_AGGREGATION_SCHEDULER_ENABLED=false` 固定为常驻安全态。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\normalization-worker-dev.yaml -f deploy\k8s\dev\storage-service-dev.yaml -f deploy\k8s\dev\aggregation-worker-dev.yaml
deployment.apps/ircs-normalization-worker configured (server dry run)
deployment.apps/ircs-storage-service configured (server dry run)
service/ircs-storage-service unchanged (server dry run)
deployment.apps/ircs-aggregation-worker configured (server dry run)

.\scripts\smoke\readiness-auto-start-gate-02816c1.ps1 -UseCluster
MARK 1/3 local application defaults keep dangerous gates false
MARK 2/3 dev manifests explicitly keep dangerous gates false
MARK 3/3 live deployments keep dangerous gates false
```

MARK 状态：

```text
MARK C1-F1 shared auto-start gate state exposes injected/default source: done
MARK C1-F2 normalization/storage dangerous watchdog beans default closed and opt-in only: done
MARK C1-F3 dev K8S manifests keep dangerous runner gates false unless a smoke enables them: done
MARK C1-F4 focused tests cover default-off, opt-in and observable source details: done
MARK C1-F5 readiness smoke reports key gates and no side-effect env: done
```

C1 父任务不完全关闭；full auto-start gate first closure 已关闭。cache warmup/TTL/evict
first closure 与 catalog countries/languages 全量 V1 preset 已由 C1-S2c 完成实现。剩余拆分项为
resolver/真实资源站 live smoke，以及后续若确需 V1 式 `RabbitListenerEndpointRegistry.start()`
自动放行再独立实现。

## 2026-06-10 readiness/initialization remaining first closure

已新增并完成实现，live smoke 统一测试后置：

```text
tasks/in-progress/028-16-c1-s2c-readiness-initialization-first-closure.md
```

本切片按用户最新策略先推进功能完整，不做逐一 live/完整测试：

- catalog-service 启动 seed 已从 V1 `presets/countries.json` / `presets/languages.json` 读取全量 preset：
  251 个 country/area、187 个 language；资源缺失时才回退原 P0 子集。
- 默认 seed 仍是最低优先级：repository 只插入缺失项或补空字段，不覆盖用户编辑行；ENV/K8S/
  启动参数/DB 真实注入仍高于 default seed。
- cache first closure 策略：catalog 字典、data source、resolver、task、credential 由 owner
  initializer 启动期 seed/warmup；SystemConfig 类 cache 使用 Rabbit event evict + short TTL
  read-through。本轮把 normalization-worker SystemConfig cache 补为默认 `30s` TTL，
  content-service 已由 C2-S21 覆盖。
- 新增 resolver/真实资源站 live smoke prep 脚本：
  `scripts/smoke/resolver-resource-live-02816c1-prep.ps1`，默认 dry-run 不发 HTTP；
  真实调用必须显式 `-ExecuteLive -AllowExternalFetch`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --tests com.prodigalgal.ircs.catalog.CatalogDefaultInitializerRunnerTest
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests com.prodigalgal.ircs.normalization.SystemConfigRepositoryTest
BUILD SUCCESSFUL

.\scripts\smoke\resolver-resource-live-02816c1-prep.ps1
MARK 1/5 .. MARK 5/5 dry-run only; no HTTP calls will be sent
```

剩余统一测试项：

- resolver/player/真实资源站 live smoke 并入 `UTB-02816-P1P2-02 C1 readiness`。
- 完整 Gradle suite 后置。
- 如后续确需 V1 式 `RabbitListenerEndpointRegistry.start()` 自动放行，再独立实现。

## 2026-06-10 本轮收敛口径

本父任务当前不能按“完全生产闭环”关闭。可入账为已完成实现的范围：

- `028-16-C1-S2c`：V1 full countries/languages preset、normalization SystemConfig 30s TTL
  fallback、resolver/resource live prep 默认 dry-run。
- `028-16-C1` first closure：shared auto-start gate health detail、normalization/storage watchdog
  默认关闭、aggregation scheduler dev gate 默认关闭已有证据。

仍后置到统一测试窗口的范围：

- empty dev DB/live seed 复验。
- resolver/player/真实资源站 live smoke。
- catalog/content/normalization-worker 组合回归。
- 如确认需要，V1 式 `RabbitListenerEndpointRegistry.start()` 自动放行独立切片。
