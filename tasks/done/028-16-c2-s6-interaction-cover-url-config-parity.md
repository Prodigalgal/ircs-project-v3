# 028-16-C2-S6 interaction cover URL config parity task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `tasks/done/028-16-c2-s5-search-cover-url-config-parity.md`
- V1 `ircs-backend/src/main/java/com/prodigalgal/ircs/modules/storage/support/StorageUrlHelper.java`
- V1 `ircs-backend/src/main/java/com/prodigalgal/ircs/modules/interaction/service/impl/MemberFavoriteServiceImpl.java`
- V3 `services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/CoverImageUrlResolver.java`

## 背景

V1 interaction 收藏和观看历史响应里的 `coverImageUrl` 通过 `StorageUrlHelper.resolveUrl()`
生成。该 helper 每次解析托管封面时读取 `SystemConfigKey.R2_PUBLIC_DOMAIN`
和 `SystemConfigKey.STORAGE_PUBLIC_PATH`，因此 R2 域名、本地 `/media` 前缀和配置变更失效
都属于 V1 已验证行为的一部分。

本任务开始前，V3 interaction-service `CoverImageUrlResolver` 只通过
`@Value("${app.storage.r2-public-domain:}")` 读取 R2 域名，LOCAL/LOCAL_STORED 等非 R2
托管图直接返回 `storagePath`。这会导致 portal 的收藏/历史页面在 V3 中无法复刻 V1
`/media/...` 前缀语义，也无法让 DB 中的 `app.storage.r2.public-domain` 通过 config
change event 驱动本地 cache 失效。

## 目标

- interaction-service 的 cover URL 解析读取顺序固定为运行时注入 -> `system_configs` DB
  -> V1 default。
- R2 cover 使用 V1 key `app.storage.r2.public-domain`，并兼容现有 V3 alias
  `app.storage.r2-public-domain` / `APP_STORAGE_R2_PUBLIC_DOMAIN`。
- LOCAL/LOCAL_STORED 等非 EXTERNAL 托管 cover 使用 `app.storage.public-path`，默认 `/media`。
- 复用 interaction-service 已有 `SystemConfigRepository` cache 和
  `SystemConfigChangedListener`，让配置变更事件能 evict 影响后续 cover URL 解析。
- 用本地单元测试覆盖 runtime override、DB fallback、default fallback、local public path
  和 V1 sourceDomain join 行为。

## 非目标

- 不改 portal-service、content-service、storage-service 的 URL helper。
- 不执行 RabbitMQ live smoke。
- 不改变 interaction 收藏、历史记录 SQL、分页、鉴权或写入副作用。
- 不改变 dev K8S 业务队列开关语义。

## 影响范围

```text
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/CoverImageUrlResolver.java
services/ircs-interaction-service/src/test/java/com/prodigalgal/ircs/interaction/CoverImageUrlResolverTest.java
tasks/in-progress/028-16-c2-system-config-cache-event-parity.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 验收标准

```text
MARK 1/6 V1 interaction StorageUrlHelper usage facts documented: done 2026-06-09
MARK 2/6 interaction-service resolves R2 cover URL from runtime/DB/default config: done 2026-06-09
MARK 3/6 interaction-service resolves local managed cover URL with app.storage.public-path: done 2026-06-09
MARK 4/6 existing interaction config cache/event evict path covers cover URL keys: done 2026-06-09
MARK 5/6 interaction-service tests pass without real Rabbit: done 2026-06-09
MARK 6/6 docs/tasks indexes updated: done 2026-06-09
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-interaction-service:test --rerun-tasks
kubectl apply --dry-run=server -f deploy\k8s\dev\interaction-service-dev.yaml
```

## 完成状态

2026-06-09：已完成实现与验证。写入范围限制在 interaction-service 的 cover URL
config reader 和本地测试，复用既有 `SystemConfigRepository` cache 与
`q.interaction.config_changed` event listener，未扩大到 portal/content/storage helper。

实现结果：

- `CoverImageUrlResolver` 现在按运行时注入 -> `system_configs` DB -> V1 default 读取
  `app.storage.r2.public-domain` 和 `app.storage.public-path`。
- R2 cover 兼容既有 V3 alias `app.storage.r2-public-domain` /
  `APP_STORAGE_R2_PUBLIC_DOMAIN`，并保持 V1 无 scheme 时自动补 `https://` 的行为。
- LOCAL/LOCAL_STORED 等托管 cover 会按 V1 `app.storage.public-path` 拼接 `/media/...`，
  portal 收藏/历史响应不再直接返回裸 `storagePath`。
- interaction-service 已有本地 config cache、`q.interaction.config_changed` 广播队列和
  `APP_INTERACTION_CONFIG_LISTENER_ENABLED` 开关；本轮 cover URL 读取复用该 evict 路径。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\interaction-service-dev.yaml
deployment.apps/ircs-interaction-service configured (server dry run)
service/ircs-interaction-service unchanged (server dry run)
```

遗留边界：

- 未执行 RabbitMQ live smoke。
- portal-service、content-service、storage-service 的 cover/avatar URL helper 仍需后续逐项迁移。
