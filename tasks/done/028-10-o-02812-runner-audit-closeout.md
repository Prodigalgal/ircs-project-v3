# 028-10-O / 028-12-E runner audit closeout

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

报告：

- `docs/reports/117-02810-02812-runner-audit-closeout.md`

## 目标

- 收口 ops maintenance runner/governor 的低风险可执行缺口。
- 收口 worker/job audit 中遗漏的 ingestion-worker queue consumer。
- 保持 dev/test 环境不执行 destructive maintenance。

## 结果

- `search-reindex-raw` 现在是 LOW risk、devAllowed、supportsDryRun 的 capped runner。
- raw/unified reindex 通过 search-service internal batch API 写 search sync task。
- 手动 maintenance SSE session 的 `sessionId` 会作为 runner `correlationId` 传递。
- `ingestion-worker` 两个 queue consumer 已接入 `worker_job_audit_events`：
  - `ingestion.raw-video`
  - `ingestion.playlist-sync`

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-ops-service:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-ingestion-worker:test --rerun-tasks
```

```text
ircs-ops-service tests=85 failures=0 errors=0 skipped=0
ircs-ingestion-worker tests=8 failures=0 errors=0 skipped=0
```

## 后置

- `trend-sync`、`sanitize`、`aggregation-reset`、`unified-recalculate` 的真实 V1 业务副作用不在本任务内伪完成。
- K8S rollout / live smoke 后置到统一测试批次。
