# 058 search producer metadata result live smoke

状态：已完成

关联任务：

- `tasks/in-progress/028-09-search-producer-live-smoke-parity.md`

关联报告：

- `docs/reports/058-search-producer-live-smoke-02809-metadata-result.md`

## 目标

为 `028-09 search producer live smoke parity` 补齐 metadata result -> raw search sync live smoke：

```text
q.enrich.metadata.result
  -> ircs-metadata-worker metadata result consumer
  -> raw_videos metadata/status update
  -> q.search.sync.raw
  -> ircs-search-service
  -> ircs_raw_video
```

## 边界

- 只新增 smoke 脚本和本地 docs/tasks。
- 不修改 `services/**`、`shared/**`、K8S manifest 或 HTTPRoute。
- 不 rollout。
- 不依赖真实 provider upstream。

## 验收

- PowerShell parse check 通过。
- live smoke 条件满足时输出 6 个 MARK。
- live smoke 条件不满足时输出结构化 `SKIPPED` 和 diagnostic。
- DB/ES fixture cleanup 后无残留。
- `q.enrich.metadata.result`、`q.enrich.metadata.result.dlq`、`q.search.sync.raw`、`q.search.sync.raw.dlq` 最终为空。

## 验证记录

语法检查：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-metadata-result-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
syntax ok
```

live smoke：

```text
.\scripts\smoke\search-producer-metadata-result-02809-smoke.ps1

MARK 1/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 2/6 metadata result fixture is seeded and pre-cleaned from DB/ES
MARK 3/6 metadata result consumer updates raw_videos to SUCCESS
MARK 4/6 metadata result publishes legacy UUID raw search sync and ES raw document is refreshed
MARK 5/6 metadata/search queues and DLQs are empty for the smoke window
MARK 6/6 smoke cleanup leaves no DB/ES fixture residue
```

结论：

- metadata result consumer -> raw search sync -> search-service raw ES refresh 链路通过 live smoke。
- DB/ES fixture cleanup 通过。
- 队列/DLQ 检查通过。
- HTTPRoute 只读检查通过。
- 未修改服务代码，未 rollout，未新增 K8S 资源。
