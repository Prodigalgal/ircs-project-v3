# 028-10-C ops runner registry and dev gate task spec

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/082-02810-ops-runner-registry-dev-gate.md`
- `docs/migrations/082-ops-runner-registry-dev-gate.md`

## 背景

ops-service 已有 `search-reindex-unified` dev-safe runner，但高风险 maintenance tasks
仍缺统一 registry 和 dev gate。继续扩展真实 runner 前，需要先让风险、limit、dry-run 和
audit 证据成为显式 contract。

## 目标

- 增加 runner registry metadata。
- 保持 `search-reindex-unified` dev-safe capped 行为。
- dev 环境拒绝 high-risk runner，并输出稳定 reason。
- 产生 worker/job audit compatible evidence。

## 非目标

- 不实现或执行 raw/unified hard reset。
- 不执行 trend-sync、sanitize、aggregation-reset。
- 不修改 K8S manifest、不 rollout。
- 不改 notification-worker 或 magnet-service。

## 输入

```text
services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/MaintenanceRunnerService.java
services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/MaintenanceSessionService.java
services/ircs-ops-service/src/test/java/com/prodigalgal/ircs/ops/**
worker_job_audit_events query contract
```

## 输出

```text
runner registry/dev gate implementation
ops-service tests
docs/reports/082-02810-ops-runner-registry-dev-gate.md
docs/migrations/082-ops-runner-registry-dev-gate.md
```

## 影响范围

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

## 子任务

### 028-10-C-1 runner registry metadata

状态：已完成

背景：现有 `MaintenanceRunnerService.supports()` 只识别 `search-reindex-unified`，缺少可查询的
risk/dev/dry-run/limit contract。

目标：在 ops-service 中增加 registry metadata，并覆盖 dev-safe 与 high-risk task。

非目标：不新增真实 high-risk runner 实现。

输出：registry metadata 类型、runner lookup、单元测试。

验收：满足 `MARK 1/5`。

### 028-10-C-2 dev gate refusal and capped unified runner

状态：已完成

背景：高风险 runner 在 dev 需要稳定拒绝 reason，`search-reindex-unified` 需要继续 capped。

目标：将 runner 执行入口统一通过 dev gate；允许 unified reindex，拒绝 high-risk task。

非目标：不执行 sanitize、trend-sync、aggregation reset、raw reindex 等维护逻辑。

输出：dev gate 执行/拒绝结果、SSE guarded/refused 输出、单元测试。

验收：满足 `MARK 2/5` 与 `MARK 3/5`。

### 028-10-C-3 worker/job audit evidence

状态：已完成

背景：runner/refusal 需要进入 `worker_job_audit_events` compatible evidence 或保留可查询 hook。

目标：复用 shared `WorkerJobAuditWriter` 记录 maintenance runner succeeded/failed evidence。

非目标：不新增数据库表结构，不修改 migrator。

输出：ops-service audit writer configuration、runner audit 调用、查询兼容测试。

验收：满足 `MARK 4/5`。

### 028-10-C-4 local verification and closeout

状态：已完成

目标：运行必跑测试，回写文档和 task 验收结果。

验收：满足 `MARK 5/5`。

## 当前实施计划

- 先在 `services/ircs-ops-service/**` 内新增 registry metadata 与 dev gate result 类型。
- 再接入 shared `WorkerJobAuditWriter`，使执行和拒绝都形成 worker/job audit compatible event。
- 最后补齐 ops-service 单元测试并运行必跑命令。

## 实施结果

- 新增 `MaintenanceRiskLevel`、`MaintenanceRunnerMetadata`、
  `MaintenanceRunnerExecution`，并在 `MaintenanceRunnerService` 中建立 registry。
- 新增 `GET /api/v1/ops/maintenance/runners`，用于查询 runner metadata。
- `search-reindex-unified` 继续执行 `MaintenanceReindexCommand.enqueueUnifiedIndex()`，
  dev limit 仍被 `MAX_DEV_LIMIT=100` cap。
- 高风险 runner 在 dev gate 中稳定 refused，不触发生产维护逻辑。
- ops-service 复用 shared `WorkerJobAuditWriter`，为 succeeded/refused/failed runner 写入
  `worker_job_audit_events` compatible evidence。

## 验收结果

```text
MARK 1/5 runner registry exposes risk/dev/dry-run/limit metadata
MARK 2/5 search-reindex-unified remains dev-safe and capped
MARK 3/5 high-risk runner is refused in dev with stable reason
MARK 4/5 runner/refusal emits worker_job_audit_events-compatible evidence
MARK 5/5 ops-service tests pass without destructive maintenance execution
```

## 测试记录

```powershell
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 15s`。测试 XML 显示 16 个 ops-service test suite 均为
`failures=0`、`errors=0`、`skipped=0`。

## 运行边界记录

- 未执行 sanitize、trend-sync、aggregation-reset、search-reindex-raw 或其他高风险
  maintenance runner。
- 未执行 K8S rollout。
- 未 push image。
- 主线程已完成复验、索引回写和父任务集成，并将本任务移动到
  `tasks/done/028-10-c-ops-runner-registry-dev-gate.md`。
