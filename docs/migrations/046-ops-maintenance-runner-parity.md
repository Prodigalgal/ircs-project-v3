# 046 ops maintenance runner parity

状态：已完成

日期：2026-06-07

## 范围

本记录承接 `028-04`，把 `POST /api/v1/ops/maintenance/search/reindex-unified/init`
从 guarded session/SSE contract 推进到真实 dev-safe runner。该 runner 不执行
Elasticsearch hard reset，不删除业务数据，只按小限额读取 `unified_videos` 并投递
`q.search.sync.unified` 的 `SearchSyncMessage(UNIFIED_VIDEO, INDEX)`。

## 实现

- ops-service 新增 `MaintenanceReindexCommand`。
- `MaintenanceRunnerService` 支持 `search-reindex-unified`。
- `MaintenanceSessionService` 在 SSE stream 阶段执行真实 runner，并输出 LOG、PROGRESS、
  WARN 和 DONE。
- `OPS_MAINTENANCE_REINDEX_DEV_LIMIT` 默认限制为 `5`，代码上限为 `100`。
- 未接入的 maintenance task 继续保持 guarded fallback。

## 镜像与部署

```text
ircs-ops-service@sha256:77736bf364124fe8e582f71ce72cee7edf065c2812b16bbcfe1101963046253d

deployment/ircs-ops-service    Ready 1/1
deployment/ircs-search-service Ready 1/1
deployment/ircs-admin-bff      Ready 1/1
```

未新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 验证

```text
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
.\scripts\smoke\behavior-02802-02804-parity-smoke.ps1
```

Smoke 标记：

```text
MARK 6/8 ops search-reindex-unified runner publishes dev-safe progress
MARK 7/8 search-service consumes reindex message and indexes unified video
MARK 8/8 smoke cleanup leaves no temporary DB/ES residue
```

后置检查：

```text
kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
```

## 剩余边界

- RawVideo reindex、完整 ES hard reset/bulk mode、trend sync provider 与最小
  `last_trend_at` 更新仍需后续独立 task。
- session 仍为单副本进程内状态，后续多副本或重启恢复另行设计。
