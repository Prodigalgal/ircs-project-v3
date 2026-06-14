# 028-16-C2-S8 storage media URL config parity task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `tasks/done/028-16-c2-s7-portal-cover-url-config-parity.md`
- V1 `ircs-backend/src/main/java/com/prodigalgal/ircs/modules/storage/support/StorageUrlHelper.java`
- V1 `ircs-backend/src/main/java/com/prodigalgal/ircs/modules/portal/service/impl/MemberProfileServiceImpl.java`
- V1 `ircs-backend/src/main/java/com/prodigalgal/ircs/modules/portal/consumer/AvatarSyncConsumer.java`
- V1 `ircs-backend/src/main/java/com/prodigalgal/ircs/modules/portal/scheduler/AvatarSyncScheduler.java`
- V3 `services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/**`

## 背景

V1 cover URL 解析、头像上传、头像 R2 同步和头像同步 scheduler 都通过 `SystemConfigService`
读取 `app.storage.public-path`、`app.storage.r2.public-domain`、`app.storage.path.prefix.avatar`
等 key。V3 storage-service 当前仍有多处 `@Value` 启动绑定：

- `CoverImageUrlResolver` 读取 `app.storage.public-path` 和 `app.storage.r2.public-domain`。
- `AvatarStorageService` 读取 `app.storage.avatar-path-prefix` 和 `app.storage.public-path`。
- `AvatarSyncService` 读取 `app.storage.public-path` 和 `app.storage.r2.public-domain`。
- `StorageR2Watchdog` 读取 `app.storage.public-path` 与 batch size。

这些路径会影响 storage 管理页、头像上传响应、头像 R2 同步 URL 改写和 watchdog 重新发布
本地头像同步命令。若只依赖启动期 `@Value`，则 DB 中由 config-service seed/admin 修改的
V1 key 不会按 V1 行为参与当前请求和 worker 副作用。

## 目标

- storage-service 新增本地 `SystemConfigRepository` cache、`q.storage.config_changed`
  广播队列和按 key evict。
- 新增 service-local runtime config reader，读取顺序固定为运行时注入 -> `system_configs` DB
  -> V1 default。
- `CoverImageUrlResolver` 使用上述读取顺序解析 R2 cover 和 LOCAL/LOCAL_STORED cover。
- `AvatarStorageService` 使用上述读取顺序读取 avatar path prefix 和 public path。
- `AvatarSyncService` 使用上述读取顺序读取 local public path 与 R2 public domain。
- `StorageR2Watchdog` 使用上述读取顺序读取 local public path；batch size 本轮保持启动绑定，
  后续由 adaptive/cache TTL 切片处理。
- dev K8S manifest 显式开启 `APP_STORAGE_CONFIG_LISTENER_ENABLED=true`，保持低资源和
  现有 no-public-route/ClusterIP 边界不变。
- 用本地测试覆盖 runtime override、DB fallback、default fallback、cache evict、listener/topology、
  avatar upload URL、avatar sync R2 URL 和 watchdog local prefix。

## 非目标

- 不迁移 R2 endpoint/account/access/secret 等敏感 object storage credential 读取。
- 不迁移 image download enabled/max-retries/timeout 等下载任务 knobs。
- 不改 cover 下载、R2 实际上传、image security/Tika 或 file normalization 语义。
- 不执行真实 RabbitMQ 或 R2 live smoke。
- 不改变 storage API route、队列 payload 或 DB schema。

## 影响范围

```text
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/**
services/ircs-storage-service/src/main/resources/application.yaml
services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/**
deploy/k8s/dev/storage-service-dev.yaml
tasks/in-progress/028-16-c2-system-config-cache-event-parity.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 验收标准

```text
MARK 1/8 V1 storage/avatar config usage facts documented: done 2026-06-09
MARK 2/8 storage-service has dedicated config cache/event listener: done 2026-06-09
MARK 3/8 CoverImageUrlResolver resolves R2/local cover URL from runtime/DB/default config: done 2026-06-09
MARK 4/8 AvatarStorageService uses runtime/DB/default avatar prefix and public path: done 2026-06-09
MARK 5/8 AvatarSyncService uses runtime/DB/default local public path and R2 public domain: done 2026-06-09
MARK 6/8 StorageR2Watchdog uses runtime/DB/default local public path: done 2026-06-09
MARK 7/8 storage-service tests and K8S dry-run pass without real Rabbit/R2: done 2026-06-09
MARK 8/8 docs/tasks indexes updated: done 2026-06-09
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --rerun-tasks
kubectl apply --dry-run=server -f deploy\k8s\dev\storage-service-dev.yaml
```

## 完成状态

2026-06-09：已完成实现与验证。写入范围限制在 storage-service 的 media URL config reader、
config cache/event listener、cover/admin URL 解析、头像上传、头像 R2 同步、watchdog 本地
头像扫描前缀、dev manifest 和对应测试；未迁移 R2 credential、image download knobs 或真实
R2/Rabbit live smoke。

实现结果：

- storage-service 新增 `RuntimeInjectedConfig`、`SystemConfigRepository`、`StorageConfigValues`、
  `SystemConfigChangedListener` 和 `StorageConfigEventTopology`。
- `CoverImageUrlResolver` 现在按运行时注入 -> `system_configs` DB -> V1 default 读取
  `app.storage.r2.public-domain` 和 `app.storage.public-path`。
- `AvatarStorageService` 现在按同样优先级读取 V1 key `app.storage.path.prefix.avatar`
  与 `app.storage.public-path`，兼容 V3 alias `app.storage.avatar-path-prefix`。
- `AvatarSyncService` 现在按同样优先级读取 local public path 与 R2 public domain，并保持
  V1 无 scheme 时自动补 `https://`。
- `StorageR2Watchdog` 现在按同样优先级读取 local public path，用于查找仍在本地 `/media`
  前缀下的头像；batch size 本轮仍保持启动绑定。
- dev K8S manifest 已显式开启 `APP_STORAGE_CONFIG_LISTENER_ENABLED=true`，资源配额不变。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\storage-service-dev.yaml
deployment.apps/ircs-storage-service configured (server dry run)
service/ircs-storage-service unchanged (server dry run)
```

遗留边界：

- 未执行 RabbitMQ live smoke。
- R2 endpoint/account/access/secret、image download enabled/max-retries/timeout、watchdog batch size
  等启动绑定配置仍需后续独立迁移或明确重启生效。
- content-service 当前未发现 cover URL helper；仍需继续扫描 content 的其他 SystemConfigKey
  消费点。
