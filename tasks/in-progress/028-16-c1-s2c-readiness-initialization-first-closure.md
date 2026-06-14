# 028-16-C1-S2c readiness initialization remaining first closure task spec

状态：完成实现；live smoke 统一测试后置

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

## 目标

- 对照 V1 `countries.json` / `languages.json` 和 resolver/default seed 行为，补齐 V3 catalog/content 初始化剩余缺口。
- 完成 cache warmup/TTL/evict first closure 策略：需要启动 warmup 的 cache 明确执行点，SystemConfig 类 cache 继续用 event evict + TTL read-through 兜底。
- 准备 resolver/真实资源站 live smoke 入口，但本轮不执行 live。
- 回写 C1 父任务、089/093 总账和统一测试清单。

## 范围

```text
services/ircs-catalog-service/**
services/ircs-content-service/**
scripts/smoke/**
docs/**
tasks/**
```

## 非目标

- 不做 live smoke，不访问真实 resolver、资源站、公网 provider、R2 或 SMTP。
- 不改 notification、C3 outbound、LLM cleaning、storage R2。
- 不恢复 V1 单体式全局 `DataInitializationOrchestrator`；如需自动 `RabbitListenerEndpointRegistry.start()` 后续独立切片处理。

## 影响文件

```text
services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog/**
services/ircs-catalog-service/src/main/resources/presets/**
services/ircs-catalog-service/src/test/java/com/prodigalgal/ircs/catalog/**
scripts/smoke/resolver-resource-live-02816c1-prep.ps1
tasks/in-progress/028-16-c1-initialization-readiness-gate.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/reports/093-v1-v3-parity-progress-ledger.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 验收标准

```text
MARK 1/6 V1 countries/languages full preset imported into catalog-service runtime owner seed.
MARK 2/6 Default seed remains lowest priority and does not overwrite existing ENV/K8S/DB injection or user-edited rows.
MARK 3/6 Cache warmup/TTL/evict matrix recorded with owner and fallback decision.
MARK 4/6 Resolver/resource live smoke prep script exists and is safe by default.
MARK 5/6 Lightweight compile/test run if fast, otherwise record unified testing deferred.
MARK 6/6 C1 task, 089/093 ledgers and unified test list updated with partial/MARK status.
```

## 测试方式

本轮按用户策略跳过 live smoke 和耗时完整测试。优先运行 catalog-service focused test 或 compile；若耗时/阻塞，则记录“统一测试后置”。

## 实现结果

2026-06-10 已完成实现 first closure：

- catalog-service 复用 V1 `presets/countries.json` / `presets/languages.json`，启动 owner seed 从资源读取 251 个 country/area 和 187 个 language；资源缺失时才回退到原 P0 子集。
- 默认 seed 仍沿用 repository 语义：只插入缺失项或补空字段，不覆盖已有用户编辑值；ENV/K8S/启动参数/DB 真实注入仍高于 default seed。
- cache 策略收敛为：catalog 字典/资源站/resolver/task/credential 通过 owner initializer 启动期 warmup/seed；SystemConfig 类消费者通过 Rabbit event evict + short TTL read-through 兜底。本轮把 normalization-worker 的 SystemConfig cache 也补为默认 30s TTL，content-service 已由 C2-S21 覆盖。
- 新增 `scripts/smoke/resolver-resource-live-02816c1-prep.ps1`，默认 dry-run 只输出计划和门禁；真实 HTTP 必须显式 `-ExecuteLive -AllowExternalFetch`。

## 验证

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --tests com.prodigalgal.ircs.catalog.CatalogDefaultInitializerRunnerTest
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests com.prodigalgal.ircs.normalization.SystemConfigRepositoryTest
BUILD SUCCESSFUL

.\scripts\smoke\resolver-resource-live-02816c1-prep.ps1
MARK 1/5 resolver/resource live smoke prep loaded
MARK 2/5 dry-run only; no HTTP calls will be sent
MARK 3/5 planned check: catalog data source preset exists and can sync remote class[] under explicit live gate
MARK 4/5 planned check: content active resolver preset exists and exposes default line
MARK 5/5 planned check: external fetch remains blocked unless -AllowExternalFetch is supplied
```

## MARK

```text
MARK 1/6 V1 countries/languages full preset imported into catalog-service runtime owner seed: done 2026-06-10
MARK 2/6 Default seed remains lowest priority and does not overwrite existing ENV/K8S/DB injection or user-edited rows: done 2026-06-10
MARK 3/6 Cache warmup/TTL/evict matrix recorded with owner and fallback decision: done first closure 2026-06-10
MARK 4/6 Resolver/resource live smoke prep script exists and is safe by default: done 2026-06-10, live not executed
MARK 5/6 Lightweight compile/test run if fast, otherwise record unified testing deferred: done focused tests 2026-06-10
MARK 6/6 C1 task, 089/093 ledgers and unified test list updated with partial/MARK status: done 2026-06-10
```

## 统一测试后置

- 不执行 resolver/player/真实资源站 live smoke；并入 `UTB-02816-P1P2-02 C1 readiness`。
- 不执行完整 Gradle suite；后续统一测试需至少覆盖 catalog-service、content-service、normalization-worker 与 live prep 脚本。
- 不恢复 V1 式 `RabbitListenerEndpointRegistry.start()` 自动放行；如后续确认需要，独立切片实现。
