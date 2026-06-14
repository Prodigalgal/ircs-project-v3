# 028-16-C2-S5 search cover URL config parity task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `tasks/done/028-16-c2-s4-system-config-key-catalog-parity.md`
- V1 `ircs-backend/src/main/java/com/prodigalgal/ircs/modules/storage/support/StorageUrlHelper.java`
- V3 `services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/support/CoverImageUrlResolver.java`

## 背景

V1 `StorageUrlHelper.resolveManagedUrl()` 每次解析 cover URL 时通过 `SystemConfigService`
读取 `app.storage.r2.public-domain` 和 `app.storage.public-path`。因此 V1 中配置变更后，
search sync、portal card、content assembler 等路径会使用统一的系统配置语义。

本任务开始前，V3 search-service `CoverImageUrlResolver` 只通过 `@Value("${app.storage.r2-public-domain:}")`
读取 R2 域名，且非 R2 托管图直接返回 `storagePath`。这会造成两个差异：

- DB 中由 config-service seed 或 admin 修改的 `app.storage.r2.public-domain` 不能参与搜索文档 URL。
- 本地托管 cover 没有按 V1 `app.storage.public-path` 补 `/media/...` 前缀。

## 目标

- 在 search-service 内为 cover URL 解析补最小 system config reader。
- `CoverImageUrlResolver` 读取顺序固定为运行时注入 -> `system_configs` DB -> V1 default。
- R2 cover 使用 `app.storage.r2.public-domain`，兼容现有 V3 alias `app.storage.r2-public-domain`。
- LOCAL/LOCAL_STORED 等非 EXTERNAL 托管 cover 使用 `app.storage.public-path`。
- search-service 本地 cache 能通过 `SystemConfigChangedEvent` 按 key evict。
- 用本地单元测试覆盖 env-first、DB fallback、default fallback、local public path 和 event evict。

## 非目标

- 不把 storage-service、portal-service 或 content-service 一次性改成动态 config client。
- 不执行 RabbitMQ live smoke。
- 不改变 search sync 消息契约、ES mapping 或 cover image DB schema。
- 不改变现有 `APP_SEARCH_LISTENER_ENABLED` 业务队列开关语义。

## 影响范围

```text
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/support/**
services/ircs-search-service/src/main/resources/application.yaml
deploy/k8s/dev/search-service-dev.yaml
services/ircs-search-service/src/test/java/com/prodigalgal/ircs/search/support/**
tasks/in-progress/028-16-c2-system-config-cache-event-parity.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 验收标准

```text
MARK 1/6 V1 StorageUrlHelper config facts documented: done 2026-06-09
MARK 2/6 search-service resolves R2 cover URL from runtime/DB/default config: done 2026-06-09
MARK 3/6 search-service resolves local managed cover URL with app.storage.public-path: done 2026-06-09
MARK 4/6 search-service config cache evicts on SystemConfigChangedEvent: done 2026-06-09
MARK 5/6 search-service tests pass without real Rabbit/ES: done 2026-06-09
MARK 6/6 docs/tasks indexes updated: done 2026-06-09
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-search-service:test --rerun-tasks
```

## 完成状态

2026-06-09：已完成实现与验证。写入范围限制在 search-service 的 cover URL config reader、
cache/event topology 和本地测试；未扩大到其他消费者。

实现结果：

- `CoverImageUrlResolver` 现在按运行时注入 -> `system_configs` DB -> V1 default 读取
  `app.storage.r2.public-domain` 和 `app.storage.public-path`。
- R2 cover 兼容既有 V3 alias `app.storage.r2-public-domain` / `APP_STORAGE_R2_PUBLIC_DOMAIN`，
  并保持 V1 无 scheme 时自动补 `https://` 的行为。
- LOCAL/LOCAL_STORED 等托管 cover 会按 V1 `app.storage.public-path` 拼接 `/media/...`，
  不再直接把 `storagePath` 写入 search document。
- search-service 新增本地 `SystemConfigRepository` cache、`q.search.config_changed`
  独立广播队列和 `SystemConfigChangedListener` 按 key evict。
- `APP_SEARCH_CONFIG_LISTENER_ENABLED` 独立控制 config event listener，避免与
  `APP_SEARCH_LISTENER_ENABLED` 业务搜索同步队列开关混用。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

遗留边界：

- 未执行 RabbitMQ live smoke。
- storage-service、portal-service、content-service 的 cover/avatar URL helper 仍需后续逐项迁移。
