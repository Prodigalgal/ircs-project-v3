# 025-20 ops-service guarded operations/debug/stream contract MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/040-remaining-route-slices-02518-02520.md`

## 背景

`ircs-ops-service` 当前已覆盖 dashboard read、request-audit read、DLQ read、
traffic status 和 maintenance active/stream expired-session。V1 剩余 ops 缺口主要是
带副作用命令、dashboard SSE、metadata debug。需要在 dev 中以 guarded contract
方式逐步复现，避免误触生产外部资源或破坏性全量任务。

## 目标

- 复现 dashboard metrics SSE stream。
- 复现 metadata debug search 的参数和 dry-run/provider-unavailable 响应契约。
- 复现 traffic reset key。
- 复现 DLQ retry/discard/batch retry/batch discard。
- 复现 maintenance init/trend sync 的 session 创建与 SSE 可观察契约。

## 非目标

- 不直接执行生产级全量重建索引、LLM 清洗、全量聚合或外部趋势抓取。
- 不访问公网 metadata provider。
- 不新增 Pod、Service、HTTPRoute、PVC 或中间件。
- 不修改 magnet/identity/content 服务。

## 输入

```text
V1 DashboardController.streamMetrics
V1 MetadataDebugController
V1 TrafficMonitorController.reset
V1 DlqController / FailedMessageServiceImpl
V1 MaintenanceController / MaintenanceService
services/ircs-ops-service
```

## 输出

- ops guarded command/debug/stream Controller/Service/Repository 增量。
- 对应单元测试。
- `admin-bff` live smoke 脚本或主 agent smoke 建议。

## 影响范围

允许写入：

```text
services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops
services/ircs-ops-service/src/test/java/com/prodigalgal/ircs/ops
tasks/done/025-20-ops-service-guarded-operations-debug-stream-contract-mvp.md
```

禁止写入：

```text
services/ircs-magnet-service
services/ircs-identity-service
services/ircs-admin-bff
deploy/k8s
```

## 验收标准

- 本任务候选接口按完成粒度输出 `MARK n/n`。
- destructive maintenance 在 dev 中只创建 session 与事件流，不执行真实破坏性全量任务。
- DLQ smoke 使用临时 `failed_messages` 记录和可控 RabbitMQ 目标。
- traffic reset smoke 只删除临时 `traffic:limit:*` key。
- 后续主 agent 通过 `admin-bff` live smoke 完成标记。

## 测试边界

主 agent 或 worker 计划执行：

```text
.\gradlew.bat :services:ircs-ops-service:test
```

主 agent 后续执行全仓 build、ARM64 rollout 和 live smoke。

## 本轮实施结果（2026-06-07）

已完成：

- `GET /api/v1/dashboard/metrics/stream`：输出一次 dashboard metrics SSE payload 并完成。
- `GET /api/v1/debug/metadata/search`：输出 dev dry-run 响应，不访问公网 provider。
- `POST /api/v1/ops/traffic/reset`：删除指定 `traffic:limit:{key}`。
- `POST /api/v1/ops/dlq/{id}/retry`、`POST /api/v1/ops/dlq/batch-retry`、
  `DELETE /api/v1/ops/dlq/{id}`、`POST /api/v1/ops/dlq/batch-discard`：
  只处理临时 `failed_messages` 记录，retry 后标记 `RETRIED`，discard 后标记
  `DISCARDED`。
- maintenance init/trend sync：创建 guarded in-memory session，SSE 输出 `LOG` 和
  `DONE`，不执行真实全量重建、LLM 清洗或公网趋势同步。
- 新增 `scripts/smoke/admin-bff-02520-smoke.ps1`，覆盖 19 条候选接口。

本轮测试：

```text
.\gradlew.bat :services:ircs-ops-service:test
BUILD SUCCESSFUL

.\gradlew.bat :services:ircs-magnet-service:test :services:ircs-identity-service:test :services:ircs-ops-service:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL
```

ARM64 rollout：

```text
deployment/ircs-ops-service successfully rolled out
imageID: harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:c3fc3dff2092be4695cd999da3f50025ed23bbb7b652fa3fb8733ff68934d7bd
```

Live smoke：

```text
MARK 1/19 GET /api/v1/dashboard/metrics/stream
MARK 2/19 GET /api/v1/debug/metadata/search
MARK 3/19 POST /api/v1/ops/traffic/reset
MARK 4/19 POST /api/v1/ops/dlq/{id}/retry
MARK 5/19 POST /api/v1/ops/dlq/batch-retry
MARK 6/19 DELETE /api/v1/ops/dlq/{id}
MARK 7/19 POST /api/v1/ops/dlq/batch-discard
MARK 8/19 POST /api/v1/ops/maintenance/sanitize/init
MARK 9/19 POST /api/v1/ops/maintenance/aggregation/reset-init
MARK 10/19 POST /api/v1/ops/maintenance/unified/recalculate-init
MARK 11/19 POST /api/v1/ops/maintenance/area/clean-init
MARK 12/19 POST /api/v1/ops/maintenance/language/clean-init
MARK 13/19 POST /api/v1/ops/maintenance/llm/sanitize/init
MARK 14/19 POST /api/v1/ops/maintenance/llm/area/init
MARK 15/19 POST /api/v1/ops/maintenance/llm/genre/init
MARK 16/19 POST /api/v1/ops/maintenance/llm/category/init
MARK 17/19 POST /api/v1/ops/maintenance/search/reindex-raw/init
MARK 18/19 POST /api/v1/ops/maintenance/search/reindex-unified/init
MARK 19/19 POST /api/v1/ops/maintenance/trend/sync
failed_messages residue: 0
```

完成计数：`19/19`。
