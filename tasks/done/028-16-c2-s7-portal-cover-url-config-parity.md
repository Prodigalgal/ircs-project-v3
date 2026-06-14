# 028-16-C2-S7 portal cover URL config parity task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `tasks/done/028-16-c2-s5-search-cover-url-config-parity.md`
- `tasks/done/028-16-c2-s6-interaction-cover-url-config-parity.md`
- V1 `ircs-backend/src/main/java/com/prodigalgal/ircs/modules/storage/support/StorageUrlHelper.java`
- V1 `ircs-backend/src/main/java/com/prodigalgal/ircs/modules/portal/model/assembler/PortalAssembler.java`
- V3 `services/ircs-portal-service/src/main/java/com/prodigalgal/ircs/portal/PortalCoverImageUrlResolver.java`

## 背景

V1 portal 首页、分类、探索和详情卡片的 `coverImageUrl` 通过 `PortalAssembler` 调用
`StorageUrlHelper.resolveUrl()` 生成。该 helper 每次解析托管封面时读取
`SystemConfigKey.R2_PUBLIC_DOMAIN` 和 `SystemConfigKey.STORAGE_PUBLIC_PATH`，因此 R2
公开域名、本地 `/media` 前缀和配置变更失效都属于门户可见的 V1 行为。

本任务开始前，V3 portal-service `PortalCoverImageUrlResolver` 只通过
`@Value("${app.storage.r2-public-domain:}")` 读取 R2 域名，LOCAL/LOCAL_STORED 等非 R2
托管图直接返回 `storagePath`。同时 dev K8S manifest 未注入 `APP_STORAGE_R2_PUBLIC_DOMAIN`，
portal-service 也没有 config change event listener。该差异会直接影响 portal-bff/门户入口
持续验证封面 URL 是否与 V1 一致。

## 目标

- portal-service 的 cover URL 解析读取顺序固定为运行时注入 -> `system_configs` DB
  -> V1 default。
- R2 cover 使用 V1 key `app.storage.r2.public-domain`，并兼容现有 V3 alias
  `app.storage.r2-public-domain` / `APP_STORAGE_R2_PUBLIC_DOMAIN`。
- LOCAL/LOCAL_STORED 等非 EXTERNAL 托管 cover 使用 `app.storage.public-path`，默认 `/media`。
- portal-service 新增本地 `SystemConfigRepository` cache、`q.portal.config_changed`
  广播队列和按 key evict。
- dev K8S manifest 显式注入 RabbitMQ 连接、`APP_STORAGE_R2_PUBLIC_DOMAIN` 和
  `APP_PORTAL_CONFIG_LISTENER_ENABLED=true`，保持 no-public-route 不变。
- 用本地单元测试覆盖 runtime override、DB fallback、default fallback、local public path、
  event evict/topology 和 V1 sourceDomain join 行为。

## 非目标

- 不改 portal query SQL、排序、分页、详情数据聚合或鉴权边界。
- 不把 content-service、storage-service 的 cover/avatar URL helper 混入本切片。
- 不执行 RabbitMQ live smoke。
- 不新增公开 HTTPRoute，不改变 portal-bff 到 portal-service 的服务名。

## 影响范围

```text
services/ircs-portal-service/build.gradle
services/ircs-portal-service/src/main/java/com/prodigalgal/ircs/portal/**
services/ircs-portal-service/src/main/resources/application.yaml
services/ircs-portal-service/src/test/java/com/prodigalgal/ircs/portal/**
deploy/k8s/dev/portal-service-dev.yaml
tasks/in-progress/028-16-c2-system-config-cache-event-parity.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 验收标准

```text
MARK 1/7 V1 portal StorageUrlHelper usage facts documented: done 2026-06-09
MARK 2/7 portal-service resolves R2 cover URL from runtime/DB/default config: done 2026-06-09
MARK 3/7 portal-service resolves local managed cover URL with app.storage.public-path: done 2026-06-09
MARK 4/7 portal-service config cache evicts on SystemConfigChangedEvent: done 2026-06-09
MARK 5/7 portal-service declares dedicated config event topology and K8S listener env: done 2026-06-09
MARK 6/7 portal-service tests and K8S dry-run pass without real Rabbit: done 2026-06-09
MARK 7/7 docs/tasks indexes updated: done 2026-06-09
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-portal-service:test --rerun-tasks
kubectl apply --dry-run=server -f deploy\k8s\dev\portal-service-dev.yaml
```

## 完成状态

2026-06-09：已完成实现与验证。写入范围限制在 portal-service 的 cover URL config reader、
本地 config cache/event listener、dev manifest 和对应测试；未改变 portal query SQL、BFF
服务名或 HTTPRoute。

实现结果：

- `PortalCoverImageUrlResolver` 现在按运行时注入 -> `system_configs` DB -> V1 default 读取
  `app.storage.r2.public-domain` 和 `app.storage.public-path`。
- R2 cover 兼容既有 V3 alias `app.storage.r2-public-domain` /
  `APP_STORAGE_R2_PUBLIC_DOMAIN`，并保持 V1 无 scheme 时自动补 `https://` 的行为。
- LOCAL/LOCAL_STORED 等托管 cover 会按 V1 `app.storage.public-path` 拼接 `/media/...`，
  portal 首页、分类、探索和详情卡片不再直接返回裸 `storagePath`。
- portal-service 新增本地 `SystemConfigRepository` cache、`q.portal.config_changed`
  广播队列、DLQ 绑定和 `SystemConfigChangedListener` 按 key evict。
- dev K8S manifest 已注入 RabbitMQ 连接、`APP_STORAGE_R2_PUBLIC_DOMAIN` 和
  `APP_PORTAL_CONFIG_LISTENER_ENABLED=true`；`no-public-route` annotation 保持不变。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-portal-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\portal-service-dev.yaml
deployment.apps/ircs-portal-service configured (server dry run)
service/ircs-portal-service unchanged (server dry run)
```

遗留边界：

- 未执行 RabbitMQ live smoke。
- content-service、storage-service 的 cover/avatar URL helper 仍需后续逐项迁移。
