# 117 028-10 / 028-12 runner audit closeout

状态：实现完成，live rollout / smoke 后置到统一测试批次

日期：2026-06-12

## 背景

`028-10` 剩余口径长期停在 “other high-risk runner / production-like governor expansion”，
`028-12` 剩余口径停在 “broader runner matrix”。本轮按功能面收口，不执行 destructive
maintenance，不引入公网入口，不新增 K8S 资源。

## 改动

- `ops-service` 将 `search-reindex-raw` 从 high-risk refusal 升级为 dev-safe capped owner runner。
- raw/unified reindex 共用 search-service internal batch enqueue 契约，只写入 `RAW_VIDEO/UNIFIED_VIDEO`
  `INDEX` search sync task，不做 ES hard reset、不删除索引、不全量无界扫描。
- manual SSE maintenance session 将 `sessionId` 下传为 `correlationId`，后续可在
  `worker_job_audit_events` 里按一次手动运维动作追踪。
- `ingestion-worker` 增加 `WorkerJobAuditWriter` 配置。
- `RawVideoIngestConsumer` 写入 `ingestion.raw-video` 成功/失败 audit。
- `PlaylistSyncConsumer` 写入 `ingestion.playlist-sync` 成功/失败 audit。
- ingestion audit 失败文案使用稳定包装错误，不写入原始 payload 或内部异常详情；业务异常继续原样抛出给 Rabbit。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-ops-service:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-ingestion-worker:test --rerun-tasks
```

结果：

```text
ircs-ops-service tests=85 failures=0 errors=0 skipped=0
ircs-ingestion-worker tests=8 failures=0 errors=0 skipped=0
```

## 边界

- 本轮不执行 `trend-sync`、`sanitize`、`aggregation-reset`、`unified-recalculate` 的真实破坏性行为。
- 上述 destructive / high-write V1 行为仍应在行为 parity 主线中按 owner service、gate、dry-run、
  execute gate、audit、数据副作用逐项迁移。
- 本轮未 rollout；`ircs-dev` live smoke 后置到统一测试批次。

## MARK

```text
MARK 1/6 PASS raw reindex runner is dev-safe capped and owner-routed
MARK 2/6 PASS unified/raw search maintenance client supports entity-specific batch enqueue
MARK 3/6 PASS manual maintenance session propagates sessionId as correlationId
MARK 4/6 PASS ingestion raw queue consumer writes worker-job audit
MARK 5/6 PASS ingestion playlist queue consumer writes worker-job audit
MARK 6/6 PASS focused ops-service and ingestion-worker tests pass
```
