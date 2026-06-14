# 028-16-C2 SystemConfig cache/event parity task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `SystemConfigInitializer`
- V1 `SystemConfigCacheAccessor`
- V1 `SystemConfigServiceImpl`
- V3 `ConfigService`

## 背景

V1 `SystemConfigInitializer` 会补齐 `SystemConfigKey` 默认值，包含 `ADMIN_PASSWORD` BCrypt、
`JWT_SECRET` 自动生成和 classpath 默认 JSON；读取通过 cache accessor，写入时 evict，并对
LLM prompt 等关键配置做校验。V3 config-service 当前主要是 CRUD 与 dry-run connectivity，
其他服务局部读取 DB，跨服务变更生效窗口和默认 key 补齐仍不清晰。

## 目标

- 建立 V1 `SystemConfigKey` 到 V3 config key 的映射。
- 补齐敏感默认值生成和不可明文回显边界。
- 固定配置优先级：运行时读取必须是 ENV/K8S Secret/启动参数等环境注入优先，其次 DB，
  最后才是初始化默认值；V1 风格初始化只允许作为最低优先级兜底种子。
- 定义 config 变更通知策略：事件、TTL 或每次读取；需要写清选择理由。
- 对至少 `app.ai.llm.enabled`、mail/storage/rate-limit 类 key 建本地测试。

## 非目标

- 不实现完整配置中心 SaaS。
- 不强制所有服务同步刷新到零延迟。
- 不访问真实外部 LLM/MAIL/R2。

## 影响范围

```text
services/ircs-config-service/**
shared/** if a common config client/event contract is introduced
selected consumer services only after owner decision
docs/tasks
```

## 验收标准

```text
MARK 1/7 V1 SystemConfigKey mapping table completed: done 2026-06-09, C2-S4 full V1 133-key catalog
MARK 2/7 default sensitive values are generated or explicitly delegated: done
MARK 3/7 prompt/config validation gaps documented or implemented: done
MARK 4/7 runtime priority is env/injection -> DB -> default seed: done, reinforced with effective source API
MARK 5/7 config-service tests cover update/read/redaction/default behavior: done, includes effective source redaction
MARK 6/7 cache/event/TTL refresh strategy implemented or decided with evidence: done first slices, config change event publisher plus identity/interaction/search consumers done
MARK 7/7 docs/tasks indexes and 089 inventory updated: done
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
```

## 当前状态

2026-06-08：已完成第一刀。config-service 新增 V1 风格 `SystemConfigDefaults`、
`SystemConfigInitializerRunner`，启动时补齐默认 key 和 description；`security.admin.password`
和 `security.jwt.secret` 会在缺省时生成非空敏感值。`ConfigService` 已补 LLM prompt 占位符校验、
MAIL SSL/STARTTLS 冲突校验和 core config delete 保护。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-08：已完成第二刀。V1 风格 `SystemConfigInitializerRunner` 现在只写最低优先级
默认种子，不再把环境注入值固化进 DB。运行时配置读取顺序固定为：

```text
ENV / K8S Secret / 启动参数等环境注入
  -> system_configs DB
  -> 初始化默认值或代码 fallback
```

本轮已覆盖：

- config-service 的 MAIL SSL/STARTTLS 冲突校验改为按 env-first effective value 判断。
- identity-service 的 `IdentityConfigService.value()` 改为 env-first；支持 V1 风格
  `security.admin.password` 明文注入在运行时编码参与 BCrypt 匹配。
- 当管理员密码由环境注入覆盖时，`AdminAuthService.changePassword()` 明确拒绝 DB 改密，
  避免“DB 已改但登录仍由 env 覆盖”的误导。
- interaction-service 的 member JWT secret/iat-floor 与 `member.message.daily-limit`
  改为 env-first。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL
```

遗留：

- 默认 catalog 已由 C2-S4 补齐为 V1 `SystemConfigKey` 全量 133 个 key。
- 跨服务 cache/event/TTL refresh 的 publisher 第一刀已完成；identity/interaction 当前仍按每次读取
  env-first/DB fallback 生效。
- 配置变更事件尚未通知 metadata/storage 等更多消费者；identity/interaction 当前按每次读取
  env-first/DB fallback 生效。

2026-06-08：本轮补强“初始化默认值最低优先级、运行时注入最高优先级”的可见边界。
V1 风格 `SystemConfigInitializerRunner` 可以继续保留，但它只能作为空库兜底 seed，不能把
ENV、K8S Secret、启动参数或其他运行时注入值写回 `system_configs`。admin/config API 需要能
区分“DB 管理值”和“当前生效值来源”，避免后续在门户验证时误以为 DB 默认值覆盖了环境注入。

本轮计划：

- `SystemConfigSummary` 保留原 `value` 作为 DB 管理值，同时增加 `effectiveValue` 与
  `effectiveSource`。
- `effectiveSource` 至少区分 `INJECTED`、`DB`、`DEFAULT`，敏感 key 的 `value` 与
  `effectiveValue` 均保持脱敏。
- `ConfigService` 在 list/get/create/update 后统一按 `Environment -> DB -> default seed`
  计算摘要；delete/core 保护和现有校验语义不变。
- 本轮只解决 config-service 可见语义；跨服务 cache/event/TTL refresh 后续由 MARK 6/7
  事件发布与消费者 evict/reload 切片继续闭环。

实现结果：

- `SystemConfigSummary` 已增加 `effectiveValue` 与 `effectiveSource`，旧构造器保留，避免
  既有 controller 测试和调用方一次性断裂。
- `ConfigService` 已统一返回 `INJECTED`、`DB`、`DEFAULT` 来源；支持 `security.jwt.secret`
  -> `app.identity.jwt.secret`、`security.admin.password` -> `app.identity.admin.password-hash`
  等 V3 runtime alias。
- `ConfigValueRedactor` 会同时脱敏 DB 管理值和 effective value；敏感 key 只暴露来源。
- `SystemConfigDefaults.staticDefaultValue()` 只读取静态默认值，不触发 `ADMIN_PASSWORD` /
  `JWT_SECRET` 随机生成；随机值仍只由 initializer 空库 seed 生成一次。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：按用户确认再次复核配置优先级边界。结论保持为：V1 风格配置初始化可以保留，
但只能作为空库最低优先级 seed；ENV、K8S Secret、启动参数、`@Value`、`Environment`
等运行时注入源始终是最高优先级覆盖源。

复核结果：

- `SystemConfigInitializerRunner` 只遍历 `SystemConfigDefaults` 并调用
  `JdbcConfigRepository.upsertDefault()`。
- `JdbcConfigRepository.upsertDefault()` 在 `config_key` 冲突时只更新 `description`、
  `updated_at` 与 `version`，不会覆盖已有 `config_value`，也不会把注入值写回 DB。
- `ConfigService.resolveEffectiveValue()` 按 `SystemConfigDefaults.injectionKeys(key)` 查找
  原 key 与 V3 runtime alias，命中 `Environment` 时返回 `INJECTED`，否则才落到 DB 和
  static default。
- 既有测试已经覆盖 create/update/find 场景下 `INJECTED` 高于 DB，以及敏感 alias 注入值只暴露
  `effectiveSource` 不回显值。

验收补充：

```text
MARK 4/7 V1 config initializer remains lowest priority seed and injected runtime values stay highest priority: done 2026-06-09
```

遗留：

- admin 前端尚未显式展示 `effectiveSource`，但 BFF/API 已可透传。
- metadata/storage/normalization/aggregation 等更多 SystemConfigKey 消费方仍需在后续切片逐项
  迁到 env-first 或明确只接受 `@Value` 注入。
- config-service 事件 publisher 第一刀已完成；跨服务消费者 evict/reload 仍未闭环。

2026-06-09：按用户补充要求继续加固 P0 消费方。上一刀已经让 config-service 自身对
`APP_*` / 大写下划线 ENV 名称保持最高优先级，但 identity-service 和 interaction-service
仍有直连 `system_configs` 的读路径。真实 Spring Boot 环境里的 `application.yaml`
占位符默认值可能被 `Environment.getProperty()` 读到，如果把这些静态默认值当成运行时注入，
会导致 DB 中由 V1 initializer seed 的值无法作为中间优先级生效。因此本轮只收紧 P0
消费者的读取模型：

- 运行时覆盖源：命令行参数、system properties、system environment、`SPRING_APPLICATION_JSON`
  和测试/显式注入 property source。
- 非运行时覆盖源：`application.yaml` 内的静态默认值、代码 fallback、V1 initializer seed。
- 读取顺序固定为运行时覆盖源 -> `system_configs` DB -> service fallback/default。
- identity-service 继续支持 V3 alias 与 V1 raw key；interaction-service 继续支持 member JWT
  与留言限流配置。

本轮范围：

- `services/ircs-identity-service/src/main/java/com/prodigalgal/ircs/identity/**`
- `services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/**`
- 对应单元测试

非目标：

- 不改 metadata/storage/notification/normalization/aggregation 等启动绑定配置。
- 不引入远程配置中心或全局动态刷新客户端。
- 不改变 config-service initializer 的 seed key catalog。

实现结果：

- `identity-service` 新增 service-local `RuntimeInjectedConfig`，只从 `commandLineArgs`、
  `systemProperties`、`systemEnvironment`、`SPRING_APPLICATION_JSON` 和测试显式注入 source
  读取最高优先级覆盖值；`application.yaml` 静态默认值不再被误判为 env 注入。
- `IdentityConfigService.value()` 继续按 V3 alias 与 V1 raw key 查找运行时覆盖源，并显式支持
  `APP_*` / 大写下划线 ENV 名称；`security.admin.password` / `SECURITY_ADMIN_PASSWORD`
  明文运行时注入仍会即时 BCrypt 编码。
- `interaction-service` 新增同样的 service-local `RuntimeInjectedConfig`，`MemberTokenService`
  的 JWT secret/iat-floor 和 `FeedbackCommandService` 的 `member.message.daily-limit` 均按
  运行时覆盖源 -> DB -> fallback 读取。
- 补充测试证明：K8S env name 可覆盖 DB；`application.yaml` 默认值不会覆盖 DB；V1 raw admin
  password 的大写 env 仍能运行时编码。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL

ConfigServiceTest: tests=18, failures=0, errors=0
JdbcConfigRepositoryTest: tests=1, failures=0, errors=0
IdentityConfigServiceTest: tests=8, failures=0, errors=0
MemberTokenServiceTest: tests=7, failures=0, errors=0
FeedbackCommandServiceTest: tests=9, failures=0, errors=0
```

2026-06-08：本轮开始第三刀，补 V1 `@CacheEvict(CACHE_L2_SYSTEM_CONFIG)` 在微服务形态下的
跨进程替代契约。V1 的 create/update/delete 会在同一 JVM 中按 key evict
`CACHE_L2_SYSTEM_CONFIG`；V3 不能依赖单体本地 cache，因此第一刀先定义 domain event 并在
config-service 写操作成功后发布，供后续消费者做本地 cache invalidation 或主动 reload。

本轮范围：

- 新增 shared contract `SystemConfigChangedEvent`，只包含 key、action、effectiveSource、
  sensitive、changedAt，不携带配置值。
- 使用既有 `QueueTopic.Names.DOMAIN_EVENT_X`，routing key 固定为
  `config.system.changed`，避免把 domain broadcast 塞入一队列一 owner 的 `QueueTopic`。
- config-service 在 create/update/delete 成功后 best-effort publish；发布失败不回滚 DB
  写入，但要保留日志和本地测试证据。
- 本轮不实现所有服务消费；消费者优先级由只读扫描补入本任务与 089 总账。

验收补充：

2026-06-09：用户确认 V1 版本里的配置初始化可以继续保留，但它必须是最低优先级兜底；
ENV、K8S Secret、启动参数、`@Value`/`Environment` 等运行时注入渠道是最高优先级覆盖源。
本轮只做优先级防回退锁定，不扩大默认 key catalog。

本轮范围：

- 复核 `SystemConfigInitializerRunner` 只写默认 seed，且已有 DB 值时不覆盖 `config_value`。
- 复核 `ConfigService` 对外摘要仍按 `Environment/injection -> DB -> default seed` 计算
  `effectiveValue` 与 `effectiveSource`。
- 显式兼容 K8S/ENV 常见大写下划线注入形态，例如 `APP_MAIL_ENABLED`、
  `APP_IDENTITY_JWT_SECRET`，避免轻量测试环境未启用 Spring relaxed binding 时发生优先级回退。
- 增加 config-service 单元测试，证明 create/update 后即使 DB 值存在，运行时注入仍作为
  `INJECTED` 生效并参与事件来源。

非目标：

- 不在本轮补全所有 V1 `SystemConfigKey`。
- 不把 metadata/storage/normalization/aggregation 等更多消费者一次性改成动态配置客户端。
- 不把 env 注入值写回 `system_configs`。

验收补充：

```text
MARK 4/7 runtime priority env/injection -> DB -> default seed regression: done
```

实现结果：

- 已复核 `SystemConfigInitializerRunner` 仅调用 `JdbcConfigRepository.upsertDefault()`；该 SQL 在
  conflict 时只更新 description/version，不覆盖已有 `config_value`。
- `SystemConfigDefaults.injectionKeys()` 已同时返回原始 property key、V3 runtime alias 和
  `APP_*` 大写下划线 env name，覆盖 K8S Secret/ConfigMap env 注入的常见形态。
- `ConfigService.resolveEffectiveValue()` 与写入校验里的 `effectiveValue()` 共用上述 env-first
  解析，因此展示、事件来源和 MAIL SSL/STARTTLS 冲突校验不会在 `APP_*` 形态下回退到 DB。
- 已补 `JdbcConfigRepositoryTest.upsertDefaultDoesNotOverwriteExistingConfigValueOnConflict()`，
  用 SQL contract 捕获 `on conflict do update` 分支，防止默认 seed 后续回退为覆盖
  `config_value`。
- 已补 `ConfigServiceTest.createSummaryUsesInjectedValueWhenEnvironmentOverridesNewDbConfig()`，
  证明 create 后 DB 管理值存在时，`effectiveValue` 仍由 `Environment` 注入值覆盖，事件来源为
  `INJECTED`。
- 已补 `ConfigServiceTest.updateSummaryKeepsRuntimeInjectionAboveStoredConfigValue()`，证明 update
  后运行时注入仍高于 DB 值，事件来源继续为 `INJECTED`。
- 已补 `ConfigServiceTest.exposesInjectedEffectiveSourceWhenK8sEnvironmentNameOverridesStoredConfig()`、
  `exposesAliasInjectedEffectiveSourceForV3K8sEnvironmentName()` 和
  `validatesAgainstK8sEnvironmentNameBeforeStoredConfig()`，证明 `APP_MAIL_ENABLED`、
  `APP_IDENTITY_JWT_SECRET`、`APP_MAIL_PROPERTIES_SSL` 这类 K8S env name 同样高于 DB。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL

ConfigServiceTest: tests=18, failures=0, errors=0

.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-scraper-service:test :services:ircs-storage-service:test :shared:ircs-common:test --rerun-tasks
BUILD SUCCESSFUL
```

```text
MARK 6/7 config change event contract and config-service publisher: done first slice
```

实现结果：

- 新增 shared contract `SystemConfigChangedEvent`，routing key 为 `config.system.changed`，
  使用 `x.domain.events` 作为广播 exchange。
- `ConfigService.createConfig()`、`updateConfig()`、`deleteConfig()` 成功后发布事件；
  publisher 通过 transaction `afterCommit` 发送，避免 DB 回滚后误发。
- 事件 payload 使用 JSON 字符串，字段只包含 `key/action/effectiveSource/sensitive/changedAt`，
  不携带 `value`、`effectiveValue` 或任何 secret。
- `SystemConfigChangePublisher` 是 best-effort：RabbitMQ 异常只记录 warning，不回滚 DB 写入。
- `deploy/k8s/dev/config-service-dev.yaml` 已为 config-service 注入 dev RabbitMQ 连接参数，
  不新增 Pod/Service。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL
```

消费者优先级扫描：

- P0：`identity-service` 和 `interaction-service` 当前已 env-first + 每次 DB 读，因此配置更新
  即时但高频；若加入本地 cache，必须第一批订阅 `SystemConfigChangedEvent` 并按 key evict。
- P0 细节：`identity-service` 的 `IdentityConfigService.updateValue()` 仍直接写 `system_configs`，
  绕过 config-service publisher；下一刀需补事件或统一改走 config-service。
- P1：`notification-worker`、`storage-service`、`metadata-worker` 大量配置仍是
  `@Value` / `@ConfigurationProperties` 启动绑定，DB 配置变更需要重启或后续 config client。
- P2：BFF route targets、ops/magnet/scraper/worker knobs 多数是启动注入型配置，dev 环境可以
  继续接受重启生效，但需要在全量 parity 前逐项标注。

2026-06-08：本轮开始第四刀，把 MARK 6/7 从“config-service 只发布事件”推进到
P0 消费者和旁路写入补偿。当前 `identity-service` / `interaction-service` 虽然已经
env-first 且每次 DB 读，但这会在高频鉴权、留言限流路径反复查 `system_configs`；V1 在单体
JVM 内依赖 cache + `@CacheEvict(CACHE_L2_SYSTEM_CONFIG)`，V3 需要用 domain event 恢复
“可缓存但可失效”的行为边界。同时，admin 改密仍由 `IdentityConfigService.updateValue()`
直接写 `system_configs`，绕过 config-service publisher，需要补同等事件，避免其他服务仍使用
旧 JWT secret / iat floor。

本轮范围：

- 在 identity-service 和 interaction-service 为 `SystemConfigRepository.findValue()` 增加
  小型本地 cache，并提供 `evict(key)`。
- 为两个服务增加 `SystemConfigChangedEvent` Rabbit listener，绑定各自独立队列到
  `x.domain.events` / `config.system.changed`，收到事件后按 key evict 本地 config cache。
- identity-service 的 `updateValue()` 仍保留直接 DB upsert，但成功后发布
  `SystemConfigChangedEvent`，并遵循 transaction afterCommit / best-effort，不携带配置值。
- 本轮不把 metadata/storage/notification 等启动绑定配置强行改为动态配置；只在总账继续标注。

验收补充：

```text
MARK 6/7 P0 identity/interaction config event consumer and identity side-write publisher: done first slice
```

实现结果：

- `identity-service` 和 `interaction-service` 的 `SystemConfigRepository.findValue()` 已增加
  进程内 key cache，收到 config changed event 后按 key evict。
- 两个服务分别声明独立广播消费队列：`q.identity.config_changed`、
  `q.interaction.config_changed`，均绑定到 `x.domain.events` / `config.system.changed`，
  不占用一队列一 owner 的 `QueueTopic` 业务队列。
- 两个 config listener 均使用独立 `autoStartup` 开关：
  `app.identity.config-listener.enabled` / `app.interaction.config-listener.enabled`；dev K8S
  manifest 显式设置 `APP_IDENTITY_CONFIG_LISTENER_ENABLED=true` 和
  `APP_INTERACTION_CONFIG_LISTENER_ENABLED=true`，不和业务队列开关混用。
- `identity-service` 的 admin 改密旁路写入仍可直接 upsert `system_configs`，但
  `IdentityConfigService.updateValue()` 成功后会发布 `SystemConfigChangedEvent`，并通过
  transaction `afterCommit` best-effort dispatch；payload 不携带配置值。
- 本轮未把 metadata/storage/notification/normalization/ops 等启动绑定配置改为动态配置，
  这些继续留在后续逐项 env-first / `@Value` 决策中。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：本轮继续按用户要求加固“V1 配置初始化最低优先级、ENV/环境注入最高优先级”。
复核发现 identity-service / interaction-service 已有 `RuntimeInjectedConfig`，能区分
`commandLineArgs`、`systemProperties`、`systemEnvironment`、`SPRING_APPLICATION_JSON`
等真正运行时覆盖源，并避免 `application.yaml` 静态默认值误压 DB；但 config-service 自身的
`ConfigService.resolveEffectiveValue()` 和 MAIL SSL/STARTTLS 校验仍直接调用
`Environment.getProperty()`。在真实 Spring Environment 中，这可能把 classpath
`application.yaml` 默认值当成最高优先级注入源，违背“DB 高于初始化/静态默认值”的要求。

本轮范围：

- 在 config-service 内新增同等 `RuntimeInjectedConfig` guard，只允许命令行参数、system
  properties、system environment、`SPRING_APPLICATION_JSON` 和测试显式注入 source 作为
  `INJECTED`。
- `ConfigService` 的 summary `effectiveSource`、create/update/delete 事件来源、MAIL
  SSL/STARTTLS 冲突校验均改用 runtime source guard。
- 补测试证明：`APP_*` / property runtime source 仍高于 DB；classpath `application.yaml`
  静态默认值不再高于 DB；静态默认值也不会误触发 SSL/STARTTLS 冲突校验。

非目标：

- 不扩大默认 key catalog。
- 不改 identity/interaction 已有 guard。
- 不把更多 metadata/storage/notification 等消费者迁入动态配置客户端。

实现结果：

- config-service 新增 service-local `RuntimeInjectedConfig`，只把 `commandLineArgs`、
  `systemProperties`、`systemEnvironment`、`SPRING_APPLICATION_JSON`、`mockProperties`
  和显式测试 property source 识别为 `INJECTED`。
- `ConfigService.resolveEffectiveValue()` 已改为 `RuntimeInjectedConfig -> DB -> default seed`；
  classpath `application.yaml` 静态默认值不再压过 `system_configs` DB。
- `ConfigService.effectiveValue()` 的 MAIL SSL/STARTTLS 冲突校验也已改为同一 guard，避免静态
  YAML 默认值误触发“运行时注入”冲突。
- 新增测试覆盖 `classpathYamlDefaultDoesNotOverrideStoredConfig()` 和
  `classpathYamlDefaultDoesNotTriggerRuntimeSslStartTlsConflict()`；`ConfigServiceTest` 从 18
  个用例增加到 20 个用例。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL

ConfigServiceTest: tests=20, failures=0, errors=0
IdentityConfigServiceTest: tests=8, failures=0, errors=0
MemberTokenServiceTest: tests=7, failures=0, errors=0
FeedbackCommandServiceTest: tests=9, failures=0, errors=0
```

验收补充：

```text
MARK 4/7 config-service runtime injection source guard and YAML-default non-override: done 2026-06-09
```

2026-06-09：用户再次明确 V1 配置初始化可以保留，但优先级必须降到最低；ENV、K8S Secret、
ConfigMap、config tree、启动参数和其他真实运行时注入源必须始终最高优先级覆盖。本轮在既有
runtime source guard 基础上补强 K8S 外部注入 source name 识别，避免只支持
`systemEnvironment` 而漏掉 Spring Boot config tree 或 Spring Cloud Kubernetes 注入形态。

本轮范围：

- `services/ircs-config-service/src/main/java/com/prodigalgal/ircs/config/RuntimeInjectedConfig.java`
- `services/ircs-identity-service/src/main/java/com/prodigalgal/ircs/identity/RuntimeInjectedConfig.java`
- `services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/RuntimeInjectedConfig.java`
- 对应 config/identity/interaction 单元测试。

验收补充：

```text
MARK 4/7 K8S Secret/ConfigMap/configtree runtime injection remains above DB and default seed: done 2026-06-09
```

实现结果：

- config-service、identity-service 和 interaction-service 的 `RuntimeInjectedConfig` 已把
  Spring Boot config tree、Spring Cloud Kubernetes ConfigMap/Secret 等外部注入 property
  source 识别为 `INJECTED`。
- 仍然明确排除 classpath `application.yaml` 这类静态默认值，避免静态 fallback 压过 DB 管理值。
- 新增测试覆盖 `Config tree '/etc/secrets/ircs/'`、`Kubernetes ConfigMap`、
  `Kubernetes Secret` 三类 source name，证明它们高于 DB 和 V1 默认 seed。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL

ConfigServiceTest: tests=23, failures=0, errors=0
IdentityConfigServiceTest: tests=9, failures=0, errors=0
MemberTokenServiceTest: tests=8, failures=0, errors=0
FeedbackCommandServiceTest: tests=10, failures=0, errors=0
```

2026-06-09：再次按用户要求确认最终优先级策略：V1 配置初始化继续保留，但仅作为最低优先级
空库兜底 seed；ENV、K8S env/Secret/ConfigMap、config tree、启动参数、system properties 和
`SPRING_APPLICATION_JSON` 等真实运行时注入源始终高于 `system_configs` DB 与默认 seed。

本轮结论：

- 不需要移除 V1 风格 `SystemConfigInitializerRunner`。
- 不允许 initializer 把运行时注入值写回 `system_configs`。
- `JdbcConfigRepository.upsertDefault()` 的 conflict 分支只更新 description/version，不覆盖
  `config_value`。
- config-service 对外摘要、写入事件来源和 MAIL SSL/STARTTLS 冲突校验均按
  `RuntimeInjectedConfig -> DB -> default seed` 计算。
- 仍需在后续消费者迁移中逐项区分“动态刷新”与“启动绑定/重启生效”；本轮不扩大到
  metadata/storage/notification/normalization/aggregation 等更多服务。

最新验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL

ConfigServiceTest: tests=23, failures=0, errors=0
```

2026-06-09：按用户最新确认继续加固一条 config-service 回归护栏。既有实现已经把
Spring Boot config tree、Spring Cloud Kubernetes ConfigMap/Secret property source
识别为 `INJECTED`，本轮补上 config-service 自身对 `Kubernetes Secret ...` source name
的显式单元测试，确保 V1 默认 seed 与 `system_configs` DB 管理值不会压过 K8S Secret。

补充验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL

ConfigServiceTest: tests=23, failures=0, errors=0
```

2026-06-09：已完成 `028-16-C2-S4 SystemConfigKey catalog parity`。本轮把 V1
`SystemConfigKey` 全量 133 个 key 纳入 config-service `SystemConfigDefaults`，不再只覆盖
高风险子集。直接 catalog diff 结果为：

```text
V1=133 V3=133 Missing=0 Extra=0
```

实现结果：

- `SystemConfigDefaults` 补齐 batch min/max、metadata timeout/cron、rate-limit rules、
  mail rate limit、adaptive scheduling、maintenance/search/storage batch、LLM cleaning
  cron/min/max、traffic wait、provider proxy/delay、trend/search cron 等 V1 默认项。
- `security.admin.password` 与 `security.jwt.secret` 继续保留随机生成语义；
  R2 bucket/public-domain 被显式标记为 required。
- LLM prompt 默认值补回 raw-preservation 约束和 `{standardItems}` / `{rawItems}` 占位符。
- 既有 `RuntimeInjectedConfig -> DB -> default seed` 优先级、`upsertDefault()` 不覆盖
  `config_value` 的 seed 合约未改变。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL

SystemConfigDefaultsTest.includesFullV1SystemConfigKeyCatalog
SystemConfigDefaultsTest.exposesCoreAndRequiredKeyBoundaries
SystemConfigDefaultsTest.keepsV1ImportantDefaultValues
SystemConfigDefaultsTest.keepsV1LlmPromptRawPreservationContract
SystemConfigDefaultsTest.resolvesSensitiveDefaultsWithoutBlankValues
```

最新遗留：

- search-service cover URL 消费者已由 C2-S5 迁到 env-first/DB/default seed 并支持 event evict；
  interaction-service cover URL 消费者已由 C2-S6 复用既有 interaction config cache/event
  evict 路径补齐 R2/public-path 读取；portal-service cover URL 消费者已由 C2-S7 新增
  portal config cache/event listener 并补齐 R2/public-path 读取；storage-service media URL /
  avatar storage / avatar R2 sync / watchdog local prefix 已由 C2-S8 新增 storage config
  cache/event listener 并补齐 public-path/R2 domain/avatar-prefix 读取；normalization-worker
  retry/backoff/watchdog batch/pending timeout 已由 C2-S9 新增 normalization config
  cache/event listener 并补齐 V1 canonical key 读取；aggregation-worker scheduler batch size
  已由 C2-S10 新增 aggregation config cache/event listener 并补齐 V1 canonical key 读取；
  notification-worker MAIL enable/from/SMTP connect options 已由 C2-S11 新增 notification
  config cache/event listener 并补齐 V1 canonical key 读取；metadata-worker provider routing
  enable/pending TTL 已由 C2-S12 新增 metadata config cache/event listener 并补齐 V1 canonical
  key 读取；ops-service reindex dev limit 已由 C2-S13 新增 ops config cache/event
  listener 并迁到运行时注入/DB/fallback 读取；content 等更多消费者仍需继续迁到
  env-first 或明确标注为启动绑定/重启生效；ops full maintenance runner 仍需独立切片。
- config change event 尚未做 live Rabbit smoke；cache TTL/evict 全矩阵仍需独立任务闭环。

2026-06-09：已完成 `028-16-C2-S5 search cover URL config parity`。本轮把 search-service 的
cover URL 解析从单一 `@Value` 启动注入推进到 V1 风格 system config 消费者：

- R2 cover 按 `RuntimeInjectedConfig -> system_configs DB -> V1 default` 读取
  `app.storage.r2.public-domain`，并兼容 V3 alias `app.storage.r2-public-domain`。
- LOCAL/LOCAL_STORED 等托管 cover 按同样优先级读取 `app.storage.public-path`，恢复 V1
  `/media/...` 拼接语义。
- search-service 新增本地 config cache、`q.search.config_changed` 广播队列和按 key evict；
  `APP_SEARCH_CONFIG_LISTENER_ENABLED` 独立控制 config event listener。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：已完成 `028-16-C2-S6 interaction cover URL config parity`。本轮把
interaction-service 的收藏/历史 cover URL 解析从单一 `@Value` 启动注入推进到 V1 风格
system config 消费者：

- R2 cover 按 `RuntimeInjectedConfig -> system_configs DB -> V1 default` 读取
  `app.storage.r2.public-domain`，并兼容 V3 alias `app.storage.r2-public-domain`。
- LOCAL/LOCAL_STORED 等托管 cover 按同样优先级读取 `app.storage.public-path`，恢复 V1
  `/media/...` 拼接语义。
- interaction-service 复用已有本地 config cache、`q.interaction.config_changed` 广播队列和
  `APP_INTERACTION_CONFIG_LISTENER_ENABLED` 独立开关；config event evict 后后续 cover URL
  读取会重新查 DB fallback。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\interaction-service-dev.yaml
deployment.apps/ircs-interaction-service configured (server dry run)
service/ircs-interaction-service unchanged (server dry run)
```

2026-06-09：已完成 `028-16-C2-S7 portal cover URL config parity`。本轮把
portal-service 首页、分类、探索和详情卡片的 cover URL 解析从单一 `@Value` 启动注入
推进到 V1 风格 system config 消费者：

- R2 cover 按 `RuntimeInjectedConfig -> system_configs DB -> V1 default` 读取
  `app.storage.r2.public-domain`，并兼容 V3 alias `app.storage.r2-public-domain`。
- LOCAL/LOCAL_STORED 等托管 cover 按同样优先级读取 `app.storage.public-path`，恢复 V1
  `/media/...` 拼接语义。
- portal-service 新增本地 config cache、`q.portal.config_changed` 广播队列和
  `APP_PORTAL_CONFIG_LISTENER_ENABLED` 独立开关；dev K8S manifest 已注入 RabbitMQ 与
  `APP_STORAGE_R2_PUBLIC_DOMAIN`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-portal-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\portal-service-dev.yaml
deployment.apps/ircs-portal-service configured (server dry run)
service/ircs-portal-service unchanged (server dry run)
```

2026-06-09：已完成 `028-16-C2-S8 storage media URL config parity`。本轮把
storage-service 的 cover 管理 URL、头像上传响应、头像 R2 同步 URL 改写和 watchdog 本地
头像扫描前缀，从启动期 `@Value` 推进到 V1 风格 system config 消费者：

- R2 cover/avatar URL 按 `RuntimeInjectedConfig -> system_configs DB -> V1 default` 读取
  `app.storage.r2.public-domain`，并兼容 V3 alias `app.storage.r2-public-domain`。
- LOCAL/LOCAL_STORED cover、avatar upload URL 和 watchdog local avatar scan 按同样优先级读取
  `app.storage.public-path`，恢复 V1 `/media/...` 默认语义。
- avatar upload path prefix 按同样优先级读取 V1 `app.storage.path.prefix.avatar`，并兼容
  V3 alias `app.storage.avatar-path-prefix`。
- storage-service 新增本地 config cache、`q.storage.config_changed` 广播队列和
  `APP_STORAGE_CONFIG_LISTENER_ENABLED` 独立开关；dev K8S manifest 已显式开启。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\storage-service-dev.yaml
deployment.apps/ircs-storage-service configured (server dry run)
service/ircs-storage-service unchanged (server dry run)
```

2026-06-09：已完成 `028-16-C2-S9 normalization retry config parity`。本轮把
normalization-worker 的 retry/backoff/watchdog 查询参数从启动期 `@Value` 推进到 V1 风格
system config 消费者：

- `RawVideoNormalizationService` 按 `RuntimeInjectedConfig -> system_configs DB -> V1 default`
  读取 `normalization.max-retries`，V1 默认值恢复为 `5`，并兼容 V3 alias
  `app.normalization.max-retries` / `APP_NORMALIZATION_MAX_RETRIES`。
- `RawVideoNormalizationService` 按同样优先级读取
  `normalization.backoff.base-seconds`，V1 默认值为 `60`，并兼容 V3 alias
  `app.normalization.backoff-base-seconds` / `APP_NORMALIZATION_BACKOFF_BASE_SECONDS`。
- `NormalizationRetryWatchdog` 按同样优先级读取 V1 `normalization.batch-size` 和
  `normalization.timeout.pending-min`；dev manifest 用
  `APP_NORMALIZATION_WATCHDOG_BATCH_SIZE=25` 覆盖 V1 `500` 默认批次，保持低资源开发环境。
- normalization-worker 新增本地 config cache、`q.normalization.config_changed` 广播队列和
  `APP_NORMALIZATION_CONFIG_LISTENER_ENABLED` 独立开关。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\normalization-worker-dev.yaml
deployment.apps/ircs-normalization-worker configured (server dry run)
```

2026-06-09：已完成 `028-16-C2-S10 aggregation batch config parity`。本轮把
aggregation-worker scheduler 的 batch size 从启动期 `@Value` 推进到 V1 风格 system config
消费者：

- `AggregationScheduler` 每轮按 `RuntimeInjectedConfig -> system_configs DB -> V1 default`
  读取 `aggregation.batch-size`，V1 默认值恢复为 `200`，并兼容 V3 alias
  `app.aggregation.batch-size` / `APP_AGGREGATION_BATCH_SIZE`。
- aggregation-worker 新增本地 config cache、`q.aggregation.config_changed` 广播队列、DLQ
  绑定和 `APP_AGGREGATION_CONFIG_LISTENER_ENABLED` 独立开关。
- dev manifest 继续用 `APP_AGGREGATION_BATCH_SIZE=1` 覆盖 V1 默认 `200`，保持低资源开发环境。
- V1 adaptive min/max、readiness/maintenance gate 和 live Rabbit smoke 继续留给后续独立切片。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\aggregation-worker-dev.yaml
deployment.apps/ircs-aggregation-worker configured (server dry run)
```

2026-06-09：已完成 `028-16-C2-S11 notification mail config parity`。本轮把
notification-worker 的 MAIL enable/from 和 SMTP connect options 从启动期
`@ConfigurationProperties` 推进到 V1 风格 system config 消费者：

- `SmtpNotificationMailService` 每次发送前按
  `RuntimeInjectedConfig -> system_configs DB -> V1 default` 读取 `app.mail.enabled` 和
  `app.mail.from`，默认 from 恢复为 `HuaWai System <huawai.system@gmail.com>`，并兼容
  V3 alias `app.notification.mail.enabled` / `app.notification.mail.from`。
- `SmtpMailDelivery` 每次 SMTP 发送时按同样优先级读取 `app.mail.host`、`app.mail.port`、
  `app.mail.protocol`、`app.mail.properties.auth`、`app.mail.properties.starttls`、
  `app.mail.properties.ssl`、`app.mail.timeout` 和 `app.mail.debug`，credential
  username/password 继续优先来自 MAIL credential lease。
- notification-worker 新增本地 config cache、`q.notification.config_changed` 广播队列、DLQ
  绑定和 `APP_NOTIFICATION_CONFIG_LISTENER_ENABLED` 独立开关。
- dev manifest 继续用 `APP_MAIL_ENABLED=false` 覆盖 V1 默认 true，避免低资源开发环境误发
  真实邮件。
- V1 mail rate-limit、真实 SMTP live smoke 和 config change event live Rabbit smoke 继续留给
  后续独立切片。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\notification-worker-dev.yaml
configmap/ircs-dev-app-config unchanged (server dry run)
deployment.apps/ircs-notification-worker configured (server dry run)
```

2026-06-09：已完成 `028-16-C2-S12 metadata provider routing config parity`。本轮把
metadata-worker 的 provider fan-out enable 和 pending TTL 从启动期
`@ConfigurationProperties` 推进到 V1 风格 system config 消费者：

- `MetadataDispatchService` 每次 dispatch 前按
  `RuntimeInjectedConfig -> system_configs DB -> V1 default` 读取 `app.metadata.enabled`、
  `app.metadata.douban.enabled`、`app.metadata.tmdb.enabled`、
  `app.metadata.rotten-tomatoes.enabled` 和 `app.metadata.timeout.pending-min`。
- `MetadataProviderWorker` provider execute gate 使用同一个 `MetadataConfigValues`，避免
  dispatch 与 worker 对 provider enable 的判断分叉。
- metadata-worker 新增本地 config cache、`q.metadata.config_changed` 广播队列、DLQ 绑定和
  `APP_METADATA_CONFIG_LISTENER_ENABLED` 独立开关。
- dev manifest 继续用 `APP_METADATA_TMDB_ENABLED=false`、
  `APP_METADATA_DOUBAN_ENABLED=false`、`APP_METADATA_RT_ENABLED=false` 覆盖 V1 provider 默认
  true，避免低资源开发环境误触发真实 provider 外呼。
- TMDB HTTP 参数、provider live smoke、metadata scheduler PROCESSING reset/queue depth guard
  和 adaptive workload 继续留给后续独立切片。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\metadata-worker-dev.yaml
deployment.apps/ircs-metadata-worker configured (server dry run)
```

2026-06-09：已完成 `028-16-C2-S13 ops maintenance reindex config parity`。本轮把
ops-service 的 dev-safe `search-reindex-unified` runner limit 从启动期 `@Value` 推进到
system config 消费者：

- `MaintenanceReindexCommand` 每次运行前按
  `RuntimeInjectedConfig -> system_configs DB -> V3 dev-safe fallback` 读取
  `app.ops.maintenance.reindex.dev-limit`，并兼容 `OPS_MAINTENANCE_REINDEX_DEV_LIMIT` /
  `APP_OPS_MAINTENANCE_REINDEX_DEV_LIMIT`。
- 继续保留 `0` 禁用、`100` 上限和默认 `5`，避免 dev K8S 低资源环境被全量 reindex 冲击。
- ops-service 新增本地 config cache、`q.ops.config_changed` 广播队列、DLQ 绑定和
  `APP_OPS_CONFIG_LISTENER_ENABLED` 独立开关。
- 本轮只覆盖 V3 dev-safe runner limit，不声称完成 V1 full maintenance raw/unified reindex；
  V1 当前 raw/unified reindex 实际使用 `ADMIN_BATCH_OP` 静态批量，后续 full runner 需独立切片。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-ops-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\ops-service-dev.yaml
deployment.apps/ircs-ops-service configured (server dry run)
service/ircs-ops-service unchanged (server dry run)
```

2026-06-09：文档集成 worker 已统一纳入 `028-16-C2-S14 content scraper base URL config parity`。
本切片把 content-service `InternalContentClients` 的
`app.content.internal.scraper-base-url` 从构造期固定配置推进到调用期读取，优先级为
`RuntimeInjectedConfig -> system_configs DB -> V3 fallback`，并兼容既有
`APP_CONTENT_SCRAPER_BASE_URL` 与 canonical `APP_CONTENT_INTERNAL_SCRAPER_BASE_URL`。

本轮证据：

- content-service 新增本地 config cache、`q.content.config_changed`、DLQ 绑定和
  `APP_CONTENT_CONFIG_LISTENER_ENABLED` 独立开关。
- `InternalContentClients.refetchRawVideo()` 每次调用前读取当前 scraper base URL，config event
  evict 后可使用新的 DB fallback。
- resolver preset seed gate、service audit 开关和 content media URL fallback 未纳入本切片，
  继续按启动绑定/后续独立切片记录。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
42 tests, 0 failures

kubectl apply --dry-run=server -f deploy\k8s\dev\content-service-dev.yaml
deployment.apps/ircs-content-service configured (server dry run)
service/ircs-content-service unchanged (server dry run)
```

2026-06-09：已完成 `028-16-C2-S15 config event live matrix smoke harness`。本轮不新增业务
consumer，而是把 live Rabbit/cache evict 矩阵推进为一个可复用、默认 no-write 的验证入口，
并选 content-service 作为代表 consumer 补强回归护栏。

本轮证据：

- 新增 `scripts/smoke/config-event-live-matrix-02816c2-smoke.ps1`，默认 `-DryRun` 只输出
  search/interaction/portal/storage/normalization/aggregation/notification/metadata/ops/content
  的 config listener queue、开关、代表 key 和后续 live smoke 项，不写 DB、不 publish
  Rabbit、不 patch deployment。
- 脚本支持可选 `-CheckClusterReadOnly`，只读采集 Rabbit queue messages/consumers，作为后续
  live smoke 的预检入口。
- config-service `SystemConfigChangePublisherTest` 固定 payload shape 只包含
  `key/action/effectiveSource/sensitive/changedAt`，敏感 key payload 不含 `configValue`、
  `effectiveValue` 或示例 secret 文本。
- content-service `SystemConfigRepositoryTest` 增加代表回归：缓存旧值后收到
  `SystemConfigChangedEvent` 按 key evict，下一次读取拿到新的 DB fallback 值。
- 子任务已归档到 `tasks/done/028-16-c2-s15-live-matrix-smoke-harness.md`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL

powershell parser check
PARSER_OK

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-live-matrix-02816c2-smoke.ps1 -DryRun
MARK 1/4 config event matrix loaded without write operations
MARK 2/4 publisher payload shape is value-free by contract
MARK 3/4 no-write follow-up commands prepared
MARK 4/4 dry-run completed; no DB/Rabbit/deployment writes were attempted
status: SUCCESS
reason: DRY_RUN_MATRIX_ONLY
```

当前剩余边界：

- config change event 已有 no-write smoke helper 和 content 代表 evict/read-refresh 回归，但真正
  live Rabbit publish/consume 仍未执行。
- cache TTL/evict 全矩阵仍未闭环；当前是 event-driven evict + process-local cache，需要后续
  决定 Rabbit outage/missed-message 下是否增加短 TTL 兜底。
- ops full maintenance runner、metadata provider HTTP 参数、provider live smoke、aggregation adaptive
  min/max、MAIL rate-limit、notification MAIL credential lease 等仍需后续独立切片。

文档集成 worker 已把 C2-S15 作为已完成子切片纳入 `docs/index.md`、`tasks/index.md`、
`docs/reports/089-v1-golden-behavior-inventory.md` 和
`docs/migrations/089-v1-golden-behavior-inventory.md`；后续风险口径统一收敛为
live Rabbit 真 publish/consume 与 Rabbit outage TTL 兜底，不把该 no-write harness 误记为
完整 live smoke。

2026-06-09：启动 `028-16-C2-S19 config-service change publisher live rollout`。C2-S18 已确认
content-service listener live 端完成，当前阻塞点转移到 config-service live deployment / image：
需要将已存在于本地代码的 `SystemConfigChangePublisher` 和 Rabbit env 真实构建上线，再重跑
C2-S17。范围限定为 config-service、dev manifest、config/Rabbit smoke 与 docs/tasks 回写。

2026-06-09：`028-16-C2-S19` 已完成。config-service 已推送并 rollout 到
`ircs-dev`，live digest 为
`sha256:de6cc3f758ca62ff42bea22b422b2c570e488ac341da7380effdc9ce93472f05`；pod 内已确认
`SystemConfigChangePublisher.class`；`SPRING_RABBITMQ_USERNAME` 已从错误的 `ircs` 修正为
dev Rabbit 实际用户 `admin`。重跑 C2-S17 后，临时 update 与 restore delete 均观察到
Rabbit publish/deliver/ack；DB 临时值与 port-forward 无残留。C2-S17 当时仍因
content-service `APP_CONTENT_SCRAPER_BASE_URL` runtime override 无法直接观察 DB fallback read，
后续已由 C2-S20 的可恢复 probe 补齐。

2026-06-09 docs/tasks 收敛补充：

- C2-S18 已完成：content-service listener/topology live 端已声明 `q.content.config_changed`，
  Rabbit 中存在 consumer=1。
- C2-S19 已完成：config-service publisher 上线到 `ircs-dev`，Rabbit publish/deliver/ack
  真实跑通，config-service Rabbit username 已修正为 `admin`。
- C2-S17/C2-S20 已完成：Rabbit 链路通过，且 content-service read probe 在显式临时移除/恢复
  runtime override 后观察到 DB fallback 临时值。
- 旧状态：父任务当时继续进行中，剩余决策为 Rabbit outage / missed-message 下是否增加
  TTL fallback；该项已由 C2-S21 覆盖并关闭。

2026-06-09：`028-16-C2-S20` 已完成。C2-S17 smoke 新增显式
`-TemporarilyRemoveContentRuntimeOverride -ProbeContentRead -CreateTemporaryRawVideoFixture`
路径，在不改变运行时覆盖源优先级的前提下短暂移除并恢复
`APP_CONTENT_SCRAPER_BASE_URL=http://ircs-scraper-service.ircs-dev.svc.cluster.local:8080`。
最终 C2-S17 完成 `MARK 7/7`，content read probe 观察到临时 DB fallback URL；
`system_configs` 临时值、`C2-S17-PROBE-*` raw fixture、Rabbit messages 和 port-forward 均无残留。
详见 `docs/reports/095-02816c2s20-content-db-fallback-recoverable-probe.md`。

2026-06-09：`028-16-C2-S21` 已完成，C2 父任务关闭。最终决策为
`Rabbit event evict + short TTL read-through`：

- Rabbit `SystemConfigChangedEvent` 是正常链路快速失效路径。
- Rabbit outage / missed-message 下不做定时全量 refresh；消费者按需读取时由短 TTL 重新查 DB，
  防止进程内 DB fallback cache 无限期滞留旧值。
- content-service 代表消费者已补 TTL cache entry，默认 `30s`，可由
  `app.content.system-config.cache-ttl` / `APP_CONTENT_SYSTEM_CONFIG_CACHE_TTL` 覆盖。
- 读取优先级保持 `RuntimeInjectedConfig / ENV / K8S override -> DB fallback cache -> service default`，
  runtime override 命中时不读取 DB cache。
- 本轮未执行新的 live Rabbit outage smoke，也未停 RabbitMQ；C2-S17/S19/S20 已有真实
  Rabbit publish/deliver/ack 与 content DB fallback read probe，S21 使用 focused test 模拟
  missed-message 后 DB 已变更但未收到 event 的最终一致性。
- 清理证据：未 patch K8S deployment，未写 live DB，未发布 Rabbit 消息，未创建 port-forward。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
```

报告：

- `docs/reports/096-02816c2s21-system-config-missed-message-ttl-fallback.md`

```text
MARK 6/7 cache/event/TTL refresh strategy implemented or decided with evidence: done
MARK 7/7 docs/tasks indexes and 089 inventory updated: done
C2 parent: done
```
