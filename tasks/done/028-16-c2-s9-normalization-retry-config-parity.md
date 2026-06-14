# 028-16-C2-S9 normalization retry config parity

状态：已完成

日期：2026-06-09

## 背景

`028-16-C2` 正在把 V1 `SystemConfigService` 的运行时配置读取、缓存和变更失效语义迁到
V3 各微服务。前序 C2-S5/C2-S6/C2-S7/C2-S8 已覆盖 search、interaction、portal 和
storage 的媒体 URL 配置消费者。

本轮审计发现 normalization-worker 仍有一组明确偏差：

- V1 `RawVideoNormalizationServiceImpl.handleFailureAsync()` 通过
  `SystemConfigService.getInt(SystemConfigKey.NORMALIZATION_MAX_RETRIES)` 读取
  `normalization.max-retries`，默认值为 `5`。
- V1 同方法通过 `SystemConfigKey.NORMALIZATION_BACKOFF_BASE_SECONDS` 读取
  `normalization.backoff.base-seconds`，默认值为 `60`。
- V1 `NormalizationScheduler.runWatchdog()` 通过 `SystemConfigService`/`AdaptiveWorkloadManager`
  获取 retry/watchdog 批次和 PENDING rescue timeout。
- V3 `RawVideoNormalizationService` 当前使用 `@Value("${app.normalization.max-retries:3}")`，
  key 与默认值均不等价，并且只在启动时绑定。
- V3 `NormalizationRetryWatchdog` 当前使用 `@Value` 读取 watchdog batch/stale pending seconds，
  不会响应 config-service 的 system config DB 更新和 `SystemConfigChangedEvent`。

## 目标

把 normalization-worker 的 retry/backoff/watchdog 查询参数迁到 V1 风格的
`Runtime injected -> system_configs DB -> V1 default seed` 读取链路，并接入 config change
event evict。

## 非目标

- 不在本轮补完整 V1 `NormalizationScheduler` 的启动时 PROCESSING reset、Rabbit queue depth guard、
  adaptive workload backoff 和 PROCESSING timeout；这些继续归属 C1/C/A/B 后续切片。
- 不迁 `@Scheduled` 的 initial/fixed delay，调度周期仍作为启动期/部署生命周期参数。
- 不迁 `app.normalization.hanlp-prewarm.enabled`，HanLP 预热开关仍作为启动期行为。
- 不做 live Rabbit smoke；本轮先用 listener/topology 单测和 K8S server dry-run 固定契约。

## 上游来源

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- V1:
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/common/enums/SystemConfigKey.java`
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/ingestion/service/impl/RawVideoNormalizationServiceImpl.java`
  - `D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/com/prodigalgal/ircs/modules/ingestion/scheduler/NormalizationScheduler.java`

## 输入

- V1 SystemConfig keys:
  - `normalization.max-retries`
  - `normalization.backoff.base-seconds`
  - `normalization.batch-size`
  - `normalization.timeout.pending-min`
- V3 aliases:
  - `app.normalization.max-retries`
  - `app.normalization.backoff-base-seconds`
  - `app.normalization.watchdog.batch-size`
  - `app.normalization.watchdog.stale-pending-seconds`

## 输出

- normalization-worker service-local config repository/cache。
- normalization-worker runtime injection source detector。
- normalization retry/watchdog config value resolver。
- `q.normalization.config_changed` event listener/topology。
- 更新 normalization-worker application.yaml 和 dev K8S manifest。
- 单元测试、K8S server dry-run 和 docs/tasks 回写。

## 影响范围

- `services/ircs-normalization-worker/**`
- `deploy/k8s/dev/normalization-worker-dev.yaml`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/index.md`
- `docs/index.md`

## 依赖

- `shared:ircs-contracts` 中的 `SystemConfigChangedEvent`。
- Rabbit topology 常量 `shared:ircs-contracts` / `shared:ircs-messaging`。
- normalization-worker 已使用 JDBC，可直接读取 `system_configs`。

## 风险

- V1 默认 `normalization.batch-size=500` 高于 dev 期资源目标；dev K8S 必须通过 runtime env
  覆盖为低批次，避免扩大资源占用。
- 只做本地 cache event evict 不能证明 live Rabbit broker 路径已通，后续仍需 C2 live smoke。
- 如果 DB 中有旧 V3 alias key，本轮 canonical DB key 优先 V1 key，alias 只用于运行时注入覆盖。

## 验收标准

- MARK 1/7 V1 normalization retry/watchdog SystemConfig facts documented：done 2026-06-09。
- MARK 2/7 normalization-worker has dedicated config cache/event listener：done 2026-06-09。
- MARK 3/7 `RawVideoNormalizationService` 使用 env/DB/default 读取 max retries，V1 默认为 5：done 2026-06-09。
- MARK 4/7 `RawVideoNormalizationService` 使用 env/DB/default 读取 backoff base seconds，V1 默认为 60：done 2026-06-09。
- MARK 5/7 `NormalizationRetryWatchdog` 使用 env/DB/default 读取 batch size 和 stale pending threshold：done 2026-06-09。
- MARK 6/7 normalization-worker tests and K8S dry-run pass without real Rabbit：done 2026-06-09。
- MARK 7/7 docs/tasks indexes and 089 inventory updated：done 2026-06-09。

## 测试边界

- 本轮运行：
  - `.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks`
  - `kubectl apply --dry-run=server -f deploy\k8s\dev\normalization-worker-dev.yaml`
- 不运行真实 Rabbit live smoke，不触发真实大批量 normalization。

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`

## 关联任务

- 父任务：`tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- 上游父任务：`tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`

## 当前状态

已完成。

## 实现结果

- normalization-worker 新增 service-local `SystemConfigRepository`、`RuntimeInjectedConfig`、
  `NormalizationConfigValues`、`SystemConfigChangedListener` 和
  `NormalizationConfigEventTopology`。
- `RawVideoNormalizationService` 不再通过启动期 `@Value` 读取 retry/backoff；
  `normalization.max-retries` 与 `normalization.backoff.base-seconds` 按
  `Runtime injected -> system_configs DB -> V1 default` 解析。
- `NormalizationRetryWatchdog` 不再通过启动期 `@Value` 读取 batch/stale pending；
  batch 使用 V1 `normalization.batch-size`，dev 可用 `APP_NORMALIZATION_WATCHDOG_BATCH_SIZE`
  运行时覆盖到低批次；pending rescue 使用 V1 `normalization.timeout.pending-min` 分钟单位，
  并兼容 V3 旧 alias `APP_NORMALIZATION_WATCHDOG_STALE_PENDING_SECONDS` 秒单位。
- dev K8S manifest 显式开启 `APP_NORMALIZATION_CONFIG_LISTENER_ENABLED=true`，并用
  `APP_NORMALIZATION_WATCHDOG_BATCH_SIZE=25` 保持低资源开发配置。

## 验证结果

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\normalization-worker-dev.yaml
deployment.apps/ircs-normalization-worker configured (server dry run)
```

## 遗留问题

- 未做 live Rabbit config event smoke，后续仍归属 `028-16-C2`。
- 未补 V1 `NormalizationScheduler` 的启动时 PROCESSING reset、queue depth guard、adaptive workload
  backoff 和 PROCESSING timeout，继续归属 C1/C/A/B 后续切片。
- `@Scheduled` initial/fixed delay 与 `HanLpPrewarmRunner` 开关仍为启动期参数，本轮明确不迁。
