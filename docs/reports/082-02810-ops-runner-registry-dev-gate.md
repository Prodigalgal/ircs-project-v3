# 082 028-10 ops runner registry and dev gate 报告

状态：已完成

日期：2026-06-08

## 背景

`028-04` 已完成 `search-reindex-unified` dev-safe runner。`028-10-A` 已记录
production governor 边界，但 ops-service 当前仍缺 runner registry metadata、dev refusal
语义和 runner audit event 口径。下一刀应先把 registry 和 dev gate 固化，避免未来继续
以 ad hoc task name 判断方式扩展高风险 runner。

## 目标

- 在 ops-service 中增加 maintenance runner registry。
- 每个 runner 固定 `riskLevel`、`devAllowed`、`supportsDryRun`、`defaultLimit`、`maxLimit`。
- unsupported 或 high-risk runner 在 dev 输出稳定 refused/guarded 事件。
- dev-safe runner 继续允许 `search-reindex-unified`。
- 将 runner execution/refusal 写入 `worker_job_audit_events` 或保留可查询 audit hook。

## 非目标

- 不实现 raw/unified hard reset。
- 不执行 trend sync、sanitize、aggregation reset 等高风险任务。
- 不新增 HTTPRoute、不 rollout。
- 不修改 notification-worker 或 magnet-service。

## 建议写入范围

```text
services/ircs-ops-service/**
docs/reports/082-02810-ops-runner-registry-dev-gate.md
docs/migrations/082-ops-runner-registry-dev-gate.md
tasks/done/028-10-c-ops-runner-registry-dev-gate.md
```

## 验收标准

```text
MARK 1/5 runner registry exposes risk/dev/dry-run/limit metadata
MARK 2/5 search-reindex-unified remains dev-safe and capped
MARK 3/5 high-risk runner is refused in dev with stable reason
MARK 4/5 runner/refusal emits worker_job_audit_events-compatible evidence
MARK 5/5 ops-service tests pass without destructive maintenance execution
```

## 测试边界

```powershell
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
```

本轮不执行 K8S rollout，不执行高风险 maintenance runner。

## 实施方案

- 在 `ircs-ops-service` 内建立显式 maintenance runner registry，固定每个 task 的
  `riskLevel`、`devAllowed`、`supportsDryRun`、`defaultLimit`、`maxLimit`。
- `search-reindex-unified` 继续作为唯一 dev-safe runner，保持 dev limit cap，不扩大执行范围。
- `sanitize`、`trend-sync`、`search-reindex-raw`、`aggregation-reset` 等生产级任务先登记为
  high-risk metadata，并在 dev gate 中稳定拒绝，返回固定 refusal reason。
- runner execution/refusal 复用 shared `WorkerJobAuditWriter`，写入
  `worker_job_audit_events` compatible evidence；写入失败只记录日志，不反向触发 destructive fallback。

## 风险与边界

- 本轮只修改 ops-service 内部 runner contract、SSE 输出和本地测试。
- 不修改公共索引、父任务、K8S 快照、K8S manifest、notification-worker 或 magnet-service。
- 不执行任何 destructive maintenance，不 rollout，不 push image。

## 实施结果

- 新增 maintenance runner registry metadata，并通过
  `GET /api/v1/ops/maintenance/runners` 暴露 `riskLevel`、`devAllowed`、
  `supportsDryRun`、`defaultLimit`、`maxLimit` 和 refusal reason。
- `search-reindex-unified` 保持唯一 dev-safe runner，继续使用 capped dev limit，
  registry `defaultLimit=5`、`maxLimit=100`。
- 高风险 runner 在 dev gate 中稳定 refused，reason 固定为
  `refused: high-risk maintenance runner is not allowed in dev`。
- runner execution/refusal 复用 shared `WorkerJobAuditWriter`，生成
  `worker_job_audit_events` compatible evidence：`job_type=maintenance-runner`、
  `job_name=<taskName>`、`status=succeeded|failed`。
- 本轮未执行 sanitize、trend-sync、aggregation-reset、search-reindex-raw 或其他高风险
  maintenance runner。

## 验收结果

```text
MARK 1/5 runner registry exposes risk/dev/dry-run/limit metadata
MARK 2/5 search-reindex-unified remains dev-safe and capped
MARK 3/5 high-risk runner is refused in dev with stable reason
MARK 4/5 runner/refusal emits worker_job_audit_events-compatible evidence
MARK 5/5 ops-service tests pass without destructive maintenance execution
```

## 测试结果

```powershell
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 15s`。测试结果 XML 显示 16 个 ops-service test suite 全部
`failures=0`、`errors=0`、`skipped=0`。
