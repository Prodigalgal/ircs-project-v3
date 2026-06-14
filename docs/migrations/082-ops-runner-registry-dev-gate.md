# 082 ops runner registry and dev gate 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-10` 增加 ops maintenance runner registry 与 dev gate。该迁移先固化 runner
metadata、dev-safe/refused 语义和 worker/job audit evidence，不执行破坏性维护任务。

## 范围

```text
services/ircs-ops-service/**
docs/reports/082-02810-ops-runner-registry-dev-gate.md
tasks/done/028-10-c-ops-runner-registry-dev-gate.md
```

## 期望结果

```text
MARK 1/5 runner registry exposes risk/dev/dry-run/limit metadata
MARK 2/5 search-reindex-unified remains dev-safe and capped
MARK 3/5 high-risk runner is refused in dev with stable reason
MARK 4/5 runner/refusal emits worker_job_audit_events-compatible evidence
MARK 5/5 ops-service tests pass without destructive maintenance execution
```

## 回滚边界

如 registry 引起回归，可回退新增 registry/gate/audit hook；现有
`search-reindex-unified` dev-safe runner 和 guarded fallback 应保持可恢复。

## 实施记录

- 迁移不新增数据库 schema，复用既有 `worker_job_audit_events` 表结构和 ops 查询 API。
- ops-service 通过本地 registry 固化 runner metadata，并把 dev refusal 作为
  `job_type=maintenance-runner`、`job_name=<taskName>`、`status=failed` 的 worker/job audit
  compatible evidence。
- `search-reindex-unified` 继续使用 dev capped reindex command，runner metadata 中的
  `defaultLimit`、`maxLimit` 与 dev cap 对齐。
- 高风险 task 只登记 metadata 和 refusal reason，不执行生产维护逻辑。

## 禁止事项

```text
不修改 docs/index.md
不修改 tasks/index.md
不修改父任务和 K8S 快照
不修改 deploy/k8s/**
不修改 services/ircs-notification-worker/**
不修改 services/ircs-magnet-service/**
不 rollout，不 push image，不执行 destructive maintenance
```

## 完成结果

- 已在 ops-service 内增加 runner registry 与 metadata response contract。
- 已接入 dev gate：`search-reindex-unified` 允许执行并保持 capped，高风险 task 返回稳定
  refused reason。
- 已接入 shared `WorkerJobAuditWriter`，runner execution/refusal 可以通过既有
  `worker_job_audit_events` 查询面观察。
- 未新增数据库 schema，未修改 migrator，未执行 rollout。

## 验证记录

```powershell
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 15s`。

```text
MARK 1/5 runner registry exposes risk/dev/dry-run/limit metadata
MARK 2/5 search-reindex-unified remains dev-safe and capped
MARK 3/5 high-risk runner is refused in dev with stable reason
MARK 4/5 runner/refusal emits worker_job_audit_events-compatible evidence
MARK 5/5 ops-service tests pass without destructive maintenance execution
```
