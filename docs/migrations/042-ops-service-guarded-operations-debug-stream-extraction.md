# 042 ops-service guarded operations/debug/stream extraction

状态：已完成

日期：2026-06-07

## 背景

本迁移承接 `025` V1 路由覆盖闭环和
`docs/reports/040-remaining-route-slices-02518-02520.md`。V1 剩余 ops 类
HTTP candidate 包含 dashboard SSE、metadata debug、traffic reset、DLQ
retry/discard 和多条 maintenance init/trend sync。此类接口风险较高，本轮按 dev
guarded contract 接入，避免误触生产级外部资源和破坏性全量任务。

## 覆盖接口

```text
GET    /api/v1/dashboard/metrics/stream
GET    /api/v1/debug/metadata/search
POST   /api/v1/ops/traffic/reset
POST   /api/v1/ops/dlq/{id}/retry
POST   /api/v1/ops/dlq/batch-retry
DELETE /api/v1/ops/dlq/{id}
POST   /api/v1/ops/dlq/batch-discard
POST   /api/v1/ops/maintenance/sanitize/init
POST   /api/v1/ops/maintenance/aggregation/reset-init
POST   /api/v1/ops/maintenance/unified/recalculate-init
POST   /api/v1/ops/maintenance/area/clean-init
POST   /api/v1/ops/maintenance/language/clean-init
POST   /api/v1/ops/maintenance/llm/sanitize/init
POST   /api/v1/ops/maintenance/llm/area/init
POST   /api/v1/ops/maintenance/llm/genre/init
POST   /api/v1/ops/maintenance/llm/category/init
POST   /api/v1/ops/maintenance/search/reindex-raw/init
POST   /api/v1/ops/maintenance/search/reindex-unified/init
POST   /api/v1/ops/maintenance/trend/sync
```

## 实现边界

- dashboard metrics stream 输出一次 metrics SSE payload 后完成。
- metadata debug 返回 `DRY_RUN`，不访问公网 provider。
- traffic reset 只删除指定 `traffic:limit:{key}`。
- DLQ retry/discard 修改临时 `failed_messages` 状态；retry 使用 RabbitMQ 发布语义但 smoke
  仅使用可控临时记录。
- maintenance init 创建 in-memory session，SSE 输出 `LOG` 和 `DONE`；不执行真实全量
  reindex、LLM sanitize 或外部 trend sync。

## 验证

```text
.\gradlew.bat :services:ircs-ops-service:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL
```

ARM64 rollout：

```text
deployment/ircs-ops-service successfully rolled out
imageID: harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:c3fc3dff2092be4695cd999da3f50025ed23bbb7b652fa3fb8733ff68934d7bd
```

`admin-bff` live smoke：

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

## 资源与流量

- 未新增 Pod、Service、PVC、中间件或 HTTPRoute。
- 复用 `ircs-ops-service` 和 `ircs-admin-bff`。
- `ircs-dev` 仍无 HTTPRoute。

完成计数：`19/19`。
