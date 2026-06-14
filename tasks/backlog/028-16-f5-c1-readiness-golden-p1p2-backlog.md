# 028-16-F5 C1 readiness golden P1/P2 backlog

状态：backlog

父任务：

- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`

## 背景

C1 first closure 已完成：shared auto-start gate 可观察，normalization/storage watchdog 与 aggregation
scheduler live gate 均为 false，核心 seed 子任务已完成。C1-S2c 已完成 cache warmup/TTL/evict
first closure 和 catalog countries/languages 全量 preset 实现。093 仍保留 C1 P1/P2：
resolver/真实资源站 live smoke，以及可选 V1 式
`RabbitListenerEndpointRegistry.start()` 自动放行。089 还保留 V1 UUID generator 与 Log4j2
async logger / Disruptor 的横切差异，本组负责把这些 dependency/tooling readiness 项转成
迁移、替代或 explicit non-goal 决策。

## 目标

- 复验 config/catalog/content/task/worker cache warmup、TTL、evict first closure。
- 复验 countries/languages 全量 V1 preset，并补 resolver/player preset live smoke。
- 明确 worker/listener/scheduler auto-start gate 是否需要恢复 V1 自动放行，或保持 V3 默认关闭。
- 明确 time-based UUID、Log4j2 async logger / Disruptor 与 V3 当前实现的兼容或替代口径。
- 将 C1 readiness 的 live smoke 输入归并到统一测试批次。

## 非目标

- 不默认开启所有 Rabbit listener、watchdog 或 scheduler。
- 不做真实资源站大批量抓取。
- 不修改 C2 已关闭的 SystemConfig cache/event 父任务口径。

## 影响范围

```text
services/ircs-catalog-service/**
services/ircs-content-service/**
services/ircs-task-service/**
services/ircs-normalization-worker/**
services/ircs-storage-service/**
services/ircs-aggregation-worker/**
docs/reports/093-v1-v3-parity-progress-ledger.md
tasks/in-progress/028-16-c1*.md
```

## 依赖

- C1-S1 至 C1-S9 和 C1 full readiness first closure。
- C2 SystemConfig cache/event parity 已关闭。
- 后续统一 smoke 允许查询 readiness health detail、DB seed 和 gate 状态。

## 风险

- 全量 preset seed 已实现，但仍需 empty dev DB/live smoke 验证是否与现有人工数据或 slug/code 唯一约束冲突。
- 自动放行 listener/scheduler 可能触发真实队列、副作用或资源占用。
- cache TTL/evict matrix 容易与 C2 已完成项重复，需要限定为 C1 readiness/cache warmup。
- UUID 与 async logging 属于横切差异，不能在未验证索引、审计和启动配置影响前直接替换。

## 验收标准

```text
MARK 1/6 cache warmup/TTL/evict matrix documented: done first closure by C1-S2c, live matrix deferred
MARK 2/6 countries/languages full preset gap closed or accepted: done implementation by C1-S2c, live seed verification deferred
MARK 3/6 resolver/player preset live smoke prepared: done dry-run prep by C1-S2c, live execution deferred
MARK 4/6 auto-start gate, UUID and async logging decisions recorded
MARK 5/6 focused catalog/content/task/worker tests pass
MARK 6/6 readiness smoke passes with gates, DB seed and residue checks
```

## 统一测试边界

纳入统一测试批次 `UTB-02816-P1P2-02`。统一执行 seed/cache focused tests、readiness health smoke、
resolver/player controlled live smoke、gate 状态核查和 HTTPRoute/ResourceQuota 后置检查。

## 2026-06-11 UTB-02816-P1P2-02 partial execution

`UTB-02816-P1P2-02` 已执行 C1 gate health focused regression、resolver/resource prep dry-run 和资源后置检查。
真实 resolver/player/资源站 live 仍未执行。

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-02 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-02-20260611.json
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch RESOURCE-02818 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\resource-02818-after-utb02-20260611.json
```

结果：

```text
C1 gate health focused regression: PASSED
C1 resolver/resource prep dry-run: PASSED
RESOURCE-02818: PASSED
ircs-dev HTTPRoute count: 0
ResourceQuota: pods 28/30, services 24/26
dangerous gates: dev-safe defaults retained
```

当前 MARK 口径：

```text
MARK 5/6 focused catalog/content/task/worker tests pass: done by UTB-02816-P1P2-02
MARK 6/6 readiness smoke passes with gates, DB seed and residue checks: partial; live resolver/player remains deferred
```
