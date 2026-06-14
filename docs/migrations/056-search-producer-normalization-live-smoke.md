# 056 search producer normalization live smoke

状态：已完成

日期：2026-06-07

关联：

- `tasks/in-progress/028-09-search-producer-live-smoke-parity.md`
- `docs/reports/055-search-producer-live-smoke-02809-normalization.md`
- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `docs/migrations/050-search-producer-matrix-parity.md`

## 迁移目标

为 `028-09` 建立第一个 search producer live smoke：验证 normalization worker 已有 producer
path 能从 `q.normalize.video` 走到 `q.search.sync.raw`，并由 search-service 刷新
Elasticsearch `ircs_raw_video`。

## 实现结果

新增脚本：

```text
scripts/smoke/search-producer-normalization-02809-smoke.ps1
```

脚本行为：

```text
seed raw_videos fixture with raw_metadata
publish x.process / video.normalize
wait raw_videos normalization_status=READY
assert normalized raw DB fields
wait ircs_raw_video ES document refresh
assert q.normalize.video/q.search.sync.raw and DLQs are empty
cleanup PostgreSQL and Elasticsearch fixture residue
```

为避免其他 worker 影响，本轮 fixture 使用 `aggregation_status='BOUND'`，不进入
aggregation scheduler 的 `PENDING + READY` claim 范围。

## 验证

语法检查：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-normalization-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
syntax ok
```

live smoke：

```text
.\scripts\smoke\search-producer-normalization-02809-smoke.ps1

MARK 1/5 normalization worker consumes q.normalize.video and marks raw video READY
MARK 2/5 normalization writes normalized raw fields from raw_metadata without triggering aggregation
MARK 3/5 normalization publishes legacy UUID raw search sync and ES raw document is refreshed
MARK 4/5 normalize/search queues and DLQs are empty for the smoke window
MARK 5/5 smoke cleanup leaves no DB/ES fixture residue
```

## 边界与后续

- 未修改服务代码。
- 未修改 K8S manifest。
- 未新增公共 HTTPRoute。
- 未修改 `docs/index.md`、`tasks/index.md` 或 K8S 总快照。
- metadata result、aggregation 和 old unified dirty lifecycle 仍在 `028-09` 后续切片中。
- raw relation strict live smoke 需要先确认 dev normalization-worker 镜像已包含 `028-05-F`
  relation upsert。
