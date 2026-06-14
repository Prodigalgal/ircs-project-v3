# 028-16-F8 production-like smoke golden P1/P2 backlog

状态：backlog

父任务：

- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

## 背景

093 显示当前策略是先推进功能完整，后续统一测试。已有 provider、R2、SMTP、C2、B15 等多条 live
证据，但 P1/P2 backlog 分散在 normalization、aggregation、Tika/scraper、LLM、C1、C3 和
worker audit。089 的 P2 还保留 CQEngine/RxJava dependency insight、MapStruct 字段级 golden guard、
JPA/hibernate-micrometer 替代 ADR 等横切裁剪项。需要单独定义统一测试批次边界，避免每个 worker
逐一测试导致重复 rollout 和资源污染。

## 目标

- 设计 `UTB-02816-P1P2-01/02/03` 三个统一测试批次的顺序、输入、开关和 cleanup。
- 明确哪些 smoke 允许真实外网、真实 R2、真实 SMTP、真实 LLM，哪些只能 fake/no-cost。
- 给出统一后置检查：DB/queue/DLQ/ES/R2/local/port-forward/Job/Pod/HTTPRoute/ResourceQuota。
- 收敛 CQEngine/RxJava、MapStruct、JPA/hibernate-micrometer 等 P2 dependency/tooling 裁剪证据。
- 将 production-like smoke 与实现任务解耦，作为后置验收门。

## 非目标

- 不替代各实现任务的 focused tests。
- 不默认触发生产命名空间、生产凭据或大批量数据。
- 不在本 spec 中运行 smoke。

## 影响范围

```text
scripts/smoke/**
deploy/k8s/dev/**
docs/reports/093-v1-v3-parity-progress-ledger.md
tasks/backlog/028-16-f*.md
tasks/in-progress/028-v1-v3-behavior-parity-parent.md
```

## 依赖

- F1-F7 backlog 完成对应实现或明确 deferred。
- dev K8S 资源预算允许短时 smoke，且保持无新增公共 HTTPRoute。
- 凭据、bucket、SMTP、LLM allowlist 均通过单独门禁确认。

## 风险

- 多模块统一 smoke 若输入设计不干净，可能造成 DB/queue/ES/R2 残留难定位。
- 真实外部依赖可能波动，需区分代码回归和环境阻塞。
- 统一批次过大可能超过 dev quota 或掩盖单模块失败。

## 验收标准

```text
MARK 1/6 unified smoke batch plan documented
MARK 2/6 batch inputs and feature gates prepared
MARK 3/6 P2 dependency/tooling trim evidence recorded
MARK 4/6 controlled external batch passes or records external blockers
MARK 5/6 cleanup and resource post-checks pass
MARK 6/6 093 ledger and task indexes updated with final results
```

## 统一测试边界

本任务本身定义统一测试边界：

- `UTB-02816-P1P2-01`：normalization、aggregation、Tika/scraper 的 fixture + dev live smoke。
- `UTB-02816-P1P2-02`：LLM fake/no-cost、C1 readiness、C3 outbound adoption。
- `UTB-02816-P1P2-03`：worker/scheduler audit、production-like cleanup、资源和无公共 HTTPRoute 后置检查。

## 2026-06-11 unified test first execution

已执行 `UTB-02816-P1P2-02` 的 no-real-cost / dry-run 子集，并执行只读资源后置检查。

```text
UTB-02816-P1P2-02:
  LLM cleaning fake/no-cost focused regression: PASSED
  C1 gate health focused regression: PASSED
  C1 resolver/resource prep dry-run: PASSED
  C2 config event matrix dry-run guard: PASSED
  C3 live/full transport: still MANUAL/deferred

RESOURCE-02818:
  status: PASSED
  HTTPRoute count: 0
  ResourceQuota: pods 28/30, services 24/26
  dangerous gates: dev-safe defaults retained
```

副作用边界：

- `providerExternal=false`
- `smtpSend=false`
- `r2Write=false`
- 未执行真实 LLM、外网 provider、SMTP 或 R2 写入。

剩余统一测试：

- `UTB-02816-P1P2-01` 完整执行。
- `UTB-02816-P1P2-02` 的 C3 dev-safe live/K8S circuit matrix 与可选真实 LLM 单条。
- `UTB-02816-P1P2-03` worker/scheduler audit 与 production-like cleanup。
- `EXTERNAL-PROVIDER-SMTP-R2` 仅在独立 allow flag 下执行。

## 2026-06-11 UTB-02816-P1P2-03 partial execution

`UTB-02816-P1P2-03` 已执行，但不能标记完成。

```text
PASSED:
  task service audit smoke
  search worker audit focused regression
  scraper worker audit focused regression
  search service audit smoke
  scraper service audit smoke, dev-safe dry-run
  resource rollback verifier

FAILED:
  notification worker audit smoke, exitCode=2
  reason: MAIL_QUEUE_NOT_EMPTY
  q.notification.mail messages=1 consumers=0
  q.notification.mail.dlq messages=0 consumers=0
```

同时修复了 orchestrator 非 0 exit code 假绿问题，后续统一测试结果可信度提升。剩余项为：

- 处理 notification mail queue 残留/consumer gate 后重跑 notification worker audit。
- 补 metadata/normalization/storage worker live queue smoke 与 ops query cleanup。
- 继续执行 `UTB-02816-P1P2-01`。
- 后续独立 gate 下执行外部 provider/SMTP/R2 批次。

## 2026-06-11 UTB-03 rerun result

`UTB-02816-P1P2-03` 已在 DB migrator 修复和 notification listener wrapper 接入后重跑通过。

```text
OutputJsonPath: build\tmp\utb-02816-p1p2-03-after-db-migration-20260611.json
all 7 steps: PASSED
providerExternal=false
smtpSend=false
r2Write=false
HTTPRoute count=0
ResourceQuota pods 28/30, services 24/26
notification queues after restore: 0
```

生产化 smoke 规则更新：

- 不再假设 live DB 等于本地 changelog；每次 schema 问题以 `databasechangelog` 和 `information_schema` 为准。
- 已执行 Liquibase changeset 必须保持不可变；后续 schema 演进必须使用新 changeset。
- dev 可按 wrapper 临时打开 V1 已验证的 enable/gate 做真实调测，但 wrapper 必须恢复开关并检查 residue。

仍未关闭：

- `UTB-02816-P1P2-02` C3 live/full transport 与可选真实 LLM 单条
- `EXTERNAL-PROVIDER-SMTP-R2` 独立 allow flag 批次

## 2026-06-12 UTB-01 rerun result

`UTB-02816-P1P2-01` 已重跑通过，输出 JSON：

```text
build\tmp\utb-02816-p1p2-01-rerun-20260612085514.json
```

结果：

```text
normalization A9 title/location/genre closure: PASSED
normalization A12 title/raw dictionary focused regression: PASSED
aggregation pipeline runtime B15: PASSED
Tika/scraper D3-D4 local fixture smoke: PASSED
```

修复点：

- A9 smoke native `kubectl` stderr 诊断捕获与单行 SQL scalar 读取修正。
- cover image upload size-limit 单测夹具补 `maxUploadBytes`，并新增 oversized guard。

后置资源/清理：

```text
RESOURCE-02818 status=PASSED
HTTPRoute count=0
ResourceQuota pods 28/30, services 24/26
target queues/DLQs empty
smoke Job/Pod residue absent
port-forward residue absent
providerExternal=false, smtpSend=false, r2Write=false
```

仍未关闭：

- `UTB-02816-P1P2-02` C3 live/full transport、K8S circuit matrix 与可选真实 LLM 单条。
- `EXTERNAL-PROVIDER-SMTP-R2` 独立 allow flag 批次。
- 真实 provider charset smoke 需独立外网窗口，不在 UTB-01 默认批次内。
