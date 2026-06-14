# 028-16-C2-S14 content scraper base URL config parity task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md` 的 P0-02 SystemConfig 默认值与缓存
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

## 背景

`028-16-C2-S5` 到 `028-16-C2-S13` 已覆盖 search、interaction、portal、storage、
normalization、aggregation、notification、metadata 和 ops 的一批 SystemConfig 消费者。
`089` 仍记录 content-service 等剩余启动绑定或读取配置需要继续迁到 env-first/DB/default
优先级，或明确标注为启动绑定、重启生效。

本轮扫描 content-service：

- `@Value("${app.content.internal.scraper-base-url:...}")`：`InternalContentClients`
  refetch raw video 时调用 scraper-service 的内部 base URL，属于运行期出站目标配置。
- `@Value("${app.content.resolver-preset-seed.enabled:true}")`：`DefaultResolverPresetSeeder`
  只在启动 seed 阶段执行，继续按启动绑定/重启生效处理。
- `@Value("${app.service.audit.enabled:true}")` 和
  `@Value("${app.service.audit.source:...}")`：service audit filter 配置，非本轮 SystemConfig
  运行期消费者，继续按启动绑定/重启生效处理。
- 未发现 content-service 直接读取 `system_configs`、`SystemConfigKey` 或已有本地 config cache。

## 目标

- 选择 `InternalContentClients` 的 `app.content.internal.scraper-base-url` 作为本轮 content
  consumer 切片。
- 将 scraper base URL 读取顺序固定为运行时注入 -> `system_configs` DB -> V3 internal
  service fallback。
- 支持 dev manifest 既有 `APP_CONTENT_SCRAPER_BASE_URL`，同时支持 canonical
  `APP_CONTENT_INTERNAL_SCRAPER_BASE_URL`。
- 为 content-service 增加本地 config cache、`q.content.config_changed` 广播消费队列和
  `APP_CONTENT_CONFIG_LISTENER_ENABLED` 独立开关。
- 添加 focused tests，覆盖优先级、cache evict、Rabbit topology 和动态 refetch base URL。

## 非目标

- 不修改全局索引、089 汇总或迁移总账。
- 不改变 task-service/notification readiness、shared outbound C3、ops-service 等其他 worker
  模块。
- 不重构全 content-service，也不把 resolver preset seed、audit 开关和 cover URL 行内 domain
  一次性纳入动态配置。
- 不新增 V1 `SystemConfigDefaults` key；`app.content.internal.scraper-base-url` 当前是 V3
  微服务内部出站目标配置，本轮使用 V3 fallback 而不是伪造 V1 默认。

## 输入

- `services/ircs-content-service/**`
- `deploy/k8s/dev/content-service-dev.yaml`
- config-service 已发布的 `SystemConfigChangedEvent` contract：
  `x.domain.events` / `config.system.changed`

## 输出

- content-service 本地 config cache 与 config event listener/topology。
- `InternalContentClients` 按当前有效配置发起 refetch 请求。
- dev K8S manifest 独立开启 content config listener。
- 本 task spec 回写实现、验证、遗留配置列表。

## 影响范围

包含：

- `services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/**`
- `services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/**`
- `services/ircs-content-service/src/main/resources/application.yaml`
- `deploy/k8s/dev/content-service-dev.yaml`
- 本 task spec

不包含：

- 其他服务模块。
- `docs/index.md`、`tasks/index.md`、`docs/reports/089-v1-golden-behavior-inventory.md`。

## 依赖

- `shared:ircs-contracts` 的 `SystemConfigChangedEvent`。
- `shared:ircs-messaging` 的 `QueueTopic.Names.DOMAIN_EVENT_X` 与 DLX 名称。
- content-service 已有 PostgreSQL `system_configs` 表访问能力。

## 风险

- `app.content.internal.scraper-base-url` 不属于 V1 133 个默认 key；因此 DB 中只有管理员显式配置时才生效。
- refetch 每次构建 RestClient 会多一次轻量 builder 构造，但换来事件 evict 后立即使用新 base URL。
- dev manifest 既有 `APP_CONTENT_SCRAPER_BASE_URL` 不是 canonical env name；本轮必须作为 alias 保留。

## 验收标准

- content-service 扫描结论写入本 task spec。
- scraper base URL 优先级为 RuntimeInjectedConfig -> `system_configs` DB -> V3 fallback。
- config event queue 使用 `q.content.config_changed`，开关为 `APP_CONTENT_CONFIG_LISTENER_ENABLED`。
- focused tests 覆盖 DB cache、event evict、topology、runtime alias 和 refetch base URL 变化。
- `.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks` 通过。
- 如修改 manifest，执行
  `kubectl apply --dry-run=server -f deploy\k8s\dev\content-service-dev.yaml`。

## 测试边界

自动测试：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
```

K8S dry-run：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\content-service-dev.yaml
```

手工检查点：

- `rg -n "@Value|@ConfigurationProperties|system_configs|SystemConfigKey|RuntimeInjectedConfig|config_changed" services/ircs-content-service`
- `rg -n "APP_CONTENT_CONFIG_LISTENER_ENABLED|q.content.config_changed" services/ircs-content-service deploy/k8s/dev/content-service-dev.yaml`

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md`

## 关联任务

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/in-progress/028-16-c2-s14-content-scraper-base-url-config-parity.md`

## 当前状态

2026-06-09：已完成 `028-16-C2-S14 content scraper base URL config parity`。

实现结果：

- 选中 consumer：`InternalContentClients` 的 `app.content.internal.scraper-base-url`。
- `InternalContentClients.refetchRawVideo()` 已改为每次调用前读取当前 scraper base URL，不再在构造期固定 URL。
- 新增 `ContentConfigValues`，按 RuntimeInjectedConfig -> `system_configs` DB -> V3 fallback 读取：
  - canonical key：`app.content.internal.scraper-base-url`
  - runtime alias：`app.content.scraper-base-url`
  - dev env alias：`APP_CONTENT_SCRAPER_BASE_URL`
  - canonical env：`APP_CONTENT_INTERNAL_SCRAPER_BASE_URL`
- 新增 content-service 本地 `SystemConfigRepository` cache、`SystemConfigChangedListener` 和
  `ContentConfigEventTopology`。
- config event queue 使用 `q.content.config_changed`，绑定
  `x.domain.events` / `config.system.changed`，并配置 DLQ。
- listener 独立开关为 `app.content.config-listener.enabled` /
  `APP_CONTENT_CONFIG_LISTENER_ENABLED`，dev manifest 已显式开启。
- `services/ircs-content-service/src/main/resources/application.yaml` 保留既有
  `APP_CONTENT_SCRAPER_BASE_URL` 兼容，同时新增 canonical
  `APP_CONTENT_INTERNAL_SCRAPER_BASE_URL`。

验证结果：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL

content-service test suites: 17
tests: 42, failures: 0, errors: 0, skipped: 0
```

新增 focused tests：

- `ContentConfigValuesTest`：V3 fallback、DB canonical key、canonical env 和 dev alias 优先级。
- `SystemConfigRepositoryTest`：DB config 本地 cache 与 evict。
- `SystemConfigChangedListenerTest`：按 key evict、坏 payload best-effort、独立 autoStartup 开关。
- `ContentConfigEventTopologyTest`：`q.content.config_changed` 绑定 domain event routing key。
- `InternalContentClientsTest`：连续 refetch 可使用不同的当前 scraper base URL。

K8S dry-run：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\content-service-dev.yaml
deployment.apps/ircs-content-service configured (server dry run)
service/ircs-content-service unchanged (server dry run)
```

扫描复核：

```text
services/ircs-content-service/.../ContentConfigValues.java
  app.content.internal.scraper-base-url -> RuntimeInjectedConfig -> DB -> V3 fallback
services/ircs-content-service/.../SystemConfigChangedListener.java
  q.content.config_changed
deploy/k8s/dev/content-service-dev.yaml
  APP_CONTENT_CONFIG_LISTENER_ENABLED=true
```

未覆盖的 content config 列表：

- `ContentServiceAuditConfiguration` 的 `app.service.audit.enabled` 与 `app.service.audit.source`：
  service audit 启动绑定，本轮未迁移，继续标注为重启生效。
- `DefaultResolverPresetSeeder` 的 `app.content.resolver-preset-seed.enabled`：
  启动 seed gate，仅启动时执行，本轮未迁移，继续标注为重启生效。
- `JdbcVideoAdminRepository.resolveCoverUrl()` 的 cover URL 拼接：
  当前读取行内 `source_domains.domain_value` / `cover_storage_path`，未读取全局 SystemConfig；
  如后续要恢复 `/media` 或 R2 public-domain 全局 fallback，需要独立 content media URL 切片。
- `APP_CONTENT_LISTENER_ENABLED`：
  业务队列 listener 开关，不属于 config event listener；本轮只新增独立
  `APP_CONTENT_CONFIG_LISTENER_ENABLED`，不改变业务队列 owner 行为。
