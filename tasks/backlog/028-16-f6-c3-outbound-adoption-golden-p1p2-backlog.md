# 028-16-F6 C3 outbound adoption golden P1/P2 backlog

状态：backlog

父任务：

- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`

## 背景

C3 已完成 shared PUBLIC_FETCH、metadata TMDB credential INTERNAL_SERVICE、admin/portal BFF_PROXY
target validation、circuit breaker first slice、content InternalContentClients adoption、task-service
POST JSON adoption、identity multipart avatar storage caller、storage `IMAGE_DOWNLOAD_STRICT`
shared adapter、C3-S9 admin/portal BFF shared streaming transport adoption，以及 C3-S10
caller-specific circuit ENV matrix implementation。093 仍保留：统一测试批次、dev-safe BFF
smoke、caller-specific K8S override/open-circuit matrix 和 C3-S6/S7/S8/S9/S10 live/full tests。

## 目标

- 推进 BFF method/body/streaming 到 shared outbound：已由 C3-S9 完成实现。
- 补 caller key、ENV/K8S override、circuit breaker 配置矩阵：C3-S10 已完成 shared/service
  默认矩阵实现，组合/集群 open-circuit 矩阵留到统一测试。
- 明确 INTERNAL_SERVICE、PUBLIC_FETCH、BFF_PROXY 三类 policy 的 adoption checklist。
- 准备 C3 live smoke 的 no-real-network 和 dev-safe 网络边界。

## 非目标

- 不重写已完成 catalog/magnet/scraper/metadata/content/task caller。
- 不默认开放 private IP/public fetch SSRF 例外。
- 不在本 spec 中执行真实外网 provider smoke。

## 影响范围

```text
shared/ircs-common/**
services/ircs-identity-service/**
services/ircs-storage-service/**
services/ircs-portal-bff/**
services/ircs-admin-bff/**
services/ircs-content-service/**
services/ircs-task-service/**
docs/reports/093-v1-v3-parity-progress-ledger.md
tasks/in-progress/028-16-c3*.md
```

## 依赖

- C3-S6/S7 当前 focused tests。
- BFF proxy target validation 已完成。
- R2/image download 和 avatar upload 既有 parity 证据。

## 风险

- BFF streaming 迁移可能改变请求体、response streaming 或错误传播。
- identity multipart 与 storage strict adapter 已完成 focused tests，但组合回归和 live-safe chain
  仍需统一测试批次确认。
- circuit breaker key 过粗会造成不同 caller 互相熔断。

## 验收标准

```text
MARK 1/6 remaining caller adoption checklist completed
MARK 2/6 identity multipart caller adopts shared INTERNAL_SERVICE without body loss: done by C3-S8
MARK 3/6 storage IMAGE_DOWNLOAD_STRICT adapter adopts shared policy: done by parallel worker
MARK 4/6 BFF method/body/streaming transport boundary covered: done by C3-S9
MARK 5/6 caller-specific circuit config tests pass: partial, C3-S10 shared config tests pass; K8S/env matrix deferred
MARK 6/6 C3 dev-safe live smoke passes or records explicit blocker
```

## 统一测试边界

纳入统一测试批次 `UTB-02816-P1P2-02`。统一执行 shared common、identity、storage、BFF focused
tests，fake transport no-real-network smoke，必要的 ClusterIP dev-safe smoke，以及 DB/queue/R2 residue
和资源后置检查。

## 2026-06-10 C3-S10 入账

- `tasks/in-progress/028-16-c3-s10-caller-specific-circuit-env-matrix.md` 已完成
  implementation-first `MARK 5/5`。
- ENV matrix 覆盖 `content-refetch-scraper`、`task-scraper-execution`、
  `identity-avatar-storage`、`admin-bff-proxy`、`portal-bff-proxy`；每个 caller 支持
  `ENABLED`、`FAILURE_THRESHOLD`、`OPEN_DURATION`、`OPEN_DURATION_MS`、
  `HALF_OPEN_MAX_CALLS`，并回落到 `IRCS_OUTBOUND_CIRCUIT_*`。
- 轻量验证通过：shared `OutboundHttpClientTest` 定向测试、5 个 adopted services
  `compileJava`。
- 未执行 live smoke、全量测试、K8S rollout；`MARK 6/6` 仍等待 `UTB-02816-P1P2-02`。

## 2026-06-11 UTB-02816-P1P2-02 execution note

`UTB-02816-P1P2-02` 已执行，但 C3 步骤在当前 orchestrator 中仍是手工记录项：

```text
C3 BFF proxy/admin target validation is covered by focused tests; live/full transport remains deferred: MANUAL
```

因此本任务状态不提升为完成；`MARK 6/6` 仍需后续 dev-safe live smoke 或明确 blocker。下一次应补齐：

- admin/portal BFF shared transport ClusterIP smoke；
- caller-specific K8S override/open-circuit matrix；
- identity avatar storage multipart + storage internal API no-real-network chain；
- DB/queue/R2 residue and resource post-checks。
