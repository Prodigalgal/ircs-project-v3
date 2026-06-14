# 028-16-C2-S12 metadata provider routing config parity

状态：已完成

日期：2026-06-09

## 背景

`028-16-C2` 已把 V1 `SystemConfigService` 的默认 seed、运行时覆盖优先级、config change
event 和多服务消费者逐步迁入 V3。C2-S5 到 C2-S11 已覆盖 search、interaction、portal、
storage、normalization、aggregation 和 notification 的一批配置消费者。

本轮审计发现 metadata-worker 的 provider fan-out 配置仍主要通过
`application.yaml` / `@ConfigurationProperties` 启动期绑定：

- V1 `TmdbMetadataProvider.isEnabled()` 每次 enrich 前读取
  `SystemConfigKey.TMDB_ENABLED`，canonical key 为 `app.metadata.tmdb.enabled`，默认值
  `true`。
- V1 `MetadataScheduler` 每轮 watchdog 读取 `SystemConfigKey.METADATA_ENABLED` 和
  `SystemConfigKey.METADATA_PENDING_RESCUE_MIN`。
- V3 metadata-worker 当前 `MetadataDispatchService.providersFor()` 使用
  `MetadataProviderRoutingProperties.providers.*Enabled`，不会在 `system_configs` DB 更新或
  `SystemConfigChangedEvent` 后刷新。
- dev K8S 需要继续用 `APP_METADATA_TMDB_ENABLED=false`、`APP_METADATA_DOUBAN_ENABLED=false`
  和 `APP_METADATA_RT_ENABLED=false` 覆盖 V1 默认 true，避免开发环境误触发真实 provider。

## 目标

把 metadata-worker 的 provider fan-out enable 与 pending TTL 迁到 V1 风格
`Runtime injected -> system_configs DB -> V1 default seed` 读取链路，并接入 config change
event evict。

## 非目标

- 不执行真实 TMDB/Douban/RT live provider smoke。
- 不改变 `APP_METADATA_TMDB_WORKER_ENABLED` 的 worker bean 启动 gate；本轮只覆盖已启动
  worker 内部的 provider enable 判断。
- 不迁 TMDB HTTP base-url、image-base-url、language、include-adult、rate-limit、proxy 等
  provider HTTP 连接参数。
- 不实现 metadata scheduler 的 PROCESSING reset、queue depth guard 和 adaptive workload
  完整 V1 行为；这些继续留给后续独立切片。

## 上游来源

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- V1:
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/common/enums/SystemConfigKey.java`
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/scraper/metadata/impl/TmdbMetadataProvider.java`
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/ingestion/scheduler/MetadataScheduler.java`

## 输入

- V1 SystemConfig keys:
  - `app.metadata.enabled`
  - `app.metadata.tmdb.enabled`
  - `app.metadata.douban.enabled`
  - `app.metadata.rotten-tomatoes.enabled`
  - `app.metadata.timeout.pending-min`
- V3 aliases:
  - `APP_METADATA_ENABLED`
  - `APP_METADATA_TMDB_ENABLED`
  - `APP_METADATA_DOUBAN_ENABLED`
  - `APP_METADATA_RT_ENABLED`
  - `APP_METADATA_PENDING_TTL`
  - `app.metadata.providers.tmdb-enabled`
  - `app.metadata.providers.douban-enabled`
  - `app.metadata.providers.rotten-tomatoes-enabled`
  - `app.metadata.dispatcher.pending-ttl`

## 输出

- metadata-worker service-local config repository/cache。
- metadata-worker runtime injection source detector。
- metadata-worker provider routing config value resolver。
- `q.metadata.config_changed` event listener/topology。
- `MetadataDispatchService` provider fan-out 使用 env/DB/default 判断 provider enable 和 pending
  TTL。
- `MetadataProviderWorker` provider execute gate 使用同一 env/DB/default 判断 provider enable。
- dev K8S manifest 显式开启 config listener，并继续保留 provider false 的低资源覆盖。
- 单元测试、K8S server dry-run 和 docs/tasks 回写。

## 影响范围

- `services/ircs-metadata-worker/**`
- `deploy/k8s/dev/metadata-worker-dev.yaml`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/index.md`
- `docs/index.md`

## 依赖

- `shared:ircs-contracts` 中的 `SystemConfigChangedEvent`。
- Rabbit topology 常量 `shared:ircs-messaging`。
- metadata-worker 已有 JDBC 依赖和 PostgreSQL runtime。
- metadata-worker 已有 Rabbit/Redis 依赖，本轮不新增真实 broker 测试要求。

## 风险

- V1 provider enable 默认 true；dev 环境必须继续通过 runtime env false 覆盖，避免真实外呼。
- `APP_METADATA_TMDB_WORKER_ENABLED` 是 bean 启动 gate，和 V1 DB 动态开关不是同一层；本轮不能把
  bean gate 误改成动态 DB 控制。
- `APP_METADATA_PENDING_TTL` 是 V3 pending set TTL，V1 是 pending rescue minute；本轮只把
  dispatch pending TTL 作为 V3 alias 兼容，不声称完成 V1 watchdog 全语义。

## 验收标准

- MARK 1/8 V1 metadata provider SystemConfig facts documented。
- MARK 2/8 metadata-worker has dedicated config cache/event listener。
- MARK 3/8 dispatch provider fan-out reads runtime/DB/default provider enable flags。
- MARK 4/8 provider worker execute gate reads the same runtime/DB/default provider enable flags。
- MARK 5/8 pending registry TTL reads runtime/DB/default with V1/V3 aliases documented。
- MARK 6/8 runtime env overrides DB/default for dev no-real-provider safety。
- MARK 7/8 metadata-worker tests and K8S dry-run pass without real provider/Rabbit。
- MARK 8/8 docs/tasks indexes and 089 inventory updated。

## 测试边界

- 本轮运行：
  - `.\gradlew.bat --no-daemon :services:ircs-metadata-worker:test --rerun-tasks`
  - `kubectl apply --dry-run=server -f deploy\k8s\dev\metadata-worker-dev.yaml`
- 不运行真实 Rabbit live smoke。
- 不运行真实 TMDB/Douban/RT provider live smoke。

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`

## 关联任务

- 父任务：`tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- 上游父任务：`tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`

## 当前状态

已完成。

## 实现结果

- metadata-worker 新增 service-local `RuntimeInjectedConfig`、`SystemConfigRepository` 和
  `MetadataConfigValues`，provider fan-out 配置读取顺序固定为运行时注入 ->
  `system_configs` DB -> V1 default seed。
- `MetadataDispatchService` 改为每次 dispatch 时读取 `app.metadata.enabled`、
  `app.metadata.douban.enabled`、`app.metadata.tmdb.enabled`、
  `app.metadata.rotten-tomatoes.enabled` 和 pending TTL，再决定是否发布 provider task。
- `MetadataProviderWorker` 改为通过同一个 `MetadataConfigValues` 判断 provider 是否启用，
  避免 dispatch 与 worker gate 语义分叉。
- metadata-worker 新增 `q.metadata.config_changed`、DLQ 绑定和
  `APP_METADATA_CONFIG_LISTENER_ENABLED` 独立开关，用于按 key evict 本地
  `system_configs` cache。
- dev K8S manifest 显式开启 metadata config listener，同时保留
  `APP_METADATA_TMDB_ENABLED=false`、`APP_METADATA_DOUBAN_ENABLED=false` 和
  `APP_METADATA_RT_ENABLED=false`，确保 V1 默认 true 不会导致开发环境误触发真实 provider。

## 验证结果

```text
.\gradlew.bat --no-daemon :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\metadata-worker-dev.yaml
deployment.apps/ircs-metadata-worker configured (server dry run)
```

## MARK

- MARK 1/8 V1 metadata provider SystemConfig facts documented：done 2026-06-09。
- MARK 2/8 metadata-worker has dedicated config cache/event listener：done 2026-06-09。
- MARK 3/8 dispatch provider fan-out reads runtime/DB/default provider enable flags：done 2026-06-09。
- MARK 4/8 provider worker execute gate reads the same runtime/DB/default provider enable flags：done 2026-06-09。
- MARK 5/8 pending registry TTL reads runtime/DB/default with V1/V3 aliases documented：done 2026-06-09。
- MARK 6/8 runtime env overrides DB/default for dev no-real-provider safety：done 2026-06-09。
- MARK 7/8 metadata-worker tests and K8S dry-run pass without real provider/Rabbit：done 2026-06-09。
- MARK 8/8 docs/tasks indexes and 089 inventory updated：done 2026-06-09。

## 遗留问题

- 本轮未执行真实 TMDB/Douban/RT provider live smoke。
- `APP_METADATA_TMDB_WORKER_ENABLED` 仍是 bean 启动 gate，不属于 DB 动态开关；如果启动时关闭，
  DB 后续开启 provider 不会创建 worker bean。
- TMDB HTTP base-url、image-base-url、language、include-adult、rate-limit、proxy 等配置仍留给
  后续 C2/provider HTTP 参数切片。
- metadata scheduler 的 PROCESSING reset、queue depth guard 和 adaptive workload 完整语义仍未闭环。
